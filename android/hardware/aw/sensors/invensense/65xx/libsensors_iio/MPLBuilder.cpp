
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

#if defined INV_LIBMOTION
#include <MPLBuilder.h>
#else
#include <MPLBuilderInt.h>
#endif
#include <dirent.h>
#include <string.h>
#include <cutils/log.h>
#include <fcntl.h>
#include <math.h>
#include "dmp3_compass_cal_lib.h"
#include "dmp3_9axis_lib.h"
#include "dmp3_geomag_lib.h"
#include "dmp3_6axis_lib.h"
#include "dmp3_gyro_cal_lib.h"
#include "dmp3_accel_cal_lib.h"
#include "ml_math_func.h"
#include "sensors.h"
#include "dmp3_bac_lib.h"
#include "dmp3_flip_pickup_detection_lib.h"
#include "dmp3_pedometer_lib.h"

#define BUILDER_VERBOSE 0

//#define COMPASS_CONVERSION 4.76837158203125e-007f
#define COMPASS_CONVERSION 1.52587890625e-005f
#define ACCEL_CONVERSION 0.000598550415039f
#define GRAVITY_CONVERSION 0.000149637603759766f
#define GYRO_CONVERSION 2.66316109007924e-007f
#define INV_TWO_POWER_NEG_30 9.313225746154785e-010f
#define INV_TWO_POWER_NEG_16 1.52587890625e-05f

static struct mpl_sensor_data sensor_data;
static unsigned char compass_storage[INV_COMPASS_STORAGE_SIZE];
static unsigned char nineaxis_storage[INV_9AXIS_STORAGE_SIZE];
static unsigned char geomag_storage[INV_GEOMAG_STORAGE_SIZE];
static unsigned char sixaxis_storage[INV_6AXIS_STORAGE_SIZE];
static unsigned char accel_storage[INV_ACCEL_STORAGE_SIZE];
static unsigned char gyro_storage[INV_GYRO_STORAGE_SIZE];
static unsigned char bac_storage[INV_BAC_STORAGE_SIZE];
static unsigned char pickup_storage[INV_PICKUP_STORAGE_SIZE];
static unsigned char pedometer_storage[INV_PEDOMETER_STORAGE_SIZE];

void init_mpl_cal_lib()
{
    dmp3_compass_cal_lib_init((void*) compass_storage);
    dmp3_6axis_lib_init((void*) sixaxis_storage);
    dmp3_9axis_lib_init((void*) nineaxis_storage);
    dmp3_geomag_lib_init((void*) geomag_storage);
    dmp3_gyro_cal_lib_init((void*) gyro_storage);
    dmp3_accel_cal_lib_init((void*) accel_storage);
    dmp3_bac_lib_init((void*) bac_storage);
    dmp3_flip_pickup_lib_init((void*) pickup_storage);
    dmp3_pedometer_lib_init((void*) pedometer_storage);
}

int mpl_gyro_reset_timestamp(void)
{
    sensor_data.gyro_timestamp = 0;
    sensor_data.gyro_last_timestamp = 0;
    sensor_data.gyro_raw_timestamp = 0;
    sensor_data.gyro_raw_last_timestamp = 0;
    sensor_data.gyro_w_timestamp = 0;
    sensor_data.gyro_w_last_timestamp = 0;
    sensor_data.gyro_raw_w_timestamp = 0;
    sensor_data.gyro_raw_w_last_timestamp = 0;

    sensor_data.quat9_timestamp = 0;
	sensor_data.quat9_last_timestamp = 0;
    sensor_data.quat9_w_timestamp = 0;
    sensor_data.quat9_w_last_timestamp = 0;
    sensor_data.or_timestamp = 0;
    sensor_data.or_last_timestamp = 0;
    sensor_data.or_w_timestamp = 0;
    sensor_data.or_w_last_timestamp = 0;

    sensor_data.quat6_timestamp = 0;
    sensor_data.quat6_last_timestamp = 0;
    sensor_data.quat6_w_timestamp = 0;
    sensor_data.quat6_w_last_timestamp = 0;

    return 0;
}

int mpl_accel_reset_timestamp(void)
{
    sensor_data.accel_timestamp = 0;
    sensor_data.accel_last_timestamp = 0;
    sensor_data.accel_w_timestamp = 0;
    sensor_data.accel_w_last_timestamp = 0;

    sensor_data.la_timestamp = 0;
    sensor_data.la_last_timestamp = 0;
    sensor_data.gravity_timestamp = 0;
    sensor_data.gravity_last_timestamp = 0;
    sensor_data.quat6_compass_timestamp = 0;
    sensor_data.quat6_compass_last_timestamp = 0;
    sensor_data.la_w_timestamp = 0;
    sensor_data.la_w_last_timestamp = 0;
    sensor_data.gravity_w_timestamp = 0;
    sensor_data.gravity_w_last_timestamp = 0;
    sensor_data.quat6_compass_w_timestamp = 0;
    sensor_data.quat6_compass_w_last_timestamp = 0;

    return 0;
}

int mpl_compass_reset_timestamp(void)
{
    sensor_data.compass_timestamp = 0;
    sensor_data.compass_last_timestamp = 0;
    sensor_data.compass_raw_timestamp = 0;
    sensor_data.compass_raw_last_timestamp = 0;
    sensor_data.compass_w_timestamp = 0;
    sensor_data.compass_w_last_timestamp = 0;
    sensor_data.compass_raw_w_timestamp = 0;
    sensor_data.compass_raw_w_last_timestamp = 0;

    return 0;
}

int mpl_gesture_reset_timestamp(void)
{
    sensor_data.sd_last_timestamp = 0;
    sensor_data.sd_w_last_timestamp = 0;

    return 0;
}

int mpl_build_gyro(int *gyro, int status, long long timestamp)
{
    int gyro_calibrated[3];
    int gyro_scaled[3];
    int accel_scaled[3];
    int gyro_bias[3];
    short temperature = 0;
    short data_status = 0x3;

        sensor_data.gyro_timestamp = timestamp;

        sensor_data.gyro_raw_timestamp = timestamp;

    sensor_data.gyro_w_timestamp = timestamp;

    sensor_data.gyro_raw_w_timestamp = timestamp;

    sensor_data.gyro_raw_data[0] = (short)(gyro[0]);
    sensor_data.gyro_raw_data[1] = (short)(gyro[1]);
    sensor_data.gyro_raw_data[2] = (short)(gyro[2]);

    sensor_data.accel_raw_data[0] = (short)(sensor_data.accel_data[0]>>sensor_data.accel_scale);
    sensor_data.accel_raw_data[1] = (short)(sensor_data.accel_data[1]>>sensor_data.accel_scale);
    sensor_data.accel_raw_data[2] = (short)(sensor_data.accel_data[2]>>sensor_data.accel_scale);

    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: gyro in: %d %d %d", sensor_data.gyro_raw_data[0], sensor_data.gyro_raw_data[1],
                            sensor_data.gyro_raw_data[2]);
    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: accel in: %d %d %d", sensor_data.accel_raw_data[0], sensor_data.accel_raw_data[1],
                            sensor_data.accel_raw_data[2]);

    dmp3_gyro_cal_lib_process(sensor_data.accel_raw_data, sensor_data.gyro_raw_data, temperature, data_status);
    dmp3_gyro_cal_lib_store_output((void *) gyro_storage);
    dmp3_gyro_cal_lib_get_gyro_calibrated((void *) gyro_storage, gyro_calibrated);
    dmp3_gyro_cal_lib_get_gyro_bias((void *)gyro_storage, gyro_bias);

    memcpy(sensor_data.gyro_data, gyro_calibrated, sizeof(gyro_calibrated));
    memcpy(sensor_data.gyro_bias, gyro_bias, sizeof(gyro_bias));

    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: cal gyro: %d %d %d", gyro_calibrated[0], gyro_calibrated[1],
                            gyro_calibrated[2]);

    mpl_build_quat();
    mpl_build_quat9();
    return 0;
}

