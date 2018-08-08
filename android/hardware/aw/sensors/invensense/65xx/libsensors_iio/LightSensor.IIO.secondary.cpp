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

#include "LightSensor.IIO.secondary.h"
#include "sensors.h"
#include "MPLSupport.h"
#include "sensor_params.h"
#include "ml_sysfs_helper.h"

#pragma message("HAL:build light sensor on Invensense MPU secondary bus")
#define CHIP_ID "APDS9930"

//#define TIMER (1)
#define DEFAULT_POLL_TIME 300
#define LIGHT_MAX_SYSFS_ATTRB sizeof(lightSysFs) / sizeof(char*)

static int s_poll_time = -1;
static int min_poll_time = 50;
static struct timespec t_pre;

/*
   Android L
   Populate sensor_t structure according to hardware sensors.h
   {    name, vendor, version,
        handle,
        type, maxRange, resolution, power, minDelay, fifoReservedEventCount, fifoMaxEventCount,
        stringType, requiredPermission, maxDelay, flags, reserved[]    }
*/
static struct sensor_t sSensorList[] =
{
     {"Invensense Light", "Invensense", 1,
     SENSORS_LIGHT_HANDLE,
     SENSOR_TYPE_LIGHT, 10000.0f, 0.009994f, 0.175f, 0, 0, 15,
     "android.sensor.light", "", 112000, SENSOR_FLAG_ON_CHANGE_MODE, {}},
     {"Invensense Light - Wakeup", "Invensense", 1,
     SENSORS_LIGHT_WAKEUP_HANDLE,
     SENSOR_TYPE_LIGHT, 10000.0f, 0.009994f, 0.175f, 0, 0, 15,
     "android.sensor.light", "", 112000, SENSOR_FLAG_ON_CHANGE_MODE | SENSOR_FLAG_WAKE_UP, {}},
     {"Invensense Proxmity", "Invensense", 1,
     SENSORS_PROXIMITY_HANDLE,
     SENSOR_TYPE_PROXIMITY, 5.0f, 1.0f, 0.5f, 10000, 0, 15,
     "android.sensor.proximity", "", 112000, SENSOR_FLAG_ON_CHANGE_MODE, {}},
     {"Invensense Proxmity - Wakeup", "Invensense", 1,
     SENSORS_PROXIMITY_WAKEUP_HANDLE,
     SENSOR_TYPE_PROXIMITY, 5.0f, 1.0f, 0.5f, 10000, 0, 15,
     "android.sensor.proximity", "", 112000, SENSOR_FLAG_ON_CHANGE_MODE | SENSOR_FLAG_WAKE_UP, {}},
};

/*****************************************************************************/

LightSensor::LightSensor(const char *sysfs_path)
                  : SensorBase(NULL, NULL),
                    light_fd(-1)
{
    VFUNC_LOG;

    mSysfsPath = sysfs_path;

    int result = find_name_by_sensor_type("in_als_px_enable", "iio:device",
                                            sensor_name);
    if(result) {
        LOGI("Light HAL:Cannot read secondary device name - (%d)", result);
        dev_name = NULL;
    } else {
	dev_name = "alx";
    }
    LOGV_IF(ENG_VERBOSE, "lightsensor path: %s", mSysfsPath);
    if(inv_init_sysfs_attributes()) {
        LOGE("Error Instantiating Light Sensor\n");
        return;
    } else {
        LOGI_IF(PROCESS_VERBOSE, "Light HAL:Secondary Chip Id: %s", CHIP_ID);
    }
}

LightSensor::~LightSensor()
{
    VFUNC_LOG;

    if( light_fd > 0)
        close(light_fd);
}

int LightSensor::isLightSensorPresent() {
   VFUNC_LOG;

   if (dev_name != NULL)
       return 1;
   else
       return 0;
}

int LightSensor::populateSensorList(struct sensor_t *list, int len) {
    int currentSize = sizeof(sSensorList) / sizeof(sensor_t);
    if(len < currentSize) {
        LOGE("Light HAL: sensor list too small, len=%d", len);
        return 0;
    }
    memcpy(list, sSensorList, sizeof(struct sensor_t) * currentSize);
    return currentSize;
}

int LightSensor::getFd() const
{
    VHANDLER_LOG;
    return light_fd;
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
int LightSensor::enable(int32_t handle, int en)
{
    VFUNC_LOG;

    int res = 0;
    (void) handle;

    if ((handle == ID_L) || (handle == ID_PR)) {
        LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            en, lightSysFs.light_enable, getTimestamp());
        res = write_sysfs_int(lightSysFs.light_enable, en);
    }

    if ((handle == ID_LW) || (handle == ID_PRW)) {
        LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            en, lightSysFs.light_wake_enable, getTimestamp());
        res = write_sysfs_int(lightSysFs.light_wake_enable, en);
    }
    return res;
}

