package play.server.ssl;

import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.ssl.SslHandler;
import play.Logger;
import play.mvc.Http.Request;
import play.server.PlayHandler;
import play.server.Server;

import javax.net.ssl.SSLException;
import java.net.InetSocketAddress;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.LOCATION;

public class SslPlayHandler extends PlayHandler {

    @Override
    public Request parseRequest(ChannelHandlerContext ctx, HttpRequest nettyRequest, MessageEvent e) throws Exception {
        Request request = super.parseRequest(ctx, nettyRequest, e);
        request.secure = true;
        return request;
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        ctx.setAttachment(e.getValue());
        // Get the SslHandler in the current pipeline.
        final SslHandler sslHandler = ctx.getPipeline().get(SslHandler.class);
        sslHandler.setEnableRenegotiation(false);
        // Get notified when SSL handshake is done.
        ChannelFuture handshakeFuture = sslHandler.handshake();
        handshakeFuture.addListener(new SslListener());
    }

    private static final class SslListener implements ChannelFutureListener {

        public void operationComplete(ChannelFuture future) throws Exception {
            if (!future.isSuccess()) {
                Logger.debug(future.getCause(), "Invalid certificate");
            }
        }

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        // We have to redirect to https://, as it was targeting http://
        // Redirect to the root as we don't know the url at that point
        if (e.getCause() instanceof SSLException) {
            Logger.debug(e.getCause(), "");
            InetSocketAddress inet = ((InetSocketAddress) ctx.getAttachment());
            ctx.getPipeline().remove("ssl");
            HttpResponse nettyResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.TEMPORARY_REDIRECT);
            nettyResponse.setHeader(LOCATION, "https://" + inet.getHostName() + ":" + Server.httpsPort + "/");
            ChannelFuture writeFuture = ctx.getChannel().write(nettyResponse);
            writeFuture.addListener(ChannelFutureListener.CLOSE);
        } else {
            Logger.error(e.getCause(), "");
            e.getChannel().close();
        }
    }

}
