package com.example.shopeehunter;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.Locale;

public class HunterAccessibilityService extends AccessibilityService {
    private long lastAttempt = 0;
    private long scanWindowEnd = 0L;
    private long scheduledStartAt = 0L;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // This timer runs in the AccessibilityService, so it continues while Shopee is foreground.
    private final Runnable eventStartRunnable = new Runnable() {
        @Override public void run() {
            SharedPreferences prefs = getSharedPreferences("hunter", MODE_PRIVATE);
            long target = prefs.getLong("start_at", 0L);
            if (!prefs.getBoolean("armed", false) || target <= 0L) {
                scheduledStartAt = 0L;
                return;
            }
            if (target != scheduledStartAt) {
                scheduleEventStart(prefs, target);
                return;
            }

            long now = System.currentTimeMillis() + prefs.getLong("server_offset_ms", 0L);
            long remaining = target - now;
            if (remaining > 0L) {
                handler.postDelayed(this, Math.min(remaining, 1000L));
                return;
            }

            AccessibilityNodeInfo root = getRootInActiveWindow();
            boolean shopeeIsForeground = root != null &&
                "com.shopee.id".contentEquals(root.getPackageName());
            if (root != null) root.recycle();

            // If Shopee is not currently visible, wait for its next accessibility event.
            // That event will immediately retry because the target time has already arrived.
            if (!shopeeIsForeground) {
                scheduledStartAt = 0L;
                return;
            }
            startRefreshOnce(prefs, target);
        }
    };

    private final Runnable fastScanLoop = new Runnable() {
        @Override public void run() {
            SharedPreferences prefs = getSharedPreferences("hunter", MODE_PRIVATE);
            if (!prefs.getBoolean("armed", false)) return;
            if (SystemClock.elapsedRealtime() > scanWindowEnd) return;
            scanAndClickTarget(prefs);
            if (prefs.getBoolean("armed", false)) handler.postDelayed(this, 150);
        }
    };

    private void scheduleEventStart(SharedPreferences prefs, long target) {
        if (scheduledStartAt == target) return;
        handler.removeCallbacks(eventStartRunnable);
        scheduledStartAt = target;
        long now = System.currentTimeMillis() + prefs.getLong("server_offset_ms", 0L);
        handler.postDelayed(eventStartRunnable, Math.max(0L, target - now));
    }

    private void startRefreshOnce(SharedPreferences prefs, long target) {
        if (!prefs.getBoolean("armed", false)) return;
        if (prefs.getLong("start_at", 0L) != target) return;
        if (prefs.getBoolean("refresh_in_progress", false)) return;
        if (prefs.getLong("refresh_for_start_at", 0L) == target) return;

        scheduledStartAt = 0L;
        handler.removeCallbacks(eventStartRunnable);
        // One pull-to-refresh attempt only for this countdown.
        prefs.edit().putLong("refresh_for_start_at", target)
            .putBoolean("refresh_in_progress", true)
            .putInt("scan_attempts", 0)
            .putLong("last_click_latency_ms", -1L).apply();
        performSingleRefresh();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        if (!"com.shopee.id".contentEquals(event.getPackageName())) return;

        SharedPreferences prefs = getSharedPreferences("hunter", MODE_PRIVATE);
        if (!prefs.getBoolean("armed", false)) return;
        long startAt = prefs.getLong("start_at", 0L);
        if (startAt <= 0L) return;
        if (prefs.getBoolean("refresh_in_progress", false)) return;

        long refreshedFor = prefs.getLong("refresh_for_start_at", 0L);
        if (refreshedFor == startAt) {
            scanAndClickTarget(prefs);
            return;
        }

        long now = System.currentTimeMillis() + prefs.getLong("server_offset_ms", 0L);
        if (now < startAt) {
            // Schedule the exact event time instead of waiting for another accessibility event.
            scheduleEventStart(prefs, startAt);
            return;
        }

        // If the event is already due when Shopee reports an accessibility event, start now.
        startRefreshOnce(prefs, startAt);
    }

