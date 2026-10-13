package io.github.fabasdev.financas.repository;

import io.github.fabasdev.financas.domain.Goal;
import io.github.fabasdev.financas.domain.GoalKind;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalRepository extends JpaRepository<Goal, Long> {

    List<Goal> findByUserIdOrderByIdAsc(Long userId);

    Optional<Goal> findByIdAndUserId(Long id, Long userId);

    Optional<Goal> findByUserIdAndSystemKey(Long userId, String systemKey);

    Optional<Goal> findFirstByUserIdAndKindOrderByIdAsc(Long userId, GoalKind kind);
}
