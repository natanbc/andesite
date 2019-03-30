package andesite.node.player;

import andesite.node.NodeState;
import andesite.node.player.filter.FilterChainConfiguration;
import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.format.transcoder.OpusChunkEncoder;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame;
import io.vertx.core.json.JsonObject;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TrackMixer implements AndesiteTrackMixer {
    private final Map<String, Player> players = new ConcurrentHashMap<>();
    private final ShortBuffer mixBuffer = ByteBuffer.allocateDirect(StandardAudioDataFormats.DISCORD_PCM_S16_BE.maximumChunkSize())
            .order(ByteOrder.nativeOrder())
            .asShortBuffer();
    private final ByteBuffer outputBuffer = ByteBuffer.allocate(StandardAudioDataFormats.DISCORD_OPUS.maximumChunkSize());
    
    private final AudioPlayerManager playerManager;
    private final AndesitePlayer parent;
    private final OpusChunkEncoder encoder;
    
    public TrackMixer(AudioPlayerManager playerManager, AndesitePlayer parent) {
        this.playerManager = playerManager;
        this.encoder = new OpusChunkEncoder(playerManager.getConfiguration(), StandardAudioDataFormats.DISCORD_OPUS);
        this.parent = parent;
    }
    
    @SuppressWarnings("unchecked")
    @Nonnull
    @CheckReturnValue
    @Override
    public Map<String, MixerPlayer> players() {
        return (Map) players;
    }
    
    @Nonnull
    @CheckReturnValue
    @Override
    public MixerPlayer getPlayer(@Nonnull String key) {
        return players.computeIfAbsent(key, k -> new Player(playerManager.createPlayer(), parent, k));
    }
    
    @Override
    public MixerPlayer getExistingPlayer(@Nonnull String key) {
        return players.get(key);
    }
    
    @Override
    public void removePlayer(@Nonnull String key) {
        var p = players.remove(key);
        if(p != null) {
            p.player.destroy();
        }
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
    public ByteBuffer provide() {
        var buffer = mixBuffer; //avoid getfield opcode
        buffer.clear().position(0);
        for(var p : players.values()) {
            if(p.provided) {
                var s = p.buffer.position(0).asShortBuffer();
                
                //http://atastypixel.com/blog/how-to-mix-audio-samples-properly-on-ios/
                for(int i = 0; i < s.capacity(); i++) {
                    var a = buffer.get(i);
                    var b = s.get(i);
                    if(a < 0 && b < 0) {
                        buffer.put(i, (short) ((a + b) - ((a * b) / Short.MIN_VALUE)));
                    } else if(a > 0 && b > 0) {
                        buffer.put(i, (short) ((a + b) - ((a * b) / Short.MAX_VALUE)));
                    } else {
                        buffer.put(i, (short) (a + b));
                    }
                }
            }
        }
        buffer.flip();
        
        encoder.encode(buffer, outputBuffer.position(0).limit(outputBuffer.capacity()));
        buffer.flip();
        return outputBuffer;
    }
    
    @Override
    public void close() {
        players.values().forEach(p -> p.player.destroy());
        encoder.close();
    }
    
    private static class Player implements MixerPlayer {
        private final ByteBuffer buffer = ByteBuffer.allocate(StandardAudioDataFormats.DISCORD_PCM_S16_BE.maximumChunkSize())
                .order(ByteOrder.BIG_ENDIAN);
        private final MutableAudioFrame frame = new MutableAudioFrame();
        private final FrameLossTracker frameLossTracker = new FrameLossTracker();
        private final FilterChainConfiguration filterConfig = new FilterChainConfiguration();
        private final AudioPlayer player;
        private final AndesitePlayer parent;
        private final String key;
        private boolean provided;
        private int framesWithoutProvide;
        
        Player(AudioPlayer player, AndesitePlayer parent, String key) {
            this.player = player;
            this.parent = parent;
            this.key = key;
            frame.setBuffer(buffer);
            buffer.limit(frame.getDataLength());
            this.player.addListener(frameLossTracker);
        }
        
        boolean tryProvide() {
            provided = player.provide(frame);
            if(provided) {
                framesWithoutProvide = 0;
                frameLossTracker.onSuccess();
            } else {
                framesWithoutProvide++;
                frameLossTracker.onFail();
            }
            return provided;
        }
        
        @Nonnull
        @Override
        public NodeState node() {
            return parent.node();
        }
        
        @Nonnull
        @Override
        public String userId() {
            return parent.userId();
        }
        
        @Nonnull
        @Override
        public String guildId() {
            return parent.guildId();
        }
        
        @Nonnull
        @Override
        public FrameLossCounter frameLossCounter() {
            return frameLossTracker;
        }
        
        @Nonnull
        @CheckReturnValue
        @Override
        public FilterChainConfiguration filterConfig() {
            return filterConfig;
        }
        
        @Nonnull
        @CheckReturnValue
        @Override
        public AudioPlayer audioPlayer() {
            return player;
        }
        
        @Nonnull
        @Override
        public JsonObject encodeState() {
            var track = player.getPlayingTrack();
            return new JsonObject()
                    .put("time", String.valueOf(Instant.now().toEpochMilli()))
                    .put("position", track == null ? null : track.getPosition())
                    .put("paused", player.isPaused())
                    .put("volume", player.getVolume())
                    .put("filters", filterConfig.encode())
                    .put("frame", new JsonObject()
                            .put("loss", frameLossTracker.lastMinuteLoss().sum())
                            .put("success", frameLossTracker.lastMinuteSuccess().sum())
                            .put("usable", frameLossTracker.isDataUsable())
                    );
        }
        
        @Nonnull
        @Override
        public AndesitePlayer parentPlayer() {
            return parent;
        }
        
        @Nonnull
        @Override
        public String key() {
            return key;
        }
    }
}
