package com.codereviewer.nodes;

import com.codereviewer.state.CodeReviewState;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Node 3 — Supervisor: fan-in, deduplication, severity ranking.
 *
 * Receives findings from all three parallel agents, merges them,
 * removes duplicates, sorts by severity (CRITICAL → MAJOR → MINOR),
 * and produces a unified findings list for the CommentBuilderNode.
 *
 * Optionally triggers HitL via interruptBefore in the graph config.
 */
@Component
public class SupervisorNode implements NodeAction<CodeReviewState> {

    private static final Logger log = LoggerFactory.getLogger(SupervisorNode.class);

    private static final String SYSTEM_PROMPT = """
        You are a principal engineer supervising a multi-agent code review.
        You have received findings from three specialist agents: Security, Logic, and Style.
        
        Your job:
        1. Merge all findings into a single unified list
        2. Remove duplicates (same issue reported by multiple agents — keep the most detailed version)
        3. Sort by severity: CRITICAL first, then MAJOR, then MINOR
        4. Ensure every finding strictly follows this output format:
        
        SEVERITY: [CRITICAL|MAJOR|MINOR]
        FILE: [filename]
        LINE: [line number or range]
        CATEGORY: [category]
        AGENT: [SECURITY|LOGIC|STYLE]
        ISSUE: [clear description]
        FIX: [actionable fix]
        ---
        
        Output ONLY the merged finding blocks in the format above.
        Do not add any introduction, summary, or conclusion text.
        If there are no findings at all, output exactly: NO_ISSUES_FOUND
        """;

    private final ChatClient chatClient;

    public SupervisorNode(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(CodeReviewState state) throws Exception {
        String secFindings  = state.securityFindings().orElse("NO_SECURITY_ISSUES_FOUND");
        String logicFindings = state.logicFindings().orElse("NO_LOGIC_ISSUES_FOUND");
        String styleFindings = state.styleFindings().orElse("NO_STYLE_ISSUES_FOUND");

        log.info("SupervisorNode: merging findings from all agents...");

        String userMessage = """
            === SECURITY AGENT FINDINGS ===
            %s
            
            === LOGIC AGENT FINDINGS ===
            %s
            
            === STYLE AGENT FINDINGS ===
            %s
            """.formatted(secFindings, logicFindings, styleFindings);

        String merged = chatClient.prompt()
            .system(SYSTEM_PROMPT)
            .user(userMessage)
            .call()
            .content();

        log.info("SupervisorNode: merged findings ready");
        return Map.of("mergedFindings", merged, "status", "supervised");
    }
}
