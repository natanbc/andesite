package andesite.node.player;

import andesite.node.NodeState;
import andesite.node.player.filter.FilterChainConfiguration;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import io.vertx.core.json.JsonObject;

public interface AndesitePlayer {
    NodeState node();

    FilterChainConfiguration filterConfig();

    String guildId();

    String userId();

    AudioPlayer audioPlayer();

    AndesiteTrackMixer mixer();

    void switchToMixer();

    void switchToSingle();

    void destroy();

    JsonObject encodeState();
}
