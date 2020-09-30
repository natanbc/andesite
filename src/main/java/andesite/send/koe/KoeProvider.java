package andesite.send.koe;

import andesite.send.AudioProvider;
import io.netty.buffer.ByteBuf;
import moe.kyokobot.koe.MediaConnection;
import moe.kyokobot.koe.media.OpusAudioFrameProvider;

public class KoeProvider extends OpusAudioFrameProvider {
    final AudioProvider source;
    
    public KoeProvider(MediaConnection connection, AudioProvider source) {
        super(connection);
        this.source = source;
    }
    
    @Override
    public boolean canProvide() {
        return source.canProvide();
    }
    
    @Override
    public void retrieveOpusFrame(ByteBuf targetBuffer) {
        targetBuffer.writeBytes(source.provide());
    }
    
    @Override
    public void dispose() {
        source.close();
        super.dispose();
    }
}
