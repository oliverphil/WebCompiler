package nz.ac.vuw.ecs.webcompiler;

import nz.ac.vuw.ecs.webcompiler.utils.*;
import org.apache.http.config.SocketConfig;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.NoConnectionReuseStrategy;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class Main {

    public static final String DATABASE_CONN_STRING = "jdbc:postgresql://localhost:5432/incremental_workloads";

    public static final ContentType TEXT_JAVASCRIPT = ContentType.create("text/javascript");
    public static final ContentType TEXT_CSS = ContentType.create("text/css");
    public static final ContentType TEXT_HTML = ContentType.create("text/html");
    public static final ContentType IMAGE_PNG = ContentType.create("image/png");
    public static final ContentType IMAGE_GIF = ContentType.create("image/gif");

    public static void main(String[] args) throws IOException {
        try {
            HttpServer server = startWebServer();
            server.start();
            server.awaitTermination(-1, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static HttpServer startWebServer() throws ClassNotFoundException {
        Class.forName("org.postgresql.Driver");
        SocketConfig socketConfig = SocketConfig.custom().setSoTimeout(20*1000).build();

        ServerLogger logger = ServerLogger.setup();

        HttpServer server = ServerBootstrap.bootstrap().setListenerPort(9100).setSocketConfig(socketConfig)
                .setConnectionReuseStrategy(new NoConnectionReuseStrategy())
                .setExceptionLogger(logger)
                .registerHandler("*.html", new StaticContentRequestHandler(TEXT_HTML))
                .registerHandler("*.js", new StaticContentRequestHandler(TEXT_JAVASCRIPT))
                .registerHandler("*.css", new StaticContentRequestHandler(TEXT_CSS))
                .registerHandler("/", new HomePage(TEXT_HTML))
                .registerHandler("/editor", new HomePage(TEXT_HTML))
                .registerHandler("/compile", new JavaWebCompiler())
                .registerHandler("/challenges", new CodingChallengesRequestHandler())
                .registerHandler("*.png", new MarkdownImageRequestHandler())
                .registerHandler("/test", new TestRunner())
                .registerHandler("/storeUser", new UserInformationStorageHandler())
                .create();

        return server;
    }


}
