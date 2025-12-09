# Set the minimum version of CMake required for this project.
cmake_minimum_required(VERSION 3.22.1)

# Name the project.
project("kawaiiraweditor")

# Use file(GLOB_RECURSE ...) to find all the C and C++ source files for LibRaw, including nested subdirectories.
# This is more robust than listing them manually.
file(GLOB_RECURSE LIBRAW_SOURCES
        "libraw/internal/*.cpp"
        "libraw/src/*.cpp"
        "libraw/src/**/*.cpp"
        "libraw/src/*.c"
        "libraw/src/**/*.c"
        "libraw/dcraw/*.c"
        # Include LibRaw core C++ sources where LibRaw class is implemented
        "libraw/libraw/*.cpp"
)

# Exclude LibRaw alternative implementation files that cause duplicate symbols when combined
list(FILTER LIBRAW_SOURCES EXCLUDE REGEX ".*/postprocessing/postprocessing_ph\\.cpp$")
list(FILTER LIBRAW_SOURCES EXCLUDE REGEX ".*/preprocessing/preprocessing_ph\\.cpp$")
list(FILTER LIBRAW_SOURCES EXCLUDE REGEX ".*/write/write_ph\\.cpp$")

# Define our library, 'kawaiiraweditor', as a shared library (.so).
# It is built from our JNI wrapper and all the LibRaw source files.
add_library(
        kawaiiraweditor
        SHARED
        native-lib.cpp
        ${LIBRAW_SOURCES}
)

# Specify the include directories needed for the compiler.
target_include_directories(
        kawaiiraweditor
        PRIVATE
        # Include directories needed by LibRaw to find its own headers.
        libraw
        libraw/internal
        libraw/src
        libraw/dcraw
)

# Add the compile definitions required by LibRaw to build correctly.
target_compile_definitions(
        kawaiiraweditor
        PRIVATE
        NO_JASPER
        NO_JPEG
        NO_LCMS
        LIBRAW_NODLL
)

# Link our library against the necessary Android NDK libraries.
# These names are provided by the NDK.
target_link_libraries(
        kawaiiraweditor
        log          # For logging to Logcat
        jnigraphics  # For Android Bitmap functions
)
