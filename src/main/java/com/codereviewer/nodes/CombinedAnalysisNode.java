package com.codereviewer.nodes;

import com.codereviewer.state.CodeReviewState;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Node 2 — Combined Security + Logic + Style review agent.
 *
 * Replaces three separate LLM calls (SecurityAgentNode, LogicAgentNode,
 * StyleAgentNode) with a single call that reviews all three categories
 * at once. This cuts per-review LLM calls from 4 down to 1, keeping
 * token usage well under free-tier rate limits.
 */
@Component
public class CombinedAnalysisNode implements NodeAction<CodeReviewState> {

    private static final Logger log = LoggerFactory.getLogger(CombinedAnalysisNode.class);

    private static final String SYSTEM_PROMPT = """
        You are a principal engineer performing a thorough code review covering
        security, logic/correctness, and style/maintainability in a single pass.
        Analyze the PR diff for issues in all three categories.

        SECURITY — injection flaws, hardcoded secrets, broken access control,
        XSS, insecure deserialization, SSRF/XXE/path traversal, known-vulnerable components.

        LOGIC — null dereferences, off-by-one errors, race conditions, resource
        leaks, incorrect error handling, edge case mishandling, wrong API assumptions.

        STYLE — naming conventions, code duplication, excessive method complexity,
        missing docs, dead code, magic numbers, poor error messages.

        For each issue found, respond EXACTLY in this format (one block per issue):

        SEVERITY: [CRITICAL|MAJOR|MINOR]
        FILE: [filename]
        LINE: [approximate line number or range]
        CATEGORY: [e.g. SQL_INJECTION, NULL_DEREFERENCE, NAMING, MAGIC_NUMBER]
        AGENT: [SECURITY|LOGIC|STYLE]
        ISSUE: [clear one-sentence description of the issue]
        FIX: [concrete actionable fix in one or two sentences]
        ---

        Sort all findings CRITICAL first, then MAJOR, then MINOR.
        Do not report the same issue twice even if it fits more than one category — pick the single best-fitting category.
        If no issues are found at all, respond with exactly: NO_ISSUES_FOUND
        """;

    private final ChatClient chatClient;

    public CombinedAnalysisNode(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(CodeReviewState state) throws Exception {
        String diff  = state.diffContent().orElse("");
        String title = state.prTitle().orElse("PR");
        String files = String.join(", ", state.changedFiles().orElse(java.util.List.of()));

        log.info("CombinedAnalysisNode: analyzing diff for security/logic/style issues...");

        String userMessage = """
            PR Title: %s
            Changed files: %s

            Diff:
            %s
            """.formatted(title, files, diff);

        String response = chatClient.prompt()
            .system(SYSTEM_PROMPT)
            .user(userMessage)
            .call()
            .content();

        log.info("CombinedAnalysisNode: analysis complete");
        return Map.of("mergedFindings", response, "status", "analysis_done");
    }
}
