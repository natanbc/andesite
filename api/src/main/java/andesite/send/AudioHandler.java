package andesite.send;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface AudioHandler {
    /**
     * Sets the provider for the given user and guild. A null provider will remove
     * the existing provider, if it exists.
     *
     * <br>If there is already a provider set, it's {@link AudioProvider#close() close}
     * method will be called.
     *
     * @param userId   User id for the provider.
     * @param guildId  Guild id for the provider.
     * @param provider Provider to set.
     */
    void setProvider(@Nonnull String userId, @Nonnull String guildId, @Nullable AudioProvider provider);
    
    /**
     * Handles a voice update.
     *
     * @param userId    User id for the update.
     * @param guildId   Guild id for the update.
     * @param sessionId Session id of the voice state.
     * @param endpoint  Endpoint of the voice server.
     * @param token     Token of the voice connection.
     */
    void handleVoiceUpdate(@Nonnull String userId, @Nonnull String guildId,
                           @Nonnull String sessionId, @Nonnull String endpoint,
                           @Nonnull String token);
    
    /**
     * Closes a connection for the given user and guild. If the connection
     * has a provider set, it's {@link AudioProvider#close() close} method
     * will be called.
     *
     * @param userId  User id of the connection.
     * @param guildId Guild id of the connection.
     */
    void closeConnection(@Nonnull String userId, @Nonnull String guildId);
}
