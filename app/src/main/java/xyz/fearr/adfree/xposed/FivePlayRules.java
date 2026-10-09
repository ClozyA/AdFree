package xyz.fearr.adfree.xposed;

import android.app.Activity;
import android.view.ViewGroup;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/**
 * Rules derived from 5E 7.2.5; each hook validates its expected method signature.
 */
final class FivePlayRules {
    private final XposedInterface framework;

    FivePlayRules(XposedInterface framework) {
        this.framework = framework;
    }

    void install(ClassLoader loader) {
        installSplash(loader);
        installOverlay(loader);
    }

    private void installSplash(ClassLoader loader) {
        XposedInterface.HookHandle listHook = null;
        try {
            Class<?> adUtils = Class.forName("com.fiveplay.commonlibrary.utils.AdUtils", false, loader);
            Class<?> adList = Class.forName("com.fiveplay.commonlibrary.bean.AdListBean", false, loader);
            Class<?> listener = Class.forName("y2.b", false, loader);
            Method withList = adUtils.getDeclaredMethod("showSplashAd", Activity.class, ViewGroup.class, adList, listener);
            Method withoutList = adUtils.getDeclaredMethod("showSplashAd", Activity.class, ViewGroup.class, listener);
            requireVoid(withList);
            requireVoid(withoutList);
            // Reuse the original null-list path: reset isOver, cancel timer, notify completion once.
            XposedInterface.Invoker<?, Method> originalWithList = framework.getInvoker(withList)
                    .setType(XposedInterface.Invoker.Type.ORIGIN);
            listHook = framework.hook(withList).intercept(chain -> {
                if (chain.getArgs().get(3) == null) return chain.proceed();
                Object[] args = chain.getArgs().toArray();
                args[2] = null;
                HookLog.log(framework, Log.INFO, "AdFree", "5E splash/list: using original no-ad path");
                return chain.proceed(args);
            });
            framework.hook(withoutList)
                    // A callback exception must not trigger a second original call/reward-like notification.
                    .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH)
                    .intercept(chain -> {
                        if (chain.getArgs().get(2) == null) return chain.proceed();
                        HookLog.log(framework, Log.INFO, "AdFree", "5E splash/request: using original no-ad path");
                        try {
                            return originalWithList.invoke(chain.getThisObject(),
                                    chain.getArgs().get(0), chain.getArgs().get(1), null, chain.getArgs().get(2));
                        } catch (InvocationTargetException error) {
                            throw error.getCause();
                        }
                    });
            HookLog.log(framework, Log.INFO, "AdFree", "Installed 5E splash rules: both overloads");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            if (listHook != null) listHook.unhook();
            HookLog.log(framework, Log.WARN, "AdFree", "5E splash rules unavailable; original behavior retained", error);
        }
    }

    private void installOverlay(ClassLoader loader) {
        try {
            Class<?> type = Class.forName("com.fiveplay.reward.service.AdFlowServiceImpl", false, loader);
            Class<?> activity = Class.forName("androidx.appcompat.app.AppCompatActivity", false, loader);
            Class<?> data = Class.forName("com.fiveplay.commonlibrary.bean.SessionAdData", false, loader);
            Method show = type.getDeclaredMethod("Z0", activity, data);
            requireVoid(show);
            framework.hook(show).intercept(chain -> {
                HookLog.log(framework, Log.INFO, "AdFree", "5E match overlay: blocked creation");
                return null;
            });
            HookLog.log(framework, Log.INFO, "AdFree", "Installed 5E match overlay rule");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            HookLog.log(framework, Log.WARN, "AdFree", "5E match overlay rule unavailable; original behavior retained", error);
        }
    }

    private static void requireVoid(Method method) throws NoSuchMethodException {
        if (method.getReturnType() != void.class)
            throw new NoSuchMethodException("Unexpected return type: " + method);
    }
}
