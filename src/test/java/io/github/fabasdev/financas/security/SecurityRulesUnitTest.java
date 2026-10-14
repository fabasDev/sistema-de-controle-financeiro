package io.github.fabasdev.financas.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Regras de senha, limite de tentativas e código de recuperação, sem subir o Spring. */
class SecurityRulesUnitTest {

    @Nested
    class Senha {

        final PasswordPolicy policy = new PasswordPolicy();

        @Test
        void listaTemPeloMenos3000SenhasComunsDe12OuMaisCaracteres() {
            assertThat(policy.commonCount()).isGreaterThanOrEqualTo(3000);
        }

        @Test
        void aceitaFraseLongaSemRegraDeComposicao() {
            assertThat(policy.check("tamandua azul pula a cerca", "ana")).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"curta", "12345678901234"})
        void recusaMenosDe15Caracteres(String password) {
            assertThat(policy.check(password, "ana")).hasValueSatisfying(m -> assertThat(m).contains("15 caracteres"));
        }

        @Test
        void aceitaExatamente15Caracteres() {
            assertThat("cafe com leite!").hasSize(PasswordPolicy.MIN_LENGTH);
            assertThat(policy.check("cafe com leite!", "ana")).isEmpty();
        }

        /**
         * O ASVS 5.0 (6.2.4) pede pelo menos 3000 senhas comuns que passem na regra de tamanho: uma
         * senha da lista mais curta que o mínimo nunca seria conferida.
         */
        @Test
        void listaDeSenhasComunsTemSoSenhasDoTamanhoMinimo() throws java.io.IOException {
            java.util.List<String> lines;
            try (var in = getClass().getResourceAsStream("/security/common-passwords.txt")) {
                lines = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines()
                        .filter(l -> !l.isEmpty()).toList();
            }
            assertThat(lines).hasSizeGreaterThanOrEqualTo(3000)
                    .allSatisfy(l -> assertThat(l.codePointCount(0, l.length())).isGreaterThanOrEqualTo(PasswordPolicy.MIN_LENGTH));
        }

        @ParameterizedTest
        @ValueSource(strings = {"1qaz2wsx3edc4rfv", "qazwsxedcrfvtgb", "123456789987654321", "Flamengo20242024!",
                "iloveyou12345678", "aaaaaaaaaaaaaaa"})
        void recusaSenhasComuns(String password) {
            assertThat(policy.check(password, "ana")).hasValueSatisfying(m -> assertThat(m).contains("adivinhar"));
        }

        @Test
        void recusaSenhaComONomeDoUsuario() {
            assertThat(policy.check("minhasenha-fabricio-ok", "fabricio")).isPresent();
        }

        /** O BCrypt só usa 72 bytes. Em vez de cortar sem avisar, a senha é recusada. */
        @Test
        void recusaMaisDe72BytesContandoAcentos() {
            String ascii72 = "a1b2c3d4e5".repeat(7) + "xy";
            // 40 letras acentuadas = 80 bytes: passa do limite mesmo com menos de 72 caracteres.
            String accented = "ãéíõú".repeat(8);
            assertThat(ascii72.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(72);
            assertThat(policy.check(ascii72, "ana")).isEmpty();
            assertThat(policy.check(ascii72 + "z", "ana")).hasValueSatisfying(m -> assertThat(m).contains("longa"));
            assertThat(policy.check(accented, "ana")).hasValueSatisfying(m -> assertThat(m).contains("longa"));
        }
    }

    @Nested
    class LimiteDeTentativas {

        final MutableClock clock = new MutableClock();
        final LoginAttemptService attempts = new LoginAttemptService(clock);

