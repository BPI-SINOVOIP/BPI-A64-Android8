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

#include <sys/mount.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/ioctl.h>

#include <linux/kdev_t.h>

#define LOG_TAG "Vold"

#include <cutils/log.h>

#include <android-base/logging.h>
#include <android-base/stringprintf.h>
#include <android-base/unique_fd.h>

#include <sysutils/SocketClient.h>
#include "Loop.h"
#include "Asec.h"
#include "VoldUtil.h"
#include "sehandle.h"

using android::base::StringPrintf;
using android::base::unique_fd;


bool set_autohogplug_enable(bool enable);

bool get_autohogplug_enable();


int Loop::dumpState(SocketClient *c) {
    int i;
    int fd;
    char filename[256];

	bool need_reset_autohotplug = false;
	if(get_autohogplug_enable()) {
		if(set_autohogplug_enable(0)) {
			need_reset_autohotplug = true;
		}
	}


    for (i = 0; i < LOOP_MAX; i++) {
        struct loop_info64 li;
        int rc;

        snprintf(filename, sizeof(filename), "/dev/block/loop%d", i);

        if ((fd = open(filename, O_RDWR | O_CLOEXEC)) < 0) {
            if (errno != ENOENT) {
                SLOGE("Unable to open %s (%s)", filename, strerror(errno));
            } else {
                continue;
            }
            goto ERROR;
        }

        rc = ioctl(fd, LOOP_GET_STATUS64, &li);
        close(fd);
        if (rc < 0 && errno == ENXIO) {
            continue;
        }

        if (rc < 0) {
            SLOGE("Unable to get loop status for %s (%s)", filename,
                 strerror(errno));
            goto ERROR;
        }
        char *tmp = NULL;
        asprintf(&tmp, "%s %d %lld:%lld %llu %lld:%lld %lld 0x%x {%s} {%s}", filename, li.lo_number,
                MAJOR(li.lo_device), MINOR(li.lo_device), li.lo_inode, MAJOR(li.lo_rdevice),
                        MINOR(li.lo_rdevice), li.lo_offset, li.lo_flags, li.lo_crypt_name,
                        li.lo_file_name);
        c->sendMsg(0, tmp, false);
        free(tmp);
    }
	if(need_reset_autohotplug) {
		set_autohogplug_enable(1);
	}
    return 0;

ERROR:
	if(need_reset_autohotplug) {
		set_autohogplug_enable(1);
	}
	return -1;
}

int Loop::lookupActive(const char *id, char *buffer, size_t len) {
    int i;
    int fd;
    char filename[256];

    memset(buffer, 0, len);

	bool need_reset_autohotplug = false;
	if(get_autohogplug_enable()) {
		if(set_autohogplug_enable(0)) {
			need_reset_autohotplug = true;
		}
	}

    for (i = 0; i < LOOP_MAX; i++) {
        struct loop_info64 li;
        int rc;

        snprintf(filename, sizeof(filename), "/dev/block/loop%d", i);

        if ((fd = open(filename, O_RDWR | O_CLOEXEC)) < 0) {
            if (errno != ENOENT) {
                SLOGE("Unable to open %s (%s)", filename, strerror(errno));
            } else {
                continue;
            }
            goto ERROR;
        }

        rc = ioctl(fd, LOOP_GET_STATUS64, &li);
        if (rc < 0 && errno == ENXIO) {
            close(fd);
            continue;
        }
        close(fd);

        if (rc < 0) {
            SLOGE("Unable to get loop status for %s (%s)", filename,
                 strerror(errno));
            goto ERROR;
        }
        if (!strncmp((const char*) li.lo_crypt_name, id, LO_NAME_SIZE)) {
            break;
        }
    }

    if (i == LOOP_MAX) {
        errno = ENOENT;
        goto ERROR;
    }
    strlcpy(buffer, filename, len);
	if(need_reset_autohotplug) {
		set_autohogplug_enable(1);
	}

	return 0;

ERROR:
	if(need_reset_autohotplug) {
		set_autohogplug_enable(1);
	}
	return -1;
}

