package andesite.event;

import io.vertx.core.json.JsonObject;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public class EventBuffer {
    private final Queue<JsonObject> queue = new ConcurrentLinkedQueue<>();
    
    public void empty(Consumer<JsonObject> sink) {
        queue.forEach(sink);
    }
    
    public void offer(JsonObject payload) {
        queue.offer(payload);
    }
}
