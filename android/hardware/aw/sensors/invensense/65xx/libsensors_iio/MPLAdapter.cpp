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

#include <MPLAdapter.h>
#include <dirent.h>
#include <string.h>
#include <stdio.h>
#include <cutils/log.h>
#include "SensorBase.h"
#include <fcntl.h>
#include "data_builder.h"
#include "hal_outputs.h"
#include "sensors.h"
#if defined INV_LIBMOTION
#include "MPLBuilder.h"
#else
#include "MPLBuilderInt.h"
#endif

static int adapter_mode;

void set_adapter(int mode)
{
    adapter_mode = mode;

    init_cal_lib();
}

void init_cal_lib()
{
    if(adapter_mode != dmp) {
        init_mpl_cal_lib();
        ALOGI("Initialize MPL calibration library");
    }
}

int adapter_gyro_reset_timestamp(void)
{
    /* call the same API for all cases for now */
    if (adapter_mode == dmp)
        mpl_gyro_reset_timestamp();
    else if (adapter_mode & mpl_gyro)
        mpl_gyro_reset_timestamp();
    else
        mpl_gyro_reset_timestamp();

    return 0;
}

int adapter_accel_reset_timestamp(void)
{
    /* call the same API for all cases for now */
    if (adapter_mode == dmp)
        mpl_accel_reset_timestamp();
    else if (adapter_mode & mpl_accel)
        mpl_accel_reset_timestamp();
    else
        mpl_accel_reset_timestamp();

    return 0;
}

int adapter_accel_set_full_scale(int full_scale)
{
    mpl_set_accel_full_scale(full_scale);

    return 0;
}

int adapter_gyro_set_scale_factor_base(int internal_sampling_rate)
{
    mpl_set_gyro_scale_factor_base(internal_sampling_rate);
    return 0;
}

int adapter_compass_reset_timestamp(void)
{
    /* call the same API for all cases for now */
    if (adapter_mode == dmp)
        mpl_compass_reset_timestamp();
    else if (adapter_mode & mpl_compass)
        mpl_compass_reset_timestamp();
    else
        mpl_compass_reset_timestamp();

    return 0;
}

int adapter_gesture_reset_timestamp(void)
{
    if (adapter_mode & mpl_gesture)
        mpl_gesture_reset_timestamp();
    else
        return 0; // not using

    return 0;
}

int adapter_build_temp(int *gyro, int temp)
{
    return 0;
}

int adapter_build_pressure(int pressure, int status, long long timestamp)
{
    inv_build_pressure(pressure, status, timestamp);
    return 0;
}

int adapter_build_gyro_dmp(int *gyro, int status, long long timestamp)
{
    if (adapter_mode == dmp)
        inv_build_gyro_dmp(gyro, status, timestamp);
    else if (adapter_mode & mpl_gyro)
        mpl_build_gyro(gyro, status, timestamp);
    else
        inv_build_gyro_dmp(gyro, status, timestamp);

    return 0;
}

int adapter_build_eis_gyro(int *eis_gyro, int status, long long timestamp)
{
    if (adapter_mode == dmp)
        inv_build_eis_gyro(eis_gyro, status, timestamp);
    else if (adapter_mode & mpl_gyro)
        mpl_build_eis_gyro(eis_gyro, status, timestamp);
    else
        inv_build_eis_gyro(eis_gyro, status, timestamp);

    return 0;
}

int adapter_build_eis_authentication(int *eis_authentication, int status, long long timestamp)
{
    inv_build_eis_authentication(eis_authentication, status, timestamp);

    return 0;
}

int adapter_build_accel_dmp(int *accel, int status, long long timestamp)
{
    if (adapter_mode == dmp)
        inv_build_accel_dmp(accel, status, timestamp);
    else if (adapter_mode & mpl_accel)
        mpl_build_accel(accel, status, timestamp);
    else
        inv_build_accel_dmp(accel, status, timestamp);

    return 0;
}

int adapter_build_compass(int *compass, int status, long long timestamp)
{
    if (adapter_mode == dmp)
        inv_build_compass(compass, status, timestamp);
    else if (adapter_mode & mpl_compass)
        mpl_build_compass(compass, status, timestamp);
    else
        inv_build_compass(compass, status, timestamp);

    return 0;
}

