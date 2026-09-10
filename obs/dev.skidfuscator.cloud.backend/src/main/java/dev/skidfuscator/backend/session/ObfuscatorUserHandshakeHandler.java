package dev.skidfuscator.backend.session;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.RequestUpgradeStrategy;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

public class ObfuscatorUserHandshakeHandler extends DefaultHandshakeHandler {
    public ObfuscatorUserHandshakeHandler() {
    }

    public ObfuscatorUserHandshakeHandler(RequestUpgradeStrategy requestUpgradeStrategy) {
        super(requestUpgradeStrategy);
    }

    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
        return new ObfuscatorUserSession();
    }
}
