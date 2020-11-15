package andesite.player;

import andesite.send.AudioProvider;
import com.sedmelluq.discord.lavaplayer.player.AudioConfiguration;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import java.nio.ByteBuffer;

class AllocatingProvider implements AudioProvider {
    private final ByteBuffer buffer;
    private final AudioPlayer player;
    private AudioFrame lastFrame;
    
    public AllocatingProvider(AudioPlayer player, AudioConfiguration configuration) {
        this.buffer = ByteBuffer.allocate(configuration.getOutputFormat().maximumChunkSize());
        this.player = player;
    }
    
    @CheckReturnValue
    @Override
    public boolean canProvide() {
        return (lastFrame = player.provide()) != null;
    }
    
    @CheckReturnValue
    @Nonnull
    @Override
    public ByteBuffer provide() {
        lastFrame.getData(buffer.array(), 0);
        return buffer.position(0).limit(lastFrame.getDataLength());
    }
    
    @Override
    public void close() {
        //noop
    }
}
