/*
* Copyright © 2011-2014 InvenSense Inc.  All rights reserved.
*
* This software and/or documentation  (collectively “Software”) is subject to InvenSense intellectual property rights
* under U.S. and international copyright and other intellectual property rights laws.
*
* The Software contained herein is PROPRIETARY and CONFIDENTIAL to InvenSense and is provided
* solely under the terms and conditions of a form of InvenSense software license agreement between
* InvenSense and you and any use, modification, reproduction or disclosure of the Software without
* such agreement or the express written consent of InvenSense is strictly prohibited.
*
* EXCEPT AS OTHERWISE PROVIDED IN A LICENSE AGREEMENT BETWEEN THE PARTIES, THE SOFTWARE IS
* PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
* TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT.
* EXCEPT AS OTHERWISE PROVIDED IN A LICENSE AGREEMENT BETWEEN THE PARTIES, IN NO EVENT SHALL
* INVENSENSE BE LIABLE FOR ANY DIRECT, SPECIAL, INDIRECT, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, OR ANY
* DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT,
* NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE
* OF THE SOFTWARE.
*/

#ifndef INV_DMP_GEOMAG_LIB__H
#define INV_DMP_GEOMAG_LIB__H

#include "dmp3_sensor_setup.h"

#ifdef __cplusplus
extern "C" {
#endif

#define INV_GEOMAG_STORAGE_SIZE 64

/** Initialize the geomag sensor fusion library module.
* @param[in] nineaxis - Input pointer for this class
*/
void dmp3_geomag_lib_init(void *nineaxis);

/** Run geomag sensor fusion
* @param[in] compass_calibrated - calibrated compass data from compass cal
* @param[in] accel - accel_body from DMP
* @param[in] compass_accuracy - compass accuracy from compass cal
* @param[in] mag_dis9x - compass magnetic disturbance from compass cal
* @param[in] fusion_data - fusion data from compass cal
*/
void dmp3_geomag_lib_process(int *compass_calibrated, int *accel, int compass_accuracy, short mag_dis9x, int *fusion_data);

/** Store geomag sensor fusion output
* @param[out] nineaxis
*/
void dmp3_geomag_lib_store_output(void *nineaxis);

/** Get geomag quaternion
* @param[in] nineaxis - Input pointer for this class
* @param[out] data - geomag quaternion output
*/
void dmp3_geomag_lib_get_geomag_quaternion(void *nineaxis, int *data);

/** Get geomag accuracy
* @param[in] nineaxis - Input pointer for this class
* returns geomag accuracy
*/
int dmp3_geomag_lib_get_geomag_accuracy(void *nineaxis);

/** Get geomag magnetic disturbance
* @param[in] nineaxis - Input pointer for this class
* returns geomag magnetic disturbance
*/
short dmp3_geomag_lib_get_geomag_mag_dis(void *nineaxis);

/** Get geomag heading accuracy
* @param[in] nineaxis - Input pointer for this class
* returns geomag heading error in radians<<30
*/
int dmp3_geomag_lib_get_geomag_heading_accuracy(void *nineaxis);


#ifdef __cplusplus
}
#endif


#endif // INV_DMP_GEOMAG_LIB__H
