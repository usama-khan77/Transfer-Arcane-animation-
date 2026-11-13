package com.usama.aianimationstudio;

import android.content.Context;
import android.os.StatFs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;

/** Downloads the official MNN Sana Edit V2 model once for offline use. */
public final class SanaModelManager {
    private static final String BASE = "https://huggingface.co/taobao-mnn/MNN-Sana-Edit-V2/resolve/main/";
    private static final String[] FILES = {
            "config.json",
            "connector.mnn", "connector.mnn.weight",
            "projector.mnn", "projector.mnn.weight",
            "transformer.mnn", "transformer.mnn.weight",
            "vae_decoder.mnn", "vae_decoder.mnn.weight",
            "vae_encoder.mnn", "vae_encoder.mnn.weight",
            "llm/config.json", "llm/export_args.json", "llm/llm.mnn",
            "llm/llm.mnn.json", "llm/llm.mnn.weight", "llm/llm_config.json",
            "llm/meta_queries.mnn", "llm/tokenizer.txt"
    };

    public interface Listener {
        void onProgress(String message);
    }

    private SanaModelManager() {}

    public static File getModelDirectory(Context context) {
        return new File(context.getFilesDir(), "MNN-Sana-Edit-V2");
    }

    public static boolean isModelReady(Context context) {
        File root = getModelDirectory(context);
        for (String path : FILES) {
            File file = new File(root, path);
            if (!file.isFile() || file.length() == 0) return false;
        }
        return true;
    }

    public static void download(Context context, Listener listener) throws Exception {
        File root = getModelDirectory(context);
        if (!root.exists() && !root.mkdirs()) throw new Exception("Cannot create model folder");

        StatFs stat = new StatFs(context.getFilesDir().getAbsolutePath());
        if (stat.getAvailableBytes() < 2_000_000_000L) {
            throw new Exception("At least 2 GB free storage is recommended for the 1.66 GB model download");
        }

        for (int i = 0; i < FILES.length; i++) {
            String path = FILES[i];
            File target = new File(root, path);
            File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new Exception("Cannot create folder for " + path);
            }

            long expected = getRemoteLength(path);
            if (target.isFile() && expected > 0 && target.length() == expected) {
                notify(listener, "Model file " + (i + 1) + "/" + FILES.length + " already downloaded");
                continue;
            }
            downloadOne(path, target, expected, i, listener);
        }
        notify(listener, "Checking model files…");
        if (!isModelReady(context)) throw new Exception("Model download is incomplete. Please retry to resume it.");
    }

    private static long getRemoteLength(String path) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(BASE + path).openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(20000);
            connection.setInstanceFollowRedirects(true);
            connection.connect();
            return connection.getContentLengthLong();
        } catch (Exception ignored) {
            return -1;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static void downloadOne(String path, File target, long expected, int index, Listener listener) throws Exception {
        File partial = new File(target.getAbsolutePath() + ".part");
        long offset = partial.isFile() ? partial.length() : 0;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(BASE + path + "?download=true").openConnection();
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(60000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "AI-Animation-Studio-Android");
            if (offset > 0) connection.setRequestProperty("Range", "bytes=" + offset + "-");
            int code = connection.getResponseCode();
            boolean append = code == HttpURLConnection.HTTP_PARTIAL;
            if (code != HttpURLConnection.HTTP_OK && !append) {
                throw new Exception("Download failed for " + path + " (HTTP " + code + ")");
            }
            if (!append) {
                offset = 0;
                if (partial.exists() && !partial.delete()) throw new Exception("Cannot restart download for " + path);
            }
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(partial, append)) {
                byte[] buffer = new byte[1024 * 1024];
                long received = offset;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                    received += count;
                    if (received % (16L * 1024L * 1024L) < buffer.length) {
                        notify(listener, "Downloading " + (index + 1) + "/" + FILES.length + ": " + path + " (" + formatMb(received) + " MB)");
                    }
                }
                output.getFD().sync();
            }
            if (expected > 0 && partial.length() != expected) {
                throw new Exception("File size check failed for " + path + ". Retry to resume the download.");
            }
            if (target.exists() && !target.delete()) throw new Exception("Cannot replace " + path);
            if (!partial.renameTo(target)) throw new Exception("Cannot finalize " + path);
            notify(listener, "Downloaded " + (index + 1) + "/" + FILES.length + ": " + path);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String formatMb(long bytes) {
        return String.format(java.util.Locale.US, "%.1f", bytes / (1024.0 * 1024.0));
    }

    private static void notify(Listener listener, String message) {
        if (listener != null) listener.onProgress(message);
    }
}
