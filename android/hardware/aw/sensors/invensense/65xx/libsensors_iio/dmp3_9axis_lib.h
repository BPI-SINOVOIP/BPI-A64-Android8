#ifndef INV_DMP_9AXIS_LIB__H
#define INV_DMP_9AXIS_LIB__H

#include "dmp3_sensor_setup.h"

#ifdef __cplusplus
extern "C" {
#endif

#define INV_9AXIS_STORAGE_SIZE 64

/** Initialize the 9-axis sensor fusion library module.
* @param[in] nineaxis - Input pointer for this class
*/
void dmp3_9axis_lib_init(void *nineaxis);

/** Run 9-axis sensor fusion
* @param[in] compass_calibrated - calibrated compass data from compass cal
* @param[in] game_rv - 6-axis quaternion from DMP
* @param[in] compass_accuracy - compass accuracy from compass cal
* @param[in] mag_dis9x - compass magnetic disturbance from compass cal
* @param[in] fusion_data - fusion data from compass cal
*/
void dmp3_9axis_lib_process(int *compass_calibrated, int *game_rv, int compass_accuracy, short mag_dis9x, int *fusion_data);

/** Store 9-axis sensor fusion output
* @param[out] nineaxis
*/
void dmp3_9axis_lib_store_output(void *nineaxis);

/** Get 9-axis quaternion
* @param[in] nineaxis - Input pointer for this class
* @param[out] data - 9-axis quaternion output
*/
void dmp3_9axis_lib_get_9axis_quaternion(void *nineaxis, int *data);

/** Get 9-axis accuracy
* @param[in] nineaxis - Input pointer for this class
* returns 9-axis accuracy
*/
int dmp3_9axis_lib_get_9axis_accuracy(void *nineaxis);

/** Get 9-axis magnetic disturbance
* @param[in] nineaxis - Input pointer for this class
* returns 9-axis magnetic disturbance
*/
short dmp3_9axis_lib_get_9axis_mag_dis(void *nineaxis);

/** Get 9-axis heading accuracy
* @param[in] nineaxis - Input pointer for this class
* returns 9-axis heading error in radians<<30
*/
int dmp3_9axis_lib_get_9axis_heading_accuracy(void *nineaxis);


#ifdef __cplusplus
}
#endif


#endif // INV_DMP_9AXIS_LIB__H
