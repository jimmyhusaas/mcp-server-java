package com.jimmy.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Searches Taiwan news by keyword using one or more configurable RSS feeds.
 *
 * <p>Configure sources in {@code application.properties}:
 * <pre>
 * mcp.tools.news.rss-urls[0]=https://news.ltn.com.tw/rss/all.xml
 * mcp.tools.news.rss-urls[1]=https://news.pts.org.tw/rss.xml
 * </pre>
 *
 * <p>Results from all sources are aggregated and deduplicated by title.
 */
@Component
public class SearchTaiwanNewsTool implements McpTool {

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 20;

    private final List<String> rssUrls;

    /** Spring constructor — injects configured RSS URLs. */
    @Autowired
    public SearchTaiwanNewsTool(NewsToolProperties props) {
        this(props.getRssUrls());
    }

    /** Package-private constructor for unit tests. */
    SearchTaiwanNewsTool(List<String> rssUrls) {
        this.rssUrls = rssUrls;
    }

    @Override
    public String getName() {
        return "search_taiwan_news";
    }

    @Override
    public String getDescription() {
        return "搜尋台灣即時新聞。輸入關鍵字，返回符合的新聞標題、連結與摘要。";
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

        List<String[]> results = new ArrayList<>();
        Set<String> seenTitles = new LinkedHashSet<>();
        List<String> errors = new ArrayList<>();

        for (String url : rssUrls) {
            try {
                String xml = fetchXml(url);
                List<String[]> items = parseMatchingItems(xml, keyword, limit - results.size());
                for (String[] item : items) {
                    // deduplicate by title across sources
                    if (seenTitles.add(item[0])) {
                        results.add(item);
                    }
                }
            } catch (Exception e) {
                errors.add(url + "：" + e.getMessage());
            }
            if (results.size() >= limit) break;
        }

        if (results.isEmpty()) {
            String base = "找不到包含「" + keyword + "」的新聞";
            return errors.isEmpty() ? base : base + "\n（部分來源失敗：" + String.join("、", errors) + "）";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("找到 ").append(results.size()).append(" 則「").append(keyword).append("」相關新聞：\n\n");
        for (int i = 0; i < results.size(); i++) {
            String[] item = results.get(i);
            sb.append(i + 1).append(". ").append(item[0]).append("\n");
            sb.append("   ").append(item[1]).append("\n");
            if (!item[2].isEmpty()) {
                sb.append("   ").append(item[2]).append("\n");
            }
            sb.append("   發布時間：").append(item[3]).append("\n\n");
        }
        if (!errors.isEmpty()) {
            sb.append("（部分來源失敗：").append(String.join("、", errors)).append("）");
        }
        return sb.toString().trim();
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

    // RSS 2.0: <item>...</item>
    private static final Pattern ITEM_PATTERN =
            Pattern.compile("(?s)<item[^>]*>(.*?)</item>");
    // Atom: <entry>...</entry>
    private static final Pattern ENTRY_PATTERN =
            Pattern.compile("(?s)<entry[^>]*>(.*?)</entry>");
    // RSS fields: title, link (text content), description, pubDate
    private static final Pattern FIELD_PATTERN =
            Pattern.compile("(?s)<(title|link|description|pubDate|summary|updated)[^>]*>(?:<!\\[CDATA\\[)?(.*?)(?:]]>)?</\\1>");
    // Atom link: <link rel="alternate" href="URL"/>
    private static final Pattern ATOM_LINK_PATTERN =
            Pattern.compile("<link[^>]+href=\"([^\"]+)\"[^>]*/>");

    /**
     * Extracts news items from either RSS 2.0 ({@code <item>}) or Atom ({@code <entry>})
     * feeds using regex, so malformed HTML inside description fields never causes errors.
     */
    List<String[]> parseMatchingItems(String xml, String keyword, int limit) {
        String lowerKeyword = keyword.toLowerCase();
        List<String[]> results = new ArrayList<>();

        // Detect format: prefer RSS items, fall back to Atom entries
        Pattern entryPattern = xml.contains("<item") ? ITEM_PATTERN : ENTRY_PATTERN;
        boolean isAtom = entryPattern == ENTRY_PATTERN;

        Matcher items = entryPattern.matcher(xml);
        while (items.find() && results.size() < limit) {
            String itemBody = items.group(1);

            String title   = extractField(itemBody, "title");
            // Atom uses <link href="..."/> self-closing; RSS uses <link>URL</link>
            String link    = isAtom ? extractAtomLink(itemBody) : extractField(itemBody, "link");
            // Atom uses <summary>; RSS uses <description>
            String desc    = isAtom ? extractField(itemBody, "summary")
                                    : extractField(itemBody, "description");
            // Atom uses <updated>; RSS uses <pubDate>
            String pubDate = isAtom ? extractField(itemBody, "updated")
                                    : extractField(itemBody, "pubDate");

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

    private static String extractAtomLink(String entryBody) {
        Matcher m = ATOM_LINK_PATTERN.matcher(entryBody);
        return m.find() ? m.group(1) : "";
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
