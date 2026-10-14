package io.github.fabasdev.financas.service.backup;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Grava o backup criptografado no mesmo formato que o app antigo exportava (exportEncryptedBackup em
 * legacy-web/src/store/backup.ts), para o arquivo voltar pela tela de importação.
 */
public final class BackupEncryptor {

    /** As mesmas 600 mil iterações do app antigo, o mínimo do Password Storage Cheat Sheet para PBKDF2-SHA256. */
    static final int ITERATIONS = 600_000;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private BackupEncryptor() {
    }

    /** Devolve o arquivo pronto: o envelope em JSON com sal, IV e os dados cifrados em Base64. */
    public static byte[] encrypt(byte[] plain, String password) {
        byte[] salt = random(16);
        byte[] iv = random(12);
        byte[] sealed;
        try {
            SecretKey key = LegacyBackupDecryptor.deriveKey(password, salt, ITERATIONS);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            cipher.updateAAD(LegacyBackupDecryptor.AAD.getBytes(StandardCharsets.UTF_8));
            sealed = cipher.doFinal(plain);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao criptografar o backup", e);
        }
        Base64.Encoder base64 = Base64.getEncoder();
        ObjectNode file = JSON.createObjectNode();
        file.put("app", "financas");
        file.put("format", "encrypted");
        file.put("version", 1);
        ObjectNode kdf = file.putObject("kdf");
        kdf.put("salt", base64.encodeToString(salt));
        kdf.put("iterations", ITERATIONS);
        file.put("iv", base64.encodeToString(iv));
        file.put("data", base64.encodeToString(sealed));
        return JSON.writeValueAsBytes(file);
    }

    private static byte[] random(int size) {
        byte[] bytes = new byte[size];
        RANDOM.nextBytes(bytes);
        return bytes;
    }
}
