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

#ifndef ANDROID_MPL_ADAPTER_H
#define ANDROID_MPL_ADAPTER_H

#include <stdint.h>

enum {
    dmp         = 0x80,
    mpl_accel   = 0x01,
    mpl_gyro    = 0x02,
    mpl_compass = 0x04,
    mpl_quat    = 0x08,
    mpl_gesture = 0x10,
    mpl         = 0x1F,
};

void set_adapter(int mode);
void init_cal_lib();

int adapter_gyro_reset_timestamp();
int adapter_accel_reset_timestamp();
int adapter_compass_reset_timestamp();
int adapter_gesture_reset_timestamp();

int adapter_build_temp(int *gyro, int temp);
int adapter_build_pressure(int pressure, int stauts, long long timestamp);
int adapter_build_gyro_dmp(int *gyro, int status, long long timestamp);
int adapter_build_eis_gyro(int *eis_gyro, int status, long long timestamp);
int adapter_build_eis_authentication(int *eis_authentication, int status, long long timestamp);
int adapter_build_accel_dmp(int *accel, int status, long long timestamp);
int adapter_build_compass(int *compass, int status, long long timestamp);
int adapter_build_quat(int *compass, int status, long long timestamp);
int adapter_build_gyro(int *gyro, int status, long long timestamp);
int adapter_build_accel(int *accel, int status, long long timestamp);

int adapter_get_sensor_type_orientation(float *values, int8_t *accuracy, int64_t *timestamp, int mode);
int adapter_get_sensor_type_accelerometer(float *values, int8_t *accuracy, int64_t *timestamp, int mode);
int adapter_get_sensor_type_accelerometer_custom(float *values, int8_t *accuracy, int64_t *timestamp, int mode);
int adapter_get_sensor_type_gyroscope(float *values, int8_t *accuracy, int64_t *timestamp, int mode);
int adapter_get_sensor_type_gyroscope_raw(float *values, int8_t *accuracy, int64_t *timestamp, int mode);
int adapter_get_sensor_type_gyroscope_raw_custom(float *values, int8_t *accuracy, int64_t *timestamp, int mode);
int adapter_get_sensor_type_eis_gyroscope(float *values, int8_t *accuracy, int64_t *timestamp, int mode);
int adapter_get_sensor_type_eis_authentication(float *values, int8_t *accuracy, int64_t *timestamp, int mode);
int adapter_get_sensor_type_magnetic_field(float *values, int8_t *accuracy, int64_t *timestamp, int mode);
int adapter_get_sensor_type_magnetic_field_raw(float *values, int8_t *accuracy, int64_t *timestamp, int mode);
int adapter_get_sensor_type_magnetic_field_raw_custom(float *values, int8_t *accuracy, int64_t *timestamp, int mode);
int adapter_get_sensor_type_rotation_vector(float *values, int8_t *accuracy, int64_t *timestamp, int mode);
int adapter_get_sensor_type_linear_acceleration(float *values, int8_t *accuracy, int64_t *timestamp, int mode);
int adapter_get_sensor_type_game_rotation_vector(float *values, int8_t *accuracy, int64_t *timestamp, int mode);
int adapter_get_sensor_type_geomagnetic_rotation_vector(float *values, int8_t *accuracy, int64_t *timestamp, int mode);
int adapter_get_sensor_type_gravity(float *values, int8_t *accuracy, int64_t *timestamp, int mode);
int adapter_get_sensor_type_tilt(float *values, int8_t *accuracy, int64_t *timestamp, int mode);
int adapter_get_sensor_type_pickup(float *values, int8_t *accuracy, int64_t *timestamp, int mode);
int adapter_get_sensor_type_step_detector(float *values, int8_t *accuracy, int64_t *timestamp, int mode);
int adapter_get_sensor_type_step_counter(uint64_t *value, int8_t *accuracy, int64_t *timestamp, int mode);
int adapter_accel_set_full_scale(int full_scale);
int adapter_gyro_set_scale_factor_base(int internal_sampling_rate);

void adapter_set_sample_rate(int sample_rate_us, int id);
void adapter_set_gyro_sample_rate(int sample_rate_us, int id);
void adapter_set_accel_sample_rate(int sample_rate_us, int id);
void adapter_set_compass_sample_rate(int sample_rate_us, int id);
void adapter_set_linear_acceleration_sample_rate(int sample_rate_us, int id);
void adapter_set_orientation_sample_rate(int sample_rate_us, int id);
void adapter_set_gravity_sample_rate(int sample_rate_us, int id);
void adapter_set_rotation_vector_sample_rate(int sample_rate_us, int id);
void adapter_set_geomagnetic_rotation_vector_sample_rate(int sample_rate_us, int id);
void adapter_set_rotation_vector_6_axis_sample_rate(int sample_rate_us, int id);

void adapter_set_gyro_orientation_and_scale(int orientation, int sensitivity);
void adapter_set_accel_orientation_and_scale(int orientation, int sensitivity);
void adapter_set_compass_orientation_and_scale(int orientation, int sensitivity, int *softIron);
void adapter_set_compass_orientation_and_scale1(int orientation, int scale, int *sensitivity, int *softIron); //

void adapter_get_mpl_gyro_bias(int *bias, int *temperature);
void adapter_set_mpl_gyro_bias(int *bias, int accuracy);
void adapter_get_mpl_accel_bias(int *bias, int *temperature);
void adapter_set_mpl_accel_bias(int *bias, int accuracy);
void adapter_get_mpl_compass_bias(int *bias);
void adapter_set_mpl_compass_bias(int *bias, int accuracy);

int adapter_get_version();
void adapter_set_version(int version_number);
int adapter_get_calibration_mode(char *ChipID);
int adapter_get_internal_sampling_rate(char *ChipID);

#endif //  ANDROID_MPL_ADAPTER_H
