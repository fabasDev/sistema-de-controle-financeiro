package io.github.fabasdev.financas.service;

import io.github.fabasdev.financas.domain.Money;
import io.github.fabasdev.financas.dto.Simulation;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Simulador de juros compostos, todo em BigDecimal. Porte de simulate, monthsToTarget e
 * requiredMonthly (finance.ts). Aportes no fim de cada mês, com juros mensais equivalentes
 * à taxa anual: (1 + taxa anual)^(1/12) − 1.
 */
@Service
public class SimulationService {

    /** Limite de 100 anos na busca de meses até a meta. */
    static final int MAX_MONTHS = 1200;

    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);
    private static final BigDecimal EPSILON = new BigDecimal("1E-30");

    public List<Simulation.Point> simulate(Simulation.Params p) {
        BigDecimal growth = BigDecimal.ONE.add(monthlyRate(p.annualRate()));
        BigDecimal inflationGrowth = BigDecimal.ONE.add(monthlyRate(p.inflation()));
        BigDecimal contributionGrowth = yearlyGrowth(p.yearlyIncrease());
        int months = Math.max(1, p.years() * 12);

        BigDecimal total = p.initial();
        BigDecimal invested = p.initial();
        BigDecimal contribution = p.monthly();
        // (1 + inflação mensal)^m, acumulado mês a mês.
        BigDecimal deflator = BigDecimal.ONE;
        List<Simulation.Point> points = new ArrayList<>(months + 1);
        points.add(new Simulation.Point(0, Money.round(invested), Money.round(total), Money.round(total)));
        for (int m = 1; m <= months; m++) {
            if (m > 1 && (m - 1) % 12 == 0) {
                contribution = contribution.multiply(contributionGrowth, Money.PRECISION);
            }
            total = total.multiply(growth, Money.PRECISION).add(contribution, Money.PRECISION);
            invested = invested.add(contribution, Money.PRECISION);
            deflator = deflator.multiply(inflationGrowth, Money.PRECISION);
            points.add(new Simulation.Point(m, Money.round(invested), Money.round(total),
                    Money.round(total.divide(deflator, Money.PRECISION))));
        }
        return points;
    }

    /** Meses até o total chegar em {@code target}. Vazio se não chegar em 100 anos. */
    public Optional<Integer> monthsToTarget(BigDecimal initial, BigDecimal monthly, BigDecimal annualRate,
            BigDecimal yearlyIncrease, BigDecimal target) {
        if (initial.compareTo(target) >= 0) {
            return Optional.of(0);
        }
        BigDecimal growth = BigDecimal.ONE.add(monthlyRate(annualRate));
        BigDecimal contributionGrowth = yearlyGrowth(yearlyIncrease);
        BigDecimal total = initial;
        BigDecimal contribution = monthly;
        for (int m = 1; m <= MAX_MONTHS; m++) {
            if (m > 1 && (m - 1) % 12 == 0) {
                contribution = contribution.multiply(contributionGrowth, Money.PRECISION);
            }
            total = total.multiply(growth, Money.PRECISION).add(contribution, Money.PRECISION);
            if (total.compareTo(target) >= 0) {
                return Optional.of(m);
            }
        }
        return Optional.empty();
    }

    /**
     * Aporte mensal constante para chegar em {@code target} em {@code months} meses.
     * Arredonda para cima: é o mínimo que garante chegar ao alvo.
     */
    public BigDecimal requiredMonthly(BigDecimal initial, BigDecimal annualRate, int months, BigDecimal target) {
        BigDecimal r = monthlyRate(annualRate);
        BigDecimal compound = BigDecimal.ONE.add(r).pow(months, Money.PRECISION);
        BigDecimal grownInitial = initial.multiply(compound, Money.PRECISION);
        if (grownInitial.compareTo(target) >= 0) {
            return Money.ZERO;
        }
        if (r.signum() == 0) {
            return target.subtract(initial).divide(BigDecimal.valueOf(months), Money.SCALE, RoundingMode.CEILING);
        }
        BigDecimal needed = target.subtract(grownInitial).multiply(r, Money.PRECISION)
                .divide(compound.subtract(BigDecimal.ONE), Money.PRECISION);
        return needed.setScale(Money.SCALE, RoundingMode.CEILING);
    }

    /** Taxa mensal equivalente a uma taxa anual em %: (1 + a/100)^(1/12) − 1. */
    public static BigDecimal monthlyRate(BigDecimal annualPercent) {
        BigDecimal yearly = BigDecimal.ONE.add(annualPercent.divide(Money.HUNDRED, Money.PRECISION));
        return twelfthRoot(yearly).subtract(BigDecimal.ONE, Money.PRECISION);
    }

    private static BigDecimal yearlyGrowth(BigDecimal percent) {
        return BigDecimal.ONE.add(percent.divide(Money.HUNDRED, Money.PRECISION));
    }

    /**
     * Raiz 12 pelo método de Newton: x ← (11·x + v / x¹¹) / 12.
     * BigDecimal não tem potência fracionária, e usar Math.pow traria o double de volta.
     */
    static BigDecimal twelfthRoot(BigDecimal value) {
        if (value.compareTo(BigDecimal.ONE) == 0) {
            return BigDecimal.ONE;
        }
        // Começa acima da raiz (desigualdade de Bernoulli), e o método desce até ela.
        BigDecimal x = BigDecimal.ONE.add(value.subtract(BigDecimal.ONE).divide(TWELVE, Money.PRECISION));
        BigDecimal eleven = BigDecimal.valueOf(11);
        for (int i = 0; i < 100; i++) {
            BigDecimal next = x.multiply(eleven, Money.PRECISION)
                    .add(value.divide(x.pow(11, Money.PRECISION), Money.PRECISION))
                    .divide(TWELVE, Money.PRECISION);
            if (next.subtract(x).abs().compareTo(EPSILON) <= 0) {
                return next;
            }
            x = next;
        }
        return x;
    }
}
