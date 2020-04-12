package andesite.node.util;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;

public class MemoryBodyHandler implements Handler<RoutingContext> {
    private static final String BODY_HANDLED = "__body-handled";

    private final int maxSize;

    public MemoryBodyHandler(int maxSize) {
        this.maxSize = maxSize;
    }

    @Override
    public void handle(RoutingContext context) {
        HttpServerRequest request = context.request();
        if (request.headers().contains(HttpHeaders.UPGRADE, HttpHeaders.WEBSOCKET, true)) {
            context.next();
            return;
        }
        if (context.get(BODY_HANDLED) != null) {
            context.next();
        } else {
            ReadHandler h = new ReadHandler(context, maxSize);
            request.handler(h);
            request.endHandler(__ -> h.onEnd());
            context.put(BODY_HANDLED, true);
        }
    }

    private static class ReadHandler implements Handler<Buffer> {
        private final Buffer buffer = Buffer.buffer();
        private final RoutingContext context;
        private final int maxSize;

        private ReadHandler(RoutingContext context, int maxSize) {
            this.context = context;
            this.maxSize = maxSize;
        }

        @Override
        public void handle(Buffer data) {
            if (maxSize != -1 && buffer.length() + data.length() > maxSize) {
                context.fail(413);
                return;
            }
            buffer.appendBuffer(data);
        }

        void onEnd() {
            context.setBody(buffer);
            context.next();
        }
    }
}
