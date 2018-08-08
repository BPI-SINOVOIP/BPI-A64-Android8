/*
* Copyright (C) 2014 Invensense, Inc.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

#ifndef INV_SENSOR_PARAMS_H
#define INV_SENSOR_PARAMS_H

/******************************************/
/******************************************/
//COMPASS_ID_AK8975
#define COMPASS_AKM8975_RANGE           (9830.f)
#define COMPASS_AKM8975_RESOLUTION      (0.285f)
#define COMPASS_AKM8975_POWER           (10.f)
#define COMPASS_AKM8975_MINDELAY        (14285)
#define COMPASS_AKM8975_MAXDELAY        (1000000)
//COMPASS_ID_AK8963C
#define COMPASS_AKM8963_RANGE           (9830.f)
#define COMPASS_AKM8963_RESOLUTION      (0.15f)
#define COMPASS_AKM8963_POWER           (10.f)
#define COMPASS_AKM8963_MINDELAY        (14285)
#define COMPASS_AKM8963_MAXDELAY        (1000000)
//COMPASS_ID_AK09911
#define COMPASS_AKM9911_RANGE           (9830.f)
#define COMPASS_AKM9911_RESOLUTION      (0.60f)
#define COMPASS_AKM9911_POWER           (10.f)
#define COMPASS_AKM9911_MINDELAY        (14084)
#define COMPASS_AKM9911_MAXDELAY        (1000000)
//COMPASS_ID_AK09912C
#define COMPASS_AKM9912_RANGE           (9830.f)
#define COMPASS_AKM9912_RESOLUTION      (0.15f)
#define COMPASS_AKM9912_POWER           (10.f)
#define COMPASS_AKM9912_MINDELAY        (14084)
#define COMPASS_AKM9912_MAXDELAY        (1000000)
//COMPASS_ID_AK09916
#define COMPASS_AKM9916_RANGE           (9830.f)
#define COMPASS_AKM9916_RESOLUTION      (0.15f)
#define COMPASS_AKM9916_POWER           (10.f)
#define COMPASS_AKM9916_MINDELAY        (14084)
#define COMPASS_AKM9916_MAXDELAY        (1000000)
//COMPASS_ID_AMI306
#define COMPASS_AMI306_RANGE            (5461.f)
#define COMPASS_AMI306_RESOLUTION       (0.9f)
#define COMPASS_AMI306_POWER            (0.15f)
#define COMPASS_AMI306_MINDELAY         (14285)
#define COMPASS_AMI306_MAXDELAY         (1000000)
//COMPASS_ID_YAS53x
#define COMPASS_YAS53x_RANGE            (8001.f)
#define COMPASS_YAS53x_RESOLUTION       (0.012f)
#define COMPASS_YAS53x_POWER            (4.f)
#define COMPASS_YAS53x_MINDELAY         (14285)
#define COMPASS_YAS53x_MAXDELAY         (1000000)// driver decimate to 1hz for CTS
/*******************************************/
//ACCEL_ID_MPU20645
#define ACCEL_ICM20645_RANGE             (2.f * GRAVITY_EARTH)
#define ACCEL_ICM20645_RESOLUTION        (0.004f * GRAVITY_EARTH)
#define ACCEL_ICM20645_POWER             (0.5f)
#define ACCEL_ICM20645_MINDELAY          (4444)
#define ACCEL_ICM20645_MAXDELAY          (1000000)
//ACCEL_ID_MPU30645
#define ACCEL_ICM30645_RANGE             (2.f * GRAVITY_EARTH)
#define ACCEL_ICM30645_RESOLUTION        (0.004f * GRAVITY_EARTH)
#define ACCEL_ICM30645_POWER             (0.5f)
#define ACCEL_ICM30645_MINDELAY          (4444)
#define ACCEL_ICM30645_MAXDELAY          (1000000)
/******************************************/
//GYRO MPU20645
#define GYRO_ICM20645_RANGE              (2000.f * RAD_P_DEG)
#define GYRO_ICM20645_RESOLUTION         (2000.f / 32768.f * RAD_P_DEG)
#define GYRO_ICM20645_POWER              (5.5f)
#define GYRO_ICM20645_MINDELAY           (4444)
#define GYRO_ICM20645_MAXDELAY           (1000000)
//GYRO MPU30645
#define GYRO_ICM30645_RANGE              (2000.f * RAD_P_DEG)
#define GYRO_ICM30645_RESOLUTION         (2000.f / 32768.f * RAD_P_DEG)
#define GYRO_ICM30645_POWER              (5.5f)
#define GYRO_ICM30645_MINDELAY           (4444)
#define GYRO_ICM30645_MAXDELAY           (1000000)
/******************************************/
//ACCEL_ID_MPU20648
#define ACCEL_ICM20648_RANGE             (2.f * GRAVITY_EARTH)
#define ACCEL_ICM20648_RESOLUTION        (0.004f * GRAVITY_EARTH)
#define ACCEL_ICM20648_POWER             (0.5f)
#define ACCEL_ICM20648_MINDELAY          (4444)
#define ACCEL_ICM20648_MAXDELAY          (1000000)
//ACCEL_ID_MPU30648
#define ACCEL_ICM30648_RANGE             (2.f * GRAVITY_EARTH)
#define ACCEL_ICM30648_RESOLUTION        (0.004f * GRAVITY_EARTH)
#define ACCEL_ICM30648_POWER             (0.5f)
#define ACCEL_ICM30648_MINDELAY          (4444)
#define ACCEL_ICM30648_MAXDELAY          (1000000)
/******************************************/
//GYRO MPU20648
#define GYRO_ICM20648_RANGE              (2000.f * RAD_P_DEG)
#define GYRO_ICM20648_RESOLUTION         (2000.f / 32768.f * RAD_P_DEG)
#define GYRO_ICM20648_POWER              (5.5f)
#define GYRO_ICM20648_MINDELAY           (4444)
#define GYRO_ICM20648_MAXDELAY           (1000000)
//GYRO MPU30648
#define GYRO_ICM30648_RANGE              (2000.f * RAD_P_DEG)
#define GYRO_ICM30648_RESOLUTION         (2000.f / 32768.f * RAD_P_DEG)
#define GYRO_ICM30648_POWER              (5.5f)
#define GYRO_ICM30648_MINDELAY           (4444)
#define GYRO_ICM30648_MAXDELAY           (1000000)
/******************************************/
//PRESSURE BMP280
#define PRESSURE_BMP280_RANGE           (1100.f)   // hpa
#define PRESSURE_BMP280_RESOLUTION      (0.009995f)// in psi
#define PRESSURE_BMP280_POWER           (0.004f)   // 0.004mA
#define PRESSURE_BMP280_MINDELAY        (33333)    // 30Hz unit in ns
#define PRESSURE_BMP280_MAXDELAY        (1000000)  // driver decimate to 1hz for CTS
/******************************************/
//LIGHT APDS9930
#define LIGHT_APS9930_RANGE                     (10000)
#define LIGHT_APS9930_RESOLUTION                (0.009994f)
#define LIGHT_APS9930_POWER                     (0.175f)
#define LIGHT_APS9930_MINDELAY                  (112000)
#define LIGHT_APS9930_MAXDELAY                  (1000000)
/******************************************/
//PROXIMITY APDS9930
#define PROXIMITY_APS9930_RANGE                 (5)
#define PROXIMITY_APS9930_RESOLUTION            (0.10070801f)
#define PROXIMITY_APS9930_POWER                 (12.675f)
#define PROXIMITY_APS9930_MINDELAY              (112000)
#define PROXIMITY_APS9930_MAXDELAY              (1000000)
/******************************************/
#endif  /* INV_SENSOR_PARAMS_H */
