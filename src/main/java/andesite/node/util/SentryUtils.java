package andesite.node.util;

import andesite.node.Version;
import andesite.node.config.Config;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.filter.ThresholdFilter;
import io.sentry.Sentry;
import io.sentry.logback.SentryAppender;
import io.sentry.util.Util;
import org.slf4j.LoggerFactory;

class SentryUtils {
    private static final String SENTRY_APPENDER_NAME = "SENTRY";

    static void setup(Config config) {
        var client = Sentry.init(config.get("sentry.dsn"));
        client.setRelease(Version.VERSION);
        var tags = config.get("sentry.tags");
        if(tags != null) {
            client.setTags(Util.parseTags(tags));
        }
        var loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        var root = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);

        var sentryAppender = (SentryAppender) root.getAppender(SENTRY_APPENDER_NAME);
        if(sentryAppender == null) {
            sentryAppender = new SentryAppender();
            sentryAppender.setName(SENTRY_APPENDER_NAME);

            var warningsOrAboveFilter = new ThresholdFilter();
            warningsOrAboveFilter.setLevel(config.get("sentry.log-level", Level.WARN.levelStr).toUpperCase());
            warningsOrAboveFilter.start();
            sentryAppender.addFilter(warningsOrAboveFilter);

            sentryAppender.setContext(loggerContext);
            root.addAppender(sentryAppender);
        }
    }
}
