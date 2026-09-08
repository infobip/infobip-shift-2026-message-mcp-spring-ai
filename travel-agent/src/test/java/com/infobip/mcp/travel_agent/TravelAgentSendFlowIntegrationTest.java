package com.infobip.mcp.travel_agent;

import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestBase.BedrockTestConfig.class)
class TravelAgentSendFlowIntegrationTest extends TestBase {

    private static final String CONVERSE_URL_PATTERN = "/model/.*/converse";
    private static final String CONVERSATION_ID_HEADER = "X-Conversation-Id";
    private static final String TEST_DESTINATION = "TEST_DESTINATION";

    @Autowired
    private RestTestClient restTestClient;

    @BeforeEach
    void stubBedrockAndMcpToolCall() {
        // Turn 1: plain itinerary text, no tool use.
        WIRE_MOCK.stubFor(post(urlPathMatching(CONVERSE_URL_PATTERN))
                .inScenario("bedrock-conversation")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(okJson("""
                        {
                          "output": { "message": { "role": "assistant", "content": [
                            { "text": "Day 1: arrive in Zadar, check in, walk the old town. Day 2: attend Infobip Shift, evening at the waterfront." }
                          ] } },
                          "stopReason": "end_turn",
                          "usage": { "inputTokens": 20, "outputTokens": 30, "totalTokens": 50 },
                          "metrics": { "latencyMs": 100 }
                        }
                        """))
                .willSetStateTo("itinerary-sent"));

        // Turn 2: Bedrock asks to call the send tool.
        WIRE_MOCK.stubFor(post(urlPathMatching(CONVERSE_URL_PATTERN))
                .inScenario("bedrock-conversation")
                .whenScenarioStateIs("itinerary-sent")
                .willReturn(okJson("""
                        {
                          "output": { "message": { "role": "assistant", "content": [
                            { "toolUse": {
                                "toolUseId": "tooluse_test_1",
                                "name": "%s",
                                "input": { "to": "%s", "text": "Day 1: Zadar arrival, old town. Day 2: Shift conference, waterfront evening." }
                              }
                            }
                          ] } },
                          "stopReason": "tool_use",
                          "usage": { "inputTokens": 40, "outputTokens": 25, "totalTokens": 65 },
                          "metrics": { "latencyMs": 120 }
                        }
                        """.formatted(SEND_TOOL_NAME, TEST_DESTINATION)))
                .willSetStateTo("tool-use-requested"));

        // Turn 3: after the tool result is fed back, Bedrock confirms.
        WIRE_MOCK.stubFor(post(urlPathMatching(CONVERSE_URL_PATTERN))
                .inScenario("bedrock-conversation")
                .whenScenarioStateIs("tool-use-requested")
                .willReturn(okJson("""
                        {
                          "output": { "message": { "role": "assistant", "content": [
                            { "text": "Sent! Your two-day Zadar itinerary was texted to %s." }
                          ] } },
                          "stopReason": "end_turn",
                          "usage": { "inputTokens": 60, "outputTokens": 15, "totalTokens": 75 },
                          "metrics": { "latencyMs": 90 }
                        }
                        """.formatted(TEST_DESTINATION)))
                .willSetStateTo("done"));

        // MCP tools/call for the send tool.
        WIRE_MOCK.stubFor(post(urlEqualTo(MCP_ENDPOINT))
                .withRequestBody(matchingJsonPath("$.method", equalTo("tools/call")))
                .willReturn(okJson("""
                        {
                          "jsonrpc": "2.0",
                          "id": "{{jsonPath request.body '$.id'}}",
                          "result": {
                            "content": [ { "type": "text", "text": "Message sent to %s." } ],
                            "isError": false
                          }
                        }
                        """.formatted(TEST_DESTINATION))));
    }

    @Test
    void plansAndSendsItineraryAcrossTwoTurns() {
        var itineraryResult = postChat(
                        "Plan a two-day conference trip to Infobip Shift Zadar.", null)
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();

        assertThat(itineraryResult.getResponseBody()).isNotBlank();
        var conversationId = itineraryResult.getResponseHeaders().getFirst(CONVERSATION_ID_HEADER);
        assertThat(conversationId).isNotBlank();

        var sendResult = postChat(
                        "Send that itinerary to " + TEST_DESTINATION + ".", conversationId)
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();

        assertThat(sendResult.getResponseBody()).isNotBlank();
        assertThat(sendResult.getResponseHeaders().getFirst(CONVERSATION_ID_HEADER))
                .isEqualTo(conversationId);

        WIRE_MOCK.verify(postRequestedFor(urlEqualTo(MCP_ENDPOINT))
                .withRequestBody(matchingJsonPath("$.method", equalTo("tools/call")))
                .withRequestBody(matchingJsonPath("$.params.name", equalTo(SEND_TOOL_NAME))));
    }

    private RestTestClient.ResponseSpec postChat(String prompt, String conversationId) {
        var spec = restTestClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON);
        if (conversationId != null) {
            spec = spec.header(CONVERSATION_ID_HEADER, conversationId);
        }
        return spec.body(new PromptRequest(prompt)).exchange();
    }
}
