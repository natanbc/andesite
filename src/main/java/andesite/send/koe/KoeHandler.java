package andesite.send.koe;

import andesite.Andesite;
import andesite.send.AudioHandler;
import andesite.send.AudioProvider;
import andesite.util.NativeUtils;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.kqueue.KQueue;
import moe.kyokobot.koe.Koe;
import moe.kyokobot.koe.KoeClient;
import moe.kyokobot.koe.KoeEventAdapter;
import moe.kyokobot.koe.MediaConnection;
import moe.kyokobot.koe.VoiceServerInfo;
import moe.kyokobot.koe.codec.netty.NettyFramePollerFactory;
import moe.kyokobot.koe.codec.udpqueue.UdpQueueFramePollerFactory;
import moe.kyokobot.koe.gateway.GatewayVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class KoeHandler implements AudioHandler {
    private static final Logger log = LoggerFactory.getLogger(KoeHandler.class);
    
    private final Map<Long, KoeClient> clients = new ConcurrentHashMap<>();
    private final Andesite andesite;
    private final Koe koe;
    
    public KoeHandler(Andesite andesite) {
        this.andesite = andesite;
        var config = andesite.config().getConfig("andesite.koe");
        var builder = new KoeBuilder()
                .setGatewayVersion(GatewayVersion.valueOf(
                        config.getString("gateway").strip().toUpperCase()
                ));
        var transport = config.getString("transport");
        switch(transport.strip().toLowerCase()) {
            case "epoll" -> builder.epoll();
            case "kqueue" -> builder.kqueue();
            case "nio" -> builder.nio();
            case "default" -> {
                if(Epoll.isAvailable()) {
                    log.info("Using epoll transport");
                    builder.epoll();
                } else if(KQueue.isAvailable()) {
                    log.info("Using kqueue transport");
                    builder.kqueue();
                } else {
                    log.info("Using nio transport");
                    builder.nio();
                }
            }
            default -> throw new IllegalArgumentException("Invalid transport '" + transport + "'");
        }
        var byteBufAllocator = config.getString("byte-buf-allocator").strip().toLowerCase();
        builder.setByteBufAllocator(switch(byteBufAllocator) {
            case "netty-default" -> ByteBufAllocator.DEFAULT;
            case "default" -> PooledByteBufAllocator.DEFAULT;
            case "unpooled" -> UnpooledByteBufAllocator.DEFAULT;
            default -> throw new IllegalArgumentException("Invalid byte buf allocator '" + byteBufAllocator + "'");
        });
        builder.setHighPacketPriority(config.getBoolean("high-packet-priority"));
    
        boolean udpQueue;
        if(config.hasPath("udp-queue.enabled")) {
            udpQueue = config.getBoolean("udp-queue.enabled");
        } else {
            udpQueue = NativeUtils.isUdpQueueAvailable();
        }
        if(udpQueue) {
            log.info("Using udp-queue poller");
            if(!NativeUtils.isUdpQueueAvailable()) {
                throw new IllegalArgumentException("udp-queue native library required by the " +
                                                           "config (koe.udp-queue.enabled = true) but " +
                                                           "is not available");
            }
            var buffer = config.getInt("udp-queue.buffer");
            var threads = config.getInt("udp-queue.threads");
            builder.setFramePollerFactory(new UdpQueueFramePollerFactory(buffer,
                    threads < 0 ? Runtime.getRuntime().availableProcessors() : threads));
        } else {
            log.info("Using netty poller");
            builder.setFramePollerFactory(new NettyFramePollerFactory());
        }
        
        this.koe = builder.create();
    }
    
    @Override
    public void setProvider(@Nonnull String userId, @Nonnull String guildId, @Nullable AudioProvider provider) {
        var conn = Objects.requireNonNull(connection(userId, guildId, true));
        var sender = (KoeProvider)conn.getAudioSender();
        if(sender != null && sender.source == provider) return;
        conn.setAudioSender(new KoeProvider(conn, provider));
        if(sender != null) sender.dispose();
    }
    
    @Override
    public void handleVoiceUpdate(@Nonnull String userId, @Nonnull String guildId, @Nonnull String sessionId, @Nonnull String endpoint, @Nonnull String token) {
        var conn = Objects.requireNonNull(connection(userId, guildId, true));
        conn.connect(new VoiceServerInfo(
                sessionId, endpoint, token
        ));
    }
    
    @Override
    public void closeConnection(@Nonnull String userId, @Nonnull String guildId) {
        var conn = connection(userId, guildId, false);
        if(conn != null) {
            conn.close();
        }
    }
    
    @Nullable
    @CheckReturnValue
    private MediaConnection connection(@Nonnull String userId, @Nonnull String guildId, boolean create) {
        var uid = Long.parseUnsignedLong(userId);
        var gid = Long.parseUnsignedLong(guildId);
        if(!create) {
            var c = clients.get(uid);
            if(c == null) return null;
            return c.getConnection(gid);
        }
        var client = clients.computeIfAbsent(uid, koe::newClient);
        MediaConnection conn;
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized(client) {
            conn = client.getConnection(gid);
            if(conn == null) {
                conn = client.createConnection(gid);
                conn.registerListener(new KoeEventAdapter() {
                    @Override
                    public void gatewayClosed(int code, String reason, boolean byRemote) {
                        andesite.dispatcher().onWebSocketClosed(
                                Long.toUnsignedString(uid),
                                Long.toUnsignedString(gid),
                                code,
                                reason,
                                byRemote
                        );
                    }
                });
            }
        }
        return conn;
    }
}
