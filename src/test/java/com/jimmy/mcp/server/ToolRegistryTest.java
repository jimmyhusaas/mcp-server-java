package com.jimmy.mcp.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.jimmy.mcp.tools.McpTool;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ToolRegistryTest {

    private static McpTool stubTool(String name) {
        return new McpTool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name + "-desc"; }
            @Override public JsonNode getInputSchema() { return null; }
            @Override public String execute(JsonNode args) { return name + "-result"; }
        };
    }

    @Test
    void all_returnsAllRegisteredTools() {
        McpTool a = stubTool("tool-a");
        McpTool b = stubTool("tool-b");
        ToolRegistry registry = new ToolRegistry(List.of(a, b));

        assertThat(registry.all()).containsExactly(a, b);
    }

    @Test
    void all_preservesInsertionOrder() {
        List<McpTool> tools = List.of(stubTool("z"), stubTool("a"), stubTool("m"));
        ToolRegistry registry = new ToolRegistry(tools);

        assertThat(registry.all())
                .extracting(McpTool::getName)
                .containsExactly("z", "a", "m");
    }

    @Test
    void findByName_knownTool_returnsPresent() {
        ToolRegistry registry = new ToolRegistry(List.of(stubTool("echo")));

        assertThat(registry.findByName("echo")).isPresent();
        assertThat(registry.findByName("echo").get().getName()).isEqualTo("echo");
    }

    @Test
    void findByName_unknownTool_returnsEmpty() {
        ToolRegistry registry = new ToolRegistry(List.of(stubTool("echo")));

        assertThat(registry.findByName("does-not-exist")).isEmpty();
    }

    @Test
    void all_emptyList_returnsEmptyCollection() {
        ToolRegistry registry = new ToolRegistry(List.of());
        assertThat(registry.all()).isEmpty();
    }

    @Test
    void duplicateName_lastRegistrationWins() {
        McpTool first = stubTool("dupe");
        McpTool second = new McpTool() {
            @Override public String getName() { return "dupe"; }
            @Override public String getDescription() { return "second"; }
            @Override public JsonNode getInputSchema() { return null; }
            @Override public String execute(JsonNode args) { return "second-result"; }
        };
        ToolRegistry registry = new ToolRegistry(List.of(first, second));

        assertThat(registry.findByName("dupe").get().getDescription()).isEqualTo("second");
        assertThat(registry.all()).hasSize(1);
    }
}