int LightSensor::setDelay(int32_t handle, int64_t ns)
{
    VFUNC_LOG;

    int res = 0;
    (void) handle;

    mDelay = int(1000000000.f / ns);
    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %lld > %s (%lld)",
            mDelay, lightSysFs.light_rate, getTimestamp());
    res = write_sysfs_int(lightSysFs.light_rate, mDelay);

    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %lld > %s (%lld)",
            mDelay, lightSysFs.light_wake_rate, getTimestamp());
    res = write_sysfs_int(lightSysFs.light_wake_rate, mDelay);

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
int LightSensor::getEnable(int32_t handle)
{
    VFUNC_LOG;

    (void) handle;

    return mEnable;
}

/**
 *  @brief  This function will return the current delay for this sensor.
 *  @return delay in nanoseconds.
 */
int64_t  LightSensor::getDelay(int32_t handle)
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

void LightSensor::fillLightList(struct sensor_t *list)
{
    VFUNC_LOG;

    const char *light = "APDS9930";

    /* fill in light sensor meta data */
    if (light) {
        if(!strcmp(light, "APDS9930")) {
            list->maxRange = LIGHT_APS9930_RANGE;
            list->resolution = LIGHT_APS9930_RESOLUTION;
            list->power = LIGHT_APS9930_POWER;
            list->minDelay = LIGHT_APS9930_MINDELAY;
            list->maxDelay = LIGHT_APS9930_MAXDELAY;
            mMinDelay = list->minDelay;
            return;
        }
    }

    LOGE("HAL:unknown light/proximity id %s -- "
         "params default to apds9930 and might be wrong.",
         light);
    list->maxRange = LIGHT_APS9930_RANGE;
    list->resolution = LIGHT_APS9930_RESOLUTION;
    list->power = LIGHT_APS9930_POWER;
    list->minDelay = LIGHT_APS9930_MINDELAY;
    list->maxDelay = LIGHT_APS9930_MAXDELAY;
    mMinDelay = list->minDelay;
    return;
}

void LightSensor::fillProxList(struct sensor_t *list)
{
    VFUNC_LOG;

    const char *proximity = "APDS9930";

    /* fill in proximity sensor meta data */
    if (proximity) {
         if(!strcmp(proximity, "APDS9930")) {
            list->maxRange = PROXIMITY_APS9930_RANGE;
            list->resolution = PROXIMITY_APS9930_RESOLUTION;
            list->power = PROXIMITY_APS9930_POWER;
            list->minDelay = PROXIMITY_APS9930_MINDELAY;
            list->maxDelay = PROXIMITY_APS9930_MAXDELAY;
            mMinDelay = list->minDelay;
            return;
        }
    }

    LOGE("HAL:unknown light/proximity id %s -- "
         "params default to apds9930 and might be wrong.",
         proximity);

    list->maxRange = PROXIMITY_APS9930_RANGE;
    list->resolution = PROXIMITY_APS9930_RESOLUTION;
    list->power = PROXIMITY_APS9930_POWER;
    list->minDelay = PROXIMITY_APS9930_MINDELAY;
    list->maxDelay = PROXIMITY_APS9930_MAXDELAY;
    mMinDelay = list->minDelay;
    return;
}

int LightSensor::inv_init_sysfs_attributes(void)
{
    VFUNC_LOG;

    pathP = (char*)malloc(sizeof(char[LIGHT_MAX_SYSFS_ATTRB][MAX_SYSFS_NAME_LEN]));
    char *sptr = pathP;
    char **dptr = (char**)&lightSysFs;
    if (sptr == NULL)
        return -1;
    unsigned char i = 0;
    do {
        *dptr++ = sptr;
        memset(sptr, 0, sizeof(char));
        sptr += sizeof(char[MAX_SYSFS_NAME_LEN]);
    } while (++i < LIGHT_MAX_SYSFS_ATTRB);

    sprintf(lightSysFs.light_enable, "%s%s", mSysfsPath, "/in_als_px_enable");
    sprintf(lightSysFs.light_rate, "%s%s", mSysfsPath, "/in_als_px_rate");
    sprintf(lightSysFs.light_wake_enable, "%s%s", mSysfsPath, "/in_als_px_wake_enable");
    sprintf(lightSysFs.light_wake_rate, "%s%s", mSysfsPath, "/in_als_px_wake_rate");
    return 0;
}
