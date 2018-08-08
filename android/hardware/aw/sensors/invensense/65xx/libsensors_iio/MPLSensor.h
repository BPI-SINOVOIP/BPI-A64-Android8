/*
* Copyright (C) 2016 Invensense, Inc.
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

#ifndef ANDROID_MPL_SENSOR_H
#define ANDROID_MPL_SENSOR_H

#include <stdint.h>
#include <errno.h>
#include <sys/cdefs.h>
#include <sys/types.h>
#include <poll.h>
#include <time.h>
#include <utils/Vector.h>
#include <utils/KeyedVector.h>
#include <utils/String8.h>
#include "sensors.h"
#include "SensorBase.h"
#include "InputEventReader.h"

#ifndef INVENSENSE_COMPASS_CAL
#pragma message("unified HAL for AKM")
#include "CompassSensor.AKM.h"
#endif

#ifdef SENSOR_ON_PRIMARY_BUS
#pragma message("Sensor on Primary Bus")
#include "CompassSensor.IIO.primary.h"
#else
#pragma message("Sensor on Secondary Bus")
#include "CompassSensor.IIO.secondary.h"
#endif

class PressureSensor;
class LightSensor;

/*
 * Version defines
 */
#ifndef INV_LIBSENSORS_VERSION_MAJOR
#  define INV_LIBSENSORS_VERSION_MAJOR				0
#endif
#ifndef INV_LIBSENSORS_VERSION_MINOR
#  define INV_LIBSENSORS_VERSION_MINOR				0
#endif
#ifndef INV_LIBSENSORS_VERSION_PATCH
#  define INV_LIBSENSORS_VERSION_PATCH				0
#endif
#ifndef INV_LIBSENSORS_VERSION_SUFFIX
#  define INV_LIBSENSORS_VERSION_SUFFIX				"-dev"
#endif

/*****************************************************************************/
/* Sensors Enable/Disable Mask
 *****************************************************************************/
#define MAX_CHIP_ID_LEN                 (20)

#define INV_THREE_AXIS_RAW_GYRO         (1LL << RawGyro)
#define INV_THREE_AXIS_GYRO             (1LL << Gyro)
#define INV_THREE_AXIS_ACCEL            (1LL << Accelerometer)
#define INV_THREE_AXIS_RAW_COMPASS      (1LL << RawMagneticField)
#define INV_THREE_AXIS_COMPASS          (1LL << MagneticField)
#define INV_ONE_AXIS_PRESSURE           (1LL << Pressure)
#define INV_ONE_AXIS_LIGHT              (1LL << Light)

#define INV_THREE_AXIS_RAW_GYRO_WAKE         (1LL << RawGyro_Wake)
#define INV_THREE_AXIS_GYRO_WAKE             (1LL << Gyro_Wake)
#define INV_THREE_AXIS_ACCEL_WAKE            (1LL << Accelerometer_Wake)
#define INV_THREE_AXIS_RAW_COMPASS_WAKE      (1LL << RawMagneticField_Wake)
#define INV_THREE_AXIS_COMPASS_WAKE          (1LL << MagneticField_Wake)
#define INV_ONE_AXIS_PRESSURE_WAKE           (1LL << Pressure_Wake)
#define INV_ONE_AXIS_LIGHT_WAKE              (1LL << Light_Wake)
#define INV_ALL_SENSORS_WAKE                 (0xFFFFFFFFFFFFFFFF)

#define ALL_MPL_SENSORS_NP          (INV_THREE_AXIS_ACCEL \
                                      | INV_THREE_AXIS_COMPASS \
                                      | INV_THREE_AXIS_GYRO \
                                      | INV_ONE_AXIS_PRESSURE \
                                      | INV_ONE_AXIS_Light)

#define ALL_MPL_SENSORS_NP_WAKE      (INV_THREE_AXIS_ACCEL_WAKE \
                                      | INV_THREE_AXIS_COMPASS_WAKE \
                                      | INV_THREE_AXIS_GYRO_WAKE \
                                      | INV_ONE_AXIS_PRESSURE_WAKE \
                                      | INV_ONE_AXIS_Light_WAKE)
#define GYRO_SIX_AXIS_MPL_SENSOR    (INV_THREE_AXIS_ACCEL\
				      | INV_THREE_AXIS_GYRO)
#define MAG_SIX_AXIS_MPL_SENSOR     (INV_THREE_AXIS_ACCEL \
                                      | INV_THREE_AXIS_COMPASS)
#define NINE_AXIS_MPL_SENSOR        (INV_THREE_AXIS_ACCEL \
                                      | INV_THREE_AXIS_COMPASS \
                                      | INV_THREE_AXIS_GYRO)
#define GYRO_SIX_AXIS_MPL_SENSOR_WAKE    (INV_THREE_AXIS_ACCEL_WAKE\
					   | INV_THREE_AXIS_GYRO_WAKE)
#define MAG_SIX_AXIS_MPL_SENSOR_WAKE     (INV_THREE_AXIS_ACCEL_WAKE \
                                            | INV_THREE_AXIS_COMPASS_WAKE)
