package io.github.fabasdev.financas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.github.fabasdev.financas.domain.Goal;
import io.github.fabasdev.financas.domain.RecurringEntry;
import io.github.fabasdev.financas.domain.TransactionType;
import io.github.fabasdev.financas.domain.User;
import io.github.fabasdev.financas.dto.MonthSummary;
import io.github.fabasdev.financas.dto.Recommendation;
import io.github.fabasdev.financas.dto.ReserveInfo;
import io.github.fabasdev.financas.repository.CategoryRepository;
import io.github.fabasdev.financas.repository.RecurringEntryRepository;
import io.github.fabasdev.financas.support.TestFixtures;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de legacy-web/src/lib/finance.test.ts, com os mesmos valores esperados.
 * Rodam contra o banco (H2) para testar também as consultas SUM ... GROUP BY.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FinanceCalculationsTest {

    static final YearMonth M = YearMonth.of(2026, 10);
    static final LocalDate DAY = M.atDay(5);

    @Autowired
    TestFixtures fixtures;
    @Autowired
    MonthSummaryService summaries;
    @Autowired
    InvestmentSuggestionService suggestions;
    @Autowired
    RuleService rules;
    @Autowired
    RecurringEntryRepository recurring;
    @Autowired
    CategoryRepository categories;

    @Nested
    class SummarizeMonth {

        @Test
        void separaConsumoDeAportesECalculaOPlanoPorPote() {
            User user = fixtures.newUser();
            fixtures.income(user, "5000", DAY);
            fixtures.expense(user, "housing", "1500", DAY);
            fixtures.expense(user, "leisure", "500", DAY, false, null);
            fixtures.expense(user, "emergency_reserve", "800", DAY, true, fixtures.goal(user, Goal.RESERVE_KEY));
            fixtures.expense(user, "groceries", "999", LocalDate.of(2026, 9, 1));

            MonthSummary s = summaries.summarize(user.getId(), M);

            assertThat(s.income()).isEqualByComparingTo("5000");
            assertThat(s.spent()).isEqualByComparingTo("2000");
            assertThat(s.saved()).isEqualByComparingTo("800");
            assertThat(s.balance()).isEqualByComparingTo("2200");
            assertThat(s.unpaid()).isEqualByComparingTo("500");
            assertThat(s.savingsRate()).isEqualByComparingTo("0.16");
            assertThat(s.buckets())
                    .extracting(MonthSummary.BucketSummary::name,
                            b -> b.planned().stripTrailingZeros().toPlainString(),
                            b -> b.actual().stripTrailingZeros().toPlainString())
                    .containsExactly(
                            tuple("Necessidades", "2500", "1500"),
                            tuple("Desejos", "1500", "500"),
                            tuple("Futuro", "1000", "800"));
            assertThat(s.hasData()).isTrue();
        }

        @Test
        void usaAsReceitasFixasComoBaseQuandoOMesAindaNaoTemRenda() {
            User user = fixtures.newUser();
            recurring.save(new RecurringEntry(user, TransactionType.INCOME, "Salário", new BigDecimal("4000"), 5,
                    YearMonth.of(2026, 1)));

            assertThat(summaries.summarize(user.getId(), M).planIncome()).isEqualByComparingTo("4000");
        }

        @Test
        void gastoSemCategoriaEntraEmOutros() {
            User user = fixtures.newUser();
            fixtures.expense(user, null, "120", DAY);

            MonthSummary s = summaries.summarize(user.getId(), M);

            assertThat(s.spent()).isEqualByComparingTo("120");
            assertThat(s.categories()).singleElement()
                    .satisfies(c -> assertThat(c.name()).isEqualTo("Outros"));
        }
    }

    @Nested
    class ReservaERecomendacao {

        @Test
        void metaEhAMediaDosGastosEssenciaisVezesOsMesesEAPrioridadeDivideOFuturo() {
            User user = fixtures.newUser();
            fixtures.income(user, "10000", DAY);
            fixtures.expense(user, "housing", "3000", DAY);
            fixtures.expense(user, "housing", "1000", LocalDate.of(2026, 9, 5));
            fixtures.expense(user, "leisure", "5000", DAY);
            fixtures.goal(user, Goal.RESERVE_KEY).setInitialBalance(new BigDecimal("2000"));

            ReserveInfo r = suggestions.reserveInfo(user.getId(), M);
            assertThat(r.monthlyCost()).isEqualByComparingTo("2000");
            assertThat(r.target()).isEqualByComparingTo("12000");
            assertThat(r.gap()).isEqualByComparingTo("10000");
            assertThat(r.monthsCovered()).isEqualByComparingTo("1");

            Recommendation rec = suggestions.recommendation(user.getId(), M);
            assertThat(rec.futurePlanned()).isEqualByComparingTo("2000");
            assertThat(rec.toReserve()).isEqualByComparingTo("1400");
            assertThat(rec.toInvest()).isEqualByComparingTo("600");
            assertThat(rec.monthsToReserve()).isEqualTo(8);
        }

        @Test
        void comAReservaCompletaTodoOFuturoVaiParaInvestimentos() {
            User user = fixtures.newUser();
            fixtures.income(user, "10000", DAY);
            fixtures.expense(user, "housing", "1000", DAY);
            fixtures.goal(user, Goal.RESERVE_KEY).setInitialBalance(new BigDecimal("50000"));

            Recommendation rec = suggestions.recommendation(user.getId(), M);
            assertThat(rec.toReserve()).isEqualByComparingTo("0");
            assertThat(rec.toInvest()).isEqualByComparingTo("2000");
            assertThat(rec.monthsToReserve()).isNull();
        }

        @Test
        void aportesNaoPagosNaoContamNoSaldoDaMeta() {
            User user = fixtures.newUser();
            fixtures.expense(user, "emergency_reserve", "300", DAY, false, fixtures.goal(user, Goal.RESERVE_KEY));

            assertThat(suggestions.reserveInfo(user.getId(), M).balance()).isEqualByComparingTo("0");
        }
    }

    @Nested
    class Regras {

        @Test
        void seisPotesDirecionaCategoriasReivindicadasEAsDemaisPorTipo() {
            User user = fixtures.newUser();
            rules.applyPreset(user.getId(), "6-potes");

            assertThat(bucketOf(user, "emergency_reserve")).isEqualTo("Longo prazo");
            assertThat(bucketOf(user, "investments")).isEqualTo("Liberdade financeira");
            assertThat(bucketOf(user, "education")).isEqualTo("Educação");
            assertThat(bucketOf(user, "leisure")).isEqualTo("Diversão");
            assertThat(bucketOf(user, "groceries")).isEqualTo("Necessidades");
        }

        private String bucketOf(User user, String key) {
            return fixtures.category(user, key).getBucket().getName();
        }
    }
}
