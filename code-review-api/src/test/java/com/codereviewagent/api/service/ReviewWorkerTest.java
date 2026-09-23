package com.codereviewagent.api.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.codereviewagent.ai.service.AiReviewService;
import com.codereviewagent.ai.service.StaticReviewService;
import com.codereviewagent.api.model.FindingEntity;
import com.codereviewagent.api.model.ReviewEntity;
import com.codereviewagent.api.repository.FindingRepository;
import com.codereviewagent.api.repository.ReviewRepository;
import com.codereviewagent.api.service.RuleService.RuleSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReviewWorkerTest {
    @Test void staticReviewCreatesFindingAndCompletes() throws Exception {
        ReviewRepository reviews = mock(ReviewRepository.class);
        FindingRepository findings = mock(FindingRepository.class);
        GitHubService github = mock(GitHubService.class);
        AiReviewService ai = mock(AiReviewService.class);
        ReviewEntity review = new ReviewEntity("review", "owner", "PASTE", "set");
        review.ruleSnapshotJson = new ObjectMapper().writeValueAsString(List.of(
                new RuleSnapshot("rule", 2, "No console", "LOW", "JAVASCRIPT", "Remove console", "console.log(", "Use logger")));
        when(reviews.findById("review")).thenReturn(Optional.of(review));
        when(reviews.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ReviewWorker worker = new ReviewWorker(reviews, findings, github, new StaticReviewService(), ai, new ObjectMapper());
        worker.paste("review", "const x = 1;\nconsole.log(x);", "x.js", "JAVASCRIPT");
        assertEquals("COMPLETED", review.status);
        assertEquals(1, review.scannedFiles);
        verify(findings).save(argThat(f -> f.lineStart == 2 && f.ruleVersion == 2 && f.source.equals("STATIC")));
    }

    @Test void semanticOnlyWithoutProviderFailsHonestly() throws Exception {
        ReviewRepository reviews = mock(ReviewRepository.class);
        ReviewEntity review = new ReviewEntity("review", "owner", "PASTE", "set");
        review.ruleSnapshotJson = new ObjectMapper().writeValueAsString(List.of(
                new RuleSnapshot("rule", 1, "Naming", "LOW", "ALL", "Check names", null, "Rename")));
        when(reviews.findById("review")).thenReturn(Optional.of(review));
        when(reviews.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ReviewWorker worker = new ReviewWorker(reviews, mock(FindingRepository.class), mock(GitHubService.class),
                new StaticReviewService(), mock(AiReviewService.class), new ObjectMapper());
        worker.paste("review", "class X {}", "X.java", "JAVA");
        assertEquals("FAILED", review.status);
        assertTrue(review.warning.contains("AI unavailable"));
    }

    @Test void acceptsValidatedSemanticFinding() throws Exception {
        ReviewRepository reviews = mock(ReviewRepository.class);
        FindingRepository findings = mock(FindingRepository.class);
        AiReviewService ai = mock(AiReviewService.class);
        ReviewEntity review = new ReviewEntity("review", "owner", "PASTE", "set");
        review.ruleSnapshotJson = new ObjectMapper().writeValueAsString(List.of(
                new RuleSnapshot("rule", 1, "Naming", "MEDIUM", "JAVA", "Flag TODO", null, "Resolve it")));
        when(reviews.findById("review")).thenReturn(Optional.of(review));
        when(reviews.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ai.available()).thenReturn(true);
        when(ai.review(any(), any(), any(), any())).thenReturn(List.of(
                new AiReviewService.Candidate("rule", "Unfinished", "TODO marker", "TODO", "Resolve it", 1, 1)));
        ReviewWorker worker = new ReviewWorker(reviews, findings, mock(GitHubService.class),
                new StaticReviewService(), ai, new ObjectMapper());
        worker.paste("review", "// TODO", "X.java", "JAVA");
        assertEquals("COMPLETED", review.status);
        verify(findings).save(argThat(f -> f.source.equals("AI") && f.ruleId.equals("rule")));
    }

    @Test void failsWhenGitHubArchiveCannotBeLoaded() throws Exception {
        ReviewRepository reviews = mock(ReviewRepository.class);
        GitHubService github = mock(GitHubService.class);
        ReviewEntity review = new ReviewEntity("review", "owner", "GITHUB", "set");
        when(reviews.findById("review")).thenReturn(Optional.of(review));
        when(reviews.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var repo = new GitHubService.Repo("acme", "repo");
        when(github.archive(repo, "a".repeat(40))).thenThrow(new IOException("Repository exceeds source file limit"));
        ReviewWorker worker = new ReviewWorker(reviews, mock(FindingRepository.class), github,
                new StaticReviewService(), mock(AiReviewService.class), new ObjectMapper());
        worker.repository("review", repo, "a".repeat(40));
        assertEquals("FAILED", review.status);
        assertEquals("Repository exceeds source file limit", review.error);
    }

    @Test void doesNotStartCancelledReview() {
        ReviewRepository reviews = mock(ReviewRepository.class);
        ReviewEntity review = new ReviewEntity("review", "owner", "PASTE", "set");
        review.status = "CANCELLED";
        when(reviews.findById("review")).thenReturn(Optional.of(review));
        FindingRepository findings = mock(FindingRepository.class);
        ReviewWorker worker = new ReviewWorker(reviews, findings, mock(GitHubService.class),
                new StaticReviewService(), mock(AiReviewService.class), new ObjectMapper());
        worker.paste("review", "// TODO", "X.java", "JAVA");
        assertEquals("CANCELLED", review.status);
        verifyNoInteractions(findings);
    }

    @Test void validatesAiEvidenceAndLineRange() {
        var valid = new AiReviewService.Candidate("rule", "Title", "Explanation", "TODO", "Fix", 2, 2);
        assertTrue(ReviewWorker.validCandidate(valid, "first\nTODO", 2));
        assertFalse(ReviewWorker.validCandidate(valid, "TODO\nfirst", 2));
        assertFalse(ReviewWorker.validCandidate(new AiReviewService.Candidate("rule", "Title", "Explanation", "TODO", "Fix", 3, 3), "first\nTODO", 2));
        assertFalse(ReviewWorker.validCandidate(new AiReviewService.Candidate("rule", "", "Explanation", "TODO", "Fix", 2, 2), "first\nTODO", 2));
        assertFalse(ReviewWorker.validCandidate(new AiReviewService.Candidate("rule", "Title", "Explanation", "password=abcdef123456", "Fix", 1, 1), "password=abcdef123456", 1));
    }

    @Test void matchesAllOrNamedLanguage() {
        assertTrue(ReviewWorker.languageMatches("ALL", "JAVA"));
        assertTrue(ReviewWorker.languageMatches("JAVA, PYTHON", "PYTHON"));
        assertFalse(ReviewWorker.languageMatches("JAVA", "PYTHON"));
    }
}
