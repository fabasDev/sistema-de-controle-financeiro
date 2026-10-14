package io.github.fabasdev.financas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import io.github.fabasdev.financas.domain.Theme;
import io.github.fabasdev.financas.security.AppUserPrincipal;
import io.github.fabasdev.financas.service.SettingsService;
import io.github.fabasdev.financas.support.TestAccounts;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Escolha de tema em Ajustes: claro, escuro ou o do aparelho. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ThemeTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TestAccounts accounts;
    @Autowired
    SettingsService settings;

    AppUserPrincipal me;
    MockHttpSession session;

    @BeforeEach
    void setUp() {
        me = accounts.create().principal();
        session = new MockHttpSession();
    }

    @Test
    void semEscolhaAPaginaSegueOAparelho() throws Exception {
        mvc.perform(get("/").session(session).with(user(me)))
                .andExpect(content().string(containsString("<html lang=\"pt-BR\">")))
                .andExpect(content().string(containsString("<meta name=\"color-scheme\" content=\"light dark\">")));
    }

    /** A mesma sessão já mostra o tema novo: a troca atualiza o tema guardado nela. */
    @Test
    void escolherEscuroValeNaHoraEEmTodasAsTelas() throws Exception {
        mvc.perform(get("/").session(session).with(user(me)));

        mvc.perform(post("/ajustes/tema").param("tema", "DARK").session(session).with(user(me)).with(csrf()))
                .andExpect(redirectedUrl("/ajustes"))
                .andExpect(flash().attribute("success", "Tema salvo."));

        for (String page : new String[] {"/", "/lancamentos", "/ajustes", "/privacidade"}) {
            mvc.perform(get(page).session(session).with(user(me)))
                    .andExpect(content().string(containsString("<html lang=\"pt-BR\" data-theme=\"dark\">")))
                    .andExpect(content().string(containsString("<meta name=\"color-scheme\" content=\"dark\">")));
        }
        mvc.perform(get("/ajustes").session(session).with(user(me)))
                .andExpect(content().string(containsString("value=\"DARK\" checked")));
        // Em outra sessão (outro aparelho), o tema vem do banco.
        mvc.perform(get("/").with(user(me)))
                .andExpect(content().string(containsString("data-theme=\"dark\"")));
    }

    @Test
    void valorDesconhecidoNaoMudaOTema() throws Exception {
        mvc.perform(post("/ajustes/tema").param("tema", "roxo").with(user(me)).with(csrf()))
                .andExpect(redirectedUrl("/ajustes"))
                .andExpect(flash().attribute("error", "Escolha um dos temas."));
        assertThat(settings.get(me.getId()).getTheme()).isEqualTo(Theme.SYSTEM);
        mvc.perform(post("/ajustes/tema").param("tema", "LIGHT").with(user(me)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    void recomecarDoZeroMantemOTema() throws Exception {
        settings.updateTheme(me.getId(), Theme.LIGHT);
        mvc.perform(post("/ajustes/recomecar").param("confirmWord", "APAGAR").with(user(me)).with(csrf()))
                .andExpect(redirectedUrl("/"));
        assertThat(settings.get(me.getId()).getTheme()).isEqualTo(Theme.LIGHT);
    }

    @Test
    void telasSemLoginSeguemOAparelho() throws Exception {
        settings.updateTheme(me.getId(), Theme.DARK);
        mvc.perform(get("/login"))
                .andExpect(content().string(not(containsString("data-theme"))))
                .andExpect(content().string(containsString("content=\"light dark\"")));
    }

    /** O escuro aparece em dois blocos do tokens.css (aparelho no escuro e escolha "Escuro"): têm de ser iguais. */
    @Test
    void osDoisBlocosDoTemaEscuroTemOsMesmosValores() throws IOException {
        String css;
        try (InputStream in = getClass().getResourceAsStream("/static/css/tokens.css")) {
            css = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String bySystem = block(css, ":root:not([data-theme=\"light\"]) {");
        String byChoice = block(css, ":root[data-theme=\"dark\"] {\n  --");
        assertThat(bySystem).contains("--paper:").isEqualTo(byChoice);
    }

    /** As declarações do bloco que começa em {@code start}, sem espaços, uma por linha. */
    private static String block(String css, String start) {
        String normalized = css.replace("\r\n", "\n");
        int from = normalized.indexOf(start);
        assertThat(from).as("bloco %s", start).isNotNegative();
        int open = normalized.indexOf('{', from);
        String body = normalized.substring(open + 1, normalized.indexOf('}', open));
        StringBuilder out = new StringBuilder();
        Matcher m = Pattern.compile("(--[\\w-]+)\\s*:\\s*([^;]+);").matcher(body);
        while (m.find()) {
            out.append(m.group(1)).append(':').append(m.group(2).strip()).append('\n');
        }
        return out.toString();
    }
}
