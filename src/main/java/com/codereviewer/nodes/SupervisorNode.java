package com.codereviewer.nodes;

import com.codereviewer.state.CodeReviewState;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Node 3 — Supervisor: passthrough / checkpoint node.
 *
 * Findings are already merged, deduplicated, and sorted by
 * CombinedAnalysisNode in a single LLM call. This node no longer
 * calls the LLM itself — it exists as a stable checkpoint for the
 * HitL interrupt (interruptBefore) and to keep the graph topology
 * unchanged for CommentBuilderNode downstream.
 */
@Component
public class SupervisorNode implements NodeAction<CodeReviewState> {

    private static final Logger log = LoggerFactory.getLogger(SupervisorNode.class);

    @Override
    public Map<String, Object> apply(CodeReviewState state) throws Exception {
        String merged = state.mergedFindings().orElse("NO_ISSUES_FOUND");
        log.info("SupervisorNode: passthrough (findings already merged upstream)");
        return Map.of("mergedFindings", merged, "status", "supervised");
    }
}
