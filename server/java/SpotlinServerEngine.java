package com.spotlin.messenger.server;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * SpotlinServerEngine (Java)
 * High-performance companion backend module for cryptographic payload handling.
 */
public class SpotlinServerEngine {
    private static final String AES_ALGO = "AES";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;

    public static byte[] deriveKey(String password, String salt) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(salt.getBytes(StandardCharsets.UTF_8));
        return md.digest(password.getBytes(StandardCharsets.UTF_8));
    }

    public static String decryptMessage(String base64Ciphertext, String base64Iv, byte[] key) throws Exception {
        byte[] cipherBytes = Base64.getDecoder().decode(base64Ciphertext);
        byte[] ivBytes = Base64.getDecoder().decode(base64Iv);

        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
        SecretKeySpec keySpec = new SecretKeySpec(key, AES_ALGO);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, ivBytes);

        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
        byte[] decrypted = cipher.doFinal(cipherBytes);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    public static String[] encryptMessage(String plaintext, byte[] key, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
        SecretKeySpec keySpec = new SecretKeySpec(key, AES_ALGO);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
        byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        return new String[] {
            Base64.getEncoder().encodeToString(encrypted),
            Base64.getEncoder().encodeToString(iv)
        };
    }
}
