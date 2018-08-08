#ifndef INV_GYRO_CAL_LIB__H
#define INV_GYRO_CAL_LIB__H


#ifdef __cplusplus
extern "C" {
#endif

#define INV_GYRO_STORAGE_SIZE 64

//Gyro Scale Factor Formula:
//((2000.0/(1<<15))*(3.1415926/180)*((divider + 1)/1125.0))*(1<<30)*(1<<14)
/*
#define	GYRO_SCALE_5    3748065965L
#define GYRO_SCALE_15   1199381109L
#define	GYRO_SCALE_50    366477561L
#define GYRO_SCALE_102   183238783L
#define	GYRO_SCALE_225    83290355L
*/

#define GYRO_SCALE_5    3748065965L
#define GYRO_SCALE_15   1349381109L
#define GYRO_SCALE_50    366477561L
#define GYRO_SCALE_55    333135631L
#define GYRO_SCALE_102   193238783L
#define GYRO_SCALE_225    83290355L

/** Initialize the gyro cal library module.
* @param[in] gyro_cal - Input pointer for this class
*/
void dmp3_gyro_cal_lib_init(void *gyro_cal);

/** Run gyro cal
* @param[in] accel - raw accel data
* @param[in] gyro - raw gyro data
* @param[in] temperature - raw temperature data
* @param[in] data_status - data ready status (bit0:gyro, bit1:accel), (1:ready, 0:not ready)
*/
void dmp3_gyro_cal_lib_process(short *accel, short *gyro, short temperature, short data_status);

/** Store gyro cal output
* @param[out] gyro_cal
*/
void dmp3_gyro_cal_lib_store_output(void *gyro_cal);

/** Sets accel scale
* @param[in] scale
*/
void dmp3_gyro_cal_lib_set_accel_scale(short scale);

/** Sets gyro scale
* @param[in] final gyro scale value to be applied
*/
void dmp3_gyro_cal_lib_set_gyro_scale(long long gyro_scale_factor);

void dmp3_gyro_cal_lib_get_gyro_calibrated_only(void *gyro_cal, int *data);

/** Get calibrated gyro data
* @param[in] gyro_cal - Input pointer for this class
* @param[out] data - calibrated gyro output
*/
void dmp3_gyro_cal_lib_get_gyro_calibrated(void *gyro_cal, int *data);

/** Get gyro bias
* @param[in] gyro_cal - Input pointer for this class
* @param[out] data - gyro bias
*/
void dmp3_gyro_cal_lib_get_gyro_bias(void *gyro_cal, int *data);

/** Get gyro accuracy
* @param[in] gyro_cal - Input pointer for this class
* returns gyro accuracy
*/
int dmp3_gyro_cal_lib_get_gyro_accuracy(void *gyro_cal);

/** Sets gyro bias
* @param[in] gyro x,y,z
*/
void dmp3_gyro_cal_lib_set_gyro_bias(int *gyro_bias);


#ifdef __cplusplus
}
#endif


#endif // INV_GYRO_CAL_LIB__H
