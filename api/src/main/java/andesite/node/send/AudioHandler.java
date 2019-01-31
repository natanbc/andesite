package andesite.node.send;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface AudioHandler {
    void setProvider(@Nonnull String userId, @Nonnull String guildId, @Nullable AudioProvider provider);

    void handleVoiceUpdate(@Nonnull String userId, @Nonnull String guildId,
                           @Nonnull String sessionId, @Nonnull String endpoint,
                           @Nonnull String token);

    void closeConnection(@Nonnull String userId, @Nonnull String guildId);
}
