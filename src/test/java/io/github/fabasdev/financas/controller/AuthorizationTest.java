package io.github.fabasdev.financas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.fabasdev.financas.domain.Bucket;
import io.github.fabasdev.financas.domain.Category;
import io.github.fabasdev.financas.domain.Goal;
import io.github.fabasdev.financas.domain.RecurringEntry;
import io.github.fabasdev.financas.domain.Transaction;
import io.github.fabasdev.financas.repository.BucketRepository;
import io.github.fabasdev.financas.repository.CategoryRepository;
import io.github.fabasdev.financas.repository.GoalRepository;
import io.github.fabasdev.financas.repository.RecurringEntryRepository;
import io.github.fabasdev.financas.repository.TransactionRepository;
import io.github.fabasdev.financas.security.AppUserPrincipal;
import io.github.fabasdev.financas.support.SampleData;
import io.github.fabasdev.financas.support.TestAccounts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Regra do CLAUDE.md: cada usuário só acessa os próprios registros. Aqui o usuário B tenta
 * ler, editar e apagar cada registro do usuário A pelo id. Toda tentativa precisa dar 404
 * (a mesma resposta de um id que não existe) e deixar os dados de A intactos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthorizationTest {

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
    BucketRepository buckets;
    @Autowired
    CategoryRepository categories;
    @Autowired
    RequestMappingHandlerMapping mappings;

    AppUserPrincipal alice;
    AppUserPrincipal bob;
    SampleData.Ids a;
    SampleData.Ids b;

    @BeforeEach
    void setUp() {
        alice = accounts.create().principal();
        bob = accounts.create().principal();
        a = sample.create(alice.getId());
        b = sample.create(bob.getId());
    }

    /** Todas as rotas com id, nos métodos GET e POST, com dados de formulário válidos. */
    List<MockHttpServletRequestBuilder> routesWithIdsOf(SampleData.Ids ids, SampleData.Ids validRefs) {
        String cat = validRefs.category().toString();
        String bucket = validRefs.bucket().toString();
        return List.of(
                get("/lancamentos/{id}/editar", ids.transaction()),
                get("/lancamentos/{id}/excluir", ids.transaction()),
                post("/lancamentos/{id}", ids.transaction()).param("type", "EXPENSE").param("date", "2026-10-01")
                        .param("amount", "1,00").param("categoryId", cat).param("description", "invasor"),
                post("/lancamentos/{id}/pago", ids.transaction()),
                post("/lancamentos/{id}/excluir", ids.transaction()),

                get("/contas-fixas/{id}/editar", ids.recurring()),
                get("/contas-fixas/{id}/excluir", ids.recurring()),
                post("/contas-fixas/{id}", ids.recurring()).param("type", "EXPENSE").param("description", "invasor")
                        .param("amount", "1,00").param("categoryId", cat).param("dayOfMonth", "1")
                        .param("startMonth", "2026-10").param("active", "true"),
                post("/contas-fixas/{id}/excluir", ids.recurring()),

                get("/metas/{id}/editar", ids.goal()),
                get("/metas/{id}/excluir", ids.goal()),
                post("/metas/{id}", ids.goal()).param("name", "invasor").param("icon", "Target").param("kind", "GOAL"),
                post("/metas/{id}/excluir", ids.goal()),

                get("/planejamento/potes/{id}/editar", ids.bucket()),
                get("/planejamento/potes/{id}/excluir", ids.bucket()),
                post("/planejamento/potes/{id}", ids.bucket()).param("name", "invasor").param("percent", "10")
                        .param("kind", "NEEDS"),
                post("/planejamento/potes/{id}/excluir", ids.bucket()),

                get("/planejamento/categorias/{id}/editar", ids.category()),
                get("/planejamento/categorias/{id}/excluir", ids.category()),
                post("/planejamento/categorias/{id}", ids.category()).param("name", "invasor").param("icon", "Tag")
                        .param("bucketId", bucket),
                post("/planejamento/categorias/{id}/excluir", ids.category()),
                post("/planejamento/categorias/{id}/reativar", ids.category()));
    }

    @TestFactory
    Stream<DynamicTest> bobNaoAcessaNadaDaAlicePeloId() {
        return routesWithIdsOf(a, b).stream().map(request -> {
            var built = request.buildRequest(new org.springframework.mock.web.MockServletContext());
            String name = built.getMethod() + " " + built.getRequestURI();
            return DynamicTest.dynamicTest(name, () -> {
                mvc.perform(request.with(user(bob)).with(csrf()))
                        .andExpect(status().isNotFound())
                        .andExpect(content().string(not(containsString(a.marker()))));
                assertAliceUntouched();
            });
        });
    }

    /** Controle: as mesmas rotas funcionam para o dono (a página ou o redirect esperado). */
    @TestFactory
    Stream<DynamicTest> oDonoConsegueUsarAsMesmasRotas() {
        AppUserPrincipal owner = accounts.create().principal();
        SampleData.Ids own = sample.create(owner.getId());
        return routesWithIdsOf(own, own).stream()
                // Apagar e mudar o pote primeiro estragaria as próximas rotas: o controle cobre o resto.
                .filter(r -> !r.buildRequest(new org.springframework.mock.web.MockServletContext()).getRequestURI()
                        .endsWith("/excluir") || r.buildRequest(new org.springframework.mock.web.MockServletContext())
                        .getMethod().equals("GET"))
                .map(request -> DynamicTest.dynamicTest(
                        request.buildRequest(new org.springframework.mock.web.MockServletContext()).getRequestURI(),
                        () -> {
                            int status = mvc.perform(request.with(user(owner)).with(csrf())).andReturn().getResponse()
                                    .getStatus();
                            assertThat(status).isIn(200, 302);
                        }));
    }

    @Test
    void bobNaoLigaOProprioLancamentoACategoriaOuMetaDaAlice() throws Exception {
        long before = transactions.count();
        mvc.perform(post("/lancamentos").with(user(bob)).with(csrf())
                        .param("type", "EXPENSE").param("date", "2026-10-01").param("amount", "10,00")
                        .param("categoryId", a.category().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Escolha uma categoria da lista.")));
        mvc.perform(post("/lancamentos").with(user(bob)).with(csrf())
                        .param("type", "EXPENSE").param("date", "2026-10-01").param("amount", "10,00")
                        .param("categoryId", b.category().toString()).param("goalId", a.goal().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Escolha uma meta da lista.")));
        assertThat(transactions.count()).isEqualTo(before);
    }

    @Test
    void bobNaoUsaPoteOuMetaDaAliceEmCategoriaEContaFixa() throws Exception {
        mvc.perform(post("/planejamento/categorias").with(user(bob)).with(csrf())
                        .param("name", "x").param("icon", "Tag").param("bucketId", a.bucket().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Escolha um pote da lista.")));
        mvc.perform(post("/contas-fixas").with(user(bob)).with(csrf())
                        .param("type", "EXPENSE").param("description", "x").param("amount", "1,00")
                        .param("categoryId", b.category().toString()).param("goalId", a.goal().toString())
                        .param("dayOfMonth", "1").param("startMonth", "2026-10").param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Escolha uma meta da lista.")));
    }

    @Test
    void listagensDoBobNaoMostramNadaDaAlice() throws Exception {
        for (String page : List.of("/", "/lancamentos", "/lancamentos?busca=segredo", "/contas-fixas",
                "/planejamento", "/metas", "/historico?meses=24", "/investimentos", "/api/assistente",
                "/lancamentos/novo", "/contas-fixas/nova", "/planejamento/categorias/nova")) {
            mvc.perform(get(page).with(user(bob)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString(a.marker()))));
        }
    }

    /**
     * Toda rota do app, menos as públicas, exige login. A lista sai do próprio Spring,
     * então uma rota nova entra no teste sozinha.
     */
    @TestFactory
    Stream<DynamicTest> todaRotaPrivadaExigeLogin() {
        Set<String> publicPaths = Set.of("/login", "/cadastro", "/cadastro/codigo", "/recuperar",
                "/recuperar/codigo", "/privacidade", "/error");
        List<DynamicTest> tests = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : mappings.getHandlerMethods().entrySet()) {
            for (String pattern : e.getKey().getPatternValues()) {
                if (publicPaths.contains(pattern)) {
                    continue;
                }
                String url = pattern.replaceAll("\\{[^}]+}", "1");
                Set<RequestMethod> methods = e.getKey().getMethodsCondition().getMethods();
                for (RequestMethod method : methods.isEmpty() ? Set.of(RequestMethod.GET) : methods) {
                    tests.add(DynamicTest.dynamicTest(method + " " + url, () -> {
                        MockHttpServletRequestBuilder request = method == RequestMethod.POST
                                ? post(url).with(csrf()) : get(url);
                        mvc.perform(request).andExpect(status().isFound()).andExpect(redirectedUrl("/login"));
                    }));
                }
            }
        }
        assertThat(tests).hasSizeGreaterThan(30);
        return tests.stream();
    }

    private void assertAliceUntouched() {
        Transaction t = transactions.findById(a.transaction()).orElseThrow();
        assertThat(t.getDescription()).contains(a.marker());
        assertThat(t.isPaid()).isTrue();
        RecurringEntry r = recurring.findById(a.recurring()).orElseThrow();
        assertThat(r.getDescription()).contains(a.marker());
        Goal g = goals.findById(a.goal()).orElseThrow();
        assertThat(g.getName()).contains(a.marker());
        Bucket bucket = buckets.findById(a.bucket()).orElseThrow();
        assertThat(bucket.getName()).isNotEqualTo("invasor");
        Category c = categories.findById(a.category()).orElseThrow();
        assertThat(c.getName()).contains(a.marker());
        assertThat(c.isArchived()).isFalse();
    }
}
