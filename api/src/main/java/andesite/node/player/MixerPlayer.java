package andesite.node.player;

import andesite.node.player.filter.FilterChainConfiguration;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;

public interface MixerPlayer {
    FilterChainConfiguration filterConfig();

    AudioPlayer audioPlayer();
}
