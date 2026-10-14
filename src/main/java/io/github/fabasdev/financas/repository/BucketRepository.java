package io.github.fabasdev.financas.repository;

import io.github.fabasdev.financas.domain.Bucket;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BucketRepository extends JpaRepository<Bucket, Long> {

    List<Bucket> findByUserIdOrderBySortOrderAscIdAsc(Long userId);

    Optional<Bucket> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    /** Apaga todos os registros do usuário (usado ao excluir a conta e ao importar um backup). */
    @Modifying
    @Query("delete from Bucket e where e.user.id = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
