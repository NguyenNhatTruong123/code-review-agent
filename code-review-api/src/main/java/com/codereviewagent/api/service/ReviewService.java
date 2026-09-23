package com.codereviewagent.api.service;

import com.codereviewagent.api.model.FindingEntity;
import com.codereviewagent.api.model.ReviewEntity;
import com.codereviewagent.api.repository.FindingRepository;
import com.codereviewagent.api.repository.ReviewRepository;
import com.codereviewagent.api.service.GitHubService.Repo;
import com.codereviewagent.api.service.GitHubService.RepoInfo;
import com.codereviewagent.api.service.RuleService.RuleSnapshot;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReviewService {
    public record RepositoryInput(String repositoryUrl, String ref, String ruleSetId) {}
    public record PasteInput(String code, String fileName, String language, String ruleSetId) {}
    public record ReviewView(String id, String inputType, String repositoryUrl, String requestedRef,
                             String commitSha, String fileName, String language, String ruleSetId,
                             String ruleSetName, String status, int scannedFiles, int skippedFiles,
                             String warning, String error, Instant createdAt, Instant updatedAt,
                             Map<String, Long> summary) {}
    public record FindingView(String id, String ruleId, int ruleVersion, String severity, String title,
                              String explanation, String evidence, String suggestedFix, String filePath,
                              Integer lineStart, Integer lineEnd, String source, String feedback) {}
    public record FindingPage(List<FindingView> items, int total, int page, int size) {}
    public record ReviewPage(List<ReviewView> items, long total, int page, int size) {}

    private static final Set<String> FEEDBACK = Set.of("HELPFUL", "IRRELEVANT", "FALSE_POSITIVE");
    private static final Set<String> LANGUAGES = Set.of("JAVA", "JAVASCRIPT", "TYPESCRIPT", "JSX", "TSX", "PYTHON", "GO", "CSHARP", "RUBY", "PHP", "RUST", "KOTLIN", "SWIFT", "C", "CPP", "VUE", "SQL");
    private final ReviewRepository reviews;
    private final FindingRepository findings;
    private final RuleService rules;
    private final GitHubService github;
    private final ReviewWorker worker;
    private final int maxPasteChars;
    public ReviewService(ReviewRepository reviews, FindingRepository findings, RuleService rules,
                         GitHubService github, ReviewWorker worker, @Value("${app.max-paste-chars}") int maxPasteChars) {
        this.reviews = reviews; this.findings = findings; this.rules = rules;
        this.github = github; this.worker = worker; this.maxPasteChars = maxPasteChars;
    }

    public RepoInfo inspect(String url) { return github.inspect(github.parse(url)); }

    public ReviewView createRepository(String owner, RepositoryInput input, String key) {
        key = key == null || key.isBlank() ? null : key;
        if (input == null || input.ruleSetId() == null) throw invalid("Rule set is required");
        Repo repo = github.parse(input.repositoryUrl());
        RepoInfo info = github.inspect(repo);
        String ref = input.ref() == null || input.ref().isBlank() ? info.defaultBranch() : input.ref().trim();
        String hash = sha256(repo.url() + "\n" + ref + "\n" + input.ruleSetId());
        ReviewEntity existing = previous(owner, key, hash);
        if (existing != null) return view(existing);
        ensureCapacity(owner);
        List<RuleSnapshot> snapshot = rules.snapshot(owner, input.ruleSetId());
        String sha = github.resolveCommit(repo, ref);
        ReviewEntity review = new ReviewEntity(UUID.randomUUID().toString(), owner, "GITHUB", input.ruleSetId());
        review.repositoryUrl = repo.url(); review.requestedRef = ref; review.commitSha = sha;
        review.ruleSetName = rules.ownedSet(input.ruleSetId(), owner).name;
        review.ruleSnapshotJson = rules.write(snapshot); review.idempotencyKey = key; review.inputHash = hash;
        reviews.save(review);
        try { worker.repository(review.id, repo, sha); }
        catch (org.springframework.core.task.TaskRejectedException e) { markQueueFull(review); }
        return view(review);
    }

    public ReviewView createPaste(String owner, PasteInput input, String key) {
        key = key == null || key.isBlank() ? null : key;
        if (input == null || input.code() == null || input.code().isBlank() || input.ruleSetId() == null)
            throw invalid("Code and rule set are required");
        if (input.code().length() > maxPasteChars) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Pasted code is too large");
        String path = input.fileName() == null || input.fileName().isBlank() ? "pasted-code.txt" : input.fileName().trim();
        if (path.length() > 180 || path.contains("/") || path.contains("\\") || path.equals("..")) throw invalid("Invalid file name");
        String language = input.language() == null || input.language().isBlank() ? GitHubService.language(path) : input.language().toUpperCase(java.util.Locale.ROOT);
        if (language == null || !LANGUAGES.contains(language)) throw invalid("Select a supported language");
        String hash = sha256(input.code() + "\n" + path + "\n" + language + "\n" + input.ruleSetId());
        ReviewEntity existing = previous(owner, key, hash);
        if (existing != null) return view(existing);
        ensureCapacity(owner);
        List<RuleSnapshot> snapshot = rules.snapshot(owner, input.ruleSetId());
        ReviewEntity review = new ReviewEntity(UUID.randomUUID().toString(), owner, "PASTE", input.ruleSetId());
        review.fileName = path; review.language = language;
        review.ruleSetName = rules.ownedSet(input.ruleSetId(), owner).name;
        review.ruleSnapshotJson = rules.write(snapshot); review.idempotencyKey = key; review.inputHash = hash;
        reviews.save(review);
        try { worker.paste(review.id, input.code(), path, language); }
        catch (org.springframework.core.task.TaskRejectedException e) { markQueueFull(review); }
        return view(review);
    }

    public ReviewPage list(String owner, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw invalid("Invalid pagination");
        var result = reviews.findByOwnerId(owner, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new ReviewPage(result.getContent().stream().map(this::view).toList(), result.getTotalElements(), page, size);
    }
    public ReviewView get(String owner, String id) { return view(owned(owner, id)); }
    public ReviewEntity owned(String owner, String id) {
        ReviewEntity review = reviews.findById(id).orElseThrow(() -> missing("Review"));
        if (!review.ownerId.equals(owner)) throw missing("Review");
        return review;
    }
    public FindingPage findings(String owner, String id, String severity, String ruleId, String file, int page, int size) {
        owned(owner, id);
        if (page < 0 || size < 1 || size > 100) throw invalid("Invalid pagination");
        List<FindingView> filtered = this.findings.findByReviewIdOrderByFilePathAscLineStartAsc(id).stream()
                .filter(f -> severity == null || severity.isBlank() || severity.equals(f.severity))
                .filter(f -> ruleId == null || ruleId.isBlank() || ruleId.equals(f.ruleId))
                .filter(f -> file == null || file.isBlank() || file.equals(f.filePath))
                .map(this::view).toList();
        long from = (long) page * size;
        if (from >= filtered.size()) return new FindingPage(List.of(), filtered.size(), page, size);
        return new FindingPage(filtered.subList((int) from, (int) Math.min(from + size, filtered.size())), filtered.size(), page, size);
    }
    public FindingView feedback(String owner, String reviewId, String findingId, String value) {
        owned(owner, reviewId);
        if (value == null || !FEEDBACK.contains(value)) throw invalid("Invalid feedback");
        FindingEntity finding = findings.findById(findingId).orElseThrow(() -> missing("Finding"));
        if (!reviewId.equals(finding.reviewId)) throw missing("Finding");
        finding.feedback = value; finding.feedbackAt = Instant.now();
        return view(findings.save(finding));
    }
    public ReviewView cancel(String owner, String id) {
        ReviewEntity r = owned(owner, id);
        if (!Set.of("QUEUED", "RUNNING").contains(r.status)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Review already finished");
        r.status = "CANCELLED"; r.updatedAt = Instant.now();
        return view(reviews.save(r));
    }
    @EventListener(ApplicationReadyEvent.class)
    public void markInterrupted() {
        for (ReviewEntity r : reviews.findByStatusIn(List.of("QUEUED", "RUNNING"))) {
            r.status = "FAILED"; r.error = "Review was interrupted by a server restart";
            r.updatedAt = Instant.now(); reviews.save(r);
        }
    }
    private ReviewEntity previous(String owner, String key, String hash) {
        if (key == null || key.isBlank()) return null;
        if (key.length() > 100 || !key.matches("[A-Za-z0-9._-]+")) throw invalid("Invalid idempotency key");
        return reviews.findByOwnerIdAndIdempotencyKey(owner, key).map(r -> {
            if (!hash.equals(r.inputHash)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key already used");
            return r;
        }).orElse(null);
    }
    private void ensureCapacity(String owner) {
        if (reviews.countByOwnerIdAndStatusIn(owner, List.of("QUEUED", "RUNNING")) >= 3)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Finish an active review before starting another");
    }
    private void markQueueFull(ReviewEntity review) {
        review.status = "FAILED"; review.error = "Review queue is full; try again later";
        review.updatedAt = Instant.now(); reviews.save(review);
    }
    private ReviewView view(ReviewEntity r) {
        Map<String, Long> summary = findings.findByReviewIdOrderByFilePathAscLineStartAsc(r.id).stream()
                .collect(Collectors.groupingBy(f -> f.severity, Collectors.counting()));
        return new ReviewView(r.id, r.inputType, r.repositoryUrl, r.requestedRef, r.commitSha,
                r.fileName, r.language, r.ruleSetId, r.ruleSetName, r.status, r.scannedFiles,
                r.skippedFiles, r.warning, r.error, r.createdAt, r.updatedAt, summary);
    }
    private FindingView view(FindingEntity f) { return new FindingView(f.id, f.ruleId, f.ruleVersion, f.severity,
            f.title, f.explanation, f.evidence, f.suggestedFix, f.filePath, f.lineStart, f.lineEnd, f.source, f.feedback); }
    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException missing(String resource) { return new ResponseStatusException(HttpStatus.NOT_FOUND, resource + " not found"); }
}
