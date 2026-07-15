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
 * Graph topology (single combined analysis call, replacing the former
 * 3-agent fan-out, to stay within Groq free-tier token rate limits):
 *
 *   START → prFetcher → combinedAnalysis → supervisor → commentBuilder → END
 */
@Service
public class CodeReviewGraphService {

    private static final Logger log = LoggerFactory.getLogger(CodeReviewGraphService.class);

    static final String NODE_FETCHER    = "prFetcher";
    static final String NODE_ANALYSIS   = "combinedAnalysis";
    static final String NODE_SUPERVISOR = "supervisor";
    static final String NODE_COMMENT    = "commentBuilder";

    @Value("${app.review.hitl-enabled:false}")
    private boolean hitlEnabled;

    private final PrFetcherNode        prFetcherNode;
    private final CombinedAnalysisNode combinedAnalysisNode;
    private final SupervisorNode       supervisorNode;
    private final CommentBuilderNode   commentBuilderNode;
    private final MemorySaver          memorySaver = new MemorySaver();

    public CodeReviewGraphService(
            PrFetcherNode prFetcherNode,
            CombinedAnalysisNode combinedAnalysisNode,
            SupervisorNode supervisorNode,
            CommentBuilderNode commentBuilderNode) {
        this.prFetcherNode        = prFetcherNode;
        this.combinedAnalysisNode = combinedAnalysisNode;
        this.supervisorNode       = supervisorNode;
        this.commentBuilderNode   = commentBuilderNode;
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

        StateGraph<CodeReviewState> builder = new StateGraph<>(CodeReviewState::new)
                .addNode(NODE_FETCHER,    node_async(prFetcherNode))
                .addNode(NODE_ANALYSIS,   node_async(combinedAnalysisNode))
                .addNode(NODE_SUPERVISOR, node_async(supervisorNode))
                .addNode(NODE_COMMENT,    node_async(commentBuilderNode))

                .addEdge(START,           NODE_FETCHER)
                .addEdge(NODE_FETCHER,    NODE_ANALYSIS)
                .addEdge(NODE_ANALYSIS,   NODE_SUPERVISOR)
                .addEdge(NODE_SUPERVISOR, NODE_COMMENT)
                .addEdge(NODE_COMMENT,    END);

        CompileConfig.Builder compileConfig = CompileConfig.builder()
                .checkpointSaver(memorySaver);

        if (hitlEnabled) {
            log.info("HitL enabled — graph will pause before supervisor");
            compileConfig.interruptBefore(NODE_SUPERVISOR);
        }

        return builder.compile(compileConfig.build());
    }
}
