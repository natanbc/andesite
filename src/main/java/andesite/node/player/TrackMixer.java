package andesite.node.player;

import andesite.node.player.filter.FilterChainConfiguration;
import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.format.transcoder.OpusChunkEncoder;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame;
import net.dv8tion.jda.core.audio.AudioSendHandler;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TrackMixer implements AudioSendHandler, AutoCloseable, AndesiteTrackMixer {
    private final Map<String, Player> players = new ConcurrentHashMap<>();
    private final ShortBuffer mixBuffer = ByteBuffer.allocateDirect(StandardAudioDataFormats.DISCORD_PCM_S16_BE.maximumChunkSize())
            .order(ByteOrder.nativeOrder())
            .asShortBuffer();

    private final AudioPlayerManager playerManager;
    private final OpusChunkEncoder encoder;

    public TrackMixer(AudioPlayerManager playerManager) {
        this.playerManager = playerManager;
        this.encoder = new OpusChunkEncoder(playerManager.getConfiguration(), StandardAudioDataFormats.DISCORD_OPUS);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, MixerPlayer> players() {
        return (Map)players;
    }

    @Override
    public MixerPlayer getPlayer(String key) {
        return players.computeIfAbsent(key, __ -> new Player(playerManager.createPlayer()));
    }

    @Override
    public void destroy() {
        close();
    }

    @Override
    public boolean canProvide() {
        var v = false;
        for(var p : players.values()) {
            v |= p.tryProvide();
        }
        players.values().removeIf(p -> {
            var notPlaying = p.player.getPlayingTrack() == null && p.framesWithoutProvide > 250; //5 seconds
            if(notPlaying) {
                p.player.destroy();
            }
            return notPlaying;
        });
        return v;
    }

    @Override
    public byte[] provide20MsAudio() {
        mixBuffer.clear().position(0);
        for(var p : players.values()) {
            if(p.provided) {
                var s = p.buffer.position(0).asShortBuffer();

                for (int i = 0; i < s.capacity(); i++) {
                    mixBuffer.put(i, (short)((mixBuffer.get(i) + s.get(i))/2));
                }
            }
        }
        mixBuffer.flip();

        var v = encoder.encode(mixBuffer);
        mixBuffer.flip();
        return v;
    }

    @Override
    public boolean isOpus() {
        return true;
    }

    @Override
    public void close() {
        players.values().forEach(p -> p.player.destroy());
        encoder.close();
    }

    public static class Player implements MixerPlayer {
        private final ByteBuffer buffer = ByteBuffer.allocate(StandardAudioDataFormats.DISCORD_PCM_S16_BE.maximumChunkSize())
                .order(ByteOrder.BIG_ENDIAN);
        private final MutableAudioFrame frame = new MutableAudioFrame();
        private final FilterChainConfiguration filterConfig = new FilterChainConfiguration();
        private final AudioPlayer player;
        private boolean provided;
        private int framesWithoutProvide;

        Player(AudioPlayer player) {
            this.player = player;
            frame.setBuffer(buffer);
            buffer.limit(frame.getDataLength());
        }

        public FilterChainConfiguration filterConfig() {
            return filterConfig;
        }

        public AudioPlayer audioPlayer() {
            return player;
        }

        boolean tryProvide() {
            provided = player.provide(frame);
            if(provided) {
                framesWithoutProvide = 0;
            } else {
                framesWithoutProvide++;
            }
            return provided;
        }
    }
}
