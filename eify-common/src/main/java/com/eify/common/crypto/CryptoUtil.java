package com.eify.common.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 加密工具，用于保护数据库中的敏感字段（如 API Key）。
 *
 * 密文格式：Base64(12-byte IV + ciphertext + 16-byte GCM tag)
 * KEK (Key Encryption Key) 通过环境变量 CRYPTO_KEK 注入。
 */
public final class CryptoUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int AES_KEY_BYTES = 32;

    private static volatile SecretKey cachedKey;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private CryptoUtil() {}

    public static String encrypt(String plaintext, byte[] kek) {
        if (plaintext == null || plaintext.isEmpty()) return plaintext;
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            SECURE_RANDOM.nextBytes(iv);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, getKey(kek), spec);
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new RuntimeException("加密失败", e);
        }
    }

    public static String decrypt(String ciphertextB64, byte[] kek) {
        if (ciphertextB64 == null || ciphertextB64.isEmpty()) return ciphertextB64;
        try {
            byte[] data = Base64.getDecoder().decode(ciphertextB64);
            ByteBuffer buffer = ByteBuffer.wrap(data);
            byte[] iv = new byte[GCM_IV_BYTES];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, getKey(kek), spec);
            return new String(cipher.doFinal(ciphertext), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("解密失败", e);
        }
    }

    public static byte[] decodeKek(String kekBase64) {
        if (kekBase64 == null || kekBase64.isBlank()) {
            throw new IllegalStateException("CRYPTO_KEK 环境变量未设置，无法加解密敏感数据");
        }
        byte[] decoded = Base64.getDecoder().decode(kekBase64);
        if (decoded.length != AES_KEY_BYTES) {
            throw new IllegalStateException(
                    "CRYPTO_KEK 长度无效: " + decoded.length + " bytes, 需要 " + AES_KEY_BYTES + " bytes");
        }
        return decoded;
    }

    private static SecretKey getKey(byte[] kek) {
        if (cachedKey == null || !java.util.Arrays.equals(cachedKey.getEncoded(), kek)) {
            cachedKey = new SecretKeySpec(kek, "AES");
        }
        return cachedKey;
    }
}
