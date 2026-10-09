package xyz.fearr.adfree;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** A bounded UTF-8 journal. All readers and writers share the same lock. */
public final class LogStore {
    private final File directory;
    private final int maxBytes;
    private final int fileCount;

    public LogStore(File directory, int maxBytes, int fileCount) {
        if (maxBytes < 64 || fileCount < 1) throw new IllegalArgumentException("Invalid log limits");
        this.directory = directory;
        this.maxBytes = maxBytes;
        this.fileCount = fileCount;
    }

    private File file(int index) {
        return new File(directory, "adfree-" + index + ".log");
    }

    public synchronized void append(String entry) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create log directory");
        // Limit characters before encoding, then truncate only at complete Unicode code points.
        String text = entry.substring(0, Math.min(entry.length(), maxBytes)) + "\n";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxBytes) {
            int end = maxBytes - 1;
            while ((bytes[end] & 0xc0) == 0x80) end--;
            byte[] truncated = new byte[end + 1];
            System.arraycopy(bytes, 0, truncated, 0, end);
            truncated[end] = '\n';
            bytes = truncated;
        }
        if (file(0).length() + bytes.length > maxBytes) {
            File oldest = file(fileCount - 1);
            if (oldest.exists() && !oldest.delete()) throw new IOException("Cannot rotate oldest log");
            for (int i = fileCount - 2; i >= 0; i--) {
                File source = file(i);
                if (source.exists() && !source.renameTo(file(i + 1))) throw new IOException("Cannot rotate log");
            }
        }
        try (FileOutputStream output = new FileOutputStream(file(0), true)) {
            output.write(bytes);
        }
    }

    public synchronized void exportTo(OutputStream output, String environment) throws IOException {
        output.write((environment + "\n\n=== AdFree logs (oldest first) ===\n").getBytes(StandardCharsets.UTF_8));
        boolean found = false;
        byte[] buffer = new byte[8192];
        for (int i = fileCount - 1; i >= 0; i--) {
            if (!file(i).isFile()) continue;
            found = true;
            try (FileInputStream input = new FileInputStream(file(i))) {
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            }
        }
        if (!found) output.write("No saved logs.\n".getBytes(StandardCharsets.UTF_8));
    }
}
