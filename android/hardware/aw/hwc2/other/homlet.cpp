
#include <stdlib.h>
#include <inttypes.h>
#include <utils/String16.h>
#include <binder/IServiceManager.h>
#include <binder/IPCThreadState.h>
#include <binder/ProcessState.h>
#include <system/thread_defs.h>
#include "IHWCPrivateService.h"
#include "IDisplaydCallback.h"

#include "../hwc.h"

class Hwcps: public android::BnHWCPrivateService {
public:
    static Hwcps *instantiate();
    void init();
    void dataspaceChangeNotify(int dataspace);

private:
    Hwcps() {}
   ~Hwcps() {};

    /* API define in IHWCPrivateService.h */
    virtual int blank(int display, int enable);
    virtual int switchDevice(const DeviceTable& tables);
    virtual int setOutputMode(int display, int type, int mode);
    virtual int setMargin(int display, int l, int r, int t, int b);
    virtual int setVideoRatio(int display, int ratio);
    virtual int set3DMode(int display, int mode);
    virtual int setDataspace(int display, int dataspace);
    virtual int registerCallback(android::sp<android::IBinder>& binder);
    virtual void setDebugTag(int32_t tag);
    android::status_t dump(int fd, const android::Vector<android::String16>& args);

    static Hwcps* mInstance;
    android::sp<android::IDisplaydCallback> mClient;
    int mDataspace;
};

Hwcps* Hwcps::mInstance = nullptr;

Hwcps* Hwcps::instantiate()
{
    if (mInstance == nullptr)
        mInstance = new Hwcps();
    return mInstance;
}

int Hwcps::blank(int display, int enable) {
    ALOGD("blank: display[%d], enable[%d]",
          display, enable);
    return 0;
}

int Hwcps::switchDevice(const DeviceTable& tables) {
    ALOGD("switchDevice: phy display count %d",
          tables.mTables.size());
    return 0;
}

int Hwcps::setOutputMode(int display, int type, int mode) {
    ALOGD("setOutputMode: display[%d] type=%d mode=%d",
          display, type, mode);
    hwc_setOutputMode(display, type, mode);
    return 0;
}

int Hwcps::setMargin(int display, int l, int r, int t, int b) {
    ALOGD("setMargin: display[%d] l=%d r=%d t=%d b=%d",
          display, l, r, t, b);
	hwc_setMargin(display, r, b);
    return 0;
}

int Hwcps::setVideoRatio(int display, int ratio) {
    ALOGD("setVideoRatio: display[%d], ratio=%d",
          display, ratio);
	hwc_setVideoRatio(display, ratio);
    return 0;
}

int Hwcps::set3DMode(int display, int mode) {
    ALOGD("set3DMode: display[%d], mode=%d",
          display, mode);
    return 0;
}

int Hwcps::setDataspace(int display, int dataspace) {
    ALOGD("setDataspace: display[%d], dataspace=%d",
          display, dataspace);

    if (dataspace == IHWCPrivateService::eDataspaceHdr)
        dataspace = DISPLAY_OUTPUT_DATASPACE_MODE_HDR;
    else if (dataspace == IHWCPrivateService::eDataspaceSdr)
        dataspace = DISPLAY_OUTPUT_DATASPACE_MODE_SDR;
    else
        ALOGD("Hwcps:setDataspace: unknow dataspace mode: %08x", dataspace);

    hwc_setDataSpacemode(display, dataspace);
    return 0;
}

int Hwcps::registerCallback(android::sp<android::IBinder>& binder) {
    android::sp<android::IDisplaydCallback> client =
                android::interface_cast<android::IDisplaydCallback>(binder);
    mClient = client;
    ALOGD("Callback register from displayd");
    return 0;
}

void Hwcps::setDebugTag(int32_t tag) {
    ALOGD("setDebugTag: tag=%d", tag);
}

android::status_t Hwcps::dump(int fd, const android::Vector<android::String16>& args) {

    android::String8 result;
    result.appendFormat("HWCPrivateService:\n");
    result.appendFormat("  DeviceSlot: none\n");

    write(fd, result.string(), result.size());
    return android::NO_ERROR;
}

void Hwcps::init() {
    /* Register Hwcps */
    android::defaultServiceManager()->addService(
            android::String16("HWCPrivateService"), this);
    ALOGD("HWCPrivateService: init");
}

void Hwcps::dataspaceChangeNotify(int dataspace) {
    if (mDataspace == dataspace)
        return;
    if (mClient.get() != nullptr) {
        mClient->dataspaceChange(dataspace);
        mDataspace = dataspace;
        ALOGD("Update dataspace(0x%08x) to client", dataspace);
    }
}

void init_homlet_service() {
    // publish service
    Hwcps *privateService = Hwcps::instantiate();
    privateService->init();
}

void homlet_dataspace_change_callback(int dataspace) {
    Hwcps *privateService = Hwcps::instantiate();
    privateService->dataspaceChangeNotify(dataspace);
}