int mpl_build_eis_gyro(int *gyro, int status, long long timestamp)
{
    int gyro_calibrated[3];
    int gyro_scaled[3];
    int accel_scaled[3];
    int gyro_bias[3];
    short temperature = 0;
    short data_status = 0x3;

    sensor_data.eis_gyro_timestamp = timestamp;

    sensor_data.gyro_raw_data[0] = (short)(gyro[0] >> 15);
    sensor_data.gyro_raw_data[1] = (short)(gyro[1] >> 15);
    sensor_data.gyro_raw_data[2] = (short)(gyro[2] >> 15);

    sensor_data.accel_raw_data[0] = (short)(sensor_data.accel_data[0]>>sensor_data.accel_scale);
    sensor_data.accel_raw_data[1] = (short)(sensor_data.accel_data[1]>>sensor_data.accel_scale);
    sensor_data.accel_raw_data[2] = (short)(sensor_data.accel_data[2]>>sensor_data.accel_scale);

    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: eis gyro in: %d %d %d", sensor_data.gyro_raw_data[0], sensor_data.gyro_raw_data[1],
                            sensor_data.gyro_raw_data[2]);
    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: accel in: %d %d %d", sensor_data.accel_raw_data[0], sensor_data.accel_raw_data[1],
                            sensor_data.accel_raw_data[2]);

    dmp3_gyro_cal_lib_process(sensor_data.accel_raw_data, sensor_data.gyro_raw_data, temperature, data_status);
    dmp3_gyro_cal_lib_store_output((void *) gyro_storage);
    dmp3_gyro_cal_lib_get_gyro_calibrated((void *) gyro_storage, gyro_calibrated);
    dmp3_gyro_cal_lib_get_gyro_bias((void *)gyro_storage, gyro_bias);

    memcpy(sensor_data.eis_calibrated, gyro_calibrated, sizeof(gyro_calibrated));
    memcpy(sensor_data.eis_gyro_bias, gyro_bias, sizeof(gyro_bias));
    sensor_data.eis_calibrated[3] = gyro[3];

    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: cal gyro: %d %d %d", sensor_data.eis_calibrated[0], sensor_data.eis_calibrated[1],
                            sensor_data.eis_calibrated[2]);

    return 0;
}

int mpl_set_accel_full_scale (int full_scale)
{
    switch (full_scale) {
        case 2:
              sensor_data.accel_full_scale = 0;
              break;
        case 4:
              sensor_data.accel_full_scale = 1;
              break;
        case 8:
              sensor_data.accel_full_scale = 2;
              break;
        case 16:
              sensor_data.accel_full_scale = 3;
              break;
        default:
              sensor_data.accel_full_scale = 0;
              break;
    }

    return 0;
}

int mpl_set_gyro_scale_factor_base(int internal_sampling_rate_hz)
{
#define  GYRO_SCALE_BASE 18740329825.4939f

    if(internal_sampling_rate_hz < 0)
        return -1;

    sensor_data.gyro_scale_factor_base = GYRO_SCALE_BASE / internal_sampling_rate_hz;

    return 0;
}

int mpl_build_accel(int *accel, int status, long long timestamp)
{
    int accel_calibrated[3];
    int accel_scaled[3];
    short accel_local[3];

    sensor_data.accel_timestamp = timestamp;
    sensor_data.accel_w_timestamp = timestamp;

    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: accel in: %d %d %d", accel[0], accel[1],
                            accel[2]);

    sensor_data.accel_raw_data[0] = (short)(accel[0]);//>>sensor_data.accel_scale);
    sensor_data.accel_raw_data[1] = (short)(accel[1]);//>>sensor_data.accel_scale);
    sensor_data.accel_raw_data[2] = (short)(accel[2]);//>>sensor_data.accel_scale);
    /* Change to use 4G. */
    accel_local[0] = (sensor_data.accel_raw_data[0] << sensor_data.accel_full_scale);
    accel_local[1] = (sensor_data.accel_raw_data[1] << sensor_data.accel_full_scale);
    accel_local[2] = (sensor_data.accel_raw_data[2] << sensor_data.accel_full_scale);

    //ALOGI("humane0 real accel = %d accel_raw=%d", accel_local[0], sensor_data.accel_raw_data[0]);
    //ALOGI("humane1 real accel = %d accel_raw=%d", accel_local[1], sensor_data.accel_raw_data[1]);
    //ALOGI("humane2 real accel = %d accel_raw=%d", accel_local[2], sensor_data.accel_raw_data[2]);

    dmp3_accel_cal_lib_process(accel_local);
    //dmp3_accel_cal_lib_process1(sensor_data.accel_raw_data, sensor_data.compass_soft_iron);
    dmp3_accel_cal_lib_store_output((void *) accel_storage);
    dmp3_accel_cal_lib_get_accel_calibrated((void *) accel_storage, accel_calibrated);
    dmp3_accel_cal_lib_get_accel_bias((void *)accel_storage, sensor_data.accel_bias);

    memcpy(sensor_data.accel_data, accel_calibrated, sizeof(accel_calibrated));
    sensor_data.accel_data[0] >>= sensor_data.accel_full_scale;
    sensor_data.accel_data[1] >>= sensor_data.accel_full_scale;
    sensor_data.accel_data[2] >>= sensor_data.accel_full_scale;

    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: cal accel: %d %d %d", accel_calibrated[0], accel_calibrated[1],
                            accel_calibrated[2]);

    mpl_build_bac();
    mpl_build_pickup();
    mpl_build_pedometer();
    mpl_build_quat_compass();
    return 0;
}

int mpl_build_compass(int *compass, int status, long long timestamp)
{
    int compass_variance;
    int compass_calibrated[3];
    int compass_bias[3];
    int raw_compass[3];
    long long temp[3];
    int soft_iron[9];

    int scale = 161061273;

    sensor_data.compass_timestamp = timestamp;

    sensor_data.compass_raw_timestamp = timestamp;

    sensor_data.compass_w_timestamp = timestamp;

    sensor_data.compass_raw_w_timestamp = timestamp;

    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: compass in: %d %d %d", compass[0], compass[1],
                            compass[2]);
#if 1 // (& mpl_compass)
    raw_compass[0] = compass[0];
    raw_compass[1] = compass[1];
    raw_compass[2] = compass[2];
#else
	temp[0] = (long long) compass[0] * sensor_data.compass_sens[0];
	temp[1] = (long long) compass[1] * sensor_data.compass_sens[1];
	temp[2] = (long long) compass[2] * sensor_data.compass_sens[2];
	raw_compass[0] = (short)(temp[0] >> 30);
	raw_compass[1] = (short)(temp[1] >> 30);
	raw_compass[2] = (short)(temp[2] >> 30);
#endif
    compass_variance = 0;

    for(int i=0; i<9; i++)
        soft_iron[i] = (int)sensor_data.compass_soft_iron[i];

    dmp3_compass_cal_lib_process(raw_compass, compass_variance, soft_iron);
    dmp3_compass_cal_lib_store_output((void *) compass_storage);
    dmp3_compass_cal_lib_get_compass_calibrated((void *) compass_storage, compass_calibrated);
    dmp3_compass_cal_lib_get_compass_bias((void *)compass_storage, compass_bias);

    memcpy(sensor_data.compass_data, compass_calibrated, sizeof(compass_calibrated));
    memcpy(sensor_data.compass_bias, compass_bias, sizeof(compass_bias));

    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: cal compass: %d %d %d", compass_calibrated[0], compass_calibrated[1],
                            compass_calibrated[2]);
    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: cal compass bias out: %d %d %d", sensor_data.compass_bias[0],
                            sensor_data.compass_bias[1],
                            sensor_data.compass_bias[2]);

    return 0;
}

int mpl_build_quat(int *quat, int status, int64_t *timestamp)
{
    return 0;
}

