package andesite.node.send;

import andesite.node.Andesite;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import moe.kyokobot.koe.Koe;
import moe.kyokobot.koe.KoeClient;
import moe.kyokobot.koe.KoeEventAdapter;
import moe.kyokobot.koe.KoeOptions;
import moe.kyokobot.koe.audio.AudioFrameProvider;
import moe.kyokobot.koe.codec.Codec;
import moe.kyokobot.koe.gateway.GatewayVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class KoeHandler implements AudioHandler {
    private static final Logger log = LoggerFactory.getLogger(KoeHandler.class);
    
    private final Map<Long, ClientState> providers = new ConcurrentHashMap<>();
    private final Andesite andesite;
    private final Koe koe;
    
    public KoeHandler(Andesite andesite) {
        this.andesite = andesite;
        this.koe = Koe.koe(createKoeOptions(andesite));
    }
    
    @Override
    public void setProvider(@Nonnull String userId, @Nonnull String guildId, @Nullable AudioProvider provider) {
        var state = providers.computeIfAbsent(Long.parseUnsignedLong(userId), id -> new ClientState(andesite, koe.newClient(id)));
        AudioProvider old;
        if(provider == null) {
            old = state.remove(Long.parseUnsignedLong(guildId));
        } else {
            old = state.put(Long.parseUnsignedLong(guildId), provider);
        }
        if(old == provider) {
            return;
        }
        state.update(Long.parseUnsignedLong(guildId), provider);
        if(old != null) {
            old.close();
        }
    }
    
    @Override
    public void handleVoiceUpdate(@Nonnull String userId, @Nonnull String guildId, @Nonnull String sessionId, @Nonnull String endpoint, @Nonnull String token) {
    
    }
    
    @Override
    public void closeConnection(@Nonnull String userId, @Nonnull String guildId) {
        var state = providers.get(Long.parseUnsignedLong(userId));
        if(state != null) {
            state.close(Long.parseUnsignedLong(guildId));
        }
    }
    
    private static KoeOptions createKoeOptions(Andesite andesite) {
        var config = andesite.config().getConfig("andesite.koe");
        
        var hasEpoll = Epoll.isAvailable();
        var epoll = hasEpoll;
        if(config.hasPath("event-loop-type")) {
            var value = config.getString("event-loop-type");
            if(value.equals("epoll")) {
                if(!hasEpoll) {
                    throw new IllegalArgumentException("Epoll is not supported on this system. Please use a different" +
                            " event loop type");
                }
            } else if(value.equals("nio")) {
                epoll = false;
            } else {
                throw new IllegalArgumentException("No event loop with type " + value);
            }
        }
        
        log.info("Using {}", epoll ? "epoll" : "nio");
        
        var pooled = true;
        if(config.hasPath("byte-buffer-allocator")) {
            switch(config.getString("byte-buffer-allocator")) {
                case "pooled":
                    pooled = true;
                    break;
                case "unpooled":
                    pooled = false;
                    break;
                default:
                    throw new IllegalArgumentException("No byte buffer allocator with type " + config.getString("byte-buffer-allocator"));
            }
        }
        
        log.info("Using {} buffers", pooled ? "pooled" : "unpooled");
        
        var gatewayVersion = GatewayVersion.V4;
        
        log.info("Using gateway V4");
        
        //default: true
        var highPriority = !config.hasPath("high-priority") || config.getBoolean("high-priority");
        
        log.info("Using {} priority packets", highPriority ? "high" : "low");
        
        return new KoeOptions(
                epoll ? new EpollEventLoopGroup() : new NioEventLoopGroup(),
                epoll ? EpollSocketChannel.class : NioSocketChannel.class,
                epoll ? EpollDatagramChannel.class : NioDatagramChannel.class,
                pooled ? PooledByteBufAllocator.DEFAULT : UnpooledByteBufAllocator.DEFAULT,
                gatewayVersion,
                highPriority
        );
    }
    
    private static class ClientState {
        private final Map<Long, AudioProvider> providerMap = new ConcurrentHashMap<>();
        private final Andesite andesite;
        private final KoeClient client;
    
        private ClientState(Andesite andesite, KoeClient client) {
            this.andesite = andesite;
            this.client = client;
        }
    
        public AudioProvider remove(long guildId) {
            client.destroyConnection(guildId);
            return providerMap.remove(guildId);
        }
    
        public AudioProvider put(long guildId, AudioProvider provider) {
            return providerMap.put(guildId, provider);
        }
    
        public void update(long guildId, AudioProvider provider) {
            var conn = client.createConnection(guildId);
            conn.registerListener(new KoeEventAdapter() {
                @Override
                public void gatewayClosed(int code, String reason) {
                    andesite.dispatcher().onWebSocketClosed(
                            Long.toUnsignedString(client.getClientId()),
                            Long.toUnsignedString(guildId),
                            code,
                            reason,
                            true
                    );
                }
            });
            conn.setAudioSender(new AudioFrameProvider() {
                @Override
                public boolean canSendFrame() {
                    return provider.canProvide();
                }
                
                @Override
                public void retrieve(Codec codec, ByteBuf buf) {
                    buf.writeBytes(provider.provide());
                }
            });
        }
    
        public void close(long guildId) {
            var provider = providerMap.remove(guildId);
            if(provider != null) {
                provider.close();
            }
            client.destroyConnection(guildId);
        }
    }
}
