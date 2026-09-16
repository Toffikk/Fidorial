package fr.euphyllia.fidorial.server.management;

import fr.euphyllia.fidorial.server.FidorialServer;
import fr.euphyllia.fidorial.server.management.protocol.ManagementFrameHandler;
import fr.euphyllia.fidorial.server.management.protocol.ManagementHandshakeHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueIoHandler;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.uring.IoUring;
import io.netty.channel.uring.IoUringIoHandler;
import io.netty.channel.uring.IoUringServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.ssl.SslContext;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.jspecify.annotations.Nullable;

public final class ManagementServer {

    private static final ComponentLogger LOGGER = ComponentLogger.logger(ManagementServer.class);

    private final FidorialServer server;
    private final @Nullable SslContext ssl;
    private final ManagementNotifier notifier;
    private final MultiThreadIoEventLoopGroup bossGroup;
    private final MultiThreadIoEventLoopGroup workerGroup;
    private final Class<? extends ServerChannel> channelClass;
    private @Nullable Channel channel;

    public ManagementServer(final FidorialServer server, final @Nullable SslContext ssl, final ManagementNotifier notifier) {
        this.server = server;
        this.ssl = ssl;
        this.notifier = notifier;

        if (server.config().useIoUring() && IoUring.isAvailable()) {
            LOGGER.info("Management API using io_uring transport");
            bossGroup = new MultiThreadIoEventLoopGroup(1, IoUringIoHandler.newFactory());
            workerGroup = new MultiThreadIoEventLoopGroup(IoUringIoHandler.newFactory());
            channelClass = IoUringServerSocketChannel.class;
        } else if (Epoll.isAvailable()) {
            LOGGER.info("Management API using epoll transport");
            bossGroup = new MultiThreadIoEventLoopGroup(1, EpollIoHandler.newFactory());
            workerGroup = new MultiThreadIoEventLoopGroup(EpollIoHandler.newFactory());
            channelClass = EpollServerSocketChannel.class;
        } else if (KQueue.isAvailable()) {
            LOGGER.info("Management API using kqueue transport");
            bossGroup = new MultiThreadIoEventLoopGroup(1, KQueueIoHandler.newFactory());
            workerGroup = new MultiThreadIoEventLoopGroup(KQueueIoHandler.newFactory());
            channelClass = KQueueServerSocketChannel.class;
        } else {
            LOGGER.info("Management API using NIO transport");
            bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
            workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
            channelClass = NioServerSocketChannel.class;
        }
    }

    public void bind() throws InterruptedException {
        final var config = server.config();
        final ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(channelClass)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(final SocketChannel ch) {
                        if (ssl != null) {
                            ch.pipeline().addLast("ssl", ssl.newHandler(ch.alloc()));
                        }
                        ch.pipeline()
                                .addLast("http-codec", new HttpServerCodec())
                                .addLast("aggregator", new HttpObjectAggregator(1 << 16))
                                .addLast("origin-auth", new ManagementHandshakeHandler(server))
                                .addLast("ws-protocol", new WebSocketServerProtocolHandler("/", "minecraft-v1", true))
                                .addLast("rpc", new ManagementFrameHandler(server, notifier));
                    }
                });
        this.channel = bootstrap.bind(config.managementServerHost(), config.managementServerPort()).sync().channel();
        LOGGER.info("Management API listening on {}:{}", config.managementServerHost(), config.managementServerPort());
    }

    public void shutdown() {
        if (channel != null) channel.close();
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }
}
