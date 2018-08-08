/*
* Copyright (C) 2014 Invensense, Inc.
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

#define FUNC_LOG LOGV("%s", __PRETTY_FUNCTION__)

#include <hardware/sensors.h>
#include <fcntl.h>
#include <errno.h>
#include <dirent.h>
#include <math.h>
#include <poll.h>
#include <pthread.h>
#include <stdlib.h>

#include <linux/input.h>

#include <utils/Atomic.h>
#include <utils/Log.h>

#include "sensors.h"
#include "MPLSensor.h"

#ifdef ALL_WINNER_LIGHT_SENSOR
#include "LightSensor.h"
#include "ProximitySensor.h"
#endif

#ifdef ALL_WINNER_LIGHT_SENSOR
#include <cutils/properties.h>

extern  int insmodDevice(void);
#endif


/*****************************************************************************/
/* The SENSORS Module */

#define LOCAL_SENSORS (TotalNumSensors)

#ifdef SENSORS_DEVICE_API_VERSION_1_4
static MPLSensor *gMPLSensor;
static int data_injection_mode;
static int data_injection_supported;
#endif

static struct sensor_t sSensorList[LOCAL_SENSORS];
static int sensors = (sizeof(sSensorList) / sizeof(sensor_t));

static int open_sensors(const struct hw_module_t* module, const char* id,
                        struct hw_device_t** device);

static int sensors__get_sensors_list(struct sensors_module_t* module,
                                     struct sensor_t const** list)
{
    (void) module;
    *list = sSensorList;
    return sensors;
}

#ifdef SENSORS_DEVICE_API_VERSION_1_4
static int sensors__set_operation_mode(unsigned int mode)
{
    data_injection_mode = mode;
    gMPLSensor->setDataInjectionMode(mode);
    return data_injection_supported;
}
#endif

static struct hw_module_methods_t sensors_module_methods = {
        open: open_sensors
};

struct sensors_module_t HAL_MODULE_INFO_SYM = {
        common: {
                tag: HARDWARE_MODULE_TAG,
                version_major: 1,
                version_minor: 0,
                id: SENSORS_HARDWARE_MODULE_ID,
                name: "Invensense module",
                author: "Invensense Inc.",
                methods: &sensors_module_methods,
                dso: NULL,
                reserved: {0}
        },
        get_sensors_list: sensors__get_sensors_list,
#ifdef SENSORS_DEVICE_API_VERSION_1_4
        set_operation_mode: sensors__set_operation_mode,
#endif
};

struct sensors_poll_context_t {
    sensors_poll_device_1_t device; // must be first

    sensors_poll_context_t();
    ~sensors_poll_context_t();
    int activate(int handle, int enabled);
    int pollEvents(sensors_event_t* data, int count);
    int batch(int handle, int flags, int64_t period_ns, int64_t timeout);
    int flush(int handle);

#ifdef SENSORS_DEVICE_API_VERSION_1_4
    int inject_sensor_data(const sensors_event_t* data);
#endif

private:
    enum {
        mpl = 0,
        compass,
        dmpSign,
        dmpPed,
        dmpTilt,
        dmpPickup,
        #ifdef ALL_WINNER_LIGHT_SENSOR
		light = 6,
		proximity = 7,
		#endif
        numSensorDrivers,
        numFds,
    };

    struct pollfd mPollFds[numFds];
    SensorBase *mSensor;
    CompassSensor *mCompassSensor;
	#ifdef ALL_WINNER_LIGHT_SENSOR
	LightSensor *mLigntSensor;
	ProximitySensor *mProximitySensor;
	#endif
};

/******************************************************************************/

