package io.github.fabasdev.financas.security;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Regras de senha (decisão da Fase 1, ASVS 5.0 6.2.1, 6.2.4, 6.2.5 e 6.2.8):
 * <ul>
 *   <li>mínimo de 15 caracteres;</li>
 *   <li>máximo de 72 bytes em UTF-8, o limite do BCrypt. A senha é recusada, nunca cortada;</li>
 *   <li>nenhuma regra de composição (não exige maiúscula, número ou símbolo);</li>
 *   <li>não pode estar entre as senhas mais comuns nem conter o nome de usuário.</li>
 * </ul>
 * A senha é usada exatamente como foi digitada, sem normalização.
 */
@Component
public class PasswordPolicy {

    /** O Authentication Cheat Sheet da OWASP considera fraca a senha com menos de 15 caracteres sem segundo fator. */
    public static final int MIN_LENGTH = 15;
    public static final int MAX_BYTES = 72;

    /** Números e símbolos no fim ("flamengo2024!") são tirados antes de comparar com as palavras-base. */
    private static final Pattern TRAILING_DIGITS_AND_SYMBOLS = Pattern.compile("[\\d\\p{Punct}\\s]+$");

    /** 10.000 senhas com 15+ caracteres mais usadas (SecLists, licença MIT), todas acima do mínimo. */
    private final Set<String> common;
    /** Senhas curtas muito usadas e palavras do app antigo, para pegar "palavra + números". */
    private final Set<String> bases;

    public PasswordPolicy() {
        this.common = load("security/common-passwords.txt");
        this.bases = load("security/common-password-bases.txt");
    }

    public static boolean exceedsMaxBytes(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES;
    }

    /** Mensagem para o usuário, ou vazio se a senha for aceita. */
    public Optional<String> check(String password, String normalizedUsername) {
        if (password == null || password.codePointCount(0, password.length()) < MIN_LENGTH) {
            return Optional.of("Use pelo menos " + MIN_LENGTH + " caracteres. Uma frase com algumas palavras funciona bem.");
        }
        if (exceedsMaxBytes(password)) {
            return Optional.of("Senha longa demais. Use até " + MAX_BYTES
                    + " bytes (cerca de 70 letras sem acento).");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        String base = TRAILING_DIGITS_AND_SYMBOLS.matcher(lower).replaceAll("");
        // "aaaaaaaaaaaa" e parecidas: poucos caracteres diferentes (mesma regra do app antigo).
        if (password.codePoints().distinct().count() < 5 || common.contains(lower) || bases.contains(base)) {
            return Optional.of("Essa senha é fácil de adivinhar (está entre as mais usadas). Escolha outra.");
        }
        if (normalizedUsername != null && normalizedUsername.length() >= 3 && lower.contains(normalizedUsername)) {
            return Optional.of("A senha não pode conter o nome de usuário.");
        }
        return Optional.empty();
    }

    int commonCount() {
        return common.size();
    }

    private static Set<String> load(String path) {
        Set<String> words = new HashSet<>();
        try (var reader = new BufferedReader(
                new InputStreamReader(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty()) {
                    words.add(line);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Lista de senhas não encontrada: " + path, e);
        }
        return Set.copyOf(words);
    }
}
