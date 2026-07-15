package com.codereviewer;

import com.codereviewer.nodes.CommentBuilderNode;
import com.codereviewer.nodes.CombinedAnalysisNode;
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
    private CombinedAnalysisNode combinedAnalysisNode;

    @Autowired
    private SupervisorNode supervisorNode;

    // ── Context loads ─────────────────────────────────────────────────────────

    @Test
    void contextLoads() {
        // Verifies all beans wire up correctly
    }

    // ── CombinedAnalysisNode unit test ────────────────────────────────────────

    @Test
    void combinedAnalysis_returnsMergedFindingsKey() throws Exception {
        String mockDiff = """
            -   String query = "SELECT * FROM users WHERE id = " + userId;
            +   String query = "SELECT * FROM users WHERE id = ?";
            """;

        CodeReviewState state = new CodeReviewState(Map.of(
            "diffContent",  mockDiff,
            "prTitle",      "Fix user lookup",
            "changedFiles", java.util.List.of("UserRepository.java")
        ));

        Map<String, Object> result = combinedAnalysisNode.apply(state);

        assertThat(result).containsKey("mergedFindings");
        assertThat(result.get("mergedFindings").toString()).isNotBlank();
    }

    // ── SupervisorNode unit test (passthrough) ────────────────────────────────

    @Test
    void supervisorNode_passesThroughMergedFindings() throws Exception {
        String findings = "SEVERITY: CRITICAL\nFILE: Foo.java\nLINE: 10\nCATEGORY: SQL_INJECTION\nAGENT: SECURITY\nISSUE: SQL injection risk\nFIX: Use prepared statements\n---";

        CodeReviewState state = new CodeReviewState(Map.of(
            "mergedFindings", findings
        ));

        Map<String, Object> result = supervisorNode.apply(state);

        assertThat(result).containsKey("mergedFindings");
        assertThat(result.get("mergedFindings").toString()).isEqualTo(findings);
    }
}
