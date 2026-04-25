package com.jimmy.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EchoToolTest {

    private EchoTool tool;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        tool = new EchoTool();
        mapper = new ObjectMapper();
    }

    @Test
    void getName_returnsEcho() {
        assertThat(tool.getName()).isEqualTo("echo");
    }

    @Test
    void getDescription_isNotEmpty() {
        assertThat(tool.getDescription()).isNotBlank();
    }

    @Test
    void getInputSchema_hasTextProperty() {
        JsonNode schema = tool.getInputSchema();
        assertThat(schema.path("type").asText()).isEqualTo("object");
        assertThat(schema.path("properties").has("text")).isTrue();
        assertThat(schema.path("required").toString()).contains("text");
    }

    @Test
    void execute_returnsInputText() throws Exception {
        JsonNode args = mapper.readTree("{\"text\": \"hello world\"}");
        assertThat(tool.execute(args)).isEqualTo("hello world");
    }

    @Test
    void execute_missingTextField_returnsPlaceholder() throws Exception {
        JsonNode args = mapper.readTree("{}");
        assertThat(tool.execute(args)).isEqualTo("(no text provided)");
    }

    @Test
    void execute_nullTextField_returnsPlaceholder() throws Exception {
        JsonNode args = mapper.readTree("{\"text\": null}");
        assertThat(tool.execute(args)).isEqualTo("(no text provided)");
    }

    @Test
    void execute_emptyString_returnsEmptyString() throws Exception {
        JsonNode args = mapper.readTree("{\"text\": \"\"}");
        assertThat(tool.execute(args)).isEqualTo("");
    }
}
