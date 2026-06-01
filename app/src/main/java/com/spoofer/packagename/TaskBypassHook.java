package com.spoofer.packagename;

import android.app.Activity;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class TaskBypassHook {

    private static final String TAG = "[TaskBypass] ";
    private static final long OFFSET_MS = 180000;     // 3分钟偏移
    private static final long BG_THRESHOLD_NS = 3_000_000_000L;  // 后台3秒
    private static final long ACTIVE_NS = 60_000_000_000L;        // 偏移持续60秒

    private static volatile boolean sInHook = false;
    private static volatile long sLastPauseNano = 0;
    private static volatile long sOffsetExpireNano = 0;

    private static boolean isOffsetActive() {
        if (sOffsetExpireNano == 0) return false;
        if (System.nanoTime() > sOffsetExpireNano) {
            sOffsetExpireNano = 0;
            XposedBridge.log(TAG + "Offset expired");
            return false;
        }
        return true;
    }

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.miui.player".equals(lpparam.packageName)) return;

        XposedBridge.log(TAG + "Installing hooks");

        // onPause
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
        } catch (Throwable ignored) {}

        // onResume
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Activity", lpparam.classLoader, "onResume",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (sLastPauseNano > 0) {
                            long bg = System.nanoTime() - sLastPauseNano;
                            if (bg > BG_THRESHOLD_NS) {
                                sOffsetExpireNano = System.nanoTime() + ACTIVE_NS;
                                XposedBridge.log(TAG + "Background "
                                    + (bg / 1_000_000) + "ms -> offset ON for 60s");
                            }
                        }
                    }
                }
            );
            XposedBridge.log(TAG + "Lifecycle hooked");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Lifecycle failed: " + t.getMessage());
        }

        // System.currentTimeMillis - 唯一的 hook 点，用 nanoTime 做过期判断
        try {
            XposedHelpers.findAndHookMethod(
                "java.lang.System", lpparam.classLoader, "currentTimeMillis",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (sInHook || !isOffsetActive()) return;
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

        XposedBridge.log(TAG + "All hooks installed");
    }
}
