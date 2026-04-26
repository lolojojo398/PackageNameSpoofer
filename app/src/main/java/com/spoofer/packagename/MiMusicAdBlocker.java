package com.spoofer.packagename;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MiMusicAdBlocker {
    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.miui.player".equals(lpparam.packageName)) return;
        XposedBridge.log("[MiMusicAd] loaded");
    }
}
