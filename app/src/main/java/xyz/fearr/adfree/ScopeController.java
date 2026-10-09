package xyz.fearr.adfree;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Scope switches always reflect the framework's readback, not a local preference.
 */
public final class ScopeController {
    public interface Gateway {
        Set<String> readScope();

        void requestScope(String packageName, Callback callback);

        void removeScope(String packageName);
    }

    public interface Callback {
        void approved();

        void failed(String message);
    }

    public record Snapshot(boolean available, Set<String> packages, Set<String> pending,
                           Map<String, String> errors, String readError) {
    }

    private final Supplier<Gateway> gateway;
    private final Executor executor;
    private final Runnable changed;
    private final Map<String, Object> operations = new ConcurrentHashMap<>();
    private final Map<String, String> errors = new ConcurrentHashMap<>();

    public ScopeController(Supplier<Gateway> gateway, Executor executor, Runnable changed) {
        this.gateway = gateway;
        this.executor = executor;
        this.changed = changed;
    }

    public Snapshot snapshot() {
        Gateway current = gateway.get();
        try {
            return new Snapshot(current != null,
                    current == null ? Collections.emptySet() : new HashSet<>(current.readScope()),
                    new HashSet<>(operations.keySet()), new HashMap<>(errors), null);
        } catch (RuntimeException error) {
            return new Snapshot(false, Collections.emptySet(), new HashSet<>(operations.keySet()),
                    new HashMap<>(errors), "无法读取作用域，请刷新后重试。");
        }
    }

    public void setEnabled(String packageName, boolean enabled) {
        if (TargetCatalog.find(packageName) == null)
            throw new IllegalArgumentException("Unsupported target");
        Object token = new Object();
        if (operations.putIfAbsent(packageName, token) != null) return;
        errors.remove(packageName);
        changed.run();
        executor.execute(() -> {
            Gateway current = gateway.get();
            if (current == null) {
                finish(packageName, token, "框架未连接，未修改作用域。");
                return;
            }
            try {
                if (enabled) {
                    current.requestScope(packageName, new Callback() {
                        @Override
                        public void approved() {
                            executor.execute(() -> verify(current, packageName, token, true));
                        }

                        @Override
                        public void failed(String message) {
                            finish(packageName, token, message.trim().isEmpty() ? "框架未批准此请求。" : message);
                        }
                    });
                } else {
                    current.removeScope(packageName);
                    verify(current, packageName, token, false);
                }
            } catch (RuntimeException error) {
                finish(packageName, token, "作用域修改失败，请在 LSPosed 中检查授权。");
            }
        });
    }

    private void verify(Gateway expected, String packageName, Object token, boolean enabled) {
        try {
            if (gateway.get() != expected) {
                finish(packageName, token, "框架连接已变化，请刷新后重试。");
            } else {
                boolean applied = expected.readScope().contains(packageName) == enabled;
                finish(packageName, token, applied ? null : "框架未应用此修改，请刷新或在 LSPosed 中检查。");
            }
        } catch (RuntimeException error) {
            finish(packageName, token, "无法确认修改结果，请刷新后检查作用域。");
        }
    }

    private void finish(String packageName, Object token, String error) {
        if (!operations.remove(packageName, token)) return;
        if (error == null) errors.remove(packageName);
        else errors.put(packageName, error);
        changed.run();
    }

    public void connectionChanged() {
        for (Map.Entry<String, Object> entry : operations.entrySet()) {
            finish(entry.getKey(), entry.getValue(), "框架连接已变化，请重新检查作用域。");
        }
    }
}
