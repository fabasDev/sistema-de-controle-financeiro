package io.github.fabasdev.financas.dto;

import io.github.fabasdev.financas.domain.BucketKind;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * Resumo de um mês. Porte de summarizeMonth (legacy-web/src/lib/finance.ts).
 *
 * @param spent       consumo: despesas fora das categorias de "futuro"
 * @param saved       aportes: despesas em categorias de "futuro"
 * @param outflow     todas as saídas (consumo + aportes)
 * @param balance     o que sobra depois de gastos e aportes
 * @param savingsRate aportes / renda (0,16 = 16%)
 * @param planIncome  renda usada no plano: a do mês ou, se ainda não houver, a das receitas fixas
 */
public record MonthSummary(
        YearMonth month,
        BigDecimal income,
        BigDecimal spent,
        BigDecimal saved,
        BigDecimal outflow,
        BigDecimal balance,
        BigDecimal savingsRate,
        BigDecimal unpaid,
        BigDecimal pendingIncome,
        BigDecimal planIncome,
        List<BucketSummary> buckets,
        List<CategorySummary> categories,
        boolean hasData) {

    public record BucketSummary(Long bucketId, int index, String name, BucketKind kind, BigDecimal percent,
            BigDecimal planned, BigDecimal actual) {
    }

    public record CategorySummary(Long categoryId, String name, String icon, BucketKind kind, Long bucketId,
            BigDecimal budget, BigDecimal actual, long count) {
    }
}
