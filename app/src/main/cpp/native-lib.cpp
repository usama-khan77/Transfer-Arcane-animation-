#include <jni.h>
#include <string>

extern "C"
JNIEXPORT jstring JNICALL
Java_com_usama_aianimationstudio_MainActivity_getEngineStatus(
        JNIEnv* env,
        jobject /* this */) {

    std::string status =
            "Native C++ engine loaded. MNN image generation is not connected yet.";

    return env->NewStringUTF(status.c_str());
}
