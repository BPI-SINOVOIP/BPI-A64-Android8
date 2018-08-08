/*
 $License:
    Copyright (C) 2014 InvenSense Corporation, All Rights Reserved.
 $
 */

/*******************************************************************************
 *
 * $Id:$
 *
 ******************************************************************************/

#ifndef INV_ANDROID_TO_DMP_H
#define INV_ANDROID_TO_DMP_H

#ifdef __cplusplus
extern "C" {
#endif

/*
    Includes.
*/
//#include "mltypes.h"
#include "hardware/sensors.h"

/*
    APIs
*/
int inv_android_to_dmp(sensors_event_t *sensors_event, char *out_data, int *size);

#ifdef __cplusplus
}
#endif
#endif  /* INV_ANDROID_TO_DMP_H */
