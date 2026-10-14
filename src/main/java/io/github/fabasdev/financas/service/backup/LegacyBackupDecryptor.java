package io.github.fabasdev.financas.service.backup;

import io.github.fabasdev.financas.service.InvalidInputException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import tools.jackson.databind.JsonNode;

/**
 * Abre o backup criptografado, no formato de legacy-web/src/store/backup.ts: chave AES-256 derivada
 * da senha com PBKDF2-SHA256 e dados em AES-GCM, com AAD "financas:backup:v1". A senha passa por NFKC
 * antes, como fazia o deriveKek do navegador. O "Exportar meus dados" (BackupEncryptor) grava no mesmo
 * formato, então o arquivo do app antigo e o deste app abrem pelo mesmo caminho.
 */
public final class LegacyBackupDecryptor {

    static final String AAD = "financas:backup:v1";
    /**
     * O número de iterações vem do arquivo, então é limitado (auditoria, item 8): abaixo de 100 mil
     * seria um backup fraco, e acima de 1 milhão um arquivo forjado poderia ocupar a CPU do servidor.
     */
    static final int MIN_ITERATIONS = 100_000;
    static final int MAX_ITERATIONS = 1_000_000;
    static final String NOT_A_BACKUP = "Este arquivo não é um backup do app Finanças.";
    static final String WRONG_PASSWORD = "Senha do backup incorreta ou arquivo alterado.";

    private LegacyBackupDecryptor() {
    }

    /** Confere o envelope antes de gastar CPU com a senha. */
    public static Envelope readEnvelope(JsonNode root) {
        if (root == null || !root.isObject()
                || !"financas".equals(text(root.get("app")))
                || !"encrypted".equals(text(root.get("format")))
                || !root.path("version").isIntegralNumber() || root.path("version").asInt() != 1) {
            throw new InvalidInputException("file", NOT_A_BACKUP);
        }
        JsonNode kdf = root.path("kdf");
        if (!kdf.path("iterations").isIntegralNumber() || !kdf.path("iterations").canConvertToInt()) {
            throw new InvalidInputException("file", NOT_A_BACKUP);
        }
        int iterations = kdf.path("iterations").asInt();
        if (iterations < MIN_ITERATIONS || iterations > MAX_ITERATIONS) {
            throw new InvalidInputException("file", "Este backup usa um parâmetro de segurança fora do esperado. "
                    + "Exporte o backup de novo.");
        }
        byte[] salt = base64(kdf.get("salt"));
        byte[] iv = base64(root.get("iv"));
        byte[] data = base64(root.get("data"));
        if (salt.length < 16 || iv.length != 12 || data.length < 17) {
            throw new InvalidInputException("file", NOT_A_BACKUP);
        }
        return new Envelope(salt, iterations, iv, data);
    }

    public record Envelope(byte[] salt, int iterations, byte[] iv, byte[] data) {
    }

    /** Devolve o JSON decifrado. Senha errada ou arquivo alterado: a tag do GCM não confere. */
    public static byte[] decrypt(Envelope envelope, String password) {
        try {
            SecretKey key = deriveKey(password, envelope.salt(), envelope.iterations());
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, envelope.iv()));
            cipher.updateAAD(AAD.getBytes(StandardCharsets.UTF_8));
            // O Web Crypto grava o texto cifrado com a tag de 16 bytes no fim, o mesmo formato do Java.
            return cipher.doFinal(envelope.data());
        } catch (AEADBadTagException e) {
            throw new InvalidInputException("password", WRONG_PASSWORD);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao abrir o backup", e);
        }
    }

    /** Chave AES-256 da senha (NFKC + PBKDF2-SHA256), usada para abrir e para gravar o backup. */
    static SecretKey deriveKey(String password, byte[] salt, int iterations) throws GeneralSecurityException {
        char[] chars = Normalizer.normalize(password, Normalizer.Form.NFKC).toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, iterations, 256);
        byte[] keyBytes = null;
        try {
            keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            // O SecretKeySpec guarda uma cópia da chave, então keyBytes pode ser limpo em seguida.
            return new SecretKeySpec(keyBytes, "AES");
        } finally {
            spec.clearPassword();
            Arrays.fill(chars, '\0');
            if (keyBytes != null) {
                Arrays.fill(keyBytes, (byte) 0);
            }
        }
    }

    private static String text(JsonNode node) {
        return node != null && node.isString() ? node.stringValue() : null;
    }

    private static byte[] base64(JsonNode node) {
        String s = text(node);
        if (s == null) {
            throw new InvalidInputException("file", NOT_A_BACKUP);
        }
        try {
            return Base64.getDecoder().decode(s);
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("file", NOT_A_BACKUP);
        }
    }
}
