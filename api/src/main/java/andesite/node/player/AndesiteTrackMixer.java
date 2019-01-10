package andesite.node.player;

import java.util.Map;

public interface AndesiteTrackMixer {
    Map<String, MixerPlayer> players();

    MixerPlayer getPlayer(String key);

    void destroy();
}
