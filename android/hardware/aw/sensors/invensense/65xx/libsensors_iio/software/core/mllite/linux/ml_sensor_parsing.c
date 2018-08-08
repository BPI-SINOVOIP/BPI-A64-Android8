/*
   $License:
   Copyright (C) 2014 InvenSense Corporation, All Rights Reserved.
   $
 */

/******************************************************************************
 *
 * $Id: ml_stored_data.c 6132 2011-10-01 03:17:27Z mcaramello $
 *
 *****************************************************************************/

/**
 * @defgroup ML_STORED_DATA
 *
 * @{
 *      @file     ml_stored_data.c
 *      @brief    functions for reading and writing stored data sets.
 *                Typically, these functions process stored calibration data.
 */

#undef MPL_LOG_NDEBUG
#define MPL_LOG_NDEBUG 1 /* Use 0 to turn on MPL_LOGV output */
#undef MPL_LOG_TAG

#include <stdio.h>
#include <string.h>

#include "log.h"
#undef MPL_LOG_TAG
#define MPL_LOG_TAG "MPL-sensor_parsing"

#include "ml_sensor_parsing.h"

#define SENSOR_PARSING_DEBUG    0
#define SENSOR_PARSING_DEBUG   0

#define SENSOR_PARSING_LOG MPL_LOGI
#define SENSOR_PARSING_LOG  MPL_LOGI
#define false 0
#define true 1

static char left_over_buffer[1024];
static int left_over_size;

unsigned short inv_sensor_parsing(char *in, char *out, int read_size)
{
    unsigned short hdr;
    char *dptr;
    int found_sensor, sensor_size;
    char tmp[32];

    if (!read_size) {
        MPL_LOGV("Not read size\n");
        return 0;
    }
    if (read_size > 32) {
        MPL_LOGV("read size > 32 size %d\n", read_size);
        return 0;
    }
    if (read_size < 0) {
        left_over_size = 0;
        MPL_LOGV("read size < 0 size %d\n", read_size);
        return 0;
    }
    memcpy(&left_over_buffer[left_over_size], in, read_size);
    left_over_size += read_size;
    dptr = left_over_buffer;

    hdr = *((unsigned short *)(dptr));
    found_sensor = false;
    sensor_size = 0;

    MPL_LOGV("parsing HDR [%04X] hdr = [%04X] size [%d]\n",hdr & (~1), hdr, left_over_size);

    switch (hdr) {
        case PRESSURE_HDR:
        case PRESSURE_WAKE_HDR:
        case STEP_COUNTER_HDR:
        case STEP_COUNTER_WAKE_HDR:
        case ACCEL_HDR:
        case ACCEL_WAKE_HDR:
        case GYRO_HDR:
        case GYRO_WAKE_HDR:
        case PEDQUAT_HDR:
        case ALS_HDR:
        case ALS_WAKE_HDR:
        case STEP_DETECTOR_HDR:
        case STEP_DETECTOR_WAKE_HDR:
            if (left_over_size >= 16) {
                found_sensor = true;
                sensor_size = 16;
                MPL_LOGV("HDR [%04X] founded [16]\n",hdr);
            } else
            {
                MPL_LOGV("HDR [%04X] left over size < 16\n", hdr);
            }
            break;
        case COMPASS_HDR:
        case COMPASS_WAKE_HDR:
        case GYRO_CALIB_HDR:
        case GYRO_CALIB_WAKE_HDR:
        case COMPASS_CALIB_HDR:
        case COMPASS_CALIB_WAKE_HDR:
        case GEOMAG_HDR:
        case GEOMAG_WAKE_HDR:
        case SIXQUAT_HDR:
        case SIXQUAT_WAKE_HDR:
        case NINEQUAT_HDR:
        case NINEQUAT_WAKE_HDR:
            if (left_over_size >= 24) {
                found_sensor = true;
                sensor_size = 24;
                MPL_LOGV("HDR [%04X] founded [32]\n",hdr);
            } else
            {
                MPL_LOGV("HDR [%04X] left over size < 24\n", hdr);
            }
            break;
	case EIS_GYROSCOPE_HDR:
            if (left_over_size >= 32) {
                found_sensor = true;
                sensor_size = 32;
                MPL_LOGV("HDR [%04X] founded [32]\n",hdr);
            } else
            {
                MPL_LOGV("HDR [%04X] left over size < 24\n", hdr);
            }
            break;
        case EIS_AUTHENTICATION_HDR:
            if (left_over_size >= 8) {
                found_sensor = true;
                sensor_size = 8;
                MPL_LOGV("HDR [%04X] founded [32]\n",hdr);
            } else
            {
                MPL_LOGV("HDR [%04X] left over size < 24\n", hdr);
            }
            break;
        case ACCEL_ACCURACY_HDR:
        case GYRO_ACCURACY_HDR:
        case COMPASS_ACCURACY_HDR:
        case EMPTY_MARKER:
        case END_MARKER:
            found_sensor = true;
            sensor_size = 8;
            break;
        default:
            left_over_size = 0;
            MPL_LOGV("header default error= %04X  %04X", hdr, hdr & (~1));
            break;
    }
    if (found_sensor) {
        memcpy(out, left_over_buffer, sensor_size);
        left_over_size -= sensor_size;
        if (left_over_size) {
            memcpy(tmp, &left_over_buffer[sensor_size], left_over_size);
            memcpy(left_over_buffer, tmp, left_over_size);
        }
        return hdr;
    } else {
        MPL_LOGV("HAL Cannot find sensor in parsing function\n");
        return 0;
    }
}
/**
 *  @}
 */
