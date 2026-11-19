#include "com_android_apex.h"

#include <cstring>

#include <tinyxml2.h>

namespace com::android::apex {

// <apex-info-list><apex-info moduleName=".." partition="VENDOR" isActive="true" .../>...</apex-info-list>
std::optional<ApexInfoList> parseApexInfoList(const char* xml) {
    tinyxml2::XMLDocument doc;
    if (doc.Parse(xml) != tinyxml2::XML_SUCCESS) return std::nullopt;
    const tinyxml2::XMLElement* root = doc.RootElement();
    if (root == nullptr || std::strcmp(root->Name(), "apex-info-list") != 0) return std::nullopt;
    std::vector<ApexInfo> infos;
    for (const auto* e = root->FirstChildElement("apex-info"); e != nullptr; e = e->NextSiblingElement("apex-info")) {
        const char* name = e->Attribute("moduleName");
        if (name == nullptr) return std::nullopt;
        const char* partition = e->Attribute("partition");
        infos.emplace_back(name, partition ? partition : "", e->BoolAttribute("isActive", false));
    }
    return ApexInfoList(std::move(infos));
}

}  // namespace com::android::apex
