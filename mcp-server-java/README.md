# mcp-server-java

Sample MCP server built with [`mcp-spring-boot-starter`](../mcp-spring-boot-starter).  
Demonstrates three tools: `echo`, `get_time`, and `search_taiwan_news`.

For full documentation see the [root README](../README.md).

---

## Build

```bash
# From the repo root — builds starter first, then this app
mvn install

# Or build only this module (starter must already be installed)
mvn -DskipTests package
```

Output: `target/mcp-server-java-0.1.0.jar`

## Run smoke test

```bash
./scripts/smoke-test.sh
```

Fires 5 JSON-RPC messages through the jar and prints the responses:
`initialize` → `tools/list` → `echo` → `get_time` → `search_taiwan_news`

## Connect to Claude Desktop

```json
{
  "mcpServers": {
    "java-mcp": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/mcp-server-java-0.1.0.jar"]
    }
  }
}
```

Config location: `~/Library/Application Support/Claude/claude_desktop_config.json` (macOS)

## Project layout

```
src/main/java/com/jimmy/mcp/
├── McpServerApplication.java      ← @SpringBootApplication + main()
└── tools/
    ├── EchoTool.java              ← echoes input text
    ├── GetTimeTool.java           ← current time in any IANA timezone
    └── SearchTaiwanNewsTool.java  ← 自由時報 RSS keyword search

src/test/java/com/jimmy/mcp/tools/
├── EchoToolTest.java
├── GetTimeToolTest.java
└── SearchTaiwanNewsToolTest.java
```

## Adding a new tool

1. Create a class implementing `McpTool` (from the starter)
2. Annotate with `@Component`
3. Rebuild — `ToolRegistry` discovers it automatically
