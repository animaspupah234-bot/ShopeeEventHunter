package com.example.shopeehunter;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.os.SystemClock;
import android.content.SharedPreferences;
import java.util.Locale;

public class HunterAccessibilityService extends AccessibilityService {
    private long lastAttempt = 0;

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        if (!"com.shopee.id".contentEquals(event.getPackageName())) return;

        SharedPreferences prefs = getSharedPreferences("hunter", MODE_PRIVATE);
        if (!prefs.getBoolean("armed", false)) return;
        long startAt = prefs.getLong("start_at", 0L);
        if (startAt <= 0L || System.currentTimeMillis() < startAt) return;
        if (SystemClock.elapsedRealtime() - lastAttempt < 1000) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        lastAttempt = SystemClock.elapsedRealtime();
        try {
            if (clickBuyNowOnce(root)) {
                prefs.edit().putBoolean("armed", false).putLong("start_at", 0L).apply();
            }
        } finally {
            root.recycle();
        }
    }

    private boolean clickBuyNowOnce(AccessibilityNodeInfo node) {
        if (node == null) return false;
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        String label = ((text == null ? "" : text.toString()) + " " +
                (desc == null ? "" : desc.toString()))
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();

        boolean target = label.equals("beli sekarang") || label.equals("buy now");
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
                boolean done = clickBuyNowOnce(child);
                child.recycle();
                if (done) return true;
            }
        }
        return false;
    }

    @Override public void onInterrupt() { }
}
