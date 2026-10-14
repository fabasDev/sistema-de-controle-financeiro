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

/** Barra de navegação do celular: a tela atual fica marcada, inclusive as que estão dentro de "Mais". */
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

    /** O trecho da barra de baixo no HTML da tela. */
    private String tabBar(String path) throws Exception {
        String html = mvc.perform(get(path).with(user(me))).andReturn().getResponse().getContentAsString();
        int start = html.indexOf("<nav class=\"tab-bar\"");
        assertThat(start).as("barra em %s", path).isNotNegative();
        return html.substring(start, html.indexOf("</nav>", start));
    }
}
