#ifndef INV_DMP_BAC_LIB__H
#define INV_DMP_BAC_LIB__H

#include "dmp3_sensor_setup.h"

#ifdef __cplusplus
extern "C" {
#endif

#define INV_BAC_STORAGE_SIZE 64

#define NONE 0x00
#define DRIVE 0x01
#define WALK 0x02
#define RUN 0x04
#define BIKE 0x08
#define TILT 0x10
#define STILL 0x20

/** Initialize the bac library module.
* @param[in] bac - Input pointer for this class
*/
void dmp3_bac_lib_init(void *bac);

/** Run bac
* @param[in] accel_calibrated - calibrated accel data from accel cal
* @param[in] accel_rate - accel output rate
* @param[in] gyro_on - gyro is enable
*/
void dmp3_bac_lib_process(int *accel_calibrated, int accel_rate, int gyro_on);

/** Store bac output
* @param[out] bac
*/
void dmp3_bac_lib_store_output(void *bac);

/** Get bac state
* @param[in] bac- Input pointer for this class
* returns state
*/
short dmp3_bac_lib_get_state(void *bac);

/** Get bac previous state
* @param[in] bac- Input pointer for this class
* returns previous state
*/
short dmp3_bac_lib_get_prev_state(void *bac);

#ifdef __cplusplus
}
#endif


#endif // INV_DMP_BAC_LIB__H

