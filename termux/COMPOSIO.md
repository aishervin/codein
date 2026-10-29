# Composio Connect for Codein

This optional integration lets Codein's Termux agent use Composio Connect MCP tools alongside its local tools.

## Setup

1. Pair Codein with the local Termux bridge and select a Termux provider model in the app. Composio actions are initiated through that selected model.
2. Create a consumer key for an AI client in Composio. It should be a consumer key (typically `ck_...`), not a platform/project API key.
3. In Codein, open the Termux Agent Room's Composio tab, enter the key in the masked field, and choose **Save key and prepare**. The bridge stores it under `~/.codein/keys/composio_consumer_api_key.json` with file permissions `0600`.
4. Enter a service name such as Gmail or GitHub and choose **Start connection**. Codein asks the selected model to start Composio's hosted authorization flow; open the returned link, approve access on the service, then return to Codein and choose **Check connection**.
5. After Composio confirms the account, ask the selected model to use that service. The available Composio tools are exposed to the model tool loop with a `composio__` prefix.

Composio Connect hosts the remote MCP endpoint, so Codein does not need its own public Composio gateway. Codein still needs the local Termux bridge for the selected model and local agent tools.

## Implementation

- Hosted endpoint: `https://connect.composio.dev/mcp`
- Authentication header: `x-consumer-api-key`
- MCP transport helper: `termux/composio_connect.py`
- Agent tool routing: `termux/server.py`
- Termux bootstrap downloads the helper into `~/.codein/composio_connect.py`.

The consumer key is entered in the masked Composio field, sent to the local bridge, and stored in its private key vault; do not send it through chat. Only connect the apps and permissions you need. Composio free-tier limits, premium tool billing, and third-party OAuth requirements still apply.

Official docs:
- https://docs.composio.dev/docs/composio-connect
- https://docs.composio.dev/reference/authenticating-to-composio
