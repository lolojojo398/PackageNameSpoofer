package com.spoofer.packagename;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "[Spoofer] "; // test
    private static String savedOrigFingerprint = null;
    private static String savedOrigModel = null;
    private static String savedOrigDevice = null;
    private static String savedOrigBoard = null;
    private static String savedOrigHardware = null;
    private static String savedOrigProduct = null;
    private static String savedOrigDisplay = null;
    private static String savedOrigHost = null;
    private static String savedOrigId = null;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (lpparam.packageName.equals("com.spoofer.packagename")) return;
        String processName = lpparam.processName;
        if (processName == null || !processName.startsWith(lpparam.packageName)) {
            XposedBridge.log(TAG + "Skipping " + lpparam.packageName + " in process " + processName);
            return;
        }
        final String hostApp = lpparam.packageName;
        XposedBridge.log(TAG + "Hooked: " + hostApp + " pid=" + android.os.Process.myPid());
        hookPackageManager(lpparam.classLoader, hostApp);
        hookStartActivity(lpparam.classLoader, hostApp);
        hookDeviceId(lpparam.classLoader, hostApp);
        ExchangeHook.hook(lpparam);
        TaskBypassHook.hook(lpparam);
    }

    private void hookPackageManager(ClassLoader cl, final String hostApp) {
        // getPackageInfo(String, int)
        XposedHelpers.findAndHookMethod("android.app.ApplicationPackageManager", cl,
            "getPackageInfo", String.class, int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.getResult() != null) return;
                    String pkg = (String) param.args[0];
                    if (shouldSpoof(pkg, hostApp) && param.getThrowable() != null) {
                        XposedBridge.log(TAG + "getPackageInfo: " + pkg);
                        param.setThrowable(null);
                        param.setResult(fakePackageInfo(pkg));
                    }
                }
            });

        // getPackageInfo(String, PackageManager.PackageInfoFlags) - API 33+
        try {
            Class<?> flagsClass = Class.forName("android.content.pm.PackageManager$PackageInfoFlags", false, cl);
            XposedHelpers.findAndHookMethod("android.app.ApplicationPackageManager", cl,
                "getPackageInfo", String.class, flagsClass, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.getResult() != null) return;
                        String pkg = (String) param.args[0];
                        if (shouldSpoof(pkg, hostApp) && param.getThrowable() != null) {
                            XposedBridge.log(TAG + "getPackageInfo(flags): " + pkg);
                            param.setThrowable(null);
                            param.setResult(fakePackageInfo(pkg));
                        }
                    }
                });
        } catch (Throwable t) {}

        // getApplicationInfo(String, int)
        XposedHelpers.findAndHookMethod("android.app.ApplicationPackageManager", cl,
            "getApplicationInfo", String.class, int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.getResult() != null) return;
                    String pkg = (String) param.args[0];
                    if (shouldSpoof(pkg, hostApp) && param.getThrowable() != null) {
                        XposedBridge.log(TAG + "getApplicationInfo: " + pkg);
                        param.setThrowable(null);
                        param.setResult(fakeAppInfo(pkg));
                    }
                }
            });

        // getApplicationInfo(String, ApplicationInfoFlags) - API 33+
        try {
            Class<?> flagsClass = Class.forName("android.content.pm.PackageManager$ApplicationInfoFlags", false, cl);
            XposedHelpers.findAndHookMethod("android.app.ApplicationPackageManager", cl,
                "getApplicationInfo", String.class, flagsClass, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.getResult() != null) return;
                        String pkg = (String) param.args[0];
                        if (shouldSpoof(pkg, hostApp) && param.getThrowable() != null) {
                            XposedBridge.log(TAG + "getApplicationInfo(flags): " + pkg);
                            param.setThrowable(null);
                            param.setResult(fakeAppInfo(pkg));
                        }
                    }
                });
        } catch (Throwable t) {}

        // queryIntentActivities
        try {
            XposedHelpers.findAndHookMethod("android.app.ApplicationPackageManager", cl,
                "queryIntentActivities", Intent.class, int.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Intent intent = (Intent) param.args[0];
                        if (intent == null) return;
                        String pkg = intent.getPackage();
                        if (pkg == null) return;
                        List<?> list = (List<?>) param.getResult();
                        if (shouldSpoof(pkg, hostApp) && (list == null || list.isEmpty())) {
                            XposedBridge.log(TAG + "queryIntentActivities: " + pkg);
                            List<ResolveInfo> fake = new ArrayList<>();
                            ResolveInfo ri = new ResolveInfo();
                            ri.activityInfo = new android.content.pm.ActivityInfo();
                            ri.activityInfo.packageName = pkg;
                            ri.activityInfo.name = pkg + ".MainActivity";
                            ri.activityInfo.applicationInfo = fakeAppInfo(pkg);
                            fake.add(ri);
                            param.setResult(fake);
                        }
                    }
                });
        } catch (Throwable t) {}

        // resolveActivity
        try {
            XposedHelpers.findAndHookMethod("android.app.ApplicationPackageManager", cl,
                "resolveActivity", Intent.class, int.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Intent intent = (Intent) param.args[0];
                        if (intent == null) return;
                        String pkg = intent.getPackage();
                        if (pkg == null) return;
                        if (shouldSpoof(pkg, hostApp) && param.getResult() == null) {
                            XposedBridge.log(TAG + "resolveActivity: " + pkg);
                            ResolveInfo ri = new ResolveInfo();
                            ri.activityInfo = new android.content.pm.ActivityInfo();
                            ri.activityInfo.packageName = pkg;
                            ri.activityInfo.name = pkg + ".MainActivity";
                            ri.activityInfo.applicationInfo = fakeAppInfo(pkg);
                            param.setResult(ri);
                        }
                    }
                });
        } catch (Throwable t) {}

        // checkSignatures
        try {
            XposedHelpers.findAndHookMethod("android.app.ApplicationPackageManager", cl,
                "checkSignatures", String.class, String.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String pkg1 = (String) param.args[0];
                        String pkg2 = (String) param.args[1];
                        if (shouldSpoof(pkg1, hostApp) || shouldSpoof(pkg2, hostApp)) {
                            param.setResult(0);
                        }
                    }
                });
        } catch (Throwable t) {}
    }

    private void hookStartActivity(ClassLoader cl, final String hostApp) {
        // Hook Instrumentation.execStartActivity
        try {
            XposedHelpers.findAndHookMethod("android.app.Instrumentation", cl,
                "execStartActivity",
                android.content.Context.class,
                android.os.IBinder.class,
                android.os.IBinder.class,
                android.app.Activity.class,
                Intent.class,
                int.class,
                android.os.Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Intent intent = (Intent) param.args[4];
                        if (intent == null) return;
                        String pkg = getTargetPackage(intent);
                        if (pkg != null && shouldSpoof(pkg, hostApp)) {
                            if (isAppReallyInstalled((android.content.Context) param.args[0], pkg)) {
                                XposedBridge.log(TAG + "execStartActivity allow (installed): " + pkg);
                                return;
                            }
                            XposedBridge.log(TAG + "execStartActivity redirect: " + pkg + " hostApp=" + hostApp + " pid=" + android.os.Process.myPid());
                            param.args[4] = makeBrowserIntent(pkg);
                        }
                    }
                });
            XposedBridge.log(TAG + "hook execStartActivity OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "hook execStartActivity failed: " + t.getMessage());
        }

        // Hook Activity.startActivityForResult — 补漏拦截另一条 startActivity 路径
        try {
            XposedHelpers.findAndHookMethod("android.app.Activity", cl,
                "startActivityForResult",
                Intent.class, int.class, android.os.Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Intent intent = (Intent) param.args[0];
                        if (intent == null) return;
                        String pkg = getTargetPackage(intent);
                        if (pkg != null && shouldSpoof(pkg, hostApp)) {
                            Activity activity = (Activity) param.thisObject;
                            if (isAppReallyInstalled(activity, pkg)) {
                                XposedBridge.log(TAG + "startActivityForResult allow (installed): " + pkg);
                                return;
                            }
                            XposedBridge.log(TAG + "startActivityForResult redirect: " + pkg);
                            param.args[0] = makeBrowserIntent(pkg);
                        }
                    }
                });
            XposedBridge.log(TAG + "hook startActivityForResult OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "hook startActivityForResult failed: " + t.getMessage());
        }

        // Hook Activity.startActivity — 拦截 market:// 直链（广告SDK绕过上述hook的路径）
        try {
            XposedHelpers.findAndHookMethod("android.app.Activity", cl,
                "startActivity", Intent.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Intent intent = (Intent) param.args[0];
                        if (intent == null) return;
                        String marketPkg = extractMarketPkg(intent);
                        if (marketPkg != null && shouldSpoof(marketPkg, hostApp)) {
                            Activity activity = (Activity) param.thisObject;
                            if (isAppReallyInstalled(activity, marketPkg)) {
                                XposedBridge.log(TAG + "startActivity allow market (installed): " + marketPkg);
                                return;
                            }
                            XposedBridge.log(TAG + "startActivity redirect market: " + marketPkg);
                            param.args[0] = makeBrowserIntent(marketPkg);
                        }
                    }
                });
            XposedBridge.log(TAG + "hook Activity.startActivity OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "hook Activity.startActivity failed: " + t.getMessage());
        }

        // Hook ContextWrapper.startActivity — 防止广告SDK用非Activity Context调用
        try {
            XposedHelpers.findAndHookMethod("android.content.ContextWrapper", cl,
                "startActivity", Intent.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Intent intent = (Intent) param.args[0];
                        if (intent == null) return;
                        String marketPkg = extractMarketPkg(intent);
                        if (marketPkg != null && shouldSpoof(marketPkg, hostApp)) {
                            android.content.Context ctx = (android.content.Context) param.thisObject;
                            if (isAppReallyInstalled(ctx, marketPkg)) {
                                XposedBridge.log(TAG + "ContextWrapper allow market (installed): " + marketPkg);
                                return;
                            }
                            XposedBridge.log(TAG + "ContextWrapper redirect market: " + marketPkg);
                            param.args[0] = makeBrowserIntent(marketPkg);
                        }
                    }
                });
            XposedBridge.log(TAG + "hook ContextWrapper.startActivity OK");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "hook ContextWrapper.startActivity failed: " + t.getMessage());
        }
    }

    /**
     * 从 market://details?id=xxx 或 market://details/xxx URL 中提取包名
     */
    private String extractMarketPkg(Intent intent) {
        try {
            android.net.Uri data = intent.getData();
            if (data == null) return null;
            String scheme = data.getScheme();
            if (!"market".equals(scheme)) return null;
            // market://details?id=com.example.app
            String id = data.getQueryParameter("id");
            if (id != null && !id.isEmpty()) return id;
            // market://details/com.example.app
            String path = data.getPath();
            if (path != null && path.startsWith("/details/")) return path.substring("/details/".length());
            if (path != null && path.startsWith("/details")) {
                String rest = path.substring("/details".length());
                if (rest.startsWith("/")) rest = rest.substring(1);
                if (!rest.isEmpty()) return rest;
            }
        } catch (Throwable t) {}
        return null;
    }

    private String getTargetPackage(Intent intent) {
        if (intent.getPackage() != null) return intent.getPackage();
        if (intent.getComponent() != null) return intent.getComponent().getPackageName();
        return intent.getPackage();
    }

    /**
     * 检查应用是否真的安装在手机上
     * 通过反射调用 IPackageManager.getApplicationInfo 原始方法，绕过本模块 hook
     * 依次检查当前用户和主用户(user 0)，覆盖双开/分身场景
     */
    private boolean isAppReallyInstalled(android.content.Context context, String pkg) {
        try {
            Object pm = context.getPackageManager();
            java.lang.reflect.Method getPM = pm.getClass().getMethod("getService");
            Object ipm = getPM.invoke(pm);
            if (ipm == null) return false;

            // 依次检查不同用户: 先当前用户，再 user 0（双开微信在 user 0 上）
            int[] userIds = {android.os.Process.myUserHandle().hashCode(), 0};
            for (int uid : userIds) {
                if (isInstalledForUser(ipm, pkg, uid)) return true;
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "isAppReallyInstalled failed for " + pkg + ": " + t.getMessage());
        }
        return false;
    }

    private boolean isInstalledForUser(Object ipm, String pkg, int userId) {
        try {
            // API 33+: getApplicationInfo(String packageName, long flags, int userId)
            java.lang.reflect.Method m = ipm.getClass().getMethod(
                "getApplicationInfo", String.class, long.class, int.class);
            Object ai = m.invoke(ipm, pkg, 0x00004000L, userId);
            if (ai != null) {
                int flags = ai.getClass().getField("flags").getInt(ai);
                return (flags & ApplicationInfo.FLAG_INSTALLED) != 0;
            }
        } catch (NoSuchMethodException e) {
            try {
                // API 28-32: getApplicationInfo(String packageName, int flags, int userId)
                java.lang.reflect.Method m = ipm.getClass().getMethod(
                    "getApplicationInfo", String.class, int.class, int.class);
                Object ai = m.invoke(ipm, pkg, 0x00004000, userId);
                if (ai != null) {
                    int flags = ai.getClass().getField("flags").getInt(ai);
                    return (flags & ApplicationInfo.FLAG_INSTALLED) != 0;
                }
            } catch (Throwable t) {}
        } catch (Throwable t) {}
        return false;
    }

    /**
     * 未安装时拉起 Via 浏览器
     */
    private Intent makeBrowserIntent(String pkg) {
        Intent i = new Intent(Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=" + pkg));
        i.setPackage("mark.via");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    private PackageInfo fakePackageInfo(String pkg) {
        PackageInfo pi = new PackageInfo();
        pi.packageName = pkg;
        pi.versionName = "1.0.0";
        pi.versionCode = 1;
        pi.firstInstallTime = System.currentTimeMillis() - 86400000;
        pi.lastUpdateTime = System.currentTimeMillis();
        pi.applicationInfo = fakeAppInfo(pkg);
        return pi;
    }

    private ApplicationInfo fakeAppInfo(String pkg) {
        ApplicationInfo ai = new ApplicationInfo();
        ai.packageName = pkg;
        ai.sourceDir = "/data/app/" + pkg + "-1/base.apk";
        ai.publicSourceDir = ai.sourceDir;
        ai.uid = 10000 + (Math.abs(pkg.hashCode()) % 90000);
        ai.flags = ApplicationInfo.FLAG_INSTALLED;
        ai.targetSdkVersion = 33;
        ai.minSdkVersion = 26;

        // 添加默认 metaData，防止某些SDK读取通知渠道配置时NPE
        Bundle meta = new Bundle();
        meta.putString("com.google.android.gms.version", "12451000");
        meta.putString("android.app.default_notification_channel_id", "default");
        ai.metaData = meta;

        return ai;
    }

    private void hookDeviceId(ClassLoader cl, final String hostApp) {
        final String fakeAndroidId = generateRandomHex(16);
        final String fakeSerial = "SPF" + generateRandomHex(10);
        final String fakeImei = generateRandomDigit(15);
        XposedBridge.log(TAG + "Fake AndroidID: " + fakeAndroidId);
        XposedBridge.log(TAG + "Fake Serial: " + fakeSerial);

        try {
            XposedHelpers.findAndHookMethod("android.provider.Settings$Secure", cl,
                "getString", android.content.ContentResolver.class, String.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String name = (String) param.args[1];
                        if ("android_id".equals(name)) {
                            XposedBridge.log(TAG + "Spoofing android_id");
                            param.setResult(fakeAndroidId);
                        }
                    }
                });
        } catch (Throwable t) {
            XposedBridge.log(TAG + "hook Settings.Secure failed: " + t.getMessage());
        }

        try {
            XposedHelpers.findAndHookMethod("android.telephony.TelephonyManager", cl,
                "getDeviceId", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        XposedBridge.log(TAG + "Spoofing getDeviceId");
                        param.setResult(fakeImei);
                    }
                });
        } catch (Throwable t) {}

        try {
            XposedHelpers.findAndHookMethod("android.telephony.TelephonyManager", cl,
                "getDeviceId", int.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        XposedBridge.log(TAG + "Spoofing getDeviceId(slot)");
                        param.setResult(fakeImei);
                    }
                });
        } catch (Throwable t) {}

        try {
            XposedHelpers.findAndHookMethod("android.telephony.TelephonyManager", cl,
                "getImei", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        XposedBridge.log(TAG + "Spoofing getImei");
                        param.setResult(fakeImei);
                    }
                });
        } catch (Throwable t) {}

        try {
            XposedHelpers.findAndHookMethod("android.telephony.TelephonyManager", cl,
                "getImei", int.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        XposedBridge.log(TAG + "Spoofing getImei(slot)");
                        param.setResult(fakeImei);
                    }
                });
        } catch (Throwable t) {}

        try {
            XposedHelpers.findAndHookMethod("android.telephony.TelephonyManager", cl,
                "getSubscriberId", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        XposedBridge.log(TAG + "Spoofing getSubscriberId");
                        param.setResult("46000" + generateRandomDigit(10));
                    }
                });
        } catch (Throwable t) {}

        try {
            XposedHelpers.findAndHookMethod("android.os.Build", cl,
                "getSerial", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        XposedBridge.log(TAG + "Spoofing getSerial");
                        param.setResult(fakeSerial);
                    }
                });
        } catch (Throwable t) {}

        try {
            XposedHelpers.setStaticObjectField(android.os.Build.class, "SERIAL", fakeSerial);
        } catch (Throwable t) {}

        try {
            if (savedOrigFingerprint == null) {
                savedOrigFingerprint = android.os.Build.FINGERPRINT;
                savedOrigModel = android.os.Build.MODEL;
                savedOrigDevice = android.os.Build.DEVICE;
                savedOrigBoard = android.os.Build.BOARD;
                savedOrigHardware = android.os.Build.HARDWARE;
                savedOrigProduct = android.os.Build.PRODUCT;
                savedOrigDisplay = android.os.Build.DISPLAY;
                savedOrigHost = android.os.Build.HOST;
                savedOrigId = android.os.Build.ID;
            }

            String hex = generateRandomHex(8);
            String hex4 = generateRandomHex(4);

            XposedHelpers.setStaticObjectField(android.os.Build.class, "FINGERPRINT", savedOrigFingerprint + "." + hex);
            XposedBridge.log(TAG + "Spoofing Build.FINGERPRINT: " + savedOrigFingerprint + "." + hex);

            XposedHelpers.setStaticObjectField(android.os.Build.class, "MODEL", savedOrigModel + "-" + hex4);
            XposedHelpers.setStaticObjectField(android.os.Build.class, "DEVICE", savedOrigDevice + hex4);
            XposedHelpers.setStaticObjectField(android.os.Build.class, "BOARD", savedOrigBoard + hex4);
            XposedHelpers.setStaticObjectField(android.os.Build.class, "HARDWARE", savedOrigHardware + hex4);
            XposedHelpers.setStaticObjectField(android.os.Build.class, "PRODUCT", savedOrigProduct + "-" + hex4);
            XposedHelpers.setStaticObjectField(android.os.Build.class, "DISPLAY", savedOrigDisplay + "." + hex);
            XposedHelpers.setStaticObjectField(android.os.Build.class, "HOST", savedOrigHost + "-" + hex4);
            XposedHelpers.setStaticObjectField(android.os.Build.class, "ID", savedOrigId + hex4);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed to hook Build.* fields: " + t.getMessage());
        }
    }

    private String generateRandomHex(int length) {
        Random r = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(Integer.toHexString(r.nextInt(16)));
        }
        return sb.toString();
    }

    private String generateRandomDigit(int length) {
        Random r = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(r.nextInt(10));
        }
        return sb.toString();
    }

    /**
     * 判断是否需要对目标包名进行伪装/拦截
     *
     * 排除规则：
     * - 系统核心包名 (android, com.android.*, com.miui.*, com.xiaomi.*, com.google.*)
     * - 模块自身包名
     * - QQ音乐相关包名 (避免内嵌SDK读取不到真实metaData导致NPE)
     * - 登录相关包名 (微信、QQ等，修复退出登录后无法重新登录的bug)
     */
    private boolean shouldSpoof(String pkg, String hostApp) {
        if (pkg == null || pkg.isEmpty()) return false;
        if (pkg.equals("com.spoofer.packagename")) return false;
        if (pkg.equals("android")) return false;
        if (pkg.startsWith("com.android.")) return false;
        if (pkg.startsWith("com.miui.")) return false;
        if (pkg.startsWith("com.xiaomi.")) return false;
        if (pkg.startsWith("com.google.")) return false;
        if (pkg.equals("mark.via")) return false;
        if (pkg.equals(hostApp)) return false;
        // 排除QQ音乐相关包名，防止读取不到真实metaData导致NPE
        if (pkg.startsWith("com.tencent.qqmusiclite")) return false;
        if (pkg.startsWith("com.tencent.qqmusic")) return false;
        // 排除登录相关包名，修复退出登录后无法重新登录的bug
        if (pkg.equals("com.tencent.mm")) return false;                    // 微信
        if (pkg.equals("com.tencent.mobileqq")) return false;              // QQ
        if (pkg.startsWith("com.tencent.open")) return false;              // QQ开放平台SDK
        if (pkg.startsWith("com.tencent.connect")) return false;           // QQ互联SDK
        if (pkg.startsWith("com.tencent.tauth")) return false;             // QQ授权SDK
        if (pkg.startsWith("com.tencent.bugly")) return false;             // Bugly (登录流程可能用到)
        if (pkg.equals("com.sina.weibo")) return false;                    // 微博登录
        if (pkg.startsWith("com.sina.weibo")) return false;                // 微博SDK
        if (pkg.equals("com.eg.android.AlipayGphone")) return false;       // 支付宝登录
        return true;
    }
}
