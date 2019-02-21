package andesite.node.handler;

import andesite.node.Andesite;
import andesite.node.Version;
import andesite.node.util.MemoryBodyHandler;
import andesite.node.util.RequestUtils;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

public class RestHandler {
    private static final Logger log = LoggerFactory.getLogger(RestHandler.class);

    public static boolean setup(@Nonnull Andesite andesite) {
        var config = andesite.config();

        var enableRest = config.getBoolean("transport.http.rest", true);
        var enableWs = config.getBoolean("transport.http.ws", true);
        var enablePrometheus = config.getBoolean("prometheus.enabled", false);

        if(!enableRest && !enableWs && !enablePrometheus && !andesite.pluginManager().requiresRouter()) {
            return false;
        }

        var nodeRegion = config.get("node.region", "unknown");
        var nodeId = config.get("node.id", "unknown");

        var router = Router.router(andesite.vertx());

        //handle failures for all routes
        router.route().failureHandler(context ->
            context.response()
                    .setStatusCode(500)
                    .setStatusMessage("Internal server error")
                    .putHeader("Content-Type", "application/json")
                    .end(RequestUtils.encodeFailure(context).toBuffer())
        );

        //setup headers
        router.route().handler(context -> {
            log.debug("Received request {} {} from {}",
                    context.request().method(),
                    context.normalisedPath(),
                    context.request().remoteAddress()
            );
            context.response().putHeader("Andesite-Version", Version.VERSION);
            context.response().putHeader("Andesite-Version-Major", Version.VERSION_MAJOR);
            context.response().putHeader("Andesite-Version-Minor", Version.VERSION_MINOR);
            context.response().putHeader("Andesite-Version-Revision", Version.VERSION_REVISION);
            context.response().putHeader("Andesite-Version-Commit", Version.COMMIT);
            context.response().putHeader("Andesite-Node-Region", nodeRegion);
            context.response().putHeader("Andesite-Node-Id", nodeId);
            context.response().putHeader("Andesite-Enabled-Sources", String.join(",", andesite.enabledSources()));
            context.response().putHeader("Andesite-Loaded-Plugins", String.join(",", andesite.pluginManager().loadedPlugins()));
            if(context.request().getHeader("upgrade") == null) {
                context.response().putHeader("Content-Type", "application/json");
            }
            context.next();
        });

        router.route().handler(r -> {
            if(andesite.pluginManager().customHandleHttpRequest(r)) {
                return;
            }
            r.next();
        });

        andesite.pluginManager().configureRouter(router);

        if(enablePrometheus) {
            router.get(config.get("prometheus.path", "/metrics")).handler(context -> {
                var writer = new StringWriter();
                try {
                    TextFormat.write004(writer, CollectorRegistry.defaultRegistry.filteredMetricFamilySamples(
                            Set.copyOf(context.queryParam("name[]"))
                    ));
                } catch(IOException e) {
                    context.fail(e);
                    return;
                }
                context.response()
                        .putHeader("Content-Type", TextFormat.CONTENT_TYPE_004)
                        .end(writer.toString());
            });
        }

        //verify authentication
        router.route().handler(context -> {
            var password = andesite.config().get("password");
            if(password != null && !password.equals(RequestUtils.findPassword(context))) {
                error(context, 401, "Unauthorized");
                return;
            }
            context.next();
        });

        if(enableRest) {
            trackRoutes(andesite, router);

            router.get("/stats").handler(context -> context.response().end(
                    andesite.requestHandler().nodeStats().toBuffer()
            ));

            router.get("/stats/lavalink").handler(context -> context.response().end(
                    andesite.requestHandler().nodeStatsForLavalink().toBuffer()
            ));
        }

        //verify user id
        router.route().handler(context -> {
            var id = context.request().getHeader("User-Id");
            if(id == null) {
                id = context.queryParams().get("user-id");
            }
            try {
                context.put("user-id", Long.toUnsignedString(Long.parseUnsignedLong(id)));
            } catch(Exception e) {
                error(context, 400, "Missing or invalid user id");
                return;
            }
            context.next();
        });

        if(enableWs) {
            WebSocketHandler.setup(andesite, router);
        }

        if(enableRest) {
            //read bodies
            router.route().handler(new MemoryBodyHandler(65536)); /* 64KiB max body size */

            router.post("/player/voice-server-update").handler(context -> {
                andesite.requestHandler().provideVoiceServerUpdate(context.get("user-id"), context.getBodyAsJson());
                context.response().setStatusCode(204).setStatusMessage("No content").end();
            });

            router.get("/player/:guild_id").handler(context -> {
                var res = andesite.requestHandler().player(context.get("user-id"), context.pathParam("guild_id"));
                sendResponse(context, res);
            });

            router.post("/player/:guild_id/play").handler(context -> {
                var res = andesite.requestHandler().play(context.get("user-id"), context.pathParam("guild_id"),
                        context.getBodyAsJson());
                sendResponse(context, res);
            });

            router.post("/player/:guild_id/stop").handler(context -> {
                var res = andesite.requestHandler().stop(context.get("user-id"), context.pathParam("guild_id"));
                sendResponse(context, res);
            });

            router.patch("/player/:guild_id/mixer").handler(context -> {
                var res = andesite.requestHandler().mixer(context.get("user-id"), context.pathParam("guild_id"),
                        context.getBodyAsJson());
                sendResponse(context, res);
            });

            router.patch("/player/:guild_id/filters").handler(context -> {
                var res = andesite.requestHandler().filters(context.get("user-id"), context.pathParam("guild_id"),
                        context.getBodyAsJson());
                sendResponse(context, res);
            });

            router.patch("/player/:guild_id/pause").handler(context -> {
                var res = andesite.requestHandler().pause(context.get("user-id"), context.pathParam("guild_id"),
                        context.getBodyAsJson());
                sendResponse(context, res);
            });

            router.patch("/player/:guild_id/seek").handler(context -> {
                var res = andesite.requestHandler().seek(context.get("user-id"), context.pathParam("guild_id"),
                        context.getBodyAsJson());
                sendResponse(context, res);
            });

            router.patch("/player/:guild_id/volume").handler(context -> {
                var res = andesite.requestHandler().volume(context.get("user-id"), context.pathParam("guild_id"),
                        context.getBodyAsJson());
                sendResponse(context, res);
            });

            router.patch("/player/:guild_id").handler(context -> {
                var res = andesite.requestHandler().update(context.get("user-id"), context.pathParam("guild_id"),
                        context.getBodyAsJson());
                sendResponse(context, res);
            });

            router.delete("/player/:guild_id").handler(context -> {
                var res = andesite.requestHandler().destroy(context.get("user-id"), context.pathParam("guild_id"));
                sendResponse(context, res);
            });
        }

        router.route().handler(context -> error(context, 404, "Not found"));

        var port = andesite.config().getInt("transport.http.port", 5000);

        log.info("Starting HTTP server on port {}", port);

        var latch = new CountDownLatch(1);
        andesite.vertx().createHttpServer()
                .requestHandler(router)
                .listen(port, result -> {
                    if(result.failed()) {
                        log.error("Error starting HTTP server", result.cause());
                        System.exit(-1);
                    } else {
                        log.info("HTTP server started successfully");
                        latch.countDown();
                    }
                });
        try {
            latch.await();
        } catch(InterruptedException e) {
            throw new AssertionError(e);
        }

        return true;
    }

