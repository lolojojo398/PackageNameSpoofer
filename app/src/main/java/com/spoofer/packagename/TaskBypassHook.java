package com.spoofer.packagename;

import android.os.SystemClock;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * QQ音乐次日任务时间校验绕过
 *
 * 原理: App在用户从外部页面返回时，检查离开时长是否满足要求。
 *       通过 hook System.currentTimeMillis() 和 SystemClock 相关方法，
 *       在任务相关调用路径中注入时间偏移，使App认为已过足够时间。
 */
public class TaskBypassHook {

    private static final String TAG = "[TaskBypass] ";
    private static final long OFFSET_MS = 180000; // 3分钟偏移

    // 任务相关关键字 - 调用栈中包含这些则注入偏移
    private static final String[] TASK_KEYWORDS = {
        "Task", "ActiveCenter", "ActTask", "Award", "Prize",
        "TaskModule", "TaskActData", "LiteTask", "CoinCenter",
        "TaskSvr", "ActCenter"
    };

    // 广告相关关键字 - 调用栈中包含这些则不偏移（避免影响广告计费）
    private static final String[] AD_EXCLUDE_KEYWORDS = {
        "GDT", "gdt", "Ecpm", "ecpm", "Splash", "RewardVideo",
        "AdReport", "AdExpose", "AdClick", "AdTrack"
    };

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.miui.player".equals(lpparam.packageName)) return;

        XposedBridge.log(TAG + "Installing hooks for " + lpparam.packageName);

        hookCurrentTimeMillis(lpparam);
        hookElapsedRealtime(lpparam);
        hookUptimeMillis(lpparam);

        XposedBridge.log(TAG + "All hooks installed");
    }

    private static void hookCurrentTimeMillis(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                "java.lang.System",
                lpparam.classLoader,
                "currentTimeMillis",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (shouldOffset()) {
                            param.setResult(System.currentTimeMillis() + OFFSET_MS);
                        }
                    }
                }
            );
            XposedBridge.log(TAG + "System.currentTimeMillis hooked");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed to hook currentTimeMillis: " + t.getMessage());
        }
    }

    private static void hookElapsedRealtime(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.os.SystemClock",
                lpparam.classLoader,
                "elapsedRealtime",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (shouldOffset()) {
                            param.setResult(SystemClock.elapsedRealtime() + OFFSET_MS);
                        }
                    }
                }
            );
            XposedBridge.log(TAG + "SystemClock.elapsedRealtime hooked");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed to hook elapsedRealtime: " + t.getMessage());
        }
    }

    private static void hookUptimeMillis(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.os.SystemClock",
                lpparam.classLoader,
                "uptimeMillis",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (shouldOffset()) {
                            param.setResult(SystemClock.uptimeMillis() + OFFSET_MS);
                        }
                    }
                }
            );
            XposedBridge.log(TAG + "SystemClock.uptimeMillis hooked");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed to hook uptimeMillis: " + t.getMessage());
        }
    }

    /**
     * 通过调用栈判断是否需要注入时间偏移
     * 策略: 包含任务关键字 且 不包含广告关键字 → 偏移
     */
    private static boolean shouldOffset() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        boolean hasTaskKeyword = false;

        for (StackTraceElement element : stack) {
            String className = element.getClassName();

            // 广告相关调用，直接排除
            for (String ad : AD_EXCLUDE_KEYWORDS) {
                if (className.contains(ad)) {
                    return false;
                }
            }

            // 任务相关调用
            for (String keyword : TASK_KEYWORDS) {
                if (className.contains(keyword)) {
                    hasTaskKeyword = true;
                    break;
                }
            }
        }

        return hasTaskKeyword;
    }
}
