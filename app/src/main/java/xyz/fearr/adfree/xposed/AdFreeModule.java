package xyz.fearr.adfree.xposed;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import io.github.libxposed.api.XposedModule;
import xyz.fearr.adfree.BuildConfig;

/** Wait for packed-app loading before installing version-specific rules. */
public final class AdFreeModule extends XposedModule {
    private static final String TAG = "AdFree";
    private String processName;
    private final Set<ClassLoader> inspectedLoaders = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<ClassLoader> configuredLoaders = Collections.newSetFromMap(new IdentityHashMap<>());

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        processName = param.getProcessName();
        log(Log.INFO, TAG, "Module loaded: process=" + processName + ", API=" + getApiVersion()
                + ", module=" + BuildConfig.VERSION_NAME);
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        String packageName = param.getPackageName();
        if (!isTarget(packageName) || !packageName.equals(processName)) return;
        log(Log.INFO, TAG, "Package ready: " + packageName);
        inspectLoader(packageName, "package-ready", param.getClassLoader());
        String applicationName = param.getApplicationInfo().className;
        if (applicationName == null) return;
        try {
            Class<?> applicationClass = Class.forName(applicationName, false, param.getClassLoader());
            Method attach = applicationClass.getDeclaredMethod("attachBaseContext", Context.class);
            hook(attach).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    Context context = (Context) chain.getArgs().get(0);
                    inspectLoader(packageName, "after-attach-context", context.getClassLoader());
                    configure(packageName, context, context.getClassLoader());
                    Context application = (Context) chain.getThisObject();
                    inspectLoader(packageName, "after-attach-application", application.getClassLoader());
                    configure(packageName, context, application.getClassLoader());
                } catch (RuntimeException | LinkageError error) {
                    log(Log.WARN, TAG, "Loader inspection failed: " + packageName, error);
                }
                return result;
            });
            log(Log.INFO, TAG, "Watching " + applicationName + ".attachBaseContext");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            log(Log.WARN, TAG, "Cannot watch packed Application: " + applicationName, error);
        }
    }

    private void configure(String packageName, Context context, ClassLoader loader) {
        if (loader == null) return;
        synchronized (configuredLoaders) {
            if (!configuredLoaders.add(loader)) return;
        }
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            long code = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            log(Log.INFO, TAG, "Target version: " + packageName + " " + info.versionName + " (" + code + ")");
            if ("com.fiveplay".equals(packageName)) {
                if ("7.2.5".equals(info.versionName) && code == 609181924L) {
                    new FivePlayRules(this).install(loader);
                    new AggressiveAdRules(this, packageName).install(loader);
                } else {
                    log(Log.WARN, TAG, "5E version differs from inspected APK; ad rules skipped");
                }
            } else {
                inspectXbudMembers(loader);
                if ("2.6.8".equals(info.versionName) && code == 150L) {
                    new XbudRules(this).install(loader);
                    new AggressiveAdRules(this, packageName).install(loader);
                } else {
                    log(Log.WARN, TAG, "Xbud version differs from inspected APK; skip rule and tracing disabled");
                }
            }
        } catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException | LinkageError error) {
            log(Log.WARN, TAG, "Cannot configure target: " + packageName, error);
        }
    }

    private void inspectXbudMembers(ClassLoader loader) {
        String[] classes = {"run.xbud.android.mvp.ui.other.SplashActivity",
                "run.xbud.android.mvp.ui.sport.run.RunStartActivity",
                "run.xbud.android.mvp.ui.other.XBDFlutterActivity"};
        for (String name : classes) {
            try {
                Class<?> type = Class.forName(name, false, loader);
                Method[] methods = type.getDeclaredMethods();
                Arrays.sort(methods, Comparator.comparing(Method::toString));
                for (int i = 0; i < Math.min(methods.length, 80); i++) {
                    log(Log.INFO, TAG, "Xbud method: " + methods[i]);
                }
                Field[] fields = type.getDeclaredFields();
                Arrays.sort(fields, Comparator.comparing(Field::getName));
                for (int i = 0; i < Math.min(fields.length, 40); i++) {
                    log(Log.INFO, TAG, "Xbud field: " + name + "." + fields[i].getName()
                            + " : " + fields[i].getType().getName());
                }
                log(Log.INFO, TAG, "Xbud inventory: " + name + ", methods=" + methods.length
                        + ", fields=" + fields.length + ", limits=80/40; values not collected");
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                log(Log.WARN, TAG, "Xbud inventory unavailable: " + name, error);
            }
        }
    }

    private void inspectLoader(String packageName, String stage, ClassLoader loader) {
        if (loader == null) return;
        // A shell can mutate an existing loader during attachBaseContext; re-probe after attach.
        if (!"package-ready".equals(stage)) {
            synchronized (inspectedLoaders) {
                if (!inspectedLoaders.add(loader)) return;
            }
        }
        String[] classes = "com.fiveplay".equals(packageName)
                ? new String[]{"com.fiveplay.commonlibrary.utils.AdUtils",
                    "com.fiveplay.commonlibrary.view.ad.AdNativeView",
                    "com.fiveplay.reward.service.AdFlowServiceImpl"}
                : new String[]{"run.xbud.android.mvp.ui.other.SplashActivity",
                    "run.xbud.android.mvp.ui.other.MainActivity"};
        for (String name : classes) {
            try {
                Class<?> type = Class.forName(name, false, loader);
                log(Log.INFO, TAG, stage + ": found " + name + ", loader="
                        + type.getClassLoader().getClass().getName());
            } catch (ClassNotFoundException error) {
                log(Log.INFO, TAG, stage + ": not yet available " + name);
            } catch (RuntimeException | LinkageError error) {
                log(Log.WARN, TAG, stage + ": cannot resolve " + name, error);
            }
        }
    }

    private static boolean isTarget(String packageName) {
        return "run.xbud.android".equals(packageName) || "com.fiveplay".equals(packageName);
    }
}
