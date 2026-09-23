package com.codereviewagent.api.repository;
import com.codereviewagent.api.model.RuleSetEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RuleSetRepository extends JpaRepository<RuleSetEntity, String> {
    List<RuleSetEntity> findByOwnerIdOrderByNameAsc(String ownerId);
}
