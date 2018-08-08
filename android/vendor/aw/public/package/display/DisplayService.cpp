/*
 * Copyright 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <android/hardware/graphics/composer/2.1/IComposer.h>
#include <android-base/logging.h>
#include <android-base/properties.h>
#include <binder/BinderService.h>
#include <binder/IServiceManager.h>
#include <binder/Status.h>

#include "com/softwinner/BnDisplayService.h"

#define DISPLAY_SERVICE_VERSION 1

using android::hardware::Return;
using android::hardware::graphics::composer::V2_1::Error;
using android::hardware::graphics::composer::V2_1::IComposer;

using namespace android;

namespace android {
namespace Hwc2 {

static const char* kSmartBacklightProperty = "persist.display.smart_backlight";
static const char* kEnhanceModeProperty = "persist.display.enhance_mode";
static const char* kReadingModeProperty = "persist.display.reading_mode";
static const char* kColorTemperatureProperty = "persist.display.color_temperature";

class DisplayService : public com::softwinner::BnDisplayService {
    enum { DISP0, DISP1, };
    enum HIDL_set_cmd {
        HIDL_HDMI_MODE_CMD,
        HIDL_ENHANCE_MODE_CMD,
        HIDL_SML_MODE_CMD,
        HIDL_COLOR_TEMPERATURE_CMD,
        HIDL_SET3D_MODE,
        HIDL_SETMARGIN,
        HIDL_SETVIDEORATIO,
    };
    enum {
        ENHANCE_MODE_ATTR,
        ENHANCE_BRIGHT_ATTR,
        ENHANCE_CONTRAST_ATTR,
        ENHANCE_DENOISE_ATTR,
        ENHANCE_DETAIL_ATTR,
        ENHANCE_EDGE_ATTR,
        ENHANCE_SATURATION_ATTR,
        COLOR_TEMPERATURE_ATTR,
        ATTR_NUM,
    };

  public:
    static char const* getServiceName() {
        return "aw_display";
    }

    DisplayService() {
        LOG(INFO) << "DisplayService created";
        if (mComposer == nullptr) {
            mComposer = IComposer::getService();
        }
        if (mComposer == nullptr) {
            LOG(ERROR) << "failed to get hwcomposer service";
        }
        mCurrentHdmiMode = -1;
        mCurrent3DMode = -1;
        mSmartBacklight = base::GetBoolProperty(kSmartBacklightProperty, false);
        mColorEnhance = base::GetBoolProperty(kEnhanceModeProperty, false);
        mReadingMode = base::GetBoolProperty(kReadingModeProperty, false);
        mColorTemperature = base::GetIntProperty(kColorTemperatureProperty, 25);
        __setSmartBacklight();
        __seEnhanceMode();
        __setColorTemperature();
        mMarginWidth = 100;
        mMarginHeight = 100;
        mHdmiFullscreen = false;
    }

    ::android::binder::Status displayCtrl(int32_t disp, int32_t cmd0, int32_t cmd1, int32_t data, int32_t* _aidl_return) {
        LOG(WARNING) << "Don't call displayCtrl beyond DisplayService version 1";
        Return<Error> ret = mComposer->setDisplayArg(disp, cmd0, cmd1, data);
        if (ret.isOk()) {
            *_aidl_return = true;
            return binder::Status::ok();
        } else {
            *_aidl_return = false;
            return binder::Status::fromServiceSpecificError((int)(Error)ret);
        }
    }

    ::android::binder::Status getVersion(int32_t* _aidl_return) {
        *_aidl_return = DISPLAY_SERVICE_VERSION;
        return binder::Status::ok();
    }

    ::android::binder::Status getHdmiMode(int32_t* _aidl_return) {
        *_aidl_return = mCurrentHdmiMode;
        return binder::Status::ok();
    }

    ::android::binder::Status setHdmiMode(int32_t mode, bool* _aidl_return) {
        mCurrentHdmiMode = mode;
        Return<Error> ret = mComposer->setDisplayArg(DISP1, HIDL_HDMI_MODE_CMD, mode, 0);
        if (ret.isOk()) {
            *_aidl_return = true;
            return binder::Status::ok();
        } else {
            *_aidl_return = false;
            return binder::Status::fromServiceSpecificError((int)(Error)ret);
        }
    }

    ::android::binder::Status getSmartBacklight(bool* _aidl_return) {
        *_aidl_return = mSmartBacklight;
        return binder::Status::ok();
    }

    ::android::binder::Status setSmartBacklight(bool on) {
        mSmartBacklight = on;
        __setSmartBacklight();
        std::stringstream ss;
        ss << on;
        base::SetProperty(kSmartBacklightProperty, ss.str());
        return binder::Status::ok();
    }

    ::android::binder::Status getSmartBacklightDemo(bool* _aidl_return) {
        *_aidl_return = mSmartBacklightDemo;
        return binder::Status::ok();
    }

    ::android::binder::Status setSmartBacklightDemo(bool on) {
        mSmartBacklightDemo = on;
        __setSmartBacklight();
        return binder::Status::ok();
    }

    ::android::binder::Status getColorEnhance(bool* _aidl_return) {
        *_aidl_return = mColorEnhance;
        return binder::Status::ok();
    }

    ::android::binder::Status setColorEnhance(bool on) {
        mColorEnhance = on;
        __seEnhanceMode();
        std::stringstream ss;
        ss << on;
        base::SetProperty(kEnhanceModeProperty, ss.str());
        return binder::Status::ok();
    }

    ::android::binder::Status getColorEnhanceDemo(bool* _aidl_return) {
        *_aidl_return = mColorEnhanceDemo;
        return binder::Status::ok();
    }

    ::android::binder::Status setColorEnhanceDemo(bool on) {
        mColorEnhanceDemo = on;
        __seEnhanceMode();
        return binder::Status::ok();
    }

    ::android::binder::Status getReadingMode(bool* _aidl_return) {
        *_aidl_return = mReadingMode;
        return binder::Status::ok();
    }

    ::android::binder::Status setReadingMode(bool on) {
        mReadingMode = on;
        __setColorTemperature();
        std::stringstream ss;
        ss << on;
        base::SetProperty(kReadingModeProperty, ss.str());
        return binder::Status::ok();
    }

    ::android::binder::Status getColorTemperature(int32_t* _aidl_return) {
        *_aidl_return = mColorTemperature;
        return binder::Status::ok();
    }

    ::android::binder::Status setColorTemperature(int32_t value) {
        mColorTemperature = value;
        __setColorTemperature();
        std::stringstream ss;
        ss << value;
        base::SetProperty(kColorTemperatureProperty, ss.str());
        return binder::Status::ok();
    }

    ::android::binder::Status get3DMode(int32_t* _aidl_return) {
        *_aidl_return = mCurrent3DMode;
        return binder::Status::ok();
    }

    ::android::binder::Status set3DMode(int32_t mode, bool* _aidl_return) {
        mCurrent3DMode = mode;
        Return<Error> ret = mComposer->setDisplayArg(DISP1, HIDL_SET3D_MODE, mode, 0);
        if (ret.isOk()) {
            *_aidl_return = true;
            return binder::Status::ok();
        } else {
            *_aidl_return = false;
            return binder::Status::fromServiceSpecificError((int)(Error)ret);
        }
    }

    ::android::binder::Status getMarginWidth(int32_t* _aidl_return) {
        *_aidl_return = mMarginWidth;
        return binder::Status::ok();
    }

    ::android::binder::Status setMarginWidth(int32_t scale) {
        mMarginWidth = scale;
        __setMargin();
        return binder::Status::ok();
    }

    ::android::binder::Status getMarginHeight(int32_t* _aidl_return) {
        *_aidl_return = mMarginHeight;
        return binder::Status::ok();
    }

    ::android::binder::Status setMarginHeight(int32_t scale) {
        mMarginHeight = scale;
        __setMargin();
        return binder::Status::ok();
    }

    ::android::binder::Status getHdmiFullscreen(bool* _aidl_return) {
        *_aidl_return = mHdmiFullscreen;
        return binder::Status::ok();
    }

    ::android::binder::Status setHdmiFullscreen(bool full) {
        mHdmiFullscreen = full;
        mComposer->setDisplayArg(DISP1, HIDL_SETVIDEORATIO, 0, full ? 2 : 0);
        return binder::Status::ok();
    }

private:
    ~DisplayService() {
        LOG(INFO) << "DisplayService destroyed";
    }
    sp<IComposer> mComposer;
    int32_t mCurrentHdmiMode;
    bool mSmartBacklight;
    bool mSmartBacklightDemo;
    bool mColorEnhance;
    bool mColorEnhanceDemo;
    bool mReadingMode;
    int32_t mColorTemperature;
    int32_t mCurrent3DMode;
    int32_t mMarginWidth;
    int32_t mMarginHeight;
    bool mHdmiFullscreen;

    void __setSmartBacklight() {
        int mode = 0;
        if (mSmartBacklight && mSmartBacklightDemo) {
            mode = 2;
        } else if (mSmartBacklight) {
            mode = 1;
        }
        mComposer->setDisplayArg(DISP0, HIDL_SML_MODE_CMD, 0, mode);
    }

    void __seEnhanceMode() {
        int mode = 0;
        if (mColorEnhance && mColorEnhanceDemo) {
            mode = 2;
        } else if (mColorEnhance) {
            mode = 1;
        }
        mComposer->setDisplayArg(DISP0, HIDL_ENHANCE_MODE_CMD, ENHANCE_MODE_ATTR, mode);
    }

    void __setColorTemperature() {
        int value = mColorTemperature;
        if (mReadingMode) {
            /* 70 ~ 200 */
            value = (value * 130 / 100) + 70;
        } else {
            /* 0 ~ 200 */
            value *= 2;
        }
        mComposer->setDisplayArg(DISP0, HIDL_COLOR_TEMPERATURE_CMD, COLOR_TEMPERATURE_ATTR, value);
    }

    void __setMargin() {
        int data = mMarginWidth << 16 | mMarginHeight;
        mComposer->setDisplayArg(DISP1, HIDL_SETMARGIN, 0, data);
    }
};

}  // namespace Hwc2
}  // namespace android

int main(const int /* argc */, char *argv[]) {
    setenv("ANDROID_LOG_TAGS", "*:v", 1);
    base::InitLogging(argv);
    LOG(INFO) << "main display service is running.";
    //IPCThreadState::self()->disableBackgroundScheduling(true);
    status_t ret = BinderService<Hwc2::DisplayService>::publish();
    if (ret != android::OK) {
        return ret;
    }

    sp<ProcessState> ps(ProcessState::self());
    ps->startThreadPool();
    ps->giveThreadPoolName();
    IPCThreadState::self()->joinThreadPool();

    LOG(INFO) << "DisplayService shutting down";
    return 0;
}
