package nz.ac.vuw.ecs.webcompiler.utils;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.FileEntity;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

public class HomePage implements HttpRequestHandler {

    private ContentType contentType;

    public HomePage(ContentType contentType) {
        this.contentType = contentType;
    }

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
            String path = "index.html";
            File file = new File(".", path);
            httpResponse.setStatusCode(HttpStatus.SC_OK);
            httpResponse.setEntity(new FileEntity(file, this.contentType));
        } catch (Exception e) {
            e.printStackTrace();
            httpResponse.setStatusCode(HttpStatus.SC_BAD_REQUEST);
        }
    }
}