int adapter_build_quat(int *quat, int status, long long timestamp)
{
    if (adapter_mode == dmp)
        inv_build_quat(quat, status, timestamp);
    else if (adapter_mode & mpl_quat)
        return 0;
    else
        inv_build_quat(quat, status, timestamp);

    return 0;
}

int adapter_get_sensor_type_orientation(float *values, int8_t *accuracy, int64_t *timestamp, int mode)
{
    if (adapter_mode == dmp)
        return inv_get_sensor_type_orientation(values, accuracy, timestamp);
    else if (adapter_mode & mpl_quat)
        return mpl_get_sensor_type_orientation(values, accuracy, timestamp, mode);
    else
        return inv_get_sensor_type_orientation(values, accuracy, timestamp);
}

int adapter_get_sensor_type_accelerometer(float *values, int8_t *accuracy, int64_t *timestamp, int mode)
{
    if (adapter_mode == dmp)
        return inv_get_sensor_type_accelerometer(values, accuracy, timestamp);
    else if (adapter_mode & mpl_accel)
        return mpl_get_sensor_type_accelerometer(values, accuracy, timestamp, mode);
    else
        return inv_get_sensor_type_accelerometer(values, accuracy, timestamp);
}

int adapter_get_sensor_type_accelerometer_custom(float *values, int8_t *accuracy, int64_t *timestamp, int mode)
{
    return inv_get_sensor_type_accelerometer_custom(values, accuracy, timestamp);
}

int adapter_get_sensor_type_gyroscope(float *values, int8_t *accuracy, int64_t *timestamp, int mode)
{
    if (adapter_mode == dmp)
        return inv_get_sensor_type_gyroscope(values, accuracy, timestamp);
    else if (adapter_mode & mpl_gyro)
        return mpl_get_sensor_type_gyroscope(values, accuracy, timestamp, mode);
    else
        return inv_get_sensor_type_gyroscope(values, accuracy, timestamp);
}

int adapter_get_sensor_type_gyroscope_raw(float *values, int8_t *accuracy, int64_t *timestamp, int mode)
{
    if (adapter_mode == dmp)
        return inv_get_sensor_type_gyroscope_raw(values, accuracy, timestamp);
    else if (adapter_mode & mpl_gyro)
        return mpl_get_sensor_type_gyroscope_raw(values, accuracy, timestamp, mode);
    else
        return inv_get_sensor_type_gyroscope_raw(values, accuracy, timestamp);

}

int adapter_get_sensor_type_gyroscope_raw_custom(float *values, int8_t *accuracy, int64_t *timestamp, int mode)
{
    return inv_get_sensor_type_gyroscope_raw_custom(values, accuracy, timestamp);
}

int adapter_get_sensor_type_eis_gyroscope(float *values, int8_t *accuracy, int64_t *timestamp, int mode)
{
    if (adapter_mode == dmp)
        return inv_get_sensor_type_eis_gyroscope(values, accuracy, timestamp);
    else if (adapter_mode & mpl_gyro)
        return mpl_get_sensor_type_eis_gyroscope(values, accuracy, timestamp, mode);
    else
        return inv_get_sensor_type_eis_gyroscope(values, accuracy, timestamp);
}

int adapter_get_sensor_type_eis_authentication(float *values, int8_t *accuracy, int64_t *timestamp, int mode)
{
    return inv_get_sensor_type_eis_authentication(values, accuracy, timestamp);
}

int adapter_get_sensor_type_magnetic_field(float *values, int8_t *accuracy, int64_t *timestamp, int mode)
{
    if (adapter_mode == dmp)
        return inv_get_sensor_type_magnetic_field(values, accuracy, timestamp);
    else if (adapter_mode & mpl_compass)
        return mpl_get_sensor_type_magnetic_field(values, accuracy, timestamp, mode);
    else
        return inv_get_sensor_type_magnetic_field(values, accuracy, timestamp);
}

int adapter_get_sensor_type_magnetic_field_raw(float *values, int8_t *accuracy, int64_t *timestamp, int mode)
{
    if (adapter_mode == dmp)
        return inv_get_sensor_type_magnetic_field_raw(values, accuracy, timestamp);
    else if (adapter_mode & mpl_compass)
        return mpl_get_sensor_type_magnetic_field_raw(values, accuracy, timestamp, mode);
    else
        return inv_get_sensor_type_magnetic_field_raw(values, accuracy, timestamp);
}

