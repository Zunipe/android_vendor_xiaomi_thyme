package com.zunipe.authonwatch;

import android.graphics.Bitmap;
import android.net.Uri;
import android.text.TextUtils;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

public class QrImportHelper {
    private QrImportHelper() {
    }

    public static OtpEntry decodeEntryFromBitmap(Bitmap bitmap) throws Exception {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        RGBLuminanceSource source = new RGBLuminanceSource(width, height, pixels);
        BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));

        Result result;
        try {
            result = new MultiFormatReader().decode(binaryBitmap);
        } catch (NotFoundException e) {
            throw new IllegalArgumentException("无法识别二维码，请选择更清晰的图片。");
        }
        return parseOtpAuth(result.getText());
    }

    public static OtpEntry parseOtpAuth(String content) {
        if (TextUtils.isEmpty(content)) {
            throw new IllegalArgumentException("二维码内容为空。");
        }

        Uri uri = Uri.parse(content);
        if (!"otpauth".equals(uri.getScheme()) || !"totp".equals(uri.getHost())) {
            throw new IllegalArgumentException("仅支持 TOTP 类型的 otpauth 链接。");
        }

        String secret = uri.getQueryParameter("secret");
        if (TextUtils.isEmpty(secret)) {
            throw new IllegalArgumentException("二维码中缺少 secret。");
        }

        String label = uri.getPath();
        if (!TextUtils.isEmpty(label) && label.startsWith("/")) {
            label = label.substring(1);
        }
        if (!TextUtils.isEmpty(label)) {
            try {
                label = URLDecoder.decode(label, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new IllegalArgumentException("二维码标签编码无效。");
            }
        }

        String issuer = uri.getQueryParameter("issuer");
        String name = chooseDisplayName(label, issuer);

        if (TextUtils.isEmpty(name)) {
            name = "未命名验证码";
        }

        return new OtpEntry(name, TotpUtils.normalizeSecret(secret));
    }

    private static String chooseDisplayName(String label, String issuer) {
        if (!TextUtils.isEmpty(label)) {
            int split = label.indexOf(':');
            if (split >= 0 && split < label.length() - 1) {
                return label.substring(split + 1).trim();
            }
            return label.trim();
        }
        if (!TextUtils.isEmpty(issuer)) {
            return issuer.trim();
        }
        return "";
    }
}
