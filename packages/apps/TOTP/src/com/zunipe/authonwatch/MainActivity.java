package com.zunipe.authonwatch;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private final Handler tickHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (adapter != null && !isUserSwiping) {
                adapter.notifyDataSetChanged();
            }
            tickHandler.postDelayed(this, 1000L);
        }
    };
    private boolean isUserSwiping = false;

    private final ActivityResultLauncher<String[]> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::handleImagePicked);

    private final ActivityResultLauncher<Intent> scanQrLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                    return;
                }
                String raw = result.getData().getStringExtra(ScanQrActivity.EXTRA_RAW_CONTENT);
                if (raw == null) {
                    return;
                }
                try {
                    OtpEntry entry = QrImportHelper.parseOtpAuth(raw);
                    addEntry(entry);
                    Toast.makeText(this, R.string.import_success, Toast.LENGTH_SHORT).show();
                } catch (IllegalArgumentException e) {
                    String msg = e.getMessage();
                    Toast.makeText(this, msg != null ? msg : e.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
                }
            });

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    scanQrLauncher.launch(new Intent(this, ScanQrActivity.class));
                } else {
                    Toast.makeText(this, R.string.camera_permission_denied, Toast.LENGTH_SHORT).show();
                }
            });

    private ViewPager2 viewPager;
    private OtpPagerAdapter adapter;
    private final List<OtpEntry> entries = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.viewPager), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        viewPager = findViewById(R.id.viewPager);

        attachSwipeToDelete((RecyclerView) viewPager.getChildAt(0));

        entries.addAll(OtpStorage.load(this));
        adapter = new OtpPagerAdapter(entries, new OtpPagerAdapter.ImportActions() {
            @Override
            public void onScanQr() {
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED) {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
                } else {
                    scanQrLauncher.launch(new Intent(MainActivity.this, ScanQrActivity.class));
                }
            }

            @Override
            public void onImportFromQr() {
                imagePickerLauncher.launch(new String[]{"image/*"});
            }

            @Override
            public void onImportManually() {
                showManualImportDialog();
            }
        }, MainApplication.sHasCamera);
        viewPager.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        tickHandler.post(tickRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        tickHandler.removeCallbacks(tickRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private void attachSwipeToDelete(RecyclerView recyclerView) {
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(0,
                ItemTouchHelper.UP) {
            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                Button button = viewHolder.itemView.findViewById(R.id.buttonScanQr);
                if (button != null) {
                    return makeMovementFlags(0, 0);
                }
                return super.getMovementFlags(recyclerView, viewHolder);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                TextView textTitle = viewHolder.itemView.findViewById(R.id.textTitle);
                if (position != RecyclerView.NO_POSITION && textTitle != null) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle(R.string.delete_confirm_title)
                            .setMessage(getString(R.string.delete_confirm_message, textTitle.getText().toString()))
                            .setNegativeButton(R.string.cancel, null)
                            .setPositiveButton(R.string.delete_action, (dialog, which) -> deleteEntry(position))
                            .show();
                }
            }

            @Override
            public void onChildDraw(@NonNull Canvas canvas,
                                    @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder, float dX,
                                    float dY, int actionState, boolean isCurrentlyActive) {
                TextView textCode = viewHolder.itemView.findViewById(R.id.textCode);
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && textCode != null) {
                    textCode.setTranslationY(dY);
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder) {
                TextView textCode = viewHolder.itemView.findViewById(R.id.textCode);
                if (textCode != null) {
                    textCode.setTranslationY(0f);
                }
                super.clearView(recyclerView, viewHolder);
            }

            @Override
            public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    isUserSwiping = true;
                } else if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
                    isUserSwiping = false;
                }
            }
        };

        new ItemTouchHelper(callback).attachToRecyclerView(recyclerView);
    }

    private void handleImagePicked(Uri uri) {
        if (uri == null) {
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // Temporary permission is still valid for this import flow.
        }

        executor.execute(() -> {
            Bitmap bitmap = null;
            try {
                bitmap = decodeBitmap(uri);
                OtpEntry entry = QrImportHelper.decodeEntryFromBitmap(bitmap);
                runOnUiThread(() -> {
                    addEntry(entry);
                    Toast.makeText(this, R.string.import_success, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                String msg = e.getMessage();
                final String errorMsg = (msg == null || msg.isEmpty())
                        ? e.getClass().getSimpleName()
                        : msg;
                Log.e("Main", "QR import failed", e);
                runOnUiThread(() -> Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show());
            } finally {
                if (bitmap != null) {
                    bitmap.recycle();
                }
            }
        });
    }

    private Bitmap decodeBitmap(Uri uri) throws IOException {
        ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), uri);
        return ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
            // QR decoding needs direct pixel access; hardware bitmaps do not support getPixels().
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            decoder.setMutableRequired(false);
            // Wear OS has little RAM; cap size so int[] for ZXing does not OOM.
            int w = info.getSize().getWidth();
            int h = info.getSize().getHeight();
            int max = Math.max(w, h);
            int cap = 1024;
            if (max > cap) {
                int tw = w * cap / max;
                int th = h * cap / max;
                decoder.setTargetSize(Math.max(1, tw), Math.max(1, th));
            }
        });
    }

    private void showManualImportDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_manual_import, null);
        EditText editName = dialogView.findViewById(R.id.editName);
        EditText editSecret = dialogView.findViewById(R.id.editSecret);

        new AlertDialog.Builder(this)
                .setTitle(R.string.manual_import_title)
                .setView(dialogView)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.import_action, (dialog, which) -> {
                    String name = editName.getText().toString().trim();
                    String secret = TotpUtils.normalizeSecret(editSecret.getText().toString());
                    if (TextUtils.isEmpty(name) || TextUtils.isEmpty(secret)) {
                        Toast.makeText(this, R.string.manual_import_invalid, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    addEntry(new OtpEntry(name, secret));
                    Toast.makeText(this, R.string.import_success, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void addEntry(OtpEntry entry) {
        entries.add(entry);
        OtpStorage.save(this, entries);
        adapter.notifyDataSetChanged();
        // Keep import page at far right, jump user to newly imported token page.
        viewPager.setCurrentItem(entries.size() - 1, true);
    }

    private void deleteEntry(int index) {
        if (index < 0 || index >= entries.size()) {
            return;
        }
        entries.remove(index);
        OtpStorage.save(this, entries);
        adapter.notifyDataSetChanged();
        int current = viewPager.getCurrentItem();
        if (entries.isEmpty()) {
            viewPager.setCurrentItem(0, true);
        } else if (current > index) {
            viewPager.setCurrentItem(current - 1, true);
        } else if (current == index && current >= entries.size()) {
            viewPager.setCurrentItem(entries.size() - 1, true);
        }
    }
}
