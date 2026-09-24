package com.codereviewagent.api.service;

import com.codereviewagent.api.model.FindingEntity;
import com.codereviewagent.api.model.ReviewEntity;
import com.codereviewagent.api.repository.FindingRepository;
import com.codereviewagent.api.repository.ReviewRepository;
import com.codereviewagent.api.service.GitHubService.Repo;
import com.codereviewagent.api.service.GitHubService.RepoInfo;
import com.codereviewagent.api.service.RuleService.RuleSnapshot;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Coordinates review submission, ownership checks, pagination, and result projection. */
@Service
public class ReviewService {
    /**
     * Input contract for a repository review; the ref is resolved to a commit before processing.
     *
     * @param repositoryUrl public repository URL
     * @param ref requested branch or commit; blank selects the default branch
     * @param ruleSetId owner-scoped rule-set identifier
     */
    public record RepositoryInput(String repositoryUrl, String ref, String ruleSetId) {}

    /**
     * Input contract for a review of one pasted source file.
     *
     * @param code source text
     * @param fileName logical source filename
     * @param language explicit language or blank for filename detection
     * @param ruleSetId owner-scoped rule-set identifier
     */
    public record PasteInput(String code, String fileName, String language, String ruleSetId) {}

    /**
     * Public review status view returned by the API.
     *
     * @param id review identifier
     * @param inputType repository or paste input type
     * @param repositoryUrl canonical repository URL when applicable
     * @param requestedRef requested repository ref when applicable
     * @param commitSha resolved immutable commit when applicable
     * @param fileName pasted filename when applicable
     * @param language normalized source language
     * @param ruleSetId selected rule-set identifier
     * @param ruleSetName selected rule-set name snapshot
     * @param status processing status
     * @param scannedFiles number of processed files
     * @param skippedFiles number of skipped files
     * @param warning non-fatal processing warning
     * @param error terminal error message
     * @param createdAt creation timestamp
     * @param updatedAt last status-update timestamp
     * @param summary finding counts grouped by severity
     */
    public record ReviewView(
            String id,
            String inputType,
            String repositoryUrl,
            String requestedRef,
            String commitSha,
            String fileName,
            String language,
            String ruleSetId,
            String ruleSetName,
            String status,
            int scannedFiles,
            int skippedFiles,
            String warning,
            String error,
            Instant createdAt,
            Instant updatedAt,
            Map<String, Long> summary) {}

    /**
     * Public finding view; line numbers remain tied to the reviewed source.
     *
     * @param id finding identifier
     * @param ruleId rule that produced the finding
     * @param ruleVersion rule version used during review
     * @param severity finding severity
     * @param title finding title
     * @param explanation finding explanation
     * @param evidence source evidence
     * @param suggestedFix remediation suggestion
     * @param filePath source path
     * @param lineStart first one-based affected line
     * @param lineEnd last one-based affected line
     * @param source static or AI finding source
     * @param feedback user feedback value, if provided
     */
    public record FindingView(
            String id,
            String ruleId,
            int ruleVersion,
            String severity,
            String title,
            String explanation,
            String evidence,
            String suggestedFix,
            String filePath,
            Integer lineStart,
            Integer lineEnd,
            String source,
            String feedback) {}

    /**
     * Paginated findings response after optional filters are applied.
     *
     * @param items findings on the current page
     * @param total total matching findings
     * @param page zero-based page number
     * @param size page size
     */
    public record FindingPage(List<FindingView> items, int total, int page, int size) {}

    /**
     * Paginated review history response.
     *
     * @param items reviews on the current page
     * @param total total owner-scoped reviews
     * @param page zero-based page number
     * @param size page size
     */
    public record ReviewPage(List<ReviewView> items, long total, int page, int size) {}

