package io.github.fabasdev.financas.service.backup;

import static io.github.fabasdev.financas.support.TestAccounts.randomIp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.fabasdev.financas.domain.AssetClass;
import io.github.fabasdev.financas.domain.BucketKind;
import io.github.fabasdev.financas.domain.Category;
import io.github.fabasdev.financas.domain.Goal;
import io.github.fabasdev.financas.domain.ReserveBase;
import io.github.fabasdev.financas.domain.RiskProfile;
import io.github.fabasdev.financas.domain.Transaction;
import io.github.fabasdev.financas.domain.UserSettings;
import io.github.fabasdev.financas.dto.MonthSummary;
import io.github.fabasdev.financas.repository.CategoryRepository;
import io.github.fabasdev.financas.repository.GoalRepository;
import io.github.fabasdev.financas.repository.InvestorProfileRepository;
import io.github.fabasdev.financas.repository.RecurringEntryRepository;
import io.github.fabasdev.financas.repository.TransactionRepository;
import io.github.fabasdev.financas.repository.UserSettingsRepository;
import io.github.fabasdev.financas.security.AppUserPrincipal;
import io.github.fabasdev.financas.service.InvalidInputException;
import io.github.fabasdev.financas.service.InvestmentSuggestionService;
import io.github.fabasdev.financas.service.MonthSummaryService;
import io.github.fabasdev.financas.support.SampleData;
import io.github.fabasdev.financas.support.TestAccounts;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Importação do backup do app antigo. O arquivo de teste foi gerado pelo exportEncryptedBackup
 * real do app React (legacy-web/scripts/backup-fixture.test.ts), com PBKDF2 de 600 mil iterações.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BackupImportTest {

    /** Senha descartável usada só para gerar o arquivo de teste. */
    static final String PASSWORD = "backup de teste com ção e café";

    @Autowired
    BackupImportService importer;
    @Autowired
    TestAccounts accounts;
    @Autowired
    SampleData sample;
    @Autowired
    TransactionRepository transactions;
    @Autowired
    CategoryRepository categories;
    @Autowired
    GoalRepository goals;
    @Autowired
    RecurringEntryRepository recurring;
    @Autowired
    InvestorProfileRepository profiles;
    @Autowired
    UserSettingsRepository settings;
    @Autowired
    MonthSummaryService summaries;
    @Autowired
    InvestmentSuggestionService suggestions;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    MockMvc mvc;

    byte[] fixture;
    AppUserPrincipal me;

    @BeforeEach
    void setUp() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/backup/backup-antigo.json")) {
            fixture = in.readAllBytes();
        }
        me = accounts.create().principal();
    }

    @Test
    void importaTudoDoBackupGeradoPeloAppAntigo() {
        BackupImportService.ImportResult result = importer.importLegacy(me.getId(), fixture, PASSWORD, randomIp());

        assertThat(result.buckets()).isEqualTo(3);
        assertThat(result.categories()).isEqualTo(18);
        assertThat(result.goals()).isEqualTo(3);
        assertThat(result.recurring()).isEqualTo(3);
        assertThat(result.transactions()).isEqualTo(7);
        assertThat(result.profile()).isTrue();
        assertThat(result.warnings()).isEmpty();

        tx.executeWithoutResult(s -> {
            Category pets = categories.findByUserIdOrderByIdAsc(me.getId()).stream()
                    .filter(c -> c.getName().equals("Pets")).findFirst().orElseThrow();
            assertThat(pets.getBucket().getName()).isEqualTo("Desejos");
            assertThat(pets.getKind()).isEqualTo(BucketKind.WANTS);
            assertThat(pets.getBudget()).isEqualByComparingTo("150");
            assertThat(pets.getSystemKey()).isNull();

            // A categoria padrão "cat-reserva" volta com a chave de sistema e ligada à reserva.
            Category reserveCat = categories.findByUserIdAndSystemKey(me.getId(), "emergency_reserve").orElseThrow();
            assertThat(reserveCat.getGoal().getSystemKey()).isEqualTo(Goal.RESERVE_KEY);

            Goal trip = goals.findByUserIdOrderByIdAsc(me.getId()).stream()
                    .filter(g -> g.getName().equals("Viagem")).findFirst().orElseThrow();
            assertThat(trip.getTarget()).isEqualByComparingTo("6000");
            assertThat(trip.getInitialBalance()).isEqualByComparingTo("1200.50");
            assertThat(trip.getDeadline()).isEqualTo(YearMonth.of(2027, 3));

            var internet = recurring.findByUserIdOrderByIdAsc(me.getId()).stream()
                    .filter(r -> r.getDescription().equals("Internet")).findFirst().orElseThrow();
            assertThat(internet.getSkippedMonths()).containsExactly(YearMonth.of(2026, 3));
            var notebook = recurring.findByUserIdOrderByIdAsc(me.getId()).stream()
                    .filter(r -> r.getDescription().equals("Notebook")).findFirst().orElseThrow();
            assertThat(notebook.getEndMonth()).isEqualTo(YearMonth.of(2026, 12));
            assertThat(notebook.getDayOfMonth()).isEqualTo(31);

            List<Transaction> all = transactions.findAll().stream()
                    .filter(t -> t.getUser().getId().equals(me.getId())).toList();
            Transaction internetSep = all.stream().filter(t -> t.getDescription().equals("Internet")).findFirst().orElseThrow();
            assertThat(internetSep.getRecurring().getId()).isEqualTo(internet.getId());
            assertThat(internetSep.getRecurringMonth()).isEqualTo(YearMonth.of(2026, 9));
            // 1,005 no JSON: o app antigo mostrava 1,00 (erro de ponto flutuante); BigDecimal HALF_UP dá 1,01.
            Transaction market = all.stream().filter(t -> t.getDescription().equals("Mercado")).findFirst().orElseThrow();
            assertThat(market.getAmount()).isEqualByComparingTo("1.01");
            Transaction passage = all.stream().filter(t -> t.getDescription().equals("Passagem")).findFirst().orElseThrow();
            assertThat(passage.getGoal().getId()).isEqualTo(trip.getId());
            assertThat(passage.getDate()).isEqualTo(LocalDate.of(2026, 10, 3));
        });

        UserSettings s = settings.findById(me.getId()).orElseThrow();
        assertThat(s.getDisplayName()).isEqualTo("Pessoa de Teste");
        assertThat(s.getReserveMonths()).isEqualTo(8);
        assertThat(s.getReserveBase()).isEqualTo(ReserveBase.NEEDS);
        assertThat(s.getReservePriority()).isEqualByComparingTo("60");
        assertThat(s.getSimMonthly()).isEqualByComparingTo("300.50");
        assertThat(s.getSimAnnualRate()).isEqualByComparingTo("11.25");
        assertThat(s.getSimYears()).isEqualTo(15);
        assertThat(s.getPresetId()).isEqualTo("50-30-20");

        tx.executeWithoutResult(st -> {
            var profile = profiles.findById(me.getId()).orElseThrow();
            assertThat(profile.getProfile()).isEqualTo(RiskProfile.MODERATE);
            assertThat(profile.getScore()).isEqualTo(12);
            assertThat(profile.getCustomAllocation()).containsOnlyKeys(AssetClass.STOCKS, AssetClass.INTERNATIONAL);
            assertThat(profile.getCustomAllocation().get(AssetClass.STOCKS)).isEqualByComparingTo("20");
        });

        // Os cálculos funcionam em cima dos dados importados.
        MonthSummary sep = summaries.summarize(me.getId(), YearMonth.of(2026, 9));
        assertThat(sep.income()).isEqualByComparingTo("4000");
        assertThat(sep.saved()).isEqualByComparingTo("500");
        assertThat(sep.spent()).isEqualByComparingTo("540.22"); // 99,90 + 89,99 + 350,33 (o notebook, ainda a pagar)
        assertThat(sep.unpaid()).isEqualByComparingTo("350.33");
        assertThat(suggestions.reserveInfo(me.getId(), YearMonth.of(2026, 10)).balance()).isEqualByComparingTo("1500");
    }

    /** "café" digitado com o acento separado (e + U+0301) vira a mesma senha depois do NFKC, como no navegador. */
    @Test
    void senhaComAcentoDecompostoTambemAbre() {
        String decomposed = PASSWORD.replace("é", "é");
        assertThat(decomposed).isNotEqualTo(PASSWORD);
        assertThat(importer.importLegacy(me.getId(), fixture, decomposed, randomIp()).transactions()).isEqualTo(7);
    }

    @Test
    void senhaErradaNaoApagaOsDadosAtuais() {
        SampleData.Ids before = sample.create(me.getId());

        assertThatThrownBy(() -> importer.importLegacy(me.getId(), fixture, "senha errada", randomIp()))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("Senha do backup incorreta ou arquivo alterado.");
        assertThat(transactions.findById(before.transaction())).isPresent();
    }

    @Test
    void arquivoAlteradoEhRecusado() {
        String json = new String(fixture, StandardCharsets.UTF_8);
        int i = json.indexOf("\"data\":\"") + 20;
        char swapped = json.charAt(i) == 'A' ? 'B' : 'A';
        byte[] tampered = (json.substring(0, i) + swapped + json.substring(i + 1)).getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> importer.importLegacy(me.getId(), tampered, PASSWORD, randomIp()))
                .hasMessage("Senha do backup incorreta ou arquivo alterado.");
    }

    /** Iterações fora de 100 mil a 1 milhão são recusadas antes de rodar o PBKDF2 (proteção contra DoS). */
    @Test
    void iteracoesForaDoLimiteSaoRecusadasSemGastarCpu() {
        String json = new String(fixture, StandardCharsets.UTF_8);
        for (String iterations : List.of("10000000", "1000")) {
            byte[] changed = json.replace("\"iterations\":600000", "\"iterations\":" + iterations)
                    .getBytes(StandardCharsets.UTF_8);
            long start = System.nanoTime();
            assertThatThrownBy(() -> importer.importLegacy(me.getId(), changed, PASSWORD, randomIp()))
                    .isInstanceOf(InvalidInputException.class)
                    .hasMessageContaining("parâmetro de segurança");
            assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(500);
        }
    }

    @Test
    void arquivoQueNaoEhBackupEhRecusado() {
        for (String content : List.of("não é json", "{\"app\":\"outro\"}", "[]", "{}")) {
            assertThatThrownBy(() -> importer.importLegacy(me.getId(), content.getBytes(StandardCharsets.UTF_8),
                    PASSWORD, randomIp()))
                    .hasMessage("Este arquivo não é um backup do app Finanças.");
        }
    }

    @Test
    void importarSubstituiOsDadosAtuaisSoDoUsuarioLogado() {
        SampleData.Ids mine = sample.create(me.getId());
        AppUserPrincipal other = accounts.create().principal();
        SampleData.Ids theirs = sample.create(other.getId());

        importer.importLegacy(me.getId(), fixture, PASSWORD, randomIp());

        assertThat(transactions.findById(mine.transaction())).isEmpty();
        assertThat(transactions.findById(theirs.transaction())).isPresent();
        assertThat(goals.findById(theirs.goal())).isPresent();
    }

    @Test
    void bloqueiaDepoisDeMuitasSenhasErradas() {
        String ip = randomIp();
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> importer.importLegacy(me.getId(), fixture, "errada", ip));
        }
        assertThatThrownBy(() -> importer.importLegacy(me.getId(), fixture, PASSWORD, ip))
                .hasMessageContaining("Muitas tentativas");
    }

    @Test
    void telaDeImportacaoComTokenCsrf() throws Exception {
        MockMultipartFile file = new MockMultipartFile("arquivo", "backup.json", "application/json", fixture);
        mvc.perform(multipart("/ajustes/importar").file(file).param("senha", PASSWORD).param("confirmo", "sim")
                        .with(user(me)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Backup importado")));
        mvc.perform(multipart("/ajustes/importar").file(file).param("senha", PASSWORD).param("confirmo", "sim")
                        .with(user(me)))
                .andExpect(status().isForbidden());
    }
}
