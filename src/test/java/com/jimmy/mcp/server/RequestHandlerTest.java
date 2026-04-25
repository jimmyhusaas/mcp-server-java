package com.jimmy.mcp.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jimmy.mcp.tools.EchoTool;
import com.jimmy.mcp.tools.McpTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RequestHandlerTest {

    private ObjectMapper mapper;
    private RequestHandler handler;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        ToolRegistry registry = new ToolRegistry(List.of(new EchoTool()));
        handler = new RequestHandler(mapper, registry);
    }

    // ---- initialize ----

    @Test
    void initialize_returnsProtocolVersionAndServerInfo() throws Exception {
        JsonNode req = mapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");
        JsonNode resp = handler.handle(req).orElseThrow();

        assertThat(resp.path("result").path("protocolVersion").asText()).isNotBlank();
        assertThat(resp.path("result").path("serverInfo").path("name").asText()).isEqualTo("mcp-server-java");
        assertThat(resp.path("result").path("capabilities").has("tools")).isTrue();
        assertThat(resp.path("id").asInt()).isEqualTo(1);
    }

    // ---- tools/list ----

    @Test
    void toolsList_returnsRegisteredTools() throws Exception {
        JsonNode req = mapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}");
        JsonNode resp = handler.handle(req).orElseThrow();

        JsonNode tools = resp.path("result").path("tools");
        assertThat(tools.isArray()).isTrue();
        assertThat(tools.size()).isEqualTo(1);
        assertThat(tools.get(0).path("name").asText()).isEqualTo("echo");
    }

    @Test
    void toolsList_eachToolHasNameDescriptionAndSchema() throws Exception {
        JsonNode req = mapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/list\",\"params\":{}}");
        JsonNode tool = handler.handle(req).orElseThrow().path("result").path("tools").get(0);

        assertThat(tool.has("name")).isTrue();
        assertThat(tool.has("description")).isTrue();
        assertThat(tool.has("inputSchema")).isTrue();
    }

    // ---- tools/call ----

    @Test
    void toolsCall_echoTool_returnsTextContent() throws Exception {
        JsonNode req = mapper.readTree("""
            {"jsonrpc":"2.0","id":4,"method":"tools/call",
             "params":{"name":"echo","arguments":{"text":"hello"}}}
            """);
        JsonNode resp = handler.handle(req).orElseThrow();

        JsonNode content = resp.path("result").path("content");
        assertThat(content.isArray()).isTrue();
        assertThat(content.get(0).path("type").asText()).isEqualTo("text");
        assertThat(content.get(0).path("text").asText()).isEqualTo("hello");
        assertThat(resp.path("result").path("isError").asBoolean()).isFalse();
    }

    @Test
    void toolsCall_unknownTool_returnsError32602() throws Exception {
        JsonNode req = mapper.readTree("""
            {"jsonrpc":"2.0","id":5,"method":"tools/call",
             "params":{"name":"no-such-tool","arguments":{}}}
            """);
        JsonNode resp = handler.handle(req).orElseThrow();

        assertThat(resp.path("error").path("code").asInt()).isEqualTo(-32602);
        assertThat(resp.path("error").path("message").asText()).contains("no-such-tool");
    }

    @Test
    void toolsCall_missingNameParam_returnsError32602() throws Exception {
        JsonNode req = mapper.readTree("""
            {"jsonrpc":"2.0","id":6,"method":"tools/call","params":{}}
            """);
        JsonNode resp = handler.handle(req).orElseThrow();

        assertThat(resp.path("error").path("code").asInt()).isEqualTo(-32602);
    }

    @Test
    void toolsCall_toolThrows_setsIsErrorTrue() throws Exception {
        McpTool bombTool = new McpTool() {
            @Override public String getName() { return "bomb"; }
            @Override public String getDescription() { return "always throws"; }
            @Override public JsonNode getInputSchema() { return mapper.createObjectNode(); }
            @Override public String execute(JsonNode args) { throw new RuntimeException("kaboom"); }
        };
        RequestHandler handlerWithBomb = new RequestHandler(mapper, new ToolRegistry(List.of(bombTool)));

        JsonNode req = mapper.readTree("""
            {"jsonrpc":"2.0","id":7,"method":"tools/call",
             "params":{"name":"bomb","arguments":{}}}
            """);
        JsonNode resp = handlerWithBomb.handle(req).orElseThrow();

        assertThat(resp.path("result").path("isError").asBoolean()).isTrue();
        assertThat(resp.path("result").path("content").get(0).path("text").asText()).contains("kaboom");
    }

    // ---- notifications ----

    @Test
    void notification_notificationsInitialized_returnsEmpty() throws Exception {
        JsonNode req = mapper.readTree("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
        Optional<JsonNode> resp = handler.handle(req);
        assertThat(resp).isEmpty();
    }

    @Test
    void notification_anyMethodWithNullId_returnsEmpty() throws Exception {
        JsonNode req = mapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":null,\"method\":\"ping\"}");
        Optional<JsonNode> resp = handler.handle(req);
        assertThat(resp).isEmpty();
    }

    // ---- ping ----

    @Test
    void ping_returnsEmptyResultObject() throws Exception {
        JsonNode req = mapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"ping\"}");
        JsonNode resp = handler.handle(req).orElseThrow();

        assertThat(resp.path("result").isObject()).isTrue();
        assertThat(resp.path("error").isMissingNode()).isTrue();
    }

    // ---- unknown method ----

    @Test
    void unknownMethod_returnsError32601() throws Exception {
        JsonNode req = mapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"does/not/exist\"}");
        JsonNode resp = handler.handle(req).orElseThrow();

        assertThat(resp.path("error").path("code").asInt()).isEqualTo(-32601);
    }

    // ---- response envelope ----

    @Test
    void successResponse_alwaysContainsJsonrpcAndId() throws Exception {
        JsonNode req = mapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":10,\"method\":\"ping\"}");
        JsonNode resp = handler.handle(req).orElseThrow();

        assertThat(resp.path("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(resp.path("id").asInt()).isEqualTo(10);
    }

    @Test
    void errorResponse_alwaysContainsJsonrpcAndId() throws Exception {
        JsonNode req = mapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":11,\"method\":\"bad/method\"}");
        JsonNode resp = handler.handle(req).orElseThrow();

        assertThat(resp.path("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(resp.path("id").asInt()).isEqualTo(11);
        assertThat(resp.path("error").isObject()).isTrue();
    }
}
