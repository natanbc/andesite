package andesite.node.player;

import andesite.node.send.AudioProvider;
import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import java.nio.ByteBuffer;

class AllocatingProvider implements AudioProvider {
    private final ByteBuffer buffer = ByteBuffer.allocate(StandardAudioDataFormats.DISCORD_OPUS.maximumChunkSize());
    private final AudioPlayer player;
    private AudioFrame lastFrame;

    public AllocatingProvider(AudioPlayer player) {
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
