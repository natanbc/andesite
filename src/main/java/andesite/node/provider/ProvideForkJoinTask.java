package andesite.node.provider;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class ProvideForkJoinTask extends ForkJoinTask<ByteBuffer> {
    private final Supplier<ByteBuffer> provider;
    private final BlockingQueue<ByteBuffer> queue;

    private final AtomicReference<ByteBuffer> packetRef = new AtomicReference<>();

    private volatile boolean stopRequested;

    public ProvideForkJoinTask(Supplier<ByteBuffer> provider, BlockingQueue<ByteBuffer> queue) {
        this.provider = provider;
        this.queue = queue;
    }

    @Override
    protected boolean exec() {
        ByteBuffer packet = this.packetRef.getAndSet(null);
        if (this.stopRequested) {
            return true;
        } else if (packet == null) {
            packet = optionallyCopyData(this.provider.get());
        }

        if (packet == null || !this.queue.offer(packet)) {
            // offer failed, retry next run
            this.packetRef.set(packet);
            ForkJoinPool pool = getPool();
            ForkJoinTask<?> me = this;

            // instead of spinning, suspend execution
            reinitialize();
            CommonAsync.SCHEDULER.schedule(() -> pool.execute(me), 40, TimeUnit.MILLISECONDS);
            return false;
        }

        reinitialize();
        fork();
        return false;
    }

    private static ByteBuffer optionallyCopyData(ByteBuffer packet) {
        if (packet != null) {
            final byte[] data = packet.array();
            final int offset = packet.arrayOffset();
            final int position = packet.position();
            return ByteBuffer.allocate(position - offset).put(data, offset, position - offset);
        }
        return null;
    }

    @Override
    protected void setRawResult(ByteBuffer value) {
        throw new UnsupportedOperationException("Not needed.");
    }

    @Override
    public ByteBuffer getRawResult() {
        throw new UnsupportedOperationException("Not needed.");
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        this.stopRequested = true;
        return super.cancel(mayInterruptIfRunning);
    }
}
