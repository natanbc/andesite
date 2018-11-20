package andesite.node.player;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;

public class EmitterReference {
    private final Player player;
    private final String key;
    private final EventEmitter emitter;

    public EmitterReference(@Nonnull Player player, @Nonnull String key, @Nonnull EventEmitter emitter) {
        this.player = player;
        this.key = key;
        this.emitter = emitter;
    }

    @Nonnull
    @CheckReturnValue
    public Player player() {
        return player;
    }

    @Nonnull
    @CheckReturnValue
    public String key() {
        return key;
    }

    @Nonnull
    @CheckReturnValue
    public EventEmitter emitter() {
        return emitter;
    }

    public void remove() {
        player.eventListeners().remove(key, emitter);
    }
}
