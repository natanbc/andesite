package andesite.node.handler;

import andesite.node.Andesite;
import andesite.node.NodeState;
import andesite.node.event.AndesiteEventListener;
import andesite.node.player.Player;
import io.vertx.core.Handler;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.http.WebSocketFrame;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WebSocketHandler {
    private static final Set<String> NEEDS_USER_AND_GUILD = Set.of(
            "voice-server-update", "voiceUpdate", "get-player",
            "mixer", "filters", "equalizer",
            "play", "stop", "pause",
            "seek", "volume", "update",
            "destroy"
    );
    
    private static final Logger log = LoggerFactory.getLogger(WebSocketHandler.class);
    
    public static void setup(@Nonnull Andesite andesite, @Nonnull Router router) {
        router.get("/websocket").handler(handler(andesite, false));
        router.get(
                andesite.config().getString("andesite.lavalink.ws-path")
        ).handler(handler(andesite, true));
    }
    
    @Nonnull
    @CheckReturnValue
    private static Handler<RoutingContext> handler(@Nonnull Andesite andesite, boolean lavalinkRoute) {
        return context -> {
            var req = context.request();
            if("websocket".equalsIgnoreCase(req.getHeader("upgrade"))) {
                var id = andesite.nextConnectionId();
                context.response().putHeader("Andesite-Connection-Id", String.valueOf(id));
                var ws = req.upgrade();
                var userId = context.<String>get("user-id");
                var llQuery = context.queryParam("lavalink");
                var lavalinkConnection = lavalinkRoute
                        || "lavalink".equalsIgnoreCase(req.getHeader("Andesite-Compat"))
                        || llQuery != null && !llQuery.isEmpty();
                log.info("New {}connection from {} with id {}",
                        lavalinkConnection ? "lavalink " : "",
                        context.request().remoteAddress(), id);
                var resumeId = context.request().getHeader("Andesite-Resume-Id");
                if(resumeId != null) {
                    try {
                        var buffer = andesite.removeEventBuffer(Long.parseLong(resumeId));
                        if(buffer != null) {
                            log.info("Resuming connection {} to {}", resumeId, id);
                            andesite.allPlayers().forEach(p -> p.eventListeners().remove(buffer));
                            buffer.empty(json -> ws.writeFinalTextFrame(json.encode()));
                        }
                    } catch(Exception ignored) {
                    }
                }
                ws.writeTextMessage(new JsonObject()
                        .put("op", "connection-id")
                        //making it a string allows a future change of the
                        //id format without breaking clients - the actual
                        //format is opaque to them.
                        .put("id", String.valueOf(id))
                        .encode()
                );
                ws.frameHandler(new FrameHandler(andesite, userId, ws, id, lavalinkConnection));
            } else {
                context.next();
            }
        };
    }
    
    private static class FrameHandler implements Handler<WebSocketFrame>, WebSocketState {
        private final Map<Key<?>, Object> userData = new HashMap<>();
        private final Andesite andesite;
        private final String user;
        private final ServerWebSocket ws;
        private final long connectionId;
        private final boolean lavalink;
        private final Long timerId;
        private final Set<Player> subscriptions = ConcurrentHashMap.newKeySet();
        private final AndesiteEventListener listener = new AndesiteEventListener() {
            @Override
            public void onWebSocketClosed(@Nonnull NodeState state, @Nonnull String userId,
                                          @Nonnull String guildId, int closeCode,
                                          @Nullable String reason, boolean byRemote) {
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
        private long timeout;
        
        FrameHandler(@Nonnull Andesite andesite, @Nonnull String user,
                     @Nonnull ServerWebSocket ws, long connectionId, boolean lavalink) {
            this.andesite = andesite;
            this.user = user;
            this.ws = ws;
            this.connectionId = connectionId;
            this.lavalink = lavalink;
            if(lavalink) {
                this.timerId = andesite.vertx().setPeriodic(30_000, __ -> {
                    var stats = andesite.requestHandler().nodeStatsForLavalink();
                    ws.writeFinalTextFrame(stats
                            .put("op", "stats")
                            .encode());
                });
            } else {
                this.timerId = null;
            }
            andesite.dispatcher().register(listener);
            ws.closeHandler(__ -> handleClose());
        }
    
        @CheckReturnValue
        @Nonnull
        @Override
        public String user() {
            return user;
        }
    
        @CheckReturnValue
        @Nonnull
        @Override
        public ServerWebSocket ws() {
            return ws;
        }
    
        @CheckReturnValue
        @Nonnull
        @Override
        public String connectionId() {
            return Long.toString(connectionId);
        }
    
        @CheckReturnValue
        @Override
        public boolean lavalink() {
            return lavalink;
        }
    
        @CheckReturnValue
        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(@Nonnull Key<T> key) {
            return (T) userData.getOrDefault(key, key.defaultValue());
        }
        
        @Nullable
        @CheckReturnValue
        @Override
        @SuppressWarnings("unchecked")
        public <T> T set(@Nonnull Key<T> key, @Nullable T value) {
            return (T) userData.put(key, value);
        }
        
        private void handleClose() {
            if(timeout != 0) {
                var buffer = andesite.createEventBuffer(connectionId);
                subscriptions.forEach(p -> p.setListener(buffer, buffer::offer));
                andesite.vertx().setTimer(timeout, __ -> {
                    subscriptions.forEach(p -> p.eventListeners().remove(buffer));
                    andesite.removeEventBuffer(connectionId);
                });
            }
            if(timerId != null) {
                andesite.vertx().cancelTimer(timerId);
            }
            andesite.dispatcher().unregister(listener);
            subscriptions.forEach(p -> p.eventListeners().remove(this));
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
                ws.close((short) 4001, "Unable to read frame data as json: " + e);
                return;
            }
            log.debug("Received payload {}", payload);
            if(andesite.pluginManager().customHandleWebSocketPayload(this, payload)) {
                return;
            }
            var user = payload.getString("userId", this.user);
            var guild = payload.getString("guildId");
            var op = payload.getString("op", null);
            if(op == null) {
                ws.close((short) 4002, "Null op provided");
                return;
            }
            if(NEEDS_USER_AND_GUILD.contains(op)) {
                if(user == null) {
                    ws.close((short) 4002, "Null user id provided");
                    return;
                }
                if(guild == null) {
                    ws.close((short) 4002, "Null guild id provided");
                    return;
                }
            }
            switch(op) {
                case "voice-server-update":
                case "voiceUpdate": {
                    andesite.requestHandler().provideVoiceServerUpdate(user, payload);
                    break;
                }
                case "event-buffer": {
                    timeout = payload.getInteger("timeout", 0);
                    break;
                }
                case "get-stats": {
                    ws.writeFinalTextFrame(new JsonObject()
                            .put("op", "stats")
                            .put("userId", user)
                            .put("stats", andesite.requestHandler().nodeStats())
                            .encode()
                    );
                    break;
                }
                case "get-player": {
                    var json = andesite.requestHandler().player(user, guild);
                    sendPlayerUpdate(user, guild, json);
                    break;
                }
                case "mixer": {
                    var json = andesite.requestHandler().mixer(user, guild, payload);
                    sendPlayerUpdate(user, guild, json);
                    break;
                }
                case "filters": {
                    var json = andesite.requestHandler().filters(user, guild, payload);
                    sendPlayerUpdate(user, guild, json);
                    break;
                }
                //lavalink compat
                case "equalizer": {
                    var json = andesite.requestHandler().equalizer(user, guild, payload);
                    sendPlayerUpdate(user, guild, json);
                    break;
                }
                case "play": {
                    andesite.requestHandler().subscribe(user, guild, this,
                            json -> {
                                if(lavalink) {
                                    json = transformPayloadForLavalink(json);
                                }
                                if(json == null) return;
                                ws.writeFinalTextFrame(json.encode());
                            });
                    subscriptions.add(andesite.getPlayer(user, guild));
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
                    var player = andesite.getExistingPlayer(user, guild);
                    if(player != null) {
                        subscriptions.remove(player);
                    }
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
    
    @Nullable
    @CheckReturnValue
    private static JsonObject transformPayloadForLavalink(@Nonnull JsonObject payload) {
        if("player-update".equals(payload.getValue("op", null))) {
            return payload.put("op", "playerUpdate");
        }
        if("event".equals(payload.getValue("op", null))) {
            if("TrackStartEvent".equals(payload.getValue("type", null))) {
                return null;
            }
        }
        return payload;
    }
}
