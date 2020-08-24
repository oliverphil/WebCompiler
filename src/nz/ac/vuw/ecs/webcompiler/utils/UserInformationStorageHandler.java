package nz.ac.vuw.ecs.webcompiler.utils;

import nz.ac.vuw.ecs.webcompiler.Main;
import org.apache.http.*;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.util.EntityUtils;

import javax.json.Json;
import javax.json.JsonObject;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Locale;

public class UserInformationStorageHandler implements HttpRequestHandler {

    @Override
    public void handle(HttpRequest httpRequest, HttpResponse httpResponse, HttpContext httpContext) throws HttpException, IOException {
        String requestMethod = httpRequest.getRequestLine().getMethod().toUpperCase(Locale.ROOT);
        if (requestMethod.equals("POST")) {
            post(httpRequest, httpResponse, httpContext);
        } else if (requestMethod.equals("DELETE")) {
            delete(httpRequest, httpResponse, httpContext);
        } else {
            httpResponse.setStatusCode(HttpStatus.SC_BAD_REQUEST);
        }
    }

    private void delete(HttpRequest httpRequest, HttpResponse httpResponse, HttpContext httpContext) throws IOException {
        String id = httpRequest.getFirstHeader("id").getValue();
        deleteUser(httpResponse, id);
    }

    private void deleteUser(HttpResponse httpResponse, String id) {
        ServerLogger.getLogger().info(String.format("User: %s, Action: Delete Folder", id));
        File f = new File(String.format("build/%s", id));
        if (f.exists()) {
            deleteFolders(f);
        }
        httpResponse.setStatusCode(HttpStatus.SC_OK);
    }

    private void deleteFolders(File file) {
        if (file.isDirectory()) {
            for(File f: file.listFiles()) {
                deleteFolders(f);
            }
        }
        file.delete();
    }

    private void post(HttpRequest httpRequest, HttpResponse httpResponse, HttpContext httpContext) throws IOException {
        HttpEntity entity = ((BasicHttpEntityEnclosingRequest) httpRequest).getEntity();
        String content = EntityUtils.toString(entity);
        JsonObject json = Json.createReader(new StringReader(content)).readObject();
        storeUserInfo(httpResponse, json);
    }

    private void storeUserInfo(HttpResponse httpResponse, JsonObject jsonObject) {
        String id = jsonObject.getString("id");
        String age = jsonObject.getString("age");
        String occupation = jsonObject.getString("occupation");
        String java_experience = jsonObject.getString("javaExperience");
        String education = jsonObject.getString("education");
        String other_langs = jsonObject.getString("otherLanguages");
        String ide_experience = jsonObject.getString("ideExperience");
        String magic_number = jsonObject.getString("magic");

        ServerLogger.getLogger().info(String.format("User: %s, Action: Add User To Database", id));

        try {
            Connection db = DriverManager.getConnection(Main.DATABASE_CONN_STRING, Main.DATABASE_PROPERTIES);
            PreparedStatement stmt = db.prepareStatement("INSERT INTO user_information" +
                    "(id, age, occupation, java_experience, education, other_langs, ide_experience, magic_number) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)");

            stmt.setString(1, id);
            stmt.setString(2, age);
            stmt.setString(3, occupation);
            stmt.setString(4, java_experience);
            stmt.setString(5, education);
            stmt.setString(6, other_langs);
            stmt.setString(7, ide_experience);
            stmt.setString(8, magic_number);

            stmt.executeUpdate();
            httpResponse.setStatusCode(HttpStatus.SC_OK);
            return;
        } catch (SQLException throwables) {
            ServerLogger.getLogger().warning(throwables.toString());
        }
        httpResponse.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
    }
}
