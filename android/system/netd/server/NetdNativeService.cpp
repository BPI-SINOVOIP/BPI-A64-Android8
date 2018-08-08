/**
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "Netd"

#include <vector>

#include <android-base/stringprintf.h>
#include <cutils/log.h>
#include <cutils/properties.h>
#include <utils/Errors.h>
#include <utils/String16.h>

#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>
#include "android/net/BnNetd.h"

#include <openssl/base64.h>

#include "Controllers.h"
#include "DumpWriter.h"
#include "EventReporter.h"
#include "InterfaceController.h"
#include "NetdConstants.h"
#include "NetdNativeService.h"
#include "RouteController.h"
#include "SockDiag.h"
#include "UidRanges.h"

using android::base::StringPrintf;

namespace android {
namespace net {

namespace {

const char CONNECTIVITY_INTERNAL[] = "android.permission.CONNECTIVITY_INTERNAL";
const char NETWORK_STACK[] = "android.permission.NETWORK_STACK";
const char DUMP[] = "android.permission.DUMP";

binder::Status toBinderStatus(const netdutils::Status s) {
    if (isOk(s)) {
        return binder::Status::ok();
    }
    return binder::Status::fromServiceSpecificError(s.code(), s.msg().c_str());
}

binder::Status checkPermission(const char *permission) {
    pid_t pid;
    uid_t uid;

    if (checkCallingPermission(String16(permission), (int32_t *) &pid, (int32_t *) &uid)) {
        return binder::Status::ok();
    } else {
        auto err = StringPrintf("UID %d / PID %d lacks permission %s", uid, pid, permission);
        return binder::Status::fromExceptionCode(binder::Status::EX_SECURITY, String8(err.c_str()));
    }
}

binder::Status getXfrmStatus(int xfrmCode) {
    switch(xfrmCode) {
        case 0:
            return binder::Status::ok();
        case -ENOENT:
            return binder::Status::fromServiceSpecificError(xfrmCode);
    }
    return binder::Status::fromExceptionCode(xfrmCode);
}

#define ENFORCE_DEBUGGABLE() {                              \
    char value[PROPERTY_VALUE_MAX + 1];                     \
    if (property_get("ro.debuggable", value, NULL) != 1     \
            || value[0] != '1') {                           \
        return binder::Status::fromExceptionCode(           \
            binder::Status::EX_SECURITY,                    \
            String8("Not available in production builds.")  \
        );                                                  \
    }                                                       \
}

#define ENFORCE_PERMISSION(permission) {                    \
    binder::Status status = checkPermission((permission));  \
    if (!status.isOk()) {                                   \
        return status;                                      \
    }                                                       \
}

#define NETD_LOCKING_RPC(permission, lock)                  \
    ENFORCE_PERMISSION(permission);                         \
    android::RWLock::AutoWLock _lock(lock);

#define NETD_BIG_LOCK_RPC(permission) NETD_LOCKING_RPC((permission), gBigNetdLock)
}  // namespace


status_t NetdNativeService::start() {
    IPCThreadState::self()->disableBackgroundScheduling(true);
    status_t ret = BinderService<NetdNativeService>::publish();
    if (ret != android::OK) {
        return ret;
    }
    sp<ProcessState> ps(ProcessState::self());
    ps->startThreadPool();
    ps->giveThreadPoolName();
    return android::OK;
}

status_t NetdNativeService::dump(int fd, const Vector<String16> & /* args */) {
    const binder::Status dump_permission = checkPermission(DUMP);
    if (!dump_permission.isOk()) {
        const String8 msg(dump_permission.toString8());
        write(fd, msg.string(), msg.size());
        return PERMISSION_DENIED;
    }

    // This method does not grab any locks. If individual classes need locking
    // their dump() methods MUST handle locking appropriately.
    DumpWriter dw(fd);
    dw.blankline();
    gCtls->netCtrl.dump(dw);
    dw.blankline();

    return NO_ERROR;
}

binder::Status NetdNativeService::isAlive(bool *alive) {
    NETD_BIG_LOCK_RPC(CONNECTIVITY_INTERNAL);

    *alive = true;
    return binder::Status::ok();
}

binder::Status NetdNativeService::firewallReplaceUidChain(const android::String16& chainName,
        bool isWhitelist, const std::vector<int32_t>& uids, bool *ret) {
    NETD_LOCKING_RPC(CONNECTIVITY_INTERNAL, gCtls->firewallCtrl.lock);

    android::String8 name = android::String8(chainName);
    int err = gCtls->firewallCtrl.replaceUidChain(name.string(), isWhitelist, uids);
    *ret = (err == 0);
    return binder::Status::ok();
}

