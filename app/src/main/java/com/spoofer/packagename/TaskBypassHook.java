package com.spoofer.packagename;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class TaskBypassHook {

    private static final String TAG = "[TaskBypass] ";

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.miui.player".equals(lpparam.packageName)) return;

        XposedBridge.log(TAG + "Installing HTTP intercept hooks");

        // Hook 1: URLConnection.getInputStream()
        try {
            XposedHelpers.findAndHookMethod(
                "java.net.URLConnection", lpparam.classLoader, "getInputStream",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        URLConnection conn = (URLConnection) param.thisObject;
                        String url = conn.getURL().toString();
                        if (!url.contains("TaskActDataReport")) return;

                        InputStream orig = (InputStream) param.getResult();
                        if (orig == null) return;

                        XposedBridge.log(TAG + "URLConnection intercept: " + url);
                        param.setResult(wrapStream(orig, conn.getContentEncoding()));
                    }
                }
            );
            XposedBridge.log(TAG + "URLConnection hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "URLConnection hook failed: " + t.getMessage());
        }

        // Hook 2: OkHttp3 - hook response body reading instead of proceed()
        // This avoids the problem of request body being consumed already
        try {
            final Class<?> respBodyClass = XposedHelpers.findClass(
                "okhttp3.ResponseBody", lpparam.classLoader);

            XposedHelpers.findAndHookMethod(respBodyClass, "string",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String body = (String) param.getResult();
                        if (body == null || !body.contains("bAwardPrize")) return;

                        String modified = body.replace(
                            "\"bAwardPrize\":false", "\"bAwardPrize\":true");

                        if (!modified.equals(body)) {
                            XposedBridge.log(TAG + "OkHttp: Modified bAwardPrize -> true");
                            param.setResult(modified);
                        }
                    }
                }
            );
            XposedBridge.log(TAG + "OkHttp3 ResponseBody.string() hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "OkHttp3 hook failed: " + t.getMessage());
        }

        XposedBridge.log(TAG + "All hooks installed");
    }

    private static InputStream wrapStream(InputStream orig, String encoding) throws Exception {
        boolean isGzip = encoding != null && encoding.toLowerCase().contains("gzip");
        InputStream in = isGzip ? new GZIPInputStream(orig) : orig;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) baos.write(buf, 0, n);
        String body = baos.toString("UTF-8");

        XposedBridge.log(TAG + "Original: " + body.substring(0, Math.min(body.length(), 500)));

        String modified = body.replace("\"bAwardPrize\":false", "\"bAwardPrize\":true");
        if (!modified.equals(body)) {
            XposedBridge.log(TAG + "Modified bAwardPrize -> true");
        }

        byte[] outBytes = modified.getBytes("UTF-8");
        if (isGzip) {
            ByteArrayOutputStream gzBuf = new ByteArrayOutputStream();
            GZIPOutputStream gz = new GZIPOutputStream(gzBuf);
            gz.write(outBytes);
            gz.finish();
            outBytes = gzBuf.toByteArray();
        }
        return new ByteArrayInputStream(outBytes);
    }
}
