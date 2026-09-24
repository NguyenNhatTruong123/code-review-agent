package com.codereviewagent.api.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.codereviewagent.api.model.FindingEntity;
import com.codereviewagent.api.model.ReviewEntity;
import com.codereviewagent.api.model.RuleSetEntity;
import com.codereviewagent.api.repository.FindingRepository;
import com.codereviewagent.api.repository.ReviewRepository;
import com.codereviewagent.api.service.ReviewService.PasteInput;
import com.codereviewagent.api.service.ReviewService.RepositoryInput;
import com.codereviewagent.api.service.RuleService.RuleSnapshot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

class ReviewServiceTest {
    private ReviewRepository reviews;
    private FindingRepository findings;
    private RuleService rules;
    private GitHubService github;
    private ReviewWorker worker;
    private ReviewService service;

    @BeforeEach
    void setup() {
        reviews = mock(ReviewRepository.class);
        findings = mock(FindingRepository.class);
        rules = mock(RuleService.class);
        github = mock(GitHubService.class);
        worker = mock(ReviewWorker.class);
        service = new ReviewService(reviews, findings, rules, github, worker, 1000);
        when(reviews.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(findings.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(findings.findByReviewIdOrderByFilePathAscLineStartAsc(any())).thenReturn(List.of());
        when(rules.snapshot("owner", "set"))
                .thenReturn(
                        List.of(
                                new RuleSnapshot(
                                        "rule", 1, "Rule", "LOW", "ALL", "Check", "TODO", "Fix")));
        when(rules.ownedSet("set", "owner")).thenReturn(new RuleSetEntity("set", "owner"));
        when(rules.write(any())).thenReturn("[]");
    }

    @Test
    void createsPasteReviewWithDetectedLanguageAndNoStoredSource() {
        var result =
                service.createPaste(
                        "owner", new PasteInput("class X {}", "X.java", null, "set"), null);
        assertEquals("QUEUED", result.status());
        assertEquals("JAVA", result.language());
        verify(worker).paste(eq(result.id()), eq("class X {}"), eq("X.java"), eq("JAVA"));
        verify(reviews)
                .save(argThat(r -> r.inputType.equals("PASTE") && r.ruleSnapshotJson.equals("[]")));
    }

    @Test
    void resolvesRepositoryToCommitBeforeDispatch() {
        var repo = new GitHubService.Repo("acme", "sample");
        when(github.parse("https://github.com/acme/sample")).thenReturn(repo);
        when(github.inspect(repo))
                .thenReturn(new GitHubService.RepoInfo(repo.url(), "main", List.of("main")));
        when(github.resolveCommit(repo, "main")).thenReturn("a".repeat(40));
        var result =
                service.createRepository(
                        "owner", new RepositoryInput(repo.url(), null, "set"), null);
        assertEquals("GITHUB", result.inputType());
        assertEquals("main", result.requestedRef());
        assertEquals("a".repeat(40), result.commitSha());
        verify(worker).repository(result.id(), repo, "a".repeat(40));
    }

    @Test
    void preventsIdempotencyKeyReuseWithDifferentInput() {
        ReviewEntity prior = new ReviewEntity("prior", "owner", "PASTE", "set");
        prior.inputHash = "different";
        when(reviews.findByOwnerIdAndIdempotencyKey("owner", "key")).thenReturn(Optional.of(prior));
        assertThrows(
                ResponseStatusException.class,
                () ->
                        service.createPaste(
                                "owner",
                                new PasteInput("class X {}", "X.java", null, "set"),
                                "key"));
        verify(worker, never()).paste(any(), any(), any(), any());
    }

    @Test
    void rejectsMissingLanguageAndOversizedPaste() {
        assertThrows(
                ResponseStatusException.class,
                () ->
                        service.createPaste(
                                "owner", new PasteInput("x", "file.txt", null, "set"), null));
        assertThrows(
                ResponseStatusException.class,
                () ->
                        service.createPaste(
                                "owner",
                                new PasteInput("x".repeat(1001), "x.java", null, "set"),
                                null));
    }

    @Test
    void neverShowsAnotherUsersReview() {
        when(reviews.findById("review"))
                .thenReturn(Optional.of(new ReviewEntity("review", "other", "PASTE", "set")));
        assertThrows(ResponseStatusException.class, () -> service.get("owner", "review"));
        verify(findings, never()).findByReviewIdOrderByFilePathAscLineStartAsc("review");
    }

    @Test
    void validatesFeedbackAndFindingOwnership() {
        ReviewEntity review = new ReviewEntity("review", "owner", "PASTE", "set");
        FindingEntity finding = new FindingEntity("finding", "other-review", "rule");
        when(reviews.findById("review")).thenReturn(Optional.of(review));
        when(findings.findById("finding")).thenReturn(Optional.of(finding));
        assertThrows(
                ResponseStatusException.class,
                () -> service.feedback("owner", "review", "finding", "HELPFUL"));
        finding.reviewId = "review";
        assertEquals(
                "HELPFUL", service.feedback("owner", "review", "finding", "HELPFUL").feedback());
        assertThrows(
                ResponseStatusException.class,
                () -> service.feedback("owner", "review", "finding", "BAD"));
    }

    @Test
    void paginatesAndFiltersFindings() {
        ReviewEntity review = new ReviewEntity("review", "owner", "PASTE", "set");
        when(reviews.findById("review")).thenReturn(Optional.of(review));
        FindingEntity a = new FindingEntity("a", "review", "rule");
        a.severity = "HIGH";
        a.filePath = "A.java";
        FindingEntity b = new FindingEntity("b", "review", "rule");
        b.severity = "LOW";
        b.filePath = "B.java";
        when(findings.findByReviewIdOrderByFilePathAscLineStartAsc("review"))
                .thenReturn(List.of(a, b));
        var page = service.findings("owner", "review", "HIGH", null, null, 0, 50);
        assertEquals(1, page.total());
        assertEquals("a", page.items().get(0).id());
        assertThrows(
                ResponseStatusException.class,
                () -> service.findings("owner", "review", null, null, null, -1, 50));
    }

    @Test
    void cancelOnlyActiveReview() {
        ReviewEntity review = new ReviewEntity("review", "owner", "PASTE", "set");
        when(reviews.findById("review")).thenReturn(Optional.of(review));
        assertEquals("CANCELLED", service.cancel("owner", "review").status());
        assertThrows(ResponseStatusException.class, () -> service.cancel("owner", "review"));
    }

    @Test
    void marksInterruptedJobsFailedAfterRestart() {
        ReviewEntity queued = new ReviewEntity("queued", "owner", "PASTE", "set");
        ReviewEntity running = new ReviewEntity("running", "owner", "GITHUB", "set");
        running.status = "RUNNING";
        when(reviews.findByStatusIn(List.of("QUEUED", "RUNNING")))
                .thenReturn(List.of(queued, running));
        service.markInterrupted();
        assertEquals("FAILED", queued.status);
        assertEquals("FAILED", running.status);
        verify(reviews, times(2)).save(any());
    }

    @Test
    void listsOnlyOnePageOfCurrentUsersReviews() {
        ReviewEntity review = new ReviewEntity("review", "owner", "PASTE", "set");
        when(reviews.findByOwnerId(eq("owner"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(review)));
        var page = service.list("owner", 0, 50);
        assertEquals(1, page.total());
        assertEquals("review", page.items().get(0).id());
        assertThrows(ResponseStatusException.class, () -> service.list("owner", 0, 101));
    }
}
