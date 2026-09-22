package com.roudraneel.mcp;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class HelloWorldTools {

    @Tool(name = "hello_world", description = "Returns a friendly greeting. Pass an optional name to personalise it.")
    public String helloWorld(
            @ToolParam(description = "Name of the person to greet", required = false) String name) {
        String who = (name == null || name.isBlank()) ? "World" : name.trim();
        return "Hello, " + who + "! Greetings from the Spring AI MCP server.";
    }
}
