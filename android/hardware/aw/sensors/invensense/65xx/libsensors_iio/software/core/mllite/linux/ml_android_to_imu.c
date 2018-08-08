/*
 $License:
    Copyright (C) 2014 InvenSense Corporation, All Rights Reserved.
 $
 */

/******************************************************************************
 *
 * $Id:$
 *
 *****************************************************************************/

/**
 * @defgroup ML_LOAD_DMP
 *
 * @{
 *      @file     ml_load_dmp.c
 *      @brief    functions for writing dmp firmware.
 */
#include <stdio.h>
#include <string.h>
#undef MPL_LOG_NDEBUG
#define MPL_LOG_NDEBUG 0 /* Use 0 to turn on MPL_LOGV output */
#undef MPL_LOG_TAG
#define MPL_LOG_TAG "MPL-loaddmp"

#include "ml_android_to_imu.h"
//#include "log.h"

#define RPS  (3.14159265359/180.0)
#define MPSS (9.80665)
#define UT_TO_MG (0.01)

//#define ANDROID_TO_DMP_LOG MPL_LOGI
//#define ANDROID_TO_DMP_LOG MPL_LOGI

/*This is for 2G. The driver will convert into proper hardware unit*/
static int inv_convert_accel_to_lsb(sensors_event_t *sensors_event, char *out_data, int *size)
{
	int data[3];
	int i;

	printf("fff==%f, %f, %f\n", sensors_event->acceleration.x, sensors_event->acceleration.y, sensors_event->acceleration.z);
	data[0] = (int)(sensors_event->acceleration.x * 16384 / MPSS);
	data[1] = (int)(sensors_event->acceleration.y * 16384 / MPSS);
	data[2] = (int)(sensors_event->acceleration.z * 16384 / MPSS);
	printf("aff=%d, %d, %d\n", data[0], data[1], data[2]);
	for (i = 0; i < 3; i++)
		memcpy(out_data + 4 * i, (void *)&data[i], sizeof(data[i]));

	*size = 12;

	return 0;
}

/* this is for 250DPS. The driver will convert into proper hardware unit */
static int inv_convert_gyro_to_lsb(sensors_event_t *sensors_event, char *out_data, int *size)
{
	int data[3];
	int i;

	printf("fff==%f, %f, %f\n", sensors_event->uncalibrated_gyro.x_uncalib,
				sensors_event->uncalibrated_gyro.y_uncalib,
				sensors_event->uncalibrated_gyro.z_uncalib);
	data[0] = (int)(sensors_event->uncalibrated_gyro.x_uncalib * 32768 / (RPS * 250));
	data[1] = (int)(sensors_event->uncalibrated_gyro.y_uncalib * 32768 / (RPS * 250));
	data[2] = (int)(sensors_event->uncalibrated_gyro.z_uncalib * 32768 / (RPS * 250));
	printf("aff=%d, %d, %d\n", data[0], data[1], data[2]);
	for (i = 0; i < 3; i++)
		memcpy(out_data + 4 * i, (void *)&data[i], sizeof(data[i]));

	*size = 12;

	return 0;
}

/* this is for 0.15uT. The driver will convert into proper hardware unit */
static int inv_convert_mag_to_lsb(sensors_event_t *sensors_event, char *out_data, int *size)
{
	int data[3];
	int i;

	printf("fff==%f, %f, %f\n", sensors_event->uncalibrated_magnetic.x_uncalib,
				sensors_event->uncalibrated_magnetic.y_uncalib,
				sensors_event->uncalibrated_magnetic.z_uncalib);
	data[0] = (int)(sensors_event->uncalibrated_magnetic.x_uncalib / 0.15);
	data[1] = (int)(sensors_event->uncalibrated_magnetic.y_uncalib / 0.15);
	data[2] = (int)(sensors_event->uncalibrated_magnetic.z_uncalib / 0.15);
	printf("aff=%d, %d, %d\n", data[0], data[1], data[2]);
	for (i = 0; i < 3; i++)
		memcpy(out_data + 4 * i, (void *)&data[i], sizeof(data[i]));

	*size = 12;

	return 0;
}

int inv_android_to_dmp(sensors_event_t *sensors_event, char *out_data, int *size)
{
	switch (sensors_event->type) {
		case SENSOR_TYPE_ACCELEROMETER:
			inv_convert_accel_to_lsb(sensors_event, out_data, size);
			break;
		case SENSOR_TYPE_GYROSCOPE_UNCALIBRATED:
			inv_convert_gyro_to_lsb(sensors_event, out_data, size);
			break;
		case SENSOR_TYPE_MAGNETIC_FIELD_UNCALIBRATED:
			inv_convert_mag_to_lsb(sensors_event, out_data, size);
		default:
			break;
	}

	return 0;
}

/**
 *  @}
 */
