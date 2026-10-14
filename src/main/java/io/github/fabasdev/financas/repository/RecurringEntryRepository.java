package io.github.fabasdev.financas.repository;

import io.github.fabasdev.financas.domain.RecurringEntry;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecurringEntryRepository extends JpaRepository<RecurringEntry, Long> {

    /** Traz categoria, meta e meses pulados juntos, para gerar o mês sem N+1. */
    @EntityGraph(attributePaths = {"category", "goal", "skippedMonths"})
    List<RecurringEntry> findByUserIdOrderByIdAsc(Long userId);

    Optional<RecurringEntry> findByIdAndUserId(Long id, Long userId);

    /** Soma das receitas fixas ativas no mês: base do plano quando o mês ainda não tem renda. */
    @Query("""
            select coalesce(sum(r.amount), 0) from RecurringEntry r
            where r.user.id = :userId and r.active = true
              and r.type = io.github.fabasdev.financas.domain.TransactionType.INCOME
              and r.startMonth <= :month and (r.endMonth is null or r.endMonth >= :month)
            """)
    BigDecimal sumActiveIncome(@Param("userId") Long userId, @Param("month") YearMonth month);

    boolean existsByCategoryId(Long categoryId);

    /** Apaga todos os registros do usuário (usado ao excluir a conta e ao importar um backup). */
    @Modifying
    @Query("delete from RecurringEntry e where e.user.id = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
