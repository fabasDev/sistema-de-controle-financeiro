package io.github.fabasdev.financas.service;

import io.github.fabasdev.financas.domain.BucketKind;
import io.github.fabasdev.financas.domain.Category;
import io.github.fabasdev.financas.domain.Money;
import io.github.fabasdev.financas.domain.Months;
import io.github.fabasdev.financas.domain.TransactionType;
import io.github.fabasdev.financas.dto.HistoryRow;
import io.github.fabasdev.financas.repository.CategoryRepository;
import io.github.fabasdev.financas.repository.MonthKindTotal;
import io.github.fabasdev.financas.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Histórico de vários meses. Porte de history e categoryHistory (finance.ts), com uma consulta agrupada cada. */
@Service
public class HistoryService {

    /** Gasto de uma categoria em cada mês do intervalo. */
    public record CategoryHistory(String name, List<BigDecimal> values, BigDecimal total) {
    }

    private final TransactionRepository transactions;
    private final CategoryRepository categories;

    public HistoryService(TransactionRepository transactions, CategoryRepository categories) {
        this.transactions = transactions;
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public List<HistoryRow> months(Long userId, YearMonth end, int count) {
        List<YearMonth> months = Months.range(end, count);
        Map<YearMonth, BigDecimal[]> totals = new HashMap<>();
        for (MonthKindTotal row : transactions.totalsByMonthAndKind(userId, months.getFirst().atDay(1),
                Months.endExclusive(end))) {
            // [receita, consumo, aportes]
            BigDecimal[] t = totals.computeIfAbsent(row.yearMonth(),
                    m -> new BigDecimal[] {Money.ZERO, Money.ZERO, Money.ZERO});
            if (row.type() == TransactionType.INCOME) {
                t[0] = t[0].add(row.total());
            } else if (row.kind() == BucketKind.FUTURE) {
                t[2] = t[2].add(row.total());
            } else {
                t[1] = t[1].add(row.total());
            }
        }
        List<HistoryRow> rows = new ArrayList<>(count);
        for (YearMonth m : months) {
            BigDecimal[] t = totals.getOrDefault(m, new BigDecimal[] {Money.ZERO, Money.ZERO, Money.ZERO});
            rows.add(new HistoryRow(m, Money.round(t[0]), Money.round(t[1]), Money.round(t[2]),
                    Money.round(t[0].subtract(t[1]).subtract(t[2])), Money.ratio(t[2], t[0])));
        }
        return rows;
    }

    /** Só categorias com algum gasto, da que mais gastou para a que menos gastou. */
    @Transactional(readOnly = true)
    public List<CategoryHistory> categories(Long userId, YearMonth end, int count) {
        List<YearMonth> months = Months.range(end, count);
        Map<Long, Category> cats = new HashMap<>();
        Category other = null;
        for (Category c : categories.findByUserIdOrderByIdAsc(userId)) {
            cats.put(c.getId(), c);
            if (Category.OTHER_KEY.equals(c.getSystemKey())) {
                other = c;
            }
        }
        Map<Long, BigDecimal[]> rows = new LinkedHashMap<>();
        for (Object[] row : transactions.expensesByCategoryAndMonth(userId, months.getFirst().atDay(1),
                Months.endExclusive(end))) {
            Category c = row[0] == null ? null : cats.get((Long) row[0]);
            Category shown = c != null ? c : other;
            if (shown == null) {
                continue;
            }
            int index = months.indexOf(YearMonth.of((Integer) row[1], (Integer) row[2]));
            BigDecimal[] values = rows.computeIfAbsent(shown.getId(), id -> {
                BigDecimal[] v = new BigDecimal[count];
                java.util.Arrays.fill(v, Money.ZERO);
                return v;
            });
            values[index] = values[index].add((BigDecimal) row[3]);
        }
        return rows.entrySet().stream()
                .map(e -> new CategoryHistory(cats.get(e.getKey()).getName(), List.of(e.getValue()),
                        Money.sum(List.of(e.getValue()))))
                .sorted(Comparator.comparing(CategoryHistory::total).reversed())
                .toList();
    }
}
