package com.spoofer.packagename;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 小米音乐 - 看广告领金币 自动化
 *
 * 原理:
 * 广告SDK在用户返回小米音乐时检查时间差
 * 如果距离广告展示已经过去足够时间(通常5-15秒)，则触发奖励
 *
 * 我们Hook onEnterForeground()，在返回时伪造一个未来时间
 * 让广告SDK认为已经过去了足够的时间
 */
public class MiMusicAdBlocker {

    private static final String TAG = "[MiMusicAd] ";
    private static long fakeTimeOffset = 0;
    private static boolean adShown = false;

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.miui.player".equals(lpparam.packageName)) return;

        XposedBridge.log(TAG + "Loading hooks...");

        // 核心Hook: 在返回时伪造时间
        hookOnEnterForeground(lpparam);

        // 辅助Hook: 记录广告展示时间
        hookAdShow(lpparam);

        // 辅助Hook: 记录事件
        hookAdEvents(lpparam);
    }

    /**
     * 核心Hook - onEnterForeground
     *
     * 当用户从外部返回小米音乐时调用
     * 广告SDK会在这里检查时间差
     * 我们在检查之前设置一个时间偏移，让时间差看起来足够大
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
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    // 重置偏移
                    fakeTimeOffset = 0;
                    adShown = false;
                    XposedBridge.log(TAG + "onEnterForeground() done, reset offset");
                }
            });

            // Hook onResume
            XposedHelpers.findAndHookMethod(listenerClass, "onResume", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    XposedBridge.log(TAG + "onResume() called");
                    if (adShown) {
                        fakeTimeOffset = 2 * 60 * 1000L;
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    fakeTimeOffset = 0;
                    adShown = false;
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
                        }
                    }
                }
            );

            XposedBridge.log(TAG + "System.currentTimeMillis() hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "System.currentTimeMillis() hook FAILED: " + t.getMessage());
        }
    }

    /**
     * 记录广告展示时间
     * 当广告展示时设置标志
     */
    private static void hookAdShow(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> listenerClass = XposedHelpers.findClass(
                "com.tencent.qqmusiclite.freemode.ad.reward.listener.ActivityRewardListener",
                lpparam.classLoader
            );

            // Hook loadVideoAd - 广告加载
            XposedHelpers.findAndHookMethod(listenerClass, "loadVideoAd",
                "com.tencent.qqmusiclite.freemode.data.enums.AdConfigID",
                "com.tencent.qqmusiclite.freemode.data.enums.RewardAdType",
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        XposedBridge.log(TAG + "loadVideoAd() called - ad loading");
                    }
                }
            );

            XposedBridge.log(TAG + "loadVideoAd hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "loadVideoAd hook FAILED: " + t.getMessage());
        }

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
                        XposedBridge.log(TAG + "requestReward() called!");
                    }
                }
            );

            XposedBridge.log(TAG + "requestReward hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "requestReward hook FAILED: " + t.getMessage());
        }
    }
}
