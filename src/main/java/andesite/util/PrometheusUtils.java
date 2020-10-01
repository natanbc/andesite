package andesite.util;

import andesite.NodeState;
import andesite.event.AndesiteEventListener;
import andesite.player.AndesitePlayer;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.typesafe.config.Config;
import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.prometheus.client.hotspot.BufferPoolsExports;
import io.prometheus.client.hotspot.ClassLoadingExports;
import io.prometheus.client.hotspot.DefaultExports;
import io.prometheus.client.hotspot.GarbageCollectorExports;
import io.prometheus.client.hotspot.MemoryAllocationExports;
import io.prometheus.client.hotspot.MemoryPoolsExports;
import io.prometheus.client.hotspot.StandardExports;
import io.prometheus.client.hotspot.VersionInfoExports;
import io.prometheus.client.logback.InstrumentedAppender;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.List;

class PrometheusUtils {
    static Duration updatePeriod = Duration.ofSeconds(3);
    
    static void setup(Config config) {
        updatePeriod = config.getDuration("prometheus.update-period");
        
        var prometheusAppender = new InstrumentedAppender();
        
        var factory = (LoggerContext) LoggerFactory.getILoggerFactory();
        var root = factory.getLogger(Logger.ROOT_LOGGER_NAME);
        prometheusAppender.setContext(root.getLoggerContext());
        prometheusAppender.start();
        root.addAppender(prometheusAppender);
        
        //same as DefaultExports.initialize() but doesn't add the
        //thread exports, because that stops all java threads every time
        //it's measured
        new StandardExports().register();
        new MemoryPoolsExports().register();
        new MemoryAllocationExports().register();
        new BufferPoolsExports().register();
        new GarbageCollectorExports().register();
        new ClassLoadingExports().register();
        new VersionInfoExports().register();
        
        JFRExports.register();
    }
    
    static void configureMetrics(@Nonnull NodeState state) {
        CollectorRegistry.defaultRegistry.register(new UptimeCollector());
        
        var players = Gauge.build()
                .namespace("andesite")
                .name("players")
                .help("Number of players alive at a given point")
                .register();
        
        state.dispatcher().register(new AndesiteEventListener() {
            @Override
            public void onPlayerCreated(@Nonnull NodeState state, @Nonnull String userId,
                                        @Nonnull String guildId, @Nonnull AndesitePlayer player) {
                players.inc();
            }
            
            @Override
            public void onPlayerDestroyed(@Nonnull NodeState state, @Nonnull String userId,
                                          @Nonnull String guildId, @Nonnull AndesitePlayer player) {
                players.dec();
            }
        });
    }
    
    private static class UptimeCollector extends Collector {
        private final Gauge gauge = Gauge.build()
                .namespace("andesite")
                .name("uptime")
                .help("Uptime of the andesite JVM")
                .create();
        
        @Override
        public List<MetricFamilySamples> collect() {
            gauge.set(ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0);
            return gauge.collect();
        }
    }
}
