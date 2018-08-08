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

#ifndef ANDROID_SENSORS_H
#define ANDROID_SENSORS_H

#include <stdint.h>
#include <errno.h>
#include <sys/cdefs.h>
#include <sys/types.h>

#include <linux/input.h>

#include <hardware/hardware.h>
#include <hardware/sensors.h>

__BEGIN_DECLS

/*****************************************************************************/

#ifndef ARRAY_SIZE
#define ARRAY_SIZE(a) (sizeof(a) / sizeof(a[0]))
#endif

#define ENABLE_PRESSURE
#ifndef ENABLE_PRESSURE
#define ID_PS 255
#define Pressure 255
#endif

#ifndef SENSORS_DEVICE_API_VERSION_1_4
#define SENSOR_FLAG_SUPPORTS_DATA_INJECTION 0
#endif


enum {
    ID_GY = 0,
    ID_RG,
    ID_A,
    ID_M,
    ID_RM,
    ID_O,
    ID_RV,
    ID_GRV,
    ID_LA,
    ID_GR,
    ID_SM,
    ID_P,
    ID_SC,
    ID_GMRV,
    ID_T,
    ID_PICK,
#if 0
    ID_EISGY,
    ID_EISAUTHENTICATION,
#endif
    ID_GYW = 18,
    ID_RGW,
    ID_AW,
    ID_MW,
    ID_RMW,
    ID_OW,
    ID_RVW,
    ID_GRVW,
    ID_LAW,
    ID_GRW,
    ID_PW,
    ID_SCW,
    ID_GMRVW,
    ID_PS,
    ID_PSW,
    ID_L,
    ID_LW,
    ID_PR,
    ID_PRW,
    ID_ARC, // Accel raw custom
    ID_GRC, // gyro raw custom
    ID_MRC, // magnetic raw custom
#if 0
    ID_OIS, // OIS sensor
#endif
    ID_SO,
    ID_NUMBER
};

enum {
    Gyro = ID_GY,
    RawGyro = ID_RG,
    Accelerometer = ID_A,
    MagneticField = ID_M,
    RawMagneticField = ID_RM,
    Orientation = ID_O,
    RotationVector = ID_RV,
    GameRotationVector = ID_GRV,
    LinearAccel = ID_LA,
    Gravity = ID_GR,
    SignificantMotion = ID_SM,
    StepDetector = ID_P,
    StepCounter = ID_SC,
    GeomagneticRotationVector = ID_GMRV,
    Tilt = ID_T,
    Pickup = ID_PICK,
#if 0
    EISGyroscope = ID_EISGY,
    EISAuthentication = ID_EISAUTHENTICATION,
#endif
    Gyro_Wake = ID_GYW,
    RawGyro_Wake = ID_RGW,
    Accelerometer_Wake = ID_AW,
    MagneticField_Wake = ID_MW,
    RawMagneticField_Wake = ID_RMW,
    Orientation_Wake = ID_OW,
    RotationVector_Wake = ID_RVW,
    GameRotationVector_Wake = ID_GRVW,
    LinearAccel_Wake = ID_LAW,
    Gravity_Wake = ID_GRW,
    StepDetector_Wake = ID_PW,
    StepCounter_Wake = ID_SCW,
    GeomagneticRotationVector_Wake = ID_GMRVW,
    Pressure = ID_PS,
    Pressure_Wake = ID_PSW,
    Light = ID_L,
    Light_Wake = ID_LW,
    Proximity = ID_PR,
    Proximity_Wake = ID_PRW,
    AccelerometerRaw = ID_ARC,
    GyroRaw = ID_GRC,
    MagneticFieldRaw = ID_MRC,
#if 0
    OisSensor = ID_OIS,
#endif
    ScreenOrientation = ID_SO,
    TotalNumSensors = ID_NUMBER,
};

/* Physical parameters of the sensors supported by Invensense MPL */
#define SENSORS_GYROSCOPE_HANDLE                   (ID_GY)
#define SENSORS_RAW_GYROSCOPE_HANDLE               (ID_RG)
#define SENSORS_ACCELERATION_HANDLE                (ID_A)
#define SENSORS_MAGNETIC_FIELD_HANDLE              (ID_M)
#define SENSORS_RAW_MAGNETIC_FIELD_HANDLE          (ID_RM)
#define SENSORS_ORIENTATION_HANDLE                 (ID_O)
#define SENSORS_ROTATION_VECTOR_HANDLE             (ID_RV)
#define SENSORS_GAME_ROTATION_VECTOR_HANDLE        (ID_GRV)
#define SENSORS_LINEAR_ACCEL_HANDLE                (ID_LA)
#define SENSORS_GRAVITY_HANDLE                     (ID_GR)
#define SENSORS_SIGNIFICANT_MOTION_HANDLE          (ID_SM)
#define SENSORS_PEDOMETER_HANDLE                   (ID_P)
#define SENSORS_STEP_COUNTER_HANDLE                (ID_SC)
#define SENSORS_GEOMAGNETIC_ROTATION_VECTOR_HANDLE (ID_GMRV)
#define SENSORS_PRESSURE_HANDLE                    (ID_PS)
#define SENSORS_LIGHT_HANDLE                       (ID_L)
#define SENSORS_PROXIMITY_HANDLE                   (ID_PR)
#define SENSORS_WAKE_UP_TILT_DETECTOR_HANDLE       (ID_T)
#define SENSORS_PICK_UP_GESTURE_HANDLE             (ID_PICK)
#if 0
#define SENSORS_EIS_GYROSCOPE_HANDLE               (ID_EISGY)
#define SENSORS_EIS_AUTHENTICATION_HANDLE          (ID_EISAUTHENTICATION)
#endif
#define SENSORS_GYROSCOPE_WAKEUP_HANDLE                   (ID_GYW)
#define SENSORS_RAW_GYROSCOPE_WAKEUP_HANDLE               (ID_RGW)
#define SENSORS_ACCELERATION_WAKEUP_HANDLE                (ID_AW)
#define SENSORS_MAGNETIC_FIELD_WAKEUP_HANDLE              (ID_MW)
#define SENSORS_RAW_MAGNETIC_FIELD_WAKEUP_HANDLE          (ID_RMW)
#define SENSORS_ORIENTATION_WAKEUP_HANDLE                 (ID_OW)
#define SENSORS_ROTATION_VECTOR_WAKEUP_HANDLE             (ID_RVW)
#define SENSORS_GAME_ROTATION_VECTOR_WAKEUP_HANDLE        (ID_GRVW)
#define SENSORS_LINEAR_ACCEL_WAKEUP_HANDLE                (ID_LAW)
#define SENSORS_GRAVITY_WAKEUP_HANDLE                     (ID_GRW)
#define SENSORS_PEDOMETER_WAKEUP_HANDLE                   (ID_PW)
#define SENSORS_STEP_COUNTER_WAKEUP_HANDLE                (ID_SCW)
#define SENSORS_GEOMAGNETIC_ROTATION_VECTOR_WAKEUP_HANDLE (ID_GMRVW)
#define SENSORS_PRESSURE_WAKEUP_HANDLE                    (ID_PSW)
#define SENSORS_LIGHT_WAKEUP_HANDLE                       (ID_LW)
#define SENSORS_PROXIMITY_WAKEUP_HANDLE                   (ID_PRW)
#define SENSORS_ACCELERATION_RAW_HANDLE                   (ID_ARC)
#define SENSORS_GYROSCOPE_RAW_HANDLE                      (ID_GRC)
#define SENSORS_MAGNETIC_FIELD_RAW_HANDLE                 (ID_MRC)
#if 0
#define SENSORS_OIS_SENSOR_HANDLE                         (ID_OIS)
#endif
#define SENSORS_SCREEN_ORIENTATION_HANDLE                 (ID_SO)
#ifdef ALL_WINNER_LIGHT_SENSOR

/* For light and proximity sensor*/
#define EVENT_TYPE_LIGHT	ABS_MISC
#define EVENT_TYPE_GREEN	ABS_MISC+1
#define EVENT_TYPE_GREENIR	ABS_MISC+2
#define EVENT_TYPE_PROXIMITY	ABS_DISTANCE

#define ICHAR                           (';')

struct sensor_info{
        char sensorName[64];
        char classPath[128];
        float priData;
};
struct sensor_list{
	char name[64];
	float lsg;
};


