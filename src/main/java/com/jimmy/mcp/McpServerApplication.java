package com.jimmy.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Entry point of the Java MCP server.
 *
 * This app runs as a stdio JSON-RPC server:
 *   - reads newline-delimited JSON-RPC requests from stdin
 *   - writes responses to stdout (nothing else may go to stdout!)
 *   - logs are redirected to stderr via logback-spring.xml
 *
 * Spring Boot is used purely as a DI container + component scan — the web server is
 * disabled via spring.main.web-application-type=none.
 */
@SpringBootApplication
public class McpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }

    /**
     * Provide an ObjectMapper bean explicitly.
     *
     * Why: Spring Boot's JacksonAutoConfiguration only creates an ObjectMapper bean when
     * Jackson2ObjectMapperBuilder (from spring-web) is on the classpath. We deliberately
     * avoid the web starter (stdio app, not HTTP), so we have to declare this ourselves.
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
