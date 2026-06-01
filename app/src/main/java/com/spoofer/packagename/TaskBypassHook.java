package com.spoofer.packagename;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import java.io.DataOutputStream;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 通过 root 权限直接修改系统时间来绕过任务时间校验
 * 与用户手动改时间的原理完全一致
 */
public class TaskBypassHook {

    private static final String TAG = "[TaskBypass] ";
    private static final long BG_THRESHOLD_NS = 3_000_000_000L;  // 后台3秒
    private static final int OFFSET_SECONDS = 180;  // 往前拨3分钟
    private static final int RESTORE_DELAY_MS = 60000;  // 60秒后恢复

    private static volatile long sLastPauseNano = 0;
    private static volatile boolean sProcessing = false;

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.miui.player".equals(lpparam.packageName)) return;

        XposedBridge.log(TAG + "Installing hooks (root mode)");

        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Activity", lpparam.classLoader, "onPause",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        sLastPauseNano = System.nanoTime();
                    }
                }
            );

            XposedHelpers.findAndHookMethod(
                "android.app.Activity", lpparam.classLoader, "onResume",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (sProcessing) return;
                        if (sLastPauseNano == 0) return;

                        long bgDuration = System.nanoTime() - sLastPauseNano;
                        if (bgDuration < BG_THRESHOLD_NS) return;

                        sProcessing = true;
                        XposedBridge.log(TAG + "Background " + (bgDuration / 1_000_000)
                            + "ms -> changing system time via root");

                        // 在子线程执行 root 命令
                        new Thread(() -> {
                            try {
                                // 关闭自动时间同步
                                execRoot("settings put global auto_time 0");

                                // 获取当前时间并往后拨
                                long currentSec = System.currentTimeMillis() / 1000;
                                long newSec = currentSec + OFFSET_SECONDS;
                                execRoot("date -s @" + newSec);

                                XposedBridge.log(TAG + "System time set to +" + OFFSET_SECONDS + "s");

                                // 60秒后恢复
                                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                    try {
                                        execRoot("settings put global auto_time 1");
                                        XposedBridge.log(TAG + "Auto time sync restored");
                                    } catch (Throwable t) {
                                        XposedBridge.log(TAG + "Restore failed: " + t.getMessage());
                                    }
                                    sProcessing = false;
                                }, RESTORE_DELAY_MS);

                            } catch (Throwable t) {
                                XposedBridge.log(TAG + "Root time change failed: " + t.getMessage());
                                sProcessing = false;
                            }
                        }, "TaskBypass-RootThread").start();
                    }
                }
            );

            XposedBridge.log(TAG + "Lifecycle hooked (root mode)");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook failed: " + t.getMessage());
        }

        XposedBridge.log(TAG + "All hooks installed");
    }

    private static void execRoot(String command) throws Exception {
        Process process = Runtime.getRuntime().exec("su");
        DataOutputStream os = new DataOutputStream(process.getOutputStream());
        os.writeBytes(command + "\n");
        os.writeBytes("exit\n");
        os.flush();
        process.waitFor();
    }
}
