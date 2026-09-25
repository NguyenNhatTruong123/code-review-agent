package com.codereviewagent.api.model;

import jakarta.persistence.*;

import java.time.Instant;

/** Review aggregate, including its immutable input identity and rule snapshot metadata. */
@Entity
@Table(
        name = "reviews",
        uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "idempotency_key"}))
public class ReviewEntity {
    @Id public String id;

    @Column(name = "owner_id", nullable = false)
    public String ownerId;

    @Column(nullable = false)
    public String inputType;

    public String repositoryUrl;
    public String requestedRef;
    public String commitSha;
    @Lob public String selectedFilePathsJson;
    public String fileName;
    public String language;

    @Lob public String pastedSource;

    public String parentReviewId;

    @Column(name = "idempotency_key")
    public String idempotencyKey;

    public String inputHash;

    @Column(nullable = false)
    public String ruleSetId;

    public String ruleSetName;
    @Lob public String ruleSnapshotJson;

    @Column(nullable = false)
    public String status;

    public int scannedFiles;
    public int skippedFiles;

    @Column(nullable = false, columnDefinition = "integer default 0")
    public int aiEvaluatedFiles = 0;

    @Column(length = 2000)
    public String warning;

    @Column(length = 2000)
    public String error;

    public Instant createdAt;
    public Instant updatedAt;

    protected ReviewEntity() {}

    /**
     * Creates a queued review with its owner and selected rule set.
     *
     * @param id review identifier
     * @param ownerId owning application user ID
     * @param inputType repository or paste input type
     * @param ruleSetId selected rule-set identifier
     */
    public ReviewEntity(String id, String ownerId, String inputType, String ruleSetId) {
        this.id = id;
        this.ownerId = ownerId;
        this.inputType = inputType;
        this.ruleSetId = ruleSetId;
        this.status = "QUEUED";
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }
}
