package com.spoofer.packagename;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 小米音乐 - 看广告领金币 自动化
 *
 * 广告倒计时在Hippy JS层，无法通过Java层时间hook跳过
 * 方案: ADActivity创建后1秒自动finish，强制跳过倒计时
 * 同时hook SystemWindowChecker确保打开app检测通过
 */
public class MiMusicAdBlocker {

    private static final String TAG = "[MiMusicAd] ";
    private static long lastPauseTime = 0;

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.miui.player".equals(lpparam.packageName)) return;

        XposedBridge.log(TAG + "Loading hooks...");

        hookAdActivity(lpparam);
        hookAdEvents(lpparam);
        hookSystemWindowChecker(lpparam);
    }

    /**
     * Hook ADActivity - 创建后自动finish
     */
    private static void hookAdActivity(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> adActivityClass = XposedHelpers.findClass(
                "com.qq.e.tg.ADActivity",
                lpparam.classLoader
            );

            XposedHelpers.findAndHookMethod(adActivityClass, "onCreate",
                android.os.Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Activity activity = (Activity) param.thisObject;
                        lastPauseTime = 0;
                        XposedBridge.log(TAG + "ADActivity.onCreate() - scheduling auto-finish in 1s");

                        // 1秒后自动关闭广告Activity
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            try {
                                XposedBridge.log(TAG + "Auto-finishing ADActivity");
                                activity.finish();
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + "Auto-finish FAILED: " + t.getMessage());
                            }
                        }, 1000);
                    }
                }
            );

            XposedBridge.log(TAG + "ADActivity hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "ADActivity hook FAILED: " + t.getMessage());
        }
    }

    /**
     * Hook SystemWindowChecker (bl类) - 确保打开app检测通过
     */
    private static void hookSystemWindowChecker(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> checkerClass = XposedHelpers.findClass(
                "com.qq.e.comm.plugin.m.bl",
                lpparam.classLoader
            );

            // Hook c() - 记录暂停时间
            XposedHelpers.findAndHookMethod(checkerClass, "c", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    lastPauseTime = (long) param.getResult();
                    XposedBridge.log(TAG + "[bl].c() = " + lastPauseTime);
                }
            });

            // Hook e() - 返回暂停时间+60秒
            XposedHelpers.findAndHookMethod(checkerClass, "e", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (lastPauseTime > 0) {
                        long fakeResumeTime = lastPauseTime + 60 * 1000L;
                        param.setResult(fakeResumeTime);
                        XposedBridge.log(TAG + "[bl].e() forced to " + fakeResumeTime);
                    }
                }
            });

            // Hook b() - 强制返回0（成功）
            XposedHelpers.findAndHookMethod(checkerClass, "b", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    int result = (int) param.getResult();
                    if (result != 0) {
                        param.setResult(0);
                        XposedBridge.log(TAG + "[bl].b() forced 0 (was " + result + ")");
                    }
                }
            });

            XposedBridge.log(TAG + "SystemWindowChecker hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "SystemWindowChecker hook FAILED: " + t.getMessage());
        }
    }

    /**
     * 辅助Hook
     */
    private static void hookAdEvents(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> listenerClass = XposedHelpers.findClass(
                "com.tencent.qqmusiclite.freemode.ad.reward.listener.ActivityRewardListener",
                lpparam.classLoader
            );

            XposedHelpers.findAndHookMethod(listenerClass, "onADClose", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    XposedBridge.log(TAG + "onADClose() fired!");
                }
            });

            XposedHelpers.findAndHookMethod(listenerClass, "requestReward",
                boolean.class, boolean.class, "kotlin.coroutines.Continuation",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        XposedBridge.log(TAG + "requestReward() called!");
                    }
                }
            );

            XposedBridge.log(TAG + "Ad events hooks OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Ad events hooks FAILED: " + t.getMessage());
        }
    }
}
