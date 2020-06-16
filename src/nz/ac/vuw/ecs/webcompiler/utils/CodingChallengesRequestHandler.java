package nz.ac.vuw.ecs.webcompiler.utils;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.entity.StringEntity;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;

public class CodingChallengesRequestHandler implements HttpRequestHandler {

    @Override
    public void handle(HttpRequest httpRequest, HttpResponse httpResponse, HttpContext httpContext) throws HttpException, IOException {
        String requestMethod = httpRequest.getRequestLine().getMethod().toUpperCase(Locale.ROOT);
        if (requestMethod.equals("GET")) {
            getAllChallenges(httpRequest, httpResponse, httpContext);
        } else {
            httpResponse.setStatusCode(HttpStatus.SC_BAD_REQUEST);
        }
    }

    private void getAllChallenges(HttpRequest httpRequest, HttpResponse httpResponse, HttpContext httpContext) {
        try {
            File challengesFolder = new File(".", "coding-challenges/challenges");
            File[] challenges = challengesFolder.listFiles();

            JsonArrayBuilder jsonChallenges = Json.createArrayBuilder();
            Parser parser = Parser.builder().build();
            HtmlRenderer renderer = HtmlRenderer.builder().build();

            for (File challenge : challenges) {
                String challengeName = challenge.getName();
                File[] skeletonCode = challenge.listFiles((dir, name) -> name.contains(".java") && !name.contains("Tests"));
                File[] instructions = challenge.listFiles(((dir, name) -> name.contains(".md")));

                JsonObjectBuilder jsonChallenge = Json.createObjectBuilder();
                jsonChallenge.add("challengeName", challengeName);
                Optional<String> code = new BufferedReader(new FileReader(skeletonCode[0])).lines().reduce((a, b) -> a + "\n" + b);
                String codeString = code.isEmpty() ? "" : code.get();
                jsonChallenge.add("starterCode", codeString);

                String markdown = new BufferedReader(new FileReader(instructions[0])).lines().reduce((a, b) -> a + "\n" + b).get();
                Node markdownDoc = parser.parse(markdown);
                String htmlInstructions = renderer.render(markdownDoc);
                jsonChallenge.add("instructions", htmlInstructions);

                jsonChallenges.add(jsonChallenge);
            }

            String jsonResponse = jsonChallenges.build().toString();

            httpResponse.setStatusCode(HttpStatus.SC_OK);
            httpResponse.addHeader("Access-Control-Allow-Origin", "http://localhost:4200");
            httpResponse.addHeader("Content-Type", "application/json");
            httpResponse.setEntity(new StringEntity(jsonResponse));
        } catch (Exception e) {
            e.printStackTrace();
            httpResponse.setStatusCode(HttpStatus.SC_BAD_REQUEST);
        }
    }
}
