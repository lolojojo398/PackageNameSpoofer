package com.spoofer.packagename;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import java.io.DataOutputStream;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class TaskBypassHook {

    private static final String TAG = "[TaskBypass] ";
    private static final long BG_THRESHOLD_NS = 3_000_000_000L;
    private static final int OFFSET_SECONDS = 180;
    private static final int RESTORE_DELAY_MS = 60000;

    private static volatile long sLastPauseNano = 0;
    private static volatile boolean sProcessing = false;
    private static String sSuPath = null;

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.miui.player".equals(lpparam.packageName)) return;

        XposedBridge.log(TAG + "Installing hooks (root mode)");
        findSu();

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
                        if (sProcessing || sSuPath == null) return;
                        if (sLastPauseNano == 0) return;
                        long bg = System.nanoTime() - sLastPauseNano;
                        if (bg < BG_THRESHOLD_NS) return;

                        sProcessing = true;
                        XposedBridge.log(TAG + "Background " + (bg / 1_000_000)
                            + "ms -> changing time via " + sSuPath);

                        new Thread(() -> {
                            try {
                                // 关闭自动时间同步
                                runSu("settings put global auto_time 0");
                                Thread.sleep(200);

                                // 往后拨3分钟
                                long newSec = System.currentTimeMillis() / 1000 + OFFSET_SECONDS;
                                runSu("date -s @" + newSec);

                                XposedBridge.log(TAG + "Time set +" + OFFSET_SECONDS + "s");

                                // 60秒后恢复
                                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                    try {
                                        runSu("settings put global auto_time 1");
                                        XposedBridge.log(TAG + "Time sync restored");
                                    } catch (Throwable t) {
                                        XposedBridge.log(TAG + "Restore failed: " + t.getMessage());
                                    }
                                    sProcessing = false;
                                }, RESTORE_DELAY_MS);

                            } catch (Throwable t) {
                                XposedBridge.log(TAG + "Failed: " + t.getMessage());
                                sProcessing = false;
                            }
                        }).start();
                    }
                }
            );

            XposedBridge.log(TAG + "Lifecycle hooked");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Hook failed: " + t.getMessage());
        }

        XposedBridge.log(TAG + "All hooks installed");
    }

    private static void findSu() {
        String[] paths = {"/system/bin/su", "/sbin/su", "/system/xbin/su", "/su/bin/su",
            "/data/adb/magisk/su", "/data/adb/ksu/bin/su"};
        for (String p : paths) {
            if (new File(p).canExecute()) {
                sSuPath = p;
                XposedBridge.log(TAG + "Found su at: " + p);
                return;
            }
        }
        // fallback
        sSuPath = "su";
        XposedBridge.log(TAG + "su not found in common paths, using 'su'");
    }

    private static void runSu(String command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(sSuPath);
        Map<String, String> env = pb.environment();
        env.put("PATH", "/system/bin:/system/xbin:/sbin:/data/adb/magisk");
        env.put("HOME", "/data/local/tmp");

        Process process = pb.start();
        DataOutputStream os = new DataOutputStream(process.getOutputStream());
        os.writeBytes(command + "\n");
        os.writeBytes("exit\n");
        os.flush();

        int exit = process.waitFor();
        if (exit != 0) {
            // 读取错误信息
            byte[] err = new byte[1024];
            int len = process.getErrorStream().read(err);
            String errMsg = len > 0 ? new String(err, 0, len) : "exit code " + exit;
            XposedBridge.log(TAG + "su error: " + errMsg);
        }
    }
}
