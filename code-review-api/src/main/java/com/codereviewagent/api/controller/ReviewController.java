package com.codereviewagent.api.controller;

import com.codereviewagent.api.service.CurrentUser;
import com.codereviewagent.api.service.GitHubService.RepoInfo;
import com.codereviewagent.api.service.ReviewService;
import com.codereviewagent.api.service.ReviewService.RepositoryInput;
import com.codereviewagent.api.service.ReviewService.PasteInput;
import com.codereviewagent.api.service.ReviewService.ReviewView;
import com.codereviewagent.api.service.ReviewService.FindingView;
import com.codereviewagent.api.service.ReviewService.FindingPage;
import com.codereviewagent.api.service.ReviewService.ReviewPage;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ReviewController {
    private final ReviewService reviews;
    private final CurrentUser current;
    public ReviewController(ReviewService reviews, CurrentUser current) { this.reviews = reviews; this.current = current; }
    @GetMapping("/github/inspect") public RepoInfo inspect(@RequestParam String url) { return reviews.inspect(url); }
    @PostMapping("/reviews/repository") @ResponseStatus(HttpStatus.ACCEPTED)
    public ReviewView repository(Principal p, @RequestBody RepositoryInput body,
                                 @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return reviews.createRepository(current.id(p), body, key);
    }
    @PostMapping("/reviews/paste") @ResponseStatus(HttpStatus.ACCEPTED)
    public ReviewView paste(Principal p, @RequestBody PasteInput body,
                            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return reviews.createPaste(current.id(p), body, key);
    }
    @GetMapping("/reviews") public ReviewPage list(Principal p, @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "50") int size) {
        return reviews.list(current.id(p), page, size);
    }
    @GetMapping("/reviews/{id}") public ReviewView get(Principal p, @PathVariable String id) { return reviews.get(current.id(p), id); }
    @PostMapping("/reviews/{id}/cancel") public ReviewView cancel(Principal p, @PathVariable String id) { return reviews.cancel(current.id(p), id); }
    @GetMapping("/reviews/{id}/findings")
    public FindingPage findings(Principal p, @PathVariable String id,
                                @RequestParam(required = false) String severity,
                                @RequestParam(required = false) String ruleId,
                                @RequestParam(required = false) String file,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "50") int size) {
        return reviews.findings(current.id(p), id, severity, ruleId, file, page, size);
    }
    @PostMapping("/reviews/{id}/findings/{findingId}/feedback")
    public FindingView feedback(Principal p, @PathVariable String id, @PathVariable String findingId,
                                @RequestBody Map<String, String> body) {
        return reviews.feedback(current.id(p), id, findingId, body.get("feedback"));
    }
}
