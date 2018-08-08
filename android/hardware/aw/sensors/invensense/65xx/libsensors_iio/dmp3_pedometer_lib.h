#ifndef INV_DMP_PEDOMETER_LIB__H
#define INV_DMP_PEDOMETER_LIB__H

#include "dmp3_sensor_setup.h"

#ifdef __cplusplus
extern "C" {
#endif

#define INV_PEDOMETER_STORAGE_SIZE 64

/** Initialize pedometer library module.
* @param[in] pedometer - Input pointer for this class
*/
void dmp3_pedometer_lib_init(void *pedometer);

/** Run Pedometer
* @param[in] accel_calibrated - calibrated accel data from accel cal
* @param[in] accel_rate - accel data rate
* @param[in] gyro_on - gyro available
*/
void dmp3_pedometer_lib_process(int *accel_calibrated, int accel_rate, int gyro_on);

/** Store Pedometer output
* @param[out] pedometer
*/
void dmp3_pedometer_lib_store_output(void *pedometer);

/** Get Pedometer step event
* @param[in] pedometer - Input pointer for this class
* returns pedometer event
*/
int dmp3_pedometer_lib_get_pedometer_event(void *pedometer);

/** Get Pedometer steps
* @param[in] pedometer - Input pointer for this class
* returns pedometer steps
*/
int dmp3_pedometer_lib_pedometer_steps(void *pedometer);

#ifdef __cplusplus
}
#endif


#endif // INV_DMP_6AXIS_LIB__H