void mpl_build_quat()
{
    int mtx[9];
    int accel_local[3];

    sensor_data.quat6_timestamp = sensor_data.gyro_timestamp;
    sensor_data.quat6_w_timestamp = sensor_data.gyro_timestamp;
    sensor_data.la_timestamp = sensor_data.gyro_timestamp;
    sensor_data.gravity_timestamp = sensor_data.gyro_timestamp;
    sensor_data.la_w_timestamp = sensor_data.gyro_timestamp;
    sensor_data.gravity_w_timestamp = sensor_data.gyro_timestamp;

    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: grv accel in: %d %d %d", sensor_data.accel_data[0],
                    sensor_data.accel_data[1], sensor_data.accel_data[2]);
    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: grv gyro in: %d %d %d", sensor_data.gyro_data[0],
                    sensor_data.gyro_data[1], sensor_data.gyro_data[2]);

    accel_local[0] = (sensor_data.accel_data[0] << sensor_data.accel_full_scale);
    accel_local[1] = (sensor_data.accel_data[1] << sensor_data.accel_full_scale);
    accel_local[2] = (sensor_data.accel_data[2] << sensor_data.accel_full_scale);

    dmp3_6axis_lib_process(sensor_data.gyro_data, accel_local, 3);
    dmp3_6axis_lib_store_output((void *) sixaxis_storage);
    dmp3_6axis_lib_get_6axis_quaternion((void *) sixaxis_storage, sensor_data.six_axis_data);
    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: grv out1: %d %d %d %d", sensor_data.six_axis_data[0], sensor_data.six_axis_data[1],
                            sensor_data.six_axis_data[2], sensor_data.six_axis_data[3]);
    inv_orientation_scalar_to_matrix(sensor_data.gyro_matrix_scalar, mtx);
    inv_convert_quaternion_to_body(mtx, sensor_data.six_axis_data, sensor_data.six_axis_data_scaled);
}

void mpl_build_quat9()
{
    short mag_dis9x;
    int compass_accuracy;
    int fusion_data[3];
    int mtx[9];
    int compass_data_scaled[3];
    int six_axis_data_scaled[4];

    sensor_data.quat9_timestamp = sensor_data.gyro_timestamp;
    sensor_data.quat9_w_timestamp = sensor_data.gyro_timestamp;
    sensor_data.or_timestamp = sensor_data.gyro_timestamp;
    sensor_data.or_w_timestamp = sensor_data.gyro_timestamp;

    compass_accuracy = dmp3_compass_cal_lib_get_compass_accuracy((void *)compass_storage);
    mag_dis9x = dmp3_compass_cal_lib_get_compass_mag_dis((void *)compass_storage);
    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder 9axis: compass acc=%d mag_dis=%d", compass_accuracy, mag_dis9x);
    dmp3_compass_cal_lib_get_fusion_data((void *)compass_storage, fusion_data);

    inv_convert_to_body(sensor_data.compass_matrix_scalar, sensor_data.compass_data, compass_data_scaled);
    inv_orientation_scalar_to_matrix(sensor_data.gyro_matrix_scalar, mtx);
    inv_convert_quaternion_to_body(mtx, sensor_data.six_axis_data, six_axis_data_scaled);

    dmp3_9axis_lib_process(compass_data_scaled, six_axis_data_scaled, compass_accuracy, mag_dis9x, fusion_data);
    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder 9axis: compass %d, %d, %d", compass_data_scaled[0], compass_data_scaled[1], compass_data_scaled[2]);
    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder 9 axis: six %d, %d, %d, %d", sensor_data.six_axis_data[0], sensor_data.six_axis_data[1], sensor_data.six_axis_data[2],
                sensor_data.six_axis_data[3]);
    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder 9 axis: six %d, %d, %d, %d", six_axis_data_scaled[0], six_axis_data_scaled[1], six_axis_data_scaled[2],
                six_axis_data_scaled[3]);
    dmp3_9axis_lib_store_output((void *)nineaxis_storage);
    dmp3_9axis_lib_get_9axis_quaternion((void *)nineaxis_storage, sensor_data.nine_axis_data);
}

void mpl_build_quat_compass()
{
    short mag_dis9x;
    int compass_accuracy;
    int fusion_data[3];
    int mtx[9];
    int compass_data_scaled[3];
    int accel_data_scaled[3];

    sensor_data.quat6_compass_timestamp = sensor_data.accel_timestamp;
    sensor_data.quat6_compass_w_timestamp = sensor_data.accel_timestamp;
    compass_accuracy = dmp3_compass_cal_lib_get_compass_accuracy((void *)compass_storage);
    mag_dis9x = dmp3_compass_cal_lib_get_compass_mag_dis((void *)compass_storage);

    inv_convert_to_body(sensor_data.compass_matrix_scalar, sensor_data.compass_data, compass_data_scaled);
    inv_convert_to_body(sensor_data.accel_matrix_scalar, sensor_data.accel_data, accel_data_scaled);

    dmp3_compass_cal_lib_get_fusion_data((void *)compass_storage, fusion_data);
    dmp3_geomag_lib_process(compass_data_scaled, accel_data_scaled, compass_accuracy, mag_dis9x, fusion_data);
    dmp3_geomag_lib_store_output((void *)geomag_storage);
    dmp3_geomag_lib_get_geomag_quaternion((void *)geomag_storage, sensor_data.six_axis_compass_data);
}

void mpl_build_bac()
{
    int accel_data[3];
    int accel_sampling_rate = 20000;
    long long build_dts;

    build_dts = sensor_data.accel_timestamp  - sensor_data.tilt_last_build_timestamp;
    if(!build_dts)
        return;
    sensor_data.tilt_last_build_timestamp = sensor_data.accel_timestamp;
    accel_sampling_rate = sensor_data.gesture_algo_sample_rate;
    if(!accel_sampling_rate){
        return;
    }
    accel_data[0] = sensor_data.accel_data[0] << sensor_data.accel_full_scale;
    accel_data[1] = sensor_data.accel_data[1] << sensor_data.accel_full_scale;
    accel_data[2] = sensor_data.accel_data[2] << sensor_data.accel_full_scale;
    dmp3_bac_lib_process(accel_data, accel_sampling_rate, false);
    dmp3_bac_lib_store_output((void *) bac_storage);
}

void mpl_build_pickup()
{
    short accel_raw_data[3];
    int accel_sampling_rate = 20000;
    long long build_dts;

    build_dts = sensor_data.accel_timestamp  - sensor_data.pickup_last_build_timestamp;
    if(!build_dts)
        return;
    accel_sampling_rate = sensor_data.gesture_algo_sample_rate;
    if(!accel_sampling_rate){
        return;
    }
    accel_raw_data[0] = sensor_data.accel_raw_data[0] << sensor_data.accel_full_scale;
    accel_raw_data[1] = sensor_data.accel_raw_data[0] << sensor_data.accel_full_scale;
    accel_raw_data[2] = sensor_data.accel_raw_data[0] << sensor_data.accel_full_scale;
    dmp3_flip_pickup_lib_process(accel_raw_data, accel_sampling_rate);
    dmp3_flip_pickup_lib_store_output((void *) pickup_storage);
}

void mpl_build_pedometer()
{
    int accel_data[3];
    int accel_sampling_rate = 20000;
    long long build_dts;

    build_dts = sensor_data.accel_timestamp  - sensor_data.pedo_last_build_timestamp;
    if(!build_dts)
        return;
    sensor_data.pedo_last_build_timestamp = sensor_data.accel_timestamp;
    accel_sampling_rate = sensor_data.gesture_algo_sample_rate;
    if(!accel_sampling_rate){
        return;
    }
    accel_data[0] = sensor_data.accel_data[0] << sensor_data.accel_full_scale;
    accel_data[1] = sensor_data.accel_data[1] << sensor_data.accel_full_scale;
    accel_data[2] = sensor_data.accel_data[2] << sensor_data.accel_full_scale;
    dmp3_pedometer_lib_process(accel_data, accel_sampling_rate, false);
    dmp3_pedometer_lib_store_output((void *) pedometer_storage);
}

