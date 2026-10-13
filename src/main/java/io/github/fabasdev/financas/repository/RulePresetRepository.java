package io.github.fabasdev.financas.repository;

import io.github.fabasdev.financas.domain.RulePreset;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RulePresetRepository extends JpaRepository<RulePreset, String> {

    @EntityGraph(attributePaths = "buckets")
    List<RulePreset> findAllByOrderBySortOrderAsc();

    @EntityGraph(attributePaths = "buckets")
    Optional<RulePreset> findWithBucketsById(String id);
}
