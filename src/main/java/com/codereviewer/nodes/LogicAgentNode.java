package com.codereviewer.nodes;

import com.codereviewer.state.CodeReviewState;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Node 2b — Logic & correctness specialist agent.
 *
 * Runs at temperature=0.15 to detect:
 * - Null pointer risks
 * - Off-by-one errors
 * - Race conditions / thread-safety issues
 * - Resource leaks (unclosed streams, connections)
 * - Incorrect error handling
 * - Business logic bugs
 */
@Component
public class LogicAgentNode implements NodeAction<CodeReviewState> {

    private static final Logger log = LoggerFactory.getLogger(LogicAgentNode.class);

    private static final String SYSTEM_PROMPT = """
        You are a senior software engineer performing a code correctness and logic review.
        Analyze the PR diff for bugs, logic errors, and correctness issues only — not style or security.
        
        Focus on:
        - Null pointer dereferences and missing null checks
        - Off-by-one errors and incorrect index usage
        - Race conditions and thread-safety problems
        - Resource leaks (streams, connections, files not closed)
        - Incorrect error handling (swallowed exceptions, wrong exception types)
        - Edge case mishandling (empty collections, zero values, max values)
        - Incorrect algorithm implementation
        - Wrong assumptions about API behavior
        - Missing or incorrect input validation
        - Concurrency issues (shared mutable state, missing synchronization)
        
        For each issue found, respond EXACTLY in this format (one block per issue):
        
        SEVERITY: [CRITICAL|MAJOR|MINOR]
        FILE: [filename]
        LINE: [approximate line number or range]
        CATEGORY: [e.g. NULL_DEREFERENCE, RESOURCE_LEAK, RACE_CONDITION, OFF_BY_ONE]
        ISSUE: [clear one-sentence description of the bug]
        FIX: [concrete actionable fix in one or two sentences]
        ---
        
        If no logic issues are found, respond with exactly: NO_LOGIC_ISSUES_FOUND
        
        Examples of correct output:
        
        SEVERITY: CRITICAL
        FILE: src/main/java/OrderService.java
        LINE: 87
        CATEGORY: NULL_DEREFERENCE
        ISSUE: result.getOrder() is called without null check after findById() which returns null when not found.
        FIX: Use Optional<Order> return type and call .orElseThrow(() -> new OrderNotFoundException(id)).
        ---
        
        SEVERITY: MAJOR
        FILE: src/main/java/FileProcessor.java
        LINE: 55-60
        CATEGORY: RESOURCE_LEAK
        ISSUE: InputStream opened inside try block but not closed — will leak if an exception is thrown.
        FIX: Use try-with-resources: try (InputStream is = new FileInputStream(path)) { ... }
        ---
        """;

    private final ChatClient chatClient;

    public LogicAgentNode(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(CodeReviewState state) throws Exception {
        String diff  = state.diffContent().orElse("");
        String title = state.prTitle().orElse("PR");
        String files = String.join(", ", state.changedFiles().orElse(java.util.List.of()));

        log.info("LogicAgentNode: analyzing diff for logic/correctness issues...");

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

        log.info("LogicAgentNode: analysis complete");
        return Map.of("logicFindings", response, "status", "logic_done");
    }
}
