package io.github.fabasdev.financas.security;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Código de recuperação de senha: 160 bits aleatórios em Base32 de Crockford
 * ("XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX"). Porte de crypto.ts.
 * No banco fica só o hash BCrypt do código, e cada código vale uma vez.
 */
public final class RecoveryCodes {

    /** Sem I, L, O e U, para não confundir na hora de digitar. */
    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private RecoveryCodes() {
    }

    public static String generate() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        StringBuilder out = new StringBuilder(39);
        int bits = 0;
        int value = 0;
        for (byte b : bytes) {
            value = (value << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                out.append(ALPHABET.charAt((value >>> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        return group(out.toString());
    }

    /** Aceita o código com ou sem hífens e espaços, e troca letras parecidas (O→0, I/L→1). */
    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        String clean = input.toUpperCase(Locale.ROOT)
                .replaceAll("[^0-9A-Z]", "")
                .replace('O', '0')
                .replace('I', '1')
                .replace('L', '1');
        // O código tem 32 caracteres. Mais que isso é cortado aqui, antes do BCrypt.
        return group(clean.length() > 32 ? clean.substring(0, 32) : clean);
    }

    private static String group(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i += 4) {
            if (i > 0) {
                out.append('-');
            }
            out.append(s, i, Math.min(i + 4, s.length()));
        }
        return out.toString();
    }
}
