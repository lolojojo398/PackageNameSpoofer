package com.spoofer.packagename;

import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class ExchangeHook {

    private static final String TAG = "[Exchange] ";
    private static boolean pageActive = false;
    private static volatile boolean inEvalHook = false;

    // DOM text replacement fallback
    private static final String DOM_JS =
        "(function(){try{" +
        "var c=0;" +
        "var bs=document.querySelectorAll('.equity_conversion__btn');" +
        "for(var i=0;i<bs.length;i++){" +
        "var t=(bs[i].textContent||bs[i].innerText||'').trim();" +
        "if(t.indexOf('\\u5df2\\u62a2\\u5149')>=0){" +
        "bs[i].textContent='\\u5151\\u6362';" +
        "bs[i].style.pointerEvents='auto';" +
        "bs[i].style.opacity='1';" +
        "c++;}" +
        "}" +
        "return 'ok:'+c;" +
        "}catch(e){return 'ERR:'+e.message;}" +
        "})()";

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.miui.player".equals(lpparam.packageName)) return;
        XposedBridge.log(TAG + "loaded");
        pageActive = false;

        hookEvaluateJavascript(lpparam.classLoader);
        hookWebView();
    }

    // Hook evaluateJavascript to intercept native->JS data callbacks.
    // When the app passes API response data (containing allSalesData) to the
    // WebView JS via evaluateJavascript, we modify the JSON before it executes.
    private static void hookEvaluateJavascript(ClassLoader cl) {
        try {
            // Try hooking on the actual WebView class in the classloader
            // (might be a subclass or the standard android.webkit.WebView)
            XposedHelpers.findAndHookMethod(WebView.class, "evaluateJavascript",
                String.class, android.webkit.ValueCallback.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (inEvalHook || !pageActive) return;
                        String script = (String) param.args[0];
                        if (script == null || !script.contains("allSalesData")) return;

                        XposedBridge.log(TAG + "eval hit, len=" + script.length());

                        String modified = modifyCallbackScript(script);
                        if (modified != script) {
                            inEvalHook = true;
                            param.args[0] = modified;
                            XposedBridge.log(TAG + "eval modified OK");
                        }
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        inEvalHook = false;
                    }
                });
            XposedBridge.log(TAG + "eval hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "eval hook failed: " + t.getMessage());
        }

        // Also try hooking loadUrl("javascript:...") calls
        try {
            XposedHelpers.findAndHookMethod(WebView.class, "loadUrl",
                String.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (inEvalHook || !pageActive) return;
                        String url = (String) param.args[0];
                        if (url == null || !url.startsWith("javascript:")) return;
                        if (!url.contains("allSalesData")) return;

                        XposedBridge.log(TAG + "loadUrl js hit, len=" + url.length());

                        String js = url.substring("javascript:".length());
                        String modified = modifyCallbackScript(js);
                        if (modified != js) {
                            inEvalHook = true;
                            param.args[0] = "javascript:" + modified;
                            XposedBridge.log(TAG + "loadUrl js modified OK");
                        }
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        inEvalHook = false;
                    }
                });
            XposedBridge.log(TAG + "loadUrl js hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "loadUrl js hook failed: " + t.getMessage());
        }
    }

    // Detect when the coin_center page loads, and inject DOM fallback
    private static void hookWebView() {
        try {
            XposedHelpers.findAndHookMethod(WebView.class, "loadUrl",
                String.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            final WebView wv = (WebView) param.thisObject;
                            String url = (String) param.args[0];
                            if (url != null && url.startsWith("http")
                                    && url.contains("coin_center")) {
                                pageActive = true;
                                XposedBridge.log(TAG + "page detected");
                                for (final int delay : new int[]{3000, 6000, 10000}) {
                                    wv.postDelayed(new Runnable() {
                                        public void run() {
                                            try {
                                                wv.evaluateJavascript(DOM_JS,
                                                    new android.webkit.ValueCallback<String>() {
                                                        public void onReceiveValue(String v) {
                                                            XposedBridge.log(TAG + "dom=" + v);
                                                        }
                                                    });
                                            } catch (Throwable t) {}
                                        }
                                    }, delay);
                                }
                            }
                        } catch (Throwable t) {}
                    }
                });
            XposedBridge.log(TAG + "webview hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "webview hook failed: " + t.getMessage());
        }
    }

    // Extract JSON from a JS callback script and modify sales data.
    // Handles formats like:
    //   M.client.execGlobalCallback("id",{...json...})
    //   someFunc({...json...})
    //   {...json...}
    private static String modifyCallbackScript(String script) {
        try {
            // Find the first '{' which should be the start of the JSON data
            int jsonStart = script.indexOf('{');
            if (jsonStart < 0) return script;

            // Find matching '}' by counting brace depth
            int depth = 0;
            int jsonEnd = -1;
            boolean inString = false;
            char prevChar = 0;
            for (int i = jsonStart; i < script.length(); i++) {
                char c = script.charAt(i);
                if (inString) {
                    if (c == '\'' && prevChar != '\\') inString = false;
                } else {
                    if (c == '\'') inString = true;
                    else if (c == '{') depth++;
                    else if (c == '}') {
                        depth--;
                        if (depth == 0) {
                            jsonEnd = i;
                            break;
                        }
                    }
                }
                prevChar = c;
            }
            if (jsonEnd < 0) return script;

            String json = script.substring(jsonStart, jsonEnd + 1);
            if (!json.contains("allSalesData")) return script;

            String modifiedJson = modifySalesData(json);
            if (modifiedJson == json) return script; // no change (identity check)

            return script.substring(0, jsonStart) + modifiedJson
                + script.substring(jsonEnd + 1);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "script parse err: " + t.getMessage());
            return script;
        }
    }

    // Modify allSalesData in the API response JSON to make products appear
    // available. Tries multiple data paths.
    private static String modifySalesData(String json) {
        try {
            JSONObject root = new JSONObject(json);

            // Try different paths to find the products array
            JSONArray products = findProducts(root);
            if (products == null || products.length() == 0) return json;

            boolean changed = false;
            for (int i = 0; i < products.length(); i++) {
                JSONObject p = products.getJSONObject(i);
                JSONObject sales = p.optJSONObject("allSalesData");
                if (sales != null) {
                    int day = sales.optInt("day", 0);
                    int week = sales.optInt("week", 0);
                    int month = sales.optInt("month", 0);
                    if (day > 0 || week > 0 || month > 0) {
                        sales.put("day", 0);
                        sales.put("week", 0);
                        sales.put("month", 0);
                        changed = true;
                    }
                }
            }

            if (changed) {
                XposedBridge.log(TAG + "sales zeroed, " + products.length() + " products");
                return root.toString();
            }
            return json;
        } catch (Throwable t) {
            XposedBridge.log(TAG + "json err: " + t.getMessage());
            return json;
        }
    }

    // Find the products JSONArray in various JSON structures
    private static JSONArray findProducts(JSONObject root) {
        // Path 1: req_0.data.products
        JSONObject req0 = root.optJSONObject("req_0");
        if (req0 != null) {
            JSONObject data = req0.optJSONObject("data");
            if (data != null) {
                JSONArray p = data.optJSONArray("products");
                if (p != null) return p;
            }
        }
        // Path 2: data.products
        JSONObject data = root.optJSONObject("data");
        if (data != null) {
            JSONArray p = data.optJSONArray("products");
            if (p != null) return p;
        }
        // Path 3: products (top level)
        return root.optJSONArray("products");
    }
}
