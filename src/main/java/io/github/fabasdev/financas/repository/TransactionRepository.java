package io.github.fabasdev.financas.repository;

import io.github.fabasdev.financas.domain.Transaction;
import io.github.fabasdev.financas.domain.TransactionType;
import io.github.fabasdev.financas.dto.TransactionRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /**
     * Listagem paginada do mês, com filtros opcionais. A ordem é sempre data mais recente primeiro
     * (e id, para desempate), e usa o índice (user_id, entry_date DESC, id DESC).
     * A busca chega já com os curingas do LIKE escapados.
     */
    @Query(value = """
            select new io.github.fabasdev.financas.dto.TransactionRow(
                t.id, t.type, t.date, t.description, t.amount, t.paid, c.name, c.icon,
                case when t.recurring is not null then true else false end)
            from Transaction t left join t.category c
            where t.user.id = :userId and t.date >= :start and t.date < :end
              and (:type is null or t.type = :type)
              and (:categoryId is null or c.id = :categoryId)
              and (:search is null or lower(t.description) like :search escape '\\')
            order by t.date desc, t.id desc
            """,
            countQuery = """
            select count(t) from Transaction t left join t.category c
            where t.user.id = :userId and t.date >= :start and t.date < :end
              and (:type is null or t.type = :type)
              and (:categoryId is null or c.id = :categoryId)
              and (:search is null or lower(t.description) like :search escape '\\')
            """)
    Page<TransactionRow> findRows(@Param("userId") Long userId, @Param("start") LocalDate start,
            @Param("end") LocalDate end, @Param("type") TransactionType type, @Param("categoryId") Long categoryId,
            @Param("search") String search, Pageable pageable);

    /** Gasto por categoria e mês no intervalo: [categoryId, ano, mês, total]. */
    @Query("""
            select c.id, year(t.date), month(t.date), sum(t.amount)
            from Transaction t left join t.category c
            where t.user.id = :userId and t.date >= :start and t.date < :end
              and t.type = io.github.fabasdev.financas.domain.TransactionType.EXPENSE
            group by c.id, year(t.date), month(t.date)
            """)
    List<Object[]> expensesByCategoryAndMonth(@Param("userId") Long userId, @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    /** Categorias mais usadas em despesas desde {@code since}, da mais usada para a menos usada. */
    @Query("""
            select t.category.id from Transaction t
            where t.user.id = :userId and t.category is not null and t.date >= :since
              and t.type = io.github.fabasdev.financas.domain.TransactionType.EXPENSE
            group by t.category.id
            order by count(t) desc, t.category.id
            """)
    List<Long> findMostUsedCategoryIds(@Param("userId") Long userId, @Param("since") LocalDate since,
            Pageable pageable);

    @Query("select t.id from Transaction t where t.user.id = :userId and t.goal.id = :goalId")
    List<Long> findIdsByGoal(@Param("userId") Long userId, @Param("goalId") Long goalId);

    List<Transaction> findByRecurringId(Long recurringId);

    boolean existsByRecurringIdAndRecurringMonth(Long recurringId, YearMonth recurringMonth);

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

    /** Todos os lançamentos do usuário, do mais antigo ao mais novo, para o backup. Uma query só. */
    @Query("""
            select new io.github.fabasdev.financas.repository.TransactionExportRow(
                t.id, t.type, t.date, t.description, t.amount, t.paid, c.id, g.id, r.id, t.recurringMonth)
            from Transaction t left join t.category c left join t.goal g left join t.recurring r
            where t.user.id = :userId
            order by t.date, t.id
            """)
    List<TransactionExportRow> findForExport(@Param("userId") Long userId);

    /** Apaga todos os registros do usuário (usado ao excluir a conta e ao importar um backup). */
    @Modifying
    @Query("delete from Transaction e where e.user.id = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
