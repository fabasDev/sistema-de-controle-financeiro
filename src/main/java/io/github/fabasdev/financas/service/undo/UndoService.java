package io.github.fabasdev.financas.service.undo;

import io.github.fabasdev.financas.domain.Bucket;
import io.github.fabasdev.financas.domain.Category;
import io.github.fabasdev.financas.domain.Goal;
import io.github.fabasdev.financas.domain.RecurringEntry;
import io.github.fabasdev.financas.domain.Transaction;
import io.github.fabasdev.financas.domain.User;
import io.github.fabasdev.financas.domain.UserSettings;
import io.github.fabasdev.financas.repository.BucketRepository;
import io.github.fabasdev.financas.repository.CategoryRepository;
import io.github.fabasdev.financas.repository.GoalRepository;
import io.github.fabasdev.financas.repository.RecurringEntryRepository;
import io.github.fabasdev.financas.repository.TransactionRepository;
import io.github.fabasdev.financas.repository.UserRepository;
import io.github.fabasdev.financas.repository.UserSettingsRepository;
import io.github.fabasdev.financas.service.BusinessRuleException;
import io.github.fabasdev.financas.service.RuleService;
import io.github.fabasdev.financas.service.undo.UndoAction.BucketSnapshot;
import io.github.fabasdev.financas.service.undo.UndoAction.TxSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Desfaz a última exclusão: recria o que foi apagado e refaz as ligações. Tudo o que o snapshot
 * referencia é buscado de novo filtrando pelo dono, porque algo pode ter mudado nesses minutos
 * (a categoria pode ter sido apagada, por exemplo). Ligação que não existe mais fica em branco.
 */
@Service
public class UndoService {

    private final UndoStore store;
    private final UserRepository users;
    private final TransactionRepository transactions;
    private final RecurringEntryRepository recurring;
    private final GoalRepository goals;
    private final CategoryRepository categories;
    private final BucketRepository buckets;
    private final UserSettingsRepository settings;

    public UndoService(UndoStore store, UserRepository users, TransactionRepository transactions,
            RecurringEntryRepository recurring, GoalRepository goals, CategoryRepository categories,
            BucketRepository buckets, UserSettingsRepository settings) {
        this.store = store;
        this.users = users;
        this.transactions = transactions;
        this.recurring = recurring;
        this.goals = goals;
        this.categories = categories;
        this.buckets = buckets;
        this.settings = settings;
    }

    /** Guarda a ação para o botão "Desfazer" e devolve o token. */
    public String offer(Long userId, UndoAction action, String returnTo) {
        return store.offer(userId, action, returnTo);
    }

    /** Desfaz e devolve a página para onde voltar. Token inválido, usado ou vencido: erro para o usuário. */
    @Transactional
    public String undo(Long userId, String token) {
        UndoStore.Entry entry = store.take(userId, token)
                .orElseThrow(() -> new BusinessRuleException("Não dá mais para desfazer. O prazo é de 10 minutos e vale só a última exclusão."));
        restore(userId, entry.action());
        return entry.returnTo();
    }

    void restore(Long userId, UndoAction action) {
        User user = users.getReferenceById(userId);
        switch (action) {
            case UndoAction.TransactionDeleted a -> restoreTransaction(user, userId, a.transaction(), null);
            case UndoAction.RecurringDeleted a -> restoreRecurring(user, userId, a);
            case UndoAction.GoalDeleted a -> restoreGoal(user, userId, a);
            case UndoAction.CategoryDeleted a -> restoreCategory(user, userId, a);
            case UndoAction.CategoryArchived a ->
                    categories.findByIdAndUserId(a.categoryId(), userId).ifPresent(c -> c.setArchived(false));
            case UndoAction.BucketRemoved a -> restoreBucket(user, userId, a);
            case UndoAction.PresetApplied a -> restorePreset(user, userId, a);
        }
    }

    /**
     * Recria o lançamento. Se ele veio de uma conta fixa, o mês volta a valer para ela
     * (sai da lista de meses pulados) e a ligação é refeita.
     */
    private Transaction restoreTransaction(User user, Long userId, TxSnapshot s, RecurringEntry forcedRecurring) {
        Transaction t = new Transaction(user, s.type(), s.date(), s.description(), s.amount(), s.paid());
        t.setCategory(s.categoryId() == null ? null : categories.findByIdAndUserId(s.categoryId(), userId).orElse(null));
        t.setGoal(s.goalId() == null ? null : goals.findByIdAndUserId(s.goalId(), userId).orElse(null));
        RecurringEntry r = forcedRecurring != null ? forcedRecurring
                : s.recurringId() == null ? null : recurring.findByIdAndUserId(s.recurringId(), userId).orElse(null);
        if (r != null && s.recurringMonth() != null) {
            r.getSkippedMonths().remove(s.recurringMonth());
            if (r.getId() == null || !transactions.existsByRecurringIdAndRecurringMonth(r.getId(), s.recurringMonth())) {
                t.linkRecurring(r, s.recurringMonth());
            }
        }
        return transactions.save(t);
    }

