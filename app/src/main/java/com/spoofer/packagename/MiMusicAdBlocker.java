package com.spoofer.packagename;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 小米音乐 - 看广告领金币 自动化
 *
 * 方案: Hook广告SDK的事件回调，直接触发奖励
 */
public class MiMusicAdBlocker {

    private static final String TAG = "[MiMusicAd] ";

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.miui.player".equals(lpparam.packageName)) return;

        XposedBridge.log(TAG + "Loading hooks...");

        // 方案1: Hook广告SDK的事件回调
        hookAdEventCallback(lpparam);

        // 方案2: Hook小米音乐的奖励监听器
        hookRewardListener(lpparam);

        // 方案3: Hook广告Activity的生命周期
        hookAdActivity(lpparam);
    }

    /**
     * Hook广告SDK的事件回调
     * TangramRewardAD$ADListenerAdapter.onADEvent(ADEvent)
     *
     * 当广告SDK触发奖励事件时，我们拦截并立即处理
     */
    private static void hookAdEventCallback(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> adapterClass = XposedHelpers.findClass(
                "com.qq.e.tg.rewardAD.TangramRewardAD$ADListenerAdapter",
                lpparam.classLoader
            );

            XposedHelpers.findAndHookMethod(adapterClass, "onADEvent",
                "com.qq.e.comm.adevent.ADEvent",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            Object adEvent = param.args[0];
                            // 获取事件类型
                            int eventType = XposedHelpers.getIntField(adEvent, "a");
                            XposedBridge.log(TAG + "ADEvent type=" + eventType);

                            // 事件类型:
                            // 1 = onADLoad
                            // 2 = onADShow
                            // 3 = onADExpose
                            // 4 = onADClick
                            // 5 = onADClose
                            // 6 = onADComplete
                            // 7 = onReward
                            // 8 = onError
                            // 9 = onADPlay
                            // 10 = onADCached

                            if (eventType == 7) { // onReward
                                XposedBridge.log(TAG + "REWARD EVENT detected!");
                            }
                        } catch (Throwable t) {
                            XposedBridge.log(TAG + "ADEvent error: " + t.getMessage());
                        }
                    }
                }
            );

            XposedBridge.log(TAG + "ADEvent hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "ADEvent hook FAILED: " + t.getMessage());
        }
    }

    /**
     * Hook小米音乐的奖励监听器
     * ActivityRewardListener.onADClose()
     *
     * 当广告关闭时触发奖励流程
     */
    private static void hookRewardListener(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> listenerClass = XposedHelpers.findClass(
                "com.tencent.qqmusiclite.freemode.ad.reward.listener.ActivityRewardListener",
                lpparam.classLoader
            );

            // Hook onADClose
            XposedHelpers.findAndHookMethod(listenerClass, "onADClose", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    XposedBridge.log(TAG + "onADClose() fired!");
                }
            });

            // Hook requestReward
            XposedHelpers.findAndHookMethod(listenerClass, "requestReward",
                boolean.class, boolean.class, "kotlin.coroutines.Continuation",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        XposedBridge.log(TAG + "requestReward() called, args=" + param.args[0] + "," + param.args[1]);
                    }
                }
            );

            XposedBridge.log(TAG + "RewardListener hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "RewardListener hook FAILED: " + t.getMessage());
        }
    }

    /**
     * Hook广告Activity
     * com.qq.e.tg.ADActivity
     *
     * 监控广告Activity的生命周期
     */
    private static void hookAdActivity(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> activityClass = XposedHelpers.findClass(
                "com.qq.e.tg.ADActivity",
                lpparam.classLoader
            );

            // Hook onDestroy
            XposedHelpers.findAndHookMethod(activityClass, "onDestroy", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    XposedBridge.log(TAG + "ADActivity.onDestroy()");
                }
            });

            // Hook onPause
            XposedHelpers.findAndHookMethod(activityClass, "onPause", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    XposedBridge.log(TAG + "ADActivity.onPause()");
                }
            });

            XposedBridge.log(TAG + "ADActivity hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "ADActivity hook FAILED: " + t.getMessage());
        }
    }
}
