package andesite.node.player;

import andesite.node.send.AudioProvider;
import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.function.Function;

class NonAllocatingProvider implements AudioProvider {
    private static final Function<MutableAudioFrame, ByteBuffer> INTERNAL_BUFFER_GETTER;
    
    private final ByteBuffer buffer = ByteBuffer.allocate(StandardAudioDataFormats.DISCORD_OPUS.maximumChunkSize());
    private final MutableAudioFrame frame = new MutableAudioFrame();
    private final AudioPlayer player;
    
    static {
        try {
            Field f = MutableAudioFrame.class.getDeclaredField("frameBuffer");
            f.setAccessible(true);
            INTERNAL_BUFFER_GETTER = frame -> {
                try {
                    return (ByteBuffer) f.get(frame);
                } catch(Exception impossible) {
                    throw new AssertionError(impossible);
                }
            };
        } catch(Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    
    public NonAllocatingProvider(AudioPlayer player) {
        this.player = player;
        frame.setBuffer(buffer);
    }
    
    @Override
    public boolean canProvide() {
        buffer.clear();
        return player.provide(frame);
    }
    
    @Override
    public ByteBuffer provide() {
        if(INTERNAL_BUFFER_GETTER.apply(frame) != buffer) {
            frame.getData(buffer.array(), frame.getDataLength());
            //hopefully this copy won't be needed for the next frame
            //and data will be directly written to the provider's buffer.
            //this call is pretty much free, and might help preventing
            //more copies for future frames.
            frame.setBuffer(buffer.limit(buffer.capacity()));
        }
        return buffer.position(0).limit(frame.getDataLength());
    }
    
    @Override
    public void close() {
        //noop
    }
}
