package com.codereviewagent.api.repository;

import com.codereviewagent.api.model.RuleEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Provides owner-scoped persistence queries for review rules. */
public interface RuleRepository extends JpaRepository<RuleEntity, String> {
    /**
     * Returns only one owner's rules in the stable order used by the UI.
     *
     * @param ownerId owning application user ID
     * @return rules ordered by name
     */
    List<RuleEntity> findByOwnerIdOrderByNameAsc(String ownerId);
}
