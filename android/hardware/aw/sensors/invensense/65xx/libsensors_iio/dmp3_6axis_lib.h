#ifndef INV_DMP_6AXIS_LIB__H
#define INV_DMP_6AXIS_LIB__H

#include "dmp3_sensor_setup.h"

#ifdef __cplusplus
extern "C" {
#endif

#define INV_6AXIS_STORAGE_SIZE 64

/** Initialize the 6-axis sensor fusion library module.
* @param[in] sixaxis - Input pointer for this class
*/
void dmp3_6axis_lib_init(void *sixaxis);

/** Run 6-axis sensor fusion
* @param[in] gyro_calibrated - calibrated gyro data from gyro cal
* @param[in] accel_calibrated - calibrated accel data from accel cal
* @param[in] accel_accuracy - accel accuracy from accel cal
*/
void dmp3_6axis_lib_process(int *gyro_calibrated, int *accel_calibrated, int accel_accuracy);

/** Store 6-axis sensor fusion output
* @param[out] sixaxis
*/
void dmp3_6axis_lib_store_output(void *sixaxis);

/** Get 6-axis quaternion
* @param[in] sixaxis - Input pointer for this class
* @param[out] data - 6-axis quaternion output
*/
void dmp3_6axis_lib_get_6axis_quaternion(void *sixaxis, int *data);

/** Get 6-axis accuracy
* @param[in] sixaxis - Input pointer for this class
* returns 6-axis accuracy
*/
int dmp3_6axis_lib_get_6axis_accuracy(void *sixaxis);




#ifdef __cplusplus
}
#endif


#endif // INV_DMP_6AXIS_LIB__H
