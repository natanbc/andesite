package andesite.node;

import andesite.node.config.Config;
import andesite.node.event.EventDispatcher;
import andesite.node.player.AndesitePlayer;
import andesite.node.send.AudioHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import io.vertx.core.Vertx;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.ref.Cleaner;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Provides access to andesite objects for plugins.
 */
public interface NodeState {
    /**
     * Config instance used by the node. Plugins should read configurations from this object,
     * which handles reading from the sources enabled by the user.
     *
     * @return The node configuration.
     */
    @Nonnull
    @CheckReturnValue
    Config config();

    /**
     * Vertx instance used by the node.
     *
     * @return The vertx instance.
     */
    @Nonnull
    @CheckReturnValue
    Vertx vertx();

    /**
     * The regular player manager, which returns opus data.
     *
     * @return The regular player manager.
     */
    @Nonnull
    @CheckReturnValue
    AudioPlayerManager audioPlayerManager();

    /**
     * The PCM player manager, which returns pcm data. Used by the track mixer for better performance.
     *
     * @return The PCM player manager.
     */
    @Nonnull
    @CheckReturnValue
    AudioPlayerManager pcmAudioPlayerManager();

    /**
     * The event dispatcher used by the node. You can use this to dynamically register/unregister listeners.
     *
     * @return The event dispatcher.
     */
    @Nonnull
    @CheckReturnValue
    EventDispatcher dispatcher();

    /**
     * The audio handler used by the node. Allows lower level configuration of the player.
     *
     * @return The audio handler.
     */
    @Nonnull
    @CheckReturnValue
    AudioHandler audioHandler();

    /**
     * Returns a map of the players owned by the provided user. This map is thread safe and will
     * be updated when players are created/destroyed.
     *
     * @param userId User id that owns the map.
     *
     * @return The map of guild id -> player for the provided user.
     *
     * @see AndesitePlayer
     */
    @Nonnull
    @CheckReturnValue
    Map<String, ? extends AndesitePlayer> playerMap(@Nonnull String userId);

    /**
     * Gets or creates a player for the provided user and guild. Equivalent to
     * {@code playerMap(userId).computeIfAbsent(guildId, createPlayer);}.
     *
     * @param userId User id for the player.
     * @param guildId Guild id for the player.
     *
     * @return The current player, if it exists, or a new player.
     *
     * @see AndesitePlayer
     */
    @Nonnull
    @CheckReturnValue
    AndesitePlayer getPlayer(@Nonnull String userId, @Nonnull String guildId);

    /**
     * Returns the existing player for the provided user and guild, or null if it doesn't exist.
     * Equivalent to {@code playerMap(userId).get(guildId)}.
     *
     * @param userId User id for the player.
     * @param guildId Guild id for the player.
     *
     * @return The current player.
     *
     * @see AndesitePlayer
     */
    @Nullable
    @CheckReturnValue
    AndesitePlayer getExistingPlayer(@Nonnull String userId, @Nonnull String guildId);

    /**
     * Removes and stops the player for the provided user and guild, returning it.
     * Returns null if there was no player.
     *
     * @param userId User id for the player.
     * @param guildId Guild id for the player.
     *
     * @return The removed player.
     *
     * @see AndesitePlayer
     */
    @Nullable
    AndesitePlayer removePlayer(@Nonnull String userId, @Nonnull String guildId);

    /**
     * Stream containing all existing players, for all users and guilds.
     *
     * @return All existing players.
     *
     * @see AndesitePlayer
     */
    @Nonnull
    @CheckReturnValue
    Stream<? extends AndesitePlayer> allPlayers();

    /**
     * Cleaner used to register actions on garbage collection of objects.
     * Plugins should use this cleaner instead of creating their own.
     *
     * @return The cleaner instance for the node.
     *
     * @see Cleaner
     */
    @Nonnull
    @CheckReturnValue
    Cleaner cleaner();
}
