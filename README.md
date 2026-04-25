# Java MCP — Spring Boot Starter for Model Context Protocol

Give any Spring Boot app the ability to expose tools to AI models (Claude, Cursor, Zed…) in under 5 minutes.

```xml
<dependency>
    <groupId>com.jimmy</groupId>
    <artifactId>mcp-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

---

## What is MCP?

[Model Context Protocol](https://modelcontextprotocol.io) is an open standard by Anthropic that defines how AI models call external tools. Think of it as the REST API standard for AI — implement it once, and any MCP-compatible client (Claude Desktop, Cursor, Zed…) can call your tools.

## Why Java?

Python already has LangChain, CrewAI, AutoGen. Java has almost nothing native.  
Yet Java runs 20–30% of enterprise backends — financial services, insurance, telco, manufacturing — all of which need AI integration. This project fills that gap.

---

## Project Structure

```
java-mcp/
├── mcp-spring-boot-starter/   ← The library (add this as a dependency)
└── mcp-server-java/           ← Sample app (3 working tools, ready to clone)
```

---

## Quick Start

### 1. Add the starter

```xml
<dependency>
    <groupId>com.jimmy</groupId>
    <artifactId>mcp-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

### 2. Configure

```properties
# application.properties
spring.main.web-application-type=none
spring.main.banner-mode=off
mcp.server.name=my-mcp-server
mcp.server.version=1.0.0
```

### 3. Main class

```java
@SpringBootApplication
public class MyMcpServer {
    public static void main(String[] args) {
        SpringApplication.run(MyMcpServer.class, args);
    }
}
```

### 4. Write a tool

```java
@Component
public class MyTool implements McpTool {

    @Override
    public String getName() { return "my_tool"; }

    @Override
    public String getDescription() {
        return "Describe what this tool does — the AI reads this to decide when to call it.";
    }

    @Override
    public JsonNode getInputSchema() {
        try {
            return new ObjectMapper().readTree("""
                {
                  "type": "object",
                  "properties": {
                    "query": { "type": "string", "description": "Search query" }
                  },
                  "required": ["query"]
                }
                """);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Override
    public String execute(JsonNode args) {
        String query = args.path("query").asText();
        return "Result for: " + query;
    }
}
```

That's it. The starter auto-discovers every `@Component` implementing `McpTool` and registers it — no manual wiring required.

---

## What the Starter Provides Automatically

| Component | What it does |
|-----------|-------------|
| `ObjectMapper` | JSON serialisation for JSON-RPC messages |
| `ToolRegistry` | Collects all `McpTool` beans from the Spring context |
| `RequestHandler` | JSON-RPC 2.0 dispatcher (`initialize`, `tools/list`, `tools/call`, `ping`) |
| `McpServer` | stdio read/write loop — reads from stdin, writes to stdout |

All beans are `@ConditionalOnMissingBean`, so you can override any of them.

---

## Configurable Properties

```properties
mcp.server.name=my-mcp-server        # default: mcp-server-java
mcp.server.version=1.0.0             # default: 0.1.0
mcp.server.protocol-version=2024-11-05  # default: 2024-11-05
```

---

## Sample App

`mcp-server-java/` is a ready-to-run example with three tools:

| Tool | What it does |
|------|-------------|
| `echo` | Echoes back the input — useful for verifying the connection |
| `get_time` | Returns the current time in any IANA timezone |
| `search_taiwan_news` | Searches Taiwan news by keyword (自由時報 RSS) |

### Build & run the sample

```bash
# Requirements: JDK 21, Maven 3.9+
git clone https://github.com/jimmyhusaas/mcp-server-java.git
cd mcp-server-java

# Build everything (parent + starter + sample)
mvn install

# Run smoke test (fires 5 JSON-RPC messages, checks responses)
cd mcp-server-java
./scripts/smoke-test.sh
```

### Connect to Claude Desktop

Edit Claude Desktop's config file:

- **macOS**: `~/Library/Application Support/Claude/claude_desktop_config.json`
- **Windows**: `%APPDATA%\Claude\claude_desktop_config.json`

```json
{
  "mcpServers": {
    "java-mcp": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/mcp-server-java/target/mcp-server-java-0.1.0.jar"
      ]
    }
  }
}
```

Restart Claude Desktop. In a new chat, try:

> Use the search_taiwan_news tool to find news about 台北

---

## Tests

```bash
mvn test          # runs all 53 unit tests across both modules
```

Tests are pure unit tests — no Spring context started, no HTTP calls. The tool tests stub `fetchXml()` with fixture XML so they run offline.

---

## Roadmap

- [ ] `@McpTool` annotation — auto-generate JSON Schema from method signatures
- [ ] SSE transport — deploy the server to the cloud (not just local stdio)
- [ ] Publish to Maven Central

---

## Requirements

- JDK 21
- Maven 3.9+

## License

MIT
