cmake_minimum_required(VERSION 3.22.1)
project("AIAnimationStudio")

# MNN's build_64.sh uses -DNATIVE_LIBRARY_OUTPUT=.; libMNN.so is emitted
# directly into project/android/build_64, not build_64/lib.
set(MNN_SOURCE_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../mnn")
set(MNN_BUILD_ROOT "${MNN_SOURCE_ROOT}/project/android/build_64")
set(MNN_LIBRARY "${MNN_BUILD_ROOT}/libMNN.so")

if(NOT EXISTS "${MNN_SOURCE_ROOT}/CMakeLists.txt")
    message(FATAL_ERROR "MNN source checkout is missing at ${MNN_SOURCE_ROOT}")
endif()
if(NOT EXISTS "${MNN_LIBRARY}")
    message(FATAL_ERROR "MNN library is missing at ${MNN_LIBRARY}; check the MNN build output")
endif()

add_library(MNN SHARED IMPORTED)
set_target_properties(MNN PROPERTIES IMPORTED_LOCATION "${MNN_LIBRARY}")

add_library(native-lib SHARED
    native-lib.cpp
    sana_jni.cpp
    sana_session.cpp
)

target_include_directories(native-lib PRIVATE
    "${MNN_SOURCE_ROOT}/include"
    "${MNN_SOURCE_ROOT}/transformers/diffusion/engine/include"
    "${MNN_SOURCE_ROOT}/transformers/llm/engine/include"
    "${MNN_SOURCE_ROOT}/tools/audio/include"
    "${MNN_SOURCE_ROOT}/3rd_party"
    "${CMAKE_CURRENT_SOURCE_DIR}"
    "${CMAKE_CURRENT_SOURCE_DIR}/third_party"
)

find_library(android-lib android)
find_library(log-lib log)
find_library(mediandk-lib mediandk)

target_link_libraries(native-lib
    MNN
    ${android-lib}
    ${log-lib}
    ${mediandk-lib}
)

target_link_options(native-lib PRIVATE "-Wl,-z,max-page-size=16384")
