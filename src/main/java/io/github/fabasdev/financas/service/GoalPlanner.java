package io.github.fabasdev.financas.service;

import io.github.fabasdev.financas.domain.Money;
import io.github.fabasdev.financas.domain.Months;
import io.github.fabasdev.financas.dto.GoalPlan;
import java.math.BigDecimal;
import java.time.YearMonth;

/** Quanto falta e quanto guardar por mês para uma meta. Porte de goalPlan (finance.ts). */
public final class GoalPlanner {

    private GoalPlanner() {
    }

    public static GoalPlan plan(BigDecimal target, YearMonth deadline, BigDecimal balance, YearMonth month) {
        BigDecimal remaining = Money.max(Money.ZERO, Money.round(target.subtract(balance)));
        Integer monthsLeft = deadline == null ? null : (int) Math.max(1, Months.between(month, deadline) + 1);
        boolean hasTarget = target.signum() > 0;
        BigDecimal progress = hasTarget ? Money.min(BigDecimal.ONE, Money.ratio(balance, target)) : BigDecimal.ZERO;
        BigDecimal monthlyNeeded = monthsLeft != null && hasTarget
                ? remaining.divide(BigDecimal.valueOf(monthsLeft), Money.SCALE, Money.ROUNDING)
                : null;
        return new GoalPlan(balance, remaining, progress, monthsLeft, monthlyNeeded,
                hasTarget && balance.compareTo(target) >= 0);
    }
}
