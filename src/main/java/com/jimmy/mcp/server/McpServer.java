package com.jimmy.mcp.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * The stdio read/write loop — this is the actual MCP server.
 *
 * Transport is newline-delimited JSON over stdin/stdout:
 *   - one complete JSON-RPC request per line on stdin
 *   - one complete JSON-RPC response per line on stdout
 *   - stdout MUST be flushed after every response (Claude Desktop reads line-by-line)
 *   - anything non-JSON on stdout will break the client → logs go to stderr only
 *
 * Why CommandLineRunner: runs after Spring context is up, blocks main thread until
 * stdin closes. Perfect fit for a long-running stdio daemon.
 */
@Component
public class McpServer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(McpServer.class);

    private final ObjectMapper mapper;
    private final RequestHandler requestHandler;

    public McpServer(ObjectMapper mapper, RequestHandler requestHandler) {
        this.mapper = mapper;
        this.requestHandler = requestHandler;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("MCP server started, waiting for JSON-RPC messages on stdin...");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(new java.io.OutputStreamWriter(System.out, StandardCharsets.UTF_8), false)) {

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                JsonNode request;
                try {
                    request = mapper.readTree(line);
                } catch (Exception parseEx) {
                    log.warn("Failed to parse incoming JSON: {}", parseEx.getMessage());
                    writeLine(writer, parseError(parseEx.getMessage()));
                    continue;
                }

                log.debug("<-- {}", line);

                Optional<JsonNode> response = requestHandler.handle(request);
                if (response.isPresent()) {
                    String responseLine = mapper.writeValueAsString(response.get());
                    log.debug("--> {}", responseLine);
                    writeLine(writer, responseLine);
                }
            }
        }
        log.info("stdin closed, MCP server exiting.");
    }

    private void writeLine(PrintWriter writer, String json) {
        // Single write + explicit flush: Claude Desktop buffers on line boundaries.
        writer.print(json);
        writer.print('\n');
        writer.flush();
    }

    private String parseError(String message) {
        return """
            {"jsonrpc":"2.0","id":null,"error":{"code":-32700,"message":"Parse error: %s"}}
            """.formatted(message == null ? "unknown" : message.replace("\"", "\\\"")).trim();
    }
}
