package com.codereviewer.nodes;

import com.codereviewer.state.CodeReviewState;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Node 2a — Security specialist agent.
 *
 * Runs at temperature=0.1 (deterministic) to detect:
 * - SQL injection, XSS, path traversal, command injection
 * - Hardcoded secrets/API keys
 * - OWASP Top 10 violations
 * - Insecure deserialization, SSRF, XXE
 */
@Component
public class SecurityAgentNode implements NodeAction<CodeReviewState> {

    private static final Logger log = LoggerFactory.getLogger(SecurityAgentNode.class);

    private static final String SYSTEM_PROMPT = """
        You are a senior application security engineer performing a thorough security code review.
        Analyze the PR diff for security vulnerabilities only.
        
        Focus on:
        - Injection flaws: SQL, LDAP, OS command, XPath injection
        - Authentication and session management issues
        - Sensitive data exposure (hardcoded secrets, API keys, passwords)
        - Broken access control
        - Security misconfigurations
        - Cross-site scripting (XSS)
        - Insecure deserialization
        - SSRF, XXE, path traversal
        - Use of components with known vulnerabilities
        
        For each issue found, respond EXACTLY in this format (one block per issue):
        
        SEVERITY: [CRITICAL|MAJOR|MINOR]
        FILE: [filename]
        LINE: [approximate line number or range, e.g. 42 or 42-48]
        CATEGORY: [e.g. SQL_INJECTION, HARDCODED_SECRET, XSS]
        ISSUE: [clear one-sentence description of the vulnerability]
        FIX: [concrete actionable fix in one or two sentences]
        ---
        
        If no security issues are found, respond with exactly: NO_SECURITY_ISSUES_FOUND
        
        Examples of correct output:
        
        SEVERITY: CRITICAL
        FILE: src/main/java/UserRepository.java
        LINE: 34
        CATEGORY: SQL_INJECTION
        ISSUE: User-controlled input is concatenated directly into an SQL string without parameterization.
        FIX: Replace with PreparedStatement or JPA parameterized query: findByUsername(:username).
        ---
        
        SEVERITY: MAJOR
        FILE: src/main/resources/application.properties
        LINE: 12
        CATEGORY: HARDCODED_SECRET
        ISSUE: AWS secret key is hardcoded in the properties file and will be committed to version control.
        FIX: Move to environment variable AWS_SECRET_KEY and reference via ${AWS_SECRET_KEY}.
        ---
        """;

    private final ChatClient chatClient;

    public SecurityAgentNode(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(CodeReviewState state) throws Exception {
        String diff  = state.diffContent().orElse("");
        String title = state.prTitle().orElse("PR");
        String files = String.join(", ", state.changedFiles().orElse(java.util.List.of()));

        log.info("SecurityAgentNode: analyzing diff for security issues...");

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

        log.info("SecurityAgentNode: analysis complete");
        return Map.of("securityFindings", response, "status", "security_done");
    }
}
