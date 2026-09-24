package com.codereviewagent.api.repository;

import com.codereviewagent.api.model.ReviewEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Provides review history, active-work, and idempotency persistence queries. */
public interface ReviewRepository extends JpaRepository<ReviewEntity, String> {
    /**
     * Paginates reviews while keeping ownership in the query boundary.
     *
     * @param ownerId owning application user ID
     * @param pageable page and sort request
     * @return owner-scoped review page
     */
    Page<ReviewEntity> findByOwnerIdAndParentReviewIdIsNull(String ownerId, Pageable pageable);

    /**
     * Lists reruns belonging to one original review in chronological order.
     *
     * @param ownerId owning application user ID
     * @param parentReviewId original review identifier
     * @return owner-scoped reruns
     */
    List<ReviewEntity> findByOwnerIdAndParentReviewIdOrderByCreatedAtAsc(
            String ownerId, String parentReviewId);

    /**
     * Counts reruns so review-history controls can be rendered without loading them all.
     *
     * @param ownerId owning application user ID
     * @param parentReviewId original review identifier
     * @return number of owner-scoped reruns
     */
    long countByOwnerIdAndParentReviewId(String ownerId, String parentReviewId);

    /**
     * Finds in-flight reviews that must be recovered or marked terminal.
     *
     * @param statuses statuses considered in-flight
     * @return matching reviews
     */
    List<ReviewEntity> findByStatusIn(List<String> statuses);

    /**
     * Supports owner-scoped idempotent submission lookup.
     *
     * @param ownerId owning application user ID
     * @param idempotencyKey client retry key
     * @return matching review, or empty when the key is unused
     */
    Optional<ReviewEntity> findByOwnerIdAndIdempotencyKey(String ownerId, String idempotencyKey);

    /**
     * Counts active work before accepting another review.
     *
     * @param ownerId owning application user ID
     * @param statuses active statuses
     * @return number of matching active reviews
     */
    long countByOwnerIdAndStatusIn(String ownerId, List<String> statuses);
}
