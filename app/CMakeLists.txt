# ============================================================
# CMake setup for ArUco + OpenCV4 on Android
# ============================================================

cmake_minimum_required(VERSION 3.22.1)

project("imageProcess")

set(CMAKE_VERBOSE_MAKEFILE ON)

# Path tới thư viện OpenCV
set(cvlibs "${CMAKE_SOURCE_DIR}/src/main/jniLibs")

# Include thư mục header của bạn
include_directories(
        ${CMAKE_SOURCE_DIR}/src/main/cpp/include
        ${CMAKE_SOURCE_DIR}/src/main/cpp/include/aruco3
)

# ============================================================
# Import OpenCV 4 native library từ jniLibs
# ============================================================
add_library(opencv_java4 SHARED IMPORTED)
set_target_properties(opencv_java4 PROPERTIES
        IMPORTED_LOCATION "${cvlibs}/${ANDROID_ABI}/libopencv_java4.so"
)

# ============================================================
# ArUco 3 (C++ sources)
# ============================================================
set(arucoDir "${CMAKE_SOURCE_DIR}/src/main/cpp/include/aruco3")

set(arucoSources
        ${arucoDir}/cameraparameters.cpp
        ${arucoDir}/cvdrawingutils.cpp
        ${arucoDir}/dictionary.cpp
        ${arucoDir}/dictionary_based.cpp
        ${arucoDir}/ippe.cpp
        ${arucoDir}/marker.cpp
        ${arucoDir}/markerdetector_impl.cpp
        ${arucoDir}/markerdetector.cpp
        ${arucoDir}/markerlabeler.cpp
        ${arucoDir}/markermap.cpp
)

add_library(libaruco3 STATIC ${arucoSources})

# ============================================================
# Your main JNI bridge code
# ============================================================
add_library(imageProcess SHARED
        src/main/cpp/cameraOpenCV.cpp)

# ============================================================
# Link các thư viện cần thiết
# ============================================================
find_library(log-lib log)
find_library(cpp_shared c++_shared)

target_link_libraries(
        imageProcess
        libaruco3            # GIỮ LẠI: Thư viện phát hiện marker của bạn
        opencv_java4         # GIỮ LẠI: Thư viện OpenCV chính (chứa cv::solvePnP và cv::calib3d)
        ${cpp_shared}
        ${log-lib}
)

# ============================================================
# Compiler flags (cho an toàn)
# ============================================================
set_target_properties(imageProcess PROPERTIES
        CXX_STANDARD 17
        CXX_STANDARD_REQUIRED YES
        CXX_EXTENSIONS NO
)

# Linker flags: cho phép unresolved từ shared lib (sẽ được resolve lúc runtime khi load OpenCV)
# Loại bỏ các cờ "no-undefined" có thể được thêm bởi toolchain
string(REPLACE "-Wl,--no-undefined-version" "" CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS}")
string(REPLACE "-Wl,--no-undefined " "" CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS}")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--allow-shlib-undefined")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fexceptions -frtti -fPIC")
