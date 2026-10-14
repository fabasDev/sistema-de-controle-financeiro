package io.github.fabasdev.financas.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Log de eventos de segurança (login, bloqueio, troca de senha). Seguindo o Logging Cheat Sheet:
 * nunca registra senha, código de recuperação ou valor financeiro. O usuário aparece como um
 * apelido (começo do SHA-256), e quebras de linha são removidas para ninguém forjar linhas no log.
 */
public final class SecurityLog {

    private static final Logger LOG = LoggerFactory.getLogger("security");

    private SecurityLog() {
    }

    public static void event(String event, String username, String ip) {
        LOG.info("evento={} usuario={} ip={}", clean(event), pseudonym(username), clean(ip));
    }

    public static void warn(String event, String username, String ip) {
        LOG.warn("evento={} usuario={} ip={}", clean(event), pseudonym(username), clean(ip));
    }

    /** Os 12 primeiros dígitos hexadecimais do SHA-256: dá para seguir o mesmo usuário sem expor o nome. */
    static String pseudonym(String username) {
        if (username == null || username.isEmpty()) {
            return "-";
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(username.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 6);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String clean(String value) {
        if (value == null) {
            return "-";
        }
        String s = value.replaceAll("[\\r\\n\\t]", "_");
        return s.length() > 64 ? s.substring(0, 64) : s;
    }
}
