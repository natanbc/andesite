package andesite.node.player;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import net.dv8tion.jda.core.audio.AudioSendHandler;

class AllocatingSendHandler implements AudioSendHandler {
    private final AudioPlayer player;
    private AudioFrame frame;

    AllocatingSendHandler(AudioPlayer player) {
        this.player = player;
    }

    @Override
    public boolean canProvide() {
        return (frame = player.provide()) != null;
    }

    @Override
    public byte[] provide20MsAudio() {
        return frame.getData();
    }
}
