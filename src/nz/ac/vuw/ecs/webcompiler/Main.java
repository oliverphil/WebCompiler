package nz.ac.vuw.ecs.webcompiler;

import nz.ac.vuw.ecs.webcompiler.utils.HomePage;
import nz.ac.vuw.ecs.webcompiler.utils.StaticContentRequestHandler;
import org.apache.http.*;
import org.apache.http.config.SocketConfig;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class Main {

    public static final ContentType TEXT_JAVASCRIPT = ContentType.create("text/javascript");
    public static final ContentType TEXT_CSS = ContentType.create("text/css");
    public static final ContentType TEXT_HTML = ContentType.create("text/html");
    public static final ContentType IMAGE_PNG = ContentType.create("image/png");
    public static final ContentType IMAGE_GIF = ContentType.create("image/gif");

    public static void main(String[] args) throws IOException {
        String userhome = System.getProperty("user.home");

        try {
            HttpServer server = startWebServer();
            server.start();
            server.awaitTermination(-1, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static HttpServer startWebServer() throws IOException {
        SocketConfig socketConfig = SocketConfig.custom().setSoTimeout(1500).build();

        HttpServer server = ServerBootstrap.bootstrap().setListenerPort(9100).setSocketConfig(socketConfig)
                .setExceptionLogger(ExceptionLogger.STD_ERR)
                .registerHandler("/css/*", new StaticContentRequestHandler(TEXT_CSS))
                .registerHandler("/js/*", new StaticContentRequestHandler(TEXT_JAVASCRIPT))
                .registerHandler("/", new HomePage(TEXT_HTML))
                .create();

        return server;
    }


}