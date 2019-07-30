package andesite.node.provider;

import net.dv8tion.jda.api.audio.factory.IAudioSendFactory;
import net.dv8tion.jda.api.audio.factory.IAudioSendSystem;
import net.dv8tion.jda.api.audio.factory.IPacketProvider;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

public class AsyncPacketProviderFactory implements IAudioSendFactory {
    private final IAudioSendFactory factory;
    private final int backlog;
    
    public AsyncPacketProviderFactory(IAudioSendFactory factory, int backlog) {
        this.factory = factory;
        this.backlog = backlog;
    }
    
    public AsyncPacketProviderFactory(IAudioSendFactory factory) {
        this(factory, CommonAsync.DEFAULT_BACKLOG);
    }
    
    @Override
    public IAudioSendSystem createSendSystem(IPacketProvider packetProvider) {
        AtomicReference<Future<?>> taskRef = new AtomicReference<>();
        
        AsyncPacketProvider provider = new AsyncPacketProvider(packetProvider, backlog, taskRef);
        return new AsyncAudioSendSystemWrapper(
                this.factory.createSendSystem(provider),
                taskRef
        );
    }
}