sensors_poll_context_t::sensors_poll_context_t() {
    VFUNC_LOG;

    mCompassSensor = new CompassSensor();
    MPLSensor *mplSensor = new MPLSensor(mCompassSensor);

#ifdef SENSORS_DEVICE_API_VERSION_1_4
    gMPLSensor = mplSensor;
    data_injection_supported = mplSensor->isDataInjectionSupported();
#endif

    // populate the sensor list
    sensors =
            mplSensor->populateSensorList(sSensorList, sizeof(sSensorList));

    mSensor = mplSensor;
    mPollFds[mpl].fd = mSensor->getFd();
    mPollFds[mpl].events = POLLIN;
    mPollFds[mpl].revents = 0;

    mPollFds[compass].fd = mCompassSensor->getFd();
    mPollFds[compass].events = POLLIN;
    mPollFds[compass].revents = 0;

    mPollFds[dmpSign].fd = ((MPLSensor*) mSensor)->getDmpSignificantMotionFd();
    mPollFds[dmpSign].events = POLLPRI;
    mPollFds[dmpSign].revents = 0;

    mPollFds[dmpPed].fd = ((MPLSensor*) mSensor)->getDmpPedometerFd();
    mPollFds[dmpPed].events = POLLPRI;
    mPollFds[dmpPed].revents = 0;

    mPollFds[dmpTilt].fd = ((MPLSensor*) mSensor)->getDmpTiltFd();
    mPollFds[dmpTilt].events = POLLPRI;
    mPollFds[dmpTilt].revents = 0;

    mPollFds[dmpPickup].fd = ((MPLSensor*) mSensor)->getDmpPickupFd();
    mPollFds[dmpPickup].events = POLLPRI;
    mPollFds[dmpPickup].revents = 0;
	#ifdef ALL_WINNER_LIGHT_SENSOR
	mLigntSensor = new LightSensor();
	mPollFds[light].fd = mLigntSensor->getFd();
    mPollFds[light].events = POLLIN;
    mPollFds[light].revents = 0;
	#endif
	#ifdef ALL_WINNER_PROXIMITY_SENSOR
	mProximitySensor = new ProximitySensor();
	mPollFds[proximity].fd = mProximitySensor->getFd();
    mPollFds[proximity].events = POLLIN;
    mPollFds[proximity].revents = 0;
	#endif
	LOGE("aw==== light : %d,px : %d",mLigntSensor->getFd(),mProximitySensor->getFd());
}

sensors_poll_context_t::~sensors_poll_context_t() {
    FUNC_LOG;
    delete mSensor;
    delete mCompassSensor;
#ifdef ALL_WINNER_LIGHT_SENSOR
    delete mLigntSensor;
#endif
#ifdef ALL_WINNER_PROXIMITY_SENSOR
    delete mProximitySensor;
#endif
    for (int i = 0; i < numSensorDrivers; i++) {
        close(mPollFds[i].fd);
    }
}

int sensors_poll_context_t::activate(int handle, int enabled) {
    FUNC_LOG;

    int err;
#ifdef ALL_WINNER_LIGHT_SENSOR
    if (handle == ID_L) {
	err = mLigntSensor->setEnable(handle, enabled);
    } else if (handle == ID_PR) {
	err = mProximitySensor->setEnable(handle, enabled);
   } else {
    err = mSensor->enable(handle, enabled);
	}
#else
	err = mSensor->enable(handle, enabled);
#endif
    return err;
}

int sensors_poll_context_t::pollEvents(sensors_event_t *data, int count)
{
    VHANDLER_LOG;

    int nbEvents = 0;
    int nb, polltime = -1;

    // look for new events
    nb = poll(mPollFds, numSensorDrivers, polltime);
    LOGI_IF(0, "poll nb=%d, count=%d, pt=%d", nb, count, polltime);
    if (nb > 0) {
        for (int i = 0; count && i < numSensorDrivers; i++) {
            if (mPollFds[i].revents & (POLLIN | POLLPRI)) {
                nb = 0;
                if (i == mpl) {
                    ((MPLSensor*) mSensor)->buildMpuEvent();
                    mPollFds[i].revents = 0;
                } else if (i == compass) {
                    nb = mCompassSensor->readEvents(data, count);
                    if (nb < count)
							mPollFds[i].revents = 0;
						count -= nb;
						nbEvents += nb;
						data += nb;
                } else if (i == dmpSign) {
                    nb = ((MPLSensor*) mSensor)->
                                    readDmpSignificantMotionEvents(data, count);
                    mPollFds[i].revents = 0;
                    count -= nb;
                    nbEvents += nb;
                    data += nb;
                } else if (i == dmpPed) {
                    nb = ((MPLSensor*) mSensor)->readDmpPedometerEvents(
                            data, count, ID_P, 0);
                    mPollFds[i].revents = 0;
                    count -= nb;
                    nbEvents += nb;
                    data += nb;
                }
                else if (i == dmpTilt) {
                    nb = ((MPLSensor*) mSensor)->readDmpTiltEvents(
                            data, count);
                    mPollFds[i].revents = 0;
                    count -= nb;
                    nbEvents += nb;
                    data += nb;
					#ifdef ALL_WINNER_LIGHT_SENSOR
					} else if (i == light) {
						nb = mLigntSensor->readEvents(data, count);
						if (nb < count)
							mPollFds[i].revents = 0;
						count -= nb;
						nbEvents += nb;
						data += nb;
					} else if (i == proximity) {
						nb = mProximitySensor->readEvents(data, count);
						if (nb < count)
							mPollFds[i].revents = 0;
						count -= nb;
						nbEvents += nb;
						data += nb;
					#endif
                }
                else if (i == dmpPickup) {
                    nb = ((MPLSensor*) mSensor)->readDmpPickupEvents(
                            data, count);
                    mPollFds[i].revents = 0;
                    count -= nb;
                    nbEvents += nb;
                    data += nb;
                }

                if(nb == 0) {
                    nb = ((MPLSensor*) mSensor)->readEvents(data, count);
                    LOGI_IF(0, "sensors_mpl:readEvents() - "
                            "i=%d, nb=%d, count=%d, nbEvents=%d, "
                            "data->timestamp=%lld, data->data[0]=%f,",
                            i, nb, count, nbEvents, data->timestamp,
                            data->data[0]);
                    if (nb > 0) {
                        count -= nb;
                        nbEvents += nb;
                        data += nb;
                    }
                }
            }
        }
    }

    return nbEvents;
}

