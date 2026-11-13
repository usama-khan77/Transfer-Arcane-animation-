#include <jni.h>
#include <string>

extern "C"
JNIEXPORT jstring JNICALL
Java_com_usama_aianimationstudio_MainActivity_getEngineStatus(
        JNIEnv* env,
        jobject /* this */) {
    const std::string status =
            "MNN Sana native library is linked. Model files and image-generation UI are not connected yet.";
    return env->NewStringUTF(status.c_str());
}
