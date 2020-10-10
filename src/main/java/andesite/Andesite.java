package andesite;

import andesite.event.EventBuffer;
import andesite.event.EventDispatcherImpl;
import andesite.handler.RequestHandler;
import andesite.handler.RestHandler;
import andesite.player.Player;
import andesite.plugin.PluginManager;
import andesite.send.AudioHandler;
import andesite.send.koe.KoeHandler;
import andesite.send.magma.MagmaHandler;
import andesite.util.ConfigUtil;
import andesite.util.FilterUtil;
import andesite.util.Init;
import andesite.util.LazyInit;
import andesite.util.NativeUtils;
import com.github.natanbc.nativeloader.NativeLibLoader;
import com.github.natanbc.nativeloader.system.SystemType;
import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.bandcamp.BandcampAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.beam.BeamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.twitch.TwitchStreamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.vimeo.VimeoAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.playback.NonAllocatingAudioFrameBuffer;
import com.sedmelluq.lava.extensions.youtuberotator.YoutubeIpRotator;
import com.sedmelluq.lava.extensions.youtuberotator.planner.AbstractRoutePlanner;
import com.sedmelluq.lava.extensions.youtuberotator.planner.BalancingIpRoutePlanner;
import com.sedmelluq.lava.extensions.youtuberotator.planner.NanoIpRoutePlanner;
import com.sedmelluq.lava.extensions.youtuberotator.planner.RotatingIpRoutePlanner;
import com.sedmelluq.lava.extensions.youtuberotator.planner.RotatingNanoIpRoutePlanner;
import com.sedmelluq.lava.extensions.youtuberotator.tools.ip.IpBlock;
import com.sedmelluq.lava.extensions.youtuberotator.tools.ip.Ipv4Block;
import com.sedmelluq.lava.extensions.youtuberotator.tools.ip.Ipv6Block;
import com.typesafe.config.Config;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.lang.ref.Cleaner;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Andesite implements NodeState {
    private static final Logger log = LoggerFactory.getLogger(Andesite.class);
    private static final LazyInit<Cleaner> CLEANER = new LazyInit<>(() -> Cleaner.create(r -> {
        var t = new Thread(r, "Andesite-Cleaner");
        t.setDaemon(true);
        return t;
    }));
    private static final Map<String, Supplier<AudioSourceManager>> SOURCE_MANAGERS = Map.of(
            "bandcamp", BandcampAudioSourceManager::new,
            "beam", BeamAudioSourceManager::new,
            "http", HttpAudioSourceManager::new,
            "local", LocalAudioSourceManager::new,
            "soundcloud", SoundCloudAudioSourceManager::createDefault,
            "twitch", TwitchStreamAudioSourceManager::new,
            "vimeo", VimeoAudioSourceManager::new,
            "youtube", YoutubeAudioSourceManager::new
    );
    private static final Set<String> DISABLED_BY_DEFAULT = Set.of("http", "local");
    
    private final PluginManager pluginManager = new PluginManager(this);
    private final AtomicLong nextBufferId = new AtomicLong();
    private final Map<Long, EventBuffer> buffers = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Player>> players = new ConcurrentHashMap<>();
    private final AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
    private final AudioPlayerManager pcmPlayerManager = new DefaultAudioPlayerManager();
    private final EventDispatcherImpl dispatcher = new EventDispatcherImpl(this);
    private final Vertx vertx;
    private final Config rootConfig;
    private final AudioHandler audioHandler;
    private final RequestHandler handler;
    private final Set<String> enabledSources;
    
    private Andesite(@Nonnull Vertx vertx, @Nonnull Config rootConfig) throws IOException {
        var config = rootConfig.getConfig("andesite");
        var plugins = new File("plugins").listFiles();
        if(plugins != null) {
            for(var f : plugins) {
                log.info("Loading plugins from {}", f);
                pluginManager.load(f);
            }
        }
        var extraPluginLocations = config.getStringList("extra-plugins");
        for(var f : extraPluginLocations) {
            log.info("Loading plugins from {}", f);
            pluginManager.load(new File(f));
        }
        this.vertx = vertx;
        this.rootConfig = pluginManager.applyPluginDefaults(rootConfig);
        this.audioHandler = createAudioHandler(config);
        this.handler = new RequestHandler(this);
        pluginManager.init();
        pluginManager.configurePlayerManager(playerManager);
        pluginManager.configurePlayerManager(pcmPlayerManager);
        this.enabledSources = SOURCE_MANAGERS.keySet().stream()
                .filter(key -> {
                    if(config.hasPath("source." + key)) {
                        return config.getBoolean("source." + key);
                    }
                    return !DISABLED_BY_DEFAULT.contains(key);
                })
                .peek(key -> playerManager.registerSourceManager(SOURCE_MANAGERS.get(key).get()))
                .peek(key -> pcmPlayerManager.registerSourceManager(SOURCE_MANAGERS.get(key).get()))
                .collect(Collectors.toSet());
        
        var planner = createRoutePlanner(config);
        configureYt(playerManager, config, planner);
        configureYt(pcmPlayerManager, config, planner);
        
        log.info("Enabled default sources: {}", enabledSources);
        //we need to set the cleanup to basically never run so mixer players aren't destroyed without need.
        playerManager.setPlayerCleanupThreshold(Long.MAX_VALUE);
        playerManager.getConfiguration().setFilterHotSwapEnabled(true);
        if(config.getBoolean("lavaplayer.non-allocating")) {
            playerManager.getConfiguration().setFrameBufferFactory(NonAllocatingAudioFrameBuffer::new);
        }
        pcmPlayerManager.setPlayerCleanupThreshold(Long.MAX_VALUE);
        pcmPlayerManager.getConfiguration().setOutputFormat(StandardAudioDataFormats.DISCORD_PCM_S16_BE);
        pcmPlayerManager.getConfiguration().setFilterHotSwapEnabled(true);
        pcmPlayerManager.getConfiguration().setFrameBufferFactory(NonAllocatingAudioFrameBuffer::new);
    
        playerManager.setFrameBufferDuration(config.getInt("lavaplayer.frame-buffer-duration"));
        pcmPlayerManager.setFrameBufferDuration(config.getInt("lavaplayer.frame-buffer-duration"));
    }
    
    @Nonnull
    @CheckReturnValue
    public PluginManager pluginManager() {
        return pluginManager;
    }
    
    @Nonnull
    @CheckReturnValue
    public Set<String> enabledSources() {
        return enabledSources;
    }
    
    @CheckReturnValue
    public long nextConnectionId() {
        return nextBufferId.incrementAndGet();
    }
    
    @Nonnull
    @CheckReturnValue
    public EventBuffer createEventBuffer(long id) {
        var buffer = new EventBuffer();
        buffers.put(id, buffer);
        return buffer;
    }
    
    @Nullable
    public EventBuffer removeEventBuffer(long id) {
        return buffers.remove(id);
    }
    
    @Nonnull
    @CheckReturnValue
    @Override
    public Config config() {
        return rootConfig;
    }
    
    @Nonnull
    @CheckReturnValue
    @Override
    public Vertx vertx() {
        return vertx;
    }
    
    @Nonnull
    @CheckReturnValue
    @Override
    public RequestHandler requestHandler() {
        return handler;
    }
    
    @Nonnull
    @CheckReturnValue
    @Override
    public AudioPlayerManager audioPlayerManager() {
        return playerManager;
    }
    
    @Nonnull
    @CheckReturnValue
    @Override
    public AudioPlayerManager pcmAudioPlayerManager() {
        return pcmPlayerManager;
    }
    
    @Nonnull
    @CheckReturnValue
    @Override
    public EventDispatcherImpl dispatcher() {
        return dispatcher;
    }
    
    @Nonnull
    @CheckReturnValue
    @Override
    public AudioHandler audioHandler() {
        return audioHandler;
    }
    
    @Nonnull
    @CheckReturnValue
    @Override
    public Map<String, Player> playerMap(@Nonnull String userId) {
        return players.computeIfAbsent(userId, __ -> new ConcurrentHashMap<>());
    }
    
    @Nonnull
    @CheckReturnValue
    @Override
    public Player getPlayer(@Nonnull String userId, @Nonnull String guildId) {
        return playerMap(userId).computeIfAbsent(guildId, __ -> {
            var player = new Player(this, guildId, userId);
            dispatcher.onPlayerCreated(userId, guildId, player);
            return player;
        });
    }
    
    @Nullable
    @CheckReturnValue
    @Override
    public Player getExistingPlayer(@Nonnull String userId, @Nonnull String guildId) {
        var map = players.get(userId);
        return map == null ? null : map.get(guildId);
    }
    
    @Nullable
    @Override
    public Player removePlayer(@Nonnull String userId, @Nonnull String guildId) {
        var map = players.get(userId);
        if(map == null) return null;
        var player = map.remove(guildId);
        if(player != null) {
            dispatcher.onPlayerDestroyed(userId, guildId, player);
        }
        return player;
    }
    
    @Nonnull
    @CheckReturnValue
    @Override
    public Stream<Player> allPlayers() {
        return players.values().stream().flatMap(m -> m.values().stream());
    }
    
    @Nonnull
    @CheckReturnValue
    @Override
    public Cleaner cleaner() {
        return CLEANER.get();
    }
    
    @Nonnull
    @CheckReturnValue
    private AudioHandler createAudioHandler(@Nonnull Config config) {
        var handlerName = config.getString("audio-handler");
        return switch(handlerName) {
            case "magma" -> new MagmaHandler(this);
            case "koe" -> new KoeHandler(this);
            default -> pluginManager.loadHandler(AudioHandler.class, handlerName);
        };
    }
    
    @SuppressWarnings("rawtypes")
    @Nullable
    @CheckReturnValue
    private static AbstractRoutePlanner createRoutePlanner(@Nonnull Config config) {
        var rotation = config.getConfig("lavaplayer.youtube.rotation");
        if(rotation.getStringList("ips").isEmpty()) return null;
        List<IpBlock> ipBlocks = rotation.getStringList("ips").stream()
                .map(block -> {
                    if(Ipv4Block.isIpv4CidrBlock(block)) {
                        return new Ipv4Block(block);
                    }
                    if(Ipv6Block.isIpv6CidrBlock(block)) {
                        return new Ipv6Block(block);
                    }
                    throw new IllegalArgumentException("Invalid IP block '" + block + "'");
                })
                .collect(Collectors.toList());
        var blacklisted = rotation.getStringList("excluded-ips").stream()
                .map(ip -> {
                    try {
                        return InetAddress.getByName(ip);
                    } catch(UnknownHostException e) {
                        throw new IllegalArgumentException("Unable to resolve blocked IP '" + ip + "'", e);
                    }
                })
                .collect(Collectors.toSet());
        var filter = ((Predicate<InetAddress>)blacklisted::contains).negate();
        var searchTriggersFail = rotation.getBoolean("search-triggers-fail");
        var strategy = rotation.getString("strategy").strip().toLowerCase();
        return switch(strategy) {
            case "rotateonban" -> new RotatingIpRoutePlanner(ipBlocks, filter, searchTriggersFail);
            case "loadbalance" -> new BalancingIpRoutePlanner(ipBlocks, filter, searchTriggersFail);
            case "nanoswitch" -> new NanoIpRoutePlanner(ipBlocks, searchTriggersFail);
            case "rotatingnanoswitch" -> new RotatingNanoIpRoutePlanner(ipBlocks, filter, searchTriggersFail);
            default -> throw new IllegalArgumentException("Unknown strategy '" + strategy + "'");
        };
    }
    
    private static void configureYt(@Nonnull AudioPlayerManager manager, @Nonnull Config config, @Nullable AbstractRoutePlanner planner) {
        var yt = manager.source(YoutubeAudioSourceManager.class);
        if(yt == null) {
            return;
        }
        yt.setPlaylistPageCount(config.getInt("lavaplayer.youtube.max-playlist-page-count"));
        if(planner != null) {
            var retryLimit = config.getInt("lavaplayer.youtube.rotation.retry-limit");
           if(retryLimit < 0) {
               YoutubeIpRotator.setup(yt, planner);
           } else if(retryLimit == 0) {
               YoutubeIpRotator.setup(yt, planner, Integer.MAX_VALUE);
           } else {
               YoutubeIpRotator.setup(yt, planner, retryLimit);
           }
        }
    }
    
    public static void main(String[] args) {
        var start = System.nanoTime();
        log.info("Starting andesite version {}, commit {}", Version.VERSION, Version.COMMIT);
        try {
            var type = SystemType.detect();
            log.info("Detected system: {}/{}", type.getOsType(), type.getArchitectureType());
            log.info("CPU info: {}", NativeLibLoader.loadSystemInfo());
        } catch(Throwable t) {
            String message = "Unable to load system info.";
            if(t instanceof UnsatisfiedLinkError || (t instanceof RuntimeException && t.getCause() instanceof UnsatisfiedLinkError)) {
                message += " This is not an error.";
            }
            log.warn(message, t);
        }
        try {
            log.info("Loading native libraries");
            NativeUtils.tryLoad();
            var andesite = createAndesite();
            var config = andesite.config().getConfig("andesite");
            Init.postInit(andesite);
            //NOTE: use the bitwise or operator, as it forces evaluation of all elements
            if(!(RestHandler.setup(andesite)
                         | andesite.pluginManager().startListeners())) {
                log.error("No handlers enabled, aborting");
                System.exit(-1);
            }
            log.info("Handlers: REST {}, WebSocket {}",
                    config.getBoolean("transport.http.rest") ? "enabled" : "disabled",
                    config.getBoolean("transport.http.ws") ? "enabled" : "disabled"
            );
            log.info("Timescale {}", FilterUtil.TIMESCALE_AVAILABLE ? "available" : "unavailable");
            log.info("Started in {} ms", (System.nanoTime() - start) / 1_000_000);
        } catch(Throwable t) {
            log.error("Fatal error during initialization", t);
            System.exit(1);
        }
    }
    
    @Nonnull
    @CheckReturnValue
    private static Andesite createAndesite() throws IOException {
        var rootConfig = ConfigUtil.load();
        var config = rootConfig.getConfig("andesite");
        Init.preInit(config);
        return new Andesite(Vertx.vertx(), rootConfig);
    }
}
