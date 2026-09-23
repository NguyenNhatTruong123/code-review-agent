package com.codereviewagent.api.service;

import com.codereviewagent.ai.service.AiReviewService;
import com.codereviewagent.ai.service.StaticReviewService;
import com.codereviewagent.api.model.FindingEntity;
import com.codereviewagent.api.model.ReviewEntity;
import com.codereviewagent.api.repository.FindingRepository;
import com.codereviewagent.api.repository.ReviewRepository;
import com.codereviewagent.api.service.GitHubService.SourceArchive;
import com.codereviewagent.api.service.GitHubService.SourceFile;
import com.codereviewagent.api.service.RuleService.RuleSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** Executes queued reviews asynchronously and persists validated findings. */
@Service
public class ReviewWorker {
    private static final Logger LOG = LoggerFactory.getLogger(ReviewWorker.class);
    private final ReviewRepository reviews;
    private final FindingRepository findings;
    private final GitHubService github;
    private final StaticReviewService staticReview;
    private final AiReviewService ai;
    private final ObjectMapper mapper;
    /** Creates an asynchronous worker with persistence and review-engine collaborators.
     * @param reviews review repository
     * @param findings finding repository
     * @param github GitHub archive service
     * @param staticReview deterministic literal-review engine
     * @param ai semantic-review provider adapter
     * @param mapper JSON mapper for persisted rule snapshots
     */
    public ReviewWorker(ReviewRepository reviews, FindingRepository findings, GitHubService github,
                        StaticReviewService staticReview, AiReviewService ai, ObjectMapper mapper) {
        this.reviews = reviews; this.findings = findings; this.github = github;
        this.staticReview = staticReview; this.ai = ai; this.mapper = mapper;
    }

    /** Processes a pinned GitHub commit; the API has already created the review record.
     * @param reviewId queued review identifier
     * @param repo parsed repository identity
     * @param sha immutable commit SHA
     */
    @Async("reviewExecutor")
    public void repository(String reviewId, GitHubService.Repo repo, String sha) {
        try {
            if (!start(reviewId)) return;
            SourceArchive archive = github.archive(repo, sha);
            ReviewEntity review = reviews.findById(reviewId).orElseThrow();
            review.skippedFiles = archive.skippedFiles();
            review.warning = limit(String.join("; ", archive.warnings()), 2000);
            reviews.save(review);
            analyze(reviewId, archive.files());
        } catch (Exception e) { fail(reviewId, e); }
    }

    /** Processes pasted code using the same rule and finding pipeline as repository reviews.
     * @param reviewId queued review identifier
     * @param code pasted source text
     * @param path logical source filename
     * @param language normalized source language
     */
    @Async("reviewExecutor")
    public void paste(String reviewId, String code, String path, String language) {
        try {
            if (!start(reviewId)) return;
            analyze(reviewId, List.of(new SourceFile(path, language, code)));
        } catch (Exception e) { fail(reviewId, e); }
    }

    private boolean start(String id) {
        ReviewEntity review = reviews.findById(id).orElseThrow();
        if (!"QUEUED".equals(review.status)) return false;
        review.status = "RUNNING"; review.updatedAt = Instant.now(); reviews.save(review);
        return true;
    }

