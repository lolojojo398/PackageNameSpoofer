package com.spoofer.packagename;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLConnection;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class TaskBypassHook {

    private static final String TAG = "[TaskBypass] ";
    private static Class<?> sOkioBufferClass = null;

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.miui.player".equals(lpparam.packageName)) return;

        XposedBridge.log(TAG + "Installing HTTP intercept hooks");

        // Find okio.Buffer class
        try {
            sOkioBufferClass = XposedHelpers.findClass("okio.Buffer", lpparam.classLoader);
            XposedBridge.log(TAG + "Found okio.Buffer");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "okio.Buffer not found: " + t.getMessage());
        }

        // Hook URLConnection.getInputStream()
        try {
            XposedHelpers.findAndHookMethod(
                "java.net.URLConnection", lpparam.classLoader, "getInputStream",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            URLConnection conn = (URLConnection) param.thisObject;
                            String url = conn.getURL().toString();
                            if (!url.contains("TaskActDataReport")) return;
                            InputStream orig = (InputStream) param.getResult();
                            if (orig == null) return;
                            XposedBridge.log(TAG + "URLConnection intercept: " + url);
                            param.setResult(wrapStream(orig, conn.getContentEncoding()));
                        } catch (Throwable t) {
                            XposedBridge.log(TAG + "URLConnection error: " + t.getMessage());
                        }
                    }
                }
            );
            XposedBridge.log(TAG + "URLConnection hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "URLConnection hook failed: " + t.getMessage());
        }

        // Hook OkHttp3/4 proceed() - only afterHookedMethod
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
                            String url = XposedHelpers.callMethod(
                                XposedHelpers.callMethod(request, "url"), "toString").toString();

                            // Log all URLs for debugging
                            XposedBridge.log(TAG + "URL: " + url);

                            // Try to read request body using okio.Buffer
                            boolean isTask = false;
                            if (sOkioBufferClass != null) {
                                try {
                                    Object body = XposedHelpers.callMethod(request, "body");
                                    if (body != null) {
                                        Object buffer = sOkioBufferClass.newInstance();
                                        XposedHelpers.callMethod(body, "writeTo", buffer);
                                        String reqBody = (String) XposedHelpers.callMethod(buffer, "readUtf8");
                                        isTask = reqBody.contains("TaskActDataReport");
                                        if (isTask) {
                                            XposedBridge.log(TAG + "FOUND TaskActDataReport!");
                                        }
                                    }
                                } catch (Throwable t) {
                                    XposedBridge.log(TAG + "Body read error: " + t.getMessage());
                                }
                            }

                            if (!isTask) return;

                            // Modify response
                            Object response = param.getResult();
                            if (response == null) return;

                            Object respBody = XposedHelpers.callMethod(response, "body");
                            if (respBody == null) return;

                            // Read response body
                            String origBody = null;
                            try {
                                origBody = (String) XposedHelpers.callMethod(respBody, "string");
                            } catch (Throwable t1) {
                                try {
                                    byte[] bytes = (byte[]) XposedHelpers.callMethod(respBody, "bytes");
                                    origBody = new String(bytes, "UTF-8");
                                } catch (Throwable t2) {
                                    XposedBridge.log(TAG + "Cannot read response: " + t2.getMessage());
                                    return;
                                }
                            }

                            XposedBridge.log(TAG + "Response: "
                                + origBody.substring(0, Math.min(origBody.length(), 500)));

                            String modified = origBody.replace(
                                "\"bAwardPrize\":false", "\"bAwardPrize\":true");

                            if (!modified.equals(origBody)) {
                                XposedBridge.log(TAG + "Modified bAwardPrize -> true");
                            }

                            Class<?> respBodyClass = XposedHelpers.findClass(
                                "okhttp3.ResponseBody", lpparam.classLoader);
                            Object mediaType = XposedHelpers.callMethod(respBody, "contentType");
                            Object newBody = XposedHelpers.callStaticMethod(
                                respBodyClass, "create", modified, mediaType);
                            Object newBuilder = XposedHelpers.callMethod(response, "newBuilder");
                            XposedHelpers.callMethod(newBuilder, "body", newBody);
                            Object newResponse = XposedHelpers.callMethod(newBuilder, "build");
                            param.setResult(newResponse);

                        } catch (Throwable t) {
                            XposedBridge.log(TAG + "Hook error: " + t.getMessage());
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
        InputStream in = isGzip ? new java.util.zip.GZIPInputStream(orig) : orig;

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
            java.util.zip.GZIPOutputStream gz = new java.util.zip.GZIPOutputStream(gzBuf);
            gz.write(outBytes);
            gz.finish();
            outBytes = gzBuf.toByteArray();
        }
        return new ByteArrayInputStream(outBytes);
    }
}
