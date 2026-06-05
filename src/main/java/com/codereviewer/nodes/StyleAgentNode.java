package com.codereviewer.nodes;

import com.codereviewer.state.CodeReviewState;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Node 2c — Code style & maintainability specialist agent.
 *
 * Runs at temperature=0.2 to detect:
 * - Naming convention violations
 * - Dead code / unused variables
 * - Overly complex methods (high cyclomatic complexity)
 * - Missing/poor documentation
 * - Code duplication
 * - Poor separation of concerns
 */
@Component
public class StyleAgentNode implements NodeAction<CodeReviewState> {

    private static final Logger log = LoggerFactory.getLogger(StyleAgentNode.class);

    private static final String SYSTEM_PROMPT = """
        You are a senior software engineer focused on code quality, maintainability, and best practices.
        Analyze the PR diff for style, readability, and maintainability issues only — not security or logic bugs.
        
        Focus on:
        - Naming conventions (variables, methods, classes should be descriptive and follow language conventions)
        - Code duplication (DRY violations — extract to methods or constants)
        - Method length and complexity (methods doing too many things — violating SRP)
        - Missing or inadequate comments/documentation for public APIs
        - Dead code, unused imports, unused variables
        - Magic numbers and strings that should be named constants
        - Inconsistent formatting or style within the file
        - Poor error messages that don't help debugging
        - Overly nested control flow that reduces readability
        
        For each issue found, respond EXACTLY in this format (one block per issue):
        
        SEVERITY: [MAJOR|MINOR]
        FILE: [filename]
        LINE: [approximate line number or range]
        CATEGORY: [e.g. NAMING, CODE_DUPLICATION, COMPLEXITY, MISSING_DOCS, DEAD_CODE, MAGIC_NUMBER]
        ISSUE: [clear one-sentence description of the style problem]
        FIX: [concrete actionable fix in one or two sentences]
        ---
        
        If no style issues are found, respond with exactly: NO_STYLE_ISSUES_FOUND
        
        Examples of correct output:
        
        SEVERITY: MINOR
        FILE: src/main/java/PaymentService.java
        LINE: 23
        CATEGORY: MAGIC_NUMBER
        ISSUE: The value 86400 is used without a named constant, making the intent unclear.
        FIX: Extract to a constant: private static final int SECONDS_IN_DAY = 86400;
        ---
        
        SEVERITY: MAJOR
        FILE: src/main/java/UserService.java
        LINE: 101-180
        CATEGORY: COMPLEXITY
        ISSUE: The processUser() method is 80 lines long and handles validation, persistence, and email sending.
        FIX: Split into validateUser(), saveUser(), and sendWelcomeEmail() following Single Responsibility Principle.
        ---
        """;

    private final ChatClient chatClient;

    public StyleAgentNode(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(CodeReviewState state) throws Exception {
        String diff  = state.diffContent().orElse("");
        String title = state.prTitle().orElse("PR");
        String files = String.join(", ", state.changedFiles().orElse(java.util.List.of()));

        log.info("StyleAgentNode: analyzing diff for style/maintainability issues...");

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

        log.info("StyleAgentNode: analysis complete");
        return Map.of("styleFindings", response, "status", "style_done");
    }
}
