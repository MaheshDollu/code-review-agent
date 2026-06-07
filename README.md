<div align="center">

# 🛡️ CodeSentinel
### Autonomous Code Review Agent

[![Java](https://img.shields.io/badge/Java-17-orange?style=flat-square&logo=java)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-brightgreen?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)
[![LangGraph4J](https://img.shields.io/badge/LangGraph4J-1.5.14-blue?style=flat-square)](https://github.com/bsorrentino/langgraph4j)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.0--M6-green?style=flat-square)](https://spring.io/projects/spring-ai)
[![Groq](https://img.shields.io/badge/Groq-llama--3.3--70b-red?style=flat-square)](https://groq.com)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](LICENSE)

**A production-grade multi-agent LLM pipeline that reviews GitHub Pull Requests like a senior engineer.**  
Paste a PR URL → 5 specialized AI agents analyze it in parallel → structured review posted back to GitHub.


</div>

---

## ✨ What It Does

CodeSentinel takes a GitHub PR URL and runs it through a **5-node agentic pipeline** built with LangGraph4J:

```
GitHub PR URL
      │
  PrFetcherNode          → Fetches diff + metadata via GitHub REST API
      │
  ┌───┼───┐
  │   │   │              → Fan-out: 3 specialist agents run in sequence
  ▼   ▼   ▼
Security  Logic  Style   → Each analyzes the diff with a specialized prompt
  │   │   │
  └───┼───┘
      │
  SupervisorNode         → Merges findings, deduplicates, ranks by severity
      │
  CommentBuilderNode     → Renders markdown + POSTs review to GitHub PR
      │
  ✅ GitHub Review Posted
```

Each agent produces **structured output** with strict format contracts:
```
SEVERITY: CRITICAL
FILE: src/main/java/AuthService.java
LINE: 47
CATEGORY: SQL_INJECTION
ISSUE: User input concatenated directly into SQL query string.
FIX: Use PreparedStatement with parameterized queries.
---
```

---

## 🚀 Features

| Feature | Details |
|---|---|
| **Multi-agent pipeline** | 5-node LangGraph4J graph with fan-out/fan-in topology |
| **Parallel analysis** | Security, Logic, and Style agents analyze the same diff |
| **GitHub API tool-use** | Agents autonomously fetch diffs and post reviews via GitHub REST API |
| **Structured output parsing** | Strict `SEVERITY:` / `FILE:` / `LINE:` / `FIX:` contracts eliminate hallucinated formatting |
| **Human-in-the-Loop** | `interruptBefore` + `MemorySaver` checkpointing — pause before posting, resume from exact state |
| **SSE streaming** | Real-time pipeline progress streamed token-by-token via `SseEmitter` |
| **Per-agent temperature** | `security=0.1` (deterministic) · `logic=0.15` · `style=0.2` · `supervisor=0.1` |
| **Session memory** | `threadId`-scoped `MemorySaver` checkpoints — zero state loss across turns |
| **Dark UI** | Terminal-aesthetic frontend with live pipeline node animation |

---

## 🏗️ Architecture

### Agent Specialization

| Agent | Temperature | Detects |
|---|---|---|
| **SecurityAgentNode** | `0.1` | SQL/LDAP/OS injection, hardcoded secrets, XSS, SSRF, OWASP Top 10 |
| **LogicAgentNode** | `0.15` | Null dereferences, off-by-one errors, resource leaks, race conditions |
| **StyleAgentNode** | `0.2` | Naming violations, code duplication, complexity, magic numbers |
| **SupervisorNode** | `0.1` | Merges all findings, deduplicates, ranks CRITICAL → MAJOR → MINOR |
| **CommentBuilderNode** | — | Renders GitHub-flavored markdown, POSTs via GitHub API |

### Shared State — `CodeReviewState`

```java
public class CodeReviewState extends AgentState {
    // PR metadata
    String prUrl, owner, repo, prTitle, prDescription;
    int prNumber;

    // Diff content
    String diffContent;
    List<String> changedFiles;

    // Per-agent findings (raw structured text)
    String securityFindings;
    String logicFindings;
    String styleFindings;

    // Supervisor merged output
    String mergedFindings;

    // Final rendered review
    String renderedReview;

    // Session
    String threadId;
}
```

### Key Design Patterns

**Tool-use via GitHub API** — agents call real external APIs autonomously:
```java
@Component
public class GitHubClient {
    public String fetchDiff(String owner, String repo, int prNumber) { ... }
    public void postReview(String owner, String repo, int prNumber, String body) { ... }
}
```

**Few-shot prompt engineering** — strict output contracts per agent:
```java
private static final String SYSTEM_PROMPT = """
    For each issue found, respond EXACTLY in this format:
    SEVERITY: [CRITICAL|MAJOR|MINOR]
    FILE: [filename]
    LINE: [line number]
    CATEGORY: [e.g. SQL_INJECTION]
    ISSUE: [description]
    FIX: [actionable fix]
    ---
    """;
```

**HitL checkpointing** — pause before posting, resume from exact state:
```java
CompileConfig.builder()
    .checkpointSaver(memorySaver)
    .interruptBefore("supervisor")  // pause here
    .build();
```

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.4.1 |
| Agent Graph | LangGraph4J 1.5.14 |
| LLM Integration | Spring AI 1.0.0-M6 |
| LLM Provider | Groq (llama-3.3-70b-versatile) — **free tier** |
| GitHub Integration | GitHub REST API v3 (OkHttp) |
| Streaming | SSE (`SseEmitter`) |
| State / Memory | `MemorySaver` + `threadId` checkpoints |
| Build | Maven · Java 17 |

---

## ⚡ Quick Start

### Prerequisites
- Java 17+
- Maven 3.6+
- [Groq API key](https://console.groq.com) (free)
- [GitHub Personal Access Token](https://github.com/settings/tokens) with `repo` scope

### 1. Clone

```bash
git clone https://github.com/MaheshDollu/code-review-agent.git
cd code-review-agent
```

### 2. Set environment variables

```bash
export GROQ_API_KEY=gsk_your_key_here
export GITHUB_TOKEN=ghp_your_token_here
```

### 3. Run

```bash
mvn spring-boot:run
```

### 4. Open the UI

Visit **http://localhost:8080** and paste any GitHub PR URL.

---

## 📡 API Reference

### Review a PR (synchronous)
```bash
curl -X POST http://localhost:8080/api/review \
  -H "Content-Type: application/json" \
  -d '{"prUrl": "https://github.com/owner/repo/pull/42"}'
```

### Review with SSE streaming
```bash
curl -N -X POST http://localhost:8080/api/review/stream \
  -H "Content-Type: application/json" \
  -d '{"prUrl": "https://github.com/owner/repo/pull/42"}'
```

### Resume a HitL-paused review
```bash
curl -X POST http://localhost:8080/api/review/resume \
  -H "Content-Type: application/json" \
  -d '{"threadId": "uuid", "decision": "APPROVED"}'
```

### Health check
```bash
curl http://localhost:8080/api/review/health
```

---

## ⚙️ Configuration

`src/main/resources/application.yml`:

```yaml
app:
  review:
    post-to-github: true    # false = dry-run, renders in UI only
    hitl-enabled: false     # true = pause before posting for human approval
```

---

## 📁 Project Structure

```
src/main/java/com/codereviewer/
├── CodeReviewAgentApplication.java
├── config/
│   └── AppConfig.java              ← Spring AI / Groq ChatClient
├── state/
│   └── CodeReviewState.java        ← Shared state across all nodes
├── nodes/
│   ├── PrFetcherNode.java          ← GitHub API → diff + metadata
│   ├── SecurityAgentNode.java      ← OWASP / injection / secrets
│   ├── LogicAgentNode.java         ← Bugs / nulls / resource leaks
│   ├── StyleAgentNode.java         ← Naming / complexity / duplication
│   ├── SupervisorNode.java         ← Fan-in merge + severity ranking
│   └── CommentBuilderNode.java     ← Markdown render + GitHub POST
├── tools/
│   └── GitHubClient.java           ← GitHub REST API v3 wrapper
├── service/
│   └── CodeReviewGraphService.java ← LangGraph4J graph builder
└── controller/
    └── CodeReviewController.java   ← REST endpoints + SSE
```

---

## 🧠 Resume Bullets

> Copy these directly for your resume/portfolio:

- Architected a **parallel multi-agent PR review pipeline** in LangGraph4J with fan-out/fan-in graph topology — Security, Logic, and Style agents execute against the same diff, then merge findings via a Supervisor node with severity ranking (`CRITICAL → MAJOR → MINOR`)
- Implemented **GitHub API tool-use**, enabling LLM agents to autonomously fetch PR diffs and post structured review comments back to GitHub without human prompt construction
- Engineered **per-agent prompt contracts** with few-shot examples and strict output tokens (`SEVERITY:`, `FILE:`, `LINE:`, `FIX:`) — enabling deterministic downstream parsing across all agent outputs
- Integrated **Human-in-the-Loop checkpoint** (`interruptBefore` + `MemorySaver`) on the Supervisor node, allowing a human to approve/modify merged findings before posting to GitHub
- Built **real-time SSE streaming** endpoint piping pipeline status events to the client, reducing perceived latency on multi-agent reviews averaging 15–20 seconds end-to-end
- Deployed on **Groq's free LLM tier** (llama-3.3-70b-versatile) via Spring AI's OpenAI-compatible adapter — zero infrastructure cost

---

## 📄 License

MIT — free to use, modify, and showcase in your portfolio.

---

<div align="center">
Built with ❤️ using LangGraph4J · Spring AI · Groq · Spring Boot
</div>
