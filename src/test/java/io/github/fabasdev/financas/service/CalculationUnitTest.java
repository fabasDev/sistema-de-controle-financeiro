package io.github.fabasdev.financas.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fabasdev.financas.domain.AssetClass;
import io.github.fabasdev.financas.domain.Money;
import io.github.fabasdev.financas.domain.Months;
import io.github.fabasdev.financas.domain.RiskProfile;
import io.github.fabasdev.financas.dto.GoalPlan;
import io.github.fabasdev.financas.dto.PlanItem;
import io.github.fabasdev.financas.dto.Simulation;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Cálculos sem banco: casos de finance.test.ts (parseBRL, meses, metas, regras, simulador)
 * e de advisor.test.ts (carteiras).
 */
class CalculationUnitTest {

    @Nested
    class ParseBrl {

        @ParameterizedTest(name = "\"{0}\" → {1}")
        @CsvSource(delimiter = '|', value = {
                "1.234,56  | 1234.56",
                "1234,5    | 1234.5",
                "R$ 2.500  | 2500",
                "12.5      | 12.5",
                "1.000.000 | 1000000"})
        void converteONumeroDigitado(String input, String expected) {
            assertThat(Money.parse(input)).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo(expected));
        }

        @ParameterizedTest(name = "\"{0}\" → vazio")
        @CsvSource(delimiter = '|', value = {"''", "abc"})
        void textoSemNumeroNaoViraValor(String input) {
            assertThat(Money.parse(input)).isEmpty();
        }

        /** No app antigo, 1,005 virava 1,00 por erro de ponto flutuante. Com BigDecimal vira 1,01. */
        @Test
        void meioCentavoArredondaParaCima() {
            assertThat(Money.parse("1,005")).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("1.01"));
        }
    }

    @Nested
    class Meses {

        @Test
        void somaESubtraiAtravessandoOAno() {
            assertThat(YearMonth.of(2026, 11).plusMonths(3)).isEqualTo(YearMonth.of(2027, 2));
            assertThat(YearMonth.of(2026, 1).minusMonths(1)).isEqualTo(YearMonth.of(2025, 12));
            assertThat(Months.between(YearMonth.of(2026, 10), YearMonth.of(2027, 4))).isEqualTo(6);
            assertThat(Months.range(YearMonth.of(2026, 2), 3))
                    .containsExactly(YearMonth.of(2025, 12), YearMonth.of(2026, 1), YearMonth.of(2026, 2));
        }

        @Test
        void diaDoMesLimitadoAoUltimoDia() {
            assertThat(Months.dateInMonth(YearMonth.of(2026, 2), 31)).hasDayOfMonth(28);
        }
    }

    @Nested
    class Metas {

        @Test
        void calculaQuantoGuardarPorMesAteOPrazo() {
            GoalPlan plan = GoalPlanner.plan(new BigDecimal("6000"), YearMonth.of(2027, 3), new BigDecimal("1200"),
                    YearMonth.of(2026, 10));

            assertThat(plan.monthsLeft()).isEqualTo(6);
            assertThat(plan.monthlyNeeded()).isEqualByComparingTo("800");
            assertThat(plan.progress()).isEqualByComparingTo("0.2");
        }
    }

    @Nested
    class Regras {

        @Test
        void normalizaPercentuaisPara100() {
            List<BigDecimal> result = RuleService.normalizePercents(
                    List.of(new BigDecimal("30"), new BigDecimal("30"), new BigDecimal("30")));

            assertThat(Money.sum(result)).isEqualByComparingTo("100");
        }
    }

    @Nested
    class Simulador {

        final SimulationService simulator = new SimulationService();

        @Test
        void semJurosTotalEhInicialMaisAportes() {
            List<Simulation.Point> pts = simulator.simulate(params("1000", "100", "0", 1));

            assertThat(pts).hasSize(13);
            assertThat(pts.getLast().total()).isEqualByComparingTo("2200");
            assertThat(pts.getLast().invested()).isEqualByComparingTo("2200");
        }

        @Test
        void jurosCompostosAnuaisEquivalentes() {
            List<Simulation.Point> pts = simulator.simulate(params("1000", "0", "10", 2));

            assertThat(pts.getLast().total()).isEqualByComparingTo("1210.00");
        }

        @Test
        void aporteNecessarioETempoAteAMetaSaoCoerentes() {
            BigDecimal monthly = simulator.requiredMonthly(BigDecimal.ZERO, BigDecimal.TEN, 120,
                    new BigDecimal("100000"));

            assertThat(simulator.monthsToTarget(BigDecimal.ZERO, monthly, BigDecimal.TEN, BigDecimal.ZERO,
                    new BigDecimal("100000"))).contains(120);
        }

        @Test
        void taxaMensalEquivalenteElevadaA12VoltaATaxaAnual() {
            BigDecimal r = SimulationService.monthlyRate(BigDecimal.TEN);

            assertThat(BigDecimal.ONE.add(r).pow(12, Money.PRECISION).setScale(20, Money.ROUNDING))
                    .isEqualByComparingTo("1.10000000000000000000");
        }

        private Simulation.Params params(String initial, String monthly, String rate, int years) {
            return new Simulation.Params(new BigDecimal(initial), new BigDecimal(monthly), new BigDecimal(rate), years,
                    BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    @Nested
    class Carteiras {

        final PortfolioService portfolio = new PortfolioService();

        @Test
        void cadaPerfilSoma100ECobreTodasAsClasses() {
            for (Map<AssetClass, BigDecimal> alloc : PortfolioService.DEFAULT_ALLOCATION.values()) {
                assertThat(Money.sum(alloc.values())).isEqualByComparingTo("100");
                assertThat(alloc.keySet()).containsExactlyInAnyOrder(AssetClass.values());
            }
        }

        @Test
        void divideOValorSemPerderCentavos() {
            List<PlanItem> items = portfolio.split(new BigDecimal("1000.01"),
                    PortfolioService.DEFAULT_ALLOCATION.get(RiskProfile.MODERATE));

            assertThat(Money.sum(items.stream().map(PlanItem::amount).toList())).isEqualByComparingTo("1000.01");
            assertThat(items.getFirst().percent()).isGreaterThanOrEqualTo(items.getLast().percent());
        }

        @Test
        void ignoraClassesCom0() {
            List<PlanItem> items = portfolio.split(new BigDecimal("500"),
                    PortfolioService.DEFAULT_ALLOCATION.get(RiskProfile.CONSERVATIVE));

            assertThat(items).extracting(PlanItem::assetClass).doesNotContain(AssetClass.STOCKS);
        }

        @Test
        void ajusteDoUsuarioSobrepoeOPerfil() {
            Map<AssetClass, BigDecimal> alloc = portfolio.allocationFor(RiskProfile.CONSERVATIVE,
                    Map.of(AssetClass.STOCKS, new BigDecimal("10")));

            assertThat(alloc.get(AssetClass.STOCKS)).isEqualByComparingTo("10");
            assertThat(alloc.get(AssetClass.POST_FIXED)).isEqualByComparingTo("45");
        }
    }
}
