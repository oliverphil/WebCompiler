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
import java.util.Locale;

public class MarkdownImageRequestHandler implements HttpRequestHandler {

    private static final ContentType contentType = ContentType.create("image/png");

    @Override
    public void handle(HttpRequest httpRequest, HttpResponse httpResponse, HttpContext httpContext) throws HttpException, IOException {
        String requestMethod = httpRequest.getRequestLine().getMethod().toUpperCase(Locale.ROOT);
        if (requestMethod.equals("GET")) {
            get(httpRequest, httpResponse, httpContext);
        } else {
            httpResponse.setStatusCode(HttpStatus.SC_BAD_REQUEST);
        }
    }

    private void get(HttpRequest httpRequest, HttpResponse httpResponse, HttpContext httpContext) throws HttpException, IOException {
        try {
            String uri = httpRequest.getRequestLine().getUri();
            String path = new URIBuilder(uri).getPath();

            path = "coding-challenges" + path;

            File file = new File(".", path);
            System.out.println(file);
            httpResponse.setStatusCode(HttpStatus.SC_OK);
            httpResponse.addHeader("Access-Control-Allow-Origin", "http://localhost:4200");
            httpResponse.setEntity(new FileEntity(file, MarkdownImageRequestHandler.contentType));
        } catch (Exception e) {
            e.printStackTrace();
            httpResponse.setStatusCode(HttpStatus.SC_BAD_REQUEST);
        }
    }
}
