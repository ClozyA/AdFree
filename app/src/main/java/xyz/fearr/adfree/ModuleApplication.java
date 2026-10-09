package xyz.fearr.adfree;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public final class ModuleApplication extends Application {
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<Runnable> observers = new HashSet<>();
    private volatile XposedService service;

    @Override
    public void onCreate() {
        super.onCreate();
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override
            public void onServiceBind(XposedService boundService) {
                service = boundService;
                notifyObservers();
            }

            @Override
            public void onServiceDied(XposedService deadService) {
                if (service == deadService) {
                    service = null;
                    notifyObservers();
                }
            }
        });
    }

    public String frameworkStatus() {
        FrameworkInfo info = frameworkInfo();
        return info.title() + "\n" + info.details();
    }

    public record FrameworkInfo(String title, String details) {}

    public FrameworkInfo frameworkInfo() {
        XposedService current = service;
        if (current == null) return new FrameworkInfo(getString(R.string.framework_waiting_title),
                getString(R.string.framework_waiting));
        try {
            int api = current.getApiVersion();
            return new FrameworkInfo(getString(api >= 102 ? R.string.framework_ready_title : R.string.framework_old_title),
                    getString(api >= 102 ? R.string.framework_connected : R.string.framework_old,
                            current.getFrameworkName(), current.getFrameworkVersion(), api));
        } catch (RuntimeException error) {
            Log.w("AdFree", "Cannot read framework status", error);
            return new FrameworkInfo(getString(R.string.framework_error_title), getString(R.string.framework_error));
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
