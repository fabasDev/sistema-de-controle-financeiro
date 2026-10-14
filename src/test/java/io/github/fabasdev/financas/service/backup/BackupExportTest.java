package io.github.fabasdev.financas.service.backup;

import static io.github.fabasdev.financas.support.TestAccounts.randomIp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.fabasdev.financas.domain.Goal;
import io.github.fabasdev.financas.domain.InvestorProfile;
import io.github.fabasdev.financas.domain.UserSettings;
import io.github.fabasdev.financas.repository.BucketRepository;
import io.github.fabasdev.financas.repository.CategoryRepository;
import io.github.fabasdev.financas.repository.GoalRepository;
import io.github.fabasdev.financas.repository.InvestorProfileRepository;
import io.github.fabasdev.financas.repository.RecurringEntryRepository;
import io.github.fabasdev.financas.repository.TransactionRepository;
import io.github.fabasdev.financas.repository.UserSettingsRepository;
import io.github.fabasdev.financas.security.AppUserPrincipal;
import io.github.fabasdev.financas.service.InvalidInputException;
import io.github.fabasdev.financas.service.RecurringService;
import io.github.fabasdev.financas.support.SampleData;
import io.github.fabasdev.financas.support.TestAccounts;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * "Exportar meus dados". O teste principal é a ida e volta: os dados do backup do app antigo são
 * exportados e importados numa segunda conta, que precisa ficar igual à primeira registro a registro.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BackupExportTest {

    static final String PASSWORD = "outra senha de teste, com ação";

    @Autowired
    BackupExportService exporter;
    @Autowired
    BackupImportService importer;
    @Autowired
    TestAccounts accounts;
    @Autowired
    SampleData sample;
    @Autowired
    RecurringService recurringService;
    @Autowired
    BucketRepository buckets;
    @Autowired
    CategoryRepository categories;
    @Autowired
    GoalRepository goals;
    @Autowired
    RecurringEntryRepository recurring;
    @Autowired
    TransactionRepository transactions;
    @Autowired
    InvestorProfileRepository profiles;
    @Autowired
    UserSettingsRepository settings;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    MockMvc mvc;

    AppUserPrincipal alice;

    @BeforeEach
    void setUp() throws IOException {
        alice = accounts.create().principal();
        try (InputStream in = getClass().getResourceAsStream("/backup/backup-antigo.json")) {
            importer.importLegacy(alice.getId(), in.readAllBytes(), BackupImportTest.PASSWORD, randomIp());
        }
    }

    @Test
    void exportarEImportarEmOutraContaDevolveOsMesmosDados() {
        byte[] file = exporter.export(alice.getId(), PASSWORD, PASSWORD, randomIp());
        AppUserPrincipal copy = accounts.create().principal();

        BackupImportService.ImportResult result = importer.importLegacy(copy.getId(), file, PASSWORD, randomIp());

        assertThat(result.warnings()).isEmpty();
        assertThat(snapshot(copy.getId())).isEqualTo(snapshot(alice.getId()));
        // Abrir os meses gera os lançamentos das contas fixas. Se a ligação entre o lançamento e a
        // conta se perdesse, a cópia ganharia lançamentos repetidos aqui; se os meses pulados se
        // perdessem, contas apagadas voltariam.
        for (YearMonth month = YearMonth.of(2026, 1); !month.isAfter(YearMonth.of(2027, 2)); month = month.plusMonths(1)) {
            recurringService.ensureMonth(alice.getId(), month);
            recurringService.ensureMonth(copy.getId(), month);
        }
        assertThat(snapshot(copy.getId())).isEqualTo(snapshot(alice.getId()));
    }

    @Test
    void registrosPadraoVoltamComAChaveDeSistema() {
        AppUserPrincipal copy = accounts.create().principal();
        importer.importLegacy(copy.getId(), exporter.export(alice.getId(), PASSWORD, PASSWORD, randomIp()), PASSWORD,
                randomIp());

        // Uma reserva só (o app cria a padrão quando o backup não traz uma) e a categoria "Outros",
        // usada no resumo para gasto sem categoria.
        assertThat(goals.findByUserIdOrderByIdAsc(copy.getId()))
                .filteredOn(g -> Goal.RESERVE_KEY.equals(g.getSystemKey())).hasSize(1);
        assertThat(categories.findByUserIdAndSystemKey(copy.getId(), "other")).isPresent();
    }

    /** A senha é normalizada (NFKC) como na importação: "ação" com o til separado abre o mesmo arquivo. */
    @Test
    void senhaComAcentoDecompostoAbreOArquivo() {
        byte[] file = exporter.export(alice.getId(), PASSWORD, PASSWORD, randomIp());
        String decomposed = PASSWORD.replace("ã", "ã");
        assertThat(decomposed).isNotEqualTo(PASSWORD);

        AppUserPrincipal copy = accounts.create().principal();
        assertThat(importer.importLegacy(copy.getId(), file, decomposed, randomIp()).transactions()).isEqualTo(7);
    }

    @Test
    void arquivoNoFormatoDoAppAntigoECadaExportacaoUsaSalENovos() throws Exception {
        byte[] first = exporter.export(alice.getId(), PASSWORD, PASSWORD, randomIp());
        byte[] second = exporter.export(alice.getId(), PASSWORD, PASSWORD, randomIp());

        JsonNode file = JsonMapper.builder().build().readTree(first);
        assertThat(file.path("app").stringValue()).isEqualTo("financas");
        assertThat(file.path("format").stringValue()).isEqualTo("encrypted");
        assertThat(file.path("version").asInt()).isEqualTo(1);
        assertThat(file.path("kdf").path("iterations").asInt()).isEqualTo(600_000);
        assertThat(Base64.getDecoder().decode(file.path("kdf").path("salt").stringValue())).hasSize(16);
        assertThat(Base64.getDecoder().decode(file.path("iv").stringValue())).hasSize(12);
        // Nada legível sai sem criptografia.
        assertThat(new String(first, java.nio.charset.StandardCharsets.UTF_8)).doesNotContain("Passagem", "Viagem");

        JsonNode other = JsonMapper.builder().build().readTree(second);
        assertThat(other.path("kdf").path("salt").stringValue()).isNotEqualTo(file.path("kdf").path("salt").stringValue());
        assertThat(other.path("iv").stringValue()).isNotEqualTo(file.path("iv").stringValue());
    }

    @Test
    void senhaFracaOuDiferenteEhRecusada() {
        assertThatThrownBy(() -> exporter.export(alice.getId(), "curta", "curta", randomIp()))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("pelo menos 12 caracteres");
        assertThatThrownBy(() -> exporter.export(alice.getId(), PASSWORD, PASSWORD + "x", randomIp()))
                .hasMessage("As senhas não são iguais.");
    }

    @Test
    void exportacaoDoBobNaoTrazNadaDaAlice() {
        SampleData.Ids aliceIds = sample.create(alice.getId());
        AppUserPrincipal bob = accounts.create().principal();
        SampleData.Ids bobIds = sample.create(bob.getId());

        String bobData = tx.execute(s -> exporter.payload(bob.getId()).toString());

        assertThat(bobData).contains(bobIds.marker()).doesNotContain(aliceIds.marker(), "Passagem", "Pets");
    }

    @Test
    void telaDevolveOArquivoParaBaixar() throws Exception {
        mvc.perform(post("/ajustes/exportar").param("senha", PASSWORD).param("confirmacao", PASSWORD)
                        .with(user(alice)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("application/json")))
                .andExpect(header().string("Content-Disposition",
                        matchesPattern("attachment; filename=\"financas-backup-\\d{4}-\\d{2}-\\d{2}\\.json\"")))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(content().string(containsString("\"app\":\"financas\"")));
    }

    @Test
    void telaComErroVoltaComAMensagemESemArquivo() throws Exception {
        mvc.perform(post("/ajustes/exportar").param("senha", PASSWORD).param("confirmacao", "outra coisa")
                        .with(user(alice)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Content-Disposition"))
                .andExpect(content().string(containsString("As senhas não são iguais.")));
        mvc.perform(post("/ajustes/exportar").param("senha", PASSWORD).param("confirmacao", PASSWORD)
                        .with(user(alice)))
                .andExpect(status().isForbidden());
    }

    /**
     * Tudo o que a conta tem, sem os ids do banco (que mudam na importação): cada registro vira um
     * texto com os valores e os nomes dos registros ligados a ele.
     */
    private List<String> snapshot(Long userId) {
        return tx.execute(status -> {
            List<String> lines = new ArrayList<>();
            buckets.findByUserIdOrderBySortOrderAscIdAsc(userId).forEach(b -> lines.add(String.join("|", "pote",
                    String.valueOf(b.getSortOrder()), b.getName(), money(b.getPercent()), b.getKind().name(),
                    String.valueOf(b.getDescription()))));
            categories.findByUserIdOrderByIdAsc(userId).forEach(c -> lines.add(String.join("|", "categoria",
                    String.valueOf(c.getSystemKey()), c.getName(), c.getIcon(), c.getBucket().getName(),
                    c.getKind().name(), money(c.getBudget()), c.getGoal() == null ? "-" : c.getGoal().getName(),
                    String.valueOf(c.isArchived()))));
            goals.findByUserIdOrderByIdAsc(userId).forEach(g -> lines.add(String.join("|", "meta",
                    String.valueOf(g.getSystemKey()), g.getName(), g.getIcon(), g.getKind().name(),
                    money(g.getTarget()), String.valueOf(g.getDeadline()), money(g.getInitialBalance()))));
            recurring.findByUserIdOrderByIdAsc(userId).forEach(r -> lines.add(String.join("|", "conta",
                    r.getType().name(), r.getDescription(), money(r.getAmount()),
                    r.getCategory() == null ? "-" : r.getCategory().getName(),
                    r.getGoal() == null ? "-" : r.getGoal().getName(), String.valueOf(r.getDayOfMonth()),
                    String.valueOf(r.getStartMonth()), String.valueOf(r.getEndMonth()), String.valueOf(r.isActive()),
                    r.getSkippedMonths().stream().sorted().map(YearMonth::toString).collect(Collectors.joining(",")))));
            transactions.findAll().stream().filter(t -> t.getUser().getId().equals(userId)).forEach(t -> lines.add(
                    String.join("|", "lancamento", t.getType().name(), String.valueOf(t.getDate()), t.getDescription(),
                            money(t.getAmount()), String.valueOf(t.isPaid()),
                            t.getCategory() == null ? "-" : t.getCategory().getName(),
                            t.getGoal() == null ? "-" : t.getGoal().getName(),
                            t.getRecurring() == null ? "-" : t.getRecurring().getDescription(),
                            String.valueOf(t.getRecurringMonth()))));
            UserSettings s = settings.findById(userId).orElseThrow();
            lines.add(String.join("|", "ajustes", s.getDisplayName(), String.valueOf(s.getPresetId()),
                    String.valueOf(s.getReserveMonths()), s.getReserveBase().name(), money(s.getReserveManualCost()),
                    money(s.getReservePriority()), money(s.getSimInitial()), money(s.getSimMonthly()),
                    money(s.getSimAnnualRate()), String.valueOf(s.getSimYears()), money(s.getSimInflation()),
                    money(s.getSimYearlyIncrease()), money(s.getSimTarget()), String.valueOf(s.isOnboarded())));
            InvestorProfile p = profiles.findById(userId).orElse(null);
            lines.add(p == null ? "perfil|-" : String.join("|", "perfil", p.getProfile().name(),
                    String.valueOf(p.getScore()), String.valueOf(p.getTakenOn()),
                    p.getCustomAllocation().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                            .map(e -> e.getKey() + "=" + money(e.getValue())).collect(Collectors.joining(","))));
            lines.sort(null);
            return lines;
        });
    }

    private static String money(BigDecimal value) {
        return Objects.toString(value == null ? null : value.stripTrailingZeros().toPlainString());
    }
}
