package com.codereviewagent.api.repository;
import com.codereviewagent.api.model.FindingEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface FindingRepository extends JpaRepository<FindingEntity, String> {
    List<FindingEntity> findByReviewIdOrderByFilePathAscLineStartAsc(String reviewId);
    void deleteByReviewId(String reviewId);
}
