package andesite.node.util;

import andesite.node.NodeState;
import andesite.node.Version;
import andesite.node.config.Config;
import andesite.node.event.AndesiteEventListener;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.filter.ThresholdFilter;
import io.sentry.Sentry;
import io.sentry.SentryClient;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import io.sentry.logback.SentryAppender;
import io.sentry.util.Util;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

class SentryUtils {
    private static final String SENTRY_APPENDER_NAME = "SENTRY";
    private static SentryClient client;

    static void setup(Config config) {
        var client = Sentry.init(config.get("sentry.dsn"));
        client.setRelease(Version.VERSION);
        var tags = config.get("sentry.tags");
        if(tags != null) {
            client.setTags(Util.parseTags(tags));
        }
        SentryUtils.client = client;
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

    static void configureWarns(NodeState state) {
        state.dispatcher().register(new AndesiteEventListener() {
            @Override
            public void onWebSocketClosed(@Nonnull NodeState state, @Nonnull String userId,
                                          @Nonnull String guildId, int closeCode,
                                          @Nullable String reason, boolean byRemote) {
                if(byRemote) {
                    client.sendEvent(new EventBuilder()
                            .withLevel(Event.Level.WARNING)
                            .withMessage("Websocket closed by server")
                            .withExtra("code", closeCode)
                            .withExtra("reason", reason)
                            .withExtra("user", userId)
                            .withExtra("guild", guildId)
                    );
                } else {
                    client.sendEvent(new EventBuilder()
                            .withLevel(Event.Level.INFO)
                            .withMessage("Websocket closed by client")
                            .withExtra("code", closeCode)
                            .withExtra("reason", reason)
                            .withExtra("user", userId)
                            .withExtra("guild", guildId)
                    );
                }
            }
        });
    }
}
