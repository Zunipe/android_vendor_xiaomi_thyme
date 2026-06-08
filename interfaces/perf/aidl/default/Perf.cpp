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

typedef int (*perf_acq_t)(int, int, int[], int);
typedef int (*perf_rel_t)(int);

static perf_acq_t g_perf_lock_acq = nullptr;
static perf_rel_t g_perf_lock_rel = nullptr;
static void* g_perf_lib_handle = nullptr;
static std::mutex g_perf_init_mutex;

static bool init_perf_symbols() {
    std::lock_guard<std::mutex> lock(g_perf_init_mutex);

    if (g_perf_lock_acq && g_perf_lock_rel) {
        return true;
    }

    if (!g_perf_lib_handle) {
        g_perf_lib_handle = dlopen("libqti-perfd-client.so", RTLD_NOW);
        if (!g_perf_lib_handle) {
            g_perf_lib_handle = dlopen("/vendor/lib64/libqti-perfd-client.so", RTLD_NOW);
        }
    }

    if (!g_perf_lib_handle) {
        ALOGE("Failed to dlopen libqti-perfd-client.so: %s", dlerror());
        return false;
    }

    g_perf_lock_acq = (perf_acq_t)dlsym(g_perf_lib_handle, "perf_lock_acq");
    g_perf_lock_rel = (perf_rel_t)dlsym(g_perf_lib_handle, "perf_lock_rel");

    if (!g_perf_lock_acq || !g_perf_lock_rel) {
        ALOGE("Failed to find symbols in libqti-perfd-client.so: %s", dlerror());
        return false;
    }

    ALOGD("Successfully initialized Qualcomm Perf client symbols.");
    return true;
}

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
    if (_aidl_return != nullptr) {
        *_aidl_return = -1; 
    }

    if (!init_perf_symbols()) {
        ALOGE("perfLockAcq: Perf library symbols not available");
        return ndk::ScopedAStatus::ok();
    }

    if (in_list.empty() || in_numArgs <= 0) {
        ALOGE("perfLockAcq: Invalid argument list or numArgs");
        return ndk::ScopedAStatus::ok();
    }

    int32_t actual_num_args = std::min(in_numArgs, static_cast<int32_t>(in_list.size()));

    int* local_list = const_cast<int*>(in_list.data());

    ALOGD("Calling native perf_lock_acq: handle=%d, duration=%d, numArgs=%d", in_handle, in_duration, actual_num_args);
    int32_t out_handle = g_perf_lock_acq(in_handle, in_duration, local_list, actual_num_args);

    if (_aidl_return != nullptr) {
        *_aidl_return = out_handle;
    }

    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Perf::perfLockRel(int32_t in_handle, int32_t* _aidl_return) {
    if (_aidl_return != nullptr) {
        *_aidl_return = -1;
    }

    if (!init_perf_symbols()) {
        ALOGE("perfLockRel: Perf library symbols not available");
        return ndk::ScopedAStatus::ok();
    }

    ALOGD("Calling native perf_lock_rel: handle=%d", in_handle);
    int32_t result = g_perf_lock_rel(in_handle);

    if (_aidl_return != nullptr) {
        *_aidl_return = result;
    }

    return ndk::ScopedAStatus::ok();
}

} // namespace aidl::vendor::zunipe::perf