int Loop::create(const char *id, const char *loopFile, char *loopDeviceBuffer, size_t len) {
    int i;
    int fd;
    char filename[256];

	bool need_reset_autohotplug = false;
	if(get_autohogplug_enable()) {
		if(set_autohogplug_enable(0)) {
			need_reset_autohotplug = true;
		}
	}

    for (i = 0; i < LOOP_MAX; i++) {
        struct loop_info64 li;
        int rc;
        char *secontext = NULL;

        snprintf(filename, sizeof(filename), "/dev/block/loop%d", i);

        /*
         * The kernel starts us off with 8 loop nodes, but more
         * are created on-demand if needed.
         */
        mode_t mode = 0660 | S_IFBLK;
        unsigned int dev = (0xff & i) | ((i << 12) & 0xfff00000) | (7 << 8);

        if (sehandle) {
            rc = selabel_lookup(sehandle, &secontext, filename, S_IFBLK);
            if (rc == 0)
                setfscreatecon(secontext);
        }

        if (mknod(filename, mode, dev) < 0) {
            if (errno != EEXIST) {
                int sverrno = errno;
                SLOGE("Error creating loop device node (%s)", strerror(errno));
                if (secontext) {
                    freecon(secontext);
                    setfscreatecon(NULL);
                }
                errno = sverrno;
               goto ERROR;
            }
        }
        if (secontext) {
            freecon(secontext);
            setfscreatecon(NULL);
        }

        if ((fd = open(filename, O_RDWR | O_CLOEXEC)) < 0) {
            SLOGE("Unable to open %s (%s)", filename, strerror(errno));
            goto ERROR;
        }

        rc = ioctl(fd, LOOP_GET_STATUS64, &li);
        if (rc < 0 && errno == ENXIO)
            break;

        close(fd);

        if (rc < 0) {
            SLOGE("Unable to get loop status for %s (%s)", filename,
                 strerror(errno));
            goto ERROR;
        }
    }

    if (i == LOOP_MAX) {
        SLOGE("Exhausted all loop devices");
        errno = ENOSPC;
        goto ERROR;
    }

    strlcpy(loopDeviceBuffer, filename, len);

    int file_fd;

    if ((file_fd = open(loopFile, O_RDWR | O_CLOEXEC)) < 0) {
        SLOGE("Unable to open %s (%s)", loopFile, strerror(errno));
        close(fd);
        goto ERROR;
    }

    if (ioctl(fd, LOOP_SET_FD, file_fd) < 0) {
        SLOGE("Error setting up loopback interface (%s)", strerror(errno));
        close(file_fd);
        close(fd);
       goto ERROR;
    }

    struct loop_info64 li;

    memset(&li, 0, sizeof(li));
    strlcpy((char*) li.lo_crypt_name, id, LO_NAME_SIZE);
    strlcpy((char*) li.lo_file_name, loopFile, LO_NAME_SIZE);

    if (ioctl(fd, LOOP_SET_STATUS64, &li) < 0) {
        SLOGE("Error setting loopback status (%s)", strerror(errno));
        close(file_fd);
        close(fd);
       goto ERROR;
    }

    close(fd);
    close(file_fd);
	if(need_reset_autohotplug) {
		set_autohogplug_enable(1);
	}

    return 0;

ERROR:
	if(need_reset_autohotplug) {
		set_autohogplug_enable(1);
	}
	return -1;
}

int Loop::create(const std::string& target, std::string& out_device) {
    unique_fd ctl_fd(open("/dev/loop-control", O_RDWR | O_CLOEXEC));
    if (ctl_fd.get() == -1) {
        PLOG(ERROR) << "Failed to open loop-control";
        return -errno;
    }

    int num = ioctl(ctl_fd.get(), LOOP_CTL_GET_FREE);
    if (num == -1) {
        PLOG(ERROR) << "Failed LOOP_CTL_GET_FREE";
        return -errno;
    }

    out_device = StringPrintf("/dev/block/loop%d", num);

    unique_fd target_fd(open(target.c_str(), O_RDWR | O_CLOEXEC));
    if (target_fd.get() == -1) {
        PLOG(ERROR) << "Failed to open " << target;
        return -errno;
    }
    unique_fd device_fd(open(out_device.c_str(), O_RDWR | O_CLOEXEC));
    if (device_fd.get() == -1) {
        PLOG(ERROR) << "Failed to open " << out_device;
        return -errno;
    }

    if (ioctl(device_fd.get(), LOOP_SET_FD, target_fd.get()) == -1) {
        PLOG(ERROR) << "Failed to LOOP_SET_FD";
        return -errno;
    }

    return 0;
}

int Loop::destroyByDevice(const char *loopDevice) {
    int device_fd;

    device_fd = open(loopDevice, O_RDONLY | O_CLOEXEC);
    if (device_fd < 0) {
        SLOGE("Failed to open loop (%d)", errno);
        return -1;
    }

    if (ioctl(device_fd, LOOP_CLR_FD, 0) < 0) {
        SLOGE("Failed to destroy loop (%d)", errno);
        close(device_fd);
        return -1;
    }

    close(device_fd);
    return 0;
}

int Loop::destroyByFile(const char * /*loopFile*/) {
    errno = ENOSYS;
    return -1;
}

