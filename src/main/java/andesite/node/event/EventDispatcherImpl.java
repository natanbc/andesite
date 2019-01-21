package andesite.node.event;

import andesite.node.NodeState;
import andesite.node.player.AndesitePlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class EventDispatcherImpl implements EventDispatcher {
    private static final Logger log = LoggerFactory.getLogger(EventDispatcher.class);

    private final Set<AndesiteEventListener> listeners = ConcurrentHashMap.newKeySet();
    private final NodeState state;

    public EventDispatcherImpl(@Nonnull NodeState state) {
        this.state = state;
    }

    @Override
    public void register(@Nonnull AndesiteEventListener listener) {
        listeners.add(listener);
    }

    @Override
    public void unregister(@Nonnull AndesiteEventListener listener) {
        listeners.remove(listener);
    }

    public void onPlayerCreated(@Nonnull String userId, @Nonnull String guildId,
                                @Nonnull AndesitePlayer player) {
        forEach(l -> l.onPlayerCreated(state, userId, guildId, player));
    }

    public void onPlayerDestroyed(@Nonnull String userId, @Nonnull String guildId,
                                  @Nonnull AndesitePlayer player) {
        forEach(l -> l.onPlayerDestroyed(state, userId, guildId, player));
    }

    public void onWebSocketClosed(@Nonnull String userId, @Nonnull String guildId,
                                  @Nonnegative int closeCode, @Nullable String reason, boolean byRemote) {
        forEach(l -> l.onWebSocketClosed(state, userId, guildId, closeCode, reason, byRemote));
    }

    private void forEach(Consumer<AndesiteEventListener> action) {
        for(var v : listeners) {
            try {
                action.accept(v);
            } catch(Throwable t) {
                log.error("Error dispatching event to {}: ", v, t);
            }
        }
    }
}