int adapter_get_sensor_type_magnetic_field_raw_custom(float *values, int8_t *accuracy, int64_t *timestamp, int mode)
{
    return inv_get_sensor_type_magnetic_field_raw_custom(values, accuracy, timestamp);
}

int adapter_get_sensor_type_rotation_vector(float *values, int8_t *accuracy, int64_t *timestamp, int mode)
{
    if (adapter_mode == dmp)
        return inv_get_sensor_type_rotation_vector(values, accuracy, timestamp);
    else if (adapter_mode & mpl_quat)
        return mpl_get_sensor_type_rotation_vector(values, accuracy, timestamp, mode);
    else
        return inv_get_sensor_type_rotation_vector(values, accuracy, timestamp);
}

int adapter_get_sensor_type_linear_acceleration(float *values, int8_t *accuracy, int64_t *timestamp, int mode)
{
    int accel_cal[3] = {0, };

    if (adapter_mode & mpl_accel) {
	inv_get_cal_accel(accel_cal);
	inv_build_accel_dmp(accel_cal, INV_CALIBRATED, (long long)timestamp);
    }

    if (adapter_mode == dmp)
        return inv_get_sensor_type_linear_acceleration(values, accuracy, timestamp);
    else if (adapter_mode & mpl_quat)
        return mpl_get_sensor_type_linear_acceleration(values, accuracy, timestamp, mode);
    else
        return inv_get_sensor_type_linear_acceleration(values, accuracy, timestamp);
}

int adapter_get_sensor_type_game_rotation_vector(float *values, int8_t *accuracy, int64_t *timestamp, int mode)
{
    if (adapter_mode == dmp)
        return inv_get_sensor_type_rotation_vector_6_axis(values, accuracy, timestamp);
    else if (adapter_mode & mpl_quat)
        return mpl_get_sensor_type_game_rotation_vector(values, accuracy, timestamp, mode);
    else
        return inv_get_sensor_type_rotation_vector_6_axis(values, accuracy, timestamp);
}

int adapter_get_sensor_type_geomagnetic_rotation_vector(float *values, int8_t *accuracy, int64_t *timestamp, int mode)
{
    if (adapter_mode == dmp)
    return inv_get_sensor_type_geomagnetic_rotation_vector(values, accuracy, timestamp);
    else if (adapter_mode & mpl_quat)
        return mpl_get_sensor_type_geomagnetic_rotation_vector(values, accuracy, timestamp, mode);
    else
        return inv_get_sensor_type_geomagnetic_rotation_vector(values, accuracy, timestamp);

}

int adapter_get_sensor_type_gravity(float *values, int8_t *accuracy, int64_t *timestamp, int mode)
{
    if (adapter_mode == dmp)
        return inv_get_sensor_type_gravity(values, accuracy, timestamp);
    else if (adapter_mode & mpl_quat)
        return mpl_get_sensor_type_gravity(values, accuracy, timestamp, mode);
    else
        return inv_get_sensor_type_gravity(values, accuracy, timestamp);
}

int adapter_get_sensor_type_tilt(float *values, int8_t *accuracy, int64_t *timestamp, int mode)
{
    if (adapter_mode & mpl_gesture)
        return mpl_get_sensor_type_tilt(values, accuracy, timestamp, mode);
    else
        return 0; // not using
}

int adapter_get_sensor_type_pickup(float *values, int8_t *accuracy, int64_t *timestamp, int mode)
{
    if (adapter_mode & mpl_gesture)
        return mpl_get_sensor_type_pickup(values, accuracy, timestamp, mode);
    else
        return 0; // not using
}

int adapter_get_sensor_type_step_detector(float *values, int8_t *accuracy, int64_t *timestamp, int mode)
{
    if (adapter_mode & mpl_gesture)
        return mpl_get_sensor_type_step_detector(values, accuracy, timestamp, mode);
    else
        return 0; // not using
}

int adapter_get_sensor_type_step_counter(uint64_t *value, int8_t *accuracy, int64_t *timestamp, int mode)
{
    if (adapter_mode & mpl_gesture)
        return mpl_get_sensor_type_step_counter(value, accuracy, timestamp, mode);
    else
        return 0; // not using
}

