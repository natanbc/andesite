package andesite.node;

import andesite.node.util.RequestUtils;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jdk.jfr.FlightRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

public class JFRDebug implements Plugin {
    private static final Logger log = LoggerFactory.getLogger(JFRDebug.class);

    @Override
    public boolean requiresRouter() {
        return true;
    }

    @Override
    public void configureRouter(@Nonnull NodeState state, @Nonnull Router router) {
        var r = Router.router(state.vertx());

        r.route().handler(c -> {
            var config = state.config();
            var password = config.get("debug-password");
            if(password == null) {
                password = config.get("password");
            }
            if(password != null && !password.equals(c.request().getHeader("Authorization"))) {
                error(c, 401, "Unauthorized");
                return;
            }
            c.next();
        });

        r.get("/state").handler(c -> {
            var recording = JFRState.current();
            var response = new JsonObject().put("recording", recording != null);
            if(recording != null) {
                var dest = recording.getDestination();
                var settings = new JsonObject();
                recording.getSettings().forEach(settings::put);
                response.put("maxSize", recording.getMaxSize())
                        .put("size", recording.getSize())
                        .put("id", recording.getId())
                        .put("duration", Instant.now()
                                .minusMillis(recording.getStartTime().toEpochMilli()).toEpochMilli())
                        .put("name", recording.getName())
                        .put("settings", settings)
                        .put("destination", dest == null ? null : dest.toAbsolutePath().toString());
            }
            c.response()
                    .putHeader("Content-Type", "application/json")
                    .end(response.toBuffer());
        });

        r.get("/events").handler(c -> {
            var array = new JsonArray();
            for(var event : FlightRecorder.getFlightRecorder().getEventTypes()) {
                array.add(new JsonObject()
                        .put("name", event.getName())
                        .put("label", event.getLabel())
                        .put("description", event.getDescription())
                        .put("id", event.getId()));
            }
            c.response()
                    .putHeader("Content-Type", "application/json")
                    .end(array.toBuffer());
        });

        r.post("/start").handler(c -> {
            var events = c.queryParams().get("events");
            if(events == null) {
                error(c, 400, "Missing events");
                return;
            }
            var recording = JFRState.createNew();
            if(recording == null) {
                error(c, 400, "Recording already started");
                return;
            }
            log.info("Starting JFR recording");
            if(events.equalsIgnoreCase("all")) {
                for(var t : FlightRecorder.getFlightRecorder().getEventTypes()) {
                    recording.enable(t.getName());
                }
            } else {
                for(var t : events.split(",")) {
                    recording.enable(t.strip());
                }
            }
            var dest = c.queryParams().get("destination");
            if(dest != null) {
                try {
                    recording.setDestination(Path.of(dest));
                    recording.setToDisk(true);
                    var maxSize = c.queryParams().get("maxSize");
                    if(maxSize != null) {
                        recording.setMaxSize(Long.parseLong(maxSize));
                    }
                } catch(Exception e) {
                    recording.close();
                    c.response()
                            .setStatusCode(500)
                            .setStatusMessage("Error configuring recorder")
                            .putHeader("Content-Type", "application/json")
                            .end(RequestUtils.encodeThrowable(c, e).toBuffer());
                    return;
                }
            }
            recording.start();
            c.response().setStatusCode(204).end();
        });

        r.post("/stop").handler(c -> {
            var recording = JFRState.stop();
            if(recording == null) {
                error(c, 400, "Recording not started");
                return;
            }
            log.info("Stopping JFR recording");
            var path = c.queryParams().get("path");
            if(path == null || recording.getSize() == 0) {
                recording.close();
                c.response().setStatusCode(204).end();
            } else {
                var p = Path.of(path).toAbsolutePath();
                log.info("Dumping recording to {}", p);
                try {
                    recording.dump(p);
                    c.response().setStatusCode(204).end();
                } catch(IOException e) {
                    c.response()
                            .setStatusCode(500)
                            .setStatusMessage("Error writing response")
                            .putHeader("Content-Type", "application/json")
                            .end(RequestUtils.encodeThrowable(c, e).toBuffer());
                }
            }
        });

        router.mountSubRouter("/jfr", r);
    }

    private static void error(@Nonnull RoutingContext context, @Nonnegative int code, @Nonnull String message) {
        context.response()
                .setStatusCode(code).setStatusMessage(message)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject()
                        .put("code", code)
                        .put("message", message)
                        .toBuffer()
                );
    }
}
