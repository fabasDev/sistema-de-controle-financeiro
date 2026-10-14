package io.github.fabasdev.financas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import io.github.fabasdev.financas.repository.TransactionRepository;
import io.github.fabasdev.financas.security.AppUserPrincipal;
import io.github.fabasdev.financas.support.SampleData;
import io.github.fabasdev.financas.support.TestAccounts;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Lista de lançamentos: a linha abre a edição, apagar fica dentro dela e os filtros ficam recolhidos. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionListTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TestAccounts accounts;
    @Autowired
    SampleData sample;
    @Autowired
    TransactionRepository transactions;

    AppUserPrincipal me;
    Long id;
    YearMonth month;

    @BeforeEach
    void setUp() {
        me = accounts.create().principal();
        id = sample.create(me.getId()).transaction();
        month = transactions.findById(id).orElseThrow().getMonth();
    }

    @Test
    void cadaLinhaLevaParaAEdicaoESemApagarNaLista() throws Exception {
        String html = page("/lancamentos?mes=" + month);
        assertThat(html).contains("<a class=\"row-link\" href=\"/lancamentos/" + id + "/editar\"><span class=\"visually-hidden\">Editar </span>")
                .doesNotContain("data-delete")
                .doesNotContain("/excluir");
    }

    @Test
    void apagarFicaNaEdicao() throws Exception {
        assertThat(page("/lancamentos/" + id + "/editar"))
                .contains("<a data-delete class=\"link-standalone\" href=\"/lancamentos/" + id + "/excluir\">")
                .contains("Apagar este lançamento");
        assertThat(page("/lancamentos/novo")).doesNotContain("data-delete");
    }

    @Test
    void filtrosFicamRecolhidosAteSeremUsados() throws Exception {
        assertThat(page("/lancamentos?mes=" + month)).contains("<details class=\"filters-box\">")
                .doesNotContain("Limpar filtros");
        assertThat(page("/lancamentos?mes=" + month + "&situacao=pendente")).contains("<details class=\"filters-box\" open=\"open\">")
                .contains("Filtros em uso")
                .contains("Limpar filtros");
        assertThat(page("/lancamentos?mes=" + month + "&busca=mercado")).contains("<details class=\"filters-box\" open=\"open\">");
    }

    private String page(String path) throws Exception {
        return mvc.perform(get(path).with(user(me))).andReturn().getResponse().getContentAsString();
    }
}
