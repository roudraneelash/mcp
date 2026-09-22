package com.roudraneel.mcp;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * MCP allows {@code tools/call} to omit {@code arguments} entirely; Spring AI's method callbacks
 * expect a JSON object, so normalise a missing/null payload to {@code {}} before delegating.
 */
class NullSafeToolCallback implements ToolCallback {

    private final ToolCallback delegate;

    NullSafeToolCallback(ToolCallback delegate) {
        this.delegate = delegate;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return delegate.call(normalise(toolInput));
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return delegate.call(normalise(toolInput), toolContext);
    }

    private static String normalise(String toolInput) {
        if (toolInput == null || toolInput.isBlank() || "null".equals(toolInput.trim())) {
            return "{}";
        }
        return toolInput;
    }
}
