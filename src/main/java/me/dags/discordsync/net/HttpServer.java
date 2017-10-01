package me.dags.discordsync.net;

import fi.iki.elonen.NanoHTTPD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author dags <dags@dags.me>
 */
public class HttpServer extends NanoHTTPD implements Server {

    private static final Logger logger = LoggerFactory.getLogger("DiscordHttp");
    private static final Response DENIED = newFixedLengthResponse(Server.FORBIDDEN, Server.TEXT, "");
    private final Map<String, Handler> handlers = new ConcurrentHashMap<>();

    public HttpServer(String hostname, int port) {
        super(hostname, port);
    }

    public HttpServer route(String path, Handler handler) {
        handlers.put(path.toLowerCase(), handler);
        return this;
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Handler handler = handlers.get(uri.toLowerCase());

        if (handler != null) {
            return handler.handle(this, session);
        }

        if (uri.equalsIgnoreCase("/favicon.ico")) {
            return denied();
        }

        logger.info("Invalid request received from: {}, uri: {}", session.getRemoteIpAddress(), uri);
        return denied();
    }

    @Override
    public Response denied() {
        return DENIED;
    }

    @Override
    public Response text(String in) {
        return newFixedLengthResponse(Server.ACCEPTED, Server.TEXT, in);
    }

    @Override
    public Response html(String in) {
        return newFixedLengthResponse(Server.ACCEPTED, Server.HTML, in);
    }

    @Override
    public Response redirect(String url) {
        Response response = newFixedLengthResponse(Server.REDIRECT, Server.TEXT, "");
        response.addHeader("Location", url);
        return response;
    }
}
