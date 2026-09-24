package com.codereviewagent.ai.service;

import java.util.ArrayList;
import java.util.List;

/** Deterministic, case-sensitive literal matching; never executes rule text. */
public class StaticReviewService {
    /** Literal rule evaluated against each source line.
     * @param id stable rule identifier
     * @param matchText case-sensitive text to find
     */
    public record Rule(String id, String matchText) {}
    /** Finding produced by a literal match using one-based source line numbering.
     * @param ruleId identifier of the matching rule
     * @param evidence matched literal text
     * @param line one-based source line containing the match
     */
    public record Match(String ruleId, String evidence, int line) {}

    /** Returns one match for each rule occurrence while preserving source order.
     * @param code source text to inspect
     * @param rules literal rules to evaluate
     * @return matches ordered by source line and rule order
     */
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
