package com.codereviewer.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin wrapper around the GitHub REST API v3.
 * Used by PrFetcherNode (read) and CommentBuilderNode (write).
 */
@Component
public class GitHubClient {

    private static final String BASE_URL = "https://api.github.com";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient http = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${app.github.token}")
    private String githubToken;

    // ── PR metadata ───────────────────────────────────────────────────────────

    public PrMetadata fetchPrMetadata(String owner, String repo, int prNumber) throws IOException {
        String url = BASE_URL + "/repos/" + owner + "/" + repo + "/pulls/" + prNumber;
        String body = get(url);
        JsonNode node = mapper.readTree(body);
        return new PrMetadata(
            node.path("title").asText(""),
            node.path("body").asText(""),
            node.path("head").path("sha").asText("")
        );
    }

    // ── Diff content ──────────────────────────────────────────────────────────

    /**
     * Fetches the unified diff for a PR (returns raw patch text).
     */
    public String fetchDiff(String owner, String repo, int prNumber) throws IOException {
        String url = BASE_URL + "/repos/" + owner + "/" + repo + "/pulls/" + prNumber;
        Request request = new Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + githubToken)
            .header("Accept", "application/vnd.github.v3.diff")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build();

        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("GitHub diff fetch failed: " + response.code() + " " + response.message());
            }
            return response.body() != null ? response.body().string() : "";
        }
    }

    // ── Changed files ─────────────────────────────────────────────────────────

    public List<String> fetchChangedFiles(String owner, String repo, int prNumber) throws IOException {
        String url = BASE_URL + "/repos/" + owner + "/" + repo + "/pulls/" + prNumber + "/files";
        String body = get(url);
        JsonNode files = mapper.readTree(body);
        List<String> result = new ArrayList<>();
        for (JsonNode file : files) {
            result.add(file.path("filename").asText());
        }
        return result;
    }

    // ── Post review ───────────────────────────────────────────────────────────

    /**
     * Posts the completed review as a PR review comment on GitHub.
     */
    public void postReview(String owner, String repo, int prNumber, String reviewBody) throws IOException {
        String url = BASE_URL + "/repos/" + owner + "/" + repo + "/pulls/" + prNumber + "/reviews";
        String json = mapper.writeValueAsString(new ReviewRequest(reviewBody, "COMMENT"));

        RequestBody body = RequestBody.create(json, JSON);
        Request request = new Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + githubToken)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .post(body)
            .build();

        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                throw new IOException("GitHub review post failed: " + response.code() + " - " + responseBody);
            }
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private String get(String url) throws IOException {
        Request request = new Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + githubToken)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build();

        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("GitHub API call failed: " + response.code() + " " + response.message());
            }
            return response.body() != null ? response.body().string() : "{}";
        }
    }

    // ── Inner record types ────────────────────────────────────────────────────

    public record PrMetadata(String title, String description, String headSha) {}

    private record ReviewRequest(String body, String event) {}
}
