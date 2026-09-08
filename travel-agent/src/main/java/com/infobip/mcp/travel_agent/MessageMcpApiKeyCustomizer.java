package com.infobip.mcp.travel_agent;

import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import org.springframework.ai.mcp.customizer.McpClientCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * Adds the Infobip API key to requests sent to the Message MCP server.
 */
@Component
public class MessageMcpApiKeyCustomizer
        implements McpClientCustomizer<HttpClientStreamableHttpTransport.Builder> {

    private static final String MESSAGE_CONNECTION_NAME = "message";

    private final String authorizationHeaderValue;

    public MessageMcpApiKeyCustomizer(@Value("${infobip.api-key}") String infobipApiKey) {
        this.authorizationHeaderValue = "App " + infobipApiKey;
    }

    @Override
    public void customize(
            String connectionName,
            HttpClientStreamableHttpTransport.Builder transportBuilder
    ) {
        if (!MESSAGE_CONNECTION_NAME.equals(connectionName)) {
            return;
        }

        transportBuilder.httpRequestCustomizer((request, method, uri, body, context) ->
                request.header(HttpHeaders.AUTHORIZATION, authorizationHeaderValue));
    }
}
