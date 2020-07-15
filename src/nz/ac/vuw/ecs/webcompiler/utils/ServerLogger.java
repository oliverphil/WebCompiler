package nz.ac.vuw.ecs.webcompiler.utils;

import org.apache.http.ExceptionLogger;

import java.io.IOException;
import java.util.logging.*;

public class ServerLogger implements ExceptionLogger {

    private static final Logger logger = Logger.getAnonymousLogger();

    public static ServerLogger setup() {
        try {
            logger.setLevel(Level.ALL);

            FileHandler textFile = new FileHandler("log.txt", true);
            textFile.setFormatter(new SimpleFormatter());

            logger.addHandler(textFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new ServerLogger();
    }

    public static Logger getLogger() {
        return logger;
    }

    @Override
    public void log(Exception e) {
        logger.warning(e.toString());
    }
}
