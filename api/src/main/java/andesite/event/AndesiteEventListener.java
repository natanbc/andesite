package andesite.event;

import andesite.NodeState;
import andesite.player.AndesitePlayer;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface AndesiteEventListener {
    default void onPlayerCreated(@Nonnull NodeState state, @Nonnull String userId,
                                 @Nonnull String guildId, @Nonnull AndesitePlayer player) {}
    
    default void onPlayerDestroyed(@Nonnull NodeState state, @Nonnull String userId,
                                   @Nonnull String guildId, @Nonnull AndesitePlayer player) {}
    
    default void onWebSocketClosed(@Nonnull NodeState state, @Nonnull String userId,
                                   @Nonnull String guildId, @Nonnegative int closeCode,
                                   @Nullable String reason, boolean byRemote) {}
}
