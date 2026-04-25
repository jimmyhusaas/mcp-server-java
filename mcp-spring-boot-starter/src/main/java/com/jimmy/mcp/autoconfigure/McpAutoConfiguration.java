package com.jimmy.mcp.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jimmy.mcp.server.McpServer;
import com.jimmy.mcp.server.RequestHandler;
import com.jimmy.mcp.server.ToolRegistry;
import com.jimmy.mcp.tools.McpTool;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Spring Boot Auto-Configuration for the MCP stdio server.
 *
 * Activated automatically when this JAR is on the classpath.
 * Wires up the full JSON-RPC stack so users only need to:
 *
 * <ol>
 *   <li>Add {@code mcp-spring-boot-starter} as a Maven/Gradle dependency.</li>
 *   <li>Implement {@link McpTool} and annotate with {@code @Component}.</li>
 *   <li>Run — the server handles the rest.</li>
 * </ol>
 *
 * Every bean is guarded with {@code @ConditionalOnMissingBean} so users can
 * override any part of the stack by declaring their own bean of the same type.
 */
@AutoConfiguration
@EnableConfigurationProperties(McpServerProperties.class)
public class McpAutoConfiguration {

    /**
     * ObjectMapper used for all JSON-RPC serialisation.
     * Skipped if the application already declares an ObjectMapper bean
     * (e.g. from Spring Web's JacksonAutoConfiguration).
     */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    /**
     * Registry that collects every {@code McpTool} bean in the application context.
     * Spring auto-injects all McpTool implementations here — no manual registration needed.
     */
    @Bean
    @ConditionalOnMissingBean
    public ToolRegistry toolRegistry(List<McpTool> tools) {
        return new ToolRegistry(tools);
    }

    /**
     * JSON-RPC dispatcher. Uses {@link McpServerProperties} so server name/version
     * are configurable via {@code mcp.server.*} in {@code application.properties}.
     */
    @Bean
    @ConditionalOnMissingBean
    public RequestHandler requestHandler(ObjectMapper mapper,
                                         ToolRegistry toolRegistry,
                                         McpServerProperties properties) {
        return new RequestHandler(mapper, toolRegistry, properties);
    }

    /**
     * Stdio transport. Implements {@link org.springframework.boot.CommandLineRunner}
     * so Spring Boot starts the JSON-RPC read loop automatically.
     */
    @Bean
    @ConditionalOnMissingBean
    public McpServer mcpServer(RequestHandler requestHandler, ObjectMapper mapper) {
        return new McpServer(requestHandler, mapper);
    }
}
