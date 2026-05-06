package com.jimmy.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchTaiwanNewsToolTest {

    private static final String FIXTURE_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>中央社即時新聞</title>
                <item>
                  <title>台灣科技業創新突破 半導體產能再創新高</title>
                  <link>https://www.cna.com.tw/news/ait/1.aspx</link>
                  <description>台灣半導體廠商宣布新世代製程量產，市佔持續領先</description>
                  <pubDate>Thu, 24 Apr 2026 10:00:00 +0800</pubDate>
                </item>
                <item>
                  <title>台股今日小幅上漲 科技股領漲</title>
                  <link>https://www.cna.com.tw/news/afe/2.aspx</link>
                  <description>加權指數上漲 0.3%，科技族群表現亮眼</description>
                  <pubDate>Thu, 24 Apr 2026 09:30:00 +0800</pubDate>
                </item>
                <item>
                  <title>明日各地天氣晴朗 午後山區防雷雨</title>
                  <link>https://www.cna.com.tw/news/ahel/3.aspx</link>
                  <description>氣象局預報明日各地天氣晴朗，午後山區有雷陣雨機率</description>
                  <pubDate>Thu, 24 Apr 2026 08:00:00 +0800</pubDate>
                </item>
              </channel>
            </rss>
            """;

    private ObjectMapper mapper;
    private SearchTaiwanNewsTool tool;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        // Override fetchXml to avoid real HTTP calls in unit tests
        tool = new SearchTaiwanNewsTool(List.of("http://unused-in-tests")) {
            @Override
            protected String fetchXml(String url) throws Exception {
                return FIXTURE_XML;
            }
        };
    }

    private JsonNode json(String s) {
        try { return mapper.readTree(s); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    // ---- getName / getDescription / getInputSchema ----

    @Test
    void getName_returnsSearchTaiwanNews() {
        assertThat(tool.getName()).isEqualTo("search_taiwan_news");
    }

    @Test
    void getDescription_isNotEmpty() {
        assertThat(tool.getDescription()).isNotBlank();
    }

    @Test
    void getInputSchema_hasKeywordRequired() {
        JsonNode schema = tool.getInputSchema();
        assertThat(schema.path("properties").has("keyword")).isTrue();
        assertThat(schema.path("required").toString()).contains("keyword");
    }

    @Test
    void getInputSchema_hasOptionalLimit() {
        JsonNode schema = tool.getInputSchema();
        assertThat(schema.path("properties").has("limit")).isTrue();
    }

    // ---- execute happy paths ----

    @Test
    void execute_keywordInTitle_returnsMatchingItem() {
        JsonNode args = json("{\"keyword\": \"半導體\"}");
        String result = tool.execute(args);

        assertThat(result).contains("半導體產能再創新高");
        assertThat(result).contains("https://www.cna.com.tw/news/ait/1.aspx");
    }

    @Test
    void execute_keywordInDescription_returnsMatchingItem() {
        JsonNode args = json("{\"keyword\": \"加權指數\"}");
        String result = tool.execute(args);

        assertThat(result).contains("台股今日小幅上漲");
    }

    @Test
    void execute_multipleMatches_returnsAll() {
        // "科技" matches title of item 1 and title+description of item 2
        JsonNode args = json("{\"keyword\": \"科技\"}");
        String result = tool.execute(args);

        assertThat(result).contains("1.");
        assertThat(result).contains("2.");
    }

    @Test
    void execute_limitRespected_capsResults() {
        JsonNode args = json("{\"keyword\": \"台\", \"limit\": 1}");
        String result = tool.execute(args);

        // Only 1 result header, no "2."
        assertThat(result).contains("找到 1 則");
        assertThat(result).doesNotContain("2.");
    }

    @Test
    void execute_noMatches_returnsNotFoundMessage() {
        JsonNode args = json("{\"keyword\": \"不存在關鍵字XYZ\"}");
        String result = tool.execute(args);

        assertThat(result).contains("找不到");
        assertThat(result).contains("不存在關鍵字XYZ");
    }

    @Test
    void execute_emptyKeyword_returnsPrompt() {
        JsonNode args = json("{\"keyword\": \"\"}");
        String result = tool.execute(args);

        assertThat(result).contains("請提供搜尋關鍵字");
    }

    @Test
    void execute_caseInsensitive_matchesLowerAndUpper() {
        JsonNode args = json("{\"keyword\": \"CNA\"}");
        // fixture links contain "cna.com.tw" — keyword in description match (lowercase)
        // This tests that the match is case-insensitive
        SearchTaiwanNewsTool caseTestTool = new SearchTaiwanNewsTool(List.of("unused")) {
            @Override
            protected String fetchXml(String url) throws Exception {
                return """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <rss version="2.0"><channel>
                          <item>
                            <title>CNA announces new service</title>
                            <link>https://example.com/1</link>
                            <description>Details about cna service</description>
                            <pubDate>Thu, 24 Apr 2026 10:00:00 +0800</pubDate>
                          </item>
                        </channel></rss>
                        """;
            }
        };
        String result = caseTestTool.execute(json("{\"keyword\": \"cna\"}"));
        assertThat(result).contains("CNA announces");
    }

    // ---- parseMatchingItems unit tests ----

    @Test
    void parseMatchingItems_returnsCorrectFields() {
        List<String[]> items = tool.parseMatchingItems(FIXTURE_XML, "半導體", 5);

        assertThat(items).hasSize(1);
        String[] first = items.get(0);
        assertThat(first[0]).contains("半導體");   // title
        assertThat(first[1]).contains("cna.com.tw"); // link
        // first[2] = description, first[3] = pubDate
        assertThat(first[3]).isNotBlank();
    }

    @Test
    void parseMatchingItems_longDescription_isTruncated() {
        String longDescXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"><channel>
                  <item>
                    <title>測試新聞標題</title>
                    <link>https://example.com</link>
                    <description>%s</description>
                    <pubDate>Thu, 24 Apr 2026 10:00:00 +0800</pubDate>
                  </item>
                </channel></rss>
                """.formatted("A".repeat(200));

        List<String[]> items = tool.parseMatchingItems(longDescXml, "測試", 5);
        assertThat(items).hasSize(1);
        assertThat(items.get(0)[2]).hasSizeLessThanOrEqualTo(124); // 120 chars + "…"
    }

    @Test
    void parseMatchingItems_htmlTagsStrippedFromDescription() {
        String htmlDescXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0"><channel>
                  <item>
                    <title>測試</title>
                    <link>https://example.com</link>
                    <description><![CDATA[<p>台灣<b>科技</b>新聞</p>]]></description>
                    <pubDate>Thu, 24 Apr 2026 10:00:00 +0800</pubDate>
                  </item>
                </channel></rss>
                """;

        List<String[]> items = tool.parseMatchingItems(htmlDescXml, "科技", 5);
        assertThat(items).hasSize(1);
        assertThat(items.get(0)[2]).doesNotContain("<p>").doesNotContain("<b>");
    }

    @Test
    void parseMatchingItems_unclosedHtmlTagsInDescription_doesNotThrow() {
        // Feeds with raw HTML (unclosed <br>, HTML entities) must not throw
        String xmlWithHtml = """
                <rss><channel>
                  <item>
                    <title>科技新聞</title>
                    <link>https://example.com</link>
                    <description>第一段<br>第二段 &nbsp; 說明</description>
                    <pubDate>Thu, 24 Apr 2026 10:00:00 +0800</pubDate>
                  </item>
                </channel></rss>
                """;
        List<String[]> items = tool.parseMatchingItems(xmlWithHtml, "科技", 5);
        assertThat(items).hasSize(1);
        assertThat(items.get(0)[2]).doesNotContain("<br>");
    }

    @Test
    void parseMatchingItems_bareAmpersandInLink_doesNotThrow() {
        String xmlWithAmp = """
                <rss><channel>
                  <item>
                    <title>台灣 A&B 科技新聞</title>
                    <link>https://example.com?a=1&limit=5</link>
                    <description>說明</description>
                    <pubDate>Thu, 24 Apr 2026 10:00:00 +0800</pubDate>
                  </item>
                </channel></rss>
                """;
        List<String[]> items = tool.parseMatchingItems(xmlWithAmp, "科技", 5);
        assertThat(items).hasSize(1);
    }

    // ---- Atom feed support ----

    @Test
    void parseMatchingItems_atomFeed_parsesEntries() {
        String atomXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <title>公視新聞網</title>
                  <entry>
                    <title><![CDATA[台灣科技廠商宣布重大突破]]></title>
                    <link rel="alternate" href="https://news.pts.org.tw/article/123"/>
                    <summary type="html"><![CDATA[台灣半導體廠商發表聲明，宣布新製程量產。]]></summary>
                    <updated>2026-05-05T10:00:00+08:00</updated>
                  </entry>
                  <entry>
                    <title><![CDATA[今日天氣晴朗]]></title>
                    <link rel="alternate" href="https://news.pts.org.tw/article/124"/>
                    <summary type="html"><![CDATA[氣象局預報各地晴朗。]]></summary>
                    <updated>2026-05-05T09:00:00+08:00</updated>
                  </entry>
                </feed>
                """;

        List<String[]> items = tool.parseMatchingItems(atomXml, "科技", 5);

        assertThat(items).hasSize(1);
        assertThat(items.get(0)[0]).contains("科技");
        assertThat(items.get(0)[1]).contains("pts.org.tw");
        assertThat(items.get(0)[3]).contains("2026-05-05");
    }

    // ---- fetchXml error handling ----

    @Test
    void execute_fetchThrows_returnsErrorMessage() {
        SearchTaiwanNewsTool failingTool = new SearchTaiwanNewsTool(List.of("unused")) {
            @Override
            protected String fetchXml(String url) throws Exception {
                throw new java.io.IOException("connection refused");
            }
        };

        JsonNode args = json("{\"keyword\": \"台灣\"}");
        String result = failingTool.execute(args);

        assertThat(result).contains("找不到");
        assertThat(result).contains("connection refused");
    }
}
