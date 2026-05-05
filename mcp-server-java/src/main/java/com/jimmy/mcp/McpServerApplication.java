package com.jimmy.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Sample MCP server built with mcp-spring-boot-starter.
 *
 * The starter's auto-configuration wires up the full JSON-RPC stack automatically:
 * ObjectMapper, ToolRegistry, RequestHandler, and McpServer (stdio transport).
 *
 * To add a new tool, implement {@code McpTool} and annotate with {@code @Component} —
 * the starter discovers and registers it automatically.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class McpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }
}
