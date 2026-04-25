package com.jimmy.mcp.server;

import com.jimmy.mcp.tools.McpTool;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Holds every McpTool discovered by Spring component scan.
 *
 * Created by {@link com.jimmy.mcp.autoconfigure.McpAutoConfiguration}.
 * Spring injects all {@code McpTool} beans found in the application context,
 * so adding a new tool is purely "drop a new {@code @Component} class".
 */
public class ToolRegistry {

    private final Map<String, McpTool> toolsByName = new LinkedHashMap<>();

    public ToolRegistry(List<McpTool> tools) {
        for (McpTool tool : tools) {
            toolsByName.put(tool.getName(), tool);
        }
    }

    public Collection<McpTool> all() {
        return toolsByName.values();
    }

    public Optional<McpTool> findByName(String name) {
        return Optional.ofNullable(toolsByName.get(name));
    }
}
