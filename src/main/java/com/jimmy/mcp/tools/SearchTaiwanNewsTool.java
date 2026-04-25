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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Searches Taiwan news by keyword using CNA (中央社) RSS feed.
 * Fetches the general news feed and filters items whose title or description
 * contains the keyword (case-insensitive).
 */
@Component
public class SearchTaiwanNewsTool implements McpTool {

    private static final String LTN_RSS_URL = "https://news.ltn.com.tw/rss/all.xml";
    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 20;

    private final String rssUrl;

    public SearchTaiwanNewsTool() {
        this(LTN_RSS_URL);
    }

    SearchTaiwanNewsTool(String rssUrl) {
        this.rssUrl = rssUrl;
    }

    @Override
    public String getName() {
        return "search_taiwan_news";
    }

    @Override
    public String getDescription() {
        return "搜尋台灣即時新聞（自由時報 RSS）。輸入關鍵字，返回符合的新聞標題、連結與摘要。";
    }

    @Override
    public JsonNode getInputSchema() {
        try {
            return new ObjectMapper().readTree("""
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
            throw new IllegalStateException("Failed to build SearchTaiwanNewsTool schema", e);
        }
    }

    @Override
    public String execute(JsonNode args) {
        String keyword = args.path("keyword").asText("").trim();
        if (keyword.isEmpty()) {
            return "請提供搜尋關鍵字";
        }

        int limit = args.path("limit").asInt(DEFAULT_LIMIT);
        limit = Math.max(1, Math.min(limit, MAX_LIMIT));

        try {
            String xml = fetchXml(rssUrl);
            List<String[]> items = parseMatchingItems(xml, keyword, limit);

            if (items.isEmpty()) {
                return "找不到包含「" + keyword + "」的新聞";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("找到 ").append(items.size()).append(" 則「").append(keyword).append("」相關新聞：\n\n");
            for (int i = 0; i < items.size(); i++) {
                String[] item = items.get(i);
                sb.append(i + 1).append(". ").append(item[0]).append("\n");
                sb.append("   ").append(item[1]).append("\n");
                if (!item[2].isEmpty()) {
                    sb.append("   ").append(item[2]).append("\n");
                }
                sb.append("   發布時間：").append(item[3]).append("\n\n");
            }
            return sb.toString().trim();

        } catch (Exception e) {
            return "取得新聞失敗：" + e.getMessage();
        }
    }

    protected String fetchXml(String url) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "mcp-server-java/0.1.0")
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
    }

    private static final Pattern ITEM_PATTERN =
            Pattern.compile("(?s)<item[^>]*>(.*?)</item>");
    private static final Pattern FIELD_PATTERN =
            Pattern.compile("(?s)<(title|link|description|pubDate)[^>]*>(?:<!\\[CDATA\\[)?(.*?)(?:]]>)?</\\1>");

    /**
     * Extracts RSS items using regex instead of a DOM parser so that malformed HTML
     * inside {@code <description>} fields (e.g. unclosed {@code <br>} tags, HTML
     * entities like {@code &nbsp;}) never causes a parse error.
     */
    List<String[]> parseMatchingItems(String xml, String keyword, int limit) {
        String lowerKeyword = keyword.toLowerCase();
        List<String[]> results = new ArrayList<>();

        Matcher items = ITEM_PATTERN.matcher(xml);
        while (items.find() && results.size() < limit) {
            String itemBody = items.group(1);

            String title   = extractField(itemBody, "title");
            String link    = extractField(itemBody, "link");
            String desc    = extractField(itemBody, "description");
            String pubDate = extractField(itemBody, "pubDate");

            // Strip any remaining HTML tags and decode common entities
            String cleanTitle = stripHtml(title);
            String cleanDesc  = stripHtml(desc);

            if (cleanTitle.toLowerCase().contains(lowerKeyword)
                    || cleanDesc.toLowerCase().contains(lowerKeyword)) {
                if (cleanDesc.length() > 120) {
                    cleanDesc = cleanDesc.substring(0, 120) + "…";
                }
                results.add(new String[]{cleanTitle, link.trim(), cleanDesc, pubDate.trim()});
            }
        }
        return results;
    }

    private static String extractField(String itemBody, String tag) {
        Matcher m = FIELD_PATTERN.matcher(itemBody);
        while (m.find()) {
            if (m.group(1).equals(tag)) {
                return m.group(2).trim();
            }
        }
        return "";
    }

    private static String stripHtml(String html) {
        return html.replaceAll("<[^>]*>", "")
                   .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                   .replace("&nbsp;", " ").replace("&quot;", "\"").replace("&apos;", "'")
                   .trim();
    }
}
