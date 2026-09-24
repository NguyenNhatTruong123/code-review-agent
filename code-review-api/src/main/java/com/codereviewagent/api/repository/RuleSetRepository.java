package com.codereviewagent.api.repository;

import com.codereviewagent.api.model.RuleSetEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Provides owner-scoped persistence queries for rule sets. */
public interface RuleSetRepository extends JpaRepository<RuleSetEntity, String> {
    /**
     * Returns only one owner's rule sets in name order.
     *
     * @param ownerId owning application user ID
     * @return rule sets ordered by name
     */
    List<RuleSetEntity> findByOwnerIdOrderByNameAsc(String ownerId);
}
