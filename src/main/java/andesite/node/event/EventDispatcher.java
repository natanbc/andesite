package andesite.node.event;

import andesite.node.player.Player;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class EventDispatcher {
    private final Set<AndesiteEventListener> listeners = ConcurrentHashMap.newKeySet();

    public void register(AndesiteEventListener listener) {
        listeners.add(listener);
    }

    public void unregister(AndesiteEventListener listener) {
        listeners.remove(listener);
    }

    public void onPlayerCreated(@Nonnull String userId, @Nonnull String guildId,
                                @Nonnull Player player) {
        listeners.forEach(l -> l.onPlayerCreated(userId, guildId, player));
    }

    public void onPlayerDestroyed(@Nonnull String userId, @Nonnull String guildId,
                                @Nonnull Player player) {
        listeners.forEach(l -> l.onPlayerDestroyed(userId, guildId, player));
    }

    public void onWebSocketClosed(@Nonnull String userId, @Nonnull String guildId,
                                  @Nonnegative int closeCode, @Nullable String reason, boolean byRemote) {
        listeners.forEach(l -> l.onWebSocketClosed(userId, guildId, closeCode, reason, byRemote));
    }
}
