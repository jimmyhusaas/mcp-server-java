import { useState } from "react";

const layers = [
  {
    id: "client",
    label: "AI Client Layer",
    color: "#7C3AED",
    bg: "#1e1133",
    items: [
      { name: "Claude Desktop", icon: "🤖" },
      { name: "Cursor", icon: "⌨️" },
      { name: "GitHub Copilot", icon: "🐙" },
    ],
    note: "任何支援 MCP 的 AI 工具",
  },
  {
    id: "protocol",
    label: "MCP Protocol",
    color: "#06B6D4",
    bg: "#0c1f2e",
    items: [
      { name: "stdio transport", icon: "📡", sub: "Week 1 先用這個" },
      { name: "JSON-RPC 2.0", icon: "🔄", sub: "訊息格式" },
    ],
    note: "Week 1 用 stdio，Week 2 再升 SSE/HTTP",
  },
  {
    id: "server",
    label: "Your MCP Server（Spring Boot）",
    color: "#10B981",
    bg: "#0a1f18",
    items: [
      { name: "McpServer.java", icon: "🖥️", sub: "主進入點，處理 stdio" },
      { name: "ToolRegistry.java", icon: "📋", sub: "管理所有 Tools 的清單" },
      { name: "RequestHandler.java", icon: "⚙️", sub: "解析 JSON-RPC，dispatch 到 Tool" },
    ],
    note: "核心 3 個 class，Week 1 全部手寫，不依賴外部 SDK",
  },
  {
    id: "tools",
    label: "Tools Layer",
    color: "#F59E0B",
    bg: "#1f1708",
    items: [
      { name: "EchoTool.java", icon: "🔊", sub: "Week 1：輸入什麼回什麼" },
      { name: "GetTimeTool.java", icon: "🕐", sub: "Week 1：回傳當前時間" },
      { name: "NewsSearchTool.java", icon: "📰", sub: "Week 2：台灣新聞查詢（預留）" },
    ],
    note: "每個 Tool 實作同一個 interface，易擴充",
  },
];

const messageFlow = [
  { from: "Claude Desktop", msg: '{"method":"tools/list"}', dir: "→" },
  { from: "RequestHandler", msg: '{"tools":[{"name":"echo",...}]}', dir: "←" },
  { from: "Claude Desktop", msg: '{"method":"tools/call","params":{"name":"echo","arguments":{"text":"hello"}}}', dir: "→" },
  { from: "EchoTool", msg: '{"content":[{"type":"text","text":"hello"}]}', dir: "←" },
];

const fileStructure = `mcp-server-java/
├── src/main/java/
│   └── com/jimmy/mcp/
│       ├── McpServerApplication.java   ← Spring Boot 主類別
│       ├── server/
│       │   ├── McpServer.java          ← stdio 讀寫迴圈
│       │   ├── RequestHandler.java     ← JSON-RPC dispatch
│       │   └── ToolRegistry.java       ← Tool 管理
│       └── tools/
│           ├── McpTool.java            ← interface
│           ├── EchoTool.java           ← Week 1 實作
│           └── GetTimeTool.java        ← Week 1 實作
├── src/test/java/
│   └── com/jimmy/mcp/
│       └── tools/
│           └── EchoToolTest.java
└── pom.xml`;

