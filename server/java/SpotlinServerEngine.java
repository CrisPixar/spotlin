package com.spotlin.messenger.server;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * SpotlinServerEngine (Java)
 * Verifies and validates cryptographic message payloads and signatures for the Spotlin messenger.
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

    public static void main(String[] args) {
        try {
            System.out.println("Starting SpotlinServerEngine self-test...");
            byte[] key = deriveKey("spotlin_secure_test_key", "spotlin_salt_2026");
            byte[] iv = new byte[12];
            for (int i = 0; i < 12; i++) iv[i] = (byte) (i + 1);

            String testMessage = "Spotlin E2EE Verified Test Message";
            String[] encrypted = encryptMessage(testMessage, key, iv);
            System.out.println("Encrypted ciphertext length: " + encrypted[0].length());

            String decrypted = decryptMessage(encrypted[0], encrypted[1], key);
            if (testMessage.equals(decrypted)) {
                System.out.println("SpotlinServerEngine Self-Test PASSED: Decrypted message matches: " + decrypted);
            } else {
                System.err.println("SpotlinServerEngine Self-Test FAILED!");
                System.exit(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
