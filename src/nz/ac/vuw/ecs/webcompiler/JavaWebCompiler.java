package nz.ac.vuw.ecs.webcompiler;

import org.apache.http.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.util.EntityUtils;

import javax.json.Json;
import javax.json.JsonObjectBuilder;
import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Locale;
import java.util.Optional;

public class JavaWebCompiler implements HttpRequestHandler {

    @Override
    public void handle(HttpRequest httpRequest, HttpResponse httpResponse, HttpContext httpContext) throws HttpException, IOException {
        String requestMethod = httpRequest.getRequestLine().getMethod().toUpperCase(Locale.ROOT);
        if (requestMethod.equals("POST")) {
            post(httpRequest, httpResponse, httpContext);
        } else {
            httpResponse.setStatusCode(HttpStatus.SC_BAD_REQUEST);
        }
    }

    private void post(HttpRequest httpRequest, HttpResponse httpResponse, HttpContext httpContext) throws HttpException, IOException {
        HttpEntity entity = ((BasicHttpEntityEnclosingRequest) httpRequest).getEntity();
        String content = EntityUtils.toString(entity);
        if (!content.isEmpty()) {
            compile(content, httpResponse);
        }
    }

    private void compile(String content, HttpResponse response) {
        try {
            FileWriter writer = new FileWriter("build/TestCompile.java");
            String fileContent = "public class TestCompile {" + content + "}";

            System.out.println(fileContent);
            writer.write(fileContent);
            writer.close();

            ProcessBuilder builder = new ProcessBuilder();
            Process p = builder.command("jdk-langtools/build/langtools/bin/javac", "build/TestCompile.java").start();
            BufferedReader stderr = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            BufferedReader stdout = new BufferedReader(new InputStreamReader(p.getInputStream()));
            System.out.println(stdout.lines().reduce((a, b) -> a + "\n" + b).get());
            
            Optional<String> stderrLines = stderr.lines().reduce((a, b) -> a + "\n" + b);
            String result = "";
            if (stderrLines.isEmpty()) {
                result = "Compilation Successful";
            } else {
                result = stderrLines.get();
            }

            JsonObjectBuilder jsonBuilder = Json.createObjectBuilder();
            jsonBuilder.add("compileResult", result);
            String jsonResponse = jsonBuilder.build().toString();

            response.setStatusCode(HttpStatus.SC_OK);
            response.addHeader("Content-Type", "application/json");
            response.setEntity(new StringEntity(jsonResponse));
            return;
        } catch(IOException e) {
            e.printStackTrace();
        }

        response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
    }
}
