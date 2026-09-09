package com.infobip.mcp.travel_agent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

@SpringBootTest
@Import(TestBase.BedrockTestConfig.class)
class MessageMcpApiKeyCustomizerIntegrationTest extends TestBase {

    @Value("${infobip.api-key}")
    private String infobipApiKey;

    @Test
    void addsAuthorizationHeaderToMcpRequests() {
        WIRE_MOCK.verify(postRequestedFor(urlEqualTo(MCP_ENDPOINT))
                .withRequestBody(matchingJsonPath("$.method", equalTo("initialize")))
                .withHeader("Authorization", equalTo("App " + infobipApiKey)));
    }
}