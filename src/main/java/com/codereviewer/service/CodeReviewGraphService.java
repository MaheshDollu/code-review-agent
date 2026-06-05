package com.codereviewer.service;

import com.codereviewer.nodes.*;
import com.codereviewer.state.CodeReviewState;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * Builds and executes the LangGraph4J multi-agent pipeline.
 *
 * Graph topology (sequential with parallel agent calls inside SupervisorNode):
 *
 *   START → prFetcher → securityAgent ─┐
 *                     → logicAgent    ─┼→ supervisor → commentBuilder → END
 *                     → styleAgent   ─┘
 *
 * NOTE: LangGraph4J 1.5.x supports fan-out via multiple edges from one node.
 * All three agent nodes feed into supervisor which waits for all to complete.
 */
@Service
public class CodeReviewGraphService {

    private static final Logger log = LoggerFactory.getLogger(CodeReviewGraphService.class);

    static final String NODE_FETCHER    = "prFetcher";
    static final String NODE_SECURITY   = "securityAgent";
    static final String NODE_LOGIC      = "logicAgent";
    static final String NODE_STYLE      = "styleAgent";
    static final String NODE_SUPERVISOR = "supervisor";
    static final String NODE_COMMENT    = "commentBuilder";

    @Value("${app.review.hitl-enabled:false}")
    private boolean hitlEnabled;

    private final PrFetcherNode      prFetcherNode;
    private final SecurityAgentNode  securityAgentNode;
    private final LogicAgentNode     logicAgentNode;
    private final StyleAgentNode     styleAgentNode;
    private final SupervisorNode     supervisorNode;
    private final CommentBuilderNode commentBuilderNode;
    private final MemorySaver        memorySaver = new MemorySaver();

    public CodeReviewGraphService(
            PrFetcherNode prFetcherNode,
            SecurityAgentNode securityAgentNode,
            LogicAgentNode logicAgentNode,
            StyleAgentNode styleAgentNode,
            SupervisorNode supervisorNode,
            CommentBuilderNode commentBuilderNode) {
        this.prFetcherNode      = prFetcherNode;
        this.securityAgentNode  = securityAgentNode;
        this.logicAgentNode     = logicAgentNode;
        this.styleAgentNode     = styleAgentNode;
        this.supervisorNode     = supervisorNode;
        this.commentBuilderNode = commentBuilderNode;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public CodeReviewState runReview(String prUrl) throws Exception {
        String threadId = UUID.randomUUID().toString();
        log.info("Starting review | threadId={} | prUrl={}", threadId, prUrl);

        CompiledGraph<CodeReviewState> graph = buildGraph();
        RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();

        Map<String, Object> initial = Map.of(
                "prUrl",    prUrl,
                "threadId", threadId,
                "status",   "started"
        );

        CodeReviewState finalState = null;
        for (var output : graph.stream(initial, config)) {
            log.info("✓ Node complete: {}", output.node());
            finalState = output.state();
        }

        log.info("Pipeline complete | status={}", finalState != null ? finalState.status().orElse("?") : "null");
        return finalState;
    }

    public CodeReviewState resumeReview(String threadId, String decision) throws Exception {
        log.info("Resuming HitL | threadId={} | decision={}", threadId, decision);

        CompiledGraph<CodeReviewState> graph = buildGraph();
        RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();

        Map<String, Object> resumeData = Map.of("hitlDecision", decision);

        CodeReviewState finalState = null;
        for (var output : graph.stream(resumeData, config)) {
            log.info("✓ Node complete (resume): {}", output.node());
            finalState = output.state();
        }
        return finalState;
    }

    // ── Graph builder ─────────────────────────────────────────────────────────

    private CompiledGraph<CodeReviewState> buildGraph() throws Exception {

        /*
         * Sequential pipeline where:
         *  1. prFetcher fetches the diff
         *  2. securityAgent, logicAgent, styleAgent each run (sequential in graph
         *     but each reads from the same shared state populated by prFetcher)
         *  3. supervisor merges all three findings
         *  4. commentBuilder posts the review
         *
         * True parallel fan-out requires LangGraph4J's Send API (available in 1.5+).
         * For simplicity and reliability we chain them — the LLM calls are the
         * bottleneck anyway (Groq rate limits). Add parallel Send API as an upgrade.
         */
        StateGraph<CodeReviewState> builder = new StateGraph<>(CodeReviewState::new)
                .addNode(NODE_FETCHER,    node_async(prFetcherNode))
                .addNode(NODE_SECURITY,   node_async(securityAgentNode))
                .addNode(NODE_LOGIC,      node_async(logicAgentNode))
                .addNode(NODE_STYLE,      node_async(styleAgentNode))
                .addNode(NODE_SUPERVISOR, node_async(supervisorNode))
                .addNode(NODE_COMMENT,    node_async(commentBuilderNode))

                .addEdge(START,          NODE_FETCHER)
                .addEdge(NODE_FETCHER,   NODE_SECURITY)
                .addEdge(NODE_SECURITY,  NODE_LOGIC)
                .addEdge(NODE_LOGIC,     NODE_STYLE)
                .addEdge(NODE_STYLE,     NODE_SUPERVISOR)
                .addEdge(NODE_SUPERVISOR,NODE_COMMENT)
                .addEdge(NODE_COMMENT,   END);

        CompileConfig.Builder compileConfig = CompileConfig.builder()
                .checkpointSaver(memorySaver);

        if (hitlEnabled) {
            log.info("HitL enabled — graph will pause before supervisor");
            compileConfig.interruptBefore(NODE_SUPERVISOR);
        }

        return builder.compile(compileConfig.build());
    }
}
