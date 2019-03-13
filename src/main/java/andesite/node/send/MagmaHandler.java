package andesite.node.send;

import andesite.node.Andesite;
import andesite.node.config.Config;
import andesite.node.provider.AsyncPacketProviderFactory;
import andesite.node.send.jdaa.JDASendFactory;
import andesite.node.send.nio.NioSendFactory;
import andesite.node.util.ByteArrayProvider;
import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.jdaudp.NativeAudioSendFactory;
import com.sedmelluq.discord.lavaplayer.udpqueue.natives.UdpQueueManager;
import net.dv8tion.jda.core.audio.AudioSendHandler;
import net.dv8tion.jda.core.audio.factory.IAudioSendFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.npstr.magma.MagmaApi;
import space.npstr.magma.MagmaMember;
import space.npstr.magma.MagmaServerUpdate;
import space.npstr.magma.events.api.WebSocketClosed;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MagmaHandler implements AudioHandler {
    private static final Logger log = LoggerFactory.getLogger(MagmaHandler.class);
    
    private final Map<String, Map<String, AudioProvider>> providers = new ConcurrentHashMap<>();
    private final MagmaApi magma;
    private final ByteArrayProvider byteArrayProvider;
    
    public MagmaHandler(Andesite andesite) {
        var factory = createSendFactory(andesite);
        this.magma = MagmaApi.of(__ -> factory);
        this.byteArrayProvider = createArrayProvider(andesite.config());
        magma.getEventStream().subscribe(event -> {
            if(event instanceof WebSocketClosed) {
                var e = (WebSocketClosed) event;
                andesite.dispatcher().onWebSocketClosed(
                    e.getMember().getUserId(),
                    e.getMember().getGuildId(),
                    e.getCloseCode(),
                    e.getReason(),
                    e.isByRemote()
                );
            }
        });
    }
    
    @Override
    public void setProvider(@Nonnull String userId, @Nonnull String guildId, @Nullable AudioProvider provider) {
        var old = providers.computeIfAbsent(userId, __ -> new ConcurrentHashMap<>()).put(guildId, provider);
        if(old == provider) {
            return;
        }
        magma.setSendHandler(
            MagmaMember.builder()
                .userId(userId)
                .guildId(guildId)
                .build(),
            provider == null ? null : new MagmaSendHandler(provider, byteArrayProvider)
        );
        if(old != null) {
            old.close();
        }
    }
    
    @Override
    public void handleVoiceUpdate(@Nonnull String userId, @Nonnull String guildId,
                                  @Nonnull String sessionId, @Nonnull String endpoint,
                                  @Nonnull String token) {
        magma.provideVoiceServerUpdate(
            MagmaMember.builder()
                .userId(userId)
                .guildId(guildId)
                .build(),
            MagmaServerUpdate.builder()
                .sessionId(sessionId)
                .endpoint(endpoint)
                .token(token)
                .build()
        );
    }
    
    @Override
    public void closeConnection(@Nonnull String userId, @Nonnull String guildId) {
        var m = MagmaMember.builder().userId(userId).guildId(guildId).build();
        var map = providers.get(userId);
        if(map != null) {
            var provider = map.remove(guildId);
            if(provider != null) {
                provider.close();
            }
        }
        magma.removeSendHandler(m);
        magma.closeConnection(m);
    }
    
    private static IAudioSendFactory createSendFactory(Andesite andesite) {
        var config = andesite.config();
        IAudioSendFactory factory;
        var hasNas = isNasSupported();
        switch(config.get("send-system.type", hasNas ? "nas" : "nio")) {
            case "nas":
                if(!hasNas) {
                    throw new IllegalArgumentException("NAS is unsupported in this environment. " +
                        "Please choose a different send system.");
                }
                factory = new NativeAudioSendFactory(config.getInt("send-system.nas-buffer", 400));
                break;
            case "jda":
                factory = new JDASendFactory();
                break;
            case "nio":
                factory = new NioSendFactory(andesite.vertx());
                break;
            default:
                throw new IllegalArgumentException("No send system with type " + config.get("send-system.type"));
        }
        if(config.getBoolean("send-system.async", true)) {
            factory = new AsyncPacketProviderFactory(factory);
        }
        log.info("Send system: {}, async provider {}",
            config.get("send-system.type", "nas"),
            config.getBoolean("send-system.async", true) ? "enabled" : "disabled"
        );
        return factory;
    }
    
    private static boolean isNasSupported() {
        try {
            new UdpQueueManager(20, 20_000_000, 4096).close();
            return true;
        } catch(UnsatisfiedLinkError e) {
            return false;
        }
    }
    
    private static ByteArrayProvider createArrayProvider(Config config) {
        ByteArrayProvider provider;
        switch(config.get("magma.array-provider", "create-new")) {
            case "create-new":
                provider = ByteArrayProvider.createNew();
                break;
            case "reuse-existing":
                provider = ByteArrayProvider.reuseExisting(
                    StandardAudioDataFormats.DISCORD_OPUS.maximumChunkSize()
                );
                break;
            default:
                throw new IllegalStateException("No provider " + config.get("magma.array-provider"));
        }
        log.info("Array provider: {}",
            config.get("magma.array-provider", "create-new")
        );
        return provider;
    }
    
    private static class MagmaSendHandler implements AudioSendHandler {
        private final AudioProvider provider;
        private final ByteArrayProvider arrayProvider;
        
        public MagmaSendHandler(@Nonnull AudioProvider provider, @Nonnull ByteArrayProvider arrayProvider) {
            this.provider = provider;
            this.arrayProvider = arrayProvider;
        }
        
        @Override
        public boolean canProvide() {
            return provider.canProvide();
        }
        
        @Nonnull
        @Override
        public byte[] provide20MsAudio() {
            var buffer = provider.provide();
            var pos = buffer.position();
            var array = arrayProvider.provide(buffer.remaining());
            buffer.get(array);
            buffer.position(pos);
            return array;
        }
        
        @Override
        public boolean isOpus() {
            return true;
        }
    }
}
