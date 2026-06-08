package com.zunipe.authonwatch;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class TotpUtils {
    private static final int TIME_STEP_SECONDS = 30;
    private static final int DIGITS = 6;
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private TotpUtils() {
    }

    public static String generateCurrentCode(String base32Secret) throws GeneralSecurityException {
        long currentSeconds = System.currentTimeMillis() / 1000L;
        long counter = currentSeconds / TIME_STEP_SECONDS;
        return generateCode(base32Secret, counter);
    }

    public static int getSecondsRemaining() {
        long currentSeconds = System.currentTimeMillis() / 1000L;
        long elapsed = currentSeconds % TIME_STEP_SECONDS;
        return (int) (TIME_STEP_SECONDS - elapsed);
    }

    private static String generateCode(String base32Secret, long counter) throws GeneralSecurityException {
        byte[] secret = decodeBase32(base32Secret);
        byte[] counterBytes = ByteBuffer.allocate(8).putLong(counter).array();
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(secret, "HmacSHA1"));
        byte[] hash = mac.doFinal(counterBytes);

        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);

        int otp = binary % (int) Math.pow(10, DIGITS);
        return String.format(Locale.US, "%0" + DIGITS + "d", otp);
    }

    public static String normalizeSecret(String rawSecret) {
        return rawSecret == null ? "" : rawSecret.replace(" ", "").toUpperCase(Locale.US);
    }

    private static byte[] decodeBase32(String rawSecret) {
        String secret = normalizeSecret(rawSecret).replace("=", "");
        if (secret.isEmpty()) {
            return new byte[0];
        }

        int byteCount = secret.length() * 5 / 8;
        byte[] result = new byte[byteCount];
        int buffer = 0;
        int bitsLeft = 0;
        int index = 0;

        for (int i = 0; i < secret.length(); i++) {
            int value = BASE32_ALPHABET.indexOf(secret.charAt(i));
            if (value < 0) {
                throw new IllegalArgumentException("Secret contains invalid base32 character.");
            }
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                result[index++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return result;
    }
}
