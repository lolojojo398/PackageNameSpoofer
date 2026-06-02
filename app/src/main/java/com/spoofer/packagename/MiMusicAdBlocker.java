package com.spoofer.packagename;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 小米音乐 - 看广告领金币 自动化
 *
 * 原理:
 * 广告SDK通过CountDownTimer控制倒计时(通常5秒)
 * 倒计时结束后调用doAfterCountDown()发送奖励请求
 * Hook CountDownTimer.start() 立即执行回调，跳过等待
 *
 * 流程:
 * 用户点击"看广告领金币"
 *   → 广告SDK加载并播放视频
 *   → 视频播放完成 → onVideoComplete()
 *   → 广告关闭 → onADClose()
 *   → CountDownTimer.start(5000, onFinish)  ← 我们在这里拦截
 *   → [被Hook] 立即执行onFinish回调
 *   → doAfterCountDown()
 *   → requestReward()
 *   → 服务器验证EcpmToken → 发放金币
 */
public class MiMusicAdBlocker {

    private static final String TAG = "[MiMusicAd] ";

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.miui.player".equals(lpparam.packageName)) return;

        XposedBridge.log(TAG + "Loading hooks...");

        // 核心Hook: 跳过CountDownTimer倒计时
        hookCountDownTimer(lpparam);

        // 辅助Hook: 记录广告事件日志
        hookAdEvents(lpparam);
    }

    /**
     * 核心Hook - 跳过CountDownTimer
     *
     * CountDownTimer.start(int totalTime, Function0 onFinish)
     * - totalTime: 倒计时总时长(毫秒)，通常是5000
     * - onFinish: 倒计时结束后的回调函数
     *
     * 我们拦截这个方法，立即调用onFinish，然后return
     * 这样整个倒计时过程被跳过，奖励请求立即发出
     */
    private static void hookCountDownTimer(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> timerClass = XposedHelpers.findClass(
                "com.tencent.qqmusiclite.freemode.util.CountDownTimer",
                lpparam.classLoader
            );

            XposedHelpers.findAndHookMethod(timerClass, "start",
                int.class,
                "kotlin.jvm.functions.Function0",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        int totalTime = (int) param.args[0];
                        Object callback = param.args[1];

                        XposedBridge.log(TAG + "CountDownTimer.start() intercepted, totalTime=" + totalTime);

                        if (callback != null) {
                            try {
                                // 立即执行完成回调
                                XposedHelpers.callMethod(callback, "invoke");
                                XposedBridge.log(TAG + "Reward callback executed immediately");
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + "Callback invoke error: " + t.getMessage());
                            }
                        }

                        // 阻止原始的start()执行(不启动倒计时)
                        param.setResult(null);
                    }
                }
            );

            XposedBridge.log(TAG + "CountDownTimer.start() hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "CountDownTimer hook FAILED: " + t.getMessage());
        }
    }

    /**
     * 辅助Hook - 记录广告事件日志
     * 用于调试，确认Hook是否正常工作
     */
    private static void hookAdEvents(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook onADClose - 广告关闭
        try {
            Class<?> listenerClass = XposedHelpers.findClass(
                "com.tencent.qqmusiclite.freemode.ad.reward.listener.ActivityRewardListener",
                lpparam.classLoader
            );

            XposedHelpers.findAndHookMethod(listenerClass, "onADClose", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    XposedBridge.log(TAG + "onADClose() fired - ad closed, reward flow starting");
                }
            });

            XposedBridge.log(TAG + "onADClose hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "onADClose hook FAILED: " + t.getMessage());
        }

        // Hook onVideoComplete - 视频播放完成
        try {
            Class<?> callbackClass = XposedHelpers.findClass(
                "com.tencentmusic.ad.adapter.mad.reward.MADRewardVideoAdAdapter$c",
                lpparam.classLoader
            );

            XposedHelpers.findAndHookMethod(callbackClass, "onVideoComplete", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    XposedBridge.log(TAG + "onVideoComplete() fired - video ad finished");
                }
            });

            XposedBridge.log(TAG + "onVideoComplete hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "onVideoComplete hook FAILED: " + t.getMessage());
        }
    }
}
