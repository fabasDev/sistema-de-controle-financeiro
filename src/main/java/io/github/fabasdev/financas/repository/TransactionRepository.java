package io.github.fabasdev.financas.repository;

import io.github.fabasdev.financas.domain.Transaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByIdAndUserId(Long id, Long userId);

    /** Totais do intervalo [start, end) por categoria. O LEFT JOIN mantém os lançamentos sem categoria. */
    @Query("""
            select new io.github.fabasdev.financas.repository.CategoryTotal(
                c.id, t.type, t.paid, sum(t.amount), count(t))
            from Transaction t left join t.category c
            where t.user.id = :userId and t.date >= :start and t.date < :end
            group by c.id, t.type, t.paid
            """)
    List<CategoryTotal> totalsByCategory(@Param("userId") Long userId, @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    /** Totais do intervalo [start, end) por mês, tipo e natureza da categoria. */
    @Query("""
            select new io.github.fabasdev.financas.repository.MonthKindTotal(
                year(t.date), month(t.date), t.type, c.kind, sum(t.amount))
            from Transaction t left join t.category c
            where t.user.id = :userId and t.date >= :start and t.date < :end
            group by year(t.date), month(t.date), t.type, c.kind
            """)
    List<MonthKindTotal> totalsByMonthAndKind(@Param("userId") Long userId, @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    /** Quanto já foi aportado (despesas pagas) em uma meta, em todos os meses. */
    @Query("""
            select coalesce(sum(t.amount), 0) from Transaction t
            where t.user.id = :userId and t.goal.id = :goalId
              and t.type = io.github.fabasdev.financas.domain.TransactionType.EXPENSE and t.paid = true
            """)
    BigDecimal sumPaidExpensesForGoal(@Param("userId") Long userId, @Param("goalId") Long goalId);

    /** O mesmo, para todas as metas de uma vez: [goalId, total]. */
    @Query("""
            select t.goal.id, sum(t.amount) from Transaction t
            where t.user.id = :userId and t.goal is not null
              and t.type = io.github.fabasdev.financas.domain.TransactionType.EXPENSE and t.paid = true
            group by t.goal.id
            """)
    List<Object[]> sumPaidExpensesByGoal(@Param("userId") Long userId);

    boolean existsByCategoryId(Long categoryId);

    /** Contas fixas que já têm lançamento no mês. */
    @Query("""
            select t.recurring.id from Transaction t
            where t.user.id = :userId and t.recurringMonth = :month and t.recurring is not null
            """)
    List<Long> findRecurringIdsInMonth(@Param("userId") Long userId, @Param("month") YearMonth month);

    /** Lançamentos ainda não pagos de uma conta fixa, do mês {@code from} em diante. */
    @Query("""
            select t from Transaction t
            where t.recurring.id = :recurringId and t.paid = false and t.recurringMonth >= :from
            """)
    List<Transaction> findUnpaidOfRecurringFrom(@Param("recurringId") Long recurringId,
            @Param("from") YearMonth from);

    /** Solta os lançamentos de uma conta fixa que vai ser apagada (o histórico fica). */
    @Modifying
    @Query("update Transaction t set t.recurring = null, t.recurringMonth = null where t.recurring.id = :recurringId")
    int unlinkRecurring(@Param("recurringId") Long recurringId);

    /** Apaga todos os registros do usuário (usado ao excluir a conta e ao importar um backup). */
    @Modifying
    @Query("delete from Transaction e where e.user.id = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
