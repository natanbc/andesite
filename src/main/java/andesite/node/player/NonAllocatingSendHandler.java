package andesite.node.player;

import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame;
import net.dv8tion.jda.core.audio.AudioSendHandler;

import java.nio.ByteBuffer;

class NonAllocatingSendHandler implements AudioSendHandler {
    private final AudioPlayer player;
    private final MutableAudioFrame frame;

    NonAllocatingSendHandler(AudioPlayer player) {
        this.player = player;
        this.frame = new MutableAudioFrame();
        this.frame.setBuffer(ByteBuffer.allocate(StandardAudioDataFormats.DISCORD_OPUS.maximumChunkSize()));
    }

    @Override
    public boolean canProvide() {
        return player.provide(frame);
    }

    @Override
    public byte[] provide20MsAudio() {
        return frame.getData();
    }
}
