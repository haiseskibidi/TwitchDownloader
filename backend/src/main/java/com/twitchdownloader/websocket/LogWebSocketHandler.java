package com.twitchdownloader.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class LogWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(LogWebSocketHandler.class);

    // Maps session to subscribed streamer username (or "SYSTEM" for general events)
    private final Map<WebSocketSession, String> sessionSubscriptions = new ConcurrentHashMap<>();
    private final List<WebSocketSession> activeSessions = new CopyOnWriteArrayList<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Map<String, Object> attributes = session.getAttributes();
        if (attributes.get("userId") == null) {
            log.warn("WebSocket connection rejected: unauthorized session {}", session.getId());
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        activeSessions.add(session);
        log.info("WebSocket connection established: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        activeSessions.remove(session);
        sessionSubscriptions.remove(session);
        log.info("WebSocket connection closed: {} (status: {})", session.getId(), status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload().trim();
        if (payload.startsWith("SUBSCRIBE:")) {
            String target = payload.substring("SUBSCRIBE:".length()).trim().toLowerCase();
            sessionSubscriptions.put(session, target);
            log.info("Session {} subscribed to: {}", session.getId(), target);
        } else if (payload.equals("UNSUBSCRIBE")) {
            sessionSubscriptions.remove(session);
            log.info("Session {} unsubscribed", session.getId());
        }
    }

    public void sendLogToSubscribers(String streamerUsername, String logLine) {
        String targetUsername = streamerUsername.toLowerCase();
        String formattedMessage = String.format("LOG:%s:%s", streamerUsername, logLine);
        TextMessage textMessage = new TextMessage(formattedMessage);

        for (WebSocketSession session : activeSessions) {
            String subscription = sessionSubscriptions.get(session);
            if (targetUsername.equals(subscription)) {
                sendMessageSafe(session, textMessage);
            }
        }
    }

    public void broadcastSystemEvent(String eventType, String payload) {
        String formattedMessage = String.format("EVENT:%s:%s", eventType, payload);
        TextMessage textMessage = new TextMessage(formattedMessage);

        for (WebSocketSession session : activeSessions) {
            // Broadcast system events to ALL connected clients
            sendMessageSafe(session, textMessage);
        }
    }

    private void sendMessageSafe(WebSocketSession session, TextMessage message) {
        if (session.isOpen()) {
            try {
                synchronized (session) {
                    session.sendMessage(message);
                }
            } catch (IOException e) {
                log.error("Failed to send WebSocket message to session {}: {}", session.getId(), e.getMessage());
            }
        }
    }
}
