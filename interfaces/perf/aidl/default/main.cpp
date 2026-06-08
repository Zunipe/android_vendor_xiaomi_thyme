#include "Perf.h"
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <android-base/logging.h>

using aidl::vendor::zunipe::perf::Perf;

int main() {
    // 1. 设置 Binder 线程池最大线程数
    ABinderProcess_setThreadPoolMaxThreadCount(0);

    // 2. 实例化我们的 HAL 服务
    std::shared_ptr<Perf> perfService = ndk::SharedRefBase::make<Perf>();

    // 3. 拼接服务名称 (格式通常为：接口名/实例名)
    std::string instance = std::string(Perf::descriptor) + "/default";
    
    // 4. 注册到 ServiceManager
    binder_status_t status = AServiceManager_addService(perfService->asBinder().get(), instance.c_str());
    
    if (status != STATUS_OK) {
        LOG(ERROR) << "Failed to start Perf AIDL HAL service";
        return -1;
    }

    LOG(INFO) << "Perf AIDL HAL service started successfully.";
    
    // 5. 启动线程池循环
    ABinderProcess_joinThreadPool();
    return EXIT_FAILURE; // 不应该执行到这里
}