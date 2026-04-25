package com.jimmy.mcp.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the MCP server.
 *
 * Configure in {@code application.properties}:
 * <pre>
 * mcp.server.name=my-mcp-server
 * mcp.server.version=1.0.0
 * </pre>
 */
@ConfigurationProperties(prefix = "mcp.server")
public class McpServerProperties {

    /** Server name reported during the MCP {@code initialize} handshake. */
    private String name = "mcp-server-java";

    /** Server version reported during the MCP {@code initialize} handshake. */
    private String version = "0.1.0";

    /** MCP protocol version this server speaks. */
    private String protocolVersion = "2024-11-05";

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getProtocolVersion() { return protocolVersion; }
    public void setProtocolVersion(String protocolVersion) { this.protocolVersion = protocolVersion; }
}
