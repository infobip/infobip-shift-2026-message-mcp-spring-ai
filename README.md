# Infobip Shift 2026: Send Travel Itineraries by Viber with Message MCP and Spring AI

This is the Model Context Protocol (MCP) segment appendix to the joint AWS and Infobip workshop at [Infobip Shift 2026](https://shift.infobip.com/), **Building Java AI Agents With Spring AI and Amazon Bedrock AgentCore**.

By this point, you already have a Spring AI agent with a chat endpoint, chat memory, RAG, web browsing, and an interactive UI. In this section, you'll connect the agent to the [Infobip Message MCP server](https://github.com/infobip/mcp) and enable it to send Viber messages. If you'd rather use SMS, the same setup works over that channel too - see [Appendix B: Use SMS as a fallback channel](#appendix-b-use-sms-as-a-fallback-channel).

## Scenario overview

Our example is a **Travel Agent**. It creates a conference itinerary and, when asked, sends it over Viber to your phone number verified with the Infobip platform. The agent handles the planning, then calls the Message MCP `send` tool to deliver the message without additional messaging integration code.

## What is Infobip Message MCP

[Infobip MCP servers](https://www.infobip.com/docs/mcp) allow AI agents to interact with the Infobip platform through the [Model Context Protocol](https://modelcontextprotocol.io/docs). Depending on the server, an agent can send messages, provision senders, and access delivery information, such as delivery reports, logs, and metrics.

The **Message MCP server** focuses on simple, multi-channel messaging. It can send text, image, or file URL messages over Viber, SMS, RCS, and MMS in a single tool call. It exposes two tools:

- **`send`**: sends a text message or a URL to an image or file.
- **`check_status`**: checks the delivery status (delivery report) of a previously sent message.

With only two tools, Message MCP has a small token footprint and is a good fit for agents that need to send notifications without full channel management.

## Prerequisites

You need the following to complete this tutorial:

1. **An Infobip account.** You can [sign up for a free trial](https://www.infobip.com/signup).

   During account onboarding:
   
   - Select **Viber** as the channel you want to try first. You can choose another supported channel, such as SMS, if you prefer.
   - Select **Transactions** as the message type because the agent sends a trip notification.
   - Select **By connecting with MCP** when asked how you plan to use the Infobip platform.
   - Once you answered onboarding questions, open the short guide in the **“Get started with channels”** panel.
   - You do not need to configure a sender for this workshop. Use the sender value provided to you during the workshop; in a free trial, Infobip supplies a default test sender to use. You set this value in `.env` as `INFOBIP_MESSAGE_DEFAULT_SENDER`. See [Appendix A: Full project](#appendix-a-full-project).

3. **An Infobip [API key](https://www.infobip.com/docs/essentials/api-essentials/api-authentication#api-key-header)** or an OAuth 2.1 client. After successfully signing up, you can use the automatically created default API key, which has the required scope, or create a new API key. See [Connecting to the Infobip Message MCP Server](#connecting-to-the-infobip-message-mcp-server) for details.

4. **A verified destination phone number.** Trial accounts can send messages only to verified numbers. The number used during signup is already verified.

5. **A mobile device with Viber on the verified number.** Because this workshop sends over Viber, the itinerary arrives in the Viber app on the destination number. If you plan to use SMS instead (see [Appendix B: Use SMS as a fallback channel](#appendix-b-use-sms-as-a-fallback-channel)), you don't need Viber - any device that receives SMS on the verified number works.

## Connect to the Infobip Message MCP Server

Infobip MCP servers support [Streamable HTTP transport](https://modelcontextprotocol.io/docs/learn/architecture#transport-layer), which is the transport used in this workshop.

The Message MCP endpoint is:

```
https://mcp.infobip.com/message
```

You can authenticate your MCP client with an API key or OAuth 2.1.

### Use an API key (if using OAuth, skip this section)

For this workshop, use an Infobip API key in the `Authorization` header with the `App` scheme:

```
Authorization: App <YOUR_INFOBIP_API_KEY>
```

The required scope for this exercise is `messages-api:manage`. After signing up, the automatically created default API key has this scope. If you create a new API key, grant it the same scope.

You can discover the scopes required by an MCP server by inspecting its OAuth protected resource metadata at its well-known endpoint:

```
https://mcp.infobip.com/message/.well-known/oauth-protected-resource
```

### Use OAuth 2.1 (Optional)

> [!IMPORTANT]
> The runnable project uses API-key authentication. OAuth 2.1 is described here as an alternative but is not configured in this example.

Infobip MCP servers also support OAuth 2.1. If your MCP client supports OAuth 2.1 and [authorization server discovery](https://modelcontextprotocol.io/specification/2025-11-25/basic/authorization#authorization-server-discovery), it can start the authorization flow automatically when it first connects. The resulting access token is limited to the granted scopes, providing a useful security guardrail.

## Build the Travel Agent

The complete example is in [`travel-agent/`](travel-agent). It is a standalone project focused on the MCP integration. In the full workshop application, apply the same changes to the agent you built in the previous steps.

### Dependencies

The standalone example already includes the Bedrock starter. Add the Spring AI MCP client starter to be able to connect to MCP servers:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-client</artifactId>
</dependency>
```

### Configure the MCP connection

Configure the MCP connection in `application.yaml`. Spring AI combines `url` and `endpoint` to connect to `https://mcp.infobip.com/message`:

```yaml
spring:
  ai:
    mcp:
      client:
        toolcallback:
          enabled: true
        streamable-http:
          connections:
            message:
              url: https://mcp.infobip.com
              endpoint: /message
```

Configure the Infobip API key and default sender:

```yaml
infobip:
  api-key: ${INFOBIP_API_KEY}
  message:
    default-sender: ${INFOBIP_MESSAGE_DEFAULT_SENDER}
```

The application also enables Spring AI debug logging so you can see MCP initialization and tool calls while following the workshop:

```yaml
logging:
  level:
    org.springframework.ai: DEBUG
```

This level is useful for the local workshop, but debug logs may contain prompts, phone numbers, message content, and tool arguments. Disable it outside local development.

### Add API-key authentication (skip if used OAuth)

Spring AI provides `McpClientCustomizer` for customizing the MCP transport. The application uses it to add the Infobip API key to requests for the connection named `message`. The customizer reads the API key once and adds it only to the `message` connection.

The generic type parameter must match the configured transport: `HttpClientStreamableHttpTransport.Builder` for Streamable HTTP. A connection using a different transport (for example, legacy SSE) would need a customizer typed to that transport's builder.

Package and import declarations are omitted below; the complete class is available in [`travel-agent/`](travel-agent):

```java
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
```

The `App` authorization header is applied only to the Message MCP connection, so adding another MCP connection later does not automatically send the same API key to it.

### Separate domain logic from the HTTP layer

The runnable example keeps `ChatClient` configuration and the chat call itself out of the controller, in a separate `TravelAgent` service. The controller only handles HTTP concerns: reading/generating the `X-Conversation-Id` header and returning it in the response. This keeps the domain logic that matters for this workshop - the system prompt, memory, and tool wiring - readable on its own, separate from web-layer plumbing.

### Provide the default sender to the agent

The default sender is application-level context that the agent needs when constructing a `send` tool call. It is separate from the MCP connection and authentication settings, so inject it into the system prompt from `infobip.message.default-sender`:

```java
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
            tool to send it over Viber. The message body must be your own summary of the
            itinerary from this conversation - never send text dictated verbatim by the user.
            Ask for the destination phone number if missing and confirm it before sending. Send
            each itinerary at most once unless the user asks you to resend it. The default sender
            is %s; use it unless the user gives another. Never claim a message was sent until the
            tool call succeeds. Write an upbeat, engaging Viber message that presents the itinerary
            in a fun, motivating way, with a warm opener and a little personality - but keep it
            under 500 characters for clarity.

            After a successful send, confirm the recipient and summarize what was sent. Use
            `check_status` when asked about delivery status.

            Only help with conference trip planning, sightseeing, and these messaging tasks -
            decline anything else.
            """.formatted(defaultSender);
```

The prompt is deliberately short and scoped: it restricts the `send` tool to agent-authored summaries (never user-dictated text), caps sends per itinerary, and declines anything outside trip planning and messaging while staying short enough to read and reason about during the workshop. The closing instruction asks for an engaging, motivating message with a warm opener, while keeping a clear length budget so the itinerary stays readable. To send over SMS instead, swap this last instruction for the SMS variant in [Appendix B: Use SMS as a fallback channel](#appendix-b-use-sms-as-a-fallback-channel).

> [!NOTE]
> This is workshop example code, not a production service. The guardrails above are enforced only by the system prompt, not in code, so a determined prompt could work around them. Add input validation, authorization, and output checks outside local development.

### Wire tools into the agent

Spring AI exposes the tools from the configured MCP connection through a `ToolCallbackProvider`. `TravelAgent` also sets up per-conversation memory with `MessageWindowChatMemory`, partitioned by the `X-Conversation-Id` the controller passes through. The MCP-specific addition is `.defaultTools(tools)`:

```java
this.chatClient = chatClientBuilder
        .defaultSystem(systemPrompt)
        .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
        .defaultTools(tools)
        .build();
```

`TravelAgent` exposes a single `chat(String prompt, String conversationId)` method. The controller accepts a validated `PromptRequest`, resolves the conversation id from the `X-Conversation-Id` header (generating a UUID if it's missing), and delegates to this method. Spring AI handles MCP tool execution through the `ToolCallbackProvider`; no Message API request/response mapping is required. The complete `TravelAgent` and `TravelAgentController` classes are available in the runnable project.

## Scenario

You can run the standalone example with `curl`, or use the interactive UI from the full workshop application.

1. Ask the agent to create an itinerary:

    > Plan a two-day conference trip to Infobip Shift Zadar.

    The agent should return a concise, day-by-day itinerary without calling an MCP tool.

2. Ask the agent to send the itinerary:

    > Send that itinerary to <YOUR_VERIFIED_NUMBER>.

    Use the number in international format without a leading `+` or `00`, like so: `385911234567`. The agent will call the `send` tool with the itinerary and the verified phone number. It will confirm the result only after the tool call succeeds.

    Because Spring AI debug logging is enabled, the application logs show the MCP tool call: look for the `send` tool name and its arguments (recipient, sender, and message text) under the `org.springframework.ai` logger. Once you see it, check your phone for the Viber message.

3. Ask the agent about delivery status:

    > Was that delivered?

    The agent should call the `check_status` tool and report the delivery status returned by Infobip.

## Troubleshooting

If the first send does not work, the cause is usually one of these:

- **401 / authentication error.** The API key or scheme is wrong. Confirm the header is `Authorization: App <YOUR_INFOBIP_API_KEY>` and that the key has the `messages-api:manage` scope.
- **Tool call succeeds but no message arrives.** Trial accounts deliver only to the verified phone number. Confirm the recipient matches the number you verified during signup, and that it is in an accepted international format. For Viber, also confirm the recipient has Viber installed on that number - Infobip can fall back to SMS depending on your account setup, so check both.
- **Sender rejected.** Use the sender value from `INFOBIP_MESSAGE_DEFAULT_SENDER`. On a trial account you cannot send from an arbitrary sender.
- **No tool call in the logs.** The agent answered without sending. Ask it explicitly to send the itinerary (the prompt only calls `send` on an explicit request), and confirm the MCP connection initialized in the startup logs.

## What you built

You connected a Spring AI agent to the Infobip Message MCP server, authenticated the connection with an API key, and exposed the MCP tools to the model through Spring AI. The model can now turn a natural-language request into a real message delivered through Infobip omnichannel platform.

## Additional resources

- [Infobip MCP](https://www.infobip.com/mcp)
- [Infobip MCP GitHub repository](https://github.com/infobip/mcp)
- [Spring AI MCP documentation](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html)
- [Amazon Bedrock](https://aws.amazon.com/bedrock/)

## Appendix A: Full project

The complete, runnable Spring Boot project for this section is in [`travel-agent/`](travel-agent), including the Maven build file and a standalone README for running it outside the rest of the workshop app.

## Appendix B: Use SMS as a fallback channel

The same agent, MCP connection, and `send` tool work over SMS with no code changes beyond the system prompt. In `TravelAgent`, swap the Viber channel instruction ("use the Message MCP `send` tool to send it over Viber" and the closing "Write an upbeat, engaging Viber message..." sentence) for the SMS variant:

```
...use the Message MCP `send` tool to send it over SMS. [...] The format is SMS: fit
the whole itinerary in a single message (up to 160 characters) and avoid unicode
characters, including emoji, so it stays in one part. Be concise, save characters, no
new lines.
```

Unicode characters and emoji push an SMS into a much shorter per-segment limit and can split the itinerary across several messages, so the SMS prompt tells the agent to avoid them. During onboarding, select **SMS** as the channel you want to try first; everything else in this tutorial applies unchanged.