void adapter_set_sample_rate(int sample_rate_us, int id)
{
    if (adapter_mode == mpl) {
        return mpl_set_sample_rate(sample_rate_us, id);
    } else {
        switch(id) {
            case ID_GY:
            case ID_RG:
            case ID_GYW:
            case ID_RGW:
		if (adapter_mode & mpl_gyro)
		    mpl_set_sample_rate(sample_rate_us, id);
		else
                inv_set_gyro_sample_rate(sample_rate_us);
                break;
            case ID_A:
            case ID_AW:
		if (adapter_mode & mpl_accel)
		    mpl_set_sample_rate(sample_rate_us, id);
		else
                inv_set_accel_sample_rate(sample_rate_us);
                break;
            case ID_M:
            case ID_RM:
            case ID_MW:
            case ID_RMW:
		if (adapter_mode & mpl_compass)
		    mpl_set_sample_rate(sample_rate_us, id);
		else
                inv_set_compass_sample_rate(sample_rate_us);
                break;
            case ID_RV:
            case ID_RVW:
            case ID_GRV:
            case ID_GRVW:
            case ID_GMRV:
            case ID_GMRVW:
		if (adapter_mode & mpl_quat)
		    mpl_set_sample_rate(sample_rate_us, id);
		else
                inv_set_quat_sample_rate(sample_rate_us);
                break;
        }
    }
}

void adapter_set_gyro_sample_rate(int sample_rate_us, int id)
{
    if (adapter_mode == dmp)
        return inv_set_compass_sample_rate(sample_rate_us);
    else if (adapter_mode & mpl_gyro)
        return mpl_set_sample_rate(sample_rate_us, id);
    else
        return inv_set_compass_sample_rate(sample_rate_us);
}

void adapter_set_compass_sample_rate(int sample_rate_us, int id)
{
    if (adapter_mode == dmp)
        return inv_set_compass_sample_rate(sample_rate_us);
    else if (adapter_mode & mpl_compass)
        return mpl_set_sample_rate(sample_rate_us, id);
    else
        return inv_set_compass_sample_rate(sample_rate_us);
}

void adapter_set_accel_sample_rate(int sample_rate_us, int id)
{
    if (adapter_mode == dmp)
        return inv_set_accel_sample_rate(sample_rate_us);
    else if (adapter_mode & mpl_accel)
        return mpl_set_sample_rate(sample_rate_us, id);
    else
        return inv_set_accel_sample_rate(sample_rate_us);
}

void adapter_set_rotation_vector_sample_rate(int sample_rate_us, int id)
{
}
void adapter_set_geomagnetic_rotation_vector_sample_rate(int sample_rate_us, int id)
{
}
void adapter_set_rotation_vector_6_axis_sample_rate(int sample_rate_us, int id)
{
}

void adapter_set_linear_acceleration_sample_rate(int sample_rate_us, int id)
{
    if (adapter_mode == dmp)
        return inv_set_linear_acceleration_sample_rate(sample_rate_us);
    else if (adapter_mode & mpl_quat)
        return mpl_set_sample_rate(sample_rate_us, id);
    else
        return inv_set_linear_acceleration_sample_rate(sample_rate_us);
}

void adapter_set_orientation_sample_rate(int sample_rate_us, int id)
{
    if (adapter_mode == dmp)
        return inv_set_orientation_sample_rate(sample_rate_us);
    else if (adapter_mode & mpl_quat)
        return mpl_set_sample_rate(sample_rate_us, id);
    else
        return inv_set_linear_acceleration_sample_rate(sample_rate_us);
}

void adapter_set_gravity_sample_rate(int sample_rate_us, int id)
{
    if (adapter_mode == dmp)
        return inv_set_gravity_sample_rate(sample_rate_us);
    else if (adapter_mode & mpl_quat)
        return mpl_set_sample_rate(sample_rate_us, id);
    else
        return inv_set_gravity_sample_rate(sample_rate_us);
}

void adapter_set_gyro_orientation_and_scale(int orientation, int sensitivity)
{
    if (adapter_mode == dmp)
        return inv_set_gyro_orientation_and_scale(orientation, sensitivity);
    else if (adapter_mode & mpl_gyro)
        return mpl_set_gyro_orientation_and_scale(orientation, sensitivity);
    else
        return inv_set_gyro_orientation_and_scale(orientation, sensitivity);
}

