package andesite.util;

import andesite.NodeState;
import andesite.Version;
import andesite.event.AndesiteEventListener;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.filter.ThresholdFilter;
import com.typesafe.config.Config;
import io.sentry.Sentry;
import io.sentry.SentryClient;
import io.sentry.SentryOptions;
import io.sentry.config.Lookup;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import io.sentry.logback.SentryAppender;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class SentryUtils {
    private static final Map<String, String> EXTRA_OPTIONS = Map.of(
            "stacktrace.app.packages", "andesite"
    );
    private static final String SENTRY_APPENDER_NAME = "SENTRY";
    private static SentryClient client;
    
    static void setup(Config config) {
        var options = SentryOptions.defaults(config.getString("sentry.dsn"));
        options.setLookup(Lookup.getDefaultWithAdditionalProviders(
                List.of(EXTRA_OPTIONS::get),
                List.of()
        ));
        var client = Sentry.init(options);
        client.setRelease(Version.VERSION);
        client.setTags(parseTags(config.getStringList("sentry.tags")));
        SentryUtils.client = client;
        var loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        var root = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        
        var sentryAppender = (SentryAppender) root.getAppender(SENTRY_APPENDER_NAME);
        if(sentryAppender == null) {
            sentryAppender = new SentryAppender();
            sentryAppender.setName(SENTRY_APPENDER_NAME);
            sentryAppender.start();
            
            var warningsOrAboveFilter = new ThresholdFilter();
            warningsOrAboveFilter.setLevel(config.getString("sentry.log-level").toUpperCase());
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
    
    @Nonnull
    @CheckReturnValue
    private static Map<String, String> parseTags(@Nonnull List<String> tags) {
        var map = new HashMap<String, String>();
        for(var tag : tags) {
            var split = tag.split(":", 2);
            if(split.length != 2) {
                throw new IllegalArgumentException("Invalid tags entry: " + tag);
            }
            map.put(split[0], split[1]);
        }
        return map;
    }
}