binder::Status NetdNativeService::bandwidthEnableDataSaver(bool enable, bool *ret) {
    NETD_LOCKING_RPC(CONNECTIVITY_INTERNAL, gCtls->bandwidthCtrl.lock);

    int err = gCtls->bandwidthCtrl.enableDataSaver(enable);
    *ret = (err == 0);
    return binder::Status::ok();
}

binder::Status NetdNativeService::networkRejectNonSecureVpn(bool add,
        const std::vector<UidRange>& uidRangeArray) {
    // TODO: elsewhere RouteController is only used from the tethering and network controllers, so
    // it should be possible to use the same lock as NetworkController. However, every call through
    // the CommandListener "network" command will need to hold this lock too, not just the ones that
    // read/modify network internal state (that is sufficient for ::dump() because it doesn't
    // look at routes, but it's not enough here).
    NETD_BIG_LOCK_RPC(CONNECTIVITY_INTERNAL);

    UidRanges uidRanges(uidRangeArray);

    int err;
    if (add) {
        err = RouteController::addUsersToRejectNonSecureNetworkRule(uidRanges);
    } else {
        err = RouteController::removeUsersFromRejectNonSecureNetworkRule(uidRanges);
    }

    if (err != 0) {
        return binder::Status::fromServiceSpecificError(-err,
                String8::format("RouteController error: %s", strerror(-err)));
    }
    return binder::Status::ok();
}

binder::Status NetdNativeService::socketDestroy(const std::vector<UidRange>& uids,
        const std::vector<int32_t>& skipUids) {

    ENFORCE_PERMISSION(CONNECTIVITY_INTERNAL);

    SockDiag sd;
    if (!sd.open()) {
        return binder::Status::fromServiceSpecificError(EIO,
                String8("Could not open SOCK_DIAG socket"));
    }

    UidRanges uidRanges(uids);
    int err = sd.destroySockets(uidRanges, std::set<uid_t>(skipUids.begin(), skipUids.end()),
                                true /* excludeLoopback */);

    if (err) {
        return binder::Status::fromServiceSpecificError(-err,
                String8::format("destroySockets: %s", strerror(-err)));
    }
    return binder::Status::ok();
}

binder::Status NetdNativeService::setResolverConfiguration(int32_t netId,
        const std::vector<std::string>& servers, const std::vector<std::string>& domains,
        const std::vector<int32_t>& params) {
    // This function intentionally does not lock within Netd, as Bionic is thread-safe.
    ENFORCE_PERMISSION(CONNECTIVITY_INTERNAL);

    int err = gCtls->resolverCtrl.setResolverConfiguration(netId, servers, domains, params);
    if (err != 0) {
        return binder::Status::fromServiceSpecificError(-err,
                String8::format("ResolverController error: %s", strerror(-err)));
    }
    return binder::Status::ok();
}

binder::Status NetdNativeService::getResolverInfo(int32_t netId,
        std::vector<std::string>* servers, std::vector<std::string>* domains,
        std::vector<int32_t>* params, std::vector<int32_t>* stats) {
    // This function intentionally does not lock within Netd, as Bionic is thread-safe.
    ENFORCE_PERMISSION(CONNECTIVITY_INTERNAL);

    int err = gCtls->resolverCtrl.getResolverInfo(netId, servers, domains, params, stats);
    if (err != 0) {
        return binder::Status::fromServiceSpecificError(-err,
                String8::format("ResolverController error: %s", strerror(-err)));
    }
    return binder::Status::ok();
}

binder::Status NetdNativeService::addPrivateDnsServer(const std::string& server, int32_t port,
        const std::string& fingerprintAlgorithm, const std::vector<std::string>& fingerprints) {
    ENFORCE_PERMISSION(CONNECTIVITY_INTERNAL);
    std::set<std::vector<uint8_t>> decoded_fingerprints;
    for (const std::string& input : fingerprints) {
        size_t out_len;
        if (EVP_DecodedLength(&out_len, input.size()) != 1) {
            return binder::Status::fromServiceSpecificError(INetd::PRIVATE_DNS_BAD_FINGERPRINT,
                    "ResolverController error: bad fingerprint length");
        }
        // out_len is now an upper bound on the output length.
        std::vector<uint8_t> decoded(out_len);
        if (EVP_DecodeBase64(decoded.data(), &out_len, decoded.size(),
                reinterpret_cast<const uint8_t*>(input.data()), input.size()) == 1) {
            // Possibly shrink the vector if the actual output was smaller than the bound.
            decoded.resize(out_len);
        } else {
            return binder::Status::fromServiceSpecificError(INetd::PRIVATE_DNS_BAD_FINGERPRINT,
                    "ResolverController error: Base64 parsing failed");
        }
        decoded_fingerprints.insert(decoded);
    }
    const int err = gCtls->resolverCtrl.addPrivateDnsServer(server, port,
            fingerprintAlgorithm, decoded_fingerprints);
    if (err != INetd::PRIVATE_DNS_SUCCESS) {
        return binder::Status::fromServiceSpecificError(err,
                String8::format("ResolverController error: %d", err));
    }
    return binder::Status::ok();
}

