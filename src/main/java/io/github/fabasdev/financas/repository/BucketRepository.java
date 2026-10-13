package io.github.fabasdev.financas.repository;

import io.github.fabasdev.financas.domain.Bucket;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BucketRepository extends JpaRepository<Bucket, Long> {

    List<Bucket> findByUserIdOrderBySortOrderAscIdAsc(Long userId);

    Optional<Bucket> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);
}
