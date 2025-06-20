cmake_minimum_required(VERSION 3.22)
project(exiftool_jni LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)

# libperl 위치 (jniLibs/${ANDROID_ABI})
set(PERL_LIB ${CMAKE_CURRENT_LIST_DIR}/../jniLibs/${ANDROID_ABI}/libperl.so)
add_library(perl SHARED IMPORTED)
set_target_properties(perl PROPERTIES IMPORTED_LOCATION ${PERL_LIB})

add_library(exiftool_jni SHARED exiftool_jni.cpp)

target_include_directories(exiftool_jni PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/perl_headers/arm64-v8a     # perl headers (Config, EXTERN.h 등) 복사해둘 것
)

#target_link_libraries(exiftool_jni
#        ${PERL_LIB}
#        log                               # __android_log_print
#        dl
#        m
#)

target_link_libraries(
        exiftool_jni
        perl            # ← IMPORTED 타깃으로 연결
        log             # __android_log_print
        android         # AAssetManager 등 사용시 필요(안 쓰면 삭제)
        dl              # dlopen / dlsym
        m               # libm, Perl 내부에서 sin/cos 등 호출
)
