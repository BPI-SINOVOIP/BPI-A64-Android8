#ifndef INV_TEST_DMP3_SENSOR_SETUO_H__HEDCMQ__
#define INV_TEST_DMP3_SENSOR_SETUO_H__HEDCMQ__

#ifdef __cplusplus
extern "C" {
#endif

int getLibVersion();
void setLibVersion(int version);

#define GYRO_AVAIL		0x1
#define ACCEL_AVAIL		0x2
#define PRESSURE_AVAIL	0x4
#define SECONDARY_AVAIL	0x8

struct dmp3_sensor_setup_t {
    // Internal variables
    short orientation; // 0x88 is identity
	short compass_orientation;
	int accel_raw_scaled[3];
    int accel_body[3];
	int accel_fs;
    int gyro_body[3];
    int gyro_body_output[3];
    long long gyro_sf;
	int compass_raw_scaled[3];
	int compass_body[3];
	int compass_sensitivity;
    int gyro_bias[3];
    int accel_bias[3];
    int compass_bias[3];
    short raw_gyro[3];
    short raw_accel[3];
    short raw_compass[3];
    short raw_temperature;
	short data_rdy_status;
};

struct dmp3_interrupt_t {
    unsigned char extended;
    unsigned char basic;
};

#ifdef __cplusplus
}
#endif


#endif // INV_TEST_DMP3_SENSOR_SETUO_H__HEDCMQ__
