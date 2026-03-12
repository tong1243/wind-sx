package com.wut.screenwebsx.Handler;

import com.wut.screenwebsx.Context.WebSocketSessionContext;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import static com.wut.screencommonsx.Static.WebModuleStatic.FRONT_TRAJ_SESSION_KEY;

@Component
public class TrajWebSocketHandler extends TextWebSocketHandler {
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 使用会话ID作为键来存储会话
        String sessionId = session.getId();
        WebSocketSessionContext.addSession(sessionId, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        super.handleTextMessage(session, message);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        // 当连接关闭时，从会话存储中移除该会话
        String sessionId = session.getId();
        WebSocketSessionContext.removeSession(sessionId);
        super.afterConnectionClosed(session, status);
    }

}
