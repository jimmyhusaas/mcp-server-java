# 我用 Java 做了一個 MCP Server，然後讓 AI 幫我搜尋台灣新聞

*寫給台灣的 Java 後端工程師*

---

## 先說結論

如果你是 Java 開發者，還沒聽過 MCP（Model Context Protocol），你現在落後的不是幾個月，是整個賽道。

更好的消息是：這個賽道 Java 幾乎沒有人在跑。

這篇文章記錄我花了三週，從零實作一個 Java MCP Server，並包裝成 Spring Boot Starter 的過程。我不是來秀技術的，我想傳達的是：**現在入場的人能定義遊戲規則**。

---

## MCP 是什麼，為什麼重要

想像你有一個 AI 助理（Claude、GPT，隨便一個），它很聰明，但被鎖在一個玻璃箱裡——它能思考，但碰不到外面的資料和系統。

MCP（Model Context Protocol）就是那扇門。

它是 Anthropic 在 2024 年底提出的開放協議，定義了 AI 模型如何安全地呼叫外部工具。你可以把它理解成 AI 世界的「REST API 標準」——只要你的系統實作了這個協議，任何支援 MCP 的 AI（Claude Desktop、Cursor、Zed...）都能直接呼叫你的工具。

舉個例子。當 Claude 回答「幫我查一下最近台灣的科技新聞」時，它現在能透過 MCP 呼叫你的 Server，拿到真實資料，再給你有根據的回答。不再是幻覺，是真實資訊。

---

## Python 在幹嘛，Java 的空缺在哪

MCP 推出以後，Python 生態系動得很快：

- LangChain 加了 MCP 支援
- CrewAI、AutoGen 在跟進
- Anthropic 官方也有 Python SDK

Java 呢？

Spring AI 有一個非常初期的實作，功能不完整。LangChain4j 社群還小。大部分 Java 企業還在問「我們能連 AI 嗎」，而不是「我們的 AI 工具怎麼設計」。

**這就是空缺。**

Java 佔據全球 20–30% 的企業後端，金融、保險、電信、製造業幾乎全是 Java。這些企業有大量的內部系統、資料庫、API，等著被 AI 呼叫。他們需要的正是一個 Java 原生的 MCP 解決方案。

---

## 我做了什麼

三週，三個里程碑：

**Week 1：核心協議**
從零實作 JSON-RPC 2.0 over stdio，讓 Claude Desktop 能和 Java Server 溝通。設計了 `McpTool` 介面，讓新增工具只需要一個 class。

**Week 2：接真實資料**
實作 `search_taiwan_news` 工具，串接自由時報 RSS，讓 AI 能查詢真實新聞。處理了 RSS feed 的各種「現實問題」（不合法的 XML entity、裸 `<br>` 標籤），用 regex 繞過不守規矩的格式。

**Week 3：包成 Starter**
把 MCP 基礎架構抽出來，做成 `mcp-spring-boot-starter`。現在其他開發者只需要加一個 Maven dependency，就能擁有完整的 MCP Server。

---

## 核心設計：McpTool 介面

整個架構最關鍵的是這個介面：

```java
public interface McpTool {
    String getName();              // AI 用來呼叫工具的名稱
    String getDescription();       // AI 讀到的工具說明（影響 AI 怎麼使用它）
    JsonNode getInputSchema();     // 定義參數格式（JSON Schema）
    String execute(JsonNode args); // 實際執行邏輯
}
```

實作一個工具長這樣：

```java
@Component
public class SearchTaiwanNewsTool implements McpTool {

    @Override
    public String getName() { return "search_taiwan_news"; }

    @Override
    public String getDescription() {
        return "搜尋台灣即時新聞。輸入關鍵字，返回符合的新聞標題、連結與摘要。";
    }

    @Override
    public JsonNode getInputSchema() {
        // JSON Schema 定義 keyword 和 limit 兩個參數
        // ...
    }

    @Override
    public String execute(JsonNode args) {
        String keyword = args.path("keyword").asText();
        // 抓自由時報 RSS → 過濾關鍵字 → 回傳結果
        // ...
    }
}
```

加上 `@Component`，Spring 自動發現這個工具，Auto-Configuration 把它注入 ToolRegistry，MCP Server 就能列出並呼叫它。**新增工具不需要改任何其他程式碼。**

---

