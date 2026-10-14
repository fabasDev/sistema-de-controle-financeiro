package io.github.fabasdev.financas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import io.github.fabasdev.financas.domain.BucketKind;
import io.github.fabasdev.financas.domain.Category;
import io.github.fabasdev.financas.domain.RecurringEntry;
import io.github.fabasdev.financas.domain.Transaction;
import io.github.fabasdev.financas.domain.TransactionType;
import io.github.fabasdev.financas.repository.BucketRepository;
import io.github.fabasdev.financas.repository.CategoryRepository;
import io.github.fabasdev.financas.repository.GoalRepository;
import io.github.fabasdev.financas.repository.RecurringEntryRepository;
import io.github.fabasdev.financas.repository.TransactionRepository;
import io.github.fabasdev.financas.repository.UserSettingsRepository;
import io.github.fabasdev.financas.security.AppUserPrincipal;
import io.github.fabasdev.financas.service.RecurringService;
import io.github.fabasdev.financas.service.TransactionService;
import io.github.fabasdev.financas.service.undo.UndoAction;
import io.github.fabasdev.financas.service.undo.UndoStore;
import io.github.fabasdev.financas.support.SampleData;
import io.github.fabasdev.financas.support.TestAccounts;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/** "Desfazer" de cada tipo de exclusão, uso único e isolamento entre usuários. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UndoTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TestAccounts accounts;
    @Autowired
    SampleData sample;
    @Autowired
    TransactionRepository transactions;
    @Autowired
    RecurringEntryRepository recurring;
    @Autowired
    GoalRepository goals;
    @Autowired
    CategoryRepository categories;
    @Autowired
    BucketRepository buckets;
    @Autowired
    UserSettingsRepository settings;
    @Autowired
    TransactionService transactionService;
    @Autowired
    RecurringService recurringService;
    @Autowired
    TransactionTemplate tx;

    AppUserPrincipal me;
    SampleData.Ids ids;
    MockHttpSession session;

    @BeforeEach
    void setUp() {
        me = accounts.create().principal();
        ids = sample.create(me.getId());
        session = new MockHttpSession();
    }

    /** Apaga pela rota e devolve o token do botão "Desfazer". */
    String delete(String url) throws Exception {
        return (String) mvc.perform(post(url).session(session).with(user(me)).with(csrf()))
                .andExpect(flash().attributeExists(UndoController.UNDO_TOKEN))
                .andReturn().getFlashMap().get(UndoController.UNDO_TOKEN);
    }

    void undo(String token, String expectedRedirect) throws Exception {
        mvc.perform(post("/desfazer").param("token", token).session(session).with(user(me)).with(csrf()))
                .andExpect(redirectedUrl(expectedRedirect))
                .andExpect(flash().attribute("success", "Pronto, desfeito."));
    }

    @Test
    void desfazLancamentoApagado() throws Exception {
        Transaction before = transactions.findById(ids.transaction()).orElseThrow();
        String token = delete("/lancamentos/" + ids.transaction() + "/excluir");
        assertThat(transactions.findById(ids.transaction())).isEmpty();

        undo(token, "/lancamentos?mes=" + before.getMonth());

        tx.executeWithoutResult(s -> {
            List<Transaction> restored = transactions.findAll().stream()
                    .filter(t -> t.getUser().getId().equals(me.getId())
                            && t.getDescription().equals(before.getDescription()))
                    .toList();
            assertThat(restored).singleElement().satisfies(t -> {
                assertThat(t.getAmount()).isEqualByComparingTo(before.getAmount());
                assertThat(t.getCategory().getId()).isEqualTo(ids.category());
                assertThat(t.getDate()).isEqualTo(before.getDate());
            });
        });
    }

    @Test
    void tokenValeUmaVezSo() throws Exception {
        String token = delete("/lancamentos/" + ids.transaction() + "/excluir");
        undo(token, "/lancamentos?mes=" + YearMonth.now());
        long count = transactions.count();

        mvc.perform(post("/desfazer").param("token", token).session(session).with(user(me)).with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attributeExists("error"));
        assertThat(transactions.count()).isEqualTo(count);
    }

    /** Outra pessoa, mesmo com o token, não desfaz nada: o "desfazer" fica na sessão de quem apagou. */
    @Test
    void outroUsuarioNaoUsaOToken() throws Exception {
        String token = delete("/lancamentos/" + ids.transaction() + "/excluir");
        AppUserPrincipal bob = accounts.create().principal();

        mvc.perform(post("/desfazer").param("token", token).session(new MockHttpSession()).with(user(bob)).with(csrf()))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attributeExists("error"));
        // Nem na mesma sessão: o token está ligado ao usuário que apagou.
        mvc.perform(post("/desfazer").param("token", token).session(session).with(user(bob)).with(csrf()))
                .andExpect(redirectedUrl("/"));
        assertThat(transactions.findAll().stream().filter(t -> t.getUser().getId().equals(bob.getId()))
                .noneMatch(t -> t.getDescription().contains(ids.marker()))).isTrue();
    }

    @Test
    void desfazerLancamentoDeContaFixaVoltaOMesParaAConta() throws Exception {
        YearMonth month = YearMonth.now();
        recurringService.ensureMonth(me.getId(), month);
        Long generated = tx.execute(s -> transactions.findByRecurringId(ids.recurring()).getFirst().getId());

        String token = delete("/lancamentos/" + generated + "/excluir");
        assertThat(skipped(ids.recurring())).contains(month);

        undo(token, "/lancamentos?mes=" + month);

        assertThat(skipped(ids.recurring())).doesNotContain(month);
        tx.executeWithoutResult(s -> assertThat(transactions.findByRecurringId(ids.recurring()))
                .singleElement().satisfies(t -> assertThat(t.getRecurringMonth()).isEqualTo(month)));
        // Abrir o mês de novo não duplica o lançamento.
        recurringService.ensureMonth(me.getId(), month);
        tx.executeWithoutResult(s -> assertThat(transactions.findByRecurringId(ids.recurring())).hasSize(1));
    }

    @Test
    void desfazContaFixaComLancamentosFuturosEPassados() throws Exception {
        YearMonth now = YearMonth.now();
        recurringService.ensureMonth(me.getId(), now);
        recurringService.ensureMonth(me.getId(), now.plusMonths(1));
        recurringService.ensureMonth(me.getId(), now.plusMonths(2));
        String token = delete("/contas-fixas/" + ids.recurring() + "/excluir");
        assertThat(recurring.findById(ids.recurring())).isEmpty();

        undo(token, "/contas-fixas");

        tx.executeWithoutResult(s -> {
            RecurringEntry restored = recurring.findByUserIdOrderByIdAsc(me.getId()).stream()
                    .filter(r -> r.getDescription().contains(ids.marker())).findFirst().orElseThrow();
            List<YearMonth> months = transactions.findByRecurringId(restored.getId()).stream()
                    .map(Transaction::getRecurringMonth).sorted().toList();
            assertThat(months).containsExactly(now, now.plusMonths(1), now.plusMonths(2));
            assertThat(restored.getGoal().getId()).isEqualTo(ids.goal());
        });
    }

    @Test
    void desfazMetaERefazAsLigacoes() throws Exception {
        String token = delete("/metas/" + ids.goal() + "/excluir");
        tx.executeWithoutResult(s -> assertThat(recurring.findById(ids.recurring()).orElseThrow().getGoal()).isNull());

        undo(token, "/metas");

        tx.executeWithoutResult(s -> {
            var goal = recurring.findById(ids.recurring()).orElseThrow().getGoal();
            assertThat(goal).isNotNull();
            assertThat(goal.getName()).contains(ids.marker());
        });
    }

    @Test
    void desfazCategoriaApagadaEArquivada() throws Exception {
        // Com lançamentos: arquiva.
        String token = delete("/planejamento/categorias/" + ids.category() + "/excluir");
        assertThat(categories.findById(ids.category()).orElseThrow().isArchived()).isTrue();
        undo(token, "/planejamento");
        assertThat(categories.findById(ids.category()).orElseThrow().isArchived()).isFalse();

        // Sem uso: apaga de verdade, e o desfazer recria.
        Long unused = tx.execute(s -> categories.findByUserIdAndSystemKey(me.getId(), "travel").orElseThrow().getId());
        token = delete("/planejamento/categorias/" + unused + "/excluir");
        assertThat(categories.findById(unused)).isEmpty();
        undo(token, "/planejamento");
        assertThat(categories.findByUserIdAndSystemKey(me.getId(), "travel")).isPresent();
    }

    @Test
    void desfazPoteRemovidoDevolvendoAsCategorias() throws Exception {
        Long wants = tx.execute(s -> buckets.findByUserIdOrderBySortOrderAscIdAsc(me.getId()).get(1).getId());
        String token = delete("/planejamento/potes/" + wants + "/excluir");
        assertThat(categoryKind("leisure")).isEqualTo(BucketKind.NEEDS);

        undo(token, "/planejamento");

        tx.executeWithoutResult(s -> {
            Category leisure = categories.findByUserIdAndSystemKey(me.getId(), "leisure").orElseThrow();
            assertThat(leisure.getBucket().getName()).isEqualTo("Desejos");
            assertThat(leisure.getKind()).isEqualTo(BucketKind.WANTS);
        });
        assertThat(settings.findById(me.getId()).orElseThrow().getPresetId()).isEqualTo("50-30-20");
    }

    @Test
    void desfazTrocaDeRegra() throws Exception {
        String token = (String) mvc.perform(post("/planejamento/regra").param("presetId", "6-potes")
                        .session(session).with(user(me)).with(csrf()))
                .andReturn().getFlashMap().get(UndoController.UNDO_TOKEN);
        assertThat(buckets.countByUserId(me.getId())).isEqualTo(6);

        undo(token, "/planejamento");

        assertThat(buckets.countByUserId(me.getId())).isEqualTo(3);
        tx.executeWithoutResult(s -> {
            assertThat(categories.findByUserIdAndSystemKey(me.getId(), "education").orElseThrow().getBucket()
                    .getName()).isEqualTo("Necessidades");
            assertThat(categories.findByUserIdAndSystemKey(me.getId(), "emergency_reserve").orElseThrow().getBucket()
                    .getName()).isEqualTo("Futuro");
        });
        assertThat(settings.findById(me.getId()).orElseThrow().getPresetId()).isEqualTo("50-30-20");
    }

    @Test
    void desfazerVenceDepoisDoPrazo() {
        MutableClock clock = new MutableClock();
        UndoStore store = new UndoStore(clock);
        String token = store.offer(1L, new UndoAction.CategoryArchived(10L), "/planejamento");
        clock.advance(UndoStore.VALIDITY.plusSeconds(1));
        assertThat(store.take(1L, token)).isEmpty();

        String other = store.offer(1L, new UndoAction.CategoryArchived(10L), "/planejamento");
        assertThat(store.take(2L, other)).isEmpty();
        assertThat(store.take(1L, other)).isPresent();
    }

    @Test
    void semJavascriptAPaginaDeConfirmacaoContinua() throws Exception {
        mvc.perform(get("/lancamentos/" + ids.transaction() + "/excluir").with(user(me)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    }

    private java.util.Set<YearMonth> skipped(Long recurringId) {
        return tx.execute(s -> java.util.Set.copyOf(recurring.findById(recurringId).orElseThrow().getSkippedMonths()));
    }

    private BucketKind categoryKind(String key) {
        return categories.findByUserIdAndSystemKey(me.getId(), key).orElseThrow().getKind();
    }

    static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-13T12:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
