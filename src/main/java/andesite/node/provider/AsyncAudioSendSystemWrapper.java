package andesite.node.provider;

import net.dv8tion.jda.api.audio.factory.IAudioSendSystem;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

class AsyncAudioSendSystemWrapper implements IAudioSendSystem {
    private final IAudioSendSystem wrapped;
    private final AtomicReference<Future<?>> taskRef;

    AsyncAudioSendSystemWrapper(IAudioSendSystem wrapped, AtomicReference<Future<?>> taskRef) {
        this.wrapped = wrapped;
        this.taskRef = taskRef;
    }

    @Override
    public void start() {
        this.wrapped.start();
    }

    @Override
    public void shutdown() {
        this.taskRef.updateAndGet(value -> {
            value.cancel(true);
            return null;
        });
        this.wrapped.shutdown();
    }
}
