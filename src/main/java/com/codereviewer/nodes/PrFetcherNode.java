package com.codereviewer.nodes;

import com.codereviewer.state.CodeReviewState;
import com.codereviewer.tools.GitHubClient;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Node 1 — Fetches PR diff + metadata from GitHub.
 *
 * Input:  prUrl (e.g. https://github.com/owner/repo/pull/42)
 * Output: owner, repo, prNumber, prTitle, prDescription, diffContent, changedFiles
 */
@Component
public class PrFetcherNode implements NodeAction<CodeReviewState> {

    private static final Logger log = LoggerFactory.getLogger(PrFetcherNode.class);

    // Pattern: https://github.com/{owner}/{repo}/pull/{number}
    private static final Pattern PR_PATTERN =
        Pattern.compile("https://github\\.com/([^/]+)/([^/]+)/pull/(\\d+)");

    private final GitHubClient gitHubClient;

    public PrFetcherNode(GitHubClient gitHubClient) {
        this.gitHubClient = gitHubClient;
    }

    @Override
    public Map<String, Object> apply(CodeReviewState state) throws Exception {
        String prUrl = state.prUrl()
            .orElseThrow(() -> new IllegalArgumentException("prUrl is required"));

        log.info("PrFetcherNode: fetching PR → {}", prUrl);

        // Parse owner/repo/number from URL
        Matcher m = PR_PATTERN.matcher(prUrl);
        if (!m.find()) {
            throw new IllegalArgumentException("Invalid GitHub PR URL: " + prUrl);
        }
        String owner    = m.group(1);
        String repo     = m.group(2);
        int    prNumber = Integer.parseInt(m.group(3));

        // Fetch data from GitHub API
        GitHubClient.PrMetadata meta  = gitHubClient.fetchPrMetadata(owner, repo, prNumber);
        String                  diff  = gitHubClient.fetchDiff(owner, repo, prNumber);
        List<String>            files = gitHubClient.fetchChangedFiles(owner, repo, prNumber);

        log.info("PrFetcherNode: fetched {} changed files, diff length={}", files.size(), diff.length());

        // Truncate diff to 12 000 chars to stay within LLM context window
        String truncatedDiff = diff.length() > 12000
            ? diff.substring(0, 12000) + "\n\n[... diff truncated for context limit ...]"
            : diff;

        return Map.of(
            "owner",          owner,
            "repo",           repo,
            "prNumber",       prNumber,
            "prTitle",        meta.title(),
            "prDescription",  meta.description(),
            "diffContent",    truncatedDiff,
            "changedFiles",   files,
            "status",         "fetched"
        );
    }
}
