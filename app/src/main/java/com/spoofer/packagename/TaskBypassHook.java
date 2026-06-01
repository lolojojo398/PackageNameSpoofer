package com.spoofer.packagename;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import java.io.DataOutputStream;
import java.io.File;

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
        // Try to actually execute each path rather than relying on canExecute()
        // because SELinux context may report canExecute()=false even when su works
        String[] paths = {"/system/bin/su", "/sbin/su", "/system/xbin/su", "/su/bin/su",
            "/data/adb/magisk/su", "/data/adb/ksu/bin/su"};
        for (String p : paths) {
            if (new File(p).exists()) {
                if (testSu(p)) {
                    sSuPath = p;
                    XposedBridge.log(TAG + "Verified su at: " + p);
                    return;
                }
            }
        }
        // Try "su" in PATH as last resort
        if (testSu("su")) {
            sSuPath = "su";
            XposedBridge.log(TAG + "Using 'su' from PATH");
            return;
        }
        XposedBridge.log(TAG + "WARNING: No working su found! Root features disabled.");
        sSuPath = null;
    }

    private static boolean testSu(String suPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(suPath, "-c", "echo ok");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            java.io.InputStream is = p.getInputStream();
            int exit = p.waitFor();
            String output = "";
            byte[] buf = new byte[64];
            int len = is.read(buf);
            if (len > 0) output = new String(buf, 0, len).trim();
            boolean ok = (exit == 0 && output.contains("ok"));
            XposedBridge.log(TAG + "testSu(" + suPath + ") exit=" + exit + " output=[" + output + "] ok=" + ok);
            return ok;
        } catch (Throwable t) {
            XposedBridge.log(TAG + "testSu(" + suPath + ") error: " + t.getMessage());
            return false;
        }
    }

    private static void runSu(String command) throws Exception {
        // Method 1: ProcessBuilder with su -c
        try {
            ProcessBuilder pb = new ProcessBuilder(sSuPath, "-c", command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            byte[] out = new byte[512];
            int len = process.getInputStream().read(out);
            String output = len > 0 ? new String(out, 0, len).trim() : "";
            int exit = process.waitFor();
            XposedBridge.log(TAG + "runSu exit=" + exit + " output=[" + output + "]");
            if (exit == 0) return;
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Method1 failed: " + t.getMessage());
        }

        // Method 2: Runtime.exec with shell form
        try {
            String[] cmd = {"su", "-c", command};
            Process process = Runtime.getRuntime().exec(cmd);
            byte[] out = new byte[512];
            int len = process.getInputStream().read(out);
            String output = len > 0 ? new String(out, 0, len).trim() : "";
            int exit = process.waitFor();
            XposedBridge.log(TAG + "Runtime.exec exit=" + exit + " output=[" + output + "]");
            if (exit == 0) return;
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Method2 failed: " + t.getMessage());
        }

        // Method 3: Interactive su shell
        try {
            String[] cmd = {sSuPath};
            Process process = Runtime.getRuntime().exec(cmd);
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            os.writeBytes(command + "\n");
            os.writeBytes("exit\n");
            os.flush();
            int exit = process.waitFor();
            XposedBridge.log(TAG + "Interactive su exit=" + exit);
            if (exit == 0) return;
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Method3 failed: " + t.getMessage());
        }

        throw new Exception("All su methods failed for: " + command);
    }
}
