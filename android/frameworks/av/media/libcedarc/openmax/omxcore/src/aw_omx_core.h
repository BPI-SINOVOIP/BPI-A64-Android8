/*
* Copyright (c) 2008-2016 Allwinner Technology Co. Ltd.
* All rights reserved.
*
* File : Aw_omx_core.h
* Description :
* History :
*   Author  : AL3
*   Date    : 2013/05/05
*   Comment : complete the design code
*/

/*============================================================================
                            O p e n M A X   w r a p p e r s
                             O p e n  M A X   C o r e

 This module contains the definitions of the OpenMAX core.

*//*========================================================================*/

#ifndef AW_OMX_CORE_H
#define AW_OMX_CORE_H

#include "aw_omx_common.h"        // OMX API
#include <string.h>

/*
 * OMX_COMP_MAX_INST: the max number of codecs can be created in the same time.
 * the value should be same as the property "concurrent-instances" in /vendor/etc/media_codecs.xml.
 * <Limit name="concurrent-instances" max="4" />.
 * The cts test case testGetMaxSupportedInstances failed sometimes in low-memory devices,
 * because there is no enough buffer to create eight codecs, then set the value to 4.
 */
#define OMX_COMP_MAX_INST 4

typedef struct _omx_core_cb_type
{
    const char*                   name;                            // Component name
    create_aw_omx_component fn_ptr;                            // create instance fn ptr
    void*                   inst[OMX_COMP_MAX_INST];        // Instance handle
    void*                   so_lib_handle;                    // So Library handle
    const char*                   so_lib_name;                    // so directory
    const char*                   roles[OMX_CORE_MAX_CMP_ROLES];    // roles played
}omx_core_cb_type;

typedef struct VideoOMXConfigParserOutput
{
    OMX_U32 width;
    OMX_U32 height;
    OMX_U32 profile;
    OMX_U32 level;
}VideoOMXConfigParserOutputs;

typedef struct OMXConfigParserInput
{
    OMX_U8* inPtr;                 //pointer to codec configuration header
    OMX_U32 inBytes;               //length of codec configuration header
    OMX_STRING cComponentRole;     //OMX component codec type
    OMX_STRING cComponentName;  //OMX component name
}OMXConfigParserInputs;

#endif