int mpl_get_sensor_type_magnetic_field(float *values, int8_t *accuracy,
                                           int64_t *timestamp, int mode)
{
    int update = 0;
    long long currentTimestamp;
    long long lastTimestamp;
    *accuracy = (int8_t) dmp3_compass_cal_lib_get_compass_accuracy((void *)compass_storage);

#if 0
	values[0] = (float) sensor_data.compass_data[0] * COMPASS_CONVERSION;
	values[1] = (float) sensor_data.compass_data[1] * COMPASS_CONVERSION;
	values[2] = (float) sensor_data.compass_data[2] * COMPASS_CONVERSION;
#else
    int compass_data_body[3];
    inv_convert_to_body(sensor_data.compass_matrix_scalar, sensor_data.compass_data, compass_data_body); //convert to body frame
    values[0] = (float) compass_data_body[0] * COMPASS_CONVERSION;
    values[1] = (float) compass_data_body[1] * COMPASS_CONVERSION;
    values[2] = (float) compass_data_body[2] * COMPASS_CONVERSION;
#endif

    if (mode & WAKE_UP_SENSOR) {
        currentTimestamp = sensor_data.compass_w_timestamp;
        lastTimestamp = sensor_data.compass_w_last_timestamp;
    } else {
        currentTimestamp = sensor_data.compass_timestamp;
        lastTimestamp = sensor_data.compass_last_timestamp;
    }

    if (lastTimestamp == 0){
        lastTimestamp = currentTimestamp;
        if (mode & WAKE_UP_SENSOR) {
            sensor_data.compass_w_last_timestamp = lastTimestamp;
        } else {
            sensor_data.compass_last_timestamp = lastTimestamp;
        }
    }
    lastTimestamp = currentTimestamp;
    *timestamp = currentTimestamp;
    update = 1;
    if (mode & WAKE_UP_SENSOR) {
        sensor_data.compass_w_timestamp = currentTimestamp;
        sensor_data.compass_w_last_timestamp = lastTimestamp;
    } else {
        sensor_data.compass_timestamp = currentTimestamp;
        sensor_data.compass_last_timestamp = lastTimestamp;
    }

    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: cal compass out: %f %f %f  - %lld - %d- %d", values[0], values[1],
                            values[2], *timestamp, *accuracy, update);
    return update;
}

int mpl_get_sensor_type_magnetic_field_raw(float *values, int8_t *accuracy,
                                           int64_t * timestamp, int mode)
{
    int update = 0;
    *accuracy = 0;
    long long currentTimestamp;
    long long lastTimestamp;

#if 0
    values[3] = (float) ((sensor_data.compass_bias[0]) * COMPASS_CONVERSION);
    values[4] = (float) ((sensor_data.compass_bias[1]) * COMPASS_CONVERSION);
    values[5] = (float) ((sensor_data.compass_bias[2]) * COMPASS_CONVERSION);

    values[0] = (float) (sensor_data.compass_data[0] * COMPASS_CONVERSION) + values[3];
    values[1] = (float) (sensor_data.compass_data[1] * COMPASS_CONVERSION) + values[4];
    values[2] = (float) (sensor_data.compass_data[2] * COMPASS_CONVERSION) + values[5];
#else
	int compass_bias_body[3];
	int compass_data_body[3];
    inv_convert_to_body(sensor_data.compass_matrix_scalar, sensor_data.compass_bias, compass_bias_body); //convert to body frame
    inv_convert_to_body(sensor_data.compass_matrix_scalar, sensor_data.compass_data, compass_data_body); //convert to body frame

	values[3] = (float) ((compass_bias_body[0]) * COMPASS_CONVERSION);
	values[4] = (float) ((compass_bias_body[1]) * COMPASS_CONVERSION);
	values[5] = (float) ((compass_bias_body[2]) * COMPASS_CONVERSION);

	values[0] = (float) (compass_data_body[0] * COMPASS_CONVERSION) + values[3];
	values[1] = (float) (compass_data_body[1] * COMPASS_CONVERSION) + values[4];
	values[2] = (float) (compass_data_body[2] * COMPASS_CONVERSION) + values[5];

#endif
    if (mode & WAKE_UP_SENSOR) {
        currentTimestamp = sensor_data.compass_raw_w_timestamp;
        lastTimestamp = sensor_data.compass_raw_w_last_timestamp;
    } else {
        currentTimestamp = sensor_data.compass_raw_timestamp;
        lastTimestamp = sensor_data.compass_raw_last_timestamp;
    }

    if (lastTimestamp == 0){
        lastTimestamp = currentTimestamp;
        if (mode & WAKE_UP_SENSOR) {
            sensor_data.compass_raw_w_last_timestamp = lastTimestamp;
        } else {
            sensor_data.compass_raw_last_timestamp = lastTimestamp;
        }
    }
    lastTimestamp = currentTimestamp;
    *timestamp = currentTimestamp;
    update = 1;
    if (mode & WAKE_UP_SENSOR) {
        sensor_data.compass_raw_w_timestamp = currentTimestamp;
        sensor_data.compass_raw_w_last_timestamp = lastTimestamp;
    } else {
        sensor_data.compass_raw_timestamp = currentTimestamp;
        sensor_data.compass_raw_last_timestamp = lastTimestamp;
    }

    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: raw compass out: %f %f %f - %d- %d", values[0], values[1],
                            values[2], *accuracy, update);
    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: raw compass bias out: %f %f %f", values[3], values[4],
                            values[5]);

    return update;
}

int mpl_get_sensor_type_accelerometer(float *values, int8_t *accuracy, int64_t * timestamp, int mode)
{
    int update = 0;
    int mtx[9];
    int accel_data_scaled[3];
    long long currentTimestamp;
    long long lastTimestamp;

    *accuracy = (int8_t) dmp3_accel_cal_lib_get_accel_accuracy((void *)accel_storage);

    inv_convert_to_body(sensor_data.accel_matrix_scalar, sensor_data.accel_data, accel_data_scaled);

    values[0] = (float) (accel_data_scaled[0]>>sensor_data.accel_scale) * ACCEL_CONVERSION;
    values[1] = (float) (accel_data_scaled[1]>>sensor_data.accel_scale) * ACCEL_CONVERSION;
    values[2] = (float) (accel_data_scaled[2]>>sensor_data.accel_scale) * ACCEL_CONVERSION;

    if (mode & WAKE_UP_SENSOR) {
        currentTimestamp = sensor_data.accel_w_timestamp;
        lastTimestamp = sensor_data.accel_w_last_timestamp;
    } else {
        currentTimestamp = sensor_data.accel_timestamp;
        lastTimestamp = sensor_data.accel_last_timestamp;
    }

    if (lastTimestamp == 0){
        lastTimestamp = currentTimestamp;
        if (mode & WAKE_UP_SENSOR) {
            sensor_data.accel_w_last_timestamp = lastTimestamp;
        } else {
            sensor_data.accel_last_timestamp = lastTimestamp;
        }
    }
    lastTimestamp = currentTimestamp;
    *timestamp = currentTimestamp;
    update = 1;
    if (mode & WAKE_UP_SENSOR) {
        sensor_data.accel_w_timestamp = currentTimestamp;
        sensor_data.accel_w_last_timestamp = lastTimestamp;
    } else {
        sensor_data.accel_timestamp = currentTimestamp;
        sensor_data.accel_last_timestamp = lastTimestamp;
    }

    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: cal accel out: %f %f %f - %d -%d", values[0], values[1],
                            values[2], *accuracy, update);

    return update;
}

