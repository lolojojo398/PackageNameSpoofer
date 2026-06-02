package com.spoofer.packagename;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 小米音乐 - 看广告领金币 自动化
 *
 * 原理:
 * 广告SDK(穿山甲/优量汇)用 System.currentTimeMillis() 判断广告是否播放了足够时长
 * 当用户改手机时间+1分钟后返回，SDK检测到时间已过去1分钟，判定广告播放完成
 *
 * 我们Hook System.currentTimeMillis()，当调用来自广告SDK类时，返回一个未来时间(+2分钟)
 * 这样广告SDK会立即认为广告已经播放完成，无需手动改时间
 */
public class MiMusicAdBlocker {

    private static final String TAG = "[MiMusicAd] ";
    private static final long TIME_OFFSET = 2 * 60 * 1000L; // +2分钟

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
     *
     * 广告SDK用 System.currentTimeMillis() 来判断广告是否播放了足够时长
     * 我们检测调用栈，如果调用来自广告SDK类，就返回+2分钟后的时间
     * 这样SDK会立即认为广告已经播放完成
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
                                // 其他广告相关
                                className.contains("reward") ||
                                className.contains("Reward") ||
                                className.contains("advideo") ||
                                className.contains("AdVideo")) {
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
                                className.contains("reward") ||
                                className.contains("Reward")) {
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

            XposedBridge.log(TAG + "SystemClock.elapsedRealtime() hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "SystemClock.elapsedRealtime() hook FAILED: " + t.getMessage());
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
