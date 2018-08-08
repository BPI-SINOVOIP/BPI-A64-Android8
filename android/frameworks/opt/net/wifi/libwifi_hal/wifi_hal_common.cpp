/*
 * Copyright 2016, The Android Open Source Project
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

#include "hardware_legacy/wifi.h"

#include <fcntl.h>
#include <stdlib.h>
#include <unistd.h>

#include <android-base/logging.h>
#include <cutils/misc.h>
#include <cutils/properties.h>
#include <sys/syscall.h>

extern "C" int delete_module(const char *, unsigned int);

#ifndef WIFI_DRIVER_FW_PATH_STA
#define WIFI_DRIVER_FW_PATH_STA NULL
#endif
#ifndef WIFI_DRIVER_FW_PATH_AP
#define WIFI_DRIVER_FW_PATH_AP NULL
#endif
#ifndef WIFI_DRIVER_FW_PATH_P2P
#define WIFI_DRIVER_FW_PATH_P2P NULL
#endif

#ifndef WIFI_DRIVER_MODULE_ARG
#define WIFI_DRIVER_MODULE_ARG ""
#endif
#ifndef WIFI_DRIVER_MODULE_PATH
#define WIFI_DRIVER_MODULE_PATH "/system/vendor/modules/"
#endif
static const char DRIVER_PROP_NAME[] = "wlan.driver.status";
#ifdef WIFI_DRIVER_MODULE_PATH
static const char MODULE_FILE[] = "/proc/modules";
#endif

static int insmod(const char *filename, const char *args) {
  int ret;
  int fd;

  fd = open(filename, O_RDONLY | O_CLOEXEC);
  if (fd < 0) return -1;
  LOG(INFO) << "Open "<< filename << " succuss.";

  ret = syscall(__NR_finit_module, fd, args, 0);
  close(fd);

  LOG(INFO) << "Do finit_module ret = " << ret;

  return ret;
}

static int rmmod(const char *modname) {
  int ret = -1;
  int maxtry = 10;

  while (maxtry-- > 0) {
    ret = delete_module(modname, O_NONBLOCK | O_EXCL);
    if (ret < 0 && errno == EAGAIN)
      usleep(500000);
    else
      break;
  }

  if (ret != 0)
    PLOG(DEBUG) << "Unable to unload driver module '" << modname << "'";
  return ret;
}

#ifdef WIFI_DRIVER_STATE_CTRL_PARAM
int wifi_change_driver_state(const char *state) {
  int len;
  int fd;
  int ret = 0;

  if (!state) return -1;
  fd = TEMP_FAILURE_RETRY(open(WIFI_DRIVER_STATE_CTRL_PARAM, O_WRONLY));
  if (fd < 0) {
    PLOG(ERROR) << "Failed to open driver state control param";
    return -1;
  }
  len = strlen(state) + 1;
  if (TEMP_FAILURE_RETRY(write(fd, state, len)) != len) {
    PLOG(ERROR) << "Failed to write driver state control param";
    ret = -1;
  }
  close(fd);
  return ret;
}
#endif

int is_wifi_driver_loaded(const char* wifi_driver_name) {
  char driver_status[PROPERTY_VALUE_MAX];
#ifdef WIFI_DRIVER_MODULE_PATH
  FILE *proc;
  char line[128] = {0};
#endif

  if (!property_get(DRIVER_PROP_NAME, driver_status, NULL) ||
      strcmp(driver_status, "ok") != 0) {
    return 0; /* driver not loaded */
  }
#ifdef WIFI_DRIVER_MODULE_PATH
  /*
   * If the property says the driver is loaded, check to
   * make sure that the property setting isn't just left
   * over from a previous manual shutdown or a runtime
   * crash.
   */
  if ((proc = fopen(MODULE_FILE, "r")) == NULL) {
    PLOG(WARNING) << "Could not open " << MODULE_FILE;
    property_set(DRIVER_PROP_NAME, "unloaded");
    return 0;
  }
  while ((fgets(line, sizeof(line), proc)) != NULL) {
    if (strncmp(line, wifi_driver_name, strlen(wifi_driver_name)) == 0) {
      fclose(proc);
      return 1;
    }
  }
  fclose(proc);
  property_set(DRIVER_PROP_NAME, "unloaded");
  return 0;
#else
  return 1;
#endif
}

