package xyz.fearr.adfree;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class AppLogs {
    public interface ExportCallback {
        void finished(boolean success);
    }

    private final Context context;
    private final SharedPreferences preferences;
    private final LogStore store;
    private volatile boolean enabled;
    private final ThreadPoolExecutor writer = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(256), runnable -> new Thread(runnable, "AdFree-log-writer"),
            new ThreadPoolExecutor.AbortPolicy());

    public AppLogs(Context context) {
        this.context = context;
        preferences = context.getSharedPreferences("logging", Context.MODE_PRIVATE);
        enabled = preferences.getBoolean("enabled", true);
        store = new LogStore(new File(context.getFilesDir(), "logs"), 512 * 1024, 3);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        enabled = value;
        preferences.edit().putBoolean("enabled", value).apply();
        if (value) record("INFO", "app", "Log storage enabled", null);
    }

    public void record(String level, String source, String message, Throwable error) {
        if (!enabled) return;
        StringWriter trace = new StringWriter();
        if (error != null) error.printStackTrace(new PrintWriter(trace));
        String entry = Instant.now() + " [" + level + "] [" + source + "] " + message
                + (error == null ? "" : "\n" + trace);
        if (entry.length() > 16_384) entry = entry.substring(0, 16_384) + " [truncated]";
        final String bounded = entry;
        try {
            writer.execute(() -> {
                try {
                    store.append(bounded);
                } catch (IOException failure) {
                    Log.w("AdFree", "Cannot save log", failure);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException full) {
            Log.w("AdFree", "Log queue full; entry dropped");
        }
    }

    // Runs after accepted log writes; no storage permission is needed for the user's document URI.
    public void export(Uri uri, String environment, ExportCallback callback) {
        try {
            writer.execute(() -> {
                boolean success = false;
                try (OutputStream output = context.getContentResolver().openOutputStream(uri, "wt")) {
                    if (output == null) throw new IOException("Document output unavailable");
                    store.exportTo(output, environment);
                    output.flush();
                    success = true;
                } catch (IOException | RuntimeException failure) {
                    success = false;
                    record("ERROR", "app", "Log export failed", failure);
                }
                callback.finished(success);
            });
        } catch (java.util.concurrent.RejectedExecutionException full) {
            callback.finished(false);
        }
    }
}
