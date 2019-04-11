package andesite.node.handler;

import andesite.node.util.metadata.MetadataEntry;
import andesite.node.util.metadata.NamePartJoiner;
import io.vertx.core.json.JsonObject;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public interface AndesiteRequestHandler {
    /**
     * Returns a map of the metadata fields to their values. The keys
     * will be formatted based on the provided {@link NamePartJoiner}.
     *
     * @param joiner Formatter for the map keys.
     *
     * @return The metadata map.
     */
    @Nonnull
    @CheckReturnValue
    Map<String, MetadataEntry> metadataFields(@Nonnull NamePartJoiner joiner);
    
    /**
     * Provides a voice server update. The provided json object must be a valid
     * voice update payload.
     *
     * @param userId User id for the payload.
     * @param json   Voice update payload.
     */
    void provideVoiceServerUpdate(@Nonnull String userId, @Nonnull JsonObject json);
    
    /**
     * Returns the encoded player, if it exists.
     *
     * @param userId  User id of the player.
     * @param guildId Guild id of the player.
     *
     * @return The encoded player, or null if it doesn't exist.
     */
    @Nullable
    JsonObject player(@Nonnull String userId, @Nonnull String guildId);
    
    /**
     * Subscribes a provided sink to receive events for the player.
     * The sink should forward the events to clients.
     *
     * @param userId    User id of the player.
     * @param guildId   Guild id of the player.
     * @param key       Unique key identifying this consumer. Anything works, as long as it's unique.
     * @param eventSink Sink for events.
     */
    void subscribe(@Nonnull String userId, @Nonnull String guildId,
                   @Nonnull Object key, @Nonnull Consumer<JsonObject> eventSink);
    
    /**
     * Handles a play payload. Returns the player state.
     *
     * @param userId  User id of the player.
     * @param guildId Guild id of the player.
     * @param payload Payload to handle.
     *
     * @return The player state.
     */
    @Nonnull
    JsonObject play(@Nonnull String userId, @Nonnull String guildId, @Nonnull JsonObject payload);
    
    /**
     * Handles a mixer payload. Returns the player state.
     *
     * @param userId  User id of the player.
     * @param guildId Guild id of the player.
     * @param payload Payload to handle.
     *
     * @return The player state.
     */
    @Nonnull
    JsonObject mixer(@Nonnull String userId, @Nonnull String guildId, @Nonnull JsonObject payload);
    
    /**
     * Handles a stop request. Returns the player state.
     *
     * @param userId  User id of the player.
     * @param guildId Guild id of the player.
     *
     * @return The player state.
     */
    @Nonnull
    JsonObject stop(@Nonnull String userId, @Nonnull String guildId);
    
    /**
     * Handles a pause payload. Returns the player state.
     *
     * @param userId  User id of the player.
     * @param guildId Guild id of the player.
     * @param payload Payload to handle.
     *
     * @return The player state.
     */
    @Nonnull
    JsonObject pause(@Nonnull String userId, @Nonnull String guildId, @Nonnull JsonObject payload);
    
    /**
     * Handles a seek payload. Returns the player state.
     *
     * @param userId  User id of the player.
     * @param guildId Guild id of the player.
     * @param payload Payload to handle.
     *
     * @return The player state.
     */
    @Nonnull
    JsonObject seek(@Nonnull String userId, @Nonnull String guildId, @Nonnull JsonObject payload);
    
    /**
     * Handles a volume payload. Returns the player state.
     *
     * @param userId  User id of the player.
     * @param guildId Guild id of the player.
     * @param payload Payload to handle.
     *
     * @return The player state.
     */
    @Nonnull
    JsonObject volume(@Nonnull String userId, @Nonnull String guildId, @Nonnull JsonObject payload);
    
    /**
     * Handles a filters payload. Returns the player state.
     *
     * @param userId  User id of the player.
     * @param guildId Guild id of the player.
     * @param payload Payload to handle.
     *
     * @return The player state.
     */
    @Nonnull
    JsonObject filters(@Nonnull String userId, @Nonnull String guildId, @Nonnull JsonObject payload);
    
    /**
     * Handles an update payload. Returns the player state.
     *
     * @param userId  User id of the player.
     * @param guildId Guild id of the player.
     * @param payload Payload to handle.
     *
     * @return The player state.
     */
    @Nonnull
    JsonObject update(@Nonnull String userId, @Nonnull String guildId, @Nonnull JsonObject payload);
    
    /**
     * Handles a destroy request. Returns the player state.
     *
     * @param userId  User id of the player.
     * @param guildId Guild id of the player.
     *
     * @return The player state, or null if it doesn't exist.
     */
    @Nullable
    JsonObject destroy(@Nonnull String userId, @Nonnull String guildId);
    
    /**
     * Resolves tracks with the provided identifier.
     *
     * <br>If the identifier is a string, starts with {@code ytsearch:} or {@code scsearch:}, it's
     * used as-is for loading.
     * <br>If it starts with {@code raw:}, the first 4 characters (raw:) are removed and the rest
     * is used for loading.
     * <br>Otherwise, the identifier is prepended with {@code ytsearch:} and used for loading.
     *
     * @param identifier Identifier to load.
     *
     * @return The result of the track lookup.
     */
    @Nonnull
    @CheckReturnValue
    CompletionStage<JsonObject> resolveTracks(@Nonnull String identifier);
    
    /**
     * Returns the stats of the node.
     *
     * @return The stats of the node.
     */
    @Nonnull
    @CheckReturnValue
    JsonObject nodeStats();
    
    /**
     * Returns lavalink compatible stats of the node.
     *
     * @return Lavalink compatible stats of the node.
     */
    @Nonnull
    @CheckReturnValue
    JsonObject nodeStatsForLavalink();
}
