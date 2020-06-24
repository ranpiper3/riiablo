package com.riiablo.net.reliable;

import com.google.flatbuffers.FlatBufferBuilder;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import java.net.InetSocketAddress;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;

import com.riiablo.codec.Animation;
import com.riiablo.net.packet.netty.Connection;
import com.riiablo.net.packet.netty.Netty;
import com.riiablo.net.packet.netty.NettyData;

public class TestClient extends ApplicationAdapter implements ReliableEndpoint.PacketProcessor {
  private static final String TAG = "Client";

  public static void main(String[] args) throws Exception {
    Thread.sleep(1000);
    HeadlessApplicationConfiguration config = new HeadlessApplicationConfiguration();
    config.renderInterval = Animation.FRAME_DURATION;
    new HeadlessApplication(new TestClient(), config);
  }

  private ReliableEndpoint endpoint;

  @Override
  public void create() {
    Gdx.app.setLogLevel(Application.LOG_DEBUG);

    EventLoopGroup group = new NioEventLoopGroup();
    try {
      Bootstrap b = new Bootstrap()
          .group(group)
          .channel(NioDatagramChannel.class)
          .handler(new ChannelInitializer<DatagramChannel>() {
            @Override
            protected void initChannel(DatagramChannel ch) {
              endpoint = new ReliableEndpoint(ch, TestClient.this);
              ch.pipeline()
                  .addLast(new ReliableChannelHandler(endpoint))
                  .addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(ChannelHandlerContext ctx) throws Exception {
                      init();
                      ctx.pipeline().remove(this);
                    }

                    void init() {
                      InetSocketAddress remoteAddress = endpoint.remoteAddress();
                      Gdx.app.log(TAG, "Sending Connection packet to " + remoteAddress.getHostString() + ":" + remoteAddress.getPort());

                      FlatBufferBuilder builder = new FlatBufferBuilder();
                      Connection.startConnection(builder);
                      int dataOffset = Connection.endConnection(builder);
                      int offset = Netty.createNetty(builder, NettyData.Connection, dataOffset);
                      Netty.finishNettyBuffer(builder, offset);

                      endpoint.sendMessage(QoS.Unreliable, builder.dataBuffer());
                    }
                  })
                  ;
            }
          });

      ChannelFuture f = b.connect("localhost", TestServer.PORT);
      f.channel().closeFuture().sync();
    } catch (Throwable t) {
      Gdx.app.error(TAG, t.getMessage(), t);
    } finally {
      group.shutdownGracefully();
    }
  }

  @Override
  public void processPacket(int sequence, ByteBuf bb) {
    Gdx.app.debug(TAG, "Processing packet...");
    Gdx.app.log(TAG, ByteBufUtil.hexDump(bb));
  }
}
