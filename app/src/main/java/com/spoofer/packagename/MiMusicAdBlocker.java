package com.spoofer.packagename;

import android.os.Handler;
import android.os.Looper;

import java.io.DataOutputStream;
import java.lang.Process;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 小米音乐 - 看广告领金币 自动化
 *
 * 广告倒计时在Hippy JS层运行，Java层时间hook无效
 * 方案: 广告开始后用root权限直接修改系统时间+2分钟
 * JS层Date.now()会读取修改后的系统时间，倒计时立即完成
 */
public class MiMusicAdBlocker {

    private static final String TAG = "[MiMusicAd] ";
    private static boolean adActive = false;
    private static long realStartTime = 0;

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.miui.player".equals(lpparam.packageName)) return;

        XposedBridge.log(TAG + "Loading hooks...");
        hookAdActivity(lpparam);
        hookAdEvents(lpparam);
    }

    /**
     * Hook ADActivity - 广告开始时用root改系统时间
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
                        if (adActive) return; // 防止重复触发
                        adActive = true;
                        realStartTime = System.currentTimeMillis();
                        XposedBridge.log(TAG + "ADActivity.onCreate() - changing system time via root");

                        // 用root权限修改系统时间: 当前时间+2分钟
                        new Thread(() -> {
                            try {
                                long newTime = System.currentTimeMillis() + 2 * 60 * 1000L;
                                // date命令格式: MMDDhhmm[[CC]YY][.ss]
                                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMddHHmmyyyy.ss", java.util.Locale.US);
                                String dateStr = sdf.format(new java.util.Date(newTime));

                                XposedBridge.log(TAG + "Setting system time to: " + dateStr);

                                Process process = Runtime.getRuntime().exec("su");
                                DataOutputStream os = new DataOutputStream(process.getOutputStream());
                                os.writeBytes("date " + dateStr + "\n");
                                os.writeBytes("exit\n");
                                os.flush();
                                int exitCode = process.waitFor();
                                XposedBridge.log(TAG + "date command exit code: " + exitCode);

                                // 3秒后恢复真实时间
                                Thread.sleep(3000);
                                String realDateStr = sdf.format(new java.util.Date(realStartTime + 3000));
                                Process process2 = Runtime.getRuntime().exec("su");
                                DataOutputStream os2 = new DataOutputStream(process2.getOutputStream());
                                os2.writeBytes("date " + realDateStr + "\n");
                                os2.writeBytes("exit\n");
                                os2.flush();
                                process2.waitFor();
                                XposedBridge.log(TAG + "System time restored to: " + realDateStr);

                                adActive = false;
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + "Time change FAILED: " + t.getMessage());
                                adActive = false;
                            }
                        }).start();
                    }
                }
            );

            XposedBridge.log(TAG + "ADActivity hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "ADActivity hook FAILED: " + t.getMessage());
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
                    adActive = false;
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
