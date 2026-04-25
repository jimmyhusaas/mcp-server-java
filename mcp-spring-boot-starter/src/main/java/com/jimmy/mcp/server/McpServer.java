package com.jimmy.mcp.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Optional;

/**
 * Stdio transport layer for the MCP server.
 *
 * Reads newline-delimited JSON-RPC messages from stdin, dispatches them to
 * {@link RequestHandler}, and writes responses to stdout — one JSON object
 * per line. All application logs go to stderr (configured in logback-spring.xml).
 *
 * Created automatically by {@link com.jimmy.mcp.autoconfigure.McpAutoConfiguration}.
 */
public class McpServer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(McpServer.class);

    private final RequestHandler handler;
    private final ObjectMapper mapper;

    public McpServer(RequestHandler handler, ObjectMapper mapper) {
        this.handler = handler;
        this.mapper = mapper;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("MCP server started, waiting for JSON-RPC messages on stdin...");

        BufferedReader in  = new BufferedReader(new InputStreamReader(System.in));
        PrintWriter   out  = new PrintWriter(System.out, true);

        String line;
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;

            JsonNode response;
            try {
                JsonNode request = mapper.readTree(line);
                Optional<JsonNode> result = handler.handle(request);
                if (result.isEmpty()) continue;   // notification — no response
                response = result.get();
            } catch (Exception e) {
                log.error("Failed to parse incoming message: {}", line, e);
                response = mapper.createObjectNode()
                        .put("jsonrpc", "2.0")
                        .put("id",      (String) null)
                        .<ObjectNode>set("error", mapper.createObjectNode()
                                .put("code",    -32700)
                                .put("message", "Parse error: " + e.getMessage()));
            }

            out.println(mapper.writeValueAsString(response));
        }

        log.info("stdin closed, MCP server exiting.");
    }
}
