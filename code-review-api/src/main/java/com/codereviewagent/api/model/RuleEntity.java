package com.codereviewagent.api.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "review_rules")
public class RuleEntity {
    @Id public String id;
    @Column(nullable = false) public String ownerId;
    @Column(nullable = false) public String name;
    @Column(length = 2000) public String description;
    public String category;
    @Column(nullable = false) public String severity;
    @Column(nullable = false) public String languages;
    @Column(length = 4000, nullable = false) public String instruction;
    @Column(length = 500) public String matchText;
    @Column(length = 2000) public String suggestedFix;
    public boolean enabled;
    public int version;
    public Instant createdAt;
    public Instant updatedAt;
    protected RuleEntity() {}
    public RuleEntity(String id, String ownerId) {
        this.id = id; this.ownerId = ownerId; this.createdAt = Instant.now();
    }
}