    private void performSingleRefresh() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float x = metrics.widthPixels / 2f;
        float startY = metrics.heightPixels * 0.20f;
        float endY = metrics.heightPixels * 0.62f;

        Path path = new Path();
        path.moveTo(x, startY);
        path.lineTo(x, endY);
        GestureDescription gesture = new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(path, 0, 420))
            .build();

        boolean dispatched = dispatchGesture(gesture, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription description) {
                finishRefreshGesture();
            }
            @Override public void onCancelled(GestureDescription description) {
                finishRefreshGesture();
            }
        }, null);

        if (!dispatched) finishRefreshGesture();
    }

    private void finishRefreshGesture() {
        SharedPreferences prefs = getSharedPreferences("hunter", MODE_PRIVATE);
        prefs.edit().putBoolean("refresh_in_progress", false)
            .putLong("last_refresh_completed_at", System.currentTimeMillis()).apply();
        // Start a short, repeated scan window after one refresh; never refresh again.
        scanWindowEnd = SystemClock.elapsedRealtime() + 5000L;
        handler.removeCallbacks(fastScanLoop);
        handler.postDelayed(fastScanLoop, 250L);
    }

    private void scanAndClickTarget(SharedPreferences prefs) {
        if (!prefs.getBoolean("armed", false)) return;
        if (prefs.getBoolean("refresh_in_progress", false)) return;
        if (SystemClock.elapsedRealtime() - lastAttempt < 120) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        lastAttempt = SystemClock.elapsedRealtime();
        int attempts = prefs.getInt("scan_attempts", 0) + 1;
        prefs.edit().putInt("scan_attempts", attempts).apply();
        String selectedTarget = prefs.getString("target_button", "Beli Sekarang");
        try {
            if (clickSelectedTargetOnce(root, selectedTarget)) {
                long clickedAt = System.currentTimeMillis();
                long refreshedAt = prefs.getLong("last_refresh_completed_at", 0L);
                long latency = refreshedAt > 0L ? clickedAt - refreshedAt : -1L;
                prefs.edit().putLong("last_click_latency_ms", latency)
                    .putInt("last_successful_scan_attempts", attempts)
                    .putLong("last_click_at", clickedAt)
                    .putBoolean("armed", false).putLong("start_at", 0L).apply();
            }
        } finally {
            root.recycle();
        }
    }

    // Scan all nodes exposed in the current Shopee screen; matching is not tied to screen position.
    private boolean clickSelectedTargetOnce(AccessibilityNodeInfo node, String selectedTarget) {
        if (node == null) return false;
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        String label = ((text == null ? "" : text.toString()) + " " +
                (desc == null ? "" : desc.toString()))
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();

        String wanted = selectedTarget == null ? "beli sekarang" :
                selectedTarget.toLowerCase(Locale.ROOT).trim();
        boolean target = matchesButtonLabel(label, wanted);
        if (target && node.isEnabled()) {
            AccessibilityNodeInfo clickable = node;
            boolean clicked = false;
            while (clickable != null) {
                if (clickable.isClickable()) {
                    clicked = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    if (clickable != node) clickable.recycle();
                    break;
                }
                AccessibilityNodeInfo parent = clickable.getParent();
                if (clickable != node) clickable.recycle();
                clickable = parent;
            }
            if (clicked) return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                boolean done = clickSelectedTargetOnce(child, selectedTarget);
                child.recycle();
                if (done) return true;
            }
        }
        return false;
    }

    private boolean matchesButtonLabel(String label, String wanted) {
        if (label.equals(wanted) || label.startsWith(wanted + " ")) return true;
        if (wanted.equals("beli sekarang")) {
            return label.equals("buy now") || label.startsWith("buy now ");
        }
        if (wanted.equals("beli dengan voucher")) {
            return label.equals("buy with voucher") || label.startsWith("buy with voucher ");
        }
        return false;
    }

    @Override public void onInterrupt() { }
}