    private static void sendResponse(@Nonnull RoutingContext context, @Nullable JsonObject response) {
        if(response == null) {
            error(context, 404, "Player not found");
            return;
        }
        context.response().end(response.toBuffer());
    }

    private static void trackRoutes(@Nonnull Andesite andesite, @Nonnull Router router) {
        router.get("/loadtracks").handler(context -> {
            var identifiers = context.queryParam("identifier");
            if(identifiers == null || identifiers.isEmpty()) {
                error(context, 400, "Missing identifier query param");
                return;
            }
            var identifier = identifiers.get(0);
            log.debug("Resolving tracks for {}", identifier);
            andesite.requestHandler().resolveTracks(identifier)
                    .thenAccept(json -> context.response().end(json.toBuffer()))
                    .exceptionally(e -> {
                        if(e instanceof FriendlyException) {
                            context.response().end(
                                    new JsonObject()
                                            .put("loadType", "LOAD_FAILED")
                                            .put("cause", RequestUtils.encodeThrowable(context, e))
                                            .put("severity", ((FriendlyException)e).severity.name())
                                            .toBuffer()
                            );
                        } else {
                            context.fail(e);
                        }
                        return null;
                    });
        });

        router.get("/decodetrack").handler(context -> {
            var encoded = context.queryParam("track");
            if(encoded == null || encoded.isEmpty()) {
                error(context, 400, "Missing track query param");
                return;
            }
            context.response().end(trackInfo(andesite, encoded.get(0)).toBuffer());
        });

        router.post("/decodetrack").handler(context -> {
            var encoded = context.getBodyAsJson().getString("track");
            if(encoded == null) {
                error(context, 400, "Missing track json field");
                return;
            }
            context.response().end(trackInfo(andesite, encoded).toBuffer());
        });

        router.post("/decodetracks").handler(context -> {
            var encoded = context.getBodyAsJsonArray();
            var response = new JsonArray();
            encoded.forEach(v -> response.add(trackInfo(andesite, (String)v)));
            context.response().end(response.toBuffer());
        });
    }

    @Nonnull
    @CheckReturnValue
    private static JsonObject trackInfo(@Nonnull Andesite andesite, @Nonnull String encodedTrack) {
        var track = RequestUtils.decodeTrack(andesite.audioPlayerManager(), encodedTrack);
        return RequestUtils.encodeTrack(andesite.audioPlayerManager(), track);
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