int sensors_poll_context_t::batch(int handle, int flags, int64_t period_ns,
                                  int64_t timeout)
{
    FUNC_LOG;
	#ifdef ALL_WINNER_LIGHT_SENSOR
	int err;
	if (handle == ID_L) {
		err = mLigntSensor->batch(handle,flags,period_ns,timeout);
    } else if (handle == ID_PR) {
		err = mProximitySensor->batch(handle, flags,  period_ns,timeout);
	} else {
    	err = mSensor->batch(handle, flags, period_ns, timeout);
	}
	return err;
	#else
	return mSensor->batch(handle, flags, period_ns, timeout);
	#endif
}

int sensors_poll_context_t::flush(int handle)
{
    FUNC_LOG;
    return mSensor->flush(handle);
}

#ifdef SENSORS_DEVICE_API_VERSION_1_4
int sensors_poll_context_t::inject_sensor_data(const sensors_event_t *data)
{
    FUNC_LOG;
    return mSensor->inject_sensor_data(data);
}
#endif

/******************************************************************************/

static int poll__close(struct hw_device_t *dev)
{
    FUNC_LOG;
    sensors_poll_context_t *ctx = (sensors_poll_context_t *)dev;
    if (ctx) {
        delete ctx;
    }
    return 0;
}

static int poll__activate(struct sensors_poll_device_t *dev,
                          int handle, int enabled)
{
    sensors_poll_context_t *ctx = (sensors_poll_context_t *)dev;
    return ctx->activate(handle, enabled);
}

static int poll__poll(struct sensors_poll_device_t *dev,
                      sensors_event_t* data, int count)
{
    sensors_poll_context_t *ctx = (sensors_poll_context_t *)dev;
    return ctx->pollEvents(data, count);
}

static int poll__setDelay(struct sensors_poll_device_t *dev,
                      int handle, int64_t period_ns)
{
    sensors_poll_context_t *ctx = (sensors_poll_context_t *)dev;
    return ctx->batch(handle, 0, period_ns, 0);
}

static int poll__batch(struct sensors_poll_device_1 *dev,
                      int handle, int flags, int64_t period_ns, int64_t timeout)
{
    sensors_poll_context_t *ctx = (sensors_poll_context_t *)dev;
    return ctx->batch(handle, flags, period_ns, timeout);
}

static int poll__flush(struct sensors_poll_device_1 *dev,
                      int handle)
{
    sensors_poll_context_t *ctx = (sensors_poll_context_t *)dev;
    return ctx->flush(handle);
}

#ifdef SENSORS_DEVICE_API_VERSION_1_4
static int poll__inject_sensor_data(struct sensors_poll_device_1 *dev,
                      const sensors_event_t *data)
{
    sensors_poll_context_t *ctx = (sensors_poll_context_t *)dev;
    return ctx->inject_sensor_data(data);
}
#endif

/******************************************************************************/

/** Open a new instance of a sensor device using name */
static int open_sensors(const struct hw_module_t* module, const char* id,
                        struct hw_device_t** device)
{
    FUNC_LOG;
    int status = -EINVAL;
    (void) id;
	#ifdef ALL_WINNER_LIGHT_SENSOR
	sensorsDetect();
	#endif

    sensors_poll_context_t *dev = new sensors_poll_context_t();

    memset(&dev->device, 0, sizeof(sensors_poll_device_1));

    dev->device.common.tag          = HARDWARE_DEVICE_TAG;
#ifdef SENSORS_DEVICE_API_VERSION_1_4
    dev->device.common.version      = SENSORS_DEVICE_API_VERSION_1_4;
#else
    dev->device.common.version      = SENSORS_DEVICE_API_VERSION_1_3;
#endif
    dev->device.common.module       = const_cast<hw_module_t*>(module);
    dev->device.common.close        = poll__close;
    dev->device.activate            = poll__activate;
    dev->device.poll                = poll__poll;
    dev->device.setDelay            = poll__setDelay;
    dev->device.batch               = poll__batch;

    dev->device.flush               = poll__flush;
#ifdef SENSORS_DEVICE_API_VERSION_1_4
    dev->device.inject_sensor_data  = poll__inject_sensor_data;
#endif

    *device = &dev->device.common;
    status = 0;

    return status;
}
