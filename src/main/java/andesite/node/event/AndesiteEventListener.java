package andesite.node.event;

import andesite.node.player.Player;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface AndesiteEventListener {
    default void onPlayerCreated(@Nonnull String userId, @Nonnull String guildId,
                                @Nonnull Player player) {}

    default void onPlayerDestroyed(@Nonnull String userId, @Nonnull String guildId,
                                   @Nonnull Player player) {}

    default void onWebSocketClosed(@Nonnull String userId, @Nonnull String guildId,
                                   @Nonnegative int closeCode, @Nullable String reason, boolean byRemote) {}
}
