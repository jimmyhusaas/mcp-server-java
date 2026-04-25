package com.jimmy.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Smallest meaningful MCP tool: echoes back whatever text the model sends.
 * Useful only for verifying the full round-trip (Claude → MCP → Tool → MCP → Claude).
 */
@Component
public class EchoTool implements McpTool {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String getName() {
        return "echo";
    }

    @Override
    public String getDescription() {
        return "Echoes back the provided text. Useful for verifying the MCP connection.";
    }

    @Override
    public JsonNode getInputSchema() {
        // Equivalent to:
        // {
        //   "type": "object",
        //   "properties": { "text": { "type": "string", "description": "Text to echo back" } },
        //   "required": ["text"]
        // }
        try {
            return mapper.readTree("""
                {
                  "type": "object",
                  "properties": {
                    "text": {
                      "type": "string",
                      "description": "Text to echo back"
                    }
                  },
                  "required": ["text"]
                }
                """);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build EchoTool schema", e);
        }
    }

    @Override
    public String execute(JsonNode args) {
        JsonNode text = args.get("text");
        if (text == null || text.isNull()) {
            return "(no text provided)";
        }
        return text.asText();
    }
}