int Loop::createImageFile(const char *file, unsigned long numSectors) {
    unique_fd fd(open(file, O_CREAT | O_WRONLY | O_TRUNC | O_CLOEXEC, 0600));
    if (fd.get() == -1) {
        PLOG(ERROR) << "Failed to create image " << file;
        return -errno;
    }
    if (fallocate(fd.get(), 0, 0, numSectors * 512) == -1) {
        PLOG(WARNING) << "Failed to fallocate; falling back to ftruncate";
        if (ftruncate(fd, numSectors * 512) == -1) {
            PLOG(ERROR) << "Failed to ftruncate";
            return -errno;
        }
    }
    return 0;
}

int Loop::resizeImageFile(const char *file, unsigned long numSectors) {
    int fd;

    if ((fd = open(file, O_RDWR | O_CLOEXEC)) < 0) {
        SLOGE("Error opening imagefile (%s)", strerror(errno));
        return -1;
    }

    SLOGD("Attempting to increase size of %s to %lu sectors.", file, numSectors);

    if (fallocate(fd, 0, 0, numSectors * 512)) {
        if (errno == ENOSYS || errno == ENOTSUP) {
            SLOGW("fallocate not found. Falling back to ftruncate.");
            if (ftruncate(fd, numSectors * 512) < 0) {
                SLOGE("Error truncating imagefile (%s)", strerror(errno));
                close(fd);
                return -1;
            }
        } else {
            SLOGE("Error allocating space (%s)", strerror(errno));
            close(fd);
            return -1;
        }
    }
    close(fd);
    return 0;
}

int Loop::lookupInfo(const char *loopDevice, struct asec_superblock *sb, unsigned long *nr_sec) {
    int fd;
    struct asec_superblock buffer;

    if ((fd = open(loopDevice, O_RDONLY | O_CLOEXEC)) < 0) {
        SLOGE("Failed to open loopdevice (%s)", strerror(errno));
        destroyByDevice(loopDevice);
        return -1;
    }

    get_blkdev_size(fd, nr_sec);
    if (*nr_sec == 0) {
        SLOGE("Failed to get loop size (%s)", strerror(errno));
        destroyByDevice(loopDevice);
        close(fd);
        return -1;
    }

    /*
     * Try to read superblock.
     */
    memset(&buffer, 0, sizeof(struct asec_superblock));
    if (lseek(fd, ((*nr_sec - 1) * 512), SEEK_SET) < 0) {
        SLOGE("lseek failed (%s)", strerror(errno));
        close(fd);
        destroyByDevice(loopDevice);
        return -1;
    }
    if (read(fd, &buffer, sizeof(struct asec_superblock)) != sizeof(struct asec_superblock)) {
        SLOGE("superblock read failed (%s)", strerror(errno));
        close(fd);
        destroyByDevice(loopDevice);
        return -1;
    }
    close(fd);

    /*
     * Superblock successfully read. Copy to caller's struct.
     */
    memcpy(sb, &buffer, sizeof(struct asec_superblock));
    return 0;
}




/*author:jiangbin,fix-block-mq wait-deadlock with disable autohotplug*/

#define AUTOHOTPLUG_ENABLE "/sys/kernel/autohotplug/enable"

bool get_autohogplug_enable() {

	SLOGD("get- %s",AUTOHOTPLUG_ENABLE);
    char buf[64];
    int len;
    int fd = open(AUTOHOTPLUG_ENABLE, O_RDONLY);
    if (fd < 0) {
        strerror_r(errno, buf, sizeof(buf));
        SLOGE("Error opening %s: %s\n", AUTOHOTPLUG_ENABLE, buf);
        return false;
    }
    len = read(fd, buf, strlen("0"));
    if (len < 0) {
        strerror_r(errno, buf, sizeof(buf));
        SLOGE("Error read %s\n", AUTOHOTPLUG_ENABLE);
		close(fd);
		return false;
    }
    close(fd);
	return !strncmp(buf, "1", strlen("1"));
}


bool set_autohogplug_enable(bool enable) {

	SLOGD("set- %s %d",AUTOHOTPLUG_ENABLE,enable);
    char buf[64];
    int len;
    int fd = open(AUTOHOTPLUG_ENABLE, O_WRONLY);
    if (fd < 0) {
        strerror_r(errno, buf, sizeof(buf));
        SLOGE("Error opening %s: %s\n", AUTOHOTPLUG_ENABLE, buf);
        return false;
    }
    len = write(fd, enable?"1":"0", strlen("0"));
    if (len < 0) {
        strerror_r(errno, buf, sizeof(buf));
        SLOGE("Error writing to %s: %s\n", AUTOHOTPLUG_ENABLE, buf);
		close(fd);
		return false;
    }
    close(fd);

	return true;

}
/*end-change*/