int mpl_get_sensor_type_gyroscope(float *values, int8_t *accuracy, int64_t *timestamp, int mode)
{
    int update = 0;
    int gyro_scaled[3];
    int gyro_temp[3];
    long long currentTimestamp;
    long long lastTimestamp;

    dmp3_gyro_cal_lib_get_gyro_calibrated_only(NULL, gyro_temp);
    inv_convert_to_body_with_scale(sensor_data.gyro_matrix_scalar,
           sensor_data.gyro_scale << 1, gyro_temp, gyro_scaled);

    *accuracy = (int8_t) dmp3_gyro_cal_lib_get_gyro_accuracy((void *)gyro_storage);
    values[0] = (float) gyro_scaled[0] * GYRO_CONVERSION;
    values[1] = (float) gyro_scaled[1] * GYRO_CONVERSION;
    values[2] = (float) gyro_scaled[2] * GYRO_CONVERSION;

    if (mode & WAKE_UP_SENSOR) {
        currentTimestamp = sensor_data.gyro_w_timestamp;
        lastTimestamp = sensor_data.gyro_w_last_timestamp;
    } else {
        currentTimestamp = sensor_data.gyro_timestamp;
        lastTimestamp = sensor_data.gyro_last_timestamp;
    }

    if (lastTimestamp == 0){
        lastTimestamp = currentTimestamp;
        if (mode & WAKE_UP_SENSOR) {
            sensor_data.gyro_w_last_timestamp = lastTimestamp;
        } else {
            sensor_data.gyro_last_timestamp = lastTimestamp;
        }
    }
    lastTimestamp = currentTimestamp;
    *timestamp = currentTimestamp;
    update = 1;
    if (mode & WAKE_UP_SENSOR) {
        sensor_data.gyro_w_timestamp = currentTimestamp;
        sensor_data.gyro_w_last_timestamp = lastTimestamp;
    } else {
        sensor_data.gyro_timestamp = currentTimestamp;
        sensor_data.gyro_last_timestamp = lastTimestamp;
    }

    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: cal gyro out mode=%d: %f %f %f - %d - %d", mode, values[0], values[1],
                            values[2], *accuracy, update);
    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: cal gyro bias out: %f %f %f", sensor_data.gyro_bias[0] * GYRO_CONVERSION, sensor_data.gyro_bias[1] * GYRO_CONVERSION,
                            sensor_data.gyro_bias[2] * GYRO_CONVERSION);
    return update;
}
int mpl_get_sensor_type_eis_gyroscope(float *values, int8_t *accuracy, int64_t *timestamp, int mode)
{

    int update = 0;
    int gyro_scaled[3];
    int gyro_temp[3];

    dmp3_gyro_cal_lib_get_gyro_calibrated_only(NULL, gyro_temp);
    inv_convert_to_body_with_scale(sensor_data.gyro_matrix_scalar,
           sensor_data.gyro_scale << 1, gyro_temp, gyro_scaled);
    *accuracy = (int8_t) dmp3_gyro_cal_lib_get_gyro_accuracy((void *)gyro_storage);

    values[0] = (float) gyro_scaled[0] * GYRO_CONVERSION;
    values[1] = (float) gyro_scaled[1] * GYRO_CONVERSION;
    values[2] = (float) gyro_scaled[2] * GYRO_CONVERSION;
    values[3] = sensor_data.eis_calibrated[3];

    *timestamp = sensor_data.eis_gyro_timestamp;
    update = 1;

    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: cal gyro out mode=%d: %f %f %f - %d - %d", mode, values[0], values[1],
                            values[2], *accuracy, update);
    return update;
}
int mpl_get_sensor_type_gyroscope_raw(float *values, int8_t *accuracy, int64_t *timestamp, int mode)
{
    float scale = 2000.0 / (1<<15);
    scale *= 3.1415926 / 180.0;

    int update = 0;
    *accuracy = 0;
    long long currentTimestamp;
    long long lastTimestamp;
    int gyro_temp[3];
    int gyro_scaled[3];
    int bias_scaled[3];

    dmp3_gyro_cal_lib_get_gyro_calibrated_only(NULL, gyro_temp);
    inv_convert_to_body_with_scale(sensor_data.gyro_matrix_scalar,
           sensor_data.gyro_scale << 1, gyro_temp, gyro_scaled);

    inv_convert_to_body(sensor_data.gyro_matrix_scalar,
           sensor_data.gyro_bias, bias_scaled);

    values[3] = (float) ((bias_scaled[0] >> 16) * scale);
    values[4] = (float) ((bias_scaled[1] >> 16) * scale);
    values[5] = (float) ((bias_scaled[2] >> 16) * scale);

    values[0] = (gyro_scaled[0] * GYRO_CONVERSION) + values[3];
    values[1] = (gyro_scaled[1] * GYRO_CONVERSION) + values[4];
    values[2] = (gyro_scaled[2] * GYRO_CONVERSION) + values[5];

    if (mode & WAKE_UP_SENSOR) {
        currentTimestamp = sensor_data.gyro_raw_w_timestamp;
        lastTimestamp = sensor_data.gyro_raw_w_last_timestamp;
    } else {
        currentTimestamp = sensor_data.gyro_raw_timestamp;
        lastTimestamp = sensor_data.gyro_raw_last_timestamp;
    }

    if (lastTimestamp == 0){
        lastTimestamp = currentTimestamp;
        if (mode & WAKE_UP_SENSOR) {
            sensor_data.gyro_raw_w_last_timestamp = lastTimestamp;
        } else {
            sensor_data.gyro_raw_last_timestamp = lastTimestamp;
        }
    }
    lastTimestamp = currentTimestamp;
    *timestamp = currentTimestamp;
    update = 1;
    if (mode & WAKE_UP_SENSOR) {
        sensor_data.gyro_raw_w_timestamp = currentTimestamp;
        sensor_data.gyro_raw_w_last_timestamp = lastTimestamp;
    } else {
        sensor_data.gyro_raw_timestamp = currentTimestamp;
        sensor_data.gyro_raw_last_timestamp = lastTimestamp;
    }

    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: raw gyro out: %f %f %f - %lld - %d - %d", values[0], values[1],
                            values[2], *timestamp, *accuracy, update);
    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: raw gyro bias out: %d %d %d", sensor_data.gyro_bias[0], sensor_data.gyro_bias[1],
                            sensor_data.gyro_bias[2]);
    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: raw gyro bias out scaled: %f %f %f", values[3], values[4],
                            values[5]);
    return update;
}

int mpl_get_sensor_type_rotation_vector(float *values, int8_t *accuracy, int64_t *timestamp, int mode)
{
    int update = 0;
    long long currentTimestamp;
    long long lastTimestamp;

	if(sensor_data.nine_axis_data[0] >= 0){
		values[0] = (float) sensor_data.nine_axis_data[1] * INV_TWO_POWER_NEG_30;
		values[1] = (float) sensor_data.nine_axis_data[2] * INV_TWO_POWER_NEG_30;
		values[2] = (float) sensor_data.nine_axis_data[3] * INV_TWO_POWER_NEG_30;
		values[3] = (float) sensor_data.nine_axis_data[0] * INV_TWO_POWER_NEG_30;
	}else{
		values[0] = -(float) sensor_data.nine_axis_data[1] * INV_TWO_POWER_NEG_30;
		values[1] = -(float) sensor_data.nine_axis_data[2] * INV_TWO_POWER_NEG_30;
		values[2] = -(float) sensor_data.nine_axis_data[3] * INV_TWO_POWER_NEG_30;
		values[3] = -(float) sensor_data.nine_axis_data[0] * INV_TWO_POWER_NEG_30;
	}
    values[4] = (float) dmp3_9axis_lib_get_9axis_heading_accuracy((void *)nineaxis_storage) * INV_TWO_POWER_NEG_16;

    if (mode & WAKE_UP_SENSOR) {
        currentTimestamp = sensor_data.quat9_w_timestamp;
        lastTimestamp = sensor_data.quat9_w_last_timestamp;
    } else {
        currentTimestamp = sensor_data.quat9_timestamp;
        lastTimestamp = sensor_data.quat9_last_timestamp;
    }

    if (lastTimestamp == 0)
        lastTimestamp = currentTimestamp;

    lastTimestamp = currentTimestamp;
    *timestamp = currentTimestamp;
    update = 1;

    if (mode & WAKE_UP_SENSOR) {
        sensor_data.quat9_w_timestamp = currentTimestamp;
        sensor_data.quat9_w_last_timestamp = lastTimestamp;
    } else {
        sensor_data.quat9_timestamp = currentTimestamp;
        sensor_data.quat9_last_timestamp = lastTimestamp;
    }

    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: rv out: %f %f %f %f %f - %lld - %d", values[0], values[1],
                            values[2], values[3], values[4], *timestamp, update);
    return update;
}

