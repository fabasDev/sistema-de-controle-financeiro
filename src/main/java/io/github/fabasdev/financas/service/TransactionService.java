package io.github.fabasdev.financas.service;

import io.github.fabasdev.financas.domain.Category;
import io.github.fabasdev.financas.domain.Goal;
import io.github.fabasdev.financas.domain.Months;
import io.github.fabasdev.financas.domain.RecurringEntry;
import io.github.fabasdev.financas.domain.Transaction;
import io.github.fabasdev.financas.domain.TransactionType;
import io.github.fabasdev.financas.dto.TransactionRow;
import io.github.fabasdev.financas.repository.RecurringEntryRepository;
import io.github.fabasdev.financas.repository.TransactionRepository;
import io.github.fabasdev.financas.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lançamentos. Porte das ações addTransaction, updateTransaction, removeTransaction e togglePaid (store.ts). */
@Service
public class TransactionService {

    /**
     * Dados vindos do formulário (já validados no formato).
     *
     * @param repeat       repetir todo mês (vira uma conta fixa)
     * @param installments número de parcelas quando repete (null ou 1 = sem fim)
     */
    public record TransactionData(TransactionType type, LocalDate date, String description, BigDecimal amount,
            Long categoryId, Long goalId, boolean paid, boolean repeat, Integer installments) {
    }

    /** Filtros da listagem. Todos opcionais. */
    public record Filter(TransactionType type, Long categoryId, String search) {
    }

    private final TransactionRepository transactions;
    private final RecurringEntryRepository recurring;
    private final UserRepository users;
    private final OwnedLookup owned;

    public TransactionService(TransactionRepository transactions, RecurringEntryRepository recurring,
            UserRepository users, OwnedLookup owned) {
        this.transactions = transactions;
        this.recurring = recurring;
        this.users = users;
        this.owned = owned;
    }

    @Transactional(readOnly = true)
    public Page<TransactionRow> page(Long userId, YearMonth month, Filter filter, Pageable pageable) {
        // A ordem é fixa na consulta. A ordenação que vier na URL é ignorada.
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return transactions.findRows(userId, month.atDay(1), Months.endExclusive(month), filter.type(),
                filter.categoryId(), likePattern(filter.search()), unsorted);
    }

    @Transactional(readOnly = true)
    public Transaction get(Long userId, Long id) {
        return transactions.findByIdAndUserId(id, userId).orElseThrow(NotFoundException::new);
    }

    @Transactional
    public Transaction create(Long userId, TransactionData data) {
        Transaction t = new Transaction(users.getReferenceById(userId), data.type(), data.date(), "", data.amount(),
                data.paid());
        apply(userId, t, data);
        if (data.repeat()) {
            YearMonth start = YearMonth.from(data.date());
            RecurringEntry r = new RecurringEntry(t.getUser(), t.getType(), t.getDescription(), t.getAmount(),
                    data.date().getDayOfMonth(), start);
            r.setCategory(t.getCategory());
            r.setGoal(t.getGoal());
            if (data.installments() != null && data.installments() > 1) {
                r.setEndMonth(start.plusMonths(data.installments() - 1L));
            }
            t.linkRecurring(recurring.save(r), start);
        }
        return transactions.save(t);
    }

    /** Edita só este lançamento. Se ele veio de uma conta fixa, a conta não muda. */
    @Transactional
    public void update(Long userId, Long id, TransactionData data) {
        Transaction t = get(userId, id);
        t.setType(data.type());
        t.setDate(data.date());
        t.setAmount(data.amount());
        t.setPaid(data.paid());
        apply(userId, t, data);
    }

    @Transactional
    public void togglePaid(Long userId, Long id) {
        Transaction t = get(userId, id);
        t.setPaid(!t.isPaid());
    }

    /**
     * Apaga o lançamento. Se ele veio de uma conta fixa, o mês fica marcado como pulado
     * para a conta não recriar o lançamento.
     */
    @Transactional
    public void delete(Long userId, Long id) {
        Transaction t = get(userId, id);
        if (t.getRecurring() != null && t.getRecurringMonth() != null) {
            t.getRecurring().getSkippedMonths().add(t.getRecurringMonth());
        }
        transactions.delete(t);
    }

    /** Categoria obrigatória na despesa; receita não tem categoria nem meta. */
    private void apply(Long userId, Transaction t, TransactionData data) {
        Category category = null;
        Goal goal = null;
        if (data.type() == TransactionType.EXPENSE) {
            if (data.categoryId() == null) {
                throw new InvalidInputException("categoryId", "Escolha uma categoria.");
            }
            category = owned.category(userId, data.categoryId());
            // Sem meta escolhida, vale a meta ligada à categoria (ex.: Reserva de emergência).
            goal = data.goalId() != null ? owned.goal(userId, data.goalId()) : category.getGoal();
        }
        t.setType(data.type());
        t.setCategory(category);
        t.setGoal(goal);
        String description = data.description() == null ? "" : data.description().strip();
        if (description.isEmpty()) {
            description = category != null ? category.getName() : "Receita";
        }
        t.setDescription(description);
    }

    /** "mercado" vira "%mercado%", com % e _ digitados tratados como texto comum. */
    static String likePattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String escaped = search.strip().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
