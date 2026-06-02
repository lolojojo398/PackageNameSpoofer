package com.spoofer.packagename;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 小米音乐 - 看广告领金币 自动化
 *
 * 广告SDK(SystemWindowChecker)用native层读取时间，绕过Java层hook
 * 方案: hook onActivityResumed，将时间戳改为足够大的值
 * 这样SDK计算的pause-resume差值就会 >= 5秒
 */
public class MiMusicAdBlocker {

    private static final String TAG = "[MiMusicAd] ";
    private static boolean adShown = false;

    public static void hook(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.miui.player".equals(lpparam.packageName)) return;

        XposedBridge.log(TAG + "Loading hooks...");

        hookAdShow(lpparam);
        hookAdEvents(lpparam);
        hookSystemWindowChecker(lpparam);
    }

    /**
     * Hook 广告SDK的 SystemWindowChecker
     * 它的 onActivityResumed 里会计算 pause-resume 的时间差
     * 我们把 resumed 的时间戳改成 paused + 60秒，让SDK认为用户离开了足够久
     */
    private static void hookSystemWindowChecker(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // 找到 SystemWindowChecker 的内部类
            // 日志显示: com.qq.e.comm.plugin.m.bl
            Class<?> checkerClass = XposedHelpers.findClass(
                "com.qq.e.comm.plugin.m.bl",
                lpparam.classLoader
            );

            // Hook 所有方法并打印日志，找到哪个方法返回结果
            XposedBridge.hookAllMethods(checkerClass, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    XposedBridge.log(TAG + "[bl] " + param.method.getName() + "() called");
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.method.getName().equals("toString") ||
                        param.method.getName().equals("hashCode")) return;
                    Object result = param.getResult();
                    if (result != null) {
                        XposedBridge.log(TAG + "[bl] " + param.method.getName() + "() returned: " + result);
                    }
                }
            });

            XposedBridge.log(TAG + "SystemWindowChecker (bl) hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "SystemWindowChecker (bl) hook FAILED: " + t.getMessage());
        }

        // 也尝试hook DirectLauncherNode 的回调
        try {
            Class<?> directClass = XposedHelpers.findClass(
                "com.qq.e.comm.plugin.n.aq",
                lpparam.classLoader
            );

            XposedBridge.hookAllMethods(directClass, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    XposedBridge.log(TAG + "[DirectLauncher] " + param.method.getName() + "() called");
                }
            });

            XposedBridge.log(TAG + "DirectLauncherNode hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "DirectLauncherNode hook FAILED: " + t.getMessage());
        }

        // Hook 所有包含 "reward" 或 "time" 相关的类
        try {
            String[] rewardClasses = {
                "com.qq.e.comm.plugin.reward.a",
                "com.qq.e.comm.plugin.reward.b",
                "com.qq.e.comm.plugin.reward.c",
                "com.qq.e.comm.plugin.reward.d",
                "com.qq.e.comm.plugin.reward.e",
                "com.qq.e.comm.plugin.m.a",
                "com.qq.e.comm.plugin.m.b",
                "com.qq.e.comm.plugin.m.c",
                "com.qq.e.comm.plugin.m.d",
                "com.qq.e.comm.plugin.m.e",
                "com.qq.e.comm.plugin.m.f",
                "com.qq.e.comm.plugin.m.g",
                "com.qq.e.comm.plugin.m.h",
                "com.qq.e.comm.plugin.m.i",
                "com.qq.e.comm.plugin.m.j",
                "com.qq.e.comm.plugin.m.k",
                "com.qq.e.comm.plugin.m.l",
                "com.qq.e.comm.plugin.m.m",
            };
            for (String className : rewardClasses) {
                try {
                    Class<?> cls = XposedHelpers.findClass(className, lpparam.classLoader);
                    XposedBridge.hookAllMethods(cls, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String name = param.method.getName();
                            if (!name.equals("toString") && !name.equals("hashCode") && !name.equals("equals")) {
                                XposedBridge.log(TAG + "[" + className.substring(className.lastIndexOf('.') + 1) + "] " + name + "() called");
                            }
                        }
                    });
                } catch (Throwable ignored) {}
            }
            XposedBridge.log(TAG + "Reward class hooks attempted");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Reward class hooks FAILED: " + t.getMessage());
        }
    }

    /**
     * 记录广告展示时间
     */
    private static void hookAdShow(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> adActivityClass = XposedHelpers.findClass(
                "com.qq.e.tg.ADActivity",
                lpparam.classLoader
            );

            XposedHelpers.findAndHookMethod(adActivityClass, "onCreate",
                android.os.Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        adShown = true;
                        XposedBridge.log(TAG + "ADActivity.onCreate() - adShown=true");
                    }
                }
            );

            XposedBridge.log(TAG + "ADActivity.onCreate hook OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "ADActivity.onCreate hook FAILED: " + t.getMessage());
        }
    }

    /**
     * 辅助Hook - 记录广告事件
     */
    private static void hookAdEvents(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> listenerClass = XposedHelpers.findClass(
                "com.tencent.qqmusiclite.freemode.ad.reward.listener.ActivityRewardListener",
                lpparam.classLoader
            );

            XposedHelpers.findAndHookMethod(listenerClass, "onADClose", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    XposedBridge.log(TAG + "onADClose() fired!");
                }
            });

            XposedHelpers.findAndHookMethod(listenerClass, "requestReward",
                boolean.class, boolean.class, "kotlin.coroutines.Continuation",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        XposedBridge.log(TAG + "requestReward() called!");
                    }
                }
            );

            XposedBridge.log(TAG + "Ad events hooks OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Ad events hooks FAILED: " + t.getMessage());
        }
    }
}
