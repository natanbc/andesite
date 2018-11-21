package andesite.node.handler;

import andesite.node.Andesite;
import andesite.node.event.AndesiteEventListener;
import andesite.node.player.EmitterReference;
import io.vertx.core.Handler;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.WebSocketFrame;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class WebSocketHandler {
    public static void setup(@Nonnull Andesite andesite, @Nonnull Router router) {
        router.get("/websocket").handler(handler(andesite, false));
        router.get(
                andesite.config().get("lavalink.ws-path", "/lavalink")
        ).handler(handler(andesite, true));
    }

    @Nonnull
    @CheckReturnValue
    private static Handler<RoutingContext> handler(@Nonnull Andesite andesite, boolean lavalinkRoute) {
        return context -> {
            var req = context.request();
            if("websocket".equalsIgnoreCase(req.getHeader("upgrade"))) {
                var ws = req.upgrade();
                var userId = context.<String>get("user-id");
                var llQuery = context.queryParam("lavalink");
                var lavalinkConnection = lavalinkRoute
                        || "lavalink".equalsIgnoreCase(req.getHeader("Andesite-Compat"))
                        || llQuery != null && !llQuery.isEmpty();
                ws.frameHandler(new FrameHandler(andesite, userId, ws, lavalinkConnection));
            } else {
                context.next();
            }
        };
    }

    private static class FrameHandler implements Handler<WebSocketFrame> {
        private final Andesite andesite;
        private final String user;
        private final ServerWebSocket ws;
        private final boolean lavalink;
        private final List<EmitterReference> emitters = new ArrayList<>();
        private final long timerId;
        private final AndesiteEventListener listener = new AndesiteEventListener() {
            @Override
            public void onWebSocketClosed(@Nonnull String userId, @Nonnull String guildId,
                                          int closeCode, @Nullable String reason, boolean byRemote) {
                var payload = new JsonObject()
                        .put("op", "event")
                        .put("type", "WebSocketClosedEvent")
                        .put("userId", userId)
                        .put("guildId", guildId)
                        .put("reason", reason)
                        .put("code", closeCode)
                        .put("byRemote", byRemote);
                ws.writeFinalTextFrame(payload.encode());
            }
        };

        FrameHandler(@Nonnull Andesite andesite, @Nonnull String user,
                     @Nonnull ServerWebSocket ws, boolean lavalink) {
            this.andesite = andesite;
            this.user = user;
            this.ws = ws;
            this.lavalink = lavalink;
            if(lavalink) {
                this.timerId = andesite.vertx().setPeriodic(30_000, __ -> {
                    var stats = andesite.requestHandler().getNodeStatsForLavalink(false);
                    ws.writeFinalTextFrame(stats.encode());
                });
            } else {
                this.timerId = 0;
            }
            andesite.dispatcher().register(listener);
            ws.closeHandler(__ -> handleClose());
        }

        private void handleClose() {
            andesite.vertx().cancelTimer(timerId);
            andesite.dispatcher().unregister(listener);
            emitters.forEach(EmitterReference::remove);
        }

        @Override
        public void handle(@Nonnull WebSocketFrame frame) {
            JsonObject payload;
            try {
                if(frame.isText()) {
                    payload = new JsonObject(frame.textData());
                } else if(frame.isBinary()) {
                    payload = new JsonObject(frame.binaryData());
                } else {
                    return;
                }
            } catch(Exception e) {
                ws.close((short)4001, "Unable to read frame data as json: " + e);
                return;
            }
            var user = payload.getString("userId", this.user);
            var guild = payload.getString("guildId");
            switch(payload.getString("op")) {
                case "voice-server-update":
                case "voiceUpdate": {
                    andesite.requestHandler().provideVoiceServerUpdate(user, payload);
                    break;
                }
                case "subscribe": {
                    emitters.add(andesite.requestHandler().subscribe(user, guild, payload.getString("key"),
                            json -> ws.writeFinalTextFrame(json.encode())
                    ));
                    break;
                }
                case "get-stats": {
                    ws.writeFinalTextFrame(new JsonObject()
                            .put("op", "stats")
                            .put("userId", user)
                            .put("stats", andesite.requestHandler().getNodeStats())
                            .encode()
                    );
                    break;
                }
                case "get-player": {
                    var json = andesite.requestHandler().player(user, guild);
                    sendPlayerUpdate(user, guild, json);
                    break;
                }
                case "play": {
                    if(lavalink) {
                        emitters.add(andesite.requestHandler().subscribe(user, guild, "lavalink-connection",
                                json -> ws.writeFinalTextFrame(json.encode())
                        ));
                    }
                    var json = andesite.requestHandler().play(user, guild, payload);
                    sendPlayerUpdate(user, guild, json);
                    break;
                }
                case "stop": {
                    var json = andesite.requestHandler().stop(user, guild);
                    sendPlayerUpdate(user, guild, json);
                    break;
                }
                case "pause": {
                    var json = andesite.requestHandler().pause(user, guild, payload);
                    sendPlayerUpdate(user, guild, json);
                    break;
                }
                case "seek": {
                    var json = andesite.requestHandler().seek(user, guild, payload);
                    sendPlayerUpdate(user, guild, json);
                    break;
                }
                case "volume": {
                    var json = andesite.requestHandler().volume(user, guild, payload);
                    sendPlayerUpdate(user, guild, json);
                    break;
                }
                case "update": {
                    var json = andesite.requestHandler().update(user, guild, payload);
                    sendPlayerUpdate(user, guild, json);
                    break;
                }
                case "destroy": {
                    var json = andesite.requestHandler().destroy(user, guild);
                    sendPlayerUpdate(user, guild, json == null ? null : json.put("destroyed", true));
                    break;
                }
                case "ping": {
                    ws.writeFinalTextFrame(payload.copy().put("userId", user).put("op", "pong").encode());
                    break;
                }
            }
        }

        private void sendPlayerUpdate(@Nonnull String userId, @Nonnull String guildId, @Nullable JsonObject player) {
            if(lavalink && player == null) return;
            var payload = new JsonObject()
                    .put("op", lavalink ? "playerUpdate" : "player-update")
                    .put("userId", userId)
                    .put("guildId", guildId)
                    .put("state", player);
            ws.writeFinalTextFrame(payload.encode());
        }
    }
}
