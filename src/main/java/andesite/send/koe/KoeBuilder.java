package andesite.send.koe;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.SocketChannel;
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

    public KoeBuilder setEventLoopGroup(EventLoopGroup eventLoopGroup) {
        this.eventLoopGroup = eventLoopGroup;
        return this;
    }

    public KoeBuilder setSocketChannelClass(Class<? extends SocketChannel> socketChannelClass) {
        this.socketChannelClass = socketChannelClass;
        return this;
    }

    public KoeBuilder setDatagramChannelClass(Class<? extends DatagramChannel> datagramChannelClass) {
        this.datagramChannelClass = datagramChannelClass;
        return this;
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
