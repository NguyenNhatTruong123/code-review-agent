package com.codereviewagent.api.repository;

import com.codereviewagent.api.model.FindingEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Provides deterministic finding retrieval and review-scoped cleanup queries. */
public interface FindingRepository extends JpaRepository<FindingEntity, String> {
    /**
     * Uses deterministic path and line ordering for pagination and display.
     *
     * @param reviewId owning review identifier
     * @return findings ordered by path and starting line
     */
    List<FindingEntity> findByReviewIdOrderByFilePathAscLineStartAsc(String reviewId);

    /**
     * Removes findings when a review is deleted.
     *
     * @param reviewId owning review identifier
     */
    void deleteByReviewId(String reviewId);
}
