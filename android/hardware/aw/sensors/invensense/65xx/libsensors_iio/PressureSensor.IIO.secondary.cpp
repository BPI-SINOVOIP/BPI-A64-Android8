/*
 * Copyright (C) 2014 The Android Open Source Project
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

#define LOG_NDEBUG 0

#include <fcntl.h>
#include <errno.h>
#include <math.h>
#include <unistd.h>
#include <dirent.h>
#include <sys/select.h>
#include <cutils/log.h>
#include <linux/input.h>
#include <string.h>

#include "PressureSensor.IIO.secondary.h"
#include "sensors.h"
#include "MPLSupport.h"
#include "sensor_params.h"
#include "ml_sysfs_helper.h"

#pragma message("HAL:build pressure sensor on Invensense MPU secondary bus")
/* dynamically get this when driver supports it */
#define CHIP_ID "BMP280"

//#define TIMER (1)
#define DEFAULT_POLL_TIME 300
#define PRESSURE_MAX_SYSFS_ATTRB sizeof(pressureSysFs) / sizeof(char*)

static int s_poll_time = -1;
static int min_poll_time = 50;
static struct timespec t_pre;
static struct sensor_t sSensorList[] =
{
     {"Invensense Pressure", "Invensense", 1,
     SENSORS_PRESSURE_HANDLE,
     SENSOR_TYPE_PRESSURE, 1100.0f, 0.009995f, 0.004f, 26315, 0, 90,
     "android.sensor.pressure", "", 200, SENSOR_FLAG_CONTINUOUS_MODE, {}},
     {"Invensense Pressure - Wakeup", "Invensense", 1,
     SENSORS_PRESSURE_WAKEUP_HANDLE,
     SENSOR_TYPE_PRESSURE, 1100.0f, 0.009995f, 0.004f, 26315, 0, 90,
     "android.sensor.pressure", "", 200, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP, {}},
};

/*****************************************************************************/

PressureSensor::PressureSensor(const char *sysfs_path)
                  : SensorBase(NULL, NULL),
                    pressure_fd(-1)
{
    VFUNC_LOG;

    mSysfsPath = sysfs_path;

    int result = find_name_by_sensor_type("in_pressure_enable", "iio:device",
		    sensor_name);
    if(result) {
	    LOGI("Pressure HAL:Cannot read secondary device name - (%d)", result);
	    dev_name = NULL;
	    return;
    } else {
	    dev_name = "bmp280";
    }


    LOGV_IF(ENG_VERBOSE, "pressuresensor path: %s", mSysfsPath);
    if(inv_init_sysfs_attributes()) {
        LOGE("Error Instantiating Pressure Sensor\n");
        return;
    } else {
        LOGI_IF(PROCESS_VERBOSE, "HAL:Secondary Chip Id: %s", CHIP_ID);
    }
}

PressureSensor::~PressureSensor()
{
    VFUNC_LOG;

    if( pressure_fd > 0)
        close(pressure_fd);
}

int PressureSensor::isPressureSensorPresent()
{
    VFUNC_LOG;

    if (dev_name != NULL)
        return 1;
    else
        return 0;
}

int PressureSensor::populateSensorList(struct sensor_t *list, int len) {
    int currentSize = sizeof(sSensorList) / sizeof(sensor_t);
    if(len < currentSize) {
        LOGE("Pressure HAL: sensor list too small, len=%d", len);
        return 0;
    }
    memcpy(list, sSensorList, sizeof (struct sensor_t) * currentSize);
    return currentSize;
}

int PressureSensor::getFd() const
{
    VHANDLER_LOG;
    return pressure_fd;
}

/**
 *  @brief        This function will enable/disable sensor.
 *  @param[in]    handle
 *                  which sensor to enable/disable.
 *  @param[in]    en
 *                  en=1, enable;
 *                  en=0, disable
 *  @return       if the operation is successful.
 */
int PressureSensor::enable(int32_t handle, int en)
{
    VFUNC_LOG;

    int res = 0;
    (void) handle;

    if (handle == ID_PS) {
        LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
                en, pressureSysFs.pressure_enable, getTimestamp());
        res = write_sysfs_int(pressureSysFs.pressure_enable, en);
    }

    if (handle == ID_PSW) {
        LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
                en, pressureSysFs.pressure_wake_enable, getTimestamp());
        res = write_sysfs_int(pressureSysFs.pressure_wake_enable, en);
    }

    return res;
}

