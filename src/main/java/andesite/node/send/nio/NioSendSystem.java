package andesite.node.send.nio;

import io.vertx.core.Vertx;
import net.dv8tion.jda.api.audio.factory.IAudioSendSystem;
import net.dv8tion.jda.api.audio.factory.IPacketProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.StandardSocketOptions;
import java.nio.channels.DatagramChannel;

public class NioSendSystem implements IAudioSendSystem {
    private static final Logger log = LoggerFactory.getLogger(NioSendSystem.class);
    private static final int OPUS_FRAME_TIME_AMOUNT = 20;

    private final Vertx vertx;
    private final IPacketProvider packetProvider;
    private final DatagramChannel channel;
    private volatile boolean started = false;
    private volatile boolean stop;
    private long lastFrameSent;
    private boolean sentPacket = true;

    public NioSendSystem(Vertx vertx, IPacketProvider packetProvider) {
        this.vertx = vertx;
        this.packetProvider = packetProvider;
        try {
            this.channel = DatagramChannel.open()
                    .setOption(StandardSocketOptions.SO_REUSEADDR, true);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create UDP channel", e);
        }
    }

    @Override
    public synchronized void start() {
        if (started) return;
        started = true;
        var socket = packetProvider.getUdpSocket();
        var local = socket.getLocalSocketAddress();
        log.debug("Converting magma socket into channel");
        try {
            socket.close();
            channel.bind(local).connect(packetProvider.getSocketAddress());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to configure UDP channel", e);
        }
        run();
    }

    @Override
    public void shutdown() {
        stop = true;
    }

    private void run() {
        if (stop) {
            try {
                channel.close();
            } catch (IOException e) {
                log.error("Error closing udp channel", e);
            }
            return;
        }
        lastFrameSent = System.currentTimeMillis();
        var changeTalking = !sentPacket || (System.currentTimeMillis() - lastFrameSent) > OPUS_FRAME_TIME_AMOUNT;
        var buffer = packetProvider.getNextPacketRaw(changeTalking);

        sentPacket = buffer != null;
        if (sentPacket) {
            try {
                channel.send(buffer, packetProvider.getSocketAddress());
            } catch (IOException e) {
                log.error("Error sending udp packet", e);
            }
        }
        scheduleNextPacket();
    }

    private void scheduleNextPacket() {
        var sleepTime = (OPUS_FRAME_TIME_AMOUNT) - (System.currentTimeMillis() - lastFrameSent);
        if (sleepTime > 0) {
            vertx.setTimer(sleepTime - 1, __ -> sendNextPacket());
        } else {
            sendNextPacket();
        }
    }

    private void sendNextPacket() {
        if (System.currentTimeMillis() < lastFrameSent + 60) {
            // If the sending didn't took longer than 60ms (3 times the time frame)
            lastFrameSent += OPUS_FRAME_TIME_AMOUNT;
        } else {
            // else reset lastFrameSent to current time
            lastFrameSent = System.currentTimeMillis();
        }
        run();
    }
}
