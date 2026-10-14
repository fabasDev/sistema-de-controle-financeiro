package io.github.fabasdev.financas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import io.github.fabasdev.financas.security.AppUserPrincipal;
import io.github.fabasdev.financas.support.TestAccounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Navegação: na barra do celular a tela atual fica marcada, inclusive as que estão dentro de "Mais";
 * no menu lateral da tela larga, as telas aparecem em grupos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NavigationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TestAccounts accounts;

    AppUserPrincipal me;

    @BeforeEach
    void setUp() {
        me = accounts.create().principal();
    }

    @Test
    void telaDaBarraFicaMarcada() throws Exception {
        String bar = tabBar("/lancamentos");
        assertThat(bar).contains("<a href=\"/lancamentos\" aria-current=\"page\">")
                .doesNotContain("is-current");
        assertThat(tabBar("/metas/nova")).contains("<a href=\"/metas\" aria-current=\"true\">");
        assertThat(tabBar("/")).contains("<a href=\"/\" aria-current=\"page\">");
    }

    @Test
    void telaDentroDoMaisMarcaOMais() throws Exception {
        for (String path : new String[] {"/planejamento", "/contas-fixas/nova", "/ajustes/exportar", "/historico"}) {
            assertThat(tabBar(path)).as(path).contains("<summary class=\"is-current\">");
        }
    }

    /** Menu lateral da tela larga: as nove telas em três grupos com nome, cada uma com ícone. */
    @Test
    void menuLateralTemAsTelasEmGrupos() throws Exception {
        String menu = section("/historico", "<nav class=\"nav-wide\"");
        assertThat(menu).contains("<p class=\"nav-group\" id=\"menu-mes\">O mês</p>",
                "<ul class=\"nav-list\" aria-labelledby=\"menu-mes\">",
                "<p class=\"nav-group\" id=\"menu-planejar\">Planejar</p>",
                "<p class=\"nav-group\" id=\"menu-investir\">Investir</p>");
        for (String path : new String[] {"/", "/lancamentos", "/contas-fixas", "/planejamento", "/metas",
                "/historico", "/investimentos", "/simulador", "/ajustes"}) {
            assertThat(menu).as(path).contains("<a href=\"" + path + "\"");
        }
        assertThat(menu).contains("<a href=\"/historico\" aria-current=\"page\"><svg class=\"icon\"");
        // Um ícone por tela, decorativo: o nome da tela vem escrito ao lado.
        assertThat(menu.split("<svg class=\"icon\" aria-hidden=\"true\"", -1)).hasSize(10);
        assertThat(menu).contains("<span>Histórico</span>");
    }

    /** O trecho da barra de baixo no HTML da tela. */
    private String tabBar(String path) throws Exception {
        return section(path, "<nav class=\"tab-bar\"");
    }

    /** O trecho de um <nav> no HTML da tela, do começo indicado até o primeiro </nav>. */
    private String section(String path, String startTag) throws Exception {
        String html = mvc.perform(get(path).with(user(me))).andReturn().getResponse().getContentAsString();
        int start = html.indexOf(startTag);
        assertThat(start).as("%s em %s", startTag, path).isNotNegative();
        return html.substring(start, html.indexOf("</nav>", start));
    }
}
