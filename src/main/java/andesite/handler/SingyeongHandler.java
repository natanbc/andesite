package andesite.handler;

import andesite.Andesite;
import andesite.NodeState;
import andesite.event.AndesiteEventListener;
import andesite.player.AndesitePlayer;
import andesite.util.metadata.MetadataType;
import andesite.util.metadata.NamePartJoiner;
import gg.amy.singyeong.SingyeongClient;
import gg.amy.singyeong.client.SingyeongType;
import gg.amy.singyeong.client.query.Query;
import gg.amy.singyeong.client.query.QueryBuilder;
import gg.amy.singyeong.data.Dispatch;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class SingyeongHandler {
    private static final Query EMPTY_QUERY = new QueryBuilder().build();
    private static final Logger log = LoggerFactory.getLogger(SingyeongHandler.class);
    
    public static boolean setup(@Nonnull Andesite andesite) {
        var config = andesite.config().getConfig("andesite");
        var enabled = config.getBoolean("transport.singyeong.enabled");
        if(!enabled) {
            return false;
        }
    
        var dsn = config.getString("transport.singyeong.dsn");
        
        log.info("Connecting to singyeong with dsn {}", dsn);
        
        var client = SingyeongClient.create(andesite.vertx(), dsn);
        
        var players = ConcurrentHashMap.<String>newKeySet();
        
        andesite.dispatcher().register(new AndesiteEventListener() {
            @Override
            public void onPlayerCreated(@Nonnull NodeState state, @Nonnull String userId,
                                        @Nonnull String guildId, @Nonnull AndesitePlayer player) {
                players.add(userId + ":" + guildId);
                updateMetadata();
            }
            
            @Override
            public void onPlayerDestroyed(@Nonnull NodeState state, @Nonnull String userId,
                                          @Nonnull String guildId, @Nonnull AndesitePlayer player) {
                players.remove(userId + ":" + guildId);
                updateMetadata();
            }
            
            private void updateMetadata() {
                client.updateMetadata("andesite-connections", SingyeongType.LIST, players.stream()
                        .reduce(
                                new JsonArray(),
                                JsonArray::add,
                                JsonArray::addAll
                        )
                );
            }
        });
        
        //used for player event listeners
        var key = new Object();
        
        client.onInvalid(i ->
                log.error("Got INVALID response for nonce {} with reason {}",
                        i.nonce(), i.reason())
        );
        
        client.onEvent(event -> {
            if(andesite.pluginManager().customHandleSingyeongPayload(event)) {
                return;
            }
            
            var payload = event.data();
            
            var guild = payload.getString("guildId");
            var user = payload.getString("userId");
            
            switch(payload.getString("op")) {
                case "voice-server-update": {
                    andesite.requestHandler().provideVoiceServerUpdate(user, payload);
                    break;
                }
                case "get-stats": {
                    sendResponse(client, event, new JsonObject()
                            .put("op", "stats")
                            .put("stats", andesite.requestHandler().nodeStats())
                    );
                    break;
                }
                case "get-player": {
                    var json = andesite.requestHandler().player(user, guild);
                    sendPlayerUpdate(client, event, json);
                    break;
                }
                case "mixer": {
                    var json = andesite.requestHandler().mixer(user, guild, payload);
                    sendPlayerUpdate(client, event, json);
                    break;
                }
                case "filters": {
                    var json = andesite.requestHandler().filters(user, guild, payload);
                    sendPlayerUpdate(client, event, json);
                    break;
                }
                case "play": {
                    var query = createQuery(payload.getJsonObject("query"), () -> new QueryBuilder()
                            .target(event.sender())
                            .build()
                    );
                    andesite.requestHandler().subscribe(user, guild, key, json -> client.send(query, json));
                    var json = andesite.requestHandler().play(user, guild, payload);
                    sendPlayerUpdate(client, event, json);
                    break;
                }
                case "stop": {
                    var json = andesite.requestHandler().stop(user, guild);
                    sendPlayerUpdate(client, event, json);
                    break;
                }
                case "pause": {
                    var json = andesite.requestHandler().pause(user, guild, payload);
                    sendPlayerUpdate(client, event, json);
                    break;
                }
                case "seek": {
                    var json = andesite.requestHandler().seek(user, guild, payload);
                    sendPlayerUpdate(client, event, json);
                    break;
                }
                case "volume": {
                    var json = andesite.requestHandler().volume(user, guild, payload);
                    sendPlayerUpdate(client, event, json);
                    break;
                }
                case "update": {
                    var json = andesite.requestHandler().update(user, guild, payload);
                    sendPlayerUpdate(client, event, json);
                    break;
                }
                case "destroy": {
                    var json = andesite.requestHandler().destroy(user, guild);
                    sendPlayerUpdate(client, event, json == null ? null : json.put("destroyed", true));
                    break;
                }
                case "ping": {
                    sendResponse(client, event, payload.put("op", "pong"));
                    break;
                }
            }
        });
        
        
        client.connect()
                .thenRun(() -> {
                    andesite.requestHandler().metadataFields(NamePartJoiner.DASHED).forEach((k, v) ->
                            client.updateMetadata("andesite-" + k, toSingyeong(v.type()), v.rawValue())
                    );
                    client.updateMetadata("andesite-connections", SingyeongType.LIST, new JsonArray());
                    log.info("Singyeong connection established");
                })
                .exceptionally(error -> {
                    log.error("Unable to establish singyeong connection", error);
                    System.exit(-1);
                    return null;
                })
                .join();
        return true;
    }
    
    private static void sendPlayerUpdate(@Nonnull SingyeongClient client, @Nonnull Dispatch dispatch,
                                         @Nullable JsonObject player) {
        sendResponse(client, dispatch, new JsonObject()
                .put("op", "player-update")
                .put("guildId", dispatch.data().getString("guildId"))
                .put("state", player));
    }
    
    private static void sendResponse(@Nonnull SingyeongClient client, @Nonnull Dispatch dispatch,
                                     @Nullable JsonObject payload) {
        if(payload == null) return;
        var data = dispatch.data();
        if(data.getValue("response-query") == null || data.getBoolean("noreply", false)) {
            return;
        }
        client.send(
                createQuery(data.getJsonObject("response-query"), () -> EMPTY_QUERY),
                data.getString("response-nonce", dispatch.nonce()),
                payload.put("userId", data.getString("userId"))
        );
    }
    
    @Nonnull
    @CheckReturnValue
    private static Query createQuery(@Nullable JsonObject json, @Nonnull Supplier<Query> defaultQuery) {
        if(json == null) {
            return defaultQuery.get();
        }
        var builder = new QueryBuilder();
        var opsRaw = json.getJsonArray("ops");
        if(opsRaw != null) {
            var ops = new ArrayList<JsonObject>();
            for(var v : opsRaw) {
                if(!(v instanceof JsonObject)) {
                    throw new IllegalArgumentException("All ops must be json objects!");
                }
                ops.add((JsonObject) v);
            }
            builder.withOps(ops);
        }
        var targetRaw = json.getValue("target");
        if(targetRaw instanceof String) {
            builder.target((String) targetRaw);
        } else if(targetRaw instanceof JsonArray) {
            var list = new ArrayList<String>();
            for(var v : (JsonArray) targetRaw) {
                if(!(v instanceof String)) {
                    throw new IllegalArgumentException("All targets must be strings");
                }
                list.add((String) v);
            }
            builder.target(list);
        }
        return builder
                .optional(json.getBoolean("optional", false))
                .hashKey(json.getString("key"))
                .restricted(json.getBoolean("restricted", false))
                .build();
    }
    
    @Nonnull
    @CheckReturnValue
    private static SingyeongType toSingyeong(@Nonnull MetadataType type) {
        switch(type) {
            case STRING:
                return SingyeongType.STRING;
            case INTEGER:
                return SingyeongType.INTEGER;
            case STRING_LIST:
                return SingyeongType.LIST;
            case VERSION:
                return SingyeongType.VERSION;
            default:
                throw new AssertionError();
        }
    }
}
