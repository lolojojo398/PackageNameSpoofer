package com.spoofer.packagename;

import android.app.Activity;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class TaskBypassHook {

    private static final String TAG = "[TaskBypass] ";
    private static final long OFFSET_MS = 180000;     // 3分钟
    private static final long BG_THRESHOLD_NS = 3_000_000_000L;
    private static final long ACTIVE_MS = 60000;       // 60秒

    private static volatile boolean sInHook = false;
    private static volatile long sLastPauseNano = 0;
    private static volatile boolean sOffsetActive = false;
    private static volatile long sOffsetStartMs = 0;

    private static boolean isActive() {
        if (!sOffsetActive) return false;
        if (System.currentTimeMillis() - sOffsetStartMs > ACTIVE_MS) {
            sOffsetActive = false;
            XposedBridge.log(TAG + "Offset expired");
            return false;
        }
        return true;
    }

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.miui.player".equals(lpparam.packageName)) return;

        XposedBridge.log(TAG + "Installing hooks");

        // --- Lifecycle ---
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Activity", lpparam.classLoader, "onPause",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        sLastPauseNano = System.nanoTime();
                    }
                }
            );
            XposedHelpers.findAndHookMethod(
                "android.app.Activity", lpparam.classLoader, "onResume",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (sLastPauseNano > 0) {
                            long bg = System.nanoTime() - sLastPauseNano;
                            if (bg > BG_THRESHOLD_NS) {
                                sOffsetActive = true;
                                sOffsetStartMs = System.currentTimeMillis();
                                XposedBridge.log(TAG + "Background "
                                    + (bg / 1_000_000) + "ms -> offset ON");
                            }
                        }
                    }
                }
            );
            XposedBridge.log(TAG + "Lifecycle hooked");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Lifecycle failed: " + t.getMessage());
        }

        // --- System.currentTimeMillis ---
        try {
            XposedHelpers.findAndHookMethod(
                "java.lang.System", lpparam.classLoader, "currentTimeMillis",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (sInHook || !isActive()) return;
                        sInHook = true;
                        try {
                            param.setResult(System.currentTimeMillis() + OFFSET_MS);
                        } finally {
                            sInHook = false;
                        }
                    }
                }
            );
            XposedBridge.log(TAG + "currentTimeMillis hooked");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "currentTimeMillis failed: " + t.getMessage());
        }

        // --- SystemClock.uptimeMillis ---
        try {
            XposedHelpers.findAndHookMethod(
                "android.os.SystemClock", lpparam.classLoader, "uptimeMillis",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (sInHook || !isActive()) return;
                        sInHook = true;
                        try {
                            // 调原方法(会被hook再拦截,但sInHook=true会跳过)
                            param.setResult(android.os.SystemClock.uptimeMillis() + OFFSET_MS);
                        } finally {
                            sInHook = false;
                        }
                    }
                }
            );
            XposedBridge.log(TAG + "uptimeMillis hooked");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "uptimeMillis failed: " + t.getMessage());
        }

        // --- SystemClock.elapsedRealtime ---
        try {
            XposedHelpers.findAndHookMethod(
                "android.os.SystemClock", lpparam.classLoader, "elapsedRealtime",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (sInHook || !isActive()) return;
                        sInHook = true;
                        try {
                            param.setResult(android.os.SystemClock.elapsedRealtime() + OFFSET_MS);
                        } finally {
                            sInHook = false;
                        }
                    }
                }
            );
            XposedBridge.log(TAG + "elapsedRealtime hooked");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "elapsedRealtime failed: " + t.getMessage());
        }

        XposedBridge.log(TAG + "All hooks installed");
    }
}
