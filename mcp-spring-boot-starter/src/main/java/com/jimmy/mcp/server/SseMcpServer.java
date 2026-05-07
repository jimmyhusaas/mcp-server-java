package com.jimmy.mcp.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP/SSE transport layer for the MCP server.
 *
 * <p>Implements the MCP SSE transport spec:
 * <ol>
 *   <li>Client connects to {@code GET /sse} — server opens an SSE stream and sends
 *       an {@code endpoint} event pointing to {@code /message?sessionId=<id>}.</li>
 *   <li>Client POSTs JSON-RPC messages to {@code /message?sessionId=<id>}.</li>
 *   <li>Server dispatches each message via {@link RequestHandler} and pushes the
 *       JSON-RPC response back over the SSE stream as a {@code message} event.</li>
 * </ol>
 *
 * <p>Activated when {@code mcp.server.transport=sse} is set in
 * {@code application.properties}. To enable, also remove the
 * {@code spring.main.web-application-type=none} line so the embedded web server starts.
 *
 * <p>Example {@code application-sse.properties}:
 * <pre>
 * spring.main.web-application-type=servlet
 * server.port=8080
 * mcp.server.transport=sse
 * </pre>
 */
@RestController
public class SseMcpServer {

    private static final Logger log = LoggerFactory.getLogger(SseMcpServer.class);

    private final RequestHandler handler;
    private final ObjectMapper mapper;

    /** Active SSE connections keyed by session ID. */
    private final ConcurrentHashMap<String, SseEmitter> sessions = new ConcurrentHashMap<>();

    public SseMcpServer(RequestHandler handler, ObjectMapper mapper) {
        this.handler = handler;
        this.mapper = mapper;
    }

    /**
     * SSE endpoint — client connects here to establish a long-lived stream.
     * Immediately sends an {@code endpoint} event so the client knows where to POST.
     */
    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect() {
        String sessionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        sessions.put(sessionId, emitter);
        emitter.onCompletion(() -> {
            sessions.remove(sessionId);
            log.info("SSE session closed: {}", sessionId);
        });
        emitter.onTimeout(() -> {
            sessions.remove(sessionId);
            log.warn("SSE session timed out: {}", sessionId);
        });

        try {
            emitter.send(SseEmitter.event()
                    .name("endpoint")
                    .data("/message?sessionId=" + sessionId));
            log.info("SSE session opened: {}", sessionId);
        } catch (IOException e) {
            log.error("Failed to send endpoint event to session {}", sessionId, e);
            sessions.remove(sessionId);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    /**
     * Message endpoint — client POSTs JSON-RPC requests here.
     * Dispatches via {@link RequestHandler} and pushes the response over SSE.
     */
    @PostMapping("/message")
    public ResponseEntity<Void> message(@RequestParam String sessionId,
                                        @RequestBody String body) {
        SseEmitter emitter = sessions.get(sessionId);
        if (emitter == null) {
            log.warn("Received message for unknown session: {}", sessionId);
            return ResponseEntity.notFound().build();
        }

        try {
            JsonNode request = mapper.readTree(body);
            Optional<JsonNode> response = handler.handle(request);

            if (response.isPresent()) {
                emitter.send(SseEmitter.event()
                        .name("message")
                        .data(mapper.writeValueAsString(response.get())));
            }
        } catch (Exception e) {
            log.error("Error handling message for session {}", sessionId, e);
            try {
                JsonNode errorResponse = mapper.createObjectNode()
                        .put("jsonrpc", "2.0")
                        .<com.fasterxml.jackson.databind.node.ObjectNode>set("error",
                                mapper.createObjectNode()
                                        .put("code", -32700)
                                        .put("message", "Internal error: " + e.getMessage()));
                emitter.send(SseEmitter.event()
                        .name("message")
                        .data(mapper.writeValueAsString(errorResponse)));
            } catch (IOException ignored) {}
        }

        return ResponseEntity.ok().build();
    }
}