## 用 Starter：5 分鐘建一個 MCP Server

**Step 1：加 dependency**

```xml
<dependency>
    <groupId>com.jimmy</groupId>
    <artifactId>mcp-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

**Step 2：設定 application.properties**

```properties
spring.main.web-application-type=none
spring.main.banner-mode=off
mcp.server.name=my-company-mcp
mcp.server.version=1.0.0
```

**Step 3：主類別**

```java
@SpringBootApplication
public class MyMcpServer {
    public static void main(String[] args) {
        SpringApplication.run(MyMcpServer.class, args);
    }
}
```

**Step 4：實作工具，加 `@Component`，完成。**

Starter 的 Auto-Configuration 自動處理：

- JSON-RPC 2.0 協議解析
- `tools/list` 回傳工具清單
- `tools/call` 分發到對應工具
- `initialize` 握手
- stderr/stdout 分離（MCP 的硬性要求）

---

## 現實碰到的坑

做這個最有趣的部分不是實作協議，而是接真實資料的時候。

**CNA RSS 已 404**：原本想用中央社，結果 RSS endpoint 掛了。換到自由時報，正常。

**RSS 裡的 HTML**：很多 RSS 的 `<description>` 欄位直接塞 HTML，像是 `<br>`、`<p>` 這些未閉合標籤，XML parser 根本吃不下去。解法是放棄 DOM parser，改用 regex 直接萃取 `<item>` 內容，對格式不守規矩的 feed 天然免疫。

**XML entity 地雷**：`&nbsp;`、`&laquo;` 這些 HTML entity 在 XML 裡是非法的。加了一個 sanitizer，把非標準 entity 全部替換成 `&amp;`，再進行解析。

這些都是「教科書學不到，只有真的接外部資料才會踩到」的問題。

---

## 測試策略：不起 Spring Context，速度快

52 個 JUnit 5 unit tests，全部不啟動 Spring，平均 1.5 秒跑完。

對 MCP Server 來說，關鍵是兩件事：

1. **JSON-RPC 協議對不對**：`RequestHandlerTest` 直接 new 出 handler，傳 JSON 字串，驗回應格式
2. **工具邏輯對不對**：工具 class 的 `execute()` 純函式，直接呼叫就好

`SearchTaiwanNewsTool` 的測試用 anonymous class 覆寫 `fetchXml()`，回傳 fixture XML，完全不發 HTTP request：

```java
SearchTaiwanNewsTool tool = new SearchTaiwanNewsTool("unused") {
    @Override
    protected String fetchXml(String url) throws Exception {
        return FIXTURE_XML;  // 測試用假資料
    }
};

assertThat(tool.execute(json("{\"keyword\": \"科技\"}")))
    .contains("科技業創新突破");
```

---

## Demo：問 Claude 台灣新聞

把這個 Server 接上 Claude Desktop 之後，對話長這樣：

> **我**：最近有沒有台北相關的新聞？
>
> **Claude**：讓我幫你查一下。
> *（呼叫 `search_taiwan_news`，keyword="台北"）*
>
> **Claude**：根據最新消息，台北今天發生了一起銀行搶案，31 歲男子攜帶假手榴彈闖入中信銀行，警方 15 分鐘內壓制逮捕。另外，台北市警局督察組女警官因生產大量失血不治，家屬懷疑有延誤送醫情形…

這不是 AI 的幻覺，是真實從自由時報 RSS 拿到的資料，時間戳顯示是幾小時前的新聞。

---

## 接下來想做的

- **`@McpTool` annotation**：讓工具定義更簡潔，自動從 annotation 生成 JSON Schema
- **SSE Transport**：除了 stdio，支援 HTTP/SSE 讓 Server 可以部署到雲端
- **更多工具範例**：資料庫查詢、企業內部 API 整合

---

## GitHub

**[github.com/jimmyhusaas/mcp-server-java](https://github.com/jimmyhusaas/mcp-server-java)**

```
├── mcp-spring-boot-starter/   ← 核心 Library（可作為 dependency）
└── mcp-server-java/           ← 範例 App（示範三個工具）
```

Star 或 fork 如果覺得有用。有問題開 issue，或直接留言。

---

*Written by Jimmy — Java 後端工程師，5 年經驗。如果你也在做 Java + AI Agent 整合相關的東西，歡迎一起討論。*
