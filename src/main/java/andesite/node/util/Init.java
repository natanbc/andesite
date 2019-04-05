package andesite.node.util;

import andesite.node.NodeState;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.typesafe.config.Config;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.management.NotificationEmitter;
import java.lang.management.ManagementFactory;

public class Init {
    public static void preInit(Config config) {
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(
                Level.valueOf(config.getString("log-level").toUpperCase())
        );
        if(config.getBoolean("prometheus.enabled")) {
            PrometheusUtils.setup();
            var listener = new GCListener();
            for(var gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
                if(gcBean instanceof NotificationEmitter) {
                    ((NotificationEmitter) gcBean)
                            .addNotificationListener(listener, null, gcBean);
                }
            }
        }
        if(config.getBoolean("sentry.enabled")) {
            SentryUtils.setup(config);
        }
    }
    
    public static void postInit(@Nonnull NodeState state) {
        if(state.config().getBoolean("andesite.prometheus.enabled")) {
            PrometheusUtils.configureMetrics(state);
        }
        if(state.config().getBoolean("andesite.sentry.enabled")) {
            SentryUtils.configureWarns(state);
        }
    }
}
