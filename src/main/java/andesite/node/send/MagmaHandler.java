package andesite.node.send;

import andesite.node.Andesite;
import net.dv8tion.jda.core.audio.AudioSendHandler;
import net.dv8tion.jda.core.audio.factory.IAudioSendFactory;
import space.npstr.magma.MagmaApi;
import space.npstr.magma.MagmaMember;
import space.npstr.magma.MagmaServerUpdate;
import space.npstr.magma.events.api.WebSocketClosed;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

public class MagmaHandler implements AudioHandler {
    private final MagmaApi magma;

    public MagmaHandler(Andesite andesite, IAudioSendFactory factory) {
        this.magma = MagmaApi.of(__ -> factory);
        magma.getEventStream().subscribe(event -> {
            if(event instanceof WebSocketClosed) {
                var e = (WebSocketClosed)event;
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
        magma.setSendHandler(
                MagmaMember.builder()
                        .userId(userId)
                        .guildId(guildId)
                        .build(),
                provider == null ? null : new MagmaSendHandler(provider)
        );
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
        magma.removeSendHandler(m);
        magma.closeConnection(m);
    }

    private static class MagmaSendHandler implements AudioSendHandler {
        private final AudioProvider provider;

        public MagmaSendHandler(@Nonnull AudioProvider provider) {
            this.provider = Objects.requireNonNull(provider, "Provider may not be null");
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
            var array = new byte[buffer.remaining()];
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
