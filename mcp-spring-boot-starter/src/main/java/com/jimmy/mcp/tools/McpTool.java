package com.jimmy.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Contract for every MCP tool exposed by this server.
 *
 * Implement this interface and annotate the class with {@code @Component} —
 * the starter auto-discovers it and registers it in the MCP tool list.
 *
 * <pre>{@code
 * @Component
 * public class MyTool implements McpTool {
 *     public String getName()        { return "my_tool"; }
 *     public String getDescription() { return "Does something useful."; }
 *     public JsonNode getInputSchema() { ... }
 *     public String execute(JsonNode args) { ... }
 * }
 * }</pre>
 */
public interface McpTool {

    /** Unique tool identifier (snake_case, e.g. {@code get_time}). */
    String getName();

    /** Human-readable description shown to the LLM client. */
    String getDescription();

    /**
     * JSON Schema object describing the tool's accepted arguments.
     * Must be a valid JSON Schema of type "object".
     */
    JsonNode getInputSchema();

    /**
     * Execute the tool with the given arguments.
     *
     * @param args parsed JSON object from the MCP {@code tools/call} request
     * @return plain-text result (will be wrapped in MCP content format by the server)
     */
    String execute(JsonNode args);
}
