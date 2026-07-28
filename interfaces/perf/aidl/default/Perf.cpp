#include "Perf.h"
#include <fstream>
#include <string>
#include <vector>
#include <dlfcn.h>
#include <mutex>
#include <android/log.h>

#define LOG_TAG "PerfHAL_Zunipe"
#define ALOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace aidl::vendor::zunipe::perf {

ndk::ScopedAStatus Perf::getCpuFreq(int core, int* _aidl_return) {
    std::string path = "/sys/devices/system/cpu/cpu" + std::to_string(core) + "/cpufreq/scaling_cur_freq";
    std::ifstream file(path);
    int32_t freq = 0;
    if (file >> freq) {
        *_aidl_return = freq;
    } else {
        *_aidl_return = -1;
    }
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Perf::getGpuFreq(int* _aidl_return) {
    std::ifstream file("/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq");
    if (!file.is_open()) {
        *_aidl_return = -1;
        return ndk::ScopedAStatus::ok();
    }
    int32_t freq = 0;
    if (file >> freq) {
        *_aidl_return = freq;
    } else {
        *_aidl_return = 0;
    }
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Perf::perfLockAcq(int32_t in_handle, int32_t in_duration, const std::vector<int32_t>& in_list, int32_t in_numArgs, int32_t* _aidl_return) {
	std::string policy[] = {"policy0/scaling_governor", "policy4/scaling_governor", "policy7/scaling_governor"};
	std::string path = "/sys/devices/system/cpu/cpufreq/";
	for (const auto& p : policy) {
        std::ofstream node(path + p);
        
        if (!node.is_open()) {
            continue;
        }
        
        node << "performance";
        node.close();
    }
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Perf::perfLockRel(int32_t in_handle, int32_t* _aidl_return) {
    std::string policy[] = {"policy0/scaling_governor", "policy4/scaling_governor", "policy7/scaling_governor"};
	std::string path = "/sys/devices/system/cpu/cpufreq/";
	for (const auto& p : policy) {
        std::ofstream node(path + p);
        
        if (!node.is_open()) {
            continue;
        }
        
        node << "schedutil";
        node.close();
    }
    return ndk::ScopedAStatus::ok();
}

} // namespace aidl::vendor::zunipe::perf