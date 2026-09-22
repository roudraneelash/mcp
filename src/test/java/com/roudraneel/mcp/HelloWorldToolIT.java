package com.roudraneel.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.ai.mcp.client.enabled=false")
class HelloWorldToolIT {

    @LocalServerPort
    int port;

    @Test
    void initializeListAndCallHelloWorld() {
        var transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                .endpoint("/mcp")
                .build();

        try (McpSyncClient client = McpClient.sync(transport).build()) {
            McpSchema.InitializeResult init = client.initialize();
            assertThat(init.serverInfo().name()).isEqualTo("hello-mcp-server");
            assertThat(init.capabilities().tools()).isNotNull();

            McpSchema.ListToolsResult tools = client.listTools();
            assertThat(tools.tools()).extracting(McpSchema.Tool::name).containsExactly("hello_world");

            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest("hello_world", Map.of("name", "Copilot")));
            assertThat(result.isError()).isFalse();
            assertThat(((McpSchema.TextContent) result.content().get(0)).text())
                    .contains("Hello, Copilot!");
        }
    }
}
