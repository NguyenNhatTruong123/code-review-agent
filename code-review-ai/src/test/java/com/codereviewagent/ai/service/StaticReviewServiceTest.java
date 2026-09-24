package com.codereviewagent.ai.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.List;

class StaticReviewServiceTest {
    private final StaticReviewService service = new StaticReviewService();

    @Test
    void findsExactLiteralAndOriginalLine() {
        var matches =
                service.review(
                        "first\nconsole.log('x');\nlast",
                        List.of(new StaticReviewService.Rule("console", "console.log(")));
        assertEquals(1, matches.size());
        assertEquals("console", matches.get(0).ruleId());
        assertEquals(2, matches.get(0).line());
        assertEquals("console.log(", matches.get(0).evidence());
    }

    @Test
    void ignoresEmptyPatternAndIsCaseSensitive() {
        assertTrue(
                service
                        .review(
                                "TODO\ntodo",
                                List.of(
                                        new StaticReviewService.Rule("empty", ""),
                                        new StaticReviewService.Rule("todo", "todo")))
                        .stream()
                        .allMatch(match -> match.line() == 2 && match.ruleId().equals("todo")));
    }

    @Test
    void returnsOneMatchPerRuleAndLine() {
        assertEquals(
                2,
                service.review(
                                "TODO TODO\nTODO",
                                List.of(new StaticReviewService.Rule("todo", "TODO")))
                        .size());
    }
}
