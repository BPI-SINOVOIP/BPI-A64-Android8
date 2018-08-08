/*
 $License:
    Copyright (C) 2011-2012 InvenSense Corporation, All Rights Reserved.
    See included License.txt for License information.
 $
 */
#ifndef INV_DATA_BUILDER_H__
#define INV_DATA_BUILDER_H__

#include <stdio.h>
#include "mltypes.h"

#ifdef __cplusplus
extern "C" {
#endif

// Uncomment this flag to enable playback debug and record or playback scenarios
//#define INV_PLAYBACK_DBG

/** This is a new sample of accel data */
#define INV_ACCEL_NEW 1
/** This is a new sample of gyro data */
#define INV_GYRO_NEW 2
/** This is a new sample of compass data */
#define INV_MAG_NEW 4
/** This is a new sample of temperature data */
#define INV_TEMP_NEW 8
/** This is a new sample of quaternion data */
#define INV_QUAT_NEW 16
/** This is a new sample of pressure data */
#define INV_PRESSURE_NEW 32

/** Set if the data is contiguous. Typically not set if a sample was skipped */
#define INV_CONTIGUOUS 16
/** Set if the calibrated data has been solved for */
#define INV_CALIBRATED 32
/** INV_NEW_DATA set for a new set of data, cleared if not available. */
#define INV_NEW_DATA 64
/** Set if raw data exists */
#define INV_RAW_DATA 128
/** Set if the sensor is on */
#define INV_SENSOR_ON 256
/** Set if quaternion has bias correction applied */
#define INV_BIAS_APPLIED 512
/** Set if quaternion is 6-axis from DMP */
#define INV_QUAT_6AXIS 1024
/** Set if quaternion is 9 axis from DMP */
#define INV_QUAT_9AXIS 2048
/** Set if quaternion is 3-axis from DMP */
#define INV_QUAT_3AXIS 4096
/** Set if DMP has applied bias */
#define INV_DMP_BIAS_APPLIED 8192
/** Set if quaternion is 3 elements (android only) */
#define INV_QUAT_3ELEMENT 16384
/** Set if calibrated data is from the DMP */
#define INV_CALIBRATED_DMP 32768
/** Set if raw data is from the DMP */
#define INV_RAW_DMP 65536
/** Set if geomagnetic quaternion */
#define INV_GEOMAG 131072
/** Set if custom sensor data has been solbed for */
#define INV_CUSTOM 262144

#define INV_PRIORITY_MOTION_NO_MOTION          100
#define INV_PRIORITY_GYRO_TC                   150
#define INV_PRIORITY_QUATERNION_GYRO_ACCEL     200
#define INV_PRIORITY_QUATERNION_NO_GYRO        250
#define INV_PRIORITY_MAGNETIC_DISTURBANCE      300
#define INV_PRIORITY_HEADING_FROM_GYRO         350
#define INV_PRIORITY_COMPASS_BIAS_W_GYRO       375
#define INV_PRIORITY_COMPASS_VECTOR_CAL        400
#define INV_PRIORITY_COMPASS_ADV_BIAS          500
#define INV_PRIORITY_9_AXIS_FUSION             600
#define INV_PRIORITY_9_AXIS_FUSION_LIGHT       650
#define INV_PRIORITY_QUATERNION_ADJUST_9_AXIS  700
#define INV_PRIORITY_QUATERNION_ACCURACY       750
#define INV_PRIORITY_RESULTS_HOLDER            800
#define INV_PRIORITY_INUSE_AUTO_CALIBRATION    850
#define INV_PRIORITY_HAL_OUTPUTS               900
#define INV_PRIORITY_GLYPH                     950
#define INV_PRIORITY_SHAKE                     975
#define INV_PRIORITY_SM                        1000

struct inv_single_sensor_t {
    /** Orientation Descriptor. Describes how to go from the mounting frame to the body frame when
    * the rotation matrix could be thought of only having elements of 0,1,-1.
    * 2 bits are used to describe the column of the 1 or -1 and the 3rd bit is used for the sign.
    * Bit 8 is sign of +/- 1 in third row. Bit 6-7 is column of +/-1 in third row.
    * Bit 5 is sign of +/- 1 in second row. Bit 3-4 is column of +/-1 in second row.
    * Bit 2 is sign of +/- 1 in first row. Bit 0-1 is column of +/-1 in first row.
    */
    int orientation;
    /** The raw data in raw data units in the mounting frame */
    short raw[3];
    /** Raw data in body frame */
    int raw_scaled[3];
    /** Calibrated data */
    int calibrated[3];
    /** Raw data in body frame from chip register - custom **/
    int raw_scaled_custom[3];
    int eis_calibrated[4];
    int eis_authentication_calibrated[1];
    int sensitivity;
    /** Sample rate in microseconds */
    int sample_rate_us;
    int sample_rate_ms;
    /** INV_CONTIGUOUS is set for contiguous data. Will not be set if there was a sample
    * skipped due to power savings turning off this sensor.
    * INV_NEW_DATA set for a new set of data, cleared if not available.
    * INV_CALIBRATED_SET if calibrated data has been solved for */
    int status;
    int cal_status;
    /** 0 to 3 for how well sensor data and biases are known. 3 is most accurate. */
    int accuracy;
    inv_time_t timestamp;
    inv_time_t timestamp_prev;
    /** Time stamp for calibrated sensor
        Timestamp need to seperate to cal and raw sensor for turn on both sensors at sametime
    **/
    inv_time_t timestamp_cal;
    inv_time_t timestamp_cal_prev;

    inv_time_t timestamp_custom;
    inv_time_t timestamp_custom_prev;
    /** Bandwidth in Hz */
    int bandwidth;
};

struct inv_quat_sensor_t {
    int raw[4];
    /** INV_CONTIGUOUS is set for contiguous data. Will not be set if there was a sample
    * skipped due to power savings turning off this sensor.
    * INV_NEW_DATA set for a new set of data, cleared if not available.
    * INV_CALIBRATED_SET if calibrated data has been solved for */
    int status;
    inv_time_t timestamp;
    inv_time_t timestamp_prev;
    int sample_rate_us;
    int sample_rate_ms;
};

struct inv_soft_iron_t {
    int raw[3];
    int trans[3];
    int matrix_d[9];  // Q30 format fixed point. The dynamic range is (-2.0 to 2.0);
    float matrix_f[9];

    int enable;
};

struct inv_sensor_cal_t {
    struct inv_single_sensor_t gyro;
    struct inv_single_sensor_t eis_gyro;
    struct inv_single_sensor_t eis_authentication;
    struct inv_single_sensor_t accel;
    struct inv_single_sensor_t compass;
    struct inv_single_sensor_t temp;
    struct inv_quat_sensor_t quat;
    struct inv_single_sensor_t pressure;
    struct inv_soft_iron_t soft_iron;
    /** Combinations of INV_GYRO_NEW, INV_ACCEL_NEW, INV_MAG_NEW to indicate
    * which data is a new sample as these data points may have different sample rates.
    */
    int status;

	// place holder for DMP unit tests
	int gyro_calibrated_dmp[3];
	int accel_calibrated_dmp[3];
	int compass_calibrated_dmp[3];
	int compass_cal_input_dmp;
	short pressure_dmp[3];
	short als_dmp[4];
	short pquat6_dmp[3];
	short heading_err_dmp;
    short activity;
};

// Useful for debug record and playback
typedef enum {
    RD_NO_DEBUG,
    RD_RECORD,
    RD_PLAYBACK
} rd_dbg_mode;

typedef enum {
    PLAYBACK_DBG_TYPE_GYRO,
    PLAYBACK_DBG_TYPE_ACCEL,
    PLAYBACK_DBG_TYPE_COMPASS,
    PLAYBACK_DBG_TYPE_TEMPERATURE,
    PLAYBACK_DBG_TYPE_EXECUTE,
    PLAYBACK_DBG_TYPE_A_ORIENT,
    PLAYBACK_DBG_TYPE_G_ORIENT,
    PLAYBACK_DBG_TYPE_C_ORIENT,
    PLAYBACK_DBG_TYPE_A_SAMPLE_RATE,
    PLAYBACK_DBG_TYPE_C_SAMPLE_RATE,
    PLAYBACK_DBG_TYPE_G_SAMPLE_RATE,
    PLAYBACK_DBG_TYPE_GYRO_OFF,
    PLAYBACK_DBG_TYPE_ACCEL_OFF,
    PLAYBACK_DBG_TYPE_COMPASS_OFF,
    PLAYBACK_DBG_TYPE_Q_SAMPLE_RATE,
    PLAYBACK_DBG_TYPE_QUAT,
    PLAYBACK_DBG_TYPE_QUAT_OFF
} inv_rd_dbg_states;

/** Change this key if the definition of the struct inv_db_save_t changes.
    Previous keys: 53394, 53395, 53396 */
#define INV_DB_SAVE_KEY (53397)
#define INV_DB_SAVE_2_KEY (50003)

#define INV_DB_SAVE_MPL_KEY (50001)
#define INV_DB_SAVE_ACCEL_MPL_KEY (50002)

struct inv_db_save_t {
    /** compass Bias in chip frame, hardware units scaled by 2^16. */
    int compass_bias[3];
    /** gyro factory bias in chip frame, hardware units scaled by 2^16,
        +/- 2000 dps full scale. */
    int factory_gyro_bias[3];
    /** accel factory bias in chip frame, hardware units scaled by 2^16,
        +/- 2 gee full scale. */
    int factory_accel_bias[3];
    /** temperature when factory_gyro_bias was stored. */
    int gyro_temp;
    /** flag to indicate temperature compensation that biases where stored. */
    int gyro_bias_tc_set;
    /** temperature when accel bias was stored. */
    int accel_temp;
    int gyro_temp_slope[3];
    /** sensor accuracies */
    int gyro_accuracy;
    int accel_accuracy;
    int compass_accuracy;
};

struct inv_db_save_mpl_t {
    /** gyro bias in chip frame, hardware units scaled by 2^16, +/- 2000 dps
        full scale */
    int gyro_bias[3];
};

struct inv_db_save_accel_mpl_t {
    /** accel bias in chip frame, hardware units scaled by 2^16, +/- 2 gee
        full scale */
    int accel_bias[3];
};

/** Maximum number of data callbacks that are supported. Safe to increase if needed.*/
#define INV_MAX_DATA_CB 20

#ifdef INV_PLAYBACK_DBG
void inv_turn_on_data_logging(FILE *file);
void inv_turn_off_data_logging();
#endif

void inv_set_gyro_orientation_and_scale(int orientation, int sensitivity);
void inv_set_accel_orientation_and_scale(int orientation,
        int sensitivity);
void inv_set_compass_orientation_and_scale(int orientation,
        int sensitivity);
void inv_set_gyro_sample_rate(int sample_rate_us);
void inv_set_compass_sample_rate(int sample_rate_us);
void inv_set_quat_sample_rate(int sample_rate_us);
void inv_set_accel_sample_rate(int sample_rate_us);
void inv_set_gyro_bandwidth(int bandwidth_hz);
void inv_set_accel_bandwidth(int bandwidth_hz);
void inv_set_compass_bandwidth(int bandwidth_hz);

void inv_get_gyro_sample_rate_ms(int *sample_rate_ms);
void inv_get_accel_sample_rate_ms(int *sample_rate_ms);
void inv_get_compass_sample_rate_ms(int *sample_rate_ms);

inv_error_t inv_register_data_cb(inv_error_t (*func)
                                 (struct inv_sensor_cal_t * data), int priority,
                                 int sensor_type);
inv_error_t inv_unregister_data_cb(inv_error_t (*func)
                                   (struct inv_sensor_cal_t * data));

inv_error_t inv_build_gyro_dmp(const int*gyro, int status, inv_time_t timestamp);
inv_error_t inv_build_eis_gyro(const int*eis_gyro, int status, inv_time_t timestamp);
inv_error_t inv_build_eis_authentication(const int*eis_auth, int status, inv_time_t timestamp);
inv_error_t inv_build_gyro(const short*gyro, inv_time_t timestamp);
inv_error_t inv_build_compass(const int *compass, int status,
                                  inv_time_t timestamp);
inv_error_t inv_build_accel(const int *accel, int status,
                            inv_time_t timestamp);
inv_error_t inv_build_accel_dmp(const int *accel, int status, inv_time_t timestamp);
inv_error_t inv_build_temp(int temp_cal, short temp_raw, inv_time_t timestamp);
inv_error_t inv_build_quat(const int *quat, int status, inv_time_t timestamp);
inv_error_t inv_build_pressure(const int pressure, int status, inv_time_t timestamp);

/* ---- build functions for DMP unit tests start -----*/
inv_error_t inv_build_gyro_calibr_dmptest(const int *gyro_calibr);
inv_error_t inv_build_accel_calibr_dmptest(const int *accel_calibr);
inv_error_t inv_build_compass_calibr_dmptest(const int *compass_calibr);
inv_error_t inv_build_compass_cal_input_dmptest(const int compass_cal_input);
inv_error_t inv_build_pressure_dmptest(const short *pressure);
inv_error_t inv_build_als_dmptest(const short *als);
inv_error_t inv_build_pquat6_dmptest(const short *pquat6);
inv_error_t inv_build_heading_error_dmptest(const short heading_error);
inv_error_t inv_build_activity_dmptest(const short act_on_off);
/* ---- build functions for DMP unit tests end -----*/

inv_error_t inv_execute_on_data(void);

void inv_get_compass_bias(int *bias);

void inv_set_compass_bias(const int *bias, int accuracy);
void inv_set_compass_disturbance(int dist);
void inv_set_compass_accuracy(int accuracy);
int  inv_get_compass_accuracy(void);

void inv_set_gyro_bias(const int *bias);
void inv_set_gyro_bias_2(const int *bias);
void inv_set_mpl_gyro_bias(const int *bias, int accuracy);
void inv_set_gyro_accuracy(int accuracy);
int  inv_get_gyro_accuracy(void);

void inv_set_accel_bias(const int *bias);
void inv_set_accel_bias_2(const int *bias);
void inv_set_mpl_accel_bias(const int *bias, int accuracy);
void inv_set_accel_accuracy(int accuracy);
void inv_set_accel_bias_mask(const int *bias, int accuracy, int mask);

void inv_get_compass_soft_iron_matrix_d(int *matrix);
void inv_set_compass_soft_iron_matrix_d(int *matrix);

void inv_get_compass_soft_iron_matrix_f(float *matrix);
void inv_set_compass_soft_iron_matrix_f(float *matrix);

void inv_get_compass_soft_iron_output_data(int *data);
void inv_get_compass_soft_iron_input_data(int *data);
void inv_set_compass_soft_iron_input_data(const int *data);

void inv_reset_compass_soft_iron_matrix(void);
void inv_enable_compass_soft_iron_matrix(void);
void inv_disable_compass_soft_iron_matrix(void);

void inv_get_mpl_gyro_bias(int *bias, int *temp);
void inv_get_gyro_bias(int *bias);
void inv_get_gyro_bias_2(int *bias);
void inv_get_gyro_bias_dmp_units(int *bias);
int inv_get_factory_accel_bias_mask();
int inv_get_factory_accel_bias_mask_2();
void inv_get_mpl_accel_bias(int *bias, int *temp);
void inv_get_accel_bias(int *bias);
void inv_get_accel_bias_2(int *bias);
void inv_get_accel_bias_dmp_units(int *bias);

void inv_gyro_was_turned_off(void);
void inv_accel_was_turned_off(void);
void inv_compass_was_turned_off(void);
void inv_quaternion_sensor_was_turned_off(void);
inv_error_t inv_init_data_builder(void);
int inv_get_gyro_sensitivity(void);
int inv_get_accel_sensitivity(void);
int inv_get_compass_sensitivity(void);

void inv_get_accel_set(int *data, int8_t *accuracy, inv_time_t * timestamp);
void inv_get_accel_set_custom(int *data, int8_t *accuracy, inv_time_t * timestamp);
void inv_get_gyro_set(int *data, int8_t *accuracy, inv_time_t * timestamp);
void inv_get_gyro_set_raw(int *data, int8_t *accuracy, inv_time_t * timestamp);
void inv_get_gyro_set_raw_custom(int *data, int8_t *accuracy, inv_time_t * timestamp);
void inv_get_eis_gyro_set(int *data, int8_t *accuracy, inv_time_t * timestamp);
void inv_get_eis_authentication_set(int *data, int8_t *accuracy, inv_time_t * timestamp);
void inv_get_compass_set(int *data, int8_t *accuracy, inv_time_t * timestamp);
void inv_get_compass_set_raw(int *data, int8_t *accuracy, inv_time_t * timestamp);
void inv_get_compass_set_raw_custom(int *data, int8_t *accuracy, inv_time_t * timestamp);

void inv_get_gyro(int *gyro);

int inv_get_gyro_accuracy(void);
int inv_get_accel_accuracy(void);
int inv_get_mag_accuracy(void);
void inv_get_raw_compass(short *raw);

short inv_get_activity(void);

int inv_get_compass_on(void);
int inv_get_gyro_on(void);
int inv_get_accel_on(void);

inv_time_t inv_get_last_timestamp(void);
int inv_get_compass_disturbance(void);

// new DMP cal functions
inv_error_t inv_get_gyro_orient(int *orient);
inv_error_t inv_get_accel_orient(int *orient);

#ifdef WIN32
void inv_overwrite_dmp_9quat(void);
#endif

// internal
int inv_get_gyro_bias_tc_set(void);
int inv_get_9_axis_timestamp(int sample_rate_us, inv_time_t *ts);
int inv_get_6_axis_gyro_accel_timestamp(int sample_rate_us, inv_time_t *ts);
int inv_get_6_axis_compass_accel_timestamp(int sample_rate_us, inv_time_t *ts);

#ifdef __cplusplus
}
#endif

#endif  /* INV_DATA_BUILDER_H__ */
