package andesite.node.player;

import andesite.node.util.ByteRingBuffer;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;

public interface FrameLossCounter {
    /**
     * Packet count expected to be sent over a minute.
     *
     * <br><br>3000 packets with 20ms of audio each.
     */
    int EXPECTED_PACKET_COUNT_PER_MIN = (60 * 1000) / 20;
    
    /**
     * Returns the amount of frames lost per second over the past minute.
     * Each entry corresponds to one second. Ideally, all values should be 0.
     *
     * <br><br>This buffer should not be modified.
     *
     * <br><br>This data is only valid if {@link #isDataUsable()} returns true.
     *
     * @return The amount of frames lost over the past minute.
     */
    @Nonnull
    @CheckReturnValue
    ByteRingBuffer lastMinuteLoss();
    
    /**
     * Returns the amount of frames sent per second over the past minute.
     * Each entry corresponds to one second. Ideally, all values should be 30.
     *
     * <br><br>This buffer should not be modified.
     *
     * <br><br>This data is only valid if {@link #isDataUsable()} returns true.
     *
     * @return The amount of frames sent over the past minute.
     */
    @Nonnull
    @CheckReturnValue
    ByteRingBuffer lastMinuteSuccess();
    
    /**
     * Returns whether or not enough data has been gathered for use.
     *
     * @return {@code true} if there's enough data stored.
     */
    @CheckReturnValue
    boolean isDataUsable();
}
