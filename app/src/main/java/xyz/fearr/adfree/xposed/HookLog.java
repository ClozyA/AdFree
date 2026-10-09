package xyz.fearr.adfree.xposed;

import android.content.Context;
import android.content.ContentValues;
import android.net.Uri;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ref.WeakReference;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.github.libxposed.api.XposedInterface;
import xyz.fearr.adfree.BuildConfig;

/** Preserve framework logging and asynchronously copy bounded entries to the module app. */
final class HookLog {
    private record Entry(int priority, String message) { }
    private static final ArrayDeque<Entry> pending = new ArrayDeque<>();
    private static WeakReference<Context> context;
    private static ThreadPoolExecutor sender;

    static synchronized void initialize(Context base) {
        if (context != null && context.get() != null) return;
        try {
            sender = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(256),
                runnable -> {
                    Thread thread = new Thread(runnable, "AdFree-hook-log");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.DiscardPolicy());
            context = new WeakReference<>(base);
            while (!pending.isEmpty()) send(pending.removeFirst());
        } catch (RuntimeException ignored) {
            // A logging failure must not skip rule installation in attachBaseContext.
        }
    }

    static void log(XposedInterface framework, int priority, String tag, String message) {
        framework.log(priority, tag, message);
        try { collect(priority, message, null); } catch (RuntimeException ignored) { }
    }

    static void log(XposedInterface framework, int priority, String tag, String message, Throwable error) {
        framework.log(priority, tag, message, error);
        try { collect(priority, message, error); } catch (RuntimeException ignored) { }
    }

    private static synchronized void collect(int priority, String message, Throwable error) {
        StringWriter trace = new StringWriter();
        if (error != null) error.printStackTrace(new PrintWriter(trace));
        String text = Instant.now() + " " + message + (error == null ? "" : "\n" + trace);
        if (text.length() > 16_384) text = text.substring(0, 16_370) + " [truncated]";
        Entry entry = new Entry(priority, text);
        if (context == null || context.get() == null) {
            if (pending.size() == 128) pending.removeFirst();
            pending.addLast(entry);
        } else {
            send(entry);
        }
    }

    private static void send(Entry entry) {
        sender.execute(() -> {
            try {
                ContentValues values = new ContentValues();
                values.put("priority", entry.priority());
                values.put("message", entry.message());
                Context current = context.get();
                if (current == null) return;
                current.getContentResolver().insert(
                        Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".logs/entries"), values);
            } catch (RuntimeException ignored) {
                // Logging must never disrupt the target app. Framework logs remain available.
            }
        });
    }
}
