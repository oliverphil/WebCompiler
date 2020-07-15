package nz.ac.vuw.ecs.webcompiler.utils;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.FileEntity;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Locale;

public class StaticContentRequestHandler implements HttpRequestHandler {

    private ContentType contentType;

    public StaticContentRequestHandler(ContentType contentType) {
        this.contentType = contentType;
    }

    public StaticContentRequestHandler() {}

    @Override
    public void handle(HttpRequest httpRequest, HttpResponse httpResponse, HttpContext httpContext) throws HttpException, IOException {
        String requestMethod = httpRequest.getRequestLine().getMethod().toUpperCase(Locale.ROOT);
        if (requestMethod.equals("GET")) {
            try {
                get(httpRequest, httpResponse, httpContext);
            } catch (URISyntaxException e) {
                ServerLogger.getLogger().warning(e.toString());
            }
        } else {
            httpResponse.setStatusCode(HttpStatus.SC_BAD_REQUEST);
        }
    }

    private void get(HttpRequest httpRequest, HttpResponse httpResponse, HttpContext httpContext) throws URISyntaxException {
        String uri = httpRequest.getRequestLine().getUri();
        String path = new URIBuilder(uri).getPath();

        path = "webcompiler-frontend/dist/WebCompilerFrontend" + path;

        File file = new File(".", path);
        System.out.println(file);
        httpResponse.setStatusCode(HttpStatus.SC_OK);

        if (this.contentType != null)
            httpResponse.setEntity(new FileEntity(file, this.contentType));
        else
            httpResponse.setEntity(new FileEntity(file, ContentType.DEFAULT_TEXT));
    }
}
