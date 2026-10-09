package xyz.fearr.adfree;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public final class ModuleApplication extends Application {
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<Runnable> observers = new HashSet<>();
    private volatile XposedService service;
    private volatile ScopeController.Gateway scopeGateway;
    public final ExecutorService worker = Executors.newSingleThreadExecutor();
    public final ScopeController scopes = new ScopeController(() -> scopeGateway, worker, this::notifyObservers);
    private AppLogs logs;

    public synchronized AppLogs logs() {
        if (logs == null) logs = new AppLogs(this);
        return logs;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        logs().record("INFO", "app", "Application started: " + BuildConfig.VERSION_NAME, null);
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override
            public void onServiceBind(XposedService boundService) {
                service = boundService;
                logs().record("INFO", "app", "Framework service connected", null);
                scopeGateway = new ScopeController.Gateway() {
                    @Override
                    public Set<String> readScope() {
                        return new HashSet<>(boundService.getScope());
                    }

                    @Override
                    public void removeScope(String packageName) {
                        boundService.removeScope(Collections.singletonList(packageName));
                    }

                    @Override
                    public void requestScope(String packageName, ScopeController.Callback callback) {
                        boundService.requestScope(Collections.singletonList(packageName), new XposedService.OnScopeEventListener() {
                            @Override
                            public void onScopeRequestApproved(java.util.List<String> approved) {
                                callback.approved();
                            }

                            @Override
                            public void onScopeRequestFailed(String message) {
                                callback.failed(message);
                            }
                        });
                    }
                };
                scopes.connectionChanged();
                notifyObservers();
            }

            @Override
            public void onServiceDied(XposedService deadService) {
                if (service == deadService) {
                    service = null;
                    logs().record("WARN", "app", "Framework service disconnected", null);
                    scopeGateway = null;
                    scopes.connectionChanged();
                    notifyObservers();
                }
            }
        });
    }

    public String frameworkStatus() {
        FrameworkInfo info = frameworkInfo();
        return info.title() + "\n" + info.details();
    }

    public record FrameworkInfo(String title, String details, boolean connected) {
    }

    public FrameworkInfo frameworkInfo() {
        XposedService current = service;
        if (current == null) return new FrameworkInfo(getString(R.string.framework_waiting_title),
                getString(R.string.framework_waiting), false);
        try {
            int api = current.getApiVersion();
            return new FrameworkInfo(getString(api >= 102 ? R.string.framework_ready_title : R.string.framework_old_title),
                    getString(api >= 102 ? R.string.framework_connected : R.string.framework_old,
                            current.getFrameworkName(), current.getFrameworkVersion(), api), api >= 102);
        } catch (RuntimeException error) {
            Log.w("AdFree", "Cannot read framework status", error);
            logs().record("ERROR", "app", "Cannot read framework status", error);
            return new FrameworkInfo(getString(R.string.framework_error_title), getString(R.string.framework_error), false);
        }
    }

    public void observe(Runnable observer) {
        observers.add(observer);
    }

    public void removeObserver(Runnable observer) {
        observers.remove(observer);
    }

    private void notifyObservers() {
        mainHandler.post(() -> {
            for (Runnable observer : new HashSet<>(observers)) observer.run();
        });
    }
}