#define NINE_AXIS_MPL_SENSOR_WAKE        (INV_THREE_AXIS_ACCEL_WAKE \
                                            | INV_THREE_AXIS_COMPASS_WAKE \
                                            | INV_THREE_AXIS_GYRO_WAKE)
#define GYRO_MPL_SENSOR				(INV_THREE_AXIS_RAW_GYRO \
									 | INV_THREE_AXIS_GYRO \
									 | INV_THREE_AXIS_RAW_GYRO_WAKE \
									 | INV_THREE_AXIS_GYRO_WAKE)
#define AUX_MPL_SENSOR				(INV_THREE_AXIS_RAW_COMPASS \
									 | INV_THREE_AXIS_COMPASS \
									 | INV_ONE_AXIS_PRESSURE \
									 | INV_ONE_AXIS_LIGHT \
									 | INV_THREE_AXIS_RAW_COMPASS_WAKE \
									 | INV_THREE_AXIS_COMPASS_WAKE \
									 | INV_ONE_AXIS_PRESSURE_WAKE \
									 | INV_ONE_AXIS_LIGHT_WAKE)

// mask of virtual sensors that require gyro + accel + compass data
#define VIRTUAL_SENSOR_9AXES_MASK (         \
        (1LL << Orientation)                \
        | (1LL << RotationVector)           \
)
// mask of virtual sensors that require gyro + accel + compass data
#define VIRTUAL_SENSOR_9AXES_MASK_WAKE (    \
        (1LL << Orientation_Wake)           \
        | (1LL << RotationVector_Wake)      \
)
// mask of virtual sensors that require gyro + accel data (but no compass data)
#define VIRTUAL_SENSOR_GYRO_6AXES_MASK (    \
        (1LL << GameRotationVector)         \
        | (1LL << LinearAccel)              \
        | (1LL << Gravity)                  \
)
// mask of virtual sensors that require gyro + accel data (but no compass data)
#define VIRTUAL_SENSOR_GYRO_6AXES_MASK_WAKE (   \
        (1LL << GameRotationVector_Wake)    	\
        | (1LL << LinearAccel_Wake)         	\
        | (1LL << Gravity_Wake)             	\
)
// mask of virtual sensors that require mag + accel data (but no gyro data)
#define VIRTUAL_SENSOR_MAG_6AXES_MASK (         \
        (1LL << GeomagneticRotationVector)      \
)
#define VIRTUAL_SENSOR_MAG_6AXES_MASK_WAKE (    \
        (1LL << GeomagneticRotationVector_Wake) \
)
// mask of all virtual sensors
#define VIRTUAL_SENSOR_ALL_MASK (           \
        VIRTUAL_SENSOR_9AXES_MASK           \
        | VIRTUAL_SENSOR_GYRO_6AXES_MASK    \
        | VIRTUAL_SENSOR_MAG_6AXES_MASK     \
)
#define VIRTUAL_SENSOR_ALL_MASK_WAKE (          \
        VIRTUAL_SENSOR_9AXES_MASK_WAKE          \
        | VIRTUAL_SENSOR_GYRO_6AXES_MASK_WAKE   \
        | VIRTUAL_SENSOR_MAG_6AXES_MASK_WAKE    \
)

// bit mask of current DMP active features (mFeatureActiveMask)
#define INV_DMP_QUATERNION            0x00000001 //3 elements without real part, 32 bit each
#define INV_DMP_DISPL_ORIENTATION     0x00000002 //screen orientation
#define INV_DMP_SIGNIFICANT_MOTION    0x00000004 //significant motion
#define INV_DMP_PEDOMETER             0x00000008 //interrupt-based pedometer
#define INV_DMP_PEDOMETER_STEP        0x00000010 //timer-based pedometer
#define INV_DMP_PED_STANDALONE        0x00000020 //timestamps only
#define INV_DMP_6AXIS_QUATERNION      0x00000040 //3 elements without real part, 32 bit each
#define INV_DMP_PED_QUATERNION        0x00000080 //3 elements without real part, 16 bit each
#define INV_DMP_PED_INDICATOR         0x00000100 //tag along header with step indciator
#define INV_DMP_BATCH_MODE            0x00000200 //batch mode
#define INV_DMP_9AXIS_QUATERNION      0x00000400 //3 elements without real part, 32 bit each
#define INV_DMP_TILT                  0x00000800 //Tilt Event
#define INV_DMP_PICKUP                0x00001000//Pick up Gesture
//#define INV_DMP_EIS_GYROSCOPE         0x00002000//Eis event
#define INV_DMP_6AXIS_QUATERNION_WAKE 0x00004000 //3 elements without real part, 32 bit each
#define INV_DMP_9AXIS_QUATERNION_WAKE 0x00008000 //3 elements without real part, 32 bit each

