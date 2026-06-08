#pragma once
#include <aidl/vendor/zunipe/perf/BnPerf.h>

namespace aidl::vendor::zunipe::perf {

class Perf : public BnPerf {
public:
    ndk::ScopedAStatus getCpuFreq(int core, int* _aidl_return) override;
    ndk::ScopedAStatus getGpuFreq(int* _aidl_return) override;
    ndk::ScopedAStatus perfLockAcq(int32_t in_handle, 
                                     int32_t in_duration, 
                                     const std::vector<int32_t>& in_list, 
                                     int32_t in_numArgs, 
                                     int32_t* _aidl_return) override;

    ndk::ScopedAStatus perfLockRel(int32_t in_handle, 
                                     int32_t* _aidl_return) override;
};

} // namespace aidl::vendor::zunipe::perf