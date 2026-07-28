#include "Vendor_007.h"
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <android-base/logging.h>

using aidl::vendor::zunipe::vendor_007::Vendor007Impl;

int main() {
    // 1. 设置 Binder 线程池最大线程数
    ABinderProcess_setThreadPoolMaxThreadCount(0);

    // 2. 实例化我们的 HAL 服务
    std::shared_ptr<Vendor007Impl> perfService = ndk::SharedRefBase::make<Vendor007Impl>();

    // 3. 拼接服务名称 (格式通常为：接口名/实例名)
    std::string instance = std::string(Vendor007Impl::descriptor) + "/default";
    
    // 4. 注册到 ServiceManager
    binder_status_t status = AServiceManager_addService(perfService->asBinder().get(), instance.c_str());
    
    if (status != STATUS_OK) {
        LOG(ERROR) << "Failed to start Vendor007Impl AIDL HAL service";
        return -1;
    }

    LOG(INFO) << "Vendor007Impl AIDL HAL service started successfully.";
    
    // 5. 启动线程池循环
    ABinderProcess_joinThreadPool();
    return EXIT_FAILURE; // 不应该执行到这里
}