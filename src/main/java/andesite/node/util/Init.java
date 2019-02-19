package andesite.node.util;

import andesite.node.NodeState;
import andesite.node.config.Config;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.management.NotificationEmitter;
import java.lang.management.ManagementFactory;

public class Init {
    public static void preInit(Config config) {
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(
                Level.valueOf(config.get("log-level", "INFO").toUpperCase())
        );
        if(config.getBoolean("prometheus.enabled", false)) {
            PrometheusUtils.setup();
            var listener = new GCListener();
            for(var gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
                if(gcBean instanceof NotificationEmitter) {
                    ((NotificationEmitter)gcBean)
                            .addNotificationListener(listener, null, gcBean);
                }
            }
        }
        if(config.get("sentry.dsn") != null) {
            SentryUtils.setup(config);
        }
    }

    public static void postInit(@Nonnull NodeState state) {
        if(state.config().getBoolean("prometheus.enabled", false)) {
            PrometheusUtils.configureMetrics(state);
        }
        if(state.config().get("sentry.dsn") != null) {
            SentryUtils.configureWarns(state);
        }
    }
}
