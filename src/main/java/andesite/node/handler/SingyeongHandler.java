package andesite.node.handler;

import andesite.node.Andesite;
import andesite.node.Version;
import andesite.node.event.AndesiteEventListener;
import andesite.node.player.Player;
import gg.amy.singyeong.Dispatch;
import gg.amy.singyeong.QueryBuilder;
import gg.amy.singyeong.SingyeongClient;
import gg.amy.singyeong.SingyeongType;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentHashMap;

public class SingyeongHandler {
    private static final Logger log = LoggerFactory.getLogger(SingyeongHandler.class);

    public static boolean setup(@Nonnull Andesite andesite) {
        var config = andesite.config();
        var enabled = config.getBoolean("transport.singyeong.enabled", false);
        if(!enabled) {
            return false;
        }
        var nodeRegion = config.require("node.region");
        var nodeId = config.require("node.id");

        var client = new SingyeongClient(config.require("transport.singyeong.url"),
                andesite.vertx(), config.get("transport.singyeong.app-id", "andesite-audio"));

        var players = ConcurrentHashMap.<String>newKeySet();

        andesite.dispatcher().register(new AndesiteEventListener() {
            @Override
            public void onPlayerCreated(@Nonnull String userId, @Nonnull String guildId, @Nonnull Player player) {
                players.add(userId + ":" + guildId);
                updateMetadata();
            }

            @Override
            public void onPlayerDestroyed(@Nonnull String userId, @Nonnull String guildId, @Nonnull Player player) {
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

        client.onEvent(event -> {
            var payload = event.data();

            var guild = payload.getString("guildId");
            var user = payload.getString("userId");

            switch(payload.getString("op")) {
                case "voice-server-update": {
                    andesite.requestHandler().provideVoiceServerUpdate(user, payload);
                    break;
                }
                case "subscribe": {
                    var receiver = payload.getString("receiver", event.sender());
                    var query = payload.getJsonArray("query", new QueryBuilder().build());
                    andesite.requestHandler().subscribe(user, guild, payload.getString("key"),
                            json -> client.send(receiver, query, json)
                    );
                    break;
                }
                case "get-stats": {
                    sendResponse(client, event, new JsonObject()
                            .put("op", "stats")
                            .put("stats", andesite.requestHandler().getNodeStats())
                    );
                    break;
                }
                case "get-player": {
                    var json = andesite.requestHandler().player(user, guild);
                    sendPlayerUpdate(client, event, json);
                    break;
                }
                case "play": {
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
                    sendResponse(client, event, payload.copy().put("op", "pong"));
                    break;
                }
            }
        });

        client.connect()
                .thenRun(() -> {
                    client.updateMetadata("andesite-region", SingyeongType.STRING, nodeRegion);
                    client.updateMetadata("andesite-id", SingyeongType.STRING, nodeId);
                    client.updateMetadata("andesite-version", SingyeongType.STRING, Version.VERSION);
                    client.updateMetadata("andesite-version-major", SingyeongType.STRING, Version.VERSION_MAJOR);
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
        var data = dispatch.data();
        if(data.getBoolean("noreply", false)) return;
        client.send(
                data.getString("response-app", dispatch.sender()),
                data.getString("response-nonce", dispatch.nonce()),
                data.getJsonArray("response-query", new QueryBuilder().build()),
                payload
        );
    }
}
