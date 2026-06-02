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
 * 我们在ADActivity创建时设置adShown标志
 * 在onEnterForeground/onResume时设置时间偏移+2分钟
 * 保持offset直到奖励触发或新广告开始
 */
public class MiMusicAdBlocker {

    private static final String TAG = "[MiMusicAd] ";
    private static long fakeTimeOffset = 0;
    private static boolean adShown = false;
    private static boolean rewardTriggered = false;

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.miui.player".equals(lpparam.packageName)) return;

        XposedBridge.log(TAG + "Loading hooks...");

        // 核心Hook
        hookOnEnterForeground(lpparam);
        hookAdShow(lpparam);
        hookAdEvents(lpparam);
        hookSystemTime(lpparam);
    }

    /**
     * Hook System.currentTimeMillis() - 只在有偏移时修改
     */
    private static void hookSystemTime(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod("java.lang.System", lpparam.classLoader,
                "currentTimeMillis",
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

            XposedBridge.log(TAG + "System.currentTimeMillis() hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "System.currentTimeMillis() hook FAILED: " + t.getMessage());
        }

        // Hook SystemClock.elapsedRealtime()
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
     * Hook onEnterForeground/onResume
     * 当用户返回小米音乐时，如果adShown为true，设置时间偏移
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
                    XposedBridge.log(TAG + "onEnterForeground() called, adShown=" + adShown + ", rewardTriggered=" + rewardTriggered);
                    if (adShown && !rewardTriggered) {
                        // 设置时间偏移: +2分钟
                        fakeTimeOffset = 2 * 60 * 1000L;
                        XposedBridge.log(TAG + "Setting fakeTimeOffset=" + fakeTimeOffset);
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    // 不在这里重置offset，让它保持到奖励触发
                    XposedBridge.log(TAG + "onEnterForeground() done, offset=" + fakeTimeOffset);
                }
            });

            // Hook onResume
            XposedHelpers.findAndHookMethod(listenerClass, "onResume", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    XposedBridge.log(TAG + "onResume() called, adShown=" + adShown + ", rewardTriggered=" + rewardTriggered);
                    if (adShown && !rewardTriggered) {
                        fakeTimeOffset = 2 * 60 * 1000L;
                        XposedBridge.log(TAG + "Setting fakeTimeOffset in onResume");
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    XposedBridge.log(TAG + "onResume() done, offset=" + fakeTimeOffset);
                }
            });

            XposedBridge.log(TAG + "onEnterForeground hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "onEnterForeground hook FAILED: " + t.getMessage());
        }
    }

    /**
     * 记录广告展示时间
     * 当广告Activity创建时设置标志
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
                        rewardTriggered = false;
                        fakeTimeOffset = 0;
                        XposedBridge.log(TAG + "ADActivity.onCreate() - adShown=true, rewardTriggered=false");
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
     * 当奖励触发时重置标志
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
                    XposedBridge.log(TAG + "onADClose() fired! adShown=" + adShown + ", offset=" + fakeTimeOffset);
                }
            });

            XposedBridge.log(TAG + "onADClose hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "onADClose hook FAILED: " + t.getMessage());
        }

        // Hook requestReward - 奖励请求触发
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
                        XposedBridge.log(TAG + "requestReward() called! adShown=" + adShown + ", offset=" + fakeTimeOffset);
                        // 奖励触发后，延迟重置
                        rewardTriggered = true;
                        // 延迟2秒后重置所有标志
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            fakeTimeOffset = 0;
                            adShown = false;
                            rewardTriggered = false;
                            XposedBridge.log(TAG + "All flags reset after reward");
                        }, 2000);
                    }
                }
            );

            XposedBridge.log(TAG + "requestReward hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "requestReward hook FAILED: " + t.getMessage());
        }
    }
}
