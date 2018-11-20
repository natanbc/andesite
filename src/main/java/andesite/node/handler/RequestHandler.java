package andesite.node.handler;

import andesite.node.Andesite;
import andesite.node.player.EmitterReference;
import andesite.node.util.RequestUtils;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.TrackMarker;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.npstr.magma.MagmaMember;
import space.npstr.magma.MagmaServerUpdate;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import static andesite.node.util.RequestUtils.encodeThrowable;

public class RequestHandler {
    private static final Class<?> INTERNAL_BEAN_CLASS;
    private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);

    private final Andesite andesite;

    static {
        Class<?> c;
        try {
            c = Class.forName("com.sun.management.OperatingSystemMXBean");
        } catch(Exception e) {
            c = null;
        }
        INTERNAL_BEAN_CLASS = c;
    }

    public RequestHandler(@Nonnull Andesite andesite) {
        this.andesite = andesite;
    }

    public void provideVoiceServerUpdate(@Nonnull String userId, @Nonnull JsonObject json) {
        log.info("Handling voice server update for user {} and payload {}", userId, json);

        var sessionId = json.getString("sessionId");
        var guildId = json.getString("guildId");

        var event = json.getJsonObject("event", null);

        if(event == null) {
            return;
        }

        var endpoint = event.getString("endpoint", null);
        var token = event.getString("token", null);

        //discord sometimes send a partial server update missing the endpoint, which can be ignored.
        if (endpoint == null || endpoint.isEmpty()) {
            return;
        }

        var member = MagmaMember.builder()
                .userId(userId)
                .guildId(guildId)
                .build();
        var serverUpdate = MagmaServerUpdate.builder()
                .sessionId(sessionId)
                .endpoint(endpoint)
                .token(token)
                .build();
        andesite.magma().provideVoiceServerUpdate(member, serverUpdate);
    }

    @Nullable
    public JsonObject player(@Nonnull String userId, @Nonnull String guildId) {
        log.info("Fetching player info for user {} in guild {}", userId, guildId);
        var player = andesite.getExistingPlayer(userId, guildId);
        return player == null ? null : player.encodeState();
    }

    @Nonnull
    public EmitterReference subscribe(@Nonnull String userId, @Nonnull String guildId,
                                      @Nonnull String key, @Nonnull Consumer<JsonObject> eventSink) {
        log.info("Subscribing for events with key {} for user {} in guild {}", key, userId, guildId);
        return andesite.getPlayer(userId, guildId).setListener(key, eventSink);
    }

    @Nonnull
    public JsonObject play(@Nonnull String userId, @Nonnull String guildId, @Nonnull JsonObject payload) {
        log.info("Playing track for user {} in guild {} and payload {}", userId, guildId, payload);
        var player = andesite.getPlayer(userId, guildId);
        var track = RequestUtils.decodeTrack(andesite.audioPlayerManager(), payload.getString("track"));
        var start = payload.getInteger("start", payload.getInteger("startTime", 0));
        if(start != 0) {
            track.setPosition(start);
        }
        var end = payload.getInteger("end", payload.getInteger("endTime", 0));
        if(end != 0) {
            track.setMarker(new TrackMarker(end, state -> {
                switch(state) {
                    case REACHED:
                    case BYPASSED:
                    case LATE:
                        player.audioPlayer().stopTrack();
                }
            }));
        }

        player.audioPlayer().setPaused(payload.getBoolean("pause", player.audioPlayer().isPaused()));
        player.audioPlayer().setVolume(payload.getInteger("volume", player.audioPlayer().getVolume()));
        player.audioPlayer().startTrack(track, false);

        var m = MagmaMember.builder()
                .userId(userId)
                .guildId(guildId)
                .build();
        andesite.magma().setSendHandler(m, player);

        return player.encodeState();
    }

    @Nullable
    public JsonObject stop(@Nonnull String userId, @Nonnull String guildId) {
        log.info("Stopping player for user {} in guild {}", userId, guildId);
        var player = andesite.getPlayer(userId, guildId);
        player.audioPlayer().stopTrack();
        return player.encodeState();
    }

    @Nullable
    public JsonObject pause(@Nonnull String userId, @Nonnull String guildId, @Nonnull JsonObject payload) {
        log.info("Updating pause state for user {} in guild {} and payload {}", userId, guildId, payload);
        var player = andesite.getPlayer(userId, guildId);
        player.audioPlayer().setPaused(payload.getBoolean("pause", false));
        return player.encodeState();
    }

    @Nullable
    public JsonObject seek(@Nonnull String userId, @Nonnull String guildId, @Nonnull JsonObject payload) {
        log.info("Seeking for user {} in guild {} and payload {}", userId, guildId, payload);
        var player = andesite.getPlayer(userId, guildId);
        var track = player.audioPlayer().getPlayingTrack();
        if(track != null) {
            track.setPosition(payload.getLong("position", 0L));
        }
        return player.encodeState();
    }

    @Nullable
    public JsonObject volume(@Nonnull String userId, @Nonnull String guildId, @Nonnull JsonObject payload) {
        log.info("Updating volume for user {} in guild {} and payload {}", userId, guildId, payload);
        var player = andesite.getPlayer(userId, guildId);
        player.audioPlayer().setVolume(payload.getInteger("volume", 100));
        return player.encodeState();
    }

    @Nullable
    public JsonObject update(@Nonnull String userId, @Nonnull String guildId, @Nonnull JsonObject payload) {
        log.info("Updating player for user {} in guild {} and payload {}", userId, guildId, payload);
        var player = andesite.getPlayer(userId, guildId);
        if(payload.getValue("pause") != null) {
            player.audioPlayer().setPaused(payload.getBoolean("pause"));
        }
        if(payload.getValue("position") != null) {
            var track = player.audioPlayer().getPlayingTrack();
            if(track != null) {
                track.setPosition(payload.getLong("position"));
            }
        }
        if(payload.getValue("volume") != null) {
            player.audioPlayer().setVolume(payload.getInteger("volume"));
        }
        return player.encodeState();
    }

    @Nullable
    public JsonObject destroy(@Nonnull String userId, @Nonnull String guildId) {
        log.info("Destroying player for user {} in guild {} and payload {}", userId, guildId);
        var player = andesite.removePlayer(userId, guildId);
        if(player != null) {
            player.destroy();
        }
        var member = MagmaMember.builder()
                .userId(userId)
                .guildId(guildId)
                .build();
        andesite.magma().removeSendHandler(member);
        andesite.magma().closeConnection(member);
        return player == null ? null : player.encodeState();
    }

    @Nonnull
    @CheckReturnValue
    public CompletionStage<JsonObject> resolveTracks(@Nonnull String identifier) {
        var future = new CompletableFuture<JsonObject>();
        var isUrl = true;
        try {
            new URL(identifier);
        } catch(MalformedURLException e) {
            isUrl = false;
        }
        var isSearch = identifier.startsWith("ytsearch:") || identifier.startsWith("scsearch:");
        andesite.audioPlayerManager().loadItem(isUrl || isSearch ? identifier : "ytsearch:" + identifier,
                new AudioLoadResultHandler() {
                    @Override
                    public void trackLoaded(AudioTrack track) {
                        future.complete(new JsonObject()
                                .put("loadType", "TRACK_LOADED")
                                .put("tracks", new JsonArray()
                                        .add(RequestUtils.encodeTrack(andesite.audioPlayerManager(), track))));
                    }

                    @Override
                    public void playlistLoaded(AudioPlaylist playlist) {
                        var array = new JsonArray();
                        for(AudioTrack track : playlist.getTracks()) {
                            array.add(RequestUtils.encodeTrack(andesite.audioPlayerManager(), track));
                        }
                        var idx = playlist.getTracks().indexOf(playlist.getSelectedTrack());
                        future.complete(new JsonObject()
                                .put("loadType", playlist.isSearchResult() ? "SEARCH_RESULT" : "PLAYLIST_LOADED")
                                .put("tracks", array)
                                .put("playlistInfo", new JsonObject()
                                        .put("name", playlist.getName())
                                        .put("selectedTrack", idx == -1 ? null : idx)
                                ));
                    }

                    @Override
                    public void noMatches() {
                        future.complete(new JsonObject().put("loadType", "NO_MATCHES"));
                    }

                    @Override
                    public void loadFailed(FriendlyException exception) {
                        future.complete(new JsonObject()
                                .put("loadType", "LOAD_FAILED")
                                .put("cause", encodeThrowable(exception))
                                .put("severity", exception.severity.name()));
                    }
                });
        return future;
    }

    @Nonnull
    @CheckReturnValue
    public JsonObject getNodeStats() {
        var root = new JsonObject();

        var playerStats = andesite.allPlayers().reduce(new int[2], (ints, player) -> {
            ints[0]++;
            if(player.isPlaying()) ints[1]++;
            return ints;
        }, (ints1, ints2) -> {
            ints1[0] += ints2[0];
            ints1[1] += ints2[1];
            return ints1;
        });
        root.put("players", new JsonObject()
                .put("total", playerStats[0])
                .put("playing", playerStats[1]));

        var runtime = ManagementFactory.getRuntimeMXBean();
        var version = Runtime.version();
        root.put("runtime", new JsonObject()
                .put("uptime", runtime.getUptime())
                .put("pid", runtime.getPid())
                .put("managementSpecVersion", runtime.getManagementSpecVersion())
                .put("name", runtime.getName())
                .put("vm", new JsonObject()
                        .put("name", runtime.getVmName())
                        .put("vendor", runtime.getVmVendor())
                        .put("version", runtime.getVmVersion())
                )
                .put("spec", new JsonObject()
                        .put("name", runtime.getSpecName())
                        .put("vendor", runtime.getSpecVendor())
                        .put("version", runtime.getSpecVersion())
                )
                .put("version", new JsonObject()
                        .put("feature", version.feature())
                        .put("interim", version.interim())
                        .put("update", version.update())
                        .put("patch", version.patch())
                        .put("pre", version.pre().orElse(null))
                        .put("build", version.build().orElse(null))
                        .put("optional", version.optional().orElse(null))
                )
        );

        var os = ManagementFactory.getOperatingSystemMXBean();
        root.put("os", new JsonObject()
                .put("processors", os.getAvailableProcessors())
                .put("name", os.getName())
                .put("arch", os.getArch())
                .put("version", os.getVersion())
        );

        //INTERNAL_BEAN_CLASS is a Class<?> object to the com.sun.management.OperatingSystemMXBean class
        if(INTERNAL_BEAN_CLASS != null && INTERNAL_BEAN_CLASS.isInstance(os)) {
            var internalBean = (com.sun.management.OperatingSystemMXBean)os;
            root.put("cpu", new JsonObject()
                    .put("andesite", internalBean.getProcessCpuLoad())
                    .put("system", internalBean.getSystemCpuLoad())
            );
        } else {
            root.putNull("cpu");
        }

        var classLoading = ManagementFactory.getClassLoadingMXBean();
        root.put("classLoading", new JsonObject()
                .put("loaded", classLoading.getLoadedClassCount())
                .put("totalLoaded", classLoading.getTotalLoadedClassCount())
                .put("unloaded", classLoading.getUnloadedClassCount())
        );

        var thread = ManagementFactory.getThreadMXBean();
        root.put("thread", new JsonObject()
                .put("running", thread.getThreadCount())
                .put("daemon", thread.getDaemonThreadCount())
                .put("peak", thread.getPeakThreadCount())
                .put("totalStarted", thread.getTotalStartedThreadCount())
        );

        var compilation = ManagementFactory.getCompilationMXBean();
        root.put("compilation", new JsonObject()
                .put("name", compilation.getName())
                .put("totalTime", compilation.getTotalCompilationTime())
        );

        var memoryBean = ManagementFactory.getMemoryMXBean();
        root.put("memory", new JsonObject()
                .put("pendingFinalization", memoryBean.getObjectPendingFinalizationCount())
                .put("heap", toJson(memoryBean.getHeapMemoryUsage()))
                .put("nonHeap", toJson(memoryBean.getNonHeapMemoryUsage())));

        var gc = ManagementFactory.getGarbageCollectorMXBeans();
        root.put("gc", gc.stream().map(bean -> new JsonObject()
                .put("name", bean.getName())
                .put("collectionCount", bean.getCollectionCount())
                .put("collectionTime", bean.getCollectionTime())
                .put("pools", Arrays.stream(bean.getMemoryPoolNames())
                        .reduce(new JsonArray(), JsonArray::add, JsonArray::addAll))
        ).reduce(new JsonArray(), JsonArray::add, JsonArray::addAll));

        var pools = ManagementFactory.getMemoryPoolMXBeans();
        root.put("memoryPools", pools.stream().map(bean -> {
            var json = new JsonObject()
                    .put("name", bean.getName())
                    .put("type", bean.getType().name())
                    .put("collectionUsage", toJson(bean.getCollectionUsage()))
                    .putNull("collectionUsageThreshold")
                    .putNull("collectionUsageThresholdCount")
                    .put("peakUsage", toJson(bean.getPeakUsage()))
                    .put("usage", toJson(bean.getUsage()))
                    .putNull("usageThreshold")
                    .putNull("usageThresholdCount")
                    .put("managers", Arrays.stream(bean.getMemoryManagerNames())
                            .reduce(new JsonArray(), JsonArray::add, JsonArray::addAll));
            if(bean.isCollectionUsageThresholdSupported()) {
                json.put("collectionUsageThreshold", bean.getCollectionUsageThreshold())
                        .put("collectionUsageThresholdCount", bean.getCollectionUsageThresholdCount());
            }
            if(bean.isUsageThresholdSupported()) {
                json.put("usageThreshold", bean.getUsageThreshold())
                        .put("usageThresholdCount", bean.getUsageThreshold());
            }
            return json;
        }).reduce(new JsonArray(), JsonArray::add, JsonArray::addAll));

        var managers = ManagementFactory.getMemoryManagerMXBeans();
        root.put("memoryManagers", managers.stream().map(bean -> new JsonObject()
                .put("name", bean.getName())
                .put("pools", Arrays.stream(bean.getMemoryPoolNames())
                        .reduce(new JsonArray(), JsonArray::add, JsonArray::addAll))
        ).reduce(new JsonArray(), JsonArray::add, JsonArray::addAll));

        return root;
    }

    @Nonnull
    @CheckReturnValue
    public JsonObject getNodeStatsForLavalink(boolean includeDetailed) {
        var root = new JsonObject();
        if(includeDetailed) {
            root.put("detailed", getNodeStats());
        }

        var playerStats = andesite.allPlayers().reduce(new int[2], (ints, player) -> {
            ints[0]++;
            if(player.isPlaying()) ints[1]++;
            return ints;
        }, (ints1, ints2) -> {
            ints1[0] += ints2[0];
            ints1[1] += ints2[1];
            return ints1;
        });
        root.put("players", playerStats[0])
                .put("playingPlayers", playerStats[1])
                .put("uptime", ManagementFactory.getRuntimeMXBean().getUptime());

        var memory = ManagementFactory.getMemoryMXBean();
        var heap = memory.getHeapMemoryUsage();
        var nonHeap = memory.getNonHeapMemoryUsage();
        root.put("memory", new JsonObject()
                .put("free", (heap.getCommitted() - heap.getUsed()) +
                        (nonHeap.getCommitted() - nonHeap.getUsed()))
                .put("used", heap.getUsed() + nonHeap.getUsed())
                .put("allocated", heap.getCommitted() + nonHeap.getCommitted())
                .put("reservable", heap.getMax() + nonHeap.getMax())
        );

        double systemLoad = 0;
        double load = 0;
        var os = ManagementFactory.getOperatingSystemMXBean();
        //INTERNAL_BEAN_CLASS is a Class<?> object to the com.sun.management.OperatingSystemMXBean class
        if(INTERNAL_BEAN_CLASS != null && INTERNAL_BEAN_CLASS.isInstance(os)) {
            var internalBean = (com.sun.management.OperatingSystemMXBean)os;
            systemLoad = internalBean.getSystemCpuLoad();
            load = internalBean.getProcessCpuLoad();
        }
        root.put("cpu", new JsonObject()
                .put("cores", os.getAvailableProcessors())
                .put("systemLoad", systemLoad)
                .put("lavalinkLoad", load)
        );

        root.putNull("frameStats");

        return root;
    }

    @Nullable
    @CheckReturnValue
    private static JsonObject toJson(@Nullable MemoryUsage usage) {
        if(usage == null) return null;
        var json = new JsonObject();
        json.put("init", usage.getInit());
        json.put("used", usage.getUsed());
        json.put("committed", usage.getCommitted());
        json.put("max", usage.getMax());
        return json;
    }
}