extern struct sensor_info ligSensorInfo;
extern struct sensor_info proSensorInfo;
extern int sensorsDetect(void);

#endif


/*****************************************************************************/

/*
   Android L
   Populate sensor_t structure according to hardware sensors.h
   {    name, vendor, version,
        handle,
        type, maxRange, resolution, power, minDelay, fifoReservedEventCount, fifoMaxEventCount,
        stringType, requiredPermission, maxDelay, flags, reserved[]    }
*/
#define MAG 1
/* This list is for 20602 chipset */
/* 896 effective FIFO size        */
static struct sensor_t s20602SensorList[] =
{
    {"Invensense Gyroscope", "Invensense", 1,
     SENSORS_GYROSCOPE_HANDLE,
     SENSOR_TYPE_GYROSCOPE, 2000.0f, 1.0f, 1.0f, 5000, 0, 149,
     "android.sensor.gyroscope", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
    {"Invensense Gyroscope Uncalibrated", "Invensense", 1,
     SENSORS_RAW_GYROSCOPE_HANDLE,
     SENSOR_TYPE_GYROSCOPE_UNCALIBRATED, 2000.0f, 1.0f, 1.0f, 5000, 0, 149,
     "android.sensor.gyroscope_uncalibrated", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_SUPPORTS_DATA_INJECTION, {}},
    {"Invensense Accelerometer", "Invensense", 1,
     SENSORS_ACCELERATION_HANDLE,
     SENSOR_TYPE_ACCELEROMETER, 10240.0f, 1.0f, 0.5f, 5000, 0, 149,
     "android.sensor.accelerometer", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_SUPPORTS_DATA_INJECTION, {}},
	#if MAG
	{"Invensense Magnetometer", "Invensense", 1,
     SENSORS_MAGNETIC_FIELD_HANDLE,
     SENSOR_TYPE_MAGNETIC_FIELD, 10240.0f, 1.0f, 0.5f, 10000, 0, 89,
     "android.sensor.magnetic_field", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
    {"Invensense Magnetometer Uncalibrated", "Invensense", 1,
     SENSORS_RAW_MAGNETIC_FIELD_HANDLE,
     SENSOR_TYPE_MAGNETIC_FIELD_UNCALIBRATED, 10240.0f, 1.0f, 0.5f, 10000, 0, 89,
     "android.sensor.magnetic_field_uncalibrated", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
     #endif
	{"Invensense Game Rotation Vector", "Invensense", 1,
     SENSORS_GAME_ROTATION_VECTOR_HANDLE,
     SENSOR_TYPE_GAME_ROTATION_VECTOR, 10240.0f, 1.0f, 1.5f, 5000, 0, 0,
     "android.sensor.game_rotation_vector", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
    {"Invensense Linear Acceleration", "Invensense", 1,
     SENSORS_LINEAR_ACCEL_HANDLE,
     SENSOR_TYPE_LINEAR_ACCELERATION, 10240.0f, 1.0f, 1.5f, 5000, 0, 0,
     "android.sensor.linear_acceleration", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
    {"Invensense Gravity", "Invensense", 1,
     SENSORS_GRAVITY_HANDLE,
     SENSOR_TYPE_GRAVITY, 10240.0f, 1.0f, 1.5f, 5000, 0, 0,
     "android.sensor.gravity", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
    {"Invensense Step Detector", "Invensense", 1,
     SENSORS_PEDOMETER_HANDLE,
     SENSOR_TYPE_STEP_DETECTOR, 100.0f, 1.0f, 0.5f, 0, 0, 0,
     "android.sensor.step_detector", "", 0, SENSOR_FLAG_SPECIAL_REPORTING_MODE | SENSOR_FLAG_SUPPORTS_DATA_INJECTION, {}},
    {"Invensense Step Counter", "Invensense", 1,
     SENSORS_STEP_COUNTER_HANDLE,
     SENSOR_TYPE_STEP_COUNTER, 100.0f, 1.0f, 0.5f, 0, 0, 0,
     "android.sensor.step_counter", "", 250000, SENSOR_FLAG_ON_CHANGE_MODE | SENSOR_FLAG_SUPPORTS_DATA_INJECTION, {}},
    {"Invensense Tilt", "Invensense", 1,
     SENSORS_WAKE_UP_TILT_DETECTOR_HANDLE,
     SENSOR_TYPE_TILT_DETECTOR, 10240.0f, 1.0f, 0.5f, 0, 0, 0,
     "android.sensor.tilt_detector", "", 0, SENSOR_FLAG_SPECIAL_REPORTING_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Pick Up Gesture", "Invensense", 1,
     SENSORS_PICK_UP_GESTURE_HANDLE,
     SENSOR_TYPE_PICK_UP_GESTURE, 10240.0f, 1.0f, 0.5f, -1, 0, 0,
     "android.sensor.pick_up_gesture", "", 0, SENSOR_FLAG_ONE_SHOT_MODE | SENSOR_FLAG_WAKE_UP, {}},
#if 0
    {"Invensense EIS Gyroscope", "Invensense", 1,
     SENSORS_EIS_GYROSCOPE_HANDLE,
     SENSOR_TYPE_EIS_GYROSCOPE, 34.906559f, 0.001065f, 1.0f, 0, 0, 0,
     "android.sensor.eis_gyroscope", "", 4444, SENSOR_FLAG_ON_CHANGE_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense EIS Authentication", "Invensense", 1,
     SENSORS_EIS_AUTHENTICATION_HANDLE,
     SENSOR_TYPE_EIS_AUTHENTICATION, 34.906559f, 0.001065f, 1.0f, 0, 0, 0,
     "android.sensor.eis_authentication", "", 4444, SENSOR_FLAG_ON_CHANGE_MODE | SENSOR_FLAG_WAKE_UP, {}},
#endif
    {"Invensense Gyroscope - Wakeup", "Invensense", 1,
     SENSORS_GYROSCOPE_WAKEUP_HANDLE,
     SENSOR_TYPE_GYROSCOPE, 2000.0f, 1.0f, 1.0f, 5000, 0, 149,
     "android.sensor.gyroscope", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Gyroscope Uncalibrated - Wakeup", "Invensense", 1,
     SENSORS_RAW_GYROSCOPE_WAKEUP_HANDLE,
     SENSOR_TYPE_GYROSCOPE_UNCALIBRATED, 2000.0f, 1.0f, 1.0f, 5000, 0, 149,
     "android.sensor.gyroscope_uncalibrated", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP | SENSOR_FLAG_SUPPORTS_DATA_INJECTION, {}},
    {"Invensense Accelerometer - Wakeup", "Invensense", 1,
     SENSORS_ACCELERATION_WAKEUP_HANDLE,
     SENSOR_TYPE_ACCELEROMETER, 10240.0f, 1.0f, 0.5f, 5000, 0, 149,
     "android.sensor.accelerometer", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP | SENSOR_FLAG_SUPPORTS_DATA_INJECTION, {}},
    {"Invensense Game Rotation Vector - Wakeup", "Invensense", 1,
     SENSORS_GAME_ROTATION_VECTOR_WAKEUP_HANDLE,
     SENSOR_TYPE_GAME_ROTATION_VECTOR, 10240.0f, 1.0f, 1.5f, 5000, 0, 0,
     "android.sensor.game_rotation_vector", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Linear Acceleration - Wakeup", "Invensense", 1,
     SENSORS_LINEAR_ACCEL_WAKEUP_HANDLE,
     SENSOR_TYPE_LINEAR_ACCELERATION, 10240.0f, 1.0f, 1.5f, 5000, 0, 0,
     "android.sensor.linear_acceleration", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP, {}},
     #if MAG
     {"Invensense Magnetometer - Wakeup", "Invensense", 1,
     SENSORS_MAGNETIC_FIELD_WAKEUP_HANDLE,
     SENSOR_TYPE_MAGNETIC_FIELD, 10240.0f, 1.0f, 0.5f, 10000, 0, 149,
     "android.sensor.magnetic_field", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Magnetometer Uncalibrated - Wakeup", "Invensense", 1,
     SENSORS_RAW_MAGNETIC_FIELD_WAKEUP_HANDLE,
     SENSOR_TYPE_MAGNETIC_FIELD_UNCALIBRATED, 10240.0f, 1.0f, 0.5f, 10000, 0, 89,
     "android.sensor.magnetic_field_uncalibrated", "", 1000000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP, {}},
     #endif
    {"Invensense Gravity - Wakeup", "Invensense", 1,
     SENSORS_GRAVITY_WAKEUP_HANDLE,
     SENSOR_TYPE_GRAVITY, 10240.0f, 1.0f, 1.5f, 5000, 0, 0,
     "android.sensor.gravity", "", 250000,  SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Step Detector - Wakeup", "Invensense", 1,
     SENSORS_PEDOMETER_WAKEUP_HANDLE,
     SENSOR_TYPE_STEP_DETECTOR, 100.0f, 1.0f, 1.5f, 0, 0, 0,
     "android.sensor.step_detector", "", 0, SENSOR_FLAG_SPECIAL_REPORTING_MODE | SENSOR_FLAG_WAKE_UP | SENSOR_FLAG_SUPPORTS_DATA_INJECTION, {}},
    {"Invensense Step Counter - Wakeup", "Invensense", 1,
     SENSORS_STEP_COUNTER_WAKEUP_HANDLE,
     SENSOR_TYPE_STEP_COUNTER, 100.0f, 1.0f, 1.5f, 0, 0, 0,
     "android.sensor.step_counter", "", 250000, SENSOR_FLAG_ON_CHANGE_MODE  | SENSOR_FLAG_WAKE_UP | SENSOR_FLAG_SUPPORTS_DATA_INJECTION, {}},
     {"Invensense Light", "ltr", 1,
     SENSORS_LIGHT_HANDLE,
     SENSOR_TYPE_LIGHT, 10000.0f, 0.009994f, 0.175f, 0, 0, 15,
     "android.sensor.light", "", 112000, SENSOR_FLAG_ON_CHANGE_MODE, {}},
     {"Invensense Proxmity", "ltr", 1,
     SENSORS_PROXIMITY_HANDLE,
     SENSOR_TYPE_PROXIMITY, 5.0f, 5.0f, 0.2f, 0, 0, 0,
     "android.sensor.proximity", "", 112000, SENSOR_FLAG_WAKE_UP |SENSOR_FLAG_ON_CHANGE_MODE, {}},
};

/* This list is for 20690 chipset */
/* 1024 effective FIFO size        */
static struct sensor_t s20690SensorList[] =
{
    {"Invensense Gyroscope", "Invensense", 1,
     SENSORS_GYROSCOPE_HANDLE,
     SENSOR_TYPE_GYROSCOPE, 2000.0f, 1.0f, 1.0f, 5000, 0, 149,
     "android.sensor.gyroscope", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
    {"Invensense Gyroscope Uncalibrated", "Invensense", 1,
     SENSORS_RAW_GYROSCOPE_HANDLE,
     SENSOR_TYPE_GYROSCOPE_UNCALIBRATED, 2000.0f, 1.0f, 1.0f, 5000, 0, 149,
     "android.sensor.gyroscope_uncalibrated", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
    {"Invensense Accelerometer", "Invensense", 1,
     SENSORS_ACCELERATION_HANDLE,
     SENSOR_TYPE_ACCELEROMETER, 10240.0f, 1.0f, 0.5f, 5000, 0, 149,
     "android.sensor.accelerometer", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
    {"Invensense Magnetometer", "Invensense", 1,
     SENSORS_MAGNETIC_FIELD_HANDLE,
     SENSOR_TYPE_MAGNETIC_FIELD, 10240.0f, 1.0f, 0.5f, 10000, 0, 89,
     "android.sensor.magnetic_field", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
    {"Invensense Magnetometer Uncalibrated", "Invensense", 1,
     SENSORS_RAW_MAGNETIC_FIELD_HANDLE,
     SENSOR_TYPE_MAGNETIC_FIELD_UNCALIBRATED, 10240.0f, 1.0f, 0.5f, 10000, 0, 89,
     "android.sensor.magnetic_field_uncalibrated", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
    {"Invensense Orientation", "Invensense", 1,
     SENSORS_ORIENTATION_HANDLE,
     SENSOR_TYPE_ORIENTATION, 360.0f, 1.0f, 9.7f, 5000, 0, 0,
     "android.sensor.orientation", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
    {"Invensense Rotation Vector", "Invensense", 1,
     SENSORS_ROTATION_VECTOR_HANDLE,
     SENSOR_TYPE_ROTATION_VECTOR, 10240.0f, 1.0f, 0.5f, 5000, 0, 0,
     "android.sensor.rotation_vector", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
    {"Invensense Game Rotation Vector", "Invensense", 1,
     SENSORS_GAME_ROTATION_VECTOR_HANDLE,
     SENSOR_TYPE_GAME_ROTATION_VECTOR, 10240.0f, 1.0f, 1.5f, 5000, 0, 0,
     "android.sensor.game_rotation_vector", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
    {"Invensense Linear Acceleration", "Invensense", 1,
     SENSORS_LINEAR_ACCEL_HANDLE,
     SENSOR_TYPE_LINEAR_ACCELERATION, 10240.0f, 1.0f, 1.5f, 5000, 0, 0,
     "android.sensor.linear_acceleration", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
    {"Invensense Gravity", "Invensense", 1,
     SENSORS_GRAVITY_HANDLE,
     SENSOR_TYPE_GRAVITY, 10240.0f, 1.0f, 1.5f, 5000, 0, 0,
     "android.sensor.gravity", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
    {"Invensense Step Detector", "Invensense", 1,
     SENSORS_PEDOMETER_HANDLE,
     SENSOR_TYPE_STEP_DETECTOR, 100.0f, 1.0f, 0.5f, 0, 0, 0,
     "android.sensor.step_detector", "", 0, SENSOR_FLAG_SPECIAL_REPORTING_MODE, {}},
    {"Invensense Step Counter", "Invensense", 1,
     SENSORS_STEP_COUNTER_HANDLE,
     SENSOR_TYPE_STEP_COUNTER, 100.0f, 1.0f, 0.5f, 0, 0, 0,
     "android.sensor.step_counter", "", 0, SENSOR_FLAG_ON_CHANGE_MODE, {}},
    {"Invensense Geomagnetic Rotation Vector", "Invensense", 1,
     SENSORS_GEOMAGNETIC_ROTATION_VECTOR_HANDLE,
     SENSOR_TYPE_GEOMAGNETIC_ROTATION_VECTOR, 10240.0f, 1.0f, 0.5f, 5000, 0, 0,
     "android.sensor.geomagnetic_rotation_vector", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
    {"Invensense Tilt", "Invensense", 1,
     SENSORS_WAKE_UP_TILT_DETECTOR_HANDLE,
     SENSOR_TYPE_TILT_DETECTOR, 10240.0f, 1.0f, 0.5f, 0, 0, 0,
     "android.sensor.tilt_detector", "", 0, SENSOR_FLAG_SPECIAL_REPORTING_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Pick Up Gesture", "Invensense", 1,
     SENSORS_PICK_UP_GESTURE_HANDLE,
     SENSOR_TYPE_PICK_UP_GESTURE, 10240.0f, 1.0f, 0.5f, -1, 0, 0,
     "android.sensor.pick_up_gesture", "", 0, SENSOR_FLAG_ONE_SHOT_MODE | SENSOR_FLAG_WAKE_UP, {}},
#if 0
    {"Invensense EIS Gyroscope", "Invensense", 1,
     SENSORS_EIS_GYROSCOPE_HANDLE,
     SENSOR_TYPE_EIS_GYROSCOPE, 34.906559f, 0.001065f, 1.0f, 0, 0, 0,
     "android.sensor.eis_gyroscope", "", 5000, SENSOR_FLAG_ON_CHANGE_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense EIS Authentication", "Invensense", 1,
     SENSORS_EIS_AUTHENTICATION_HANDLE,
     SENSOR_TYPE_EIS_AUTHENTICATION, 34.906559f, 0.001065f, 1.0f, 0, 0, 0,
     "android.sensor.eis_authentication", "", 5000, SENSOR_FLAG_ON_CHANGE_MODE | SENSOR_FLAG_WAKE_UP, {}},
#endif
    {"Invensense Gyroscope - Wakeup", "Invensense", 1,
     SENSORS_GYROSCOPE_WAKEUP_HANDLE,
     SENSOR_TYPE_GYROSCOPE, 2000.0f, 1.0f, 1.0f, 5000, 0, 149,
     "android.sensor.gyroscope", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Gyroscope Uncalibrated - Wakeup", "Invensense", 1,
     SENSORS_RAW_GYROSCOPE_WAKEUP_HANDLE,
     SENSOR_TYPE_GYROSCOPE_UNCALIBRATED, 2000.0f, 1.0f, 1.0f, 5000, 0, 149,
     "android.sensor.gyroscope_uncalibrated", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Accelerometer - Wakeup", "Invensense", 1,
     SENSORS_ACCELERATION_WAKEUP_HANDLE,
     SENSOR_TYPE_ACCELEROMETER, 10240.0f, 1.0f, 0.5f, 5000, 0, 149,
     "android.sensor.accelerometer", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Magnetometer - Wakeup", "Invensense", 1,
     SENSORS_MAGNETIC_FIELD_WAKEUP_HANDLE,
     SENSOR_TYPE_MAGNETIC_FIELD, 10240.0f, 1.0f, 0.5f, 10000, 0, 149,
     "android.sensor.magnetic_field", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Magnetometer Uncalibrated - Wakeup", "Invensense", 1,
     SENSORS_RAW_MAGNETIC_FIELD_WAKEUP_HANDLE,
     SENSOR_TYPE_MAGNETIC_FIELD_UNCALIBRATED, 10240.0f, 1.0f, 0.5f, 10000, 0, 89,
     "android.sensor.magnetic_field_uncalibrated", "", 1000000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Orientation - Wakeup", "Invensense", 1,
     SENSORS_ORIENTATION_WAKEUP_HANDLE,
     SENSOR_TYPE_ORIENTATION, 360.0f, 1.0f, 9.7f, 5000, 0, 0,
     "android.sensor.orientation", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Rotation Vector - Wakeup", "Invensense", 1,
     SENSORS_ROTATION_VECTOR_WAKEUP_HANDLE,
     SENSOR_TYPE_ROTATION_VECTOR, 10240.0f, 1.0f, 0.5f, 5000, 0, 0,
     "android.sensor.rotation_vector", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Game Rotation Vector - Wakeup", "Invensense", 1,
     SENSORS_GAME_ROTATION_VECTOR_WAKEUP_HANDLE,
     SENSOR_TYPE_GAME_ROTATION_VECTOR, 10240.0f, 1.0f, 1.5f, 5000, 0, 0,
     "android.sensor.game_rotation_vector", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Linear Acceleration - Wakeup", "Invensense", 1,
     SENSORS_LINEAR_ACCEL_WAKEUP_HANDLE,
     SENSOR_TYPE_LINEAR_ACCELERATION, 10240.0f, 1.0f, 1.5f, 5000, 0, 0,
     "android.sensor.linear_acceleration", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Gravity - Wakeup", "Invensense", 1,
     SENSORS_GRAVITY_WAKEUP_HANDLE,
     SENSOR_TYPE_GRAVITY, 10240.0f, 1.0f, 1.5f, 5000, 0, 0,
     "android.sensor.gravity", "", 250000,  SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Step Detector - Wakeup", "Invensense", 1,
     SENSORS_PEDOMETER_WAKEUP_HANDLE,
     SENSOR_TYPE_STEP_DETECTOR, 100.0f, 1.0f, 1.5f, 0, 0, 0,
     "android.sensor.step_detector", "", 0, SENSOR_FLAG_SPECIAL_REPORTING_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Step Counter - Wakeup", "Invensense", 1,
     SENSORS_STEP_COUNTER_WAKEUP_HANDLE,
     SENSOR_TYPE_STEP_COUNTER, 100.0f, 1.0f, 1.5f, 0, 0, 0,
     "android.sensor.step_counter", "", 1000000, SENSOR_FLAG_ON_CHANGE_MODE  | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Geomagnetic Rotation Vector - Wakeup", "Invensense", 1,
     SENSORS_GEOMAGNETIC_ROTATION_VECTOR_WAKEUP_HANDLE,
     SENSOR_TYPE_GEOMAGNETIC_ROTATION_VECTOR, 100.0f, 1.0f, 0.5f, 10000, 0, 0,
     "android.sensor.geomagnetic_rotation_vector", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP, {}},
#if 0
    {"Invensense OIS Sensor", "Invensense", 1,
     SENSORS_OIS_SENSOR_HANDLE,
     SENSOR_TYPE_OIS_SENSOR, 100.0f, 1.0f, 0.5f, 1000, 0, 0,
     "android.sensor.ois_sensor", "", 250000, SENSOR_FLAG_SPECIAL_REPORTING_MODE, {}},
#endif
};

/* This list is for 20690 chipset without compass*/
/* 1024 effective FIFO size        */
static struct sensor_t s20690WithoutCompassSensorList[] =
{
    {"Invensense Gyroscope", "Invensense", 1,
     SENSORS_GYROSCOPE_HANDLE,
     SENSOR_TYPE_GYROSCOPE, 2000.0f, 1.0f, 1.0f, 5000, 0, 149,
     "android.sensor.gyroscope", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
    {"Invensense Gyroscope Uncalibrated", "Invensense", 1,
     SENSORS_RAW_GYROSCOPE_HANDLE,
     SENSOR_TYPE_GYROSCOPE_UNCALIBRATED, 2000.0f, 1.0f, 1.0f, 5000, 0, 149,
     "android.sensor.gyroscope_uncalibrated", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
    {"Invensense Accelerometer", "Invensense", 1,
     SENSORS_ACCELERATION_HANDLE,
     SENSOR_TYPE_ACCELEROMETER, 10240.0f, 1.0f, 0.5f, 5000, 0, 149,
     "android.sensor.accelerometer", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
    {"Invensense Game Rotation Vector", "Invensense", 1,
     SENSORS_GAME_ROTATION_VECTOR_HANDLE,
     SENSOR_TYPE_GAME_ROTATION_VECTOR, 10240.0f, 1.0f, 1.5f, 5000, 0, 0,
     "android.sensor.game_rotation_vector", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
    {"Invensense Linear Acceleration", "Invensense", 1,
     SENSORS_LINEAR_ACCEL_HANDLE,
     SENSOR_TYPE_LINEAR_ACCELERATION, 10240.0f, 1.0f, 1.5f, 5000, 0, 0,
     "android.sensor.linear_acceleration", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
    {"Invensense Gravity", "Invensense", 1,
     SENSORS_GRAVITY_HANDLE,
     SENSOR_TYPE_GRAVITY, 10240.0f, 1.0f, 1.5f, 5000, 0, 0,
     "android.sensor.gravity", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
    {"Invensense Step Detector", "Invensense", 1,
     SENSORS_PEDOMETER_HANDLE,
     SENSOR_TYPE_STEP_DETECTOR, 100.0f, 1.0f, 0.5f, 0, 0, 0,
     "android.sensor.step_detector", "", 0, SENSOR_FLAG_SPECIAL_REPORTING_MODE, {}},
    {"Invensense Step Counter", "Invensense", 1,
     SENSORS_STEP_COUNTER_HANDLE,
     SENSOR_TYPE_STEP_COUNTER, 100.0f, 1.0f, 0.5f, 0, 0, 0,
     "android.sensor.step_counter", "", 0, SENSOR_FLAG_ON_CHANGE_MODE, {}},
    {"Invensense Tilt", "Invensense", 1,
     SENSORS_WAKE_UP_TILT_DETECTOR_HANDLE,
     SENSOR_TYPE_TILT_DETECTOR, 10240.0f, 1.0f, 0.5f, 0, 0, 0,
     "android.sensor.tilt_detector", "", 0, SENSOR_FLAG_SPECIAL_REPORTING_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Pick Up Gesture", "Invensense", 1,
     SENSORS_PICK_UP_GESTURE_HANDLE,
     SENSOR_TYPE_PICK_UP_GESTURE, 10240.0f, 1.0f, 0.5f, -1, 0, 0,
     "android.sensor.pick_up_gesture", "", 0, SENSOR_FLAG_ONE_SHOT_MODE | SENSOR_FLAG_WAKE_UP, {}},
#if 0
    {"Invensense EIS Gyroscope", "Invensense", 1,
     SENSORS_EIS_GYROSCOPE_HANDLE,
     SENSOR_TYPE_EIS_GYROSCOPE, 34.906559f, 0.001065f, 1.0f, 0, 0, 0,
     "android.sensor.eis_gyroscope", "", 5000, SENSOR_FLAG_ON_CHANGE_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense EIS Authentication", "Invensense", 1,
     SENSORS_EIS_AUTHENTICATION_HANDLE,
     SENSOR_TYPE_EIS_AUTHENTICATION, 34.906559f, 0.001065f, 1.0f, 0, 0, 0,
     "android.sensor.eis_authentication", "", 5000, SENSOR_FLAG_ON_CHANGE_MODE | SENSOR_FLAG_WAKE_UP, {}},
#endif

    {"Invensense Gyroscope - Wakeup", "Invensense", 1,
     SENSORS_GYROSCOPE_WAKEUP_HANDLE,
     SENSOR_TYPE_GYROSCOPE, 2000.0f, 1.0f, 1.0f, 5000, 0, 149,
     "android.sensor.gyroscope", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Gyroscope Uncalibrated - Wakeup", "Invensense", 1,
     SENSORS_RAW_GYROSCOPE_WAKEUP_HANDLE,
     SENSOR_TYPE_GYROSCOPE_UNCALIBRATED, 2000.0f, 1.0f, 1.0f, 5000, 0, 149,
     "android.sensor.gyroscope_uncalibrated", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Accelerometer - Wakeup", "Invensense", 1,
     SENSORS_ACCELERATION_WAKEUP_HANDLE,
     SENSOR_TYPE_ACCELEROMETER, 10240.0f, 1.0f, 0.5f, 5000, 0, 149,
     "android.sensor.accelerometer", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Game Rotation Vector - Wakeup", "Invensense", 1,
     SENSORS_GAME_ROTATION_VECTOR_WAKEUP_HANDLE,
     SENSOR_TYPE_GAME_ROTATION_VECTOR, 10240.0f, 1.0f, 1.5f, 5000, 0, 0,
     "android.sensor.game_rotation_vector", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Linear Acceleration - Wakeup", "Invensense", 1,
     SENSORS_LINEAR_ACCEL_WAKEUP_HANDLE,
     SENSOR_TYPE_LINEAR_ACCELERATION, 10240.0f, 1.0f, 1.5f, 5000, 0, 0,
     "android.sensor.linear_acceleration", "", 250000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Gravity - Wakeup", "Invensense", 1,
     SENSORS_GRAVITY_WAKEUP_HANDLE,
     SENSOR_TYPE_GRAVITY, 10240.0f, 1.0f, 1.5f, 5000, 0, 0,
     "android.sensor.gravity", "", 250000,  SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Step Detector - Wakeup", "Invensense", 1,
     SENSORS_PEDOMETER_WAKEUP_HANDLE,
     SENSOR_TYPE_STEP_DETECTOR, 100.0f, 1.0f, 1.5f, 0, 0, 0,
     "android.sensor.step_detector", "", 0, SENSOR_FLAG_SPECIAL_REPORTING_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Step Counter - Wakeup", "Invensense", 1,
     SENSORS_STEP_COUNTER_WAKEUP_HANDLE,
     SENSOR_TYPE_STEP_COUNTER, 100.0f, 1.0f, 1.5f, 0, 0, 0,
     "android.sensor.step_counter", "", 1000000, SENSOR_FLAG_ON_CHANGE_MODE  | SENSOR_FLAG_WAKE_UP, {}},
#if 0
    {"Invensense OIS Sensor", "Invensense", 1,
     SENSORS_OIS_SENSOR_HANDLE,
     SENSOR_TYPE_OIS_SENSOR, 100.0f, 1.0f, 0.5f, 1000, 0, 0,
     "android.sensor.ois_sensor", "", 250000, SENSOR_FLAG_SPECIAL_REPORTING_MODE, {}},
#endif
};

/* 512 bytes FIFO size */
/* GYRO data size is 18+2 bytes; Accel data size is 6+2 */
/* This list is for combo chipset */
static struct sensor_t s20608DSensorList[] =
{
    {"Invensense Gyroscope", "Invensense", 1,
     SENSORS_GYROSCOPE_HANDLE,
     SENSOR_TYPE_GYROSCOPE, 2000.0f, 1.0f, 0.5f, 5000, 0, 25,
     "android.sensor.gyroscope", "", 1000000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
    {"Invensense Gyroscope Uncalibrated", "Invensense", 1,
     SENSORS_RAW_GYROSCOPE_HANDLE,
     SENSOR_TYPE_GYROSCOPE_UNCALIBRATED, 2000.0f, 1.0f, 0.5f, 5000, 0, 25,
     "android.sensor.gyroscope_uncalibrated", "", 1000000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
    {"Invensense Accelerometer", "Invensense", 1,
     SENSORS_ACCELERATION_HANDLE,
     SENSOR_TYPE_ACCELEROMETER, 10240.0f, 1.0f, 0.5f, 5000, 0, 64,
     "android.sensor.accelerometer", "", 1000000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
    {"Invensense Game Rotation Vector", "Invensense", 1,
     SENSORS_GAME_ROTATION_VECTOR_HANDLE,
     SENSOR_TYPE_GAME_ROTATION_VECTOR, 10240.0f, 1.0f, 0.5f, 5000, 0, 19,
     "android.sensor.game_rotation_vector", "", 1000000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
    {"Invensense Linear Acceleration", "Invensense", 1,
     SENSORS_LINEAR_ACCEL_HANDLE,
     SENSOR_TYPE_LINEAR_ACCELERATION, 10240.0f, 1.0f, 0.5f, 5000, 0, 0,
     "android.sensor.linear_acceleration", "", 1000000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
    {"Invensense Gravity", "Invensense", 1,
     SENSORS_GRAVITY_HANDLE,
     SENSOR_TYPE_GRAVITY, 10240.0f, 1.0f, 0.5f, 5000, 0, 0,
     "android.sensor.gravity", "", 1000000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
    {"Invensense Significant Motion", "Invensense", 1,
     SENSORS_SIGNIFICANT_MOTION_HANDLE,
     SENSOR_TYPE_SIGNIFICANT_MOTION, 100.0f, 1.0f, 1.1f, -1, 0, 0,
     "android.sensor.significant_motion", "", 0, SENSOR_FLAG_ONE_SHOT_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Step Detector", "Invensense", 1,
     SENSORS_PEDOMETER_HANDLE,
     SENSOR_TYPE_STEP_DETECTOR, 100.0f, 1.0f, 1.1f, 0, 0, 62,
     "android.sensor.step_detector", "", 0, SENSOR_FLAG_SPECIAL_REPORTING_MODE, {}},
    {"Invensense Step Counter", "Invensense", 1,
     SENSORS_STEP_COUNTER_HANDLE,
     SENSOR_TYPE_STEP_COUNTER, 100.0f, 1.0f, 1.1f, 0, 0, 0,
     "android.sensor.step_counter", "", 100000, SENSOR_FLAG_ON_CHANGE_MODE, {}},
#if 0
    {"Invensense EIS Gyroscope", "Invensense", 1,
     SENSORS_EIS_GYROSCOPE_HANDLE,
     SENSOR_TYPE_EIS_GYROSCOPE, 34.906559f, 0.001065f, 5.5f, 0, 0, 0,
     "android.sensor.eis_gyroscope", "", 10000, SENSOR_FLAG_ON_CHANGE_MODE | SENSOR_FLAG_WAKE_UP, {}},
#endif
    {"Invensense Gyroscope - Wakeup", "Invensense", 1,
     SENSORS_GYROSCOPE_WAKEUP_HANDLE,
     SENSOR_TYPE_GYROSCOPE, 2000.0f, 1.0f, 0.5f, 5000, 0, 25,
     "android.sensor.gyroscope", "", 1000000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Gyroscope Uncalibrated - Wakeup", "Invensense", 1,
     SENSORS_RAW_GYROSCOPE_WAKEUP_HANDLE,
     SENSOR_TYPE_GYROSCOPE_UNCALIBRATED, 2000.0f, 1.0f, 0.5f, 5000, 0, 25,
     "android.sensor.gyroscope_uncalibrated", "", 1000000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Accelerometer - Wakeup", "Invensense", 1,
     SENSORS_ACCELERATION_WAKEUP_HANDLE,
     SENSOR_TYPE_ACCELEROMETER, 10240.0f, 1.0f, 0.5f, 5000, 0, 64,
     "android.sensor.accelerometer", "", 1000000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Game Rotation Vector - Wakeup", "Invensense", 1,
     SENSORS_GAME_ROTATION_VECTOR_WAKEUP_HANDLE,
     SENSOR_TYPE_GAME_ROTATION_VECTOR, 10240.0f, 1.0f, 0.5f, 5000, 0, 19,
     "android.sensor.game_rotation_vector", "", 1000000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Linear Acceleration - Wakeup", "Invensense", 1,
     SENSORS_LINEAR_ACCEL_WAKEUP_HANDLE,
     SENSOR_TYPE_LINEAR_ACCELERATION, 10240.0f, 1.0f, 0.5f, 5000, 0, 0,
     "android.sensor.linear_acceleration", "", 1000000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Gravity - Wakeup", "Invensense", 1,
     SENSORS_GRAVITY_WAKEUP_HANDLE,
     SENSOR_TYPE_GRAVITY, 10240.0f, 1.0f, 0.5f, 5000, 0, 0,
     "android.sensor.gravity", "", 1000000,  SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Step Detector - Wakeup", "Invensense", 1,
     SENSORS_PEDOMETER_WAKEUP_HANDLE,
     SENSOR_TYPE_STEP_DETECTOR, 100.0f, 1.0f, 1.1f, 0, 0, 62,
     "android.sensor.step_detector", "", 0, SENSOR_FLAG_SPECIAL_REPORTING_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Step Counter - Wakeup", "Invensense", 1,
     SENSORS_STEP_COUNTER_WAKEUP_HANDLE,
     SENSOR_TYPE_STEP_COUNTER, 100.0f, 1.0f, 1.1f, 0, 0, 0,
     "android.sensor.step_counter", "", 100000, SENSOR_FLAG_ON_CHANGE_MODE  | SENSOR_FLAG_WAKE_UP, {}},
};

/* This list is for Accel Only */
static struct sensor_t sBaseSensorList[] =
{
    {"Invensense Accelerometer", "Invensense", 1,
     SENSORS_ACCELERATION_HANDLE,
     SENSOR_TYPE_ACCELEROMETER, 19.613300f, 0.039227f, 0.5f, 4545, 0, 62,
     "android.sensor.accelerometer", "", 200000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_SUPPORTS_DATA_INJECTION, {}},
    {"Invensense Magnetometer", "Invensense", 1,
     SENSORS_MAGNETIC_FIELD_HANDLE,
     SENSOR_TYPE_MAGNETIC_FIELD, 9830.0f, 0.15f, 10, 14285 /*10000*/, 0, 62,
     "android.sensor.magnetic_field", "", 10000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
    {"Invensense Magnetometer Uncalibrated", "Invensense", 1,
     SENSORS_RAW_MAGNETIC_FIELD_HANDLE,
     SENSOR_TYPE_MAGNETIC_FIELD_UNCALIBRATED, 9830.0f, 0.15f, 10, 14285 /*10000*/, 0, 62,
     "android.sensor.magnetic_field_uncalibrated", "", 10000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_SUPPORTS_DATA_INJECTION, {}},
    {"Invensense Significant Motion", "Invensense", 1,
     SENSORS_SIGNIFICANT_MOTION_HANDLE,
     SENSOR_TYPE_SIGNIFICANT_MOTION, 1.0f, 1.0f, 0.5f, -1, 0, 0,
     "android.sensor.significant_motion", "", 0, SENSOR_FLAG_ONE_SHOT_MODE | SENSOR_FLAG_WAKE_UP | SENSOR_FLAG_SUPPORTS_DATA_INJECTION, {}},
    {"Invensense Step Detector", "Invensense", 1,
     SENSORS_PEDOMETER_HANDLE,
     SENSOR_TYPE_STEP_DETECTOR, 1.0f, 1.0f, 1.1f, 0, 0, 62,
     "android.sensor.step_detector", "", 0, SENSOR_FLAG_SPECIAL_REPORTING_MODE | SENSOR_FLAG_SUPPORTS_DATA_INJECTION, {}},
    {"Invensense Step Counter", "Invensense", 1,
     SENSORS_STEP_COUNTER_HANDLE,
     SENSOR_TYPE_STEP_COUNTER, 4294967296.0f, 1.0f, 1.1f, 0, 0, 0,
     "android.sensor.step_counter", "", 100000, SENSOR_FLAG_ON_CHANGE_MODE | SENSOR_FLAG_SUPPORTS_DATA_INJECTION, {}},
    {"Invensense Geomagnetic Rotation Vector", "Invensense", 1,
     SENSORS_GEOMAGNETIC_ROTATION_VECTOR_HANDLE,
     SENSOR_TYPE_GEOMAGNETIC_ROTATION_VECTOR, 1.0f, 0.000010f, 1.0f, 4545, 0, 0,
     "android.sensor.geomagnetic_rotation_vector", "", 200000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
    {"Invensense Tilt", "Invensense", 1,
     SENSORS_WAKE_UP_TILT_DETECTOR_HANDLE,
     SENSOR_TYPE_TILT_DETECTOR, 1.0f, 1.0f, 0.5f, 0, 0, 0,
     "android.sensor.tilt_detector", "", 0, SENSOR_FLAG_SPECIAL_REPORTING_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Pick Up Gesture", "Invensense", 1,
     SENSORS_PICK_UP_GESTURE_HANDLE,
     SENSOR_TYPE_PICK_UP_GESTURE, 1.0f, 1.0f, 0.5f, -1, 0, 0,
     "android.sensor.pick_up_gesture", "", 0, SENSOR_FLAG_ONE_SHOT_MODE | SENSOR_FLAG_WAKE_UP, {}},
#if 0
    {"Invensense EIS Gyroscope", "Invensense", 1,
     SENSORS_EIS_GYROSCOPE_HANDLE,
     SENSOR_TYPE_EIS_GYROSCOPE, 2000.0f, 1.0f, 0.5f, 4545, 0, 0,
     "android.sensor.eis_gyroscope", "", 4545, SENSOR_FLAG_SPECIAL_REPORTING_MODE, {}},
    {"Invensense EIS Authentication", "Invensense", 1,
     SENSORS_EIS_AUTHENTICATION_HANDLE,
     SENSOR_TYPE_EIS_AUTHENTICATION, 2000.0f, 1.0f, 0.5f, 4545, 0, 0,
     "android.sensor.eis_authentication", "", 4545, SENSOR_FLAG_SPECIAL_REPORTING_MODE, {}},
#endif
    {"Invensense Accelerometer - Wakeup", "Invensense", 1,
     SENSORS_ACCELERATION_WAKEUP_HANDLE,
     SENSOR_TYPE_ACCELEROMETER, 19.613300f, 0.039227f, 0.5f, 4545, 0, 62,
     "android.sensor.accelerometer", "", 20000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP | SENSOR_FLAG_SUPPORTS_DATA_INJECTION, {}},
    {"Invensense Magnetometer - Wakeup", "Invensense", 1,
     SENSORS_MAGNETIC_FIELD_WAKEUP_HANDLE,
     SENSOR_TYPE_MAGNETIC_FIELD, 9830.0f, 0.15f, 10.f, 14285 /*10000*/, 0, 62,
     "android.sensor.magnetic_field", "", 10000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Magnetometer Uncalibrated - Wakeup", "Invensense", 1,
     SENSORS_RAW_MAGNETIC_FIELD_WAKEUP_HANDLE,
     SENSOR_TYPE_MAGNETIC_FIELD_UNCALIBRATED, 9830.0f, 0.15f, 10.f, 14285 /*10000*/, 0, 62,
     "android.sensor.magnetic_field_uncalibrated", "", 10000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP | SENSOR_FLAG_SUPPORTS_DATA_INJECTION, {}},
    {"Invensense Step Detector - Wakeup", "Invensense", 1,
     SENSORS_PEDOMETER_WAKEUP_HANDLE,
     SENSOR_TYPE_STEP_DETECTOR, 1.0f, 1.0f, 1.1f, 0, 0, 62,
     "android.sensor.step_detector", "", 0, SENSOR_FLAG_SPECIAL_REPORTING_MODE | SENSOR_FLAG_WAKE_UP | SENSOR_FLAG_SUPPORTS_DATA_INJECTION, {}},
    {"Invensense Step Counter - Wakeup", "Invensense", 1,
     SENSORS_STEP_COUNTER_WAKEUP_HANDLE,
     SENSOR_TYPE_STEP_COUNTER, 4294967296.0f, 1.0f, 1.1f, 0, 0, 0,
     "android.sensor.step_counter", "", 2000, SENSOR_FLAG_ON_CHANGE_MODE  | SENSOR_FLAG_WAKE_UP | SENSOR_FLAG_SUPPORTS_DATA_INJECTION, {}},
    {"Invensense Geomagnetic Rotation Vector - Wakeup", "Invensense", 1,
     SENSORS_GEOMAGNETIC_ROTATION_VECTOR_WAKEUP_HANDLE,
     SENSOR_TYPE_GEOMAGNETIC_ROTATION_VECTOR, 1.0f, 0.000010f, 1.0f, 4545, 0, 0,
     "android.sensor.geomagnetic_rotation_vector", "", 200000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP, {}},
};

/* This list is for combo chipset */
static struct sensor_t sMplSensorList[] =
{
    {"Invensense Gyroscope", "Invensense", 1,
     SENSORS_GYROSCOPE_HANDLE,
     SENSOR_TYPE_GYROSCOPE, 2000.0f, 1.0f, 0.5f, 10000, 0, 57,
     "android.sensor.gyroscope", "", 10000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
    {"Invensense Gyroscope Uncalibrated", "Invensense", 1,
     SENSORS_RAW_GYROSCOPE_HANDLE,
     SENSOR_TYPE_GYROSCOPE_UNCALIBRATED, 2000.0f, 1.0f, 0.5f, 10000, 0, 40,
     "android.sensor.gyroscope_uncalibrated", "", 10000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_SUPPORTS_DATA_INJECTION, {}},
    {"Invensense Accelerometer", "Invensense", 1,
     SENSORS_ACCELERATION_HANDLE,
     SENSOR_TYPE_ACCELEROMETER, 10240.0f, 1.0f, 0.5f, 10000, 0, 90,
     "android.sensor.accelerometer", "", 10000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_SUPPORTS_DATA_INJECTION, {}},
    {"Invensense Magnetometer", "Invensense", 1,
     SENSORS_MAGNETIC_FIELD_HANDLE,
     SENSOR_TYPE_MAGNETIC_FIELD, 10240.0f, 1.0f, 0.5f, 10000, 0, 57,
     "android.sensor.magnetic_field", "", 10000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
    {"Invensense Magnetometer Uncalibrated", "Invensense", 1,
     SENSORS_RAW_MAGNETIC_FIELD_HANDLE,
     SENSOR_TYPE_MAGNETIC_FIELD_UNCALIBRATED, 10240.0f, 1.0f, 0.5f, 10000, 0, 40,
     "android.sensor.magnetic_field_uncalibrated", "", 10000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_SUPPORTS_DATA_INJECTION, {}},
    {"Invensense Orientation", "Invensense", 1,
     SENSORS_ORIENTATION_HANDLE,
     SENSOR_TYPE_ORIENTATION, 360.0f, 1.0f, 9.7f, 10000, 0, 50,
     "android.sensor.orientation", "", 10000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
    {"Invensense Rotation Vector", "Invensense", 1,
     SENSORS_ROTATION_VECTOR_HANDLE,
     SENSOR_TYPE_ROTATION_VECTOR, 10240.0f, 1.0f, 0.5f, 10000, 0, 50,
     "android.sensor.rotation_vector", "", 10000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
    {"Invensense Game Rotation Vector", "Invensense", 1,
     SENSORS_GAME_ROTATION_VECTOR_HANDLE,
     SENSOR_TYPE_GAME_ROTATION_VECTOR, 10240.0f, 1.0f, 0.5f, 10000, 0, 62,
     "android.sensor.game_rotation_vector", "", 10000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
    {"Invensense Linear Acceleration", "Invensense", 1,
     SENSORS_LINEAR_ACCEL_HANDLE,
     SENSOR_TYPE_LINEAR_ACCELERATION, 10240.0f, 1.0f, 0.5f, 10000, 0, 0,
     "android.sensor.linear_acceleration", "", 10000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
    {"Invensense Gravity", "Invensense", 1,
     SENSORS_GRAVITY_HANDLE,
     SENSOR_TYPE_GRAVITY, 10240.0f, 1.0f, 0.5f, 10000, 0, 0,
     "android.sensor.gravity", "", 10000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
    {"Invensense Significant Motion", "Invensense", 1,
     SENSORS_SIGNIFICANT_MOTION_HANDLE,
     SENSOR_TYPE_SIGNIFICANT_MOTION, 100.0f, 1.0f, 1.1f, -1, 0, 0,
     "android.sensor.significant_motion", "", 0, SENSOR_FLAG_ONE_SHOT_MODE | SENSOR_FLAG_WAKE_UP | SENSOR_FLAG_SUPPORTS_DATA_INJECTION, {}},
    {"Invensense Step Detector", "Invensense", 1,
     SENSORS_PEDOMETER_HANDLE,
     SENSOR_TYPE_STEP_DETECTOR, 100.0f, 1.0f, 1.1f, 0, 0, 62,
     "android.sensor.step_detector", "", 0, SENSOR_FLAG_SPECIAL_REPORTING_MODE | SENSOR_FLAG_SUPPORTS_DATA_INJECTION, {}},
    {"Invensense Step Counter", "Invensense", 1,
     SENSORS_STEP_COUNTER_HANDLE,
     SENSOR_TYPE_STEP_COUNTER, 100.0f, 1.0f, 1.1f, 0, 0, 0,
     "android.sensor.step_counter", "", 100000, SENSOR_FLAG_ON_CHANGE_MODE | SENSOR_FLAG_SUPPORTS_DATA_INJECTION, {}},
    {"Invensense Geomagnetic Rotation Vector", "Invensense", 1,
     SENSORS_GEOMAGNETIC_ROTATION_VECTOR_HANDLE,
     SENSOR_TYPE_GEOMAGNETIC_ROTATION_VECTOR, 10240.0f, 1.0f, 0.5f, 5000, 0, 50,
     "android.sensor.geomagnetic_rotation_vector", "", 5000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
    {"Invensense Tilt", "Invensense", 1,
     SENSORS_WAKE_UP_TILT_DETECTOR_HANDLE,
     SENSOR_TYPE_TILT_DETECTOR, 10240.0f, 1.0f, 0.5f, 0, 0, 0,
     "android.sensor.tilt_detector", "", 0, SENSOR_FLAG_SPECIAL_REPORTING_MODE, {}},
    {"Invensense Pick Up Gesture", "Invensense", 1,
     SENSORS_PICK_UP_GESTURE_HANDLE,
     SENSOR_TYPE_PICK_UP_GESTURE, 10240.0f, 1.0f, 0.5f, -1, 0, 0,
     "android.sensor.pick_up_gesture", "", 0, SENSOR_FLAG_ONE_SHOT_MODE | SENSOR_FLAG_WAKE_UP, {}},
#if 0
    {"Invensense EIS Gyroscope", "Invensense", 1,
     SENSORS_EIS_GYROSCOPE_HANDLE,
     SENSOR_TYPE_EIS_GYROSCOPE, 34.906559f, 0.001065f, 5.5f, 0, 0, 0,
     "android.sensor.eis_gyroscope", "", 4444, SENSOR_FLAG_ON_CHANGE_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense EIS Authentication", "Invensense", 1,
     SENSORS_EIS_AUTHENTICATION_HANDLE,
     SENSOR_TYPE_EIS_AUTHENTICATION, 34.906559f, 0.001065f, 5.5f, 0, 0, 0,
     "android.sensor.eis_authentication", "", 4444, SENSOR_FLAG_ON_CHANGE_MODE | SENSOR_FLAG_WAKE_UP, {}},
#endif
    {"Invensense Gyroscope - Wakeup", "Invensense", 1,
     SENSORS_GYROSCOPE_WAKEUP_HANDLE,
     SENSOR_TYPE_GYROSCOPE, 2000.0f, 1.0f, 0.5f, 10000, 0, 57,
     "android.sensor.gyroscope", "", 10000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Gyroscope Uncalibrated - Wakeup", "Invensense", 1,
     SENSORS_RAW_GYROSCOPE_WAKEUP_HANDLE,
     SENSOR_TYPE_GYROSCOPE_UNCALIBRATED, 2000.0f, 1.0f, 0.5f, 10000, 0, 40,
     "android.sensor.gyroscope_uncalibrated", "", 10000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP | SENSOR_FLAG_SUPPORTS_DATA_INJECTION, {}},
    {"Invensense Accelerometer - Wakeup", "Invensense", 1,
     SENSORS_ACCELERATION_WAKEUP_HANDLE,
     SENSOR_TYPE_ACCELEROMETER, 10240.0f, 1.0f, 0.5f, 10000, 0, 90,
     "android.sensor.accelerometer", "", 10000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP | SENSOR_FLAG_SUPPORTS_DATA_INJECTION, {}},
    {"Invensense Magnetometer - Wakeup", "Invensense", 1,
     SENSORS_MAGNETIC_FIELD_WAKEUP_HANDLE,
     SENSOR_TYPE_MAGNETIC_FIELD, 10240.0f, 1.0f, 0.5f, 10000, 0, 57,
     "android.sensor.magnetic_field", "", 10000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Magnetometer Uncalibrated - Wakeup", "Invensense", 1,
     SENSORS_RAW_MAGNETIC_FIELD_WAKEUP_HANDLE,
     SENSOR_TYPE_MAGNETIC_FIELD_UNCALIBRATED, 10240.0f, 1.0f, 0.5f, 10000, 0, 40,
     "android.sensor.magnetic_field_uncalibrated", "", 10000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP | SENSOR_FLAG_SUPPORTS_DATA_INJECTION, {}},
    {"Invensense Orientation - Wakeup", "Invensense", 1,
     SENSORS_ORIENTATION_WAKEUP_HANDLE,
     SENSOR_TYPE_ORIENTATION, 360.0f, 1.0f, 9.7f, 10000, 0, 50,
     "android.sensor.orientation", "", 10000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Rotation Vector - Wakeup", "Invensense", 1,
     SENSORS_ROTATION_VECTOR_WAKEUP_HANDLE,
     SENSOR_TYPE_ROTATION_VECTOR, 10240.0f, 1.0f, 0.5f, 10000, 0, 50,
     "android.sensor.rotation_vector", "", 10000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Game Rotation Vector - Wakeup", "Invensense", 1,
     SENSORS_GAME_ROTATION_VECTOR_WAKEUP_HANDLE,
     SENSOR_TYPE_GAME_ROTATION_VECTOR, 10240.0f, 1.0f, 0.5f, 10000, 0, 62,
     "android.sensor.game_rotation_vector", "", 10000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Linear Acceleration - Wakeup", "Invensense", 1,
     SENSORS_LINEAR_ACCEL_WAKEUP_HANDLE,
     SENSOR_TYPE_LINEAR_ACCELERATION, 10240.0f, 1.0f, 0.5f, 10000, 0, 0,
     "android.sensor.linear_acceleration", "", 10000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Gravity - Wakeup", "Invensense", 1,
     SENSORS_GRAVITY_WAKEUP_HANDLE,
     SENSOR_TYPE_GRAVITY, 10240.0f, 1.0f, 0.5f, 10000, 0, 0,
     "android.sensor.gravity", "", 10000,  SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP, {}},
    {"Invensense Step Detector - Wakeup", "Invensense", 1,
     SENSORS_PEDOMETER_WAKEUP_HANDLE,
     SENSOR_TYPE_STEP_DETECTOR, 100.0f, 1.0f, 1.1f, 0, 0, 62,
     "android.sensor.step_detector", "", 0, SENSOR_FLAG_SPECIAL_REPORTING_MODE | SENSOR_FLAG_WAKE_UP | SENSOR_FLAG_SUPPORTS_DATA_INJECTION, {}},
    {"Invensense Step Counter - Wakeup", "Invensense", 1,
     SENSORS_STEP_COUNTER_WAKEUP_HANDLE,
     SENSOR_TYPE_STEP_COUNTER, 100.0f, 1.0f, 1.1f, 0, 0, 0,
     "android.sensor.step_counter", "", 100000, SENSOR_FLAG_ON_CHANGE_MODE  | SENSOR_FLAG_WAKE_UP | SENSOR_FLAG_SUPPORTS_DATA_INJECTION, {}},
    {"Invensense Geomagnetic Rotation Vector - Wakeup", "Invensense", 1,
     SENSORS_GEOMAGNETIC_ROTATION_VECTOR_WAKEUP_HANDLE,
     SENSOR_TYPE_GEOMAGNETIC_ROTATION_VECTOR, 10240.0f, 1.0f, 0.5f, 5000, 0, 50,
     "android.sensor.geomagnetic_rotation_vector", "", 5000, SENSOR_FLAG_CONTINUOUS_MODE | SENSOR_FLAG_WAKE_UP, {}},
};

/* This list is for raw sensors (custom sensor) */
static struct sensor_t sCustomSensorList[] =
{
    {"Invensense Raw Gyroscope", "Invensense", 1,
     SENSORS_GYROSCOPE_RAW_HANDLE,
     SENSOR_TYPE_GYROSCOPE_UNCALIBRATED, 2000.0f, 1.0f, 0.5f, 4545, 0, 40,
     "android.sensor.gyroscope_uncalibrated", "", 10000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
    {"Invensense Raw Accelerometer", "Invensense", 1,
     SENSORS_ACCELERATION_RAW_HANDLE,
     SENSOR_TYPE_ACCELEROMETER, 19.61330f, 1.0f, 0.5f, 4545, 0, 90,
     "android.sensor.accelerometer", "", 10000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
    {"Invensense Raw Magnetometer", "Invensense", 1,
     SENSORS_MAGNETIC_FIELD_RAW_HANDLE,
     SENSOR_TYPE_MAGNETIC_FIELD_UNCALIBRATED, 10240.0f, 1.0f, 0.5f, 10000, 0, 40,
     "android.sensor.magnetic_field_uncalibrated", "", 10000, SENSOR_FLAG_CONTINUOUS_MODE, {}},
};

/*****************************************************************************/

/*
 * The SENSORS Module
 */

/* MPU6050 MPU9150 */
#define EVENT_TYPE_ICOMPASS_X      REL_X
#define EVENT_TYPE_ICOMPASS_Y      REL_Y
#define EVENT_TYPE_ICOMPASS_Z      REL_Z

// conversion of acceleration data to SI units (m/s^2)
#define RANGE_A                     (4*GRAVITY_EARTH)
#define RESOLUTION_A                (GRAVITY_EARTH / LSG)
#define CONVERT_A                   (GRAVITY_EARTH / LSG)
#define CONVERT_A_X                 (CONVERT_A)
#define CONVERT_A_Y                 (CONVERT_A)
#define CONVERT_A_Z                 (CONVERT_A)

/* AKM  compasses */
#define EVENT_TYPE_MAGV_X           ABS_RX
#define EVENT_TYPE_MAGV_Y           ABS_RY
#define EVENT_TYPE_MAGV_Z           ABS_RZ
#define EVENT_TYPE_MAGV_STATUS      ABS_RUDDER

/* conversion of magnetic data to uT units */
#define CONVERT_M                   (0.06f)

/* conversion of sensor rates */
#define hertz_request = 200;
#define DEFAULT_MPL_GYRO_RATE           (20000L)     //us
#define DEFAULT_MPL_COMPASS_RATE        (20000L)     //us

#define DEFAULT_HW_GYRO_RATE            (100)        //Hz
#define DEFAULT_HW_ACCEL_RATE           (20)         //ms
#define DEFAULT_HW_COMPASS_RATE         (20000000L)  //ns
#define DEFAULT_HW_AKMD_COMPASS_RATE    (200000000L) //ns

/* convert ns to hardware units */
#define HW_GYRO_RATE_NS                 (1000000000LL / rate_request) // to Hz
#define HW_ACCEL_RATE_NS                (rate_request / (1000000L))   // to ms
#define HW_COMPASS_RATE_NS              (rate_request)                // to ns

/* convert Hz to hardware units */
#define HW_GYRO_RATE_HZ                 (hertz_request)
#define HW_ACCEL_RATE_HZ                (1000 / hertz_request)
#define HW_COMPASS_RATE_HZ              (1000000000LL / hertz_request)

#define RATE_200HZ                      5000000LL
#define RATE_15HZ                       66667000LL
#define RATE_5HZ                        200000000LL

/* convert radian to degree */
#define RAD_P_DEG                      (3.14159f/180.f)

#define PROXIMITY_RANGE                 280
__END_DECLS

#endif  // ANDROID_SENSORS_H
