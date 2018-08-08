/*
 * Copyright 2014 Intel Corporation
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

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/parsedouble.h>
#include <android-base/stringprintf.h>
#include <fcntl.h>
#include <inttypes.h>
#include <linux/fs.h>
#include <string.h>
#include <sys/mount.h>
#include <sys/wait.h>
#include <unistd.h>

#include "error_code.h"
#include "edify/expr.h"
#include "mounts.h"
#include "updater/updater.h"
#include "BurnBoot.h"
#include "BurnNandBoot.h"
#include "BurnSdBoot.h"

static Value* executeBurnboot(const char* name, State* state, DeviceBurn burnFunc, const char* path) {
    ZipArchiveHandle za = static_cast<UpdaterInfo*>(state->cookie)->package_zip;
    ZipString zip_string_path(path);
    ZipEntry entry;
    if (FindEntry(za, zip_string_path, &entry) != 0) {
      return ErrorAbort(state, kPackageExtractFileFailure, "%s(): no %s in package", name,
                        path);
    }

    std::string buffer;
    buffer.resize(entry.uncompressed_length);

    int32_t ret = ExtractToMemory(za, &entry, reinterpret_cast<uint8_t*>(&buffer[0]), buffer.size());
    if (ret != 0) {
      return ErrorAbort(state, kPackageExtractFileFailure,
                        "%s: Failed to extract entry \"%s\" (%zu bytes) to memory: %s", name,
                        path, buffer.size(), ErrorCodeString(ret));
    }
    BufferExtractCookie* cookie = reinterpret_cast<BufferExtractCookie *>(malloc(sizeof(BufferExtractCookie)));
    cookie->buffer = (unsigned char *)buffer.c_str();
    cookie->len = buffer.size();
    burnFunc(cookie);
    return StringValue("");
}

Value* BurnBootFn(const char* name, State* state, const std::vector<std::unique_ptr<Expr>>& argv) {
    if (argv.size() != 0) {
        return ErrorAbort(state, kArgsParsingFailure, "%s() expects no arg, got %zu", name, argv.size());
    }
    const char* boot0_fex;
    const char* uboot_fex;
    DeviceBurn burnboot0_func;
    DeviceBurn burnuboot_func;
    int flash_type = getFlashType();
    if (flash_type == FLASH_TYPE_UNKNOW)
        return StringValue("");
    int secure = check_soc_is_secure();
    if (secure) {
        boot0_fex = TOC0_FEX;
        uboot_fex = TOC1_FEX;
    } else {
        if (flash_type == FLASH_TYPE_NAND) {
            boot0_fex = BOOT0_NAND_FEX;
        } else {
            boot0_fex = BOOT0_EMMC_FEX;
        }
        uboot_fex = UBOOT_FEX;
    }
    if (flash_type == FLASH_TYPE_NAND) {
        burnboot0_func = burnNandBoot0;
        burnuboot_func = burnNandUboot;
    } else {
        burnboot0_func = burnSdBoot0;
        burnuboot_func = burnSdUboot;
    }
    executeBurnboot(name, state, burnboot0_func, boot0_fex);
    return executeBurnboot(name, state, burnuboot_func, uboot_fex);
}

Value* AssertBootVersionFn(const char* name, State* state, const std::vector<std::unique_ptr<Expr>>& argv) {
    if (argv.size() != 1) {
        return ErrorAbort(state, kArgsParsingFailure, "%s() expects 1 arg, got %zu", name, argv.size());
    }

    std::vector<std::string> args;
    if (!ReadArgs(state, argv, &args)) {
        return ErrorAbort(state, kArgsParsingFailure, "%s() Failed to parse the argument(s)", name);
    }
    const std::string& ver_str = args[0];
    double ota_boot_version;
    if (!android::base::ParseDouble(ver_str.c_str(), &ota_boot_version)) {
        return ErrorAbort(state, kArgsParsingFailure, "%s: failed to parse double in %s\n", name,
                ver_str.c_str());
    }

    if (ota_boot_version != 0) {
        int rc = access("sys/nand_driver0/nand_debug", R_OK);
        double device_boot_version = rc == 0 ? 2.0 : 1.0;
        if (device_boot_version == ota_boot_version) {
            printf("device is boot_v%f and ota is boot_v%f\n", device_boot_version, ota_boot_version);
        } else {
            return ErrorAbort(state, kArgsParsingFailure, "%s() device is boot_v%f , but ota is boot_v%f\n", name, device_boot_version , ota_boot_version);
        }
    }
    return StringValue("t");
}

static int exec_cmd(const char* path, char* const argv[]) {
    pid_t child;
    if ((child = vfork()) == 0) {
        execv(path, argv);
        _exit(EXIT_FAILURE);
    }

    int status;
    waitpid(child, &status, 0);
    if (!WIFEXITED(status) || WEXITSTATUS(status) != 0) {
        LOG(ERROR) << path << " failed with status " << WEXITSTATUS(status) << " err " << errno << " " << strerror(errno);
    }
    return WEXITSTATUS(status);
}

Value* Resize2fsFn(const char* name, State* state, const std::vector<std::unique_ptr<Expr>>& argv) {
    if (argv.size() != 1) {
        return ErrorAbort(state, kArgsParsingFailure, "%s() expects 1 arg, got %zu", name, argv.size());
    }

    std::vector<std::string> args;
    if (!ReadArgs(state, argv, &args)) {
        return ErrorAbort(state, kArgsParsingFailure, "%s() Failed to parse the argument(s)", name);
    }

    std::string cmdline;
    if (android::base::ReadFileToString("/proc/cmdline", &cmdline)) {
        bool gpt = cmdline.find("gpt=1") != std::string::npos;
        LOG(INFO) << name << ": Read /proc/cmdline gpt = " << gpt;
        if (gpt) {
            LOG(INFO) << name << ": Device already use gpt partitions";
            return StringValue("");
        }
    } else {
        LOG(WARNING) << name << ": Failed to read /proc/cmdline";
        return ErrorAbort(state, kArgsParsingFailure, "%s() Failed to read /proc/cmdline", name);
    }
    const std::string& location = args[0];
    int fd = open(location.c_str(), O_RDONLY);
    if (fd == -1) {
        LOG(ERROR) << name << ": Failed to open \"" << location << "\"";
        return ErrorAbort(state, kArgsParsingFailure, "%s() Failed to open \"%s\"", name, location.c_str());
    }
    uint64_t size = 0;
    if (ioctl(fd, BLKGETSIZE64, &size) == -1 || size == 0) {
        LOG(ERROR) << name << ": Failed to get partition size";
        return ErrorAbort(state, kArgsParsingFailure, "%s() Failed to get partition size", name);
    } else if (size < 1024 * 1024) {
        LOG(ERROR) << name << ": Partition size " << size << " too small";
        return ErrorAbort(state, kArgsParsingFailure, "%s() Partition size %" PRIu64 " too small", name, size);
    } else {
        LOG(INFO) << name << ": Partition size " << size;
    }
    // gpt use 20K for backup
    std::string block = android::base::StringPrintf("%" PRIu64 "K", size / 1024 - 20);

    scan_mounted_volumes();
    MountedVolume* vol = find_mounted_volume_by_mount_point("/system");
    if (vol == nullptr) {
        if (mount("/dev/block/by-name/system", "/system", "ext4", MS_NOATIME | MS_NODEV | MS_NODIRATIME, "") < 0) {
            LOG(WARNING) << name << ": Failed to mount /system: " << strerror(errno);
        }
    }

    const char* resize2fs_argv[] = { "/system/bin/resize2fs", "-f", location.c_str(), block.c_str(), nullptr };

    if (access(resize2fs_argv[0], X_OK) != 0) {
        LOG(ERROR) << name << ": Failed to access " << resize2fs_argv[0];
        return ErrorAbort(state, kArgsParsingFailure, "%s() Failed to access %s", name, resize2fs_argv[0]);
    }

    LOG(INFO) << name << ": exec_cmd " << resize2fs_argv[0] << " " << resize2fs_argv[1] << " " << resize2fs_argv[2] << " " << resize2fs_argv[3];
    int status = exec_cmd(resize2fs_argv[0], const_cast<char**>(resize2fs_argv));

    if (status != 0) {
        LOG(ERROR) << name << ": " << resize2fs_argv[0] << " failed with status " << status << " err " << errno << " " << strerror(errno);
        return ErrorAbort(state, kArgsParsingFailure, "%s() %s failed with status %d %d %s", name, resize2fs_argv[0], status, errno, strerror(errno));
    }

    if (vol == nullptr) {
        scan_mounted_volumes();
        vol = find_mounted_volume_by_mount_point("/system");
        if (vol != nullptr) {
            int ret = unmount_mounted_volume(vol);
            if (ret != 0) {
                LOG(WARNING) << name << ": Failed to unmount /system: " << strerror(errno);
            }
        }
    }

    return StringValue("");
}

void Register_librecovery_updater_common() {
    RegisterFunction("burnboot", BurnBootFn);
    RegisterFunction("assert_boot_version", AssertBootVersionFn);
    RegisterFunction("resize2fs", Resize2fsFn);
}
