package com.spoofer.packagename;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 小米音乐 - 看广告领金币 自动化
 *
 * 广告倒计时在Hippy JS层运行，直接读取系统时间
 * 本模块负责检测广告启动并输出日志
 * 配合PC端脚本(ad_timer_skip.sh)通过adb+root改系统时间
 */
public class MiMusicAdBlocker {

    private static final String TAG = "[MiMusicAd] ";

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.miui.player".equals(lpparam.packageName)) return;

        XposedBridge.log(TAG + "Loading hooks...");
        hookAdActivity(lpparam);
        hookAdEvents(lpparam);
    }

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
                        XposedBridge.log(TAG + "ADActivity.onCreate() - ad detected");
                    }
                }
            );

            XposedBridge.log(TAG + "ADActivity hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "ADActivity hook FAILED: " + t.getMessage());
        }
    }

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
