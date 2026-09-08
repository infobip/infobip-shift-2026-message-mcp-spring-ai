package com.infobip.mcp.travel_agent;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

import java.net.URI;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

/**
 * WireMock server standing in for both the Message MCP server and the Bedrock Converse
 * endpoint, with the MCP handshake stubs needed for the Spring context to start.
 */
@ActiveProfiles("test")
public abstract class TestBase {

    protected static final String MCP_ENDPOINT = "/message";
    protected static final String SEND_TOOL_NAME = "send";
    protected static final String CHECK_STATUS_TOOL_NAME = "check_status";

    protected static final WireMockServer WIRE_MOCK = new WireMockServer(
            WireMockConfiguration.options()
                    .dynamicPort()
                    .templatingEnabled(true)
                    .globalTemplating(true));

    static {
        WIRE_MOCK.start();
        stubMcpHandshake();
    }

    @DynamicPropertySource
    static void mcpProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.mcp.client.streamable-http.connections.message.url", WIRE_MOCK::baseUrl);
        registry.add("spring.ai.mcp.client.streamable-http.connections.message.endpoint", () -> MCP_ENDPOINT);
    }

    private static void stubMcpHandshake() {
        // 405 tells the client we don't support the optional server push stream; a 200
        // with an empty body instead leaves it waiting indefinitely for a first event.
        WIRE_MOCK.stubFor(get(urlEqualTo(MCP_ENDPOINT))
                .willReturn(aResponse().withStatus(405)));

        WIRE_MOCK.stubFor(post(urlEqualTo(MCP_ENDPOINT))
                .withRequestBody(matchingJsonPath("$.method", equalTo("initialize")))
                .willReturn(okJson("""
                        {
                          "jsonrpc": "2.0",
                          "id": "{{jsonPath request.body '$.id'}}",
                          "result": {
                            "protocolVersion": "2025-06-18",
                            "capabilities": { "tools": { "listChanged": false } },
                            "serverInfo": { "name": "message-mcp-fake", "version": "1.0.0" }
                          }
                        }
                        """).withHeader("Mcp-Session-Id", "test-session-1")));

        WIRE_MOCK.stubFor(post(urlEqualTo(MCP_ENDPOINT))
                .withRequestBody(matchingJsonPath("$.method", equalTo("notifications/initialized")))
                .willReturn(aResponse().withStatus(202)));

        WIRE_MOCK.stubFor(post(urlEqualTo(MCP_ENDPOINT))
                .withRequestBody(matchingJsonPath("$.method", equalTo("tools/list")))
                .willReturn(okJson("""
                        {
                          "jsonrpc": "2.0",
                          "id": "{{jsonPath request.body '$.id'}}",
                          "result": {
                            "tools": [
                              {
                                "name": "%s",
                                "description": "Send a message to a phone number over Viber or SMS.",
                                "inputSchema": {
                                  "type": "object",
                                  "properties": {
                                    "to": { "type": "string", "description": "Destination phone number in E.164 format" },
                                    "text": { "type": "string", "description": "Message body" },
                                    "from": { "type": "string", "description": "Sender identifier" }
                                  },
                                  "required": ["to", "text"]
                                }
                              },
                              {
                                "name": "%s",
                                "description": "Check the delivery status of a previously sent message.",
                                "inputSchema": {
                                  "type": "object",
                                  "properties": {
                                    "messageId": { "type": "string" }
                                  },
                                  "required": ["messageId"]
                                }
                              }
                            ]
                          }
                        }
                        """.formatted(SEND_TOOL_NAME, CHECK_STATUS_TOOL_NAME))));
    }

    // No Spring property exists to redirect the Bedrock AWS SDK client to WireMock, so
    // it's overridden as a bean instead. Nested @TestConfiguration isn't auto-detected
    // across inheritance, so subclasses must add @Import(TestBase.BedrockTestConfig.class).
    @TestConfiguration
    public static class BedrockTestConfig {

        @Bean
        BedrockRuntimeClient bedrockRuntimeClient() {
            return BedrockRuntimeClient.builder()
                    .region(Region.of("test-region"))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("test", "test")))
                    .endpointOverride(URI.create(WIRE_MOCK.baseUrl()))
                    .build();
        }

        @Bean
        BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient() {
            return BedrockRuntimeAsyncClient.builder()
                    .region(Region.of("test-region"))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("test", "test")))
                    .endpointOverride(URI.create(WIRE_MOCK.baseUrl()))
                    .build();
        }
    }
}
