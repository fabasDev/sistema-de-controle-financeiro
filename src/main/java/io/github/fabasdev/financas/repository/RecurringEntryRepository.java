package io.github.fabasdev.financas.repository;

import io.github.fabasdev.financas.domain.RecurringEntry;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurringEntryRepository extends JpaRepository<RecurringEntry, Long> {

    /** Traz categoria, meta e meses pulados juntos, para gerar o mês sem N+1. */
    @EntityGraph(attributePaths = {"category", "goal", "skippedMonths"})
    List<RecurringEntry> findByUserIdOrderByIdAsc(Long userId);

    Optional<RecurringEntry> findByIdAndUserId(Long id, Long userId);
}
