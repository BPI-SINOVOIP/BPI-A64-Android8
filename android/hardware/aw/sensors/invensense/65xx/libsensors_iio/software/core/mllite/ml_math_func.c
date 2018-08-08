/*
 $License:
    Copyright (C) 2011-2012 InvenSense Corporation, All Rights Reserved.
    See included License.txt for License information.
 $
 */

/*******************************************************************************
 *
 * $Id:$
 *
 ******************************************************************************/

/**
 *   @defgroup  ML_MATH_FUNC ml_math_func
 *   @brief     Motion Library - Math Functions
 *              Common math functions the Motion Library
 *
 *   @{
 *       @file ml_math_func.c
 *       @brief Math Functions.
 */

#include "mlmath.h"
#include "ml_math_func.h"
#include "mlinclude.h"
#include <string.h>

/** @internal
 * Does the cross product of compass by gravity, then converts that
 * to the world frame using the quaternion, then computes the angle that
 * is made.
 *
 * @param[in] compass Compass Vector (Body Frame), length 3
 * @param[in] grav Gravity Vector (Body Frame), length 3
 * @param[in] quat Quaternion, Length 4
 * @return Angle Cross Product makes after quaternion rotation.
 */
float inv_compass_angle(const int *compass, const int *grav, const float *quat)
{
    float cgcross[4], q1[4], q2[4], qi[4];
    float angW;

    // Compass cross Gravity
    cgcross[0] = 0.f;
    cgcross[1] = (float)compass[1] * grav[2] - (float)compass[2] * grav[1];
    cgcross[2] = (float)compass[2] * grav[0] - (float)compass[0] * grav[2];
    cgcross[3] = (float)compass[0] * grav[1] - (float)compass[1] * grav[0];

    // Now convert cross product into world frame
    inv_q_multf(quat, cgcross, q1);
    inv_q_invertf(quat, qi);
    inv_q_multf(q1, qi, q2);

    // Protect against atan2 of 0,0
    if ((q2[2] == 0.f) && (q2[1] == 0.f))
        return 0.f;

    // This is the unfiltered heading correction
    angW = -atan2f(q2[2], q2[1]);
    return angW;
}

/**
 *  @brief  The gyro data magnitude squared :
 *          (1 degree per second)^2 = 2^6 = 2^GYRO_MAG_SQR_SHIFT.
 * @param[in] gyro Gyro data scaled with 1 dps = 2^16
 *  @return the computed magnitude squared output of the gyroscope.
 */
unsigned int inv_get_gyro_sum_of_sqr(const int *gyro)
{
    unsigned int gmag = 0;
    int temp;
    int kk;

    for (kk = 0; kk < 3; ++kk) {
        temp = gyro[kk] >> (16 - (GYRO_MAG_SQR_SHIFT / 2));
        gmag += temp * temp;
    }

    return gmag;
}

/** Performs a multiply and shift by 29. These are good functions to write in assembly on
 * with devices with small memory where you want to get rid of the long long which some
 * assemblers don't handle well
 * @param[in] a
 * @param[in] b
 * @return ((long long)a*b)>>29
*/
int inv_q29_mult(int a, int b)
{
#ifdef UMPL_ELIMINATE_64BIT
    int result;
    result = (int)((float)a * b / (1L << 29));
    return result;
#else
    long long temp;
    int result;
    temp = (long long)a * b;
    result = (int)(temp >> 29);
    return result;
#endif
}

/** Performs a multiply and shift by 30. These are good functions to write in assembly on
 * with devices with small memory where you want to get rid of the long long which some
 * assemblers don't handle well
 * @param[in] a
 * @param[in] b
 * @return ((long long)a*b)>>30
*/
int inv_q30_mult(int a, int b)
{
#ifdef UMPL_ELIMINATE_64BIT
    int result;
    result = (int)((float)a * b / (1L << 30));
    return result;
#else
    long long temp;
    int result;
    temp = (long long)a * b;
    result = (int)(temp >> 30);
    return result;
#endif
}

#ifndef UMPL_ELIMINATE_64BIT
int inv_q30_div(int a, int b)
{
    long long temp;
    int result;
    temp = (((long long)a) << 30) / b;
    result = (int)temp;
    return result;
}
#endif

/** Performs a multiply and shift by shift. These are good functions to write
 * in assembly on with devices with small memory where you want to get rid of
 * the long long which some assemblers don't handle well
 * @param[in] a First multicand
 * @param[in] b Second multicand
 * @param[in] shift Shift amount after multiplying
 * @return ((long long)a*b)<<shift
*/
#ifndef UMPL_ELIMINATE_64BIT
int inv_q_shift_mult(int a, int b, int shift)
{
    int result;
    result = (int)(((long long)a * b) >> shift);
    return result;
}
#endif

/** Performs a fixed point quaternion multiply.
* @param[in] q1 First Quaternion Multicand, length 4. 1.0 scaled
*            to 2^30
* @param[in] q2 Second Quaternion Multicand, length 4. 1.0 scaled
*            to 2^30
* @param[out] qProd Product after quaternion multiply. Length 4.
*             1.0 scaled to 2^30.
*/
void inv_q_mult(const int *q1, const int *q2, int *qProd)
{
    INVENSENSE_FUNC_START;
    qProd[0] = inv_q30_mult(q1[0], q2[0]) - inv_q30_mult(q1[1], q2[1]) -
               inv_q30_mult(q1[2], q2[2]) - inv_q30_mult(q1[3], q2[3]);

    qProd[1] = inv_q30_mult(q1[0], q2[1]) + inv_q30_mult(q1[1], q2[0]) +
               inv_q30_mult(q1[2], q2[3]) - inv_q30_mult(q1[3], q2[2]);

    qProd[2] = inv_q30_mult(q1[0], q2[2]) - inv_q30_mult(q1[1], q2[3]) +
               inv_q30_mult(q1[2], q2[0]) + inv_q30_mult(q1[3], q2[1]);

    qProd[3] = inv_q30_mult(q1[0], q2[3]) + inv_q30_mult(q1[1], q2[2]) -
               inv_q30_mult(q1[2], q2[1]) + inv_q30_mult(q1[3], q2[0]);
}

/** Performs a fixed point quaternion addition.
* @param[in] q1 First Quaternion term, length 4. 1.0 scaled
*            to 2^30
* @param[in] q2 Second Quaternion term, length 4. 1.0 scaled
*            to 2^30
* @param[out] qSum Sum after quaternion summation. Length 4.
*             1.0 scaled to 2^30.
*/
void inv_q_add(int *q1, int *q2, int *qSum)
{
    INVENSENSE_FUNC_START;
    qSum[0] = q1[0] + q2[0];
    qSum[1] = q1[1] + q2[1];
    qSum[2] = q1[2] + q2[2];
    qSum[3] = q1[3] + q2[3];
}

void inv_vector_normalize(int *vec, int length)
{
    INVENSENSE_FUNC_START;
    double normSF = 0;
    int ii;
    for (ii = 0; ii < length; ii++) {
        normSF +=
            inv_q30_to_double(vec[ii]) * inv_q30_to_double(vec[ii]);
    }
    if (normSF > 0) {
        normSF = 1 / sqrt(normSF);
        for (ii = 0; ii < length; ii++) {
            vec[ii] = (int)((double)vec[ii] * normSF);
        }
    } else {
        vec[0] = 1073741824L;
        for (ii = 1; ii < length; ii++) {
            vec[ii] = 0;
        }
    }
}

void inv_q_normalize(int *q)
{
    INVENSENSE_FUNC_START;
    inv_vector_normalize(q, 4);
}

void inv_q_invert(const int *q, int *qInverted)
{
    INVENSENSE_FUNC_START;
    qInverted[0] = q[0];
    qInverted[1] = -q[1];
    qInverted[2] = -q[2];
    qInverted[3] = -q[3];
}

double quaternion_to_rotation_angle(const int *quat) {
    double quat0 = (double )quat[0] / 1073741824;
    if (quat0 > 1.0f) {
        quat0 = 1.0;
    } else if (quat0 < -1.0f) {
        quat0 = -1.0;
    }

    return acos(quat0)*2*180/M_PI;
}

/** Rotates a 3-element vector by Rotation defined by Q
*/
void inv_q_rotate(const int *q, const int *in, int *out)
{
    int q_temp1[4], q_temp2[4];
    int in4[4], out4[4];

    // Fixme optimize
    in4[0] = 0;
    memcpy(&in4[1], in, 3 * sizeof(int));
    inv_q_mult(q, in4, q_temp1);
    inv_q_invert(q, q_temp2);
    inv_q_mult(q_temp1, q_temp2, out4);
    memcpy(out, &out4[1], 3 * sizeof(int));
}

