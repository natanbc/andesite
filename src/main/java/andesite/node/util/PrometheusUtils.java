package andesite.node.util;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.prometheus.client.hotspot.DefaultExports;
import io.prometheus.client.logback.InstrumentedAppender;
import org.slf4j.LoggerFactory;

class PrometheusUtils {
    static void setup() {
        var prometheusAppender = new InstrumentedAppender();

        var factory = (LoggerContext) LoggerFactory.getILoggerFactory();
        var root = factory.getLogger(Logger.ROOT_LOGGER_NAME);
        prometheusAppender.setContext(root.getLoggerContext());
        prometheusAppender.start();
        root.addAppender(prometheusAppender);


        DefaultExports.initialize();
    }
}
