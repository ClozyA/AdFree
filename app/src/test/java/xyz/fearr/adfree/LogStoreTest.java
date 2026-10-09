package xyz.fearr.adfree;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class LogStoreTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void rotatesWithinBudgetAndExportsOldestFirstAcrossRestart() throws Exception {
        File directory = temporary.newFolder();
        LogStore store = new LogStore(directory, 64, 3);
        for (int i = 0; i < 5; i++) store.append("entry-" + i + "-" + "x".repeat(48));
        File[] files = directory.listFiles();
        assertNotNull(files);
        assertEquals(3, files.length);
        for (File file : files) assertTrue(file.length() <= 64);
        String exported = export(new LogStore(directory, 64, 3));
        assertFalse(exported.contains("entry-0"));
        assertFalse(exported.contains("entry-1"));
        assertTrue(exported.indexOf("entry-2") < exported.indexOf("entry-3"));
        assertTrue(exported.indexOf("entry-3") < exported.indexOf("entry-4"));
        assertTrue(exported.startsWith("environment\n"));
    }

    @Test
    public void truncationPreservesUtf8AndByteBudget() throws Exception {
        File directory = temporary.newFolder();
        new LogStore(directory, 64, 3).append("中文🙂".repeat(100));
        byte[] bytes = Files.readAllBytes(new File(directory, "adfree-0.log").toPath());
        assertTrue(bytes.length <= 64);
        String text = new String(bytes, StandardCharsets.UTF_8);
        assertFalse(text.contains("\uFFFD"));
        assertTrue(text.endsWith("\n"));
    }

    @Test
    public void emptyExportIsExplicit() throws Exception {
        assertTrue(export(new LogStore(temporary.newFolder(), 64, 3)).contains("No saved logs."));
    }

    @Test
    public void concurrentWritesRemainComplete() throws Exception {
        LogStore store = new LogStore(temporary.newFolder(), 64 * 1024, 3);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            java.util.List<java.util.concurrent.Future<?>> tasks = new java.util.ArrayList<>();
            for (int i = 0; i < 100; i++) {
                final int id = i;
                tasks.add(executor.submit(() -> {
                    try { store.append("line-" + id); }
                    catch (java.io.IOException error) { throw new RuntimeException(error); }
                }));
            }
            for (java.util.concurrent.Future<?> task : tasks) task.get();
            String text = export(store);
            for (int i = 0; i < 100; i++) assertTrue(text.contains("line-" + i + "\n"));
        } finally {
            executor.shutdownNow();
        }
    }

    private static String export(LogStore store) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        store.exportTo(output, "environment");
        return output.toString(StandardCharsets.UTF_8);
    }
}
