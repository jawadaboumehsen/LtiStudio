# checkvintf: Android's VINTF compatibility checker (the one AOSP's release tools run before an OTA), built for the
# host from AOSP android-16.0.0_r4. Setup builds it as the "vintf-tools" tool group (RecipeConfig.AospCMake): the
# source is system/libvintf, the other AOSP projects are fetched to ../aosp/ at pinned commits, and this folder
# (the app's tool-recipes/vintf) is written to ltirom-build/ beside them.
#
# third_party/ AOSP files copied as they are, to avoid fetching two large repos for them:
#              fcm_exclude (hardware/interfaces/compatibility_matrices/exclude) and the HIDL/AIDL metadata headers
# stubs/       not AOSP's: the subset of the xsdc-generated apex-info-list parser libvintf uses, and empty
#              interface metadata (AOSP generates it from its whole tree; only HIDL child interfaces in
#              checkvintf's deprecation and unused-HAL checks are affected)
cmake_minimum_required(VERSION 3.20)
project(vintf-tools C CXX)

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
add_compile_options(-Wno-attributes -Wno-deprecated-declarations)
# AOSP builds against libc++, whose headers pull these in indirectly; libstdc++'s do not.
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -include limits -include cstdint -include cstring -include algorithm")
add_compile_definitions(_FILE_OFFSET_BITS=64 _LARGEFILE64_SOURCE)

set(A ${CMAKE_CURRENT_SOURCE_DIR}/../aosp)
set(T ${CMAKE_CURRENT_SOURCE_DIR}/third_party)
find_package(ZLIB REQUIRED)

set(FMT_INSTALL OFF CACHE BOOL "" FORCE)
add_subdirectory(${A}/fmtlib fmt EXCLUDE_FROM_ALL)

add_library(liblog STATIC
    ${A}/logging/liblog/log_event_list.cpp
    ${A}/logging/liblog/log_event_write.cpp
    ${A}/logging/liblog/logger_name.cpp
    ${A}/logging/liblog/logger_read.cpp
    ${A}/logging/liblog/logger_write.cpp
    ${A}/logging/liblog/properties.cpp
    ${A}/logging/liblog/logprint.cpp)
target_compile_definitions(liblog PRIVATE LIBLOG_LOG_TAG=1006 _XOPEN_SOURCE=700 FAKE_LOG_DEVICE=1)
target_include_directories(liblog PUBLIC
    ${A}/core/include ${A}/logging/liblog/include ${A}/core/libcutils/include ${A}/libbase/include)

add_library(libbase STATIC
    ${A}/libbase/chrono_utils.cpp
    ${A}/libbase/file.cpp
    ${A}/libbase/hex.cpp
    ${A}/libbase/logging.cpp
    ${A}/libbase/mapped_file.cpp
    ${A}/libbase/parsebool.cpp
    ${A}/libbase/parsenetaddress.cpp
    ${A}/libbase/posix_strerror_r.cpp
    ${A}/libbase/process.cpp
    ${A}/libbase/properties.cpp
    ${A}/libbase/result.cpp
    ${A}/libbase/stringprintf.cpp
    ${A}/libbase/strings.cpp
    ${A}/libbase/threads.cpp
    ${A}/libbase/errors_unix.cpp)
target_include_directories(libbase PUBLIC ${A}/libbase/include ${A}/core/include)
target_link_libraries(libbase PUBLIC fmt::fmt liblog)
# It wraps the XSI strerror_r, which glibc gives only without _GNU_SOURCE (clang++ defines it by default).
set_source_files_properties(${A}/libbase/posix_strerror_r.cpp PROPERTIES COMPILE_OPTIONS "-U_GNU_SOURCE")

add_library(tinyxml2 STATIC ${A}/tinyxml2/tinyxml2.cpp)
target_include_directories(tinyxml2 PUBLIC ${A}/tinyxml2)

add_library(kver STATIC
    ${A}/gki/libkver/kernel_release.cpp
    ${A}/gki/libkver/kmi_version.cpp
    ${A}/gki/libkver/utils.cpp)
target_include_directories(kver PUBLIC ${A}/gki/libkver/include)
target_link_libraries(kver PUBLIC libbase)

add_library(fcm_exclude STATIC ${T}/fcm_exclude/fcm_exclude.cpp)
target_include_directories(fcm_exclude PUBLIC ${T}/fcm_exclude/include)
target_link_libraries(fcm_exclude PUBLIC libbase)

set(V ${CMAKE_CURRENT_SOURCE_DIR}/..)
add_library(vintf STATIC
    ${V}/parse_string.cpp ${V}/parse_xml.cpp ${V}/Apex.cpp ${V}/CompatibilityMatrix.cpp ${V}/FileSystem.cpp
    ${V}/FQName.cpp ${V}/FqInstance.cpp ${V}/HalManifest.cpp ${V}/HalInterface.cpp
    ${V}/KernelConfigTypedValue.cpp ${V}/KernelInfo.cpp ${V}/RuntimeInfo.cpp ${V}/ManifestHal.cpp
    ${V}/ManifestInstance.cpp ${V}/MatrixHal.cpp ${V}/MatrixInstance.cpp ${V}/MatrixKernel.cpp
    ${V}/PropertyFetcher.cpp ${V}/Regex.cpp ${V}/SystemSdk.cpp ${V}/TransportArch.cpp ${V}/VintfObject.cpp
    ${V}/XmlFile.cpp ${V}/utils.cpp ${V}/RuntimeInfo-host.cpp ${V}/VintfObjectRecovery.cpp
    ${V}/KernelConfigParser.cpp ${V}/KernelConfigs.cpp
    ${A}/core/libutils/binder/Errors.cpp
    stubs/com_android_apex.cpp)
target_include_directories(vintf PUBLIC
    ${V}/include ${V}/include/vintf ${V}/include-host ${V} stubs ${T}/metadata/include
    ${A}/core/libutils/include ${A}/core/libutils/binder/include ${A}/core/libcutils/include)
target_link_libraries(vintf PUBLIC libbase liblog tinyxml2 kver ZLIB::ZLIB)

add_executable(checkvintf ${V}/check_vintf.cpp ${V}/HostFileSystem.cpp stubs/metadata.cpp)
target_link_libraries(checkvintf PRIVATE vintf fcm_exclude)
target_link_options(checkvintf PRIVATE -static-libstdc++ -static-libgcc)
install(TARGETS checkvintf RUNTIME DESTINATION bin)
