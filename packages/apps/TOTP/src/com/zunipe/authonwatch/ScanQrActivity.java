package com.zunipe.authonwatch;

import android.content.Intent;
import android.os.Bundle;
import android.util.Size;
import android.view.KeyEvent;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScanQrActivity extends AppCompatActivity {
    public static final String EXTRA_RAW_CONTENT = "com.zunipe.authonwatch.EXTRA_RAW_CONTENT";

    private static final int ANALYSIS_WIDTH = 640;
    private static final int ANALYSIS_HEIGHT = 480;
    private static final long MIN_DECODE_INTERVAL_MS = 200L;

    private PreviewView previewView;
    private ExecutorService cameraExecutor;
    private final MultiFormatReader reader = new MultiFormatReader();
    private volatile boolean finished;
    private volatile long lastDecodeAttemptMs;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_qr);

        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.QR_CODE));
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        reader.setHints(hints);

        previewView = findViewById(R.id.previewView);
        cameraExecutor = Executors.newSingleThreadExecutor();

        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                bindCameraUseCases(provider);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            setResult(RESULT_CANCELED);
        }
        return super.onKeyDown(keyCode, event);
    }

    private void bindCameraUseCases(@NonNull ProcessCameraProvider provider) {
        if (finished || !getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
            return;
        }

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        int rotation = 0;
        if (previewView.getDisplay() != null) {
            rotation = previewView.getDisplay().getRotation();
        }
        preview.setTargetRotation(rotation);

        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(ANALYSIS_WIDTH, ANALYSIS_HEIGHT))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build();
        analysis.setTargetRotation(rotation);
        analysis.setAnalyzer(cameraExecutor, this::analyzeFrame);

        provider.unbindAll();

        try {
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
        } catch (IllegalArgumentException e) {
            try {
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis);
            } catch (IllegalArgumentException e2) {
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.scan_qr_no_camera, Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        }
    }

    private void analyzeFrame(@NonNull ImageProxy image) {
        if (finished) {
            image.close();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastDecodeAttemptMs < MIN_DECODE_INTERVAL_MS) {
            image.close();
            return;
        }
        lastDecodeAttemptMs = now;

        try {
            byte[] luminances = yPlaneToPackedLuminance(image);
            int w = image.getWidth();
            int h = image.getHeight();
            String text = tryDecodeQrText(luminances, w, h);
            if (text != null) {
                try {
                    QrImportHelper.parseOtpAuth(text);
                    finished = true;
                    final String payload = text;
                    runOnUiThread(() -> deliverSuccess(payload));
                } catch (IllegalArgumentException ignored) {
                    reader.reset();
                }
            }
        } catch (Throwable ignored) {
            // 单帧解码失败很常见，继续扫描。
        } finally {
            image.close();
        }
    }

    private void deliverSuccess(@NonNull String rawContent) {
        Intent data = new Intent();
        data.putExtra(EXTRA_RAW_CONTENT, rawContent);
        setResult(RESULT_OK, data);
        finish();
    }

    @Nullable
    private String tryDecodeQrText(@NonNull byte[] luminances, int w, int h) {
        int[] pixels = new int[w * h];
        for (int i = 0; i < luminances.length; i++) {
            int v = luminances[i] & 0xFF;
            pixels[i] = 0xFF000000 | (v << 16) | (v << 8) | v;
        }
        RGBLuminanceSource source = new RGBLuminanceSource(w, h, pixels);
        BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));
        try {
            Result result = reader.decode(binaryBitmap);
            String text = result.getText();
            reader.reset();
            return text;
        } catch (NotFoundException e) {
            reader.reset();
            return null;
        }
    }

    @NonNull
    private static byte[] yPlaneToPackedLuminance(@NonNull ImageProxy image) {
        ImageProxy.PlaneProxy plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int rowStride = plane.getRowStride();
        int pixelStride = plane.getPixelStride();
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] data = new byte[width * height];
        int offset = 0;
        if (pixelStride == 1 && rowStride == width) {
            int total = width * height;
            buffer.rewind();
            int len = Math.min(total, buffer.remaining());
            buffer.get(data, 0, len);
            return data;
        }
        for (int y = 0; y < height; y++) {
            int rowStart = y * rowStride;
            for (int x = 0; x < width; x++) {
                data[offset++] = buffer.get(rowStart + x * pixelStride);
            }
        }
        return data;
    }
}
