package andesite.handler;

import andesite.Andesite;
import andesite.NodeState;
import andesite.util.MemoryBodyHandler;
import andesite.util.RequestUtils;
import andesite.util.metadata.NamePartJoiner;
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
        var config = andesite.config().getConfig("andesite");
    
        var enableRest = config.getBoolean("transport.http.rest");
        var enableWs = config.getBoolean("transport.http.ws");
        var enablePrometheus = config.getBoolean("prometheus.enabled");
        
        if(!enableRest && !enableWs && !enablePrometheus && !andesite.pluginManager().requiresRouter()) {
            return false;
        }
        
        var router = Router.router(andesite.vertx());
        
        //handle failures for all routes
        router.route().failureHandler(context -> {
            log.error("Error in HTTP handler", context.failure());
            context.response()
                    .setStatusCode(500)
                    .setStatusMessage("Internal server error")
                    .putHeader("Content-Type", "application/json")
                    .end(RequestUtils.encodeFailure(context).toBuffer());
        });
        
        //setup headers
        router.route().handler(context -> {
            log.debug("Received request {} {} from {}",
                    context.request().method(),
                    context.normalisedPath(),
                    context.request().remoteAddress()
            );
            andesite.requestHandler().metadataFields(NamePartJoiner.HTTP_HEADER).forEach((k, v) ->
                    context.response().putHeader("Andesite-" + k, v.toString())
            );
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
            router.get(config.getString("prometheus.path")).handler(context -> {
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
            var password = config.hasPath("password") ? config.getString("password") : null;
            if(password != null && !password.equals(RequestUtils.findPassword(context))) {
                error(context, 401, "Unauthorized");
                return;
            }
            context.next();
        });
        
        //read bodies
        router.route().handler(new MemoryBodyHandler(65536)); /* 64KiB max body size */
        
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
    
        var address = config.getString("transport.http.bind-address");
        var port = config.getInt("transport.http.port");
    
        log.info("Starting HTTP server on port {}:{}", address, port);
        
        var latch = new CountDownLatch(1);
        andesite.vertx().createHttpServer()
                .requestHandler(router)
                .listen(port, address, result -> {
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
    
    private static void trackRoutes(@Nonnull NodeState state, @Nonnull Router router) {
        router.get("/loadtracks").handler(context -> {
            var identifiers = context.queryParam("identifier");
            if(identifiers == null || identifiers.isEmpty()) {
                error(context, 400, "Missing identifier query param");
                return;
            }
            var identifier = identifiers.get(0);
            log.debug("Resolving tracks for {}", identifier);
            state.requestHandler().resolveTracks(identifier)
                    .thenAccept(json -> context.response().end(json.toBuffer()))
                    .exceptionally(e -> {
                        if(e.getCause() instanceof FriendlyException) {
                            context.response().end(
                                    new JsonObject()
                                            .put("loadType", "LOAD_FAILED")
                                            .put("cause", RequestUtils.encodeThrowable(context, e.getCause()))
                                            .put("severity", ((FriendlyException) e.getCause()).severity.name())
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
            var track = trackInfo(state, encoded.get(0));
            if(track == null) {
                error(context, 400, "Unable to decode track. Is the source manager enabled?");
                return;
            }
            context.response().end(track.toBuffer());
        });
        
        router.post("/decodetrack").handler(context -> {
            var encoded = context.getBodyAsJson().getString("track");
            if(encoded == null) {
                error(context, 400, "Missing track json field");
                return;
            }
            var track = trackInfo(state, encoded);
            if(track == null) {
                error(context, 400, "Unable to decode track. Is the source manager enabled?");
                return;
            }
            context.response().end(track.toBuffer());
        });
        
        router.post("/decodetracks").handler(context -> {
            var encoded = context.getBodyAsJsonArray();
            var response = new JsonArray();
            encoded.forEach(v -> {
                var track = trackInfo(state, (String) v);
                if(track == null) {
                    track = new JsonObject()
                            .put("error", "Unable to decode track. Is the source manager enabled?");
                }
                response.add(track);
            });
            context.response().end(response.toBuffer());
        });
    }
    
    @Nullable
    @CheckReturnValue
    private static JsonObject trackInfo(@Nonnull NodeState state, @Nonnull String encodedTrack) {
        var track = RequestUtils.decodeTrack(state.audioPlayerManager(), encodedTrack);
        if(track == null) {
            return null;
        }
        return RequestUtils.encodeTrack(state.audioPlayerManager(), track);
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