    private static final Set<String> FEEDBACK = Set.of("HELPFUL", "IRRELEVANT", "FALSE_POSITIVE");
    private static final Set<String> LANGUAGES =
            Set.of(
                    "JAVA",
                    "JAVASCRIPT",
                    "TYPESCRIPT",
                    "JSX",
                    "TSX",
                    "PYTHON",
                    "GO",
                    "CSHARP",
                    "RUBY",
                    "PHP",
                    "RUST",
                    "KOTLIN",
                    "SWIFT",
                    "C",
                    "CPP",
                    "VUE",
                    "SQL");
    private final ReviewRepository reviews;
    private final FindingRepository findings;
    private final RuleService rules;
    private final GitHubService github;
    private final ReviewWorker worker;
    private final int maxPasteChars;

    /**
     * Creates the review use-case service and its external collaborators.
     *
     * @param reviews review repository
     * @param findings finding repository
     * @param rules rule snapshot service
     * @param github GitHub integration service
     * @param worker asynchronous review worker
     * @param maxPasteChars maximum accepted pasted-source length
     */
    public ReviewService(
            ReviewRepository reviews,
            FindingRepository findings,
            RuleService rules,
            GitHubService github,
            ReviewWorker worker,
            @Value("${app.max-paste-chars}") int maxPasteChars) {
        this.reviews = reviews;
        this.findings = findings;
        this.rules = rules;
        this.github = github;
        this.worker = worker;
        this.maxPasteChars = maxPasteChars;
    }

    /**
     * Inspects a repository before an asynchronous review is created.
     *
     * @param url user-supplied public repository URL
     * @return repository metadata
     */
    public RepoInfo inspect(String url) {
        return github.inspect(github.parse(url));
    }

    /**
     * Creates an idempotent asynchronous review for a pinned repository commit.
     *
     * @param owner authenticated application user ID
     * @param input repository review request
     * @param key optional idempotency key
     * @return queued or previously-created review view
     * @throws ResponseStatusException when input is invalid, ownership fails, or capacity is full
     */
    public ReviewView createRepository(String owner, RepositoryInput input, String key) {
        // The hash binds an idempotency key to the complete logical request, not just its key
        // string.
        key = key == null || key.isBlank() ? null : key;
        if (input == null || input.ruleSetId() == null) {
            throw invalid("Rule set is required");
        }

        Repo repo = github.parse(input.repositoryUrl());
        RepoInfo info = github.inspect(repo);
        String ref =
                input.ref() == null || input.ref().isBlank()
                        ? info.defaultBranch()
                        : input.ref().trim();
        String hash = sha256(repo.url() + "\n" + ref + "\n" + input.ruleSetId());
        ReviewEntity existing = previous(owner, key, hash);
        if (existing != null) {
            return view(existing);
        }

        ensureCapacity(owner);
        List<RuleSnapshot> snapshot = rules.snapshot(owner, input.ruleSetId());
        String sha = github.resolveCommit(repo, ref);

        ReviewEntity review =
                new ReviewEntity(UUID.randomUUID().toString(), owner, "GITHUB", input.ruleSetId());
        review.repositoryUrl = repo.url();
        review.requestedRef = ref;
        review.commitSha = sha;
        review.ruleSetName = rules.ownedSet(input.ruleSetId(), owner).name;
        review.ruleSnapshotJson = rules.write(snapshot);
        review.idempotencyKey = key;
        review.inputHash = hash;
        reviews.save(review);

        try {
            worker.repository(review.id, repo, sha);
        } catch (org.springframework.core.task.TaskRejectedException e) {
            markQueueFull(review);
        }
        return view(review);
    }

