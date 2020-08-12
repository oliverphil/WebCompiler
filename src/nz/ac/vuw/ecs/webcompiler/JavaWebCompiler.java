package nz.ac.vuw.ecs.webcompiler;

import nz.ac.vuw.ecs.webcompiler.utils.ServerLogger;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
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
import java.sql.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class JavaWebCompiler implements HttpRequestHandler {

    @Override
    public void handle(HttpRequest httpRequest, HttpResponse httpResponse, HttpContext httpContext) throws IOException {
        String requestMethod = httpRequest.getRequestLine().getMethod().toUpperCase(Locale.ROOT);
        if (requestMethod.equals("POST")) {
            post(httpRequest, httpResponse, httpContext);
        } else if(requestMethod.equals("OPTIONS")) {
            httpResponse.setStatusCode(HttpStatus.SC_OK);
        } else {
            httpResponse.setStatusCode(HttpStatus.SC_BAD_REQUEST);
        }
    }

    private void post(HttpRequest httpRequest, HttpResponse httpResponse, HttpContext httpContext) throws IOException {
        HttpEntity entity = ((BasicHttpEntityEnclosingRequest) httpRequest).getEntity();
        String content = EntityUtils.toString(entity);
        JsonObject json = Json.createReader(new StringReader(content)).readObject();
        String code = json.getString("code");
        String sessionKey = json.getString("sessionKey");
        String challengeName = json.getString("challengeName");
        if (!challengeName.isEmpty() && !sessionKey.isEmpty()) {
            compile(code, sessionKey, challengeName, httpResponse);
        }
    }

    private void compile(String content, String sessionKey, String challengeName, HttpResponse response) {
        Logger logger = ServerLogger.getLogger();
        logger.info(String.format("User: %s, Challenge: %s, Action: Compile", sessionKey, challengeName));
        try {
            File challengeDir = createRequiredFolders(sessionKey, challengeName);
            File challengeFile = new File(String.format("%s/%s.java", challengeDir.getPath(), challengeName));
            File compileTestFile = new File(String.format("%s/%sCompileTests.java", challengeDir.getPath(), challengeName));
            FileWriter writer = new FileWriter(challengeFile);
            File imports = new File(String.format("%s/imports.txt", challengeDir.getPath()));

            String fileContent = String.format("public class %s { %s }", challengeName, content);
            if (imports.exists()) {
                String importsString = new BufferedReader(new FileReader(imports)).lines().reduce((a, b) -> a + "\n" + b).get();
                fileContent = String.format("%s\n%s", importsString, fileContent);
            }
            fileContent = String.format("package %s;\n%s", challengeName, fileContent);
            writer.write(fileContent);
            writer.close();

            ProcessBuilder builder = new ProcessBuilder();
            Process p = builder.command("jdk-langtools/build/langtools/bin/javac", challengeFile.getPath(), compileTestFile.getPath()).start();
            BufferedReader stderr = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            BufferedReader stdout = new BufferedReader(new InputStreamReader(p.getInputStream()));

            Timestamp timestamp = Timestamp.from(Instant.now());

            this.addToDatabase(stdout.lines(), sessionKey, content, challengeName, timestamp);

            Set<String> errorLines = new HashSet<>();

            Optional<String> stderrLines = stderr.lines().reduce((a, b) -> a + "\n" + b);
            String result = "";
            if (stderrLines.isEmpty()) {
                result = "Compilation Successful";
            } else {
                result = stderrLines.get();
                String[] lines = result.split("\n");
                for (int i = 0; i < lines.length; i++) {
                    String s = lines[i];
                    if (!s.contains(".java:")) continue;

                    String[] arr = s.split(":");
                    String lineNumber = Integer.toString(Integer.parseInt(arr[1]) - 1);
                    arr[1] = lineNumber;
                    errorLines.add(lineNumber);
                    arr[0] = arr[0].split("/")[3];
                    lines[i] = String.join(":", arr);
                }
                result = String.join("\n", lines);
            }

            JsonArrayBuilder jsonArrayBuilder = Json.createArrayBuilder(errorLines);

            JsonObjectBuilder jsonBuilder = Json.createObjectBuilder();
            jsonBuilder.add("compileResult", result);
            jsonBuilder.add("errorLines", jsonArrayBuilder);
            jsonBuilder.add("timestamp", timestamp.toString());
            String jsonResponse = jsonBuilder.build().toString();

            response.setStatusCode(HttpStatus.SC_OK);
            response.addHeader("Content-Type", "application/json");
            response.setEntity(new StringEntity(jsonResponse));
            return;
        } catch(IOException e) {
            logger.warning(e.toString());
        }

        response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
    }

    private void addToDatabase(Stream<String> stream, String user_id, String code, String challengeName, Timestamp timestamp) {
        Runnable r = () -> {
            try {
                ServerLogger.getLogger().info(String.format("User: %s, Action: Add Compilation To Database", user_id));
                Connection db = DriverManager.getConnection(Main.DATABASE_CONN_STRING);

                PreparedStatement insertCodeStmt = db.prepareStatement("INSERT INTO compile_request" +
                        "(timestamp, user_id, code, challenge) VALUES (?, ?, ?, ?);");
                PreparedStatement insertFlagStmt = db.prepareStatement("INSERT INTO compile_result" +
                        "(timestamp, user_id, compilation_flag, flag_result, challenge) VALUES (?, ?, ?, ?, ?);");

                insertCodeStmt.setTimestamp(1, timestamp);
                insertCodeStmt.setString(2, user_id);
                insertCodeStmt.setString(3, code);
                insertCodeStmt.setString(4, challengeName);
                insertCodeStmt.executeUpdate();

                AtomicBoolean typeCheckPass = new AtomicBoolean(true);

                stream.filter(s -> s.contains("Flag")).distinct().forEach(s -> {
                    String[] flagData = s.split("[-:]");
                    String flagName = flagData[1].strip();
                    boolean flagResult = flagData[2].strip().equals("Complete") ? true : false;

                    if (flagName.equals("Type Checking") && !flagResult) typeCheckPass.set(false);
                    else if (flagName.equals("Type Checking")) return;

                    try {
                        insertFlagStmt.setTimestamp(1, timestamp);
                        insertFlagStmt.setString(2, user_id);
                        insertFlagStmt.setString(3, flagName);
                        insertFlagStmt.setBoolean(4, flagResult);
                        insertFlagStmt.setString(5, challengeName);
                        insertFlagStmt.executeUpdate();
                    } catch (SQLException throwables) {
                        ServerLogger.getLogger().warning(throwables.toString());
                    }
                });

                if (typeCheckPass.get()) {
                    try {
                        insertFlagStmt.setTimestamp(1, timestamp);
                        insertFlagStmt.setString(2, user_id);
                        insertFlagStmt.setString(3, "Type Checking");
                        insertFlagStmt.setBoolean(4, true);
                        insertFlagStmt.setString(5, challengeName);
                        insertFlagStmt.executeUpdate();
                    } catch (SQLException throwables) {
                        ServerLogger.getLogger().warning(throwables.toString());
                    }
                }
            } catch (SQLException throwables) {
                ServerLogger.getLogger().warning(throwables.toString());
            }
        };
        r.run();
    }

    private File createRequiredFolders(String sessionKey, String challengeName) throws IOException {
        File sessionFolder = new File("build/" + sessionKey);
        File challenges = new File("coding-challenges/challenges");
        assert challenges.exists();
        if (!sessionFolder.exists()) {
            ServerLogger.getLogger().info(String.format("User: %s, Action: Create Folders", sessionKey));
            sessionFolder.mkdir();
            FileUtils.copyDirectory(challenges, sessionFolder);
        }

        File challengeFolder = new File(String.format("build/%s/%s", sessionKey, challengeName));
        return challengeFolder;
    }
}
