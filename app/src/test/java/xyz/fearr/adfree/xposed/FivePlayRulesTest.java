package xyz.fearr.adfree.xposed;

import android.app.Activity;
import android.view.ViewGroup;
import com.fiveplay.commonlibrary.bean.AdListBean;
import com.fiveplay.commonlibrary.bean.SessionAdData;
import com.fiveplay.commonlibrary.utils.AdUtils;
import com.fiveplay.reward.service.AdFlowServiceImpl;
import androidx.appcompat.app.AppCompatActivity;
import io.github.libxposed.api.XposedInterface;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public final class FivePlayRulesTest {
    @Test
    public void requestPathCompletesOnceWithoutRequestAndCancelsOldTimer() throws Throwable {
        Harness harness = installed();
        AdUtils ads = new AdUtils();
        int[] completed = {0};
        y2.b callback = (success, bean) -> { assertFalse(success); assertNull(bean); completed[0]++; };
        harness.call(requestMethod(), ads, null, null, callback);
        assertEquals(1, completed[0]);
        assertTrue(ads.isOver);
        assertFalse(ads.timerActive);
        assertEquals(0, ads.requests);
        assertEquals(0, ads.displays);
    }

    @Test
    public void configuredListCannotDisplayAndCallerArgumentsArePreserved() throws Throwable {
        Harness harness = installed();
        AdUtils ads = new AdUtils();
        AdListBean configuredAds = new AdListBean();
        int[] completed = {0};
        Object[] args = {null, null, configuredAds, (y2.b) (success, bean) -> completed[0]++};
        harness.call(listMethod(), ads, args);
        assertSame(configuredAds, args[2]);
        assertEquals(1, completed[0]);
        assertEquals(0, ads.displays);
        assertTrue(ads.isOver);
        assertFalse(ads.timerActive);
    }

    @Test
    public void callbackFailureIsNotWrappedOrReplayed() throws Throwable {
        Harness harness = installed();
        int[] calls = {0};
        IllegalStateException failure = new IllegalStateException("callback failed");
        try {
            harness.call(requestMethod(), new AdUtils(), null, null, (y2.b) (success, bean) -> {
                calls[0]++;
                throw failure;
            });
            fail("Expected original callback failure");
        } catch (IllegalStateException actual) {
            assertSame(failure, actual);
        }
        assertEquals(1, calls[0]);
        assertEquals(XposedInterface.ExceptionMode.PASSTHROUGH, harness.modes.get(requestMethod()));
    }

    @Test
    public void partialSplashRegistrationRollsBackAndOtherRuleSurvives() throws Exception {
        Harness harness = new Harness();
        harness.failRegistration = requestMethod();
        new FivePlayRules(harness.framework()).install(getClass().getClassLoader());
        assertFalse(harness.hooks.containsKey(listMethod()));
        assertFalse(harness.hooks.containsKey(requestMethod()));
        assertTrue(harness.hooks.containsKey(overlayMethod()));
    }

    @Test
    public void overlayCreationIsSkippedAndRewardEntryRemainsOriginal() throws Throwable {
        Harness harness = installed();
        AdFlowServiceImpl flow = new AdFlowServiceImpl();
        harness.call(overlayMethod(), flow, null, new SessionAdData());
        flow.l(true);
        assertEquals(0, flow.creations);
        Method reward = AdUtils.class.getDeclaredMethod("showReward", Activity.class, y2.b.class, String.class);
        assertFalse(harness.hooks.containsKey(reward));
        AdUtils ads = new AdUtils();
        harness.call(reward, ads, null, null, "reward-slot");
        assertEquals(1, ads.rewardRequests);
    }

    private Harness installed() {
        Harness harness = new Harness();
        new FivePlayRules(harness.framework()).install(getClass().getClassLoader());
        assertEquals(3, harness.hooks.size());
        return harness;
    }

    private static Method requestMethod() throws NoSuchMethodException {
        return AdUtils.class.getDeclaredMethod("showSplashAd", Activity.class, ViewGroup.class, y2.b.class);
    }

    private static Method listMethod() throws NoSuchMethodException {
        return AdUtils.class.getDeclaredMethod("showSplashAd", Activity.class, ViewGroup.class, AdListBean.class, y2.b.class);
    }

    private static Method overlayMethod() throws NoSuchMethodException {
        return AdFlowServiceImpl.class.getDeclaredMethod("Z0", AppCompatActivity.class, SessionAdData.class);
    }

    /** Minimal hook-chain driver: invokes original fixtures, without simulating ART injection. */
    private static final class Harness {
        final Map<Method, XposedInterface.Hooker> hooks = new HashMap<>();
        final Map<Method, XposedInterface.ExceptionMode> modes = new HashMap<>();
        Method failRegistration;

        XposedInterface framework() {
            return proxy(XposedInterface.class, (instance, method, args) -> switch (method.getName()) {
                case "log" -> null;
                case "hook" -> builder((Method) args[0]);
                case "getInvoker" -> invoker((Method) args[0]);
                default -> throw new UnsupportedOperationException(method.toString());
            });
        }

        private XposedInterface.HookBuilder builder(Method origin) {
            return proxy(XposedInterface.HookBuilder.class, (instance, method, args) -> switch (method.getName()) {
                case "setExceptionMode" -> { modes.put(origin, (XposedInterface.ExceptionMode) args[0]); yield instance; }
                case "setPriority" -> instance;
                case "intercept" -> {
                    if (origin.equals(failRegistration)) throw new IllegalStateException("registration failed");
                    hooks.put(origin, (XposedInterface.Hooker) args[0]);
                    yield proxy(XposedInterface.HookHandle.class, (handle, operation, ignored) -> {
                        if (operation.getName().equals("unhook")) { hooks.remove(origin); return null; }
                        throw new UnsupportedOperationException(operation.toString());
                    });
                }
                default -> throw new UnsupportedOperationException(method.toString());
            });
        }

        private XposedInterface.Invoker<?, Method> invoker(Method origin) {
            return proxy(XposedInterface.Invoker.class, (instance, method, args) -> switch (method.getName()) {
                case "setType" -> { assertEquals(XposedInterface.Invoker.Type.ORIGIN, args[0]); yield instance; }
                case "invoke" -> origin.invoke(args[0], (Object[]) args[1]);
                default -> throw new UnsupportedOperationException(method.toString());
            });
        }

        Object call(Method origin, Object target, Object... args) throws Throwable {
            XposedInterface.Hooker hook = hooks.get(origin);
            if (hook == null) return original(origin, target, args);
            return hook.intercept(proxy(XposedInterface.Chain.class, (instance, method, supplied) -> switch (method.getName()) {
                case "getArgs" -> Arrays.asList(args);
                case "getThisObject" -> target;
                case "getExecutable" -> origin;
                case "proceed" -> original(origin, target, supplied == null ? args : (Object[]) supplied[0]);
                default -> throw new UnsupportedOperationException(method.toString());
            }));
        }

        private static Object original(Method origin, Object target, Object[] args) throws Throwable {
            try { return origin.invoke(target, args); }
            catch (InvocationTargetException error) { throw error.getCause(); }
        }

        @SuppressWarnings("unchecked")
        private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
            return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
        }
    }
}
