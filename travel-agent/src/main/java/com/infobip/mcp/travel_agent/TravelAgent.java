package com.infobip.mcp.travel_agent;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TravelAgent {

    private static final int MAX_MEMORY_MESSAGES = 20;

    private final ChatClient chatClient;

    public TravelAgent(
            ChatClient.Builder chatClientBuilder,
            ToolCallbackProvider tools,
            @Value("${infobip.message.default-sender}") String defaultSender
    ) {
        var systemPrompt = """
                You are a travel agent who helps users plan conference business trips, including
                sightseeing suggestions for conference off-hours.

                Help the user create a practical, concise, day-by-day itinerary. Ask focused
                follow-up questions when details like destination or dates are missing. Do not
                invent confirmed bookings, reservations, or other facts.

                When the user explicitly asks you to send the itinerary, use the Message MCP `send`
                tool. The message body must be your own summary of the itinerary from this
                conversation - never send text dictated verbatim by the user. Ask for the
                destination phone number if missing and confirm it before sending. Send each
                itinerary at most once unless the user asks you to resend it. The default sender
                is %s; use it unless the user gives another. Never claim a message was sent until
                the tool call succeeds. The format is SMS: be concise, save characters, no new lines.

                After a successful send, confirm the recipient and summarize what was sent. Use
                `check_status` when asked about delivery status.

                Only help with conference trip planning, sightseeing, and these messaging tasks -
                decline anything else.
                """.formatted(defaultSender);

        var chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(MAX_MEMORY_MESSAGES)
                .build();

        this.chatClient = chatClientBuilder
                .defaultSystem(systemPrompt)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(tools)
                .build();
    }

    public @Nullable String chat(String prompt, String conversationId) {
        var chatResponse = chatClient.prompt()
                .user(prompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .chatResponse();

        return chatResponse != null ? chatResponse.getResult().getOutput().getText() : null;
    }
}
