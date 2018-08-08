#ifndef INV_DMP3_FLIP_PICKUP_DETECTION_LIB__H
#define INV_DMP3_FLIP_PICKUP_DETECTION_LIB__H

#include "dmp3_sensor_setup.h"

#define INV_PICKUP_STORAGE_SIZE 64

#ifdef __cplusplus
extern "C" {
#endif

/** Initialize the flip_pickup library module.
* @param[in] flip_pickup - Input pointer for this class
*/
void dmp3_flip_pickup_lib_init(void *flip_pickup);

/** Run flip_pickup
* @param[in] accel - raw accel data
* @param[in] accel_rate - accel rate
*/
void dmp3_flip_pickup_lib_process(short *accel, int accel_rate);

/** Store flip_pickup output
* @param[out] flip_pickup
*/
void dmp3_flip_pickup_lib_store_output(void *flip_pickup);

/** Get flip_pickup state
* @param[in] flip_pickup- Input pointer for this class
* returns state
*/
int dmp3_flip_pickup_lib_get_state(void *flip_pickup);

#ifdef __cplusplus
}
#endif

#endif // INV_DMP3_FLIP_PICKUP_DETECTION__H
