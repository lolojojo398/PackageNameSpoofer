package com.spoofer.packagename;

import android.app.Activity;
import android.app.Dialog;
import android.view.View;
import android.view.Window;
import android.webkit.WebView;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 小米音乐广告弹窗拦截
 * 通过hook Dialog.show()、View.setVisibility()、WebView加载等来拦截广告
 */
public class AdBlockHook {

    private static final String TAG = "[AdBlock] ";

    // 广告相关关键词，命中任意一个就拦截
    private static final String[] AD_KEYWORDS = {
        "ad", "ads", "advert", "AdService", "AdManager", "AdView", "AdDialog",
        "splash", "Splash", "SPLASH",
        "banner", "Banner",
        "interstitial", "Interstitial",
        "popup", "Popup",
        "reward", "Reward",
        "nativead", "NativeAd",
        "feedad", "FeedAd",
        "floatad", "FloatAd",
        "dialogad", "DialogAd",
        "openad", "OpenAd",
        "pangle", "Pangle",           // 穿山甲SDK
        "tt_ad", "TTAd",              // 穿山甲
        "youliang", "YouLiang",       // 优量汇
        "gdt", "GDT",                 // 广点通
        "baiduad", "BaiduAd",         // 百度广告
        "csj", "CSJ",                 // 穿山甲简写
        "miui.ad", "MiuiAd",          // MIUI广告
        "com.miui.systemAdSolution",  // MIUI系统广告
        "mimo", "MIMO",               // 小米广告SDK
        "com.xiaomi.market",          // 小米应用商店广告
        "coin_center",                // 金币中心(兑换页广告)
        "promotion", "Promotion",     // 推广
        "recommend", "Recommend",     // 推荐
    };

    // 这些关键词不拦截（白名单，防止误杀登录等正常弹窗）
    private static final String[] AD_WHITELIST = {
        "login", "Login", "LOGIN",
        "auth", "Auth",
        "oauth", "OAuth",
        "sign", "Sign",
        "account", "Account",
        "permission", "Permission",
        "update", "Update",
        "privacy", "Privacy",
        "agreement", "Agreement",
        "protocol", "Protocol",
        "policy", "Policy",
    };

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        // 只 hook 小米音乐
        String pkg = lpparam.packageName;
        if (!"com.miui.player".equals(pkg)) return;

        XposedBridge.log(TAG + "loaded for " + pkg);

        hookDialogShow(lpparam.classLoader);
        hookAlertDialogShow(lpparam.classLoader);
        hookViewVisibility(lpparam.classLoader);
    }

    /**
     * Hook Dialog.show() - 拦截广告弹窗
     */
    private static void hookDialogShow(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(Dialog.class, "show", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Dialog dialog = (Dialog) param.thisObject;
                    if (isAdDialog(dialog)) {
                        XposedBridge.log(TAG + "blocked Dialog: " + dialog.getClass().getName());
                        param.setResult(null); // 阻止 show()
                    }
                }
            });
            XposedBridge.log(TAG + "Dialog.show hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Dialog.show hook failed: " + t.getMessage());
        }
    }

    /**
     * Hook AlertDialog.show() - 额外拦截 AlertDialog 类型的广告
     */
    private static void hookAlertDialogShow(ClassLoader cl) {
        try {
            Class<?> alertDialogClass = Class.forName("android.app.AlertDialog", false, cl);
            XposedHelpers.findAndHookMethod(alertDialogClass, "show", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Dialog dialog = (Dialog) param.thisObject;
                    if (isAdDialog(dialog)) {
                        XposedBridge.log(TAG + "blocked AlertDialog: " + dialog.getClass().getName());
                        param.setResult(null);
                    }
                }
            });
            XposedBridge.log(TAG + "AlertDialog.show hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "AlertDialog.show hook failed: " + t.getMessage());
        }
    }

    /**
     * Hook View.setVisibility() - 拦截广告View的显示
     * 当广告View被设置为VISIBLE时，改为GONE
     */
    private static void hookViewVisibility(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(View.class, "setVisibility", int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    int visibility = (int) param.args[0];
                    if (visibility != View.VISIBLE) return; // 只拦截设为可见的操作

                    View view = (View) param.thisObject;
                    String className = view.getClass().getName().toLowerCase();
                    if (isAdClassName(className)) {
                        XposedBridge.log(TAG + "blocked View show: " + view.getClass().getName());
                        param.args[0] = View.GONE;
                    }
                }
            });
            XposedBridge.log(TAG + "View.setVisibility hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "View.setVisibility hook failed: " + t.getMessage());
        }
    }

    /**
     * 判断一个Dialog是否是广告弹窗
     */
    private static boolean isAdDialog(Dialog dialog) {
        if (dialog == null) return false;

        String className = dialog.getClass().getName();
        String classNameLower = className.toLowerCase();

        // 白名单检查 - 如果是登录相关的弹窗，不拦截
        for (String keyword : AD_WHITELIST) {
            if (classNameLower.contains(keyword.toLowerCase())) {
                return false;
            }
        }

        // 检查Dialog类名是否包含广告关键词
        if (isAdClassName(className)) {
            return true;
        }

        // 检查调用栈中是否有广告相关类
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stack) {
            String callerClass = element.getClassName().toLowerCase();
            // 白名单检查
            boolean isWhitelisted = false;
            for (String keyword : AD_WHITELIST) {
                if (callerClass.contains(keyword.toLowerCase())) {
                    isWhitelisted = true;
                    break;
                }
            }
            if (isWhitelisted) continue;

            if (isAdClassName(callerClass)) {
                XposedBridge.log(TAG + "ad caller in stack: " + element.getClassName());
                return true;
            }
        }

        // 检查Window的属性
        try {
            Window window = dialog.getWindow();
            if (window != null) {
                String title = "";
                try {
                    // 尝试获取窗口标题
                    Object titleObj = window.getAttributes().getTitle();
                    if (titleObj != null) title = titleObj.toString().toLowerCase();
                } catch (Throwable ignored) {}

                if (isAdClassName(title)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}

        return false;
    }

    /**
     * 判断类名/字符串是否包含广告关键词
     */
    private static boolean isAdClassName(String name) {
        if (name == null || name.isEmpty()) return false;
        String lower = name.toLowerCase();
        for (String keyword : AD_KEYWORDS) {
            if (lower.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