// bit mask of whether DMP should be turned on
#define DMP_FEATURE_MASK (                           \
        (INV_DMP_QUATERNION)                         \
        | (INV_DMP_DISPL_ORIENTATION)                \
        | (INV_DMP_SIGNIFICANT_MOTION)               \
        | (INV_DMP_PICKUP)                           \
        | (INV_DMP_PEDOMETER)                        \
        | (INV_DMP_PEDOMETER_STEP)                   \
        | (INV_DMP_6AXIS_QUATERNION)                 \
        | (INV_DMP_PED_QUATERNION)                   \
        | (INV_DMP_BATCH_MODE)                       \
        | (INV_DMP_9AXIS_QUATERNION)                 \
)

// bit mask of DMP features as sensors
#define DMP_SENSOR_MASK (                            \
        (INV_DMP_DISPL_ORIENTATION)                  \
        | (INV_DMP_SIGNIFICANT_MOTION)               \
        | (INV_DMP_PICKUP)                           \
        | (INV_DMP_PEDOMETER)                        \
        | (INV_DMP_PEDOMETER_STEP)                   \
        | (INV_DMP_6AXIS_QUATERNION)                 \
        | (INV_DMP_9AXIS_QUATERNION)                 \
)

// data header format used by kernel driver.
#define DATA_FORMAT_WAKEUP           0x8000

#define DATA_FORMAT_ACCEL            1
#define DATA_FORMAT_RAW_GYRO         2
#define DATA_FORMAT_RAW_COMPASS      3
#define DATA_FORMAT_ALS              4
#define DATA_FORMAT_6_AXIS           5
#define DATA_FORMAT_9_AXIS           6
#define DATA_FORMAT_PED_QUAT         7
#define DATA_FORMAT_GEOMAG           8
#define DATA_FORMAT_PRESSURE         9
#define DATA_FORMAT_GYRO             10
#define DATA_FORMAT_COMPASS          11
#define DATA_FORMAT_STEP_COUNT       12
#define DATA_FORMAT_PED_STANDALONE   13
#define DATA_FORMAT_STEP             14
#define DATA_FORMAT_ACTIVITY         15
#define DATA_FORMAT_PICKUP           16
#define DATA_FORMAT_EMPTY_MARKER     17
#define DATA_FORMAT_MARKER           18
#define DATA_FORMAT_COMPASS_ACCURACY 19
#define DATA_FORMAT_ACCEL_ACCURACY   20
#define DATA_FORMAT_GYRO_ACCURACY    21
#if 0
#define DATA_FORMAT_EIS_GYROSCOPE    36
#define DATA_FORMAT_EIS_AUTHENTICATION    37
#endif

#define DATA_FORMAT_ACCEL_WAKE            (DATA_FORMAT_ACCEL | DATA_FORMAT_WAKEUP)
#define DATA_FORMAT_RAW_GYRO_WAKE         (DATA_FORMAT_RAW_GYRO | DATA_FORMAT_WAKEUP)
#define DATA_FORMAT_RAW_COMPASS_WAKE      (DATA_FORMAT_RAW_COMPASS | DATA_FORMAT_WAKEUP)
#define DATA_FORMAT_ALS_WAKE              (DATA_FORMAT_ALS | DATA_FORMAT_WAKEUP)
#define DATA_FORMAT_6_AXIS_WAKE           (DATA_FORMAT_6_AXIS | DATA_FORMAT_WAKEUP)
#define DATA_FORMAT_9_AXIS_WAKE           (DATA_FORMAT_9_AXIS | DATA_FORMAT_WAKEUP)
#define DATA_FORMAT_PED_QUAT_WAKE         (DATA_FORMAT_PED_QUAT | DATA_FORMAT_WAKEUP)
#define DATA_FORMAT_GEOMAG_WAKE           (DATA_FORMAT_GEOMAG | DATA_FORMAT_WAKEUP)
#define DATA_FORMAT_PRESSURE_WAKE         (DATA_FORMAT_PRESSURE | DATA_FORMAT_WAKEUP)
#define DATA_FORMAT_GYRO_WAKE             (DATA_FORMAT_GYRO | DATA_FORMAT_WAKEUP)
#define DATA_FORMAT_COMPASS_WAKE          (DATA_FORMAT_COMPASS | DATA_FORMAT_WAKEUP)
#define DATA_FORMAT_STEP_COUNT_WAKE       (DATA_FORMAT_STEP_COUNT | DATA_FORMAT_WAKEUP)
#define DATA_FORMAT_PED_STANDALONE_WAKE   (DATA_FORMAT_PED_STANDALONE | DATA_FORMAT_WAKEUP)

#define BYTES_PER_SENSOR                8
#define BYTES_PER_SENSOR_PACKET         24
#define QUAT_ONLY_LAST_PACKET_OFFSET    16
#define BYTES_QUAT_DATA                 24
#define MAX_READ_SIZE                   (BYTES_PER_SENSOR_PACKET + 8)
#define MAX_SUSPEND_BATCH_PACKET_SIZE   1024
#define MAX_PACKET_SIZE                 80 //8 * 4 + (2 * 24)
#define NS_PER_SECOND                   1000000000LL
#define NS_PER_SECOND_FLOAT             1000000000.f

