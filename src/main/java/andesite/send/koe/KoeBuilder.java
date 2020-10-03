package andesite.send.koe;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueDatagramChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import moe.kyokobot.koe.Koe;
import moe.kyokobot.koe.KoeOptions;
import moe.kyokobot.koe.codec.FramePollerFactory;
import moe.kyokobot.koe.gateway.GatewayVersion;

public class KoeBuilder {
    private EventLoopGroup eventLoopGroup;
    private Class<? extends SocketChannel> socketChannelClass;
    private Class<? extends DatagramChannel> datagramChannelClass;
    private ByteBufAllocator byteBufAllocator;
    private GatewayVersion gatewayVersion;
    private FramePollerFactory framePollerFactory;
    private boolean highPacketPriority;

    public void epoll() {
        if(!Epoll.isAvailable()) {
            throw new IllegalArgumentException("Epoll is not available");
        }
        eventLoopGroup = new EpollEventLoopGroup();
        socketChannelClass = EpollSocketChannel.class;
        datagramChannelClass = EpollDatagramChannel.class;
    }
    
    public void kqueue() {
        if(!KQueue.isAvailable()) {
            throw new IllegalArgumentException("KQueue is not available");
        }
        eventLoopGroup = new KQueueEventLoopGroup();
        socketChannelClass = KQueueSocketChannel.class;
        datagramChannelClass = KQueueDatagramChannel.class;
    }
    
    public void nio() {
        eventLoopGroup = new NioEventLoopGroup();
        socketChannelClass = NioSocketChannel.class;
        datagramChannelClass = NioDatagramChannel.class;
    }

    public KoeBuilder setByteBufAllocator(ByteBufAllocator byteBufAllocator) {
        this.byteBufAllocator = byteBufAllocator;
        return this;
    }

    public KoeBuilder setGatewayVersion(GatewayVersion gatewayVersion) {
        this.gatewayVersion = gatewayVersion;
        return this;
    }

    public KoeBuilder setFramePollerFactory(FramePollerFactory framePollerFactory) {
        this.framePollerFactory = framePollerFactory;
        return this;
    }

    public KoeBuilder setHighPacketPriority(boolean highPacketPriority) {
        this.highPacketPriority = highPacketPriority;
        return this;
    }

    public Koe create() {
        return Koe.koe(new KoeOptions(
                eventLoopGroup, socketChannelClass, datagramChannelClass, byteBufAllocator,
                gatewayVersion, framePollerFactory, highPacketPriority
        ));
    }
}
