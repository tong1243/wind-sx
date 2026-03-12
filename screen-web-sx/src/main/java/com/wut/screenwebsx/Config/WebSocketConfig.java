package com.wut.screenwebsx.Config;

import com.wut.screenwebsx.Handler.TrajWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
public class WebSocketConfig implements WebSocketConfigurer {
    private final TrajWebSocketHandler trajWebSocketHandler;

    @Autowired
    public WebSocketConfig(TrajWebSocketHandler trajWebSocketHandler) {
        this.trajWebSocketHandler = trajWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(trajWebSocketHandler, "/socket/traj")
                .setAllowedOrigins("*");
    }

}