int mpl_get_sensor_type_game_rotation_vector(float *values, int8_t *accuracy, int64_t *timestamp, int mode)
{
    int update = 0;
    long long currentTimestamp;
    long long lastTimestamp;

    *accuracy = 0;
    values[0] = (float) sensor_data.six_axis_data_scaled[1] * INV_TWO_POWER_NEG_30;
    values[1] = (float) sensor_data.six_axis_data_scaled[2] * INV_TWO_POWER_NEG_30;
    values[2] = (float) sensor_data.six_axis_data_scaled[3] * INV_TWO_POWER_NEG_30;
    values[3] = (float) sensor_data.six_axis_data_scaled[0] * INV_TWO_POWER_NEG_30;

    if (mode & WAKE_UP_SENSOR) {
        currentTimestamp = sensor_data.quat6_w_timestamp;
        lastTimestamp = sensor_data.quat6_w_last_timestamp;
    } else {
        currentTimestamp = sensor_data.quat6_timestamp;
        lastTimestamp = sensor_data.quat6_last_timestamp;
    }

    if (lastTimestamp == 0)
        lastTimestamp = currentTimestamp;

    lastTimestamp = currentTimestamp;
    *timestamp = currentTimestamp;
    update = 1;

    if (mode & WAKE_UP_SENSOR) {
        sensor_data.quat6_w_timestamp = currentTimestamp;
        sensor_data.quat6_w_last_timestamp = lastTimestamp;
    } else {
        sensor_data.quat6_timestamp = currentTimestamp;
        sensor_data.quat6_last_timestamp = lastTimestamp;
    }

    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: grv out2: %f %f %f %f - %lld - %d", values[0], values[1],
                            values[2], values[3], *timestamp, update);
    return update;
}

int mpl_get_sensor_type_geomagnetic_rotation_vector(float *values, int8_t *accuracy, int64_t *timestamp, int mode)
{
    int update = 0;
    long long lastTimestamp;
    long long currentTimestamp;

    values[0] = (float) sensor_data.six_axis_compass_data[1] * INV_TWO_POWER_NEG_30;
    values[1] = (float) sensor_data.six_axis_compass_data[2] * INV_TWO_POWER_NEG_30;
    values[2] = (float) sensor_data.six_axis_compass_data[3] * INV_TWO_POWER_NEG_30;
    values[3] = (float) sensor_data.six_axis_compass_data[0] * INV_TWO_POWER_NEG_30;
    values[4] = (float) dmp3_geomag_lib_get_geomag_heading_accuracy((void *)geomag_storage) * INV_TWO_POWER_NEG_16;

    if (mode & WAKE_UP_SENSOR) {
        currentTimestamp = sensor_data.quat6_compass_w_timestamp;
        lastTimestamp = sensor_data.quat6_compass_w_last_timestamp;
    } else {
        currentTimestamp = sensor_data.quat6_compass_timestamp;
        lastTimestamp = sensor_data.quat6_compass_last_timestamp;
    }

    if (lastTimestamp == 0)
        lastTimestamp = currentTimestamp;

    lastTimestamp = currentTimestamp;
    *timestamp = currentTimestamp;
    update = 1;

    if (mode & WAKE_UP_SENSOR) {
        sensor_data.quat6_compass_w_timestamp = currentTimestamp;
        sensor_data.quat6_compass_w_last_timestamp = lastTimestamp;
    } else {
        sensor_data.quat6_compass_timestamp = currentTimestamp;
        sensor_data.quat6_compass_last_timestamp = lastTimestamp;
    }

    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: gmrv out: %f %f %f %f - %lld - %d", values[0], values[1],
                            values[2], values[3], *timestamp, update);
    return update;
}

int mpl_get_sensor_type_linear_acceleration(float *values, int8_t *accuracy,
                                            int64_t *timestamp, int mode)
{
    int gravity[3], accel[3];
    int accel_scaled[3];
    int accel_temp[3], accel_local[3];
    int update = 0;
    long long currentTimestamp;
    long long lastTimestamp;

    *accuracy = (int8_t) dmp3_accel_cal_lib_get_accel_accuracy((void *)accel_storage);
    accel_local[0] = (sensor_data.accel_data[0] << sensor_data.accel_full_scale);
    accel_local[1] = (sensor_data.accel_data[1] << sensor_data.accel_full_scale);
    accel_local[2] = (sensor_data.accel_data[2] << sensor_data.accel_full_scale);

    memcpy(accel, accel_local, sizeof(accel_local));
    inv_get_gravity_6x(gravity);
    inv_convert_to_body(sensor_data.accel_matrix_scalar, accel, accel_temp);

    accel_scaled[0] = accel_temp[0] >> 9; // (1<<2) is Accel Scale (1>>11) is DMP scale
    accel_scaled[1] = accel_temp[1] >> 9;
    accel_scaled[2] = accel_temp[2] >> 9;

    accel_scaled[0] -= (gravity[0] >> 14);
    accel_scaled[1] -= (gravity[1] >> 14);
    accel_scaled[2] -= (gravity[2] >> 14);

    values[0] = accel_scaled[0] * GRAVITY_CONVERSION;
    values[1] = accel_scaled[1] * GRAVITY_CONVERSION;
    values[2] = accel_scaled[2] * GRAVITY_CONVERSION;

    if (mode & WAKE_UP_SENSOR) {
        currentTimestamp = sensor_data.la_w_timestamp;
        lastTimestamp = sensor_data.la_w_last_timestamp;
    } else {
        currentTimestamp = sensor_data.la_timestamp;
        lastTimestamp = sensor_data.la_last_timestamp;
    }

    if (lastTimestamp == 0)
        lastTimestamp = currentTimestamp;

    lastTimestamp = currentTimestamp;
    *timestamp = currentTimestamp;
    update = 1;

    if (mode & WAKE_UP_SENSOR) {
        sensor_data.la_w_timestamp = currentTimestamp;
        sensor_data.la_w_last_timestamp = lastTimestamp;
    } else {
        sensor_data.la_timestamp = currentTimestamp;
        sensor_data.la_last_timestamp = lastTimestamp;
    }
    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: la out mode=%d: %f %f %f - %lld - %d", mode, values[0], values[1],
                            values[2], *timestamp, update);
    return update;
}

int mpl_get_sensor_type_gravity(float *values, int8_t *accuracy,
                                            int64_t *timestamp, int mode)
{
    int gravity[3];
    int update = 0;
    long long currentTimestamp;
    long long lastTimestamp;

    *accuracy = (int8_t) dmp3_accel_cal_lib_get_accel_accuracy((void *)accel_storage);

    inv_get_gravity_6x(gravity);
    values[0] = (gravity[0] >> 14) * GRAVITY_CONVERSION;
    values[1] = (gravity[1] >> 14) * GRAVITY_CONVERSION;
    values[2] = (gravity[2] >> 14) * GRAVITY_CONVERSION;

    if (mode & WAKE_UP_SENSOR) {
        currentTimestamp = sensor_data.gravity_w_timestamp;
        lastTimestamp = sensor_data.gravity_w_last_timestamp;
    } else {
        currentTimestamp = sensor_data.gravity_timestamp;
        lastTimestamp = sensor_data.gravity_last_timestamp;
    }

    if (lastTimestamp == 0)
        lastTimestamp = currentTimestamp;

    lastTimestamp = currentTimestamp;
    *timestamp = currentTimestamp;
    update = 1;

    if (mode & WAKE_UP_SENSOR) {
        sensor_data.gravity_w_timestamp = currentTimestamp;
        sensor_data.gravity_w_last_timestamp = lastTimestamp;
    } else {
        sensor_data.gravity_timestamp = currentTimestamp;
        sensor_data.gravity_last_timestamp = lastTimestamp;
    }

    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: grav out mode=%d: %f %f %f - %lld - %d", mode, values[0], values[1],
                            values[2], *timestamp, update);
    return update;
}

