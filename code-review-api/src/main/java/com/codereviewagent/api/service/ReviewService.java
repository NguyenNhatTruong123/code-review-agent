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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
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
     * @param ruleSetId owner-scoped rule-set identifier when using a rule set
     * @param ruleIds owner-scoped rule identifiers when selecting individual rules
     */
    public record RepositoryInput(
            String repositoryUrl, String ref, String ruleSetId, List<String> ruleIds) {
        public RepositoryInput(String repositoryUrl, String ref, String ruleSetId) {
            this(repositoryUrl, ref, ruleSetId, null);
        }
    }

    /**
     * Input contract for a review of one pasted source file.
     *
     * @param code source text
     * @param fileName logical source filename
     * @param language explicit language or blank for filename detection
     * @param ruleSetId owner-scoped rule-set identifier when using a rule set
     * @param ruleIds owner-scoped rule identifiers when selecting individual rules
     */
    public record PasteInput(
            String code, String fileName, String language, String ruleSetId, List<String> ruleIds) {
        public PasteInput(String code, String fileName, String language, String ruleSetId) {
            this(code, fileName, language, ruleSetId, null);
        }
    }

    /**
     * Selects whether a rerun uses its stored rule snapshot, an enabled current rule set, or
     * directly selected current rules.
     *
     * @param ruleMode {@code ORIGINAL}, {@code CURRENT}, or {@code CURRENT_RULES}
     * @param ruleSetId required only when {@code ruleMode} is {@code CURRENT}
     * @param ruleIds required only when {@code ruleMode} is {@code CURRENT_RULES}
     */
    public record RerunInput(String ruleMode, String ruleSetId, List<String> ruleIds) {
        public RerunInput(String ruleMode, String ruleSetId) {
            this(ruleMode, ruleSetId, null);
        }
    }

    /**
     * Public review status view returned by the API.
     *
     * @param id review identifier
     * @param parentReviewId original review identifier when this review is a rerun
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
     * @param aiEvaluatedFiles number of files with a successful AI provider response
     * @param warning non-fatal processing warning
     * @param error terminal error message
     * @param createdAt creation timestamp
     * @param updatedAt last status-update timestamp
     * @param rerunCount number of direct reruns created from this review
     * @param summary finding counts grouped by severity
     */
    public record ReviewView(
            String id,
            String parentReviewId,
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
            int aiEvaluatedFiles,
            String warning,
            String error,
            Instant createdAt,
            Instant updatedAt,
            long rerunCount,
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
     * @param items original reviews on the current page
     * @param total total owner-scoped original reviews
     * @param page zero-based page number
     * @param size page size
     */
    public record ReviewPage(List<ReviewView> items, long total, int page, int size) {}

    private static final Set<String> FEEDBACK = Set.of("HELPFUL", "IRRELEVANT", "FALSE_POSITIVE");
    private static final Set<String> RERUN_RULE_MODES =
            Set.of("ORIGINAL", "CURRENT", "CURRENT_RULES");
    private static final String DIRECT_RULES_ID = "DIRECT_RULES";
    private static final String DIRECT_RULES_NAME = "Selected rules";
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

    private record SelectedRules(String ruleSetId, String ruleSetName, String snapshotJson) {}

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
        if (input == null) {
            throw invalid("Review input is required");
        }

        SelectedRules selectedRules = selectRules(owner, input.ruleSetId(), input.ruleIds());

        Repo repo = github.parse(input.repositoryUrl());
        RepoInfo info = github.inspect(repo);
        String ref =
                input.ref() == null || input.ref().isBlank()
                        ? info.defaultBranch()
                        : input.ref().trim();
        String hash =
                sha256(repo.url() + "\n" + ref + "\n" + selectedRules.snapshotJson());
        ReviewEntity existing = previous(owner, key, hash);
        if (existing != null) {
            return view(existing);
        }

        ensureCapacity(owner);
        String sha = github.resolveCommit(repo, ref);

        ReviewEntity review =
                new ReviewEntity(
                        UUID.randomUUID().toString(),
                        owner,
                        "GITHUB",
                        selectedRules.ruleSetId());
        review.repositoryUrl = repo.url();
        review.requestedRef = ref;
        review.commitSha = sha;
        review.ruleSetName = selectedRules.ruleSetName();
        review.ruleSnapshotJson = selectedRules.snapshotJson();
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
                || input.code().isBlank()) {
            throw invalid("Code is required");
        }

        SelectedRules selectedRules = selectRules(owner, input.ruleSetId(), input.ruleIds());

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
                sha256(
                        input.code()
                                + "\n"
                                + path
                                + "\n"
                                + language
                                + "\n"
                                + selectedRules.snapshotJson());
        ReviewEntity existing = previous(owner, key, hash);
        if (existing != null) {
            return view(existing);
        }

        ensureCapacity(owner);

        ReviewEntity review =
                new ReviewEntity(
                        UUID.randomUUID().toString(),
                        owner,
                        "PASTE",
                        selectedRules.ruleSetId());
        review.fileName = path;
        review.language = language;
        review.pastedSource = input.code();
        review.ruleSetName = selectedRules.ruleSetName();
        review.ruleSnapshotJson = selectedRules.snapshotJson();
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
     * Queues a new review from an owned review's pinned source and selected rule strategy.
     *
     * @param owner authenticated application user ID
     * @param id original review identifier
     * @param input original-snapshot or current-rule-set selection
     * @return newly queued review view
     * @throws ResponseStatusException when the source, snapshot, rule set, or capacity is
     *     unavailable
     */
    public ReviewView rerun(String owner, String id, RerunInput input) {
        ReviewEntity original = owned(owner, id);
        SelectedRules rerunRules = selectRerunRules(owner, original, input);

        if ("GITHUB".equals(original.inputType)) {
            return rerunRepository(owner, original, rerunRules);
        }
        if ("PASTE".equals(original.inputType)) {
            return rerunPaste(owner, original, rerunRules);
        }

        throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Original review source is unavailable");
    }

    private ReviewView rerunRepository(
            String owner, ReviewEntity original, SelectedRules rerunRules) {
        if (original.repositoryUrl == null
                || original.repositoryUrl.isBlank()
                || original.commitSha == null
                || !original.commitSha.matches("[a-fA-F0-9]{40}")) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Original repository source is unavailable");
        }

        Repo repo;
        try {
            repo = github.parse(original.repositoryUrl);
        } catch (ResponseStatusException e) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Original repository source is unavailable");
        }

        ReviewEntity rerun = newRerun(owner, original, rerunRules);
        rerun.repositoryUrl = original.repositoryUrl;
        rerun.requestedRef = original.requestedRef;
        rerun.commitSha = original.commitSha;
        reviews.save(rerun);

        try {
            worker.repository(rerun.id, repo, rerun.commitSha);
        } catch (org.springframework.core.task.TaskRejectedException e) {
            markQueueFull(rerun);
        }
        return view(rerun);
    }

    private ReviewView rerunPaste(String owner, ReviewEntity original, SelectedRules rerunRules) {
        if (original.pastedSource == null
                || original.pastedSource.isBlank()
                || original.fileName == null
                || original.fileName.isBlank()
                || original.language == null
                || original.language.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Original pasted source is unavailable");
        }

        ReviewEntity rerun = newRerun(owner, original, rerunRules);
        rerun.fileName = original.fileName;
        rerun.language = original.language;
        rerun.pastedSource = original.pastedSource;
        reviews.save(rerun);

        try {
            worker.paste(rerun.id, rerun.pastedSource, rerun.fileName, rerun.language);
        } catch (org.springframework.core.task.TaskRejectedException e) {
            markQueueFull(rerun);
        }
        return view(rerun);
    }

    /**
     * Resolves exactly one current rule-selection mode into a stable review snapshot.
     *
     * @param owner authenticated application user ID
     * @param ruleSetId selected enabled rule-set identifier, if any
     * @param ruleIds selected enabled individual rule identifiers, if any
     * @return selection metadata and serialized immutable snapshot
     * @throws ResponseStatusException when both or neither selection modes are supplied
     */
    private SelectedRules selectRules(String owner, String ruleSetId, List<String> ruleIds) {
        boolean hasRuleSet = ruleSetId != null && !ruleSetId.isBlank();
        boolean hasRules = ruleIds != null && !ruleIds.isEmpty();
        if (hasRuleSet == hasRules) {
            throw invalid("Choose one enabled rule set or one or more rules");
        }

        if (hasRuleSet) {
            List<RuleSnapshot> snapshot = rules.snapshot(owner, ruleSetId);
            return new SelectedRules(
                    ruleSetId, rules.ownedSet(ruleSetId, owner).name, rules.write(snapshot));
        }

        List<RuleSnapshot> snapshot = rules.snapshotRules(owner, ruleIds);
        return new SelectedRules(DIRECT_RULES_ID, DIRECT_RULES_NAME, rules.write(snapshot));
    }

    private SelectedRules selectRerunRules(String owner, ReviewEntity original, RerunInput input) {
        String mode = input == null || input.ruleMode() == null ? null : input.ruleMode().trim();
        if (!RERUN_RULE_MODES.contains(mode)) {
            throw invalid("Choose original rules, an enabled rule set, or one or more rules");
        }

        if ("ORIGINAL".equals(mode)) {
            if ((input.ruleSetId() != null && !input.ruleSetId().isBlank())
                    || (input.ruleIds() != null && !input.ruleIds().isEmpty())) {
                throw invalid("Original reruns cannot include a current rule selection");
            }
            try {
                rules.readSnapshot(original.ruleSnapshotJson);
                return new SelectedRules(
                        original.ruleSetId, original.ruleSetName, original.ruleSnapshotJson);
            } catch (IllegalStateException e) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Original rule snapshot is unavailable");
            }
        }

        if ("CURRENT_RULES".equals(mode)) {
            if (input.ruleSetId() != null && !input.ruleSetId().isBlank()) {
                throw invalid("Choose either an enabled rule set or individual rules");
            }
            return selectRules(owner, null, input.ruleIds());
        }

        if (input.ruleIds() != null && !input.ruleIds().isEmpty()) {
            throw invalid("Choose either an enabled rule set or individual rules");
        }

        if (input.ruleSetId() == null || input.ruleSetId().isBlank()) {
            throw invalid("Select an enabled rule set");
        }

        List<RuleSnapshot> snapshot = rules.snapshot(owner, input.ruleSetId());
        String ruleSetName = rules.ownedSet(input.ruleSetId(), owner).name;
        return new SelectedRules(input.ruleSetId(), ruleSetName, rules.write(snapshot));
    }

    private ReviewEntity newRerun(String owner, ReviewEntity original, SelectedRules rerunRules) {
        ensureCapacity(owner);

        ReviewEntity rerun =
                new ReviewEntity(
                        UUID.randomUUID().toString(),
                        owner,
                        original.inputType,
                        rerunRules.ruleSetId());
        rerun.parentReviewId =
                original.parentReviewId == null ? original.id : original.parentReviewId;
        rerun.ruleSetName = rerunRules.ruleSetName();
        rerun.ruleSnapshotJson = rerunRules.snapshotJson();
        rerun.inputHash = sha256(original.inputHash + "\n" + rerun.ruleSnapshotJson);
        return rerun;
    }

    /**
     * Returns one owner's original review groups using bounded pagination.
     *
     * @param owner authenticated application user ID
     * @param page zero-based page number
     * @param size page size, between 1 and 100
     * @return owner-scoped original-review page
     * @throws ResponseStatusException when pagination is outside the accepted range
     */
    public ReviewPage list(String owner, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw invalid("Invalid pagination");
        }
        var result =
                reviews.findByOwnerIdAndParentReviewIdIsNull(
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
     * Lists an original review's owned reruns in chronological order.
     *
     * @param owner authenticated application user ID
     * @param id original or rerun review identifier
     * @return reviews in the same rerun group, excluding the original review
     * @throws ResponseStatusException when the review is missing or belongs to another user
     */
    public List<ReviewView> reruns(String owner, String id) {
        ReviewEntity review = owned(owner, id);
        String originalId = review.parentReviewId == null ? review.id : review.parentReviewId;
        owned(owner, originalId);

        return reviews.findByOwnerIdAndParentReviewIdOrderByCreatedAtAsc(owner, originalId).stream()
                .map(this::view)
                .toList();
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
     * Deletes a terminal review and all findings and feedback stored with it.
     *
     * @param owner authenticated application user ID
     * @param id review identifier
     * @throws ResponseStatusException when the review is missing, foreign, or still running
     */
    @Transactional
    public void delete(String owner, String id) {
        ReviewEntity review = owned(owner, id);
        if (Set.of("QUEUED", "RUNNING").contains(review.status)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Cancel the review before deleting it");
        }

        if (review.parentReviewId == null
                && reviews.countByOwnerIdAndParentReviewId(owner, review.id) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Delete reruns before deleting the original review");
        }

        findings.deleteByReviewId(review.id);
        reviews.delete(review);
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

    /**
     * Links reruns created before review grouping was introduced when their stored input hashes
     * identify one unambiguous original review.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void linkLegacyReruns() {
        List<ReviewEntity> allReviews = new ArrayList<>(reviews.findAll());
        allReviews.sort(Comparator.comparing(review -> review.createdAt));
        boolean changed = false;

        for (ReviewEntity review : allReviews) {
            if (review.parentReviewId != null
                    || review.inputHash == null
                    || review.ruleSnapshotJson == null) {
                continue;
            }

            List<ReviewEntity> directParents =
                    allReviews.stream()
                            .filter(candidate -> candidate != review)
                            .filter(candidate -> review.ownerId.equals(candidate.ownerId))
                            .filter(candidate -> candidate.inputHash != null)
                            .filter(
                                    candidate ->
                                            review.inputHash.equals(
                                                    sha256(
                                                            candidate.inputHash
                                                                    + "\n"
                                                                    + review.ruleSnapshotJson)))
                            .toList();
            if (directParents.size() == 1) {
                ReviewEntity parent = directParents.get(0);
                review.parentReviewId =
                        parent.parentReviewId == null ? parent.id : parent.parentReviewId;
                changed = true;
            }
        }

        if (changed) {
            reviews.saveAll(allReviews);
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
                r.parentReviewId,
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
                r.aiEvaluatedFiles,
                r.warning,
                r.error,
                r.createdAt,
                r.updatedAt,
                reviews.countByOwnerIdAndParentReviewId(r.ownerId, r.id),
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
