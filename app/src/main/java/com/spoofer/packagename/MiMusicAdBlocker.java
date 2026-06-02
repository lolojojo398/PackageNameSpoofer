package com.spoofer.packagename;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 小米音乐 - 看广告领金币 自动化
 *
 * 广告SDK的SystemWindowChecker(bl类)用native层读取时间
 * c()=暂停时间, e()=恢复时间, b()=结果(3=时间不够)
 * 方案: hook e()返回 c()+60秒，让SDK认为用户离开了足够久
 */
public class MiMusicAdBlocker {

    private static final String TAG = "[MiMusicAd] ";
    private static long lastPauseTime = 0;

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.miui.player".equals(lpparam.packageName)) return;

        XposedBridge.log(TAG + "Loading hooks...");

        hookAdShow(lpparam);
        hookAdEvents(lpparam);
        hookSystemWindowChecker(lpparam);
    }

    /**
     * Hook SystemWindowChecker (bl类)
     * onActivityPaused时记录c()的值
     * hook e()返回暂停时间+60秒
     * hook b()确保返回成功
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

            // Hook e() - 返回暂停时间+60秒（而不是真实恢复时间）
            XposedHelpers.findAndHookMethod(checkerClass, "e", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (lastPauseTime > 0) {
                        long fakeResumeTime = lastPauseTime + 60 * 1000L;
                        param.setResult(fakeResumeTime);
                        XposedBridge.log(TAG + "[bl].e() forced to " + fakeResumeTime + " (was " + param.getResult() + ")");
                    }
                }
            });

            // Hook b() - 强制返回0（成功）而不是3（时间不够）
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
     * 记录广告展示时间
     */
    private static void hookAdShow(XC_LoadPackage.LoadPackageParam lpparam) {
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
                        lastPauseTime = 0;
                        XposedBridge.log(TAG + "ADActivity.onCreate() - reset");
                    }
                }
            );

            XposedBridge.log(TAG + "ADActivity.onCreate hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "ADActivity.onCreate hook FAILED: " + t.getMessage());
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