#define ASENSOR_RATE_200HZ 5000
#define ASENSOR_RATE_100HZ 10000
#define ASENSOR_RATE_50HZ  20000
#define ASENSOR_RATE_30HZ  33333
#define ISENSOR_RATE_220HZ 4545
#define ISENSOR_RATE_110HZ 9090
#define ISENSOR_RATE_55HZ 18181
#define ISENSOR_RATE_31HZ 32258

/* Uncomment to enable Low Power Quaternion */
#define ENABLE_LP_QUAT_FEAT

/* Enable Pressure sensor support */
//#define ENABLE_PRESSURE

/* Enable Pressure sensor support */
//#define ENABLE_COMPASS_ANALYSIS

const char* dataPath = "/persist";

class MPLSensor: public SensorBase
{
    typedef int (MPLSensor::*hfunc_t)(sensors_event_t*);

public:

    MPLSensor(CompassSensor *);
    virtual ~MPLSensor();

    virtual int setDelay(int32_t handle, int64_t ns);
    virtual int enable(int32_t handle, int enabled);
    virtual int batch(int handle, int flags, int64_t period_ns, int64_t timeout);
    virtual int flush(int handle);

#ifdef SENSORS_DEVICE_API_VERSION_1_4
    virtual int inject_sensor_data(const sensors_event_t* data);
#endif

    int selectAndSetQuaternion(int batchMode, int mEnabled, long long featureMask);
    int checkBatchEnabled();
    int setBatch(int en);
    int writeBatchTimeout(int en);
    int32_t getEnableMask() { return mEnabled; }
    void getHandle(int32_t handle, int &what, android::String8 &sname);

    virtual int readEvents(sensors_event_t *data, int count);
    virtual int getFd() const;
    virtual int getAccelFd() const;
    virtual int getCompassFd() const;
    virtual int getPollTime();
    virtual bool hasPendingEvents() const;
    int isDataInjectionSupported();
    void setDataInjectionMode(int mode);
    int populateSensorList(struct sensor_t *list, int len);

    void buildCompassEvent();
    void buildMpuEvent();
    int checkValidHeader(unsigned short data_format);

    int turnOffAccelFifo();
    int turnOffGyroFifo();

    int getDmpSignificantMotionFd();
    int readDmpSignificantMotionEvents(sensors_event_t* data, int count);
    int enableDmpSignificantMotion(int);
    int significantMotionHandler(sensors_event_t* data);
    bool checkSmdSupport(){return (mDmpSignificantMotionEnabled);};

    int enableDmpPedometer(int, int);
    int readDmpPedometerEvents(sensors_event_t* data, int count, int32_t id, int outputType);
    int getDmpPedometerFd();
    bool checkPedometerSupport() {return (mDmpPedometerEnabled || mDmpStepCountEnabled);};

    int getDmpTiltFd();
    int readDmpTiltEvents(sensors_event_t* data, int count);
    int enableDmpTilt(int);

    int getDmpPickupFd();
    int readDmpPickupEvents(sensors_event_t* data, int count);
    int enableDmpPickup(int);
#if 0
    int enableDmpEis(int);
    int enableDmpEisAuthentication(int);

    int enableOisSensor(int);
#endif
protected:
    CompassSensor *mCompassSensor;
	#ifdef IIO_LIGHT_SENSOR
    PressureSensor *mPressureSensor;
    LightSensor *mLightSensor;
	#endif
    int gyroHandler(sensors_event_t *data);
    int rawGyroHandler(sensors_event_t *data);
    int accelHandler(sensors_event_t *data);
    int compassHandler(sensors_event_t *data);
    int rawCompassHandler(sensors_event_t *data);
    int rvHandler(sensors_event_t *data);
    int grvHandler(sensors_event_t *data);
    int laHandler(sensors_event_t *data);
    int gravHandler(sensors_event_t *data);
    int orienHandler(sensors_event_t *data);
    int smHandler(sensors_event_t *data);
    int pHandler(sensors_event_t *data);
    int gmHandler(sensors_event_t *data);
    int psHandler(sensors_event_t *data);
    int sdHandler(sensors_event_t *data);
    int scHandler(sensors_event_t *data);
    int lightHandler(sensors_event_t *data);
    int proxHandler(sensors_event_t *data);
    int tiltHandler(sensors_event_t *data);
    int pickupHandler(sensors_event_t *data);
#if 0
    int eisHandler(sensors_event_t *data);
    int eisAuthenticationHandler(sensors_event_t *data);
#endif
    int metaHandler(sensors_event_t *data, int flags);

