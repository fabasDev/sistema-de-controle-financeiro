package io.github.fabasdev.financas.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.fabasdev.financas.domain.RiskProfile;
import io.github.fabasdev.financas.security.AppUserPrincipal;
import io.github.fabasdev.financas.service.ProfileService;
import io.github.fabasdev.financas.support.SampleData;
import io.github.fabasdev.financas.support.TestAccounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Abre cada tela logado, com dados, para pegar erro de template ou de consulta. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PagesRenderTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TestAccounts accounts;
    @Autowired
    SampleData sample;
    @Autowired
    ProfileService profiles;

    AppUserPrincipal me;
    SampleData.Ids ids;

    @BeforeEach
    void setUp() {
        me = accounts.create().principal();
        ids = sample.create(me.getId());
    }

    @Test
    void todasAsTelasAbrem() throws Exception {
        String[] pages = {
                "/", "/?mes=2026-01", "/?mes=lixo",
                "/lancamentos", "/lancamentos?tipo=EXPENSE&busca=gasto&page=0&size=500", "/lancamentos/novo",
                "/lancamentos/" + ids.transaction() + "/editar", "/lancamentos/" + ids.transaction() + "/excluir",
                "/contas-fixas", "/contas-fixas/nova", "/contas-fixas/" + ids.recurring() + "/editar",
                "/contas-fixas/" + ids.recurring() + "/excluir",
                "/planejamento", "/planejamento/potes/" + ids.bucket() + "/editar",
                "/planejamento/potes/" + ids.bucket() + "/excluir", "/planejamento/categorias/nova",
                "/planejamento/categorias/" + ids.category() + "/editar",
                "/planejamento/categorias/" + ids.category() + "/excluir",
                "/metas", "/metas/nova", "/metas/" + ids.goal() + "/editar", "/metas/" + ids.goal() + "/excluir",
                "/investimentos", "/simulador", "/simulador?inicial=1000&mensal=100&taxa=10,5&anos=2&meta=5000",
                "/simulador?taxa=abc", "/historico", "/historico?meses=24", "/ajustes", "/privacidade"};
        for (String page : pages) {
            mvc.perform(get(page).with(user(me))).andExpect(status().isOk());
        }
    }

    @Test
    void investimentosComPerfilMostraACarteira() throws Exception {
        profiles.saveResult(me.getId(), RiskProfile.MODERATE, 15);
        mvc.perform(get("/investimentos").with(user(me)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Carteira sugerida")));
    }

    @Test
    void lancamentoNovoComValorEmFormatoBrasileiro() throws Exception {
        mvc.perform(post("/lancamentos").with(user(me)).with(csrf())
                        .param("type", "EXPENSE").param("date", "2026-10-13").param("amount", "1.234,56")
                        .param("categoryId", ids.category().toString()).param("paid", "true"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/lancamentos?mes=2026-10"));
        mvc.perform(get("/lancamentos?mes=2026-10").with(user(me)))
                .andExpect(content().string(containsString("1.234,56")));
    }

    @Test
    void formularioComErroVoltaComAMensagemAoLadoDoCampo() throws Exception {
        mvc.perform(post("/lancamentos").with(user(me)).with(csrf())
                        .param("type", "EXPENSE").param("date", "2026-10-13").param("amount", "-5")
                        .param("categoryId", ids.category().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Informe um valor maior que zero.")));
    }

    @Test
    void apiDoSimuladorResponde() throws Exception {
        mvc.perform(get("/api/simulacao").with(user(me))
                        .param("initial", "1000").param("monthly", "0").param("annualRate", "10").param("years", "2")
                        .param("inflation", "0").param("yearlyIncrease", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()").value(25))
                .andExpect(jsonPath("$.points[24].total").value(1210.00));
        mvc.perform(get("/api/simulacao").with(user(me))
                        .param("initial", "-1").param("monthly", "0").param("annualRate", "10").param("years", "2")
                        .param("inflation", "0").param("yearlyIncrease", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void apiDoAssistenteResponde() throws Exception {
        mvc.perform(get("/api/assistente").with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendation.futurePlanned").exists());
    }
}
