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
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        if (!"com.shopee.id".contentEquals(event.getPackageName())) return;

        SharedPreferences prefs = getSharedPreferences("hunter", MODE_PRIVATE);
        if (!prefs.getBoolean("armed", false)) return;
        long startAt = prefs.getLong("start_at", 0L);
        long serverOffset = prefs.getLong("server_offset_ms", 0L);
        if (startAt <= 0L || System.currentTimeMillis() + serverOffset < startAt) return;
        if (prefs.getBoolean("refresh_in_progress", false)) return;

        long refreshedFor = prefs.getLong("refresh_for_start_at", 0L);
        if (refreshedFor != startAt) {
            // One pull-to-refresh attempt only for this countdown.
            prefs.edit().putLong("refresh_for_start_at", startAt)
                .putBoolean("refresh_in_progress", true).apply();
            performSingleRefresh();
            return;
        }
        scanAndClickTarget(prefs);
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
        prefs.edit().putBoolean("refresh_in_progress", false).apply();
        // Give Shopee a moment to update the page before looking for the selected button.
        handler.postDelayed(() -> scanAndClickTarget(prefs), 1200);
    }

    private void scanAndClickTarget(SharedPreferences prefs) {
        if (!prefs.getBoolean("armed", false)) return;
        if (prefs.getBoolean("refresh_in_progress", false)) return;
        if (SystemClock.elapsedRealtime() - lastAttempt < 700) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        lastAttempt = SystemClock.elapsedRealtime();
        String selectedTarget = prefs.getString("target_button", "Beli Sekarang");
        try {
            if (clickSelectedTargetOnce(root, selectedTarget)) {
                prefs.edit().putBoolean("armed", false).putLong("start_at", 0L).apply();
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