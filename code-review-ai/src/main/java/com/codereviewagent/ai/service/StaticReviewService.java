package com.codereviewagent.ai.service;

import java.util.ArrayList;
import java.util.List;

/** Deterministic, case-sensitive literal matching; never executes rule text. */
public class StaticReviewService {
    public record Rule(String id, String matchText) {}
    public record Match(String ruleId, String evidence, int line) {}

    public List<Match> review(String code, List<Rule> rules) {
        List<Match> findings = new ArrayList<>();
        String[] lines = code.split("\n", -1);
        for (int line = 0; line < lines.length; line++) {
            for (Rule rule : rules) {
                if (rule.matchText() == null || rule.matchText().isBlank()) continue;
                if (lines[line].contains(rule.matchText())) {
                    findings.add(new Match(rule.id(), rule.matchText(), line + 1));
                }
            }
        }
        return findings;
    }
}
