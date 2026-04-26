package com.spoofer.packagename;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MiMusicAdBlocker {

    private static final String TAG = "[MiMusicAd] ";
    private static final String TARGET_PKG = "com.miui.player";

    private static final String[] AD_KEYWORDS = {
        "广告", "开启免费模式", "免费听歌", "限时免费",
        "领金币", "赚钱", "福利", "签到",
        "开通会员", "VIP", "会员专享",
        "青少年模式", "评分", "好评", "去评分",
        "更新", "升级", "通知权限", "开启通知",
        "隐私", "用户协议", "活动", "红包", "抽奖",
        "跳过", "关闭广告", "取消"
    };

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PKG.equals(lpparam.packageName)) return;
        XposedBridge.log(TAG + "loaded");

        try {
            XposedHelpers.findAndHookMethod(Dialog.class, "show", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Dialog dialog = (Dialog) param.thisObject;
                    if (isAdDialog(dialog)) {
                        XposedBridge.log(TAG + "blocked: " + dialog.getClass().getSimpleName());
                        param.setResult(null);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + "hook fail: " + t.getMessage());
        }

        try {
            XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Activity a = (Activity) param.thisObject;
                    String name = a.getClass().getName();
                    if (!name.startsWith(TARGET_PKG)) return;
                    if (matchKeyword(name)) {
                        XposedBridge.log(TAG + "blocked Activity: " + name);
                        a.finish();
                        param.setResult(null);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + "hook Activity fail: " + t.getMessage());
        }

        try {
            XposedHelpers.findAndHookMethod(View.class, "setVisibility", int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if ((int) param.args[0] != View.VISIBLE) return;
                    View v = (View) param.thisObject;
                    String id = "";
                    try {
                        int rid = v.getId();
                        if (rid != View.NO_ID) id = v.getResources().getResourceEntryName(rid);
                    } catch (Throwable ignored) {}
                    if (matchKeyword(id)) {
                        param.args[0] = View.GONE;
                    }
                }
            });
        } catch (Throwable ignored) {}

        XposedBridge.log(TAG + "hooks installed");
    }

    private static boolean isAdDialog(Dialog dialog) {
        try {
            String cls = dialog.getClass().getName();
            if (matchKeyword(cls)) return true;
            Window w = dialog.getWindow();
            if (w != null) {
                CharSequence title = w.getTitle();
                if (title != null && matchKeyword(title.toString())) return true;
            }
            View content = dialog.findViewById(android.R.id.content);
            if (content != null) {
                for (String t : getAllTexts(content)) {
                    if (matchKeyword(t)) return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean matchKeyword(String text) {
        if (text == null || text.isEmpty()) return false;
        String lower = text.toLowerCase();
        for (String kw : AD_KEYWORDS) {
            if (lower.contains(kw.toLowerCase())) return true;
        }
        return false;
    }

    private static List<String> getAllTexts(View view) {
        List<String> texts = new ArrayList<>();
        if (view instanceof TextView) {
            CharSequence t = ((TextView) view).getText();
            if (t != null) texts.add(t.toString());
        }
        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            for (int i = 0; i < g.getChildCount(); i++) {
                texts.addAll(getAllTexts(g.getChildAt(i)));
            }
        }
        return texts;
    }
}
