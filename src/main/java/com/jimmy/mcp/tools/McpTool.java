package com.jimmy.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Contract every MCP tool implements.
 *
 * Why an interface:
 *   - Adding a new tool == new @Component implementing this → zero changes in the server core.
 *   - Keeps the stdio loop, JSON-RPC dispatch, and tool logic orthogonal.
 *
 * Why getInputSchema returns JsonNode:
 *   - MCP's `tools/list` response embeds a JSON Schema object directly.
 *   - Returning a parsed JsonNode lets us emit it as-is without re-serializing.
 */
public interface McpTool {

    /** Unique tool name, e.g. "echo". Surfaced to the AI model. */
    String getName();

    /** Human-readable description. This is what the AI model reads to decide when to call us. */
    String getDescription();

    /** JSON Schema describing the `arguments` object that `execute` will receive. */
    JsonNode getInputSchema();

    /**
     * Actually run the tool.
     * @param args the `arguments` field from the tools/call request (may be empty object)
     * @return a plain-text response; RequestHandler will wrap it in MCP's content format.
     */
    String execute(JsonNode args);
}
