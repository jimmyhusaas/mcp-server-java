package com.jimmy.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GetTimeToolTest {

    private GetTimeTool tool;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        tool = new GetTimeTool();
        mapper = new ObjectMapper();
    }

    @Test
    void getName_returnsGetTime() {
        assertThat(tool.getName()).isEqualTo("get_time");
    }

    @Test
    void getDescription_isNotEmpty() {
        assertThat(tool.getDescription()).isNotBlank();
    }

    @Test
    void getInputSchema_hasOptionalZoneProperty() {
        JsonNode schema = tool.getInputSchema();
        assertThat(schema.path("type").asText()).isEqualTo("object");
        assertThat(schema.path("properties").has("zone")).isTrue();
        // zone is optional — no "required" array
        assertThat(schema.has("required")).isFalse();
    }

    @Test
    void execute_noArgs_defaultsToTaipei() throws Exception {
        JsonNode args = mapper.readTree("{}");
        String result = tool.execute(args);
        assertThat(result).contains("Asia/Taipei");
    }

    @Test
    void execute_explicitUtcZone_returnsUtcTime() throws Exception {
        JsonNode args = mapper.readTree("{\"zone\": \"UTC\"}");
        String result = tool.execute(args);
        assertThat(result).contains("UTC");
    }

    @Test
    void execute_explicitTaipeiZone_returnsTaipeiTime() throws Exception {
        JsonNode args = mapper.readTree("{\"zone\": \"Asia/Taipei\"}");
        String result = tool.execute(args);
        assertThat(result).contains("Asia/Taipei");
    }

    @Test
    void execute_invalidZone_returnsErrorMessage() throws Exception {
        JsonNode args = mapper.readTree("{\"zone\": \"Mars/Olympus\"}");
        String result = tool.execute(args);
        assertThat(result).startsWith("Invalid timezone:");
        assertThat(result).contains("Mars/Olympus");
    }

    @Test
    void execute_nullZoneField_defaultsToTaipei() throws Exception {
        JsonNode args = mapper.readTree("{\"zone\": null}");
        String result = tool.execute(args);
        assertThat(result).contains("Asia/Taipei");
    }

    @Test
    void execute_nullArgs_defaultsToTaipei() {
        String result = tool.execute(null);
        assertThat(result).contains("Asia/Taipei");
    }
}
