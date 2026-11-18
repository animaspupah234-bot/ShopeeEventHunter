package com.example.shopeehunter;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private TextView status;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("hunter", MODE_PRIVATE);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(28, 40, 28, 24);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("Shopee Event Hunter");
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        TextView warning = new TextView(this);
        warning.setText("\nMODE TESTING SAJA\n\nJika diaktifkan, aplikasi hanya akan mencoba menekan tombol “Beli Sekarang” satu kali saat tombol itu terlihat di Shopee. Setelah itu otomatis nonaktif.\n\nAplikasi TIDAK menekan Checkout, Buat Pesanan, Bayar, atau memasukkan PIN. Namun, Shopee dapat langsung membuka halaman checkout setelah tombol Beli Sekarang ditekan. Jangan gunakan pada produk yang tidak ingin dibeli.");
        warning.setTextSize(15);
        layout.addView(warning);

        status = new TextView(this);
        status.setTextSize(16);
        status.setPadding(0, 22, 0, 12);
        layout.addView(status);
        refreshStatus();

        Button arm = new Button(this);
        arm.setText("Aktifkan uji satu kali");
        arm.setOnClickListener(v -> {
            prefs.edit().putBoolean("armed", true).apply();
            refreshStatus();
        });
        layout.addView(arm);

        Button stop = new Button(this);
        stop.setText("MATIKAN / STOP");
        stop.setOnClickListener(v -> {
            prefs.edit().putBoolean("armed", false).apply();
            refreshStatus();
        });
        layout.addView(stop);

        Button accessibility = new Button(this);
        accessibility.setText("Pengaturan Aksesibilitas");
        accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        layout.addView(accessibility);

        Button shopee = new Button(this);
        shopee.setText("Buka Shopee");
        shopee.setOnClickListener(v -> {
            Intent i = getPackageManager().getLaunchIntentForPackage("com.shopee.id");
            if (i != null) startActivity(i);
            else status.setText("Shopee tidak ditemukan. Instal aplikasi Shopee resmi dahulu.");
        });
        layout.addView(shopee);

        setContentView(layout);
    }

    private void refreshStatus() {
        if (status != null) {
            status.setText(prefs.getBoolean("armed", false)
                ? "Status: SIAP — satu kali klik, lalu otomatis STOP."
                : "Status: NONAKTIF — aman, tidak ada klik otomatis.");
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (prefs != null) refreshStatus();
    }
}
