#include "Vendor_007.h"
#include <fstream>
#include <android-base/logging.h>

namespace aidl::vendor::zunipe::vendor_007 {

ndk::ScopedAStatus Vendor007Impl::getPersistHotspotPassword(std::string* _aidl_return) {
        std::ifstream file(kPasswordPath);
        if (!file.is_open()) {
            *_aidl_return = ""; // 如果文件不存在，返回空字符串
            return ndk::ScopedAStatus::ok();
        }
        std::string password;
        std::getline(file, password);
        *_aidl_return = password;
        return ndk::ScopedAStatus::ok();
    }

    ndk::ScopedAStatus Vendor007Impl::setPersistHotspotPassword(const std::string& in_password) {
        std::ofstream file(kPasswordPath);
        if (!file.is_open()) {
            return ndk::ScopedAStatus::fromServiceSpecificError(-1); // 写入失败
        }
        file << in_password;
        return ndk::ScopedAStatus::ok();
    }

} // namespace aidl::vendor::zunipe::vendor_007