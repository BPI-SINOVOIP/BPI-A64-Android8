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

#include "Exfat.h"
#include "Utils.h"
#include "VoldUtil.h"

using android::base::StringPrintf;

namespace android {
namespace vold {
namespace exfat {

static const char* kMntPath = "/system/bin/mount.exfat";
static const char* kFsckPath = "/system/bin/fsck.exfat";
static const char* kMkfsPath = "/system/bin/mkfs.exfat";

bool IsSupported() {
    return access(kFsckPath, X_OK) == 0
            && access(kMkfsPath, X_OK) == 0
            && access(kMntPath, X_OK) == 0;
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
            SLOGE("Filesystem check failed (not a EXFAT filesystem)");
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
    char mountData[255];

    const char* c_source = source.c_str();
    const char* c_target = target.c_str();

    sprintf(mountData,
            "locale=utf8,uid=%d,gid=%d,fmask=%o,dmask=%o,noatime,nodiratime",
            ownerUid, ownerGid, permMask, permMask);
    if (access(kMntPath, X_OK)) {
        SLOGW("Skipping fs Mount\n");
        return 1;
    }

    do {
        std::vector<std::string> cmd;
        cmd.push_back(kMntPath);
        cmd.push_back("-o");
        cmd.push_back(mountData);
        cmd.push_back(c_source);
        cmd.push_back(c_target);

        rc = ForkExecvp(cmd);
    } while (0);
	if( rc !=0 ) {
		SLOGE("exfat::Mount error (%s)", strerror(errno));
	}

    return rc;
}

status_t Format(const std::string& source, unsigned int numSectors) {
    SLOGW("[lkj]:Skipping exfat format\n");
    return 0;
}

}  // namespace exfat
}  // namespace vold
}  // namespace android
