package io.github.fabasdev.financas.service;

import io.github.fabasdev.financas.domain.Months;
import io.github.fabasdev.financas.domain.RecurringEntry;
import io.github.fabasdev.financas.domain.Transaction;
import io.github.fabasdev.financas.domain.TransactionType;
import io.github.fabasdev.financas.repository.RecurringEntryRepository;
import io.github.fabasdev.financas.repository.TransactionRepository;
import io.github.fabasdev.financas.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Contas fixas e parcelamentos. Porte de ensureMonth, updateRecurring e removeRecurring (store.ts). */
@Service
public class RecurringService {

    /** Dados de uma conta fixa vindos do formulário (já validados no formato). */
    public record RecurringData(TransactionType type, String description, BigDecimal amount, Long categoryId,
            Long goalId, int dayOfMonth, YearMonth startMonth, YearMonth endMonth, boolean active) {
    }

    private final RecurringEntryRepository recurring;
    private final TransactionRepository transactions;
    private final UserRepository users;
    private final OwnedLookup owned;
    private final TransactionTemplate tx;
    private final Clock clock;

    public RecurringService(RecurringEntryRepository recurring, TransactionRepository transactions,
            UserRepository users, OwnedLookup owned, PlatformTransactionManager txManager, Clock clock) {
        this.recurring = recurring;
        this.transactions = transactions;
        this.users = users;
        this.owned = owned;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
    }

    /**
     * Cria no mês os lançamentos das contas fixas que ainda não existem (como "a pagar").
     * Se duas abas pedirem o mesmo mês ao mesmo tempo, o UNIQUE do banco barra a duplicata
     * e a segunda simplesmente desiste.
     */
    public void ensureMonth(Long userId, YearMonth month) {
        try {
            tx.executeWithoutResult(status -> generate(userId, month));
        } catch (DataIntegrityViolationException e) {
            // Outra requisição gerou este mês primeiro. Nada a fazer.
        }
    }

    private void generate(Long userId, YearMonth month) {
        Set<Long> existing = new HashSet<>(transactions.findRecurringIdsInMonth(userId, month));
        for (RecurringEntry r : recurring.findByUserIdOrderByIdAsc(userId)) {
            if (!r.coversMonth(month) || r.getSkippedMonths().contains(month) || existing.contains(r.getId())) {
                continue;
            }
            Transaction t = new Transaction(r.getUser(), r.getType(), Months.dateInMonth(month, r.getDayOfMonth()),
                    r.getDescription(), r.getAmount(), false);
            t.setCategory(r.getCategory());
            t.setGoal(r.getGoal());
            t.linkRecurring(r, month);
            transactions.save(t);
        }
    }

    @Transactional(readOnly = true)
    public List<RecurringEntry> list(Long userId) {
        return recurring.findByUserIdOrderByIdAsc(userId);
    }

    @Transactional(readOnly = true)
    public RecurringEntry get(Long userId, Long id) {
        return recurring.findByIdAndUserId(id, userId).orElseThrow(NotFoundException::new);
    }

    @Transactional
    public RecurringEntry create(Long userId, RecurringData data) {
        validate(data);
        RecurringEntry r = new RecurringEntry(users.getReferenceById(userId), data.type(), data.description(),
                data.amount(), data.dayOfMonth(), data.startMonth());
        apply(userId, r, data);
        return recurring.save(r);
    }

    /** Altera a conta. Lançamentos ainda não pagos deste mês em diante acompanham a mudança. */
    @Transactional
    public void update(Long userId, Long id, RecurringData data) {
        validate(data);
        RecurringEntry r = recurring.findByIdAndUserId(id, userId).orElseThrow(NotFoundException::new);
        apply(userId, r, data);
        for (Transaction t : transactions.findUnpaidOfRecurringFrom(r.getId(), YearMonth.now(clock))) {
            YearMonth month = t.getRecurringMonth();
            if (!r.coversMonth(month)) {
                transactions.delete(t);
                continue;
            }
            t.setType(r.getType());
            t.setDescription(r.getDescription());
            t.setAmount(r.getAmount());
            t.setCategory(r.getCategory());
            t.setGoal(r.getGoal());
            t.setDate(Months.dateInMonth(month, r.getDayOfMonth()));
        }
    }

    /** Apaga a conta e os lançamentos futuros ainda não pagos. O que já passou fica no histórico. */
    @Transactional
    public void remove(Long userId, Long id) {
        RecurringEntry r = recurring.findByIdAndUserId(id, userId).orElseThrow(NotFoundException::new);
        YearMonth next = YearMonth.now(clock).plusMonths(1);
        transactions.deleteAll(transactions.findUnpaidOfRecurringFrom(r.getId(), next));
        transactions.flush();
        transactions.unlinkRecurring(r.getId());
        recurring.delete(r);
    }

    private void apply(Long userId, RecurringEntry r, RecurringData data) {
        r.setType(data.type());
        r.setDescription(data.description());
        r.setAmount(data.amount());
        r.setDayOfMonth(data.dayOfMonth());
        r.setStartMonth(data.startMonth());
        r.setEndMonth(data.endMonth());
        r.setActive(data.active());
        r.setCategory(owned.category(userId, data.categoryId()));
        r.setGoal(owned.goal(userId, data.goalId()));
    }

    private static void validate(RecurringData data) {
        if (data.endMonth() != null && data.endMonth().isBefore(data.startMonth())) {
            throw new InvalidInputException("installments", "O último mês não pode ser antes do primeiro.");
        }
    }
}