int mpl_get_sensor_type_orientation(float *values, int8_t *accuracy,
                                            int64_t *timestamp, int mode)
{
    int update = 0;
    long long currentTimestamp;
    long long lastTimestamp;

    *accuracy = (int8_t) dmp3_compass_cal_lib_get_compass_accuracy((void *)compass_storage);

    if (mode & WAKE_UP_SENSOR) {
        currentTimestamp = sensor_data.or_w_timestamp;
        lastTimestamp = sensor_data.or_w_last_timestamp;
    } else {
        currentTimestamp = sensor_data.or_timestamp;
        lastTimestamp = sensor_data.or_last_timestamp;
    }

    if (lastTimestamp == 0)
        lastTimestamp = currentTimestamp;

    lastTimestamp = currentTimestamp;
    *timestamp = currentTimestamp;
    update = 1;

    if (mode & WAKE_UP_SENSOR) {
        sensor_data.or_w_timestamp = currentTimestamp;
        sensor_data.or_w_last_timestamp = lastTimestamp;
    } else {
        sensor_data.or_timestamp = currentTimestamp;
        sensor_data.or_last_timestamp = lastTimestamp;
    }

    google_orientation(values);

    return update;
}

short tilt_prev_status = 0;
int mpl_get_sensor_type_tilt(float *values, int8_t *accuracy,
                                            int64_t *timestamp, int mode)
{
    int update = 0;
    short bac_status = 0;
    short tilt_status = 0;

    values[0] = 1.f;
    values[1] = 0.f;
    values[2] = 0.f;
    *accuracy = 0;
    *timestamp = sensor_data.accel_timestamp;

    bac_status = dmp3_bac_lib_get_state((void *) bac_storage);
    tilt_status = bac_status & TILT;
    if(tilt_status && !tilt_prev_status){
        update = 1;
    }
    tilt_prev_status = tilt_status;

    return update;
}

int mpl_get_sensor_type_pickup(float *values, int8_t *accuracy,
                                            int64_t *timestamp, int mode)
{
    int update = 0;
    int detected = 0;

    values[0] = 1.f;
    values[1] = 0.f;
    values[2] = 0.f;
    *accuracy = 0;
    *timestamp = sensor_data.accel_timestamp;

    detected = dmp3_flip_pickup_lib_get_state((void *) pickup_storage);
    if(detected)
        update = 1;

    return update;
}

int mpl_get_sensor_type_step_detector(float *values, int8_t *accuracy,
                                            int64_t *timestamp, int mode)
{
    int update = 0;
    int detected = 0;
    long long lastTimestamp;

    values[0] = 1.f;
    values[1] = 0.f;
    values[2] = 0.f;
    *accuracy = 0;
    *timestamp = sensor_data.accel_timestamp;

    detected = dmp3_pedometer_lib_get_pedometer_event((void *) pedometer_storage);
    if(detected)
        update = 1;
    if(update){
        if (mode & WAKE_UP_SENSOR) {
            lastTimestamp = sensor_data.sd_w_last_timestamp;
        } else {
            lastTimestamp = sensor_data.sd_last_timestamp;
        }
        if (*timestamp == lastTimestamp)
            update = 0;
        if (mode & WAKE_UP_SENSOR) {
            sensor_data.sd_w_last_timestamp = *timestamp;
        } else {
            sensor_data.sd_last_timestamp = *timestamp;
        }
    }

    return update;
}

int mpl_get_sensor_type_step_counter(uint64_t *value, int8_t *accuracy,
                                            int64_t *timestamp, int mode)
{
    int update = 0;
    int step = 0;

    step = dmp3_pedometer_lib_pedometer_steps((void *) pedometer_storage);
    *value = step;
    *accuracy = 0;
    *timestamp = sensor_data.accel_timestamp;
    update = 1;
    return update;
}

void mpl_set_sample_rate(int sample_rate_us, int id)
{
    int effective_rate = get_effective_sample_rate(sample_rate_us);
    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: id: %d effective rate: %d", id, effective_rate);

    switch(id) {
        case ID_GY:
        sensor_data.gyro_sample_rate = effective_rate;
        set_gyro_sample_rate(sample_rate_us);
        break;
        case ID_RG:
        sensor_data.gyro_sample_rate = effective_rate;
        break;
        case ID_A:
        sensor_data.accel_sample_rate = effective_rate;
        set_accel_sample_rate(sample_rate_us);
        break;
        case ID_M:
        sensor_data.compass_sample_rate = effective_rate;
        set_compass_sample_rate(sample_rate_us);
        break;
        case ID_RM:
        sensor_data.compass_sample_rate = effective_rate;
        set_compass_sample_rate(sample_rate_us);
        break;
        case ID_O:
        sensor_data.or_sample_rate = effective_rate;
        break;
        case ID_RV:
        sensor_data.quat9_sample_rate = effective_rate;
        break;
        case ID_GRV:
        sensor_data.quat6_sample_rate = effective_rate;
        break;
        case ID_LA:
        sensor_data.la_sample_rate = effective_rate;
        break;
        case ID_GR:
        sensor_data.gravity_sample_rate = effective_rate;
        break;
        case ID_GMRV:
        sensor_data.quat6_compass_sample_rate = effective_rate;
        break;
        case ID_GYW:
        sensor_data.gyro_w_sample_rate = effective_rate;
        set_gyro_sample_rate(sample_rate_us);
        break;
        case ID_RGW:
        sensor_data.gyro_w_sample_rate = effective_rate;
        break;
        case ID_AW:
        sensor_data.accel_w_sample_rate = effective_rate;
        set_accel_sample_rate(sample_rate_us);
        break;
        case ID_MW:
        sensor_data.compass_w_sample_rate = effective_rate;
        set_compass_sample_rate(sample_rate_us);
        break;
        case ID_RMW:
        sensor_data.compass_w_sample_rate = effective_rate;
        set_compass_sample_rate(sample_rate_us);
        break;
        case ID_OW:
        sensor_data.or_w_sample_rate = effective_rate;
        break;
        case ID_RVW:
        sensor_data.quat9_w_sample_rate = effective_rate;
        break;
        case ID_GRVW:
        sensor_data.quat6_sample_rate = effective_rate;
        break;
        case ID_LAW:
        sensor_data.la_w_sample_rate = effective_rate;
        break;
        case ID_GRW:
        sensor_data.gravity_w_sample_rate = effective_rate;
        break;
        case ID_GMRVW:
        sensor_data.quat6_compass_w_sample_rate = effective_rate;
        break;
        case ID_P:
        case ID_SC:
        case ID_T:
        case ID_PICK:
        case ID_PW:
        case ID_SCW:
        sensor_data.gesture_algo_sample_rate = effective_rate;
        break;
    }
}

int get_effective_sample_rate(int sample_rate_us)
{
    int rateInHertz = (int)(1000000LL /sample_rate_us);

    switch(rateInHertz) {
        case 5:
            return 200000;
        case 15:
            return 66666;
        case 55:
            return 18181;
        case 70:
            return 13333;
        case 110:
            return 9090;
        case 220:
            return 4545;
        default:
            return sample_rate_us;
    }
}

void set_compass_sample_rate(int sample_rate_us)
{
    int rateInHertz = (int)(1000000LL /sample_rate_us);
    dmp3_compass_cal_lib_set_compass_rate(rateInHertz);

    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: set compass rate: %d", rateInHertz);

}

