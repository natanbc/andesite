package andesite.send;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import java.nio.ByteBuffer;

/**
 * Used to provide audio for an {@link AudioHandler}. The format the provided audio should be
 * depends on the handler. Currently only opus audio is supported.
 */
public interface AudioProvider extends AutoCloseable {
    /**
     * Attempts to load the next audio frame, returning true if there is one.
     *
     * <br>If this method returns true, the frame data should be returned in the next
     * {@link #provide() provide} call.
     *
     * @return Whether or not an audio frame was loaded.
     */
    @CheckReturnValue
    boolean canProvide();
    
    /**
     * Returns the data for the latest frame.
     *
     * @return Buffer containing the frame's data.
     */
    @Nonnull
    @CheckReturnValue
    ByteBuffer provide();
    
    /**
     * Runs any cleanup actions. Called when this provider is removed with
     * {@link AudioHandler#setProvider(String, String, AudioProvider)} or
     * {@link AudioHandler#closeConnection(String, String)}.
     *
     * <br>This method may be called more than once if this provider is added
     * again to the audio handler, but will be called only once each time it is removed.
     */
    void close();
}
