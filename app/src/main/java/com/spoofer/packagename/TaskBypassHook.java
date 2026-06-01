package com.spoofer.packagename;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
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

        // Hook 2: OkHttp3 RealInterceptorChain.proceed()
        // Read request body in BEFORE to detect TaskActDataReport,
        // modify response body in AFTER
        try {
            final Class<?> chainClass = XposedHelpers.findClass(
                "okhttp3.internal.http.RealInterceptorChain", lpparam.classLoader);
            final Class<?> reqBodyClass = XposedHelpers.findClass(
                "okhttp3.RequestBody", lpparam.classLoader);

            XposedHelpers.findAndHookMethod(chainClass, "proceed",
                "okhttp3.Request",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        // Read request body to check for TaskActDataReport
                        try {
                            Object request = param.args[0];
                            Object body = XposedHelpers.callMethod(request, "body");
                            if (body == null) { param.setObjectExtra("isTask", false); return; }

                            // Buffer the body so we can read it
                            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
                            Object bufferedSink = XposedHelpers.callStaticMethod(
                                XposedHelpers.findClass("okio.Okio", null),
                                "buffer",
                                XposedHelpers.callStaticMethod(
                                    XposedHelpers.findClass("okio.Okio", null),
                                    "sink", buf));
                            XposedHelpers.callMethod(body, "writeTo", bufferedSink);
                            XposedHelpers.callMethod(bufferedSink, "flush");
                            String reqBody = buf.toString("UTF-8");

                            boolean isTask = reqBody.contains("TaskActDataReport");
                            param.setObjectExtra("isTask", isTask);

                            if (isTask) {
                                XposedBridge.log(TAG + "Detected TaskActDataReport in request body");

                                // Create new request with re-readable body
                                Object mediaType = XposedHelpers.callMethod(body, "contentType");
                                Object newBody = XposedHelpers.callStaticMethod(
                                    reqBodyClass, "create", mediaType, reqBody);
                                Object newReq = XposedHelpers.callMethod(request, "newBuilder");
                                newReq = XposedHelpers.callMethod(newReq, "method",
                                    XposedHelpers.callMethod(request, "method"), newBody);
                                newReq = XposedHelpers.callMethod(newReq, "build");
                                param.args[0] = newReq;
                            }
                        } catch (Throwable t) {
                            param.setObjectExtra("isTask", false);
                            XposedBridge.log(TAG + "Request body read error: " + t.getMessage());
                        }
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Boolean isTask = (Boolean) param.getObjectExtra("isTask");
                        if (isTask == null || !isTask) return;

                        Object response = param.getResult();
                        if (response == null) return;

                        XposedBridge.log(TAG + "OkHttp: Intercepting TaskActDataReport response");

                        try {
                            Object body = XposedHelpers.callMethod(response, "body");
                            if (body == null) return;

                            String origBody = (String) XposedHelpers.callMethod(body, "string");
                            XposedBridge.log(TAG + "Original: "
                                + origBody.substring(0, Math.min(origBody.length(), 500)));

                            String modified = origBody.replace(
                                "\"bAwardPrize\":false", "\"bAwardPrize\":true");

                            if (!modified.equals(origBody)) {
                                XposedBridge.log(TAG + "Modified bAwardPrize -> true");
                            } else {
                                XposedBridge.log(TAG + "bAwardPrize not found or already true");
                            }

                            // Build new response
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
                            XposedBridge.log(TAG + "Response modify error: " + t.getMessage());
                        }
                    }
                }
            );
            XposedBridge.log(TAG + "OkHttp3 proceed() hook OK");
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
