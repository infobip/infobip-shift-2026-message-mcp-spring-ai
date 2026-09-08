package com.infobip.mcp.travel_agent;

import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class TravelAgentController {

    private static final String CONVERSATION_ID_HEADER = "X-Conversation-Id";

    private final TravelAgent travelAgent;

    public TravelAgentController(TravelAgent travelAgent) {
        this.travelAgent = travelAgent;
    }

    @PostMapping("/chat")
    public ResponseEntity<String> chat(
            @Valid @RequestBody PromptRequest promptRequest,
            @RequestHeader(value = CONVERSATION_ID_HEADER, required = false) @Nullable String conversationIdHeader
    ) {
        var conversationId = StringUtils.hasText(conversationIdHeader)
                ? conversationIdHeader
                : UUID.randomUUID().toString();

        var body = travelAgent.chat(promptRequest.prompt(), conversationId);

        return ResponseEntity.ok()
                .header(CONVERSATION_ID_HEADER, conversationId)
                .body(body);
    }
}