void set_accel_sample_rate(int sample_rate_us)
{
    int rateInHertz = (int)(1000000LL /sample_rate_us);
    acc_rate accelRate= ACC_RATE_50;

    switch(rateInHertz) {
        case 5:
            accelRate = ACC_RATE_5;
            break;
        case 15:
            accelRate = ACC_RATE_15;
            break;
        case 50:
            accelRate = ACC_RATE_50;
            break;
        case 100:
            accelRate = ACC_RATE_102;
            break;
        case 200:
            accelRate = ACC_RATE_225;
        break;
    }
    dmp3_accel_cal_lib_set_accel_rate(accelRate);
    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: set accel rate: %d %d Hertz", accelRate, rateInHertz);
}

void set_gyro_sample_rate(int sample_rate_us)
{
    int rateInHertz = (int)(1000000LL /sample_rate_us);
    long long gyroScaleFactor;

    gyroScaleFactor = sensor_data.gyro_scale_factor_base * (1000/rateInHertz);
    dmp3_gyro_cal_lib_set_gyro_scale(gyroScaleFactor);
    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: set gyro rate: %d Hertz gyro_sf=%lld", rateInHertz, gyroScaleFactor);
}

void mpl_set_accel_orientation_and_scale(int orientation, int sensitivity)
{
    sensor_data.accel_matrix_scalar = orientation;
    sensor_data.accel_scale = sensitivity;
    dmp3_accel_cal_lib_set_accel_scale(12);
    sensor_data.accel_scale = 10; //11;

    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: set accel matrix: %d  scale: %d",
                    orientation, sensitivity);
}

void mpl_set_gyro_orientation_and_scale(int orientation, int sensitivity)
{
    sensor_data.gyro_matrix_scalar = orientation;
    sensor_data.gyro_scale = sensitivity;
    dmp3_gyro_cal_lib_set_accel_scale(12);

    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: set gyro matrix: %d  scale: %d",
                    orientation, sensitivity);
}

void mpl_set_compass_orientation_and_scale(int orientation, int sensitivity, int *softIron)
{
    sensor_data.compass_matrix_scalar = orientation;
    sensor_data.compass_scale = sensitivity;

    for (int i=0; i<9; i++)
        sensor_data.compass_soft_iron[i] = softIron[i];

    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: set compass soft iron: %d %d %d", sensor_data.compass_soft_iron[0],
                    sensor_data.compass_soft_iron[4], sensor_data.compass_soft_iron[8]);
    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: set compass matrix: %d  scale: %d",
                    orientation, sensitivity);
}

void mpl_set_compass_orientation_and_scale1(int orientation, int scale, int *sensitivity, int *softIron)
{
    sensor_data.compass_matrix_scalar = orientation;
//     sensor_data.compass_scale = sensitivity;
    sensor_data.compass_scale = scale;

    for (int i=0; i<3; i++)
		sensor_data.compass_sens[i] = sensitivity[i];

    for (int i=0; i<9; i++)
        sensor_data.compass_soft_iron[i] = softIron[i];

    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: set compass soft iron: %d %d %d", sensor_data.compass_soft_iron[0],
                    sensor_data.compass_soft_iron[4], sensor_data.compass_soft_iron[8]);
    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: set compass matrix: %d  scale: %d sens_x: %d sens_y: %d sens_z: %d ",
                    orientation, scale, sensitivity[0], sensitivity[1], sensitivity[2]);
}
/** Pick the smallest non-negative number. Priority to td1 on equal
* If both are negative, return the largest.
*/
static int inv_pick_best_time_difference(int td1, int td2)
{
    if (td1 >= 0) {
        if (td2 >= 0) {
            if (td1 <= td2) {
                // td1
                return 0;
            } else {
                // td2
                return 1;
            }
        } else {
            // td1
            return 0;
        }
    } else if (td2 >= 0) {
        // td2
        return 1;
    } else {
        // Both are negative
        if (td1 >= td2) {
            // td1
            return 0;
        } else {
            // td2
            return 1;
        }
    }
}

int inv_get_cal_accel(int *data)
{
    int accel_data_scaled[3];

    if (data == NULL)
	return -1;

    data[0] = sensor_data.accel_data[0]>>(sensor_data.accel_scale + 1);
    data[1] = sensor_data.accel_data[1]>>(sensor_data.accel_scale + 1);
    data[2] = sensor_data.accel_data[2]>>(sensor_data.accel_scale + 1);

    return 0;
}

int inv_get_gravity_6x(int *data)
{
    data[0] =
        inv_q29_mult(sensor_data.six_axis_data_scaled[1], sensor_data.six_axis_data_scaled[3]) - inv_q29_mult(sensor_data.six_axis_data_scaled[2], sensor_data.six_axis_data_scaled[0]);
    data[1] =
        inv_q29_mult(sensor_data.six_axis_data_scaled[2], sensor_data.six_axis_data_scaled[3]) + inv_q29_mult(sensor_data.six_axis_data_scaled[1], sensor_data.six_axis_data_scaled[0]);
    data[2] =
        (inv_q29_mult(sensor_data.six_axis_data_scaled[3], sensor_data.six_axis_data_scaled[3]) + inv_q29_mult(sensor_data.six_axis_data_scaled[0], sensor_data.six_axis_data_scaled[0])) -
        1073741824L;
    return 0;
}

void inv_get_rotation(float r[3][3])
{
    int rot[9], quat_9_axis[4];
    float conv = 1.f / (1L<<30);

    for(int i=0; i<4; i++)
        quat_9_axis[i] = sensor_data.nine_axis_data[i];

    inv_quaternion_to_rotation(quat_9_axis, rot);
    r[0][0] = rot[0]*conv;
    r[0][1] = rot[1]*conv;
    r[0][2] = rot[2]*conv;
    r[1][0] = rot[3]*conv;
    r[1][1] = rot[4]*conv;
    r[1][2] = rot[5]*conv;
    r[2][0] = rot[6]*conv;
    r[2][1] = rot[7]*conv;
    r[2][2] = rot[8]*conv;
}

void google_orientation(float *g)
{
    float rad2deg = (float)(180.0 / M_PI);
    float R[3][3];

    inv_get_rotation(R);

    g[0] = atan2f(-R[1][0], R[0][0]) * rad2deg;
    g[1] = atan2f(-R[2][1], R[2][2]) * rad2deg;
    g[2] = asinf ( R[2][0])          * rad2deg;
    if (g[0] < 0)
        g[0] += 360;
}

int mpl_get_mpl_gyro_bias(int *bias)
{
    if (bias != NULL)
        memcpy(bias, sensor_data.gyro_bias,
               sizeof(sensor_data.gyro_bias));
    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: get gyro bias %d %d %d", bias[0], bias[1], bias[2]);
    return 1;
}

int mpl_set_mpl_gyro_bias(int *bias)
{
    dmp3_gyro_cal_lib_set_gyro_bias(bias);
    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: set gyro bias %d %d %d", bias[0], bias[1], bias[2]);
    return 1;
}

int mpl_get_mpl_accel_bias(int *bias)
{
    if (bias != NULL)
        memcpy(bias, sensor_data.accel_bias,
               sizeof(sensor_data.accel_bias));
    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: get accel bias %d %d %d", bias[0], bias[1], bias[2]);
    return 1;
}

int mpl_set_mpl_accel_bias(int *bias)
{
    dmp3_accel_cal_lib_set_accel_bias(bias);
    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: set accel bias %d %d %d", bias[0], bias[1], bias[2]);
    return 1;
}

int mpl_get_mpl_compass_bias(int *bias)
{
    if (bias != NULL)
        memcpy(bias, sensor_data.compass_bias,
               sizeof(sensor_data.compass_bias));
    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: get compass bias %d %d %d", bias[0], bias[1], bias[2]);
    return 1;
}

int mpl_set_mpl_compass_bias(int *bias, int accuracy)
{
    ALOGD_IF(BUILDER_VERBOSE, "MPL Builder: set compass bias %d %d %d", bias[0], bias[1], bias[2]);
    return 1;
}

void mpl_set_lib_version(int version_number)
{
    setLibVersion(version_number);
}

int mpl_get_lib_version()
{
    return getLibVersion();
}

