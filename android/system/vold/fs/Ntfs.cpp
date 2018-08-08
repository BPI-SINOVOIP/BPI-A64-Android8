/*
 * Copyright (C) 2008 The Android Open Source Project
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

#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <unistd.h>
#include <errno.h>
#include <string.h>
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>

#include <sys/types.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/mman.h>
#include <sys/mount.h>
#include <sys/wait.h>
#include <linux/fs.h>
#include <sys/ioctl.h>

#include <linux/kdev_t.h>

#define LOG_TAG "Vold"

#include <android-base/logging.h>
#include <android-base/stringprintf.h>
#include <cutils/log.h>
#include <cutils/properties.h>
#include <selinux/selinux.h>

#include <logwrap/logwrap.h>

#include "Ntfs.h"
#include "Utils.h"
#include "VoldUtil.h"

using android::base::StringPrintf;

namespace android {
namespace vold {
namespace ntfs {

static const char* kMntPath = "/system/bin/ntfs-3g";
static const char* kFsckPath = "/system/bin/ntfs-3g.probe";

bool IsSupported() {
    return access(kMntPath, X_OK) == 0
            && access(kFsckPath, X_OK) == 0;
}

status_t Check(const std::string& source) {
    if (access(kFsckPath, X_OK)) {
        SLOGW("Skipping fs checks\n");
        return 0;
    }

    int pass = 1;
    int rc = 0;
    do {
        std::vector<std::string> cmd;
        cmd.push_back(kFsckPath);
        cmd.push_back("--readwrite");
        cmd.push_back(source);

        // Fat devices are currently always untrusted
        rc = ForkExecvp(cmd, sFsckUntrustedContext);

        if (rc < 0) {
            SLOGE("Filesystem check failed due to logwrap error");
            errno = EIO;
            return -1;
        }

        rc = WEXITSTATUS(rc);

        switch(rc) {
        case 0:
            SLOGI("Filesystem check completed OK");
            return 0;

        case 2:
            SLOGE("Filesystem check failed (not a NTFS filesystem)");
            errno = ENODATA;
            return -1;

        case 4:
            if (pass++ <= 3) {
                SLOGW("Filesystem modified - rechecking (pass %d)",
                        pass);
                continue;
            }
            SLOGE("Failing check after too many rechecks");
            errno = EIO;
            return -1;

        case 15:
            SLOGI("The disk contains an unclean file system (0, 0).");
            return 0;

        default:
            SLOGE("Filesystem check failed (unknown exit code %d)", rc);
            errno = EIO;
            return -1;
        }
    } while (0);

    return 0;
}

status_t Mount(const std::string& source, const std::string& target, bool ro,
        bool remount, bool executable, int ownerUid, int ownerGid, int permMask,
        bool createLost) {
    int rc;
    unsigned long flags;
    char mountData[255];

    const char* c_source = source.c_str();
    const char* c_target = target.c_str();

    flags = MS_NODEV | MS_NOSUID | MS_DIRSYNC;

    flags |= (executable ? 0 : MS_NOEXEC);
    flags |= (ro ? MS_RDONLY : 0);
    flags |= (remount ? MS_REMOUNT : 0);

    /*
     * Note: This is a temporary hack. If the sampling profiler is enabled,
     * we make the SD card world-writable so any process can write snapshots.
     *
     * TODO: Remove this code once we have a drop box in system_server.
     */
    char value[PROPERTY_VALUE_MAX];
    property_get("persist.sampling_profiler", value, "");
    if (value[0] == '1') {
        SLOGW("The SD card is world-writable because the"
            " 'persist.sampling_profiler' system property is set to '1'.");
        permMask = 0;
    }

    sprintf(mountData,
            "locale=utf8,uid=%d,gid=%d,fmask=%o,dmask=%o,big_writes,async,noatime,nodiratime",
            ownerUid, ownerGid, permMask, permMask);

#if 0
    rc = mount(c_source, c_target, "ntfs", flags, mountData);
#else
    if (access(kMntPath, X_OK)) {
        SLOGW("Skipping fs Mount\n");
        return 1;
    }

    do {
        std::vector<std::string> cmd;
        cmd.push_back(kMntPath);
        cmd.push_back(c_source);
        cmd.push_back(c_target);
        cmd.push_back("-o");
        cmd.push_back(mountData);

        rc = ForkExecvp(cmd);
    } while (0);
#endif

    if (rc && errno == EROFS) {
        SLOGE("%s appears to be a read only filesystem - retrying mount RO", c_source);
        flags |= MS_RDONLY;
        rc = mount(c_source, c_target, "ntfs", flags, mountData);
    }

    if (rc == 0 && createLost) {
        char *lost_path;
        asprintf(&lost_path, "%s/LOST.DIR", c_target);
        if (access(lost_path, F_OK)) {
            /*
             * Create a LOST.DIR in the root so we have somewhere to put
             * lost cluster chains (fsck_msdos doesn't currently do this)
             */
            if (mkdir(lost_path, 0755)) {
                SLOGE("Unable to create LOST.DIR (%s)", strerror(errno));
            }
        }
        free(lost_path);
    }

    return rc;
}

status_t Format(const std::string& source, unsigned int numSectors) {
    SLOGW("[lkj]:Skipping ntfs format\n");
    return 0;
}

}  // namespace ntfs
}  // namespace vold
}  // namespace android
