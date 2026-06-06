# Java MCP — Enterprise AI Integration for Spring Boot

AI models need access to your data to be useful. Your data lives in Java systems that have been running for years. This project bridges the two — without rewriting your backend, without sending data to third-party pipelines, and without losing control of what the AI can access.

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

## The Problem with AI Integration in Enterprise Java

Python has LangChain, CrewAI, and AutoGen. Java has almost nothing native.

Yet 20–30% of enterprise backends — banking, insurance, telecom, manufacturing, healthcare, government — run on Java. These systems hold the most valuable data and face the strictest compliance requirements. They also have the most to gain from AI.

The typical workarounds all have the same problem:

| Workaround | Cost |
|---|---|
| Rewrite logic in Python | Two stacks to maintain, months of work, compliance re-review |
| Expose everything via new REST APIs | Security surface expands, deployment overhead, often blocked |
| Use a cloud AI pipeline | Data leaves your infrastructure — unacceptable in regulated industries |

---

## What This Project Gives You

A single Spring Boot dependency that turns any Java method into an AI-callable tool — while keeping your data exactly where it already is.

Implement one interface, annotate with `@Component`, rebuild. That's the entire integration:

```java
@Component
public class QueryOrderTool implements McpTool {

    @Autowired private OrderRepository orders;

    @Override
    public String getName() { return "query_order"; }

    @Override
    public String getDescription() {
        return "Look up an order by ID and return its current status and line items.";
    }

    @Override
    public String execute(JsonNode args) {
        String orderId = args.path("orderId").asText();
        return orders.findById(orderId)
                     .map(Order::toSummary)
                     .orElse("Order not found: " + orderId);
    }

    @Override
    public JsonNode getInputSchema() { /* ... */ }
}
```

The AI can now answer "What's the status of order #12345?" by calling your existing repository. Your JPA layer, your business rules, your Spring Security — all unchanged.

---

## Security Model

MCP is a permission boundary, not a passthrough.

**You decide what the AI can access.** Each `McpTool` you implement is an explicit gate. If you don't open it, the AI cannot reach it — not your database, not your internal APIs, not your file system.

**Data stays on your infrastructure.** The MCP server runs as a process on your own hardware or VMs. Nothing is sent to a third-party pipeline before it reaches your code.

**Every AI call is a Java method call.** That means Spring Security, `@Transactional`, rate limiting, and audit logging all apply exactly as they do today. There is no new security model to learn.

**Zero implicit access.** Claude cannot infer or guess its way into your system. It can only call tools you explicitly wrote.

---

## Enterprise Use Cases

**Financial services — real-time queries without a new API layer**
Your core banking system has 15 years of Java business rules. Wrap key read operations as MCP tools. Risk officers ask "What's our current exposure to sector X?" — the query hits your system, the answer never passes through a third party.

**Healthcare / 健保 — compliant data access**
Patient data never leaves your on-prem JVM. Tools return only what a specific role is allowed to see, enforced by your existing Spring Security configuration. The compliance boundary is the same one you already maintain.

**Insurance — underwriting assistant**
Wrap your policy lookup, claims history, and risk scoring services. Underwriters get AI-assisted summaries backed by live production data — not a cached export, not a third-party data lake.

**Manufacturing — supply chain decisions**
Your ERP runs on Java. Write a tool that queries inventory, lead times, and open purchase orders. A planner asks "Can we fulfill this order by Friday?" — the AI calls your system and reasons over real numbers.

**Government / 公部門 — internal knowledge retrieval**
Expose document search and policy lookup as tools. Staff get AI-assisted access to official records without those records ever touching an external AI service.

---

## Project Structure

```
java-mcp/
├── mcp-spring-boot-starter/   ← The library (add this as a dependency)
└── mcp-server-java/           ← Working sample with 4 tools, ready to clone
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

The starter auto-discovers every `@Component` implementing `McpTool` — no manual registration required.

---

## Transport Modes

### stdio (default — Claude Desktop, local tools)

The server reads JSON-RPC from stdin and writes to stdout. Standard transport for local MCP clients.

```properties
spring.main.web-application-type=none
mcp.server.transport=stdio
```

### SSE (HTTP — internal servers, cloud deployment)

Exposes `GET /sse` and `POST /message` HTTP endpoints. Use when the MCP server runs on a shared server or internal platform rather than each user's local machine.

```properties
spring.main.web-application-type=servlet
server.port=${PORT:8080}
mcp.server.transport=sse
```

```bash
java -jar mcp-server-java.jar --spring.profiles.active=sse
```

**Live demo** (Render free tier — first request may take ~30s to wake up):
```
GET https://mcp-server-java-ffs4.onrender.com/sse
```

---

## What the Starter Provides Automatically

| Component | What it does |
|-----------|-------------|
| `ToolRegistry` | Discovers all `McpTool` beans from the Spring context |
| `RequestHandler` | JSON-RPC 2.0 dispatcher (`initialize`, `tools/list`, `tools/call`, `ping`) |
| `McpServer` | stdio transport — reads from stdin, writes to stdout |
| `SseMcpServer` | SSE transport — `GET /sse` + `POST /message` HTTP endpoints |
| `ObjectMapper` | JSON serialisation for all JSON-RPC messages |

All beans are `@ConditionalOnMissingBean` — override any of them with your own implementation.

---

## Configurable Properties

```properties
mcp.server.name=my-mcp-server           # default: mcp-server-java
mcp.server.version=1.0.0                # default: 0.1.0
mcp.server.protocol-version=2024-11-05  # default: 2024-11-05
mcp.server.transport=stdio              # stdio | sse

# News tool RSS sources (RSS 2.0 and Atom both supported)
mcp.tools.news.rss-urls[0]=https://news.ltn.com.tw/rss/all.xml
mcp.tools.news.rss-urls[1]=https://news.pts.org.tw/xml/newsfeed.xml
mcp.tools.news.rss-urls[2]=https://public.twreporter.org/rss/twreporter-rss.xml
```

---

## Sample App

`mcp-server-java/` is a ready-to-run example demonstrating the integration pattern with 4 working tools:

| Tool | What it does |
|------|-------------|
| `echo` | Echoes input — verifies the MCP connection end-to-end |
| `get_time` | Returns current time in any IANA timezone |
| `search_taiwan_news` | Searches Taiwan news by keyword across configurable RSS sources (自由時報, 公視, 報導者) |
| `search_cna_news` | Searches 中央社 real-time news via their JSON API |

### Build & run

```bash
# Requirements: JDK 21, Maven 3.9+
git clone https://github.com/jimmyhusaas/mcp-server-java.git
cd mcp-server-java
mvn install

# Smoke test — fires 5 JSON-RPC messages and checks responses
cd mcp-server-java && ./scripts/smoke-test.sh
```

### Connect to Claude Desktop

Edit Claude Desktop's config:

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

Restart Claude Desktop. In a new chat:

> Use search_cna_news to find news about 半導體

---

## Tests

```bash
mvn test    # 65 unit tests across both modules
```

Pure unit tests — no Spring context, no HTTP calls. Tool tests stub network calls with fixture data and run fully offline.

---

## Roadmap

- [x] SSE transport — deploy the server to the cloud
- [x] Configurable RSS sources — bring your own feeds
- [x] Atom feed support
- [x] 中央社 JSON API integration
- [ ] `@McpTool` annotation — auto-generate JSON Schema from method signatures
- [ ] Publish to Maven Central

---

## Requirements

- JDK 21
- Maven 3.9+

## License

MIT
