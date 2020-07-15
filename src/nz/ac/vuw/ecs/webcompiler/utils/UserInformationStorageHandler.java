package nz.ac.vuw.ecs.webcompiler.utils;

import nz.ac.vuw.ecs.webcompiler.Main;
import org.apache.http.*;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.util.EntityUtils;

import javax.json.Json;
import javax.json.JsonObject;
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
        } else {
            httpResponse.setStatusCode(HttpStatus.SC_BAD_REQUEST);
        }
    }

    private void post(HttpRequest httpRequest, HttpResponse httpResponse, HttpContext httpContext) throws HttpException, IOException {
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
        String other_langs = jsonObject.getString("otherLangs");
        String ide_experience = jsonObject.getString("ideExperience");

        try {
            Connection db = DriverManager.getConnection(Main.DATABASE_CONN_STRING);
            PreparedStatement stmt = db.prepareStatement("INSERT INTO user_information" +
                    "(id, age, occupation, java_experience, education, other_langs, ide_experience) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)");

            stmt.setString(1, id);
            stmt.setString(2, age);
            stmt.setString(3, occupation);
            stmt.setString(4, java_experience);
            stmt.setString(5, education);
            stmt.setString(6, other_langs);
            stmt.setString(7, ide_experience);

            stmt.executeUpdate();
            httpResponse.setStatusCode(HttpStatus.SC_OK);
            return;
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        httpResponse.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
    }
}
