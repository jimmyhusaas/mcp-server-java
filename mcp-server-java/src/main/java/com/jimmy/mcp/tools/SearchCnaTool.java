package com.jimmy.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Searches 中央社 (CNA) real-time news via their internal JSON feed.
 *
 * <p>Uses CNA's internal feed API which returns structured JSON directly,
 * avoiding the need to scrape individual article pages for JSON-LD metadata.
 * Requires a non-datacenter IP — works when the JAR runs locally via Claude Desktop.
 */
@Component
public class SearchCnaTool implements McpTool {

    static final String API_URL =
            "https://www.cna.com.tw/cna2018api/api/WNewsFeed?action=0&category=aall&cnt=30";
    private static final String BASE_URL = "https://www.cna.com.tw";
    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 20;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String getName() { return "search_cna_news"; }

    @Override
    public String getDescription() {
        return "搜尋中央社即時新聞。輸入關鍵字，返回符合的新聞標題、連結與摘要。";
    }

    @Override
    public JsonNode getInputSchema() {
        try {
            return MAPPER.readTree("""
                    {
                      "type": "object",
                      "properties": {
                        "keyword": {
                          "type": "string",
                          "description": "搜尋關鍵字（比對標題與摘要）"
                        },
                        "limit": {
                          "type": "integer",
                          "description": "最多返回幾筆結果（預設 5，最多 20）",
                          "default": 5
                        }
                      },
                      "required": ["keyword"]
                    }
                    """);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build SearchCnaTool schema", e);
        }
    }

    @Override
    public String execute(JsonNode args) {
        String keyword = args.path("keyword").asText("").trim();
        if (keyword.isEmpty()) return "請提供搜尋關鍵字";

        int limit = Math.max(1, Math.min(args.path("limit").asInt(DEFAULT_LIMIT), MAX_LIMIT));

        String json;
        try {
            json = fetchJson(API_URL);
        } catch (Exception e) {
            return "無法連線至中央社：" + e.getMessage();
        }

        List<String[]> results;
        try {
            results = parseItems(json, keyword, limit);
        } catch (Exception e) {
            return "解析中央社回應失敗：" + e.getMessage();
        }

        if (results.isEmpty()) return "找不到包含「" + keyword + "」的中央社新聞";

        StringBuilder sb = new StringBuilder();
        sb.append("找到 ").append(results.size()).append(" 則「").append(keyword).append("」相關中央社新聞：\n\n");
        for (int i = 0; i < results.size(); i++) {
            String[] item = results.get(i);
            sb.append(i + 1).append(". ").append(item[0]).append("\n")
              .append("   ").append(item[1]).append("\n");
            if (!item[2].isEmpty()) sb.append("   ").append(item[2]).append("\n");
            sb.append("   發布時間：").append(item[3]).append("\n\n");
        }
        return sb.toString().trim();
    }

    protected String fetchJson(String url) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "mcp-server-java/0.1.0")
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP " + response.statusCode()
                    + "（中央社封鎖了此 IP，請確認是否從本機執行）");
        }
        return response.body();
    }

    List<String[]> parseItems(String json, String keyword, int limit) throws Exception {
        JsonNode root = MAPPER.readTree(json);
        JsonNode items = findItemsArray(root);
        if (items == null) {
            throw new RuntimeException("無法從回應中找到新聞列表，請回報以下內容供除錯："
                    + json.substring(0, Math.min(300, json.length())));
        }

        String lower = keyword.toLowerCase();
        List<String[]> results = new ArrayList<>();

        for (JsonNode item : items) {
            if (results.size() >= limit) break;
            String title = field(item, "HeadLine", "Title", "title", "headline");
            String url   = resolveUrl(field(item, "PageUrl", "Url", "url", "link"));
            String desc  = field(item, "Summary", "Introduction", "summary", "description");
            String date  = field(item, "CreateTime", "PublishTime", "pubDate", "date");
            if (desc.length() > 120) desc = desc.substring(0, 120) + "…";

            if (title.toLowerCase().contains(lower) || desc.toLowerCase().contains(lower)) {
                results.add(new String[]{title, url, desc, date});
            }
        }
        return results;
    }

    // Tries common CNA API response shapes to locate the news array.
    private static JsonNode findItemsArray(JsonNode root) {
        String[][] paths = {
            {"ResultData", "Items"},
            {"data", "list"},
            {"data", "items"},
            {"items"},
            {"list"},
            {"news"},
        };
        for (String[] path : paths) {
            JsonNode node = root;
            for (String key : path) node = node.path(key);
            if (node.isArray() && node.size() > 0) return node;
        }
        // Last resort: first non-empty array anywhere one level deep
        for (JsonNode child : root) {
            if (child.isArray() && child.size() > 0) return child;
            if (child.isObject()) {
                for (JsonNode grandchild : child) {
                    if (grandchild.isArray() && grandchild.size() > 0) return grandchild;
                }
            }
        }
        return null;
    }

    private String resolveUrl(String url) {
        if (url.isEmpty() || url.startsWith("http")) return url;
        return BASE_URL + (url.startsWith("/") ? url : "/" + url);
    }

    private static String field(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode v = node.path(key);
            if (!v.isMissingNode() && !v.isNull() && !v.asText().isBlank()) return v.asText().trim();
        }
        return "";
    }
}
