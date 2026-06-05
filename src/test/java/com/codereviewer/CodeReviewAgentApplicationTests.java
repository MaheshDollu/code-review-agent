package com.codereviewer;

import com.codereviewer.nodes.CommentBuilderNode;
import com.codereviewer.nodes.SecurityAgentNode;
import com.codereviewer.nodes.LogicAgentNode;
import com.codereviewer.nodes.StyleAgentNode;
import com.codereviewer.nodes.SupervisorNode;
import com.codereviewer.state.CodeReviewState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.codereviewer.tools.GitHubClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest
class CodeReviewAgentApplicationTests {

    @MockBean
    private GitHubClient gitHubClient;

    @Autowired
    private SecurityAgentNode securityAgentNode;

    @Autowired
    private LogicAgentNode logicAgentNode;

    @Autowired
    private StyleAgentNode styleAgentNode;

    @Autowired
    private SupervisorNode supervisorNode;

    // ── Context loads ─────────────────────────────────────────────────────────

    @Test
    void contextLoads() {
        // Verifies all beans wire up correctly
    }

    // ── SecurityAgentNode unit test ───────────────────────────────────────────

    @Test
    void securityAgent_returnsFindingsKey() throws Exception {
        String mockDiff = """
            -   String query = "SELECT * FROM users WHERE id = " + userId;
            +   String query = "SELECT * FROM users WHERE id = ?";
            """;

        CodeReviewState state = new CodeReviewState(Map.of(
            "diffContent",  mockDiff,
            "prTitle",      "Fix user lookup",
            "changedFiles", java.util.List.of("UserRepository.java")
        ));

        Map<String, Object> result = securityAgentNode.apply(state);

        assertThat(result).containsKey("securityFindings");
        assertThat(result.get("securityFindings").toString()).isNotBlank();
    }

    // ── LogicAgentNode unit test ──────────────────────────────────────────────

    @Test
    void logicAgent_returnsFindingsKey() throws Exception {
        String mockDiff = """
            +   User user = userRepo.findById(id);
            +   return user.getName();
            """;

        CodeReviewState state = new CodeReviewState(Map.of(
            "diffContent",  mockDiff,
            "prTitle",      "Add user name endpoint",
            "changedFiles", java.util.List.of("UserService.java")
        ));

        Map<String, Object> result = logicAgentNode.apply(state);

        assertThat(result).containsKey("logicFindings");
        assertThat(result.get("logicFindings").toString()).isNotBlank();
    }

    // ── StyleAgentNode unit test ──────────────────────────────────────────────

    @Test
    void styleAgent_returnsFindingsKey() throws Exception {
        String mockDiff = """
            +   public void x(String a, int b, boolean c, List d, Map e) {
            +       if (a != null) { if (b > 0) { if (c) { for (Object o : d) { } } } }
            +   }
            """;

        CodeReviewState state = new CodeReviewState(Map.of(
            "diffContent",  mockDiff,
            "prTitle",      "Add processing method",
            "changedFiles", java.util.List.of("Processor.java")
        ));

        Map<String, Object> result = styleAgentNode.apply(state);

        assertThat(result).containsKey("styleFindings");
        assertThat(result.get("styleFindings").toString()).isNotBlank();
    }

    // ── SupervisorNode unit test ──────────────────────────────────────────────

    @Test
    void supervisorNode_mergesAllFindings() throws Exception {
        CodeReviewState state = new CodeReviewState(Map.of(
            "securityFindings", "SEVERITY: CRITICAL\nFILE: Foo.java\nLINE: 10\nCATEGORY: SQL_INJECTION\nAGENT: SECURITY\nISSUE: SQL injection risk\nFIX: Use prepared statements\n---",
            "logicFindings",    "SEVERITY: MAJOR\nFILE: Bar.java\nLINE: 20\nCATEGORY: NULL_DEREFERENCE\nAGENT: LOGIC\nISSUE: Null pointer risk\nFIX: Add null check\n---",
            "styleFindings",    "NO_STYLE_ISSUES_FOUND"
        ));

        Map<String, Object> result = supervisorNode.apply(state);

        assertThat(result).containsKey("mergedFindings");
        String merged = result.get("mergedFindings").toString();
        assertThat(merged).isNotBlank();
    }
}
