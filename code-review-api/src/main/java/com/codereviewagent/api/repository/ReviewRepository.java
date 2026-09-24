package com.codereviewagent.api.repository;
import com.codereviewagent.api.model.ReviewEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
/** Provides review history, active-work, and idempotency persistence queries. */
public interface ReviewRepository extends JpaRepository<ReviewEntity, String> {
    /** Paginates reviews while keeping ownership in the query boundary.
     * @param ownerId owning application user ID
     * @param pageable page and sort request
     * @return owner-scoped review page
     */
    Page<ReviewEntity> findByOwnerId(String ownerId, Pageable pageable);
    /** Finds in-flight reviews that must be recovered or marked terminal.
     * @param statuses statuses considered in-flight
     * @return matching reviews
     */
    List<ReviewEntity> findByStatusIn(List<String> statuses);
    /** Supports owner-scoped idempotent submission lookup.
     * @param ownerId owning application user ID
     * @param idempotencyKey client retry key
     * @return matching review, or empty when the key is unused
     */
    Optional<ReviewEntity> findByOwnerIdAndIdempotencyKey(String ownerId, String idempotencyKey);
    /** Counts active work before accepting another review.
     * @param ownerId owning application user ID
     * @param statuses active statuses
     * @return number of matching active reviews
     */
    long countByOwnerIdAndStatusIn(String ownerId, List<String> statuses);
}
