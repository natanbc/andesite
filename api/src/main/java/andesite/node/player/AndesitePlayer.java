package andesite.node.player;

import andesite.node.player.filter.FilterChainConfiguration;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import io.vertx.core.json.JsonObject;

public interface AndesitePlayer {
    FilterChainConfiguration filterConfig();

    AudioPlayerManager audioPlayerManager();

    String guildId();

    String userId();

    AudioPlayer audioPlayer();

    AndesiteTrackMixer mixer();

    void switchToMixer();

    void switchToSingle();

    void destroy();

    JsonObject encodeState();
}
