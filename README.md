# mcp-server-java

A minimal **Model Context Protocol** server written in Java / Spring Boot 3, using **stdio** transport.

## Requirements

- JDK 21
- Maven 3.9+
- Claude Desktop (for end-to-end verification)

## Build

```bash
mvn -DskipTests package
```

Produces `target/mcp-server-java-0.1.0.jar` (executable fat-jar via `spring-boot-maven-plugin`).

## Run (stand-alone sanity check)

```bash
java -jar target/mcp-server-java-0.1.0.jar
```

Then paste one JSON-RPC line at a time (hit Enter after each):

```json
{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"manual","version":"0"}}}
```

Expected reply (on one line):

```json
{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2024-11-05","capabilities":{"tools":{}},"serverInfo":{"name":"mcp-server-java","version":"0.1.0"}}}
```

Then:

```json
{"jsonrpc":"2.0","id":2,"method":"tools/list"}
{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"echo","arguments":{"text":"hi"}}}
{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"get_time","arguments":{"zone":"UTC"}}}
```

Ctrl-D to exit.

## Hook into Claude Desktop

Edit Claude Desktop's MCP config:

- **macOS**: `~/Library/Application Support/Claude/claude_desktop_config.json`
- **Windows**: `%APPDATA%\Claude\claude_desktop_config.json`

Add (replace the path with your own absolute path):

```json
{
  "mcpServers": {
    "java-demo": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/mcp-server-java-0.1.0.jar"
      ]
    }
  }
}
```

Restart Claude Desktop. In a new chat, open the tool menu — you should see `echo` and `get_time`. Ask:

> Use the echo tool to say "hello from Java"

> What time is it right now? Use the get_time tool in UTC.

## Project layout

```
src/main/java/com/jimmy/mcp/
├── McpServerApplication.java   — Spring Boot entry point
├── server/
│   ├── McpServer.java          — stdio read/write loop (CommandLineRunner)
│   ├── RequestHandler.java     — JSON-RPC dispatch (initialize / tools/list / tools/call)
│   └── ToolRegistry.java       — auto-discovers @Component McpTool beans
└── tools/
    ├── McpTool.java            — interface: getName / getDescription / getInputSchema / execute
    ├── EchoTool.java           — echoes input
    └── GetTimeTool.java        — returns current time in given IANA timezone
```

## Add a new tool (Week 2+)

1. Create `src/main/java/com/jimmy/mcp/tools/MyTool.java` implementing `McpTool`.
2. Annotate with `@Component`.
3. Rebuild. `ToolRegistry` picks it up automatically via constructor-injected `List<McpTool>`.

## Protocol notes

- Transport: **stdio**, newline-delimited JSON. No `Content-Length` headers.
- Messages follow **JSON-RPC 2.0**.
- MCP methods handled in Week 1: `initialize`, `tools/list`, `tools/call`, plus `notifications/initialized` (silently) and `ping`.
- **stdout is sacred**: anything non-JSON there breaks the client. All logs go to stderr via `logback-spring.xml`. Spring banner is disabled.

## Roadmap

- Week 2: real tool — `search_taiwan_news(keyword)` hitting RSS feeds.
- Week 3: extract core into `mcp-spring-boot-starter`; `@McpTool` annotation.
- Week 4: write-up + Medium Taiwan / PTT / LinkedIn publish.
