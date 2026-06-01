package com.spoofer.packagename;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
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

        // Hook 1: URLConnection.getInputStream() (parent of HttpURLConnection)
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

                        XposedBridge.log(TAG + "URLConnection: Intercepting TaskActDataReport");

                        String encoding = conn.getContentEncoding();
                        boolean isGzip = encoding != null
                            && encoding.toLowerCase().contains("gzip");
                        InputStream in = isGzip ? new GZIPInputStream(orig) : orig;

                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        byte[] buf = new byte[4096];
                        int n;
                        while ((n = in.read(buf)) != -1) baos.write(buf, 0, n);
                        String body = baos.toString("UTF-8");

                        XposedBridge.log(TAG + "Original: "
                            + body.substring(0, Math.min(body.length(), 500)));

                        String modified = body.replace(
                            "\"bAwardPrize\":false", "\"bAwardPrize\":true");

                        if (!modified.equals(body)) {
                            XposedBridge.log(TAG + "Modified bAwardPrize -> true");
                        } else {
                            XposedBridge.log(TAG + "bAwardPrize not found or already true");
                        }

                        byte[] outBytes = modified.getBytes("UTF-8");
                        if (isGzip) {
                            ByteArrayOutputStream gzBuf = new ByteArrayOutputStream();
                            GZIPOutputStream gz = new GZIPOutputStream(gzBuf);
                            gz.write(outBytes);
                            gz.finish();
                            outBytes = gzBuf.toByteArray();
                        }

                        param.setResult(new ByteArrayInputStream(outBytes));
                    }
                }
            );
            XposedBridge.log(TAG + "URLConnection hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "URLConnection hook failed: " + t.getMessage());
        }

        // Hook 2: OkHttp3 with debug logging
        try {
            final Class<?> chainClass = XposedHelpers.findClass(
                "okhttp3.internal.http.RealInterceptorChain", lpparam.classLoader);

            XposedHelpers.findAndHookMethod(chainClass, "proceed",
                "okhttp3.Request",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            Object request = param.args[0];
                            Object urlObj = XposedHelpers.callMethod(request, "url");
                            String url = urlObj.toString();

                            // Debug: log all URLs to see what format they are
                            XposedBridge.log(TAG + "OkHttp URL: " + url);

                            if (!url.contains("TaskActDataReport")) return;

                            Object response = param.getResult();
                            if (response == null) return;

                            XposedBridge.log(TAG + "OkHttp: Found TaskActDataReport!");

                            Object body = XposedHelpers.callMethod(response, "body");
                            if (body == null) return;

                            String origBody = (String) XposedHelpers.callMethod(body, "string");
                            XposedBridge.log(TAG + "OkHttp Original: "
                                + origBody.substring(0, Math.min(origBody.length(), 500)));

                            String modified = origBody.replace(
                                "\"bAwardPrize\":false", "\"bAwardPrize\":true");

                            if (!modified.equals(origBody)) {
                                XposedBridge.log(TAG + "OkHttp: Modified bAwardPrize -> true");
                            }

                            Class<?> respBodyClass = XposedHelpers.findClass(
                                "okhttp3.ResponseBody", lpparam.classLoader);
                            Object mediaType = XposedHelpers.callMethod(body, "contentType");
                            Object newBody = XposedHelpers.callStaticMethod(
                                respBodyClass, "create", modified, mediaType);

                            Object newBuilder = XposedHelpers.callMethod(response, "newBuilder");
                            XposedHelpers.callMethod(newBuilder, "body", newBody);
                            Object newResponse = XposedHelpers.callMethod(newBuilder, "build");

                            param.setResult(newResponse);
                        } catch (Throwable t) {
                            XposedBridge.log(TAG + "OkHttp hook error: " + t.getMessage());
                        }
                    }
                }
            );
            XposedBridge.log(TAG + "OkHttp3 hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "OkHttp3 hook failed: " + t.getMessage());
        }

        XposedBridge.log(TAG + "All hooks installed");
    }
}