int PressureSensor::setDelay(int32_t handle, int64_t ns)
{
    VFUNC_LOG;

    int res = 0;
    (void) handle;

    mDelay = int(1000000000.f / ns);
    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %lld > %s (%lld)",
            mDelay, pressureSysFs.pressure_rate, getTimestamp());
    res = write_sysfs_int(pressureSysFs.pressure_rate, mDelay);

    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %lld > %s (%lld)",
            mDelay, pressureSysFs.pressure_wake_rate, getTimestamp());
    res = write_sysfs_int(pressureSysFs.pressure_wake_rate, mDelay);

#ifdef TIMER
    int t_poll_time = (int)(ns / 1000000LL);
    if (t_poll_time > min_poll_time) {
        s_poll_time = t_poll_time;
    } else {
        s_poll_time = min_poll_time;
    }
    LOGV_IF(PROCESS_VERBOSE,
            "HAL:setDelay : %llu ns, (%.2f Hz)", ns, 1000000000.f/ns);
#endif
    return res;
}


/**
    @brief      This function will return the state of the sensor.
    @return     1=enabled; 0=disabled
**/
int PressureSensor::getEnable(int32_t handle)
{
    VFUNC_LOG;

    (void) handle;
    return mEnable;
}

/**
 *  @brief  This function will return the current delay for this sensor.
 *  @return delay in nanoseconds.
 */
int64_t PressureSensor::getDelay(int32_t handle)
{
    VFUNC_LOG;

    (void) handle;

#ifdef TIMER
    if (mEnable) {
        return s_poll_time;
    } else {
        return -1;
    }
#endif
    return mDelay;
}

void PressureSensor::fillList(struct sensor_t *list)
{
    VFUNC_LOG;

    const char *pressure = "BMP280";

    if (pressure) {
        if(!strcmp(pressure, "BMP280")) {
            list->maxRange = PRESSURE_BMP280_RANGE;
            list->resolution = PRESSURE_BMP280_RESOLUTION;
            list->power = PRESSURE_BMP280_POWER;
            list->minDelay = PRESSURE_BMP280_MINDELAY;
            list->maxDelay = PRESSURE_BMP280_MAXDELAY;
            mMinDelay = list->minDelay;
            return;
        }
    }
    LOGE("HAL:unknown pressure id %s -- "
         "params default to bmp280 and might be wrong.",
         pressure);
    list->maxRange = PRESSURE_BMP280_RANGE;
    list->resolution = PRESSURE_BMP280_RESOLUTION;
    list->power = PRESSURE_BMP280_POWER;
    list->minDelay = PRESSURE_BMP280_MINDELAY;
    list->maxDelay = PRESSURE_BMP280_MAXDELAY;
    mMinDelay = list->minDelay;
    return;
}

int PressureSensor::inv_init_sysfs_attributes(void)
{
    VFUNC_LOG;

    pathP = (char*)malloc(sizeof(char[PRESSURE_MAX_SYSFS_ATTRB][MAX_SYSFS_NAME_LEN]));
    char *sptr = pathP;
    char **dptr = (char**)&pressureSysFs;
    if (sptr == NULL)
        return -1;
    unsigned char i = 0;
    do {
        *dptr++ = sptr;
        memset(sptr, 0, sizeof(char));
        sptr += sizeof(char[MAX_SYSFS_NAME_LEN]);
    } while (++i < PRESSURE_MAX_SYSFS_ATTRB);

    sprintf(pressureSysFs.pressure_enable, "%s%s", mSysfsPath, "/in_pressure_enable");
    sprintf(pressureSysFs.pressure_rate, "%s%s", mSysfsPath, "/in_pressure_rate");
    sprintf(pressureSysFs.pressure_wake_enable, "%s%s", mSysfsPath, "/in_pressure_wake_enable");
    sprintf(pressureSysFs.pressure_wake_rate, "%s%s", mSysfsPath, "/in_pressure_wake_rate");
    return 0;
}
