package andesite.node.handler;

import andesite.node.Andesite;
import andesite.node.player.filter.FilterChainConfiguration;
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

public class RequestHandler implements AndesiteRequestHandler {
    private static final Class<?> INTERNAL_BEAN_CLASS;
    private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);

    private final Andesite andesite;

    static {
        Class<?> c;
        try {
            c = Class.forName("com.sun.management.OperatingSystemMXBean");
        } catch(Exception e) {
            c = null;
            log.error("Unable to load internal OperatingSystemMXBean class. CPU usage info unavailable");
        }
        INTERNAL_BEAN_CLASS = c;
    }

    public RequestHandler(@Nonnull Andesite andesite) {
        this.andesite = andesite;
    }

    @Override
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

        andesite.audioHandler()
                .handleVoiceUpdate(userId, guildId, sessionId, endpoint, token);
    }

    @Nullable
    @Override
    public JsonObject player(@Nonnull String userId, @Nonnull String guildId) {
        log.info("Fetching player info for user {} in guild {}", userId, guildId);
        var player = andesite.getExistingPlayer(userId, guildId);
        return player == null ? null : player.encodeState();
    }

    @Override
    public void subscribe(@Nonnull String userId, @Nonnull String guildId,
                          @Nonnull Object key, @Nonnull Consumer<JsonObject> eventSink) {
        log.info("Subscribing for events for user {} in guild {}", userId, guildId);
        andesite.getPlayer(userId, guildId).setListener(key, eventSink);
    }

    @Nonnull
    @Override
    public JsonObject play(@Nonnull String userId, @Nonnull String guildId, @Nonnull JsonObject payload) {
        log.info("Playing track for user {} in guild {} with payload {}", userId, guildId, payload);
        var player = andesite.getPlayer(userId, guildId);
        if(payload.getBoolean("noReplace", false) && player.audioPlayer().getPlayingTrack() != null) {
            return player.encodeState();
        }
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

        andesite.audioHandler().setProvider(userId, guildId, player);

        return player.encodeState();
    }

    @Nonnull
    @Override
    public JsonObject mixer(@Nonnull String userId, @Nonnull String guildId, @Nonnull JsonObject payload) {
        log.info("Configuring mixer for user {} in guild {} with payload {}", userId, guildId, payload);
        var player = andesite.getPlayer(userId, guildId);
        var mixer = player.mixer();

        //check if field present
        if(payload.containsKey("enable")) {
            if(payload.getBoolean("enable")) {
                //switches to mixer as soon as it's ready to send audio
                player.switchToMixer();
            } else {
                //switches back to regular player
                player.switchToSingle();
            }
        }

        var players = payload.getJsonObject("players", new JsonObject());
        players.fieldNames().forEach(key -> {
            var config = players.getJsonObject(key);
            var mixerPlayer = mixer.getPlayer(key);
            var p = mixerPlayer.audioPlayer();
            p.setPaused(config.getBoolean("pause", p.isPaused()));
            p.setVolume(config.getInteger("volume", p.getVolume()));
            AudioTrack track;
            if(config.containsKey("track")) {
                track = RequestUtils.decodeTrack(andesite.audioPlayerManager(), config.getString("track"));
                var start = config.getInteger("start", config.getInteger("startTime", 0));
                if(start != 0) {
                    track.setPosition(start);
                }
            } else {
                track = p.getPlayingTrack();
            }
            var end = config.getInteger("end", config.getInteger("endTime", 0));
            if(end != 0 && track != null) {
                track.setMarker(new TrackMarker(end, state -> {
                    switch(state) {
                        case REACHED:
                        case BYPASSED:
                        case LATE:
                            player.audioPlayer().stopTrack();
                    }
                }));
            }
            if(config.containsKey("filters")) {
                var cfg = player.filterConfig();
                updateFilters(cfg, config.getJsonObject("filters"));
                if(cfg.isEnabled()) {
                    p.setFilterFactory(cfg.factory());
                }
            }
            if(track != p.getPlayingTrack()) {
                p.startTrack(track, false);
            } else {
                if(config.containsKey("position")) {
                    if(track != null) {
                        track.setPosition(config.getLong("position"));
                    }
                }
            }
        });

        andesite.audioHandler().setProvider(userId, guildId, player);

        return player.encodeState();
    }

    @Nonnull
    @Override
    public JsonObject stop(@Nonnull String userId, @Nonnull String guildId) {
        log.info("Stopping player for user {} in guild {}", userId, guildId);
        var player = andesite.getPlayer(userId, guildId);
        player.audioPlayer().stopTrack();
        return player.encodeState();
    }

    @Nonnull
    @Override
    public JsonObject pause(@Nonnull String userId, @Nonnull String guildId, @Nonnull JsonObject payload) {
        log.info("Updating pause state for user {} in guild {} with payload {}", userId, guildId, payload);
        var player = andesite.getPlayer(userId, guildId);
        player.audioPlayer().setPaused(payload.getBoolean("pause", false));
        return player.encodeState();
    }

    @Nonnull
    @Override
    public JsonObject seek(@Nonnull String userId, @Nonnull String guildId, @Nonnull JsonObject payload) {
        log.info("Seeking for user {} in guild {} with payload {}", userId, guildId, payload);
        var player = andesite.getPlayer(userId, guildId);
        var track = player.audioPlayer().getPlayingTrack();
        if(track != null) {
            track.setPosition(payload.getLong("position", 0L));
        }
        return player.encodeState();
    }

    @Nonnull
    @Override
    public JsonObject volume(@Nonnull String userId, @Nonnull String guildId, @Nonnull JsonObject payload) {
        log.info("Updating volume for user {} in guild {} with payload {}", userId, guildId, payload);
        var player = andesite.getPlayer(userId, guildId);
        player.audioPlayer().setVolume(payload.getInteger("volume", 100));
        return player.encodeState();
    }

    @Nonnull
    @Override
    public JsonObject filters(@Nonnull String userId, @Nonnull String guildId, @Nonnull JsonObject payload) {
        log.info("Updating filters for user {} in guild {} with payload {}", userId, guildId, payload);
        var player = andesite.getPlayer(userId, guildId);
        updateFilters(player.filterConfig(), payload);
        return player.encodeState();
    }

    //lavalink compat
    @Nonnull
    public JsonObject equalizer(@Nonnull String userId, @Nonnull String guildId, @Nonnull JsonObject payload) {
        return filters(userId, guildId, new JsonObject().put("equalizer", payload));
    }

    @Nonnull
    @Override
    public JsonObject update(@Nonnull String userId, @Nonnull String guildId, @Nonnull JsonObject payload) {
        log.info("Updating player for user {} in guild {} and payload {}", userId, guildId, payload);
        var player = andesite.getPlayer(userId, guildId);
        if(payload.containsKey("pause")) {
            player.audioPlayer().setPaused(payload.getBoolean("pause"));
        }
        if(payload.containsKey("position")) {
            var track = player.audioPlayer().getPlayingTrack();
            if(track != null) {
                track.setPosition(payload.getLong("position"));
            }
        }
        if(payload.containsKey("volume")) {
            player.audioPlayer().setVolume(payload.getInteger("volume"));
        }
        if(payload.containsKey("filters")) {
            var config = player.filterConfig();
            updateFilters(config, payload.getJsonObject("filters"));
            if(config.isEnabled()) {
                player.audioPlayer().setFilterFactory(config.factory());
            }
        }
        return player.encodeState();
    }
    
    private void updateFilters(@Nonnull FilterChainConfiguration filterConfig, @Nonnull JsonObject config) {
        if(config.containsKey("equalizer")) {
            var array = config.getJsonObject("equalizer").getJsonArray("bands");
            var equalizerConfig = filterConfig.equalizer();
            for(var i = 0; i < array.size(); i++) {
                var band = array.getJsonObject(i);
                equalizerConfig.setBand(band.getInteger("band"), band.getFloat("gain"));
            }
        }
        if(config.containsKey("karaoke")) {
            var karaoke = config.getJsonObject("karaoke");
            var karaokeConfig = filterConfig.karaoke();
            karaokeConfig.setLevel(karaoke.getFloat("level", karaokeConfig.level()));
            karaokeConfig.setMonoLevel(karaoke.getFloat("monoLevel", karaokeConfig.monoLevel()));
            karaokeConfig.setFilterBand(karaoke.getFloat("filterBand", karaokeConfig.filterBand()));
            karaokeConfig.setFilterWidth(karaoke.getFloat("filterWidth", karaokeConfig.filterWidth()));
        }
        if(config.containsKey("timescale")) {
            var timescale = config.getJsonObject("timescale");
            var timescaleConfig = filterConfig.timescale();
            timescaleConfig.setSpeed(timescale.getFloat("speed", timescaleConfig.speed()));
            timescaleConfig.setPitch(timescale.getFloat("pitch", timescaleConfig.pitch()));
            timescaleConfig.setRate(timescale.getFloat("rate", timescaleConfig.rate()));
        }
        if(config.containsKey("tremolo")) {
            var tremolo = config.getJsonObject("tremolo");
            var tremoloConfig = filterConfig.tremolo();
            tremoloConfig.setFrequency(tremolo.getFloat("frequency", tremoloConfig.frequency()));
            tremoloConfig.setDepth(tremolo.getFloat("depth", tremoloConfig.depth()));
        }
        if(config.containsKey("vibrato")) {
            var vibrato = config.getJsonObject("vibrato");
            var vibratoConfig = filterConfig.vibrato();
            vibratoConfig.setFrequency(vibrato.getFloat("frequency", vibratoConfig.frequency()));
            vibratoConfig.setDepth(vibrato.getFloat("depth", vibratoConfig.depth()));
        }
        if(config.containsKey("volume")) {
            var volumeConfig = filterConfig.volume();
            volumeConfig.setVolume(config.getJsonObject("volume").getFloat("volume", volumeConfig.volume()));
        }
    }

    @Nullable
    @Override
    public JsonObject destroy(@Nonnull String userId, @Nonnull String guildId) {
        log.info("Destroying player for user {} in guild {} and payload {}", userId, guildId);
        var player = andesite.removePlayer(userId, guildId);
        //will call close()
        andesite.audioHandler().closeConnection(userId, guildId);
        return player == null ? null : player.encodeState();
    }

    @Nonnull
    @CheckReturnValue
    @Override
    public CompletionStage<JsonObject> resolveTracks(@Nonnull String identifier) {
        var future = new CompletableFuture<JsonObject>();
        var isUrl = true;
        try {
            new URL(identifier);
        } catch(MalformedURLException e) {
            isUrl = false;
        }
        var isSearch = identifier.startsWith("ytsearch:") || identifier.startsWith("scsearch:");
        andesite.audioPlayerManager().loadItem(isUrl || isSearch ? identifier :
                        identifier.startsWith("raw:") ? identifier.substring(4) : "ytsearch:" + identifier,
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
                        future.completeExceptionally(exception);
                    }
                });
        return future;
    }

    @Nonnull
    @CheckReturnValue
    @Override
    public JsonObject nodeStats() {
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
    @Override
    public JsonObject nodeStatsForLavalink() {
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
