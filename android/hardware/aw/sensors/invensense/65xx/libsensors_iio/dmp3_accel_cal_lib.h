#ifndef INV_ACCEL_CAL_LIB__H
#define INV_ACCEL_CAL_LIB__H


#ifdef __cplusplus
extern "C" {
#endif

#define INV_ACCEL_STORAGE_SIZE 64

typedef enum {
	ACC_RATE_5,
	ACC_RATE_15,
	ACC_RATE_50,
	ACC_RATE_102,
	ACC_RATE_225
} acc_rate;

/** Initialize the accel cal library module.
* @param[in] accel_cal - Input pointer for this class
*/
void dmp3_accel_cal_lib_init(void *accel_cal);

/** Run accel cal
* @param[in] accel - raw accel data
*/
void dmp3_accel_cal_lib_process(short *accel);

/** Store accel cal output
* @param[out] accel_cal
*/
void dmp3_accel_cal_lib_store_output(void *accel_cal);

/** Sets accel rate
* @param[in] rate
*/
void dmp3_accel_cal_lib_set_accel_rate(acc_rate rate);

/** Sets accel scale
* @param[in] scale
*/
void dmp3_accel_cal_lib_set_accel_scale(short scale);

/** Get calibrated accel data
* @param[in] accel_cal - Input pointer for this class
* @param[out] data - calibrated accel output
*/
void dmp3_accel_cal_lib_get_accel_calibrated(void *accel_cal, int *data);

/** Get accel bias
* @param[in] accel_cal - Input pointer for this class
* @param[out] data - accel bias
*/
void dmp3_accel_cal_lib_get_accel_bias(void *accel_cal, int *data);

/** Get accel accuracy
* @param[in] accel_cal - Input pointer for this class
* returns accel accuracy
*/
int dmp3_accel_cal_lib_get_accel_accuracy(void *accel_cal);

/** Sets accel bias
* @param[in] accel x,y,z
*/
void dmp3_accel_cal_lib_set_accel_bias(int *accel_bias);


#ifdef __cplusplus
}
#endif


#endif // INV_ACCEL_CAL_LIB__H
