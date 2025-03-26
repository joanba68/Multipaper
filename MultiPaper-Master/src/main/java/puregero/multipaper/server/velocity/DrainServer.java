package puregero.multipaper.server.velocity;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class DrainServer {
    private final int port;
    private HttpServer server;
    private final Logger logger;
    private final DrainListener listener;

    public interface DrainListener {
        boolean onDrain(String serverName);
    }

    public DrainServer(Logger logger, int port, DrainListener listener) {
        this.logger = logger;
        this.port = port;
        this.listener = listener;

        try {
            // TODO: Replace with a proper HTTP REST library
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/start-drain", exchange -> {
                try {
                    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                        sendResponse(exchange, 405, "Method Not Allowed");
                        return;
                    }

                    String path = exchange.getRequestURI().getPath();
                    String[] parts = path.split("/start-drain/");

                    if (parts.length < 2 || parts[1].isEmpty()) {
                        sendResponse(exchange, 400, "Bad Request: Missing server name");
                        return;
                    }

                    String serverName = parts[1];
                    boolean success = listener.onDrain(serverName);

                    if (success) {
                        sendResponse(exchange, 200, "OK");
                    } else {
                        sendResponse(exchange, 404, "Not Found");
                    }
                } catch (Exception e) {
                    sendResponse(exchange, 500, "Internal Server Error");
                    logger.error("Error handling request", e);
                    e.printStackTrace();
                } finally {
                    exchange.close();
                }
            });
            server.setExecutor(null);
            server.start();
            logger.info("[DrainServer] Listening on 0.0.0.0:{}", port);
        } catch (IOException e) {
            logger.error("Error creating HTTP server", e);
            e.printStackTrace();
        }
    }

    private void sendResponse(HttpExchange exchange, int code, String message) throws IOException {
        exchange.sendResponseHeaders(code, message.length());
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(message.getBytes());
        }
    }
}