export default function App() {
  const [activeLayer, setActiveLayer] = useState(null);
  const [tab, setTab] = useState("arch");

  return (
    <div style={{
      background: "#0d0d14",
      minHeight: "100vh",
      fontFamily: "'JetBrains Mono', 'Fira Code', monospace",
      color: "#e2e8f0",
      padding: "24px",
    }}>
      <div style={{ maxWidth: 820, margin: "0 auto" }}>

        {/* Header */}
        <div style={{ marginBottom: 28 }}>
          <div style={{ fontSize: 11, color: "#7C3AED", letterSpacing: 3, marginBottom: 6 }}>
            WEEK 1 ／ MCP SERVER
          </div>
          <h1 style={{ fontSize: 22, fontWeight: 700, margin: 0, color: "#f1f5f9" }}>
            Java Spring Boot × MCP 架構圖
          </h1>
          <p style={{ fontSize: 12, color: "#64748b", marginTop: 6 }}>
            目標：跑起來一個可以被 Claude Desktop 呼叫的本地 server
          </p>
        </div>

        {/* Tabs */}
        <div style={{ display: "flex", gap: 2, marginBottom: 20 }}>
          {[["arch", "架構總覽"], ["flow", "訊息流程"], ["files", "專案結構"]].map(([id, label]) => (
            <button key={id} onClick={() => setTab(id)} style={{
              padding: "7px 16px",
              fontSize: 12,
              border: "1px solid",
              borderColor: tab === id ? "#7C3AED" : "#1e293b",
              background: tab === id ? "#1e1133" : "transparent",
              color: tab === id ? "#a78bfa" : "#64748b",
              cursor: "pointer",
              borderRadius: 4,
            }}>
              {label}
            </button>
          ))}
        </div>

        {/* Tab: Architecture */}
        {tab === "arch" && (
          <div>
            {layers.map((layer, i) => (
              <div key={layer.id}>
                {/* Layer box */}
                <div
                  onClick={() => setActiveLayer(activeLayer === layer.id ? null : layer.id)}
                  style={{
                    background: layer.bg,
                    border: `1px solid ${activeLayer === layer.id ? layer.color : "#1e293b"}`,
                    borderRadius: 8,
                    padding: "14px 18px",
                    cursor: "pointer",
                    transition: "border-color 0.2s",
                  }}
                >
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                    <span style={{ fontSize: 11, color: layer.color, fontWeight: 700, letterSpacing: 1.5 }}>
                      {layer.label.toUpperCase()}
                    </span>
                    <span style={{ fontSize: 10, color: "#475569" }}>{activeLayer === layer.id ? "▲ 收起" : "▼ 展開"}</span>
                  </div>

                  <div style={{ display: "flex", gap: 8, marginTop: 10, flexWrap: "wrap" }}>
                    {layer.items.map(item => (
                      <div key={item.name} style={{
                        background: "#0d0d14",
                        border: `1px solid ${layer.color}33`,
                        borderRadius: 6,
                        padding: "8px 12px",
                        fontSize: 12,
                        flex: "1 1 180px",
                      }}>
                        <div>{item.icon} {item.name}</div>
                        {item.sub && <div style={{ fontSize: 10, color: "#64748b", marginTop: 3 }}>{item.sub}</div>}
                      </div>
                    ))}
                  </div>

                  {activeLayer === layer.id && (
                    <div style={{
                      marginTop: 10,
                      padding: "8px 12px",
                      background: `${layer.color}11`,
                      borderRadius: 5,
                      fontSize: 11,
                      color: "#94a3b8",
                      borderLeft: `2px solid ${layer.color}`,
                    }}>
                      💡 {layer.note}
                    </div>
                  )}
                </div>

                {/* Arrow between layers */}
                {i < layers.length - 1 && (
                  <div style={{ textAlign: "center", padding: "4px 0", color: "#334155", fontSize: 12 }}>
                    ↕ JSON-RPC over stdio
                  </div>
                )}
              </div>
            ))}

            {/* Key insight */}
            <div style={{
              marginTop: 20,
              padding: "12px 16px",
              background: "#0f172a",
              border: "1px solid #1e293b",
              borderRadius: 8,
              fontSize: 11,
              color: "#64748b",
            }}>
              <span style={{ color: "#10B981" }}>Week 1 核心任務</span>：實作 <span style={{ color: "#e2e8f0" }}>McpServer.java</span> 的 stdio 迴圈，
              讓它能夠讀取 AI 發來的 JSON-RPC，回傳 tools/list，並執行 EchoTool。
              整個 server 就是一個 <span style={{ color: "#e2e8f0" }}>讀 stdin → 解析 → 執行 Tool → 寫 stdout</span> 的迴圈。
            </div>
          </div>
        )}

        {/* Tab: Message Flow */}
        {tab === "flow" && (
          <div>
            <div style={{ marginBottom: 14, fontSize: 11, color: "#64748b" }}>
              一次完整的「AI 呼叫 Tool」互動流程
            </div>
            {messageFlow.map((m, i) => (
              <div key={i} style={{
                display: "flex",
                gap: 10,
                marginBottom: 12,
                alignItems: "flex-start",
              }}>
                <div style={{
                  fontSize: 10,
                  color: "#475569",
                  minWidth: 20,
                  paddingTop: 3,
                }}>
                  {i + 1}
                </div>
                <div style={{
                  flex: 1,
                  background: m.dir === "→" ? "#0c1f2e" : "#0a1f18",
                  border: `1px solid ${m.dir === "→" ? "#06B6D433" : "#10B98133"}`,
                  borderRadius: 6,
                  padding: "10px 14px",
                }}>
                  <div style={{ fontSize: 10, color: m.dir === "→" ? "#06B6D4" : "#10B981", marginBottom: 5 }}>
                    {m.dir === "→" ? `Claude → Server` : `Server → Claude`}
                    <span style={{ color: "#475569", marginLeft: 8 }}>（via stdin/stdout）</span>
                  </div>
                  <div style={{
                    fontSize: 11,
                    fontFamily: "monospace",
                    color: "#cbd5e1",
                    wordBreak: "break-all",
                  }}>
                    {m.msg}
                  </div>
                </div>
              </div>
            ))}

            <div style={{
              padding: "12px 16px",
              background: "#0f172a",
              border: "1px solid #1e293b",
              borderRadius: 8,
              fontSize: 11,
              color: "#64748b",
              marginTop: 4,
            }}>
              <span style={{ color: "#F59E0B" }}>重點</span>：MCP 本質上就是一個固定格式的 JSON-RPC 協議。
              你的 server 只需要正確處理 <span style={{ color: "#e2e8f0" }}>initialize</span>、
              <span style={{ color: "#e2e8f0" }}>tools/list</span>、
              <span style={{ color: "#e2e8f0" }}>tools/call</span> 這三個 method，Week 1 就完成了。
            </div>
          </div>
        )}

        {/* Tab: File Structure */}
        {tab === "files" && (
          <div>
            <div style={{ marginBottom: 14, fontSize: 11, color: "#64748b" }}>
              建議的 Maven 專案結構
            </div>
            <div style={{
              background: "#0a0a10",
              border: "1px solid #1e293b",
              borderRadius: 8,
              padding: "16px 20px",
              fontSize: 12,
              lineHeight: 1.9,
              whiteSpace: "pre",
              overflowX: "auto",
              color: "#94a3b8",
            }}>
              {fileStructure.split('\n').map((line, i) => {
                let color = "#94a3b8";
                if (line.includes("←")) color = "#64748b";
                if (line.includes("McpServer")) color = "#10B981";
                if (line.includes("Tool")) color = "#F59E0B";
                if (line.includes("interface")) color = "#7C3AED";
                if (line.includes("Application")) color = "#06B6D4";
                return <div key={i} style={{ color }}>{line}</div>;
              })}
            </div>

            {/* Key interface */}
            <div style={{ marginTop: 16 }}>
              <div style={{ fontSize: 11, color: "#7C3AED", marginBottom: 8, letterSpacing: 1 }}>
                MCTOOL INTERFACE（最關鍵的設計）
              </div>
              <div style={{
                background: "#0a0a10",
                border: "1px solid #7C3AED33",
                borderRadius: 8,
                padding: "14px 18px",
                fontSize: 12,
                lineHeight: 1.8,
                fontFamily: "monospace",
              }}>
                <div style={{ color: "#7C3AED" }}>public interface <span style={{ color: "#a78bfa" }}>McpTool</span> {"{"}</div>
                <div style={{ paddingLeft: 20 }}>
                  <div><span style={{ color: "#06B6D4" }}>String</span> <span style={{ color: "#10B981" }}>getName</span>();{" "}<span style={{ color: "#475569" }}>// "echo", "get_time"</span></div>
                  <div><span style={{ color: "#06B6D4" }}>String</span> <span style={{ color: "#10B981" }}>getDescription</span>();{" "}<span style={{ color: "#475569" }}>// AI 看到的說明</span></div>
                  <div><span style={{ color: "#06B6D4" }}>JsonNode</span> <span style={{ color: "#10B981" }}>getInputSchema</span>();{" "}<span style={{ color: "#475569" }}>// 參數定義</span></div>
                  <div><span style={{ color: "#06B6D4" }}>String</span> <span style={{ color: "#10B981" }}>execute</span>(<span style={{ color: "#06B6D4" }}>JsonNode</span> args);{" "}<span style={{ color: "#475569" }}>// 實際執行</span></div>
                </div>
                <div style={{ color: "#7C3AED" }}>{"}"}</div>
              </div>
              <div style={{ fontSize: 11, color: "#475569", marginTop: 8 }}>
                所有 Tool 都實作這個 interface → ToolRegistry 統一管理 → 之後加新 Tool 只要新增一個 class
              </div>
            </div>
          </div>
        )}

        {/* Footer */}
        <div style={{
          marginTop: 24,
          padding: "10px 14px",
          borderTop: "1px solid #1e293b",
          display: "flex",
          justifyContent: "space-between",
          fontSize: 10,
          color: "#334155",
        }}>
          <span>Week 1 完成條件：Claude Desktop 成功呼叫 EchoTool</span>
          <span>預估時間：5–8 hr</span>
        </div>
      </div>
    </div>
  );
}
