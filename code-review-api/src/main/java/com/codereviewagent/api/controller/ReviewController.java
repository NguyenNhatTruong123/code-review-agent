package com.codereviewagent.api.controller;

import com.codereviewagent.api.service.CurrentUser;
import com.codereviewagent.api.service.GitHubService.RepoInfo;
import com.codereviewagent.api.service.ReviewService;
import com.codereviewagent.api.service.ReviewService.FindingPage;
import com.codereviewagent.api.service.ReviewService.FindingView;
import com.codereviewagent.api.service.ReviewService.PasteInput;
import com.codereviewagent.api.service.ReviewService.RepositoryInput;
import com.codereviewagent.api.service.ReviewService.ReviewPage;
import com.codereviewagent.api.service.ReviewService.ReviewView;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

/** Maps authenticated review creation, retrieval, cancellation, and feedback requests. */
@RestController
@RequestMapping("/api/v1")
public class ReviewController {
    private final ReviewService reviews;
    private final CurrentUser current;

    /**
     * Creates the review endpoint adapter.
     *
     * @param reviews review use-case service
     * @param current authenticated-user resolver
     */
    public ReviewController(ReviewService reviews, CurrentUser current) {
        this.reviews = reviews;
        this.current = current;
    }

    /**
     * Inspects a public repository before review submission.
     *
     * @param url public GitHub repository URL
     * @return repository metadata and available branches
     */
    @GetMapping("/github/inspect")
    public RepoInfo inspect(@RequestParam String url) {
        return reviews.inspect(url);
    }

    /**
     * Queues a repository review and returns its initial view with HTTP 202.
     *
     * @param p authenticated principal
     * @param body repository URL, ref, and rule-set selection
     * @param key optional idempotency key for retry-safe submission
     * @return queued review view
     */
    @PostMapping("/reviews/repository")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ReviewView repository(
            Principal p,
            @RequestBody RepositoryInput body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return reviews.createRepository(current.id(p), body, key);
    }

    /**
     * Queues a pasted-source review and returns its initial view with HTTP 202.
     *
     * @param p authenticated principal
     * @param body pasted source, filename, language, and rule-set selection
     * @param key optional idempotency key for retry-safe submission
     * @return queued review view
     */
    @PostMapping("/reviews/paste")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ReviewView paste(
            Principal p,
            @RequestBody PasteInput body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return reviews.createPaste(current.id(p), body, key);
    }

    /**
     * Lists only the authenticated user's reviews using bounded pagination.
     *
     * @param p authenticated principal
     * @param page zero-based page number
     * @param size requested page size
     * @return paginated review history
     */
    @GetMapping("/reviews")
    public ReviewPage list(
            Principal p,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return reviews.list(current.id(p), page, size);
    }

    /**
     * Returns one owner-scoped review.
     *
     * @param p authenticated principal
     * @param id review identifier
     * @return review view
     */
    @GetMapping("/reviews/{id}")
    public ReviewView get(Principal p, @PathVariable String id) {
        return reviews.get(current.id(p), id);
    }

    /**
     * Cancels a queued or running owner-scoped review.
     *
     * @param p authenticated principal
     * @param id review identifier
     * @return updated review view
     */
    @PostMapping("/reviews/{id}/cancel")
    public ReviewView cancel(Principal p, @PathVariable String id) {
        return reviews.cancel(current.id(p), id);
    }

    /**
     * Deletes an owner-scoped terminal review and its stored findings and feedback.
     *
     * @param p authenticated principal
     * @param id review identifier
     * @throws ResponseStatusException with 404 for a missing or foreign review, or 409 while it
     *     is queued or running
     */
    @DeleteMapping("/reviews/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Principal p, @PathVariable String id) {
        reviews.delete(current.id(p), id);
    }

    /**
     * Lists filtered findings for an owner-scoped review.
     *
     * @param p authenticated principal
     * @param id review identifier
     * @param severity optional severity filter
     * @param ruleId optional rule identifier filter
     * @param file optional exact file-path filter
     * @param page zero-based page number
     * @param size requested page size
     * @return filtered, paginated findings
     */
    @GetMapping("/reviews/{id}/findings")
    public FindingPage findings(
            Principal p,
            @PathVariable String id,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String ruleId,
            @RequestParam(required = false) String file,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return reviews.findings(current.id(p), id, severity, ruleId, file, page, size);
    }

    /**
     * Stores validated feedback for one finding belonging to the review.
     *
     * @param p authenticated principal
     * @param id review identifier
     * @param findingId finding identifier
     * @param body request containing the feedback value
     * @return updated finding view
     */
    @PostMapping("/reviews/{id}/findings/{findingId}/feedback")
    public FindingView feedback(
            Principal p,
            @PathVariable String id,
            @PathVariable String findingId,
            @RequestBody Map<String, String> body) {
        return reviews.feedback(current.id(p), id, findingId, body.get("feedback"));
    }
}
