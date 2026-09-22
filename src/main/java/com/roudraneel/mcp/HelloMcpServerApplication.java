package com.roudraneel.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class HelloMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(HelloMcpServerApplication.class, args);
    }

    /**
     * Every {@code @Tool}-annotated method on the given beans is registered with the MCP server
     * and advertised to clients via {@code tools/list}.
     */
    @Bean
    ToolCallbackProvider helloTools(HelloWorldTools helloWorldTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(helloWorldTools)
                .build();
    }
}
