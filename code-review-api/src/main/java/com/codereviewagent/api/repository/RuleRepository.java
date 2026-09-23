package com.codereviewagent.api.repository;
import com.codereviewagent.api.model.RuleEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RuleRepository extends JpaRepository<RuleEntity, String> {
    List<RuleEntity> findByOwnerIdOrderByNameAsc(String ownerId);
}
