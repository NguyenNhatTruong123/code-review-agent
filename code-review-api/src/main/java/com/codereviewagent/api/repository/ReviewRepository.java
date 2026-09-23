package com.codereviewagent.api.repository;
import com.codereviewagent.api.model.ReviewEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
public interface ReviewRepository extends JpaRepository<ReviewEntity, String> {
    Page<ReviewEntity> findByOwnerId(String ownerId, Pageable pageable);
    List<ReviewEntity> findByStatusIn(List<String> statuses);
    Optional<ReviewEntity> findByOwnerIdAndIdempotencyKey(String ownerId, String idempotencyKey);
    long countByOwnerIdAndStatusIn(String ownerId, List<String> statuses);
}
