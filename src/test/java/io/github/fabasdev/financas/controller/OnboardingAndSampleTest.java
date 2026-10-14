package io.github.fabasdev.financas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.fabasdev.financas.domain.Goal;
import io.github.fabasdev.financas.domain.RecurringEntry;
import io.github.fabasdev.financas.domain.TransactionType;
import io.github.fabasdev.financas.repository.BucketRepository;
import io.github.fabasdev.financas.repository.GoalRepository;
import io.github.fabasdev.financas.repository.RecurringEntryRepository;
import io.github.fabasdev.financas.repository.UserSettingsRepository;
import io.github.fabasdev.financas.security.AppUserPrincipal;
import io.github.fabasdev.financas.service.MonthSummaryService;
import io.github.fabasdev.financas.support.TestAccounts;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OnboardingAndSampleTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TestAccounts accounts;
    @Autowired
    UserSettingsRepository settings;
    @Autowired
    RecurringEntryRepository recurring;
    @Autowired
    GoalRepository goals;
    @Autowired
    BucketRepository buckets;
    @Autowired
    MonthSummaryService summaries;
    @Autowired
    TransactionTemplate tx;

    AppUserPrincipal me;

    @BeforeEach
    void setUp() {
        me = accounts.create().principal();
    }

    @Test
    void contaNovaVeOConviteParaConfigurar() throws Exception {
        mvc.perform(get("/").with(user(me))).andExpect(content().string(containsString("Configurar em 1 minuto")));
        mvc.perform(post("/comecar/pular").with(user(me)).with(csrf())).andExpect(redirectedUrl("/"));
        mvc.perform(get("/").with(user(me)))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Configurar em 1 minuto"))));
    }

    @Test
    void assistenteInicialCriaRendaContasEReserva() throws Exception {
        mvc.perform(post("/comecar").with(user(me)).with(csrf())
                        .param("displayName", "Fabrício").param("presetId", "70-20-10")
                        .param("income", "5.000,00").param("incomeDay", "5")
                        .param("conta", "1.500", "", "", "", "99,90", "", "", "")
                        .param("reserveBalance", "2.000").param("reserveMonths", "12"))
                .andExpect(redirectedUrl("/"));

        var s = settings.findById(me.getId()).orElseThrow();
        assertThat(s.isOnboarded()).isTrue();
        assertThat(s.getDisplayName()).isEqualTo("Fabrício");
        assertThat(s.getReserveMonths()).isEqualTo(12);
        assertThat(s.getPresetId()).isEqualTo("70-20-10");
        assertThat(buckets.countByUserId(me.getId())).isEqualTo(3);
        tx.executeWithoutResult(t -> {
            List<RecurringEntry> entries = recurring.findByUserIdOrderByIdAsc(me.getId());
            assertThat(entries).extracting(RecurringEntry::getDescription)
                    .containsExactly("Salário", "Aluguel / financiamento", "Internet");
            assertThat(entries.getFirst().getType()).isEqualTo(TransactionType.INCOME);
            assertThat(entries.get(1).getCategory().getSystemKey()).isEqualTo("housing");
            assertThat(entries.get(2).getAmount()).isEqualByComparingTo("99.90");
        });
        assertThat(goals.findByUserIdAndSystemKey(me.getId(), Goal.RESERVE_KEY).orElseThrow().getInitialBalance())
                .isEqualByComparingTo("2000");
        // O mês já começa com a renda das contas fixas como base do plano.
        mvc.perform(get("/").with(user(me))).andExpect(status().isOk());
        assertThat(summaries.summarize(me.getId(), YearMonth.now()).planIncome()).isEqualByComparingTo("5000");
    }

    @Test
    void contaComValorInvalidoVoltaComAMensagem() throws Exception {
        mvc.perform(post("/comecar").with(user(me)).with(csrf())
                        .param("presetId", "50-30-20").param("incomeDay", "5").param("reserveMonths", "6")
                        .param("conta", "abc", "", "", "", "", "", "", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Aluguel / financiamento")))
                .andExpect(content().string(containsString("inválido")));
        assertThat(settings.findById(me.getId()).orElseThrow().isOnboarded()).isFalse();
    }

    @Test
    void dadosDeExemploPedemConfirmacaoESubstituemOsDados() throws Exception {
        mvc.perform(post("/ajustes/exemplo").with(user(me)).with(csrf())).andExpect(redirectedUrl("/ajustes"));
        assertThat(summaries.summarize(me.getId(), YearMonth.now()).hasData()).isFalse();

        mvc.perform(post("/ajustes/exemplo").param("confirmo", "sim").with(user(me)).with(csrf()))
                .andExpect(redirectedUrl("/"));

        assertThat(settings.findById(me.getId()).orElseThrow().getDisplayName()).isEqualTo("Visitante");
        assertThat(summaries.summarize(me.getId(), YearMonth.now().minusMonths(1)).hasData()).isTrue();
        assertThat(summaries.summarize(me.getId(), YearMonth.now().minusMonths(1)).income())
                .isGreaterThanOrEqualTo(new java.math.BigDecimal("6500"));
        assertThat(goals.findByUserIdOrderByIdAsc(me.getId())).hasSize(4);
        tx.executeWithoutResult(t -> assertThat(recurring.findByUserIdOrderByIdAsc(me.getId())).hasSize(10));
    }
}
