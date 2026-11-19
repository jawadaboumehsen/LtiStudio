// AOSP generates these lists at build time from every HIDL and AIDL interface in its tree. Outside that tree
// they are empty: checkvintf then does not know which HIDL interfaces extend others, which only matters to its
// deprecation and unused-HAL checks of HIDL child interfaces. The compatibility check itself does not use them.
#include <aidl/metadata.h>
#include <hidl/metadata.h>

namespace android {

std::vector<HidlInterfaceMetadata> HidlInterfaceMetadata::all() { return {}; }

std::vector<AidlInterfaceMetadata> AidlInterfaceMetadata::all() { return {}; }

}  // namespace android
