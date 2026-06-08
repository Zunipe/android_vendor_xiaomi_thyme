package com.zunipe.authonwatch;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import java.security.GeneralSecurityException;
import java.util.List;

public class OtpPagerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_OTP = 1;
    private static final int TYPE_IMPORT = 2;

    public interface ImportActions {
        void onScanQr();

        void onImportFromQr();

        void onImportManually();
    }

    public interface OnDeleteEntryListener {
        void onDeleteEntry(int entryIndex);
    }

    private final List<OtpEntry> entries;
    private final ImportActions importActions;
    private final boolean showScanQrEntry;

    public OtpPagerAdapter(
            List<OtpEntry> entries,
            ImportActions importActions,
            boolean showScanQrEntry) {
        this.entries = entries;
        this.importActions = importActions;
        this.showScanQrEntry = showScanQrEntry;
    }

    @Override
    public int getItemViewType(int position) {
        if (position < entries.size()) {
            return TYPE_OTP;
        }
        return TYPE_IMPORT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_OTP) {
            View view = inflater.inflate(R.layout.item_otp_page, parent, false);
            return new OtpViewHolder(view);
        }
        View view = inflater.inflate(R.layout.item_import_page, parent, false);
        return new ImportViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof OtpViewHolder) {
            ((OtpViewHolder) holder).bind(entries.get(position));
        } else if (holder instanceof ImportViewHolder) {
            ((ImportViewHolder) holder).bind(importActions, showScanQrEntry);
        }
    }

    @Override
    public int getItemCount() {
        return entries.size() + 1;
    }

    static class OtpViewHolder extends RecyclerView.ViewHolder {
        private final TextView title;
        private final TextView code;
        private final TextView timer;

        OtpViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.textTitle);
            code = itemView.findViewById(R.id.textCode);
            timer = itemView.findViewById(R.id.textTimer);
        }

        void bind(OtpEntry entry) {
            title.setText(entry.getName());
            try {
                String rawCode = TotpUtils.generateCurrentCode(entry.getSecret());
                code.setText(formatCode(rawCode));
                timer.setText(itemView.getContext().getString(R.string.seconds_remaining, TotpUtils.getSecondsRemaining()));
            } catch (GeneralSecurityException | IllegalArgumentException e) {
                code.setText("------");
                timer.setText(R.string.invalid_secret);
            }
        }

        private String formatCode(String rawCode) {
            if (rawCode.length() == 6) {
                return rawCode.substring(0, 3) + " " + rawCode.substring(3);
            }
            return rawCode;
        }
    }

    static class ImportViewHolder extends RecyclerView.ViewHolder {
        private final Button buttonScan;
        private final Button buttonQr;
        private final Button buttonManual;

        ImportViewHolder(@NonNull View itemView) {
            super(itemView);
            buttonScan = itemView.findViewById(R.id.buttonScanQr);
            buttonQr = itemView.findViewById(R.id.buttonImportQr);
            buttonManual = itemView.findViewById(R.id.buttonImportManual);
        }

        void bind(ImportActions actions, boolean showScanQr) {
            buttonScan.setVisibility(showScanQr ? View.VISIBLE : View.GONE);
            buttonScan.setOnClickListener(v -> actions.onScanQr());
            buttonQr.setOnClickListener(v -> actions.onImportFromQr());
            buttonManual.setOnClickListener(v -> actions.onImportManually());
        }
    }
}