binder::Status NetdNativeService::removePrivateDnsServer(const std::string& server) {
    ENFORCE_PERMISSION(CONNECTIVITY_INTERNAL);
    const int err = gCtls->resolverCtrl.removePrivateDnsServer(server);
    if (err != INetd::PRIVATE_DNS_SUCCESS) {
        return binder::Status::fromServiceSpecificError(err,
                String8::format("ResolverController error: %d", err));
    }
    return binder::Status::ok();
}

binder::Status NetdNativeService::tetherApplyDnsInterfaces(bool *ret) {
    NETD_BIG_LOCK_RPC(CONNECTIVITY_INTERNAL);

    *ret = gCtls->tetherCtrl.applyDnsInterfaces();
    return binder::Status::ok();
}

binder::Status NetdNativeService::interfaceAddAddress(const std::string &ifName,
        const std::string &addrString, int prefixLength) {
    ENFORCE_PERMISSION(CONNECTIVITY_INTERNAL);

    const int err = InterfaceController::addAddress(
            ifName.c_str(), addrString.c_str(), prefixLength);
    if (err != 0) {
        return binder::Status::fromServiceSpecificError(-err,
                String8::format("InterfaceController error: %s", strerror(-err)));
    }
    return binder::Status::ok();
}

binder::Status NetdNativeService::interfaceDelAddress(const std::string &ifName,
        const std::string &addrString, int prefixLength) {
    ENFORCE_PERMISSION(CONNECTIVITY_INTERNAL);

    const int err = InterfaceController::delAddress(
            ifName.c_str(), addrString.c_str(), prefixLength);
    if (err != 0) {
        return binder::Status::fromServiceSpecificError(-err,
                String8::format("InterfaceController error: %s", strerror(-err)));
    }
    return binder::Status::ok();
}

binder::Status NetdNativeService::setProcSysNet(
        int32_t family, int32_t which, const std::string &ifname, const std::string &parameter,
        const std::string &value) {
    ENFORCE_PERMISSION(CONNECTIVITY_INTERNAL);

    const char *familyStr;
    switch (family) {
        case INetd::IPV4:
            familyStr = "ipv4";
            break;
        case INetd::IPV6:
            familyStr = "ipv6";
            break;
        default:
            return binder::Status::fromServiceSpecificError(EAFNOSUPPORT, String8("Bad family"));
    }

    const char *whichStr;
    switch (which) {
        case INetd::CONF:
            whichStr = "conf";
            break;
        case INetd::NEIGH:
            whichStr = "neigh";
            break;
        default:
            return binder::Status::fromServiceSpecificError(EINVAL, String8("Bad category"));
    }

    const int err = InterfaceController::setParameter(
            familyStr, whichStr, ifname.c_str(), parameter.c_str(),
            value.c_str());
    if (err != 0) {
        return binder::Status::fromServiceSpecificError(-err,
                String8::format("ResolverController error: %s", strerror(-err)));
    }
    return binder::Status::ok();
}

binder::Status NetdNativeService::getMetricsReportingLevel(int *reportingLevel) {
    // This function intentionally does not lock, since the only thing it does is one read from an
    // atomic_int.
    ENFORCE_PERMISSION(CONNECTIVITY_INTERNAL);
    ENFORCE_DEBUGGABLE();

    *reportingLevel = gCtls->eventReporter.getMetricsReportingLevel();
    return binder::Status::ok();
}

binder::Status NetdNativeService::setMetricsReportingLevel(const int reportingLevel) {
    // This function intentionally does not lock, since the only thing it does is one write to an
    // atomic_int.
    ENFORCE_PERMISSION(CONNECTIVITY_INTERNAL);
    ENFORCE_DEBUGGABLE();

    return (gCtls->eventReporter.setMetricsReportingLevel(reportingLevel) == 0)
            ? binder::Status::ok()
            : binder::Status::fromExceptionCode(binder::Status::EX_ILLEGAL_ARGUMENT);
}

