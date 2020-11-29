package andesite.event;

import andesite.player.Player;
import io.vertx.core.json.JsonObject;

import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public class EventBuffer {
    private final Queue<JsonObject> queue = new ConcurrentLinkedQueue<>();
    private final Set<Player> subscriptions;
    
    public EventBuffer(Set<Player> subscriptions) {
        this.subscriptions = subscriptions;
    }
    
    public Set<Player> subscriptions() {
        return subscriptions;
    }
    
    public void empty(Consumer<JsonObject> sink) {
        queue.forEach(sink);
    }
    
    public void offer(JsonObject payload) {
        queue.offer(payload);
    }
}
