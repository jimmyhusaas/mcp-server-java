package com.jimmy.mcp.server;

import com.jimmy.mcp.tools.McpTool;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Holds every McpTool discovered by Spring component scan.
 *
 * Spring's @Component + constructor injection of List<McpTool> means Spring auto-wires
 * every bean that implements McpTool into this registry — so adding a new tool is purely
 * "drop a new class with @Component".
 */
@Component
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
