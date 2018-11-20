package andesite.node.util;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.io.MessageInput;
import com.sedmelluq.discord.lavaplayer.tools.io.MessageOutput;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.function.Function;

public class RequestUtils {
    @Nonnull
    @CheckReturnValue
    public static AudioTrack decodeTrack(@Nonnull AudioPlayerManager playerManager, @Nonnull String base64) {
        try {
            return playerManager.decodeTrack(new MessageInput(new ByteArrayInputStream(
                    Base64.getDecoder().decode(base64)
            ))).decodedTrack;
        } catch(IOException e) {
            throw new AssertionError(e);
        }
    }

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

    @Nonnull
    @CheckReturnValue
    public static JsonObject encodeThrowable(@Nonnull Throwable throwable) {
        var json = new JsonObject()
                .put("class", throwable.getClass().getName())
                .put("message", throwable.getMessage())
                .put("suppressed", encodeArray(throwable.getSuppressed(), RequestUtils::encodeThrowable))
                .put("stack", encodeArray(throwable.getStackTrace(), RequestUtils::encodeStackFrame));
        var cause = throwable.getCause();
        if(cause != null) {
            json.put("cause", encodeThrowable(cause));
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
