package io.github.fabasdev.financas.service;

import io.github.fabasdev.financas.domain.BucketKind;
import io.github.fabasdev.financas.domain.Goal;
import io.github.fabasdev.financas.domain.GoalKind;
import io.github.fabasdev.financas.domain.Money;
import io.github.fabasdev.financas.domain.Months;
import io.github.fabasdev.financas.domain.ReserveBase;
import io.github.fabasdev.financas.domain.TransactionType;
import io.github.fabasdev.financas.domain.UserSettings;
import io.github.fabasdev.financas.dto.MonthSummary;
import io.github.fabasdev.financas.dto.Recommendation;
import io.github.fabasdev.financas.dto.ReserveInfo;
import io.github.fabasdev.financas.repository.GoalRepository;
import io.github.fabasdev.financas.repository.MonthKindTotal;
import io.github.fabasdev.financas.repository.TransactionRepository;
import io.github.fabasdev.financas.repository.UserSettingsRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sugestão educativa de quanto guardar e investir no mês. Porte de reserveInfo e
 * recommendation (finance.ts). Não é recomendação financeira: a tela precisa dizer isso.
 */
@Service
public class InvestmentSuggestionService {

    /** Meses usados na média do custo de vida. */
    static final int COST_AVERAGE_MONTHS = 3;

    private final MonthSummaryService summaries;
    private final TransactionRepository transactions;
    private final GoalRepository goals;
    private final UserSettingsRepository settings;

    public InvestmentSuggestionService(MonthSummaryService summaries, TransactionRepository transactions,
            GoalRepository goals, UserSettingsRepository settings) {
        this.summaries = summaries;
        this.transactions = transactions;
        this.goals = goals;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public Recommendation recommendation(Long userId, YearMonth month) {
        return recommendation(userId, month, summaries.summarize(userId, month));
    }

    /** Mesma conta, reaproveitando um resumo do mês que a tela já calculou. */
    @Transactional(readOnly = true)
    public Recommendation recommendation(Long userId, YearMonth month, MonthSummary summary) {
        UserSettings userSettings = settings.findById(userId).orElseThrow(NotFoundException::new);
        ReserveInfo reserve = reserveInfo(userId, month, userSettings, summary);

        BigDecimal futurePlanned = Money.round(summary.buckets().stream()
                .filter(b -> b.kind() == BucketKind.FUTURE)
                .map(MonthSummary.BucketSummary::planned)
                .reduce(Money.ZERO, BigDecimal::add));
        BigDecimal toReserve = reserve.gap().signum() > 0
                ? Money.min(reserve.gap(), Money.percentOf(futurePlanned, userSettings.getReservePriority()))
                : Money.ZERO;
        BigDecimal toInvest = Money.round(futurePlanned.subtract(toReserve));
        Integer monthsToReserve = reserve.gap().signum() > 0 && toReserve.signum() > 0
                ? reserve.gap().divide(toReserve, 0, RoundingMode.CEILING).intValueExact()
                : null;

        return new Recommendation(
                summary.planIncome(),
                futurePlanned,
                toReserve,
                toInvest,
                summary.saved(),
                Money.max(Money.ZERO, Money.round(futurePlanned.subtract(summary.saved()))),
                monthsToReserve,
                reserve);
    }

    @Transactional(readOnly = true)
    public ReserveInfo reserveInfo(Long userId, YearMonth month) {
        UserSettings userSettings = settings.findById(userId).orElseThrow(NotFoundException::new);
        return reserveInfo(userId, month, userSettings, null);
    }

    /**
     * Alvo da reserva = custo mensal × meses desejados. O custo mensal é a média dos últimos
     * 3 meses com gastos ou, sem histórico, o que a regra planeja para esses gastos.
     */
    private ReserveInfo reserveInfo(Long userId, YearMonth month, UserSettings userSettings, MonthSummary summary) {
        BigDecimal balance = reserveGoal(userId)
                .map(goal -> goalBalance(userId, goal))
                .orElse(Money.ZERO);

        ReserveBase base = userSettings.getReserveBase();
        BigDecimal monthlyCost;
        String basis;
        if (base == ReserveBase.MANUAL) {
            monthlyCost = userSettings.getReserveManualCost();
            basis = base.label();
        } else {
            List<BigDecimal> monthTotals = costPerMonth(userId, month, base);
            if (!monthTotals.isEmpty()) {
                monthlyCost = Money.sum(monthTotals).divide(BigDecimal.valueOf(monthTotals.size()), Money.PRECISION);
                basis = "média de " + base.label() + " dos últimos "
                        + (monthTotals.size() == 1 ? "mês" : monthTotals.size() + " meses");
            } else {
                MonthSummary planned = summary != null ? summary : summaries.summarize(userId, month);
                Predicate<BucketKind> wanted = base == ReserveBase.NEEDS
                        ? kind -> kind == BucketKind.NEEDS
                        : kind -> kind != BucketKind.FUTURE;
                monthlyCost = planned.buckets().stream()
                        .filter(b -> wanted.test(b.kind()))
                        .map(MonthSummary.BucketSummary::planned)
                        .reduce(Money.ZERO, BigDecimal::add);
                basis = base.label() + " planejados (ainda sem histórico)";
            }
        }

        monthlyCost = Money.round(monthlyCost);
        BigDecimal target = Money.round(monthlyCost.multiply(BigDecimal.valueOf(userSettings.getReserveMonths())));
        BigDecimal gap = Money.max(Money.ZERO, Money.round(target.subtract(balance)));
        BigDecimal progress = target.signum() > 0 ? Money.min(BigDecimal.ONE, Money.ratio(balance, target))
                : BigDecimal.ZERO;
        return new ReserveInfo(monthlyCost, target, balance, gap, progress, Money.ratio(balance, monthlyCost), basis);
    }

    /** Gasto de cada um dos últimos 3 meses (só os meses com algum gasto). */
    private List<BigDecimal> costPerMonth(Long userId, YearMonth month, ReserveBase base) {
        YearMonth first = month.minusMonths(COST_AVERAGE_MONTHS - 1);
        Predicate<BucketKind> wanted = base == ReserveBase.NEEDS
                ? kind -> kind == BucketKind.NEEDS
                // Sem categoria também conta como gasto.
                : kind -> kind != BucketKind.FUTURE;
        Map<YearMonth, BigDecimal> perMonth = new TreeMap<>();
        for (MonthKindTotal row : transactions.totalsByMonthAndKind(userId, first.atDay(1), Months.endExclusive(month))) {
            if (row.type() == TransactionType.EXPENSE && wanted.test(row.kind())) {
                perMonth.merge(row.yearMonth(), row.total(), BigDecimal::add);
            }
        }
        return perMonth.values().stream().filter(v -> v.signum() > 0).toList();
    }

    private Optional<Goal> reserveGoal(Long userId) {
        return goals.findByUserIdAndSystemKey(userId, Goal.RESERVE_KEY)
                .or(() -> goals.findFirstByUserIdAndKindOrderByIdAsc(userId, GoalKind.RESERVE));
    }

    /** Saldo de uma meta: saldo inicial + aportes pagos ligados a ela. */
    BigDecimal goalBalance(Long userId, Goal goal) {
        return Money.round(goal.getInitialBalance().add(transactions.sumPaidExpensesForGoal(userId, goal.getId())));
    }
}
