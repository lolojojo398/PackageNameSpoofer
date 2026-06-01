package com.spoofer.packagename;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.SystemClock;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * QQ音乐次日任务时间校验绕过
 *
 * 策略: 通过 Activity 生命周期检测用户从外部返回的时机，
 *       在返回后的短暂窗口期内对 currentTimeMillis 注入偏移，
 *       让 App 认为已离开足够时间。窗口期过后自动关闭偏移。
 */
public class TaskBypassHook {

    private static final String TAG = "[TaskBypass] ";
    private static final long OFFSET_MS = 180000;  // 3分钟偏移
    private static final long WINDOW_MS = 8000;    // 返回后8秒窗口期

    // 窗口期开关 - 只在用户从外部返回后的短时间内生效
    private static volatile boolean sOffsetActive = false;
    private static volatile long sOffsetDisableTime = 0;

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.miui.player".equals(lpparam.packageName)) return;

        XposedBridge.log(TAG + "Installing hooks");

        // 1. 监听 Activity 生命周期，检测从外部返回
        hookActivityLifecycle(lpparam);

        // 2. hook currentTimeMillis - 只在窗口期内偏移，不做栈检查
        hookTime(lpparam);

        XposedBridge.log(TAG + "All hooks installed");
    }

    /**
     * 监听所有 Activity 的 onResume
     * 当用户从外部 App 返回时触发，开启偏移窗口期
     */
    private static void hookActivityLifecycle(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application",
                lpparam.classLoader,
                "registerActivityLifecycleCallbacks",
                Application.ActivityLifecycleCallbacks.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        // 不拦截，只是 log
                        XposedBridge.log(TAG + "registerActivityLifecycleCallbacks called");
                    }
                }
            );
        } catch (Throwable ignored) {}

        // Hook Activity.onResume 来检测返回
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

                        // 只关注主 Activity / 任务页面
                        if (name.contains("MainActivity") || name.contains("Task")
                            || name.contains("Active") || name.contains("Coin")
                            || name.contains("Lite")) {

                            // 检查 App 是否刚从后台回来
                            // 用一个延迟检查：如果 App 之前在后台，onResume 时开启窗口期
                            sOffsetActive = true;
                            sOffsetDisableTime = SystemClock.uptimeMillis() + WINDOW_MS;

                            XposedBridge.log(TAG + "onResume: " + name
                                + " - offset window opened for " + WINDOW_MS + "ms");
                        }
                    }
                }
            );
            XposedBridge.log(TAG + "Activity.onResume hooked");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed to hook onResume: " + t.getMessage());
        }

        // Hook onPause 记录进入后台的时间
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Activity",
                lpparam.classLoader,
                "onPause",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        // App 进入后台，下次 onResume 时应该开启偏移
                        XposedBridge.log(TAG + "onPause: app going to background");
                    }
                }
            );
        } catch (Throwable ignored) {}
    }

    /**
     * hook System.currentTimeMillis
     * 不查调用栈，只看窗口期标记
     */
    private static void hookTime(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                "java.lang.System",
                lpparam.classLoader,
                "currentTimeMillis",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (sOffsetActive) {
                            // 检查窗口期是否过期
                            if (SystemClock.uptimeMillis() > sOffsetDisableTime) {
                                sOffsetActive = false;
                                XposedBridge.log(TAG + "Offset window closed");
                                return;
                            }
                            param.setResult(System.currentTimeMillis() + OFFSET_MS);
                        }
                    }
                }
            );
            XposedBridge.log(TAG + "System.currentTimeMillis hooked (windowed)");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed to hook currentTimeMillis: " + t.getMessage());
        }
    }
}
