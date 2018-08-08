#ifndef INV_UTEST_DMP3_SENSOR_CONTROL_H__HDPWBQ_
#define INV_UTEST_DMP3_SENSOR_CONTROL_H__HDPWBQ_

#include "mltypes.h"

struct sensor_dmp_cntrl_t
{
    short fifo_control, fifo_control2, motion_event_control;
	short gyro_fifo_mask, motion_event_mask;
    int gyro_state;
    short gyro_cntr; // The gyro takes 50ms to turn on, this is used to measure that time
    short gyro_cal_state;
};

#ifdef __cplusplus
extern "C" {
#endif

void dmp3_sensor_control(struct sensor_dmp_cntrl_t *sc);
void dmp3_sensor_control_init(struct sensor_dmp_cntrl_t *sc);


#ifdef __cplusplus
}
#endif

#endif // INV_UTEST_DMP3_SENSOR_CONTROL_H__HDPWBQ_
