package me.dags.discordsync.net;

import fi.iki.elonen.NanoHTTPD;

/**
 * @author dags <dags@dags.me>
 */
public interface Handler {

    NanoHTTPD.Response handle(Server writer, NanoHTTPD.IHTTPSession session);
}
