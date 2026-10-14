package io.github.fabasdev.financas.repository;

import io.github.fabasdev.financas.domain.Goal;
import io.github.fabasdev.financas.domain.GoalKind;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoalRepository extends JpaRepository<Goal, Long> {

    List<Goal> findByUserIdOrderByIdAsc(Long userId);

    Optional<Goal> findByIdAndUserId(Long id, Long userId);

    Optional<Goal> findByUserIdAndSystemKey(Long userId, String systemKey);

    Optional<Goal> findFirstByUserIdAndKindOrderByIdAsc(Long userId, GoalKind kind);

    /** Apaga todos os registros do usuário (usado ao excluir a conta e ao importar um backup). */
    @Modifying
    @Query("delete from Goal e where e.user.id = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
