package com.spoofer.packagename;

import android.os.Handler;
import android.os.Looper;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 小米音乐 - 看广告领金币 自动化
 *
 * 原理:
 * 广告SDK在用户返回小米音乐时检查时间差
 * 我们在onEnterForeground时设置时间偏移+2分钟
 * 保持5秒后自动重置，确保时间检查期间offset有效
 */
public class MiMusicAdBlocker {

    private static final String TAG = "[MiMusicAd] ";
    private static long fakeTimeOffset = 0;
    private static boolean adShown = false;
    private static Handler resetHandler;

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.miui.player".equals(lpparam.packageName)) return;

        XposedBridge.log(TAG + "Loading hooks...");

        // 初始化Handler用于延迟重置
        try {
            resetHandler = new Handler(Looper.getMainLooper());
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Handler init failed: " + t.getMessage());
        }

        // 核心Hook
        hookOnEnterForeground(lpparam);
        hookAdShow(lpparam);
        hookAdEvents(lpparam);
    }

    /**
     * 核心Hook - onEnterForeground
     *
     * 当用户返回小米音乐时，设置时间偏移+2分钟
     * 保持5秒后自动重置，确保时间检查期间offset有效
     */
    private static void hookOnEnterForeground(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> listenerClass = XposedHelpers.findClass(
                "com.tencent.qqmusiclite.freemode.ad.reward.listener.ActivityRewardListener",
                lpparam.classLoader
            );

            // Hook onEnterForeground
            XposedHelpers.findAndHookMethod(listenerClass, "onEnterForeground", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    XposedBridge.log(TAG + "onEnterForeground() called, adShown=" + adShown);
                    if (adShown) {
                        // 设置时间偏移: +2分钟
                        fakeTimeOffset = 2 * 60 * 1000L;
                        XposedBridge.log(TAG + "Setting fakeTimeOffset=" + fakeTimeOffset);

                        // 5秒后自动重置
                        if (resetHandler != null) {
                            resetHandler.postDelayed(() -> {
                                fakeTimeOffset = 0;
                                adShown = false;
                                XposedBridge.log(TAG + "Offset reset after delay");
                            }, 5000);
                        }
                    }
                }
            });

            // Hook onResume
            XposedHelpers.findAndHookMethod(listenerClass, "onResume", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    XposedBridge.log(TAG + "onResume() called, adShown=" + adShown);
                    if (adShown) {
                        fakeTimeOffset = 2 * 60 * 1000L;
                        XposedBridge.log(TAG + "Setting fakeTimeOffset in onResume");

                        // 5秒后自动重置
                        if (resetHandler != null) {
                            resetHandler.postDelayed(() -> {
                                fakeTimeOffset = 0;
                                adShown = false;
                                XposedBridge.log(TAG + "Offset reset after delay (from onResume)");
                            }, 5000);
                        }
                    }
                }
            });

            XposedBridge.log(TAG + "onEnterForeground hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "onEnterForeground hook FAILED: " + t.getMessage());
        }

        // Hook System.currentTimeMillis() - 只在有偏移时修改
        try {
            XposedHelpers.findAndHookMethod("java.lang.System", lpparam.classLoader,
                "currentTimeMillis",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (fakeTimeOffset > 0) {
                            long original = (long) param.getResult();
                            param.setResult(original + fakeTimeOffset);
                            XposedBridge.log(TAG + "Time modified: " + original + " -> " + (original + fakeTimeOffset));
                        }
                    }
                }
            );

            XposedBridge.log(TAG + "System.currentTimeMillis() hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "System.currentTimeMillis() hook FAILED: " + t.getMessage());
        }

        // Hook SystemClock.elapsedRealtime() - 有些SDK用这个
        try {
            XposedHelpers.findAndHookMethod("android.os.SystemClock", lpparam.classLoader,
                "elapsedRealtime",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (fakeTimeOffset > 0) {
                            long original = (long) param.getResult();
                            param.setResult(original + fakeTimeOffset);
                        }
                    }
                }
            );

            XposedBridge.log(TAG + "SystemClock.elapsedRealtime() hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "SystemClock.elapsedRealtime() hook FAILED: " + t.getMessage());
        }
    }

    /**
     * 记录广告展示时间
     */
    private static void hookAdShow(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook ADActivity.onCreate - 广告Activity创建
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
                        adShown = true;
                        XposedBridge.log(TAG + "ADActivity.onCreate() - adShown=true");
                    }
                }
            );

            XposedBridge.log(TAG + "ADActivity.onCreate hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "ADActivity.onCreate hook FAILED: " + t.getMessage());
        }
    }

    /**
     * 辅助Hook - 记录广告事件
     */
    private static void hookAdEvents(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook onADClose
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

            XposedBridge.log(TAG + "onADClose hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "onADClose hook FAILED: " + t.getMessage());
        }

        // Hook requestReward
        try {
            Class<?> listenerClass = XposedHelpers.findClass(
                "com.tencent.qqmusiclite.freemode.ad.reward.listener.ActivityRewardListener",
                lpparam.classLoader
            );

            XposedHelpers.findAndHookMethod(listenerClass, "requestReward",
                boolean.class, boolean.class, "kotlin.coroutines.Continuation",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        XposedBridge.log(TAG + "requestReward() called! fakeTimeOffset=" + fakeTimeOffset);
                    }
                }
            );

            XposedBridge.log(TAG + "requestReward hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "requestReward hook FAILED: " + t.getMessage());
        }
    }
}
