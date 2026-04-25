package com.jimmy.mcp.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jimmy.mcp.autoconfigure.McpServerProperties;
import com.jimmy.mcp.tools.McpTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Handles one JSON-RPC 2.0 request and produces one JSON-RPC response.
 *
 * MCP methods supported:
 *   - initialize                : protocol handshake; returns server info & capabilities
 *   - tools/list                : list all registered tools with their JSON Schemas
 *   - tools/call                : invoke a tool by name and wrap its result in MCP content format
 *   - notifications/initialized : notification, no reply sent
 *   - ping                      : keep-alive, responds with empty result
 *
 * Created by {@link com.jimmy.mcp.autoconfigure.McpAutoConfiguration}.
 */
public class RequestHandler {

    private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);

    private final ObjectMapper mapper;
    private final ToolRegistry toolRegistry;
    private final McpServerProperties properties;

    public RequestHandler(ObjectMapper mapper, ToolRegistry toolRegistry, McpServerProperties properties) {
        this.mapper = mapper;
        this.toolRegistry = toolRegistry;
        this.properties = properties;
    }

    /**
     * @param request parsed JSON-RPC request
     * @return an Optional containing the JSON-RPC response, or empty for notifications
     */
    public Optional<JsonNode> handle(JsonNode request) {
        JsonNode idNode = request.get("id");
        boolean isNotification = (idNode == null || idNode.isNull());
        String method = request.path("method").asText("");

        try {
            JsonNode result = switch (method) {
                case "initialize"                -> handleInitialize();
                case "tools/list"                -> handleToolsList();
                case "tools/call"                -> handleToolsCall(request);
                case "notifications/initialized" -> null;
                case "ping"                      -> mapper.createObjectNode();
                default -> throw new JsonRpcException(-32601, "Method not found: " + method);
            };

            if (isNotification) return Optional.empty();
            return Optional.of(successResponse(idNode, result));

        } catch (JsonRpcException e) {
            if (isNotification) return Optional.empty();
            return Optional.of(errorResponse(idNode, e.code, e.getMessage()));
        } catch (Exception e) {
            log.error("Unhandled error while processing method={}", method, e);
            if (isNotification) return Optional.empty();
            return Optional.of(errorResponse(idNode, -32603, "Internal error: " + e.getMessage()));
        }
    }

    // ---------- method handlers ----------

    private JsonNode handleInitialize() {
        ObjectNode result = mapper.createObjectNode();
        result.put("protocolVersion", properties.getProtocolVersion());

        ObjectNode capabilities = mapper.createObjectNode();
        capabilities.set("tools", mapper.createObjectNode());
        result.set("capabilities", capabilities);

        ObjectNode serverInfo = mapper.createObjectNode();
        serverInfo.put("name", properties.getName());
        serverInfo.put("version", properties.getVersion());
        result.set("serverInfo", serverInfo);

        return result;
    }

    private JsonNode handleToolsList() {
        ArrayNode tools = mapper.createArrayNode();
        for (McpTool tool : toolRegistry.all()) {
            ObjectNode node = mapper.createObjectNode();
            node.put("name", tool.getName());
            node.put("description", tool.getDescription());
            node.set("inputSchema", tool.getInputSchema());
            tools.add(node);
        }
        ObjectNode result = mapper.createObjectNode();
        result.set("tools", tools);
        return result;
    }

    private JsonNode handleToolsCall(JsonNode request) {
        JsonNode params = request.path("params");
        String name = params.path("name").asText("");
        if (name.isEmpty()) {
            throw new JsonRpcException(-32602, "Missing required param: name");
        }

        McpTool tool = toolRegistry.findByName(name)
                .orElseThrow(() -> new JsonRpcException(-32602, "Unknown tool: " + name));

        JsonNode arguments = params.has("arguments") ? params.get("arguments") : mapper.createObjectNode();

        String output;
        boolean isError = false;
        try {
            output = tool.execute(arguments);
        } catch (Exception e) {
            log.warn("Tool '{}' threw during execution", name, e);
            output = "Tool execution failed: " + e.getMessage();
            isError = true;
        }

        ObjectNode textContent = mapper.createObjectNode();
        textContent.put("type", "text");
        textContent.put("text", output);

        ArrayNode content = mapper.createArrayNode();
        content.add(textContent);

        ObjectNode result = mapper.createObjectNode();
        result.set("content", content);
        result.put("isError", isError);
        return result;
    }

    // ---------- response helpers ----------

    private ObjectNode successResponse(JsonNode id, JsonNode result) {
        ObjectNode resp = mapper.createObjectNode();
        resp.put("jsonrpc", "2.0");
        resp.set("id", id);
        resp.set("result", result);
        return resp;
    }

    private ObjectNode errorResponse(JsonNode id, int code, String message) {
        ObjectNode error = mapper.createObjectNode();
        error.put("code", code);
        error.put("message", message);

        ObjectNode resp = mapper.createObjectNode();
        resp.put("jsonrpc", "2.0");
        resp.set("id", id == null ? mapper.nullNode() : id);
        resp.set("error", error);
        return resp;
    }

    private static class JsonRpcException extends RuntimeException {
        final int code;
        JsonRpcException(int code, String message) {
            super(message);
            this.code = code;
        }
    }
}
