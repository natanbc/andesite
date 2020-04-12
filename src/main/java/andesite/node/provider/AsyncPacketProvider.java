package andesite.node.provider;

import net.dv8tion.jda.api.audio.factory.IPacketProvider;
import net.dv8tion.jda.api.audio.hooks.ConnectionStatus;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

class AsyncPacketProvider implements IPacketProvider {
    private final AtomicBoolean talking = new AtomicBoolean();
    private final IPacketProvider packetProvider;
    private final BlockingQueue<ByteBuffer> queue;

    AsyncPacketProvider(IPacketProvider packetProvider, int backlog, AtomicReference<Future<?>> taskRef) {
        this.packetProvider = packetProvider;
        this.queue = new ArrayBlockingQueue<>(backlog);

        taskRef.updateAndGet(__ -> CommonAsync.WORKER_POOL.submit(new ProvideForkJoinTask(
                () -> this.packetProvider.getNextPacketRaw(this.talking.get()),
                this.queue
        )));
    }

    private static DatagramPacket asDatagramPacket(final ByteBuffer buffer, final InetSocketAddress targetAddress) {
        final byte[] data = buffer.array();
        final int offset = buffer.arrayOffset();
        final int position = buffer.position();
        return new DatagramPacket(data, offset, position - offset, targetAddress);
    }

    @Override
    public String getIdentifier() {
        return packetProvider.getIdentifier();
    }

    @Override
    public String getConnectedChannel() {
        return packetProvider.getConnectedChannel();
    }

    @Override
    public DatagramSocket getUdpSocket() {
        return packetProvider.getUdpSocket();
    }

    @Override
    public InetSocketAddress getSocketAddress() {
        return packetProvider.getSocketAddress();
    }

    @Override
    public ByteBuffer getNextPacketRaw(boolean changeTalking) {
        this.talking.set(changeTalking);
        return this.queue.poll();
    }

    @Override
    public DatagramPacket getNextPacket(boolean changeTalking) {
        InetSocketAddress targetAddress = getSocketAddress();
        if (targetAddress == null) {
            return null;
        }
        ByteBuffer nextPacket = getNextPacketRaw(changeTalking);
        return nextPacket == null ? null : asDatagramPacket(nextPacket, targetAddress);
    }

    @Override
    public void onConnectionError(ConnectionStatus status) {
        packetProvider.onConnectionError(status);
    }

    @Override
    public void onConnectionLost() {
        packetProvider.onConnectionLost();
    }
}