binder::Status NetdNativeService::ipSecAllocateSpi(
        int32_t transformId,
        int32_t direction,
        const std::string& localAddress,
        const std::string& remoteAddress,
        int32_t inSpi,
        int32_t* outSpi) {
    // Necessary locking done in IpSecService and kernel
    ENFORCE_PERMISSION(CONNECTIVITY_INTERNAL);
    ALOGD("ipSecAllocateSpi()");
    return getXfrmStatus(gCtls->xfrmCtrl.ipSecAllocateSpi(
                    transformId,
                    direction,
                    localAddress,
                    remoteAddress,
                    inSpi,
                    outSpi));
}

binder::Status NetdNativeService::ipSecAddSecurityAssociation(
        int32_t transformId,
        int32_t mode,
        int32_t direction,
        const std::string& localAddress,
        const std::string& remoteAddress,
        int64_t underlyingNetworkHandle,
        int32_t spi,
        const std::string& authAlgo, const std::vector<uint8_t>& authKey, int32_t authTruncBits,
        const std::string& cryptAlgo, const std::vector<uint8_t>& cryptKey, int32_t cryptTruncBits,
        int32_t encapType,
        int32_t encapLocalPort,
        int32_t encapRemotePort) {
    // Necessary locking done in IpSecService and kernel
    ENFORCE_PERMISSION(CONNECTIVITY_INTERNAL);
    ALOGD("ipSecAddSecurityAssociation()");
    return getXfrmStatus(gCtls->xfrmCtrl.ipSecAddSecurityAssociation(
              transformId, mode, direction, localAddress, remoteAddress,
              underlyingNetworkHandle,
              spi,
              authAlgo, authKey, authTruncBits,
              cryptAlgo, cryptKey, cryptTruncBits,
              encapType, encapLocalPort, encapRemotePort));
}

binder::Status NetdNativeService::ipSecDeleteSecurityAssociation(
        int32_t transformId,
        int32_t direction,
        const std::string& localAddress,
        const std::string& remoteAddress,
        int32_t spi) {
    // Necessary locking done in IpSecService and kernel
    ENFORCE_PERMISSION(CONNECTIVITY_INTERNAL);
    ALOGD("ipSecDeleteSecurityAssociation()");
    return getXfrmStatus(gCtls->xfrmCtrl.ipSecDeleteSecurityAssociation(
                    transformId,
                    direction,
                    localAddress,
                    remoteAddress,
                    spi));
}

binder::Status NetdNativeService::ipSecApplyTransportModeTransform(
        const android::base::unique_fd& socket,
        int32_t transformId,
        int32_t direction,
        const std::string& localAddress,
        const std::string& remoteAddress,
        int32_t spi) {
    // Necessary locking done in IpSecService and kernel
    ENFORCE_PERMISSION(CONNECTIVITY_INTERNAL);
    ALOGD("ipSecApplyTransportModeTransform()");
    return getXfrmStatus(gCtls->xfrmCtrl.ipSecApplyTransportModeTransform(
                    socket,
                    transformId,
                    direction,
                    localAddress,
                    remoteAddress,
                    spi));
}

binder::Status NetdNativeService::ipSecRemoveTransportModeTransform(
            const android::base::unique_fd& socket) {
    // Necessary locking done in IpSecService and kernel
    ENFORCE_PERMISSION(CONNECTIVITY_INTERNAL);
    ALOGD("ipSecRemoveTransportModeTransform()");
    return getXfrmStatus(gCtls->xfrmCtrl.ipSecRemoveTransportModeTransform(
                    socket));
}

binder::Status NetdNativeService::setIPv6AddrGenMode(const std::string& ifName,
                                                     int32_t mode) {
    ENFORCE_PERMISSION(NETWORK_STACK);
    return toBinderStatus(InterfaceController::setIPv6AddrGenMode(ifName, mode));
}

binder::Status NetdNativeService::wakeupAddInterface(const std::string& ifName,
                                                     const std::string& prefix, int32_t mark,
                                                     int32_t mask) {
    ENFORCE_PERMISSION(NETWORK_STACK);
    return toBinderStatus(gCtls->wakeupCtrl.addInterface(ifName, prefix, mark, mask));
}

binder::Status NetdNativeService::wakeupDelInterface(const std::string& ifName,
                                                     const std::string& prefix, int32_t mark,
                                                     int32_t mask) {
    ENFORCE_PERMISSION(NETWORK_STACK);
    return toBinderStatus(gCtls->wakeupCtrl.delInterface(ifName, prefix, mark, mask));
}

}  // namespace net
}  // namespace android
