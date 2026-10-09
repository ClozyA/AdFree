package xyz.fearr.adfree.xposed;

import io.github.libxposed.api.XposedInterface;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class HookLogTest {
    @Test
    public void preservesFrameworkLogArgumentsBeforeContextAvailable() throws Exception {
        List<Object[]> calls = new ArrayList<>();
        XposedInterface framework = (XposedInterface) Proxy.newProxyInstance(
                XposedInterface.class.getClassLoader(), new Class<?>[]{XposedInterface.class},
                (proxy, method, args) -> { if (method.getName().equals("log")) calls.add(args); return null; });
        IllegalStateException error = new IllegalStateException("probe");
        HookLog.log(framework, 4, "AdFree", "event");
        HookLog.log(framework, 5, "AdFree", "failure", error);
        assertArrayEquals(new Object[]{4, "AdFree", "event"}, calls.get(0));
        assertArrayEquals(new Object[]{5, "AdFree", "failure", error}, calls.get(1));
        // Startup buffering is bounded even if the target never reaches attachBaseContext.
        for (int i = 0; i < 1000; i++) HookLog.log(framework, 4, "AdFree", "early " + i);
        assertEquals(1002, calls.size());
        java.lang.reflect.Field pending = HookLog.class.getDeclaredField("pending");
        pending.setAccessible(true);
        assertEquals(128, ((java.util.Collection<?>) pending.get(null)).size());
    }
}
