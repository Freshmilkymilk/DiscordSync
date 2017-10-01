package me.dags.discordsync.net;

import fi.iki.elonen.NanoHTTPD;

import java.util.List;

/**
 * @author dags <dags@dags.me>
 */
public interface Server {

    String HTML = NanoHTTPD.MIME_HTML;
    String TEXT = NanoHTTPD.MIME_PLAINTEXT;
    NanoHTTPD.Response.IStatus ACCEPTED = NanoHTTPD.Response.Status.ACCEPTED;
    NanoHTTPD.Response.IStatus FORBIDDEN = NanoHTTPD.Response.Status.FORBIDDEN;
    NanoHTTPD.Response.IStatus REDIRECT = NanoHTTPD.Response.Status.REDIRECT;

    NanoHTTPD.Response denied();

    NanoHTTPD.Response text(String in);

    NanoHTTPD.Response html(String in);

    NanoHTTPD.Response redirect(String in);

    default String getParam(NanoHTTPD.IHTTPSession session, String key) {
        List<String> values = session.getParameters().get(key);
        if (values == null || values.size() == 0) {
            return null;
        }
        return values.get(0);
    }
}
