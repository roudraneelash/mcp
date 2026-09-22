# hello-mcp-server

A minimal [Model Context Protocol](https://modelcontextprotocol.io) server built with **Java 17, Spring Boot 3.5 and Spring AI 1.1**, exposing a single `hello_world` tool over the **Streamable HTTP** transport so that **GitHub Copilot** (VS Code agent mode) can call it.

```
src/main/java/com/roudraneel/mcp/
  HelloMcpServerApplication.java   Spring Boot entry point + ToolCallbackProvider bean
  HelloWorldTools.java             @Tool hello_world(name)
src/main/resources/application.yml MCP server config (protocol: STREAMABLE, endpoint: /mcp)
.vscode/mcp.json                   Tells Copilot where the server lives
```

## Run

```bash
./gradlew bootRun          # server on http://localhost:8080/mcp
./gradlew test             # integration test: real MCP client -> initialize -> tools/list -> tools/call
```

## Use from GitHub Copilot

`.vscode/mcp.json` is already in the repo:

```json
{ "servers": { "hello-mcp-server": { "type": "http", "url": "http://localhost:8080/mcp" } } }
```

1. `./gradlew bootRun`
2. Open this folder in VS Code, open the Copilot Chat panel, switch to **Agent** mode.
3. Click the tools icon – `hello_world` should be listed (or run `MCP: List Servers` from the command palette and start `hello-mcp-server`).
4. Ask: *"use the hello_world tool to greet Roudraneel"*.

## Add more tools

Add a method annotated with `@Tool` to `HelloWorldTools` (or any bean passed to `MethodToolCallbackProvider`). Spring AI derives the JSON schema from the method parameters / `@ToolParam` annotations and registers it with the MCP server automatically.

---

## How MCP initialization works (and where the session id comes from)

MCP is JSON-RPC 2.0 over a transport. With **Streamable HTTP** there is a single endpoint (`/mcp` here):

| HTTP verb | Purpose |
|-----------|---------|
| `POST /mcp` | client -> server JSON-RPC request / notification. Response is either `application/json` or a `text/event-stream` (SSE) that carries the response and any server-initiated messages related to that request. |
| `GET /mcp` (Accept: text/event-stream) | optional long-lived SSE stream for server -> client notifications (e.g. `notifications/tools/list_changed`). |
| `DELETE /mcp` | client explicitly ends the session. |

### The handshake

```
Client                                              Server (Spring AI / MCP Java SDK)
  |                                                    |
  | POST /mcp  {"method":"initialize", params:{        |
  |   protocolVersion:"2025-06-18",                    |
  |   capabilities:{...}, clientInfo:{name,version}}}  |
  |--------------------------------------------------->|  1. validates the request, negotiates
  |                                                    |     protocol version
  |                                                    |  2. creates a new McpServerSession,
  |                                                    |     generates UUID session id
  | 200  Mcp-Session-Id: af35d023-...                  |
  |      {"result":{protocolVersion, capabilities:{    |
  |        tools:{listChanged:true}, logging:{}},      |
  |        serverInfo:{name:"hello-mcp-server",...}}}  |
  |<---------------------------------------------------|
  |                                                    |
  | POST /mcp  Mcp-Session-Id: af35d023-...            |
  |   {"method":"notifications/initialized"}           |
  |--------------------------------------------------->|  3. session state -> INITIALIZED
  | 202 Accepted                                       |     (no body: it's a notification)
  |<---------------------------------------------------|
  |                                                    |
  | POST /mcp  Mcp-Session-Id: af35d023-...            |
  |   {"id":2,"method":"tools/list"}                   |
  |--------------------------------------------------->|  4. normal operation
  | 200 text/event-stream  data:{"result":{tools:[..]}}|
  |<---------------------------------------------------|
  | POST /mcp  Mcp-Session-Id: ...  tools/call         |
  |--------------------------------------------------->|
  | 200 ... {"result":{content:[{type:"text",...}]}}   |
  |<---------------------------------------------------|
  |                                                    |
  | DELETE /mcp  Mcp-Session-Id: af35d023-...          |
  |--------------------------------------------------->|  5. session closed / removed
```

**Step 1 – `initialize` request.** The very first message a client sends. It carries the
`protocolVersion` the client speaks, the client's `capabilities` (e.g. `roots`, `sampling`, `elicitation`)
and `clientInfo`. Nothing else is accepted before this.

**Step 2 – `initialize` response.** The server answers with the protocol version it agreed on
(same version if supported, otherwise its latest), its own `capabilities` – this is where Copilot learns
that the server offers `tools` and will send `listChanged` notifications – and `serverInfo`
(`spring.ai.mcp.server.name/version` from `application.yml`).

**Step 3 – `notifications/initialized`.** A notification (no `id`, so no response, HTTP 202) telling the
server the client has processed the capabilities and is ready. Only after this may the client issue
`tools/list`, `tools/call`, etc. Before it, the server rejects normal requests (only `ping` and logging are allowed).

**Steps 4/5** are normal operation and teardown.

### The session id

* The **server** creates it while handling `initialize` (in the Java SDK: `WebMvcStreamableServerTransportProvider`
  asks `DefaultMcpStreamableServerSessionFactory` for a new `McpStreamableServerSession`, whose id is
  `UUID.randomUUID()`), and returns it in the
  **`Mcp-Session-Id` response header** – *not* in the JSON body. It is purely a transport-level concept:
  the stdio transport has no session id at all because one process == one session.
* The **client must echo it back** in the `Mcp-Session-Id` request header on **every** subsequent
  `POST`, `GET` and `DELETE`. This is how the server maps a stateless HTTP request to the right
  `McpServerSession` object, which holds: negotiated protocol version, client capabilities/info,
  the initialization state machine, in-flight request ids, and any open SSE streams.
* Missing header after initialization -> **400 Bad Request** (`"Session ID missing"`, as you can reproduce
  with curl below). Unknown/expired id -> **404 Not Found**, which tells a well-behaved client to start
  over with a fresh `initialize`.
* `DELETE /mcp` with the header terminates the session; the server also drops it on shutdown. If the server
  restarts, all ids are gone and Copilot will transparently re-initialize.

### Try it with curl

```bash
# 1. initialize -> note the Mcp-Session-Id response header
curl -i localhost:8080/mcp -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"curl","version":"1.0"}}}'

SID=<paste the id>

# 2. initialized notification -> 202
curl -i localhost:8080/mcp -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
  -H "Mcp-Session-Id: $SID" -d '{"jsonrpc":"2.0","method":"notifications/initialized"}'

# 3. list tools
curl localhost:8080/mcp -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
  -H "Mcp-Session-Id: $SID" -d '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'

# 4. call the tool
curl localhost:8080/mcp -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
  -H "Mcp-Session-Id: $SID" -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"hello_world","arguments":{"name":"Roudraneel"}}}'

# 5. no header -> 400 "Session ID missing"
curl -i localhost:8080/mcp -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":4,"method":"tools/list"}'

# 6. end the session
curl -i -X DELETE localhost:8080/mcp -H "Mcp-Session-Id: $SID"
```

### Where this lives in Spring AI

* `spring-ai-starter-mcp-server-webmvc` auto-configures `WebMvcStreamableServerTransportProvider`
  (because `spring.ai.mcp.server.protocol=STREAMABLE`) and registers a `RouterFunction` for
  `spring.ai.mcp.server.streamable-http.mcp-endpoint`.
* `McpServerAutoConfiguration` builds an `McpSyncServer` from that transport plus every
  `ToolCallbackProvider` / `ToolCallback` bean it finds – that is how `hello_world` gets advertised.
* The handshake and session state machine themselves are implemented in the MCP Java SDK
  (`io.modelcontextprotocol.sdk:mcp`), not in your code; you only supply tools/resources/prompts.
