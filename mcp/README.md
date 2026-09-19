# Knowledge MCP server

Lets Claude or ChatGPT research something, write it up, and publish it straight into
the app's knowledge base - with the schema enforced on the way in, so a malformed
document is rejected instead of shipped to the phone.

```
assistant ──MCP──▶ this server ──▶ knowledge/*.json ──build──▶ knowledge-base.json
                                                                      │
                                                              git push │
                                                                      ▼
                                              phone: Settings ▸ Knowledge base ▸ Sync
```

## Install

```bash
cd mcp && npm install
```

`validate`, `build` and `list` work without installing anything; only the MCP server
itself needs the SDK.

## Tools

| Tool | What it does |
| --- | --- |
| `list_knowledge` | What is already published, per kind |
| `read_knowledge` | One document in full |
| `publish_exercise` | Create or replace an exercise: cues, mistakes, evidence, demo keyframes |
| `publish_food` | Create or replace a food: the portion as eaten, and its macros |
| `publish_plan` | Create or replace a training week |
| `check_knowledge` | Validate a draft, or the whole base, without writing |
| `build_bundle` | Validate everything and write `knowledge-base.json` |

Validation is not a formality. A food whose calories contradict its macros by more than
about a third is refused, a demo missing a joint in its first frame is refused, and a
plan pointing at an exercise that does not exist is refused.

## Connecting Claude

Claude Desktop - add to `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "my-fitness": {
      "command": "node",
      "args": ["/absolute/path/to/my-fitness/mcp/src/server.js"]
    }
  }
}
```

Claude Code: `claude mcp add my-fitness -- node /absolute/path/to/my-fitness/mcp/src/server.js`

Then ask for what you want in plain words:

> Add an incline dumbbell curl to my knowledge base - cues, the usual mistakes, what the
> evidence says about stretch-position training, and a demo animation. Then build the bundle.

## Connecting ChatGPT

ChatGPT's connectors speak MCP over HTTP rather than stdio, so put a bridge in front:

```bash
npx -y supergateway --stdio "node /absolute/path/to/mcp/src/server.js" --port 8000
```

and point a custom connector at `http://<host>:8000/sse`. Anything reachable from the
public internet needs auth in front of it - this server trusts whoever can talk to it.

## Publishing to Unity Wiki, or anywhere else

The app syncs from a URL, so the bundle can live wherever you can publish a file:

- **GitHub raw** - commit `knowledge/knowledge-base.json` and point the app at
  `https://raw.githubusercontent.com/ug911/my-fitness/<branch>/knowledge/knowledge-base.json`
- **Unity Wiki** - publish the bundle as a document and use its public URL; the app
  accepts a JSON body or an HTML page with the JSON in a `<pre>` or `<code>` block,
  which is what a wiki page of a JSON document looks like

The app ships the bundle as an asset too, so a fresh install has the full knowledge base
before it has ever seen the network.

## House rules for whoever writes here

- Never invent a citation. Leave `source` out rather than guess a paper.
- `confidence` is `strong` only for things the field broadly agrees on.
- Food numbers are household estimates for how *this* person eats - a katori, a plate,
  two eggs - and are marked `estimate` rather than dressed up as lab values.
- None of it is medical advice, and the app says so.
