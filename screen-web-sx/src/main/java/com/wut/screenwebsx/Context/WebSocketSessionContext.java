package com.wut.screenwebsx.Context;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.socket.WebSocketSession;

public class WebSocketSessionContext {
    private static final ConcurrentHashMap<String, WebSocketSession> SESSIONS = new ConcurrentHashMap<>();

    public static void addSession(String sessionId, WebSocketSession session) {
        SESSIONS.put(sessionId, session);
    }

    public static void removeSession(String sessionId) {
        SESSIONS.remove(sessionId);
    }

    public static WebSocketSession getSession(String sessionId) {
        return SESSIONS.get(sessionId);
    }

    public static ConcurrentHashMap<String, WebSocketSession> getAllSessions() {
        return SESSIONS;
    }
}