    int gyrowHandler(sensors_event_t *data);
    int rawGyrowHandler(sensors_event_t *data);
    int accelwHandler(sensors_event_t *data);
    int compasswHandler(sensors_event_t *data);
    int rawCompasswHandler(sensors_event_t *data);
    int rvwHandler(sensors_event_t *data);
    int grvwHandler(sensors_event_t *data);
    int lawHandler(sensors_event_t *data);
    int gravwHandler(sensors_event_t *data);
    int orienwHandler(sensors_event_t *data);
    int pwHandler(sensors_event_t *data);
    int gmwHandler(sensors_event_t *data);
    int pswHandler(sensors_event_t *data);
    int sdwHandler(sensors_event_t *data);
    int scwHandler(sensors_event_t *data);
    int lightwHandler(sensors_event_t *data);
    int proxwHandler(sensors_event_t *data);

    int gyroRawHandler(sensors_event_t *data);
    int accelRawHandler(sensors_event_t *data);
    int compassRawHandler(sensors_event_t *data);

    void calcOrientationSensor(float *Rx, float *Val);
    virtual int update_delay();

    void inv_set_device_properties();
    int inv_constructor_init();
    int inv_constructor_default_enable();
    void inv_set_engine_rate(uint32_t rate, uint64_t mask);
    void setRawGyroRate(uint64_t delay);
    void setRawGyroRateWake(uint64_t delay);
    void setGyroRate(uint64_t delay);
    void setGyroRateWake(uint64_t delay);
    void setAccelRate(uint64_t delay);
    void setAccelRateWake(uint64_t delay);
    void set6AxesRate(uint64_t delay);
    void set6AxesRateWake(uint64_t delay);
    void setRawMagRate(uint64_t delay);
    void setRawMagRateWake(uint64_t delay);
    void setMagRate(uint64_t delay);
    void setMagRateWake(uint64_t delay);
    void set9AxesRate(uint64_t delay);
    void set9AxesRateWake(uint64_t delay);
    void set6AxesMagRate(uint64_t delay);
    void set6AxesMagRateWake(uint64_t delay);
    void setPressureRate(uint64_t delay);
    void setPressureRateWake(uint64_t delay);
    void setLightRate(uint64_t delay);
    void setLightRateWake(uint64_t delay);
    int enablePedStandalone(int en);
    int enablePedStandaloneData(int en);
    int enablePedQuaternion(int);
    int enablePedQuaternionData(int);
    int setPedQuaternionRate(int64_t wanted);
    int enableCompass6AxisQuaternion(int en);
    int enable6AxisQuaternion(int);
    int enable6AxisQuaternionData(int);
    int set6AxisQuaternionRate(int64_t wanted);
    int set6AxisQuaternionWakeRate(int64_t wanted);
    int enable9AxisQuaternion(int);
    int enableLPQuaternion(int);
    int enableQuaternionData(int);
    int setQuaternionRate(int64_t wanted);
    int enable6AxisQuaternionWake(int en);
    int enable6AxisQuaternionDataWake(int en);
    int enable9AxisQuaternionWake(int en);
    int enableCompass6AxisQuaternionWake(int en);
    int enableLPQuaternionWake(int en);
    int enableQuaternionDataWake(int en);
    int enableAccelPedometer(int);
    int enableAccelPedData(int);
    int onDmp(int);
    int enableGyroEngine(int en);
    int enableRawGyro(int en);
    int enableRawGyroWake(int en);
    int enableGyro(int en);
    int enableGyroWake(int en);
    int enableAccel(int en);
    int enableAccelWake(int en);
    int enableRawCompass(int en);
    int enableRawCompassWake(int en);
    int enableCompass(int en);
    int enableCompassWake(int en);
	#ifdef IIO_LIGHT_SENSOR
    int enablePressure(int en);
    int enablePressureWake(int en);
    int enableLight(int en);
    int enableLightWake(int en);
    int enableProximity(int en);
    int enableProximityWake(int en);
	#endif
    int enableBatch(int64_t timeout);
    int enableDmpCalibration(int en);
    void computeLocalSensorMask(uint64_t enabled_sensors);
    uint64_t checkChangedSensors(int what, uint64_t lastEnabled, uint64_t enableFlag);
    int computeBatchSensorMask(uint64_t enableSensor, uint64_t checkNewBatchSensor);
    int computeBatchDataOutput();
    int enableSensors(uint64_t sensors, int en, uint64_t changed);
    uint64_t invCheckOutput(uint32_t sensor);
    int inv_read_temperature(long long *data);
    int inv_read_dmp_state(int fd);
    int inv_read_sensor_bias(int fd, int *data);
    void inv_get_sensors_orientation(void);
    int inv_init_sysfs_attributes(void);
    void setCompassDelay(int64_t ns);
    void enable_iio_sysfs(void);
    int setDmpFeature(int en);
    int computeAndSetDmpState(void);
    int enablePedometer(int);
    int enablePedIndicator(int en);
    int checkPedStandaloneBatched(void);
    int checkPedStandaloneEnabled(void);
    int checkPedQuatEnabled();
    int check6AxisQuatEnabled();
    int checkLPQRateSupported();
    int checkLPQuaternion();
    int check9AxisQuaternion();
    int checkAccelPed();
    int check9AxisQuaternionWake(void);
    int checkLPQuaternionWake(void);
    void setInitial6QuatValue();
    int writeSignificantMotionParams(uint32_t delayThreshold1, uint32_t delayThreshold2,
                                     uint32_t motionThreshold);
    void setAccuracy(int sensor, int accuracy);
    void inv_write_sysfs(uint32_t delay, uint64_t mask, char *sysfs_rate);
    void inv_read_sysfs_engine(uint64_t mask, char *sysfs_rate);

