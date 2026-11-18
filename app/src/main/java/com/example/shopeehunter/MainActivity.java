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
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private TextView status, countdown, timeSyncStatus;
    private static final long SYNC_VALID_MS = 10 * 60 * 1000L;

    private long adjustedNow() {
        return System.currentTimeMillis() + prefs.getLong("server_offset_ms", 0L);
    }

    private boolean hasFreshSync() {
        long t = prefs.getLong("server_synced_at", 0L);
        return t > 0L && System.currentTimeMillis() - t <= SYNC_VALID_MS;
    }
    private final Handler handler = new Handler();
    private final Calendar eventTime = Calendar.getInstance();
    private final SimpleDateFormat fmt = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", new Locale("id","ID"));
    private static final String[] TARGETS = {"Beli Sekarang", "Beli dengan Voucher"};

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            long target = prefs.getLong("start_at", 0L);
            boolean armed = prefs.getBoolean("armed", false);
            if (armed && target > 0) {
                long left = target - adjustedNow();
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
        title.setText("Shopee Event Hunter 0.7");
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

        timeSyncStatus = new TextView(this);
        timeSyncStatus.setTextSize(14);
        timeSyncStatus.setPadding(0, 8, 0, 8);
        layout.addView(timeSyncStatus);

        Button sync = new Button(this);
        sync.setText("SINKRONKAN WAKTU SHOPEE");
        sync.setOnClickListener(v -> synchronizeShopeeTime(sync));
        layout.addView(sync);

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
            if (!hasFreshSync()) {
                countdown.setText("Sinkronkan waktu Shopee dahulu. Hasil sinkronisasi berlaku 10 menit.");
                return;
            }
            long target = eventTime.getTimeInMillis();
            if (target <= adjustedNow()) {
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
        refreshTimeSyncStatus();
    }

    private void synchronizeShopeeTime(Button button) {
        button.setEnabled(false);
        button.setText("SEDANG SINKRON...");
        new Thread(() -> {
            HttpURLConnection connection = null;
            long requestStart = System.currentTimeMillis();
            try {
                connection = (HttpURLConnection) new URL("https://shopee.co.id/").openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(4000);
                connection.setReadTimeout(4000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
                connection.setRequestProperty("Range", "bytes=0-0");
                connection.connect();
                long serverDate = connection.getHeaderFieldDate("Date", -1L);
                long requestEnd = System.currentTimeMillis();
                if (serverDate <= 0L) throw new Exception("Header waktu tidak tersedia");
                long midpoint = requestStart + ((requestEnd - requestStart) / 2L);
                long offset = serverDate - midpoint;
                prefs.edit().putLong("server_offset_ms", offset).putLong("server_synced_at", requestEnd).apply();
                runOnUiThread(() -> {
                    button.setEnabled(true);
                    button.setText("SINKRONKAN WAKTU SHOPEE");
                    refreshTimeSyncStatus();
                    countdown.setText("Sinkronisasi selesai. Pariksa deui jadwal event.");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    button.setEnabled(true);
                    button.setText("SINKRONKAN WAKTU SHOPEE");
                    timeSyncStatus.setText("Sinkronisasi gagal. Pariksa internet, tuluy cobian deui.");
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private void refreshTimeSyncStatus() {
        if (timeSyncStatus == null || prefs == null) return;
        if (!hasFreshSync()) {
            timeSyncStatus.setText("Waktu can disinkronkeun atawa geus kadaluwarsa. Sinkronkeun saméméh countdown.");
            return;
        }
        long seconds = Math.round(prefs.getLong("server_offset_ms", 0L) / 1000.0);
        timeSyncStatus.setText("Waktu respons server Shopee geus sinkron. Bédana jeung jam HP kira-kira " +
            (seconds >= 0 ? "+" : "") + seconds + " detik. Berlaku 10 menit.");
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
            refreshTimeSyncStatus();
            handler.removeCallbacks(ticker);
            handler.post(ticker);
        }
    }

    @Override protected void onPause() {
        handler.removeCallbacks(ticker);
        super.onPause();
    }
}
