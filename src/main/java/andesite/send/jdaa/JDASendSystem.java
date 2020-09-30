package andesite.send.jdaa;

import net.dv8tion.jda.api.audio.factory.IAudioSendSystem;
import net.dv8tion.jda.api.audio.factory.IPacketProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.NoRouteToHostException;
import java.net.SocketException;

/**
 * Taken from JDA code and adapted to work wih magma.
 *
 * @see <a href="https://github.com/DV8FromTheWorld/JDA/blob/44d925d584ee8590c98d7fda4aa4e518a9d9047d/src/main/java/net/dv8tion/jda/core/audio/factory/DefaultSendSystem.java">JDA
 * source</a>
 */
public class JDASendSystem implements IAudioSendSystem {
    private static final int OPUS_FRAME_TIME_AMOUNT = 20;//This is 20 milliseconds. We are only dealing with 20ms opus packets.
    private static final Logger log = LoggerFactory.getLogger(JDASendSystem.class);
    
    private final IPacketProvider packetProvider;
    private Thread sendThread;
    
    public JDASendSystem(IPacketProvider packetProvider) {
        this.packetProvider = packetProvider;
    }
    
    @Override
    public void start() {
        final DatagramSocket udpSocket = packetProvider.getUdpSocket();
        
        sendThread = new Thread(() -> {
            long lastFrameSent = System.currentTimeMillis();
            boolean sentPacket = true;
            while(!udpSocket.isClosed() && !sendThread.isInterrupted()) {
                try {
                    boolean changeTalking = !sentPacket || (System.currentTimeMillis() - lastFrameSent) > OPUS_FRAME_TIME_AMOUNT;
                    DatagramPacket packet = packetProvider.getNextPacket(changeTalking);
                    
                    sentPacket = packet != null;
                    if(sentPacket) {
                        udpSocket.send(packet);
                    }
                } catch(NoRouteToHostException e) {
                    //we can't call this as magma doesn't support it
                    //packetProvider.onConnectionLost();
                    break;
                } catch(SocketException e) {
                    //Most likely the socket has been closed due to the audio connection be closed. Next iteration will kill loop.
                } catch(Exception e) {
                    log.error("Error while sending udp audio data", e);
                } finally {
                    long sleepTime = (OPUS_FRAME_TIME_AMOUNT) - (System.currentTimeMillis() - lastFrameSent);
                    if(sleepTime > 0) {
                        try {
                            Thread.sleep(sleepTime);
                        } catch(InterruptedException e) {
                            //We've been asked to stop.
                            Thread.currentThread().interrupt();
                        }
                    }
                    if(System.currentTimeMillis() < lastFrameSent + 60) {
                        // If the sending didn't took longer than 60ms (3 times the time frame)
                        lastFrameSent += OPUS_FRAME_TIME_AMOUNT;
                    } else {
                        // else reset lastFrameSent to current time
                        lastFrameSent = System.currentTimeMillis();
                    }
                }
            }
        });
        sendThread.setUncaughtExceptionHandler((thread, throwable) -> {
            log.error("Uncaught exception in audio send thread", throwable);
            start();
        });
        sendThread.setDaemon(true);
        //we can't call getIdentifier() as magma doesn't support it either, so just use the toString there
        sendThread.setName(packetProvider + " Sending Thread");
        sendThread.setPriority((Thread.NORM_PRIORITY + Thread.MAX_PRIORITY) / 2);
        sendThread.start();
    }
    
    @Override
    public void shutdown() {
        if(sendThread != null) {
            sendThread.interrupt();
        }
    }
}
