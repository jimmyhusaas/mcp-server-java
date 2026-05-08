# Java MCP — Spring Boot Starter for Model Context Protocol

Give any Spring Boot app the ability to expose tools to AI models (Claude, Cursor, Zed…) in under 5 minutes.

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.jimmyhusaas.mcp-server-java</groupId>
    <artifactId>mcp-spring-boot-starter</artifactId>
    <version>v0.1.0</version>
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
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.jimmyhusaas.mcp-server-java</groupId>
    <artifactId>mcp-spring-boot-starter</artifactId>
    <version>v0.1.0</version>
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

## Transport Modes

### stdio (default — for Claude Desktop)

The server reads JSON-RPC from stdin and writes to stdout. This is the standard transport for local MCP clients.

```properties
# application.properties (default, no change needed)
spring.main.web-application-type=none
mcp.server.transport=stdio
```

### SSE (HTTP — for cloud deployment)

The server exposes `GET /sse` and `POST /message` HTTP endpoints. Use this when deploying to a cloud platform.

```properties
# application-sse.properties
spring.main.web-application-type=servlet
server.port=${PORT:8080}
mcp.server.transport=sse
```

```bash
java -jar mcp-server-java.jar --spring.profiles.active=sse
```

**Live demo** (Render free tier):
```
GET https://mcp-server-java-ffs4.onrender.com/sse
```

> Note: free tier spins down after 15 min inactivity — first request may take ~30s to wake up.

---

## What the Starter Provides Automatically

| Component | What it does |
|-----------|-------------|
| `ObjectMapper` | JSON serialisation for JSON-RPC messages |
| `ToolRegistry` | Collects all `McpTool` beans from the Spring context |
| `RequestHandler` | JSON-RPC 2.0 dispatcher (`initialize`, `tools/list`, `tools/call`, `ping`) |
| `McpServer` | stdio transport — reads from stdin, writes to stdout |
| `SseMcpServer` | SSE transport — `GET /sse` + `POST /message` HTTP endpoints |

All beans are `@ConditionalOnMissingBean`, so you can override any of them.

---

## Configurable Properties

```properties
mcp.server.name=my-mcp-server           # default: mcp-server-java
mcp.server.version=1.0.0                # default: 0.1.0
mcp.server.protocol-version=2024-11-05  # default: 2024-11-05
mcp.server.transport=stdio              # default: stdio | sse

# News tool — add any RSS feed (RSS 2.0 or Atom both supported)
mcp.tools.news.rss-urls[0]=https://news.ltn.com.tw/rss/all.xml
mcp.tools.news.rss-urls[1]=https://news.pts.org.tw/xml/newsfeed.xml
```

---

## Sample App

`mcp-server-java/` is a ready-to-run example with three tools:

| Tool | What it does |
|------|-------------|
| `echo` | Echoes back the input — useful for verifying the connection |
| `get_time` | Returns the current time in any IANA timezone |
| `search_taiwan_news` | Searches Taiwan news by keyword across configurable RSS sources |

The news tool supports multiple RSS feeds (RSS 2.0 and Atom) and aggregates results from all sources, deduplicating by title. Default sources: 自由時報 + 公視.

### Build & run (stdio)

```bash
# Requirements: JDK 21, Maven 3.9+
git clone https://github.com/jimmyhusaas/mcp-server-java.git
cd mcp-server-java

mvn install
cd mcp-server-java
./scripts/smoke-test.sh
```

### Run as HTTP server (SSE)

```bash
mvn package -DskipTests
java -jar mcp-server-java/target/mcp-server-java-0.1.0.jar --spring.profiles.active=sse
# Server starts on http://localhost:8080
# SSE endpoint: GET http://localhost:8080/sse
```

### Deploy to Render (free)

1. Fork this repo
2. Create a new Web Service on [render.com](https://render.com) → connect your fork
3. Runtime: **Docker**, Branch: **main**, Instance: **Free**
4. Deploy — Render detects the `Dockerfile` automatically

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
mvn test          # runs all 54 unit tests across both modules
```

Tests are pure unit tests — no Spring context started, no HTTP calls. The tool tests stub `fetchXml()` with fixture XML so they run offline. Atom and RSS 2.0 feed formats are both covered.

---

## Roadmap

- [x] SSE transport — deploy the server to the cloud
- [x] Configurable RSS sources — bring your own feeds
- [x] Atom feed support — works with 公視 and other Atom-format sources
- [ ] `@McpTool` annotation — auto-generate JSON Schema from method signatures
- [ ] Publish to Maven Central

---

## Requirements

- JDK 21
- Maven 3.9+

## License

MIT
