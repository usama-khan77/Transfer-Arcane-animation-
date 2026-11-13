package com.usama.aianimationstudio;

/**
 * JNI declarations for Alibaba MNN's Sana editing session.
 *
 * This class is only the native bridge. Model download, model-path setup,
 * image URI copying, and the generation UI are separate integration steps.
 */
public final class SanaEngine {
    static {
        System.loadLibrary("native-lib");
    }

    private SanaEngine() {}

    public interface ProgressListener {
        boolean onProgress(String progress);
    }

    public static native long initNative(String resourcePath, String configJson);

    public static native void releaseNative(long instanceId);

    public static native java.util.HashMap<String, Object> generateNative(
            long instanceId,
            String prompt,
            String inputImagePath,
            String outputImagePath,
            int steps,
            int seed,
            boolean useCfg,
            float cfgScale,
            ProgressListener listener
    );
}
