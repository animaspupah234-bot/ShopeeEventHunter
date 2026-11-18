package com.example.shopeehunter;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.os.Handler;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private TextView status, countdown;
    private final Handler handler = new Handler();
    private final Calendar eventTime = Calendar.getInstance();
    private final SimpleDateFormat fmt = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", new Locale("id","ID"));
    private static final String[] TARGETS = {"Beli Sekarang", "Beli dengan Voucher"};

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            long target = prefs.getLong("start_at", 0L);
            boolean armed = prefs.getBoolean("armed", false);
            if (armed && target > 0) {
                long left = target - System.currentTimeMillis();
                if (left > 0) {
                    long sec = left / 1000;
                    countdown.setText(String.format(Locale.getDefault(),
                        "Hitung mundur: %02d:%02d:%02d", sec/3600, (sec%3600)/60, sec%60));
                } else {
                    countdown.setText("Waktu event tiba — menunggu tombol target yang dipilih terlihat.");
                }
            } else {
                countdown.setText("Belum ada hitung mundur aktif.");
            }
            refreshStatus();
            handler.postDelayed(this, 500);
        }
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("hunter", MODE_PRIVATE);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(28, 36, 28, 24);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("Shopee Event Hunter 0.5");
        title.setTextSize(23);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        TextView warning = new TextView(this);
        warning.setText("\nMODE TESTING — SATU KALI KLIK\n\nPilih tombol target secara manual, lalu atur waktu event. Pada waktunya, aplikasi hanya mencoba satu kali menekan tombol target yang dipilih di Shopee. Setelah berhasil, otomatis STOP.\n\nTidak melakukan checkout lanjutan, Buat Pesanan, pembayaran, atau PIN. Tombol Beli dengan Voucher dapat membawa ke halaman pembelian. Gunakan produk uji yang tidak akan dibeli.");
        warning.setTextSize(14);
        layout.addView(warning);

        status = new TextView(this);
        status.setTextSize(16);
        status.setPadding(0, 18, 0, 8);
        layout.addView(status);

        countdown = new TextView(this);
        countdown.setTextSize(18);
        countdown.setPadding(0, 8, 0, 10);
        layout.addView(countdown);

        TextView targetLabel = new TextView(this);
        targetLabel.setText("Pilih tombol yang akan dideteksi:");
        targetLabel.setTextSize(16);
        targetLabel.setPadding(0, 8, 0, 4);
        layout.addView(targetLabel);

        Spinner targetSpinner = new Spinner(this);
        ArrayAdapter<String> targetAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, TARGETS);
        targetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        targetSpinner.setAdapter(targetAdapter);
        String savedTarget = prefs.getString("target_button", TARGETS[0]);
        targetSpinner.setSelection(savedTarget.equals(TARGETS[1]) ? 1 : 0);
        targetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                prefs.edit().putString("target_button", TARGETS[position]).apply();
                refreshStatus();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        layout.addView(targetSpinner);

        Button choose = new Button(this);
        choose.setText("PILIH WAKTU EVENT");
        choose.setOnClickListener(v -> chooseDateTime());
        layout.addView(choose);

        Button start = new Button(this);
        start.setText("MULAI COUNTDOWN");
        start.setOnClickListener(v -> {
            long target = eventTime.getTimeInMillis();
            if (target <= System.currentTimeMillis()) {
                countdown.setText("Pilih waktu di masa depan terlebih dahulu.");
                return;
            }
            prefs.edit().putLong("start_at", target).putBoolean("armed", true).apply();
            refreshStatus();
        });
        layout.addView(start);

        Button stop = new Button(this);
        stop.setText("MATIKAN / STOP");
        stop.setOnClickListener(v -> {
            prefs.edit().putBoolean("armed", false).putLong("start_at", 0L).apply();
            refreshStatus();
        });
        layout.addView(stop);

        Button accessibility = new Button(this);
        accessibility.setText("PENGATURAN AKSESIBILITAS");
        accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        layout.addView(accessibility);

        Button shopee = new Button(this);
        shopee.setText("BUKA SHOPEE");
        shopee.setOnClickListener(v -> {
            Intent i = getPackageManager().getLaunchIntentForPackage("com.shopee.id");
            if (i != null) startActivity(i);
            else status.setText("Shopee tidak ditemukan. Instal aplikasi Shopee resmi dahulu.");
        });
        layout.addView(shopee);

        setContentView(layout);
        refreshStatus();
    }

    private void chooseDateTime() {
        Calendar now = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, day) -> {
            eventTime.set(year, month, day);
            new TimePickerDialog(this, (timeView, hour, minute) -> {
                eventTime.set(Calendar.HOUR_OF_DAY, hour);
                eventTime.set(Calendar.MINUTE, minute);
                eventTime.set(Calendar.SECOND, 0);
                eventTime.set(Calendar.MILLISECOND, 0);
                countdown.setText("Waktu event dipilih: " + fmt.format(eventTime.getTime()));
            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show();
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void refreshStatus() {
        if (status != null && prefs != null) {
            String target = prefs.getString("target_button", TARGETS[0]);
            status.setText(prefs.getBoolean("armed", false)
                ? "Status: COUNTDOWN AKTIF — target: " + target + " (maksimal satu klik)."
                : "Status: NONAKTIF — target dipilih: " + target + ".");
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (prefs != null) {
            refreshStatus();
            handler.removeCallbacks(ticker);
            handler.post(ticker);
        }
    }

    @Override protected void onPause() {
        handler.removeCallbacks(ticker);
        super.onPause();
    }
}
