package andesite.node.provider;

import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

public class CommonAsync {
    public static final int DEFAULT_BACKLOG = 20;

    public static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        var thread = new Thread(r);
        thread.setDaemon(true);
        thread.setPriority((Thread.MIN_PRIORITY + Thread.NORM_PRIORITY) / 2);
        thread.setName("async-packet-provider-scheduler");
        return thread;
    });

    public static final ForkJoinPool WORKER_POOL = createPool();

    private static ForkJoinPool createPool() {
        AtomicInteger threadNumber = new AtomicInteger();
        return new ForkJoinPool(
                Runtime.getRuntime().availableProcessors(),
                pool -> {
                    var thread = new ForkJoinWorkerThread(pool) {
                    };
                    thread.setDaemon(true);
                    thread.setPriority((Thread.NORM_PRIORITY + Thread.MIN_PRIORITY) / 2);
                    thread.setName("async-packet-provider-thread-" + (threadNumber.incrementAndGet()));
                    return thread;
                },
                null,
                true
        );
    }
}
