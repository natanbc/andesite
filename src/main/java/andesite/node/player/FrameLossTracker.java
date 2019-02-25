package andesite.node.player;

import andesite.node.util.ByteRingBuffer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;

import javax.annotation.Nonnull;
import java.util.concurrent.TimeUnit;

public class FrameLossTracker extends AudioEventAdapter implements FrameLossCounter {
    private static final long ACCEPTABLE_TRACK_SWITCH_TIME = TimeUnit.MILLISECONDS.toNanos(100);
    private static final long ONE_SECOND = TimeUnit.SECONDS.toNanos(1);
    
    private final ByteRingBuffer loss = new ByteRingBuffer(60);
    private final ByteRingBuffer success = new ByteRingBuffer(60);
    private long playingSince = Long.MAX_VALUE;
    private long trackStart;
    private long lastTrackEnd;
    private long lastUpdate;
    private byte currentLoss;
    private byte currentSuccess;
    
    public void onSuccess() {
        checkTime();
        currentSuccess++;
    }
    
    public void onFail() {
        checkTime();
        currentLoss++;
    }
    
    @Nonnull
    @Override
    public ByteRingBuffer lastMinuteLoss() {
        return loss;
    }
    
    @Nonnull
    @Override
    public ByteRingBuffer lastMinuteSuccess() {
        return success;
    }
    
    @Override
    public boolean isDataUsable() {
        if(trackStart - lastTrackEnd > ACCEPTABLE_TRACK_SWITCH_TIME && lastTrackEnd != 0) {
            return false;
        }
        return TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - playingSince) >= 60;
    }
    
    private void checkTime() {
        var now = System.nanoTime();
        if(now - lastUpdate > ONE_SECOND) {
            lastUpdate = now;
            loss.put(currentLoss);
            success.put(currentSuccess);
            currentLoss = 0;
            currentSuccess = 0;
        }
    }
    
    private void start() {
        trackStart = System.nanoTime();
        if(trackStart - playingSince > ACCEPTABLE_TRACK_SWITCH_TIME || playingSince == Long.MAX_VALUE) {
            playingSince = trackStart;
            loss.clear();
            success.clear();
        }
    }
    
    private void end() {
        lastTrackEnd = System.nanoTime();
    }
    
    @Override
    public void onPlayerPause(AudioPlayer player) {
        end();
    }
    
    @Override
    public void onPlayerResume(AudioPlayer player) {
        start();
    }
    
    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) {
        start();
    }
    
    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        end();
    }
}