void inv_q_multf(const float *q1, const float *q2, float *qProd)
{
    INVENSENSE_FUNC_START;
    qProd[0] =
        (q1[0] * q2[0] - q1[1] * q2[1] - q1[2] * q2[2] - q1[3] * q2[3]);
    qProd[1] =
        (q1[0] * q2[1] + q1[1] * q2[0] + q1[2] * q2[3] - q1[3] * q2[2]);
    qProd[2] =
        (q1[0] * q2[2] - q1[1] * q2[3] + q1[2] * q2[0] + q1[3] * q2[1]);
    qProd[3] =
        (q1[0] * q2[3] + q1[1] * q2[2] - q1[2] * q2[1] + q1[3] * q2[0]);
}

void inv_q_addf(const float *q1, const float *q2, float *qSum)
{
    INVENSENSE_FUNC_START;
    qSum[0] = q1[0] + q2[0];
    qSum[1] = q1[1] + q2[1];
    qSum[2] = q1[2] + q2[2];
    qSum[3] = q1[3] + q2[3];
}

void inv_q_normalizef(float *q)
{
    INVENSENSE_FUNC_START;
    float normSF = 0;
    float xHalf = 0;
    normSF = (q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
    if (normSF < 2) {
        xHalf = 0.5f * normSF;
        normSF = normSF * (1.5f - xHalf * normSF * normSF);
        normSF = normSF * (1.5f - xHalf * normSF * normSF);
        normSF = normSF * (1.5f - xHalf * normSF * normSF);
        normSF = normSF * (1.5f - xHalf * normSF * normSF);
        q[0] *= normSF;
        q[1] *= normSF;
        q[2] *= normSF;
        q[3] *= normSF;
    } else {
        q[0] = 1.0;
        q[1] = 0.0;
        q[2] = 0.0;
        q[3] = 0.0;
    }
    normSF = (q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
}

/** Performs a length 4 vector normalization with a square root.
* @param[in,out] q vector to normalize. Returns [1,0,0,0] is magnitude is zero.
*/
void inv_q_norm4(float *q)
{
    float mag;
    mag = sqrtf(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
    if (mag) {
        q[0] /= mag;
        q[1] /= mag;
        q[2] /= mag;
        q[3] /= mag;
    } else {
        q[0] = 1.f;
        q[1] = 0.f;
        q[2] = 0.f;
        q[3] = 0.f;
    }
}

void inv_q_invertf(const float *q, float *qInverted)
{
    INVENSENSE_FUNC_START;
    qInverted[0] = q[0];
    qInverted[1] = -q[1];
    qInverted[2] = -q[2];
    qInverted[3] = -q[3];
}

/**
* one-step triad - get quaternion from accel and compass at one instant.
* based on a modified version of triad.m in users\algorithms folder (no transpose on "R = [X, Y, Z]';" line).
* should return q=[334637952 -284493440 703934784 681526400] when
* when accel=[-24998052 22384162 0], compass=[1754490 -1443843 1307299], and accel_fs=11.
*
* @param[in] long accel_body[3] - accel in rawscaled units
*            long compass_body[3] - compass in rawscaled units (1uT=2^16lsb)
*            long accel_fs - how is accel scaled (11 = 2g, 12=4g, etc)
* @param[out] long Qbi_fp[4] - quaternion in fixed point that takes you from Inertial to Body frame (DSB notation). 1.0 is 2^30lsb.
*/
void inv_triad(int *accel_body, int *compass_body, int accel_fs, int *Qbi_fp)
{ 
	float acc[3], an, mag[3], mn, X[3], Xn, Y[3], Z[3], O_BI[9];

	acc[0] = (float)accel_body[0] / ((float) (1 << (14 + accel_fs) )); //get accel into float g's, ie divide RawScaled by 2^(14+11) in 2g setting or 26 in 4g, etc. 14 comes from basic raw definition: 2g=2^15lsb
	acc[1] = (float)accel_body[1] / ((float) (1 << (14 + accel_fs) ));
	acc[2] = (float)accel_body[2] / ((float) (1 << (14 + accel_fs) ));

	mag[0] = (float)compass_body[0] / ((float) (1 << 16) ); //get compass into float uT's, ie divide RawScaled by 2^16
	mag[1] = (float)compass_body[1] / ((float) (1 << 16) );
	mag[2] = (float)compass_body[2] / ((float) (1 << 16) );

	//Z = accel/norm(accel);
	an = sqrtf( acc[0]*acc[0] + acc[1]*acc[1] + acc[2]*acc[2] ); //acc norm
	Z[0] = acc[0]/an; 
	Z[1] = acc[1]/an; 
	Z[2] = acc[2]/an; 

	//compass = compass/norm(compass);
	mn = sqrtf( mag[0]*mag[0] + mag[1]*mag[1] + mag[2]*mag[2] ); //mag norm
	mag[0] = mag[0]/mn; 
	mag[1] = mag[1]/mn; 
	mag[2] = mag[2]/mn; 

	//X = cross(compass, Z);
	X[0] = - mag[2] * Z[1] + mag[1] * Z[2];
	X[1] =   mag[2] * Z[0] - mag[0] * Z[2];
	X[2] = - mag[1] * Z[0] + mag[0] * Z[1];

	//X = X/norm(X);
	Xn = sqrtf( X[0]*X[0] + X[1]*X[1] + X[2]*X[2] ); //X norm
	X[0] = X[0]/Xn; 
	X[1] = X[1]/Xn; 
	X[2] = X[2]/Xn; 

	//Y = cross(Z, X);
	Y[0] = - Z[2] * X[1] + Z[1] * X[2];
	Y[1] =   Z[2] * X[0] - Z[0] * X[2];
	Y[2] = - Z[1] * X[0] + Z[0] * X[1];

	//O = [X, Y, Z];
	O_BI[0] = X[0]; O_BI[1] = Y[0]; O_BI[2] = Z[0]; 
	O_BI[3] = X[1]; O_BI[4] = Y[1]; O_BI[5] = Z[1]; 
	O_BI[6] = X[2]; O_BI[7] = Y[2]; O_BI[8] = Z[2]; 

	//q = O2q(O);
	inv_rotation_to_quaternion(O_BI, Qbi_fp);
}

/**
 * Converts a rotation matrix to a quaternion.
 * @param[in] Rcb Rotation matrix in floating point. The
 *             First 3 elements of the rotation matrix, represent
 *             the first row of the matrix. 
 * @param[out] Qcb_fp 4-element quaternion in fixed point. One is 2^30.
 */
void inv_rotation_to_quaternion(float *Rcb, int *Qcb_fp)
{
         float r11,r12,r13, r21,r22,r23, r31,r32,r33;
         float Qcb[4];

         r11 = Rcb[0]; //assume matrix is stored row wise first, that is rot[1] is row 1, col 2
         r12 = Rcb[1];
         r13 = Rcb[2];

         r21 = Rcb[3];
         r22 = Rcb[4];
         r23 = Rcb[5];

         r31 = Rcb[6];
         r32 = Rcb[7];
         r33 = Rcb[8];

         Qcb[0] = (1.f + r11 + r22 + r33) / 4.f;
         Qcb[1] = (1.f + r11 - r22 - r33) / 4.f;
         Qcb[2] = (1.f - r11 + r22 - r33) / 4.f;
         Qcb[3] = (1.f - r11 - r22 + r33) / 4.f;

         if(Qcb[0] < 0.0f) Qcb[0] = 0.0f;
         if(Qcb[1] < 0.0f) Qcb[1] = 0.0f;
         if(Qcb[2] < 0.0f) Qcb[2] = 0.0f;
         if(Qcb[3] < 0.0f) Qcb[3] = 0.0f;
         Qcb[0] = sqrt(Qcb[0]);
         Qcb[1] = sqrt(Qcb[1]);
         Qcb[2] = sqrt(Qcb[2]);
         Qcb[3] = sqrt(Qcb[3]);

         if(Qcb[0] >= Qcb[1] && Qcb[0] >= Qcb[2] && Qcb[0] >= Qcb[3]) //Qcb[0] is max
         {
                 Qcb[0] = Qcb[0];
                 Qcb[1] = (r23 - r32)/(4.f*Qcb[0]);
                 Qcb[2] = (r31 - r13)/(4.f*Qcb[0]);
                 Qcb[3] = (r12 - r21)/(4.f*Qcb[0]);
         }
         else if(Qcb[1] >= Qcb[0] && Qcb[1] >= Qcb[2] && Qcb[1] >= Qcb[3]) //Qcb[1] is max
         {
                 Qcb[0] = (r23 - r32)/(4.f*Qcb[1]);
                 Qcb[1] = Qcb[1];
                 Qcb[2] = (r12 + r21)/(4.f*Qcb[1]);
                 Qcb[3] = (r31 + r13)/(4.f*Qcb[1]);
         }
         else if(Qcb[2] >= Qcb[0] && Qcb[2] >= Qcb[1] && Qcb[2] >= Qcb[3]) //Qcb[2] is max
         {
                 Qcb[0] = (r31 - r13)/(4.f*Qcb[2]);
                 Qcb[1] = (r12 + r21)/(4.f*Qcb[2]);
                 Qcb[2] = Qcb[2];
                 Qcb[3] = (r23 + r32)/(4.f*Qcb[2]);
         }
         else if(Qcb[3] >= Qcb[0] && Qcb[3] >= Qcb[1] && Qcb[3] >= Qcb[2]) //Qcb[3] is max
         {
                 Qcb[0] = (r12 - r21)/(4.f*Qcb[3]);
                 Qcb[1] = (r31 + r13)/(4.f*Qcb[3]);
                 Qcb[2] = (r23 + r32)/(4.f*Qcb[3]);
                 Qcb[3] = Qcb[3];
         }
         else
         {
                 //printf('coding error\n'); //error
         }

         Qcb_fp[0] = (int)(Qcb[0]*1073741824.0f);
         Qcb_fp[1] = (int)(Qcb[1]*1073741824.0f);
         Qcb_fp[2] = (int)(Qcb[2]*1073741824.0f);
         Qcb_fp[3] = (int)(Qcb[3]*1073741824.0f);
}

/**
 * Converts a quaternion to a rotation matrix.
 * @param[in] quat 4-element quaternion in fixed point. One is 2^30.
 * @param[out] rot Rotation matrix in fixed point. One is 2^30. The
 *             First 3 elements of the rotation matrix, represent
 *             the first row of the matrix. Rotation matrix multiplied
 *             by a 3 element column vector transform a vector from Body
 *             to World.
 */
void inv_quaternion_to_rotation(const int *quat, int *rot)
{
    rot[0] =
        inv_q29_mult(quat[1], quat[1]) + inv_q29_mult(quat[0],
                quat[0]) -
        1073741824L;
    rot[1] =
        inv_q29_mult(quat[1], quat[2]) - inv_q29_mult(quat[3], quat[0]);
    rot[2] =
        inv_q29_mult(quat[1], quat[3]) + inv_q29_mult(quat[2], quat[0]);
    rot[3] =
        inv_q29_mult(quat[1], quat[2]) + inv_q29_mult(quat[3], quat[0]);
    rot[4] =
        inv_q29_mult(quat[2], quat[2]) + inv_q29_mult(quat[0],
                quat[0]) -
        1073741824L;
    rot[5] =
        inv_q29_mult(quat[2], quat[3]) - inv_q29_mult(quat[1], quat[0]);
    rot[6] =
        inv_q29_mult(quat[1], quat[3]) - inv_q29_mult(quat[2], quat[0]);
    rot[7] =
        inv_q29_mult(quat[2], quat[3]) + inv_q29_mult(quat[1], quat[0]);
    rot[8] =
        inv_q29_mult(quat[3], quat[3]) + inv_q29_mult(quat[0],
                quat[0]) -
        1073741824L;
}

/**
 * Converts a quaternion to a rotation vector. A rotation vector is
 * a method to represent a 4-element quaternion vector in 3-elements.
 * To get the quaternion from the 3-elements, The last 3-elements of
 * the quaternion will be the given rotation vector. The first element
 * of the quaternion will be the positive value that will be required
 * to make the magnitude of the quaternion 1.0 or 2^30 in fixed point units.
 * @param[in] quat 4-element quaternion in fixed point. One is 2^30.
 * @param[out] rot Rotation vector in fixed point. One is 2^30.
 */
void inv_quaternion_to_rotation_vector(const int *quat, int *rot)
{
    rot[0] = quat[1];
    rot[1] = quat[2];
    rot[2] = quat[3];

    if (quat[0] < 0.0) {
        rot[0] = -rot[0];
        rot[1] = -rot[1];
        rot[2] = -rot[2];
    }
}

/** Converts a 32-bit long to a big endian byte stream */
unsigned char *inv_int32_to_big8(int x, unsigned char *big8)
{
    big8[0] = (unsigned char)((x >> 24) & 0xff);
    big8[1] = (unsigned char)((x >> 16) & 0xff);
    big8[2] = (unsigned char)((x >> 8) & 0xff);
    big8[3] = (unsigned char)(x & 0xff);
    return big8;
}

/** Converts a big endian byte stream into a 32-bit long */
int inv_big8_to_int32(const unsigned char *big8)
{
    int x;
    x = ((int)big8[0] << 24) | ((int)big8[1] << 16) | ((int)big8[2] << 8)
        | ((int)big8[3]);
    return x;
}

/** Converts a big endian byte stream into a 16-bit integer (short) */
short inv_big8_to_int16(const unsigned char *big8)
{
    short x;
    x = ((short)big8[0] << 8) | ((short)big8[1]);
    return x;
}

/** Converts a little endian byte stream into a 16-bit integer (short) */
short inv_little8_to_int16(const unsigned char *little8)
{
    short x;
    x = ((short)little8[1] << 8) | ((short)little8[0]);
    return x;
}

/** Converts a 16-bit short to a big endian byte stream */
unsigned char *inv_int16_to_big8(short x, unsigned char *big8)
{
    big8[0] = (unsigned char)((x >> 8) & 0xff);
    big8[1] = (unsigned char)(x & 0xff);
    return big8;
}

void inv_matrix_det_inc(float *a, float *b, int *n, int x, int y)
{
    int k, l, i, j;
    for (i = 0, k = 0; i < *n; i++, k++) {
        for (j = 0, l = 0; j < *n; j++, l++) {
            if (i == x)
                i++;
            if (j == y)
                j++;
            *(b + 6 * k + l) = *(a + 6 * i + j);
        }
    }
    *n = *n - 1;
}

void inv_matrix_det_incd(double *a, double *b, int *n, int x, int y)
{
    int k, l, i, j;
    for (i = 0, k = 0; i < *n; i++, k++) {
        for (j = 0, l = 0; j < *n; j++, l++) {
            if (i == x)
                i++;
            if (j == y)
                j++;
            *(b + 6 * k + l) = *(a + 6 * i + j);
        }
    }
    *n = *n - 1;
}

float inv_matrix_det(float *p, int *n)
{
    float d[6][6], sum = 0;
    int i, j, m;
    m = *n;
    if (*n == 2)
        return (*p ** (p + 7) - *(p + 1) ** (p + 6));
    for (i = 0, j = 0; j < m; j++) {
        *n = m;
        inv_matrix_det_inc(p, &d[0][0], n, i, j);
        sum =
            sum + *(p + 6 * i + j) * SIGNM(i +
                                            j) *
            inv_matrix_det(&d[0][0], n);
    }

    return (sum);
}

double inv_matrix_detd(double *p, int *n)
{
    double d[6][6], sum = 0;
    int i, j, m;
    m = *n;
    if (*n == 2)
        return (*p ** (p + 7) - *(p + 1) ** (p + 6));
    for (i = 0, j = 0; j < m; j++) {
        *n = m;
        inv_matrix_det_incd(p, &d[0][0], n, i, j);
        sum =
            sum + *(p + 6 * i + j) * SIGNM(i +
                                            j) *
            inv_matrix_detd(&d[0][0], n);
    }

    return (sum);
}

/** Wraps angle from (-M_PI,M_PI]
 * @param[in] ang Angle in radians to wrap
 * @return Wrapped angle from (-M_PI,M_PI]
 */
float inv_wrap_angle(float ang)
{
    if (ang > M_PI)
        return ang - 2 * (float)M_PI;
    else if (ang <= -(float)M_PI)
        return ang + 2 * (float)M_PI;
    else
        return ang;
}

/** Finds the minimum angle difference ang1-ang2 such that difference
 * is between [-M_PI,M_PI]
 * @param[in] ang1
 * @param[in] ang2
 * @return angle difference ang1-ang2
 */
float inv_angle_diff(float ang1, float ang2)
{
    float d;
    ang1 = inv_wrap_angle(ang1);
    ang2 = inv_wrap_angle(ang2);
    d = ang1 - ang2;
    if (d > M_PI)
        d -= 2 * (float)M_PI;
    else if (d < -(float)M_PI)
        d += 2 * (float)M_PI;
    return d;
}

/** bernstein hash, derived from public domain source */
uint32_t inv_checksum(const unsigned char *str, int len)
{
    uint32_t hash = 5381;
    int i, c;

    for (i = 0; i < len; i++) {
        c = *(str + i);
        hash = ((hash << 5) + hash) + c;    /* hash * 33 + c */
    }

    return hash;
}

static unsigned short inv_row_2_scale(const signed char *row)
{
    unsigned short b;

    if (row[0] > 0)
        b = 0;
    else if (row[0] < 0)
        b = 4;
    else if (row[1] > 0)
        b = 1;
    else if (row[1] < 0)
        b = 5;
    else if (row[2] > 0)
        b = 2;
    else if (row[2] < 0)
        b = 6;
    else
        b = 7;  // error
    return b;
}



/** Converts an orientation matrix made up of 0,+1,and -1 to a scalar representation.
* @param[in] mtx Orientation matrix to convert to a scalar.
* @return Description of orientation matrix. The lowest 2 bits (0 and 1) represent the column the one is on for the
* first row, with the bit number 2 being the sign. The next 2 bits (3 and 4) represent
* the column the one is on for the second row with bit number 5 being the sign.
* The next 2 bits (6 and 7) represent the column the one is on for the third row with
* bit number 8 being the sign. In binary the identity matrix would therefor be:
* 010_001_000 or 0x88 in hex.
*/
unsigned short inv_orientation_matrix_to_scalar(const signed char *mtx)
{

    unsigned short scalar;

    /*
       XYZ  010_001_000 Identity Matrix
       XZY  001_010_000
       YXZ  010_000_001
       YZX  000_010_001
       ZXY  001_000_010
       ZYX  000_001_010
     */

    scalar = inv_row_2_scale(mtx);
    scalar |= inv_row_2_scale(mtx + 3) << 3;
    scalar |= inv_row_2_scale(mtx + 6) << 6;

    return scalar;
}

/** Uses the scalar orientation value to convert from chip frame to body frame
* @param[in] orientation A scalar that represent how to go from chip to body frame
* @param[in] input Input vector, length 3
* @param[out] output Output vector, length 3
*/
void inv_convert_to_body(unsigned short orientation, const int *input, int *output)
{
    output[0] = input[orientation      & 0x03] * SIGNSET(orientation & 0x004);
    output[1] = input[(orientation>>3) & 0x03] * SIGNSET(orientation & 0x020);
    output[2] = input[(orientation>>6) & 0x03] * SIGNSET(orientation & 0x100);
}

/** Uses the scalar orientation value to convert from body frame to chip frame
* @param[in] orientation A scalar that represent how to go from chip to body frame
* @param[in] input Input vector, length 3
* @param[out] output Output vector, length 3
*/
void inv_convert_to_chip(unsigned short orientation, const int *input, int *output)
{
    output[orientation & 0x03]      = input[0] * SIGNSET(orientation & 0x004);
    output[(orientation>>3) & 0x03] = input[1] * SIGNSET(orientation & 0x020);
    output[(orientation>>6) & 0x03] = input[2] * SIGNSET(orientation & 0x100);
}

/** Uses the scalar orientation value to convert from body frame to chip frame
* @param[in] orientation A scalar that represent how to go from chip to body frame
* @param[out] output Output matrix, length 9
*/
void inv_orientation_scalar_to_matrix(unsigned short orientation, int *output)
{
	output[0] =0;
	output[1] =0;
	output[2] =0;
	output[3] =0;
	output[4] =0;
	output[5] =0;
	output[6] =0;
	output[7] =0;
	output[8] =0;


    output[orientation & 0x03]      = SIGNSET(orientation & 0x004);
    output[((orientation>>3) & 0x03)+3] = SIGNSET(orientation & 0x020);
    output[((orientation>>6) & 0x03)+6] = SIGNSET(orientation & 0x100);
}


/** Uses the scalar orientation value to convert from chip frame to body frame and
* apply appropriate scaling.
* @param[in] orientation A scalar that represent how to go from chip to body frame
* @param[in] sensitivity Sensitivity scale
* @param[in] input Input vector, length 3
* @param[out] output Output vector, length 3
*/
void inv_convert_to_body_with_scale(unsigned short orientation,
                                    int sensitivity,
                                    const int *input, int *output)
{
    output[0] = inv_q30_mult(input[orientation & 0x03] *
                             SIGNSET(orientation & 0x004), sensitivity);
    output[1] = inv_q30_mult(input[(orientation>>3) & 0x03] *
                             SIGNSET(orientation & 0x020), sensitivity);
    output[2] = inv_q30_mult(input[(orientation>>6) & 0x03] *
                             SIGNSET(orientation & 0x100), sensitivity);
}

/* Converts Quaternion from chip to body
*  Requires real part
*  (1) q_rotation([1 0 0 0]) (2) q_rotation([sqrt(.5) 0 0 sqrt(.5)])
*  (3) q_rotation([sqrt(.5) 0 0 -sqrt(.5)]) (4) q_rotation([0 1 0 0])
*  (5) q_rotation([0 0 1 0]) (6) q_rotation([0 -sqrt(.5) sqrt(.5) 0])
*  (7) q_rotation([0 sqrt(.5) sqrt(.5) 0]) (8) q_rotation([0 0 0 1])
*/
//#define nexus5
void inv_convert_quaternion_to_body(int *mcb,
									const int *Qc, int *Qb)
{
   int qcb[4];
   float Rcb[9];
   int i;
#ifdef nexus5
   qcb[0] = 0; 
   qcb[1] = 759250125; //0.7071 
   qcb[2] = 759250125;
   qcb[3] = 0;
#else
   for (i=0; i<9; i++)
	   Rcb[i] = (float)mcb[i];
   inv_rotation_to_quaternion(Rcb, qcb);
#endif
   inv_q_mult(Qc, qcb, Qb);
   return;
}

/** find a norm for a vector
* @param[in] a vector [3x1]
* @param[out] output the norm of the input vector
*/
double inv_vector_norm(const float *x)
{
    return sqrt(x[0]*x[0]+x[1]*x[1]+x[2]*x[2]);
}

void inv_init_biquad_filter(inv_biquad_filter_t *pFilter, float *pBiquadCoeff) {
    int i;
    // initial state to zero
    pFilter->state[0] = 0;
    pFilter->state[1] = 0;

    // set up coefficients
    for (i=0; i<5; i++) {
        pFilter->c[i] = pBiquadCoeff[i];
    }
}

void inv_calc_state_to_match_output(inv_biquad_filter_t *pFilter, float input)
{
    pFilter->input = input;
    pFilter->output = input;
    pFilter->state[0] = input / (1 + pFilter->c[2] + pFilter->c[3]);
    pFilter->state[1] = pFilter->state[0];
}

float inv_biquad_filter_process(inv_biquad_filter_t *pFilter, float input)  {
    float stateZero;

    pFilter->input = input;
    // calculate the new state;
    stateZero = pFilter->input - pFilter->c[2]*pFilter->state[0]
                               - pFilter->c[3]*pFilter->state[1];

    pFilter->output = stateZero + pFilter->c[0]*pFilter->state[0]
                                + pFilter->c[1]*pFilter->state[1];

    // update the output and state
    pFilter->output = pFilter->output * pFilter->c[4];
    pFilter->state[1] = pFilter->state[0];
    pFilter->state[0] = stateZero;
    return pFilter->output;
}

void inv_get_cross_product_vec(float *cgcross, float compass[3], float grav[3])  {

    cgcross[0] = (float)compass[1] * grav[2] - (float)compass[2] * grav[1];
    cgcross[1] = (float)compass[2] * grav[0] - (float)compass[0] * grav[2];
    cgcross[2] = (float)compass[0] * grav[1] - (float)compass[1] * grav[0];
}

void mlMatrixVectorMult(int matrix[9], const int vecIn[3], int *vecOut)  {
        // matrix format
        //  [ 0  3  6;
        //    1  4  7;
        //    2  5  8]

        // vector format:  [0  1  2]^T;
        int i, j;
        int temp;

        for (i=0; i<3; i++)	{
                temp = 0;
                for (j=0; j<3; j++)  {
                        temp += inv_q30_mult(matrix[i+j*3], vecIn[j]);
                }
                vecOut[i] = temp;
        }
}

//============== 1/sqrt(x), 1/x, sqrt(x) Functions ================================

/** Calculates 1/square-root of a fixed-point number (30 bit mantissa, positive): Q1.30
* Input must be a positive scaled (2^30) integer
* The number is scaled to lie between a range in which a Newton-Raphson
* iteration works best. Corresponding square root of the power of two is returned.
*  Caller must scale final result by 2^rempow (while avoiding overflow).
* @param[in] x0, length 1
* @param[out] rempow, length 1
* @return scaledSquareRoot on success or zero.
*/
int inv_inverse_sqrt(int x0, int*rempow)
{
	//% Inverse sqrt NR in the neighborhood of 1.3>x>=0.65
	//% x(k+1) = x(k)*(3 - x0*x(k)^2)

	//% Seed equals 1. Works best in this region.
	//xx0 = int32(1*2^30);

	int oneoversqrt2, oneandhalf, x0_2;
	unsigned int xx;
	int pow2, sq2scale, nr_iters;
	//long upscale, sqrt_upscale, upsclimit;
	//long downscale, sqrt_downscale, downsclimit;

	// Precompute some constants for efficiency
	//% int32(2^30*1/sqrt(2))
	oneoversqrt2=759250125L;
	//% int32(1.5*2^30);
	oneandhalf=1610612736L;

	//// Further scaling into optimal region saves one or more NR iterations. Maps into region (.9, 1.1)
	//// int32(0.9/log(2)*2^30)
	//upscale = 1394173804L;
	//// int32(sqrt(0.9/log(2))*2^30)
	//sqrt_upscale = 1223512453L;
	// // int32(1.1*log(2)/.9*2^30)
	//upsclimit = 909652478L;
	//// int32(1.1/log(4)*2^30)
	//downscale = 851995103L;
	//// int32(sqrt(1.1/log(4))*2^30)
	//sqrt_downscale = 956463682L;
	// // int32(0.9*log(4)/1.1*2^30)
	//downsclimit = 1217881829L;

	nr_iters = test_limits_and_scale(&x0, &pow2);

	sq2scale=pow2%2;  // Find remainder. Is it even or odd?

	
	// Further scaling to decrease NR iterations
	// With the mapping below, 89% of calculations will require 2 NR iterations or less.
	// TBD


	x0_2 = x0 >>1; // This scaling incorporates factor of 2 in NR iteration below.
	// Initial condition starts at 1: xx=(1L<<30);
	// First iteration is simple: Instead of initializing xx=1, assign to result of first iteration:
	// xx= (3/2-x0/2);
	//% NR formula: xx=xx*(3/2-x0*xx*xx/2); = xx*(1.5 - (x0/2)*xx*xx)
	// Initialize NR (first iteration). Note we are starting with xx=1, so the first iteration becomes an initialization.
	xx = oneandhalf - x0_2;
 	if ( nr_iters>=2 ) {
		// Second NR iteration
		xx = inv_q30_mult( xx, ( oneandhalf - inv_q30_mult(x0_2, inv_q30_mult(xx,xx) ) ) );
		if ( nr_iters==3 ) {
			// Third NR iteration. 
			xx = inv_q30_mult( xx, ( oneandhalf - inv_q30_mult(x0_2, inv_q30_mult(xx,xx) ) ) );
			// Fourth NR iteration: Not needed due to single precision.
		}
	}
	if (sq2scale) {
		*rempow = (pow2>>1) + 1; // Account for sqrt(2) in denominator, note we multiply if s2scale is true
		return (inv_q30_mult(xx,oneoversqrt2));
	}
	else {
		*rempow = pow2>>1;
		return xx;
	}
}


/** Calculates square-root of a fixed-point number (30 bit mantissa, positive)
* Input must be a positive scaled (2^30) integer
* The number is scaled to lie between a range in which a Newton-Raphson
* iteration works best.
* @param[in] x0, length 1
* @return scaledSquareRoot on success or zero. **/
int inv_fast_sqrt(int x0)
{

	//% Square-Root with NR in the neighborhood of 1.3>x>=0.65 (log(2) <= x <= log(4) )
    // Two-variable NR iteration:
    // Initialize: a=x; c=x-1;  
    // 1st Newton Step:  a=a-a*c/2; ( or: a = x - x*(x-1)/2  )
    // Iterate: c = c*c*(c-3)/4
    //          a = a - a*c/2    --> reevaluating c at this step gives error of approximation

	//% Seed equals 1. Works best in this region.
	//xx0 = int32(1*2^30);

	int sqrt2, oneoversqrt2, one_pt5;
	int xx, cc;
	int pow2, sq2scale, nr_iters;

	// Return if input is zero. Negative should really error out. 
	if (x0 <= 0L) {
		return 0L;
	}

	sqrt2 =1518500250L;
	oneoversqrt2=759250125L;
	one_pt5=1610612736L;

	nr_iters = test_limits_and_scale(&x0, &pow2);
	
	sq2scale = 0;
	if (pow2 > 0) 
		sq2scale=pow2%2;  // Find remainder. Is it even or odd?
	pow2 = pow2-sq2scale; // Now pow2 is even. Note we are adding because result is scaled with sqrt(2)

	// Sqrt 1st NR iteration
	cc = x0 - (1L<<30);
	xx = x0 - (inv_q30_mult(x0, cc)>>1);
 	if ( nr_iters>=2 ) {
		// Sqrt second NR iteration
		// cc = cc*cc*(cc-3)/4; = cc*cc*(cc/2 - 3/2)/2;
		// cc = ( cc*cc*((cc>>1) - onePt5) ) >> 1
		cc = inv_q30_mult( cc, inv_q30_mult(cc, (cc>>1) - one_pt5) ) >> 1;
		xx = xx - (inv_q30_mult(xx, cc)>>1);
		if ( nr_iters==3 ) {
			// Sqrt third NR iteration
			cc = inv_q30_mult( cc, inv_q30_mult(cc, (cc>>1) - one_pt5) ) >> 1;
			xx = xx - (inv_q30_mult(xx, cc)>>1);
		}
	}
	if (sq2scale)
		xx = inv_q30_mult(xx,oneoversqrt2);
	// Scale the number with the half of the power of 2 scaling
	if (pow2>0)
		xx = (xx >> (pow2>>1)); 
	else if (pow2 == -1)
		xx = inv_q30_mult(xx,sqrt2);
	return xx;
}

/** Calculates 1/x of a fixed-point number (30 bit mantissa)
* Input must be a scaled (2^30) integer (+/-)
* The number is scaled to lie between a range in which a Newton-Raphson
* iteration works best. Corresponding multiplier power of two is returned.
*  Caller must scale final result by 2^pow (while avoiding overflow).
* @param[in] x, length 1
* @param[out] pow, length 1
* @return scaledOneOverX on success or zero.
*/
int inv_one_over_x(int x0, int*pow)
{
	//% NR for 1/x in the neighborhood of log(2) => x => log(4)
	//%    y(k+1)=y(k)*(2 \ 96 x0*y(k))
    //% with y(0) = 1 as the starting value for NR

	int two, xx;
	int numberwasnegative, nr_iters, did_upscale, did_downscale;

	int upscale, downscale, upsclimit, downsclimit;

	*pow = 0;
	// Return if input is zero. 
	if (x0 == 0L) {
		return 0L;
	}

	// This is really (2^31-1), i.e. 1.99999... .
	// Approximation error is 1e-9. Note 2^31 will overflow to sign bit, so it can't be used here.
	two = 2147483647L; 

	// int32(0.92/log(2)*2^30)
	upscale = 1425155444L;
	// int32(1.08/upscale*2^30) 
	upsclimit = 873697834L;

	// int32(1.08/log(4)*2^30)
	downscale = 836504283L;
	// int32(0.92/downscale*2^30) 
	downsclimit = 1268000423L;

	// Algorithm is intended to work with positive numbers only. Change sign:
	numberwasnegative = 0;
	if (x0 < 0L) {
		numberwasnegative = 1;
		x0 = -x0;
	}

	nr_iters = test_limits_and_scale(&x0, pow);

	did_upscale=0;
	did_downscale=0;
	// Pre-scaling to reduce NR iterations and improve accuracy:
	if (x0<=upsclimit) {
		x0 = inv_q30_mult(x0, upscale);
		did_upscale = 1;
		// The scaling ALWAYS leaves the number in the 2-NR iterations region:
		nr_iters = 2;
		// Is x0 in the single NR iteration region (0.994, 1.006) ?
		if (x0 > 1067299373 && x0 < 1080184275)
			nr_iters = 1;
	} else if (x0>=downsclimit) {
		x0 = inv_q30_mult(x0, downscale);
		did_downscale = 1;
		// The scaling ALWAYS leaves the number in the 2-NR iterations region:
		nr_iters = 2;
		// Is x0 in the single NR iteration region (0.994, 1.006) ?
		if (x0 > 1067299373 && x0 < 1080184275)
			nr_iters = 1;
	}

	xx = (two - x0) + 1; // Note 2 will overflow so the computation (2-x) is done with "two" == (2^30-1)
	// First NR iteration
	xx = inv_q30_mult( xx, (two - inv_q30_mult(x0, xx)) + 1 );
 	if ( nr_iters>=2 ) {
		// Second NR iteration
		xx = inv_q30_mult( xx, (two - inv_q30_mult(x0, xx)) + 1 );
		if ( nr_iters==3 ) {
			// THird NR iteration. 
			xx = inv_q30_mult( xx, (two - inv_q30_mult(x0, xx)) + 1 );
			// Fourth NR iteration: Not needed due to single precision.
		}
	}

	// Post-scaling
	if (did_upscale)
		xx = inv_q30_mult( xx, upscale);
	else if (did_downscale)
		xx = inv_q30_mult( xx, downscale);

	if (numberwasnegative) 
		xx = -xx;
	return xx;
}

/** Auxiliary function used by inv_OneOverX(), inv_fastSquareRoot(), inv_inverseSqrt().
* Finds the range of the argument, determines the optimal number of Newton-Raphson
* iterations and . Corresponding square root of the power of two is returned.
* Restrictions: Number is represented as Q1.30.
*               Number is betweeen the range 2<x<=0
* @param[in] x, length 1
* @param[out] pow, length 1
* @return # of NR iterations, x0 scaled between log(2) and log(4) and 2^N scaling (N=pow)
*/
int test_limits_and_scale(int *x0, int *pow)
{
	int lowerlimit, upperlimit, oneiterlothr, oneiterhithr, zeroiterlothr, zeroiterhithr;

	// Lower Limit: ll = int32(log(2)*2^30);
	lowerlimit = 744261118L;
	//Upper Limit ul = int32(log(4)*2^30);
	upperlimit = 1488522236L;
	//  int32(0.9*2^30)
	oneiterlothr = 966367642L;
	// int32(1.1*2^30)
	oneiterhithr = 1181116006L;
	// int32(0.99*2^30)
	zeroiterlothr=1063004406L;
	//int32(1.01*2^30)
	zeroiterhithr=1084479242L;

	// Scale number such that Newton Raphson iteration works best:
	// Find the power of two scaling that leaves the number in the optimal range,
	// ll <= number <= ul. Note odd powers have special scaling further below
	if (*x0 > upperlimit) {
		// Halving the number will push it in the optimal range since largest value is 2
		*x0 = *x0>>1;
		*pow=-1;
	} else if (*x0 < lowerlimit) {
		// Find position of highest bit, counting from left, and scale number 
		*pow=get_highest_bit_position((unsigned int*)x0);
		if (*x0 >= upperlimit) {
			// Halving the number will push it in the optimal range
			*x0 = *x0>>1;
			*pow=*pow-1;
		}
		else if (*x0 < lowerlimit) {
			// Doubling the number will push it in the optimal range
			*x0 = *x0<<1;
			*pow=*pow+1;
		}
	} else {
		*pow = 0;
	}

	if ( *x0<oneiterlothr || *x0>oneiterhithr )
		return 3; // 3 NR iterations
	if ( *x0<zeroiterlothr || *x0>zeroiterhithr )
		return 2; // 2 NR iteration

	return 1; // 1 NR iteration
}

/** Auxiliary function used by testLimitsAndScale()
* Find the highest nonzero bit in an unsigned 32 bit integer:
* @param[in] value, length 1.
* @return highes bit position.
**/int get_highest_bit_position(unsigned int *value)
{
    int position;
    position = 0;
    if (*value == 0) return 0;

    if ((*value & 0xFFFF0000) == 0) {
		position += 16;
		*value=*value<<16;
	}
    if ((*value & 0xFF000000) == 0) {
		position += 8;
		*value=*value<<8;
	}
    if ((*value & 0xF0000000) == 0) {
		position += 4;
		*value=*value<<4;
	}
    if ((*value & 0xC0000000) == 0) {
		position += 2;
		*value=*value<<2;
	}

	// If we got too far into sign bit, shift back. Note we are using an
	// unsigned long here, so right shift is going to shift all the bits.
    if ((*value & 0x80000000)) { 
		position -= 1;
		*value=*value>>1;
	}
    return position;
}

int inv_fastsine29(int x)
{
    // http://devmaster.net/forums/topic/4648-fast-and-accurate-sinecosine/
    // Method is based on a parabola fit for the sine function. Q, P are chosen to minimize error.
    // Input argument, constants and calculations are Q29. Result is promoted to Q30 before return.
    // Argument (scaled Q29) must reside between (-pi, pi).
    int two_over_pi = 341782638L; // int32(2/pi*2^29)
    int Q = 416074957L; // = int32(0.775*2^29)
    int P = 120795955L; // = int32(0.225*2^29)
    int small_angle = 100448548L; // = int32(0.1871*2^29) Angle threshold for better accuracy, about 10.7 deg.
    int y, absx;
    
    // Early cop-out for smaller angles:
    if (ABS(x) < small_angle)
        return x<<1; // Promote to 30 bits. Note this is a signed-int shift.
    // First iteration
    // y = x*(B  - C * abs(x))  Scale. As a result B=2, C=1.
    // y = x*(2 - abs(x)) after scaling.
    x = inv_q29_mult(x, two_over_pi); //
    absx = ABS(x);
    y = inv_q29_mult( x, (2L<<29) - absx );
    // Second iteration
    //  %y = Q * y + P * y * abs(y) = y *(Q + P*abs(y))
    y = inv_q29_mult( y, Q + inv_q29_mult( P, ABS(y) ) );

    return y<<1;// Promote to 30 bits. Note this is a signed-int shift.
}

int inv_fastcosine29(int x)
{
    // Input argument, constants and calculations are Q29. Result is promoted to Q30 before return  
    int pi_over_2 = 843314857L;

    if (x < 0)
        x = x + pi_over_2;
    else
        x = pi_over_2 - x;

    return inv_fastsine29(x);
}

/* compute real part of quaternion, element[0]
@param[in]  inQuat, 3 elements gyro quaternion
@param[out] outquat, 4 elements gyro quaternion
*/
int inv_compute_scalar_part(const int * inQuat, int* outQuat)
{
    int scalarPart = 0;

    scalarPart = inv_fast_sqrt((1L<<30) - inv_q30_mult(inQuat[0], inQuat[0])
                                        - inv_q30_mult(inQuat[1], inQuat[1])
                                        - inv_q30_mult(inQuat[2], inQuat[2]) );
                outQuat[0] = scalarPart;
                outQuat[1] = inQuat[0];
                outQuat[2] = inQuat[1];
                outQuat[3] = inQuat[2];

                return 0;
}
//=====================================================================//
// FIXED POINT MATH FUNCTIONS IN Q15 AND Q30
//
// Q15 MULTIPLY FUNCTION
/** Performs a multiply and shift by 15. 
* This function is to be moved to ml_math_func.c
* @param[in] a
* @param[in] b
* @return ((long long)a*b)>>15
*/
int inv_q15_mult(int a, int b)
{
	int out = (int)(((long long)a * (long long)b) >> 15);
	return out;
}

//% 1/sqrt(x)
//% x(k+1) = x(k)*(3 - x0*x(k)^2)
// THIS VERSION IMPLEMENTED FOR Q15 Fixed-point
//% Code scales number and performs NR in the neighborhood of 1.3>x>=0.65
int inverse_sqrt_q15(int x)
{
    int oneoversqrt2 = 23170L; // int32(2^15*1/sqrt(2))
    int oneandhalf = 49152L; // int32(1.5*2^15);
    int upperlimit = 45426; // int32(log(4)*2^15);
    int lowerlimit = 22713; // int32(log(2)*2^15); 
    int xx, x0_2, invsqrtx;
    int pow2;

    if (x <= 0)
        return 0L;

    pow2 = 0;
    xx = x;
    if (xx > upperlimit) {
downscale:
        if (xx > upperlimit) {
            xx = xx/2;
            pow2 = pow2 - 1;
            goto downscale;
        }
        goto newton_raphson;
    }

    if (xx < lowerlimit) {
upscale:
        if (xx < lowerlimit) {
            xx = xx*2;
            pow2 = pow2 + 1;
            goto upscale;
        }
        goto newton_raphson;
    }

newton_raphson:
    // 3 NR iterations. In some cases second and/or third iteration may not be needed, however
    // for code simplicity always iterate three times. Fourth iteration is below bit precision.
    x0_2 = xx >>1;
    xx = oneandhalf - x0_2;
    xx = inv_q15_mult( xx, ( oneandhalf - inv_q15_mult(x0_2, inv_q15_mult(xx,xx) ) ) );
    xx = inv_q15_mult( xx, ( oneandhalf - inv_q15_mult(x0_2, inv_q15_mult(xx,xx) ) ) );

    if (pow2 & 1) { // This checks if the number is even or odd.
        pow2 = (pow2>>1) + 1; // Account for sqrt(2) in denominator
        invsqrtx = (inv_q15_mult(xx,oneoversqrt2));
    }
    else {
        pow2 = pow2>>1;
        invsqrtx =  xx;
    }

    if (pow2 < 0)
        invsqrtx = invsqrtx>>ABS(pow2);
    else if (pow2>0)
        invsqrtx = invsqrtx <<pow2;

    return invsqrtx;
}


//% Square-Root:
// This code calls 1/sqrt(x) and multiplies result with x, i.e. sqrt(x)=x*(1/sqrt(x).
// See the implementation of 1/sqrt(x) in inverse_sqrt_q15(). 
// THIS VERSION IMPLEMENTED FOR Q15 Fixed-point
int sqrt_fun_q15(int x)
{
    int sqrtx;
    if (x <= 0L) {
        sqrtx = 0L;
        return sqrtx;
    }
    sqrtx = inverse_sqrt_q15 (x); // invsqrtx

    sqrtx = inv_q15_mult(x, sqrtx);

    return sqrtx;
}


//reciprocal_fun_q15:
// Reciprocal function based on Newton-Raphson 1/sqrt(x) calculation
// THIS VERSION IMPLEMENTED FOR Q15 Fixed-point
int reciprocal_fun_q15(int x)
{
    int y;
    int negx;

	if (x == 0) {
		y = 0L;
		return y;
	}

    negx=0;
    if (x < 0 ) {
        x = -x;
        negx = 1;
    }

	if(x >= 1073741824L) { // 2^15 in Q15; underflow number
        if (negx)
            y=-1L;
        else
            y = 1L;
		return y;
	}

    y = inverse_sqrt_q15 (x); // sqrt(y)
    y = inv_q15_mult(y, y);

    if (negx)
        y=-y;
    return y;
}


// ATAN2() Chebychev polynomial approximation.
// For polynomial orders 3, 5, 7, use:
// constA3 = int32(2^15*[0.970562748477141, -0.189514164974601]); % 3rd order
// constA5 = int32(2^15*[0.994949366116654,-0.287060635532652,0.078037176446441]); %5th order
// constA7 = int32(2^15*[0.999133448222780 -0.320533292381664 0.144982490144465,-0.038254464970299]); %7th order
// AND  use corresponding multiplications
// 3rd   Z = q15_mult((constA3(1) + q15_mult(constA3(2), tmp2)), tmp);
// 5th:  Z = q15_mult((constA5(1) + q15_mult((constA5(2) + q15_mult(constA5(3), tmp2)), tmp2)), tmp);
// 7th:  Z = q15_mult((constA7(1) + ...
//           q15_mult((constA7(2) + ...
//           q15_mult((constA7(3) + ...
//           q15_mult(constA7(4), tmp2)), tmp2)), tmp2)), tmp);
// 3rd Order Accuracy is +/-0.3 degrees except near the origin (avoid when both args X and Y less than +/-0.03 simultaneously)
// 7th Order Accuracy is +/-0.02 degrees (worst case) through entire range (accomplished with scaling)
//
// This code depends on:
//    reciprocal_fun_q15()
//       inverse_sqrt_q15()
//    inv_q15_mult()
//
/** Third, fifth or seventh order Chebychev polynomial approximation in Q15 (or 5th order).
* @param[in] Y input of atan2(Y, X) in Q15
* @param[in] X input of atan2(Y, X) in Q15
* @param[out] output angle in radians, Q15
*/
int atan2_q15(int Y, int X) 
{
    int absy, absx, maxABS, tmp, tmp2, tmp3, Z, angle;
    //static long constA3[2] = {31803,-6210};      //int32(2^15*[0.970562748477141, -0.189514164974601]); % 3rd order
    //static long constA5[3] = {32603,-9406,2557}; //int32(2^15*[0.994949366116654,-0.287060635532652,0.078037176446441]); %5th order
    static int constA7[4] = {32740, -10503,  4751, -1254}; // int32(2^15*[0.999133448222780 -0.320533292381664 0.144982490144465,-0.038254464970299]); %7th order
    static int PI15 = 102944; // int32(2^15*pi): pi in Q15

    absx=ABS(X);
    absy=ABS(Y);
    // 3rd order only:
    //if (absx<10000L && absy<10000L) { // args X and Y less than +/-0.03 simultaneously. Ill conditioned.
    //    return = 0L;
    //}
    // When one argument is zero:
    // These can be eliminated to save code space. Code works correctly 
    // under these conditions
    //if (X == 0L) {
    //    if (Y == 0L)
    //        return 0L; // Undefined, but we return zero.
    //    if (Y > 0L)
    //        return PI15/2;
    //    if (Y < 0L)
    //        return -PI15/2;
    //}

    //if (Y == 0L) {
    //    if (X > 0L)
    //        return 0L;
    //    if (X < 0L)
    //        return -PI15;
    //}

    maxABS=MAX(absx, absy);
    // SCALE arguments down to protect from roundoff loss due to 1/x operation.
    //% Threshold for scaling found by numericaly simulating arguments
    //% to yield optimal (minimal) error of less than 0.01 deg through
    //% entire range (for Chebycheff order 7).
//    while ( maxABS >> 13) {  --> Or it can be done this way if DMP code is more efficient
    while ( maxABS > 8192L) {
            maxABS=maxABS/2;
            absx=absx/2;
            absy=absy/2;
    }

    {
        if (absx >= absy) // (0, pi/4]: tmp = abs(y)/abs(x);
            tmp = inv_q15_mult(absy, reciprocal_fun_q15(absx));
        else             // (pi/4, pi/2): tmp = abs(x)/abs(y);
            tmp = inv_q15_mult(absx, reciprocal_fun_q15(absy));

        tmp2=inv_q15_mult(tmp, tmp);
        // 3rd order
        // Z = inv_q15_mult((constA3[0] + inv_q15_mult(constA3[1], tmp2)), tmp);
        // 5th order
        // Z = inv_q15_mult((constA5[0] + inv_q15_mult((constA5[1] + inv_q15_mult(constA5[2], tmp2)), tmp2)), tmp);
        // 7th order
         //Z = inv_q15_mult((constA7[0] + 
         //    inv_q15_mult((constA7[1] + 
         //    inv_q15_mult((constA7[2] + 
         //    inv_q15_mult( constA7[3], tmp2)), tmp2)), tmp2)), tmp);
         // Alternatively:
        tmp3 = inv_q15_mult(constA7[3], tmp2);
        tmp3 = inv_q15_mult(constA7[2] + tmp3, tmp2);
        tmp3 = inv_q15_mult(constA7[1] + tmp3, tmp2);
        Z    = inv_q15_mult(constA7[0] + tmp3, tmp);

        if (absx < absy)
            Z = PI15/2 - Z;

        if (X < 0) { // second and third quadrant
            if (Y < 0)
                Z = -PI15 + Z;
            else
                Z = PI15 - Z;
        }
        else { // fourth quadrant
            if (Y < 0)
                Z = -Z;
        }
        angle = Z; // Note the result is angle in radians, expressed in Q15.
    }
    return angle;
}


int inv_fastsine_q15(int x)
{
    // http://devmaster.net/forums/topic/4648-fast-and-accurate-sinecosine/
    // Method is based on a parabola fit for the sine function. Q, P are chosen to minimize error.
    // Input argument, constants and calculations are Q15. 
    // Argument (scaled Q15) must reside between (-pi, pi).
    // For more accurate results, use the Q29 version.
    int PI15 = 102944L;
    int two_over_pi = 20861L; // int32(2/pi*2^15)
    int Q = 25395L; // = int32(0.775*2^15)
    int P = 7373L; // = int32(0.225*2^15)
    int small_angle = 6131L; // = int32(0.1871*2^15) Angle threshold for better accuracy, about 10.7 deg.
    int y, absx;
    
    // Early cop-out for smaller angles:
    if (ABS(x) < small_angle)
        return x; 
    // Fix for arguments outside (-pi, pi)
    if (ABS(x) > PI15)
        x = x%(2*PI15);
    if (x>PI15)
        x = x - 2*PI15;
    if (x<-102944L)
        x = x + 2*PI15;
    // First iteration
    // y = x*(B  - C * abs(x))  Scale. As a result B=2, C=1.
    // y = x*(2 - abs(x)) after scaling.
    x = inv_q15_mult(x, two_over_pi); //
    absx = ABS(x);
    y = inv_q15_mult( x, (2L<<15) - absx );
    // Second iteration
    //  %y = Q * y + P * y * abs(y) = y *(Q + P*abs(y))
    y = inv_q15_mult( y, Q + inv_q15_mult( P, ABS(y) ) );

    return y;
}

int inv_fastcosine_q15(int x)
{
    int pi_over_2 = 51472L; // int32(pi/2*2^15)
    int PI15 = 102944L;
    // Fix for arguments larger than (-pi, pi)
    if (ABS(x) > PI15)
        x = x%(2*PI15);
    if (x>PI15)
        x = x - 2*PI15;
    if (x<-102944L)
        x = x + 2*PI15;

    if (x < 0)
        x = x + pi_over_2;
    else
        x = pi_over_2 - x;

    return inv_fastsine_q15(x);
}


//====================================================
// Q30 Functions
//===================================================

int inverse_sqrt_q30(int x, int *pow2)
{
    int oneoversqrt2 = 759250125L; // int32(2^30*1/sqrt(2))
    int oneandhalf = 1610612736L; // int32(1.5*2^30);
    int upperlimit = 1488522236; // int32(log(4)*2^30);
    int lowerlimit = 744261118; // int32(log(2)*2^30); 
    int xx, x0_2, invsqrtx;

    *pow2 = 0;
	if (x <= 0) {
        return 1L<<30;
	}

    xx = x;
    if (xx > upperlimit) {
downscale:
        if (xx > upperlimit) {
            xx = xx/2;
            *pow2 = *pow2 - 1;
            goto downscale;
        }
    }

    if (xx < lowerlimit) {
upscale:
        if (xx < lowerlimit) {
            xx = xx*2;
            *pow2 = *pow2 + 1;
            goto upscale;
        }
    }

newton_raphson:
    // 3 NR iterations. In some cases second and/or third iteration may not be needed, however
    // for code simplicity always iterate three times. Fourth iteration is below bit precision.
    x0_2 = xx >>1;
    xx = oneandhalf - x0_2;
    xx = inv_q30_mult( xx, ( oneandhalf - inv_q30_mult(x0_2, inv_q30_mult(xx,xx) ) ) );
    xx = inv_q30_mult( xx, ( oneandhalf - inv_q30_mult(x0_2, inv_q30_mult(xx,xx) ) ) );

    if (*pow2 & 1) { // This checks if the number is even or odd.
        *pow2 = (*pow2>>1) + 1; // Account for sqrt(2) in denominator
        invsqrtx = (inv_q30_mult(xx,oneoversqrt2));
    }
    else {
        *pow2 = *pow2>>1;
        invsqrtx =  xx;
    }

    return invsqrtx;
}


//% Square-Root:
// This code calls 1/sqrt(x) and multiplies result with x, i.e. sqrt(x)=x*(1/sqrt(x).
// See the implementation of 1/sqrt(x) in inverse_sqrt_q15(). 
// THIS VERSION IMPLEMENTED FOR Q15 Fixed-point
int sqrt_fun_q30(int x)
{
    int sqrtx;
    int pow2;

    if (x <= 0L) {
        sqrtx = 0L;
        return sqrtx;
    }
    sqrtx = inverse_sqrt_q30(x, &pow2); // invsqrtx

    sqrtx = inv_q30_mult(x, sqrtx);

power_up:
    if (pow2 > 0) {
        sqrtx = 2*sqrtx;
        pow2=pow2-1;
        goto power_up;
    }
power_down:
    if (pow2 < 0) {
        sqrtx = sqrtx/2;
        pow2=pow2+1;
        goto power_down;
    }

    return sqrtx;
}

/** c = reciprocal_fun_q30( b, &pow2 ); Reciprocal function based on Newton-Raphson 1/sqrt(x) calculation
INPUTS
 b - the q30 number we are trying to divide by

OUTPUTS
 c - the 1/b result in q30 downshifted by pow2:
 pow2 - a power of 2 by which 1/b is downshifted to fit in q30.

LIMITS
 Note that upshifting c by pow2 right away will overflow q30 if b<0.5 in q30 (=536870912)
 so if you are doing some multiplication later on (like a/b), then it might be better
 to do q30_mult(a,c) first and then shift it up by pow2: q30_mult(a,c)<<pow2
 The result might still overflow in some cases (large a, small b: a=1073741824, b=1
 but precise limits of the overflow are tbd).

EXAMPLE
 //we want to find a/b, where a and b are very small. Hence 1/b will 
 //definitely overflow q30, but downshifted 1/b won't. So just remember 
 //to multiply 1/b by a first and only then upshift by pow2:
 long a = 1024; //1e-6 in q30
 long b = 2048; //2e-6 in q30
 long c; //the scaled result of 1/b in pow2 format
 int pow2; //exponent storage
 long d; //the result of a/b (in this example should be 0.5 in q30)
 c = reciprocal_fun_q30( b, &pow2 ); //c=536870912, pow2=20 (ie .5*2^30*2^20, much greater than q30 limit)
 d = inv_q30_mult(a,c) << pow2; //as expected d=536870912 (ie 0.5 in q30)
*/
int reciprocal_fun_q30(int x, int *pow2)
{
    int y;
    int negx;

	if (x == 0) {
		y = 0L;
        *pow2 = 0;
		return y;
	}

    negx=0;
    if (x < 0 ) {
        x = -x;
        negx = 1;
    }

    y = inverse_sqrt_q30 (x, pow2); // sqrt(y)
    y = inv_q30_mult(y, y);
    *pow2 = *pow2*2;  // Must double exponent due to multiply 

    if (negx)
        y=-y;
    return y;
}

// Division algorithm. Divide num by den.
// Algorithm retains full precision by  intelligent scaling.
// Precision loss for very small arguments is avoided.
// The result is scaled by some power-of-two. Caller needs to handle this. 
// Inputs are in the range [-2, 2). 
// Output is N*2^e where N is in the range [-2, 2). Number is scaled with exponent e.
int num_over_den_q30(int num, int den, int *pow2)
{
    int oneoverDen;
    int i, neg;
    int one = 1L<<30; // int32(2^31-1) to avoid overflow
    int onehalf = 536870912L; // int32(2^29)

    *pow2 = 0;
    if (den == 0) {  // Return with zero.
		return 0L;
	}

    neg = 0;
    if  (num < 0 && den > 0 ) {
        neg = 1;
        num = -num;
    }
    else if (num > 0 && den < 0 ) {
        neg = 1;
        den = -den;
    }

    if (den == one) {  // Return with zero.
		return num;
	}

    oneoverDen = reciprocal_fun_q30(den, pow2);

    while (num <= onehalf) {
        num = num<<1;
        *pow2 = *pow2 - 1;
    }
    while (num > onehalf) {
        num = num>>1;
        *pow2 = *pow2 + 1;
    }
    num = inv_q30_mult(num, oneoverDen);
    // The above while loops guarantee the result of 
    // the division will be always less than One.
    
    if (neg)
        num=-num;

    return num;
}

/**
 * @}
 */
