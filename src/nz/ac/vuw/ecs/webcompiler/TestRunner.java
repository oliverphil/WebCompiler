package nz.ac.vuw.ecs.webcompiler;

import nz.ac.vuw.ecs.webcompiler.utils.ServerLogger;
import org.apache.http.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.util.EntityUtils;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import java.io.*;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class TestRunner implements HttpRequestHandler {

    private static final String JUNIT_JAR_PATH = "jdk-langtools/build/junit-platform-console-standalone-1.7.0-M1.jar";

    @Override
    public void handle(HttpRequest httpRequest, HttpResponse httpResponse, HttpContext httpContext) throws IOException {
        String requestMethod = httpRequest.getRequestLine().getMethod().toUpperCase(Locale.ROOT);
        if (requestMethod.equals("POST")) {
            post(httpRequest, httpResponse);
        } else {
            httpResponse.setStatusCode(HttpStatus.SC_BAD_REQUEST);
        }
    }

    private void post(HttpRequest httpRequest, HttpResponse httpResponse) throws IOException {
        HttpEntity entity = ((BasicHttpEntityEnclosingRequest) httpRequest).getEntity();
        String content = EntityUtils.toString(entity);
        JsonObject json = Json.createReader(new StringReader(content)).readObject();
        String sessionKey = json.getString("sessionKey");
        String challengeName = json.getString("challengeName");
        if (!challengeName.isEmpty() && !sessionKey.isEmpty()) {
            test(sessionKey, challengeName, httpResponse);
        }
    }

    private void addListToJsonObject(List<String> lines, JsonObjectBuilder objectBuilder, String arrayKey) {
        JsonArrayBuilder jsonArray = Json.createArrayBuilder();
        lines.forEach(s -> jsonArray.add(s));
        objectBuilder.add(arrayKey, jsonArray);
    }

    private boolean handleProcessInformation(HttpResponse httpResponse, Process proc, JsonObjectBuilder json, boolean compileTime) {
        BufferedReader stderr = new BufferedReader(new InputStreamReader(proc.getErrorStream()));

        List<String> allErrors = stderr.lines().collect(Collectors.toList());
        List<String> errorList = allErrors.stream().filter(a -> a.contains("error")).collect(Collectors.toList());
        if (!errorList.isEmpty() && compileTime) {
            addListToJsonObject(errorList, json, "compileErrors");
            return false;
        }

        BufferedReader stdout = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        List<String> allTestResultList = stdout.lines().collect(Collectors.toList());
        List<String> testResultList = allTestResultList.stream().filter(a -> a.matches("^\\[\\W+\\d+\\Wtests.*\\]$")).collect(Collectors.toList());
        if (!testResultList.isEmpty() && !compileTime) {
            addListToJsonObject(testResultList, json, "testResults");
        }

        return true;
    }

    private void test(String sessionKey, String challengeName, HttpResponse httpResponse) throws UnsupportedEncodingException {
        try {
            ServerLogger.getLogger().info(String.format("User: %s, Challenge: %s, Action: Run Tests", sessionKey, challengeName));
            Map<String, String> env = System.getenv();
            File challengeDir = new File(String.format("build/%s/%s", sessionKey, challengeName));
            File testFile = new File(String.format("%s/%sTests.java", challengeDir.getPath(), challengeName));
            ProcessBuilder builder = new ProcessBuilder();

            JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder();

            Process compile = builder.command("jdk-langtools/build/langtools/bin/javac", "-Xlint:none", "-cp",
                    String.format("%s:build/%s", JUNIT_JAR_PATH, sessionKey),
                    testFile.getPath()).start();

            if (handleProcessInformation(httpResponse, compile, jsonObjectBuilder, true)) {
                Process runTests = builder.command("jdk-langtools/build/release/jdk/bin/java", "-Djava.security.manager",
                        "-Djava.security.policy=securitypolicy", "-jar", JUNIT_JAR_PATH, "--class-path",
                        String.format("build/%s", sessionKey), "--scan-class-path", "-n", String.format("^.*?%sTests.*?$", challengeName))
                        .start();
                if (!runTests.waitFor(30, TimeUnit.SECONDS)) {
                    throw new TimeoutException("Tests timed out");
                }
                handleProcessInformation(httpResponse, runTests, jsonObjectBuilder, false);
            }

            httpResponse.setEntity(new StringEntity(jsonObjectBuilder.build().toString()));
            httpResponse.setStatusCode(HttpStatus.SC_OK);
            return;
        } catch(TimeoutException e) {
            ServerLogger.getLogger().info(String.format("User: %s timeout on %s", sessionKey, challengeName));
            httpResponse.setStatusCode(HttpStatus.SC_OK);
            JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder();
            jsonObjectBuilder.add("timeout", e.getMessage());
            httpResponse.setEntity(new StringEntity(jsonObjectBuilder.build().toString()));
            return;
        } catch(IOException | InterruptedException e) {
            ServerLogger.getLogger().warning(e.toString());
        }

        httpResponse.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
    }
}
