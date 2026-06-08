package com.zunipe.authonwatch;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class OtpStorage {
    private static final String PREF_NAME = "otp_storage";
    private static final String KEY_ENCRYPTED_DATA = "encrypted_data";
    private static final String KEY_ENCRYPTED_IV = "encryption_iv";

    private OtpStorage() {
    }

    public static List<OtpEntry> load(Context context) {
        List<OtpEntry> entries = new ArrayList<>();

        try {
            SharedPreferences sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String encryptedStringBase64 = sharedPreferences.getString(KEY_ENCRYPTED_DATA, null);
            String ivBase64 = sharedPreferences.getString(KEY_ENCRYPTED_IV, null);
            if (encryptedStringBase64 == null || ivBase64 == null) {
                return entries;
            }
            byte[] encryptedBytes = Base64.decode(encryptedStringBase64, Base64.DEFAULT);
            byte[] iv = Base64.decode(ivBase64, Base64.DEFAULT);

            SecretKey secretKey = SecretHelper.getSecretKey();

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            String decryptedData = new String(decryptedBytes, "UTF-8");
            JSONArray array = new JSONArray(decryptedData);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                entries.add(OtpEntry.fromJson(object));
            }
        } catch (Exception ignored) {

        }
        return entries;
    }

    public static void save(Context context, List<OtpEntry> entries) {
        try {
            SecretHelper.getOrCreateSecretKey();
            SecretKey secretKey = SecretHelper.getSecretKey();

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);

            JSONArray array = new JSONArray();
            for (OtpEntry entry : entries) {
                array.put(entry.toJson());
            }

            byte[] encryptionBytes = cipher.doFinal(array.toString().getBytes("UTF-8"));
            byte[] iv = cipher.getIV();

            String encryptedStringBase64 = Base64.encodeToString(encryptionBytes, Base64.DEFAULT);
            String ivBase64 = Base64.encodeToString(iv, Base64.DEFAULT);

            SharedPreferences preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            preferences.edit()
                    .putString(KEY_ENCRYPTED_DATA, encryptedStringBase64)
                    .putString(KEY_ENCRYPTED_IV, ivBase64)
                    .apply();
        } catch (Exception ignored) {

        }
    }
}
