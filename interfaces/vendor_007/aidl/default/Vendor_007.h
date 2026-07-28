#pragma once
#include <aidl/vendor/zunipe/vendor_007/BnVendor007.h>

namespace aidl::vendor::zunipe::vendor_007 {

class Vendor007Impl : public BnVendor007 {
public:
    ndk::ScopedAStatus getPersistHotspotPassword(std::string* _aidl_return) override;
    ndk::ScopedAStatus setPersistHotspotPassword(const std::string& in_password) override;
private:
	std::string kPasswordPath = "/mnt/vendor/persist/hotspot/password";
};

} // namespace aidl::vendor::zunipe::vendor_007