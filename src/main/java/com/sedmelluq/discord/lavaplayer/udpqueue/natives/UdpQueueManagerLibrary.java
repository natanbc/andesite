package com.sedmelluq.discord.lavaplayer.udpqueue.natives;

import com.sedmelluq.lava.common.natives.NativeLibraryLoader;
import java.nio.ByteBuffer;

public class UdpQueueManagerLibrary {
    private static final NativeLibraryLoader nativeLoader =
            NativeLibraryLoader.create(UdpQueueManagerLibrary.class, "udpqueue");
    
    private UdpQueueManagerLibrary() {
    
    }
    
    public static UdpQueueManagerLibrary getInstance() {
        nativeLoader.load();
        return new UdpQueueManagerLibrary();
    }
    
    public native long create(int bufferCapacity, long packetInterval);
    
    public native void destroy(long instance);
    
    public native int getRemainingCapacity(long instance, long key);
    
    public native boolean queuePacket(long instance, long key, String address, int port, ByteBuffer dataDirectBuffer,
                                      int dataLength);
    
    public native boolean queuePacketWithSocket(long instance, long key, String address, int port,
                                                ByteBuffer dataDirectBuffer, int dataLength, long explicitSocket);
    
    public native boolean deleteQueue(long instance, long key);
    
    public native void process(long instance);
    
    public native void processWithSocket(long instance, long ipv4Handle, long ipv6Handle);
    
    public static native void pauseDemo(int length);
}
