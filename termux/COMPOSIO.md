# Composio Connect for Codein

This optional integration lets Codein's Termux agent use Composio Connect MCP tools alongside its local tools.

## Setup

1. Open [Composio](https://composio.dev/) and go to **For You → AI Clients**.
2. Create a consumer key for the AI client. It should be a consumer key (typically `ck_...`), not a platform/project API key.
3. Configure the key through the local MCP tool `composio_configure`. The bridge stores it under `~/.codein/keys/composio_consumer_api_key.json` and sets file permissions to `0600`.
4. Call `composio_tools_refresh` to discover tools. They are exposed to the model tool loop with a `composio__` prefix.
5. When Composio requests app authorization, complete its OAuth flow and refresh the tools.

## Implementation

- Endpoint: `https://connect.composio.dev/mcp`
- Authentication header: `x-consumer-api-key`
- MCP transport helper: `termux/composio_connect.py`
- Agent tool routing: `termux/server.py`
- Termux bootstrap downloads the helper into `~/.codein/composio_connect.py`.

The bridge stores the consumer key locally and does not add it to source code. However, passing a secret through a normal model conversation means it may enter that provider's context. Prefer a dedicated local-only key-entry screen in a future Android UI update. Only connect the apps and permissions you need. Composio free-tier limits, premium tool billing, and third-party OAuth requirements still apply.

Official docs:
- https://docs.composio.dev/docs/composio-connect
- https://docs.composio.dev/reference/authenticating-to-composio
