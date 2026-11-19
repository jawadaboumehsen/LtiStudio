// The part of AOSP's xsdc-generated apex-info-list parser (system/apex/apexd/ApexInfoList.xsd) that libvintf's
// Apex.cpp uses: each <apex-info>'s moduleName, partition and isActive.
#pragma once

#include <optional>
#include <string>
#include <vector>

namespace com::android::apex {

class ApexInfo {
  public:
    ApexInfo(std::string moduleName, std::string partition, bool isActive)
        : moduleName_(std::move(moduleName)), partition_(std::move(partition)), isActive_(isActive) {}
    const std::string& getModuleName() const { return moduleName_; }
    const std::string& getPartition() const { return partition_; }
    bool getIsActive() const { return isActive_; }

  private:
    std::string moduleName_;
    std::string partition_;
    bool isActive_;
};

class ApexInfoList {
  public:
    explicit ApexInfoList(std::vector<ApexInfo> apexInfo) : apexInfo_(std::move(apexInfo)) {}
    const std::vector<ApexInfo>& getApexInfo() const { return apexInfo_; }

  private:
    std::vector<ApexInfo> apexInfo_;
};

std::optional<ApexInfoList> parseApexInfoList(const char* xml);

}  // namespace com::android::apex
