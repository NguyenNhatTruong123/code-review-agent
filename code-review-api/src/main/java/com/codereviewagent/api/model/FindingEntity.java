package com.codereviewagent.api.model;

import jakarta.persistence.*;

import java.time.Instant;

/** Finding with source provenance; feedback is stored independently after generation. */
@Entity
@Table(name = "findings")
public class FindingEntity {
    @Id public String id;

    @Column(nullable = false)
    public String reviewId;

    @Column(nullable = false)
    public String ruleId;

    public int ruleVersion;
    public String severity;
    public String title;

    @Column(length = 4000)
    public String explanation;

    @Column(length = 1000)
    public String evidence;

    @Column(length = 4000)
    public String suggestedFix;

    public String filePath;
    public Integer lineStart;
    public Integer lineEnd;
    public String source;
    public String feedback;
    public Instant feedbackAt;

    protected FindingEntity() {}

    /**
     * Creates a finding linked to one review and the rule that produced it.
     *
     * @param id finding identifier
     * @param reviewId owning review identifier
     * @param ruleId rule that produced the finding
     */
    public FindingEntity(String id, String reviewId, String ruleId) {
        this.id = id;
        this.reviewId = reviewId;
        this.ruleId = ruleId;
    }
}
