package nz.ac.vuw.ecs.webcompiler;

import nz.ac.vuw.ecs.webcompiler.utils.ServerLogger;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.util.EntityUtils;

import javax.json.*;
import java.io.*;
import java.sql.*;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
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
        Timestamp timestamp = Timestamp.valueOf(json.getString("compileTimestamp"));
        if (!challengeName.isEmpty() && !sessionKey.isEmpty()) {
            test(sessionKey, challengeName, timestamp, httpResponse);
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

    private void saveRunToDatabase(String user_id, String challengeName, Timestamp timestamp, JsonObject json) {
        Runnable r = () -> {
            try {
                ServerLogger.getLogger().info(String.format("User: %s, Action: Add Test Run To Database", user_id));
                Connection db = DriverManager.getConnection(Main.DATABASE_CONN_STRING);

                PreparedStatement insertCodeStmt = db.prepareStatement("INSERT INTO test_result" +
                        "(timestamp, user_id, challenge, test_result) VALUES (?, ?, ?, ?);");

                String results = "error";
                if (json.containsKey("compileErrors")) {
                    ServerLogger.getLogger().info(String.format("User: %s, Details: Test Compilation Failed", user_id));
                } else {
                    JsonArray testResults = json.getJsonArray("testResults");
                    AtomicInteger totalTests = new AtomicInteger();
                    AtomicInteger passedTests = new AtomicInteger();
                    testResults.stream().filter(o -> o.toString().contains("successful") || o.toString().contains("found"))
                            .forEach(o -> {
                        String[] arr = o.toString().split(" ");
                        String num = arr[9];
                        if (o.toString().contains("successful")) {
                            passedTests.set(Integer.parseInt(num));
                        } else {
                            totalTests.set(Integer.parseInt(num));
                        }
                    });
                    results = String.format("%d/%d", passedTests.get(), totalTests.get());
                }

                insertCodeStmt.setTimestamp(1, timestamp);
                insertCodeStmt.setString(2, user_id);
                insertCodeStmt.setString(3, challengeName);
                insertCodeStmt.setString(4, results);
                insertCodeStmt.executeUpdate();

            } catch (SQLException throwables) {
                ServerLogger.getLogger().warning(throwables.toString());
            }
        };
        r.run();
    }

    private void test(String sessionKey, String challengeName, Timestamp timestamp, HttpResponse httpResponse) throws UnsupportedEncodingException {
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
//                Process runTests = builder.command("jdk-langtools/build/release/jdk/bin/java", "-Djava.security.manager",
                Process runTests = builder.command(env.get("JAVAC_JDK_ROOT") + "/bin/java", "-Djava.security.manager",
                        "-Djava.security.policy=securitypolicy", "-jar", JUNIT_JAR_PATH, "--class-path",
                        String.format("build/%s", sessionKey), "--scan-class-path", "-n", String.format("^.*?%sTests.*?$", challengeName))
                        .start();
                if (!runTests.waitFor(30, TimeUnit.SECONDS)) {
                    throw new TimeoutException("Tests timed out");
                }
                handleProcessInformation(httpResponse, runTests, jsonObjectBuilder, false);
            }

            JsonObject json = jsonObjectBuilder.build();
            saveRunToDatabase(sessionKey, challengeName, timestamp, json);

            httpResponse.setEntity(new StringEntity(json.toString()));
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
