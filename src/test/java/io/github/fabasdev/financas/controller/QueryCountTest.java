package io.github.fabasdev.financas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.fabasdev.financas.domain.Bucket;
import io.github.fabasdev.financas.domain.Category;
import io.github.fabasdev.financas.domain.GoalKind;
import io.github.fabasdev.financas.domain.TransactionType;
import io.github.fabasdev.financas.repository.BucketRepository;
import io.github.fabasdev.financas.security.AppUserPrincipal;
import io.github.fabasdev.financas.service.CategoryService;
import io.github.fabasdev.financas.service.GoalService;
import io.github.fabasdev.financas.service.RecurringService;
import io.github.fabasdev.financas.service.TransactionService;
import io.github.fabasdev.financas.support.TestAccounts;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Detector de N+1: abre cada tela com 25 e com 75 registros e compara o número de queries.
 * Se uma tela fizer uma query por registro, o número cresce e o teste falha.
 * Os dois tamanhos passam de uma página (20): com menos que isso, o Spring Data pula a query
 * de contagem, e a diferença não seria N+1.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QueryCountTest {

    static final List<String> PAGES = List.of("/", "/lancamentos", "/lancamentos/novo", "/contas-fixas",
            "/planejamento", "/metas", "/historico?meses=12", "/investimentos", "/api/assistente", "/comecar", "/simulador",
            "/ajustes");

    @Autowired
    MockMvc mvc;
    @Autowired
    TestAccounts accounts;
    @Autowired
    TransactionService transactions;
    @Autowired
    RecurringService recurring;
    @Autowired
    CategoryService categories;
    @Autowired
    GoalService goals;
    @Autowired
    BucketRepository buckets;
    @Autowired
    EntityManagerFactory emf;

    @Test
    void numeroDeQueriesNaoCresceComONumeroDeRegistros() throws Exception {
        Map<String, Long> small = queriesPerPage(25);
        Map<String, Long> large = queriesPerPage(75);
        for (String page : PAGES) {
            assertThat(large.get(page)).as("queries em %s (25 registros: %s, 75 registros: %s)", page,
                    small.get(page), large.get(page)).isEqualTo(small.get(page));
        }
    }

    @Test
    void paginaNuncaPassaDe100Itens() throws Exception {
        AppUserPrincipal me = accounts.create().principal();
        var result = mvc.perform(get("/lancamentos?size=500").with(user(me))).andExpect(status().isOk()).andReturn();
        Page<?> page = (Page<?>) result.getModelAndView().getModel().get("page");
        assertThat(page.getSize()).isEqualTo(100);

        var defaultResult = mvc.perform(get("/lancamentos").with(user(me))).andReturn();
        assertThat(((Page<?>) defaultResult.getModelAndView().getModel().get("page")).getSize()).isEqualTo(20);
    }

    private Map<String, Long> queriesPerPage(int n) throws Exception {
        AppUserPrincipal me = accounts.create().principal();
        Long userId = me.getId();
        Bucket bucket = buckets.findByUserIdOrderBySortOrderAscIdAsc(userId).getFirst();
        LocalDate today = LocalDate.now();
        for (int i = 0; i < n; i++) {
            Category c = categories.create(userId,
                    new CategoryService.CategoryData("Categoria " + i, "Tag", bucket.getId(), null, null));
            var goal = goals.create(userId, new GoalService.GoalData("Meta " + i, "Target", GoalKind.GOAL,
                    new BigDecimal("1000"), YearMonth.now().plusMonths(12), BigDecimal.ZERO));
            transactions.create(userId, new TransactionService.TransactionData(TransactionType.EXPENSE,
                    today.minusMonths(i % 6), "Gasto " + i, new BigDecimal("10.00"), c.getId(), goal.getId(), true,
                    false, null));
            recurring.create(userId, new RecurringService.RecurringData(TransactionType.EXPENSE, "Conta " + i,
                    new BigDecimal("5.00"), c.getId(), null, 5, YearMonth.now().minusMonths(1), null, true));
        }
        // Primeira visita gera os lançamentos das contas fixas do mês; a medição é na segunda.
        for (String page : PAGES) {
            mvc.perform(get(page).with(user(me))).andExpect(status().isOk());
        }
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String page : PAGES) {
            stats.clear();
            mvc.perform(get(page).with(user(me))).andExpect(status().isOk());
            counts.put(page, stats.getPrepareStatementCount());
        }
        System.out.println("Queries por tela com " + n + " registros: " + counts);
        return counts;
    }
}
