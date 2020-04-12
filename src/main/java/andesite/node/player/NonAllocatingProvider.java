package andesite.node.player;

import andesite.node.send.AudioProvider;
import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import java.nio.ByteBuffer;

class NonAllocatingProvider implements AudioProvider {
    private final ByteBuffer buffer = ByteBuffer.allocate(StandardAudioDataFormats.DISCORD_OPUS.maximumChunkSize());
    private final MutableAudioFrame frame = new MutableAudioFrame();
    private final AudioPlayer player;

    public NonAllocatingProvider(AudioPlayer player) {
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