    private void restoreRecurring(User user, Long userId, UndoAction.RecurringDeleted a) {
        UndoAction.RecurringSnapshot s = a.recurring();
        RecurringEntry r = new RecurringEntry(user, s.type(), s.description(), s.amount(), s.dayOfMonth(),
                s.startMonth());
        r.setEndMonth(s.endMonth());
        r.setActive(s.active());
        r.setCategory(s.categoryId() == null ? null : categories.findByIdAndUserId(s.categoryId(), userId).orElse(null));
        r.setGoal(s.goalId() == null ? null : goals.findByIdAndUserId(s.goalId(), userId).orElse(null));
        r.getSkippedMonths().addAll(s.skippedMonths());
        RecurringEntry saved = recurring.saveAndFlush(r);
        for (TxSnapshot future : a.deletedFuture()) {
            restoreTransaction(user, userId, future, saved);
        }
        for (UndoAction.RecurringLink link : a.unlinked()) {
            transactions.findByIdAndUserId(link.transactionId(), userId)
                    .filter(t -> t.getRecurring() == null && link.month() != null)
                    .ifPresent(t -> t.linkRecurring(saved, link.month()));
        }
    }

    private void restoreGoal(User user, Long userId, UndoAction.GoalDeleted a) {
        UndoAction.GoalSnapshot s = a.goal();
        Goal g = new Goal(user, null, s.name(), s.icon(), s.kind());
        g.setTarget(s.target());
        g.setDeadline(s.deadline());
        g.setInitialBalance(s.initialBalance());
        Goal saved = goals.save(g);
        // Só religa o que continua sem meta: se a pessoa já escolheu outra, ela vale.
        for (Long id : a.transactionIds()) {
            transactions.findByIdAndUserId(id, userId).filter(t -> t.getGoal() == null).ifPresent(t -> t.setGoal(saved));
        }
        for (Long id : a.recurringIds()) {
            recurring.findByIdAndUserId(id, userId).filter(r -> r.getGoal() == null).ifPresent(r -> r.setGoal(saved));
        }
        for (Long id : a.categoryIds()) {
            categories.findByIdAndUserId(id, userId).filter(c -> c.getGoal() == null).ifPresent(c -> c.setGoal(saved));
        }
    }

    private void restoreCategory(User user, Long userId, UndoAction.CategoryDeleted a) {
        UndoAction.CategorySnapshot s = a.category();
        Bucket bucket = buckets.findByIdAndUserId(s.bucketId(), userId)
                .orElseGet(() -> buckets.findByUserIdOrderBySortOrderAscIdAsc(userId).getFirst());
        String key = s.systemKey() != null && !categories.existsByUserIdAndSystemKey(userId, s.systemKey())
                ? s.systemKey() : null;
        Category c = new Category(user, key, s.name(), s.icon(), bucket, s.kind());
        c.setBudget(s.budget());
        c.setGoal(s.goalId() == null ? null : goals.findByIdAndUserId(s.goalId(), userId).orElse(null));
        categories.save(c);
    }

    private void restoreBucket(User user, Long userId, UndoAction.BucketRemoved a) {
        BucketSnapshot s = a.bucket();
        Bucket bucket = buckets.save(new Bucket(user, s.sortOrder(), s.name(), s.percent(), s.kind(), s.description()));
        for (Map.Entry<Long, io.github.fabasdev.financas.domain.BucketKind> e : a.movedCategories().entrySet()) {
            categories.findByIdAndUserId(e.getKey(), userId).ifPresent(c -> {
                c.setBucket(bucket);
                c.setKind(e.getValue());
            });
        }
        settingsOf(userId).setPresetId(a.previousPresetId());
    }

    /** Volta a regra anterior: recria os potes antigos, devolve as categorias e apaga os potes da regra nova. */
    private void restorePreset(User user, Long userId, UndoAction.PresetApplied a) {
        List<Bucket> restored = new ArrayList<>();
        for (BucketSnapshot s : a.previousBuckets()) {
            restored.add(buckets.save(new Bucket(user, s.sortOrder(), s.name(), s.percent(), s.kind(), s.description())));
        }
        List<RuleService.BucketWithClaims> options = restored.stream()
                .map(b -> new RuleService.BucketWithClaims(b, Set.of())).toList();
        Set<Long> created = Set.copyOf(a.createdBucketIds());
        for (Category c : categories.findByUserIdOrderByIdAsc(userId)) {
            Integer index = a.categoryBucketIndex().get(c.getId());
            if (index != null && index >= 0 && index < restored.size()) {
                c.setBucket(restored.get(index));
            } else if (created.contains(c.getBucket().getId())) {
                // Categoria criada depois da troca: vai para o pote antigo mais parecido.
                c.setBucket(RuleService.bucketFor(c.getSystemKey(), c.getKind(), options));
            }
        }
        categories.flush();
        for (Long id : a.createdBucketIds()) {
            buckets.findByIdAndUserId(id, userId).ifPresent(buckets::delete);
        }
        settingsOf(userId).setPresetId(a.previousPresetId());
    }

    private UserSettings settingsOf(Long userId) {
        return settings.findById(userId).orElseThrow();
    }
}
