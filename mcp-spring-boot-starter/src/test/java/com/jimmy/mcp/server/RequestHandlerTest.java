package com.jimmy.mcp.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jimmy.mcp.autoconfigure.McpServerProperties;
import com.jimmy.mcp.tools.McpTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RequestHandlerTest {

    private ObjectMapper mapper;
    private RequestHandler handler;

    /** Minimal McpTool stub for the echo use case */
    private static final McpTool ECHO = new McpTool() {
        @Override public String getName() { return "echo"; }
        @Override public String getDescription() { return "echoes text"; }
        @Override public JsonNode getInputSchema() { return new ObjectMapper().createObjectNode(); }
        @Override public String execute(JsonNode args) { return args.path("text").asText(""); }
    };

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        McpServerProperties props = new McpServerProperties();   // default values
        ToolRegistry registry = new ToolRegistry(List.of(ECHO));
        handler = new RequestHandler(mapper, registry, props);
    }

    private JsonNode req(String json) {
        try { return mapper.readTree(json); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    // ---- initialize ----

    @Test
    void initialize_returnsProtocolVersionAndServerInfo() {
        JsonNode resp = handler.handle(req(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}"
        )).orElseThrow();

        assertThat(resp.path("result").path("protocolVersion").asText()).isNotBlank();
        assertThat(resp.path("result").path("serverInfo").path("name").asText()).isEqualTo("mcp-server-java");
        assertThat(resp.path("result").path("capabilities").has("tools")).isTrue();
        assertThat(resp.path("id").asInt()).isEqualTo(1);
    }

    @Test
    void initialize_usesCustomProperties() {
        McpServerProperties props = new McpServerProperties();
        props.setName("my-custom-server");
        props.setVersion("2.0.0");
        RequestHandler custom = new RequestHandler(mapper, new ToolRegistry(List.of()), props);

        JsonNode resp = custom.handle(req(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}"
        )).orElseThrow();

        assertThat(resp.path("result").path("serverInfo").path("name").asText()).isEqualTo("my-custom-server");
        assertThat(resp.path("result").path("serverInfo").path("version").asText()).isEqualTo("2.0.0");
    }

    // ---- tools/list ----

    @Test
    void toolsList_returnsRegisteredTools() {
        JsonNode resp = handler.handle(req(
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}"
        )).orElseThrow();

        JsonNode tools = resp.path("result").path("tools");
        assertThat(tools.isArray()).isTrue();
        assertThat(tools.size()).isEqualTo(1);
        assertThat(tools.get(0).path("name").asText()).isEqualTo("echo");
    }

    @Test
    void toolsList_eachToolHasNameDescriptionAndSchema() {
        JsonNode tool = handler.handle(req(
                "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/list\",\"params\":{}}"
        )).orElseThrow().path("result").path("tools").get(0);

        assertThat(tool.has("name")).isTrue();
        assertThat(tool.has("description")).isTrue();
        assertThat(tool.has("inputSchema")).isTrue();
    }

    // ---- tools/call ----

    @Test
    void toolsCall_echoTool_returnsTextContent() {
        JsonNode resp = handler.handle(req("""
                {"jsonrpc":"2.0","id":4,"method":"tools/call",
                 "params":{"name":"echo","arguments":{"text":"hello"}}}
                """)).orElseThrow();

        JsonNode content = resp.path("result").path("content");
        assertThat(content.isArray()).isTrue();
        assertThat(content.get(0).path("type").asText()).isEqualTo("text");
        assertThat(content.get(0).path("text").asText()).isEqualTo("hello");
        assertThat(resp.path("result").path("isError").asBoolean()).isFalse();
    }

    @Test
    void toolsCall_unknownTool_returnsError32602() {
        JsonNode resp = handler.handle(req("""
                {"jsonrpc":"2.0","id":5,"method":"tools/call",
                 "params":{"name":"no-such-tool","arguments":{}}}
                """)).orElseThrow();

        assertThat(resp.path("error").path("code").asInt()).isEqualTo(-32602);
        assertThat(resp.path("error").path("message").asText()).contains("no-such-tool");
    }

    @Test
    void toolsCall_missingNameParam_returnsError32602() {
        JsonNode resp = handler.handle(req(
                "{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/call\",\"params\":{}}"
        )).orElseThrow();

        assertThat(resp.path("error").path("code").asInt()).isEqualTo(-32602);
    }

    @Test
    void toolsCall_toolThrows_setsIsErrorTrue() {
        McpTool bombTool = new McpTool() {
            @Override public String getName() { return "bomb"; }
            @Override public String getDescription() { return "always throws"; }
            @Override public JsonNode getInputSchema() { return mapper.createObjectNode(); }
            @Override public String execute(JsonNode args) { throw new RuntimeException("kaboom"); }
        };
        RequestHandler h = new RequestHandler(mapper, new ToolRegistry(List.of(bombTool)), new McpServerProperties());

        JsonNode resp = h.handle(req("""
                {"jsonrpc":"2.0","id":7,"method":"tools/call",
                 "params":{"name":"bomb","arguments":{}}}
                """)).orElseThrow();

        assertThat(resp.path("result").path("isError").asBoolean()).isTrue();
        assertThat(resp.path("result").path("content").get(0).path("text").asText()).contains("kaboom");
    }

    // ---- notifications ----

    @Test
    void notification_notificationsInitialized_returnsEmpty() {
        Optional<JsonNode> resp = handler.handle(req(
                "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"
        ));
        assertThat(resp).isEmpty();
    }

    @Test
    void notification_nullId_returnsEmpty() {
        Optional<JsonNode> resp = handler.handle(req(
                "{\"jsonrpc\":\"2.0\",\"id\":null,\"method\":\"ping\"}"
        ));
        assertThat(resp).isEmpty();
    }

    // ---- ping ----

    @Test
    void ping_returnsEmptyResultObject() {
        JsonNode resp = handler.handle(req(
                "{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"ping\"}"
        )).orElseThrow();

        assertThat(resp.path("result").isObject()).isTrue();
        assertThat(resp.path("error").isMissingNode()).isTrue();
    }

    // ---- unknown method ----

    @Test
    void unknownMethod_returnsError32601() {
        JsonNode resp = handler.handle(req(
                "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"does/not/exist\"}"
        )).orElseThrow();

        assertThat(resp.path("error").path("code").asInt()).isEqualTo(-32601);
    }

    // ---- response envelope ----

    @Test
    void successResponse_containsJsonrpcAndId() {
        JsonNode resp = handler.handle(req(
                "{\"jsonrpc\":\"2.0\",\"id\":10,\"method\":\"ping\"}"
        )).orElseThrow();

        assertThat(resp.path("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(resp.path("id").asInt()).isEqualTo(10);
    }

    @Test
    void errorResponse_containsJsonrpcAndId() {
        JsonNode resp = handler.handle(req(
                "{\"jsonrpc\":\"2.0\",\"id\":11,\"method\":\"bad/method\"}"
        )).orElseThrow();

        assertThat(resp.path("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(resp.path("id").asInt()).isEqualTo(11);
        assertThat(resp.path("error").isObject()).isTrue();
    }
}
