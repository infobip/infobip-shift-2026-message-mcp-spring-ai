package com.infobip.mcp.travel_agent;

import jakarta.validation.constraints.NotBlank;

public record PromptRequest(@NotBlank String prompt) {
}
