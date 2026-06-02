package com.spoofer.packagename;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 小米音乐 - 看广告领金币 自动化
 *
 * 原理:
 * 广告SDK用 System.currentTimeMillis() 判断广告是否播放了足够时长
 * 我们Hook这个方法，对广告SDK类返回+2分钟的未来时间
 */
public class MiMusicAdBlocker {

    private static final String TAG = "[MiMusicAd] ";
    private static final long TIME_OFFSET = 2 * 60 * 1000L; // +2分钟
    private static int callCount = 0;

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.miui.player".equals(lpparam.packageName)) return;

        XposedBridge.log(TAG + "Loading hooks...");

        // 核心Hook: 对广告SDK返回伪造的未来时间
        hookSystemTimeForAdSDK(lpparam);

        // 辅助Hook: 记录广告事件日志
        hookAdEvents(lpparam);
    }

    /**
     * 核心Hook - 对广告SDK返回伪造的未来时间
     */
    private static void hookSystemTimeForAdSDK(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod("java.lang.System", lpparam.classLoader,
                "currentTimeMillis",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        // 检查调用栈，判断是否来自广告SDK
                        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
                        boolean fromAdSdk = false;
                        String matchedClass = "";

                        for (StackTraceElement element : stack) {
                            String className = element.getClassName();
                            // 穿山甲SDK (字节跳动)
                            if (className.startsWith("com.bytedance.") ||
                                className.startsWith("com.ss.android.") ||
                                className.startsWith("com.pangle.") ||
                                // 优量汇SDK (腾讯)
                                className.startsWith("com.qq.e.") ||
                                className.startsWith("com.gdt.") ||
                                // 腾讯广告SDK
                                className.startsWith("com.tencentmusic.ad.") ||
                                className.startsWith("com.tencent.qqmusiclite.ad.") ||
                                className.startsWith("com.tencent.qqmusiclite.freemode.") ||
                                // 广告Activity
                                className.contains("ADActivity") ||
                                className.contains("RewardActivity") ||
                                // 其他广告相关
                                className.contains("reward") ||
                                className.contains("Reward") ||
                                className.contains("advideo") ||
                                className.contains("AdVideo") ||
                                className.contains("countdown") ||
                                className.contains("CountDown")) {
                                fromAdSdk = true;
                                matchedClass = className;
                                break;
                            }
                        }

                        if (fromAdSdk) {
                            long original = (long) param.getResult();
                            param.setResult(original + TIME_OFFSET);
                            callCount++;
                            // 只打印前10次，避免日志太多
                            if (callCount <= 10) {
                                XposedBridge.log(TAG + "Time hooked from " + matchedClass + ", offset=" + TIME_OFFSET);
                            }
                        }
                    }
                }
            );

            XposedBridge.log(TAG + "System.currentTimeMillis() hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "System.currentTimeMillis() hook FAILED: " + t.getMessage());
        }

        // 也Hook SystemClock.elapsedRealtime()，有些SDK用这个
        try {
            XposedHelpers.findAndHookMethod("android.os.SystemClock", lpparam.classLoader,
                "elapsedRealtime",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
                        boolean fromAdSdk = false;
                        String matchedClass = "";

                        for (StackTraceElement element : stack) {
                            String className = element.getClassName();
                            if (className.startsWith("com.bytedance.") ||
                                className.startsWith("com.ss.android.") ||
                                className.startsWith("com.pangle.") ||
                                className.startsWith("com.qq.e.") ||
                                className.startsWith("com.gdt.") ||
                                className.startsWith("com.tencentmusic.ad.") ||
                                className.startsWith("com.tencent.qqmusiclite.ad.") ||
                                className.startsWith("com.tencent.qqmusiclite.freemode.") ||
                                className.contains("ADActivity") ||
                                className.contains("RewardActivity") ||
                                className.contains("reward") ||
                                className.contains("Reward") ||
                                className.contains("countdown") ||
                                className.contains("CountDown")) {
                                fromAdSdk = true;
                                matchedClass = className;
                                break;
                            }
                        }

                        if (fromAdSdk) {
                            long original = (long) param.getResult();
                            param.setResult(original + TIME_OFFSET);
                            if (callCount <= 10) {
                                XposedBridge.log(TAG + "elapsedRealtime hooked from " + matchedClass);
                            }
                        }
                    }
                }
            );

            XposedBridge.log(TAG + "SystemClock.elapsedRealtime() hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "SystemClock.elapsedRealtime() hook FAILED: " + t.getMessage());
        }

        // Hook android.os.SystemClock.uptimeMillis() - 有些SDK用这个
        try {
            XposedHelpers.findAndHookMethod("android.os.SystemClock", lpparam.classLoader,
                "uptimeMillis",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
                        boolean fromAdSdk = false;

                        for (StackTraceElement element : stack) {
                            String className = element.getClassName();
                            if (className.startsWith("com.bytedance.") ||
                                className.startsWith("com.ss.android.") ||
                                className.startsWith("com.pangle.") ||
                                className.startsWith("com.qq.e.") ||
                                className.startsWith("com.gdt.") ||
                                className.startsWith("com.tencentmusic.ad.") ||
                                className.startsWith("com.tencent.qqmusiclite.ad.") ||
                                className.startsWith("com.tencent.qqmusiclite.freemode.") ||
                                className.contains("ADActivity") ||
                                className.contains("reward") ||
                                className.contains("Reward") ||
                                className.contains("countdown") ||
                                className.contains("CountDown")) {
                                fromAdSdk = true;
                                break;
                            }
                        }

                        if (fromAdSdk) {
                            long original = (long) param.getResult();
                            param.setResult(original + TIME_OFFSET);
                        }
                    }
                }
            );

            XposedBridge.log(TAG + "SystemClock.uptimeMillis() hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "SystemClock.uptimeMillis() hook FAILED: " + t.getMessage());
        }
    }

    /**
     * 辅助Hook - 记录广告事件日志
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
                    XposedBridge.log(TAG + "onADClose() fired!");
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
                    XposedBridge.log(TAG + "onVideoComplete() fired!");
                }
            });

            XposedBridge.log(TAG + "onVideoComplete hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "onVideoComplete hook FAILED: " + t.getMessage());
        }

        // Hook 广告SDK的 rewardVerify 方法
        try {
            Class<?> rewardClass = XposedHelpers.findClass(
                "com.bykv.vk.openvk.mediation.bridge.custom.reward.MediationCustomRewardVideoLoader",
                lpparam.classLoader
            );

            XposedHelpers.findAndHookMethod(rewardClass, "callRewardVideoRewardVerify",
                "com.bykv.vk.openvk.mediation.custom.MediationRewardItem",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        XposedBridge.log(TAG + "callRewardVideoRewardVerify() fired!");
                    }
                }
            );

            XposedBridge.log(TAG + "callRewardVideoRewardVerify hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "callRewardVideoRewardVerify hook FAILED: " + t.getMessage());
        }
    }
}
