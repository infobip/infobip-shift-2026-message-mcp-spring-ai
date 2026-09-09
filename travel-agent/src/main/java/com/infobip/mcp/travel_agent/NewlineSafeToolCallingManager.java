package com.infobip.mcp.travel_agent;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * Normalizes tool-call arguments before execution to work around a Bedrock Converse
 * serialization defect in Spring AI 2.0.1.
 *
 * <p>{@code BedrockProxyChatModel} stringifies the tool-use input via the AWS SDK
 * {@code Document.toString()}, which is not a JSON serializer: it emits literal control
 * characters (e.g. {@code \n}) unescaped inside string values. The downstream
 * {@code SyncMcpToolCallback} then parses that string with a strict Jackson 3 mapper and
 * fails with "Illegal unquoted character (CTRL-CHAR, code 10)".
 *
 * <p>This manager re-parses each tool call's arguments with a lenient reader that tolerates
 * unescaped control characters, then re-serializes them as valid JSON before delegating to
 * the default {@link ToolCallingManager}. It is tool-agnostic and only rewrites arguments
 * when a tool call is present.
 */
@Component
public class NewlineSafeToolCallingManager implements ToolCallingManager {

    private final ToolCallingManager delegate = ToolCallingManager.builder().build();

    private final JsonMapper lenientMapper = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
            .build();

    @Override
    public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions chatOptions) {
        return delegate.resolveToolDefinitions(chatOptions);
    }

    @Override
    public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
        return delegate.executeToolCalls(prompt, normalize(chatResponse));
    }

    private ChatResponse normalize(ChatResponse chatResponse) {
        var normalizedGenerations = chatResponse.getResults().stream()
                .map(this::normalizeGeneration)
                .toList();

        return new ChatResponse(normalizedGenerations, chatResponse.getMetadata());
    }

    private Generation normalizeGeneration(Generation generation) {
        var message = generation.getOutput();
        if (!message.hasToolCalls()) {
            return generation;
        }

        var normalizedToolCalls = message.getToolCalls().stream()
                .map(this::normalizeArguments)
                .toList();

        var normalizedMessage = message.mutate()
                .toolCalls(normalizedToolCalls)
                .build();

        return new Generation(normalizedMessage, generation.getMetadata());
    }

    private AssistantMessage.ToolCall normalizeArguments(AssistantMessage.ToolCall toolCall) {
        var arguments = toolCall.arguments();
        if (arguments == null || arguments.isBlank()) {
            return toolCall;
        }

        try {
            var parsed = lenientMapper.readValue(arguments, Object.class);
            var repaired = lenientMapper.writeValueAsString(parsed);
            if (repaired.equals(arguments)) {
                return toolCall;
            }
            return new AssistantMessage.ToolCall(
                    toolCall.id(), toolCall.type(), toolCall.name(), repaired);
        } catch (RuntimeException e) {
            // Leave the original arguments untouched; downstream surfaces the real error.
            return toolCall;
        }
    }
}