    private void analyze(String id, List<SourceFile> files) throws Exception {
        ReviewEntity review = reviews.findById(id).orElseThrow();
        List<RuleSnapshot> rules = Arrays.asList(mapper.readValue(review.ruleSnapshotJson, RuleSnapshot[].class));
        List<String> warnings = new ArrayList<>();
        if (review.warning != null && !review.warning.isBlank()) warnings.add(review.warning);
        Set<String> dedupe = new HashSet<>();
        int scanned = 0;
        boolean evaluated = false;
        for (SourceFile file : files) {
            // Re-read status before each file so cancellation stops work between source files.
            if ("CANCELLED".equals(reviews.findById(id).orElseThrow().status)) return;
            List<RuleSnapshot> applicable = rules.stream().filter(rule -> languageMatches(rule.languages(), file.language())).toList();
            List<StaticReviewService.Rule> literal = applicable.stream()
                    .filter(rule -> rule.matchText() != null && !rule.matchText().isBlank())
                    .map(rule -> new StaticReviewService.Rule(rule.id(), rule.matchText())).toList();
            if (!literal.isEmpty()) evaluated = true;
            for (StaticReviewService.Match match : staticReview.review(file.code(), literal)) {
                RuleSnapshot rule = findRule(applicable, match.ruleId());
                saveFinding(id, file.path(), match.line(), match.line(), rule, rule.name(),
                        rule.instruction(), match.evidence(), rule.suggestedFix(), "STATIC", dedupe);
            }
            List<RuleSnapshot> semantic = applicable.stream().filter(rule -> rule.matchText() == null || rule.matchText().isBlank()).toList();
            if (!semantic.isEmpty()) {
                // Literal rules are deterministic; only rules without matchText are sent to the AI adapter.
                if (!ai.available()) warnings.add("AI unavailable: semantic rules skipped for " + file.path());
                else {
                    try {
                        List<AiReviewService.RuleSpec> specs = semantic.stream()
                                .map(rule -> new AiReviewService.RuleSpec(rule.id(), rule.instruction())).toList();
                        int lines = file.code().split("\n", -1).length;
                        for (AiReviewService.Candidate candidate : ai.review(file.path(), file.language(), file.code(), specs)) {
                            RuleSnapshot rule = semantic.stream().filter(r -> r.id().equals(candidate.ruleId())).findFirst().orElse(null);
                            if (rule == null || !validCandidate(candidate, file.code(), lines)) {
                                warnings.add("Discarded invalid AI finding for " + file.path()); continue;
                            }
                            saveFinding(id, file.path(), candidate.lineStart(), candidate.lineEnd(), rule,
                                    candidate.title(), candidate.explanation(), candidate.evidence(),
                                    candidate.suggestedFix(), "AI", dedupe);
                        }
                        evaluated = true;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw e;
                    } catch (Exception e) {
                        warnings.add("AI review failed for " + file.path());
                        LOG.warn("AI review failed for review {}: {}", id, e.getClass().getSimpleName());
                    }
                }
            }
            scanned++;
            review = reviews.findById(id).orElseThrow();
            review.scannedFiles = scanned; review.updatedAt = Instant.now(); reviews.save(review);
        }
        review = reviews.findById(id).orElseThrow();
        if ("CANCELLED".equals(review.status)) return;
        review.warning = limit(String.join("; ", warnings.stream().distinct().limit(20).toList()), 2000);
        review.status = !evaluated ? "FAILED" : warnings.isEmpty() ? "COMPLETED" : "COMPLETED_WITH_WARNINGS";
        if (!evaluated) review.error = "No rule could be evaluated for the selected source";
        review.updatedAt = Instant.now(); reviews.save(review);
    }

    static boolean languageMatches(String languages, String language) {
        return Arrays.stream(languages.split(",")).map(String::trim)
                .anyMatch(value -> value.equals("ALL") || value.equalsIgnoreCase(language));
    }
    private static RuleSnapshot findRule(List<RuleSnapshot> rules, String id) {
        return rules.stream().filter(rule -> rule.id().equals(id)).findFirst().orElseThrow();
    }
    static boolean validCandidate(AiReviewService.Candidate c, String code, int lines) {
        // AI output is untrusted: require valid bounds and exact evidence from the reviewed source.
        if (c.ruleId() == null || c.title() == null || c.title().isBlank() || c.explanation() == null
                || c.explanation().isBlank() || c.suggestedFix() == null || c.suggestedFix().isBlank()
                || c.evidence() == null || c.evidence().isBlank() || c.evidence().length() > 500
                || c.lineStart() == null || c.lineEnd() == null || c.lineStart() < 1
                || c.lineEnd() < c.lineStart() || c.lineEnd() > lines) return false;
        String[] sourceLines = code.split("\n", -1);
        String span = String.join("\n", Arrays.copyOfRange(sourceLines, c.lineStart() - 1, c.lineEnd()));
        return span.contains(c.evidence()) && !looksSecret(c.evidence())
                && !looksSecret(c.title()) && !looksSecret(c.explanation()) && !looksSecret(c.suggestedFix());
    }
    private static boolean looksSecret(String evidence) {
        return evidence.matches("(?is).*(api[_-]?key|secret|password|token)\\s*[:=]\\s*['\"]?[^\s'\"]{8,}.*");
    }
    private void saveFinding(String reviewId, String path, Integer start, Integer end, RuleSnapshot rule,
                             String title, String explanation, String evidence, String fix, String source, Set<String> dedupe) {
        if ("CANCELLED".equals(reviews.findById(reviewId).orElseThrow().status)) return;
        String key = rule.id() + ":" + path + ":" + start + ":" + end;
        if (!dedupe.add(key)) return;
        FindingEntity f = new FindingEntity(UUID.randomUUID().toString(), reviewId, rule.id());
        f.ruleVersion = rule.version(); f.severity = rule.severity(); f.title = limit(title, 200);
        f.explanation = limit(explanation, 4000); f.evidence = limit(evidence, 1000);
        f.suggestedFix = limit(fix == null ? "Review and update this code" : fix, 4000);
        f.filePath = path; f.lineStart = start; f.lineEnd = end; f.source = source;
        findings.save(f);
    }
    private static String limit(String text, int max) { return text == null ? "" : text.substring(0, Math.min(text.length(), max)); }
    private void fail(String id, Exception e) {
        if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        ReviewEntity review = reviews.findById(id).orElse(null);
        if (review == null || "CANCELLED".equals(review.status)) return;
        LOG.warn("Review {} failed: {}", id, e.getClass().getSimpleName());
        review.status = "FAILED";
        review.error = e instanceof IOException ? e.getMessage() : "Review failed while loading or analyzing source code";
        review.updatedAt = Instant.now(); reviews.save(review);
    }
}
