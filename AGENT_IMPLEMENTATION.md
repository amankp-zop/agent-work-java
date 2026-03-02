# Multi-Agent Supervisor Architecture — Blueprint

> **Purpose**: This document is a complete, self-contained blueprint for recreating a multi-agent supervisor system using **Java 17**, **Spring Boot**, **LangGraph4j**, and **LangChain4j**. Hand this file to an AI coding agent and it should be able to scaffold the entire project in a new repository.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Technology Stack & Dependencies](#technology-stack--dependencies)
3. [Project Structure](#project-structure)
4. [Component-by-Component Implementation Guide](#component-by-component-implementation-guide)
   - [Step 1: Shared State](#step-1-shared-state--supervisorstate)
   - [Step 2: Supervisor Agent (Router)](#step-2-supervisor-agent-router)
   - [Step 3: Worker Agents](#step-3-worker-agents)
   - [Step 4: Tools / External Services](#step-4-tools--external-services)
   - [Step 5: Graph Configuration (Wiring)](#step-5-graph-configuration-wiring)
   - [Step 6: Service Layer](#step-6-service-layer)
   - [Step 7: REST Controller](#step-7-rest-controller)
   - [Step 8: Application Properties](#step-8-application-properties)
5. [Graph Topology Diagram](#graph-topology-diagram)
6. [Key Design Decisions](#key-design-decisions)
7. [How to Extend](#how-to-extend)

---

## Architecture Overview

This system implements the **Supervisor pattern** for multi-agent orchestration:

- A **Supervisor Agent** acts as the router/manager. It uses an LLM to decide which worker should handle the current task.
- **Worker Agents** (Researcher, Coder, Reviewer) each have a specialized role and their own system prompt.
- All agents communicate through a **shared state** object — no direct agent-to-agent calls.
- The graph is built and compiled using **LangGraph4j**, which manages the execution flow, conditional routing, and state propagation.

```
User Request → REST API → AgentService → CompiledGraph.stream(state)
  → Supervisor decides next worker → Worker(s) execute → Supervisor re-evaluates → ... → FINISH
```

---

## Technology Stack & Dependencies

| Component          | Library / Version                     | Purpose                                  |
|--------------------|---------------------------------------|------------------------------------------|
| Language           | Java 17                              | Core language                            |
| Framework          | Spring Boot 3.5.x                    | Web framework, DI, configuration         |
| Graph Engine       | LangGraph4j 1.8.2 (BOM)             | Agent graph: nodes, edges, state mgmt    |
| LLM Abstraction    | LangChain4j 1.0.0-beta3             | Chat model interface, message types      |
| LLM Provider       | Groq (OpenAI-compatible endpoint)    | Free LLM inference (Llama 3.3 70B)      |
| Web Search         | Tavily API                           | AI-optimized web search for research     |
| Build Tool         | Maven                                | Dependency management, build             |
| Utility            | Lombok                               | Boilerplate reduction                    |
| Database           | PostgreSQL + Spring Data JPA         | Persistence (optional for agent layer)   |

### Maven Dependencies (pom.xml)

```xml
<properties>
    <java.version>17</java.version>
    <langgraph4j.version>1.8.2</langgraph4j.version>
    <langchain4j.version>1.0.0-beta3</langchain4j.version>
</properties>

<!-- LangGraph4j BOM: manages all langgraph4j module versions -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.bsc.langgraph4j</groupId>
            <artifactId>langgraph4j-bom</artifactId>
            <version>${langgraph4j.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- LangGraph4j Core: StateGraph, AgentState, Nodes, Edges -->
    <dependency>
        <groupId>org.bsc.langgraph4j</groupId>
        <artifactId>langgraph4j-core</artifactId>
    </dependency>

    <!-- LangGraph4j ↔ LangChain4j glue (message types, agent executor) -->
    <dependency>
        <groupId>org.bsc.langgraph4j</groupId>
        <artifactId>langgraph4j-langchain4j</artifactId>
    </dependency>

    <!-- LangChain4j OpenAI-compatible model (works with Groq by changing base URL) -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-open-ai</artifactId>
        <version>${langchain4j.version}</version>
    </dependency>

    <!-- LangChain4j core abstractions -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-core</artifactId>
        <version>${langchain4j.version}</version>
    </dependency>

    <!-- Spring Boot starters -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- Lombok (optional, for boilerplate reduction) -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

> **Note**: Configure the Maven Compiler Plugin with Lombok annotation processor path for proper compilation.

---

## Project Structure

```
src/main/java/com/<your-package>/agent/
├── config/
│   └── AgentGraphConfig.java       # Graph wiring, LLM beans, compilation
├── controller/
│   └── AgentController.java        # REST API endpoint
├── service/
│   └── AgentService.java           # Bridge between Spring and the graph
├── state/
│   └── SupervisorState.java        # Shared state definition
├── supervisor/
│   └── SupervisorAgent.java        # LLM-based routing agent
├── tools/
│   └── TavilySearchService.java    # Web search tool using Tavily API
└── workers/
    ├── CoderAgent.java             # Code generation worker
    ├── ResearcherAgent.java        # Web research + LLM synthesis worker
    └── ReviewerAgent.java          # Code/research review worker
```

---

## Component-by-Component Implementation Guide

### Step 1: Shared State — `SupervisorState`

**Package**: `agent.state`

The shared state is the memory that flows through the entire graph. All nodes read from and write to this object. No direct node-to-node communication happens.

**Key Concepts**:
- Extends `AgentState` from LangGraph4j.
- Defines a `SCHEMA` map that controls how each field behaves on updates.
- **Appender channel** (`Channels.appender()`): Values are **accumulated** into a list. Used for message history.
- **Base channel** (`Channels.base()`): Values are **overwritten**. Used for routing decisions and counters.

**Fields**:

| Field                | Type          | Channel   | Purpose                                          |
|----------------------|---------------|-----------|--------------------------------------------------|
| `messages`           | `List<String>`| Appender  | Accumulated chat history (all agent outputs)     |
| `next`               | `String`      | Base      | Supervisor's routing decision (worker name or "FINISH") |
| `review_count`       | `Integer`     | Base      | Tracks how many review cycles have occurred      |
| `last_review_feedback` | `String`    | Base      | Latest reviewer feedback (overwritten each cycle)|

**Implementation**:

```java
public class SupervisorState extends AgentState {

    public static final String MESSAGES = "messages";
    public static final String NEXT = "next";
    public static final String REVIEW_COUNT = "review_count";
    public static final String LAST_REVIEW_FEEDBACK = "last_review_feedback";

    public static final Map<String, Channel<?>> SCHEMA = Map.of(
        MESSAGES, Channels.appender(ArrayList::new),
        NEXT, Channels.base(() -> ""),
        REVIEW_COUNT, Channels.base(() -> 0),
        LAST_REVIEW_FEEDBACK, Channels.base(() -> "")
    );

    public SupervisorState(Map<String, Object> initData) {
        super(initData);
    }

    public List<String> messages() {
        return this.<List<String>>value(MESSAGES).orElse(List.of());
    }

    public String next() {
        return this.<String>value(NEXT).orElse("");
    }

    public int reviewCount() {
        return this.<Integer>value(REVIEW_COUNT).orElse(0);
    }

    public String lastReviewFeedback() {
        return this.<String>value(LAST_REVIEW_FEEDBACK).orElse("");
    }
}
```

**Why plain Strings instead of ChatMessage objects?**
LangGraph4j serializes state internally for checkpointing. Plain Strings serialize naturally. Each agent converts to/from `ChatMessage` objects internally when calling the LLM.

---

### Step 2: Supervisor Agent (Router)

**Package**: `agent.supervisor`

The Supervisor is the **only** node that makes routing decisions. It does NOT perform actual work — it reads the conversation state and decides which worker should go next.

**Implements**: `NodeAction<SupervisorState>`

**How it works**:
1. Reads all messages from state.
2. **Condenses** them before sending to the LLM (researcher/coder outputs get summarized to "[Agent] has provided results" to save tokens).
3. Sends a system prompt listing available workers + condensed context to the LLM.
4. The LLM responds with **exactly one word**: a worker name (e.g., `researcher`, `coder`) or `FINISH`.
5. Stores the decision in `state.next` via the return map.

**Critical detail — uses a cheaper/faster LLM for routing**: The supervisor uses a small, fast model (e.g., `llama-3.1-8b-instant`) because its task is simple classification. Worker agents use a larger model (e.g., `llama-3.3-70b-versatile`) for actual work. This saves cost and latency.

**System Prompt Template**:

```
You are a supervisor agent managing a team of specialized workers.
Available workers: {worker_names}
Your job is to decide which worker should act next based on the conversation,
or whether the task is complete.

Rules:
- "researcher" — Route here when the user needs information, analysis, or research.
- "coder"      — Route here when the user needs code, implementation, or technical solutions.
- "FINISH"     — Say this when the task is fully completed.

IMPORTANT: Respond with ONLY ONE WORD — either a worker name or "FINISH".
Do NOT include any explanation, punctuation, or extra text.
```

**Context Condensation Logic** (in the `apply()` method):
```
- Messages starting with "User: "      → passed as-is
- Messages starting with "[Researcher]" → condensed to "[Researcher] has provided research findings."
- Messages starting with "[Coder]"      → condensed to "[Coder] has provided a code solution."
- Messages starting with "[Reviewer]"   → condensed to "[Reviewer] has reviewed the work."
- Messages starting with "[Supervisor]" → skipped entirely
```

**Return value**: `Map.of(NEXT, routingDecision, MESSAGES, List.of("[Supervisor] Routing to: " + decision))`

---

### Step 3: Worker Agents

All workers implement `NodeAction<SupervisorState>`. Each receives the full state, extracts what it needs, calls the LLM with a role-specific system prompt, and returns its output to state.

#### 3a. ResearcherAgent

**Package**: `agent.workers`

**Purpose**: Performs web research using a 2-step process:
1. **Search** — Calls Tavily API to get web results.
2. **Synthesize** — Passes raw search results + original question to the LLM, which produces a structured research summary.

**Dependencies**: `TavilySearchService`, `ChatLanguageModel`

**System Prompt**:
```
You are a research analyst. Given a user's question and web search results,
synthesize a clear, well-organized research summary.

Rules:
- Be concise but thorough (3-5 key points)
- Cite information from the search results
- Highlight key findings and actionable insights
- If the search results don't cover the topic well, say so honestly
```

**Return value**: `Map.of(MESSAGES, List.of("[Researcher] 📚 " + summary))`

#### 3b. CoderAgent

**Package**: `agent.workers`

**Purpose**: Generates production-quality code using the LLM. Supports two modes:
- **First pass**: Fresh code generation based on user request + any research context.
- **Revision mode**: If `lastReviewFeedback` is non-empty, uses a revision-focused system prompt that addresses the reviewer's comments.

**Dependencies**: `ChatLanguageModel`

**System Prompt (first pass)**:
```
You are an expert software engineer. Generate clean, well-commented,
production-quality code based on the user's request.

Rules:
- Write complete, runnable code (not fragments)
- Include helpful comments explaining key logic
- Follow best practices and design patterns
- If research context is provided, use it to inform your solution
- Keep the code concise but correct
```

**System Prompt (revision mode)**:
```
You are an expert software engineer revising code based on review feedback.
A code reviewer has provided specific comments on your previous implementation.
You MUST address ALL review comments and produce an improved version.

Rules:
- Read the review feedback carefully and address every point
- Write complete, runnable code (not fragments)
- After the code, briefly explain what you changed and why
```

**Context assembly**:
- Extracts user query from messages (first `"User: "` prefixed message).
- Builds research context from `[Researcher]` messages.
- Appends `lastReviewFeedback` if present.

**Return value**: `Map.of(MESSAGES, List.of("[Coder] 🖥️ " + codeResult))`

#### 3c. ReviewerAgent

**Package**: `agent.workers`

**Purpose**: Reviews code/research output and provides actionable feedback. Responds with `"LGTM"` (Looks Good To Me) if the work meets production standards — this signal is used to exit the coder-reviewer loop.

**Dependencies**: `ChatLanguageModel`

**System Prompt**:
```
You are an expert reviewer of code and research reports. Generate clean, clear, concise
review for the code or research data you are provided.

REMEMBER DO NOT WRITE CODE YOURSELF, YOUR TASK IS JUST TO PROVIDE REVIEW COMMENTS.

Rules:
- Write clear, directive review comments (3 key points at max)
- Keep comments short, directive and actionable
- Follow production grade standard for code review
- Follow analyst level standard for research report review
- Provide actionable reviews with brief explanations

If the code fully meets the standard and no changes are needed, respond with
exactly "LGTM" (Looks Good To Me) and nothing else.
```

**Context building** (important!):
- Extracts research findings from `[Researcher]` messages.
- Keeps only the **latest** `[Coder]` output (overwrites on each loop, discards older code versions).
- This prevents context from growing unboundedly during coder-reviewer loops.

**Return value**:
```java
Map.of(
    MESSAGES, List.of("[Reviewer] 📒 " + reviewOutput),
    REVIEW_COUNT, state.reviewCount() + 1,
    LAST_REVIEW_FEEDBACK, reviewOutput
)
```

---

### Step 4: Tools / External Services

#### TavilySearchService

**Package**: `agent.tools`

**Purpose**: Wraps the Tavily Search REST API for web research.

**How it works**:
1. Sends a POST to `https://api.tavily.com/search` with query, search depth, and max results.
2. Uses `Authorization: Bearer <API_KEY>` header.
3. Parses JSON response to extract the AI-generated answer + top 3 source results.
4. Truncates each source content to 300 chars to keep state size manageable.

**Key details**:
- Uses Java's built-in `HttpClient` (no extra dependencies).
- 10s connect timeout, 15s request timeout.
- Returns a formatted string: summary + numbered sources with title, URL, content snippet.

**To replace Tavily**, implement any service that takes a `String query` and returns a `String` of search results. The `ResearcherAgent` will synthesize whatever you return.

---

### Step 5: Graph Configuration (Wiring)

**Package**: `agent.config`

This is a Spring `@Configuration` class that:
1. Creates LLM model beans (two separate models: one for routing, one for work).
2. Creates the TavilySearchService bean.
3. Instantiates all agent nodes.
4. Builds and compiles the `StateGraph`.

#### LLM Bean Configuration

```java
// Worker model — larger, more capable
@Bean
public ChatLanguageModel chatLanguageModel(
        @Value("${groq.api.key}") String apiKey,
        @Value("${groq.model}") String model,
        @Value("${groq.base.url}") String baseUrl) {
    return OpenAiChatModel.builder()
            .apiKey(apiKey)
            .modelName(model)        // e.g., llama-3.3-70b-versatile
            .baseUrl(baseUrl)        // e.g., https://api.groq.com/openai/v1
            .temperature(0.0)
            .build();
}

// Router model — smaller, faster, cheaper
@Bean
public ChatLanguageModel routingLanguageModel(
        @Value("${groq.api.key}") String apiKey,
        @Value("${groq.router.model}") String model,
        @Value("${groq.base.url}") String baseUrl) {
    return OpenAiChatModel.builder()
            .apiKey(apiKey)
            .modelName(model)        // e.g., llama-3.1-8b-instant
            .baseUrl(baseUrl)
            .temperature(0.0)
            .build();
}
```

#### Graph Building

```java
@Bean
public CompiledGraph<SupervisorState> compiledGraph(
        @Qualifier("routingLanguageModel") ChatLanguageModel routerModel,
        @Qualifier("chatLanguageModel") ChatLanguageModel chatModel,
        TavilySearchService tavilySearch) throws Exception {

    List<String> workerNames = List.of("researcher", "coder");

    var researcherAgent = new ResearcherAgent(tavilySearch, chatModel);
    var coderAgent      = new CoderAgent(chatModel);
    var supervisorAgent = new SupervisorAgent(routerModel, workerNames);
    var reviewerAgent   = new ReviewerAgent(chatModel);

    var graph = new StateGraph<>(SupervisorState.SCHEMA, SupervisorState::new)

        // === NODES ===
        .addNode("supervisor",  node_async(supervisorAgent))
        .addNode("researcher",  node_async(researcherAgent))
        .addNode("coder",       node_async(coderAgent))
        .addNode("reviewer",    node_async(reviewerAgent))

        // === EDGES ===

        // Entry point: every request starts at the supervisor
        .addEdge(START, "supervisor")

        // Supervisor → conditional routing based on state.next()
        .addConditionalEdges("supervisor",
            edge_async(state -> state.next()),
            Map.of(
                "researcher", "researcher",
                "coder",      "coder",
                "FINISH",     END
            ))

        // Researcher → always back to Supervisor for re-evaluation
        .addEdge("researcher", "supervisor")

        // Coder → always goes to Reviewer first
        .addEdge("coder", "reviewer")

        // Reviewer → conditional:
        //   - If LGTM or reviewCount >= 2 → back to Supervisor (task complete)
        //   - Otherwise → back to Coder for revision
        .addConditionalEdges("reviewer",
            edge_async(state -> {
                String feedback = state.lastReviewFeedback();
                if (state.reviewCount() >= 2 ||
                    (feedback != null && feedback.trim().equalsIgnoreCase("LGTM"))) {
                    return "supervisor";
                }
                return "coder";
            }),
            Map.of(
                "supervisor", "supervisor",
                "coder",      "coder"
            ));

    return graph.compile();
}
```

**Important imports for graph building**:
```java
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
```

---

### Step 6: Service Layer

**Package**: `agent.service`

The `AgentService` is a `@Service` that bridges Spring Boot with the LangGraph4j compiled graph.

```java
@Service
public class AgentService {

    private final CompiledGraph<SupervisorState> compiledGraph;

    public AgentService(CompiledGraph<SupervisorState> compiledGraph) {
        this.compiledGraph = compiledGraph;
    }

    public AgentResponse chat(String userMessage) {
        // Prepare initial state — user message is prefixed with "User: "
        Map<String, Object> inputs = Map.of(
            SupervisorState.MESSAGES, List.of("User: " + userMessage)
        );

        // Stream through the graph — each item is the state after a node executes
        var results = compiledGraph.stream(inputs);

        // Collect trace and final output
        StringBuilder fullTrace = new StringBuilder();
        String finalOutput = "";

        for (var nodeOutput : results) {
            var state = nodeOutput.state();
            var messages = state.messages();
            if (!messages.isEmpty()) {
                var lastMessage = messages.get(messages.size() - 1);
                finalOutput = lastMessage;
                fullTrace.append("📍 Node '").append(nodeOutput.node()).append("':\n");
                fullTrace.append("   ").append(lastMessage).append("\n\n");
            }
        }

        return new AgentResponse(finalOutput, fullTrace.toString());
    }

    public record AgentResponse(String result, String trace) {}
}
```

---

### Step 7: REST Controller

**Package**: `agent.controller`

```java
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/chat")
    public AgentService.AgentResponse chat(@RequestBody Map<String, String> request) {
        String message = request.getOrDefault("message", "");
        if (message.isBlank()) {
            return new AgentService.AgentResponse("Error: message cannot be empty", "");
        }
        return agentService.chat(message);
    }
}
```

**API Usage**:
```bash
POST /api/agent/chat
Content-Type: application/json

{ "message": "Write a Python function to merge two sorted arrays" }
```

**Response**:
```json
{
  "result": "[Coder] 🖥️ Here is the implementation...",
  "trace": "📍 Node 'supervisor': [Supervisor] Routing to: coder\n📍 Node 'coder': ...\n📍 Node 'reviewer': ...\n..."
}
```

---

### Step 8: Application Properties

```properties
# ==================== AI Agent (Groq - free tier) ====================
# Get your free key at https://console.groq.com
groq.api.key=YOUR_GROQ_API_KEY
groq.model=llama-3.3-70b-versatile           # Worker model (larger)
groq.router.model=llama-3.1-8b-instant       # Router model (smaller, faster)
groq.base.url=https://api.groq.com/openai/v1

# ==================== Tavily Search API (free tier) ====================
# Get your free key at https://app.tavily.com
tavily.api.key=YOUR_TAVILY_API_KEY
```

---

## Graph Topology Diagram

```
    START
      │
      ▼
  ┌──────────┐
  │Supervisor │◄─────────────────────────────────┐
  └──────────┘                                    │
      │                                            │
      │ (conditional edge: reads state.next)       │
      │                                            │
      ├── "researcher" ──►┌───────────┐            │
      │                   │Researcher │────────────┘  (always back to supervisor)
      │                   └───────────┘
      │
      ├── "coder" ──────►┌───────┐     ┌──────────┐
      │                  │ Coder │────►│ Reviewer  │
      │                  └───────┘     └──────────┘
      │                       ▲              │
      │                       │  (feedback)  │ (conditional edge)
      │                       └──────────────┤   reviewCount < 2
      │                                      │   AND not LGTM
      │                                      │
      │                                      │ LGTM or reviewCount >= 2
      │                                      ▼
      │                                 Back to Supervisor
      │
      └── "FINISH" ──────► END
```

---

## Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| **Plain Strings in state** | LangGraph4j serializes state for checkpointing. Strings serialize naturally. Convert to `ChatMessage` only inside each agent's LLM call. |
| **Two LLM models** | Routing is simple classification — a small 8B model works. Worker tasks need reasoning — use a larger 70B model. Saves cost and latency. |
| **Context condensation in Supervisor** | Full worker outputs can be thousands of tokens. The Supervisor only needs to know "researcher already answered" — not the full content. Pass summaries instead. |
| **Coder-Reviewer loop with safety limit** | The reviewer can send code back for revision, but max 2 review cycles to prevent infinite loops. Exit on LGTM or max count. |
| **Message prefixes** | All messages are prefixed with `[AgentName]` (e.g., `[Coder]`, `[Researcher]`). This enables agents to parse and filter message history by source. |
| **`NodeAction<State>` interface** | LangGraph4j pattern: each node is a function `State → Map<String, Object>`. The returned map contains state updates to be merged. |

---

## How to Extend

### Adding a New Worker Agent

1. **Create the class**: Implement `NodeAction<SupervisorState>` in `agent/workers/`.
2. **Add it to the graph**: In `AgentGraphConfig`, instantiate it and call `.addNode("new_worker", node_async(newAgent))`.
3. **Register it with the Supervisor**: Add `"new_worker"` to the `workerNames` list.
4. **Add routing edges**: Add the new worker name to the Supervisor's conditional edge map.
5. **Update the Supervisor's system prompt**: Add a rule describing when to route to the new worker.

### Swapping the LLM Provider

Since `OpenAiChatModel` works with any OpenAI-compatible API, just change:
- `groq.base.url` → your provider's URL
- `groq.model` → your provider's model name
- `groq.api.key` → your provider's API key

For non-OpenAI-compatible providers (e.g., AWS Bedrock, Google Vertex AI), swap the `langchain4j-open-ai` dependency for the appropriate LangChain4j integration (e.g., `langchain4j-bedrock`).

### Adding New Tools

1. Create a service class in `agent/tools/`.
2. Register it as a Spring `@Bean` in `AgentGraphConfig`.
3. Inject it into the relevant worker agent's constructor.
4. Use it inside the worker's `apply()` method.

### Adding State Fields

1. Add a new constant and channel to `SupervisorState.SCHEMA`.
2. Add a getter method.
3. Return the field in any agent's `apply()` return map to update it.
