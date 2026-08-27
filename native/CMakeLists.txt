cmake_minimum_required(VERSION 3.24)

project(SimpleUvNative VERSION 0.1.0 LANGUAGES CXX)

include(CTest)

add_library(xatlas STATIC
    xatlas/xatlas.cpp
    xatlas/xatlas.h
)
target_include_directories(xatlas PUBLIC xatlas)
target_compile_features(xatlas PUBLIC cxx_std_17)

add_library(uv_engine STATIC
    uv_engine.cpp
    uv_engine.h
)
target_include_directories(uv_engine PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})
target_compile_features(uv_engine PUBLIC cxx_std_17)
target_link_libraries(uv_engine PRIVATE xatlas)

if(MSVC)
    target_compile_options(uv_engine PRIVATE /W4 /permissive-)
else()
    target_compile_options(uv_engine PRIVATE -Wall -Wextra -Wpedantic)
endif()

add_executable(native_smoke_test tests/native_smoke_test.cpp)
target_link_libraries(native_smoke_test PRIVATE uv_engine)

if(BUILD_TESTING)
    add_test(NAME native_smoke_test COMMAND native_smoke_test)
endif()

