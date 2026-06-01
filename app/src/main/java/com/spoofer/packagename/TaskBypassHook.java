package com.spoofer.packagename;

import android.app.Activity;
import android.os.SystemClock;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class TaskBypassHook {

    private static final String TAG = "[TaskBypass] ";
    private static final long OFFSET_MS = 180000;  // 3分钟偏移
    private static final long WINDOW_MS = 8000;    // 返回后8秒窗口期

    private static volatile boolean sOffsetActive = false;
    private static volatile long sOffsetDisableTime = 0;
    private static volatile boolean sInHook = false; // 防递归

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.miui.player".equals(lpparam.packageName)) return;

        XposedBridge.log(TAG + "Installing hooks");

        // 1. 监听 Activity.onResume 检测从外部返回
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Activity",
                lpparam.classLoader,
                "onResume",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Activity activity = (Activity) param.thisObject;
                        String name = activity.getClass().getName();
                        // 开启偏移窗口期
                        sOffsetActive = true;
                        sOffsetDisableTime = android.os.SystemClock.uptimeMillis() + WINDOW_MS;
                        XposedBridge.log(TAG + "onResume: " + name + " - window opened");
                    }
                }
            );
            XposedBridge.log(TAG + "Activity.onResume hooked");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed to hook onResume: " + t.getMessage());
        }

        // 2. hook currentTimeMillis - 窗口期内偏移，带防递归
        try {
            XposedHelpers.findAndHookMethod(
                "java.lang.System",
                lpparam.classLoader,
                "currentTimeMillis",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (sInHook) return; // 防递归
                        if (!sOffsetActive) return;

                        sInHook = true;
                        try {
                            // 检查窗口期是否过期
                            if (android.os.SystemClock.uptimeMillis() > sOffsetDisableTime) {
                                sOffsetActive = false;
                                XposedBridge.log(TAG + "Window closed");
                                return;
                            }
                            param.setResult(System.currentTimeMillis() + OFFSET_MS);
                        } finally {
                            sInHook = false;
                        }
                    }
                }
            );
            XposedBridge.log(TAG + "System.currentTimeMillis hooked");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed to hook currentTimeMillis: " + t.getMessage());
        }

        XposedBridge.log(TAG + "All hooks installed");
    }
}
