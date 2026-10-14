package io.github.fabasdev.financas.repository;

import io.github.fabasdev.financas.domain.Bucket;
import io.github.fabasdev.financas.domain.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** Já traz o pote junto, para não fazer uma query por categoria. */
    @EntityGraph(attributePaths = "bucket")
    List<Category> findByUserIdOrderByIdAsc(Long userId);

    Optional<Category> findByIdAndUserId(Long id, Long userId);

    Optional<Category> findByUserIdAndSystemKey(Long userId, String systemKey);

    List<Category> findByBucket(Bucket bucket);

    /** Apaga todos os registros do usuário (usado ao excluir a conta e ao importar um backup). */
    @Modifying
    @Query("delete from Category e where e.user.id = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