    uint64_t mMasterSensorMask;
    uint64_t mLocalSensorMask;
    int mPollTime;
    bool mHaveGoodMpuCal;   // flag indicating that the cal file can be written
    int mGyroAccuracy;      // value indicating the quality of the gyro calibr.
    int mAccelAccuracy;     // value indicating the quality of the accel calibr.
    int mCompassAccuracy;   // value indicating the quality of the compass calibr.
    int mHeadingAccuracy;   // value indicating the 9 axis heading accuracy interval
    struct pollfd mPollFds[5];
    pthread_mutex_t mMplMutex;
    pthread_mutex_t mHALMutex;

    char mIIOBuffer[(16 + 8 * 3 + 8) * IIO_BUFFER_LENGTH];

    int iio_fd;
    int accel_fd;
    int mpufifo_fd;
    int gyro_temperature_fd;
    int accel_x_offset_fd;
    int accel_y_offset_fd;
    int accel_z_offset_fd;

    int accel_x_dmp_bias_fd;
    int accel_y_dmp_bias_fd;
    int accel_z_dmp_bias_fd;

    int gyro_x_offset_fd;
    int gyro_y_offset_fd;
    int gyro_z_offset_fd;

    int gyro_x_dmp_bias_fd;
    int gyro_y_dmp_bias_fd;
    int gyro_z_dmp_bias_fd;

    int compass_x_dmp_bias_fd;
    int compass_y_dmp_bias_fd;
    int compass_z_dmp_bias_fd;

    int dmp_orient_fd;
    int mDmpOrientationEnabled;

    int dmp_sign_motion_fd;
    int mDmpSignificantMotionEnabled;

    int dmp_pedometer_fd;
    int mDmpPedometerEnabled;
    int mDmpStepCountEnabled;

    int dmp_pickup_fd;
    int mDmpPickupEnabled;

    int dmp_tilt_fd;
    int mDmpTiltEnabled;

    uint64_t mEnabled;
    uint64_t mEnabledCached;
    uint64_t mBatchEnabled;
    android::Vector<int> mFlushSensorEnabledVector;
    uint32_t mOldBatchEnabledMask;
    int64_t mBatchTimeoutInMs;
    sensors_event_t mPendingEvents[TotalNumSensors];
    sensors_event_t mSmEvents;
    sensors_event_t mSdEvents;
    sensors_event_t mTiltEvents;
    sensors_event_t mPickupEvents;
    sensors_event_t mSdwEvents;
    sensors_event_t mScwEvents;
    int64_t mDelays[TotalNumSensors];
    int64_t mBatchDelays[TotalNumSensors];
    int64_t mBatchTimeouts[TotalNumSensors];
    int32_t mSensorUpdate[TotalNumSensors];
    hfunc_t mHandlers[TotalNumSensors];
    int64_t mEnabledTime[TotalNumSensors];
    int mCachedGyroData[3];
    int mCachedCalGyroData[3];
    int mCachedAccelData[3];
    int mCachedCompassData[3];
    int mCachedQuaternionData[3];
    int mCached6AxisQuaternionData[3];
    int mCached9AxisQuaternionData[3];
    int mCachedGeomagData[3];
    int mCachedPedQuaternionData[3];
    int mCachedPressureData;
    int mCachedPressureWakeData;
    int mCachedAlsData[3];
    int mCachedAlsWakeData[3];
#if 0
    int mCachedEisData[4];
    int mCachedEisAuthenticationData[4];
#endif
    android::KeyedVector<int, int> mIrqFds;
    int mCompassSoftIron[9];
    int mCompassSens[3];
    uint32_t mNumSensors;
    uint32_t mNumSysfs;

    InputEventCircularReader mAccelInputReader;
    InputEventCircularReader mGyroInputReader;

    bool mFirstRead;
    short mTempScale;
    short mTempOffset;
    int64_t mTempCurrentTime;
    int mAccelScale;
    int mAccelSelfTestScale;
    int mGyroScale;
    int mGyroDmpScaleFactor;
    int mGyroSelfTestScale;
    int mCompassScale;
    float mCompassBias[3];
    bool mFactoryGyroBiasAvailable;
    int mFactoryGyroBias[3];
    int mFactoryGyroBiasLp[3];
    int mGyroBiasUiMode[3];
    bool mGyroBiasAvailable;
    bool mGyroBiasApplied;
    int mDmpGyroBias[3];
    float mGyroBias[3];    //in body frame
    int mGyroChipBias[3]; //in chip frame
    bool mFactoryAccelBiasAvailable;
    int mFactoryAccelBias[3];
    int mFactoryAccelBiasLp[3];
    int mAccelBiasUiMode[3];
    bool mAccelBiasAvailable;
    bool mAccelBiasApplied;
    int mAccelDmpBias[3];
    int mAccelBias[3];    //in chip frame
    int mDmpCompassBias[3];
    bool mCompassBiasAvailable;
    bool mCompassBiasApplied;

