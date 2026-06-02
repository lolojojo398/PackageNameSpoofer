package com.spoofer.packagename;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 小米音乐 - 看广告领金币 自动化
 *
 * 诊断版本: hook广告SDK内部类，打印所有方法调用
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
     * Hook 广告SDK的 SystemWindowChecker (com.qq.e.comm.plugin.m.bl)
     * 打印所有方法调用，找到时间判定逻辑
     */
    private static void hookSystemWindowChecker(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook SystemWindowChecker 内部类
        try {
            Class<?> checkerClass = XposedHelpers.findClass(
                "com.qq.e.comm.plugin.m.bl",
                lpparam.classLoader
            );

            // 打印该类所有方法
            Method[] methods = checkerClass.getDeclaredMethods();
            for (Method m : methods) {
                XposedBridge.log(TAG + "[bl] method: " + m.getName() + " params=" + m.getParameterCount());
            }

            // Hook 所有方法
            for (Method m : methods) {
                final String methodName = m.getName();
                try {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            StringBuilder args = new StringBuilder();
                            for (int i = 0; i < param.args.length; i++) {
                                if (i > 0) args.append(", ");
                                args.append(param.args[i] != null ? param.args[i].toString() : "null");
                            }
                            XposedBridge.log(TAG + "[bl]." + methodName + "(" + args + ")");
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object result = param.getResult();
                            if (result != null && !(result instanceof String)) {
                                XposedBridge.log(TAG + "[bl]." + methodName + " -> " + result);
                            }
                        }
                    });
                } catch (Throwable t) {
                    XposedBridge.log(TAG + "[bl]." + methodName + " hook FAILED: " + t.getMessage());
                }
            }

            XposedBridge.log(TAG + "SystemWindowChecker hook OK, " + methods.length + " methods hooked");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "SystemWindowChecker hook FAILED: " + t.getMessage());
        }

        // Hook DirectLauncherNode (com.qq.e.comm.plugin.n.aq)
        try {
            Class<?> directClass = XposedHelpers.findClass(
                "com.qq.e.comm.plugin.n.aq",
                lpparam.classLoader
            );

            Method[] methods = directClass.getDeclaredMethods();
            for (Method m : methods) {
                final String methodName = m.getName();
                try {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            XposedBridge.log(TAG + "[Direct]." + methodName + "() called");
                        }
                    });
                } catch (Throwable ignored) {}
            }

            XposedBridge.log(TAG + "DirectLauncherNode hook OK, " + methods.length + " methods hooked");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "DirectLauncherNode hook FAILED: " + t.getMessage());
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
