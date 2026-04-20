cmake_minimum_required(VERSION 3.22.1)
project(bluex_piper LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(ExternalProject)

if(NOT DEFINED PIPER_VENDOR_ROOT)
    message(FATAL_ERROR "PIPER_VENDOR_ROOT was not provided")
endif()

if(NOT DEFINED ONNXRUNTIME_AAR)
    message(FATAL_ERROR "ONNXRUNTIME_AAR was not provided")
endif()

set(ONNXRUNTIME_EXTRACT_DIR "${CMAKE_BINARY_DIR}/onnxruntime_aar")
if(NOT EXISTS "${ONNXRUNTIME_EXTRACT_DIR}/headers/onnxruntime_cxx_api.h")
    file(MAKE_DIRECTORY "${ONNXRUNTIME_EXTRACT_DIR}")
    file(ARCHIVE_EXTRACT INPUT "${ONNXRUNTIME_AAR}" DESTINATION "${ONNXRUNTIME_EXTRACT_DIR}")
endif()

set(ONNXRUNTIME_INCLUDE_DIR "${ONNXRUNTIME_EXTRACT_DIR}/headers")
set(ONNXRUNTIME_SHARED_LIB "${ONNXRUNTIME_EXTRACT_DIR}/jni/${ANDROID_ABI}/libonnxruntime.so")
if(NOT EXISTS "${ONNXRUNTIME_SHARED_LIB}")
    message(FATAL_ERROR "libonnxruntime.so not found for ABI ${ANDROID_ABI}")
endif()

set(ESPEAKNG_BUILD_DIR "${CMAKE_BINARY_DIR}/espeak_ng")
set(ESPEAKNG_INSTALL_DIR "${CMAKE_BINARY_DIR}/espeak_ng-install")
set(ESPEAKNG_STATIC_LIB "${ESPEAKNG_INSTALL_DIR}/lib/libespeak-ng.a")
set(UCD_STATIC_LIB "${ESPEAKNG_BUILD_DIR}/src/espeak_ng_external-build/src/ucd-tools/libucd.a")
set(UCD_INCLUDE_DIR "${ESPEAKNG_BUILD_DIR}/src/espeak_ng_external/src/ucd-tools/src/include")

ExternalProject_Add(espeak_ng_external
    GIT_REPOSITORY https://github.com/espeak-ng/espeak-ng.git
    GIT_TAG 212928b394a96e8fd2096616bfd54e17845c48f6
    PREFIX "${ESPEAKNG_BUILD_DIR}"
    UPDATE_DISCONNECTED TRUE
    PATCH_COMMAND
        ${CMAKE_COMMAND} -E copy_if_different
        ${CMAKE_CURRENT_LIST_DIR}/patches/espeak-ng-src-CMakeLists.txt
        <SOURCE_DIR>/src/CMakeLists.txt
    CMAKE_ARGS
        -G${CMAKE_GENERATOR}
        -DCMAKE_MAKE_PROGRAM=${CMAKE_MAKE_PROGRAM}
        -DCMAKE_TOOLCHAIN_FILE=${CMAKE_TOOLCHAIN_FILE}
        -DCMAKE_BUILD_TYPE=${CMAKE_BUILD_TYPE}
        -DANDROID_ABI=${ANDROID_ABI}
        -DANDROID_PLATFORM=${ANDROID_PLATFORM}
        -DANDROID_STL=${ANDROID_STL}
        -DCMAKE_INSTALL_PREFIX=${ESPEAKNG_INSTALL_DIR}
        -DBUILD_SHARED_LIBS=OFF
        -DCMAKE_POSITION_INDEPENDENT_CODE=ON
        -DUSE_ASYNC=OFF
        -DUSE_MBROLA=OFF
        -DUSE_LIBSONIC=OFF
        -DUSE_LIBPCAUDIO=OFF
        -DUSE_KLATT=OFF
        -DUSE_SPEECHPLAYER=OFF
        -DEXTRA_cmn=ON
        -DEXTRA_ru=ON
        -DCMAKE_C_FLAGS=-D_FILE_OFFSET_BITS=64\ -I${UCD_INCLUDE_DIR}
        -DCMAKE_CXX_FLAGS=-D_FILE_OFFSET_BITS=64\ -I${UCD_INCLUDE_DIR}
    BUILD_BYPRODUCTS
        ${ESPEAKNG_STATIC_LIB}
        ${UCD_STATIC_LIB}
)

add_library(onnxruntime SHARED IMPORTED)
set_target_properties(onnxruntime PROPERTIES
    IMPORTED_LOCATION "${ONNXRUNTIME_SHARED_LIB}"
)

add_library(bluex_piper_jni SHARED
    bluex_piper_jni.cpp
    ${PIPER_VENDOR_ROOT}/libpiper/src/piper.cpp
)

add_dependencies(bluex_piper_jni espeak_ng_external)

target_include_directories(bluex_piper_jni PRIVATE
    ${PIPER_VENDOR_ROOT}/libpiper/include
    ${ESPEAKNG_INSTALL_DIR}/include
    ${ONNXRUNTIME_INCLUDE_DIR}
)

target_link_libraries(bluex_piper_jni
    android
    log
    onnxruntime
    ${ESPEAKNG_STATIC_LIB}
    ${UCD_STATIC_LIB}
)
