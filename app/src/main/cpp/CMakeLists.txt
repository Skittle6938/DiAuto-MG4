cmake_minimum_required(VERSION 3.22.1)

project("headunit")

# Force 16 KB memory page size alignment for Android 15+ compatibility
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

# =========================================================================
# Target 1: usbhelper (Libusb Support)
# =========================================================================

# Define imported precompiled libusb1.0.so library (which is now 16 KB page-aligned)
add_library(usb1.0 SHARED IMPORTED)

set_target_properties(usb1.0 PROPERTIES
    IMPORTED_LOCATION "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libusb1.0.so"
)

# Define our JNI wrapper library
add_library(usbhelper SHARED usbhelper.c)

# Link wrapper with libusb and Android logging framework
target_link_libraries(usbhelper
    usb1.0
    log
)


