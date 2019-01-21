package andesite.node;

import andesite.node.config.Config;
import andesite.node.event.EventDispatcher;
import andesite.node.player.AndesitePlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import io.vertx.core.Vertx;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.stream.Stream;

public interface NodeState {
    @Nonnull
    @CheckReturnValue
    Config config();

    @Nonnull
    @CheckReturnValue
    Vertx vertx();

    @Nonnull
    @CheckReturnValue
    AudioPlayerManager audioPlayerManager();

    @Nonnull
    @CheckReturnValue
    AudioPlayerManager pcmAudioPlayerManager();

    @Nonnull
    @CheckReturnValue
    EventDispatcher dispatcher();

    @Nonnull
    @CheckReturnValue
    Map<String, ? extends AndesitePlayer> playerMap(@Nonnull String userId);

    @Nonnull
    @CheckReturnValue
    AndesitePlayer getPlayer(@Nonnull String userId, @Nonnull String guildId);

    @Nullable
    @CheckReturnValue
    AndesitePlayer getExistingPlayer(@Nonnull String userId, @Nonnull String guildId);

    @Nullable
    AndesitePlayer removePlayer(@Nonnull String userId, @Nonnull String guildId);

    @Nonnull
    @CheckReturnValue
    Stream<? extends AndesitePlayer> allPlayers();
}