    uint32_t mPendingMask;
    unsigned int mSensorMask;
    unsigned int mSensorMaskCached;

    char chip_ID[MAX_CHIP_ID_LEN];
    char mSysfsPath[MAX_SYSFS_NAME_LEN];

    signed char mGyroOrientationMatrix[9];
    signed char mAccelOrientationMatrix[9];
    signed char mCompassOrientationMatrix[9];

    unsigned short mGyroOrientationScalar;
    unsigned short mAccelOrientationScalar;
    unsigned short mCompassOrientationScalar;

    int64_t mSensorTimestamp;
    int64_t mCompassTimestamp;
    int64_t mPressureTimestamp;
    int64_t mPressureWakeTimestamp;
#if 0
    int64_t mEisTimestamp;
    int64_t mEisAuthenticationTimestamp;
#endif
    struct sensor_mask {
        uint64_t sensorMask;
	android::String8 sname;
	uint64_t engineMask;
	uint32_t *engineRateAddr;
    };
    struct sysfs_mask {
        uint64_t sensorMask;
	int (MPLSensor::*enable)(int);
	int (MPLSensor::*second_enable)(int);
	void (MPLSensor::*setRate)(uint64_t);
	void (MPLSensor::*second_setRate)(uint64_t);
	uint32_t engineRate;
	int64_t nominalRate;
	uint64_t minimumNonBatchRate;
        bool en;
    };

    struct sensor_mask *mCurrentSensorMask;
    struct sysfs_mask *mSysfsMask;

    struct sysfs_attrbs {
       char *chip_enable;
       char *master_enable;
       char *dmp_firmware;
       char *firmware_loaded;
       char *dmp_on;
       char *dmp_int_on;
       char *dmp_event_int_on;
       char *tap_on;
       char *self_test;
       char *dmp_init;
       char *temperature;

       char *gyro_enable;
       char *gyro_fsr;
       char *gyro_sf;
       char *gyro_orient;
       char *gyro_fifo_enable;
       char *gyro_rate;
       char *gyro_raw_data;

       char *compass_raw_data;

       char *gyro_wake_fifo_enable;
       char *gyro_wake_rate;

       char *calib_gyro_enable;
       char *calib_gyro_rate;
       char *calib_gyro_wake_enable;
       char *calib_gyro_wake_rate;

       char *accel_enable;
       char *accel_fsr;
       char *accel_bias;
       char *accel_orient;
       char *accel_fifo_enable;
       char *accel_rate;
       char *accel_raw_data;

       char *accel_wake_fifo_enable;
       char *accel_wake_rate;

       char *three_axis_q_on; //formerly quaternion_on
       char *three_axis_q_rate;

       char *six_axis_q_on;
       char *six_axis_q_rate;
       char *six_axis_q_wake_on;
       char *six_axis_q_wake_rate;
       char *six_axis_q_value;

       char *nine_axis_q_on;
       char *nine_axis_q_rate;
       char *nine_axis_q_wake_on;
       char *nine_axis_q_wake_rate;

       char *ped_q_on;
       char *ped_q_rate;
       char *ped_q_wake_on;
       char *ped_q_wake_rate;

       char *in_geomag_enable;
       char *in_geomag_rate;
       char *in_geomag_wake_enable;
       char *in_geomag_wake_rate;

       char *step_detector_on;
       char *step_indicator_on;

       char *in_timestamp_en;
       char *in_timestamp_index;
       char *in_timestamp_type;

       char *buffer_length;

       char *display_orientation_on;
       char *event_display_orientation;

       char *in_accel_x_offset;
       char *in_accel_y_offset;
       char *in_accel_z_offset;
       char *in_accel_self_test_scale;

       char *in_accel_x_dmp_bias;
       char *in_accel_y_dmp_bias;
       char *in_accel_z_dmp_bias;

       char *in_gyro_x_offset;
       char *in_gyro_y_offset;
       char *in_gyro_z_offset;
       char *in_gyro_self_test_scale;

       char *in_gyro_x_dmp_bias;
       char *in_gyro_y_dmp_bias;
       char *in_gyro_z_dmp_bias;

       char *in_compass_x_dmp_bias;
       char *in_compass_y_dmp_bias;
       char *in_compass_z_dmp_bias;

       char *event_smd;
       char *smd_enable;
       char *smd_delay_threshold;
       char *smd_delay_threshold2;
       char *smd_threshold;
       char *batchmode_timeout;
       char *batchmode_wake_fifo_full_on;
       char *flush_batch;

       char *tilt_on;
       char *event_tilt;

