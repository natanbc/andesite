package andesite.node.send;

import andesite.node.Andesite;
import andesite.node.send.jdaa.JDASendFactory;
import andesite.node.send.nio.NioSendFactory;
import com.sedmelluq.discord.lavaplayer.jdaudp.NativeAudioSendFactory;
import com.sedmelluq.discord.lavaplayer.udpqueue.natives.UdpQueueManager;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.audio.factory.IAudioSendFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.npstr.magma.MagmaApi;
import space.npstr.magma.MagmaMember;
import space.npstr.magma.MagmaServerUpdate;
import space.npstr.magma.events.api.WebSocketClosed;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MagmaHandler implements AudioHandler {
    private static final Logger log = LoggerFactory.getLogger(MagmaHandler.class);
    
    private final Map<Long, Map<Long, AudioProvider>> providers = new ConcurrentHashMap<>();
    private final MagmaApi magma;
    
    public MagmaHandler(Andesite andesite) {
        var factory = createSendFactory(andesite);
        this.magma = MagmaApi.of(__ -> factory);
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
        var map = providers.computeIfAbsent(Long.parseUnsignedLong(userId), __ -> new ConcurrentHashMap<>());
        AudioProvider old;
        if(provider == null) {
            old = map.remove(Long.parseUnsignedLong(guildId));
        } else {
            old = map.put(Long.parseUnsignedLong(guildId), provider);
        }
        if(old == provider) {
            return;
        }
        magma.setSendHandler(
                MagmaMember.builder()
                        .userId(userId)
                        .guildId(guildId)
                        .build(),
                provider == null ? null : new MagmaSendHandler(provider)
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
        var map = providers.get(Long.parseUnsignedLong(userId));
        if(map != null) {
            var provider = map.remove(Long.parseUnsignedLong(guildId));
            if(provider != null) {
                provider.close();
            }
        }
        magma.removeSendHandler(m);
        magma.closeConnection(m);
    }
    
    private static IAudioSendFactory createSendFactory(Andesite andesite) {
        var config = andesite.config().getConfig("andesite.magma");
        IAudioSendFactory factory;
        var hasNas = isNasSupported();
        var hasConfig = config.hasPath("send-system.type");
        var sendSystem = hasConfig ? config.getString("send-system.type") : hasNas ? "nas" : "nio";
        switch(sendSystem) {
            case "nas":
                if(!hasNas) {
                    throw new IllegalArgumentException("NAS is unsupported in this environment. " +
                            "Please choose a different send system.");
                }
                factory = new NativeAudioSendFactory(config.getInt("send-system.nas-buffer"));
                break;
            case "jda":
                factory = new JDASendFactory();
                break;
            case "nio":
                factory = new NioSendFactory(andesite.vertx());
                break;
            default:
                throw new IllegalArgumentException("No send system with type " + config.getString("send-system.type"));
        }
//        if(config.getBoolean("send-system.async")) {
//            factory = new AsyncPacketProviderFactory(factory);
//        }
        log.info("Send system: {}",
                sendSystem
//                config.getBoolean("send-system.async") ? "enabled" : "disabled"
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
    
    private static class MagmaSendHandler implements AudioSendHandler {
        private final AudioProvider provider;
    
        MagmaSendHandler(@Nonnull AudioProvider provider) {
            this.provider = provider;
        }
        
        @Override
        public boolean canProvide() {
            return provider.canProvide();
        }
        
        @Nonnull
        @Override
        public ByteBuffer provide20MsAudio() {
            return provider.provide();
        }
        
        @Override
        public boolean isOpus() {
            return true;
        }
    }
}
