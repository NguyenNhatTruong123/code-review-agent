package com.codereviewagent.api.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "rule_sets")
public class RuleSetEntity {
    @Id public String id;
    @Column(nullable = false) public String ownerId;
    @Column(nullable = false) public String name;
    @Column(length = 2000) public String description;
    @Lob public String ruleIdsJson;
    public boolean enabled;
    public Instant createdAt;
    public Instant updatedAt;
    protected RuleSetEntity() {}
    public RuleSetEntity(String id, String ownerId) {
        this.id = id; this.ownerId = ownerId; this.createdAt = Instant.now();
    }
}
