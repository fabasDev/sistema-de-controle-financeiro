package io.github.fabasdev.financas.service;

import io.github.fabasdev.financas.domain.Bucket;
import io.github.fabasdev.financas.domain.Category;
import io.github.fabasdev.financas.repository.BucketRepository;
import io.github.fabasdev.financas.repository.CategoryRepository;
import io.github.fabasdev.financas.repository.RecurringEntryRepository;
import io.github.fabasdev.financas.repository.TransactionRepository;
import io.github.fabasdev.financas.repository.UserRepository;
import io.github.fabasdev.financas.service.undo.Snapshots;
import io.github.fabasdev.financas.service.undo.UndoAction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Categorias de gasto. Porte de addCategory, updateCategory e removeCategory (store.ts). */
@Service
public class CategoryService {

    public record CategoryData(String name, String icon, Long bucketId, BigDecimal budget, Long goalId) {
    }

    public enum RemoveResult { REMOVED, ARCHIVED }

    public record RemoveOutcome(RemoveResult result, UndoAction undo) {
    }

    private final CategoryRepository categories;
    private final BucketRepository buckets;
    private final TransactionRepository transactions;
    private final RecurringEntryRepository recurring;
    private final UserRepository users;
    private final OwnedLookup owned;

    public CategoryService(CategoryRepository categories, BucketRepository buckets,
            TransactionRepository transactions, RecurringEntryRepository recurring, UserRepository users,
            OwnedLookup owned) {
        this.categories = categories;
        this.buckets = buckets;
        this.transactions = transactions;
        this.recurring = recurring;
        this.users = users;
        this.owned = owned;
    }

    @Transactional(readOnly = true)
    public List<Category> list(Long userId) {
        return categories.findByUserIdOrderByIdAsc(userId);
    }

    /** Categorias do lançamento rápido: as mais usadas primeiro, depois as outras (sem as arquivadas). */
    public record QuickAddOptions(List<Category> mostUsed, List<Category> others) {
    }

    /** Quantas categorias aparecem no grupo "Mais usadas". */
    static final int MOST_USED = 5;

    @Transactional(readOnly = true)
    public QuickAddOptions quickAddOptions(Long userId, LocalDate today) {
        List<Category> active = categories.findByUserIdOrderByIdAsc(userId).stream()
                .filter(c -> !c.isArchived())
                .toList();
        List<Long> usedIds = transactions.findMostUsedCategoryIds(userId, today.minusDays(90),
                PageRequest.of(0, MOST_USED));
        List<Category> mostUsed = usedIds.stream()
                .flatMap(id -> active.stream().filter(c -> c.getId().equals(id)))
                .toList();
        List<Category> others = active.stream()
                .filter(c -> !mostUsed.contains(c))
                .sorted(Comparator.comparing((Category c) -> c.getBucket().getSortOrder()).thenComparing(Category::getName))
                .toList();
        return new QuickAddOptions(mostUsed, others);
    }

    @Transactional(readOnly = true)
    public Category get(Long userId, Long id) {
        return categories.findByIdAndUserId(id, userId).orElseThrow(NotFoundException::new);
    }

    /** A natureza (necessidade, desejo...) vem do pote escolhido. */
    @Transactional
    public Category create(Long userId, CategoryData data) {
        Bucket bucket = bucket(userId, data.bucketId());
        Category c = new Category(users.getReferenceById(userId), null, data.name(), data.icon(), bucket,
                bucket.getKind());
        c.setBudget(data.budget());
        c.setGoal(owned.goal(userId, data.goalId()));
        return categories.save(c);
    }

    @Transactional
    public void update(Long userId, Long id, CategoryData data) {
        Category c = get(userId, id);
        Bucket bucket = bucket(userId, data.bucketId());
        if (!bucket.getId().equals(c.getBucket().getId())) {
            // Trocar de pote muda a natureza da categoria para a do novo pote.
            c.setKind(bucket.getKind());
        }
        c.setBucket(bucket);
        c.setName(data.name());
        c.setIcon(data.icon());
        c.setBudget(data.budget());
        c.setGoal(owned.goal(userId, data.goalId()));
    }

    /** Sem uso, apaga. Com lançamentos ou contas fixas, só arquiva (o histórico continua certo). */
    @Transactional
    public RemoveOutcome remove(Long userId, Long id) {
        Category c = get(userId, id);
        if (transactions.existsByCategoryId(c.getId()) || recurring.existsByCategoryId(c.getId())) {
            c.setArchived(true);
            return new RemoveOutcome(RemoveResult.ARCHIVED, new UndoAction.CategoryArchived(c.getId()));
        }
        UndoAction undo = new UndoAction.CategoryDeleted(Snapshots.of(c));
        categories.delete(c);
        return new RemoveOutcome(RemoveResult.REMOVED, undo);
    }

    @Transactional
    public void unarchive(Long userId, Long id) {
        get(userId, id).setArchived(false);
    }

    private Bucket bucket(Long userId, Long bucketId) {
        if (bucketId == null) {
            throw new InvalidInputException("bucketId", "Escolha um pote.");
        }
        return buckets.findByIdAndUserId(bucketId, userId)
                .orElseThrow(() -> new InvalidInputException("bucketId", "Escolha um pote da lista."));
    }
}
