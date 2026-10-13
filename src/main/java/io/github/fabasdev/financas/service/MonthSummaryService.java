package io.github.fabasdev.financas.service;

import io.github.fabasdev.financas.domain.Bucket;
import io.github.fabasdev.financas.domain.BucketKind;
import io.github.fabasdev.financas.domain.Category;
import io.github.fabasdev.financas.domain.Money;
import io.github.fabasdev.financas.domain.Months;
import io.github.fabasdev.financas.domain.TransactionType;
import io.github.fabasdev.financas.dto.MonthSummary;
import io.github.fabasdev.financas.dto.MonthSummary.BucketSummary;
import io.github.fabasdev.financas.dto.MonthSummary.CategorySummary;
import io.github.fabasdev.financas.repository.BucketRepository;
import io.github.fabasdev.financas.repository.CategoryRepository;
import io.github.fabasdev.financas.repository.CategoryTotal;
import io.github.fabasdev.financas.repository.RecurringEntryRepository;
import io.github.fabasdev.financas.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Responde quanto entrou, quanto saiu e quanto sobra no mês. As somas são feitas no banco
 * (SUM ... GROUP BY), e aqui só se juntam os totais. Porte de summarizeMonth (finance.ts).
 */
@Service
public class MonthSummaryService {

    private final TransactionRepository transactions;
    private final CategoryRepository categories;
    private final BucketRepository buckets;
    private final RecurringEntryRepository recurring;

    public MonthSummaryService(TransactionRepository transactions, CategoryRepository categories,
            BucketRepository buckets, RecurringEntryRepository recurring) {
        this.transactions = transactions;
        this.categories = categories;
        this.buckets = buckets;
        this.recurring = recurring;
    }

    @Transactional(readOnly = true)
    public MonthSummary summarize(Long userId, YearMonth month) {
        Map<Long, Category> cats = categories.findByUserIdOrderByIdAsc(userId).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
        Category fallback = cats.values().stream()
                .filter(c -> Category.OTHER_KEY.equals(c.getSystemKey()))
                .findFirst().orElse(null);
        List<CategoryTotal> totals = transactions.totalsByCategory(userId, month.atDay(1), Months.endExclusive(month));

        BigDecimal income = Money.ZERO;
        BigDecimal outflow = Money.ZERO;
        BigDecimal saved = Money.ZERO;
        BigDecimal unpaid = Money.ZERO;
        BigDecimal pendingIncome = Money.ZERO;
        long count = 0;
        Map<Long, CategoryAccumulator> byCategory = new LinkedHashMap<>();

        for (CategoryTotal row : totals) {
            count += row.count();
            if (row.type() == TransactionType.INCOME) {
                income = income.add(row.total());
                if (!row.paid()) {
                    pendingIncome = pendingIncome.add(row.total());
                }
                continue;
            }
            Category category = row.categoryId() == null ? null : cats.get(row.categoryId());
            outflow = outflow.add(row.total());
            if (category != null && category.getKind() == BucketKind.FUTURE) {
                saved = saved.add(row.total());
            }
            if (!row.paid()) {
                unpaid = unpaid.add(row.total());
            }
            // Gasto sem categoria entra em "Outros" no detalhe por categoria.
            Category shown = category != null ? category : fallback;
            if (shown != null) {
                byCategory.computeIfAbsent(shown.getId(), id -> new CategoryAccumulator(shown)).add(row);
            }
        }

        BigDecimal planIncome = income.signum() > 0 ? income : recurring.sumActiveIncome(userId, month);
        List<BucketSummary> bucketSummaries = summarizeBuckets(userId, planIncome, byCategory);
        List<CategorySummary> categorySummaries = byCategory.values().stream()
                .map(CategoryAccumulator::toSummary)
                .sorted(Comparator.comparing(CategorySummary::actual).reversed())
                .toList();

        return new MonthSummary(
                month,
                Money.round(income),
                Money.round(outflow.subtract(saved)),
                Money.round(saved),
                Money.round(outflow),
                Money.round(income.subtract(outflow)),
                Money.ratio(saved, income),
                Money.round(unpaid),
                Money.round(pendingIncome),
                Money.round(planIncome),
                bucketSummaries,
                categorySummaries,
                count > 0);
    }

    private List<BucketSummary> summarizeBuckets(Long userId, BigDecimal planIncome,
            Map<Long, CategoryAccumulator> byCategory) {
        List<Bucket> userBuckets = buckets.findByUserIdOrderBySortOrderAscIdAsc(userId);
        List<BucketSummary> result = new ArrayList<>(userBuckets.size());
        for (int i = 0; i < userBuckets.size(); i++) {
            Bucket bucket = userBuckets.get(i);
            BigDecimal actual = byCategory.values().stream()
                    .filter(acc -> acc.category.getBucket().getId().equals(bucket.getId()))
                    .map(acc -> acc.actual)
                    .reduce(Money.ZERO, BigDecimal::add);
            result.add(new BucketSummary(bucket.getId(), i, bucket.getName(), bucket.getKind(), bucket.getPercent(),
                    Money.percentOf(planIncome, bucket.getPercent()), Money.round(actual)));
        }
        return result;
    }

    private static final class CategoryAccumulator {
        private final Category category;
        private BigDecimal actual = Money.ZERO;
        private long count;

        CategoryAccumulator(Category category) {
            this.category = category;
        }

        void add(CategoryTotal row) {
            actual = actual.add(row.total());
            count += row.count();
        }

        CategorySummary toSummary() {
            return new CategorySummary(category.getId(), category.getName(), category.getIcon(), category.getKind(),
                    category.getBucket().getId(), category.getBudget(), Money.round(actual), count);
        }
    }
}
