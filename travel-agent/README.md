# Travel Agent

A Spring Boot application that uses Spring AI and Amazon Bedrock to plan conference business trips. It connects to the Infobip Message MCP server so the agent can send an itinerary by Viber, with SMS available as a fallback channel.

This project is the runnable example for the [Infobip Shift 2026 workshop appendix](../README.md), the MCP segment of the joint AWS and Infobip workshop.

## Prerequisites

- Java 25 or later
- An AWS account with access to Amazon Bedrock
- An Infobip account and an API key with the `messages-api:manage` scope
- A phone number verified with the Infobip platform (typically, this is done as part of the account onboarding process) 
- A mobile device with Viber on that verified number (not needed if you use the SMS fallback)
- The sender value provided for the workshop

## Configure the application

Copy the example environment file:

```bash
cp .env.example .env
```

Set the values in `.env`:

```dotenv
AWS_REGION=<YOUR_AWS_REGION>
AWS_ACCESS_KEY=<YOUR_AWS_ACCESS_KEY>
AWS_SECRET_KEY=<YOUR_AWS_SECRET_KEY>
AWS_MODEL_ID=<YOUR_BEDROCK_MODEL_ID>
INFOBIP_API_KEY=<YOUR_INFOBIP_API_KEY>
INFOBIP_MESSAGE_DEFAULT_SENDER=<YOUR_INFOBIP_SENDER>
```

The application uses these values to configure Amazon Bedrock and authenticate requests to the Infobip Message MCP server. Do not commit `.env` or any file containing real credentials.

For API-key scope details and OAuth 2.1 as an alternative, see [Connecting to the Infobip Message MCP Server](../README.md#connecting-to-the-infobip-message-mcp-server).

## Run the application

Load the environment variables and start Spring Boot:

```bash
set -a
source .env
set +a
./mvnw spring-boot:run
```

Spring AI debug logging is enabled in `src/main/resources/application.yaml`, so MCP connection and tool-call details are printed in the application logs.

## Try the travel agent

The chat endpoint accepts a JSON object containing a non-blank `prompt`.

Conversations are tracked with an `X-Conversation-Id` header. If you omit it, the
application generates a new one and returns it in the response headers; send that
same value back on subsequent requests to continue the conversation with memory intact.

First, ask the agent to create an itinerary. Capture the generated conversation ID
from the response headers:

```bash
curl -i -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"prompt":"Plan a two-day conference trip to Infobip Shift Zadar."}'
```

Then ask it to send the itinerary to a verified phone number, passing back the same
`X-Conversation-Id` so the agent remembers the itinerary it just created. Use the number
in international format without a leading `+` (for example, `385911234567`):

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -H "X-Conversation-Id: <CONVERSATION_ID_FROM_PREVIOUS_RESPONSE>" \
  -d '{"prompt":"Send that itinerary to <YOUR_VERIFIED_NUMBER>."}'
```

The agent should invoke the Message MCP `send` tool and confirm the result after the tool call succeeds.

## Build and test

The test suite mocks Amazon Bedrock and the Message MCP server with WireMock, so it's
hermetic - no `.env` file or real credentials needed to run it.

Run the test suite:

```bash
./mvnw test
```

Package the application (this runs the test suite first, as part of the normal Maven
build lifecycle):

```bash
./mvnw package
```
