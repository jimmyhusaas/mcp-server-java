package com.jimmy.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchCnaToolTest {

    // Matches the shape CNA's cna2018api returns: { ResultData: { Items: [...] } }
    private static final String FIXTURE_JSON = """
            {
              "ResultCode": 200,
              "ResultData": {
                "Items": [
                  {
                    "Id": "202506040001",
                    "HeadLine": "台積電宣布擴廠 投資千億新台幣",
                    "Summary": "台積電董事長表示將在台中設立新廠，預計 2028 年量產。",
                    "CreateTime": "2026-06-04 10:30",
                    "PageUrl": "/news/ait/202506040001.aspx"
                  },
                  {
                    "Id": "202506040002",
                    "HeadLine": "總統出席台美論壇 強調民主價值",
                    "Summary": "總統在台美論壇致詞，重申台灣對民主自由的堅定立場。",
                    "CreateTime": "2026-06-04 09:15",
                    "PageUrl": "/news/aipl/202506040002.aspx"
                  },
                  {
                    "Id": "202506040003",
                    "HeadLine": "颱風生成 氣象局發布海上颱風警報",
                    "Summary": "今年首個颱風在菲律賓東方海面生成，預計明日逼近台灣東部。",
                    "CreateTime": "2026-06-04 08:00",
                    "PageUrl": "/news/ahel/202506040003.aspx"
                  }
                ]
              }
            }
            """;

    private static final String FIXTURE_JSON_KEYWORD_IN_DESC = """
            {
              "ResultCode": 200,
              "ResultData": {
                "Items": [
                  {
                    "Id": "202506040010",
                    "HeadLine": "科技業最新動態",
                    "Summary": "台積電相關供應鏈今日表現強勁，帶動大盤上揚。",
                    "CreateTime": "2026-06-04 11:00",
                    "PageUrl": "/news/ait/202506040010.aspx"
                  }
                ]
              }
            }
            """;

    private SearchCnaTool tool;

    @BeforeEach
    void setUp() {
        tool = new SearchCnaTool() {
            @Override
            protected String fetchJson(String url) {
                return FIXTURE_JSON;
            }
        };
    }

    @Test
    void name_and_description_are_set() {
        assertThat(tool.getName()).isEqualTo("search_cna_news");
        assertThat(tool.getDescription()).contains("中央社");
    }

    @Test
    void schema_is_valid_json_with_keyword_required() throws Exception {
        JsonNode schema = tool.getInputSchema();
        assertThat(schema.path("properties").has("keyword")).isTrue();
        assertThat(schema.path("required").toString()).contains("keyword");
    }

    @Test
    void matches_keyword_in_title() throws Exception {
        List<String[]> results = tool.parseItems(FIXTURE_JSON, "台積電", 5);
        assertThat(results).hasSize(1);
        assertThat(results.get(0)[0]).contains("台積電");
    }

    @Test
    void matches_keyword_in_summary() throws Exception {
        List<String[]> results = tool.parseItems(FIXTURE_JSON_KEYWORD_IN_DESC, "台積電", 5);
        assertThat(results).hasSize(1);
        assertThat(results.get(0)[2]).contains("台積電");
    }

    @Test
    void resolves_relative_url_to_absolute() throws Exception {
        List<String[]> results = tool.parseItems(FIXTURE_JSON, "台積電", 5);
        assertThat(results.get(0)[1]).startsWith("https://www.cna.com.tw/news/ait/");
    }

    @Test
    void respects_limit() throws Exception {
        List<String[]> results = tool.parseItems(FIXTURE_JSON, "台", 1);
        assertThat(results).hasSize(1);
    }

    @Test
    void case_insensitive_matching() throws Exception {
        List<String[]> results = tool.parseItems(FIXTURE_JSON, "cna", 5);
        // keyword "cna" doesn't match any Chinese text — expect empty, not an error
        assertThat(results).isEmpty();
    }

    @Test
    void returns_no_results_message_when_no_match() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode args = mapper.readTree("{\"keyword\": \"不存在的關鍵字xyz\"}");
        String result = tool.execute(args);
        assertThat(result).contains("找不到");
    }

    @Test
    void returns_error_on_empty_keyword() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode args = mapper.readTree("{\"keyword\": \"\"}");
        String result = tool.execute(args);
        assertThat(result).contains("請提供");
    }

    @Test
    void truncates_long_description() throws Exception {
        String longDescJson = """
                {
                  "ResultData": { "Items": [{
                    "HeadLine": "測試標題很長",
                    "Summary": "%s",
                    "CreateTime": "2026-06-04 10:00",
                    "PageUrl": "/news/test/1.aspx"
                  }]}
                }
                """.formatted("測".repeat(200));
        List<String[]> results = tool.parseItems(longDescJson, "測試", 5);
        assertThat(results.get(0)[2]).endsWith("…");
        assertThat(results.get(0)[2].length()).isLessThanOrEqualTo(124);
    }

    @Test
    void throws_on_unrecognised_json_structure() {
        assertThatThrownBy(() -> tool.parseItems("{\"unexpected\": true}", "keyword", 5))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("無法從回應中找到新聞列表");
    }

    @Test
    void parses_date_and_includes_in_output() throws Exception {
        List<String[]> results = tool.parseItems(FIXTURE_JSON, "台積電", 5);
        assertThat(results.get(0)[3]).isEqualTo("2026-06-04 10:30");
    }
}
