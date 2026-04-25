# MCP Server 專案背景 context
> 從 claude.ai 對話整理，2026-04-22

---

## 個人背景

- 後端工程師，約 5 年經驗
- 技術棧：Java、Spring Boot、RESTful API、Docker、Kubernetes、CI/CD
- 現職：永聯（近期從待業轉為在職）
- 核心目標：最大化薪資成長 → 進入外商 / SaaS / Remote 市場

---

## 為什麼做這個專案

### 趨勢判斷
- MCP（Model Context Protocol）正在成為 AI 工具互通的事實標準，類比當年的 REST API
- 主流 AI Agent 工具（LangChain、CrewAI、AutoGen）幾乎全是 Python
- Java 企業想整合 AI Agent，**幾乎沒有原生方案** → 這是空缺
- 現有 Java 選項：Spring AI（弱）、LangChain4j（社群小）→ 競爭者極少

### 策略價值
- 在 Java/後端路線上插入 MCP + AI Agent 旗幟，比別人早 6 個月
- 一個計畫同時覆蓋：技術成長 + 開源貢獻 + 個人品牌

---

## 四週實作計畫

### Week 1｜跑起來再說（5–8 小時）
**目標**：本地跑一個可被 Claude Desktop 呼叫的 Hello World MCP Server

任務清單：
- [ ] 閱讀 MCP 官方 spec（modelcontextprotocol.io）
- [ ] Spring Boot 3.x 建立專案
- [ ] 實作 stdio transport（先不用 SSE）
- [ ] 實作 McpTool interface
- [ ] 實作 EchoTool（輸入什麼回什麼）
- [ ] 實作 GetTimeTool（回傳當前時間）
- [ ] 用 Claude Desktop 連上去，確認可以呼叫

**驗收標準**：Claude Desktop 成功呼叫 EchoTool

### Week 2｜做一個有意義的 Tool
**目標**：讓 AI 能查詢真實資料

- 接 RSS feed 或爬蟲
- 實作 `NewsSearchTool`：`search_taiwan_news(keyword)`
- 理由：與個人品牌（台灣地緣政治）掛鉤

### Week 3｜包裝成可被使用的套件
**目標**：讓其他 Java 開發者可以直接用

- 把 MCP 基礎架構抽象化
- 包成 Spring Boot Starter
- 目標：加一個 dependency + 寫 `@McpTool` annotation 就能定義工具
- 推上 GitHub，寫 README

### Week 4｜寫文章建立能見度
**目標**：輸出成個人品牌素材

文章結構：
1. 什麼是 MCP，為什麼重要
2. Python 生態在幹嘛，Java 的空缺在哪
3. 我做了什麼（附 GitHub）
4. Demo：用這個 server 查台灣地緣政治新聞

發布管道：Medium Taiwan、PTT、LinkedIn

---

## 專案結構（Week 1）

```
mcp-server-java/
├── src/main/java/
│   └── com/jimmy/mcp/
│       ├── McpServerApplication.java   ← Spring Boot 主類別
│       ├── server/
│       │   ├── McpServer.java          ← stdio 讀寫迴圈
│       │   ├── RequestHandler.java     ← JSON-RPC dispatch
│       │   └── ToolRegistry.java       ← Tool 管理
│       └── tools/
│           ├── McpTool.java            ← interface（最關鍵）
│           ├── EchoTool.java           ← Week 1 實作
│           └── GetTimeTool.java        ← Week 1 實作
└── pom.xml
```

---

## 核心 Interface 設計

```java
public interface McpTool {
    String getName();           // "echo", "get_time"
    String getDescription();    // AI 看到的說明
    JsonNode getInputSchema();  // 參數定義（JSON Schema）
    String execute(JsonNode args); // 實際執行邏輯
}
```

所有 Tool 實作這個 interface → ToolRegistry 統一管理 → 新增 Tool 只要新增一個 class

---

## 技術選型

| 項目 | 選擇 | 理由 |
|---|---|---|
| 框架 | Spring Boot 3.2+ | 熟悉，有 Virtual Thread 支援 |
| Transport | stdio（Week 1）→ SSE（Week 2+） | stdio 最簡單，快速驗證 |
| 訊息格式 | JSON-RPC 2.0 | MCP 標準協議 |
| JSON 處理 | Jackson（Spring 內建） | 不需額外學習 |
| 參考 | modelcontextprotocol/java-sdk | 官方初步 Java SDK |

---

## MCP 訊息流程

```
Claude Desktop → {"method":"tools/list"}            → MCP Server
Claude Desktop ← {"tools":[{"name":"echo",...}]}    ← MCP Server
Claude Desktop → {"method":"tools/call","params":{"name":"echo","arguments":{"text":"hello"}}} → MCP Server
Claude Desktop ← {"content":[{"type":"text","text":"hello"}]}  ← EchoTool
```

Week 1 只需處理三個 method：`initialize`、`tools/list`、`tools/call`

---

## 下一步（進 Cowork 後繼續）

優先任務：把 `McpServer.java` 和 `EchoTool.java` 的完整程式碼寫出來並跑起來。
