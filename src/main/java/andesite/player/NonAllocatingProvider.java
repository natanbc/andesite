package andesite.player;

import andesite.send.AudioProvider;
import com.sedmelluq.discord.lavaplayer.player.AudioConfiguration;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import java.nio.ByteBuffer;

class NonAllocatingProvider implements AudioProvider {
    private final MutableAudioFrame frame = new MutableAudioFrame();
    private final ByteBuffer buffer;
    private final AudioPlayer player;
    
    public NonAllocatingProvider(AudioPlayer player, AudioConfiguration configuration) {
        this.buffer = ByteBuffer.allocate(configuration.getOutputFormat().maximumChunkSize());
        this.player = player;
        frame.setBuffer(buffer);
    }
    
    @CheckReturnValue
    @Override
    public boolean canProvide() {
        buffer.clear();
        return player.provide(frame);
    }
    
    @CheckReturnValue
    @Nonnull
    @Override
    public ByteBuffer provide() {
        return buffer.position(0).limit(frame.getDataLength());
    }
    
    @Override
    public void close() {
        //noop
    }
}