        @Test
        void bloqueiaNoQuintoErroEEsperaDobra() {
            for (int i = 0; i < 4; i++) {
                attempts.recordFailure("login", "ana", "1.1.1.1");
            }
            assertThat(attempts.blockedFor("login", "ana", "1.1.1.1")).isEmpty();

            attempts.recordFailure("login", "ana", "1.1.1.1");
            assertThat(attempts.blockedFor("login", "ana", "1.1.1.1")).contains(Duration.ofSeconds(30));

            clock.advance(Duration.ofSeconds(31));
            assertThat(attempts.blockedFor("login", "ana", "1.1.1.1")).isEmpty();

            attempts.recordFailure("login", "ana", "1.1.1.1");
            assertThat(attempts.blockedFor("login", "ana", "1.1.1.1")).contains(Duration.ofMinutes(1));
        }

        @Test
        void esperaNuncaPassaDe15Minutos() {
            assertThat(LoginAttemptService.delay(0)).isEqualTo(Duration.ofSeconds(30));
            assertThat(LoginAttemptService.delay(4)).isEqualTo(Duration.ofMinutes(8));
            assertThat(LoginAttemptService.delay(5)).isEqualTo(Duration.ofMinutes(15));
            assertThat(LoginAttemptService.delay(40)).isEqualTo(Duration.ofMinutes(15));
        }

        @Test
        void acertoZeraOContadorDoUsuario() {
            for (int i = 0; i < 4; i++) {
                attempts.recordFailure("login", "ana", "1.1.1.1");
            }
            attempts.recordSuccess("login", "ana", "1.1.1.1");
            attempts.recordFailure("login", "ana", "1.1.1.1");
            assertThat(attempts.blockedFor("login", "ana", "1.1.1.1")).isEmpty();
        }

        /** Ataque distribuído: muitos IPs tentando a mesma conta. */
        @Test
        void bloqueiaAContaDepoisDe20ErrosDeIpsDiferentes() {
            for (int i = 0; i < 20; i++) {
                attempts.recordFailure("login", "ana", "2.2.2." + i);
            }
            assertThat(attempts.blockedFor("login", "ana", "9.9.9.9")).isPresent();
            assertThat(attempts.blockedFor("login", "bia", "9.9.9.9")).isEmpty();
        }

        /** Password spraying: um IP testando muitas contas. */
        @Test
        void bloqueiaOIpDepoisDe50ErrosEmContasDiferentes() {
            for (int i = 0; i < 50; i++) {
                attempts.recordFailure("login", "conta" + i, "3.3.3.3");
            }
            assertThat(attempts.blockedFor("login", "outra", "3.3.3.3")).isPresent();
        }

        @Test
        void contadorZeraDepoisDeUmaHoraSemErro() {
            for (int i = 0; i < 4; i++) {
                attempts.recordFailure("login", "ana", "1.1.1.1");
            }
            clock.advance(Duration.ofMinutes(61));
            attempts.recordFailure("login", "ana", "1.1.1.1");
            assertThat(attempts.blockedFor("login", "ana", "1.1.1.1")).isEmpty();
        }
    }

    @Nested
    class CodigoDeRecuperacao {

        @Test
        void tem32CaracteresEmGruposDe4() {
            assertThat(RecoveryCodes.generate()).matches("([0-9A-HJKMNP-TV-Z]{4}-){7}[0-9A-HJKMNP-TV-Z]{4}");
        }

        @Test
        void aceitaSemHifenEComLetrasParecidas() {
            assertThat(RecoveryCodes.normalize("abcd efgh ijkl mnop qrst vwxy z012 3456"))
                    .isEqualTo("ABCD-EFGH-1JK1-MN0P-QRST-VWXY-Z012-3456");
        }
    }

    @Nested
    class Log {

        @Test
        void naoRegistraONomeEImpedeQuebraDeLinha() {
            assertThat(SecurityLog.pseudonym("fabricio")).hasSize(12).doesNotContain("fabricio");
            assertThat(SecurityLog.clean("a\r\nevento=falso")).doesNotContain("\n").doesNotContain("\r");
        }
    }

    static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-13T12:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
