package io.github.fabasdev.financas.support;

import io.github.fabasdev.financas.domain.Bucket;
import io.github.fabasdev.financas.domain.Category;
import io.github.fabasdev.financas.domain.Goal;
import io.github.fabasdev.financas.domain.GoalKind;
import io.github.fabasdev.financas.domain.RecurringEntry;
import io.github.fabasdev.financas.domain.Transaction;
import io.github.fabasdev.financas.domain.TransactionType;
import io.github.fabasdev.financas.repository.BucketRepository;
import io.github.fabasdev.financas.service.CategoryService;
import io.github.fabasdev.financas.service.GoalService;
import io.github.fabasdev.financas.service.RecurringService;
import io.github.fabasdev.financas.service.TransactionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Um registro de cada tipo para um usuário, criado pelos serviços (como faria a tela). */
@Component
public class SampleData {

    public record Ids(Long transaction, Long recurring, Long goal, Long bucket, Long category, String marker) {
    }

    private final TransactionService transactions;
    private final RecurringService recurring;
    private final GoalService goals;
    private final CategoryService categories;
    private final BucketRepository buckets;

    public SampleData(TransactionService transactions, RecurringService recurring, GoalService goals,
            CategoryService categories, BucketRepository buckets) {
        this.transactions = transactions;
        this.recurring = recurring;
        this.goals = goals;
        this.categories = categories;
        this.buckets = buckets;
    }

    /** {@code marker} é um texto único que aparece na descrição, para conferir vazamento entre usuários. */
    public Ids create(Long userId) {
        String marker = "segredo-" + UUID.randomUUID().toString().substring(0, 8);
        Bucket bucket = buckets.findByUserIdOrderBySortOrderAscIdAsc(userId).getFirst();
        Category category = categories.create(userId,
                new CategoryService.CategoryData("Categoria " + marker, "Tag", bucket.getId(), null, null));
        Goal goal = goals.create(userId, new GoalService.GoalData("Meta " + marker, "Target", GoalKind.GOAL,
                new BigDecimal("5000"), YearMonth.now().plusMonths(10), BigDecimal.ZERO));
        Transaction t = transactions.create(userId, new TransactionService.TransactionData(TransactionType.EXPENSE,
                LocalDate.now(), "Gasto " + marker, new BigDecimal("123.45"), category.getId(), null, true, false, null));
        RecurringEntry r = recurring.create(userId, new RecurringService.RecurringData(TransactionType.EXPENSE,
                "Conta " + marker, new BigDecimal("99.90"), category.getId(), goal.getId(), 10, YearMonth.now(), null,
                true));
        return new Ids(t.getId(), r.getId(), goal.getId(), bucket.getId(), category.getId(), marker);
    }
}
