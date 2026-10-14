package io.github.fabasdev.financas.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.fabasdev.financas.domain.RiskProfile;
import io.github.fabasdev.financas.domain.TransactionType;
import io.github.fabasdev.financas.security.AppUserPrincipal;
import io.github.fabasdev.financas.service.CategoryService;
import io.github.fabasdev.financas.service.ProfileService;
import io.github.fabasdev.financas.service.TransactionService;
import io.github.fabasdev.financas.support.SampleData;
import io.github.fabasdev.financas.support.TestAccounts;
import java.math.BigDecimal;
import java.time.LocalDate;
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
    @Autowired
    io.github.fabasdev.financas.service.SettingsService settings;
    @Autowired
    CategoryService categories;
    @Autowired
    TransactionService transactions;

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
                "/lancamentos?situacao=pendente", "/lancamentos?situacao=pago", "/lancamentos?situacao=lixo",
                "/lancamentos/" + ids.transaction() + "/editar", "/lancamentos/" + ids.transaction() + "/excluir",
                "/contas-fixas", "/contas-fixas/nova", "/contas-fixas/" + ids.recurring() + "/editar",
                "/contas-fixas/" + ids.recurring() + "/excluir",
                "/planejamento", "/planejamento/potes/" + ids.bucket() + "/editar",
                "/planejamento/potes/" + ids.bucket() + "/excluir", "/planejamento/categorias/nova",
                "/planejamento/categorias/" + ids.category() + "/editar",
                "/planejamento/categorias/" + ids.category() + "/excluir",
                "/metas", "/metas/nova", "/metas/" + ids.goal() + "/editar", "/metas/" + ids.goal() + "/excluir",
                "/investimentos", "/simulador", "/simulador?inicial=1000&mensal=100&taxa=10,5&anos=2&meta=5000",
                "/simulador?taxa=abc", "/historico", "/historico?meses=24", "/ajustes", "/privacidade", "/comecar",
                "/ajustes/importar", "/ajustes/exportar"};
        for (String page : pages) {
            mvc.perform(get(page).with(user(me))).andExpect(status().isOk());
        }
    }

    /** O texto de ajuda das telas de senha acompanha o mínimo do PasswordPolicy. */
    @Test
    void ajudaDaSenhaMostraOMinimoAtual() throws Exception {
        String hint = "Pelo menos " + io.github.fabasdev.financas.security.PasswordPolicy.MIN_LENGTH + " caracteres.";
        mvc.perform(get("/cadastro")).andExpect(content().string(containsString(hint)));
        mvc.perform(get("/recuperar")).andExpect(content().string(containsString(hint)));
        mvc.perform(get("/ajustes").with(user(me))).andExpect(content().string(containsString(hint)));
        mvc.perform(get("/ajustes/exportar").with(user(me))).andExpect(content().string(containsString(hint)));
    }

    @Test
    void investimentosComPerfilMostraACarteira() throws Exception {
        profiles.saveResult(me.getId(), RiskProfile.MODERATE, 15);
        mvc.perform(get("/investimentos").with(user(me)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Carteira sugerida")));
    }

    /**
     * Planejado × realizado: uma barra por pote. Sem renda no mês nada foi planejado: o pote com gasto
     * fica com a barra cheia e "acima do plano", os outros vazios, e não há porcentagem.
     */
    @Test
    void visaoGeralMostraUmaBarraPorPote() throws Exception {
        mvc.perform(get("/").with(user(me)))
                .andExpect(content().string(containsString("<ul class=\"plan-list\">")))
                .andExpect(content().string(containsString("<li class=\"is-over\">")))
                .andExpect(content().string(containsString("<rect class=\"plan-bar-fill\" height=\"4\" width=\"100\">")))
                .andExpect(content().string(containsString("<rect class=\"plan-bar-fill\" height=\"4\" width=\"0\">")))
                .andExpect(content().string(not(containsString("% do planejado"))));
    }

    /**
     * Visão geral em duas colunas (registrar um gasto vem antes das pendências no HTML, para o
     * celular). A coluna "Limite" dos gastos por categoria só aparece se alguma categoria tiver limite.
     */
    @Test
    void visaoGeralMostraLimiteSoQuandoHa() throws Exception {
        String html = mvc.perform(get("/").with(user(me))).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(html.indexOf("<div class=\"home-side\">"))
                .isPositive().isLessThan(html.indexOf("<div class=\"home-main\">"));
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain(">Limite</th>");

        Long limited = categories.create(me.getId(), new CategoryService.CategoryData("Lazer", "Tag", ids.bucket(),
                new BigDecimal("50"), null)).getId();
        transactions.create(me.getId(), new TransactionService.TransactionData(TransactionType.EXPENSE,
                LocalDate.now(), "Cinema", new BigDecimal("80"), limited, null, true, false, null));
        mvc.perform(get("/").with(user(me)))
                .andExpect(content().string(containsString(">Limite</th>")))
                .andExpect(content().string(containsString("passou do limite")));
    }

    @Test
    void menuMarcaAPaginaAtual() throws Exception {
        // Na própria página: aria-current="page"; numa tela dentro da seção: "true"; nas outras, nada.
        mvc.perform(get("/metas").with(user(me)))
                .andExpect(content().string(containsString("href=\"/metas\" aria-current=\"page\"")))
                .andExpect(content().string(not(containsString("href=\"/\" aria-current"))));
        mvc.perform(get("/metas/nova").with(user(me)))
                .andExpect(content().string(containsString("href=\"/metas\" aria-current=\"true\"")));
        mvc.perform(get("/").with(user(me)))
                .andExpect(content().string(containsString("href=\"/\" aria-current=\"page\"")))
                .andExpect(content().string(not(containsString("href=\"/metas\" aria-current"))));
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
    void lancamentoRapidoSalvaEVoltaParaATelaDeOrigem() throws Exception {
        mvc.perform(post("/lancamentos/rapido").with(user(me)).with(csrf())
                        .param("type", "EXPENSE").param("paid", "true").param("voltar", "inicio")
                        .param("date", "2026-10-13").param("amount", "42,50").param("categoryId", ids.category().toString()))
                .andExpect(redirectedUrl("/?mes=2026-10"));
        // Qualquer outro valor de "voltar" volta para a lista (não é um redirect aberto).
        mvc.perform(post("/lancamentos/rapido").with(user(me)).with(csrf())
                        .param("type", "EXPENSE").param("paid", "true").param("voltar", "https://exemplo.com")
                        .param("date", "2026-10-13").param("amount", "1,00").param("categoryId", ids.category().toString()))
                .andExpect(redirectedUrl("/lancamentos?mes=2026-10"));
        // A categoria usada aparece no grupo "Mais usadas".
        mvc.perform(get("/").with(user(me)))
                .andExpect(content().string(containsString("label=\"Mais usadas\"")))
                .andExpect(content().string(containsString("Registrar um gasto")));
    }

    @Test
    void lancamentoRapidoComErroAbreOFormularioCompleto() throws Exception {
        mvc.perform(post("/lancamentos/rapido").with(user(me)).with(csrf())
                        .param("type", "EXPENSE").param("paid", "true").param("voltar", "inicio")
                        .param("date", "2026-10-13").param("amount", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Informe um valor.")));
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
    void salvarOsValoresDoSimulador() throws Exception {
        mvc.perform(get("/simulador?inicial=2500&mensal=150&taxa=9,5&anos=7").with(user(me)))
                .andExpect(content().string(containsString("action=\"/simulador/salvar\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        mvc.perform(post("/simulador/salvar").with(user(me)).with(csrf())
                        .param("inicial", "2500").param("mensal", "150").param("taxa", "9,5").param("anos", "7"))
                .andExpect(redirectedUrl("/simulador"));
        var s = settings.get(me.getId());
        org.assertj.core.api.Assertions.assertThat(s.getSimInitial()).isEqualByComparingTo("2500");
        org.assertj.core.api.Assertions.assertThat(s.getSimAnnualRate()).isEqualByComparingTo("9.5");
        org.assertj.core.api.Assertions.assertThat(s.getSimYears()).isEqualTo(7);
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
