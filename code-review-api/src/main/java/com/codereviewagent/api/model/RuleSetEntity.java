package com.codereviewagent.api.model;

import jakarta.persistence.*;
import java.time.Instant;

/** User-owned collection of rule IDs persisted as JSON for stable ordering. */
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
    /** Creates a new rule set owned by the supplied application user.
     * @param id rule-set identifier
     * @param ownerId owning application user ID
     */
    public RuleSetEntity(String id, String ownerId) {
        this.id = id; this.ownerId = ownerId; this.createdAt = Instant.now();
    }
}
