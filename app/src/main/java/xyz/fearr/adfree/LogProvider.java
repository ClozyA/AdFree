package xyz.fearr.adfree;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;

/** Write-only collection endpoint. Logs are never exposed to other applications. */
public final class LogProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        if (!BuildConfig.APPLICATION_ID.concat(".logs").equals(uri.getAuthority())
                || !"/entries".equals(uri.getPath())) throw new IllegalArgumentException("Unknown log URI");
        // getCallingPackage() is verified against the Binder caller's UID by Android.
        String caller = getCallingPackage();
        if (!isAllowed(caller, providerContext().getPackageManager().getPackagesForUid(Binder.getCallingUid()))) {
            throw new SecurityException("Caller cannot write AdFree logs");
        }
        String message = values == null ? null : values.getAsString("message");
        if (message == null || message.length() > 16_384) throw new IllegalArgumentException("Invalid log entry");
        Integer priority = values.getAsInteger("priority");
        String level = priority != null && priority >= 6 ? "ERROR"
                : priority != null && priority >= 5 ? "WARN" : "INFO";
        ((ModuleApplication) providerContext().getApplicationContext()).logs()
                .record(level, "hook:" + caller, message, null);
        return uri;
    }

    static boolean isAllowed(String caller, String[] packages) {
        if (caller == null) return false;
        if (!BuildConfig.APPLICATION_ID.equals(caller) && TargetCatalog.find(caller) == null) return false;
        if (packages != null) for (String name : packages) if (caller.equals(name)) return true;
        return false;
    }

    private android.content.Context providerContext() {
        if (getContext() == null) throw new IllegalStateException("Provider context unavailable");
        return getContext();
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        throw new SecurityException("Logs are private");
    }
    @Override public String getType(Uri uri) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new SecurityException("Logs are private"); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new SecurityException("Logs are private"); }
}
