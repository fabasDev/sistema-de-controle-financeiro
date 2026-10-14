package io.github.fabasdev.financas.security;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Formato do nome de usuário. "Ana" e "ana" são a mesma conta. */
public final class UsernameRules {

    public static final int MIN = 3;
    public static final int MAX = 60;

    /** Letras (com acento), números, ponto, hífen e sublinhado. Sem espaço nem quebra de linha. */
    private static final Pattern ALLOWED = Pattern.compile("^[\\p{L}\\p{N}._-]+$");

    private UsernameRules() {
    }

    /** Forma usada para guardar e buscar: sem espaços nas pontas, Unicode NFKC e minúsculas. */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return Normalizer.normalize(raw.strip(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    /** Mensagem de erro para o cadastro, ou vazio se o nome estiver bom. */
    public static Optional<String> validate(String normalized) {
        int length = normalized.codePointCount(0, normalized.length());
        if (length < MIN) {
            return Optional.of("Use pelo menos " + MIN + " caracteres.");
        }
        if (length > MAX) {
            return Optional.of("Use no máximo " + MAX + " caracteres.");
        }
        if (!ALLOWED.matcher(normalized).matches()) {
            return Optional.of("Use só letras, números, ponto, hífen ou sublinhado, sem espaços.");
        }
        return Optional.empty();
    }
}
