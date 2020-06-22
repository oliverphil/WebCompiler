package nz.ac.vuw.ecs.webcompiler;

import org.apache.http.*;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.util.EntityUtils;

import javax.json.Json;
import javax.json.JsonObject;
import java.io.*;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class TestRunner implements HttpRequestHandler {

    private static final String JUNIT_JAR_PATH = "jdk-langtools/build/junit-platform-console-standalone-1.7.0-M1.jar";

    @Override
    public void handle(HttpRequest httpRequest, HttpResponse httpResponse, HttpContext httpContext) throws HttpException, IOException {
        String requestMethod = httpRequest.getRequestLine().getMethod().toUpperCase(Locale.ROOT);
        if (requestMethod.equals("POST")) {
            post(httpRequest, httpResponse);
        } else {
            httpResponse.setStatusCode(HttpStatus.SC_BAD_REQUEST);
        }
    }

    private void post(HttpRequest httpRequest, HttpResponse httpResponse) throws HttpException, IOException {
        HttpEntity entity = ((BasicHttpEntityEnclosingRequest) httpRequest).getEntity();
        String content = EntityUtils.toString(entity);
        JsonObject json = Json.createReader(new StringReader(content)).readObject();
        String sessionKey = json.getString("sessionKey");
        String challengeName = json.getString("challengeName");
        if (!challengeName.isEmpty() && !sessionKey.isEmpty()) {
            test(sessionKey, challengeName, httpResponse);
        }
    }

    private void test(String sessionKey, String challengeName, HttpResponse httpResponse) {
        try {
            Map<String, String> env = System.getenv();
            File challengeDir = new File(String.format("build/%s/%s", sessionKey, challengeName));
            File testFile = new File(String.format("%s/%sTests.java", challengeDir.getPath(), challengeName));
            //javac -cp junit-platform-console-standalone-1.7.0-M1.jar JavacFlagTests.java
            //java -jar junit-platform-console-standalone-1.7.0-M1.jar --class-path . --scan-class-path
            ProcessBuilder builder = new ProcessBuilder();
            Process compile = builder.command(env.get("JAVAC_JDK_ROOT") + "/bin/javac", "-Xlint:none", "-cp",
                    String.format("%s:build/%s", JUNIT_JAR_PATH, sessionKey),
                    testFile.getPath()).start();
            BufferedReader e = new BufferedReader(new InputStreamReader(compile.getErrorStream()));
            BufferedReader o = new BufferedReader(new InputStreamReader(compile.getInputStream()));
            Optional<String> eos = e.lines().reduce((a, b) -> a + "\n" + b);
            if (eos.isPresent()) {
                String s = eos.get();
                System.out.println(s);
            }
            Optional<String> oos = o.lines().reduce((a, b) -> a + "\n" + b);
            if (oos.isPresent()) {
                String s = oos.get();
                System.out.println(oos);
            }

            Process runTests = builder.command(env.get("JAVAC_JDK_ROOT") + "/bin/java", "-jar", JUNIT_JAR_PATH, "--class-path",
                    String.format("build/%s", sessionKey), "--scan-class-path", "-n", String.format("^.*?%sTests.*?$", challengeName)).start();
            BufferedReader stderr = new BufferedReader(new InputStreamReader(runTests.getErrorStream()));
            BufferedReader stdout = new BufferedReader(new InputStreamReader(runTests.getInputStream()));

            Optional<String> err = stderr.lines().reduce((a, b) -> a + "\n" + b);
            if(err.isPresent()) {
                String s = err.get();
                System.out.println(s);
            }
            Optional<String> out = stdout.lines().reduce((a, b) -> a + "\n" + b);
            if (out.isPresent()) {
                String s = out.get();
                System.out.println(s);
            }

            httpResponse.addHeader("Access-Control-Allow-Origin", "http://localhost:4200");
            return;
        } catch(IOException e) {
            e.printStackTrace();
        }

        httpResponse.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
    }
}
