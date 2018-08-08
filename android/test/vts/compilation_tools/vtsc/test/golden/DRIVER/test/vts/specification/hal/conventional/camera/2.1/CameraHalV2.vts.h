#ifndef __VTS_DRIVER__hal_conventional_camera_V2_1__
#define __VTS_DRIVER__hal_conventional_camera_V2_1__

#undef LOG_TAG
#define LOG_TAG "FuzzerExtended_camera_module_t"
#include <hardware/hardware.h>
#include <hardware/camera_common.h>
#include <hardware/camera.h>

#include <stdio.h>
#include <stdarg.h>
#include <stdlib.h>
#include <string.h>
#include <utils/Log.h>

#include <driver_base/DriverBase.h>
#include <driver_base/DriverCallbackBase.h>

namespace android {
namespace vts {
class FuzzerExtended_camera_module_t : public DriverBase {
 public:
    FuzzerExtended_camera_module_t() : DriverBase(HAL_CONVENTIONAL) {}
 protected:
    bool Fuzz(FunctionSpecificationMessage* func_msg, void** result, const string& callback_socket_name);
    bool CallFunction(const FunctionSpecificationMessage& func_msg, const string& callback_socket_name, FunctionSpecificationMessage* result_msg);
    bool VerifyResults(const FunctionSpecificationMessage& expected_result, const FunctionSpecificationMessage& actual_result);
    bool GetAttribute(FunctionSpecificationMessage* func_msg, void** result);
        bool Fuzz__common(FunctionSpecificationMessage* func_msg,
                    void** result, const string& callback_socket_name);
        bool GetAttribute__common(FunctionSpecificationMessage* /*func_msg*/,
                    void** /*result*/);
            bool Fuzz__common_methods(FunctionSpecificationMessage* func_msg,
                        void** result, const string& callback_socket_name);
            bool GetAttribute__common_methods(FunctionSpecificationMessage* /*func_msg*/,
                        void** /*result*/);
 private:
};


extern "C" {
extern android::vts::DriverBase* vts_func_1_2_V2_1_();
}
}  // namespace vts
}  // namespace android
#endif
