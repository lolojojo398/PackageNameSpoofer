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
    private static String sOkioBufferClass = null;

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.miui.player".equals(lpparam.packageName)) return;

        XposedBridge.log(TAG + "Installing HTTP intercept hooks");

        // Find Okio class name (may be repackaged)
        findOkioClass(lpparam.classLoader);

        // Hook 1: URLConnection.getInputStream()
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

        // Hook 2: OkHttp3 RealInterceptorChain.proceed()
        try {
            final Class<?> chainClass = XposedHelpers.findClass(
                "okhttp3.internal.http.RealInterceptorChain", lpparam.classLoader);

            XposedHelpers.findAndHookMethod(chainClass, "proceed",
                "okhttp3.Request",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            Object request = param.args[0];
                            Object body = XposedHelpers.callMethod(request, "body");
                            if (body == null) {
                                param.setObjectExtra("isTask", false);
                                return;
                            }

                            String reqBody = readRequestBody(body, lpparam.classLoader);
                            if (reqBody == null) {
                                param.setObjectExtra("isTask", false);
                                return;
                            }

                            boolean isTask = reqBody.contains("TaskActDataReport");
                            param.setObjectExtra("isTask", isTask);

                            if (isTask) {
                                XposedBridge.log(TAG + "Detected TaskActDataReport!");

                                Object mediaType = XposedHelpers.callMethod(body, "contentType");
                                Class<?> reqBodyClass = XposedHelpers.findClass(
                                    "okhttp3.RequestBody", lpparam.classLoader);
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
                            XposedBridge.log(TAG + "Body read error: " + t.getMessage());
                        }
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Boolean isTask = (Boolean) param.getObjectExtra("isTask");
                        if (isTask == null || !isTask) return;

                        Object response = param.getResult();
                        if (response == null) return;

                        XposedBridge.log(TAG + "Intercepting response");

                        try {
                            Object body = XposedHelpers.callMethod(response, "body");
                            if (body == null) return;

                            String origBody = readResponseBody(body);
                            if (origBody == null) {
                                XposedBridge.log(TAG + "Could not read response body");
                                return;
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
                            Object mediaType = XposedHelpers.callMethod(body, "contentType");
                            Object newBody = XposedHelpers.callStaticMethod(
                                respBodyClass, "create", modified, mediaType);

                            Object newBuilder = XposedHelpers.callMethod(response, "newBuilder");
                            XposedHelpers.callMethod(newBuilder, "body", newBody);
                            Object newResponse = XposedHelpers.callMethod(newBuilder, "build");

                            param.setResult(newResponse);
                        } catch (Throwable t) {
                            XposedBridge.log(TAG + "Response error: " + t.getMessage());
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

    private static void findOkioClass(ClassLoader cl) {
        // Try common Okio package names (may be repackaged)
        String[] candidates = {
            "okio.Buffer",
            "okio.Okio",
            "com.tencent.mars.okio.Buffer",
            "com.tencent.mars.okio.Okio",
            "com.squareup.okio.Buffer",
            "com.squareup.okio.Okio"
        };
        for (String name : candidates) {
            try {
                Class<?> c = cl.loadClass(name);
                sOkioBufferClass = name.endsWith("Buffer") ? name : null;
                XposedBridge.log(TAG + "Found Okio class: " + name);
                return;
            } catch (ClassNotFoundException ignored) {}
        }

        // Try to find by searching classloader for Buffer class
        try {
            // okhttp3.internal.io.FileSystem uses Okio - trace its dependencies
            Class<?> fsClass = cl.loadClass("okhttp3.internal.io.FileSystem");
            XposedBridge.log(TAG + "Found FileSystem: " + fsClass.getName());
        } catch (Throwable ignored) {}

        XposedBridge.log(TAG + "Okio class not found, will try direct writeTo");
    }

    private static String readRequestBody(Object body, ClassLoader cl) {
        // Method 1: Try writeTo with ByteArrayOutputStream (if writeTo accepts OutputStream)
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            XposedHelpers.callMethod(body, "writeTo", buf);
            return buf.toString("UTF-8");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "writeTo(OutputStream) failed: " + t.getMessage());
        }

        // Method 2: Try writeTo with Okio Buffer if we found the class
        if (sOkioBufferClass != null) {
            try {
                Class<?> bufferClass = XposedHelpers.findClass(sOkioBufferClass, cl);
                Object buffer = bufferClass.newInstance();
                // Find the BufferedSink wrapper - try okio.Okio.buffer(buffer)
                String okioClassName = sOkioBufferClass.replace("Buffer", "Okio");
                try {
                    Class<?> okioClass = XposedHelpers.findClass(okioClassName, cl);
                    Object sink = XposedHelpers.callStaticMethod(okioClass, "buffer", buffer);
                    XposedHelpers.callMethod(body, "writeTo", sink);
                    return (String) XposedHelpers.callMethod(buffer, "readUtf8");
                } catch (Throwable t2) {
                    // Try direct writeTo with buffer
                    XposedHelpers.callMethod(body, "writeTo", buffer);
                    return (String) XposedHelpers.callMethod(buffer, "readUtf8");
                }
            } catch (Throwable t) {
                XposedBridge.log(TAG + "Okio Buffer failed: " + t.getMessage());
            }
        }

        // Method 3: Try to read via contentLength + reflection
        try {
            long len = (long) XposedHelpers.callMethod(body, "contentLength");
            XposedBridge.log(TAG + "Body contentLength: " + len);
        } catch (Throwable ignored) {}

        return null;
    }

    private static String readResponseBody(Object body) {
        // Try string() first
        try {
            return (String) XposedHelpers.callMethod(body, "string");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "body.string() failed: " + t.getMessage());
        }

        // Try bytes()
        try {
            byte[] bytes = (byte[]) XposedHelpers.callMethod(body, "bytes");
            return new String(bytes, "UTF-8");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "body.bytes() failed: " + t.getMessage());
        }

        // Try source() + readUtf8()
        if (sOkioBufferClass != null) {
            try {
                Object source = XposedHelpers.callMethod(body, "source");
                return (String) XposedHelpers.callMethod(source, "readUtf8");
            } catch (Throwable t) {
                XposedBridge.log(TAG + "body.source().readUtf8() failed: " + t.getMessage());
            }
        }

        return null;
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