void adapter_set_accel_orientation_and_scale(int orientation, int sensitivity)
{
    if (adapter_mode == dmp)
        return inv_set_accel_orientation_and_scale(orientation, sensitivity);
    else if (adapter_mode & mpl_accel) {
	if (!(adapter_mode & mpl_quat)) {
	    inv_set_accel_orientation_and_scale(orientation, sensitivity);
	}
        return mpl_set_accel_orientation_and_scale(orientation, sensitivity);
    }
    else
        return inv_set_accel_orientation_and_scale(orientation, sensitivity);
}

void adapter_set_compass_orientation_and_scale(int orientation, int sensitivity, int *softIron)
{
    if (adapter_mode == dmp)
        return inv_set_compass_orientation_and_scale(orientation, sensitivity);
    else if (adapter_mode & mpl_compass)
        return mpl_set_compass_orientation_and_scale(orientation, sensitivity, softIron);
    else
        return inv_set_compass_orientation_and_scale(orientation, sensitivity);
}
void adapter_set_compass_orientation_and_scale1(int orientation, int scale, int *sensitivity, int *softIron)
{ //
    if (adapter_mode == dmp)
        return inv_set_compass_orientation_and_scale(orientation, scale);
    else if (adapter_mode & mpl_compass)
        return mpl_set_compass_orientation_and_scale1(orientation, scale, sensitivity, softIron);
    else
        return inv_set_compass_orientation_and_scale(orientation, scale);
}

void adapter_get_mpl_gyro_bias(int *bias, int *temperature)
{
    if (adapter_mode == dmp)
        inv_get_mpl_gyro_bias(bias, temperature);
    else if (adapter_mode & mpl_gyro)
        mpl_get_mpl_gyro_bias(bias);
    else
        inv_get_mpl_gyro_bias(bias, temperature);
}

void adapter_set_mpl_gyro_bias(int *bias, int accuracy)
{
    if (adapter_mode == dmp)
        inv_set_mpl_gyro_bias(bias, accuracy);
    else if (adapter_mode & mpl_gyro)
        mpl_set_mpl_gyro_bias(bias);
    else
        inv_set_mpl_gyro_bias(bias, accuracy);
}

void adapter_get_mpl_accel_bias(int *bias, int *temperature)
{
    if (adapter_mode == dmp)
        inv_get_mpl_accel_bias(bias, temperature);
    else if (adapter_mode & mpl_accel)
        mpl_get_mpl_accel_bias(bias);
    else
        inv_get_mpl_accel_bias(bias, temperature);
}

void adapter_set_mpl_accel_bias(int *bias, int accuracy)
{
    if (adapter_mode == dmp)
        inv_set_accel_bias_mask(bias, 3,7);
    else if (adapter_mode & mpl_accel)
        mpl_set_mpl_accel_bias(bias);
    else
        inv_set_accel_bias_mask(bias, 3,7);
}

void adapter_get_mpl_compass_bias(int *bias)
{
    if (adapter_mode == dmp) {
        ;//inv_get_mpl_compass_bias(bias)
    } else if (adapter_mode & mpl_compass) {
        mpl_get_mpl_compass_bias(bias);
    } else {
        ;//inv_get_mpl_compass_bias(bias);
    }
}

void adapter_set_mpl_compass_bias(int *bias, int accuracy)
{
    if (adapter_mode == dmp) {
        ;//inv_set_mpl_gyro_bias(bias, accuracy);
    } else if (adapter_mode & mpl_compass) {
        mpl_set_mpl_compass_bias(bias, accuracy);
    } else {
        ;//inv_set_mpl_compass_bias(bias, accuracy);
    }
}

void adapter_set_version(int version_number)
{
    mpl_set_lib_version(version_number);
}

int adapter_get_version()
{
    return mpl_get_lib_version();
}

int adapter_get_calibration_mode(char *ChipID)
{
    if (!strcmp(ChipID, "ICM20648"))
	return dmp;
    else if (!strcmp(ChipID, "ICM20608D"))
	return dmp | mpl_accel;
    else if (!strcmp(ChipID, "ICM20602"))
	return mpl;
    else if (!strcmp(ChipID, "ICM20690"))
	return mpl;
    else
	return dmp;
}

int adapter_get_internal_sampling_rate(char *ChipID)
{
    if (!strcmp(ChipID, "ICM20648"))
	return 1125;
    else if (!strcmp(ChipID, "ICM20608D"))
	return 1000;
    else if (!strcmp(ChipID, "ICM20602"))
	return 1000;
    else
	return 1000;
}
