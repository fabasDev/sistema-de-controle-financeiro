package io.github.fabasdev.financas.security;

import static io.github.fabasdev.financas.support.TestAccounts.fromIp;
import static io.github.fabasdev.financas.support.TestAccounts.randomIp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.fabasdev.financas.repository.UserRepository;
import io.github.fabasdev.financas.support.TestAccounts;
import io.github.fabasdev.financas.support.TestAccounts.Account;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebSecurityTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TestAccounts accounts;
    @Autowired
    UserRepository users;

    @Nested
    class Login {

        @Test
        void entraComASenhaCerta() throws Exception {
            Account a = accounts.create();
            mvc.perform(post("/login").with(csrf()).with(fromIp(randomIp()))
                            .param("username", a.user().getUsername()).param("password", a.password()))
                    .andExpect(redirectedUrl("/"))
                    .andExpect(authenticated());
        }

        @Test
        void aceitaOUsuarioComMaiusculasEEspacos() throws Exception {
            Account a = accounts.create();
            mvc.perform(post("/login").with(csrf()).with(fromIp(randomIp()))
                            .param("username", "  " + a.user().getUsername().toUpperCase() + " ")
                            .param("password", a.password()))
                    .andExpect(authenticated());
        }

        /** Mesma resposta para senha errada e usuário inexistente: não dá para descobrir quem tem conta. */
        @Test
        void senhaErradaEUsuarioInexistenteTemAMesmaResposta() throws Exception {
            Account a = accounts.create();
            mvc.perform(post("/login").with(csrf()).with(fromIp(randomIp()))
                            .param("username", a.user().getUsername()).param("password", "senha errada de proposito"))
                    .andExpect(status().isFound())
                    .andExpect(redirectedUrl("/login?erro"))
                    .andExpect(unauthenticated());
            mvc.perform(post("/login").with(csrf()).with(fromIp(randomIp()))
                            .param("username", "ninguem" + UUID.randomUUID()).param("password", "qualquer senha longa"))
                    .andExpect(status().isFound())
                    .andExpect(redirectedUrl("/login?erro"))
                    .andExpect(unauthenticated());
        }

        @Test
        void senhaComMaisDe72BytesNaoEntraMesmoComOsPrimeiros72Certos() throws Exception {
            Account a = accounts.create();
            mvc.perform(post("/login").with(csrf()).with(fromIp(randomIp()))
                            .param("username", a.user().getUsername())
                            .param("password", a.password() + "x".repeat(80)))
                    .andExpect(redirectedUrl("/login?erro"))
                    .andExpect(unauthenticated());
        }

        @Test
        void bloqueiaDepoisDe5ErrosMesmoComASenhaCerta() throws Exception {
            Account a = accounts.create();
            String ip = randomIp();
            for (int i = 0; i < 5; i++) {
                mvc.perform(post("/login").with(csrf()).with(fromIp(ip))
                        .param("username", a.user().getUsername()).param("password", "errada numero " + i));
            }
            mvc.perform(post("/login").with(csrf()).with(fromIp(ip))
                            .param("username", a.user().getUsername()).param("password", a.password()))
                    .andExpect(redirectedUrl("/login?bloqueado"))
                    .andExpect(unauthenticated());
        }

        /**
         * Um caminho codificado (/%6cogin = /login) não serve para escapar do limite de tentativas:
         * o firewall do Spring Security recusa a requisição antes do login (400).
         */
        @Test
        void caminhoCodificadoNaoEntraPorFora() throws Exception {
            Account a = accounts.create();
            mvc.perform(post("/%6cogin").with(csrf()).with(fromIp(randomIp()))
                            .param("username", a.user().getUsername()).param("password", a.password()))
                    .andExpect(status().isBadRequest())
                    .andExpect(unauthenticated());
        }

        @Test
        void loginSemTokenCsrfEhRecusado() throws Exception {
            Account a = accounts.create();
            mvc.perform(post("/login").with(fromIp(randomIp()))
                            .param("username", a.user().getUsername()).param("password", a.password()))
                    .andExpect(status().isForbidden())
                    .andExpect(unauthenticated());
        }

        /** Fixação de sessão: o id da sessão muda no login. */
        @Test
        void trocaOIdDaSessaoNoLogin() throws Exception {
            Account a = accounts.create();
            MockHttpSession session = new MockHttpSession();
            String before = session.getId();
            MvcResult result = mvc.perform(post("/login").session(session).with(csrf()).with(fromIp(randomIp()))
                            .param("username", a.user().getUsername()).param("password", a.password()))
                    .andExpect(authenticated())
                    .andReturn();
            assertThat(result.getRequest().getSession().getId()).isNotEqualTo(before);
        }

        @Test
        void paginaLogadaSemLoginVaiParaOLogin() throws Exception {
            mvc.perform(get("/"))
                    .andExpect(status().isFound())
                    .andExpect(redirectedUrl("/login"));
        }

        @Test
        void sairEncerraASessao() throws Exception {
            Account a = accounts.create();
            MvcResult login = mvc.perform(post("/login").with(csrf()).with(fromIp(randomIp()))
                            .param("username", a.user().getUsername()).param("password", a.password()))
                    .andReturn();
            MockHttpSession session = (MockHttpSession) login.getRequest().getSession();
            mvc.perform(post("/logout").session(session).with(csrf()))
                    .andExpect(redirectedUrl("/login?saiu"));
            assertThat(session.isInvalid()).isTrue();
        }
    }

    @Nested
    class Cadastro {

        @Test
        void criaAContaEMostraOCodigoUmaVez() throws Exception {
            String username = "novo" + UUID.randomUUID().toString().substring(0, 8);
            mvc.perform(post("/cadastro").with(csrf()).with(fromIp(randomIp()))
                            .param("username", username)
                            .param("password", TestAccounts.PASSWORD)
                            .param("confirm", TestAccounts.PASSWORD))
                    .andExpect(redirectedUrl("/cadastro/codigo"))
                    .andExpect(flash().attributeExists("recoveryCode"));
            assertThat(users.findByUsername(username)).hasValueSatisfying(u -> {
                assertThat(u.getPasswordHash()).startsWith("{bcrypt}");
                assertThat(u.getPasswordHash()).doesNotContain(TestAccounts.PASSWORD);
                assertThat(u.getRecoveryHash()).startsWith("{bcrypt}");
            });
            // Sem o flash attribute (recarregou a página), o código não aparece de novo.
            mvc.perform(get("/cadastro/codigo")).andExpect(redirectedUrl("/login"));
        }

        @Test
        void avisaQueOUsuarioJaExiste() throws Exception {
            Account a = accounts.create();
            mvc.perform(post("/cadastro").with(csrf()).with(fromIp(randomIp()))
                            .param("username", a.user().getUsername().toUpperCase())
                            .param("password", TestAccounts.PASSWORD)
                            .param("confirm", TestAccounts.PASSWORD))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("já existe")));
        }

        @Test
        void recusaSenhaComum() throws Exception {
            mvc.perform(post("/cadastro").with(csrf()).with(fromIp(randomIp()))
                            .param("username", "u" + UUID.randomUUID().toString().substring(0, 8))
                            .param("password", "qwerty123456")
                            .param("confirm", "qwerty123456"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("fácil de adivinhar")));
        }

        @Test
        void naoDevolveASenhaNoFormularioComErro() throws Exception {
            mvc.perform(post("/cadastro").with(csrf()).with(fromIp(randomIp()))
                            .param("username", "u" + UUID.randomUUID().toString().substring(0, 8))
                            .param("password", "senha secreta diferente")
                            .param("confirm", "outra coisa qualquer aqui"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("senha secreta diferente"))));
        }

        @Test
        void textoDoUsuarioSaiEscapadoNoHtml() throws Exception {
            mvc.perform(post("/cadastro").with(csrf()).with(fromIp(randomIp()))
                            .param("username", "<script>alert(1)</script>")
                            .param("password", TestAccounts.PASSWORD)
                            .param("confirm", TestAccounts.PASSWORD))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("<script>alert(1)</script>"))))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;")));
        }
    }

    @Nested
    class Recuperacao {

        @Test
        void codigoCertoTrocaASenhaEOCodigoAntigoDeixaDeValer() throws Exception {
            Account a = accounts.create();
            String newPassword = "jabuticaba madura no quintal";
            mvc.perform(post("/recuperar").with(csrf()).with(fromIp(randomIp()))
                            .param("username", a.user().getUsername())
                            .param("code", a.recoveryCode().toLowerCase().replace("-", " "))
                            .param("password", newPassword)
                            .param("confirm", newPassword))
                    .andExpect(redirectedUrl("/recuperar/codigo"))
                    .andExpect(flash().attributeExists("recoveryCode"));

            mvc.perform(post("/login").with(csrf()).with(fromIp(randomIp()))
                            .param("username", a.user().getUsername()).param("password", newPassword))
                    .andExpect(authenticated());

            mvc.perform(post("/recuperar").with(csrf()).with(fromIp(randomIp()))
                            .param("username", a.user().getUsername())
                            .param("code", a.recoveryCode())
                            .param("password", "outra frase bem comprida")
                            .param("confirm", "outra frase bem comprida"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("código de recuperação incorretos")));
        }

        @Test
        void usuarioInexistenteTemAMesmaMensagemDeCodigoErrado() throws Exception {
            mvc.perform(post("/recuperar").with(csrf()).with(fromIp(randomIp()))
                            .param("username", "ninguem" + UUID.randomUUID().toString().substring(0, 8))
                            .param("code", "AAAA-BBBB-CCCC-DDDD-EEEE-FFFF-GGGG-HHHH")
                            .param("password", "outra frase bem comprida")
                            .param("confirm", "outra frase bem comprida"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("código de recuperação incorretos")));
        }
    }

    @Nested
    class Cabecalhos {

        /** secure(true): o HSTS só é enviado em HTTPS, como manda a especificação. */
        @Test
        void respostasTrazemOsCabecalhosDeSeguranca() throws Exception {
            MvcResult result = mvc.perform(get("/login").secure(true))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("X-Frame-Options", "DENY"))
                    .andExpect(header().string("Referrer-Policy", "no-referrer"))
                    .andExpect(header().string("Strict-Transport-Security", "max-age=63072000 ; includeSubDomains"))
                    .andExpect(header().string("Cross-Origin-Opener-Policy", "same-origin"))
                    .andReturn();
            String csp = result.getResponse().getHeader("Content-Security-Policy");
            assertThat(csp).contains("script-src 'self'", "style-src 'self'", "frame-ancestors 'none'",
                    "object-src 'none'").doesNotContain("unsafe-inline", "unsafe-eval");
            assertThat(result.getResponse().getHeader("Cache-Control")).contains("no-store");
        }

        @Test
        void paginaDeErroNaoMostraDetalhesInternos() throws Exception {
            mvc.perform(get("/nao-existe").with(
                            org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                    .user(accounts.create().principal())))
                    .andExpect(status().isNotFound())
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("Exception"))));
        }
    }
}
