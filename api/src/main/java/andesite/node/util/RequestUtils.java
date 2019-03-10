package andesite.node.util;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.io.MessageInput;
import com.sedmelluq.discord.lavaplayer.tools.io.MessageOutput;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.function.Function;

public class RequestUtils {
    /**
     * Attempts to find a password in a request.
     *
     * The password will be read from the following locations, in order, with
     * the first one present being returned.
     *
     * <ul>
     * <li>The {@code Authorization} header</li>
     * <li>A websocket protocol which starts with {@code andesite-password:}
     * (the returned password will have this prefix stripped)</li>
     * <li>The {@code password} query param</li>
     * </ul>
     *
     * If the websocket protocol is matched, it's value will be inserted into the
     * {@code Sec-WebSocket-Protocol} header of the response.
     *
     * @param context Context where the password should be located.
     *
     * @return The password located, or null if nothing was found.
     */
    @Nullable
    @CheckReturnValue
    public static String findPassword(@Nonnull RoutingContext context) {
        var authHeader = context.request().getHeader("Authorization");
        if(authHeader != null) {
            return authHeader;
        }
        
        //allow browser access
        //the browser websocket api doesn't support custom headers,
        //so this hack is needed.
        //browsers can use new WebSocket(url, "andesite-password:" + password)
        var wsHeader = context.request().getHeader("Sec-WebSocket-Protocol");
        if(wsHeader != null) {
            var parts = wsHeader.split(",");
            for(var part : parts) {
                if(part.startsWith("andesite-password:")) {
                    context.response().putHeader("Sec-WebSocket-Protocol", part);
                    return part.substring("andesite-password:".length());
                }
            }
        }
        
        var query = context.queryParam("password");
        
        return query.isEmpty() ? null : query.get(0);
    }
    
    /**
     * Decodes an audio track from it's base64 representation.
     *
     * @param playerManager Player manager used for decoding (must have the source manager enabled).
     * @param base64        Base64 encoded track.
     *
     * @return The decoded track.
     */
    @Nullable
    @CheckReturnValue
    public static AudioTrack decodeTrack(@Nonnull AudioPlayerManager playerManager, @Nonnull String base64) {
        try {
            var v = playerManager.decodeTrack(new MessageInput(new ByteArrayInputStream(
                Base64.getDecoder().decode(base64)
            )));
            return v == null ? null : v.decodedTrack;
        } catch(IOException e) {
            throw new AssertionError(e);
        }
    }
    
    /**
     * Encodes the provided track to base64.
     *
     * @param playerManager Player manager used for encoding (must have the source manager enabled).
     * @param track         Track to encode.
     *
     * @return Base64 encoded track.
     */
    @Nonnull
    @CheckReturnValue
    public static String trackString(@Nonnull AudioPlayerManager playerManager, @Nonnull AudioTrack track) {
        var baos = new ByteArrayOutputStream();
        try {
            playerManager.encodeTrack(new MessageOutput(baos), track);
        } catch(IOException e) {
            throw new AssertionError(e);
        }
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }
    
    /**
     * Encodes a track into a json object, useful for sending to clients.
     *
     * @param playerManager Player manager used to encode the track.
     * @param track         Track to encode.
     *
     * @return A json object containing information about the track.
     */
    @Nonnull
    @CheckReturnValue
    public static JsonObject encodeTrack(@Nonnull AudioPlayerManager playerManager, @Nonnull AudioTrack track) {
        var info = track.getInfo();
        return new JsonObject()
            .put("track", trackString(playerManager, track))
            .put("info", new JsonObject()
                .put("class", track.getClass().getName())
                .put("title", info.title)
                .put("author", info.author)
                .put("length", info.length)
                .put("identifier", info.identifier)
                .put("uri", info.uri)
                .put("isStream", info.isStream)
                .put("isSeekable", track.isSeekable())
                .put("position", track.getPosition())
            );
    }
    
    /**
     * Encodes the {@link RoutingContext#failure() failure} of the context.
     * Equivalent to {@code encodeThrowable(context, context.failure())}
     *
     * @param context Context of the request.
     *
     * @return An encoded version of the exception.
     *
     * @see #encodeThrowable(RoutingContext, Throwable)
     */
    @Nonnull
    @CheckReturnValue
    public static JsonObject encodeFailure(@Nonnull RoutingContext context) {
        return encodeThrowable(context, context.failure());
    }
    
    /**
     * Encodes the provided throwable, based on the context for deciding whether
     * or not to use a shorter version with less details (omit stacktraces).
     *
     * @param context   Context for the request.
     * @param throwable Error to encode.
     *
     * @return An encoded version of the exception.
     */
    @Nonnull
    @CheckReturnValue
    public static JsonObject encodeThrowable(@Nonnull RoutingContext context, @Nonnull Throwable throwable) {
        return useShortMessage(context) ?
            RequestUtils.encodeThrowableShort(throwable) :
            RequestUtils.encodeThrowableDetailed(throwable);
    }
    
    private static boolean useShortMessage(@Nonnull RoutingContext context) {
        return context.request().getHeader("Andesite-Short-Errors") != null ||
            context.queryParams().contains("shortErrors");
    }
    
    /**
     * Encodes a throwable with minimal details (omits stacktraces).
     *
     * @param throwable Error to encode.
     *
     * @return An encoded version of the exception.
     */
    @Nonnull
    @CheckReturnValue
    public static JsonObject encodeThrowableShort(@Nonnull Throwable throwable) {
        var json = new JsonObject()
            .put("class", throwable.getClass().getName())
            .put("message", throwable.getMessage())
            .put("suppressed", encodeArray(throwable.getSuppressed(), RequestUtils::encodeThrowableShort));
        var cause = throwable.getCause();
        if(cause != null) {
            json.put("cause", encodeThrowableShort(cause));
        } else {
            json.putNull("cause");
        }
        return json;
    }
    
    /**
     * Encodes a throwable with as many details as possible. The resulting object may
     * be very large.
     *
     * @param throwable Error to encode.
     *
     * @return An encoded version of the exception.
     */
    @Nonnull
    @CheckReturnValue
    public static JsonObject encodeThrowableDetailed(@Nonnull Throwable throwable) {
        var json = new JsonObject()
            .put("class", throwable.getClass().getName())
            .put("message", throwable.getMessage())
            .put("suppressed", encodeArray(throwable.getSuppressed(), RequestUtils::encodeThrowableDetailed))
            .put("stack", encodeArray(throwable.getStackTrace(), RequestUtils::encodeStackFrame));
        var cause = throwable.getCause();
        if(cause != null) {
            json.put("cause", encodeThrowableDetailed(cause));
        } else {
            json.putNull("cause");
        }
        return json;
    }
    
    @Nonnull
    @CheckReturnValue
    private static JsonObject encodeStackFrame(@Nonnull StackTraceElement element) {
        return new JsonObject()
            .put("classLoader", element.getClassLoaderName())
            .put("moduleName", element.getModuleName())
            .put("moduleVersion", element.getModuleVersion())
            .put("className", element.getClassName())
            .put("methodName", element.getMethodName())
            .put("fileName", element.getFileName())
            .put("lineNumber", element.getLineNumber() < 0 ? null : element.getLineNumber())
            .put("pretty", element.toString());
    }
    
    @Nonnull
    @CheckReturnValue
    private static <T> JsonArray encodeArray(@Nonnull T[] array,
                                             @Nonnull Function<T, JsonObject> serializeFunction) {
        var json = new JsonArray();
        for(var t : array) {
            json.add(serializeFunction.apply(t));
        }
        return json;
    }
}