       char *pickup_on;
       char *event_pickup;
#if 0
       char *eis_on;
       char *eis_data_on;
       char *eis_rate;
#endif
       char *activity_on;
       char * event_activity;

       char *pedometer_on;
       char *pedometer_wake_on;
       char *pedometer_counter_on;
       char *pedometer_counter_wake_on;
       char *pedometer_int_on;
       char *event_pedometer;
       char *pedometer_steps;
       char *pedometer_step_thresh;
       char *pedometer_counter;

       char *motion_lpa_on;

       char *gyro_cal_enable;
       char *accel_cal_enable;
       char *compass_cal_enable;

       char *accel_accuracy_enable;
       char *anglvel_accuracy_enable;
       char *magn_accuracy_enable;

       char *debug_determine_engine_on;
       char *d_compass_enable;
       char *d_misc_gyro_recalibration;
       char *d_misc_accel_recalibration;
       char *d_misc_compass_recalibration;

       char *d_misc_accel_cov;
       char *d_misc_compass_cov;
       char *d_misc_current_compass_cov;
       char *d_misc_ref_mag_3d;
#if 0
       char *ois_enable;
#endif
#ifdef SENSORS_DEVICE_API_VERSION_1_4
       char *info_poke_mode;
       char *misc_bin_poke_accel;
       char *misc_bin_poke_gyro;
       char *misc_bin_poke_mag;
#endif
    } mpu;

    char *sysfs_names_ptr;
    int mMplFeatureActiveMask;
    uint64_t mFeatureActiveMask;
    bool mDmpOn;
    int mPedUpdate;
    int mPedWakeUpdate;
    int mPressureUpdate;
    int mPressureWakeUpdate;
#if 0
    int mEisUpdate;
    int mEisAuthenticationUpdate;
#endif
    int64_t mQuatSensorTimestamp;
    int64_t mQuatSensorLastTimestamp;
    int64_t m6QuatSensorTimestamp;
    int64_t m6QuatSensorLastTimestamp;
    int64_t mGeoQuatSensorTimestamp;
    int64_t mGeoQuatSensorLastTimestamp;
    int64_t mStepSensorTimestamp;
    int64_t mStepSensorWakeTimestamp;
    int64_t mAlsSensorTimestamp;
    int64_t mAlsSensorWakeTimestamp;
    int64_t mLastStepCount;
    int64_t mStepCount;
    int64_t mLastStepCountWake;
    int64_t mStepCountWake;
    bool mLightInitEvent;
    bool mLightWakeInitEvent;
    bool mProxiInitEvent;
    bool mProxiWakeInitEvent;
    bool mInitial6QuatValueAvailable;
    int mInitial6QuatValue[4];
    uint32_t mSkipReadEvents;
    bool mPressureSensorPresent;
    bool mLightSensorPresent;
    bool mCustomSensorPresent;
    bool mEmptyDataMarkerDetected;

    int mCalibrationMode;
#if 0
    int mOisEnabled;
#endif

private:
    /* added for dynamic get sensor list */
    void fillSensorMaskArray();
    void fillAccel(const char* accel, struct sensor_t *list);
    void fillGyro(const char* gyro, struct sensor_t *list);
    void fillRV(struct sensor_t *list);
    void fillGMRV(struct sensor_t *list);
    void fillGRV(struct sensor_t *list);
    void fillOrientation(struct sensor_t *list);
    void fillGravity(struct sensor_t *list);
    void fillLinearAccel(struct sensor_t *list);
    void fillSignificantMotion(struct sensor_t *list);
    void fillTilt(struct sensor_t *list);
    void fillPickup(struct sensor_t *list);
    void storeCalibration();
    void loadDMP(char *chipID);
    bool isMpuNonDmp();
    bool isPressurePresent();
    int isLowPowerQuatEnabled();
    int isDmpDisplayOrientationOn();
    void resetCalStatus(int recal);
    void getCompassBias();
    void getFactoryGyroBias();
    void setFactoryGyroBias();
    void getGyroBias();
    void setGyroZeroBias();
    void setDmpGyroBias();
    void getDmpGyroBias();
    void getFactoryAccelBias();
    void setFactoryAccelBias();
    void getAccelBias();
    void getDmpAccelBias();
    void setDmpAccelBias();
    void setAccelBias();
    void setDmpCompassBias();
    void getDmpCompassBias();
    void setCompassBias();
    int isCompassDisabled();
    int setBatchDataRates();
    int resetDataRates();
    void initBias();
    void resetMplStates();
    void resetGyroDecimatorStates();
    void resetAccelDecimatorStates();
    void resetMagDecimatorStates();
    int lookupSensorRate(int inputRateUs);
    int setAccelCov();
    int setCompassCov();
    void sys_dump(bool fileMode);
    void printTimeProfile(int64_t &previousTime, int64_t currentTime, int64_t sensorTs, int64_t lastSensorTs);
};

extern "C" {
    void setCallbackObject(MPLSensor*);
    MPLSensor *getCallbackObject();
}

#endif  // ANDROID_MPL_SENSOR_H
