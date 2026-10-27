cmake_minimum_required(VERSION 3.22.1)

project(rawdecoder CXX)

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Vendor LibRaw 0.21.3 sources; no RawSpeed / DNG SDK / LCMS / JPEG extras.
file(GLOB_RECURSE LIBRAW_SOURCES CONFIGURE_DEPENDS
    "${CMAKE_CURRENT_SOURCE_DIR}/libraw/src/*.cpp")

add_library(raw STATIC ${LIBRAW_SOURCES})
target_include_directories(raw PUBLIC "${CMAKE_CURRENT_SOURCE_DIR}/libraw")
target_compile_definitions(raw PRIVATE LIBRAW_NODLL USE_ZLIB)
target_compile_options(raw PRIVATE -O3 -w)
target_link_libraries(raw PUBLIC z)

add_library(rawdecoder SHARED rawdecoder.cpp)
target_link_libraries(rawdecoder raw android jnigraphics log c++_static c++abi)
