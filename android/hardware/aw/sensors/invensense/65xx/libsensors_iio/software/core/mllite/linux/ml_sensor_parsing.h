/*
 $License:
    Copyright (C) 2014 InvenSense Corporation, All Rights Reserved.
 $
 */

/*******************************************************************************
 *
 * $Id: ml_stored_data.h 5873 2011-08-11 03:13:48Z mcaramello $
 *
 ******************************************************************************/

#ifndef INV_MPL_SENSOR_PARSING_H
#define INV_MPL_SENSOR_PARSING_H

#ifdef __cplusplus
extern "C" {
#endif

/*
    Includes.
*/

/*
    Defines
*/
/* data header defines */
#define WAKE_HDR                 0x8000

#define ACCEL_HDR                1
#define GYRO_HDR                 2
#define COMPASS_HDR              3
#define ALS_HDR                  4
#define SIXQUAT_HDR              5
#define NINEQUAT_HDR             6
#define PEDQUAT_HDR              7
#define GEOMAG_HDR               8
#define PRESSURE_HDR             9
#define GYRO_CALIB_HDR           10
#define COMPASS_CALIB_HDR        11
#define STEP_COUNTER_HDR         12
#define STEP_DETECTOR_HDR        13
#define STEP_COUNT_HDR           14
#define ACTIVITY_HDR             15
#define PICK_UP_HDR              16
#define EMPTY_MARKER             17
#define END_MARKER               18
#define COMPASS_ACCURACY_HDR     19
#define ACCEL_ACCURACY_HDR       20
#define GYRO_ACCURACY_HDR        21
#define EIS_GYROSCOPE_HDR        36
#define EIS_AUTHENTICATION_HDR   37

#define ACCEL_WAKE_HDR           (ACCEL_HDR | WAKE_HDR)
#define GYRO_WAKE_HDR            (GYRO_HDR | WAKE_HDR)
#define COMPASS_WAKE_HDR         (COMPASS_HDR | WAKE_HDR)
#define ALS_WAKE_HDR             (ALS_HDR | WAKE_HDR)
#define SIXQUAT_WAKE_HDR         (SIXQUAT_HDR | WAKE_HDR)
#define NINEQUAT_WAKE_HDR        (NINEQUAT_HDR | WAKE_HDR)
#define PEDQUAT_WAKE_HDR         (PEDQUAT_HDR | WAKE_HDR)
#define GEOMAG_WAKE_HDR          (GEOMAG_HDR | WAKE_HDR)
#define PRESSURE_WAKE_HDR        (PRESSURE_HDR | WAKE_HDR)
#define GYRO_CALIB_WAKE_HDR      (GYRO_CALIB_HDR | WAKE_HDR)
#define COMPASS_CALIB_WAKE_HDR   (COMPASS_CALIB_HDR | WAKE_HDR)
#define STEP_COUNTER_WAKE_HDR    (STEP_COUNTER_HDR | WAKE_HDR)
#define STEP_DETECTOR_WAKE_HDR   (STEP_DETECTOR_HDR | WAKE_HDR)

/*
    APIs
*/
unsigned short inv_sensor_parsing(char *in, char *out, int read_size);

/*
    Internal APIs
*/

/*
    Other prototypes
*/


#ifdef __cplusplus
}
#endif
#endif  /* INV_MPL_SENSOR_PARSING_H */
