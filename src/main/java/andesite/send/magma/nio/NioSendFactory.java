package andesite.send.magma.nio;

import io.vertx.core.Vertx;
import net.dv8tion.jda.api.audio.factory.IAudioSendFactory;
import net.dv8tion.jda.api.audio.factory.IAudioSendSystem;
import net.dv8tion.jda.api.audio.factory.IPacketProvider;

public class NioSendFactory implements IAudioSendFactory {
    private final Vertx vertx;
    
    public NioSendFactory(Vertx vertx) {
        this.vertx = vertx;
    }
    
    @Override
    public IAudioSendSystem createSendSystem(IPacketProvider packetProvider) {
        return new NioSendSystem(vertx, packetProvider);
    }
}
