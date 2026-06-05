package com.codereviewer.controller;

import com.codereviewer.service.CodeReviewGraphService;
import com.codereviewer.state.CodeReviewState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * REST API for triggering and managing PR reviews.
 *
 * POST /api/review              — Start a review (returns JSON result)
 * POST /api/review/stream       — Start a review with SSE streaming updates
 * POST /api/review/resume       — Resume a HitL-paused review
 * GET  /api/review/health       — Health check
 */
@RestController
@RequestMapping("/api/review")
@CrossOrigin(origins = "*")
public class CodeReviewController {

    private static final Logger log = LoggerFactory.getLogger(CodeReviewController.class);

    private final CodeReviewGraphService graphService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public CodeReviewController(CodeReviewGraphService graphService) {
        this.graphService = graphService;
    }

    // ── POST /api/review — synchronous ────────────────────────────────────────

    @PostMapping
    public ResponseEntity<?> startReview(@RequestBody ReviewRequest request) {
        if (request.prUrl() == null || request.prUrl().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "prUrl is required"));
        }

        try {
            log.info("Starting synchronous review for: {}", request.prUrl());
            CodeReviewState result = graphService.runReview(request.prUrl());

            if (result == null) {
                return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Review pipeline returned no state"));
            }

            return ResponseEntity.ok(Map.of(
                "status",         result.status().orElse("complete"),
                "prUrl",          request.prUrl(),
                "owner",          result.owner().orElse(""),
                "repo",           result.repo().orElse(""),
                "prNumber",       result.prNumber().orElse(0),
                "prTitle",        result.prTitle().orElse(""),
                "changedFiles",   result.changedFiles().orElse(java.util.List.of()),
                "renderedReview", result.renderedReview().orElse(""),
                "threadId",       result.threadId().orElse("")
            ));
        } catch (Exception e) {
            log.error("Review failed for {}: {}", request.prUrl(), e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }

    // ── POST /api/review/stream — SSE streaming ───────────────────────────────

    @PostMapping("/stream")
    public SseEmitter startReviewStream(@RequestBody ReviewRequest request) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 min timeout

        executor.submit(() -> {
            try {
                emitter.send(SseEmitter.event()
                    .name("status")
                    .data(Map.of("message", "Starting review pipeline...", "prUrl", request.prUrl())));

                emitter.send(SseEmitter.event()
                    .name("status")
                    .data(Map.of("message", "Fetching PR diff from GitHub...")));

                // Run the pipeline (blocking in background thread)
                CodeReviewState result = graphService.runReview(request.prUrl());

                emitter.send(SseEmitter.event()
                    .name("status")
                    .data(Map.of("message", "Security, Logic, and Style agents analyzing...")));

                emitter.send(SseEmitter.event()
                    .name("status")
                    .data(Map.of("message", "Supervisor merging findings...")));

                if (result != null) {
                    emitter.send(SseEmitter.event()
                        .name("complete")
                        .data(Map.of(
                            "status",         "complete",
                            "prTitle",        result.prTitle().orElse(""),
                            "renderedReview", result.renderedReview().orElse(""),
                            "threadId",       result.threadId().orElse("")
                        )));
                }

                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event()
                        .name("error")
                        .data(Map.of("error", e.getMessage())));
                    emitter.complete();
                } catch (Exception ex) {
                    emitter.completeWithError(ex);
                }
            }
        });

        return emitter;
    }

    // ── POST /api/review/resume — HitL resume ─────────────────────────────────

    @PostMapping("/resume")
    public ResponseEntity<?> resumeReview(@RequestBody ResumeRequest request) {
        if (request.threadId() == null || request.threadId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "threadId is required"));
        }

        try {
            log.info("Resuming HitL review | threadId={} | decision={}", request.threadId(), request.decision());
            CodeReviewState result = graphService.resumeReview(request.threadId(), request.decision());

            return ResponseEntity.ok(Map.of(
                "status",         result != null ? result.status().orElse("complete") : "complete",
                "renderedReview", result != null ? result.renderedReview().orElse("") : "",
                "threadId",       request.threadId()
            ));
        } catch (Exception e) {
            log.error("HitL resume failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }

    // ── GET /api/review/health ────────────────────────────────────────────────

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
            "status",  "UP",
            "service", "Code Review Agent",
            "version", "1.0.0"
        ));
    }

    // ── Request records ───────────────────────────────────────────────────────

    public record ReviewRequest(String prUrl) {}
    public record ResumeRequest(String threadId, String decision) {}
}