    /**
     * Creates an idempotent asynchronous review for one pasted source file.
     *
     * @param owner authenticated application user ID
     * @param input pasted-source review request
     * @param key optional idempotency key
     * @return queued or previously-created review view
     * @throws ResponseStatusException when input is invalid, source is too large, or capacity is
     *     full
     */
    public ReviewView createPaste(String owner, PasteInput input, String key) {
        key = key == null || key.isBlank() ? null : key;
        if (input == null
                || input.code() == null
                || input.code().isBlank()
                || input.ruleSetId() == null) {
            throw invalid("Code and rule set are required");
        }
        if (input.code().length() > maxPasteChars) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE, "Pasted code is too large");
        }

        String path =
                input.fileName() == null || input.fileName().isBlank()
                        ? "pasted-code.txt"
                        : input.fileName().trim();
        if (path.length() > 180 || path.contains("/") || path.contains("\\") || path.equals("..")) {
            throw invalid("Invalid file name");
        }
        String language =
                input.language() == null || input.language().isBlank()
                        ? GitHubService.language(path)
                        : input.language().toUpperCase(java.util.Locale.ROOT);
        if (language == null || !LANGUAGES.contains(language)) {
            throw invalid("Select a supported language");
        }

        String hash =
                sha256(input.code() + "\n" + path + "\n" + language + "\n" + input.ruleSetId());
        ReviewEntity existing = previous(owner, key, hash);
        if (existing != null) {
            return view(existing);
        }

        ensureCapacity(owner);
        List<RuleSnapshot> snapshot = rules.snapshot(owner, input.ruleSetId());

        ReviewEntity review =
                new ReviewEntity(UUID.randomUUID().toString(), owner, "PASTE", input.ruleSetId());
        review.fileName = path;
        review.language = language;
        review.ruleSetName = rules.ownedSet(input.ruleSetId(), owner).name;
        review.ruleSnapshotJson = rules.write(snapshot);
        review.idempotencyKey = key;
        review.inputHash = hash;
        reviews.save(review);

        try {
            worker.paste(review.id, input.code(), path, language);
        } catch (org.springframework.core.task.TaskRejectedException e) {
            markQueueFull(review);
        }
        return view(review);
    }

    /**
     * Returns one owner's review history using bounded pagination.
     *
     * @param owner authenticated application user ID
     * @param page zero-based page number
     * @param size page size, between 1 and 100
     * @return owner-scoped review page
     * @throws ResponseStatusException when pagination is outside the accepted range
     */
    public ReviewPage list(String owner, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw invalid("Invalid pagination");
        }
        var result =
                reviews.findByOwnerId(
                        owner,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new ReviewPage(
                result.getContent().stream().map(this::view).toList(),
                result.getTotalElements(),
                page,
                size);
    }

    /**
     * Returns a review only when it belongs to the requesting owner.
     *
     * @param owner authenticated application user ID
     * @param id review identifier
     * @return review view
     * @throws ResponseStatusException when the review is missing or not owned by the user
     */
    public ReviewView get(String owner, String id) {
        return view(owned(owner, id));
    }

    /**
     * Loads a review while returning not-found for both missing and foreign IDs.
     *
     * @param owner authenticated application user ID
     * @param id review identifier
     * @return persisted owner-scoped review
     * @throws ResponseStatusException when the review is missing or belongs to another user
     */
    public ReviewEntity owned(String owner, String id) {
        ReviewEntity review = reviews.findById(id).orElseThrow(() -> missing("Review"));
        if (!review.ownerId.equals(owner)) {
            throw missing("Review");
        }
        return review;
    }

    /**
     * Returns owner-checked findings after optional filters and pagination.
     *
     * @param owner authenticated application user ID
     * @param id review identifier
     * @param severity optional severity filter
     * @param ruleId optional rule filter
     * @param file optional exact path filter
     * @param page zero-based page number
     * @param size page size, between 1 and 100
     * @return filtered finding page
     * @throws ResponseStatusException when the review is not owned or pagination is invalid
     */
    public FindingPage findings(
            String owner,
            String id,
            String severity,
            String ruleId,
            String file,
            int page,
            int size) {
        owned(owner, id);
        if (page < 0 || size < 1 || size > 100) {
            throw invalid("Invalid pagination");
        }
        List<FindingView> filtered =
                this.findings.findByReviewIdOrderByFilePathAscLineStartAsc(id).stream()
                        .filter(
                                f ->
                                        severity == null
                                                || severity.isBlank()
                                                || severity.equals(f.severity))
                        .filter(f -> ruleId == null || ruleId.isBlank() || ruleId.equals(f.ruleId))
                        .filter(f -> file == null || file.isBlank() || file.equals(f.filePath))
                        .map(this::view)
                        .toList();
        long from = (long) page * size;
        if (from >= filtered.size()) {
            return new FindingPage(List.of(), filtered.size(), page, size);
        }
        return new FindingPage(
                filtered.subList((int) from, (int) Math.min(from + size, filtered.size())),
                filtered.size(),
                page,
                size);
    }

    /**
     * Stores one of the supported feedback values for an owned finding.
     *
     * @param owner authenticated application user ID
     * @param reviewId review identifier
     * @param findingId finding identifier
     * @param value one of the supported feedback values
     * @return updated finding view
     * @throws ResponseStatusException when ownership, finding identity, or feedback value is
     *     invalid
     */
    public FindingView feedback(String owner, String reviewId, String findingId, String value) {
        owned(owner, reviewId);
        if (value == null || !FEEDBACK.contains(value)) {
            throw invalid("Invalid feedback");
        }
        FindingEntity finding = findings.findById(findingId).orElseThrow(() -> missing("Finding"));
        if (!reviewId.equals(finding.reviewId)) {
            throw missing("Finding");
        }
        finding.feedback = value;
        finding.feedbackAt = Instant.now();
        return view(findings.save(finding));
    }

    /**
     * Cancels a queued or running review and leaves terminal reviews unchanged.
     *
     * @param owner authenticated application user ID
     * @param id review identifier
     * @return updated cancelled review view
     * @throws ResponseStatusException when the review is missing, foreign, or already terminal
     */
    public ReviewView cancel(String owner, String id) {
        ReviewEntity r = owned(owner, id);
        if (!Set.of("QUEUED", "RUNNING").contains(r.status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Review already finished");
        }
        r.status = "CANCELLED";
        r.updatedAt = Instant.now();
        return view(reviews.save(r));
    }

    /**
     * Marks work interrupted by a process restart as failed instead of leaving it queued forever.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void markInterrupted() {
        for (ReviewEntity r : reviews.findByStatusIn(List.of("QUEUED", "RUNNING"))) {
            r.status = "FAILED";
            r.error = "Review was interrupted by a server restart";
            r.updatedAt = Instant.now();
            reviews.save(r);
        }
    }

    private ReviewEntity previous(String owner, String key, String hash) {
        if (key == null || key.isBlank()) {
            return null;
        }
        if (key.length() > 100 || !key.matches("[A-Za-z0-9._-]+")) {
            throw invalid("Invalid idempotency key");
        }
        return reviews.findByOwnerIdAndIdempotencyKey(owner, key)
                .map(
                        r -> {
                            if (!hash.equals(r.inputHash)) {
                                throw new ResponseStatusException(
                                        HttpStatus.CONFLICT, "Idempotency key already used");
                            }
                            return r;
                        })
                .orElse(null);
    }

    private void ensureCapacity(String owner) {
        if (reviews.countByOwnerIdAndStatusIn(owner, List.of("QUEUED", "RUNNING")) >= 3) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Finish an active review before starting another");
        }
    }

    private void markQueueFull(ReviewEntity review) {
        review.status = "FAILED";
        review.error = "Review queue is full; try again later";
        review.updatedAt = Instant.now();
        reviews.save(review);
    }

    private ReviewView view(ReviewEntity r) {
        Map<String, Long> summary =
                findings.findByReviewIdOrderByFilePathAscLineStartAsc(r.id).stream()
                        .collect(Collectors.groupingBy(f -> f.severity, Collectors.counting()));
        return new ReviewView(
                r.id,
                r.inputType,
                r.repositoryUrl,
                r.requestedRef,
                r.commitSha,
                r.fileName,
                r.language,
                r.ruleSetId,
                r.ruleSetName,
                r.status,
                r.scannedFiles,
                r.skippedFiles,
                r.warning,
                r.error,
                r.createdAt,
                r.updatedAt,
                summary);
    }

    private FindingView view(FindingEntity f) {
        return new FindingView(
                f.id,
                f.ruleId,
                f.ruleVersion,
                f.severity,
                f.title,
                f.explanation,
                f.evidence,
                f.suggestedFix,
                f.filePath,
                f.lineStart,
                f.lineEnd,
                f.source,
                f.feedback);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException missing(String resource) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, resource + " not found");
    }
}
