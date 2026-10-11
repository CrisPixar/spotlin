package com.spotlin.server;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Spotlin Server Java Crypto & Media Verification Engine.
 * Provides high-speed validation for DoubleRatchet payloads and media fingerprints.
 */
public class SpotlinCryptoVerifier {

    public static String computeSHA256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 calculation failed", e);
        }
    }

    public static String computeFileFingerprint(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return Base64.getEncoder().encodeToString(digest.digest());
        } catch (Exception e) {
            throw new RuntimeException("Fingerprint failed for " + file.getName(), e);
        }
    }

    public static boolean verifyPayloadIntegrity(String payloadBase64, String expectedHash) {
        if (payloadBase64 == null || expectedHash == null) return false;
        try {
            byte[] raw = Base64.getDecoder().decode(payloadBase64);
            String calculated = computeSHA256(raw);
            return calculated.equals(expectedHash);
        } catch (Exception e) {
            return false;
        }
    }

    public static void main(String[] args) {
        System.out.println("Spotlin Server Java Verification Engine v2.0 initialized.");
        if (args.length > 0 && args[0].equals("--test")) {
            byte[] sample = "Spotlin-School-Protocol".getBytes();
            String hash = computeSHA256(sample);
            boolean ok = verifyPayloadIntegrity(Base64.getEncoder().encodeToString(sample), hash);
            System.out.println("Self-test integrity check: " + (ok ? "PASSED" : "FAILED"));
            System.exit(ok ? 0 : 1);
        }
    }
}