int wifi_load_driver() {
#ifdef WIFI_DRIVER_MODULE_PATH
  char module_path[128] = {0};
  char module_arg[128] = {0};
  const char *wifi_driver_name = get_wifi_driver_name();
  if (is_wifi_driver_loaded(wifi_driver_name)) {
    return 0;
  }
  LOG(ERROR) << "wifi_load_driver: Start to insmod " << wifi_driver_name << ".ko";
  get_driver_module_arg(module_arg);
  LOG(ERROR) << "module_arg= " << module_arg;
  if (!strstr(WIFI_DRIVER_MODULE_PATH, ".ko")) {
    snprintf(module_path, sizeof(module_path), "%s%s.ko", WIFI_DRIVER_MODULE_PATH, wifi_driver_name);
  } else {
    snprintf(module_path, sizeof(module_path), "%s", WIFI_DRIVER_MODULE_PATH);
  }
  LOG(ERROR) << "module_path= " << module_path;

  if (insmod(module_path, module_arg) < 0) return -1;
  if (0 == strcmp(get_wifi_vendor_name(),"realtek")) {
    char tmp_buf[2048]  = {0};
    char *p_strstr_wlan = NULL;
    char *p_strstr_p2p  = NULL;
    FILE *fp            = NULL;
    int  count          = 20;

    do {
      fp = fopen("/proc/net/dev", "r");
      if (!fp) {
        LOG(INFO) << "Failed to fopen file: /proc/net/dev";
        property_set(DRIVER_PROP_NAME, "failed");
        rmmod(wifi_driver_name);
        return -1;
      }
      fread(tmp_buf, sizeof(tmp_buf), 1, fp);
      fclose(fp);
      p_strstr_wlan = strstr(tmp_buf, "wlan0");
      p_strstr_p2p  = strstr(tmp_buf, "p2p0");
      if (p_strstr_wlan != NULL && p_strstr_p2p != NULL) {
        LOG(INFO) << "Register net device wlan0 success, time: " << (20 - count) * 200 << "ms.";
        goto Success;
      }
      usleep(200000);
      } while (count--);

      LOG(INFO) << "Timeout, register net device wlan0 failed.";
      property_set(DRIVER_PROP_NAME, "timeout");
      rmmod(wifi_driver_name);
      return -1;
  }
#endif
Success:
#ifdef WIFI_DRIVER_STATE_CTRL_PARAM
  if (is_wifi_driver_loaded(wifi_driver_name)) {
    return 0;
  }

  if (wifi_change_driver_state(WIFI_DRIVER_STATE_ON) < 0) return -1;
#endif
  property_set(DRIVER_PROP_NAME, "ok");
  return 0;
}

int wifi_unload_driver() {
  const char* wifi_driver_name = get_wifi_driver_name();
  if (!is_wifi_driver_loaded(wifi_driver_name)) {
    return 0;
  }
  usleep(200000); /* allow to finish interface down */
#ifdef WIFI_DRIVER_MODULE_PATH
  if (rmmod(wifi_driver_name) == 0) {
    int count = 20; /* wait at most 10 seconds for completion */
    while (count-- > 0) {
      if (!is_wifi_driver_loaded(wifi_driver_name)) break;
      usleep(500000);
    }
    usleep(500000); /* allow card removal */
    if (count) {
      return 0;
    }
    return -1;
  } else
    return -1;
#else
#ifdef WIFI_DRIVER_STATE_CTRL_PARAM
  if (is_wifi_driver_loaded(wifi_driver_name)) {
    if (wifi_change_driver_state(WIFI_DRIVER_STATE_OFF) < 0) return -1;
  }
#endif
  property_set(DRIVER_PROP_NAME, "unloaded");
  return 0;
#endif
}

const char *wifi_get_fw_path(int fw_type) {
  switch (fw_type) {
    case WIFI_GET_FW_PATH_STA:
      return WIFI_DRIVER_FW_PATH_STA;
    case WIFI_GET_FW_PATH_AP:
      return WIFI_DRIVER_FW_PATH_AP;
    case WIFI_GET_FW_PATH_P2P:
      return WIFI_DRIVER_FW_PATH_P2P;
  }
  return NULL;
}

int wifi_change_fw_path(const char *fwpath) {
  int len;
  int fd;
  int ret = 0;

  if (!fwpath) return ret;
  fd = TEMP_FAILURE_RETRY(open(WIFI_DRIVER_FW_PATH_PARAM, O_WRONLY));
  if (fd < 0) {
    PLOG(ERROR) << "Failed to open wlan fw path param";
    return -1;
  }
  len = strlen(fwpath) + 1;
  if (TEMP_FAILURE_RETRY(write(fd, fwpath, len)) != len) {
    PLOG(ERROR) << "Failed to write wlan fw path param";
    ret = -1;
  }
  close(fd);
  return ret;
}
