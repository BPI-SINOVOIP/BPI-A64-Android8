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

#define LOG_NDEBUG 0

//see also the EXTRA_VERBOSE define in the MPLSensor.h header file

#include <fcntl.h>
#include <errno.h>
#include <math.h>
#include <float.h>
#include <poll.h>
#include <unistd.h>
#include <dirent.h>
#include <stdlib.h>
#include <sys/select.h>
#include <sys/syscall.h>
#include <dlfcn.h>
#include <pthread.h>
#include <cutils/log.h>
#include <utils/KeyedVector.h>
#include <utils/Vector.h>
#include <utils/String8.h>
#include <string.h>
#include <linux/input.h>
#include <utils/Atomic.h>
#include <utils/SystemClock.h>

#include "MPLSensor.h"
#include "MPLAdapter.h"
#ifdef IIO_LIGHT_SENSOR
#include "PressureSensor.IIO.secondary.h"
#include "LightSensor.IIO.secondary.h"
#endif

#include "MPLSupport.h"
#include "sensor_params.h"

#include "invensense.h"
#include "ml_stored_data.h"
#include "ml_load_dmp.h"
#include "ml_sysfs_helper.h"
#include "ml_sensor_parsing.h"
#include "ml_android_to_imu.h"

// #define TESTING
// #define DEBUG_TIME_PROFILE
// #define DEBUG_BATCHING 1
#define DEBUG_OUTPUT_CONTROL 0
#define MAX_SYSFS_ATTRB (sizeof(struct sysfs_attrbs) / sizeof(char*))

#if defined DEBUG_DRIVER
#pragma message("HAL:build Invensense sensor debug driver mode")
#endif

/*******************************************************************************
 * MPLSensor class implementation
 ******************************************************************************/

static struct timespec mt_pre;
static int64_t prevtime;
static int64_t currentime;
static int64_t htime;
struct sensor_t *currentSensorList;
uint64_t sysfsId[] = {
   INV_THREE_AXIS_RAW_GYRO,
   INV_THREE_AXIS_GYRO,
   INV_THREE_AXIS_ACCEL,
   INV_THREE_AXIS_RAW_COMPASS,
   INV_THREE_AXIS_COMPASS,
   INV_ONE_AXIS_PRESSURE,
   INV_ONE_AXIS_LIGHT,
   INV_THREE_AXIS_RAW_GYRO_WAKE,
   INV_THREE_AXIS_GYRO_WAKE,
   INV_THREE_AXIS_ACCEL_WAKE,
   INV_THREE_AXIS_RAW_COMPASS_WAKE,
   INV_THREE_AXIS_COMPASS_WAKE,
   INV_ONE_AXIS_PRESSURE_WAKE,
   INV_ONE_AXIS_LIGHT_WAKE,
   VIRTUAL_SENSOR_9AXES_MASK,
   VIRTUAL_SENSOR_9AXES_MASK_WAKE,
   VIRTUAL_SENSOR_GYRO_6AXES_MASK,
   VIRTUAL_SENSOR_GYRO_6AXES_MASK_WAKE,
   VIRTUAL_SENSOR_MAG_6AXES_MASK,
   VIRTUAL_SENSOR_MAG_6AXES_MASK_WAKE,
};
int sensorId[] = {
    Gyro,
    RawGyro,
    Accelerometer,
    MagneticField,
    RawMagneticField,
    Orientation,
    RotationVector,
    GameRotationVector,
    LinearAccel,
    Gravity,
    SignificantMotion,
    StepDetector,
    StepCounter,
    GeomagneticRotationVector,
    Tilt,
    Pickup,
#if 0
    EISGyroscope,
    EISAuthentication,
#endif
    Gyro_Wake,
    RawGyro_Wake,
    Accelerometer_Wake,
    MagneticField_Wake,
    RawMagneticField_Wake,
    Orientation_Wake,
    RotationVector_Wake,
    GameRotationVector_Wake,
    LinearAccel_Wake,
    Gravity_Wake,
    StepDetector_Wake,
    StepCounter_Wake,
    GeomagneticRotationVector_Wake,
    Pressure,
    Pressure_Wake,
    Light,
    Light_Wake,
    Proximity,
    Proximity_Wake,
    AccelerometerRaw,
    GyroRaw,
    MagneticFieldRaw,
#if 0
    OisSensor,
#endif
    ScreenOrientation,
    TotalNumSensors,
};


// following extended initializer list would only be available with -std=c++11
//  or -std=gnu+11
MPLSensor::MPLSensor(CompassSensor *compass)
    : SensorBase(NULL, NULL),
    mMasterSensorMask(INV_ALL_SENSORS_WAKE),
    mLocalSensorMask(0),
    mPollTime(-1),
    mHaveGoodMpuCal(0),
    mGyroAccuracy(0),
    mAccelAccuracy(0),
    mCompassAccuracy(0),
    dmp_orient_fd(-1),
    mDmpOrientationEnabled(0),
    dmp_sign_motion_fd(-1),
    mDmpSignificantMotionEnabled(0),
    dmp_pedometer_fd(-1),
    mDmpPedometerEnabled(0),
    mDmpStepCountEnabled(0),
    mEnabled(0),
    mEnabledCached(0),
    mBatchEnabled(0),
    mOldBatchEnabledMask(0),
    mAccelInputReader(4),
    mGyroInputReader(32),
    mTempScale(0),
    mTempOffset(0),
    mTempCurrentTime(0),
    mAccelScale(2),
    mAccelSelfTestScale(2),
    mGyroScale(2000),
    mGyroDmpScaleFactor(41031322),
    mGyroSelfTestScale(2000),
    mCompassScale(0),
    mFactoryGyroBiasAvailable(false),
    mGyroBiasAvailable(false),
    mGyroBiasApplied(false),
    mFactoryAccelBiasAvailable(false),
    mAccelBiasAvailable(false),
    mAccelBiasApplied(false),
    mCompassBiasAvailable(false),
    mCompassBiasApplied(false),
    mPendingMask(0),
    mSensorMask(0),
    mSensorMaskCached(0),
    mMplFeatureActiveMask(0),
    mFeatureActiveMask(0),
    mDmpOn(0),
    mPedUpdate(0),
    mPedWakeUpdate(0),
    mPressureUpdate(0),
#if 0
    mEisUpdate(0),
    mEisAuthenticationUpdate(0),
#endif
    mQuatSensorTimestamp(0),
    mQuatSensorLastTimestamp(0),
    m6QuatSensorTimestamp(0),
    m6QuatSensorLastTimestamp(0),
    mGeoQuatSensorTimestamp(0),
    mGeoQuatSensorLastTimestamp(0),
    mStepSensorTimestamp(0),
    mStepSensorWakeTimestamp(0),
    mAlsSensorTimestamp(0),
    mAlsSensorWakeTimestamp(0),
    mLastStepCount(-1),
    mStepCount(0),
    mLastStepCountWake(-1),
    mStepCountWake(0),
    mInitial6QuatValueAvailable(0),
    mSkipReadEvents(0),
    mPressureSensorPresent(0),
    mLightSensorPresent(0),
    mCustomSensorPresent(0),
    mEmptyDataMarkerDetected(0)
#if 0
    ,mOisEnabled(0)
#endif
{
        VFUNC_LOG;

        inv_error_t rv;
        int i, fd;
        char *port = NULL;
        char *ver_str;
        int res;
        FILE *fptr;

        mCompassSensor = compass;

        LOGV_IF(EXTRA_VERBOSE,
                "HAL:MPLSensor constructor : NumSensors = %d", TotalNumSensors);

        pthread_mutex_init(&mMplMutex, NULL);
        pthread_mutex_init(&mHALMutex, NULL);
        memset(mGyroOrientationMatrix, 0, sizeof(mGyroOrientationMatrix));
        memset(mAccelOrientationMatrix, 0, sizeof(mAccelOrientationMatrix));
        memset(mCompassOrientationMatrix, 0, sizeof(mCompassOrientationMatrix));
        memset(mInitial6QuatValue, 0, sizeof(mInitial6QuatValue));
        mFlushSensorEnabledVector.setCapacity(TotalNumSensors);
	memset(mEnabledTime, 0, sizeof(mEnabledTime));

        /* setup sysfs paths */
        inv_init_sysfs_attributes();

        /* get chip name */
        if (inv_get_chip_name(chip_ID) != INV_SUCCESS) {
            LOGE("HAL:ERR- Failed to get chip ID\n");
        } else {
            LOGV_IF(PROCESS_VERBOSE, "HAL:Chip ID= %s\n", chip_ID);
        }
#ifdef IIO_LIGHT_SENSOR
        /* check pressure sensor */
        mPressureSensor = new PressureSensor((const char*)mSysfsPath);
        if (mPressureSensor) {
	    if (mPressureSensor->isPressureSensorPresent()) {
                mPressureSensorPresent = 1;
	    }
	}

        /* check light/prox sensor */
        mLightSensor = new LightSensor((const char*)mSysfsPath);
        if (mLightSensor) {
            if (mLightSensor->isLightSensorPresent())
                mLightSensorPresent = 1;
        }
#endif
	/* check custom sensor (Raw accel, Raw gyro, Raw compass) */
	if (CUSTOM_SENSOR)
	    mCustomSensorPresent = 1;

	/* print software version string */
	LOGI("InvenSense Libsensors Version MA%d.%d.%d%s\n",
	INV_LIBSENSORS_VERSION_MAJOR, INV_LIBSENSORS_VERSION_MINOR,
	INV_LIBSENSORS_VERSION_PATCH, INV_LIBSENSORS_VERSION_SUFFIX);

        LOGI("CHECK here11111111111\n");

        enable_iio_sysfs();

        /* Load DMP image */
        loadDMP(chip_ID);

	 mCalibrationMode = adapter_get_calibration_mode(chip_ID);
	 set_adapter(mCalibrationMode);
        /* Get Soft Iron Matrix with sensitivity and scale */
#if 0
        int tempOrientationMatrix[9];
        if (inv_get_soft_iron_matrix(tempOrientationMatrix, mCompassSoftIron) < 0) {
            LOGV_IF(EXTRA_VERBOSE, "HAL:error getting soft iron matrix");
        } else {
            LOGV_IF(EXTRA_VERBOSE, "HAL:compass soft iron matrix: %d %d %d", mCompassSoftIron[0],
                    mCompassSoftIron[4], mCompassSoftIron[8]);
        }
#else
        //get the compass sensitity adjustment

        if (inv_get_compass_sens(mCompassSens) < 0) {
            LOGV_IF(EXTRA_VERBOSE, "HAL:error getting compass sensitivity !");
        } else {
            LOGV_IF(EXTRA_VERBOSE, "HAL:compass sensitivity :  %d %d %d",
                    mCompassSens[0], mCompassSens[0], mCompassSens[0]);
        }

	float inSoftIronMatrix[9]= {1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0};
	//(chip-frame based) soft iron matrix of compass from customer, it normally needs to be transposed before beeing used here

        float SoftIronMatrix0[9] = {1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0}; //default soft iron matrix of compass
        for (int ii=0; ii < 9; ii++)
            mCompassSoftIron[ii] = (int)(SoftIronMatrix0[ii] * ROT_MATRIX_SCALE_LONG); //init

        for (int ii=0; ii < 9; ii++) {
            if (abs(inSoftIronMatrix[ii]) > 1.0){//check
                LOGE("input soft iron matrix value is invalid !");
                break;
            }
            else
                mCompassSoftIron[ii] = (int)(inSoftIronMatrix[ii] * ROT_MATRIX_SCALE_LONG);
        }
#endif
        /* open temperature fd for temp comp */
        LOGV_IF(EXTRA_VERBOSE, "HAL:gyro temperature path: %s", mpu.temperature);
        gyro_temperature_fd = open(mpu.temperature, O_RDONLY);
        if (gyro_temperature_fd == -1) {
            LOGE("HAL:could not open temperature node");
        } else {
            LOGV_IF(EXTRA_VERBOSE,
                    "HAL:temperature_fd opened: %s", mpu.temperature);
        }

        /* read gyro FSR to calculate accel scale later */
        char gyroBuf[10];
        int count = 0;
        LOGV_IF(SYSFS_VERBOSE,
                "HAL:sysfs:cat %s (%lld)", mpu.gyro_fsr, getTimestamp());

        fd = open(mpu.gyro_fsr, O_RDONLY);
        if(fd < 0) {
            LOGE("HAL:Error opening gyro FSR");
        } else {
            memset(gyroBuf, 0, sizeof(gyroBuf));
            count = read_attribute_sensor(fd, gyroBuf, sizeof(gyroBuf));
            if(count < 1) {
                LOGE("HAL:Error reading gyro FSR");
            } else {
                count = sscanf(gyroBuf, "%d", &mGyroScale);
                if(count)
                    LOGV_IF(EXTRA_VERBOSE, "HAL:Gyro FSR used %d", mGyroScale);
            }
            close(fd);
        }

        /* read gyro FSR to calculate accel scale later */
        char gyroSfBuf[10];
        LOGV_IF(SYSFS_VERBOSE,
                "HAL:sysfs:cat %s (%lld)", mpu.gyro_sf, getTimestamp());

        fd = open(mpu.gyro_sf, O_RDONLY);
        if(fd < 0) {
            LOGE("HAL:Error opening gyro FSR");
        } else {
            memset(gyroSfBuf, 0, sizeof(gyroSfBuf));
            count = read_attribute_sensor(fd, gyroSfBuf, sizeof(gyroSfBuf));
            if(count < 1) {
                LOGE("HAL:Error reading gyro Dmp scale factor");
            } else {
                count = sscanf(gyroSfBuf, "%d", &mGyroDmpScaleFactor);
                if(count)
                    LOGV_IF(EXTRA_VERBOSE, "HAL:Gyro Dmp scale factor used %d", mGyroDmpScaleFactor);
            }
            adapter_gyro_set_scale_factor_base(adapter_get_internal_sampling_rate(chip_ID));
            close(fd);
        }

        /* read gyro self test scale used to calculate factory cal bias later */
        char gyroScale[10];
        LOGV_IF(SYSFS_VERBOSE,
                "HAL:sysfs:cat %s (%lld)", mpu.in_gyro_self_test_scale, getTimestamp());
        fd = open(mpu.in_gyro_self_test_scale, O_RDONLY);
        if(fd < 0) {
            LOGE_IF(0,"HAL:Error opening gyro self test scale");
        } else {
            memset(gyroBuf, 0, sizeof(gyroBuf));
            count = read_attribute_sensor(fd, gyroScale, sizeof(gyroScale));
            if(count < 1) {
                LOGE_IF(0,"HAL:Error reading gyro self test scale");
            } else {
                count = sscanf(gyroScale, "%d", &mGyroSelfTestScale);
                if(count)
                    LOGV_IF(EXTRA_VERBOSE, "HAL:Gyro self test scale used %d", mGyroSelfTestScale);
            }
            close(fd);
        }

        /* open Factory Gyro Bias fd */
        /* mFactoryGyBias contains bias values that will be used for device offset */
        memset(mFactoryGyroBias, 0, sizeof(mFactoryGyroBias));
        memset(mFactoryGyroBiasLp, 0, sizeof(mFactoryGyroBiasLp));
        memset(mGyroBiasUiMode, 0, sizeof(mGyroBiasUiMode));
        LOGV_IF(EXTRA_VERBOSE, "HAL:factory gyro x offset path: %s", mpu.in_gyro_x_offset);
        LOGV_IF(EXTRA_VERBOSE, "HAL:factory gyro y offset path: %s", mpu.in_gyro_y_offset);
        LOGV_IF(EXTRA_VERBOSE, "HAL:factory gyro z offset path: %s", mpu.in_gyro_z_offset);
        gyro_x_offset_fd = open(mpu.in_gyro_x_offset, O_RDWR);
        gyro_y_offset_fd = open(mpu.in_gyro_y_offset, O_RDWR);
        gyro_z_offset_fd = open(mpu.in_gyro_z_offset, O_RDWR);
        if (gyro_x_offset_fd == -1 ||
                gyro_y_offset_fd == -1 || gyro_z_offset_fd == -1) {
            LOGE_IF(0,"HAL:could not open factory gyro calibrated bias");
        } else {
            LOGV_IF(EXTRA_VERBOSE,
                    "HAL:gyro_offset opened");
        }

        /* open Gyro Bias fd */
        /* mGyroBias contains bias values that will be used for framework */
        /* mGyroChipBias contains bias values that will be used for dmp */
        LOGV_IF(EXTRA_VERBOSE, "HAL: gyro x dmp bias path: %s", mpu.in_gyro_x_dmp_bias);
        LOGV_IF(EXTRA_VERBOSE, "HAL: gyro y dmp bias path: %s", mpu.in_gyro_y_dmp_bias);
        LOGV_IF(EXTRA_VERBOSE, "HAL: gyro z dmp bias path: %s", mpu.in_gyro_z_dmp_bias);
        gyro_x_dmp_bias_fd = open(mpu.in_gyro_x_dmp_bias, O_RDWR);
        gyro_y_dmp_bias_fd = open(mpu.in_gyro_y_dmp_bias, O_RDWR);
        gyro_z_dmp_bias_fd = open(mpu.in_gyro_z_dmp_bias, O_RDWR);
        if (gyro_x_dmp_bias_fd == -1 ||
                gyro_y_dmp_bias_fd == -1 || gyro_z_dmp_bias_fd == -1) {
            LOGW("HAL:could not open gyro DMP calibrated bias");
        } else {
            LOGV_IF(EXTRA_VERBOSE,
                    "HAL:gyro_dmp_bias opened");
        }

        /* read accel FSR to calcuate accel scale later */
        char buf[3];
        count = 0;
        LOGV_IF(SYSFS_VERBOSE,
                "HAL:sysfs:cat %s (%lld)", mpu.accel_fsr, getTimestamp());
        fd = open(mpu.accel_fsr, O_RDONLY);
        if(fd < 0) {
            LOGE("HAL:Error opening accel FSR");
        } else {
            memset(buf, 0, sizeof(buf));
            count = read_attribute_sensor(fd, buf, sizeof(buf));
            if(count < 1) {
                LOGE("HAL:Error reading accel FSR");
            } else {
                count = sscanf(buf, "%d", &mAccelScale);
                if(count)
                    LOGV_IF(EXTRA_VERBOSE, "HAL:Accel FSR used %d", mAccelScale);
            }
            adapter_accel_set_full_scale (mAccelScale);
            close(fd);
        }

        /* read accel self test scale used to calculate factory cal bias later */
        char accelScale[5];
        LOGV_IF(SYSFS_VERBOSE,
                "HAL:sysfs:cat %s (%lld)", mpu.in_accel_self_test_scale, getTimestamp());
        fd = open(mpu.in_accel_self_test_scale, O_RDONLY);
        if(fd < 0) {
            LOGE_IF(0,"HAL:Error opening accel self test scale");
        } else {
            memset(buf, 0, sizeof(buf));
            count = read_attribute_sensor(fd, accelScale, sizeof(accelScale));
            if(count < 1) {
                LOGE_IF(0,"HAL:Error reading accel self test scale");
            } else {
                count = sscanf(accelScale, "%d", &mAccelSelfTestScale);
                if(count)
                    LOGV_IF(EXTRA_VERBOSE, "HAL:Accel self test scale used %d", mAccelSelfTestScale);
            }
            close(fd);
        }

        /* open Factory Accel Bias fd */
        /* mFactoryAccelBias contains bias values that will be used for device offset */
        memset(mFactoryAccelBias, 0, sizeof(mFactoryAccelBias));
        memset(mFactoryAccelBiasLp, 0, sizeof(mFactoryAccelBiasLp));
        memset(mAccelBiasUiMode, 0, sizeof(mAccelBiasUiMode));
        LOGV_IF(EXTRA_VERBOSE, "HAL:factory accel x offset path: %s", mpu.in_accel_x_offset);
        LOGV_IF(EXTRA_VERBOSE, "HAL:factory accel y offset path: %s", mpu.in_accel_y_offset);
        LOGV_IF(EXTRA_VERBOSE, "HAL:factory accel z offset path: %s", mpu.in_accel_z_offset);
        accel_x_offset_fd = open(mpu.in_accel_x_offset, O_RDWR);
        accel_y_offset_fd = open(mpu.in_accel_y_offset, O_RDWR);
        accel_z_offset_fd = open(mpu.in_accel_z_offset, O_RDWR);
        if (accel_x_offset_fd == -1 ||
                accel_y_offset_fd == -1 || accel_z_offset_fd == -1) {
            LOGW("HAL:could not open factory accel calibrated bias");
        } else {
            LOGV_IF(EXTRA_VERBOSE,
                    "HAL:accel_offset opened");
        }

        /* open Accel Bias fd */
        /* mAccelBias contains bias that will be used for dmp */
        LOGV_IF(EXTRA_VERBOSE, "HAL:accel x dmp bias path: %s", mpu.in_accel_x_dmp_bias);
        LOGV_IF(EXTRA_VERBOSE, "HAL:accel y dmp bias path: %s", mpu.in_accel_y_dmp_bias);
        LOGV_IF(EXTRA_VERBOSE, "HAL:accel z dmp bias path: %s", mpu.in_accel_z_dmp_bias);
        accel_x_dmp_bias_fd = open(mpu.in_accel_x_dmp_bias, O_RDWR);
        accel_y_dmp_bias_fd = open(mpu.in_accel_y_dmp_bias, O_RDWR);
        accel_z_dmp_bias_fd = open(mpu.in_accel_z_dmp_bias, O_RDWR);
        if (accel_x_dmp_bias_fd == -1 ||
                accel_y_dmp_bias_fd == -1 || accel_z_dmp_bias_fd == -1) {
            LOGW("HAL:could not open accel DMP calibrated bias");
        } else {
            LOGV_IF(EXTRA_VERBOSE,
                    "HAL:accel_dmp_bias opened");
        }

        /* open Compass Bias fd */
        /* mCompassBias contains bias that will be used for dmp */
        LOGV_IF(EXTRA_VERBOSE, "HAL:compass x dmp bias path: %s", mpu.in_compass_x_dmp_bias);
        LOGV_IF(EXTRA_VERBOSE, "HAL:compass y dmp bias path: %s", mpu.in_compass_y_dmp_bias);
        LOGV_IF(EXTRA_VERBOSE, "HAL:compass z dmp bias path: %s", mpu.in_compass_z_dmp_bias);
        compass_x_dmp_bias_fd = open(mpu.in_compass_x_dmp_bias, O_RDWR);
        compass_y_dmp_bias_fd = open(mpu.in_compass_y_dmp_bias, O_RDWR);
        compass_z_dmp_bias_fd = open(mpu.in_compass_z_dmp_bias, O_RDWR);
        if (compass_x_dmp_bias_fd == -1 ||
                compass_y_dmp_bias_fd == -1 || compass_z_dmp_bias_fd == -1) {
            LOGW("HAL:could not open compass DMP calibrated bias");
        } else {
            LOGV_IF(EXTRA_VERBOSE,
                    "HAL:compass_dmp_bias opened");
        }

	if (!(mCalibrationMode & mpl_gesture))
	{
		dmp_sign_motion_fd = open(mpu.event_smd, O_RDONLY | O_NONBLOCK);
		if (dmp_sign_motion_fd < 0) {
			LOGE("HAL:ERR couldn't open dmp_sign_motion node");
		} else {
			LOGV_IF(ENG_VERBOSE,
					"HAL:dmp_sign_motion_fd opened : %d", dmp_sign_motion_fd);
		}

		dmp_pedometer_fd = open(mpu.event_pedometer, O_RDONLY | O_NONBLOCK);
		if (dmp_pedometer_fd < 0) {
			LOGE("HAL:ERR couldn't open dmp_pedometer node");
		} else {
			LOGV_IF(ENG_VERBOSE,
					"HAL:dmp_pedometer_fd opened : %d", dmp_pedometer_fd);
		}

		dmp_tilt_fd = open(mpu.event_tilt, O_RDONLY | O_NONBLOCK);
		if (dmp_tilt_fd < 0) {
			LOGE("HAL:ERR couldn't open dmp_tilt node");
		} else {
			LOGV_IF(ENG_VERBOSE,
					"HAL:dmp_tilt_fd opened : %d", dmp_tilt_fd);
		}

		dmp_pickup_fd = open(mpu.event_pickup, O_RDONLY | O_NONBLOCK);
		if (dmp_pickup_fd < 0) {
			LOGE("HAL:ERR couldn't open dmp_pickup node");
		} else {
			LOGV_IF(ENG_VERBOSE,
					"HAL:dmp_pickup_fd opened : %d", dmp_pickup_fd);
		}
	}

#if DEBUG_DRIVER
        /* setup Dmp Calibration */
        enableDmpCalibration(1);
#endif

        /* setup sensor bias */
        initBias();

        LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
                0, mpu.debug_determine_engine_on, getTimestamp());
        write_sysfs_int(mpu.debug_determine_engine_on, 0);

#if DEBUG_DRIVER
        /* debug driver */
        LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
                1, mpu.debug_determine_engine_on, getTimestamp());
        write_sysfs_int(mpu.debug_determine_engine_on, 1);

        LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
                1, mpu.d_misc_gyro_recalibration, getTimestamp());
        write_sysfs_int(mpu.d_misc_gyro_recalibration, 1);

        LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
                0, mpu.accel_accuracy_enable, getTimestamp());
        write_sysfs_int(mpu.accel_accuracy_enable, 0);
        LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
                0, mpu.anglvel_accuracy_enable, getTimestamp());
        write_sysfs_int(mpu.anglvel_accuracy_enable, 0);
        LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
                0, mpu.magn_accuracy_enable, getTimestamp());
        write_sysfs_int(mpu.magn_accuracy_enable, 0);
#endif

        /* setup MPL */
        inv_constructor_init();

        /* setup orientation matrix and scale */
        inv_set_device_properties();

        /* initialize sensor data */
        memset(mPendingEvents, 0, sizeof(mPendingEvents));

        /* Pending Events for HW and Virtual Sensors */
        mPendingEvents[RotationVector].version = sizeof(sensors_event_t);
        mPendingEvents[RotationVector].sensor = ID_RV;
        mPendingEvents[RotationVector].type = SENSOR_TYPE_ROTATION_VECTOR;
        mPendingEvents[RotationVector].acceleration.status
            = SENSOR_STATUS_UNRELIABLE;

        mPendingEvents[GameRotationVector].version = sizeof(sensors_event_t);
        mPendingEvents[GameRotationVector].sensor = ID_GRV;
        mPendingEvents[GameRotationVector].type = SENSOR_TYPE_GAME_ROTATION_VECTOR;
        mPendingEvents[GameRotationVector].acceleration.status
            = SENSOR_STATUS_UNRELIABLE;

        mPendingEvents[LinearAccel].version = sizeof(sensors_event_t);
        mPendingEvents[LinearAccel].sensor = ID_LA;
        mPendingEvents[LinearAccel].type = SENSOR_TYPE_LINEAR_ACCELERATION;
        mPendingEvents[LinearAccel].acceleration.status
            = SENSOR_STATUS_UNRELIABLE;

        mPendingEvents[Gravity].version = sizeof(sensors_event_t);
        mPendingEvents[Gravity].sensor = ID_GR;
        mPendingEvents[Gravity].type = SENSOR_TYPE_GRAVITY;
        mPendingEvents[Gravity].acceleration.status = SENSOR_STATUS_UNRELIABLE;

        mPendingEvents[Gyro].version = sizeof(sensors_event_t);
        mPendingEvents[Gyro].sensor = ID_GY;
        mPendingEvents[Gyro].type = SENSOR_TYPE_GYROSCOPE;
        mPendingEvents[Gyro].gyro.status = SENSOR_STATUS_UNRELIABLE;

        mPendingEvents[RawGyro].version = sizeof(sensors_event_t);
        mPendingEvents[RawGyro].sensor = ID_RG;
        mPendingEvents[RawGyro].type = SENSOR_TYPE_GYROSCOPE_UNCALIBRATED;
        mPendingEvents[RawGyro].gyro.status = SENSOR_STATUS_UNRELIABLE;

        mPendingEvents[Accelerometer].version = sizeof(sensors_event_t);
        mPendingEvents[Accelerometer].sensor = ID_A;
        mPendingEvents[Accelerometer].type = SENSOR_TYPE_ACCELEROMETER;
        mPendingEvents[Accelerometer].acceleration.status
            = SENSOR_STATUS_UNRELIABLE;

        /* Invensense compass calibration */
        mPendingEvents[MagneticField].version = sizeof(sensors_event_t);
        mPendingEvents[MagneticField].sensor = ID_M;
        mPendingEvents[MagneticField].type = SENSOR_TYPE_MAGNETIC_FIELD;
        mPendingEvents[MagneticField].magnetic.status =
            SENSOR_STATUS_UNRELIABLE;

        mPendingEvents[RawMagneticField].version = sizeof(sensors_event_t);
        mPendingEvents[RawMagneticField].sensor = ID_RM;
        mPendingEvents[RawMagneticField].type = SENSOR_TYPE_MAGNETIC_FIELD_UNCALIBRATED;
        mPendingEvents[RawMagneticField].magnetic.status =
            SENSOR_STATUS_UNRELIABLE;

        mPendingEvents[Orientation].version = sizeof(sensors_event_t);
        mPendingEvents[Orientation].sensor = ID_O;
        mPendingEvents[Orientation].type = SENSOR_TYPE_ORIENTATION;
        mPendingEvents[Orientation].orientation.status
            = SENSOR_STATUS_UNRELIABLE;

        mPendingEvents[GeomagneticRotationVector].version = sizeof(sensors_event_t);
        mPendingEvents[GeomagneticRotationVector].sensor = ID_GMRV;
        mPendingEvents[GeomagneticRotationVector].type
            = SENSOR_TYPE_GEOMAGNETIC_ROTATION_VECTOR;
        mPendingEvents[GeomagneticRotationVector].acceleration.status
            = SENSOR_STATUS_UNRELIABLE;

        mPendingEvents[StepCounter].version = sizeof(sensors_event_t);
        mPendingEvents[StepCounter].sensor = ID_SC;
        mPendingEvents[StepCounter].type = SENSOR_TYPE_STEP_COUNTER;
        mPendingEvents[StepCounter].acceleration.status = SENSOR_STATUS_UNRELIABLE;

        mSmEvents.version = sizeof(sensors_event_t);
        mSmEvents.sensor = ID_SM;
        mSmEvents.type = SENSOR_TYPE_SIGNIFICANT_MOTION;
        mSmEvents.acceleration.status = SENSOR_STATUS_UNRELIABLE;

        mPendingEvents[StepDetector].version = sizeof(sensors_event_t);
        mPendingEvents[StepDetector].sensor = ID_P;
        mPendingEvents[StepDetector].type = SENSOR_TYPE_STEP_DETECTOR;
        mPendingEvents[StepDetector].acceleration.status = SENSOR_STATUS_UNRELIABLE;

        if(mCalibrationMode & mpl_gesture){
            mPendingEvents[Tilt].version = sizeof(sensors_event_t);
            mPendingEvents[Tilt].sensor = ID_T;
            mPendingEvents[Tilt].type = SENSOR_TYPE_TILT_DETECTOR;
            mPendingEvents[Tilt].acceleration.status = SENSOR_STATUS_UNRELIABLE;
            mPendingEvents[Pickup].version = sizeof(sensors_event_t);
            mPendingEvents[Pickup].sensor = ID_PICK;
            mPendingEvents[Pickup].type = SENSOR_TYPE_PICK_UP_GESTURE;
            mPendingEvents[Pickup].acceleration.status = SENSOR_STATUS_UNRELIABLE;
        } else {
            mTiltEvents.version = sizeof(sensors_event_t);
            mTiltEvents.sensor = ID_T;
            mTiltEvents.type = SENSOR_TYPE_TILT_DETECTOR;
            mTiltEvents.acceleration.status = SENSOR_STATUS_UNRELIABLE;
            mPickupEvents.version = sizeof(sensors_event_t);
            mPickupEvents.sensor = ID_PICK;
            mPickupEvents.type = SENSOR_TYPE_PICK_UP_GESTURE;
            mPickupEvents.acceleration.status = SENSOR_STATUS_UNRELIABLE;
        }
#if 0
        mPendingEvents[EISGyroscope].version = sizeof(sensors_event_t);
        mPendingEvents[EISGyroscope].sensor = ID_EISGY;
        mPendingEvents[EISGyroscope].type = SENSOR_TYPE_EIS_GYROSCOPE;
        mPendingEvents[EISGyroscope].acceleration.status
            = SENSOR_STATUS_UNRELIABLE;

        mPendingEvents[EISAuthentication].version = sizeof(sensors_event_t);
        mPendingEvents[EISAuthentication].sensor = ID_EISAUTHENTICATION;
        mPendingEvents[EISAuthentication].type = SENSOR_TYPE_EIS_AUTHENTICATION;
        mPendingEvents[EISAuthentication].acceleration.status
            = SENSOR_STATUS_UNRELIABLE;
#endif
        /* Pending Events for Wakeup Sensors */
        mPendingEvents[RotationVector_Wake].version = sizeof(sensors_event_t);
        mPendingEvents[RotationVector_Wake].sensor = ID_RVW;
        mPendingEvents[RotationVector_Wake].type = SENSOR_TYPE_ROTATION_VECTOR;
        mPendingEvents[RotationVector_Wake].acceleration.status
            = SENSOR_STATUS_UNRELIABLE;

        mPendingEvents[GameRotationVector_Wake].version = sizeof(sensors_event_t);
        mPendingEvents[GameRotationVector_Wake].sensor = ID_GRVW;
        mPendingEvents[GameRotationVector_Wake].type = SENSOR_TYPE_GAME_ROTATION_VECTOR;
        mPendingEvents[GameRotationVector_Wake].acceleration.status
            = SENSOR_STATUS_UNRELIABLE;

        mPendingEvents[LinearAccel_Wake].version = sizeof(sensors_event_t);
        mPendingEvents[LinearAccel_Wake].sensor = ID_LAW;
        mPendingEvents[LinearAccel_Wake].type = SENSOR_TYPE_LINEAR_ACCELERATION;
        mPendingEvents[LinearAccel_Wake].acceleration.status
            = SENSOR_STATUS_UNRELIABLE;

        mPendingEvents[Gravity_Wake].version = sizeof(sensors_event_t);
        mPendingEvents[Gravity_Wake].sensor = ID_GRW;
        mPendingEvents[Gravity_Wake].type = SENSOR_TYPE_GRAVITY;
        mPendingEvents[Gravity_Wake].acceleration.status = SENSOR_STATUS_UNRELIABLE;

        mPendingEvents[Gyro_Wake].version = sizeof(sensors_event_t);
        mPendingEvents[Gyro_Wake].sensor = ID_GYW;
        mPendingEvents[Gyro_Wake].type = SENSOR_TYPE_GYROSCOPE;
        mPendingEvents[Gyro_Wake].gyro.status = SENSOR_STATUS_UNRELIABLE;

        mPendingEvents[RawGyro_Wake].version = sizeof(sensors_event_t);
        mPendingEvents[RawGyro_Wake].sensor = ID_RGW;
        mPendingEvents[RawGyro_Wake].type = SENSOR_TYPE_GYROSCOPE_UNCALIBRATED;
        mPendingEvents[RawGyro_Wake].gyro.status = SENSOR_STATUS_UNRELIABLE;

        mPendingEvents[Accelerometer_Wake].version = sizeof(sensors_event_t);
        mPendingEvents[Accelerometer_Wake].sensor = ID_AW;
        mPendingEvents[Accelerometer_Wake].type = SENSOR_TYPE_ACCELEROMETER;
        mPendingEvents[Accelerometer_Wake].acceleration.status
            = SENSOR_STATUS_UNRELIABLE;

        /* Invensense compass calibration */
        mPendingEvents[MagneticField_Wake].version = sizeof(sensors_event_t);
        mPendingEvents[MagneticField_Wake].sensor = ID_MW;
        mPendingEvents[MagneticField_Wake].type = SENSOR_TYPE_MAGNETIC_FIELD;
        mPendingEvents[MagneticField_Wake].magnetic.status =
            SENSOR_STATUS_UNRELIABLE;

        mPendingEvents[RawMagneticField_Wake].version = sizeof(sensors_event_t);
        mPendingEvents[RawMagneticField_Wake].sensor = ID_RMW;
        mPendingEvents[RawMagneticField_Wake].type = SENSOR_TYPE_MAGNETIC_FIELD_UNCALIBRATED;
        mPendingEvents[RawMagneticField_Wake].magnetic.status =
            SENSOR_STATUS_UNRELIABLE;

        mPendingEvents[Orientation_Wake].version = sizeof(sensors_event_t);
        mPendingEvents[Orientation_Wake].sensor = ID_OW;
        mPendingEvents[Orientation_Wake].type = SENSOR_TYPE_ORIENTATION;
        mPendingEvents[Orientation_Wake].orientation.status
            = SENSOR_STATUS_UNRELIABLE;

        mPendingEvents[GeomagneticRotationVector_Wake].version = sizeof(sensors_event_t);
        mPendingEvents[GeomagneticRotationVector_Wake].sensor = ID_GMRVW;
        mPendingEvents[GeomagneticRotationVector_Wake].type
            = SENSOR_TYPE_GEOMAGNETIC_ROTATION_VECTOR;
        mPendingEvents[GeomagneticRotationVector_Wake].acceleration.status
            = SENSOR_STATUS_UNRELIABLE;

        mPendingEvents[StepCounter_Wake].version = sizeof(sensors_event_t);
        mPendingEvents[StepCounter_Wake].sensor = ID_SCW;
        mPendingEvents[StepCounter_Wake].type = SENSOR_TYPE_STEP_COUNTER;
        mPendingEvents[StepCounter_Wake].acceleration.status = SENSOR_STATUS_UNRELIABLE;

        mPendingEvents[StepDetector_Wake].version = sizeof(sensors_event_t);
        mPendingEvents[StepDetector_Wake].sensor = ID_PW;
        mPendingEvents[StepDetector_Wake].type = SENSOR_TYPE_STEP_DETECTOR;
        mPendingEvents[StepDetector_Wake].acceleration.status = SENSOR_STATUS_UNRELIABLE;

        if (mPressureSensorPresent) {
            mPendingEvents[Pressure].version = sizeof(sensors_event_t);
            mPendingEvents[Pressure].sensor = ID_PS;
            mPendingEvents[Pressure].type = SENSOR_TYPE_PRESSURE;
            mPendingEvents[Pressure].magnetic.status =
                SENSOR_STATUS_UNRELIABLE;

            mPendingEvents[Pressure_Wake].version = sizeof(sensors_event_t);
            mPendingEvents[Pressure_Wake].sensor = ID_PSW;
            mPendingEvents[Pressure_Wake].type = SENSOR_TYPE_PRESSURE;
            mPendingEvents[Pressure_Wake].magnetic.status =
                SENSOR_STATUS_UNRELIABLE;
        }

        if (mLightSensorPresent) {
            mPendingEvents[Light].version = sizeof(sensors_event_t);
            mPendingEvents[Light].sensor = ID_L;
            mPendingEvents[Light].type = SENSOR_TYPE_LIGHT;
            mPendingEvents[Light].magnetic.status =
                SENSOR_STATUS_UNRELIABLE;

            mPendingEvents[Proximity].version = sizeof(sensors_event_t);
            mPendingEvents[Proximity].sensor = ID_PR;
            mPendingEvents[Proximity].type = SENSOR_TYPE_PROXIMITY;
            mPendingEvents[Proximity].magnetic.status =
                SENSOR_STATUS_UNRELIABLE;

            mPendingEvents[Light_Wake].version = sizeof(sensors_event_t);
            mPendingEvents[Light_Wake].sensor = ID_LW;
            mPendingEvents[Light_Wake].type = SENSOR_TYPE_LIGHT;
            mPendingEvents[Light_Wake].magnetic.status =
                SENSOR_STATUS_UNRELIABLE;

            mPendingEvents[Proximity_Wake].version = sizeof(sensors_event_t);
            mPendingEvents[Proximity_Wake].sensor = ID_PRW;
            mPendingEvents[Proximity_Wake].type = SENSOR_TYPE_PROXIMITY;
            mPendingEvents[Proximity_Wake].magnetic.status =
                SENSOR_STATUS_UNRELIABLE;
        }

	if (mCustomSensorPresent) {
	    mPendingEvents[GyroRaw].version = sizeof(sensors_event_t);
	    mPendingEvents[GyroRaw].sensor = ID_GRC;
	    mPendingEvents[GyroRaw].type = SENSOR_TYPE_GYROSCOPE_UNCALIBRATED;
	    mPendingEvents[GyroRaw].gyro.status = SENSOR_STATUS_UNRELIABLE;

	    mPendingEvents[AccelerometerRaw].version = sizeof(sensors_event_t);
	    mPendingEvents[AccelerometerRaw].sensor = ID_ARC;
	    mPendingEvents[AccelerometerRaw].type = SENSOR_TYPE_ACCELEROMETER;
	    mPendingEvents[AccelerometerRaw].acceleration.status = SENSOR_STATUS_UNRELIABLE;

	    mPendingEvents[MagneticFieldRaw].version = sizeof(sensors_event_t);
	    mPendingEvents[MagneticFieldRaw].sensor = ID_MRC;
	    mPendingEvents[MagneticFieldRaw].type = SENSOR_TYPE_MAGNETIC_FIELD_UNCALIBRATED;
	    mPendingEvents[MagneticFieldRaw].magnetic.status = SENSOR_STATUS_UNRELIABLE;
	}

        /* Event Handlers for HW and Virtual Sensors */
        mHandlers[RotationVector] = &MPLSensor::rvHandler;
        mHandlers[GameRotationVector] = &MPLSensor::grvHandler;
        mHandlers[LinearAccel] = &MPLSensor::laHandler;
        mHandlers[Gravity] = &MPLSensor::gravHandler;
        mHandlers[Gyro] = &MPLSensor::gyroHandler;
        mHandlers[RawGyro] = &MPLSensor::rawGyroHandler;
        mHandlers[Accelerometer] = &MPLSensor::accelHandler;
        mHandlers[MagneticField] = &MPLSensor::compassHandler;
        mHandlers[RawMagneticField] = &MPLSensor::rawCompassHandler;
        mHandlers[Orientation] = &MPLSensor::orienHandler;
        mHandlers[GeomagneticRotationVector] = &MPLSensor::gmHandler;
        mHandlers[Tilt] = &MPLSensor::tiltHandler;
        mHandlers[Pickup] = &MPLSensor::pickupHandler;
	#if 0
        mHandlers[EISGyroscope] = &MPLSensor::eisHandler;
        mHandlers[EISAuthentication] = &MPLSensor::eisAuthenticationHandler;
	#endif
        mHandlers[StepCounter] = &MPLSensor::scHandler;
        mHandlers[StepDetector] = &MPLSensor::sdHandler;
        /* Event Handlers for Wakeup Sensors */
        mHandlers[RotationVector_Wake] = &MPLSensor::rvwHandler;
        mHandlers[GameRotationVector_Wake] = &MPLSensor::grvwHandler;
        mHandlers[LinearAccel_Wake] = &MPLSensor::lawHandler;
        mHandlers[Gravity_Wake] = &MPLSensor::gravwHandler;
        mHandlers[Gyro_Wake] = &MPLSensor::gyrowHandler;
        mHandlers[RawGyro_Wake] = &MPLSensor::rawGyrowHandler;
        mHandlers[Accelerometer_Wake] = &MPLSensor::accelwHandler;
        mHandlers[MagneticField_Wake] = &MPLSensor::compasswHandler;
        mHandlers[RawMagneticField_Wake] = &MPLSensor::rawCompasswHandler;
        mHandlers[Orientation_Wake] = &MPLSensor::orienwHandler;
        mHandlers[GeomagneticRotationVector_Wake] = &MPLSensor::gmwHandler;
        mHandlers[StepCounter_Wake] = &MPLSensor::scwHandler;
        mHandlers[StepDetector_Wake] = &MPLSensor::sdwHandler;
        /* Event Handler for Secondary devices */
        if (mPressureSensorPresent) {
            mHandlers[Pressure] = &MPLSensor::psHandler;
            mHandlers[Pressure_Wake] = &MPLSensor::pswHandler;
        }
        if (mLightSensorPresent) {
            mHandlers[Light] = &MPLSensor::lightHandler;
            mHandlers[Proximity] = &MPLSensor::proxHandler;
            mHandlers[Light_Wake] = &MPLSensor::lightwHandler;
            mHandlers[Proximity_Wake] = &MPLSensor::proxwHandler;
        }

	if (mCustomSensorPresent) {
	    mHandlers[GyroRaw] = &MPLSensor::gyroRawHandler;
	    mHandlers[AccelerometerRaw] = &MPLSensor::accelRawHandler;
	    mHandlers[MagneticFieldRaw] = &MPLSensor::compassRawHandler;
	}

        /* initialize delays to reasonable values */
        for (int i = 0; i < TotalNumSensors; i++) {
            mDelays[i] = NS_PER_SECOND;
            mBatchDelays[i] = NS_PER_SECOND;
            mBatchTimeouts[i] = 100000000000LL;
        }

        /* initialize Compass Bias */
        memset(mCompassBias, 0, sizeof(mCompassBias));
        memset(mDmpCompassBias, 0, sizeof(mDmpCompassBias));

        /* initialize Accel Bias */
        memset(mFactoryAccelBias, 0, sizeof(mFactoryAccelBias));
        memset(mAccelBias, 0, sizeof(mAccelBias));
        memset(mAccelDmpBias, 0, sizeof(mAccelDmpBias));

        /* initialize Gyro Bias */
        memset(mGyroBias, 0, sizeof(mGyroBias));
        memset(mGyroChipBias, 0, sizeof(mGyroChipBias));
        memset(mDmpGyroBias, 0, sizeof(mDmpGyroBias));

        /* load calibration file from /data/inv_cal_data.bin */
        rv = inv_load_calibration();
        if(rv == INV_SUCCESS) {
            LOGV_IF(PROCESS_VERBOSE, "HAL:Calibration file successfully loaded");
            /* Get initial values */
            getGyroBias();
            if (mGyroBiasAvailable) {
                setDmpGyroBias();
            }
            getAccelBias();
            if (mAccelBiasAvailable) {
                setDmpAccelBias();
            }
            getCompassBias();
            if (mCompassBiasAvailable) {
                setDmpCompassBias();
            }
            getFactoryGyroBias();
            if (mFactoryGyroBiasAvailable) {
                setFactoryGyroBias();
            }
            getFactoryAccelBias();
            if (mFactoryAccelBiasAvailable) {
                setFactoryAccelBias();
            }
            if (mGyroBiasAvailable || mAccelBiasAvailable ||
                    mCompassBiasAvailable) {
            }
            resetCalStatus(0);
        }
        else {
            LOGE("HAL:Could not open or load MPL calibration file (%d)", rv);
            resetCalStatus(1);
        }

        /* disable all sensors and features */
        enableRawGyro(0);
        enableGyro(0);
        enableAccel(0);
        enableCompass(0);
        enableRawCompass(0);
		#ifdef IIO_LIGHT_SENSOR
        enablePressure(0);
		#endif
        enableBatch(0);

        if (isLowPowerQuatEnabled()) {
            enableLPQuaternion(0);
            enableLPQuaternionWake(0);
        }

        prevtime =getTimestamp();
        currentime =getTimestamp();
}

void MPLSensor::enable_iio_sysfs(void)
{
    VFUNC_LOG;

    char iio_device_node[MAX_CHIP_ID_LEN];
    FILE *tempFp = NULL;

    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo 1 > %s (%lld)",
            mpu.in_timestamp_en, getTimestamp());

    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            IIO_BUFFER_LENGTH, mpu.buffer_length, getTimestamp());
    tempFp = fopen(mpu.buffer_length, "w");
    if (tempFp == NULL) {
        LOGE("HAL:could not open buffer length");
    } else {
        if (fprintf(tempFp, "%d", IIO_BUFFER_LENGTH) < 0 || fclose(tempFp) < 0) {
            LOGE("HAL:could not write buffer length");
        }
    }

    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            0, mpu.chip_enable, getTimestamp());
    tempFp = fopen(mpu.chip_enable, "w");
    if (tempFp == NULL) {
        LOGE("HAL:could not open chip enable");
    } else {
        if (fprintf(tempFp, "%d", 0) < 0 || fclose(tempFp) < 0) {
            LOGE("HAL:could not write chip enable");
        }
    }

    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            1, mpu.chip_enable, getTimestamp());
    tempFp = fopen(mpu.chip_enable, "w");
    if (tempFp == NULL) {
        LOGE("HAL:could not open chip enable");
    } else {
        if (fprintf(tempFp, "%d", 1) < 0 || fclose(tempFp) < 0) {
            LOGE("HAL:could not write chip enable");
        }
    }

    inv_get_iio_device_node(iio_device_node);
    iio_fd = open(iio_device_node, O_RDONLY);
    if (iio_fd < 0) {
        LOGE("HAL:could not open iio device node");
    } else {
        LOGV_IF(ENG_VERBOSE, "HAL:iio iio_fd opened : %d", iio_fd);
    }
}

int MPLSensor::inv_constructor_init(void)
{
    VFUNC_LOG;

    inv_error_t result = inv_init_mpl();
    if (result) {
        LOGE("HAL:inv_init_mpl() failed");
        return result;
    }
    result = inv_constructor_default_enable();
    result = inv_start_mpl();
    if (result) {
        LOGE("HAL:inv_start_mpl() failed");
        LOG_RESULT_LOCATION(result);
        return result;
    }

    return result;
}

int MPLSensor::inv_constructor_default_enable(void)
{
    VFUNC_LOG;

    inv_error_t result;


    result = inv_enable_hal_outputs();
    if (result) {
        return result;
    }
    return result;
}

/* TODO: create function pointers to calculate scale */
void MPLSensor::inv_set_device_properties(void)
{
    VFUNC_LOG;

    inv_get_sensors_orientation();

    adapter_set_sample_rate(DEFAULT_MPL_GYRO_RATE, ID_GY);
    adapter_set_sample_rate(DEFAULT_MPL_COMPASS_RATE, ID_M);

    /* gyro setup */
    mGyroOrientationScalar = inv_orientation_matrix_to_scalar(mGyroOrientationMatrix);
    adapter_set_gyro_orientation_and_scale(mGyroOrientationScalar, mGyroScale << 15);
    LOGI_IF(EXTRA_VERBOSE, "HAL: Set orient %d, MPL Gyro Scale %d", mGyroOrientationScalar, mGyroScale << 15);

    /* accel setup */
    mAccelOrientationScalar = inv_orientation_matrix_to_scalar(mAccelOrientationMatrix);
    adapter_set_accel_orientation_and_scale(mAccelOrientationScalar, (int)mAccelScale << 19);
    LOGI_IF(EXTRA_VERBOSE,
            "HAL: Set orient %d, MPL Accel Scale %d", mAccelOrientationScalar, (int)mAccelScale << 19);

    /* compass setup */
    mCompassSensor->getOrientationMatrix(mCompassOrientationMatrix);
    mCompassOrientationScalar = inv_orientation_matrix_to_scalar(mCompassOrientationMatrix);

    mCompassScale = mCompassSensor->getSensitivity();
    adapter_set_compass_orientation_and_scale1(mCompassOrientationScalar, mCompassScale, mCompassSens, mCompassSoftIron);
    LOGI_IF(EXTRA_VERBOSE,
            "HAL: Set MPL Compass Scale %d", mCompassScale);
}

void MPLSensor::loadDMP(char *chipID)
{
    VFUNC_LOG;

    int res, fd;
    FILE *fptr;

    if (isMpuNonDmp()) {
        return;
    }

    /* load DMP firmware */
    LOGV_IF(SYSFS_VERBOSE,
            "HAL:sysfs:cat %s (%lld)", mpu.firmware_loaded, getTimestamp());
    fd = open(mpu.firmware_loaded, O_RDONLY);
    if(fd < 0) {
        LOGE("HAL:could not open DMP state");
    } else {
        //if(inv_read_dmp_state(fd) == 0) {
            LOGV_IF(EXTRA_VERBOSE, "HAL:load DMP");
           // if (mSysfsPath)
				{
                /* Modify to load other chipsets */
                int tempID = 20645;
                if (strcmp(chipID, "ICM10320") == 0)
                    tempID = 10320;
                if (strcmp(chipID, "ICM20608D") == 0)
                    tempID = 206080;
                if (inv_load_dmp(mSysfsPath, tempID, "/system/vendor/firmware") < 0) {
                    LOGE("HAL:load DMP failed");
                } else {
                    LOGV_IF(EXTRA_VERBOSE, "HAL:DMP loaded");
                }
            }
        /*} else {
            LOGV_IF(EXTRA_VERBOSE, "HAL:DMP is already loaded");
        }*/
    }

    /* initialize DMP */
    char version_buf[16];
    int version_number = adapter_get_version();
    fd = open(mpu.dmp_init, O_WRONLY);
    if (fd < 0) {
        LOGE("HAL:could not initialize DMP");
    } else {
        if (write_attribute_sensor(fd, version_number) < 0) {
            LOGE("HAL:Error checking DMP version");
            return;
        }
        close(fd);
        fd = open(mpu.dmp_init, O_RDONLY);
        if(read_attribute_sensor(fd, version_buf, sizeof(version_buf)) < 0) {
            LOGE("HAL:Error reading DMP version");
            return;
        } else {
            int tempVersion = atoi(version_buf);
            adapter_set_version(tempVersion);
            LOGV_IF(EXTRA_VERBOSE, "HAL:read DMP version=%d", tempVersion);
        }
    }
}

void MPLSensor::inv_get_sensors_orientation(void)
{
    VFUNC_LOG;

    FILE *fptr;

    // get gyro orientation
    LOGV_IF(SYSFS_VERBOSE,
            "HAL:sysfs:cat %s (%lld)", mpu.gyro_orient, getTimestamp());
    fptr = fopen(mpu.gyro_orient, "r");
    if (fptr != NULL) {
        int om[9];
        if (fscanf(fptr, "%d,%d,%d,%d,%d,%d,%d,%d,%d",
                    &om[0], &om[1], &om[2], &om[3], &om[4], &om[5],
                    &om[6], &om[7], &om[8]) < 0 || fclose(fptr) < 0) {
            LOGE("HAL:Could not read gyro mounting matrix");
        } else {
            LOGV_IF(EXTRA_VERBOSE,
                    "HAL:gyro mounting matrix: "
                    "%+d %+d %+d %+d %+d %+d %+d %+d %+d",
                    om[0], om[1], om[2], om[3], om[4], om[5], om[6], om[7], om[8]);

            mGyroOrientationMatrix[0] = om[0];
            mGyroOrientationMatrix[1] = om[1];
            mGyroOrientationMatrix[2] = om[2];
            mGyroOrientationMatrix[3] = om[3];
            mGyroOrientationMatrix[4] = om[4];
            mGyroOrientationMatrix[5] = om[5];
            mGyroOrientationMatrix[6] = om[6];
            mGyroOrientationMatrix[7] = om[7];
            mGyroOrientationMatrix[8] = om[8];
        }
    }

    // get accel orientation
    LOGV_IF(SYSFS_VERBOSE,
            "HAL:sysfs:cat %s (%lld)", mpu.accel_orient, getTimestamp());
    fptr = fopen(mpu.accel_orient, "r");
    if (fptr != NULL) {
        int om[9];
        if (fscanf(fptr, "%d,%d,%d,%d,%d,%d,%d,%d,%d",
                    &om[0], &om[1], &om[2], &om[3], &om[4], &om[5],
                    &om[6], &om[7], &om[8]) < 0 || fclose(fptr) < 0) {
            LOGE("HAL:could not read accel mounting matrix");
        } else {
            LOGV_IF(EXTRA_VERBOSE,
                    "HAL:accel mounting matrix: "
                    "%+d %+d %+d %+d %+d %+d %+d %+d %+d",
                    om[0], om[1], om[2], om[3], om[4], om[5], om[6], om[7], om[8]);

            mAccelOrientationMatrix[0] = om[0];
            mAccelOrientationMatrix[1] = om[1];
            mAccelOrientationMatrix[2] = om[2];
            mAccelOrientationMatrix[3] = om[3];
            mAccelOrientationMatrix[4] = om[4];
            mAccelOrientationMatrix[5] = om[5];
            mAccelOrientationMatrix[6] = om[6];
            mAccelOrientationMatrix[7] = om[7];
            mAccelOrientationMatrix[8] = om[8];
        }
    }
}

MPLSensor::~MPLSensor()
{
    VFUNC_LOG;

    /* Close open fds */
    if (iio_fd > 0)
        close(iio_fd);
    if( accel_fd > 0 )
        close(accel_fd );
    if (gyro_temperature_fd > 0)
        close(gyro_temperature_fd);
    if (sysfs_names_ptr)
        free(sysfs_names_ptr);
    if (accel_x_dmp_bias_fd > 0) {
        close(accel_x_dmp_bias_fd);
    }
    if (accel_y_dmp_bias_fd > 0) {
        close(accel_y_dmp_bias_fd);
    }
    if (accel_z_dmp_bias_fd > 0) {
        close(accel_z_dmp_bias_fd);
    }

    if (gyro_x_dmp_bias_fd > 0) {
        close(gyro_x_dmp_bias_fd);
    }
    if (gyro_y_dmp_bias_fd > 0) {
        close(gyro_y_dmp_bias_fd);
    }
    if (gyro_z_dmp_bias_fd > 0) {
        close(gyro_z_dmp_bias_fd);
    }

    if (gyro_x_offset_fd > 0) {
        close(gyro_x_dmp_bias_fd);
    }
    if (gyro_y_offset_fd > 0) {
        close(gyro_y_offset_fd);
    }
    if (gyro_z_offset_fd > 0) {
        close(accel_z_offset_fd);
    }
    delete mCurrentSensorMask;
    delete mSysfsMask;
}

int MPLSensor::onDmp(int en)
{
    VFUNC_LOG;

    int res = 0;
    int status;
    mDmpOn = en;

    //Sequence to enable DMP
    //1. Check if DMP image is loaded
    //2. Enable DMP

    if (!(mCalibrationMode & dmp))
        return 0;

    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:cat %s (%lld)",
            mpu.firmware_loaded, getTimestamp());
    if(read_sysfs_int(mpu.firmware_loaded, &status) < 0){
        LOGE("HAL:ERR can't get firmware_loaded status");
        res = -1;
    } else if (status == 1) {
        //Write only if curr DMP state <> request
        LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:cat %s (%lld)",
                mpu.dmp_on, getTimestamp());
        if (read_sysfs_int(mpu.dmp_on, &status) < 0) {
            LOGE("HAL:ERR can't read DMP state");
            res = -1;
        } else if (status != en) {
            LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
                    en, mpu.dmp_on, getTimestamp());
            if (write_sysfs_int(mpu.dmp_on, en) < 0) {
                LOGE("HAL:ERR can't write dmp_on");
                res = -1;
            }
        }
    } else {
        LOGE("HAL:ERR No DMP image");
        res = -1;
    }
    return res;
}

int MPLSensor::setDmpFeature(int en)
{
    int res = 0;

    // set sensor engine and fifo
    if ((mFeatureActiveMask & DMP_FEATURE_MASK) || en) {
        if ((mFeatureActiveMask & INV_DMP_6AXIS_QUATERNION) ||
                (mFeatureActiveMask & INV_DMP_PED_QUATERNION) ||
                (mFeatureActiveMask & INV_DMP_QUATERNION)) {
            res += enableGyro(1);
            if (res < 0) {
                return res;
            }
            if (!(mLocalSensorMask & mMasterSensorMask &
                        (INV_THREE_AXIS_GYRO | INV_THREE_AXIS_RAW_GYRO))) {
                res = turnOffGyroFifo();
                if (res < 0) {
                    return res;
                }
            }
        }
        res = enableAccel(1);
        if (res < 0) {
            return res;
        }
        if (!(mLocalSensorMask & mMasterSensorMask & INV_THREE_AXIS_ACCEL)) {
            res = turnOffAccelFifo();
            if (res < 0) {
                return res;
            }
        }
    } else {
        if (!(mLocalSensorMask & mMasterSensorMask &
                    (INV_THREE_AXIS_GYRO | INV_THREE_AXIS_RAW_GYRO))) {
            res += enableGyro(0);
            res += enableRawGyro(0);
            if (res < 0) {
                return res;
            }
        }
        if (!(mLocalSensorMask & mMasterSensorMask & INV_THREE_AXIS_ACCEL)) {
            res = enableAccel(0);
            if (res < 0) {
                return res;
            }
        }
    }

    return res;
}

int MPLSensor::computeAndSetDmpState()
{
    int res = 0;
    bool dmpState = 1;

    if (mFeatureActiveMask) {
        dmpState = 1;
        LOGV_IF(PROCESS_VERBOSE, "HAL:computeAndSetDmpState() mFeatureActiveMask = 1");
    } else if ((mEnabled & VIRTUAL_SENSOR_9AXES_MASK)
            || (mEnabled & VIRTUAL_SENSOR_GYRO_6AXES_MASK)) {
        if (checkLPQuaternion() && checkLPQRateSupported()) {
            dmpState = 1;
            LOGV_IF(PROCESS_VERBOSE, "HAL:computeAndSetDmpState() Sensor Fusion = 1");
        }
    }

    // set Dmp state
    res = onDmp(dmpState);
    if (res < 0)
        return res;

    LOGV_IF(PROCESS_VERBOSE, "HAL:DMP is set %s", (dmpState ? "on" : "off"));
    return dmpState;
}
void MPLSensor::inv_set_engine_rate(uint32_t rate, uint64_t mask)
{
    struct sysfs_mask *localSensorMask;
    uint32_t i;

   for (i = 0; i < mNumSysfs; i++) {
	if (mask == mSysfsMask[i].sensorMask) {
		mSysfsMask[i].engineRate = rate;
	}
    }
}
void MPLSensor::inv_write_sysfs(uint32_t delay, uint64_t mask, char *sysfs_rate)
{
    int tempFd, count, res;
    char buf[10];
    int rate;

    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %.0f > %s (%lld)",
			NS_PER_SECOND_FLOAT / delay, sysfs_rate, getTimestamp());
    tempFd = open(sysfs_rate, O_RDWR);
    res = write_attribute_sensor(tempFd, NS_PER_SECOND_FLOAT / delay);
    if(res < 0) {
        LOGE("HAL:%s update delay error", sysfs_rate);
    }
    read_sysfs_int(sysfs_rate, &rate);
    rate = NS_PER_SECOND/rate;
    inv_set_engine_rate(rate, mask);

    LOGV_IF(SYSFS_VERBOSE, "read engine %s rate= %d\n", sysfs_rate, rate);

}
void MPLSensor::inv_read_sysfs_engine(uint64_t mask, char *_rate)
{
    int rate = 0;
    char sysfs_path[MAX_SYSFS_NAME_LEN];
    char sysfs_rate[MAX_SYSFS_NAME_LEN];

    memset(sysfs_rate, 0, sizeof(sysfs_rate));
    memset(sysfs_path, 0, sizeof(sysfs_path));
    sprintf(sysfs_rate, "%s/%s", mSysfsPath, _rate);
    read_sysfs_int(sysfs_rate, &rate);

    rate = NS_PER_SECOND/rate;
    inv_set_engine_rate(rate, mask);

    LOGV_IF(SYSFS_VERBOSE, "read engine %s rate= %d\n", sysfs_rate, rate);

}

void MPLSensor::setRawGyroRate(uint64_t delay)
{
     inv_write_sysfs(delay, INV_THREE_AXIS_RAW_GYRO, mpu.gyro_rate);
}
void MPLSensor::setRawGyroRateWake(uint64_t delay)
{
    inv_write_sysfs(delay, INV_THREE_AXIS_RAW_GYRO_WAKE, mpu.gyro_wake_rate);
}
void MPLSensor::setGyroRate(uint64_t delay)
{
    inv_write_sysfs(delay, INV_THREE_AXIS_GYRO, mpu.calib_gyro_rate);
}
void MPLSensor::setGyroRateWake(uint64_t delay)
{
    inv_write_sysfs(delay, INV_THREE_AXIS_GYRO_WAKE, mpu.calib_gyro_wake_rate);
}
void MPLSensor::setAccelRate(uint64_t delay)
{
    inv_write_sysfs(delay, INV_THREE_AXIS_ACCEL, mpu.accel_rate);
}
void MPLSensor::setAccelRateWake(uint64_t delay)
{
    inv_write_sysfs(delay, INV_THREE_AXIS_ACCEL_WAKE, mpu.accel_wake_rate);
}
void MPLSensor::set6AxesRate(uint64_t delay)
{
    inv_write_sysfs(delay, VIRTUAL_SENSOR_GYRO_6AXES_MASK, mpu.six_axis_q_rate);
}
void MPLSensor::set6AxesRateWake(uint64_t delay)
{
    inv_write_sysfs(delay, VIRTUAL_SENSOR_GYRO_6AXES_MASK_WAKE, mpu.six_axis_q_wake_rate);
}
void MPLSensor::set9AxesRate(uint64_t delay)
{
    inv_write_sysfs(delay, VIRTUAL_SENSOR_9AXES_MASK, mpu.nine_axis_q_rate);
}
void MPLSensor::set9AxesRateWake(uint64_t delay)
{
    inv_write_sysfs(delay, VIRTUAL_SENSOR_9AXES_MASK_WAKE, mpu.nine_axis_q_wake_rate);
}
void MPLSensor::set6AxesMagRate(uint64_t delay)
{
    inv_write_sysfs(delay, VIRTUAL_SENSOR_MAG_6AXES_MASK, mpu.in_geomag_rate);
}
void MPLSensor::set6AxesMagRateWake(uint64_t delay)
{
    inv_write_sysfs(delay, VIRTUAL_SENSOR_MAG_6AXES_MASK_WAKE, mpu.in_geomag_wake_rate);
}

void MPLSensor::setRawMagRate(uint64_t delay)
{
    char rate[MAX_SYSFS_NAME_LEN];

    memset(rate, 0, sizeof(rate));
    sprintf(rate, "in_magn_rate");
    mCompassSensor->setDelay(ID_RM, delay);

	mLocalSensorMask |= INV_THREE_AXIS_COMPASS;
    //inv_read_sysfs_engine(INV_THREE_AXIS_RAW_COMPASS, rate);

}
void MPLSensor::setRawMagRateWake(uint64_t delay)
{
    char rate[MAX_SYSFS_NAME_LEN];

    memset(rate, 0, sizeof(rate));
    sprintf(rate, "in_magn_wake_rate");
    mCompassSensor->setDelay(ID_RMW, delay);
	mLocalSensorMask |= INV_THREE_AXIS_COMPASS;
    //inv_read_sysfs_engine(INV_THREE_AXIS_RAW_COMPASS_WAKE, rate);
}
void MPLSensor::setMagRate(uint64_t delay)
{
    char rate[MAX_SYSFS_NAME_LEN];

    memset(rate, 0, sizeof(rate));
    sprintf(rate, "in_calib_magn_rate");

    mCompassSensor->setDelay(ID_M, delay);
	mLocalSensorMask |= INV_THREE_AXIS_COMPASS;
    //inv_read_sysfs_engine(INV_THREE_AXIS_COMPASS, rate);
}
void MPLSensor::setMagRateWake(uint64_t delay)
{
    char rate[MAX_SYSFS_NAME_LEN];

    memset(rate, 0, sizeof(rate));
    sprintf(rate, "in_calib_magn_rate");

    mCompassSensor->setDelay(ID_MW, delay);
	mLocalSensorMask |= INV_THREE_AXIS_COMPASS;
    //inv_read_sysfs_engine(INV_THREE_AXIS_COMPASS_WAKE, rate);
}
#ifdef IIO_LIGHT_SENSOR
void MPLSensor::setPressureRate(uint64_t delay)
{
    char rate[MAX_SYSFS_NAME_LEN];

    memset(rate, 0, sizeof(rate));
    sprintf(rate, "in_pressure_rate");

    mPressureSensor->setDelay(ID_PS, delay);
    inv_read_sysfs_engine(INV_ONE_AXIS_PRESSURE, rate);

}
void MPLSensor::setPressureRateWake(uint64_t delay)
{
    char rate[MAX_SYSFS_NAME_LEN];

    memset(rate, 0, sizeof(rate));
    sprintf(rate, "in_pressure_wake_rate");

    mPressureSensor->setDelay(ID_PSW, delay);
    inv_read_sysfs_engine(INV_ONE_AXIS_PRESSURE_WAKE, rate);

}
void MPLSensor::setLightRate(uint64_t delay)
{
    char rate[MAX_SYSFS_NAME_LEN];

    memset(rate, 0, sizeof(rate));
    sprintf(rate, "in_light_rate");
    mLightSensor->setDelay(ID_L, delay);
    inv_read_sysfs_engine(INV_ONE_AXIS_LIGHT, rate);

}
void MPLSensor::setLightRateWake(uint64_t delay)
{
    char rate[MAX_SYSFS_NAME_LEN];

    memset(rate, 0, sizeof(rate));
    sprintf(rate, "in_light_rate");

    mLightSensor->setDelay(ID_LW, delay);
    inv_read_sysfs_engine(INV_ONE_AXIS_LIGHT_WAKE, rate);
}
#endif

/* called when batch and hw sensor enabled*/
int MPLSensor::enablePedIndicator(int en)
{
    VFUNC_LOG;

    int res = 0;

    if (!(mCalibrationMode & dmp))
        return res;

    if (en) {
        if (!(mFeatureActiveMask & INV_DMP_PED_QUATERNION)) {
            //Disable DMP Pedometer Interrupt
            LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
                    0, mpu.pedometer_int_on, getTimestamp());
            if (write_sysfs_int(mpu.pedometer_int_on, 0) < 0) {
                LOGE("HAL:ERR can't enable Android Pedometer Interrupt");
                res = -1;
                return res;
            }

            LOGV_IF(ENG_VERBOSE, "HAL:Enabling ped standalone");
            // disable accel FIFO
            if (!((mLocalSensorMask & mMasterSensorMask) & INV_THREE_AXIS_ACCEL)) {
                res = turnOffAccelFifo();
                if (res < 0)
                    return res;
            }
        }
    } else {
        //Disable Accel if no sensor needs it
        if (!(mFeatureActiveMask & DMP_FEATURE_MASK)
                && (!(mLocalSensorMask & mMasterSensorMask
                        & INV_THREE_AXIS_ACCEL))) {
            LOGV_IF(ENG_VERBOSE, "HAL:Disabling ped standalone");
            LOGV_IF(EXTRA_VERBOSE, "mLocalSensorMask=0x%llx", mLocalSensorMask);
        }
    }

    LOGV_IF(ENG_VERBOSE, "HAL:Toggling step indicator to %d", en);
    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            en, mpu.step_indicator_on, getTimestamp());
    if (write_sysfs_int(mpu.step_indicator_on, en) < 0) {
        res = -1;
        LOGE("HAL:ERR can't write to DMP step_indicator_on");
    }

    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            en, mpu.step_detector_on, getTimestamp());
    if (write_sysfs_int(mpu.step_detector_on, en) < 0) {
        LOGE("HAL:ERR can't write DMP step_detector_on");
        res = -1;
    }
    return res;
}

int MPLSensor::checkPedStandaloneBatched(void)
{
    VFUNC_LOG;
    int res = 0;

    if ((mFeatureActiveMask & INV_DMP_PEDOMETER) &&
            (mBatchEnabled & (1LL << StepDetector))) {
        res = 1;
    } else
        res = 0;

    LOGV_IF(ENG_VERBOSE, "HAL:checkPedStandaloneBatched=%d", res);
    return res;
}

int MPLSensor::checkPedStandaloneEnabled(void)
{
    VFUNC_LOG;
    return ((mFeatureActiveMask & INV_DMP_PED_STANDALONE)? 1:0);
}

/* This feature is only used in batch mode */
/* Stand-alone Step Detector */
int MPLSensor::enablePedStandalone(int en)
{
    VFUNC_LOG;

    if (!(mCalibrationMode & dmp))
        return 0;

    if (!en) {
        enablePedStandaloneData(0);
        mFeatureActiveMask &= ~INV_DMP_PED_STANDALONE;
        if (mFeatureActiveMask == 0) {
        } else if(mFeatureActiveMask & INV_DMP_PEDOMETER) {
            //Re-enable DMP Pedometer Interrupt
            LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
                    1, mpu.pedometer_int_on, getTimestamp());
            if (write_sysfs_int(mpu.pedometer_int_on, 1) < 0) {
                LOGE("HAL:ERR can't enable Android Pedometer Interrupt");
                return (-1);
            }
        }
        LOGV_IF(ENG_VERBOSE, "HAL:Ped Standalone disabled");
    } else {
        if (enablePedStandaloneData(1) < 0 || onDmp(1) < 0) {
            LOGE("HAL:ERR can't enable Ped Standalone");
        } else {
            mFeatureActiveMask |= INV_DMP_PED_STANDALONE;
            //Disable DMP Pedometer Interrupt
            LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
                    0, mpu.pedometer_int_on, getTimestamp());
            if (write_sysfs_int(mpu.pedometer_int_on, 0) < 0) {
                LOGE("HAL:ERR can't disable Android Pedometer Interrupt");
                return (-1);
            }
            LOGV_IF(ENG_VERBOSE, "HAL:Ped Standalone enabled");
        }
    }
    return 0;
}

int MPLSensor:: enablePedStandaloneData(int en)
{
    VFUNC_LOG;

    int res = 0;

    // Set DMP Ped standalone
    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            en, mpu.step_detector_on, getTimestamp());
    if (write_sysfs_int(mpu.step_detector_on, en) < 0) {
        LOGE("HAL:ERR can't write DMP step_detector_on");
        res = -1;
    }

    // Set DMP Step indicator
    // In ICM, step_indicator_on is only for batch + other sensors.
    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            en, mpu.step_indicator_on, getTimestamp());
    if (write_sysfs_int(mpu.step_indicator_on, en) < 0) {
        LOGE("HAL:ERR can't write DMP step_indicator_on");
        res = -1;
    }

    return res;
}

int MPLSensor::checkPedQuatEnabled(void)
{
    VFUNC_LOG;
    return ((mFeatureActiveMask & INV_DMP_PED_QUATERNION)? 1:0);
}

/* This feature is only used in batch mode */
/* Step Detector && Game Rotation Vector */
int MPLSensor::enablePedQuaternion(int en)
{
    VFUNC_LOG;

    if (!en) {
        enablePedQuaternionData(0);
        mFeatureActiveMask &= ~INV_DMP_PED_QUATERNION;
        if (mFeatureActiveMask == 0) {
        } else if(mFeatureActiveMask & INV_DMP_PEDOMETER) {
            //Re-enable DMP Pedometer Interrupt
            LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
                    1, mpu.pedometer_int_on, getTimestamp());
            if (write_sysfs_int(mpu.pedometer_int_on, 1) < 0) {
                LOGE("HAL:ERR can't enable Android Pedometer Interrupt");
                return (-1);
            }
        }
        LOGV_IF(ENG_VERBOSE, "HAL:Ped Quat disabled");
    } else {
        if (enablePedQuaternionData(1) < 0 || onDmp(1) < 0) {
            LOGE("HAL:ERR can't enable Ped Quaternion");
        } else {
            mFeatureActiveMask |= INV_DMP_PED_QUATERNION;
            //Disable DMP Pedometer Interrupt
            LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
                    0, mpu.pedometer_int_on, getTimestamp());
            if (write_sysfs_int(mpu.pedometer_int_on, 0) < 0) {
                LOGE("HAL:ERR can't disable Android Pedometer Interrupt");
                return (-1);
            }

            LOGV_IF(ENG_VERBOSE, "HAL:Ped Quat enabled");
        }
    }
    return 0;
}

int MPLSensor::enablePedQuaternionData(int en)
{
    VFUNC_LOG;

    int res = 0;

    // Enable DMP quaternion
    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            en, mpu.ped_q_on, getTimestamp());
    if (write_sysfs_int(mpu.ped_q_on, en) < 0) {
        LOGE("HAL:ERR can't write DMP ped_q_on");
        res = -1;   //Indicate an err
    }

    return res;
}

int MPLSensor::setPedQuaternionRate(int64_t wanted)
{
    VFUNC_LOG;
    int res = 0;

    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            int(NS_PER_SECOND_FLOAT / wanted), mpu.ped_q_rate,
            getTimestamp());
    res = write_sysfs_int(mpu.ped_q_rate, NS_PER_SECOND_FLOAT / wanted);
    LOGV_IF(PROCESS_VERBOSE,
            "HAL:DMP ped quaternion rate %.2f Hz", NS_PER_SECOND_FLOAT / wanted);

    return res;
}

int MPLSensor::check6AxisQuatEnabled(void)
{
    VFUNC_LOG;
    return ((mFeatureActiveMask & INV_DMP_6AXIS_QUATERNION)? 1:0);
}

/* This is used for batch mode only */
/* GRV is batched but not along with ped */
int MPLSensor::enable6AxisQuaternion(int en)
{
    VFUNC_LOG;

    if (mCalibrationMode & mpl_quat)
        return 0;

    if (!en) {
        enable6AxisQuaternionData(0);
        mFeatureActiveMask &= ~INV_DMP_6AXIS_QUATERNION;
        if (mFeatureActiveMask == 0) {
        }
        LOGV_IF(ENG_VERBOSE, "HAL:6 Axis Quat disabled");
    } else {
        if (enable6AxisQuaternionData(1) < 0 || onDmp(1) < 0) {
            LOGE("HAL:ERR can't enable 6 Axis Quaternion");
        } else {
            mFeatureActiveMask |= INV_DMP_6AXIS_QUATERNION;
            LOGV_IF(PROCESS_VERBOSE, "HAL:6 Axis Quat enabled");
        }
    }
    return 0;
}

int MPLSensor::enable6AxisQuaternionWake(int en)
{
    VFUNC_LOG;

    if (mCalibrationMode & mpl_quat)
        return 0;

    if (!en) {
        enable6AxisQuaternionDataWake(0);
        mFeatureActiveMask &= ~INV_DMP_6AXIS_QUATERNION;
        if (mFeatureActiveMask == 0) {
        }
        LOGV_IF(ENG_VERBOSE, "HAL:6 Axis Quat disabled");
    } else {
        if (enable6AxisQuaternionDataWake(1) < 0 || onDmp(1) < 0) {
            LOGE("HAL:ERR can't enable 6 Axis Quaternion");
        } else {
            mFeatureActiveMask |= INV_DMP_6AXIS_QUATERNION;
            LOGV_IF(PROCESS_VERBOSE, "HAL:6 Axis Quat enabled");
        }
    }
    return 0;
}


int MPLSensor::enable6AxisQuaternionData(int en)
{
    VFUNC_LOG;

    int res = 0;

    bool quat_type = mFeatureActiveMask & INV_DMP_9AXIS_QUATERNION;

    // Enable DMP quaternion
    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            en, mpu.six_axis_q_on, getTimestamp());
    if (write_sysfs_int(mpu.six_axis_q_on, en) < 0) {
        LOGE("HAL:ERR can't write DMP six_axis_q_on");
        res = -1;
    }

    if (!en) {
        LOGV_IF(EXTRA_VERBOSE, "HAL:DMP six axis quaternion data was turned off");
        inv_quaternion_sensor_was_turned_off();
    }
    return res;
}

int MPLSensor::enable6AxisQuaternionDataWake(int en)
{
    VFUNC_LOG;

    int res = 0;

    bool quat_type = mFeatureActiveMask & (INV_DMP_9AXIS_QUATERNION_WAKE);

    // Enable DMP quaternion
    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            en, mpu.six_axis_q_wake_on, getTimestamp());
    if (write_sysfs_int(mpu.six_axis_q_wake_on, en) < 0) {
        LOGE("HAL:ERR can't write DMP six_axis_q_wake_on");
        res = -1;
    }

    if (!en) {
        LOGV_IF(EXTRA_VERBOSE, "HAL:DMP six axis quaternion wake data was turned off");
        inv_quaternion_sensor_was_turned_off();
    }

    return res;
}

int MPLSensor::set6AxisQuaternionRate(int64_t wanted)
{
    VFUNC_LOG;
    int res = 0;

    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            int(NS_PER_SECOND_FLOAT / wanted), mpu.six_axis_q_rate,
            getTimestamp());
    res = write_sysfs_int(mpu.six_axis_q_rate, NS_PER_SECOND_FLOAT / wanted);
    res = write_sysfs_int(mpu.nine_axis_q_rate, NS_PER_SECOND_FLOAT / wanted);
    res = write_sysfs_int(mpu.in_geomag_rate, NS_PER_SECOND_FLOAT / wanted);

    LOGV_IF(PROCESS_VERBOSE,
            "HAL:DMP six axis rate %.2f Hz", NS_PER_SECOND_FLOAT / wanted);

    return res;
}

int MPLSensor::set6AxisQuaternionWakeRate(int64_t wanted)
{
    VFUNC_LOG;
    int res = 0;

    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            int(NS_PER_SECOND_FLOAT / wanted), mpu.six_axis_q_wake_rate,
            getTimestamp());
    res = write_sysfs_int(mpu.six_axis_q_wake_rate, NS_PER_SECOND_FLOAT / wanted);
    res = write_sysfs_int(mpu.nine_axis_q_wake_rate, NS_PER_SECOND_FLOAT / wanted);
    res = write_sysfs_int(mpu.in_geomag_wake_rate, NS_PER_SECOND_FLOAT / wanted);

    LOGV_IF(PROCESS_VERBOSE,
            "HAL:DMP six axis wake rate %.2f Hz", NS_PER_SECOND_FLOAT / wanted);

    return res;
}

int MPLSensor::check9AxisQuaternion(void)
{
    VFUNC_LOG;
    return ((mFeatureActiveMask & INV_DMP_9AXIS_QUATERNION)? 1:0);
}

int MPLSensor::check9AxisQuaternionWake(void)
{
    VFUNC_LOG;
    return ((mFeatureActiveMask & (INV_DMP_9AXIS_QUATERNION_WAKE))? 1:0);
}

int MPLSensor::enable9AxisQuaternion(int en)
{
    VFUNC_LOG;

    int res = 0;
    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            en, mpu.nine_axis_q_on, getTimestamp());
    if (write_sysfs_int(mpu.nine_axis_q_on, en) < 0) {
        LOGE("HAL:ERR can't write DMP nine_axis_q_on");
        res = -1;
    }
    if (res < 0 || onDmp(1) < 0) {
        LOGE("HAL:ERR can't enable 9 Axis Quaternion res=%d",res);
    }
    if (!en) {
        mFeatureActiveMask &= ~INV_DMP_9AXIS_QUATERNION;
        LOGV_IF(ENG_VERBOSE, "HAL:9 Axis Quat disabled");
    } else {
        mFeatureActiveMask |= INV_DMP_9AXIS_QUATERNION;
        LOGV_IF(PROCESS_VERBOSE, "HAL:9 Axis Quat enabled");
    }
    return 0;
}

int MPLSensor::enable9AxisQuaternionWake(int en)
{
    VFUNC_LOG;

    int res = 0;
    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            en, mpu.nine_axis_q_wake_on, getTimestamp());
    if (write_sysfs_int(mpu.nine_axis_q_wake_on, en) < 0) {
        LOGE("HAL:ERR can't write DMP nine_axis_q_wake_on");
        res = -1;
    }
    if (res < 0 || onDmp(1) < 0) {
        LOGE("HAL:ERR can't enable 9 Axis Quaternion wake res=%d",res);
    }
    if (!en) {
        mFeatureActiveMask &= ~(INV_DMP_9AXIS_QUATERNION_WAKE);
        LOGV_IF(ENG_VERBOSE, "HAL:9 Axis Quat wake disabled");
    } else {
        mFeatureActiveMask |= (INV_DMP_9AXIS_QUATERNION_WAKE);
        LOGV_IF(PROCESS_VERBOSE, "HAL:9 Axis Quat wake enabled");
    }
    return 0;
}


int MPLSensor::enableCompass6AxisQuaternion(int en)
{
    VFUNC_LOG;

    int res = 0;
    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            en, mpu.in_geomag_enable, getTimestamp());
    if (write_sysfs_int(mpu.in_geomag_enable, en) < 0) {
        LOGE("HAL:ERR can't write geomag enable");
        res = -1;
    }
    if (res < 0 || onDmp(1) < 0) {
        LOGE("HAL:ERR can't enable geomag enable res=%d",res);
    }
    return 0;
}

int MPLSensor::enableCompass6AxisQuaternionWake(int en)
{
    VFUNC_LOG;

    int res = 0;
    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            en, mpu.in_geomag_wake_enable, getTimestamp());
    if (write_sysfs_int(mpu.in_geomag_wake_enable, en) < 0) {
        LOGE("HAL:ERR can't write geomag wake enable");
        res = -1;
    }
    if (res < 0 || onDmp(1) < 0) {
        LOGE("HAL:ERR can't enable geomag wake enable res=%d",res);
    }
    return 0;
}


/* this is for batch  mode only */
int MPLSensor::checkLPQRateSupported(void)
{
    VFUNC_LOG;

    return 1;
}

int MPLSensor::checkLPQuaternion(void)
{
    VFUNC_LOG;
    return ((mFeatureActiveMask & INV_DMP_6AXIS_QUATERNION)? 1:0);
}

int MPLSensor::enableLPQuaternion(int en)
{
    VFUNC_LOG;

    if (mCalibrationMode & mpl_quat)
        return 0;

    if (!en) {
        enableQuaternionData(0);
        mFeatureActiveMask &= ~INV_DMP_6AXIS_QUATERNION;
        LOGV_IF(ENG_VERBOSE, "HAL:LP Quat disabled");
    } else {
        if (enableQuaternionData(1) < 0 || onDmp(1) < 0) {
            LOGE("HAL:ERR can't enable LP Quaternion");
        } else {
            mFeatureActiveMask |= INV_DMP_6AXIS_QUATERNION;
            LOGV_IF(ENG_VERBOSE, "HAL:LP Quat enabled");
        }
    }
    return 0;
}

int MPLSensor::checkLPQuaternionWake(void)
{
    VFUNC_LOG;
    return ((mFeatureActiveMask & (INV_DMP_6AXIS_QUATERNION_WAKE))? 1:0);
}

int MPLSensor::enableLPQuaternionWake(int en)
{
    VFUNC_LOG;

    if (mCalibrationMode & mpl_quat)
        return 0;

    if (!en) {
        enableQuaternionDataWake(0);
        mFeatureActiveMask &= ~(INV_DMP_6AXIS_QUATERNION_WAKE);
        LOGV_IF(ENG_VERBOSE, "HAL:LP Quat wake disabled");
    } else {
        if (enableQuaternionDataWake(1) < 0 || onDmp(1) < 0) {
            LOGE("HAL:ERR can't enable LP Quaternion");
        } else {
            mFeatureActiveMask |= (INV_DMP_6AXIS_QUATERNION_WAKE);
            LOGV_IF(ENG_VERBOSE, "HAL:LP Quat wake  enabled");
        }
    }
    return 0;
}

int MPLSensor::enableQuaternionData(int en)
{
    VFUNC_LOG;

    int res = 0;
    bool quat_type = mFeatureActiveMask & INV_DMP_9AXIS_QUATERNION;

    // Enable DMP quaternion
    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            en, mpu.six_axis_q_on, getTimestamp());
    if (write_sysfs_int(mpu.six_axis_q_on, en) < 0) {
        LOGE("HAL:ERR can't write DMP three_axis_q__on");
        res = -1;
    }

    if (!en) {
        LOGV_IF(ENG_VERBOSE, "HAL:DMP quaternion data was turned off");
        inv_quaternion_sensor_was_turned_off();
    } else {
        LOGV_IF(ENG_VERBOSE, "HAL:Enabling three axis quat");
    }

    return res;
}

int MPLSensor::enableQuaternionDataWake(int en)
{
    VFUNC_LOG;

    int res = 0;
    bool quat_type = mFeatureActiveMask & (INV_DMP_9AXIS_QUATERNION_WAKE);

    // Enable DMP quaternion
    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            en, mpu.six_axis_q_wake_on, getTimestamp());
    if (write_sysfs_int(mpu.six_axis_q_wake_on, en) < 0) {
        LOGE("HAL:ERR can't write DMP three_axis_q__on");
        res = -1;
    }

    if (!en) {
        LOGV_IF(ENG_VERBOSE, "HAL:DMP quaternion data was turned off");
        inv_quaternion_sensor_was_turned_off();
    } else {
        LOGV_IF(ENG_VERBOSE, "HAL:Enabling three axis quat");
    }

    return res;
}

int MPLSensor::setQuaternionRate(int64_t wanted)
{
    VFUNC_LOG;
    int res = 0;

    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            int(NS_PER_SECOND_FLOAT / wanted), mpu.six_axis_q_rate,
            getTimestamp());
    res = write_sysfs_int(mpu.six_axis_q_rate, NS_PER_SECOND_FLOAT / wanted);
    res = write_sysfs_int(mpu.nine_axis_q_rate, NS_PER_SECOND_FLOAT / wanted);
    res = write_sysfs_int(mpu.in_geomag_rate, NS_PER_SECOND_FLOAT / wanted);
    LOGV_IF(PROCESS_VERBOSE,
            "HAL:DMP six axis rate %.2f Hz", NS_PER_SECOND_FLOAT / wanted);

    return res;
}

int MPLSensor::getDmpTiltFd()
{
    VFUNC_LOG;

    LOGV_IF(EXTRA_VERBOSE, "getDmpTiltFd returning %d",
            dmp_tilt_fd);
    return dmp_tilt_fd;
}

int MPLSensor::readDmpTiltEvents(sensors_event_t* data, int count)
{
    VFUNC_LOG;

    int res = 0;
    char dummy[4];
    int tilt;
    FILE *fp;
    int sensors = mEnabled;
    int numEventReceived = 0;
    int update = 0;

    /* Technically this step is not necessary for now  */
    /* In the future, we may have meaningful values */
    fp = fopen(mpu.event_tilt, "r");
    if (fp == NULL) {
        LOGE("HAL:cannot open event_tilt");
        return 0;
    } else {
        if (fscanf(fp, "%d\n", &tilt) < 0 || fclose(fp) < 0) {
            LOGE("HAL:cannot read event_tilt");
        }
    }

    if(mDmpTiltEnabled && count > 0) {
        /* By implementation, tilt is disabled once an event is triggered */
        sensors_event_t temp;

        /* Handles return event */
        LOGI("HAL: TILT detected");
        int update = tiltHandler(&mTiltEvents);
        if (update && count > 0) {
            *data++ = mTiltEvents;
            count--;
            numEventReceived++;
        }
    }

    // read dummy data per driver's request
    read(dmp_tilt_fd, dummy, 4);

    return numEventReceived;
}

int MPLSensor::enableDmpTilt(int en)
{
    VFUNC_LOG;

    int res = 0;

    //Enable DMP Tilt Function
    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            en, mpu.tilt_on, getTimestamp());
    if (write_sysfs_int(mpu.tilt_on, en) < 0) {
        LOGE("HAL:ERR can't enable Tilt");
        res = -1;
    }
    return res;
}

int MPLSensor::getDmpPickupFd()
{
    VFUNC_LOG;

    LOGV_IF(EXTRA_VERBOSE, "getDmpPickupFd returning %d",
            dmp_pickup_fd);
    return dmp_pickup_fd;
}

int MPLSensor::readDmpPickupEvents(sensors_event_t* data, int count)
{
    VFUNC_LOG;

    int res = 0;
    char dummy[4];
    int pickup;
    FILE *fp;
    int sensors = mEnabled;
    int numEventReceived = 0;
    int update = 0;

    /* Technically this step is not necessary for now  */
    /* In the future, we may have meaningful values */
    fp = fopen(mpu.event_pickup, "r");
    if (fp == NULL) {
        LOGE("HAL:cannot open event_pickup");
        return 0;
    } else {
        if (fscanf(fp, "%d\n", &pickup) < 0 || fclose(fp) < 0) {
            LOGE("HAL:cannot read event_pickup");
        }
    }

    if(mDmpPickupEnabled && count > 0) {
        /* By implementation, tilt is disabled once an event is triggered */
        sensors_event_t temp;

        /* Handles return event */
        LOGI("HAL: PICKUP detected");
        int update = pickupHandler(&mPickupEvents);
        if (update && count > 0) {
            *data++ = mPickupEvents;
            count--;
            numEventReceived++;

            /* reset state */
            mDmpPickupEnabled = 0;
            mFeatureActiveMask &= ~INV_DMP_PICKUP;
            mEnabled &= ~(1LL << Pickup);
            mEnabledCached = mEnabled;

            /* Auto diable this sensor - One shot sensor */
            enableDmpPickup(0);
        }
    }

    // read dummy data per driver's request
    read(dmp_pickup_fd, dummy, 4);

    return numEventReceived;
}

#if 0
int MPLSensor::enableDmpEis(int en)
{
    VFUNC_LOG;

    int res = 0;

    //Enable EIS Function
    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            en, mpu.eis_on, getTimestamp());
    if (write_sysfs_int(mpu.eis_on, en) < 0) {
        LOGE("HAL:ERR can't enable Eis");
        res = -1;
    }

    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            en, mpu.eis_data_on, getTimestamp());
    if (write_sysfs_int(mpu.eis_data_on, en) < 0) {
        LOGE("HAL:ERR can't enable Eis");
        res = -1;
}

    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            225, mpu.eis_rate, getTimestamp());
    if (write_sysfs_int(mpu.eis_rate, 225) < 0) {
        LOGE("HAL:ERR can't set  Eis rate");
        res = -1;
    }

    return res;
}

int MPLSensor::enableDmpEisAuthentication(int en)
{
    VFUNC_LOG;

    int res = 0;

    //Enable EIS Authentication

    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            en, mpu.dmp_init, getTimestamp());
    if (write_sysfs_int(mpu.dmp_init, en) < 0) {
        LOGE("HAL:ERR can't enable Eis");
        res = -1;
}

    return res;
}
#endif

int MPLSensor::enableDmpPickup(int en)
{
    VFUNC_LOG;

    int res = 0;

    //Toggle Pick up detection
    if(en) {
        LOGV_IF(ENG_VERBOSE, "HAL:Enabling Pickup gesture");
        LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
                1, mpu.pickup_on, getTimestamp());
        if (write_sysfs_int(mpu.pickup_on, 1) < 0) {
            LOGE("HAL:ERR can't write DMP pickup_on");
            res = -1;
        }
        mFeatureActiveMask |= INV_DMP_PICKUP;
    }
    else {
        LOGV_IF(ENG_VERBOSE, "HAL:Disabling Pickup gesture");
        LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
                0, mpu.pickup_on, getTimestamp());
        if (write_sysfs_int(mpu.pickup_on, 0) < 0) {
            LOGE("HAL:ERR write DMP smd_enable");
        }
        mFeatureActiveMask &= ~INV_DMP_PICKUP;
    }

    if ((res = computeAndSetDmpState()) < 0)
        return res;

    /*
    VFUNC_LOG;

    int res = 0;

    //Enable DMP Pick up Function
    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            en, mpu.pickup_on, getTimestamp());
    if (write_sysfs_int(mpu.pickup_on, en) < 0) {
        LOGE("HAL:ERR can't enable Pick up");
        res = -1;
    }
    */
    return res;
}

int MPLSensor::enableDmpPedometer(int en, int pedometerMode)
{
    VFUNC_LOG;
    int res = 0;
    uint64_t enabled_sensors = mEnabled;

    adapter_gesture_reset_timestamp();

    if (pedometerMode == 1) {
        //Enable DMP Pedometer Step Detect Function
        LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
                en, mpu.pedometer_on, getTimestamp());
        if (write_sysfs_int(mpu.pedometer_on, en) < 0) {
            LOGE("HAL:ERR can't enable Android Pedometer");
            res = -1;
            return res;
        }
        mFeatureActiveMask |= (int32_t(en) & INV_DMP_PEDOMETER);
    } else if (pedometerMode == 0) {
        //Enable DMP Pedometer Step Counter Function
        LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
                en, mpu.pedometer_counter_on, getTimestamp());
        if (write_sysfs_int(mpu.pedometer_counter_on, en) < 0) {
            LOGE("HAL:ERR can't enable Android Pedometer");
            res = -1;
            return res;
        }
        mFeatureActiveMask |= (int32_t(en) & INV_DMP_PEDOMETER_STEP);
    } else if (pedometerMode == 2) {
        //Enable DMP Pedometer Step Counter Function
        LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
                en, mpu.pedometer_counter_wake_on, getTimestamp());
        if (write_sysfs_int(mpu.pedometer_counter_wake_on, en) < 0) {
            LOGE("HAL:ERR can't enable Android Pedometer");
            res = -1;
            return res;
        }
        mFeatureActiveMask |= (int32_t(en) & INV_DMP_PEDOMETER_STEP);
    } else {
        //Enable DMP Pedometer Step Detect Function
        LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
                en, mpu.pedometer_wake_on, getTimestamp());
        if (write_sysfs_int(mpu.pedometer_wake_on, en) < 0) {
            LOGE("HAL:ERR can't enable Android Pedometer");
            res = -1;
            return res;
        }
        mFeatureActiveMask |= (int32_t(en) & INV_DMP_PEDOMETER);
    }

    if ((res = computeAndSetDmpState()) < 0)
        return res;

    if (!mBatchEnabled && (resetDataRates() < 0))
        return res;

    return res;
}

int MPLSensor::enableGyroEngine(int en)
{
    VFUNC_LOG;

    int res = 0;
    (void) en;

#if DEBUG_DRIVER
    /* need to also turn on/off the master enable */
    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            en, mpu.gyro_enable, getTimestamp());
    write_sysfs_int(mpu.gyro_enable, en);
#endif
    return res;
}

int MPLSensor::enableRawGyro(int en)
{
    VFUNC_LOG;

    int res = 0;

    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            en, mpu.gyro_fifo_enable, getTimestamp());
    res += write_sysfs_int(mpu.gyro_fifo_enable, en);

    adapter_gyro_reset_timestamp();

    if (!en) {
        LOGV_IF(EXTRA_VERBOSE, "HAL:MPL:inv_gyro_was_turned_off");
        inv_gyro_was_turned_off();
        resetGyroDecimatorStates();
    }

    return res;
}

int MPLSensor::enableRawGyroWake(int en)
{
    VFUNC_LOG;

    int res = 0;

    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            en, mpu.gyro_wake_fifo_enable, getTimestamp());
    res += write_sysfs_int(mpu.gyro_wake_fifo_enable, en);

    adapter_gyro_reset_timestamp();

    if (!en) {
        LOGV_IF(EXTRA_VERBOSE, "HAL:MPL:inv_gyro_was_turned_off");
        inv_gyro_was_turned_off();
        resetGyroDecimatorStates();
    }

    return res;
}

int MPLSensor::enableGyro(int en)
{
    VFUNC_LOG;

    int res = 0;

    if (en == 0)
        resetGyroDecimatorStates();

    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            en, mpu.calib_gyro_enable, getTimestamp());
    res = write_sysfs_int(mpu.calib_gyro_enable, en);

    adapter_gyro_reset_timestamp();

    if (!en) {
        LOGV_IF(EXTRA_VERBOSE, "HAL:MPL:inv_gyro_was_turned_off");
        inv_gyro_was_turned_off();
    }

    return res;
}

int MPLSensor::enableGyroWake(int en)
{
    VFUNC_LOG;

    int res = 0;

    if (en == 0)
        resetGyroDecimatorStates();

    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            en, mpu.calib_gyro_wake_enable, getTimestamp());
    res = write_sysfs_int(mpu.calib_gyro_wake_enable, en);

    adapter_gyro_reset_timestamp();

    if (!en) {
        LOGV_IF(EXTRA_VERBOSE, "HAL:MPL:inv_gyro_was_turned_off");
        inv_gyro_was_turned_off();
    }

    return res;
}

int MPLSensor::enableAccel(int en)
{
    VFUNC_LOG;

    int res = 0;

    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            en, mpu.accel_fifo_enable, getTimestamp());
    res += write_sysfs_int(mpu.accel_fifo_enable, en);

    adapter_accel_reset_timestamp();

    if (!en) {
        LOGV_IF(EXTRA_VERBOSE, "HAL:MPL:inv_accel_was_turned_off");
        inv_accel_was_turned_off();
        resetAccelDecimatorStates();
    }

    return res;
}

int MPLSensor::enableAccelWake(int en)
{
    VFUNC_LOG;

    int res = 0;

    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            en, mpu.accel_wake_fifo_enable, getTimestamp());
    res += write_sysfs_int(mpu.accel_wake_fifo_enable, en);

    adapter_accel_reset_timestamp();

    if (!en) {
        LOGV_IF(EXTRA_VERBOSE, "HAL:MPL:inv_accel_was_turned_off");
        inv_accel_was_turned_off();
        resetAccelDecimatorStates();
    }

    return res;
}

int MPLSensor::enableRawCompass(int en)
{
    VFUNC_LOG;

    int res = 0;
    /* TODO: handle ID_RM if third party compass cal is used */
    res = mCompassSensor->enable(ID_RM, en);

    adapter_compass_reset_timestamp();

    if (en == 0 || res != 0) {
        LOGV_IF(EXTRA_VERBOSE, "HAL:MPL:inv_compass_was_turned_off %d", res);
        inv_compass_was_turned_off();
        resetMagDecimatorStates();
    }

    return res;
}

int MPLSensor::enableRawCompassWake(int en)
{
    VFUNC_LOG;

    int res = 0;
    /* TODO: handle ID_RM if third party compass cal is used */
    res = mCompassSensor->enable(ID_RMW, en);

    adapter_compass_reset_timestamp();

    if (en == 0 || res != 0) {
        LOGV_IF(EXTRA_VERBOSE, "HAL:MPL:inv_compass_was_turned_off %d", res);
        inv_compass_was_turned_off();
        resetMagDecimatorStates();
    }

    return res;
}

int MPLSensor::enableCompass(int en)
{
    VFUNC_LOG;

    int res = 0;

    if (en == 0)
        resetMagDecimatorStates();

    /* TODO: handle ID_RM if third party compass cal is used */
    res = mCompassSensor->enable(ID_M, en);

    adapter_compass_reset_timestamp();

    if (en == 0 || res != 0) {
        LOGV_IF(EXTRA_VERBOSE, "HAL:MPL:inv_compass_was_turned_off %d", res);
        inv_compass_was_turned_off();
    }

    return res;
}

int MPLSensor::enableCompassWake(int en)
{
    VFUNC_LOG;

    int res = 0;

    if (en == 0)
        resetMagDecimatorStates();

    /* TODO: handle ID_RM if third party compass cal is used */
    res = mCompassSensor->enable(ID_MW, en);

    adapter_compass_reset_timestamp();

    if (en == 0 || res != 0) {
        LOGV_IF(EXTRA_VERBOSE, "HAL:MPL:inv_compass_was_turned_off %d", res);
        inv_compass_was_turned_off();
    }

    return res;
}
#ifdef IIO_LIGHT_SENSOR
int MPLSensor::enablePressure(int en)
{
    VFUNC_LOG;

    int res = 0;

    if (mPressureSensor)
        res = mPressureSensor->enable(ID_PS, en);

    return res;
}

int MPLSensor::enablePressureWake(int en)
{
    VFUNC_LOG;

    int res = 0;

    if (mPressureSensor)
        res = mPressureSensor->enable(ID_PSW, en);

    return res;
}

int MPLSensor::enableLight(int en)
{
    VFUNC_LOG;

    int res = 0;

    if (mLightSensor)
        res = mLightSensor->enable(ID_L, en);

    return res;
}

int MPLSensor::enableLightWake(int en)
{
    VFUNC_LOG;

    int res = 0;

    if (mLightSensor)
        res = mLightSensor->enable(ID_LW, en);

    return res;
}

int MPLSensor::enableProximity(int en)
{
    VFUNC_LOG;

    int res = 0;

    if (mLightSensor)
        res = mLightSensor->enable(ID_PR, en);

    return res;
}

int MPLSensor::enableProximityWake(int en)
{
    VFUNC_LOG;

    int res = 0;

    if (mLightSensor)
        res = mLightSensor->enable(ID_PRW, en);

    return res;
}
#endif

/* use this function for initialization */
int MPLSensor::enableBatch(int64_t timeout)
{
    VFUNC_LOG;

    int res = 0;

    res = write_sysfs_int(mpu.batchmode_timeout, timeout);
    if (timeout == 0) {
        res = write_sysfs_int(mpu.six_axis_q_on, 0);
        res = write_sysfs_int(mpu.nine_axis_q_on, 0);
        res = write_sysfs_int(mpu.ped_q_on, 0);
        res = write_sysfs_int(mpu.step_detector_on, 0);
        res = write_sysfs_int(mpu.step_indicator_on, 0);
    }

    if (timeout == 0) {
        LOGV_IF(EXTRA_VERBOSE, "HAL:MPL:batchmode timeout is zero");
    }

    return res;
}

int MPLSensor::enableDmpCalibration(int en)
{
    VFUNC_LOG;

    int res = 0;
    int fd = 0;

    /* enable gyro calibration */
    fd = open(mpu.gyro_cal_enable, O_RDWR);
    if (fd < 0) {
        LOGE("HAL:ERR couldn't open gyro_cal_enable node");
    } else {
        LOGV_IF(ENG_VERBOSE,
                "HAL:gyro_cal_enable opened : %d", fd);
        if(write_attribute_sensor(fd, en) < 0) {
            LOGE("HAL:Error writing to gyro_cal_enable");
            return 1;
        }
        LOGV_IF(ENG_VERBOSE, "HAL:Invensense DMP gyro cal enabled");
    }

    /* enable accel calibration */
    fd = open(mpu.accel_cal_enable, O_RDWR);
    if (fd < 0) {
        LOGE("HAL:ERR couldn't open accel_cal_enable node");
    } else {
        LOGV_IF(ENG_VERBOSE,
                "HAL:accel_cal_enable opened : %d", fd);
        if(write_attribute_sensor(fd, en) < 0) {
            LOGE("HAL:Error writing to accel_cal_enable");
            return 1;
        }
        LOGV_IF(ENG_VERBOSE, "HAL:Invensense DMP accel cal enabled");
    }

    /* enable compass calibration */
    fd = open(mpu.compass_cal_enable, O_RDWR);
    if (fd < 0) {
        LOGE("HAL:ERR couldn't open compass_cal_enable node");
    } else {
        LOGV_IF(ENG_VERBOSE,
                "HAL:compass_cal_enable opened : %d", fd);
        if(write_attribute_sensor(fd, en) < 0) {
            LOGE("HAL:Error writing to compass_cal_enable");
            return 1;
        }
        LOGV_IF(ENG_VERBOSE, "HAL:Invensense DMP compass cal enabled");
    }
    return res;
}

#if 0
int MPLSensor::enableOisSensor(int en)
{
    VFUNC_LOG;
    int res = 0;
    int fd = 0;

    /* enable Ois sensor */
    fd = open(mpu.ois_enable, O_RDWR);
    if (fd < 0) {
        LOGE("HAL:ERR couldn't open ois_enable node");
    } else {
        LOGV_IF(ENG_VERBOSE,
                "HAL:ois_enable opened : %d", fd);
        if(write_attribute_sensor(fd, en) < 0) {
            LOGE("HAL:Error writing to ois_enable");
            return 1;
        }
        LOGV_IF(ENG_VERBOSE, "HAL:OIS is %s", en ? "enabled": "disabled");
    }

    return res;
}
#endif

int MPLSensor::enable(int32_t handle, int en)
{
    VFUNC_LOG;

    android::String8 sname;
    int what = -1, err = 0;
    int batchMode = 0;
    uint32_t i;

    getHandle(handle, what, sname);
    if (what < 0) {
        LOGV_IF(ENG_VERBOSE, "HAL:can't find handle %d",handle);
        return -EINVAL;
    }
    if (!en)
        mBatchEnabled &= ~(1LL << handle);

    LOGV_IF(ENG_VERBOSE, "HAL:handle111 = %d en = %d", handle, en);

    switch (handle) {
#if 0
        case ID_EISGY:
            enableDmpEis(en);
            break;
        case ID_EISAUTHENTICATION:
            enableDmpEisAuthentication(en);
            break;
#endif
        case ID_PICK:
            LOGV_IF(PROCESS_VERBOSE, "HAL:enable - sensor %s (handle %d) %s -> %s",
                    sname.string(), handle,
                    (mDmpPickupEnabled? "en": "dis"),
                    (en? "en" : "dis"));
            enableDmpPickup(en);
            mDmpPickupEnabled = !!en;
            break;
        case ID_T:
            LOGV_IF(PROCESS_VERBOSE, "HAL:enable - sensor %s (handle %d) %s -> %s",
                    sname.string(), handle,
                    (mDmpTiltEnabled? "en": "dis"),
                    (en? "en" : "dis"));
            enableDmpTilt(en);
            mDmpTiltEnabled = !!en;
            break;
        case ID_SC:
            LOGV_IF(PROCESS_VERBOSE, "HAL:enable - sensor %s (handle %d) %s -> %s",
                    sname.string(), handle,
                    (mDmpStepCountEnabled? "en": "dis"),
                    (en? "en" : "dis"));
            mDmpStepCountEnabled = !!en;
            mEnabled &= ~(1LL << what);
            mEnabled |= (uint64_t(en) << what);
            mEnabledCached = mEnabled;
            enableDmpPedometer(en, 0);
            if (en)
                mEnabledTime[StepCounter] = android::elapsedRealtimeNano();
            else {
                mEnabledTime[StepCounter] = 0;
                mLastStepCount = -1;
            }
            return 0;
        case ID_P:
            LOGV_IF(PROCESS_VERBOSE, "HAL:enable - sensor %s (handle %d) %s -> %s",
                    sname.string(), handle,
                    (mDmpPedometerEnabled? "en": "dis"),
                    (en? "en" : "dis"));
            mDmpPedometerEnabled = !!en;
            mEnabled &= ~(1LL << what);
            mEnabled |= (uint64_t(en) << what);
            mEnabledCached = mEnabled;
            enableDmpPedometer(en, 1);
            batchMode = computeBatchSensorMask(mEnabled, mBatchEnabled);
            /* skip setBatch if there is no need to */
            if(((int)mOldBatchEnabledMask != batchMode) || batchMode) {
                setBatch(batchMode);
            }
            mOldBatchEnabledMask = batchMode;
            if (en)
                mEnabledTime[StepDetector] = android::elapsedRealtimeNano();
            else
                mEnabledTime[StepDetector] = 0;
            return 0;
        case ID_SM:
            LOGV_IF(PROCESS_VERBOSE, "HAL:enable - sensor %s (handle %d) %s -> %s",
                    sname.string(), handle,
                    (mDmpSignificantMotionEnabled? "en": "dis"),
                    (en? "en" : "dis"));
            enableDmpSignificantMotion(en);
            mDmpSignificantMotionEnabled = !!en;
            if (en)
                mEnabledTime[SignificantMotion] = android::elapsedRealtimeNano();
            else
                mEnabledTime[SignificantMotion] = 0;
            return 0;
        case ID_SCW:
            LOGV_IF(PROCESS_VERBOSE, "HAL:enable - sensor %s (handle %d) %s -> %s",
                    sname.string(), handle,
                    (mDmpStepCountEnabled? "en": "dis"),
                    (en? "en" : "dis"));
            mDmpStepCountEnabled = !!en;
            mEnabled &= ~(1LL << what);
            mEnabled |= (uint64_t(en) << what);
            mEnabledCached = mEnabled;
            enableDmpPedometer(en, 2);
            if (en)
                mEnabledTime[StepCounter_Wake] = android::elapsedRealtimeNano();
            else {
                mEnabledTime[StepCounter_Wake] = 0;
                mLastStepCountWake = -1;
            }
            return 0;
        case ID_PW:
            LOGV_IF(PROCESS_VERBOSE, "HAL:enable - sensor %s (handle %d) %s -> %s",
                    sname.string(), handle,
                    (mDmpPedometerEnabled? "en": "dis"),
                    (en? "en" : "dis"));
            mDmpPedometerEnabled = !!en;
            mEnabled &= ~(1LL << what);
            mEnabled |= (uint64_t(en) << what);
            mEnabledCached = mEnabled;
            enableDmpPedometer(en, 3);
            batchMode = computeBatchSensorMask(mEnabled, mBatchEnabled);
            /* skip setBatch if there is no need to */
            if(((int)mOldBatchEnabledMask != batchMode) || batchMode) {
                setBatch(batchMode);
            }
            mOldBatchEnabledMask = batchMode;
            if (en)
                mEnabledTime[StepDetector_Wake] = android::elapsedRealtimeNano();
            else
                mEnabledTime[StepDetector_Wake] = 0;
            return 0;
        case ID_L:
            mLightInitEvent = (en) ? true : false;
            break;
        case ID_PR:
            mProxiInitEvent = (en) ? true : false;
            break;
        case ID_LW:
            mLightWakeInitEvent = (en) ? true : false;
            break;
        case ID_PRW:
            mProxiWakeInitEvent = (en) ? true : false;
            break;
	case ID_ARC:
	    what = AccelerometerRaw;
	    sname = "Raw Accelerometer";
	    break;
	case ID_GRC:
	    what = GyroRaw;
	    sname = "Raw Gyroscope";
	    break;
	case ID_MRC:
	    what = MagneticFieldRaw;
	    sname = "Raw Magneric Field";
	    break;
#if 0
    case ID_OIS:
	    what = OisSensor;
	    sname = "Ois Sensor";
        LOGV_IF(PROCESS_VERBOSE, "HAL:enable - sensor %s (handle %d) %s -> %s",
                sname.string(), handle,
                (mOisEnabled? "en": "dis"),
                (en? "en" : "dis"));
        enableOisSensor(en);
        mOisEnabled = !!en;
		return 0;
#endif
	default:
            break;
    }

    uint64_t newState = en ? 1 : 0;
    uint64_t sen_mask;

    LOGV_IF(PROCESS_VERBOSE, "HAL:enable - sensor %s (handle %d) %s -> %s",
            sname.string(), handle,
            ((mEnabled & (1LL << what)) ? "en" : "dis"),
            (((newState) << what) ? "en" : "dis"));
    LOGV_IF(ENG_VERBOSE,
            "HAL:%s sensor state change what=%d", sname.string(), what);

    if (((newState) << what) != (mEnabled & (1LL << what))) {
        uint64_t sensor_type;
        uint64_t flags = newState;
        uint64_t lastEnabled = mEnabled, changed = 0;

        if (en)
            mEnabledCached = mEnabled;
        mEnabled &= ~(1LL << what);
        mEnabled |= (uint64_t(flags) << what);
        if (!en)
            mEnabledCached = mEnabled;

        LOGV_IF(ENG_VERBOSE, "HAL:flags = %lld", flags);
        sen_mask = mLocalSensorMask & mMasterSensorMask;
        mSensorMaskCached = sen_mask;
	changed = mLocalSensorMask;
        computeLocalSensorMask(mEnabled);
        LOGV_IF(ENG_VERBOSE, "HAL:enable : mEnabled = 0x%llx lastEnabled = 0x%llx", mEnabled, lastEnabled);
        LOGV_IF(ENG_VERBOSE, "HAL:enable : local mask = 0x%llx master mask = 0x%llx", mLocalSensorMask, mMasterSensorMask);
        sen_mask = mLocalSensorMask & mMasterSensorMask;
        mSensorMask = sen_mask;
        LOGV_IF(ENG_VERBOSE, "HAL:sen_mask= 0x%0llx beforechanged=0x%llx", sen_mask, changed);
	changed ^= mLocalSensorMask;
        LOGV_IF(ENG_VERBOSE, "HAL:changed = 0x%0llx", changed);
        mEnabledCached = mEnabled;
        enableSensors(sen_mask, flags, changed);

        if (en)
            mEnabledTime[what] = android::elapsedRealtimeNano();
        else
            mEnabledTime[what] = 0;
    }

    return err;
}

void MPLSensor::computeLocalSensorMask(uint64_t enabled_sensors)
{
    VFUNC_LOG;
    int i;

    mLocalSensorMask = 0;
    for (i = 0; i < ID_NUMBER; i++) {
	if (enabled_sensors & (1LL << i)) {
		mLocalSensorMask |= mCurrentSensorMask[i].sensorMask;
	}
    }
    LOGV_IF(ENG_VERBOSE, "mpl calibration final mLocalSensorMask=%llx, enabledsensor=%llx", mLocalSensorMask, enabled_sensors);
}

int MPLSensor::enableSensors(uint64_t sensors, int en, uint64_t changed)
{
    VFUNC_LOG;

    inv_error_t res = -1;
    int on = 1;
    int off = 0;
    int cal_stored = 0;
    bool MagEnable, MagEnable_Wake;
    struct sysfs_mask *localSensorMask;
    uint32_t i;

    if (isLowPowerQuatEnabled() ||
		(changed & (INV_THREE_AXIS_RAW_GYRO |
			    INV_THREE_AXIS_GYRO |
			    INV_THREE_AXIS_ACCEL |
			    INV_THREE_AXIS_RAW_COMPASS |
			    INV_THREE_AXIS_COMPASS |
			    INV_ONE_AXIS_PRESSURE |
			    INV_ONE_AXIS_LIGHT |
			    INV_THREE_AXIS_RAW_GYRO_WAKE |
			    INV_THREE_AXIS_GYRO_WAKE |
			    INV_THREE_AXIS_ACCEL_WAKE |
			    INV_THREE_AXIS_RAW_COMPASS_WAKE |
			    INV_THREE_AXIS_COMPASS_WAKE |
			    INV_ONE_AXIS_PRESSURE_WAKE |
			    INV_ONE_AXIS_LIGHT_WAKE))) {
        if (!en && (!(sensors& INV_THREE_AXIS_GYRO) ||
                    !(sensors & INV_THREE_AXIS_ACCEL) ||
                    !(sensors & INV_THREE_AXIS_COMPASS))) {
            getDmpGyroBias();
            getDmpAccelBias();
            getDmpCompassBias();
            inv_store_calibration();
            cal_stored = 1;
            LOGV_IF(PROCESS_VERBOSE, "HAL:Cal file updated");
        }
    }

    LOGV_IF(ENG_VERBOSE, "HAL:enableSensors - sensors: 0x%0llx changed: 0x%0llx",
						       sensors, changed);
    for (i = 0; i < mNumSysfs; i++) {
	localSensorMask = &mSysfsMask[i];
	if (localSensorMask->sensorMask && (changed & localSensorMask->sensorMask) && localSensorMask->enable) {
		(this->*(localSensorMask->enable))(!!(sensors & localSensorMask->sensorMask));
		if (localSensorMask->second_enable) {
			(this->*(localSensorMask->second_enable))(!!(sensors & localSensorMask->sensorMask));
		}
	}
    }
    for (i = 0; i < mNumSysfs; i++) {
	localSensorMask = &mSysfsMask[i];
	if (localSensorMask->sensorMask && localSensorMask->enable) {
		localSensorMask->en = (!!(sensors & localSensorMask->sensorMask));
	}
    }

    /* to batch or not to batch */
    int batchMode = computeBatchSensorMask(mEnabled, mBatchEnabled);

    /* skip setBatch if there is no need to */
    if(((int)mOldBatchEnabledMask != batchMode) || batchMode || changed) {
        setBatch(batchMode);
    }
    mOldBatchEnabledMask = batchMode;

    if (!batchMode && (resetDataRates() < 0)) {
        LOGE("HAL:ERR can't reset output rate back to original setting");
    }

    if(mFeatureActiveMask || sensors) {
        onDmp(1);
    }

    return res;
}

/* check if batch mode should be turned on or not */
int MPLSensor::computeBatchSensorMask(uint64_t enableSensors, uint64_t tempBatchSensor)
{
    VFUNC_LOG;
    int batchMode = 1;
    uint32_t i;
    mFeatureActiveMask &= ~INV_DMP_BATCH_MODE;

    LOGV_IF(ENG_VERBOSE,
            "HAL:computeBatchSensorMask: enableSensors=%lld tempBatchSensor=%lld",
            enableSensors, tempBatchSensor);

    // handle initialization case
    if (enableSensors == 0 && tempBatchSensor == 0)
        return 0;

    // check for possible continuous data mode
    for(i = 0; i < ID_NUMBER; i++) {
        // if any one of the hardware sensor is in continuous data mode then turn off batch mode.
        if ((enableSensors & (1LL << i)) && !(tempBatchSensor & (1LL << i))) {
            LOGV_IF(ENG_VERBOSE, "HAL:computeBatchSensorMask: "
                    "hardware sensor on continuous mode:%d", i);
            return 0;
        }
        if ((enableSensors & (1LL << i)) && (tempBatchSensor & (1LL << i))) {
            LOGV_IF(ENG_VERBOSE,
                    "HAL:computeBatchSensorMask: hardware sensor is batch:%d",
                    i);
            // if hardware sensor is batched, check if virtual sensor is also batched
            if ((enableSensors & (1LL << GameRotationVector))
                    && !(tempBatchSensor & (1LL << GameRotationVector))) {
                LOGV_IF(ENG_VERBOSE,
                        "HAL:computeBatchSensorMask: but virtual sensor is not:%d",
                        i);
                return 0;
            }
        }
    }

    // if virtual sensors are on but not batched, turn off batch mode.
    for(i = Orientation; i <= GeomagneticRotationVector; i++) {
        if ((enableSensors & (1LL << i)) && !(tempBatchSensor & (1LL << i))) {
            LOGV_IF(ENG_VERBOSE, "HAL:computeBatchSensorMask: "
                    "composite sensor on continuous mode:%d", i);
            return 0;
        }
    }

    if ((mFeatureActiveMask & INV_DMP_PEDOMETER) && !(tempBatchSensor & (1LL << StepDetector))) {
        LOGV("HAL:computeBatchSensorMask: step detector on continuous mode.");
        return 0;
    }

    mFeatureActiveMask |= INV_DMP_BATCH_MODE;
    LOGV_IF(EXTRA_VERBOSE,
            "HAL:computeBatchSensorMask: batchMode=%d, mBatchEnabled=%0x",
            batchMode, tempBatchSensor);
    return (batchMode && tempBatchSensor);
}

/* This function is called by enable() */
int MPLSensor::setBatch(int en)
{
    VFUNC_LOG;

    int res = 0;
    int64_t wanted = NS_PER_SECOND;
    int64_t timeout = 0;
    int timeoutInMs = 0;
    int featureMask = computeBatchDataOutput();

    if (isLowPowerQuatEnabled()) {
#if 0
        /* step detector is enabled and batch mode is standalone */
        if (en && (mFeatureActiveMask & INV_DMP_PEDOMETER) &&
                (featureMask & INV_DMP_PED_STANDALONE)) {
            LOGV_IF(ENG_VERBOSE, "setBatch: ID_P only = 0x%x", mBatchEnabled);
            enablePedStandalone(1);
        } else {
            enablePedStandalone(0);
        }
#endif

        /* step detector and GRV are enabled and batch mode is ped q */
        if (en && (mFeatureActiveMask & INV_DMP_PEDOMETER) &&
                (mEnabled & (1LL << GameRotationVector)) &&
                (featureMask & INV_DMP_PED_QUATERNION)) {
            LOGV_IF(ENG_VERBOSE, "setBatch: ID_P and GRV or ALL = 0x%x", mBatchEnabled);
            LOGV_IF(ENG_VERBOSE, "setBatch: ID_P is enabled for batching, "
                    "PED quat will be automatically enabled");
            enableLPQuaternion(0);
            enablePedQuaternion(1);
        } else if (!(featureMask & INV_DMP_PED_STANDALONE)){
            enablePedQuaternion(0);
        } else {
            enablePedQuaternion(0);
        }

#if 0
        /* step detector and hardware sensors enabled */
        if (en && (featureMask & INV_DMP_PED_INDICATOR) &&
                ((mEnabled) ||
                 (mFeatureActiveMask & INV_DMP_PED_STANDALONE))) {
            enablePedIndicator(1);
        } else {
            enablePedIndicator(0);
        }
#endif

        /* GRV is enabled and batch mode is 6axis q */
        if (en && (mEnabled & (1LL << GameRotationVector)) &&
                (featureMask & INV_DMP_6AXIS_QUATERNION)) {
            LOGV_IF(ENG_VERBOSE, "setBatch: GRV = 0x%x", mBatchEnabled);
            enableLPQuaternion(0);
            enable6AxisQuaternion(1);
        } else if (!(featureMask & INV_DMP_PED_QUATERNION)){
            LOGV_IF(ENG_VERBOSE, "setBatch: Toggle back to normal 6 axis");
            if (mEnabled & (1LL << GameRotationVector)) {
                enableLPQuaternion(checkLPQRateSupported());
            }
        } else if (!(mEnabled & (1LL << GameRotationVector))){
            enable6AxisQuaternion(0);
        }
    }

    writeBatchTimeout(en);

    if (en) {
        // enable DMP
        res = onDmp(1);
        if (res < 0) {
            return res;
        }

        // set batch rates
        if (setBatchDataRates() < 0) {
            LOGE("HAL:ERR can't set batch data rates");
        }
    } else {
        if (mFeatureActiveMask == 0) {
            /* reset sensor rate */
            if (resetDataRates() < 0) {
                LOGE("HAL:ERR can't reset output rate back to original setting");
            }
        }
    }

    return res;
}

int MPLSensor::writeBatchTimeout(int en)
{
    VFUNC_LOG;

    int64_t timeoutInMs = 0;
    if (en) {
        /* take the minimum batchmode timeout */
        int64_t timeout = 100000000000LL;
        int64_t ns = 0;
        for (uint32_t i = 0; i < ID_NUMBER; i++) {
            LOGV_IF(ENG_VERBOSE, "mFeatureActiveMask=0x%016llx, mEnabled=0x%01x, mBatchEnabled=0x%x",
                            mFeatureActiveMask, mEnabled, mBatchEnabled);
            if (((mEnabled & (1LL << i)) && (mBatchEnabled & (1LL << i))) ||
                    (checkPedStandaloneBatched() && (i == StepDetector))) {
                LOGV_IF(ENG_VERBOSE, "sensor=%d, timeout=%lld", i, mBatchTimeouts[i]);
                ns = mBatchTimeouts[i];
                timeout = (ns < timeout) ? ns : timeout;
            }
        }
        /* Convert ns to millisecond */
        timeoutInMs = timeout / 1000000;
    } else {
        timeoutInMs = 0;
    }

    LOGV_IF(PROCESS_VERBOSE,
            "HAL: batch timeout set to %lld ms", timeoutInMs);

    if(mBatchTimeoutInMs != timeoutInMs) {
        /* write required timeout to sysfs */
        LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %lld > %s (%lld)",
                timeoutInMs, mpu.batchmode_timeout, getTimestamp());
        if (write_sysfs_int(mpu.batchmode_timeout, timeoutInMs) < 0) {
            LOGE("HAL:ERR can't write batchmode_timeout");
        }
    }
    /* remember last timeout value */
    mBatchTimeoutInMs = timeoutInMs;

    return 0;
}

/* Store calibration file */
void MPLSensor::storeCalibration(void)
{
    VFUNC_LOG;

    if(mHaveGoodMpuCal == true
            || mAccelAccuracy >= 2
            || mCompassAccuracy >= 3) {
        int res = inv_store_calibration();
        if (res) {
            LOGE("HAL:Cannot store calibration on file");
        } else {
            LOGV_IF(PROCESS_VERBOSE, "HAL:Cal file updated");
        }
    }
}

#if defined INV_LIBMOTION
#include "MPLBuilder.h"
#pragma message ("libmotion library must be present in system")
#else
#include "MPLBuilderInt.h"
#endif
extern "C" {
    struct mpl_sensor_data sensor_data;

    int64_t gyro_timestamp0;
    int64_t rawgyro_timestamp0;
    int64_t accel_timestamp0;
    int64_t compass_timestamp0;
    int64_t rawcompass_timestamp0;
    int64_t gameRV_timestamp0;
    int64_t geomagRV_timestamp0;
    int64_t RV_timestamp0;
    int64_t orientation_timestamp0;
    int64_t gravity_timestamp0;
    int64_t LA_timestamp0;

    /* timestamp for custom sensor */
    int64_t accelraw_timestamp0;
    int64_t gyroraw_timestamp0;
    int64_t compassraw_timestamp0;


    int64_t gyro_wake_timestamp0;
    int64_t rawgyro_wake_timestamp0;
    int64_t accel_wake_timestamp0;
    int64_t compass_wake_timestamp0;
    int64_t rawcompass_wake_timestamp0;
    int64_t gameRV_wake_timestamp0;
    int64_t geomagRV_wake_timestamp0;
    int64_t RV_wake_timestamp0;
    int64_t orientation_wake_timestamp0;
    int64_t gravity_wake_timestamp0;
    int64_t LA_wake_timestamp0;
    int CalibrationMode;

    typedef int (*get_sensor_data_func)(float *values, int8_t *accuracy, int64_t *timestamp, int mode);

    int output_control(uint64_t mask, sensors_event_t* s, int64_t sensor_time, get_sensor_data_func func,
		int sensor_value_size, float *sensor_values, int8_t *sensor_accuracy,
		int64_t *sensor_timestamp, int mode, int64_t *timestamp0, int64_t Rate0, int sensor_id)
    {
        float values[6];
        int8_t accuracy;
        int64_t timestamp = -1;
        static int cnt0;
        int update=0;
        int checkBatchEnable;

        checkBatchEnable = ((mask & INV_DMP_BATCH_MODE)? 1:0);

        if (checkBatchEnable) {
            update = func(sensor_values, sensor_accuracy, sensor_timestamp, mode);
            if (!sensor_time || !(s->timestamp > sensor_time) || (*timestamp0 == *sensor_timestamp))
                update = 0;
            ALOGV_IF(DEBUG_OUTPUT_CONTROL, "output_cotnrol pass through");
            *timestamp0 = *sensor_timestamp;
            return update;
        }

        memset(values, 0, sensor_value_size*sizeof(float));
        update = func(&(values[0]), &accuracy, &timestamp, mode);
        ALOGV_IF(DEBUG_OUTPUT_CONTROL, ">>%d 0: (Rate0:%d) timestamp=%lld, prev timestamp0=%lld\n", sensor_id, (uint32_t)Rate0, timestamp, *timestamp0);

        if (sensor_id != -1)
            ALOGV_IF(DEBUG_OUTPUT_CONTROL, ">>%d 1: (Rate0:%d) timestamp=%lld, prev timestamp0=%lld\n", sensor_id, (uint32_t)Rate0, timestamp, *timestamp0);

        if ((timestamp != -1)&&(float)(timestamp - *timestamp0) > Rate0){ //report sensor data
            memcpy(sensor_values, values, sensor_value_size*sizeof(float));
            *sensor_accuracy = accuracy;
            *sensor_timestamp = timestamp;
            *timestamp0 = timestamp;
            cnt0 = 1;
            ALOGV_IF(DEBUG_OUTPUT_CONTROL, ">>%d 1.2 (log) - [%d] timestamp=%lld, prev timestamp0=%lld update=%d", sensor_id, cnt0, timestamp, (*timestamp0), update);
            if (sensor_id != -1)
                ALOGV_IF(DEBUG_OUTPUT_CONTROL, ">>%d 1.2 (report) - [%d] timestamp=%lld, prev timestamp0=%lld\n", sensor_id, cnt0, timestamp, (*timestamp0));
        }else{
            cnt0 ++;
            if (sensor_id != -1)
                ALOGV_IF(DEBUG_OUTPUT_CONTROL, ">>%d 1.1 (skip) - [%d] timestamp=%lld, prev timestamp0=%lld\n",  sensor_id, cnt0, timestamp, *timestamp0);
            else
                ALOGV_IF(DEBUG_OUTPUT_CONTROL, ">>%d 1.1 (skip) - [%d] timestamp=%lld, prev timestamp0=%lld\n",  sensor_id, cnt0, timestamp, *timestamp0);
            return 0 ;
        }

        if (sensor_id != -1)
            ALOGV_IF(0, ">>%d 2: (gyroRate0:%d) timestamp=%llu, gyro_timestamp0=%llu\n", sensor_id, (uint32_t)Rate0, (uint64_t)timestamp, (uint64_t)(*timestamp0));
	if (!sensor_time || !(s->timestamp > sensor_time))
		update = 0;
	else
		update = 1;

        return update;
    }
}
uint64_t MPLSensor::invCheckOutput(uint32_t sensor)
{
	int64_t tmp, orig_delay;
        uint32_t engine_delay = 0;

        LOGI_IF(HANDLER_DATA, "HAL:invCheckOutput - sensor=%d, sname=%s", sensor, mCurrentSensorMask[sensor].sname.string());
	if (sensor >= ID_NUMBER)
		return 0;
	if (*mCurrentSensorMask[sensor].engineRateAddr)
		engine_delay = *(mCurrentSensorMask[sensor].engineRateAddr);
        orig_delay = mDelays[sensor];

	if (engine_delay)
		tmp = orig_delay / engine_delay;
	else
		return 0;
	if (tmp)
		tmp = tmp * 2 -1;
        LOGI_IF(HANDLER_DATA, "HAL:invCheckOutput - orig delay=%lld, engine delay=%d, final=%lld",
				orig_delay, engine_delay, (uint64_t)tmp * engine_delay / 2);
	return tmp * engine_delay / 2;
}

static int cnt;

/*  these handlers transform mpl data into one of the Android sensor types */
int MPLSensor::gyroHandler(sensors_event_t* s)
{
    VHANDLER_LOG;
    int update;

    update = output_control(mFeatureActiveMask, s, mEnabledTime[Gyro], adapter_get_sensor_type_gyroscope, 3, s->gyro.v,
                &s->gyro.status, (int64_t *)&s->timestamp, 0, &gyro_timestamp0, invCheckOutput(Gyro), 0);
    if (update) {
	if (mGyroAccuracy > s->gyro.status)
		s->gyro.status = mGyroAccuracy;
    }
    LOGV_IF(HANDLER_DATA, "HAL:gyro data : %+f %+f %+f -- %lld - %d - %d",
            s->gyro.v[0], s->gyro.v[1], s->gyro.v[2], s->timestamp, s->gyro.status, update);
    return update;
}

int MPLSensor::gyrowHandler(sensors_event_t* s)
{
    VHANDLER_LOG;
    int update;

    update = output_control(mFeatureActiveMask, s, mEnabledTime[Gyro_Wake], adapter_get_sensor_type_gyroscope, 3, s->gyro.v,
                &s->gyro.status, (int64_t *)&s->timestamp, 1, &gyro_wake_timestamp0, invCheckOutput(Gyro_Wake), -1);
    if (update) {
        if (mGyroAccuracy > s->gyro.status)
            s->gyro.status = mGyroAccuracy;
    }
    LOGV_IF(HANDLER_DATA, "HAL:gyro wake data : %+f %+f %+f -- %lld - %d - %d",
            s->gyro.v[0], s->gyro.v[1], s->gyro.v[2], s->timestamp, s->gyro.status, update);
    return update;
}

int MPLSensor::rawGyroHandler(sensors_event_t* s)
{
    VHANDLER_LOG;
    int update;
    float cal_gyro[6];
    float raw_gyro[6];
    float bias[3];
    int8_t status;
    int64_t ts;

    memset(bias, 0, sizeof(bias));

    update = output_control(mFeatureActiveMask, s, mEnabledTime[RawGyro], adapter_get_sensor_type_gyroscope_raw, 3, raw_gyro,
            &s->gyro.status, (int64_t*)&s->timestamp, 0, &rawgyro_timestamp0, invCheckOutput(RawGyro), -1);

    if(update) {
        s->uncalibrated_gyro.uncalib[0] = raw_gyro[0];
        s->uncalibrated_gyro.uncalib[1] = raw_gyro[1];
        s->uncalibrated_gyro.uncalib[2] = raw_gyro[2];
    }

    adapter_get_sensor_type_gyroscope(cal_gyro, &status, &ts, 0);
    if (ts == s->timestamp && (mGyroAccuracy > 0 || (mCalibrationMode == mpl))) {
	    inv_calculate_bias(cal_gyro, s->uncalibrated_gyro.uncalib, bias);
	    memcpy(s->uncalibrated_gyro.bias, bias, sizeof(bias));
        LOGV_IF(HANDLER_DATA,"HAL:gyro bias data: %+f %+f %+f -- %lld - %d",
                s->uncalibrated_gyro.bias[0], s->uncalibrated_gyro.bias[1],
                s->uncalibrated_gyro.bias[2], s->timestamp, update);
    }

    s->gyro.status = SENSOR_STATUS_UNRELIABLE;
    LOGV_IF(HANDLER_DATA, "HAL:raw gyro data : %+f %+f %+f -- %lld - %d",
            s->uncalibrated_gyro.uncalib[0], s->uncalibrated_gyro.uncalib[1],
            s->uncalibrated_gyro.uncalib[2], s->timestamp, update);
    return update;
}

int MPLSensor::rawGyrowHandler(sensors_event_t* s)
{
    VHANDLER_LOG;
    int update;
    float cal_gyro[6];
    float raw_gyro[6];
    float bias[3];
    int8_t status;
    int64_t ts;


    update = output_control(mFeatureActiveMask, s, mEnabledTime[RawGyro_Wake],
		adapter_get_sensor_type_gyroscope_raw, 3, raw_gyro,
            &s->gyro.status, (int64_t*)&s->timestamp, 1, &rawgyro_wake_timestamp0, invCheckOutput(RawGyro_Wake), -1);

    memset(bias, 0, sizeof(bias));

    if(update) {
        s->uncalibrated_gyro.uncalib[0] = raw_gyro[0];
        s->uncalibrated_gyro.uncalib[1] = raw_gyro[1];
        s->uncalibrated_gyro.uncalib[2] = raw_gyro[2];
    }

    adapter_get_sensor_type_gyroscope(cal_gyro, &status, &ts, 0);
    if (ts == s->timestamp && (mGyroAccuracy > 0 || (mCalibrationMode == mpl))) {
	    inv_calculate_bias(cal_gyro, s->uncalibrated_gyro.uncalib, bias);
	    memcpy(s->uncalibrated_gyro.bias, bias, sizeof(bias));
	    LOGV_IF(HANDLER_DATA,"HAL:gyro bias data: %+f %+f %+f -- %lld - %d",
                s->uncalibrated_gyro.bias[0], s->uncalibrated_gyro.bias[1],
                s->uncalibrated_gyro.bias[2], s->timestamp, update);
    }
    s->gyro.status = SENSOR_STATUS_UNRELIABLE;
    LOGV_IF(HANDLER_DATA, "HAL:raw gyro wake data : %+f %+f %+f -- %lld - %d",
            s->uncalibrated_gyro.uncalib[0], s->uncalibrated_gyro.uncalib[1],
            s->uncalibrated_gyro.uncalib[2], s->timestamp, update);
    return update;
}

int MPLSensor::gyroRawHandler(sensors_event_t* s)
{
	VHANDLER_LOG;
	int update = 0;
	float cal_gyro[6] = {0,};
	float bias[3] = {0,};
	int8_t status;
	int64_t ts;

	update = output_control(mFeatureActiveMask, s, mEnabledTime[GyroRaw], adapter_get_sensor_type_gyroscope_raw_custom, 3, s->uncalibrated_gyro.uncalib,
			&s->gyro.status, (int64_t*)&s->timestamp, 0, &gyroraw_timestamp0, invCheckOutput(GyroRaw), -1);
	if (update) {
		if (mGyroAccuracy > s->gyro.status)
			s->gyro.status = mGyroAccuracy;
	}

	if (update == 1) {
		adapter_get_sensor_type_gyroscope(cal_gyro, &status, &ts, 0);
			inv_calculate_bias(cal_gyro, s->uncalibrated_gyro.uncalib, bias);
			memcpy(s->uncalibrated_gyro.bias, bias, sizeof(bias));
			LOGV_IF(HANDLER_DATA,"HAL:gyro bias data: %+f %+f %+f -- %lld - %d",
					s->uncalibrated_gyro.bias[0], s->uncalibrated_gyro.bias[1],
					s->uncalibrated_gyro.bias[2], s->timestamp, update);

	LOGV_IF(HANDLER_DATA, "HAL:raw gyro data (custom) : %+f %+f %+f -- %lld - %d",
			s->uncalibrated_gyro.uncalib[0], s->uncalibrated_gyro.uncalib[1],
			s->uncalibrated_gyro.uncalib[2], s->timestamp, update);
	}
	return update;
}


int MPLSensor::accelHandler(sensors_event_t* s)
{
    VHANDLER_LOG;
    int update;

    update = output_control(mFeatureActiveMask, s, mEnabledTime[Accelerometer], adapter_get_sensor_type_accelerometer, 3, s->acceleration.v,
            &s->acceleration.status, (int64_t*)&s->timestamp, 0, &accel_timestamp0, invCheckOutput(Accelerometer), -1);
    if (update) {
        if (mAccelAccuracy > s->acceleration.status)
            s->acceleration.status = mAccelAccuracy;
    }
    LOGV_IF(HANDLER_DATA, "HAL:accel data : %+f %+f %+f -- %lld - %d - %d",
            s->acceleration.v[0], s->acceleration.v[1], s->acceleration.v[2],
            s->timestamp, s->acceleration.status, update);
    return update;
}

int MPLSensor::accelwHandler(sensors_event_t* s)
{
    VHANDLER_LOG;
    int update;

    update = output_control(mFeatureActiveMask, s, mEnabledTime[Accelerometer_Wake],
			adapter_get_sensor_type_accelerometer, 3, s->acceleration.v,
            &s->acceleration.status, (int64_t*)&s->timestamp, 1, &accel_wake_timestamp0, invCheckOutput(Accelerometer_Wake), -1);

    if (update) {
        if (mAccelAccuracy > s->acceleration.status)
            s->acceleration.status = mAccelAccuracy;
    }
    LOGV_IF(HANDLER_DATA, "HAL:accel wake data : %+f %+f %+f -- %lld - %d - %d",
            s->acceleration.v[0], s->acceleration.v[1], s->acceleration.v[2],
            s->timestamp, s->acceleration.status, update);
    return update;
}

int MPLSensor::accelRawHandler(sensors_event_t* s)
{
    VHANDLER_LOG;
    int update;

    update = output_control(mFeatureActiveMask, s, mEnabledTime[AccelerometerRaw],
		    adapter_get_sensor_type_accelerometer_custom, 3, s->acceleration.v,
		    &s->acceleration.status, (int64_t*)&s->timestamp, 1, &accelraw_timestamp0, invCheckOutput(AccelerometerRaw), -1);

    if (update) {
	    if (mAccelAccuracy > s->acceleration.status)
		    s->acceleration.status = mAccelAccuracy;
    }
    LOGV_IF(HANDLER_DATA, "HAL:raw accel data : %+f %+f %+f -- %lld - %d",
		    s->acceleration.v[0], s->acceleration.v[1],
		    s->acceleration.v[2], s->timestamp, update);
    return update;
}


int MPLSensor::compassHandler(sensors_event_t* s)
{
    VHANDLER_LOG;
    int update;

    update = output_control(mFeatureActiveMask, s, mEnabledTime[MagneticField],
				adapter_get_sensor_type_magnetic_field, 3, s->magnetic.v,
            &s->magnetic.status, (int64_t*)&s->timestamp, 0, &compass_timestamp0,
			invCheckOutput(MagneticField), -1);

    if (update) {
        // If magnetic dist is detected, accuracy will be 2
        if (mCompassAccuracy == 1 && mCompassAccuracy > s->magnetic.status) {
            s->magnetic.status = mCompassAccuracy;
        } else {
            mCompassAccuracy = s->magnetic.status;
        }
    }

    LOGV_IF(HANDLER_DATA, "HAL:compass data: %+f %+f %+f -- %lld - %d - %d",
            s->magnetic.v[0], s->magnetic.v[1], s->magnetic.v[2],
            s->timestamp, s->magnetic.status, update);
    return update;
}

int MPLSensor::compasswHandler(sensors_event_t* s)
{
    VHANDLER_LOG;
    int update;

    update = output_control(mFeatureActiveMask, s, mEnabledTime[MagneticField_Wake], adapter_get_sensor_type_magnetic_field, 3, s->magnetic.v,
            &s->magnetic.status, (int64_t*)&s->timestamp, 1, &compass_wake_timestamp0,
		invCheckOutput(MagneticField_Wake), -1);

    if (update) {
        // If magnetic dist is detected, accuracy will be 2
        if (mCompassAccuracy == 1 && mCompassAccuracy > s->magnetic.status) {
            s->magnetic.status = mCompassAccuracy;
        } else {
            mCompassAccuracy = s->magnetic.status;
        }
    }

    LOGV_IF(HANDLER_DATA, "HAL:compass wake data: %+f %+f %+f -- %lld - %d - %d",
            s->magnetic.v[0], s->magnetic.v[1], s->magnetic.v[2],
            s->timestamp, s->magnetic.status, update);
    return update;
}

int MPLSensor::rawCompassHandler(sensors_event_t* s)
{
    VHANDLER_LOG;
    int update;
    int8_t status;
    int64_t ts;
    float bias[3];
    float cal_compass[6];
    float raw_compass[6];
    int convertedCompassBias[3];

    static long long int timestamp_compass_prev = 0;

    memset(bias, 0, sizeof(bias));
    //TODO: need to handle uncalib data and bias for 3rd party compass
    if(mCompassSensor->providesCalibration()) {
        update = mCompassSensor->readRawSample(s->uncalibrated_magnetic.uncalib, &s->timestamp);
    }
    else {
        update = output_control(mFeatureActiveMask, s, mEnabledTime[RawMagneticField],
			adapter_get_sensor_type_magnetic_field_raw, 3, raw_compass,
                    &s->magnetic.status, (int64_t*)&s->timestamp, 0, &rawcompass_timestamp0,
		invCheckOutput(RawMagneticField), -1);
    }
    if (timestamp_compass_prev ==  s->timestamp)
        return 0; // not updated raw compass

    timestamp_compass_prev = s->timestamp;

    if(update) {
        adapter_get_sensor_type_magnetic_field(cal_compass, &status, &ts, 0);
        if (mCompassAccuracy > 0) {
            inv_calculate_bias(cal_compass, raw_compass, bias);
        }
        memcpy(s->uncalibrated_magnetic.bias, bias, sizeof(bias));
        s->uncalibrated_magnetic.uncalib[0] = raw_compass[0];
        s->uncalibrated_magnetic.uncalib[1] = raw_compass[1];
        s->uncalibrated_magnetic.uncalib[2] = raw_compass[2];

        LOGV_IF(HANDLER_DATA, "HAL:compass bias data: %+f %+f %+f %d -- %lld - %d",
            s->uncalibrated_magnetic.bias[0], s->uncalibrated_magnetic.bias[1],
            s->uncalibrated_magnetic.bias[2], mCompassAccuracy, s->timestamp, update);
    }
    s->magnetic.status = SENSOR_STATUS_UNRELIABLE;
    LOGV_IF(HANDLER_DATA, "HAL:raw compass data: %+f %+f %+f %d -- %lld diff %lld - %d",
            s->uncalibrated_magnetic.uncalib[0], s->uncalibrated_magnetic.uncalib[1],
            s->uncalibrated_magnetic.uncalib[2], s->magnetic.status, s->timestamp, s->timestamp -
            timestamp_compass_prev, update);
    if (!mEnabledTime[RawMagneticField] || !(s->timestamp > mEnabledTime[RawMagneticField]))
        update = 0;

    return update;
}
#if 0
int MPLSensor::rawCompasswHandler(sensors_event_t* s)
{
    VHANDLER_LOG;
    int update;
    float bias[3];
    float cal_compass[6];

    memset(bias, 0, sizeof(bias));
    //TODO: need to handle uncalib data and bias for 3rd party compass
    if(mCompassSensor->providesCalibration()) {
        update = mCompassSensor->readRawSample(s->uncalibrated_magnetic.uncalib, &s->timestamp);
    } else {
        int8_t status;
        inv_time_t ts;
        update = adapter_get_sensor_type_magnetic_field_raw(cal_compass,
                &s->magnetic.status, (long long int*)&s->timestamp, 1);
        adapter_get_sensor_type_magnetic_field(cal_compass, &status, &ts, 1);
        if (mCompassAccuracy > 0) {
            inv_calculate_bias(cal_compass, s->uncalibrated_magnetic.uncalib, bias);
        }
    }
    if(update) {
        if (mCalibrationMode == dmp) {
            memcpy(s->uncalibrated_magnetic.bias, bias, sizeof(bias));
        } else {
            s->uncalibrated_magnetic.uncalib[0] = cal_compass[0];
            s->uncalibrated_magnetic.uncalib[1] = cal_compass[1];
            s->uncalibrated_magnetic.uncalib[2] = cal_compass[2];
            s->uncalibrated_magnetic.bias[0] = cal_compass[3];
            s->uncalibrated_magnetic.bias[1] = cal_compass[4];
            s->uncalibrated_magnetic.bias[2] = cal_compass[5];
        }
        LOGV_IF(HANDLER_DATA, "HAL:compass wake bias data: %+f %+f %+f %d -- %lld - %d",
                s->uncalibrated_magnetic.bias[0], s->uncalibrated_magnetic.bias[1],
                s->uncalibrated_magnetic.bias[2], mCompassAccuracy, s->timestamp, update);
    }
    s->magnetic.status = SENSOR_STATUS_UNRELIABLE;
    LOGV_IF(HANDLER_DATA, "HAL:compass wake raw data: %+f %+f %+f %d -- %lld - %d",
            s->uncalibrated_magnetic.uncalib[0], s->uncalibrated_magnetic.uncalib[1],
            s->uncalibrated_magnetic.uncalib[2], s->magnetic.status, s->timestamp, update);
    return update;
}
#else //
int MPLSensor::rawCompasswHandler(sensors_event_t* s)
{
    VHANDLER_LOG;
    int update;
    float bias[3];
    float cal_compass[6];
    float raw_compass[6];

    memset(bias, 0, sizeof(bias));
    //TODO: need to handle uncalib data and bias for 3rd party compass
    if(mCompassSensor->providesCalibration()) {
        update = mCompassSensor->readRawSample(s->uncalibrated_magnetic.uncalib, &s->timestamp);
    } else {
        int8_t status;
        int64_t ts;

        update = output_control(mFeatureActiveMask, s, mEnabledTime[RawMagneticField_Wake],
		   adapter_get_sensor_type_magnetic_field_raw, 3, raw_compass,
                   &s->magnetic.status, (int64_t*)&s->timestamp, 1, &rawcompass_wake_timestamp0,
		invCheckOutput(RawMagneticField_Wake), -1);
        adapter_get_sensor_type_magnetic_field(cal_compass, &status, &ts, 1);
        if (mCompassAccuracy > 0) {
            inv_calculate_bias(cal_compass, raw_compass, bias);
        }
    }
    if(update) {
        memcpy(s->uncalibrated_magnetic.bias, bias, sizeof(bias));

        s->uncalibrated_magnetic.uncalib[0] = raw_compass[0];
        s->uncalibrated_magnetic.uncalib[1] = raw_compass[1];
        s->uncalibrated_magnetic.uncalib[2] = raw_compass[2];

        LOGV_IF(HANDLER_DATA, "HAL:compass bias data: %+f %+f %+f %d -- %lld - %d",
                s->uncalibrated_magnetic.bias[0], s->uncalibrated_magnetic.bias[1],
                s->uncalibrated_magnetic.bias[2], mCompassAccuracy, s->timestamp, update);
    }
    s->magnetic.status = SENSOR_STATUS_UNRELIABLE;
    if (!mEnabledTime[RawMagneticField_Wake] || !(s->timestamp > mEnabledTime[RawMagneticField_Wake]))
        update = 0;

    LOGV_IF(HANDLER_DATA, "HAL:compass wake raw data: %+f %+f %+f %d -- %lld - %d",
            s->uncalibrated_magnetic.uncalib[0], s->uncalibrated_magnetic.uncalib[1],
            s->uncalibrated_magnetic.uncalib[2], s->magnetic.status, s->timestamp, update);
    return update;
}
#endif

int MPLSensor::compassRawHandler(sensors_event_t* s)
{
    VHANDLER_LOG;
    int update = 0;
    int8_t status;
    int64_t ts;
    float bias[3] = {0};
    float cal_compass[6];

    update = output_control(mFeatureActiveMask, s, mEnabledTime[MagneticFieldRaw],
		    adapter_get_sensor_type_magnetic_field_raw_custom, 3, s->uncalibrated_magnetic.uncalib,
		    &s->magnetic.status, (int64_t*)&s->timestamp, 0, &compassraw_timestamp0, invCheckOutput(MagneticFieldRaw), -1);


    if (update) {
	    adapter_get_sensor_type_magnetic_field(cal_compass, &status, &ts, 0);
	    if (mCompassAccuracy > 0) {
		    inv_calculate_bias(cal_compass, s->uncalibrated_magnetic.uncalib, bias);
	    }
	    memcpy(s->uncalibrated_magnetic.bias, bias, sizeof(bias));
	    LOGV_IF(HANDLER_DATA, "HAL:compass bias data: %+f %+f %+f %d -- %lld - %d",
			    s->uncalibrated_magnetic.bias[0], s->uncalibrated_magnetic.bias[1],
			    s->uncalibrated_magnetic.bias[2], mCompassAccuracy, s->timestamp, update);
    }
    s->magnetic.status = SENSOR_STATUS_UNRELIABLE;
    LOGV_IF(HANDLER_DATA, "HAL:compass raw data: %+f %+f %+f %d -- %lld - %d",
            s->uncalibrated_magnetic.uncalib[0], s->uncalibrated_magnetic.uncalib[1],
            s->uncalibrated_magnetic.uncalib[2], s->magnetic.status, s->timestamp, update);

    LOGV_IF(HANDLER_DATA, "HAL:raw compass data : %+f %+f %+f -- %lld",
		    s->magnetic.v[0], s->magnetic.v[1],
		    s->magnetic.v[2], s->timestamp);
    return update;
}


/*
   Rotation Vector handler.
NOTE: rotation vector does not have an accuracy or status by definition
 */
int MPLSensor::rvHandler(sensors_event_t* s)
{
    VHANDLER_LOG;
    int8_t status;
    int update;
    int64_t delayThreshold;

    delayThreshold = invCheckOutput(RotationVector);
    update = output_control(mFeatureActiveMask, s, mEnabledTime[RotationVector],
                adapter_get_sensor_type_rotation_vector, 5, s->data,
                &status, (int64_t*)&s->timestamp, 0, &RV_timestamp0, delayThreshold, -1);


#if 0
    int outQuat[4];
    inv_compute_scalar_part(mCached9AxisQuaternionData, outQuat);
    if (outQuat[0] >= 0) {
        s->data[0] = outQuat[1] * INV_TWO_POWER_NEG_30;
        s->data[1] = outQuat[2] * INV_TWO_POWER_NEG_30;
        s->data[2] = outQuat[3] * INV_TWO_POWER_NEG_30;
        s->data[3] = outQuat[0] * INV_TWO_POWER_NEG_30;
    } else {
        s->data[0] = -outQuat[1] * INV_TWO_POWER_NEG_30;
        s->data[1] = -outQuat[2] * INV_TWO_POWER_NEG_30;
        s->data[2] = -outQuat[3] * INV_TWO_POWER_NEG_30;
        s->data[3] = -outQuat[0] * INV_TWO_POWER_NEG_30;
    }
    float heading[4];
    quat_to_google_orientation(outQuat, heading);
    LOGV_IF(HANDLER_DATA, "rv heading=%f, %f, %f", heading[0], heading[1], heading[2]);
#endif

    if (!(mCalibrationMode & mpl_quat)) {
        /* convert Q29 radian (Upper 2 bytes) to float radian */
        s->data[4] = (float)ABS(mHeadingAccuracy) / (1 << 13);
    }
    s->orientation.status = mCompassAccuracy;
    update |= isCompassDisabled();
    LOGV_IF(HANDLER_DATA, "HAL:rv data: %+f %+f %+f %+f %+f %+lld - %d - %d",
            s->data[0], s->data[1], s->data[2], s->data[3], s->data[4],  s->timestamp, s->orientation.status,
            update);
#if DEBUG_TIME_PROFILE
    if (update)
        printTimeProfile(prevtime, 0, s->timestamp, 0);
#endif
    return update;
}

int MPLSensor::rvwHandler(sensors_event_t* s)
{
    VHANDLER_LOG;
    int8_t status;
    int update;
    int64_t delayThreshold;

    delayThreshold = invCheckOutput(RotationVector_Wake);
    update = output_control(mFeatureActiveMask, s, mEnabledTime[RotationVector_Wake],
                adapter_get_sensor_type_rotation_vector, 4, s->data,
                &status, (int64_t*)&s->timestamp, 1, &RV_wake_timestamp0, delayThreshold, -1);

#if 0
    int outQuat[4];
    inv_compute_scalar_part(mCached9AxisQuaternionData, outQuat);
    if (outQuat[0] >= 0) {
        s->data[0] = outQuat[1] * INV_TWO_POWER_NEG_30;
        s->data[1] = outQuat[2] * INV_TWO_POWER_NEG_30;
        s->data[2] = outQuat[3] * INV_TWO_POWER_NEG_30;
        s->data[3] = outQuat[0] * INV_TWO_POWER_NEG_30;
    } else {
        s->data[0] = -outQuat[1] * INV_TWO_POWER_NEG_30;
        s->data[1] = -outQuat[2] * INV_TWO_POWER_NEG_30;
        s->data[2] = -outQuat[3] * INV_TWO_POWER_NEG_30;
        s->data[3] = -outQuat[0] * INV_TWO_POWER_NEG_30;
    }
    float heading[4];
    quat_to_google_orientation(outQuat, heading);
    LOGV_IF(HANDLER_DATA, "rv wake heading=%f, %f, %f", heading[0], heading[1], heading[2]);
#endif

    if (!(mCalibrationMode & mpl_quat)) {
        /* convert Q29 radian (Upper 2 bytes) to float radian */
        s->data[4] = (float)ABS(mHeadingAccuracy) / (1 << 13);
    }
    s->orientation.status = mCompassAccuracy;
    update |= isCompassDisabled();
    LOGV_IF(HANDLER_DATA, "HAL:rv wake data: %+f %+f %+f %+f %+f %+lld - %d - %d",
            s->data[0], s->data[1], s->data[2], s->data[3], s->data[4],  s->timestamp, s->orientation.status,
            update);
#if DEBUG_TIME_PROFILE
    if (update)
        printTimeProfile(prevtime, 0, s->timestamp, 0);
#endif
    return update;
}

/*
   Game Rotation Vector handler.
NOTE: rotation vector does not have an accuracy or status
 */
int MPLSensor::grvHandler(sensors_event_t* s)
{
    VHANDLER_LOG;
    int8_t status;
    int update = 0;

    update = output_control(mFeatureActiveMask, s, mEnabledTime[GameRotationVector],
                 adapter_get_sensor_type_game_rotation_vector, 4, s->data,
                &status, (int64_t*)&s->timestamp, 0, &gameRV_timestamp0, invCheckOutput(GameRotationVector), -1);


#if 0
    if (checkBatchEnabled) {
        int outQuat[4];
        inv_compute_scalar_part(mCached6AxisQuaternionData, outQuat);
        if (outQuat[0] >= 0) {
            s->data[0] = outQuat[1] * INV_TWO_POWER_NEG_30;
            s->data[1] = outQuat[2] * INV_TWO_POWER_NEG_30;
            s->data[2] = outQuat[3] * INV_TWO_POWER_NEG_30;
            s->data[3] = outQuat[0] * INV_TWO_POWER_NEG_30;
        } else {
            s->data[0] = -outQuat[1] * INV_TWO_POWER_NEG_30;
            s->data[1] = -outQuat[2] * INV_TWO_POWER_NEG_30;
            s->data[2] = -outQuat[3] * INV_TWO_POWER_NEG_30;
            s->data[3] = -outQuat[0] * INV_TWO_POWER_NEG_30;
        }
        s->timestamp = mQuatSensorTimestamp;
        if (mQuatSensorLastTimestamp < mQuatSensorTimestamp) {
            mQuatSensorLastTimestamp = mQuatSensorTimestamp;
            update = 1;
        }
    }

    float heading[4];
    quat_to_google_orientation(outQuat, heading);
    LOGV_IF(HANDLER_DATA, "grv heading=%f, %f, %f", heading[0], heading[1], heading[2]);
#endif
    if (mCalibrationMode & mpl_quat) {
        s->orientation.status = mAccelAccuracy;
    }
    LOGV_IF(HANDLER_DATA, "HAL:grv data: %+f %+f %+f %+f %+lld - %d - %d",
            s->data[0], s->data[1], s->data[2], s->data[3], s->timestamp, s->orientation.status,
            update);
    return update;
}

int MPLSensor::grvwHandler(sensors_event_t* s)
{
    VHANDLER_LOG;
    int8_t status;
    int update;

    update = output_control(mFeatureActiveMask, s, mEnabledTime[GameRotationVector_Wake],
                 adapter_get_sensor_type_game_rotation_vector, 4, s->data,
                &status, (int64_t*)&s->timestamp, 1, &gameRV_wake_timestamp0, invCheckOutput(GameRotationVector_Wake), -1);

#if 0
    int outQuat[4];
    inv_compute_scalar_part(mCached6AxisQuaternionData, outQuat);
    if (outQuat[0] >= 0) {
        s->data[0] = outQuat[1] * INV_TWO_POWER_NEG_30;
        s->data[1] = outQuat[2] * INV_TWO_POWER_NEG_30;
        s->data[2] = outQuat[3] * INV_TWO_POWER_NEG_30;
        s->data[3] = outQuat[0] * INV_TWO_POWER_NEG_30;
    } else {
        s->data[0] = -outQuat[1] * INV_TWO_POWER_NEG_30;
        s->data[1] = -outQuat[2] * INV_TWO_POWER_NEG_30;
        s->data[2] = -outQuat[3] * INV_TWO_POWER_NEG_30;
        s->data[3] = -outQuat[0] * INV_TWO_POWER_NEG_30;
    }
    float heading[4];
    quat_to_google_orientation(outQuat, heading);
    LOGV_IF(HANDLER_DATA, "grv wake heading=%f, %f, %f", heading[0], heading[1], heading[2]);
#endif
    if (mCalibrationMode & mpl_quat) {
        s->orientation.status = mAccelAccuracy;
    }
    LOGV_IF(HANDLER_DATA, "HAL:grv wake data: %+f %+f %+f %+f %+f %+lld - %d - %d",
            s->data[0], s->data[1], s->data[2], s->data[3], s->data[4], s->timestamp, s->orientation.status,
            update);
    return update;
}

int MPLSensor::laHandler(sensors_event_t* s)
{
    VHANDLER_LOG;
    int update;

    update = output_control(mFeatureActiveMask, s, mEnabledTime[LinearAccel],
                adapter_get_sensor_type_linear_acceleration, 4, s->gyro.v,
                &s->gyro.status, (int64_t*)&s->timestamp, 0, &LA_timestamp0, invCheckOutput(LinearAccel), -1);

    if (mAccelAccuracy > s->gyro.status)
        s->gyro.status = mAccelAccuracy;
    update |= isCompassDisabled();
    LOGV_IF(HANDLER_DATA, "HAL:la data: %+f %+f %+f - %lld - %d - %d",
            s->gyro.v[0], s->gyro.v[1], s->gyro.v[2], s->timestamp, s->gyro.status, update);
    return update;
}

int MPLSensor::lawHandler(sensors_event_t* s)
{
    VHANDLER_LOG;
    int update;

    update = output_control(mFeatureActiveMask, s, mEnabledTime[LinearAccel_Wake],
                adapter_get_sensor_type_linear_acceleration, 4, s->gyro.v,
                &s->gyro.status, (int64_t*)&s->timestamp, 1, &LA_wake_timestamp0, invCheckOutput(LinearAccel_Wake), -1);

    if (mAccelAccuracy > s->gyro.status)
        s->gyro.status = mAccelAccuracy;
    update |= isCompassDisabled();
    LOGV_IF(HANDLER_DATA, "HAL:la wake data: %+f %+f %+f - %lld - %d - %d",
            s->gyro.v[0], s->gyro.v[1], s->gyro.v[2], s->timestamp, s->gyro.status, update);
    return update;
}

int MPLSensor::gravHandler(sensors_event_t* s)
{
    VHANDLER_LOG;
    int update;

    update = output_control(mFeatureActiveMask, s, mEnabledTime[Gravity],
        adapter_get_sensor_type_gravity, 4, s->gyro.v,
                &s->gyro.status, (int64_t*)&s->timestamp, 0, &gravity_timestamp0, invCheckOutput(Gravity), -1);

    if (mAccelAccuracy > s->gyro.status)
        s->gyro.status = mAccelAccuracy;
    update |= isCompassDisabled();
    LOGV_IF(HANDLER_DATA, "HAL:gr data: %+f %+f %+f - %lld - %d - %d",
            s->gyro.v[0], s->gyro.v[1], s->gyro.v[2], s->timestamp, s->gyro.status, update);
    return update;
}

int MPLSensor::gravwHandler(sensors_event_t* s)
{
    VHANDLER_LOG;
    int update;

    update = output_control(mFeatureActiveMask, s, mEnabledTime[Gravity_Wake],
			adapter_get_sensor_type_gravity, 4, s->gyro.v,
                &s->gyro.status, (int64_t*)&s->timestamp, 1, &gravity_wake_timestamp0, invCheckOutput(Gravity_Wake), -1);

    if (mAccelAccuracy > s->gyro.status)
        s->gyro.status = mAccelAccuracy;
    update |= isCompassDisabled();
    LOGV_IF(HANDLER_DATA, "HAL:gr wake data: %+f %+f %+f - %lld - %d - %d",
            s->gyro.v[0], s->gyro.v[1], s->gyro.v[2], s->timestamp, s->gyro.status, update);
    return update;
}

int MPLSensor::orienHandler(sensors_event_t* s)
{
    VHANDLER_LOG;
    int update;
    int64_t delayThreshold;

    delayThreshold = invCheckOutput(Orientation);
    update = output_control(mFeatureActiveMask, s, mEnabledTime[Orientation],
                adapter_get_sensor_type_orientation, 4, s->orientation.v,
                &s->orientation.status, (int64_t*)&s->timestamp, 0, &orientation_timestamp0, delayThreshold, -1);

    if (mCompassAccuracy > s->orientation.status)
        s->orientation.status = mCompassAccuracy;
    update |= isCompassDisabled();
    LOGV_IF(HANDLER_DATA, "HAL:or data: %f %f %f - %lld - %d -%d",
            s->orientation.v[0], s->orientation.v[1], s->orientation.v[2],
            s->timestamp, s->orientation.status, update);
    return update;
}

int MPLSensor::orienwHandler(sensors_event_t* s)
{
    VHANDLER_LOG;
    int update;
    int64_t delayThreshold;

    delayThreshold = invCheckOutput(Orientation_Wake);
    update = output_control(mFeatureActiveMask, s, mEnabledTime[Orientation_Wake],
                adapter_get_sensor_type_orientation, 4, s->orientation.v,
                &s->orientation.status, (int64_t*)&s->timestamp, 1, &orientation_wake_timestamp0, delayThreshold, -1);

    if (mCompassAccuracy > s->orientation.status)
        s->orientation.status = mCompassAccuracy;
    update |= isCompassDisabled();
    LOGV_IF(HANDLER_DATA, "HAL:or wake data: %f %f %f - %lld - %d -%d",
            s->orientation.v[0], s->orientation.v[1], s->orientation.v[2],
            s->timestamp, s->orientation.status, update);
    return update;
}

int MPLSensor::smHandler(sensors_event_t* s)
{
    VHANDLER_LOG;
    int update = 1;

    /* When event is triggered, set data to 1 */
    s->data[0] = 1.f;
    s->data[1] = 0.f;
    s->data[2] = 0.f;

    /* Capture timestamp in HAL */
    s->timestamp = android::elapsedRealtimeNano();

    LOGV_IF(HANDLER_DATA, "HAL:sm data: %f - %lld - %d",
            s->data[0], s->timestamp, update);
    return update;
}

int MPLSensor::gmHandler(sensors_event_t* s)
{
    VHANDLER_LOG;
    int8_t status;
    int update = 0;

    update = output_control(mFeatureActiveMask, s, mEnabledTime[GeomagneticRotationVector],
                adapter_get_sensor_type_geomagnetic_rotation_vector, 5, s->data,
                &status, (int64_t*)&s->timestamp, 0, &geomagRV_timestamp0,
		invCheckOutput(GeomagneticRotationVector), -1);

    if (!(mCalibrationMode & mpl_quat)) {
        /* convert Q29 radian (Upper 2 bytes) to float radian */
        s->data[4] = (float)ABS(mHeadingAccuracy) / (1 << 13);
    }
    s->orientation.status = mCompassAccuracy;
    LOGV_IF(HANDLER_DATA, "HAL:gm data: %+f %+f %+f %+f %+f %+lld - %d - %d",
            s->data[0], s->data[1], s->data[2], s->data[3], s->data[4],  s->timestamp, s->orientation.status, update);
    return update < 1 ? 0 :1;

}

int MPLSensor::gmwHandler(sensors_event_t* s)
{
    VHANDLER_LOG;
    int8_t status;
    int update = 0;

    update = output_control(mFeatureActiveMask, s, mEnabledTime[GeomagneticRotationVector_Wake],
                adapter_get_sensor_type_geomagnetic_rotation_vector, 4, s->data,
                &status, (int64_t*)&s->timestamp, 1, &geomagRV_wake_timestamp0,
			invCheckOutput(GeomagneticRotationVector_Wake), -1);
    if (!(mCalibrationMode & mpl_quat)) {
        /* convert Q29 radian (Upper 2 bytes) to float radian */
	s->data[4] = (float)ABS(mHeadingAccuracy) / (1 << 13);
    }
    s->orientation.status = mCompassAccuracy;
    LOGV_IF(HANDLER_DATA, "HAL:gm wake data: %+f %+f %+f %+f %+f %+lld - %d - %d",
            s->data[0], s->data[1], s->data[2], s->data[3], s->data[4],  s->timestamp, s->orientation.status, update);
    return update;

}

int MPLSensor::psHandler(sensors_event_t* s)
{
    VHANDLER_LOG;
    int8_t status;
    int update = 0;

    s->pressure = mCachedPressureData / 100.f; //hpa (millibar)
    s->data[1] = 0;
    s->data[2] = 0;
    s->timestamp = mPressureTimestamp;
    s->magnetic.status = 0;
    update = mPressureUpdate;
    mPressureUpdate = 0;

    LOGV_IF(HANDLER_DATA, "HAL:ps data: %+f %+f %+f %+f- %+lld - %d",
            s->data[0], s->data[1], s->data[2], s->data[3], s->timestamp, update);
    return update < 1 ? 0 :1;

}

int MPLSensor::pswHandler(sensors_event_t* s)
{
    VHANDLER_LOG;
    int8_t status;
    int update = 0;

    s->pressure = mCachedPressureWakeData / 100.f; //hpa (millibar)
    s->data[1] = 0;
    s->data[2] = 0;
    s->timestamp = mPressureWakeTimestamp;
    s->magnetic.status = 0;
    update = mPressureWakeUpdate;
    mPressureWakeUpdate = 0;

    LOGV_IF(HANDLER_DATA, "HAL:ps wake data: %+f %+f %+f %+f- %+lld - %d",
            s->data[0], s->data[1], s->data[2], s->data[3], s->timestamp, update);
    return update;

}

int MPLSensor::sdHandler(sensors_event_t* s)
{
    VHANDLER_LOG;
    int8_t status;
    int update = mPedUpdate;

    /* When event is triggered, set data to 1 */
    s->data[0] = 1;
    s->data[1] = 0.f;
    s->data[2] = 0.f;

    mPedUpdate = 0;

    if (mCalibrationMode & mpl_gesture) {
        update  = adapter_get_sensor_type_step_detector(s->data, &status, &s->timestamp, 0);
    } else {
        /* get current timestamp */
        s->timestamp = (int64_t)mStepSensorTimestamp;
    }
    LOGV_IF(HANDLER_DATA, "HAL:sd data: %f - %lld - %d",
		s->data[0], s->timestamp, update);
    return update < 1 ? 0 :1;
}

int MPLSensor::sdwHandler(sensors_event_t* s)
{
    VHANDLER_LOG;
    int8_t status;
    int update = mPedWakeUpdate;

    /* When event is triggered, set data to 1 */
    s->data[0] = 1;
    s->data[1] = 0.f;
    s->data[2] = 0.f;

    mPedWakeUpdate = 0;

    if (mCalibrationMode & mpl_gesture) {
        update  = adapter_get_sensor_type_step_detector(s->data, &status, &s->timestamp, 1);
    } else {
        /* get current timestamp */
        s->timestamp = (int64_t)mStepSensorTimestamp;
    }
    LOGV_IF(HANDLER_DATA, "HAL:sd wake data: %f - %lld - %d",
            s->data[0], s->timestamp, update);
    return update < 1 ? 0 :1;
}

int MPLSensor::scHandler(sensors_event_t* s)
{
    VHANDLER_LOG;
    int8_t status;
    int update = 0;

    if (mCalibrationMode & mpl_gesture) {
        adapter_get_sensor_type_step_counter(&s->u64.step_counter, &status, &s->timestamp, 0);
        mStepCount = s->u64.step_counter;
        if (mLastStepCount < 0 || mLastStepCount != mStepCount) {
            mLastStepCount = mStepCount;
            update = 1;
        }
    } else {
    /* Set step count */
        if (mLastStepCount < 0 || mLastStepCount != mStepCount) {
            mLastStepCount = mStepCount;
            update = 1;
        }
        s->u64.step_counter = mStepCount;
        s->timestamp = mStepSensorTimestamp;
    }
    LOGV_IF(HANDLER_DATA, "HAL:sc data: %lld - %lld - %d",
            s->u64.step_counter, s->timestamp, update);
    return update;
}

int MPLSensor::scwHandler(sensors_event_t* s)
{
    VHANDLER_LOG;
    int8_t status;
    int update = 0;

    if (mCalibrationMode & mpl_gesture) {
        adapter_get_sensor_type_step_counter(&s->u64.step_counter, &status, &s->timestamp, 1);
        mStepCountWake = s->u64.step_counter;
        /* Set step count */
        if (mLastStepCountWake  < 0 || mLastStepCountWake != mStepCountWake) {
            mLastStepCountWake = mStepCountWake;
            update = 1;
        }
    } else {
        /* Set step count */
        if (mLastStepCountWake  < 0 || mLastStepCountWake != mStepCountWake) {
            mLastStepCountWake = mStepCountWake;
            update = 1;
        }
        s->u64.step_counter = mStepCountWake;
        s->timestamp = mStepSensorWakeTimestamp;
    }
    LOGV_IF(HANDLER_DATA, "HAL:sc wake data: %lld - %lld - %d",
            s->u64.step_counter, s->timestamp, update);
    return update;
}

int MPLSensor::tiltHandler(sensors_event_t* s)
{
    VHANDLER_LOG;
    int update = 1;
    int8_t status = 0;

    /* When event is triggered, set data to 1 */
    s->data[0] = 1.f;
    s->data[1] = 0.f;
    s->data[2] = 0.f;

    if (mCalibrationMode & mpl_gesture) {
        update  = adapter_get_sensor_type_tilt(s->data, &status, &s->timestamp, 0);
    } else {
        /* Capture timestamp in HAL */
        s->timestamp = android::elapsedRealtimeNano();
    }
    LOGV_IF(HANDLER_DATA, "HAL:tilt data: %f - %lld - %d",
            s->data[0], s->timestamp, update);
    return update;
}

int MPLSensor::pickupHandler(sensors_event_t* s)
{
    VHANDLER_LOG;
    int update = 1;
    int8_t status = 0;

    /* When event is triggered, set data to 1 */
    s->data[0] = 1.f;
    s->data[1] = 0.f;
    s->data[2] = 0.f;

    if (mCalibrationMode & mpl_gesture) {
        update  = adapter_get_sensor_type_pickup(s->data, &status, &s->timestamp, 0);
        //reset status
        if(update)
            enable(ID_PICK, 0);
    } else {
        /* Capture timestamp in HAL */
        s->timestamp = android::elapsedRealtimeNano();
    }
    LOGV_IF(HANDLER_DATA, "HAL:pickup data: %f - %lld - %d",
            s->data[0], s->timestamp, update);
    return update;
}

#if 0
int MPLSensor::eisHandler(sensors_event_t* s)
{
    VHANDLER_LOG;
    int update = 0;
    int8_t status = 0;

    update = adapter_get_sensor_type_eis_gyroscope(s->data, &status, &s->timestamp, 0);

    LOGV_IF(HANDLER_DATA, "HAL:eis data: %f %f %f %f - %lld - %d",
            s->data[0], s->data[1], s->data[2], s->data[3], s->timestamp, update);
    update = mEisUpdate;
    mEisUpdate = 0;
    return update;
}

int MPLSensor::eisAuthenticationHandler(sensors_event_t* s)
{
    VHANDLER_LOG;
    int update = 0;
    int8_t status = 0;

    update = adapter_get_sensor_type_eis_authentication(s->data, &status, &s->timestamp, 0);

    LOGV_IF(HANDLER_DATA, "HAL:eis auth data: %f %f %f %f - %lld - %d",
            s->data[0], s->data[1], s->data[2], s->data[3], s->timestamp, update);
    update = mEisAuthenticationUpdate;
    mEisAuthenticationUpdate = 0;
    return update;
}
#endif
int MPLSensor::metaHandler(sensors_event_t* s, int flags)
{
    VHANDLER_LOG;
    int update = 1;

    /* initalize SENSOR_TYPE_META_DATA */
    s->version = META_DATA_VERSION;
    s->sensor = 0;
    s->reserved0 = 0;
    s->timestamp = 0LL;

    switch(flags) {
        case META_DATA_FLUSH_COMPLETE:
            s->type = SENSOR_TYPE_META_DATA;
            s->meta_data.what = flags;
            s->meta_data.sensor = mFlushSensorEnabledVector[0];

            pthread_mutex_lock(&mHALMutex);
            mFlushSensorEnabledVector.removeAt(0);
            pthread_mutex_unlock(&mHALMutex);
            LOGV_IF(HANDLER_DATA,
                    "HAL:flush complete data: type=%d what=%d, "
                    "sensor=%d - %lld - %d",
                    s->type, s->meta_data.what, s->meta_data.sensor,
                    s->timestamp, update);
            break;

        default:
            LOGW("HAL: Meta flags not supported");
            break;
    }

    return update;
}

int MPLSensor::lightHandler(sensors_event_t* s)
{
    VHANDLER_LOG;

    if ((int)s->data[0] == mCachedAlsData[0]) {
	if (mLightInitEvent) {
	    mLightInitEvent = false;
	}
        else
	    return 0;
    }

    s->data[0] = (float)mCachedAlsData[0];
    s->timestamp = mAlsSensorTimestamp;
    LOGV_IF(HANDLER_DATA, "HAL:light data: %+f %+lld - %d",
            s->data[0], s->timestamp, 1);
    return 1;
}

int MPLSensor::lightwHandler(sensors_event_t* s)
{
    VHANDLER_LOG;

    if ((int)s->data[0] == mCachedAlsWakeData[0]) {
	if (mLightWakeInitEvent) {
	    mLightWakeInitEvent = false;
        }
	else
	    return 0;
    }

    s->data[0] = mCachedAlsWakeData[0];
    s->timestamp = mAlsSensorWakeTimestamp;
    LOGV_IF(HANDLER_DATA, "HAL:light wake data: %+f %+lld - %d",
            s->data[0], s->timestamp, 1);
    return 1;
}

int MPLSensor::proxHandler(sensors_event_t* s)
{
    VHANDLER_LOG;

    if (mProxiInitEvent) {
	s->data[0] = -1;
	mProxiInitEvent = false;
    }

    if (mCachedAlsData[1] > PROXIMITY_RANGE) {
	if ((int)s->data[0] == 0)
	    return 0;

        s->data[0] = 0;
    }
    else {
	if ((int)s->data[0] == 5)
	    return 0;

        s->data[0] = 5;
    }

    s->timestamp = mAlsSensorTimestamp;
    LOGV_IF(HANDLER_DATA, "HAL:prox data: %+f -%+ld %+lld - %d",
            s->data[0], mCachedAlsData[1], s->timestamp, 1);
    return 1;
}

int MPLSensor::proxwHandler(sensors_event_t* s)
{
    VHANDLER_LOG;

    if (mProxiWakeInitEvent) {
	s->data[0] = -1;
	mProxiWakeInitEvent = false;
    }

    if (mCachedAlsWakeData[1] > PROXIMITY_RANGE) {
	if ((int)s->data[0] == 0)
	    return 0;

        s->data[0] = 0;
    }
    else {
	if ((int)s->data[0] == 5)
	    return 0;

        s->data[0] = 5;
    }

    s->timestamp = mAlsSensorWakeTimestamp;
    LOGV_IF(HANDLER_DATA, "HAL:prox wake data: %+f -%+ld %+lld - %d",
            s->data[0], mCachedAlsWakeData[1], s->timestamp, 1);
    return 1;
}

void MPLSensor::getHandle(int32_t handle, int &what, android::String8 &sname)
{
    VFUNC_LOG;
    uint32_t i;
    what = -1;

    if (handle >= ID_NUMBER) {
        LOGV_IF(ENG_VERBOSE, "HAL:handle over = %d",handle);
        return;
    }
    for (i = 0; i < mNumSensors; i++) {
	if (handle == currentSensorList[i].handle) {
		what = handle;
		sname = mCurrentSensorMask[handle].sname;
		break;
	}
    }

    LOGI_IF(EXTRA_VERBOSE, "HAL:getHandle - what=%d, sname=%s", what, sname.string());
    return;
}

int MPLSensor::setDelay(int32_t handle, int64_t ns)
{
    VFUNC_LOG;

    android::String8 sname;
    int what = -1;

    getHandle(handle, what, sname);
    if (what < 0)
        return -EINVAL;

    if (ns < 0)
        return -EINVAL;

    LOGV_IF(PROCESS_VERBOSE,
            "setDelay : %llu ns, (%.2f Hz)", ns, NS_PER_SECOND_FLOAT / ns);

    // limit all rates to reasonable ones */
    if (ns < 5000000LL) {
        ns = 5000000LL;
    }

    /* store request rate to mDelays arrary for each sensor */
    int64_t previousDelay = mDelays[what];
    mDelays[what] = ns;

    switch (what) {
        case StepCounter:
            /* set limits of delivery rate of events */
            LOGV_IF(ENG_VERBOSE, "step count rate is not applicable");
            break;
        case StepDetector:
        case SignificantMotion:
            LOGV_IF(ENG_VERBOSE, "Step Detect, SMD, SO rate=%lld ns", ns);
            break;
        case Gyro:
        case RawGyro:
        case GyroRaw:
        case Accelerometer:
        case AccelerometerRaw:
            // need to update delay since they are different
            // resetDataRates was called earlier
            // LOGV("what=%d mEnabled=%d mDelays[%d]=%lld previousDelay=%lld",
            // what, mEnabled, what, mDelays[what], previousDelay);
            if ((mEnabled & (1LL << what)) && (previousDelay != mDelays[what])) {
                LOGV_IF(ENG_VERBOSE,
                        "HAL:need to update delay due to resetDataRates");
                break;
            }
            for (int i = Gyro;
                    i <= Accelerometer + mCompassSensor->isIntegrated();
                    i++) {
                if (i != what && (mEnabled & (1LL << i)) && ns > mDelays[i]) {
                    LOGV_IF(ENG_VERBOSE,
                            "HAL:ignore delay set due to sensor %d", i);
                    return 0;
                }
            }
            break;

        case MagneticField:
        case RawMagneticField:
        case MagneticFieldRaw:
            // need to update delay since they are different
            // resetDataRates was called earlier
            if ((mEnabled & (1LL << what)) && (previousDelay != mDelays[what])) {
                LOGV_IF(ENG_VERBOSE,
                        "HAL:need to update delay due to resetDataRates");
                break;
            }
            if (mCompassSensor->isIntegrated() &&
                    (((mEnabled & (1LL << Gyro)) && ns > mDelays[Gyro]) ||
                     ((mEnabled & (1LL << RawGyro)) && ns > mDelays[RawGyro]) ||
                     ((mEnabled & (1LL << Accelerometer)) &&
                      ns > mDelays[Accelerometer])) &&
                    !checkBatchEnabled()) {
                /* if request is slower rate, ignore request */
                LOGV_IF(ENG_VERBOSE,
                        "HAL:ignore delay set due to gyro/accel");
                return 0;
            }
            break;

        case Orientation:
        case RotationVector:
        case GameRotationVector:
        case GeomagneticRotationVector:
        case LinearAccel:
        case Gravity:
            if (isLowPowerQuatEnabled()) {
                LOGV_IF(ENG_VERBOSE,
                        "HAL:need to update delay due to LPQ");
                break;
            }

            for (int i = 0; i < ID_NUMBER; i++) {
                if (i != what && (mEnabled & (1LL << i)) && ns > mDelays[i]) {
                    LOGV_IF(ENG_VERBOSE,
                            "HAL:ignore delay set due to sensor %d", i);
                    return 0;
                }
            }
            break;
    }

    int res = update_delay();
    return res;
}

int MPLSensor::update_delay(void)
{
    return 0;
}

/**
 *  Should be called after reading at least one of gyro
 *  compass or accel data. (Also okay for handling all of them).
 *  @returns 0, if successful, error number if not.
 */
int MPLSensor::readEvents(sensors_event_t* data, int count)
{
    VHANDLER_LOG;

    inv_execute_on_data();

    int numEventReceived = 0;

    // handle flush complete event
    if(!mFlushSensorEnabledVector.isEmpty()) {
        if (!mEmptyDataMarkerDetected) {
            // turn off sensors in data_builder
            mEmptyDataMarkerDetected = 0;
        }
        sensors_event_t temp;
        int sendEvent = metaHandler(&temp, META_DATA_FLUSH_COMPLETE);
        if(sendEvent == 1 && count > 0) {
            *data++ = temp;
            count--;
            numEventReceived++;
        }
    }

    if (mSkipReadEvents) {
        mSkipReadEvents = 0;
        return numEventReceived;
    }

    for (int i = 0; i < ID_NUMBER; i++) {
        int update = 0;

        // load up virtual sensors
        if (mEnabledCached & (1LL << i)) {
            update = CALL_MEMBER_FN(this, mHandlers[i])(mPendingEvents + i);
            mPendingMask |= (1LL << i);
            if (update && (count > 0)) {
                *data++ = mPendingEvents[i];
                count--;
                numEventReceived++;
            }
        }
    }

    return numEventReceived;
}

// collect data for MPL (but NOT sensor service currently), from driver layer
void MPLSensor::buildMpuEvent(void)
{
    VHANDLER_LOG;

    mSkipReadEvents = 0;
    int64_t mGyroSensorTimestamp=0, mAccelSensorTimestamp=0, latestTimestamp=0;
    size_t nbyte = BYTES_PER_SENSOR;
    int data_format = 0;
    int mask = 0;
    uint64_t sensors = ((mLocalSensorMask & INV_THREE_AXIS_GYRO)? 1 : 0) +
            ((mLocalSensorMask & INV_THREE_AXIS_RAW_GYRO)? 1 : 0) +
            ((mLocalSensorMask & INV_THREE_AXIS_ACCEL)? 1 : 0) +
            (((mLocalSensorMask & INV_THREE_AXIS_COMPASS)
              && mCompassSensor->isIntegrated())? 1 : 0) +
            (((mLocalSensorMask & INV_THREE_AXIS_RAW_COMPASS)
              && mCompassSensor->isIntegrated())? 1 : 0) +
            ((mLocalSensorMask & INV_ONE_AXIS_PRESSURE)? 1 : 0);
    long long data_out[4];
    char *rdata;
    int rsize = 0;
    char outBuffer[32];
    bool doneFlag = 0;
    int what_sensor = -1;
    int i;

    rsize = read(iio_fd, outBuffer, nbyte);

#ifdef TESTING
    LOGV_IF(INPUT_DATA,
            "HAL:input outBuffer:r=%d, n=%d,"
            "%d, %d, %d, %d, %d, %d, %d, %d",
            (int)rsize, nbyte,
            outBuffer[0], outBuffer[1], outBuffer[2], outBuffer[3],
            outBuffer[4], outBuffer[5], outBuffer[6], outBuffer[7]);
#endif

    rdata = (char *)data_out;
    if(rsize < 0) {
        /* IIO buffer might have old data.
           Need to flush it if no sensor is on, to avoid infinite
           read loop.*/
        LOGE("HAL:input data file descriptor not available - (%s)",
                strerror(errno));
        data_format = inv_sensor_parsing(outBuffer, rdata, rsize);
        if (sensors == 0) {
            rsize = read(iio_fd, outBuffer, MAX_READ_SIZE);
            if(rsize > 0) {
                LOGV_IF(ENG_VERBOSE, "HAL:input data flush rsize=%d", (int)rsize);
#ifdef TESTING
                LOGV_IF(INPUT_DATA,
                        "HAL:input outBuffer:r=%d, n=%d,"
                        "%d, %d, %d, %d, %d, %d, %d, %d",
                        (int)rsize, nbyte,
                        outBuffer[0], outBuffer[1], outBuffer[2], outBuffer[3],
                        outBuffer[4], outBuffer[5], outBuffer[6], outBuffer[7]);
#endif
                return;
            }
        }
    }

    data_format = inv_sensor_parsing(outBuffer, rdata, rsize);

    if (data_format == 0) {
        mSkipReadEvents = 1;
        return;
    }

    if(checkValidHeader(data_format) == 0) {
        LOGE("HAL:input invalid data_format 0x%02X", data_format);
        return;
    }

    if (data_format == DATA_FORMAT_STEP) {
        rdata += BYTES_PER_SENSOR;
        latestTimestamp = *((long long*) (rdata));
        LOGV_IF(ENG_VERBOSE && INPUT_DATA, "STEP DETECTED:0x%x - ts: %lld", data_format, latestTimestamp);
        mPedUpdate = data_format;
        mask = DATA_FORMAT_STEP;
        // cancels step bit
        //data_format &= (~DATA_FORMAT_STEP);
    }

    if (data_format == DATA_FORMAT_GYRO_ACCURACY) {
        setAccuracy(0, *((short *) (rdata + 2)));
        LOGV_IF(ENG_VERBOSE && INPUT_DATA, "GYRO ACCURACY DETECTED:%d", mGyroAccuracy);
        getDmpGyroBias();
    }
    else if (data_format == DATA_FORMAT_COMPASS_ACCURACY) {
        setAccuracy(2, *((short *) (rdata + 2)));
        LOGV_IF(ENG_VERBOSE && INPUT_DATA, "COMPASS ACCURACY DETECTED:%d", mCompassAccuracy);
	getDmpCompassBias();
    }
    else if (data_format == DATA_FORMAT_ACCEL_ACCURACY) {
        setAccuracy(1, *((short *) (rdata + 2)));
        LOGV_IF(ENG_VERBOSE && INPUT_DATA, "ACCEL ACCURACY DETECTED:%d", mAccelAccuracy);
	getDmpAccelBias();
    }
    else if (data_format == DATA_FORMAT_MARKER) {
        what_sensor = *((int *) (rdata + 4));
        mFlushSensorEnabledVector.push_back(what_sensor);
        LOGV_IF(ENG_VERBOSE && INPUT_DATA, "MARKER DETECTED what:%d", what_sensor);
    }
    else if (data_format == DATA_FORMAT_EMPTY_MARKER) {
        what_sensor = *((int *) (rdata + 4));
        mFlushSensorEnabledVector.push_back(what_sensor);
        mEmptyDataMarkerDetected = 1;
        LOGV_IF(ENG_VERBOSE && INPUT_DATA, "EMPTY MARKER DETECTED what:%d", what_sensor);
    }
    else if (data_format == DATA_FORMAT_ALS) {
        LOGV_IF(ENG_VERBOSE && INPUT_DATA, "ALS DETECTED:0x%x", data_format);

        mCachedAlsData[0] = *((short *) (rdata + 2));
        mCachedAlsData[1] = *((short *) (rdata + 4));
        rdata += BYTES_PER_SENSOR;
        mAlsSensorTimestamp = *((long long*) (rdata));
        mask = DATA_FORMAT_ALS;
    }
    else if (data_format == DATA_FORMAT_ALS_WAKE) {
        LOGV_IF(ENG_VERBOSE && INPUT_DATA, "ALS WAKE DETECTED:0x%x", data_format);

        mCachedAlsWakeData[0] = *((short *) (rdata + 2));
        mCachedAlsWakeData[1] = *((short *) (rdata + 4));
        rdata += BYTES_PER_SENSOR;
        mAlsSensorWakeTimestamp = *((long long*) (rdata));
        mask = DATA_FORMAT_ALS_WAKE;
    }
    else if (data_format == DATA_FORMAT_6_AXIS || data_format == DATA_FORMAT_6_AXIS_WAKE) {
        LOGV_IF(ENG_VERBOSE && INPUT_DATA, "6AXIS DETECTED:0x%x", data_format);

        mCached6AxisQuaternionData[0] = *((int *) (rdata + 4));
        mCached6AxisQuaternionData[1] = *((int *) (rdata + 8));
        mCached6AxisQuaternionData[2] = *((int *) (rdata + 12));
        rdata += QUAT_ONLY_LAST_PACKET_OFFSET;
        m6QuatSensorTimestamp = *((long long*) (rdata));
        mask = DATA_FORMAT_6_AXIS;

    }
    else if (data_format == DATA_FORMAT_9_AXIS || data_format == DATA_FORMAT_9_AXIS_WAKE) {
#if DEBUG_TIME_PROFILE
        prevtime = getTimestamp();
#endif
        LOGV_IF(ENG_VERBOSE && INPUT_DATA, "9AXIS DETECTED:0x%x", data_format);

        mHeadingAccuracy = *((short *) (rdata + 2));
        mCached9AxisQuaternionData[0] = *((int *) (rdata + 4));
        mCached9AxisQuaternionData[1] = *((int *) (rdata + 8));
        mCached9AxisQuaternionData[2] = *((int *) (rdata + 12));
        rdata += QUAT_ONLY_LAST_PACKET_OFFSET;
        mQuatSensorTimestamp = *((long long*) (rdata));
        mask = DATA_FORMAT_9_AXIS;
    }
    else if (data_format == DATA_FORMAT_GEOMAG || data_format == DATA_FORMAT_GEOMAG_WAKE) {
        LOGV_IF(ENG_VERBOSE && INPUT_DATA, "GEOMAG DETECTED:0x%x", data_format);

        mHeadingAccuracy = *((short *) (rdata + 2));
        mCachedGeomagData[0] = *((int *) (rdata + 4));
        mCachedGeomagData[1] = *((int *) (rdata + 8));
        mCachedGeomagData[2] = *((int *) (rdata + 12));
        rdata += QUAT_ONLY_LAST_PACKET_OFFSET;
        mGeoQuatSensorTimestamp = *((long long*)
                (rdata));
        mask = DATA_FORMAT_GEOMAG;
    }
    else if (data_format == DATA_FORMAT_PED_QUAT || data_format == DATA_FORMAT_PED_QUAT_WAKE) {
        LOGV_IF(ENG_VERBOSE && INPUT_DATA, "PED QUAT DETECTED:0x%x", data_format);

        mCachedPedQuaternionData[0] = *((short *) (rdata + 2));
        mCachedPedQuaternionData[1] = *((short *) (rdata + 4));
        mCachedPedQuaternionData[2] = *((short *) (rdata + 6));
        rdata += BYTES_PER_SENSOR;
        m6QuatSensorTimestamp = *((long long*) (rdata));
        mask = DATA_FORMAT_PED_QUAT;
    }
    else if (data_format == DATA_FORMAT_STEP_COUNT) {
        LOGV_IF(ENG_VERBOSE && INPUT_DATA, "STEP COUNTER DETECTED:0x%x", data_format);

        mStepCount = *((int *) (rdata + 4));
        rdata += BYTES_PER_SENSOR;
        mStepSensorTimestamp = *((long long*) (rdata));
        mask = DATA_FORMAT_STEP_COUNT;
    }
    else if (data_format == DATA_FORMAT_STEP_COUNT_WAKE) {
        LOGV_IF(ENG_VERBOSE && INPUT_DATA, "STEP COUNTER WAKE DETECTED:0x%x", data_format);

        mStepCountWake = *((int *) (rdata + 4));
        rdata += BYTES_PER_SENSOR;
        mStepSensorWakeTimestamp = *((long long*) (rdata));
        mask = DATA_FORMAT_STEP_COUNT_WAKE;
    }
    else if (data_format == DATA_FORMAT_PED_STANDALONE) {
        LOGV_IF(ENG_VERBOSE && INPUT_DATA, "STANDALONE STEP DETECTED:0x%x", data_format);

        rdata += BYTES_PER_SENSOR;
        mStepSensorTimestamp = *((long long*) (rdata));
        mask = DATA_FORMAT_PED_STANDALONE;
        mPedUpdate = data_format;
    }
    else if (data_format == DATA_FORMAT_PED_STANDALONE_WAKE) {
        LOGV_IF(ENG_VERBOSE && INPUT_DATA, "STANDALONE STEP WAKE DETECTED:0x%x", data_format);

        rdata += BYTES_PER_SENSOR;
        mStepSensorTimestamp = *((long long*) (rdata));
        mask = DATA_FORMAT_PED_STANDALONE;
        mPedWakeUpdate |= DATA_FORMAT_PED_STANDALONE_WAKE;
    }
    else if (data_format == DATA_FORMAT_RAW_GYRO ||
            data_format == DATA_FORMAT_RAW_GYRO_WAKE) {
        LOGV_IF(ENG_VERBOSE && INPUT_DATA, "RAW GYRO DETECTED:0x%x", data_format);

        mCachedGyroData[0] = *((short *) (rdata + 2));
        mCachedGyroData[1] = *((short *) (rdata + 4));
        mCachedGyroData[2] = *((short *) (rdata + 6));

        //if (!mOisEnabled) {
            LOGV_IF(ENG_VERBOSE && INPUT_DATA, "Apply gyro bias for UI");
            for (i = 0; i < 3; i++)
                mCachedGyroData[i] += mGyroBiasUiMode[i];
       // }

        rdata += BYTES_PER_SENSOR;
        mGyroSensorTimestamp = *((long long*) (rdata));
        mask = DATA_FORMAT_RAW_GYRO;
    }
    else if (data_format == DATA_FORMAT_GYRO || data_format == DATA_FORMAT_GYRO_WAKE) {
        LOGV_IF(ENG_VERBOSE && INPUT_DATA, "CAL GYRO DETECTED:0x%x", data_format);

        mCachedCalGyroData[0] = *((int *) (rdata + 4));
        mCachedCalGyroData[1] = *((int *) (rdata + 8));
        mCachedCalGyroData[2] = *((int *) (rdata + 12));
        rdata += QUAT_ONLY_LAST_PACKET_OFFSET;
        mGyroSensorTimestamp = *((long long*) (rdata));
        mask = DATA_FORMAT_GYRO;
    }
    else if (data_format == DATA_FORMAT_ACCEL ||
            data_format == DATA_FORMAT_ACCEL_WAKE) {
        LOGV_IF(ENG_VERBOSE && INPUT_DATA, "ACCEL DETECTED:0x%x", data_format);

        mCachedAccelData[0] = *((short *) (rdata + 2)); // 4 8 12 - jhkim
        mCachedAccelData[1] = *((short *) (rdata + 4)); // 8
        mCachedAccelData[2] = *((short *) (rdata + 6));// 12
        //if (!mOisEnabled) {
			/* accel is always continous mode if gyro is enabled */
            if (((GYRO_MPL_SENSOR & mLocalSensorMask) == 0)
                    && ((AUX_MPL_SENSOR & mLocalSensorMask) == 0)) {
                LOGV_IF(ENG_VERBOSE && INPUT_DATA, "Apply accel bias for UI");
                for (i = 0; i < 3; i++)
                    mCachedAccelData[i] += mAccelBiasUiMode[i];
            }
        //}
        rdata += BYTES_PER_SENSOR;//QUAT_ONLY_LAST_PACKET_OFFSET;
        mAccelSensorTimestamp = *((long long*) (rdata));
        mask = DATA_FORMAT_ACCEL;
    }
    else if (data_format == DATA_FORMAT_RAW_COMPASS ||
            data_format == DATA_FORMAT_RAW_COMPASS_WAKE) {
        LOGV_IF(ENG_VERBOSE && INPUT_DATA, "RAW COMPASS DETECTED:0x%x", data_format);

        if (mCompassSensor->isIntegrated()) {
            mCachedCompassData[0] = *((int *) (rdata + 4));
            mCachedCompassData[1] = *((int *) (rdata + 8));
            mCachedCompassData[2] = *((int *) (rdata + 12));
            rdata += QUAT_ONLY_LAST_PACKET_OFFSET;
            mCompassTimestamp = *((long long*) (rdata));
            mask = DATA_FORMAT_RAW_COMPASS;
        }
    }
    else if (data_format == DATA_FORMAT_COMPASS || data_format == DATA_FORMAT_COMPASS_WAKE) {
        LOGV_IF(ENG_VERBOSE && INPUT_DATA, "COMPASS DETECTED:0x%x", data_format);

        if (mCompassSensor->isIntegrated()) {
            mCachedCompassData[0] = *((int *) (rdata + 4));
            mCachedCompassData[1] = *((int *) (rdata + 8));
            mCachedCompassData[2] = *((int *) (rdata + 12));
            rdata += QUAT_ONLY_LAST_PACKET_OFFSET;
            mCompassTimestamp = *((long long*) (rdata));
            mask = DATA_FORMAT_COMPASS;
        }
    }
#if IIO_LIGHT_SENSOR
    else if (data_format == DATA_FORMAT_PRESSURE) {
        LOGV_IF(ENG_VERBOSE && INPUT_DATA, "PRESSURE DETECTED:0x%x", data_format);

        if (mPressureSensor->isIntegrated()) {
            mCachedPressureData =
                ((*((short *)(rdata + 4))) << 16) +
                (*((unsigned short *) (rdata + 6)));
            rdata += BYTES_PER_SENSOR;
            mPressureTimestamp = *((long long*) (rdata));
            if (mCachedPressureData != 0) {
                mask = DATA_FORMAT_PRESSURE;
            }
        }
    }
    else if (data_format == DATA_FORMAT_PRESSURE_WAKE) {
        LOGV_IF(ENG_VERBOSE && INPUT_DATA, "PRESSURE WAKE DETECTED:0x%x", data_format);

        if (mPressureSensor->isIntegrated()) {
            mCachedPressureWakeData =
                ((*((short *)(rdata + 4))) << 16) +
                (*((unsigned short *) (rdata + 6)));
            rdata += BYTES_PER_SENSOR;
            mPressureWakeTimestamp = *((long long*) (rdata));
            if (mCachedPressureWakeData != 0) {
                mask = DATA_FORMAT_PRESSURE_WAKE;
            }
        }
    }
#endif
#if 0
else if (data_format == DATA_FORMAT_EIS_GYROSCOPE) {
	    LOGV_IF(ENG_VERBOSE && INPUT_DATA, "EIS DETECTED:0x%x", data_format);
	    mCachedEisData[0] = *((int *) (rdata + 4));
	    mCachedEisData[1] = *((int *) (rdata + 8));
	    mCachedEisData[2] = *((int *) (rdata + 12));
	    mCachedEisData[3] = *((int *) (rdata + 16));
	    rdata += BYTES_QUAT_DATA;
	    mEisTimestamp = *((long long*) (rdata));
	    mask = DATA_FORMAT_EIS_GYROSCOPE;

	    LOGV_IF(ENG_VERBOSE && INPUT_DATA, "EIS DATA [%d] [%d] [%d] [%d] [%lld]",mCachedEisData[0], mCachedEisData[1], mCachedEisData[2], mCachedEisData[3], mEisTimestamp);
    } else if (data_format == DATA_FORMAT_EIS_AUTHENTICATION) {
            LOGV_IF(ENG_VERBOSE && INPUT_DATA, "EIS AUTH DETECTED:0x%x", data_format);
            mCachedEisAuthenticationData[0] = *((int *) (rdata + 4));
            rdata += 8;
            mask = DATA_FORMAT_EIS_AUTHENTICATION;

            LOGV_IF(ENG_VERBOSE && INPUT_DATA, "EIS AUTH DATA [%d] [%d] [%d] [%d] [%lld]",mCachedEisAuthenticationData[0], mCachedEisAuthenticationData[1], mCachedEisAuthenticationData[2], mCachedEisAuthenticationData[3], mEisAuthenticationTimestamp);
    }
#endif
	else {
	    LOGI("Data format [%d]",data_format);
    }

    /* handle data read */
    if (mask == DATA_FORMAT_GYRO || mask == DATA_FORMAT_RAW_GYRO) {
        /* batch mode does not batch temperature */
        /* disable temperature read */
        if (!(mFeatureActiveMask & INV_DMP_BATCH_MODE)) {
            // send down temperature every 0.5 seconds
            // with timestamp measured in "driver" layer
            if(mGyroSensorTimestamp - mTempCurrentTime >= 500000000LL) {
                mTempCurrentTime = mGyroSensorTimestamp;
                long long temperature[2];
#ifdef TEMP_COMP
                if(inv_read_temperature(temperature) == 0) {
                    LOGV_IF(INPUT_DATA,
                            "HAL:input read_temperature = %lld, timestamp= %lld",
                            temperature[0], temperature[1]);
                    //inv_build_temp(temperature[0], temperature[1]);
                }
#ifdef TESTING
                int bias[3], temp, temp_slope[3];
                inv_get_mpl_gyro_bias(bias, &temp);
                inv_get_gyro_ts(temp_slope);
                LOGI("T: %.3f "
                        "GB: %+13f %+13f %+13f "
                        "TS: %+13f %+13f %+13f "
                        "\n",
                        (float)temperature[0] / 65536.f,
                        (float)bias[0] / 65536.f / 16.384f,
                        (float)bias[1] / 65536.f / 16.384f,
                        (float)bias[2] / 65536.f / 16.384f,
                        temp_slope[0] / 65536.f,
                        temp_slope[1] / 65536.f,
                        temp_slope[2] / 65536.f);
#endif
#endif
            }
        }
        mPendingMask |= 1LL << Gyro;
        mPendingMask |= 1LL << RawGyro;

        int status = 0;
        if(mask == DATA_FORMAT_GYRO) {
            status |= INV_CALIBRATED;
            LOGV_IF(INPUT_DATA,
                    "HAL:input build_gyro_cal: %+8ld %+8ld %+8ld - %lld",
                    mCachedCalGyroData[0], mCachedCalGyroData[1],
                    mCachedCalGyroData[2], mGyroSensorTimestamp);
            adapter_build_gyro_dmp(mCachedCalGyroData, status, mGyroSensorTimestamp);
        } else {
            LOGV_IF(INPUT_DATA,
                    "HAL:input build_gyro: %+8ld %+8ld %+8ld - %lld",
                    mCachedGyroData[0], mCachedGyroData[1],
                    mCachedGyroData[2], mGyroSensorTimestamp);
            adapter_build_gyro_dmp(mCachedGyroData, status, mGyroSensorTimestamp);

	    if (mCustomSensorPresent) {
		int raw_data[3];
		status |= INV_CUSTOM;
		read_sysfs_int_array(mpu.gyro_raw_data, raw_data);
		LOGV_IF(INPUT_DATA,
				"HAL:input build_gyro(reg): %+8ld %+8ld %+8ld - %lld",
				raw_data[0], raw_data[1],
				raw_data[2], mGyroSensorTimestamp);
		adapter_build_gyro_dmp(raw_data, status, mGyroSensorTimestamp);
	    }
        }
        latestTimestamp = mGyroSensorTimestamp;
    }

#if 0
    if (mask == DATA_FORMAT_EIS_GYROSCOPE) {
        mPendingMask |= 1LL << EISGyroscope;
        adapter_build_eis_gyro(mCachedEisData, INV_CALIBRATED, mEisTimestamp);
        LOGV_IF(INPUT_DATA,
                "HAL:input build_Eis: %+8ld %+8ld %+8ld %d - %lld",
                mCachedEisData[0], mCachedEisData[1],
                mCachedEisData[2], mCachedEisData[3], mEisTimestamp);
        latestTimestamp = mEisTimestamp;
	mEisUpdate = 1;
    }

    if (mask == DATA_FORMAT_EIS_AUTHENTICATION) {
        mPendingMask |= 1LL << EISAuthentication;
        adapter_build_eis_authentication(mCachedEisAuthenticationData, INV_CALIBRATED, mEisAuthenticationTimestamp);
        LOGV_IF(INPUT_DATA,
                "HAL:input build_Eis_Authentication: %+8ld %+8ld %+8ld %d - %lld",
                mCachedEisAuthenticationData[0], mCachedEisAuthenticationData[1],
                mCachedEisAuthenticationData[2], mCachedEisAuthenticationData[3], mEisAuthenticationTimestamp);
        latestTimestamp = mEisAuthenticationTimestamp;
        mEisAuthenticationUpdate = 1;
    }
#endif

    if (mask == DATA_FORMAT_ACCEL) {
	long long currTS;
	currTS = android::elapsedRealtimeNano();
        mPendingMask |= 1LL << Accelerometer;
        adapter_build_accel_dmp(mCachedAccelData, INV_CALIBRATED, mAccelSensorTimestamp);
        LOGV_IF(INPUT_DATA,
                "HAL:input build_accel: %+8ld %+8ld %+8ld - %lld - %lld",
                mCachedAccelData[0], mCachedAccelData[1],
                mCachedAccelData[2], mAccelSensorTimestamp, currTS - mAccelSensorTimestamp);
	if (mCustomSensorPresent) {
		int raw_data[3];
		read_sysfs_int_array(mpu.accel_raw_data, raw_data);
		LOGV_IF(INPUT_DATA,
				"HAL:input build_accel(reg): %+8ld %+8ld %+8ld - %lld",
				raw_data[0],
				raw_data[1],
				raw_data[2],
				mAccelSensorTimestamp);
		adapter_build_accel_dmp(raw_data, INV_CUSTOM, mAccelSensorTimestamp);
	}
        latestTimestamp = mAccelSensorTimestamp;
    }

    if (((mask == DATA_FORMAT_RAW_COMPASS) || (mask == DATA_FORMAT_COMPASS)) &&
                                     mCompassSensor->isIntegrated()) {
        int status = 0;
        if (mask == DATA_FORMAT_COMPASS) {
            status |= INV_CALIBRATED;
            status |= mCompassAccuracy & 3; // apply accuracy from DMP
        }
        else {
			status |= (INV_RAW_DMP | INV_CALIBRATED);
        }
        if (mCompassSensor->providesCalibration()) {
            status = mCompassSensor->getAccuracy(); // Always return 0
            status |= INV_CALIBRATED;
        }
        adapter_build_compass(mCachedCompassData, status,
                mCompassTimestamp);
        LOGV_IF(INPUT_DATA,
                "HAL:input build_compass: %+8ld %+8ld %+8ld - %lld",
                mCachedCompassData[0], mCachedCompassData[1],
                mCachedCompassData[2], mCompassTimestamp);
	if (mCustomSensorPresent && (mask == DATA_FORMAT_RAW_COMPASS)) {
		int raw_data[3];
		status |= INV_CUSTOM;
		read_sysfs_int_array(mpu.compass_raw_data, raw_data);
		LOGV_IF(INPUT_DATA,
				"HAL:input build_compass(reg): %+8ld %+8ld %+8ld - %lld",
				raw_data[0],
				raw_data[1],
				raw_data[2],
				mCompassTimestamp);
		adapter_build_compass(raw_data, status, mCompassTimestamp);
	}
        latestTimestamp = mCompassTimestamp;
    }
#ifdef IIO_LIGHT_SENSOR

    if ((mask == DATA_FORMAT_ALS) && mLightSensor->isIntegrated()) {
        int status = 0;
        if (mLocalSensorMask & INV_ONE_AXIS_LIGHT) {
            latestTimestamp = mAlsSensorTimestamp;
            LOGV_IF(INPUT_DATA,
                    "HAL:input build_light: %+8ld - %lld",
                    mCachedAlsData[0], mAlsSensorTimestamp);
            LOGV_IF(INPUT_DATA,
                    "HAL:input build_prox: %+8ld - %lld",
                    mCachedAlsData[1], mAlsSensorTimestamp);
        }
    }
    if ((mask == DATA_FORMAT_ALS_WAKE) && mLightSensor->isIntegrated()) {
        int status = 0;
        if (mLocalSensorMask & INV_ONE_AXIS_LIGHT_WAKE) {
            latestTimestamp = mAlsSensorWakeTimestamp;
            LOGV_IF(INPUT_DATA,
                    "HAL:input build_light wake: %+8ld - %lld",
                    mCachedAlsWakeData[0], mAlsSensorWakeTimestamp);
            LOGV_IF(INPUT_DATA,
                    "HAL:input build_prox wake: %+8ld - %lld",
                    mCachedAlsWakeData[1], mAlsSensorWakeTimestamp);
        }
    }
#endif
    if (mask == DATA_FORMAT_6_AXIS) {
        /* if bias was applied to DMP bias,
           set status bits to disable gyro bias cal */
        int status = 0;
        if (mGyroBiasApplied == true) {
            LOGV_IF(0, "HAL:input dmp bias is used");
            status |= INV_QUAT_6AXIS;
        }
        status |= INV_CALIBRATED | INV_QUAT_6AXIS | INV_QUAT_3ELEMENT; /* default 32 (16/32bits) */
        adapter_build_quat(mCached6AxisQuaternionData,
                status,
                m6QuatSensorTimestamp);
        LOGV_IF(INPUT_DATA,
                "HAL:input build_quat-6x: %+8ld %+8ld %+8ld - %lld",
                mCached6AxisQuaternionData[0], mCached6AxisQuaternionData[1],
                mCached6AxisQuaternionData[2], m6QuatSensorTimestamp);
        latestTimestamp = m6QuatSensorTimestamp;
    }

    if (mask == DATA_FORMAT_9_AXIS) {
        int status = 0;
        if (mGyroBiasApplied == true) {
            LOGV_IF(0, "HAL:input dmp bias is used");
            status |= INV_QUAT_9AXIS;
        }
        status |= INV_CALIBRATED | INV_QUAT_9AXIS | INV_QUAT_3ELEMENT; /* default 32 (16/32bits) */
        adapter_build_quat(mCached9AxisQuaternionData,
                status,
                mQuatSensorTimestamp);
        LOGV_IF(INPUT_DATA,
                "HAL:input build_quat-9x: %+8ld %+8ld %+8ld - %d - %lld",
                mCached9AxisQuaternionData[0], mCached9AxisQuaternionData[1],
                mCached9AxisQuaternionData[2], mHeadingAccuracy, mQuatSensorTimestamp);
        latestTimestamp = mQuatSensorTimestamp;
    }

    if (mask == DATA_FORMAT_GEOMAG) {
        int status = 0;
        if (mGyroBiasApplied == true) {
            LOGV_IF(0, "HAL:input dmp bias is used");
            status |= INV_GEOMAG;
        }
        status |= INV_CALIBRATED | INV_GEOMAG | INV_QUAT_3ELEMENT; /* default 32
                                                                      (16/32bits) */
        adapter_build_quat(mCachedGeomagData,
                status,
                mGeoQuatSensorTimestamp);
        LOGV_IF(INPUT_DATA,
                "HAL:input build_quat-geomag: %+8ld %+8ld %+8ld - %d - %lld",
                mCachedGeomagData[0],
                mCachedGeomagData[1],
                mCachedGeomagData[2],
                mHeadingAccuracy,
                mGeoQuatSensorTimestamp);
        latestTimestamp = mGeoQuatSensorTimestamp;
    }


    if (mask == DATA_FORMAT_PED_QUAT) {
        /* if bias was applied to DMP bias,
           set status bits to disable gyro bias cal */
        int status = 0;
        if (mGyroBiasApplied == true) {
            LOGV_IF(0,
                    "HAL:input dmp bias is used");
            status |= INV_QUAT_6AXIS;
        }
        status |= INV_CALIBRATED | INV_QUAT_6AXIS | INV_QUAT_3ELEMENT; /* default 32 (16/32bits) */
        mCachedPedQuaternionData[0] = mCachedPedQuaternionData[0] << 16;
        mCachedPedQuaternionData[1] = mCachedPedQuaternionData[1] << 16;
        mCachedPedQuaternionData[2] = mCachedPedQuaternionData[2] << 16;
        adapter_build_quat(mCachedPedQuaternionData,
                status,
                m6QuatSensorTimestamp);

        LOGV_IF(INPUT_DATA,
                "HAL:HAL:input build_quat-ped_6x: %+8ld %+8ld %+8ld - %lld",
                mCachedPedQuaternionData[0], mCachedPedQuaternionData[1],
                mCachedPedQuaternionData[2], m6QuatSensorTimestamp);
        latestTimestamp = m6QuatSensorTimestamp;
    }
#ifdef IIO_LIGHT_SENSOR

    if ((mask == DATA_FORMAT_PRESSURE) && mPressureSensor->isIntegrated()) {
        int status = 0;
        if (mLocalSensorMask & INV_ONE_AXIS_PRESSURE) {
            latestTimestamp = mPressureTimestamp;
            mPressureUpdate = 1;
            adapter_build_pressure(mCachedPressureData,
                    status,
                    mPressureTimestamp);
            LOGV_IF(INPUT_DATA,
                    "HAL:input build_pressure: %+8ld - %lld",
                    mCachedPressureData, mPressureTimestamp);
        }
    }

    if ((mask == DATA_FORMAT_PRESSURE_WAKE) && mPressureSensor->isIntegrated()) {
        int status = 0;
        if (mLocalSensorMask & INV_ONE_AXIS_PRESSURE_WAKE) {
            latestTimestamp = mPressureWakeTimestamp;
            mPressureWakeUpdate = 1;
            adapter_build_pressure(mCachedPressureWakeData,
                    status,
                    mPressureWakeTimestamp);
            LOGV_IF(INPUT_DATA,
                    "HAL:input build_pressure wake: %+8ld - %lld",
                    mCachedPressureWakeData, mPressureWakeTimestamp);
        }
    }
#endif
    /* take the latest timestamp */
    if (mask == DATA_FORMAT_STEP) {
        /* work around driver output duplicate step detector bit */
        if (latestTimestamp > mStepSensorTimestamp) {
            mStepSensorTimestamp = latestTimestamp;
            LOGV_IF(INPUT_DATA,
                    "HAL:input build step: 1 - %lld", mStepSensorTimestamp);
        } else {
            mPedUpdate = 0;
        }
    }

    if (mask == DATA_FORMAT_STEP_COUNT) {
        mPendingMask |= 1LL << StepCounter;
        LOGV_IF(INPUT_DATA,
                "HAL:input build step count: %lld - %lld", mStepCount, mStepSensorTimestamp);
    }

    if (mask == DATA_FORMAT_STEP_COUNT_WAKE) {
        mPendingMask |= 1LL << StepCounter_Wake;
        LOGV_IF(INPUT_DATA,
                "HAL:input build step count: %lld - %lld", mStepCountWake, mStepSensorWakeTimestamp);
    }

    if (mask == DATA_FORMAT_PED_STANDALONE) {
        mPendingMask |= 1LL << StepDetector;
        LOGV_IF(INPUT_DATA,
                "HAL:input build step detector %lld", mStepSensorTimestamp);
    }

    if (mask == DATA_FORMAT_PED_STANDALONE_WAKE) {
        mPendingMask |= 1LL << StepDetector_Wake;
        LOGV_IF(INPUT_DATA,
                "HAL:input build step wake detector %lld", mStepSensorTimestamp);
    }
}

int MPLSensor::checkValidHeader(unsigned short data_format)
{
    LOGV_IF(ENG_VERBOSE && INPUT_DATA, "check data_format=%x", data_format);

    if(data_format == DATA_FORMAT_STEP ||
            data_format == DATA_FORMAT_COMPASS_ACCURACY ||
            data_format == DATA_FORMAT_ACCEL_ACCURACY ||
            data_format == DATA_FORMAT_GYRO_ACCURACY
      )
        return 1;

    if ((data_format == DATA_FORMAT_PED_STANDALONE) ||
            (data_format == DATA_FORMAT_STEP) ||
            (data_format == DATA_FORMAT_GEOMAG) ||
            (data_format == DATA_FORMAT_PED_STANDALONE_WAKE) ||
            (data_format == DATA_FORMAT_PED_QUAT) ||
            (data_format == DATA_FORMAT_6_AXIS) ||
            (data_format == DATA_FORMAT_9_AXIS) ||
            (data_format == DATA_FORMAT_ALS) ||
            (data_format == DATA_FORMAT_COMPASS) ||
            (data_format == DATA_FORMAT_RAW_COMPASS) ||
            (data_format == DATA_FORMAT_GYRO) ||
            (data_format == DATA_FORMAT_RAW_GYRO) ||
            (data_format == DATA_FORMAT_ACCEL) ||
            (data_format == DATA_FORMAT_PRESSURE) ||
            (data_format == DATA_FORMAT_EMPTY_MARKER) ||
            (data_format == DATA_FORMAT_MARKER) ||
            (data_format == DATA_FORMAT_ACCEL_WAKE) ||
            (data_format == DATA_FORMAT_RAW_COMPASS_WAKE) ||
            (data_format == DATA_FORMAT_COMPASS_WAKE) ||
            (data_format == DATA_FORMAT_RAW_GYRO_WAKE) ||
            (data_format == DATA_FORMAT_GYRO_WAKE) ||
            (data_format == DATA_FORMAT_ALS_WAKE) ||
            (data_format == DATA_FORMAT_PRESSURE_WAKE) ||
            (data_format == DATA_FORMAT_PED_QUAT_WAKE) ||
            (data_format == DATA_FORMAT_6_AXIS_WAKE) ||
            (data_format == DATA_FORMAT_9_AXIS_WAKE) ||
            (data_format == DATA_FORMAT_GEOMAG_WAKE) ||
            (data_format == DATA_FORMAT_STEP_COUNT) ||
            (data_format == DATA_FORMAT_STEP_COUNT_WAKE))
            return 1;
    else {
        LOGV_IF(ENG_VERBOSE, "bad data_format = %x", data_format);
        return 0;
    }
}

/* use for both MPUxxxx and third party compass */
void MPLSensor::buildCompassEvent(void)
{
    VHANDLER_LOG;

    int done = 0;

    done = mCompassSensor->readSample(mCachedCompassData, &mCompassTimestamp);

    if (done > 0) {
        int status = 0;
        if (mCompassSensor->providesCalibration()) {
            status = mCompassSensor->getAccuracy();
            status |= INV_CALIBRATED;
        }

        if (mLocalSensorMask & INV_THREE_AXIS_COMPASS)
			{
            adapter_build_compass(mCachedCompassData, status,
                    mCompassTimestamp);
            LOGV_IF(INPUT_DATA,
                    "HAL:input inv_build_compass: %+8ld %+8ld %+8ld - %lld",
                    mCachedCompassData[0], mCachedCompassData[1],
                    mCachedCompassData[2], mCompassTimestamp);
            mSkipReadEvents = 0;
        }
    }
}

int MPLSensor::getFd(void) const
{
    VFUNC_LOG;
    LOGV_IF(EXTRA_VERBOSE, "getFd returning %d", iio_fd);
    return iio_fd;
}

int MPLSensor::getAccelFd(void) const
{
    VFUNC_LOG;
    LOGV_IF(EXTRA_VERBOSE, "getAccelFd returning %d", accel_fd);
    return accel_fd;
}

int MPLSensor::getCompassFd(void) const
{
    VFUNC_LOG;
    int fd = mCompassSensor->getFd();
    LOGV_IF(EXTRA_VERBOSE, "getCompassFd returning %d", fd);
    return fd;
}

int MPLSensor::turnOffAccelFifo(void)
{
    VFUNC_LOG;
    int i, res = 0, tempFd;
    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            0, mpu.accel_fifo_enable, getTimestamp());
    res += write_sysfs_int(mpu.accel_fifo_enable, 0);
    return res;
}

int MPLSensor::turnOffGyroFifo(void)
{
    VFUNC_LOG;
    int i, res = 0, tempFd;
    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            0, mpu.gyro_fifo_enable, getTimestamp());
    res += write_sysfs_int(mpu.gyro_fifo_enable, 0);

    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            0, mpu.calib_gyro_enable, getTimestamp());
    res = write_sysfs_int(mpu.calib_gyro_enable, 0);
    return res;
}

int MPLSensor::getPollTime(void)
{
    VFUNC_LOG;
    return mPollTime;
}

bool MPLSensor::hasPendingEvents(void) const
{
    VFUNC_LOG;
    // if we are using the polling workaround, force the main
    // loop to check for data every time
    return (mPollTime != -1);
}

int MPLSensor::inv_read_temperature(long long *data)
{
    VHANDLER_LOG;

    int count = 0;
    char raw_buf[40];
    int raw = 0;

    long long timestamp = 0;

    memset(raw_buf, 0, sizeof(raw_buf));
    count = read_attribute_sensor(gyro_temperature_fd, raw_buf,
            sizeof(raw_buf));
    if(count < 0) {
        LOGE("HAL:error reading gyro temperature");
        return -1;
    }

    count = sscanf(raw_buf, "%d %lld", &raw, &timestamp);

    if(count < 0) {
        LOGW("HAL:error parsing gyro temperature count=%d", count);
        return -1;
    }

    LOGV_IF(ENG_VERBOSE && INPUT_DATA,
            "HAL:temperature raw = %d, timestamp = %lld, count = %d",
            raw, timestamp, count);
    data[0] = raw;
    data[1] = timestamp;

    return 0;
}

int MPLSensor::inv_read_dmp_state(int fd)
{
    VFUNC_LOG;

    if(fd < 0)
        return -1;

    int count = 0;
    char raw_buf[10];
    short raw = 0;

    memset(raw_buf, 0, sizeof(raw_buf));
    count = read_attribute_sensor(fd, raw_buf, sizeof(raw_buf));
    if(count < 1) {
        LOGE("HAL:error reading dmp state");
        close(fd);
        return -1;
    }
    count = sscanf(raw_buf, "%hd", &raw);
    if(count < 0) {
        LOGE("HAL:dmp state data is invalid");
        close(fd);
        return -1;
    }
    LOGV_IF(EXTRA_VERBOSE, "HAL:dmp state = %d, count = %d", raw, count);
    close(fd);
    return (int)raw;
}

int MPLSensor::inv_read_sensor_bias(int fd, int *data)
{
    VFUNC_LOG;

    if(fd == -1) {
        return -1;
    }

    char buf[50];
    char x[15], y[15], z[15];

    memset(buf, 0, sizeof(buf));
    int count = read_attribute_sensor(fd, buf, sizeof(buf));
    if(count < 1) {
        LOGE("HAL:Error reading gyro bias");
        return -1;
    }
    count = sscanf(buf, "%[^','],%[^','],%[^',']", x, y, z);
    if(count) {
        /* scale appropriately for MPL */
        LOGV_IF(ENG_VERBOSE,
                "HAL:pre-scaled bias: X:Y:Z (%d, %d, %d)",
                atoi(x), atoi(y), atoi(z));

        data[0] = (int)(atoi(x) / 10000 * (1L << 16));
        data[1] = (int)(atoi(y) / 10000 * (1L << 16));
        data[2] = (int)(atoi(z) / 10000 * (1L << 16));

        LOGV_IF(ENG_VERBOSE,
                "HAL:scaled bias: X:Y:Z (%d, %d, %d)",
                data[0], data[1], data[2]);
    }
    return 0;
}

#ifdef SENSORS_DEVICE_API_VERSION_1_4
int MPLSensor::inject_sensor_data(const sensors_event_t* data)
{
	char out_data[16] = {0, };
	char sysfs_path[MAX_SYSFS_NAME_LEN] = {0, };
	int size;
	FILE* fp = NULL;

	/*
	LOGE("HAL sensor_event handle=%d ts=%lld data=%.2f, %.2f, %.2f %.2f %.2f %.2f", data->sensor, data->timestamp,
			data->data[0], data->data[1], data->data[2], data->data[3], data->data[4], data->data[5]);
	*/

	inv_android_to_dmp((sensors_event_t*)data, out_data, &size);

	switch(data->type)
	{
		case SENSOR_TYPE_ACCELEROMETER :
			fp = fopen(mpu.misc_bin_poke_accel, "wb");
			break;
		case SENSOR_TYPE_GYROSCOPE_UNCALIBRATED:
			fp = fopen(mpu.misc_bin_poke_gyro, "wb");
			break;
		case SENSOR_TYPE_MAGNETIC_FIELD_UNCALIBRATED:
			fp = fopen(mpu.misc_bin_poke_mag, "wb");
			break;
		default :
			return -1;
	}

	if (fp == NULL) {
		LOGE("HAL: cannot open injection sysfs");
		return -1;
	}

	if (fwrite(out_data, 1, 12, fp) == 0)
	{
	    LOGE("HAL: write to data injection sysfs is failed");
	    return -1;
	}

	fclose(fp);

    return 0;
}

int MPLSensor::isDataInjectionSupported()
{
    FILE *fp = NULL;

    fp = fopen(mpu.info_poke_mode, "r");

    if (fp == NULL)
    {
        LOGI("HAL: Injection is not supported");
        return -1;
    }

    LOGI("HAL: Injection is supported");
    fclose(fp);
    return 0;
}

void MPLSensor::setDataInjectionMode(int mode)
{
    write_sysfs_int(mpu.info_poke_mode, mode);
}
#endif // end of #ifdef SENSORS_DEVICE_API_VERSION_1_4

/** fill in the sensor list based on which sensors are configured.
 *  return the number of configured sensors.
 *  parameter list must point to a memory region of at least 7*sizeof(sensor_t)
 *  parameter len gives the length of the buffer pointed to by list
 */
int MPLSensor::populateSensorList(struct sensor_t *list, int len)
{
    VFUNC_LOG;

    currentSensorList = sMplSensorList;
    int listSize = sizeof(sMplSensorList);
    if (strcmp(chip_ID, "ICM10320") == 0)
    {
        currentSensorList = sBaseSensorList;
        listSize = sizeof(sBaseSensorList);
        LOGI("ICM 10320 sensor list is used");
    } else if (strcmp(chip_ID, "ICM20608D") == 0)
    {
	currentSensorList = s20608DSensorList;
	listSize = sizeof(s20608DSensorList);
	LOGI("ICM 20608D/20609/20689 sensor list is used");
    } else if (strcmp(chip_ID, "ICM20602") == 0)
    {
        currentSensorList = s20602SensorList;
        listSize = sizeof(s20602SensorList);
        LOGI("ICM 20602 sensor list is used");
    } else if (strcmp(chip_ID, "ICM20690") == 0)
    {
    	#if 0
        if(mCompassSensor->isCompassSensorPresent()){
            currentSensorList = s20690SensorList;
            listSize = sizeof(s20690SensorList);
            LOGI("ICM 20690 sensor list is used");
        } else {
            currentSensorList = s20690WithoutCompassSensorList;
            listSize = sizeof(s20690WithoutCompassSensorList);
            LOGI("ICM 20690 sensor list is used (without compass)");
        }
		#endif
    }

    if(len <
            (int)((listSize / sizeof(sensor_t)) * sizeof(sensor_t))) {
        LOGE("HAL:sensor list too small, not populating.");
        return -(listSize / sizeof(sensor_t));
    }

    mNumSensors = listSize / sizeof(sensor_t);
#ifdef IIO_LIGHT_SENSOR
    if(mPressureSensorPresent)
        mNumSensors += mPressureSensor->populateSensorList(currentSensorList + mNumSensors,
                mNumSensors);

    if(mLightSensorPresent)
        mNumSensors += mLightSensor->populateSensorList(currentSensorList + mNumSensors,
                mNumSensors);
#endif
    if(mCustomSensorPresent)
    {
	memcpy(currentSensorList + mNumSensors, sCustomSensorList, sizeof(sCustomSensorList));
	mNumSensors += sizeof(sCustomSensorList) / sizeof(struct sensor_t);
    }

    /* fill in the base values */
    memcpy(list, currentSensorList, sizeof (struct sensor_t) * mNumSensors);


 //   if(chip_ID == NULL)
 	{
        LOGE("HAL:Can not get gyro/accel id");
    }

    if ((strcmp(chip_ID, "ICM10320") != 0) && (strcmp(chip_ID, "ICM20608D") != 0) &&
        (strcmp(chip_ID, "ICM20602") != 0) && (strcmp(chip_ID, "ICM20690") != 0)) {
        // strcmp result can be -1 or 1, If campare result is same or not, should check with != 0
        LOGW("HAL:Set sensor information");
        /* fill in gyro/accel values */
        fillAccel(chip_ID, list);
        /* fill in geomagnetic vecotr values */
        fillGMRV(list);
        /* fill in Significant motion values */
        fillSignificantMotion(list);
        /* fill in tilt values */
        fillTilt(list);
        /* fill in pick up values */
        fillPickup(list);

        // TODO: need fixes for unified HAL and 3rd-party solution
        mCompassSensor->fillList(&list[MagneticField]);
        mCompassSensor->fillList(&list[RawMagneticField]);
        mCompassSensor->fillList(&list[MagneticField_Wake]);
        mCompassSensor->fillList(&list[RawMagneticField_Wake]);
	if (mCustomSensorPresent) {
	    mCompassSensor->fillList(&list[MagneticFieldRaw]);
	}
        /* fill in gyro/accel values */
        fillGyro(chip_ID, list);

        if(1) {
            /* all sensors will be added to the list
               fill in orientation values */
            fillOrientation(list);
            /* fill in rotation vector values */
            fillRV(list);
            /* fill in game rotation vector values */
            fillGRV(list);
            /* fill in gravity values */
            fillGravity(list);
            /* fill in Linear accel values */
            fillLinearAccel(list);
        } else {
            /* no 9-axis sensors, zero fill that part of the list */
            mNumSensors = 3;
            memset(list + 3, 0, 4 * sizeof(struct sensor_t));
        }
#ifdef IIO_LIGHT_SENSOR
        if (mPressureSensorPresent) {
            if (mPressureSensor != NULL) {
                mPressureSensor->fillList(&list[Pressure]);
                mPressureSensor->fillList(&list[Pressure_Wake]);

            }
        }
        if (mLightSensorPresent) {
            if (mLightSensor != NULL) {
                mLightSensor->fillLightList(&list[Light]);
                mLightSensor->fillProxList(&list[Proximity]);

                mLightSensor->fillLightList(&list[Light_Wake]);
                mLightSensor->fillProxList(&list[Proximity_Wake]);
            }
        }
#endif
        memcpy(currentSensorList, list, sizeof (struct sensor_t) * mNumSensors);
    }
    fillSensorMaskArray();

    return mNumSensors;
}
void MPLSensor::fillSensorMaskArray()
{
	uint32_t i, j, ind;
	VFUNC_LOG;

	mCurrentSensorMask = new MPLSensor::sensor_mask[ID_NUMBER];
	mSysfsMask         = new MPLSensor::sysfs_mask[ARRAY_SIZE(sysfsId)];

	for (i = 0; i < ID_NUMBER; i++) {
		mCurrentSensorMask[i].sensorMask = 0;
		mCurrentSensorMask[i].engineRateAddr = 0;
	}
	for (i = 0; i < ARRAY_SIZE(sysfsId); i++) {
		mSysfsMask[i].sensorMask = 0;
		mSysfsMask[i].setRate = NULL;
		mSysfsMask[i].second_setRate = NULL;
		mSysfsMask[i].enable = NULL;
		mSysfsMask[i].second_enable = NULL;
	}

	for (ind = 0; ind < mNumSensors; ind++) {
		i = currentSensorList[ind].handle;
		mCurrentSensorMask[i].sname = currentSensorList[ind].name;

		switch (i) {
		case SENSORS_GYROSCOPE_HANDLE:
			if (mCalibrationMode & mpl_gyro) {
				mCurrentSensorMask[i].sensorMask = INV_THREE_AXIS_RAW_GYRO;
				mCurrentSensorMask[i].engineMask = INV_THREE_AXIS_RAW_GYRO;
			} else {
				mCurrentSensorMask[i].sensorMask = INV_THREE_AXIS_GYRO;
				mCurrentSensorMask[i].engineMask = INV_THREE_AXIS_GYRO;
			}
			break;
		case SENSORS_RAW_GYROSCOPE_HANDLE:
		case SENSORS_GYROSCOPE_RAW_HANDLE:
			mCurrentSensorMask[i].sensorMask = INV_THREE_AXIS_RAW_GYRO;
			mCurrentSensorMask[i].engineMask = INV_THREE_AXIS_RAW_GYRO;
			break;
		case SENSORS_ACCELERATION_HANDLE:
		case SENSORS_ACCELERATION_RAW_HANDLE:
			mCurrentSensorMask[i].sensorMask = INV_THREE_AXIS_ACCEL;
			mCurrentSensorMask[i].engineMask = INV_THREE_AXIS_ACCEL;
			break;
		case SENSORS_MAGNETIC_FIELD_HANDLE:
			if (mCalibrationMode & mpl_compass) {
				mCurrentSensorMask[i].sensorMask = INV_THREE_AXIS_RAW_COMPASS;
				mCurrentSensorMask[i].engineMask = INV_THREE_AXIS_RAW_COMPASS;
			} else {
				mCurrentSensorMask[i].sensorMask = INV_THREE_AXIS_COMPASS;
				mCurrentSensorMask[i].engineMask = INV_THREE_AXIS_COMPASS;
			}
			break;
		case SENSORS_RAW_MAGNETIC_FIELD_HANDLE:
		case SENSORS_MAGNETIC_FIELD_RAW_HANDLE:
			mCurrentSensorMask[i].sensorMask = INV_THREE_AXIS_RAW_COMPASS;
			mCurrentSensorMask[i].engineMask = INV_THREE_AXIS_RAW_COMPASS;
			break;
		case SENSORS_ORIENTATION_HANDLE:
		case SENSORS_ROTATION_VECTOR_HANDLE:
			if (mCalibrationMode & mpl_quat) {
				mCurrentSensorMask[i].sensorMask = (INV_THREE_AXIS_RAW_GYRO | INV_THREE_AXIS_ACCEL
									| INV_THREE_AXIS_RAW_COMPASS);
				mCurrentSensorMask[i].engineMask = INV_THREE_AXIS_RAW_GYRO;
			} else {
				mCurrentSensorMask[i].sensorMask = VIRTUAL_SENSOR_9AXES_MASK;
				mCurrentSensorMask[i].engineMask = VIRTUAL_SENSOR_9AXES_MASK;
			}
			break;
		case SENSORS_GAME_ROTATION_VECTOR_HANDLE:
			if (mCalibrationMode & mpl_quat) {
				mCurrentSensorMask[i].sensorMask = (INV_THREE_AXIS_RAW_GYRO | INV_THREE_AXIS_ACCEL);
				mCurrentSensorMask[i].engineMask = INV_THREE_AXIS_RAW_GYRO;
			} else {
				mCurrentSensorMask[i].sensorMask = VIRTUAL_SENSOR_GYRO_6AXES_MASK;
				mCurrentSensorMask[i].engineMask = VIRTUAL_SENSOR_GYRO_6AXES_MASK;
			}
			break;
		case SENSORS_LINEAR_ACCEL_HANDLE:
		case SENSORS_GRAVITY_HANDLE:
			if (mCalibrationMode & mpl_quat) {
				mCurrentSensorMask[i].sensorMask = (INV_THREE_AXIS_RAW_GYRO | INV_THREE_AXIS_ACCEL);
				mCurrentSensorMask[i].engineMask = INV_THREE_AXIS_RAW_GYRO;
			} else {
				mCurrentSensorMask[i].sensorMask = (VIRTUAL_SENSOR_GYRO_6AXES_MASK | INV_THREE_AXIS_ACCEL);
				mCurrentSensorMask[i].engineMask = VIRTUAL_SENSOR_GYRO_6AXES_MASK;
			}
			break;
		case SENSORS_GEOMAGNETIC_ROTATION_VECTOR_HANDLE:
			if (mCalibrationMode & mpl_quat) {
				mCurrentSensorMask[i].sensorMask = (INV_THREE_AXIS_ACCEL | INV_THREE_AXIS_RAW_COMPASS);
				mCurrentSensorMask[i].engineMask = INV_THREE_AXIS_ACCEL;
			} else {
				mCurrentSensorMask[i].sensorMask = VIRTUAL_SENSOR_MAG_6AXES_MASK;
				mCurrentSensorMask[i].engineMask = VIRTUAL_SENSOR_MAG_6AXES_MASK;
			}
			break;
		case SENSORS_PRESSURE_HANDLE:
			mCurrentSensorMask[i].sensorMask = INV_ONE_AXIS_PRESSURE;
			mCurrentSensorMask[i].engineMask = INV_ONE_AXIS_PRESSURE;
			break;
		case SENSORS_LIGHT_HANDLE:
			mCurrentSensorMask[i].sensorMask = INV_ONE_AXIS_LIGHT;
			mCurrentSensorMask[i].engineMask = INV_ONE_AXIS_LIGHT;
			break;
		case SENSORS_PROXIMITY_HANDLE:
			mCurrentSensorMask[i].sensorMask = INV_ONE_AXIS_LIGHT;
			mCurrentSensorMask[i].engineMask = INV_ONE_AXIS_LIGHT;
			break;
		case SENSORS_GYROSCOPE_WAKEUP_HANDLE:
			if (mCalibrationMode & mpl_gyro) {
				mCurrentSensorMask[i].sensorMask = INV_THREE_AXIS_RAW_GYRO_WAKE;
				mCurrentSensorMask[i].engineMask = INV_THREE_AXIS_RAW_GYRO_WAKE;
			} else {
				mCurrentSensorMask[i].sensorMask = INV_THREE_AXIS_GYRO_WAKE;
				mCurrentSensorMask[i].engineMask = INV_THREE_AXIS_GYRO_WAKE;
			}
			break;
		case SENSORS_RAW_GYROSCOPE_WAKEUP_HANDLE:
			mCurrentSensorMask[i].sensorMask = INV_THREE_AXIS_RAW_GYRO_WAKE;
			mCurrentSensorMask[i].engineMask = INV_THREE_AXIS_RAW_GYRO_WAKE;
			break;
		case SENSORS_ACCELERATION_WAKEUP_HANDLE:
			mCurrentSensorMask[i].sensorMask = INV_THREE_AXIS_ACCEL_WAKE;
			mCurrentSensorMask[i].engineMask = INV_THREE_AXIS_ACCEL_WAKE;
			break;
		case SENSORS_MAGNETIC_FIELD_WAKEUP_HANDLE:
			if (mCalibrationMode & mpl_compass) {
				mCurrentSensorMask[i].sensorMask = INV_THREE_AXIS_RAW_COMPASS_WAKE;
				mCurrentSensorMask[i].engineMask = INV_THREE_AXIS_RAW_COMPASS_WAKE;
			} else {
				mCurrentSensorMask[i].sensorMask = INV_THREE_AXIS_COMPASS_WAKE;
				mCurrentSensorMask[i].engineMask = INV_THREE_AXIS_COMPASS_WAKE;
			}
			break;
		case SENSORS_RAW_MAGNETIC_FIELD_WAKEUP_HANDLE:
			mCurrentSensorMask[i].sensorMask = INV_THREE_AXIS_RAW_COMPASS_WAKE;
			mCurrentSensorMask[i].engineMask = INV_THREE_AXIS_RAW_COMPASS_WAKE;
			break;
		case SENSORS_ORIENTATION_WAKEUP_HANDLE:
		case SENSORS_ROTATION_VECTOR_WAKEUP_HANDLE:
			if (mCalibrationMode & mpl_quat) {
				mCurrentSensorMask[i].sensorMask =  (INV_THREE_AXIS_RAW_GYRO_WAKE | INV_THREE_AXIS_ACCEL_WAKE
									| INV_THREE_AXIS_RAW_COMPASS_WAKE);
				mCurrentSensorMask[i].engineMask = INV_THREE_AXIS_RAW_GYRO_WAKE;
			} else {
				mCurrentSensorMask[i].sensorMask = VIRTUAL_SENSOR_9AXES_MASK_WAKE;
				mCurrentSensorMask[i].engineMask = VIRTUAL_SENSOR_9AXES_MASK_WAKE;
			}
			break;
		case SENSORS_GAME_ROTATION_VECTOR_WAKEUP_HANDLE:
			if (mCalibrationMode & mpl_quat) {
				mCurrentSensorMask[i].sensorMask = (INV_THREE_AXIS_RAW_GYRO_WAKE | INV_THREE_AXIS_ACCEL_WAKE);
				mCurrentSensorMask[i].engineMask = INV_THREE_AXIS_RAW_GYRO_WAKE;
			} else {
				mCurrentSensorMask[i].sensorMask = VIRTUAL_SENSOR_GYRO_6AXES_MASK_WAKE;
				mCurrentSensorMask[i].engineMask = VIRTUAL_SENSOR_GYRO_6AXES_MASK_WAKE;
			}
			break;
		case SENSORS_LINEAR_ACCEL_WAKEUP_HANDLE:
		case SENSORS_GRAVITY_WAKEUP_HANDLE:
			if (mCalibrationMode & mpl_quat) {
				mCurrentSensorMask[i].sensorMask = (INV_THREE_AXIS_RAW_GYRO_WAKE | INV_THREE_AXIS_ACCEL_WAKE);
				mCurrentSensorMask[i].engineMask = INV_THREE_AXIS_RAW_GYRO_WAKE;
			} else {
				mCurrentSensorMask[i].sensorMask = (VIRTUAL_SENSOR_GYRO_6AXES_MASK_WAKE | INV_THREE_AXIS_ACCEL_WAKE);
				mCurrentSensorMask[i].engineMask = VIRTUAL_SENSOR_GYRO_6AXES_MASK_WAKE;
			}
			break;
		case SENSORS_GEOMAGNETIC_ROTATION_VECTOR_WAKEUP_HANDLE:
			if (mCalibrationMode & mpl_quat) {
				mCurrentSensorMask[i].sensorMask = (INV_THREE_AXIS_ACCEL_WAKE
									| INV_THREE_AXIS_RAW_COMPASS_WAKE);
				mCurrentSensorMask[i].engineMask = INV_THREE_AXIS_ACCEL_WAKE;
			} else {
				mCurrentSensorMask[i].sensorMask = VIRTUAL_SENSOR_MAG_6AXES_MASK_WAKE;
				mCurrentSensorMask[i].engineMask = VIRTUAL_SENSOR_MAG_6AXES_MASK_WAKE;
			}
			break;
		case SENSORS_PRESSURE_WAKEUP_HANDLE:
			mCurrentSensorMask[i].sensorMask = INV_ONE_AXIS_PRESSURE_WAKE;
			mCurrentSensorMask[i].engineMask = INV_ONE_AXIS_PRESSURE_WAKE;
			break;
		case SENSORS_LIGHT_WAKEUP_HANDLE:
			mCurrentSensorMask[i].sensorMask = INV_ONE_AXIS_LIGHT_WAKE;
			mCurrentSensorMask[i].engineMask = INV_ONE_AXIS_LIGHT_WAKE;
			break;
		case SENSORS_PROXIMITY_WAKEUP_HANDLE:
			mCurrentSensorMask[i].sensorMask = INV_ONE_AXIS_LIGHT_WAKE;
			mCurrentSensorMask[i].engineMask = INV_ONE_AXIS_LIGHT_WAKE;
			break;
		default:
			break;
		}
	}

	mNumSysfs = 0;
	for (ind = 0; ind < ARRAY_SIZE(sysfsId); ind++) {
		bool hasSys;

		hasSys = false;
		for (j = 0; j < ID_NUMBER; j++) {
			if (mCurrentSensorMask[j].sensorMask) {
				if (sysfsId[ind] & mCurrentSensorMask[j].sensorMask) {
					hasSys = true;
					break;
				}
			}
		}
		if (hasSys) {
			i = mNumSysfs;
			mNumSysfs++;
			mSysfsMask[i].sensorMask = sysfsId[ind];
			if (mCalibrationMode & mpl)
				mSysfsMask[i].minimumNonBatchRate = 10000000;
			else
				mSysfsMask[i].minimumNonBatchRate = NS_PER_SECOND;

			switch (sysfsId[ind]) {
			case INV_THREE_AXIS_GYRO:
				mSysfsMask[i].enable = &MPLSensor::enableGyro;
				mSysfsMask[i].setRate = &MPLSensor::setGyroRate;
				break;
			case INV_THREE_AXIS_RAW_GYRO:
				mSysfsMask[i].enable = &MPLSensor::enableRawGyro;
				mSysfsMask[i].setRate = &MPLSensor::setRawGyroRate;
				if (!(mCalibrationMode & mpl_gyro)) {
					mSysfsMask[i].second_enable = &MPLSensor::enableGyro;
					mSysfsMask[i].second_setRate = &MPLSensor::setGyroRate;
				}
				break;
			case INV_THREE_AXIS_ACCEL:
				mSysfsMask[i].enable = &MPLSensor::enableAccel;
				mSysfsMask[i].setRate = &MPLSensor::setAccelRate;
				break;
			case INV_THREE_AXIS_COMPASS:
				mSysfsMask[i].enable = &MPLSensor::enableCompass;
				mSysfsMask[i].setRate = &MPLSensor::setMagRate;
				mSysfsMask[i].minimumNonBatchRate = NS_PER_SECOND;
				break;
			case INV_THREE_AXIS_RAW_COMPASS:
				mSysfsMask[i].enable = &MPLSensor::enableRawCompass;
				mSysfsMask[i].setRate = &MPLSensor::setRawMagRate;
				if (!(mCalibrationMode & mpl_compass)) {
					mSysfsMask[i].second_enable = &MPLSensor::enableCompass;
					mSysfsMask[i].second_setRate = &MPLSensor::setMagRate;
				}
				mSysfsMask[i].minimumNonBatchRate = NS_PER_SECOND;
				break;
			case VIRTUAL_SENSOR_9AXES_MASK:
				mSysfsMask[i].enable = &MPLSensor::enable9AxisQuaternion;
				mSysfsMask[i].setRate = &MPLSensor::set9AxesRate;
				break;
			case VIRTUAL_SENSOR_GYRO_6AXES_MASK:
				mSysfsMask[i].enable = &MPLSensor::enableLPQuaternion;
				mSysfsMask[i].setRate = &MPLSensor::set6AxesRate;
				break;
			case VIRTUAL_SENSOR_MAG_6AXES_MASK:
				mSysfsMask[i].enable = &MPLSensor::enableCompass6AxisQuaternion;
				mSysfsMask[i].setRate = &MPLSensor::set6AxesMagRate;
				break;
	#ifdef IIO_LIGHT_SENSOR
			case INV_ONE_AXIS_PRESSURE:
				mSysfsMask[i].enable = &MPLSensor::enablePressure;
				mSysfsMask[i].setRate = &MPLSensor::setPressureRate;
				break;
			case INV_ONE_AXIS_LIGHT:
				mSysfsMask[i].enable = &MPLSensor::enableLight;
				mSysfsMask[i].setRate = &MPLSensor::setLightRate;
				break;
	#endif
			case INV_THREE_AXIS_GYRO_WAKE:
				mSysfsMask[i].enable = &MPLSensor::enableGyroWake;
				mSysfsMask[i].setRate = &MPLSensor::setGyroRateWake;
				break;
			case INV_THREE_AXIS_RAW_GYRO_WAKE:
				mSysfsMask[i].enable = &MPLSensor::enableRawGyroWake;
				mSysfsMask[i].setRate = &MPLSensor::setRawGyroRateWake;
				if (!(mCalibrationMode & mpl_gyro)) {
					mSysfsMask[i].second_enable = &MPLSensor::enableGyroWake;
					mSysfsMask[i].second_setRate = &MPLSensor::setGyroRateWake;
				}
				break;
			case INV_THREE_AXIS_ACCEL_WAKE:
				mSysfsMask[i].enable = &MPLSensor::enableAccelWake;
				mSysfsMask[i].setRate = &MPLSensor::setAccelRateWake;
				break;
			case INV_THREE_AXIS_COMPASS_WAKE:
				mSysfsMask[i].enable = &MPLSensor::enableCompassWake;
				mSysfsMask[i].setRate = &MPLSensor::setMagRateWake;
				mSysfsMask[i].minimumNonBatchRate = NS_PER_SECOND;
				break;
			case INV_THREE_AXIS_RAW_COMPASS_WAKE:
				mSysfsMask[i].enable = &MPLSensor::enableRawCompassWake;
				mSysfsMask[i].setRate = &MPLSensor::setRawMagRateWake;
				if (!(mCalibrationMode & mpl_compass)) {
					mSysfsMask[i].second_enable = &MPLSensor::enableCompassWake;
					mSysfsMask[i].second_setRate = &MPLSensor::setMagRateWake;
				}
				mSysfsMask[i].minimumNonBatchRate = NS_PER_SECOND;
				break;
			case VIRTUAL_SENSOR_9AXES_MASK_WAKE:
				mSysfsMask[i].enable = &MPLSensor::enable9AxisQuaternionWake;
				mSysfsMask[i].setRate = &MPLSensor::set9AxesRateWake;
				break;
			case VIRTUAL_SENSOR_GYRO_6AXES_MASK_WAKE:
				mSysfsMask[i].enable = &MPLSensor::enableLPQuaternionWake;
				mSysfsMask[i].setRate = &MPLSensor::set6AxesRateWake;
				break;
			case VIRTUAL_SENSOR_MAG_6AXES_MASK_WAKE:
				mSysfsMask[i].enable = &MPLSensor::enableCompass6AxisQuaternionWake;
				mSysfsMask[i].setRate = &MPLSensor::set6AxesMagRateWake;
				break;
		#ifdef IIO_LIGHT_SENSOR
			case INV_ONE_AXIS_PRESSURE_WAKE:
				mSysfsMask[i].enable = &MPLSensor::enablePressureWake;
				mSysfsMask[i].setRate = &MPLSensor::setPressureRateWake;
				break;
			case INV_ONE_AXIS_LIGHT_WAKE:
				mSysfsMask[i].enable = &MPLSensor::enableLightWake;
				mSysfsMask[i].setRate = &MPLSensor::setLightRateWake;
				break;
		#endif
			default:
				break;
			}
		}
	}
	for (i = 0; i < ID_NUMBER; i++) {
		for (j = 0; j < mNumSysfs; j++) {
			if (mSysfsMask[j].sensorMask == mCurrentSensorMask[i].engineMask) {
				LOGI("HAL:sensor=%s, engine=%d, sensorid=%d", mCurrentSensorMask[i].sname.string(), j, i);
				mCurrentSensorMask[i].engineRateAddr = &mSysfsMask[j].engineRate;
			}
		}
	}
	LOGI("HAL:Sysfs num=%d, total=%d", mNumSysfs, ARRAY_SIZE(sysfsId));

	return;
}
void MPLSensor::fillAccel(const char* accel, struct sensor_t *list)
{
    VFUNC_LOG;

    if (accel != NULL && strcmp(accel, "ICM10320") == 0) {
        list[Accelerometer].maxRange = ACCEL_ICM20645_RANGE;
        list[Accelerometer].resolution = ACCEL_ICM20645_RESOLUTION;
        list[Accelerometer].power = ACCEL_ICM20645_POWER;
        list[Accelerometer].minDelay = ACCEL_ICM20645_MINDELAY;
        list[Accelerometer].maxDelay = ACCEL_ICM20645_MAXDELAY;
    } else if (accel != NULL && strcmp(accel, "ICM20628") == 0) {
        list[Accelerometer].maxRange = ACCEL_ICM20645_RANGE;
        list[Accelerometer].resolution = ACCEL_ICM20645_RESOLUTION;
        list[Accelerometer].power = ACCEL_ICM20645_POWER;
        list[Accelerometer].minDelay = ACCEL_ICM20645_MINDELAY;
        list[Accelerometer].maxDelay = ACCEL_ICM20645_MAXDELAY;
    } else if (accel != NULL && strcmp(accel, "ICM20645") == 0) {
        list[Accelerometer].maxRange = ACCEL_ICM20645_RANGE;
        list[Accelerometer].resolution = ACCEL_ICM20645_RESOLUTION;
        list[Accelerometer].power = ACCEL_ICM20645_POWER;
        list[Accelerometer].minDelay = ACCEL_ICM20645_MINDELAY;
        list[Accelerometer].maxDelay = ACCEL_ICM20645_MAXDELAY;
    }  else if (accel != NULL && strcmp(accel, "ICM20648") == 0) {
        list[Accelerometer].maxRange = ACCEL_ICM20648_RANGE;
        list[Accelerometer].resolution = ACCEL_ICM20648_RESOLUTION;
        list[Accelerometer].power = ACCEL_ICM20648_POWER;
        list[Accelerometer].minDelay = ACCEL_ICM20648_MINDELAY;
        list[Accelerometer].maxDelay = ACCEL_ICM20648_MAXDELAY;
    } else if (accel != NULL && strcmp(accel, "ICM30645") == 0) {
        list[Accelerometer].maxRange = ACCEL_ICM30645_RANGE;
        list[Accelerometer].resolution = ACCEL_ICM30645_RESOLUTION;
        list[Accelerometer].power = ACCEL_ICM30645_POWER;
        list[Accelerometer].minDelay = ACCEL_ICM30645_MINDELAY;
        list[Accelerometer].maxDelay = ACCEL_ICM30645_MAXDELAY;
    } else {
        LOGE("HAL:unknown accel id %s -- "
                "params default to icm20628 and might be wrong.",
                accel);
        list[Accelerometer].maxRange = ACCEL_ICM20645_RANGE;
        list[Accelerometer].resolution = ACCEL_ICM20645_RESOLUTION;
        list[Accelerometer].power = ACCEL_ICM20645_POWER;
        list[Accelerometer].minDelay = ACCEL_ICM20645_MINDELAY;
        list[Accelerometer].maxDelay = ACCEL_ICM20645_MAXDELAY;
    }

    list[Accelerometer_Wake].maxRange = list[Accelerometer].maxRange;
    list[Accelerometer_Wake].resolution = list[Accelerometer].resolution;
    list[Accelerometer_Wake].power = list[Accelerometer].power;
    list[Accelerometer_Wake].minDelay = list[Accelerometer].minDelay;
    list[Accelerometer_Wake].maxDelay = list[Accelerometer].maxDelay;

    if (mCustomSensorPresent) {
        list[AccelerometerRaw].maxRange = list[Accelerometer].maxRange;
        list[AccelerometerRaw].resolution = list[Accelerometer].resolution;
        list[AccelerometerRaw].power = list[Accelerometer].power;
        list[AccelerometerRaw].minDelay = list[Accelerometer].minDelay;
        list[AccelerometerRaw].maxDelay = list[Accelerometer].maxDelay;
    }

    return;
}

void MPLSensor::fillGyro(const char* gyro, struct sensor_t *list)
{
    VFUNC_LOG;

    if( gyro != NULL && strcmp(gyro, "ICM20628") == 0) {
        list[Gyro].maxRange = GYRO_ICM20645_RANGE;
        list[Gyro].resolution = GYRO_ICM20645_RESOLUTION;
        list[Gyro].power = GYRO_ICM20645_POWER;
        list[Gyro].minDelay = GYRO_ICM20645_MINDELAY;
        list[Gyro].maxDelay = GYRO_ICM20645_MAXDELAY;
    } else if ( gyro != NULL && strcmp(gyro, "ICM20645") == 0) {
        list[Gyro].maxRange = GYRO_ICM20645_RANGE;
        list[Gyro].resolution = GYRO_ICM20645_RESOLUTION;
        list[Gyro].power = GYRO_ICM20645_POWER;
        list[Gyro].minDelay = GYRO_ICM20645_MINDELAY;
        list[Gyro].maxDelay = GYRO_ICM20645_MAXDELAY;
    } else if ( gyro != NULL && strcmp(gyro, "ICM20648") == 0) {
        list[Gyro].maxRange = GYRO_ICM20648_RANGE;
        list[Gyro].resolution = GYRO_ICM20648_RESOLUTION;
        list[Gyro].power = GYRO_ICM20648_POWER;
        list[Gyro].minDelay = GYRO_ICM20648_MINDELAY;
        list[Gyro].maxDelay = GYRO_ICM20648_MAXDELAY;
    } else if( gyro != NULL && strcmp(gyro, "ICM30645") == 0) {
        list[Gyro].maxRange = GYRO_ICM30645_RANGE;
        list[Gyro].resolution = GYRO_ICM30645_RESOLUTION;
        list[Gyro].power = GYRO_ICM30645_POWER;
        list[Gyro].minDelay = GYRO_ICM30645_MINDELAY;
        list[Gyro].maxDelay = GYRO_ICM30645_MAXDELAY;
    } else if( gyro != NULL && strcmp(gyro, "ICM30648") == 0) {
        list[Gyro].maxRange = GYRO_ICM30648_RANGE;
        list[Gyro].resolution = GYRO_ICM30648_RESOLUTION;
        list[Gyro].power = GYRO_ICM30648_POWER;
        list[Gyro].minDelay = GYRO_ICM30648_MINDELAY;
        list[Gyro].maxDelay = GYRO_ICM30648_MAXDELAY;
    } else {
        LOGE("HAL:unknown gyro id -- gyro params will be wrong.");
        LOGE("HAL:default to use icm20625 params");
        list[Gyro].maxRange = GYRO_ICM20645_RANGE;
        list[Gyro].resolution = GYRO_ICM20645_RESOLUTION;
        list[Gyro].power = GYRO_ICM20645_POWER;
        list[Gyro].minDelay = GYRO_ICM20645_MINDELAY;
        list[Gyro].maxDelay = GYRO_ICM20645_MAXDELAY;
    }

    list[RawGyro].maxRange = list[Gyro].maxRange;
    list[RawGyro].resolution = list[Gyro].resolution;
    list[RawGyro].power = list[Gyro].power;
    list[RawGyro].minDelay = list[Gyro].minDelay;
    list[RawGyro].maxDelay = list[Gyro].maxDelay;

    list[Gyro_Wake].maxRange = list[Gyro].maxRange;
    list[Gyro_Wake].resolution = list[Gyro].resolution;
    list[Gyro_Wake].power = list[Gyro].power;
    list[Gyro_Wake].minDelay = list[Gyro].minDelay;
    list[Gyro_Wake].maxDelay = list[Gyro].maxDelay;

    list[RawGyro_Wake].maxRange = list[Gyro].maxRange;
    list[RawGyro_Wake].resolution = list[Gyro].resolution;
    list[RawGyro_Wake].power = list[Gyro].power;
    list[RawGyro_Wake].minDelay = list[Gyro].minDelay;
    list[RawGyro_Wake].maxDelay = list[Gyro].maxDelay;

    if (mCustomSensorPresent) {
	list[GyroRaw].maxRange = list[Gyro].maxRange;
	list[GyroRaw].resolution = list[Gyro].resolution;
	list[GyroRaw].power = list[Gyro].power;
	list[GyroRaw].minDelay = list[Gyro].minDelay;
	list[GyroRaw].maxDelay = list[Gyro].maxDelay;
    }

    return;
}

/* fillRV depends on values of gyro, accel and compass in the list */
void MPLSensor::fillRV(struct sensor_t *list)
{
    VFUNC_LOG;

    /* compute power on the fly */
    list[RotationVector].power = list[Gyro].power +
        list[Accelerometer].power +
        list[MagneticField].power;
    list[RotationVector].resolution = .00001;
    list[RotationVector].maxRange = 1.0;
    list[RotationVector].minDelay = list[Gyro].minDelay;
    list[RotationVector].maxDelay = list[Gyro].maxDelay;

    list[RotationVector_Wake].power = list[Gyro].power +
        list[Accelerometer].power +
        list[MagneticField].power;
    list[RotationVector_Wake].resolution = .00001;
    list[RotationVector_Wake].maxRange = 1.0;
    list[RotationVector_Wake].minDelay = list[Gyro].minDelay;
    list[RotationVector_Wake].maxDelay = list[Gyro].maxDelay;

    return;
}

/* fillGMRV depends on values of accel and mag in the list */
void MPLSensor::fillGMRV(struct sensor_t *list)
{
    VFUNC_LOG;

    /* compute power on the fly */
    list[GeomagneticRotationVector].power = list[Accelerometer].power +
        list[MagneticField].power;
    list[GeomagneticRotationVector].resolution = .00001;
    list[GeomagneticRotationVector].maxRange = 1.0;
    list[GeomagneticRotationVector].minDelay = list[Accelerometer].minDelay;
    list[GeomagneticRotationVector].maxDelay = list[Accelerometer].maxDelay;

    list[GeomagneticRotationVector_Wake].power = list[Accelerometer].power +
        list[MagneticField].power;
    list[GeomagneticRotationVector_Wake].resolution = .00001;
    list[GeomagneticRotationVector_Wake].maxRange = 1.0;
    list[GeomagneticRotationVector_Wake].minDelay = list[Accelerometer].minDelay;
    list[GeomagneticRotationVector_Wake].maxDelay = list[Accelerometer].maxDelay;

    return;
}

/* fillGRV depends on values of gyro and accel in the list */
void MPLSensor::fillGRV(struct sensor_t *list)
{
    VFUNC_LOG;

    /* compute power on the fly */
    list[GameRotationVector].power = list[Gyro].power +
        list[Accelerometer].power;
    list[GameRotationVector].resolution = .00001;
    list[GameRotationVector].maxRange = 1.0;
    list[GameRotationVector].minDelay = list[Gyro].minDelay;
    list[GameRotationVector].maxDelay = list[Gyro].maxDelay;

    list[GameRotationVector_Wake].power = list[Gyro].power +
        list[Accelerometer].power;
    list[GameRotationVector_Wake].resolution = .00001;
    list[GameRotationVector_Wake].maxRange = 1.0;
    list[GameRotationVector_Wake].minDelay = list[Gyro].minDelay;
    list[GameRotationVector_Wake].maxDelay = list[Gyro].maxDelay;

    return;
}

void MPLSensor::fillOrientation(struct sensor_t *list)
{
    VFUNC_LOG;

    list[Orientation].power = list[Gyro].power +
        list[Accelerometer].power +
        list[MagneticField].power;
    list[Orientation].resolution = .00001;
    list[Orientation].maxRange = 360.0;
    list[Orientation].minDelay = list[Gyro].minDelay;
    list[Orientation].maxDelay = list[Gyro].maxDelay;

    list[Orientation_Wake].power = list[Gyro].power +
        list[Accelerometer].power +
        list[MagneticField].power;
    list[Orientation_Wake].resolution = .00001;
    list[Orientation_Wake].maxRange = 360.0;
    list[Orientation_Wake].minDelay = list[Gyro].minDelay;
    list[Orientation_Wake].maxDelay = list[Gyro].maxDelay;

    return;
}

void MPLSensor::fillGravity( struct sensor_t *list)
{
    VFUNC_LOG;

    list[Gravity].power = list[Gyro].power +
                          list[Accelerometer].power;
    list[Gravity].resolution = .00001;
    list[Gravity].maxRange = 9.81;
    list[Gravity].minDelay = list[Gyro].minDelay;
    list[Gravity].maxDelay = list[Gyro].maxDelay;

    list[Gravity_Wake].power = list[Gyro].power +
        list[Accelerometer].power;
    list[Gravity_Wake].resolution = .00001;
    list[Gravity_Wake].maxRange = 9.81;
    list[Gravity_Wake].minDelay = list[Gyro].minDelay;
    list[Gravity_Wake].maxDelay = list[Gyro].maxDelay;

    return;
}

void MPLSensor::fillLinearAccel(struct sensor_t *list)
{
    VFUNC_LOG;

    list[LinearAccel].power = list[Gyro].power +
                          list[Accelerometer].power;
    list[LinearAccel].resolution = list[Accelerometer].resolution;
    list[LinearAccel].maxRange = list[Accelerometer].maxRange;
    list[LinearAccel].minDelay = list[Gyro].minDelay;
    list[LinearAccel].maxDelay = list[Gyro].maxDelay;

    list[LinearAccel_Wake].power = list[Gyro].power +
        list[Accelerometer].power;
    list[LinearAccel_Wake].resolution = list[Accelerometer].resolution;
    list[LinearAccel_Wake].maxRange = list[Accelerometer].maxRange;
    list[LinearAccel_Wake].minDelay = list[Gyro].minDelay;
    list[LinearAccel_Wake].maxDelay = list[Gyro].maxDelay;

    return;
}

void MPLSensor::fillSignificantMotion(struct sensor_t *list)
{
    VFUNC_LOG;

    list[SignificantMotion].power = list[Accelerometer].power;
    list[SignificantMotion].resolution = 1;
    list[SignificantMotion].maxRange = 1;
    list[SignificantMotion].minDelay = -1;
    list[SignificantMotion].maxDelay = 0;
}

void MPLSensor::fillTilt(struct sensor_t *list)
{
    VFUNC_LOG;

    list[Tilt].power = list[Accelerometer].power;
    list[Tilt].resolution = 1;
    list[Tilt].maxRange = 1;
    list[Tilt].minDelay = 0;
    list[Tilt].maxDelay = 0;
}

void MPLSensor::fillPickup(struct sensor_t *list)
{
    VFUNC_LOG;

    list[Pickup].power = list[Accelerometer].power;
    list[Pickup].resolution = 1;
    list[Pickup].maxRange = 1;
    list[Pickup].minDelay = -1;
    list[Pickup].maxDelay = 0;
}

int MPLSensor::inv_init_sysfs_attributes(void)
{
    VFUNC_LOG;

    unsigned char i = 0;
    char sysfs_path[MAX_SYSFS_NAME_LEN];
    char tbuf[2];
    char *sptr;
    char **dptr;
    int num;

    memset(sysfs_path, 0, sizeof(sysfs_path));

    sysfs_names_ptr =
        (char*)malloc(sizeof(char[MAX_SYSFS_ATTRB][MAX_SYSFS_NAME_LEN]));
    sptr = sysfs_names_ptr;
    if (sptr != NULL) {
        dptr = (char**)&mpu;
        do {
            *dptr++ = sptr;
            memset(sptr, 0, sizeof(char));
            sptr += sizeof(char[MAX_SYSFS_NAME_LEN]);
        } while (++i < MAX_SYSFS_ATTRB);
    } else {
        LOGE("HAL:couldn't alloc mem for sysfs paths");
        return -1;
    }

    // get absolute IIO path & build MPU's sysfs paths
    inv_get_sysfs_path(sysfs_path);

    memcpy(mSysfsPath, sysfs_path, sizeof(sysfs_path));
    sprintf(mpu.chip_enable, "%s%s", sysfs_path, "/buffer/enable");
    sprintf(mpu.buffer_length, "%s%s", sysfs_path, "/buffer/length");
    sprintf(mpu.master_enable, "%s%s", sysfs_path, "/master_enable");

    sprintf(mpu.in_timestamp_en, "%s%s", sysfs_path,
            "/scan_elements/in_timestamp_en");
    sprintf(mpu.in_timestamp_index, "%s%s", sysfs_path,
            "/scan_elements/in_timestamp_index");
    sprintf(mpu.in_timestamp_type, "%s%s", sysfs_path,
            "/scan_elements/in_timestamp_type");

    sprintf(mpu.dmp_firmware, "%s%s", sysfs_path, "/misc_bin_dmp_firmware");
    sprintf(mpu.firmware_loaded, "%s%s", sysfs_path, "/info_firmware_loaded");
    sprintf(mpu.dmp_on, "%s%s", sysfs_path, "/debug_dmp_on");
    sprintf(mpu.dmp_int_on, "%s%s", sysfs_path, "/dmp_int_on");
    sprintf(mpu.dmp_event_int_on, "%s%s", sysfs_path, "/debug_event_int_on");
    sprintf(mpu.tap_on, "%s%s", sysfs_path, "/event_tap_enable");

    sprintf(mpu.self_test, "%s%s", sysfs_path, "/misc_self_test");
    sprintf(mpu.dmp_init, "%s%s", sysfs_path, "/in_sc_auth");

    /* gyro sysfs */
    sprintf(mpu.temperature, "%s%s", sysfs_path, "/out_temperature");
    sprintf(mpu.gyro_enable, "%s%s", sysfs_path, "/debug_gyro_enable");
    sprintf(mpu.gyro_orient, "%s%s", sysfs_path, "/info_anglvel_matrix");
    sprintf(mpu.gyro_fifo_enable, "%s%s", sysfs_path, "/in_anglvel_enable");
    sprintf(mpu.gyro_fsr, "%s%s", sysfs_path, "/in_anglvel_scale");
    sprintf(mpu.gyro_sf, "%s%s", sysfs_path, "/info_gyro_sf");
    sprintf(mpu.gyro_rate, "%s%s", sysfs_path, "/in_anglvel_rate");
    sprintf(mpu.calib_gyro_enable, "%s%s", sysfs_path, "/in_calib_anglvel_enable");
    sprintf(mpu.calib_gyro_rate, "%s%s", sysfs_path, "/in_calib_anglvel_rate");
    sprintf(mpu.calib_gyro_wake_enable, "%s%s", sysfs_path, "/in_calib_anglvel_wake_enable");
    sprintf(mpu.calib_gyro_wake_rate, "%s%s", sysfs_path, "/in_calib_anglvel_wake_rate");
    sprintf(mpu.gyro_wake_fifo_enable, "%s%s", sysfs_path, "/in_anglvel_wake_enable");
    sprintf(mpu.gyro_wake_rate, "%s%s", sysfs_path, "/in_anglvel_wake_rate");
    sprintf(mpu.gyro_raw_data, "%s%s", sysfs_path, "/in_anglvel_raw");

    /* compass raw sysfs */
    sprintf(mpu.compass_raw_data, "%s%s", sysfs_path, "/in_magn_raw");

    /* accel sysfs */
    sprintf(mpu.accel_enable, "%s%s", sysfs_path, "/debug_accel_enable");
    sprintf(mpu.accel_orient, "%s%s", sysfs_path, "/info_accel_matrix");
    sprintf(mpu.accel_fifo_enable, "%s%s", sysfs_path, "/in_accel_enable");
    sprintf(mpu.accel_rate, "%s%s", sysfs_path, "/in_accel_rate");
    sprintf(mpu.accel_fsr, "%s%s", sysfs_path, "/in_accel_scale");
    sprintf(mpu.accel_wake_fifo_enable, "%s%s", sysfs_path, "/in_accel_wake_enable");
    sprintf(mpu.accel_wake_rate, "%s%s", sysfs_path, "/in_accel_wake_rate");
    sprintf(mpu.accel_raw_data, "%s%s", sysfs_path, "/in_accel_raw");

    // DMP uses these values
    sprintf(mpu.in_accel_x_dmp_bias, "%s%s", sysfs_path, "/in_accel_x_dmp_bias");
    sprintf(mpu.in_accel_y_dmp_bias, "%s%s", sysfs_path, "/in_accel_y_dmp_bias");
    sprintf(mpu.in_accel_z_dmp_bias, "%s%s", sysfs_path, "/in_accel_z_dmp_bias");

    // MPU uses these values
    sprintf(mpu.in_accel_x_offset, "%s%s", sysfs_path, "/in_accel_x_offset");
    sprintf(mpu.in_accel_y_offset, "%s%s", sysfs_path, "/in_accel_y_offset");
    sprintf(mpu.in_accel_z_offset, "%s%s", sysfs_path, "/in_accel_z_offset");
    sprintf(mpu.in_accel_self_test_scale, "%s%s", sysfs_path, "/in_accel_self_test_scale");

    // DMP uses these bias values
    sprintf(mpu.in_gyro_x_dmp_bias, "%s%s", sysfs_path, "/in_anglvel_x_dmp_bias");
    sprintf(mpu.in_gyro_y_dmp_bias, "%s%s", sysfs_path, "/in_anglvel_y_dmp_bias");
    sprintf(mpu.in_gyro_z_dmp_bias, "%s%s", sysfs_path, "/in_anglvel_z_dmp_bias");

    // MPU uses these bias values
    sprintf(mpu.in_gyro_x_offset, "%s%s", sysfs_path, "/in_anglvel_x_offset");
    sprintf(mpu.in_gyro_y_offset, "%s%s", sysfs_path, "/in_anglvel_y_offset");
    sprintf(mpu.in_gyro_z_offset, "%s%s", sysfs_path, "/in_anglvel_z_offset");
    sprintf(mpu.in_gyro_self_test_scale, "%s%s", sysfs_path, "/in_anglvel_self_test_scale");

    // DMP uses these bias values
    sprintf(mpu.in_compass_x_dmp_bias, "%s%s", sysfs_path, "/in_magn_x_dmp_bias");
    sprintf(mpu.in_compass_y_dmp_bias, "%s%s", sysfs_path, "/in_magn_y_dmp_bias");
    sprintf(mpu.in_compass_z_dmp_bias, "%s%s", sysfs_path, "/in_magn_z_dmp_bias");

    sprintf(mpu.three_axis_q_on, "%s%s", sysfs_path, "/in_3quat_enable");
    sprintf(mpu.three_axis_q_rate, "%s%s", sysfs_path, "/in_3quat_rate");

    sprintf(mpu.ped_q_on, "%s%s", sysfs_path, "/in_p6quat_enable");
    sprintf(mpu.ped_q_rate, "%s%s", sysfs_path, "/in_p6quat_rate");
    sprintf(mpu.ped_q_wake_on, "%s%s", sysfs_path, "/in_p6quat_wake_enable");
    sprintf(mpu.ped_q_wake_rate, "%s%s", sysfs_path, "/in_p6quat_wake_rate");

    sprintf(mpu.six_axis_q_on, "%s%s", sysfs_path, "/in_6quat_enable");
    sprintf(mpu.six_axis_q_rate, "%s%s", sysfs_path, "/in_6quat_rate");
    sprintf(mpu.six_axis_q_wake_on, "%s%s", sysfs_path, "/in_6quat_wake_enable");
    sprintf(mpu.six_axis_q_wake_rate, "%s%s", sysfs_path, "/in_6quat_wake_rate");

    sprintf(mpu.nine_axis_q_on, "%s%s", sysfs_path, "/in_9quat_enable");
    sprintf(mpu.nine_axis_q_rate, "%s%s", sysfs_path, "/in_9quat_rate");
    sprintf(mpu.nine_axis_q_wake_on, "%s%s", sysfs_path, "/in_9quat_wake_enable");
    sprintf(mpu.nine_axis_q_wake_rate, "%s%s", sysfs_path, "/in_9quat_wake_rate");

    sprintf(mpu.six_axis_q_value, "%s%s", sysfs_path, "/six_axes_q_value");

    sprintf(mpu.in_geomag_enable, "%s%s", sysfs_path, "/in_geomag_enable");
    sprintf(mpu.in_geomag_rate, "%s%s", sysfs_path, "/in_geomag_rate");
    sprintf(mpu.in_geomag_wake_enable, "%s%s", sysfs_path, "/in_geomag_wake_enable");
    sprintf(mpu.in_geomag_wake_rate, "%s%s", sysfs_path, "/in_geomag_wake_rate");

    sprintf(mpu.step_detector_on, "%s%s", sysfs_path, "/in_step_detector_enable");
    sprintf(mpu.step_indicator_on, "%s%s", sysfs_path, "/in_step_indicator_enable");

    sprintf(mpu.display_orientation_on, "%s%s", sysfs_path,
            "/event_display_orientation_on");
    sprintf(mpu.event_display_orientation, "%s%s", sysfs_path,
            "/poll_display_orientation");
    sprintf(mpu.event_smd, "%s%s", sysfs_path,
            "/poll_smd");
    sprintf(mpu.smd_enable, "%s%s", sysfs_path,
            "/event_smd_enable");
    sprintf(mpu.smd_delay_threshold, "%s%s", sysfs_path,
            "/params_smd_delay_thld");
    sprintf(mpu.smd_delay_threshold2, "%s%s", sysfs_path,
            "/params_smd_delay_thld2");
    sprintf(mpu.smd_threshold, "%s%s", sysfs_path,
            "/params_smd_thrld");
    sprintf(mpu.event_pickup, "%s%s", sysfs_path,
            "/poll_pick_up");
    sprintf(mpu.event_tilt, "%s%s", sysfs_path,
            "/poll_tilt");
    sprintf(mpu.event_activity, "%s%s", sysfs_path,
            "/poll_activity");
    sprintf(mpu.batchmode_timeout, "%s%s", sysfs_path,
            "/misc_batchmode_timeout");
    sprintf(mpu.batchmode_wake_fifo_full_on, "%s%s", sysfs_path,
            "/batchmode_wake_fifo_full_on");
    sprintf(mpu.flush_batch, "%s%s", sysfs_path,
            "/misc_flush_batch");
    sprintf(mpu.pickup_on, "%s%s", sysfs_path,
            "/event_pick_up_enable");
    sprintf(mpu.tilt_on, "%s%s", sysfs_path,
            "/event_tilt_enable");
#if 0
    sprintf(mpu.eis_on, "%s%s", sysfs_path,
            "/event_eis_enable");
    sprintf(mpu.eis_data_on, "%s%s", sysfs_path,
            "/in_eis_enable");
    sprintf(mpu.eis_rate, "%s%s", sysfs_path,
            "/in_eis_rate");
#endif
    sprintf(mpu.pedometer_on, "%s%s", sysfs_path,
            "/in_step_detector_enable");
    sprintf(mpu.pedometer_wake_on, "%s%s", sysfs_path,
            "/in_step_detector_wake_enable");
    sprintf(mpu.pedometer_counter_on, "%s%s", sysfs_path,
            "/in_step_counter_enable");
    sprintf(mpu.pedometer_counter_wake_on, "%s%s", sysfs_path,
            "/in_step_counter_wake_enable");
    sprintf(mpu.pedometer_int_on, "%s%s", sysfs_path,
            "/params_pedometer_int_on");
    sprintf(mpu.event_pedometer, "%s%s", sysfs_path,
            "/poll_pedometer");
    sprintf(mpu.pedometer_steps, "%s%s", sysfs_path,
            "/out_pedometer_steps");
    sprintf(mpu.pedometer_step_thresh, "%s%s", sysfs_path,
            "/params_ped_step_thresh");
    sprintf(mpu.pedometer_counter, "%s%s", sysfs_path,
            "/out_pedometer_counter");
    sprintf(mpu.motion_lpa_on, "%s%s", sysfs_path,
            "/motion_lpa_on");
    sprintf(mpu.gyro_cal_enable, "%s%s", sysfs_path,
            "/debug_gyro_cal_enable");
    sprintf(mpu.accel_cal_enable, "%s%s", sysfs_path,
            "/debug_accel_cal_enable");
    sprintf(mpu.compass_cal_enable, "%s%s", sysfs_path,
            "/debug_compass_cal_enable");
    sprintf(mpu.accel_accuracy_enable, "%s%s", sysfs_path,
            "/debug_accel_accuracy_enable");
    sprintf(mpu.anglvel_accuracy_enable, "%s%s", sysfs_path,
            "/debug_anglvel_accuracy_enable");
    sprintf(mpu.magn_accuracy_enable, "%s%s", sysfs_path,
            "/debug_magn_accuracy_enable");
    sprintf(mpu.debug_determine_engine_on, "%s%s", sysfs_path,
            "/debug_determine_engine_on");
    sprintf(mpu.d_compass_enable, "%s%s", sysfs_path,
            "/debug_compass_enable");
    sprintf(mpu.d_misc_gyro_recalibration, "%s%s", sysfs_path,
            "/misc_gyro_recalibration");
    sprintf(mpu.d_misc_accel_recalibration, "%s%s", sysfs_path,
            "/misc_accel_recalibration");
    sprintf(mpu.d_misc_compass_recalibration, "%s%s", sysfs_path,
            "/misc_magn_recalibration");
    sprintf(mpu.d_misc_accel_cov, "%s%s", sysfs_path,
            "/misc_bin_accel_covariance");
    sprintf(mpu.d_misc_current_compass_cov, "%s%s", sysfs_path,
            "/misc_bin_cur_magn_covariance");
    sprintf(mpu.d_misc_compass_cov, "%s%s", sysfs_path,
            "/misc_bin_magn_covariance");
    sprintf(mpu.d_misc_ref_mag_3d, "%s%s", sysfs_path,
            "/misc_ref_mag_3d");
#if 0
    sprintf(mpu.ois_enable, "%s%s", sysfs_path,
            "/in_ois_enable");
#endif
#ifdef SENSORS_DEVICE_API_VERSION_1_4
    /* Data injection sysfs */
    sprintf(mpu.info_poke_mode, "%s%s", sysfs_path, "/info_poke_mode");
    sprintf(mpu.misc_bin_poke_accel, "%s%s", sysfs_path, "/misc_bin_poke_accel");
    sprintf(mpu.misc_bin_poke_gyro, "%s%s", sysfs_path, "/misc_bin_poke_gyro");
    sprintf(mpu.misc_bin_poke_mag, "%s%s", sysfs_path, "/misc_bin_poke_mab");
#endif

    return 0;
}

//DMP support only for MPU6xxx/9xxx
bool MPLSensor::isMpuNonDmp(void)
{
    VFUNC_LOG;
    if (!strcmp(chip_ID, "mpu3050") || !strcmp(chip_ID, "MPU3050")
					|| !strcmp(chip_ID, "ICM20602")
					|| !strcmp(chip_ID, "ICM20690"))
        return true;
    else
        return false;
}

bool MPLSensor::isPressurePresent(void)
{
    VFUNC_LOG;
    if (!strcmp(chip_ID, "MPU7400") || !strcmp(chip_ID, "ICM20728"))
        return true;
    else
        return false;
}

int MPLSensor::isLowPowerQuatEnabled(void)
{
    VFUNC_LOG;

    if (!(mCalibrationMode & dmp))
        return 0;
    else
        return 1;
}

void MPLSensor::resetCalStatus(int recal)
{
    VFUNC_LOG;

    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)", recal,
            mpu.d_misc_gyro_recalibration, getTimestamp());
    if(write_sysfs_int(mpu.d_misc_gyro_recalibration, recal) < 0) {
        LOGE("HAL:Error writing to misc_gyro_recalibration");
    }
    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)", recal,
            mpu.d_misc_accel_recalibration, getTimestamp());
    if(write_sysfs_int(mpu.d_misc_accel_recalibration, recal) < 0) {
        LOGE("HAL:Error writing to misc_accel_recalibration");
    }
    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)", recal,
            mpu.d_misc_compass_recalibration, getTimestamp());
    if(write_sysfs_int(mpu.d_misc_compass_recalibration, recal) < 0) {
        LOGE("HAL:Error writing to misc_magn_recalibration");
    }

    return;
}

void MPLSensor::getFactoryGyroBias()
{
    VFUNC_LOG;

    /* Get Values from MPL */
    inv_get_gyro_bias(mFactoryGyroBias);
    inv_get_gyro_bias_2(mFactoryGyroBiasLp); /* lp mode */
    LOGV_IF(ENG_VERBOSE, "Factory Gyro Bias OIS %d %d %d",
			mFactoryGyroBias[0], mFactoryGyroBias[1], mFactoryGyroBias[2]);
    LOGV_IF(ENG_VERBOSE, "Factory Gyro Bias LP  %d %d %d",
			mFactoryGyroBiasLp[0], mFactoryGyroBiasLp[1], mFactoryGyroBiasLp[2]);

    /* adjust factory bias for lp according to fsr */
    if ((mFactoryGyroBias[0] && mFactoryGyroBias[1] && mFactoryGyroBias[2])
            && (mFactoryGyroBiasLp[0] && mFactoryGyroBiasLp[1] && mFactoryGyroBiasLp[2])) {
        /* bias in calibration file is 2000dps scaled by 1<<16 */
        int i;
        for (i = 0; i < 3; i++)
            mGyroBiasUiMode[i] = ((mFactoryGyroBias[i] - mFactoryGyroBiasLp[i])
                    * (2000 / mGyroScale)) >>16;
    }
    LOGV_IF(ENG_VERBOSE, "Gyro Bias UI (LSB) %d %d %d",
			mGyroBiasUiMode[0], mGyroBiasUiMode[1], mGyroBiasUiMode[2]);
    mFactoryGyroBiasAvailable = true;

    return;
}

/* set bias from factory cal file to MPU offset (in chip frame)
   x = values store in cal file --> (v * 2^16 / (2000/250))
   (hardware unit @ 2000dps scaled by 2^16)
   set the same values to driver
 */
void MPLSensor::setFactoryGyroBias()
{
    VFUNC_LOG;
    int scaleRatio = 1;
    int offsetScale = 1;
    LOGV_IF(ENG_VERBOSE, "HAL: scaleRatio used =%d", scaleRatio);
    LOGV_IF(ENG_VERBOSE, "HAL: offsetScale used =%d", offsetScale);

    /* Write to Driver */
    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            (((int) (((float) mFactoryGyroBias[0]) * scaleRatio)) / offsetScale),
            mpu.in_gyro_x_offset, getTimestamp());
    if(write_attribute_sensor_continuous(gyro_x_offset_fd,
                (((int) (((float) mFactoryGyroBias[0]) * scaleRatio)) / offsetScale)) < 0)
    {
        LOGE("HAL:Error writing to gyro_x_offset");
        return;
    }
    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            (((int) (((float) mFactoryGyroBias[1]) * scaleRatio)) / offsetScale),
            mpu.in_gyro_y_offset, getTimestamp());
    if(write_attribute_sensor_continuous(gyro_y_offset_fd,
                (((int) (((float) mFactoryGyroBias[1]) * scaleRatio)) / offsetScale)) < 0)
    {
        LOGE("HAL:Error writing to gyro_y_offset");
        return;
    }
    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            (((int) (((float) mFactoryGyroBias[2]) * scaleRatio)) / offsetScale),
            mpu.in_gyro_z_offset, getTimestamp());
    if(write_attribute_sensor_continuous(gyro_z_offset_fd,
                (((int) (((float) mFactoryGyroBias[2]) * scaleRatio)) / offsetScale)) < 0)
    {
        LOGE("HAL:Error writing to gyro_z_offset");
        return;
    }
    mFactoryGyroBiasAvailable = false;
    setAccuracy(0, 1);
    LOGV_IF(EXTRA_VERBOSE, "HAL:Factory Gyro Calibrated Bias Applied");

    return;
}

/*
 * get gyro bias from calibration file (scaled and in body frame)
 */
void MPLSensor::getGyroBias()
{
    VFUNC_LOG;

    int *temp = NULL;

    /* Get Values from MPL */
    inv_get_mpl_gyro_bias(mDmpGyroBias, temp);
    LOGV_IF(ENG_VERBOSE, "Gyro Bias %d %d %d",
            mDmpGyroBias[0], mDmpGyroBias[1], mDmpGyroBias[2]);
    if(mDmpGyroBias[0] && mDmpGyroBias[1] && mDmpGyroBias[2]) {
        mGyroBiasAvailable = true;
    }
    return;
}

void MPLSensor::setGyroZeroBias()
{
    VFUNC_LOG;

    /* Write to Driver */
    LOGV_IF(SYSFS_VERBOSE && INPUT_DATA, "HAL:sysfs:echo %d > %s (%lld)",
            0, mpu.in_gyro_x_dmp_bias, getTimestamp());
    if(write_attribute_sensor_continuous(gyro_x_dmp_bias_fd, 0) < 0) {
        LOGE("HAL:Error writing to gyro_x_dmp_bias");
        return;
    }
    LOGV_IF(SYSFS_VERBOSE && INPUT_DATA, "HAL:sysfs:echo %d > %s (%lld)",
            0, mpu.in_gyro_y_dmp_bias, getTimestamp());
    if(write_attribute_sensor_continuous(gyro_y_dmp_bias_fd, 0) < 0) {
        LOGE("HAL:Error writing to gyro_y_dmp_bias");
        return;
    }
    LOGV_IF(SYSFS_VERBOSE && INPUT_DATA, "HAL:sysfs:echo %d > %s (%lld)",
            0, mpu.in_gyro_z_dmp_bias, getTimestamp());
    if(write_attribute_sensor_continuous(gyro_z_dmp_bias_fd, 0) < 0) {
        LOGE("HAL:Error writing to gyro_z_dmp_bias");
        return;
    }
    LOGV_IF(EXTRA_VERBOSE, "HAL:Zero Gyro DMP Calibrated Bias Applied");

    return;
}

/*
   Set Gyro Bias to DMP, from MPL - load cal
   DMP units: bias * 46850825 / 2^30 = bias / 2 (in body frame)
 */
void MPLSensor::setDmpGyroBias()
{
    VFUNC_LOG;

    if (mCalibrationMode & mpl_gyro) {
        if (mGyroBiasAvailable) {
            adapter_set_mpl_gyro_bias(mDmpGyroBias, 3);
            setAccuracy(0, 1);
        }
    }
    /* Write to Driver */
    LOGV_IF(SYSFS_VERBOSE && INPUT_DATA, "HAL:sysfs:echo %d > %s (%lld)",
            mDmpGyroBias[0], mpu.in_gyro_x_dmp_bias, getTimestamp());
    if(write_attribute_sensor_continuous(gyro_x_dmp_bias_fd, mDmpGyroBias[0]) < 0) {
        LOGE("HAL:Error writing to gyro_x_dmp_bias");
        return;
    }
    LOGV_IF(SYSFS_VERBOSE && INPUT_DATA, "HAL:sysfs:echo %d > %s (%lld)",
            mDmpGyroBias[1], mpu.in_gyro_y_dmp_bias, getTimestamp());
    if(write_attribute_sensor_continuous(gyro_y_dmp_bias_fd, mDmpGyroBias[1]) < 0) {
        LOGE("HAL:Error writing to gyro_y_dmp_bias");
        return;
    }
    LOGV_IF(SYSFS_VERBOSE && INPUT_DATA, "HAL:sysfs:echo %d > %s (%lld)",
            mDmpGyroBias[2], mpu.in_gyro_z_dmp_bias, getTimestamp());
    if(write_attribute_sensor_continuous(gyro_z_dmp_bias_fd, mDmpGyroBias[2]) < 0) {
        LOGE("HAL:Error writing to gyro_z_dmp_bias");
        return;
    }
    mGyroBiasApplied = true;
    mGyroBiasAvailable = false;
    LOGV_IF(EXTRA_VERBOSE, "HAL:Gyro DMP Calibrated Bias Applied");

    return;
}

/*
   Get Gyro Bias from DMP, then store in MPL - store cal
   DMP units: bias * 46850825 / 2^30 = bias / 2 (in body frame)
 */
void MPLSensor::getDmpGyroBias()
{
    VFUNC_LOG;

    char buf[sizeof(int) *4];
    int bias[3];

    if(mCalibrationMode & mpl_gyro)
    {
        int temperature;
        memset(bias, 0, sizeof(bias));
        adapter_get_mpl_gyro_bias(bias, &temperature);
    }
    else
    {
        /* Read from Driver */
        if(read_attribute_sensor(gyro_x_dmp_bias_fd, buf, sizeof(buf)) < 0) {
            LOGE("HAL:Error reading gyro_x_dmp_bias");
            return;
        }
        if(sscanf(buf, "%d", &bias[0]) < 0) {
            LOGE("HAL:Error reading gyro bias");
            return;
        }
        if(read_attribute_sensor(gyro_y_dmp_bias_fd, buf, sizeof(buf)) < 0) {
            LOGE("HAL:Error reading gyro_y_dmp_bias");
            return;
        }
        if(sscanf(buf, "%d", &bias[1]) < 0) {
            LOGE("HAL:Error reading gyro bias");
            return;
        }
        if(read_attribute_sensor(gyro_z_dmp_bias_fd, buf, sizeof(buf)) < 0) {
            LOGE("HAL:Error reading gyro_z_dmp_bias");
            return;
        }
        if(sscanf(buf, "%d", &bias[2]) < 0) {
            LOGE("HAL:Error reading gyro bias");
            return;
        }
    }
    LOGV_IF(SYSFS_VERBOSE && INPUT_DATA, "HAL:Dmp Gyro Bias read %d %d %d", bias[0], bias[1], bias[2]);
    if (bias[0] && bias[1] && bias[2]) {
        mDmpGyroBias[0] = bias[0];
        mDmpGyroBias[1] = bias[1];
        mDmpGyroBias[2] = bias[2];
        inv_set_mpl_gyro_bias(mDmpGyroBias, 3);
        LOGV_IF(EXTRA_VERBOSE, "HAL:Gyro DMP Calibrated Bias Obtained");
    }
    return;
}

void MPLSensor::getFactoryAccelBias()
{
    VFUNC_LOG;

    int temp;

    /* Get Values from MPL */
    inv_get_accel_bias(mFactoryAccelBias);
    inv_get_accel_bias_2(mFactoryAccelBiasLp);
    if(mFactoryAccelBias[0] && mFactoryAccelBias[1] && mFactoryAccelBias[2]) {
        LOGV_IF(ENG_VERBOSE, "Factory Accel Bias OIS %d %d %d",
                mFactoryAccelBias[0], mFactoryAccelBias[1], mFactoryAccelBias[2]);
        LOGV_IF(ENG_VERBOSE, "Factory Accel Bias LP  %d %d %d",
                mFactoryAccelBiasLp[0], mFactoryAccelBiasLp[1], mFactoryAccelBiasLp[2]);
        mFactoryAccelBiasAvailable = true;
        if (mFactoryAccelBiasLp[0] && mFactoryAccelBiasLp[1] && mFactoryAccelBias[2]) {
            /* bias in calibration file is 2g scaled by 1<<16 */
            int i;
            for (i = 0; i < 3; i++)
                mAccelBiasUiMode[i] = ((mFactoryAccelBias[i] - mFactoryAccelBiasLp[i])
                        * 2 / mAccelScale)>>16;
        }
    }
    LOGV_IF(ENG_VERBOSE, "Accel Bias UI (LSB) %d %d %d",
            mAccelBiasUiMode[0], mAccelBiasUiMode[1], mAccelBiasUiMode[2]);

    return;
}

void MPLSensor::setFactoryAccelBias()
{
    VFUNC_LOG;

    if(mFactoryAccelBiasAvailable == false)
        return;

    /* add scaling here - depends on self test parameters */
    int scaleRatio = 1;
    int offsetScale = 1;
    int tempBias;

    LOGV_IF(ENG_VERBOSE, "HAL: scaleRatio used =%d", scaleRatio);
    LOGV_IF(ENG_VERBOSE, "HAL: offsetScale used =%d", offsetScale);

    /* Write to Driver */
    tempBias = mFactoryAccelBias[0] * scaleRatio / offsetScale;
    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            tempBias, mpu.in_accel_x_offset, getTimestamp());
    if(write_attribute_sensor_continuous(accel_x_offset_fd, tempBias) < 0) {
        LOGE("HAL:Error writing to accel_x_offset");
        return;
    }
    tempBias = mFactoryAccelBias[1] * scaleRatio / offsetScale;
    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            tempBias, mpu.in_accel_y_offset, getTimestamp());
    if(write_attribute_sensor_continuous(accel_y_offset_fd, tempBias) < 0) {
        LOGE("HAL:Error writing to accel_y_offset");
        return;
    }
    tempBias = mFactoryAccelBias[2] * scaleRatio / offsetScale;
    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            tempBias, mpu.in_accel_z_offset, getTimestamp());
    if(write_attribute_sensor_continuous(accel_z_offset_fd, tempBias) < 0) {
        LOGE("HAL:Error writing to accel_z_offset");
        return;
    }
    mFactoryAccelBiasAvailable = false;
    setAccuracy(1, 1);
    LOGV_IF(EXTRA_VERBOSE, "HAL:Factory Accel Calibrated Bias Applied = %d", mAccelAccuracy);

    return;
}

void MPLSensor::getAccelBias()
{
    VFUNC_LOG;
    int temp;

    /* Get Values from MPL */
    inv_get_mpl_accel_bias(mAccelDmpBias, &temp);
    LOGV_IF(ENG_VERBOSE, "get_mpl_accel_bias %d %d %d",
            mAccelDmpBias[0], mAccelDmpBias[1], mAccelDmpBias[2]);
    if(mAccelDmpBias[0] && mAccelDmpBias[1] && mAccelDmpBias[2]) {
        mAccelBiasAvailable = true;
    }
    return;
}

/*
   Get Accel Bias from DMP, then set to MPL (store cal)
   DMP units: bias * 536870912 / 2^30 = bias / 2 (in body frame)
 */
void MPLSensor::getDmpAccelBias()
{
    VFUNC_LOG;
    char buf[sizeof(int) *4];
    int data;
    int bias[3];

    if(mCalibrationMode & mpl_accel)
    {
        int temperature;
        memset(bias, 0, sizeof(bias));
        adapter_get_mpl_accel_bias(bias, &temperature);
    }
    else
    {
        /* read from driver */
        memset(buf, 0, sizeof(buf));
        if(read_attribute_sensor(
                    accel_x_dmp_bias_fd, buf, sizeof(buf)) < 0) {
            LOGE("HAL:Error reading accel_x_dmp_bias");
            return;
        }
        sscanf(buf, "%d", &data);
        bias[0] = data;

        memset(buf, 0, sizeof(buf));
        if(read_attribute_sensor(
                    accel_y_dmp_bias_fd, buf, sizeof(buf)) < 0) {
            LOGE("HAL:Error reading accel_y_dmp_bias");
            return;
        }
        sscanf(buf, "%d", &data);
        bias[1] = data;

        memset(buf, 0, sizeof(buf));
        if(read_attribute_sensor(
                    accel_z_dmp_bias_fd, buf, sizeof(buf)) < 0) {
            LOGE("HAL:Error reading accel_z_dmp_bias");
            return;
        }
        sscanf(buf, "%d", &data);
        bias[2] = data;
    }
    LOGV_IF(SYSFS_VERBOSE && INPUT_DATA, "HAL:Dmp Accel Bias read %d %d %d",
            bias[0], bias[1], bias[2]);
    if (bias[0] && bias[1] && bias[2]) {
        mAccelDmpBias[0] = bias[0];
        mAccelDmpBias[1] = bias[1];
        mAccelDmpBias[2] = bias[2];
        inv_set_accel_bias_mask(mAccelDmpBias, 3, 7); //(3=highest accuracy; 7=all axis)
        mAccelBiasAvailable =  true;
        setAccuracy(1, 3);
        LOGV_IF(EXTRA_VERBOSE, "HAL:Accel DMP Calibrated Bias Obtained");
    }
    return;
}

/*    set accel bias obtained from cal file to DMP - load cal
      DMP expects: bias * 536870912 / 2^30 = bias / 2 (in body frame)
 */
void MPLSensor::setDmpAccelBias()
{
    VFUNC_LOG;

    if (mCalibrationMode & mpl_accel) {
        if (mAccelBiasAvailable) {
            adapter_set_mpl_accel_bias(mAccelDmpBias, 3);
            setAccuracy(1,1);
        }
    }
    /* write to driver */
    LOGV_IF(SYSFS_VERBOSE && INPUT_DATA, "HAL:sysfs:echo %d > %s (%lld)",
            mAccelDmpBias[0], mpu.in_accel_x_dmp_bias, getTimestamp());
    if((write_attribute_sensor_continuous(
                    accel_x_dmp_bias_fd, mAccelDmpBias[0])) < 0) {
        LOGE("HAL:Error writing to accel_x_dmp_bias");
        return;
    }
    LOGV_IF(SYSFS_VERBOSE && INPUT_DATA, "HAL:sysfs:echo %d > %s (%lld)",
            mAccelDmpBias[1], mpu.in_accel_y_dmp_bias, getTimestamp());
    if((write_attribute_sensor_continuous(
                    accel_y_dmp_bias_fd, mAccelDmpBias[1])) < 0) {
        LOGE("HAL:Error writing to accel_y_dmp_bias");
        return;
    }
    LOGV_IF(SYSFS_VERBOSE && INPUT_DATA, "HAL:sysfs:echo %d > %s (%lld)",
            mAccelDmpBias[2], mpu.in_accel_z_dmp_bias, getTimestamp());
    if((write_attribute_sensor_continuous(
                    accel_z_dmp_bias_fd, mAccelDmpBias[2])) < 0) {
        LOGE("HAL:Error writing to accel_z_dmp_bias");
        return;
    }

    mAccelBiasApplied = true;
    mAccelBiasAvailable = false;
    LOGV_IF(EXTRA_VERBOSE, "HAL:Accel DMP Calibrated Bias Applied");

    return;
}

/*    set accel bias obtained from MPL
      bias is scaled by 65536 from MPL
      DMP expects: bias * 536870912 / 2^30 = bias / 2 (in body frame)
 */
void MPLSensor::setAccelBias()
{
    VFUNC_LOG;

    if(mAccelBiasAvailable == false) {
        LOGV_IF(ENG_VERBOSE, "HAL: setAccelBias - accel bias not available");
        return;
    }

    /* write to driver */
    LOGV_IF(SYSFS_VERBOSE && INPUT_DATA, "HAL:sysfs:echo %d > %s (%lld)",
            (int) (mAccelBias[0] / 65536.f / 2),
            mpu.in_accel_x_dmp_bias, getTimestamp());
    if(write_attribute_sensor_continuous(
                accel_x_dmp_bias_fd, (int)(mAccelBias[0] / 65536.f / 2)) < 0) {
        LOGE("HAL:Error writing to accel_x_dmp_bias");
        return;
    }
    LOGV_IF(SYSFS_VERBOSE && INPUT_DATA, "HAL:sysfs:echo %d > %s (%lld)",
            (int)(mAccelBias[1] / 65536.f / 2),
            mpu.in_accel_y_dmp_bias, getTimestamp());
    if(write_attribute_sensor_continuous(
                accel_y_dmp_bias_fd, (int)(mAccelBias[1] / 65536.f / 2)) < 0) {
        LOGE("HAL:Error writing to accel_y_dmp_bias");
        return;
    }
    LOGV_IF(SYSFS_VERBOSE && INPUT_DATA, "HAL:sysfs:echo %d > %s (%lld)",
            (int)(mAccelBias[2] / 65536 / 2),
            mpu.in_accel_z_dmp_bias, getTimestamp());
    if(write_attribute_sensor_continuous(
                accel_z_dmp_bias_fd, (int)(mAccelBias[2] / 65536 / 2)) < 0) {
        LOGE("HAL:Error writing to accel_z_dmp_bias");
        return;
    }

    mAccelBiasAvailable = false;
    mAccelBiasApplied = true;
    LOGV_IF(EXTRA_VERBOSE, "HAL:Accel DMP Calibrated Bias Applied");

    return;
}

/*
   Get Compass Bias from DMP, then store in MPL - store cal
   DMP units: scaled in body frame
 */
void MPLSensor::getDmpCompassBias()
{
    VFUNC_LOG;

    char buf[sizeof(int) *4];
    int bias[3];

    if(mCalibrationMode & mpl_compass)
    {
        memset(bias, 0, sizeof(bias));
        adapter_get_mpl_compass_bias(bias);
    }
    else {
        /* Read from Driver */
        if(read_attribute_sensor(compass_x_dmp_bias_fd, buf, sizeof(buf)) < 0) {
            LOGE("HAL:Error reading compass_x_dmp_bias fd %d", compass_x_dmp_bias_fd);
            return;
        }
        if(sscanf(buf, "%d", &bias[0]) < 0) {
            LOGE("HAL:Error reading compass bias x buf [%s]", buf);
            return;
        }
        if(read_attribute_sensor(compass_y_dmp_bias_fd, buf, sizeof(buf)) < 0) {
            LOGE("HAL:Error reading compass_y_dmp_bias fd %d", compass_y_dmp_bias_fd);
            return;
        }
        if(sscanf(buf, "%d", &bias[1]) < 0) {
            LOGE("HAL:Error reading compass bias y buf [%s]", buf);
            return;
        }
        if(read_attribute_sensor(compass_z_dmp_bias_fd, buf, sizeof(buf)) < 0) {
            LOGE("HAL:Error reading compass_z_dmp_bias fd %d", compass_z_dmp_bias_fd);
            return;
        }
        if(sscanf(buf, "%d", &bias[2]) < 0) {
            LOGE("HAL:Error reading compass bias z buf [%s]", buf);
            return;
        }
    }
    LOGV_IF(SYSFS_VERBOSE && INPUT_DATA, "HAL:Dmp Compass Bias read %d %d %d", bias[0], bias[1], bias[2]);
    if (bias[0] && bias[1] && bias[2]) {
        mDmpCompassBias[0] = bias[0];
        mDmpCompassBias[1] = bias[1];
        mDmpCompassBias[2] = bias[2];
        mCompassBiasAvailable = 1;
        inv_set_compass_bias(mDmpCompassBias, 3);
        LOGV_IF(EXTRA_VERBOSE, "HAL:Compass DMP Calibrated Bias Obtained");
    }
    return;
}

/*    set compass bias obtained from cal file to DMP - load cal
      DMP expects: scaled in body frame
 */
void MPLSensor::setDmpCompassBias()
{
    VFUNC_LOG;

    if (mCalibrationMode & mpl_compass) {
        adapter_set_mpl_compass_bias(mDmpCompassBias, 3);
        setAccuracy(2, 1);
    }
    /* write to driver */
    LOGV_IF(SYSFS_VERBOSE && INPUT_DATA, "HAL:sysfs:echo %d > %s (%lld)",
            mDmpCompassBias[0], mpu.in_compass_x_dmp_bias, getTimestamp());
    if((write_attribute_sensor_continuous(
                    compass_x_dmp_bias_fd, mDmpCompassBias[0])) < 0) {
        LOGE("HAL:Error writing to compass_x_dmp_bias");
        return;
    }
    LOGV_IF(SYSFS_VERBOSE && INPUT_DATA, "HAL:sysfs:echo %d > %s (%lld)",
            mDmpCompassBias[1], mpu.in_compass_y_dmp_bias, getTimestamp());
    if((write_attribute_sensor_continuous(
                    compass_y_dmp_bias_fd, mDmpCompassBias[1])) < 0) {
        LOGE("HAL:Error writing to compass_y_dmp_bias");
        return;
    }
    LOGV_IF(SYSFS_VERBOSE && INPUT_DATA, "HAL:sysfs:echo %d > %s (%lld)",
            mDmpCompassBias[2], mpu.in_compass_z_dmp_bias, getTimestamp());
    if((write_attribute_sensor_continuous(
                    compass_z_dmp_bias_fd, mDmpCompassBias[2])) < 0) {
        LOGE("HAL:Error writing to compass_z_dmp_bias");
        return;
    }

    mCompassBiasApplied = true;
    mCompassBiasAvailable = false;
    LOGV_IF(EXTRA_VERBOSE, "HAL:Compass DMP Calibrated Bias Applied");

    return;
}

void MPLSensor::getCompassBias()
{
    VFUNC_LOG;

    /* Get Values from MPL */
    inv_get_compass_bias(mDmpCompassBias);
    LOGV_IF(ENG_VERBOSE, "Compass Bias %d %d %d",
            mDmpCompassBias[0], mDmpCompassBias[1], mDmpCompassBias[2]);
    if(mDmpCompassBias[0] && mDmpCompassBias[1] && mDmpCompassBias[2]) {
        mCompassBiasAvailable = true;
    }
    return;
}

int MPLSensor::isCompassDisabled(void)
{
    VFUNC_LOG;
    if(mCompassSensor->getFd() < 0 && !mCompassSensor->isIntegrated()) {
        LOGI_IF(EXTRA_VERBOSE, "HAL: Compass is disabled, Six-axis Sensor Fusion is used.");
        return 1;
    }
    return 0;
}

int MPLSensor::checkBatchEnabled(void)
{
    VFUNC_LOG;
    return ((mFeatureActiveMask & INV_DMP_BATCH_MODE)? 1:0);
}

/* precondition: framework disallows this case, ie enable continuous sensor, */
/* and enable batch sensor */
/* if one sensor is in continuous mode, HAL disallows enabling batch for this sensor */
/* or any other sensors */
int MPLSensor::batch(int handle, int flags, int64_t period_ns, int64_t timeout)
{
    VFUNC_LOG;

    int res = 0;
    int period_ns_int;
    int i, list_index;

    /* Enables batch mode and sets timeout for the given sensor */
    bool dryRun = false;
    android::String8 sname;
    int what = -1;
    uint64_t enabled_sensors = mEnabled;
    int batchMode = timeout > 0 ? 1 : 0;

    /* This makes Android M suspend device test pass to round up the frequency
       when requested frequency has fractional digit */
    period_ns_int = (NS_PER_SECOND + (period_ns - 1))/ period_ns;
    period_ns = NS_PER_SECOND / period_ns_int;

    LOGI_IF(DEBUG_BATCHING || ENG_VERBOSE,
            "HAL:batch called - handle=%d, flags=%d, period=%lld, timeout=%lld",
            handle, flags, period_ns, timeout);

    if(flags & SENSORS_BATCH_DRY_RUN) {
        dryRun = true;
        LOGI_IF(PROCESS_VERBOSE,
                "HAL:batch - dry run mode is set (%d)", SENSORS_BATCH_DRY_RUN);
    }

    /* check if we can support issuing interrupt before FIFO fills-up */
    /* in a given timeout.                                          */
    if (flags & SENSORS_BATCH_WAKE_UPON_FIFO_FULL) {
        LOGE("HAL: batch SENSORS_BATCH_WAKE_UPON_FIFO_FULL is not supported");
        return -EINVAL;
    }

    getHandle(handle, what, sname);
    if(what < 0) {
        LOGE("HAL:batch sensors %d not found", handle);
        return -EINVAL;
    }

    LOGV_IF(PROCESS_VERBOSE,
            "HAL:batch : %llu ns, (%.2f Hz) timeout=%lld", period_ns, NS_PER_SECOND_FLOAT / period_ns, timeout);

#if 0
    /* OIS Sensor is dummy, do nothing */
    if(uint32_t(what) == OisSensor) {
        LOGI_IF(ENG_VERBOSE, "HAL:batch Ois Sensor is dummy, return.");
        return 0;
    }
#endif

    int size = mNumSensors;
    list_index = -1;
    for (i = 0; i < size; i++) {
        if (handle == currentSensorList[i].handle) {
            list_index = i;
            break;
        }
    }
    if (period_ns > currentSensorList[list_index].maxDelay * 1000)
        period_ns = currentSensorList[list_index].maxDelay * 1000;

    if (period_ns < currentSensorList[list_index].minDelay * 1000)
        period_ns = currentSensorList[list_index].minDelay * 1000;

    if (size > 0) {
        if (currentSensorList[list_index].fifoMaxEventCount != 0) {
            LOGV_IF(PROCESS_VERBOSE, "HAL: batch - select sensor (handle %d)", list_index);
        } else if (timeout > 0) {
            LOGE("sensor (handle %d) is not supported in batch mode", list_index);
            return -EINVAL;
        }
    }

    if(dryRun == true) {
        return 0;
    }

    int tempBatch = 0;
    if (timeout > 0) {
        tempBatch = mBatchEnabled | (1LL << what);
    } else {
        tempBatch = mBatchEnabled & ~(1LL << what);
    }

    if (!computeBatchSensorMask(mEnabled, tempBatch)) {
        batchMode = 0;
    } else {
        batchMode = 1;
    }

    /* get maximum possible bytes to batch per sample */
    /* get minimum delay for each requested sensor    */
    int nBytes = 0;
    int64_t wanted = NS_PER_SECOND, ns = 0;
    int64_t timeoutInMs = 0;
    for (i = 0; i < ID_NUMBER; i++) {
        if (batchMode == 1) {
            ns = mBatchDelays[i];
            LOGV_IF(DEBUG_BATCHING && EXTRA_VERBOSE,
                    "HAL:batch - requested sensor=0x%llx, batch delay=%lld", mEnabled & (1LL << i), ns);
            // take the min delay ==> max rate
            wanted = (ns < wanted) ? ns : wanted;
            if (i <= RawMagneticField) {
                nBytes += 8;
            }
            if (i == Pressure) {
                nBytes += 6;
            }
            if ((i == StepDetector) || (i == GameRotationVector)) {
                nBytes += 16;
            }
        }
    }

    /* starting from code below,  we will modify hardware */
    /* first edit global batch mode mask */

    if (timeout == 0) {
        mBatchEnabled &= ~(1LL << what);
        mBatchDelays[what] = NS_PER_SECOND;
        mDelays[what] = period_ns;
        mBatchTimeouts[what] = 100000000000LL;
    } else {
        mBatchEnabled |= (1LL << what);
        mBatchDelays[what] = period_ns;
        mDelays[what] = period_ns;
        mBatchTimeouts[what] = timeout;
    }

    if(((int)mOldBatchEnabledMask != batchMode) || batchMode) {

        /* remember batch mode that is set  */
        mOldBatchEnabledMask = batchMode;

        /* For these sensors, switch to different data output */
        int featureMask = computeBatchDataOutput();

        LOGV_IF(ENG_VERBOSE, "batchMode =%d, featureMask=0x%x, mEnabled=%d",
                batchMode, featureMask, mEnabled);
        if (DEBUG_BATCHING && EXTRA_VERBOSE) {
            //LOGV("HAL:batch - sensor=0x%llx", mBatchEnabled);
            for (int d = 0; d < ID_NUMBER; d++) {
                LOGV("HAL:batch - sensor %d status=0x%llx batch status=0x%llx timeout=%lld delay=%lld",
                        d, mEnabled & (1LL << d), (mBatchEnabled & (1LL << d)), mBatchTimeouts[d],
                        mBatchDelays[d]);
            }
        }

        /* case for Ped standalone */
        if ((batchMode == 1) && (featureMask & INV_DMP_PED_STANDALONE) &&
                (mFeatureActiveMask & INV_DMP_PEDOMETER)) {
            LOGI_IF(ENG_VERBOSE, "batch - ID_P only = 0x%x", mBatchEnabled);
            enablePedQuaternion(0);
            enablePedStandalone(1);
        } else {
            enablePedStandalone(0);
            if (featureMask & INV_DMP_PED_QUATERNION) {
                enableLPQuaternion(0);
                enablePedQuaternion(1);
            }
        }

        /* case for Ped Quaternion */
        if ((batchMode == 1) && (featureMask & INV_DMP_PED_QUATERNION) &&
                (mEnabled & (1LL << GameRotationVector)) &&
                (mFeatureActiveMask & INV_DMP_PEDOMETER)) {
            LOGI_IF(ENG_VERBOSE, "batch - ID_P and GRV or ALL = 0x%x", mBatchEnabled);
            LOGI_IF(ENG_VERBOSE, "batch - ID_P is enabled for batching, PED quat will be automatically enabled");
            enableLPQuaternion(0);
            enablePedQuaternion(1);

            /* set pedq rate */
            wanted = mBatchDelays[GameRotationVector];
            setPedQuaternionRate(wanted);
        } else if (!(featureMask & INV_DMP_PED_STANDALONE)){
            LOGV_IF(ENG_VERBOSE, "batch - PedQ Toggle back to normal 6 axis");
            if (mEnabled & (1LL << GameRotationVector)) {
                enableLPQuaternion(checkLPQRateSupported());
            }
            enablePedQuaternion(0);
        } else {
            enablePedQuaternion(0);
        }

        /* case for Ped indicator */
        if ((batchMode == 1) && ((featureMask & INV_DMP_PED_INDICATOR))) {
            enablePedIndicator(1);
        } else {
            enablePedIndicator(0);
        }

        /* case for Six Axis Quaternion */
        if ((batchMode == 1) && (featureMask & INV_DMP_6AXIS_QUATERNION) &&
                (mEnabled & (1LL << GameRotationVector))) {
            LOGI_IF(ENG_VERBOSE, "batch - GRV = 0x%x", mBatchEnabled);
            enableLPQuaternion(0);
            enable6AxisQuaternion(1);

            /* set sixaxis rate */
            wanted = mBatchDelays[GameRotationVector];
            wanted = lookupSensorRate(wanted / 1000) * 1000;
            set6AxisQuaternionRate(wanted);
        } else if (!(featureMask & INV_DMP_PED_QUATERNION)){
            LOGV_IF(ENG_VERBOSE, "batch - 6Axis Toggle back to normal 6 axis");
            if (mEnabled & (1LL << GameRotationVector)) {
                enableLPQuaternion(checkLPQRateSupported());
            }
        } else if (!(mEnabled & (1LL << GameRotationVector))){
            enable6AxisQuaternion(0);
        }

        writeBatchTimeout(batchMode);

        if (computeAndSetDmpState() < 0) {
            LOGE("HAL:ERR can't compute dmp state");
        }

    }//end of batch mode modify

    if (batchMode == 1) {
        /* set batch rates */
        if (setBatchDataRates() < 0) {
            LOGE("HAL:ERR can't set batch data rates");
        }
    } else {
        /* reset sensor rate */
        if (resetDataRates() < 0) {
            LOGE("HAL:ERR can't reset output rate back to original setting");
        }
    }

    return res;
}

/* Send empty event when:        */
/* 1. batch mode is not enabled  */
/* 2. no data in HW FIFO         */
/* return status zero if (2)     */
int MPLSensor::flush(int handle)
{
    VFUNC_LOG;

    int res = 0;
    int status = 0;
    android::String8 sname;
    int what = -1;

    getHandle(handle, what, sname);
    if (what < 0) {
        LOGE("HAL:flush - what=%d is invalid", what);
        return -EINVAL;
    }

    LOGV_IF(PROCESS_VERBOSE, "HAL: flush - select sensor %s (handle %d)", sname.string(), handle);

    if (((what != StepDetector) && (!(mEnabled & (1LL << what)))) ||
            ((what == StepDetector) && !(mFeatureActiveMask & INV_DMP_PEDOMETER))) {
        LOGE_IF(ENG_VERBOSE, "HAL: flush - sensor %s not enabled", sname.string());
        //return -EINVAL;
    }

    if(!(mBatchEnabled & (1LL << what))) {
        LOGV_IF(PROCESS_VERBOSE, "HAL:flush - batch mode not enabled for sensor %s (handle %d)", sname.string(), handle);
    }

    /*write sysfs */
    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            handle, mpu.flush_batch, getTimestamp());

    if (write_sysfs_int(mpu.flush_batch, handle) < 0) {
        LOGE("HAL:ERR can't write flush_batch");
    }

    return 0;
}

int MPLSensor::selectAndSetQuaternion(int batchMode, int mEnabled, long long featureMask)
{
    VFUNC_LOG;
    int res = 0;

    int64_t wanted;

    /* case for Ped Quaternion */
    if (batchMode == 1) {
        if ((featureMask & INV_DMP_PED_QUATERNION) &&
                (mEnabled & (1LL << GameRotationVector)) &&
                (mFeatureActiveMask & INV_DMP_PEDOMETER)) {
            enableLPQuaternion(0);
            enable6AxisQuaternion(0);
            enablePedQuaternion(1);

            /* set pedq rate */
            wanted = mBatchDelays[GameRotationVector];
            wanted = lookupSensorRate(wanted / 1000) * 1000;
            setPedQuaternionRate(wanted);
        } else if ((featureMask & INV_DMP_6AXIS_QUATERNION) &&
                (mEnabled & (1LL << GameRotationVector))) {
            enableLPQuaternion(0);
            enablePedQuaternion(0);
            enable6AxisQuaternion(1);

            /* set sixaxis rate */
            wanted = mBatchDelays[GameRotationVector];
            set6AxisQuaternionRate(wanted);
        } else {
            enablePedQuaternion(0);
            enable6AxisQuaternion(0);
        }
    } else {
        if(mEnabled & (1LL << GameRotationVector)) {
            enablePedQuaternion(0);
            enable6AxisQuaternion(0);
            enableLPQuaternion(checkLPQRateSupported());
        }
        else {
            enablePedQuaternion(0);
            enable6AxisQuaternion(0);
        }
    }

    return res;
}

/*
   Select Quaternion and Options for Batching

   ID_P    ID_GRV     HW Batch     Type
   a   1       1          1            PedQ, Ped Indicator, HW
   b   1       1          0            PedQ
   c   1       0          1            Ped Indicator, HW
   d   1       0          0            Ped Standalone, Ped Indicator
   e   0       1          1            6Q, HW
   f   0       1          0            6Q
   g   0       0          1            HW
   h   0       0          0            LPQ <defualt>
 */
int MPLSensor::computeBatchDataOutput()
{
    VFUNC_LOG;

    int featureMask = 0;
    if (mBatchEnabled == 0)
        return 0;//h

    uint64_t hardwareSensorMask = (1LL << Gyro)
        | (1LL << RawGyro)
        | (1LL << Accelerometer)
        | (1LL << MagneticField)
        | (1LL << RawMagneticField)
        | ((1LL << Pressure) & mPressureSensorPresent)
        | ((1LL << Light) & mLightSensorPresent)
        | ((1LL << Proximity) & mLightSensorPresent)
        ;
    LOGV_IF(ENG_VERBOSE, "hardwareSensorMask = 0x%0x, mBatchEnabled = 0x%0x",
            hardwareSensorMask, mBatchEnabled);

    if (mBatchEnabled & (1LL << StepDetector)) {
        if (mBatchEnabled & (1LL << GameRotationVector)) {
            if ((mBatchEnabled & hardwareSensorMask)) {
                featureMask |= INV_DMP_6AXIS_QUATERNION;//a
                featureMask |= INV_DMP_PED_INDICATOR;
                //LOGE("batch output: a");
            } else {
                featureMask |= INV_DMP_PED_QUATERNION;  //b
                featureMask |= INV_DMP_PED_INDICATOR;   //always piggy back a bit
                //LOGE("batch output: b");
            }
        } else {
            if (mBatchEnabled & hardwareSensorMask) {
                featureMask |= INV_DMP_PED_INDICATOR;   //c
                //LOGE("batch output: c");
            } else {
                featureMask |= INV_DMP_PED_STANDALONE;  //d
                featureMask |= INV_DMP_PED_INDICATOR;   //required for standalone
                //LOGE("batch output: d");
            }
        }
    } else if (mBatchEnabled & (1LL << GameRotationVector)) {
        featureMask |= INV_DMP_6AXIS_QUATERNION;        //e,f
        //LOGE("batch output: e,f");
    } else {
        LOGV_IF(ENG_VERBOSE,
                "HAL:computeBatchDataOutput: featuerMask=0x%x", featureMask);
        //LOGE("batch output: g");
        return 0; //g
    }

    LOGV_IF(ENG_VERBOSE,
            "HAL:computeBatchDataOutput: featuerMask=0x%x", featureMask);
    return featureMask;
}

int MPLSensor::getDmpPedometerFd()
{
    VFUNC_LOG;
    LOGV_IF(EXTRA_VERBOSE, "getDmpPedometerFd returning %d", dmp_pedometer_fd);
    return dmp_pedometer_fd;
}

/* @param [in] : outputType = 1 --event is from PED_Q        */
/*               outputType = 0 --event is from ID_SC, ID_P  */
int MPLSensor::readDmpPedometerEvents(sensors_event_t* data, int count,
        int32_t id, int outputType)
{
    VFUNC_LOG;

    int res = 0;
    char dummy[4];

    int numEventReceived = 0;
    int update = 0;

    LOGI_IF(0, "HAL: Read Pedometer Event ID=%d", id);
    switch (id) {
        case ID_P:
            if (mDmpPedometerEnabled && count > 0) {
                mPedUpdate = 1;
                /* Handles return event */
                LOGI("HAL: Step detected");
                update = sdHandler(&mSdEvents);
            }

            if (update && count > 0) {
                *data++ = mSdEvents;
                count--;
                numEventReceived++;
            }
            break;
    }

    if (!outputType) {
        // read dummy data per driver's request
        // only required if actual irq is issued
        read(dmp_pedometer_fd, dummy, 4);
    } else {
        return 1;
    }

    return numEventReceived;
}

int MPLSensor::getDmpSignificantMotionFd()
{
    VFUNC_LOG;

    LOGV_IF(EXTRA_VERBOSE, "getDmpSignificantMotionFd returning %d",
            dmp_sign_motion_fd);
    return dmp_sign_motion_fd;
}

int MPLSensor::readDmpSignificantMotionEvents(sensors_event_t* data, int count)
{
    VFUNC_LOG;

    int res = 0;
    char dummy[4];
    int significantMotion;
    FILE *fp;
    int sensors = mEnabled;
    int numEventReceived = 0;
    int update = 0;

    /* Technically this step is not necessary for now  */
    /* In the future, we may have meaningful values */
    fp = fopen(mpu.event_smd, "r");
    if (fp == NULL) {
        LOGE("HAL:cannot open event_smd");
        return 0;
    } else {
        if (fscanf(fp, "%d\n", &significantMotion) < 0 || fclose(fp) < 0) {
            LOGE("HAL:cannot read event_smd");
        }
    }
    LOGV_IF(EXTRA_VERBOSE,"HAL: Significant motion opened");

    if(mDmpSignificantMotionEnabled && count > 0) {
        /* By implementation, smd is disabled once an event is triggered */
        sensors_event_t temp;

        /* Handles return event */
        LOGI("HAL: SMD detected");
        int update = smHandler(&mSmEvents);
        if (update && count > 0) {
            *data++ = mSmEvents;
            count--;
            numEventReceived++;

            /* reset smd state */
            mDmpSignificantMotionEnabled = 0;
            mFeatureActiveMask &= ~INV_DMP_SIGNIFICANT_MOTION;

            /* auto disable this sensor */
            enableDmpSignificantMotion(0);
        }
    }

    // read dummy data per driver's request
    read(dmp_sign_motion_fd, dummy, 4);

    return numEventReceived;
}

int MPLSensor::enableDmpSignificantMotion(int en)
{
    VFUNC_LOG;

    int res = 0;
    uint64_t enabled_sensors = mEnabled;

    if (isMpuNonDmp())
        return res;

    //Toggle significant montion detection
    if(en) {
        LOGV_IF(ENG_VERBOSE, "HAL:Enabling Significant Motion");
        LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
                1, mpu.smd_enable, getTimestamp());
        if (write_sysfs_int(mpu.smd_enable, 1) < 0) {
            LOGE("HAL:ERR can't write DMP smd_enable");
            res = -1;
        }
        mFeatureActiveMask |= INV_DMP_SIGNIFICANT_MOTION;
    }
    else {
        LOGV_IF(ENG_VERBOSE, "HAL:Disabling Significant Motion");
        LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
                0, mpu.smd_enable, getTimestamp());
        if (write_sysfs_int(mpu.smd_enable, 0) < 0) {
            LOGE("HAL:ERR write DMP smd_enable");
        }
        mFeatureActiveMask &= ~INV_DMP_SIGNIFICANT_MOTION;
    }

    if ((res = computeAndSetDmpState()) < 0)
        return res;

    return res;
}

void MPLSensor::setInitial6QuatValue()
{
    VFUNC_LOG;

    if (!mInitial6QuatValueAvailable)
        return;

    /* convert to unsigned char array */
    size_t length = 16;
    unsigned char quat[16];
    convert_int_to_hex_char(mInitial6QuatValue, quat, 4);

    /* write to sysfs */
    LOGV_IF(EXTRA_VERBOSE, "HAL:sysfs:echo quat value > %s", mpu.six_axis_q_value);
    LOGV_IF(EXTRA_VERBOSE && ENG_VERBOSE, "quat=%d,%d,%d,%d", mInitial6QuatValue[0],
            mInitial6QuatValue[1],
            mInitial6QuatValue[2],
            mInitial6QuatValue[3]);
    FILE* fptr = fopen(mpu.six_axis_q_value, "w");
    if(fptr == NULL) {
        LOGE("HAL:could not open six_axis_q_value");
    } else {
        if (fwrite(quat, 1, length, fptr) != length || fclose(fptr) < 0) {
            LOGE("HAL:write six axis q value failed");
        } else {
            mInitial6QuatValueAvailable = 0;
        }
    }

    return;
}
int MPLSensor::writeSignificantMotionParams(uint32_t delayThreshold1,
        uint32_t delayThreshold2,
        uint32_t motionThreshold)
{
    VFUNC_LOG;

    int res = 0;

    // Write supplied values
    LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
            delayThreshold1, mpu.smd_delay_threshold, getTimestamp());
    res = write_sysfs_int(mpu.smd_delay_threshold, delayThreshold1);
    if (res == 0) {
        LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
                delayThreshold2, mpu.smd_delay_threshold2, getTimestamp());
        res = write_sysfs_int(mpu.smd_delay_threshold2, delayThreshold2);
    }
    if (res == 0) {
        LOGV_IF(SYSFS_VERBOSE, "HAL:sysfs:echo %d > %s (%lld)",
                motionThreshold, mpu.smd_threshold, getTimestamp());
        res = write_sysfs_int(mpu.smd_threshold, motionThreshold);
    }

    return res;
}

/* set batch data rate */
/* this function should be optimized */
int MPLSensor::setBatchDataRates()
{
    VFUNC_LOG;

    int res = 0;
    int tempFd = -1;
    int64_t wanted = NS_PER_SECOND;

    int64_t gyroRate = wanted;
    int64_t accelRate = wanted;
    int64_t compassRate = wanted;
    int64_t pressureRate = wanted;
    int64_t quatRate = wanted;
    int64_t nineAxisRate = wanted;
    int64_t compassSixAxisRate = wanted;

    int64_t gyroWakeRate = wanted;
    int64_t accelWakeRate = wanted;
    int64_t compassWakeRate = wanted;
    int64_t quatWakeRate = wanted;
    int64_t nineAxisWakeRate = wanted;
    int64_t compassSixAxisWakeRate = wanted;

    int mplGyroRate = wanted / 1000LL;
    int mplAccelRate = wanted / 1000LL;
    int mplCompassRate = wanted / 1000LL;
    int mplQuatRate = wanted / 1000LL;
    int mplQuatWakeRate = wanted / 1000LL;
    int mplNineAxisQuatRate = wanted / 1000LL;
    int mplNineAxisQuatWakeRate = wanted / 1000LL;
    int mplCompassQuatRate = wanted / 1000LL;
    int mplCompassQuatWakeRate = wanted / 1000LL;
    uint32_t i;

    /* take care of case where only one type of gyro sensors or
       compass sensors is turned on */
    CalibrationMode = mCalibrationMode;
    if (mBatchEnabled & (1LL << Gyro) || mBatchEnabled & (1LL << RawGyro)) {
        gyroRate = (mBatchDelays[Gyro] <= mBatchDelays[RawGyro]) ?
            (mBatchEnabled & (1LL << Gyro) ? mBatchDelays[Gyro] : mBatchDelays[RawGyro]):
            (mBatchEnabled & (1LL << RawGyro) ? mBatchDelays[RawGyro] : mBatchDelays[Gyro]);
    } else {
	mBatchDelays[Gyro] = wanted;
	mBatchDelays[RawGyro] = wanted;
    }
    LOGV_IF(EXTRA_VERBOSE,
            "HAL:Batch gyro wake sample rate: %lld", mBatchDelays[Gyro_Wake]);

    if (mBatchEnabled & (1LL << Gyro_Wake) || mBatchEnabled & (1LL << RawGyro_Wake)) {
        gyroWakeRate = (mBatchDelays[Gyro_Wake] <= mBatchDelays[RawGyro_Wake]) ?
            (mBatchEnabled & (1LL << Gyro_Wake) ? mBatchDelays[Gyro_Wake] : mBatchDelays[RawGyro_Wake]):
            (mBatchEnabled & (1LL << RawGyro_Wake) ? mBatchDelays[RawGyro_Wake] : mBatchDelays[Gyro_Wake]);
    } else {
	mBatchDelays[Gyro_Wake] = wanted;
	mBatchDelays[RawGyro_Wake] = wanted;
    }

    gyroRate = gyroRate < gyroWakeRate ? gyroRate : gyroWakeRate;

    if (mBatchEnabled & (1LL << MagneticField) || mBatchEnabled & (1LL << RawMagneticField)) {
        compassRate = (mBatchDelays[MagneticField] <= mBatchDelays[RawMagneticField]) ?
            (mBatchEnabled & (1LL << MagneticField) ? mBatchDelays[MagneticField] :
             mBatchDelays[RawMagneticField]) :
            (mBatchEnabled & (1LL << RawMagneticField) ? mBatchDelays[RawMagneticField] :
             mBatchDelays[MagneticField]);
    } else {
	mBatchDelays[RawMagneticField] = wanted;
	mBatchDelays[MagneticField] = wanted;
    }
    LOGV_IF(EXTRA_VERBOSE,
            "HAL:Batch compass wake sample rate: %lld", mBatchDelays[MagneticField_Wake]);

    if (mBatchEnabled & (1LL << MagneticField_Wake) || mBatchEnabled & (1LL << RawMagneticField_Wake)) {
        compassRate = (mBatchDelays[MagneticField_Wake] <= mBatchDelays[RawMagneticField_Wake]) ?
            (mBatchEnabled & (1LL << MagneticField_Wake) ? mBatchDelays[MagneticField_Wake] :
             mBatchDelays[RawMagneticField_Wake]) :
            (mBatchEnabled & (1LL << RawMagneticField_Wake) ? mBatchDelays[RawMagneticField_Wake] :
             mBatchDelays[MagneticField_Wake]);
    } else {
	mBatchDelays[RawMagneticField_Wake] = wanted;
	mBatchDelays[MagneticField_Wake] = wanted;
    }
    compassRate = compassRate < compassWakeRate ? compassRate : compassWakeRate;

    if ((mBatchEnabled & (1LL << Accelerometer)) || (mBatchEnabled & (1LL << LinearAccel))) {
	    if (mBatchEnabled & (1LL << LinearAccel)) {
		accelRate = (mBatchDelays[LinearAccel] < mBatchDelays[Accelerometer]) ? mBatchDelays[LinearAccel] : mBatchDelays[Accelerometer];
	    } else {
	    accelRate = mBatchDelays[Accelerometer];
	    }
	    LOGV_IF(EXTRA_VERBOSE,
			    "HAL:Batch accel sample rate: %lld", accelRate);
    } else {
	mBatchDelays[Accelerometer] = wanted;
    }

    if ((mBatchEnabled & (1LL << Accelerometer_Wake)) || (mBatchEnabled & (1LL << LinearAccel_Wake))) {
	    if (mBatchEnabled & (1LL << LinearAccel_Wake)) {
		accelWakeRate = (mBatchDelays[LinearAccel_Wake] < mBatchDelays[Accelerometer_Wake]) ? mBatchDelays[LinearAccel_Wake] : mBatchDelays[Accelerometer_Wake];
	    } else {
	    accelWakeRate = mBatchDelays[Accelerometer_Wake];
	    }
	    LOGV_IF(EXTRA_VERBOSE,
			    "HAL:Batch accel wake sample rate: %lld", accelWakeRate);
    } else {
	mBatchDelays[Accelerometer_Wake] = wanted;
    }

    accelRate = accelRate < accelWakeRate ? accelRate : accelWakeRate;

    if (mBatchEnabled & (1LL << Pressure) || mBatchEnabled & (1LL << Pressure_Wake)) {
	    pressureRate = mBatchDelays[Pressure] < mBatchDelays[Pressure_Wake] ?
		    mBatchDelays[Pressure] : mBatchDelays[Pressure_Wake];
    } else {
	mBatchDelays[Pressure] = wanted;
	mBatchDelays[Pressure_Wake] = wanted;
    }

    int64_t tempQuatRate = mBatchDelays[GameRotationVector] < mBatchDelays[GameRotationVector_Wake] ?
            mBatchDelays[GameRotationVector] : mBatchDelays[GameRotationVector_Wake];
    if ((mCalibrationMode & mpl_quat) || ((mFeatureActiveMask & INV_DMP_PED_QUATERNION) ||
                (mFeatureActiveMask & INV_DMP_6AXIS_QUATERNION) ||
		(mFeatureActiveMask & INV_DMP_6AXIS_QUATERNION_WAKE))) {
        if ((mBatchEnabled & (1LL << GameRotationVector)) ||
            (mBatchEnabled & (1LL << GameRotationVector_Wake)) ||
	    (mBatchEnabled & (1LL << LinearAccel)) ||
	    (mBatchEnabled & (1LL << LinearAccel_Wake)) ||
	    (mBatchEnabled & (1LL << Gravity)) ||
	    (mBatchEnabled & (1LL << Gravity_Wake))) {
            quatRate = mBatchDelays[GameRotationVector];
	    quatRate = (mBatchDelays[LinearAccel] < quatRate) ? mBatchDelays[LinearAccel] : quatRate;
	    quatRate = (mBatchDelays[Gravity] < quatRate) ? mBatchDelays[Gravity] : quatRate;
            quatWakeRate = mBatchDelays[GameRotationVector_Wake];
	    quatWakeRate = (mBatchDelays[LinearAccel_Wake] < quatWakeRate) ? mBatchDelays[LinearAccel_Wake] : quatWakeRate;
	    quatWakeRate = (mBatchDelays[Gravity_Wake] < quatWakeRate) ? mBatchDelays[Gravity_Wake] : quatWakeRate;
            mplQuatRate = (int) quatRate / 1000LL;
            mplQuatRate = lookupSensorRate(mplQuatRate);
            mplQuatWakeRate = (int) quatWakeRate / 1000LL;
            mplQuatWakeRate = lookupSensorRate(mplQuatWakeRate);
            adapter_set_sample_rate(mplQuatRate, ID_GRV);
            adapter_set_sample_rate(mplQuatWakeRate, ID_GRVW);
            set6AxisQuaternionRate(mplQuatRate * 1000LL);
            set6AxisQuaternionWakeRate(mplQuatWakeRate * 1000LL);
            setPedQuaternionRate(mplQuatRate * 1000LL);
            LOGV_IF(EXTRA_VERBOSE,
                    "HAL:Batch rv final sample rate: %lld", tempQuatRate);
            LOGV_IF(EXTRA_VERBOSE,"HAL:Batch grv sample rate: %lld - %d",
                    quatRate, mplQuatRate);
            LOGV_IF(EXTRA_VERBOSE,"HAL:Batch grv wake sample rate: %lld - %d",
                    quatWakeRate, mplQuatWakeRate);
        } else {
	    mBatchDelays[GameRotationVector] = wanted;
	    mBatchDelays[LinearAccel] = wanted;
	    mBatchDelays[Gravity] = wanted;
	    mBatchDelays[GameRotationVector_Wake] = wanted;
	    mBatchDelays[LinearAccel_Wake] = wanted;
	    mBatchDelays[Gravity_Wake] = wanted;
        }
    } else {
	    mplQuatRate = (int) tempQuatRate / 1000LL;
	    mplQuatRate = lookupSensorRate(mplQuatRate);
	    quatRate = mplQuatRate * 1000LL;
	    adapter_set_sample_rate(mplQuatRate, ID_GRV);
	    adapter_set_sample_rate(mplQuatRate, ID_GRVW);
    }

    mplGyroRate = (int) gyroRate / 1000LL;
    mplAccelRate = (int) accelRate / 1000LL;
    mplCompassRate = (int) compassRate / 1000LL;

    /* converts to ICM20645 rate */
    mplGyroRate = lookupSensorRate(mplGyroRate);
    mplAccelRate = lookupSensorRate(mplAccelRate);
    mplCompassRate = lookupSensorRate(mplCompassRate);

    gyroRate = mplGyroRate * 1000LL;
    accelRate = mplAccelRate * 1000LL;
    compassRate = mplCompassRate * 1000LL;

    /* set rate in MPL */
    /* compass can only do 100Hz max */
    adapter_set_sample_rate(mplGyroRate, ID_GY);
    adapter_set_sample_rate(mplAccelRate, ID_A);
    adapter_set_sample_rate(mplCompassRate, ID_M);

    adapter_set_sample_rate(mplGyroRate, ID_GYW);
    adapter_set_sample_rate(mplAccelRate, ID_AW);
    adapter_set_sample_rate(mplCompassRate, ID_MW);

    LOGV_IF(PROCESS_VERBOSE,
            "HAL:MPL gyro sample rate: (mpl)=%d us (mpu)=%.2f Hz", mplGyroRate, NS_PER_SECOND_FLOAT / gyroRate);
    LOGV_IF(PROCESS_VERBOSE,
            "HAL:MPL accel sample rate: (mpl)=%d us (mpu)=%.2f Hz", mplAccelRate, NS_PER_SECOND_FLOAT / accelRate);
    LOGV_IF(PROCESS_VERBOSE,
            "HAL:MPL compass sample rate: (mpl)=%d us (mpu)=%.2f Hz", mplCompassRate, NS_PER_SECOND_FLOAT / compassRate);
    LOGV_IF(PROCESS_VERBOSE,
            "HAL:MPL quat sample rate: (mpl)=%d us (mpu)=%.2f Hz", mplQuatRate, NS_PER_SECOND_FLOAT / quatRate);

    if (!(mCalibrationMode & mpl_quat)) {
	    if (mBatchEnabled & (1LL << RotationVector) || mBatchEnabled & (1LL << RotationVector_Wake)) {
		    nineAxisRate = mBatchDelays[RotationVector] < mBatchDelays[RotationVector_Wake] ?
            mBatchDelays[RotationVector] : mBatchDelays[RotationVector_Wake];
		    nineAxisWakeRate = mBatchDelays[RotationVector_Wake];
	    } else {
		mBatchDelays[RotationVector] = wanted;
		mBatchDelays[RotationVector_Wake] = wanted;
	    }

	    if (mBatchEnabled & (1LL << Orientation) || mBatchEnabled & (1LL << Orientation_Wake)) {
		    nineAxisRate = mBatchDelays[Orientation] < mBatchDelays[Orientation_Wake] ?
			    mBatchDelays[Orientation] : mBatchDelays[Orientation_Wake];
		    nineAxisWakeRate = mBatchDelays[Orientation_Wake];
	    } else {
		    mBatchDelays[Orientation] = wanted;
		    mBatchDelays[Orientation_Wake] = wanted;
	    }

	    if (mBatchEnabled & (1LL << GeomagneticRotationVector) || mBatchEnabled & (1LL << GeomagneticRotationVector_Wake)) {
		    compassSixAxisRate = mBatchDelays[GeomagneticRotationVector] < mBatchDelays[GeomagneticRotationVector_Wake] ?
			    mBatchDelays[GeomagneticRotationVector] : mBatchDelays[GeomagneticRotationVector_Wake];
		    compassSixAxisWakeRate = mBatchDelays[GeomagneticRotationVector_Wake];
	    } else {
		mBatchDelays[GeomagneticRotationVector] = wanted;
		mBatchDelays[GeomagneticRotationVector_Wake] = wanted;
	    }

        mplNineAxisQuatRate = (int) nineAxisRate / 1000LL;
        mplNineAxisQuatWakeRate = (int) nineAxisWakeRate / 1000LL;
        mplCompassQuatRate = (int) compassSixAxisRate / 1000LL;
        mplCompassQuatWakeRate = (int) compassSixAxisWakeRate / 1000LL;

        /* converts to ICM20648 rate */
        mplNineAxisQuatRate = lookupSensorRate(mplNineAxisQuatRate);
        mplNineAxisQuatWakeRate = lookupSensorRate(mplNineAxisQuatWakeRate);
        mplCompassQuatRate = lookupSensorRate(mplCompassQuatRate);
        mplCompassQuatWakeRate = lookupSensorRate(mplCompassQuatWakeRate);

        nineAxisRate = mplNineAxisQuatRate * 1000LL;
        nineAxisWakeRate = mplNineAxisQuatWakeRate * 1000LL;
        compassSixAxisRate = mplCompassQuatRate * 1000LL;
        compassSixAxisWakeRate = mplCompassQuatWakeRate * 1000LL;

        adapter_set_sample_rate((int)mplNineAxisQuatRate, ID_RV);
        adapter_set_sample_rate((int)mplNineAxisQuatWakeRate, ID_RVW);
        adapter_set_sample_rate((int)mplCompassQuatRate, ID_GMRV);
        adapter_set_sample_rate((int)mplCompassQuatWakeRate, ID_GMRVW);

        LOGV_IF(PROCESS_VERBOSE,
            "HAL:MPL nine quat sample rate: (mpl)=%d us (mpu)=%.2f Hz", mplNineAxisQuatRate, NS_PER_SECOND_FLOAT / nineAxisRate);
        LOGV_IF(PROCESS_VERBOSE,
            "HAL:MPL compass quat sample rate: (mpl)=%d us (mpu)=%.2f Hz", mplCompassQuatRate, NS_PER_SECOND_FLOAT / compassSixAxisRate);
        LOGV_IF(PROCESS_VERBOSE,
            "HAL:MPL nine quat wake sample rate: (mpl)=%d us (mpu)=%.2f Hz", mplNineAxisQuatWakeRate, NS_PER_SECOND_FLOAT / nineAxisWakeRate);
        LOGV_IF(PROCESS_VERBOSE,
            "HAL:MPL compass quat wake sample rate: (mpl)=%d us (mpu)=%.2f Hz", mplCompassQuatWakeRate, NS_PER_SECOND_FLOAT / compassSixAxisWakeRate);
    }

    for (i = 0; i < mNumSysfs; i++) {
        if (mSysfsMask[i].sensorMask) {
		switch (mSysfsMask[i].sensorMask) {
		case INV_THREE_AXIS_GYRO:
		case INV_THREE_AXIS_RAW_GYRO:
			mSysfsMask[i].nominalRate = gyroRate;
			break;
		case INV_THREE_AXIS_GYRO_WAKE:
		case INV_THREE_AXIS_RAW_GYRO_WAKE:
			mSysfsMask[i].nominalRate = gyroWakeRate;
			break;
		case INV_THREE_AXIS_ACCEL:
			mSysfsMask[i].nominalRate = accelRate;
			break;
		case INV_THREE_AXIS_ACCEL_WAKE:
			mSysfsMask[i].nominalRate = accelWakeRate;
			break;
		case INV_THREE_AXIS_COMPASS:
		case INV_THREE_AXIS_COMPASS_WAKE:
		case INV_THREE_AXIS_RAW_COMPASS:
		case INV_THREE_AXIS_RAW_COMPASS_WAKE:
			mSysfsMask[i].nominalRate = compassRate;
			break;
		case VIRTUAL_SENSOR_9AXES_MASK:
			mSysfsMask[i].nominalRate = nineAxisRate;
			break;
		case VIRTUAL_SENSOR_9AXES_MASK_WAKE:
			mSysfsMask[i].nominalRate = nineAxisWakeRate;
			break;
		case VIRTUAL_SENSOR_GYRO_6AXES_MASK:
			mSysfsMask[i].nominalRate = quatRate;
			break;
		case VIRTUAL_SENSOR_GYRO_6AXES_MASK_WAKE:
			mSysfsMask[i].nominalRate = quatWakeRate;
			break;
		case VIRTUAL_SENSOR_MAG_6AXES_MASK:
			mSysfsMask[i].nominalRate = compassSixAxisRate;
			break;
		case VIRTUAL_SENSOR_MAG_6AXES_MASK_WAKE:
			mSysfsMask[i].nominalRate = compassSixAxisWakeRate;
			break;
		case INV_ONE_AXIS_PRESSURE:
		case INV_ONE_AXIS_PRESSURE_WAKE:
			mSysfsMask[i].nominalRate = pressureRate;
			break;
		case INV_ONE_AXIS_LIGHT:
			break;
		default:
			break;
		}
	}
    }

   for (i = 0; i < mNumSysfs; i++) {
       if (mSysfsMask[i].sensorMask && mSysfsMask[i].setRate && mSysfsMask[i].en) {
	    (this->*(mSysfsMask[i].setRate))(mSysfsMask[i].nominalRate);
           if (mSysfsMask[i].second_setRate) {
	       (this->*(mSysfsMask[i].second_setRate))(mSysfsMask[i].nominalRate);
           }
       }
   }

    return res;
}

/* Set sensor rate */
/* this function should be optimized */
int MPLSensor::resetDataRates()
{
    VFUNC_LOG;

    int64_t wanted =     NS_PER_SECOND;
    int64_t ns =  wanted;
    uint32_t i, j;
    int64_t gestureRate = wanted;
    int64_t engineRate = wanted;

    LOGV_IF(ENG_VERBOSE,"HAL:resetDataRates mEnabled=%lld", mEnabled);
    CalibrationMode = mCalibrationMode;

    if (!(mCalibrationMode & dmp)) {
        /* search the minimum delay requested across all enabled sensors */
        /* skip setting rates if it is not changed */
        for (i = 0; i < ID_NUMBER; i++) {
            if (mEnabled & (1LL << i)) {
                ns = mDelays[i];
                if ((wanted == ns) && (i != Pressure)) {
                    LOGV_IF(ENG_VERBOSE, "skip resetDataRates : same delay mDelays[%d]=%lld", i,mDelays[i]);
                }
                if (i != StepDetector && i != StepDetector_Wake && i != StepCounter &&
                        i != StepCounter_Wake && i != Tilt &&  i != Pickup) {
                    LOGV_IF(ENG_VERBOSE, "resetDataRates - mDelays[%d]=%lld", i, mDelays[i]);
                    wanted = wanted < mDelays[i] ? wanted : mDelays[i];
                }
            }
        }
        LOGV_IF(ENG_VERBOSE,"HAL:resetDataRates wanted=%lld", wanted);
        wanted = lookupSensorRate(wanted/1000) * 1000;
   }

   for (i = 0; i < mNumSysfs; i++)
	mSysfsMask[i].nominalRate = mSysfsMask[i].minimumNonBatchRate;

   for (i = 0; i < ID_NUMBER; i++) {
       if (mEnabled & (1LL << i)) {
           int64_t ns = mDelays[i];
           wanted = lookupSensorRate(ns/1000) * 1000;
           for (j = 0; j < mNumSysfs; j++) {
		if (mSysfsMask[j].sensorMask & mCurrentSensorMask[i].sensorMask) {
			if (mSysfsMask[j].nominalRate > wanted) {
			    mSysfsMask[j].nominalRate = wanted;
                        }
                }
           }
           LOGV_IF(ENG_VERBOSE,"HAL:resetDataRates sensor %s wanted=%lld",
					mCurrentSensorMask[i].sname.string(), wanted);
       }
   }

   if (mCalibrationMode & mpl_compass) {
   	for (i = 0; i < mNumSysfs; i++) {
   		if ((mSysfsMask[i].sensorMask == INV_THREE_AXIS_RAW_COMPASS) ||
   		   (mSysfsMask[i].sensorMask == INV_THREE_AXIS_RAW_COMPASS_WAKE)) {
			mSysfsMask[i].nominalRate = 28571428; //iniate as 35Hz.
			LOGV_IF(PROCESS_VERBOSE, "init Mag rate = %lld\n", NS_PER_SECOND/mSysfsMask[i].nominalRate);
                   }
            }
   }
   if (mEnabled & ((1LL << SENSORS_MAGNETIC_FIELD_HANDLE)
                 | (1LL << SENSORS_RAW_MAGNETIC_FIELD_HANDLE)
                 | (1LL << SENSORS_MAGNETIC_FIELD_WAKEUP_HANDLE)
                 | (1LL << SENSORS_RAW_MAGNETIC_FIELD_WAKEUP_HANDLE))) {
	if (mCalibrationMode & mpl_compass) {
		for (i = 0; i < mNumSysfs; i++) {
			if ((mSysfsMask[i].sensorMask == INV_THREE_AXIS_RAW_COMPASS) ||
			   (mSysfsMask[i].sensorMask == INV_THREE_AXIS_RAW_COMPASS_WAKE)) {
			        if (mSysfsMask[i].nominalRate > mDelays[SENSORS_MAGNETIC_FIELD_HANDLE])
				    mSysfsMask[i].nominalRate = mDelays[SENSORS_MAGNETIC_FIELD_HANDLE];
			        if (mSysfsMask[i].nominalRate > mDelays[SENSORS_RAW_MAGNETIC_FIELD_HANDLE])
				    mSysfsMask[i].nominalRate = mDelays[SENSORS_RAW_MAGNETIC_FIELD_HANDLE];
			        if (mSysfsMask[i].nominalRate > mDelays[SENSORS_MAGNETIC_FIELD_WAKEUP_HANDLE])
				    mSysfsMask[i].nominalRate = mDelays[SENSORS_MAGNETIC_FIELD_WAKEUP_HANDLE];
			        if (mSysfsMask[i].nominalRate > mDelays[SENSORS_RAW_MAGNETIC_FIELD_WAKEUP_HANDLE])
				    mSysfsMask[i].nominalRate = mDelays[SENSORS_RAW_MAGNETIC_FIELD_WAKEUP_HANDLE];
				LOGV_IF(PROCESS_VERBOSE, "raw Mag rate = %lld\n", NS_PER_SECOND/mSysfsMask[i].nominalRate);
                        }
                 }
         }
    }

   if (mEnabled & ((1LL << SENSORS_ORIENTATION_HANDLE)
                 | (1LL << SENSORS_ROTATION_VECTOR_HANDLE)
                 | (1LL << SENSORS_ORIENTATION_WAKEUP_HANDLE)
                 | (1LL << SENSORS_ROTATION_VECTOR_WAKEUP_HANDLE))) {
	if (mCalibrationMode & mpl_quat) {
		for (i = 0; i < mNumSysfs; i++) {
			if ((mSysfsMask[i].sensorMask == INV_THREE_AXIS_RAW_COMPASS) ||
			   (mSysfsMask[i].sensorMask == INV_THREE_AXIS_RAW_COMPASS_WAKE)) {
                            if (mSysfsMask[i].nominalRate > 28571428)
			        mSysfsMask[i].nominalRate = 28571428;
			    LOGV_IF(PROCESS_VERBOSE, "9axes Mag rate = %lld\n", NS_PER_SECOND/mSysfsMask[i].nominalRate);
                        }
                 }
         }
    }

   if (mEnabled & ((1LL << SENSORS_GEOMAGNETIC_ROTATION_VECTOR_HANDLE)
                 | (1LL << SENSORS_GEOMAGNETIC_ROTATION_VECTOR_WAKEUP_HANDLE))) {
	if (mCalibrationMode & mpl_quat) {
		for (i = 0; i < mNumSysfs; i++) {
			if ((mSysfsMask[i].sensorMask == INV_THREE_AXIS_RAW_COMPASS) ||
			   (mSysfsMask[i].sensorMask == INV_THREE_AXIS_RAW_COMPASS_WAKE)) {
                            if (mSysfsMask[i].nominalRate > 14285714)
			        mSysfsMask[i].nominalRate = 14285714;
                            LOGV_IF(PROCESS_VERBOSE, "Geomag Mag rate = %lld\n", NS_PER_SECOND/mSysfsMask[i].nominalRate);
                        }
                 }
         }
    }
   for (i = 0; i < mNumSysfs; i++)
       mSysfsMask[i].engineRate = NS_PER_SECOND;

   for (i = 0; i < mNumSysfs; i++) {
       if (mSysfsMask[i].sensorMask && mSysfsMask[i].setRate && mSysfsMask[i].en) {
	    (this->*(mSysfsMask[i].setRate))(mSysfsMask[i].nominalRate);
           if (mSysfsMask[i].second_setRate) {
	       (this->*(mSysfsMask[i].second_setRate))(mSysfsMask[i].nominalRate);
           }
       }
   }

    /* set mpl data rate */
    for (i = 0; i < mNumSysfs; i++) {
        if (mSysfsMask[i].sensorMask && mSysfsMask[i].en) {
                engineRate = mSysfsMask[i].engineRate;
		switch (mSysfsMask[i].sensorMask) {
		case INV_THREE_AXIS_GYRO:
		case INV_THREE_AXIS_RAW_GYRO:
		case INV_THREE_AXIS_GYRO_WAKE:
		case INV_THREE_AXIS_RAW_GYRO_WAKE:
                    adapter_set_sample_rate((int)engineRate/1000LL, ID_GY);
		    adapter_set_sample_rate((int)engineRate/1000LL, ID_GYW);
                    LOGV_IF(PROCESS_VERBOSE,
			"HAL:MPL gyro sample rate: (mpl)=%lld us (mpu)=%.2f Hz",
			engineRate/1000LL, NS_PER_SECOND_FLOAT / engineRate);
                    LOGV_IF(PROCESS_VERBOSE,
                        "HAL:MPL gyro wake sample rate: (mpl)=%lld us (mpu)=%.2f Hz",
                         engineRate/1000LL, NS_PER_SECOND_FLOAT / engineRate);
		    break;
		case INV_THREE_AXIS_ACCEL:
		case INV_THREE_AXIS_ACCEL_WAKE:
                    adapter_set_sample_rate((int)engineRate/1000LL, ID_A);
                    adapter_set_sample_rate((int)engineRate/1000LL, ID_AW);
                    LOGV_IF(PROCESS_VERBOSE,
                          "HAL:MPL accel wake sample rate: (mpl)=%lld us (mpu)=%.2f Hz",
                            engineRate/1000LL, NS_PER_SECOND_FLOAT / engineRate);

                    LOGV_IF(PROCESS_VERBOSE,
                          "HAL:MPL accel sample rate: (mpl)=%lld us (mpu)=%.2f Hz",
                                 engineRate/1000LL, NS_PER_SECOND_FLOAT / engineRate);
                    break;
		case INV_THREE_AXIS_COMPASS:
		case INV_THREE_AXIS_RAW_COMPASS:
		case INV_THREE_AXIS_COMPASS_WAKE:
		case INV_THREE_AXIS_RAW_COMPASS_WAKE:
                    adapter_set_sample_rate((int)engineRate/1000LL, ID_M);
                    LOGV_IF(PROCESS_VERBOSE,
                          "HAL:MPL compass sample rate: (mpl)=%lld us (mpu)=%.2f Hz",
                                engineRate/1000LL, NS_PER_SECOND_FLOAT / engineRate);
                    LOGV_IF(PROCESS_VERBOSE,
                           "HAL:MPL compass wake sample rate: (mpl)=%lld us (mpu)=%.2f Hz",
                             engineRate/1000LL, NS_PER_SECOND_FLOAT / engineRate);
		    break;
		case VIRTUAL_SENSOR_9AXES_MASK:
		case VIRTUAL_SENSOR_9AXES_MASK_WAKE:
                    adapter_set_orientation_sample_rate((int)engineRate/1000LL, ID_O);
                    adapter_set_sample_rate((int)engineRate/1000LL, ID_RV);
                    adapter_set_sample_rate((int)engineRate/1000LL, ID_RVW);
                    LOGV_IF(PROCESS_VERBOSE,
                            "HAL:MPL 9quat sample rate: (mpl)=%lld us (mpu)=%.2f Hz",
                             engineRate/1000LL, NS_PER_SECOND_FLOAT / engineRate);
                    LOGV_IF(PROCESS_VERBOSE,
                             "HAL:MPL 9quat wake sample rate: (mpl)=%d us (mpu)=%.2f Hz",
                             engineRate/1000LL, 1000000.f / engineRate);
                    break;
		case VIRTUAL_SENSOR_GYRO_6AXES_MASK:
		case VIRTUAL_SENSOR_GYRO_6AXES_MASK_WAKE:
                    adapter_set_linear_acceleration_sample_rate((int)engineRate/1000LL, ID_LA);
                    adapter_set_gravity_sample_rate((int)engineRate/1000LL, ID_GR);
                    adapter_set_sample_rate((int)engineRate/1000LL, ID_GRV);
                    adapter_set_sample_rate((int)engineRate/1000LL, ID_GRVW);
                    LOGV_IF(PROCESS_VERBOSE,
                          "HAL:MPL 6quat sample rate: (mpl)=%lld us (mpu)=%.2f Hz",
                                 engineRate/1000LL, NS_PER_SECOND_FLOAT / engineRate);
		    break;
		case VIRTUAL_SENSOR_MAG_6AXES_MASK:
		case VIRTUAL_SENSOR_MAG_6AXES_MASK_WAKE:
                    adapter_set_sample_rate((int)engineRate/1000LL, ID_GMRV);
                    adapter_set_sample_rate((int)engineRate/1000LL, ID_GMRVW);
                    LOGV_IF(PROCESS_VERBOSE,
                        "HAL:MPL mag quat sample rate: (mpl)=%lld us (mpu)=%.2f Hz",
                         engineRate/1000LL, NS_PER_SECOND_FLOAT / engineRate);
		    break;
		default:
		    break;
		}
	}
   }

    /* set mpl data rate for gesture algo */
    if (mCalibrationMode & mpl_gesture) {
        gestureRate = 20000000LL;
        for (i = 0; i < mNumSysfs; i++) {
            if(mSysfsMask[i].en && ((mSysfsMask[i].sensorMask & INV_THREE_AXIS_ACCEL)
                || (mSysfsMask[i].sensorMask & INV_THREE_AXIS_ACCEL_WAKE))){
                if(gestureRate > mSysfsMask[i].nominalRate && mSysfsMask[i].nominalRate)
                    gestureRate = mSysfsMask[i].nominalRate;
            }
        }
        adapter_set_sample_rate(gestureRate/1000LL, ID_P); // gesture algothms are sharing sampling rate
        LOGV_IF(PROCESS_VERBOSE, "gestureRate = %llu", gestureRate/1000LL);
    }

   inv_set_quat_sample_rate(ISENSOR_RATE_220HZ);

   return 0;
}

void MPLSensor::resetMplStates()
{
    VFUNC_LOG;
    LOGV_IF(ENG_VERBOSE, "HAL:resetMplStates()");

    inv_gyro_was_turned_off();
    inv_accel_was_turned_off();
    inv_compass_was_turned_off();
    inv_quaternion_sensor_was_turned_off();

    adapter_gyro_reset_timestamp();
    adapter_accel_reset_timestamp();
    adapter_compass_reset_timestamp();

    resetGyroDecimatorStates();
    resetAccelDecimatorStates();
    resetMagDecimatorStates();

    return;
}

void MPLSensor::resetGyroDecimatorStates()
{
    gyro_timestamp0 = 0;
    rawgyro_timestamp0 = 0;
    gameRV_timestamp0 = 0;
    RV_timestamp0 = 0;
    orientation_timestamp0 = 0;
    gravity_timestamp0 = 0;
    LA_timestamp0 = 0;

    gyro_wake_timestamp0 = 0;
    rawgyro_wake_timestamp0 = 0;
    gameRV_wake_timestamp0 = 0;
    RV_wake_timestamp0 = 0;
    orientation_wake_timestamp0 = 0;
    gravity_wake_timestamp0 = 0;
    LA_wake_timestamp0 = 0;

    return;
}

void MPLSensor::resetAccelDecimatorStates()
{
    accel_timestamp0 = 0;
    geomagRV_timestamp0 = 0;

    accel_wake_timestamp0 = 0;
    geomagRV_wake_timestamp0 = 0;

    return;
}

void MPLSensor::resetMagDecimatorStates()
{
    compass_timestamp0 = 0;
    rawcompass_timestamp0 = 0;

    compass_wake_timestamp0 = 0;
    rawcompass_wake_timestamp0 = 0;

    return;
}

void MPLSensor::initBias()
{
    VFUNC_LOG;

    LOGV_IF(ENG_VERBOSE, "HAL:inititalize dmp and device offsets to 0");
    if(write_attribute_sensor_continuous(accel_x_dmp_bias_fd, 0) < 0) {
        LOGE("HAL:Error writing to accel_x_dmp_bias");
    }
    if(write_attribute_sensor_continuous(accel_y_dmp_bias_fd, 0) < 0) {
        LOGE("HAL:Error writing to accel_y_dmp_bias");
    }
    if(write_attribute_sensor_continuous(accel_z_dmp_bias_fd, 0) < 0) {
        LOGE("HAL:Error writing to accel_z_dmp_bias");
    }

    if(write_attribute_sensor_continuous(accel_x_offset_fd, 0) < 0) {
        LOGE("HAL:Error writing to accel_x_offset");
    }
    if(write_attribute_sensor_continuous(accel_y_offset_fd, 0) < 0) {
        LOGE("HAL:Error writing to accel_y_offset");
    }
    if(write_attribute_sensor_continuous(accel_z_offset_fd, 0) < 0) {
        LOGE("HAL:Error writing to accel_z_offset");
    }

    if(write_attribute_sensor_continuous(gyro_x_dmp_bias_fd, 0) < 0) {
        LOGE("HAL:Error writing to gyro_x_dmp_bias");
    }
    if(write_attribute_sensor_continuous(gyro_y_dmp_bias_fd, 0) < 0) {
        LOGE("HAL:Error writing to gyro_y_dmp_bias");
    }
    if(write_attribute_sensor_continuous(gyro_z_dmp_bias_fd, 0) < 0) {
        LOGE("HAL:Error writing to gyro_z_dmp_bias");
    }

    if(write_attribute_sensor_continuous(gyro_x_offset_fd, 0) < 0) {
        LOGE("HAL:Error writing to gyro_x_offset");
    }
    if(write_attribute_sensor_continuous(gyro_y_offset_fd, 0) < 0) {
        LOGE("HAL:Error writing to gyro_y_offset");
    }
    if(write_attribute_sensor_continuous(gyro_z_offset_fd, 0) < 0) {
        LOGE("HAL:Error writing to gyro_z_offset");
    }
    return;
}

int MPLSensor::lookupSensorRate(int inputRateUs)
{
    if(adapter_get_internal_sampling_rate(chip_ID) == 1000)
        return inputRateUs;

    switch (inputRateUs)
    {
        case ASENSOR_RATE_200HZ:
            return ISENSOR_RATE_220HZ;
        case ASENSOR_RATE_100HZ:
            return ISENSOR_RATE_110HZ;
        case ASENSOR_RATE_50HZ:
            return ISENSOR_RATE_55HZ;
        case ASENSOR_RATE_30HZ:
            return ISENSOR_RATE_31HZ;
    }
    return inputRateUs;
}

int MPLSensor::setAccelCov() {
    FILE *fp;
    int res = 0;
    char firmware_file[100];
    int bank[] = {0x30, 0x40, 0x50, 0x60,
        0x77, 0x88, 0x99, 0xAA,
        0xBB, 0xCC, 0xCC, 0xDD,
        0xEE, 0xFF};

    LOGV_IF(EXTRA_VERBOSE, "HAL:sysfs:echo Accel Cov value > %s", mpu.d_misc_accel_cov);
    int fd = open(mpu.d_misc_accel_cov, O_RDWR);

    res = write_attribute_sensor(fd, (char *) bank);
    if (res < 0)
        LOGE("HAL:Error writing to d_misc_accel_cov");

    close(fd);
    return res;
}

int MPLSensor::setCompassCov() {
    FILE *fp;
    int res = 0;
    char firmware_file[100];
    int bank[] = {0x33, 0x43, 0x55, 0x66,
        0x77, 0x88, 0x99, 0xAA,
        0xBB, 0xCC, 0xCC, 0xDD,
        0xEE, 0xFF};
    int bank_curr[] = {0x38, 0x93, 0xFF55, 0x66,
        0x77, 0xA8, 0x99, 0xAA,
        0xBBFF, 0xFFCC, 0xCC, 0xDD,
        0xEE, 0xFFCF};

    LOGV_IF(EXTRA_VERBOSE, "HAL:sysfs:echo Compass Cov value > %s", mpu.d_misc_compass_cov);
    int fd = open(mpu.d_misc_compass_cov, O_RDWR);

    res = write_attribute_sensor(fd, (char *) bank);
    if (res < 0)
        LOGE("HAL:Error writing to d_misc_compass_cov");

    fd = open(mpu.d_misc_current_compass_cov, O_RDWR);
    res = write_attribute_sensor(fd, (char *) bank_curr);
    if (res < 0)
        LOGE("HAL:Error writing to d_misc_current_compass_cov");

    return res;
}

/* set global accuracy flags.  sensor 0=gyro 1=accel 2=mag */
void MPLSensor::setAccuracy(int sensor, int accuracy)
{
    if (sensor == 0) {
        mGyroAccuracy = mGyroAccuracy < accuracy ? accuracy : mGyroAccuracy;
        return;
    }

    if (sensor == 1) {
        mAccelAccuracy = mAccelAccuracy < accuracy ? accuracy : mAccelAccuracy;
        return;
    }

    if (sensor == 2) {
        /* compass accuracy could drop due to disturbance */
        mCompassAccuracy = accuracy;
        return;
    }

    return;
}

/*TODO: reg_dump in a separate file*/
void MPLSensor::sys_dump(bool fileMode)
{
    VFUNC_LOG;

    char sysfs_path[MAX_SYSFS_NAME_LEN];
    char scan_element_path[MAX_SYSFS_NAME_LEN];

    memset(sysfs_path, 0, sizeof(sysfs_path));
    memset(scan_element_path, 0, sizeof(scan_element_path));
    inv_get_sysfs_path(sysfs_path);
    sprintf(scan_element_path, "%s%s", sysfs_path, "/scan_elements");

    read_sysfs_dir(fileMode, sysfs_path);
    read_sysfs_dir(fileMode, scan_element_path);

    dump_dmp_img();
    return;
}


void MPLSensor::printTimeProfile(int64_t &previousTime, int64_t currentTime, int64_t sensorTs, int64_t lastSensorTs)
{
    if (currentTime == 0) {
        currentTime = getTimestamp();
    }
    int64_t dhtime = sensorTs - lastSensorTs;
    if (lastSensorTs == 0) {
        dhtime = sensorTs - htime;
        htime = sensorTs;
    }
    int64_t dtime = currentTime - previousTime;
    int64_t stime = abs(currentTime - sensorTs);
    int64_t mCurrentRate = dhtime;
    float_t interval = abs(dhtime - mCurrentRate) / mCurrentRate;  //non-batch only

    if (stime > 0) {
        //LOGV("time_profile: dhtime=%lldms interval err=%.2fp", dhtime/1000000, interval * 100);
        //LOGV("time_profile: dtime=%lldms sync err=%lldms", dtime/1000000, stime/1000000);
        //LOGV("time_profile: ctime=%lld ts=%lld diff=%lld", currentTime, sensorTs, stime/1000000);
    }
}
