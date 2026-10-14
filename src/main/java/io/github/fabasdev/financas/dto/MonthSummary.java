package io.github.fabasdev.financas.dto;

import io.github.fabasdev.financas.domain.BucketKind;
import java.math.BigDecimal;
import java.math.RoundingMode;
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

        private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

        /** Quanto do planejado já foi usado, em % inteiro (121 = 21% acima). Null quando nada foi planejado. */
        public Integer usedPercent() {
            if (planned.signum() <= 0) {
                return null;
            }
            return actual.multiply(HUNDRED).divide(planned, 0, RoundingMode.HALF_UP).intValue();
        }

        /**
         * Largura da barra, de 0 a 100: passar do plano enche a barra, e o texto ao lado diz quanto.
         * Sem nada planejado (mês sem renda), qualquer gasto já passa do plano e enche a barra.
         */
        public int barWidth() {
            Integer used = usedPercent();
            if (used == null) {
                return actual.signum() > 0 ? 100 : 0;
            }
            return Math.clamp(used, 0, 100);
        }

        /** Gastou mais que o planejado. No pote de futuro (guardar e investir), passar do plano é bom. */
        public boolean overPlan() {
            return kind != BucketKind.FUTURE && actual.compareTo(planned) > 0;
        }
    }

    public record CategorySummary(Long categoryId, String name, String icon, BucketKind kind, Long bucketId,
            BigDecimal budget, BigDecimal actual, long count) {
    }
}
