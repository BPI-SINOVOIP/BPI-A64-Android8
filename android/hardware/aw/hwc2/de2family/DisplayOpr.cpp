/*
 * Copyright (C) 2011 The Android Open Source Project
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

#include "../hwc.h"
#include <hardware/hardware.h>
#include <hardware/hwcomposer2.h>
#include <cutils/log.h>
#include <system/graphics.h>
#include <cutils/list.h>
#include <stdlib.h>
#include <sys/ioctl.h>
#include <linux/fb.h>
#include <fcntl.h>
#include <EGL/egl.h>
#include <hardware/hal_public.h>
#include <pthread.h>
#include <hardware/sunxi_metadata_def.h>
#include <sys/resource.h>
#include <utils/Trace.h>
#include <cutils/properties.h>
#include <math.h>

#ifdef HOMLET_PLATFORM
#include "other/homlet.h"
#endif

#define ION_IOC_SUNXI_PHYS_ADDR 7
#define HAL_PIXEL_FORMAT_AW_NV12 0x101
#define PIPE_NUM 4
#define DE_NUM 2
#define LAYER_BY_PIPE 4

#define VI_NUM 1
/*limited by atw hardware*/
#define ATW_DEVICE_MAX_EYE_BUFFER_WIDTH 1280

using namespace android;

extern DisplayOpr_t sunxiDisplayOpr;
bool isOutOfLineBuffer = false;

typedef enum PipeType {
	NOTASSIGN = 0,
	VIDEO,
	UI,
	UI_NO_ALPHA,
} PipeType_t;

typedef struct PipeInfo {
	int layNum;
	PipeType_t type;
	float planeAlpha;
	Layer_t *hwcLayer[LAYER_BY_PIPE];//Z-Order up
	float scaleW;
	float scaleH;
	int curentMem;
}PipeInfo_t;

typedef struct DisplayPrivate {
	hwc_rect_t ScreenCutOff;//left is cutted pixels from left,etc.
	struct disp_output type;
}DisplayPrivate_t;
/*
  * DELayerPrivate_t all the hw assigned data
  */
typedef struct DELayerPrivate {
	int pipe;
	int layerId;
	int zOrder;
	float pipeScaleW;
	float pipeScaleH;
}DELayerPrivate_t;

/*
 * hardware Layout:
 * 0~fixVideoPipeNum-1 is video channel;
 * fixVideoPipeNum ~ fixPipeNumber-1 is UI channel;
 * Pipe[PIPE_NUM]:Z-Order is up.
 */
typedef struct DESource {
	int fixPipeNumber;
	int fixVideoPipeNum;
	int currentPipe;
	int usedViPipe;
	int usedPiexlAlpha;
	PipeInfo_t *Pipe;
	disp_layer_config2 *layerInfo;
	DisplayConfig_t currentConfig;
} DESource_t;

int dispFd;
int tdFd;
long long deFreq = 254000000;

DESource_t DESource[DE_NUM];

char displayName[5][10] = {
{"lcd"},
{"hdmi"},
{"tv"},
{"vga"},
{"default"},
};

tv_para_t hdmi_support[]=
{
	/* 1'st is default */
	{DISP_TV_MOD_1080P_60HZ,       1920,   1080, 60, 0},
	{DISP_TV_MOD_720P_60HZ,        1280,   720,  60, 0},

	{DISP_TV_MOD_480I,             720,    480, 60, 0},
	{DISP_TV_MOD_576I,             720,    576, 60, 0},
	{DISP_TV_MOD_480P,             720,    480, 60, 0},
	{DISP_TV_MOD_576P,             720,    576, 60, 0},
	{DISP_TV_MOD_720P_50HZ,        1280,   720, 50, 0},

	{DISP_TV_MOD_1080P_24HZ,       1920,   1080, 24, 0},
	{DISP_TV_MOD_1080P_50HZ,       1920,   1080, 50, 0},

	{DISP_TV_MOD_1080I_50HZ,       1920,   1080, 50, 0},
	{DISP_TV_MOD_1080I_60HZ,       1920,   1080, 60, 0},
	{DISP_TV_MOD_3840_2160P_25HZ,  3840,   2160, 25, 0},
	{DISP_TV_MOD_3840_2160P_24HZ,  3840,   2160, 24, 0},
	{DISP_TV_MOD_3840_2160P_30HZ,  3840,   2160, 30, 0},
	{DISP_TV_MOD_3840_2160P_60HZ,  3840,   2160, 60, 0},
	{DISP_TV_MOD_3840_2160P_50HZ,  3840,   2160, 50, 0},
	{DISP_TV_MOD_4096_2160P_60HZ,  4096,   2160, 60, 0},
	{DISP_TV_MOD_4096_2160P_50HZ,  4096,   2160, 50, 0},
	{DISP_TV_MOD_1080P_24HZ_3D_FP, 1920,   1080, 24, 0},
	{DISP_TV_MOD_720P_50HZ_3D_FP,  1280,   720, 50, 0},
	{DISP_TV_MOD_720P_60HZ_3D_FP,  1280,   720, 60, 0},
};

enum display_3d_mode hdmi_3D_mode = DISPLAY_2D_ORIGINAL;

extern char *dumpchar;

#define arraySizeOf(array) sizeof(array)/sizeof(array[0])

static inline DisplayPrivate_t* toHwDisplay(Display_t *display) {
	DisplayPrivate_t *hwDisplay = NULL;
	hwDisplay = (DisplayPrivate_t*)display->data;
	return hwDisplay;
}

static inline DELayerPrivate_t* toHwLayer(Layer_t *layer) {
	DELayerPrivate_t* hwLayer = NULL;
	hwLayer = (DELayerPrivate_t*)layer->data;
	return hwLayer;
}

static inline DisplayConfigPrivate_t* toHwConfig(DisplayConfig_t *config) {
	DisplayConfigPrivate_t* hwconfig = NULL;
	hwconfig = (DisplayConfigPrivate_t*)config->data;
	return hwconfig;
}

void calcGlobleLayerFactor(Display_t *display, Layer_t *layer, float *width, float *hight)
{
	DisplayPrivate_t *hwdiplay;
	DisplayConfig *config;
	DisplayConfigPrivate_t *hwConfig;
	float screenW, screenH;
	float layerW, layerH;

	config = display->displayConfigList[display->activeConfigId];

	hwConfig = toHwConfig(config);
	screenW = hwConfig->screenDisplay.right - hwConfig->screenDisplay.left;
	screenH =  hwConfig->screenDisplay.bottom -  hwConfig->screenDisplay.top;
	screenW /= config->width;
	screenH /= config->height;
	calcLayerFactor(layer, &layerW, &layerH);

	*width = screenW * layerW;
	*hight =  screenH * layerH;

}

bool layerCanScale(long long deFreq, Display_t *display, Layer_t *layer)
{
	DisplayPrivate_t *hwdiplay;
	DisplayConfig *config;
	DisplayConfigPrivate_t *hwConfig;
	long long layerLinePeroid, screenLinePeroid,vsyncPeroid;
	int srcW, srcH, swap, screenW, dstW;
	float widthScale, hightScale;

	config = display->displayConfigList[display->activeConfigId];

	hwConfig = toHwConfig(config);
	screenW = hwConfig->screenDisplay.right - hwConfig->screenDisplay.left;
	calcGlobleLayerFactor(display, layer, &widthScale, &hightScale);
	if (!layerIsVideo(layer)) {
#if (TARGET_BOARD_PLATFORM != petrel)
		return config->width < 2048;
#endif
	}

	srcW = layer->crop.right - layer->crop.left;
	srcH = layer->crop.bottom - layer->crop.top;
	if(layer->transform & HAL_TRANSFORM_ROT_90) {
		swap = srcW;
		srcW = srcH;
		srcH = swap;
	}

	dstW = (int)ceilf(srcW * widthScale);
	vsyncPeroid = (config->vsyncPeriod ? config->vsyncPeriod : (1000000000/60));

	screenLinePeroid = vsyncPeroid / config->height;
	layerLinePeroid = (srcW > dstW) ? (1000000*((long long)(screenW - dstW + srcW))/(deFreq/1000))
				: (1000000*((long long)screenW)/(deFreq/1000));

	if((screenLinePeroid *4/5) < layerLinePeroid)
		return 0; //can't
	else
		return 1;//can

}


#define max(a,b) ((a) > (b) ? (a) : (b))
#define min(a,b) ((a) < (b) ? (a) : (b))

/*
 * check if de can handle ui scale.
 *
 * input params:
 * ovlw, ovlh : source size
 * outw, outh : frame size
 * lcdw, lcdh : screen size
 * lcd_fps : frame rate in Hz
 * de_freq : de clock freqence in Hz
 *
 * return:
 * 1 -- can use de scale
 * 0 -- can NOT use de scale
 */
static int de_scale_capability_detect(
            unsigned int ovlw, unsigned int ovlh,
            unsigned int outw, unsigned int outh,
            unsigned long long lcdw,
            unsigned long long lcdh,
            unsigned long long lcd_fps,
            unsigned long long de_freq)
{
	    unsigned long long de_freq_req;
	    unsigned long long lcd_freq;
	    unsigned long long layer_cycle_num;
	    unsigned int dram_efficience = 95;

	    /*
	     *  ovlh > outh : vertical scale down
	     *  ovlh < outh : vertical scale up
	     */
	    if (ovlh > outh)
	        layer_cycle_num = max(ovlw, outw) + max((ovlh - outh) * ovlw / outh, lcdw - outw);
	    else
	        layer_cycle_num = max(ovlw, outw) + (lcdw - outw);

	    lcd_freq = (lcdh) * (lcdw) * (lcd_fps) * 10 / 9;
	    de_freq_req = lcd_freq * layer_cycle_num * 100 / dram_efficience;
	    de_freq_req = de_freq_req / lcdw;

	    if (de_freq > de_freq_req)
	        return 1;
	    else
	        return 0;
	}

static int hwc_ui_scaler_check(Display_t *display, Layer_t *layer)
{
	int src_w = ceil(layer->crop.right  - layer->crop.left);
	int src_h = ceil(layer->crop.bottom - layer->crop.top);
	int out_w = layer->frame.right  - layer->frame.left;
	int out_h = layer->frame.bottom - layer->frame.top;
	DisplayConfig_t *dispconfig;

	dispconfig = display->displayConfigList[display->activeConfigId];
	int InitDisplayWidth = dispconfig->width;
	int InitDisplayHeight = dispconfig->height;

	out_w = out_w * display->hpercent * display->VarDisplayWidth  / InitDisplayWidth  / 100;
	out_h = out_h * display->vpercent * display->VarDisplayHeight / InitDisplayHeight / 100;

	out_w = out_w == 0 ? 1 : out_w;
	out_h = out_h == 0 ? 1 : out_h;

	return de_scale_capability_detect(src_w, src_h, out_w, out_h,
					display->VarDisplayWidth,
					display->VarDisplayHeight, 60, 696000000);
}


static inline void resetLayer(Layer_t *layer)
{
	DELayerPrivate_t *deLayer;
	deLayer = toHwLayer(layer);

	deLayer->pipe = -1;
	deLayer->layerId = -1;
	deLayer->zOrder = -1;
	layer->typeChange = 0;
	layer->clearClientTarget = 0;
	layer->duetoFlag = HWC_LAYER;
	if (layer->compositionType == HWC2_COMPOSITION_CLIENT_TARGET)
		layer->duetoFlag = NOT_ASSIGN;
}

static void resetHwPipe(Display_t *display, int from, int to, bool first)
{
	int i = 0, j = 0, cutVi = 0, cutUIAlpha = 0;
	Layer_t *layer;
	DESource_t *hw = &DESource[display->displayId];

	if (to >= hw->fixPipeNumber)
		to = hw->fixPipeNumber - 1;

	for (i = from; i <= to; i++) {
		if (hw->Pipe[i].type == VIDEO)
			cutVi++;
		if (hw->Pipe[i].type == UI)
			cutUIAlpha++;
		hw->Pipe[i].type = NOTASSIGN;
		hw->Pipe[i].planeAlpha = 1.0;
		hw->Pipe[i].scaleH = 1.0;
		hw->Pipe[i].scaleW = 1.0;
		for (j = 0; j < hw->Pipe[i].layNum; j++) {
			layer = hw->Pipe[i].hwcLayer[j];
			hw->Pipe[i].hwcLayer[j] = NULL;
			if (layer != NULL && !first) {
				memCtrlDealLayer(layer, false);
				resetLayer(layer);
			}
		}
		if (!first)
			memCtrlDelCur(hw->Pipe[i].curentMem);
		hw->Pipe[i].layNum = 0;
		hw->Pipe[i].curentMem = 0;
	}
	if (from == 0) {
		hw->usedViPipe = 0;
		hw->currentPipe = 0;
		hw->usedPiexlAlpha = 0;
	} else {
		hw->usedViPipe -= cutVi;
		hw->usedPiexlAlpha -= cutUIAlpha;
		if (hw->Pipe[from-1].layNum < LAYER_BY_PIPE)
			hw->currentPipe = from-1;
		else
			hw->currentPipe = from;
	}
}

void resetLayerList(Display_t *display)
{
	struct listnode *node;
	Layer_t *layer;
	int hastr = 0;
    list_for_each(node, &display->layerSortedByZorder) {
		layer = node_to_item(node, Layer_t, node);
		resetLayer(layer);
		hastr |= layer->transform;
    }
	if(!hastr)
		trResetErr();
}

static int calc_point_byPercent(const unsigned char percent,
    const int middle_point, const int src_point)
{
    int condition = (src_point > middle_point) ? 1 : 0;
    int length = condition ? (src_point - middle_point) : (middle_point - src_point);
    length = length * percent / 100;
    return condition ? (middle_point + length) : (middle_point - length);
}

void recomputeDisplayFrame(int screenRadio, const hwc_rect_t sourceCrop,
    const int unitedFrameWidth, const int unitedFrameHeight, hwc_rect_t *displayFrame)
{
    switch(screenRadio) {
    case SCREEN_AUTO: {
        unsigned int ratio_width, ratio_height;
        int frame_width = sourceCrop.right - sourceCrop.left;
        int frame_height = sourceCrop.bottom - sourceCrop.top;
        if(frame_width >= unitedFrameWidth || frame_height >= unitedFrameHeight) {
            // scale down
            if(frame_width > unitedFrameWidth) {
                frame_height = frame_height * unitedFrameWidth / frame_width;
                frame_width = unitedFrameWidth;
            }
            if(frame_height > unitedFrameHeight) {
                frame_width = frame_width * unitedFrameHeight / frame_height;
                frame_height = unitedFrameHeight;
            }
        } else {
            // scale up
            ratio_width = frame_width * unitedFrameHeight;
            ratio_height = frame_height * unitedFrameWidth;
            if(ratio_width >= ratio_height) {
                // scale up until frame_width equal unitedFrameWidth
                frame_height = ratio_height / frame_width;
                frame_width = unitedFrameWidth;
            } else {
                // scale up until frame_height equal unitedFrameHeight
                frame_width = ratio_width / frame_height;
                frame_height = unitedFrameHeight;
            }
        }
        //ALOGD("###frame[%d,%d], unitedFrame[%d,%d], displayFrame[%d,%d,%d,%d]",
        //    frame_width, frame_height, unitedFrameWidth, unitedFrameHeight,
        //    displayFrame->left, displayFrame->right, displayFrame->top, displayFrame->bottom);

        if(unitedFrameWidth > frame_width) {
            ratio_width = frame_width * (displayFrame->right - displayFrame->left)
                / unitedFrameWidth;
            displayFrame->left += (displayFrame->right - displayFrame->left - ratio_width) >> 1;
            displayFrame->right = displayFrame->left + ratio_width;
        }
        if(unitedFrameHeight > frame_height) {
            ratio_height = frame_height * (displayFrame->bottom - displayFrame->top)
                / unitedFrameHeight;
            displayFrame->top += (displayFrame->bottom - displayFrame->top - ratio_height) >> 1;
            displayFrame->bottom = displayFrame->top + ratio_height;
        }
    }
        break;
    case SCREEN_FULL:
    // here do nothing because the frame of cmcc is allready and always full.
    default:
        break;
    }
}

static bool resize_layer(Display_t *display, disp_layer_info2 *layer_info , Layer_t *layer)
{
	hwc_rect_t sourceCrop;
	hwc_rect_t displayFrame;
	DisplayPrivate_t *hwdisplay;
	DisplayConfigPrivate_t *hwconfig;
	DisplayConfig_t *dispconfig;

	dispconfig = display->displayConfigList[display->activeConfigId];
	hwdisplay = toHwDisplay(display);
	hwconfig = toHwConfig(dispconfig);

#if 0
	int VarDisplayWidth = hwconfig->screenDisplay.right - hwconfig->screenDisplay.left;
	int VarDisplayHeight = hwconfig->screenDisplay.bottom - hwconfig->screenDisplay.top;
	int InitDisplayWidth = dispconfig->width;
	int InitDisplayHeight = dispconfig->height;
#endif

	int VarDisplayWidth = display->VarDisplayWidth;
	int VarDisplayHeight = display->VarDisplayHeight;
	int InitDisplayWidth = dispconfig->width;
	int InitDisplayHeight = dispconfig->height;

	sourceCrop.left =   layer->crop.left < 0 ? 0 : layer->crop.left;
	sourceCrop.right =  layer->crop.right < 0 ? 0 : layer->crop.right;
	sourceCrop.top =    layer->crop.top < 0 ? 0 : layer->crop.top;
	sourceCrop.bottom = layer->crop.bottom < 0 ? 0 : layer->crop.bottom;

	layer_info->fb.crop.x = (long long)(((long long)(sourceCrop.left)) << 32);
	layer_info->fb.crop.width = (long long)(((long long)(sourceCrop.right)) << 32);
	layer_info->fb.crop.width -= layer_info->fb.crop.x;
	layer_info->fb.crop.y = (long long)(((long long)(sourceCrop.top)) << 32);
	layer_info->fb.crop.height = (long long)(((long long)(sourceCrop.bottom)) << 32);
	layer_info->fb.crop.height -= layer_info->fb.crop.y;

	memcpy((void *)&displayFrame, (void *)&(layer->frame), sizeof(hwc_rect_t));

	if(layerIsVideo(layer)) {
		int unitedFrameWidth = InitDisplayWidth;
		int unitedFrameHeight = InitDisplayHeight;
		recomputeDisplayFrame(display->screenRadio, sourceCrop,
				unitedFrameWidth, unitedFrameHeight, &displayFrame);
	}

	layer_info->screen_win.x =
		calc_point_byPercent(display->hpercent, InitDisplayWidth >> 1, displayFrame.left)
		* VarDisplayWidth / InitDisplayWidth;
	layer_info->screen_win.width =
		calc_point_byPercent(display->hpercent, InitDisplayWidth >> 1, displayFrame.right)
		* VarDisplayWidth / InitDisplayWidth;
	layer_info->screen_win.width -= layer_info->screen_win.x;
	layer_info->screen_win.width = (0 == layer_info->screen_win.width) ? 1 : layer_info->screen_win.width;
	layer_info->screen_win.y =
		calc_point_byPercent(display->vpercent, InitDisplayHeight >> 1, displayFrame.top)
		* VarDisplayHeight / InitDisplayHeight;
	layer_info->screen_win.height =
		calc_point_byPercent(display->vpercent, InitDisplayHeight >> 1, displayFrame.bottom)
		* VarDisplayHeight / InitDisplayHeight;
	layer_info->screen_win.height -= layer_info->screen_win.y;
	layer_info->screen_win.height = (0 == layer_info->screen_win.height) ? 1 : layer_info->screen_win.height;
/*
	ALOGD("screen[%d,%d,%d,%d]", layer_info->screen_win.x, layer_info->screen_win.y,
	    layer_info->screen_win.width, layer_info->screen_win.height);
*/
        return 0;
}

static bool check_hw_dataspace_mode(Display_t *display, Layer_t *layer)
{
	if ((NULL == layer) || (NULL == display) || (display->displayId != 0))
		return 0;

	int dataspace_mode = 0;

	if (DISPLAY_OUTPUT_DATASPACE_MODE_AUTO == display->dataspace_mode) {
		int transfer = layer->dataspace & HAL_DATASPACE_TRANSFER_MASK;
		int standard = layer->dataspace & HAL_DATASPACE_STANDARD_MASK;
		if ((HAL_DATASPACE_TRANSFER_ST2084 == transfer)
				|| (HAL_DATASPACE_TRANSFER_HLG == transfer))
			dataspace_mode = DISPLAY_OUTPUT_DATASPACE_MODE_HDR;
		else
			dataspace_mode = DISPLAY_OUTPUT_DATASPACE_MODE_SDR;
	} else {
		dataspace_mode = display->dataspace_mode;
	}

	return (dataspace_mode == DISPLAY_OUTPUT_DATASPACE_MODE_HDR) ? true : false;
}

void reCalPipeForHDR(Display_t *display)
{
	struct listnode *node;
	Layer_t *layer;
	int isHDR = 0;
	DESource_t *deHw = &DESource[display->displayId];

	deHw->fixPipeNumber = (display->displayId == 0) ? 4 : 2;
	list_for_each(node, &display->layerSortedByZorder) {
		layer = node_to_item(node, Layer_t, node);
		isHDR |= check_hw_dataspace_mode(display, layer);
	}

	if (isHDR) {
#ifdef HOMLET_PLATFORM
		homlet_dataspace_change_callback(isHDR);
#endif
		deHw->fixPipeNumber = 2;
	}
}

void resetDisplayConfig(Display_t *display)
{
	DisplayPrivate_t *hwdisplay;
	DisplayConfigPrivate_t *hwconfig;
	DisplayConfig_t *dispconfig;

	dispconfig = display->displayConfigList[display->activeConfigId];
	hwdisplay = toHwDisplay(display);
	hwconfig = toHwConfig(dispconfig);

	hwconfig->screenDisplay.left = hwdisplay->ScreenCutOff.left;
	hwconfig->screenDisplay.right = dispconfig->width - hwdisplay->ScreenCutOff.right;
	hwconfig->screenDisplay.top = hwdisplay->ScreenCutOff.top;
	hwconfig->screenDisplay.bottom = dispconfig->height - hwdisplay->ScreenCutOff.bottom;
}

void resetDisplay(Display_t *display)
{

	resetDisplayConfig(display);
	/*  */
	display->needclientTarget = 0;


}

void addClinetTarget(Display_t *display)
{
	if(!display->needclientTarget) {
		memCtrlDealFBLayer(display->clientTargetLayer, true);
	}
	display->needclientTarget = 1;
}

static bool checkSamePipeScale(PipeInfo_t *pipe, Layer_t *layer)
{
	float wFactor = 1;
	float hFactor = 1;
	if (checkSoildLayer(layer))
		return true;
	calcLayerFactor(layer, &wFactor, &hFactor);
	return checkFloatSame(wFactor, pipe->scaleW) && checkFloatSame(hFactor, pipe->scaleH);
}

bool inline checkSupportTransform(Display_t *display, Layer_t *layer)
{
	private_handle_t *handle = NULL;
	handle = (private_handle_t *)layer->buffer;
	switch(layer->transform) {
		case 0:
			return true;
		case HAL_TRANSFORM_ROT_90:
		case HAL_TRANSFORM_ROT_180:
		case HAL_TRANSFORM_ROT_270:
			break;// tmp not support rotate.
		default:
			return false;
	}
	if (handle == NULL)
		return false;

	switch (handle->format) {
		case HAL_PIXEL_FORMAT_RGBA_8888:
		case HAL_PIXEL_FORMAT_RGBX_8888:
		case HAL_PIXEL_FORMAT_RGB_888:
		case HAL_PIXEL_FORMAT_RGB_565:
		case HAL_PIXEL_FORMAT_BGRA_8888:
		case HAL_PIXEL_FORMAT_YV12:
		case HAL_PIXEL_FORMAT_AW_NV12:
		case HAL_PIXEL_FORMAT_YCrCb_420_SP:
		case HAL_PIXEL_FORMAT_BGRX_8888:
			break;
		default:
			return false;
	}

	return supportTR(display, layer);
}

static inline int checkSupportFormat(Layer_t *layer)
{
	private_handle_t *handle = NULL;

	handle = (private_handle_t *)layer->buffer;
	if (handle == NULL)
		return 0;
	switch(handle->format) {
		case HAL_PIXEL_FORMAT_RGBA_8888:
		case HAL_PIXEL_FORMAT_RGBX_8888:
		case HAL_PIXEL_FORMAT_RGB_888:
		case HAL_PIXEL_FORMAT_RGB_565:
		case HAL_PIXEL_FORMAT_BGRA_8888:
		case HAL_PIXEL_FORMAT_YV12:
		case HAL_PIXEL_FORMAT_YCrCb_420_SP:
		case HAL_PIXEL_FORMAT_BGRX_8888:
		case HAL_PIXEL_FORMAT_AW_NV12:
#if DE_VERSION == 30
		case HAL_PIXEL_FORMAT_AW_YV12_10bit:
		case HAL_PIXEL_FORMAT_AW_I420_10bit:
		case HAL_PIXEL_FORMAT_AW_NV21_10bit:
		case HAL_PIXEL_FORMAT_AW_NV12_10bit:
		case HAL_PIXEL_FORMAT_AW_P010_UV:
		case HAL_PIXEL_FORMAT_AW_P010_VU:
#endif
	        return 1;
	    default:
	        return 0;
    }
}

static inline bool checkSupportBlendingFormate(Layer_t *layer)
{
	private_handle_t *handle = NULL;

	handle = (private_handle_t *)layer->buffer;
	if (handle == NULL)
		return 0;

	switch(handle->format) {
        case HAL_PIXEL_FORMAT_RGBA_8888:
        case HAL_PIXEL_FORMAT_RGBX_8888:
        case HAL_PIXEL_FORMAT_RGB_888:
        case HAL_PIXEL_FORMAT_RGB_565:
        case HAL_PIXEL_FORMAT_BGRA_8888:
        case HAL_PIXEL_FORMAT_BGRX_8888:
            return 1;
        default:
            return 0;
    }
}

bool checkLayerPipeCross(PipeInfo_t *pipe, Layer_t *layer)
{
	int i = 0;
	bool cross = 0;

	for (i = 0; i < pipe->layNum; i++) {
		if (pipe->hwcLayer[i] != NULL)
			cross |= checkLayerCross(pipe->hwcLayer[i], layer);
		if (cross)
			break;
	}
	return cross;
}

bool checkLayerClientCross(struct listnode *layerSortedByZorder, Layer_t *layer)
{
	struct listnode *node;
	Layer_t *layer2;
	bool cross = 0;
	DELayerPrivate_t *deLayer;

	list_for_each(node, layerSortedByZorder) {
			layer2 = node_to_item(node, Layer_t, node);
			if (layer2->zorder >= layer->zorder || cross)
				break;
			deLayer = toHwLayer(layer2);
			if (deLayer->pipe == -1)
				cross |= checkLayerCross(layer2, layer);
	}
	return cross;
}

bool checkSimpleSupport(Display_t *display, Layer_t *layer)
{
	if(layer->compositionType == HWC2_COMPOSITION_CLIENT_TARGET)
		return true;
	if (display->colorTransformHint != 0) {
		layer->duetoFlag = COLORT_HINT;
		return false;
	}
	if (display->forceClient){
		layer->duetoFlag = FORCE_GPU;
		return false;
	}
	if (checkSkipLayer(layer)){
		layer->duetoFlag = SKIP_FLAGS;
		return false;
	}
	if (!checkSoildLayer(layer) && checkNullBuffer(layer)) {
		layer->duetoFlag = NO_BUFFER;
		return false;
	}
	if (checkSoildLayer(layer)){
		return true;
	}
	if (!checkSupportFormat(layer)) {
		layer->duetoFlag = FORMAT_MISS;
		return false;
	}
	if (layerIsScale(display, layer) && !layerCanScale(deFreq, display, layer)) {
		layer->duetoFlag = SCALE_OUT;
		return false;
	}
	if (!checkSupportTransform(display, layer)) {
		layer->duetoFlag = TRANSFROM_RT;
		return false;
	}

	// for afbc
	private_handle_t *handle = (private_handle_t *)layer->buffer;
	if (handle && is_afbc_buf(handle)) {
		if (hwc_ui_scaler_check(display, layer) == 0) {
			layer->duetoFlag = SCALE_OUT;
			return false;
		}
	}

	if (!checkDealContiMem(layer)){
#ifdef USE_IOMMU
		return true;
#else
		layer->duetoFlag = NOCONTIG_MEM;
		return false;
#endif
	}
	return true;

}

bool checkSameConfig(DisplayConfig_t *src, DisplayConfig_t *dst)
{
	DisplayConfigPrivate_t *srchwconfig;
	DisplayConfigPrivate_t *dsthwconfig;
	srchwconfig = toHwConfig(src);
	dsthwconfig = toHwConfig(dst);

	if (src->width != dst->width)
		return false;
	if (src->height != dst->height)
		return false;
	if (srchwconfig->mode != dsthwconfig->mode)
			return false;
	return true;
}

static bool canAssignSpecialPipe(DESource_t *deHw, Layer_t *layer, PipeType_t type)
{
	unusedpara(layer);
	if (type == VIDEO && deHw->usedViPipe < deHw->fixVideoPipeNum) {
		return true;
	}
	return false;
}

void assignLayerComType(Display_t *display)
{
	Layer_t *layer;
	DELayerPrivate_t *deLayer;
	struct listnode *node;

	list_for_each(node, &display->layerSortedByZorder) {
		layer = node_to_item(node, Layer_t, node);
		deLayer = toHwLayer(layer);
		if (layer->compositionType == HWC2_COMPOSITION_CLIENT_TARGET)
			continue;
		if (deLayer->pipe == -1) {
			if (layer->compositionType != HWC2_COMPOSITION_CLIENT)
				layer->typeChange = 1;
			layer->compositionType = HWC2_COMPOSITION_CLIENT;
		} else {
			if (layer->compositionType == HWC2_COMPOSITION_CLIENT
					|| layer->compositionType == HWC2_COMPOSITION_CURSOR
					|| layer->compositionType == HWC2_COMPOSITION_SIDEBAND) {
				layer->typeChange = 1;
				/* JetCui:  cursor--> device but dim layer not */
				layer->compositionType = HWC2_COMPOSITION_DEVICE;
			}
		}
		if (deLayer->pipe == -1) {
			trCachePut(layer);
			layer->trcache = NULL;
		}
	}
}

void reAssignHwPipeZorder(DESource_t *hw)
{
	int i = 0, j = 0, z = 0, zOrder = 0, uiBigen = 1, viBigen = 0, pipe = 0;
	Layer_t *layer = NULL;
	DELayerPrivate_t *hwlayer = NULL;
	PipeInfo_t *hwPipe = NULL;

	for (i = 0; i < hw->fixPipeNumber; i++) {
		if (hw->Pipe[i].type == NOTASSIGN)
			continue;
		/* the z=0 chanel must no alpha */

		hwPipe = &hw->Pipe[i];
		if (hw->Pipe[i].type == UI) {
			pipe = uiBigen++;
		} else if (hw->Pipe[i].type == VIDEO) {
			pipe = viBigen++;
		} else {
			if (hw->usedViPipe >= hw->fixVideoPipeNum) {
				pipe = uiBigen++;
			} else {
				pipe = viBigen++;
				hw->usedViPipe++;
			}
		}

		for (j = 0, z = 0; j < LAYER_BY_PIPE; j++) {
			layer = hwPipe->hwcLayer[j];
			if (layer != NULL) {
				hwlayer = toHwLayer(layer);
				hwlayer->pipe = pipe;
				hwlayer->layerId = z;
				hwlayer->zOrder = zOrder;
				hwlayer->pipeScaleW = hwPipe->scaleW;
				hwlayer->pipeScaleH = hwPipe->scaleH;				
				zOrder++;
				z++;
			}
		}
	}
}

static int siwtchDevice(Display_t *display, int mode)
{
    int ret = 0;
    unsigned long arg[4] = {0};

    arg[0] = display->displayId;
    arg[1] = DISP_OUTPUT_TYPE_HDMI;
    arg[2] = mode;
    ioctl(dispFd, DISP_DEVICE_SWITCH, (unsigned long)arg);
    return 0;
}

void setPipeScale(PipeInfo_t *Pipe, Layer_t *layer)
{
	float w, h, tmp;
	if (checkSoildLayer(layer)) {
		Pipe->scaleH = 1.0;
		Pipe->scaleW = 1.0;
		return;
	}
	w = layer->crop.right - layer->crop.left;
	h = layer->crop.bottom - layer->crop.top;
	if (layer->transform & HAL_TRANSFORM_ROT_90) {
		tmp = w;
		w = h;
		h = tmp;
	}
	Pipe->scaleW = (layer->frame.right - layer->frame.left) / w;
	Pipe->scaleH = (layer->frame.bottom - layer->frame.top) / w;
}

bool matchPipeAttribute(Display_t *display, int i, Layer_t *uplayer)
{
	DisplayPrivate_t *hwDisplay = toHwDisplay(display);
	DESource_t *deHw = &DESource[display->displayId];
	PipeInfo_t *Pipe = &deHw->Pipe[i];

	if (display->needclientTarget
			&& uplayer->compositionType != HWC2_COMPOSITION_CLIENT_TARGET
			&& i >= deHw->fixPipeNumber - 1)
		goto not_match;

	if (Pipe->layNum == 0) {
		if (layerIsVideo(uplayer)){
			if (deHw->usedViPipe >= deHw->fixVideoPipeNum)
				goto not_match;
		}
		if (layerIsPixelBlended(uplayer))
			if (deHw->usedPiexlAlpha >= deHw->fixPipeNumber - deHw->fixVideoPipeNum )
				goto not_match;
		return true;
	}
	if (Pipe->layNum >= LAYER_BY_PIPE)
		goto not_match;

	if (!checkFloatSame(uplayer->planeAlpha, Pipe->planeAlpha))
		goto not_match;

	if (!checkSamePipeScale(Pipe, uplayer)) {
		goto not_match;
	}

	if (Pipe->type == VIDEO) {
		if(!isSameForamt(Pipe->hwcLayer[0], uplayer))
			goto not_match;
	}

	if (layerIsBlended(uplayer)) {
		if (checkLayerPipeCross(Pipe, uplayer))
			goto not_match;
	}

	if (uplayer->stream)
		goto not_match;

	return true;
not_match:
	return false;
}

Layer_t *reCalPipeForVideoLayer(PipeInfo_t *Pipe, Layer_t *videoLayer)
{
	Layer_t *layer;

	if(checkLayerPipeCross(Pipe, videoLayer)) {
		videoLayer->clearClientTarget = 1;
		return videoLayer;
	}
	layer = node_to_item(Pipe->hwcLayer[0]->node.prev, Layer_t, node);
	/* do not == list head, so can use layer */
	return layer;
}

/*
  * assignLayersToPipe() return the last assigned layer...
  */
Layer_t* assignLayersToPipe(Display_t *display, Layer_t *layer)
{
	int i = 0, matchId = -1;
	PipeInfo_t *matchPipe = NULL;
	Layer_t *assignedlayer = layer;
	int resetedPipe = -1, resetto = -1;
	DELayerPrivate_t *deLayer = toHwLayer(layer);
	DisplayPrivate_t *hwDisplay = toHwDisplay(display);
	DESource_t *deHw = &DESource[display->displayId];
	enum sunnxi_dueto_flags duetoFlag = HWC_LAYER;

	/* memCtrl only reserve mem for video and FB */
	if (layerIsBlended(layer)
			&& layer->compositionType != HWC2_COMPOSITION_CLIENT_TARGET) {

		if (checkLayerClientCross(&display->layerSortedByZorder, layer)) {
			layer->duetoFlag = CROSS_FB;
			goto assigned;
		}
	}

	for (i = 0; i < deHw->fixPipeNumber; i++) {
		if (matchPipe == NULL && matchPipeAttribute(display, i, layer)) {
			matchPipe = &deHw->Pipe[i];
			matchId = i;
			if (matchPipe->layNum == 0) {
				break;
			}
			continue;
		}
		if (deHw->Pipe[i].layNum == 0) {
			i--;
			break;
		}
		if (checkLayerPipeCross(&deHw->Pipe[i], layer)) {
			matchPipe = NULL;
			matchId = -1;
		}
	}

	if (matchPipe == NULL) {
		if (layerIsVideo(layer)) {
			if (canAssignSpecialPipe(deHw, layer, VIDEO)) {
				resetto = resetedPipe = deHw->currentPipe;
				if (deHw->currentPipe == deHw->fixPipeNumber - 1
						|| display->needclientTarget) {
					resetedPipe = deHw->fixPipeNumber - 2;
					addClinetTarget(display);
				}
				/* video is reserve mem so it can pass the memctrl  */
				assignedlayer = reCalPipeForVideoLayer(&deHw->Pipe[resetedPipe], layer);
				resetHwPipe(display, resetedPipe, resetto, false);
				matchId = resetedPipe;
				matchPipe = &deHw->Pipe[resetedPipe];
			}else{
				layer->duetoFlag = NO_V_PIPE;
				addClinetTarget(display);
			}
		}else{
			layer->duetoFlag = NO_U_PIPE;
			addClinetTarget(display);
		}
assign_FB:
		if (layer->compositionType == HWC2_COMPOSITION_CLIENT_TARGET) {
			layer->duetoFlag = HWC_LAYER;
			resetedPipe = deHw->currentPipe;
			assignedlayer = deHw->Pipe[resetedPipe].hwcLayer[0];
			if(assignedlayer != NULL)
				assignedlayer = node_to_item(assignedlayer->node.prev, Layer_t, node);
			else
				assignedlayer = node_to_item(layer->node.prev, Layer_t, node);
			resetHwPipe(display, resetedPipe, resetedPipe, false);
			if (deHw->currentPipe > 0) {
				deHw->currentPipe--;
			}
		}
	}

	if (matchPipe) {
		int addmem;
		bool memok;
		deLayer = toHwLayer(layer);
		deLayer->pipe = matchId;
		memok = memCtrlAddLayer(display, layer, &addmem);

		if(layer->compositionType == HWC2_COMPOSITION_CLIENT_TARGET) {

			if (!memok && layer->duetoFlag == NOT_ASSIGN) {
				layer->duetoFlag = HWC_LAYER;
				memCtrlDealFBLayer(layer, true);
				matchPipe = NULL;
				goto assign_FB;
			}
			layer->duetoFlag = HWC_LAYER;
		}else{
			if (!memok) {
				deLayer->layerId = -1;
				deLayer->pipe = -1;
				deLayer->zOrder = -2;
				layer->duetoFlag = MEM_CTRL;
				addClinetTarget(display);
				goto assigned;
			}
			if (checkLayerClientCross(&display->layerSortedByZorder, layer)) {
				layer->clearClientTarget = 1;
			}
		}
		if (layerIsVideo(layer)) {
			matchPipe->type = VIDEO;
			if (matchPipe->layNum == 0)
				deHw->usedViPipe++;
		}else if (layerIsPixelBlended(layer) && matchId != 0) {
			if (matchPipe->layNum == 0)
				deHw->usedPiexlAlpha++;
			matchPipe->type = UI;
		}else {
			matchPipe->type = UI_NO_ALPHA;
		}
		if (matchPipe->layNum == 0) {
			setPipeScale(matchPipe, layer);
		}
		matchPipe->curentMem += addmem;
		matchPipe->hwcLayer[matchPipe->layNum++] = layer;

	}

	if (matchPipe && matchId >= deHw->currentPipe) {
		deHw->currentPipe = matchId;
	}

assigned:
	memCtrlDealLayer(layer, true);

return_layer:
	return assignedlayer;
}

static void TryToAssignLayer(Display_t *display)
{
	struct listnode *node;
	Layer_t *layer;

	list_for_each(node, &display->layerSortedByZorder) {
        layer = node_to_item(node, Layer_t, node);
		if (!display->needclientTarget
			&& layer->compositionType == HWC2_COMPOSITION_CLIENT_TARGET)
			continue;

		if (!checkSimpleSupport(display, layer)) {
			memCtrlDealLayer(layer, true);
			addClinetTarget(display);
			continue;
		}
		layer = assignLayersToPipe(display, layer);
		node = &layer->node;
    }
}

int32_t de2AssignLayer(Display_t *display)
{
	DisplayPrivate_t *hwDisplay = toHwDisplay(display);
	DESource_t *deHw = &DESource[display->displayId];

	resetDisplay(display);
	resetLayerList(display);
	resetHwPipe(display, 0, deHw->fixPipeNumber - 1, true);
	memResetPerframe(display);

	reCalPipeForHDR(display);

	TryToAssignLayer(display);

	memContrlComplet(display);
	reAssignHwPipeZorder(deHw);
	assignLayerComType(display);

	return 0;
}

static void setup3dMode(Display_t *display, disp_layer_info2 *info)
{
	if (display->displayId < 0) {
		/* not support. */
		return;
	}

	switch(hdmi_3D_mode) {
		case DISPLAY_2D_LEFT:
			info->b_trd_out = 0;
			info->fb.flags = DISP_BF_STEREO_SSH;
			break;
		case DISPLAY_2D_TOP:
			info->b_trd_out = 0;
			info->fb.flags = DISP_BF_STEREO_TB;
			break;
		case DISPLAY_3D_LEFT_RIGHT_HDMI:
			info->b_trd_out = 1;
			info->out_trd_mode = DISP_3D_OUT_MODE_FP;
			info->fb.flags = DISP_BF_STEREO_SSH;
			info->screen_win.x = 0;
			info->screen_win.y = 0;
			info->screen_win.width = 1920;
			info->screen_win.height = 1080;
			break;
		case DISPLAY_3D_TOP_BOTTOM_HDMI:
			info->b_trd_out = 1;
			info->out_trd_mode = DISP_3D_OUT_MODE_FP;
			info->fb.flags = DISP_BF_STEREO_TB;
			info->screen_win.x = 0;
			info->screen_win.y = 0;
			info->screen_win.width = 1920;
			info->screen_win.height = 1080;
			break;
		case DISPLAY_3D_DUAL_STREAM:
			info->b_trd_out = 1;
			info->out_trd_mode = DISP_3D_OUT_MODE_FP;
			info->fb.flags = DISP_BF_STEREO_FP;
			info->screen_win.x = 0;
			info->screen_win.y = 0;
			info->screen_win.width = 1920;
			info->screen_win.height = 1080;
			break;
		case DISPLAY_2D_DUAL_STREAM:
		default :
			info->b_trd_out = 0;
			info->fb.flags = DISP_BF_NORMAL;
			break;
	}

}

int setupSolidColorLayer(Layer_t *layer, disp_layer_config2 *layerConfig)
{
	disp_layer_info2 *info = &layerConfig->info;
	DELayerPrivate_t *hwlayer = toHwLayer(layer);

	info->color = layer->color.a<<24
		| layer->color.r<<16
		| layer->color.g<<8
		| layer->color.b;
	info->mode = LAYER_MODE_COLOR;
	info->fb.format = DISP_FORMAT_ARGB_8888;
	info->screen_win.x = layer->frame.left < 0 ? 0 : layer->frame.left;
	info->screen_win.y = layer->frame.top < 0 ? 0 : layer->frame.top;
	info->screen_win.width = layer->frame.right - layer->frame.left;
	info->screen_win.height = layer->frame.bottom - layer->frame.top;
	info->fb.size[0].height = info->screen_win.height;
	info->fb.size[0].width = info->screen_win.width;
	info->fb.crop.x = ((long long)(info->screen_win.x) << 32);
	info->fb.crop.y = ((long long)(info->screen_win.y) << 32);
	info->fb.crop.width = ((long long)(info->screen_win.width / hwlayer->pipeScaleW) << 32);
	info->fb.crop.height = ((long long)(info->screen_win.height / hwlayer->pipeScaleH) << 32);
	return 0;

}

static int setup_sunxi_metadata(Layer_t *layer, disp_layer_info2 *layer_info)
{
#if (GRALLOC_SUNXI_METADATA_BUF & (DE_VERSION == 30))
	private_handle_t *handle = (private_handle_t *)layer->buffer;

	/* Update handle->flags from metadata buffer */
	handle->ion_metadata_flag &= ionGetMetadataFlag(layer->buffer);

	layer_info->fb.metadata_flag = handle->ion_metadata_flag;
	layer_info->fb.metadata_size = handle->ion_metadata_size;
	layer_info->fb.metadata_fd = handle->metadata_fd;
	if (is_afbc_buf(handle))
		layer_info->fb.fbd_en = 1;
#if 0
	ALOGD("fbd_en=%d, metadata_flag=0x%x, metadata_size=%d, metadata__buf[0x%llx, %x]",
			layer_info->fb.fbd_en,
			layer_info->fb.metadata_flag,
			layer_info->fb.metadata_size,
			layer_info->fb.metadata_buf,
			get_ion_address(handle->metadata_fd));
#endif
#else
	layer, layer_info;
#endif /* GRALLOC_SUNXI_METADATA_BUF */
	return 0;
}

static int setup_layer_dataspace(
		disp_layer_info2 *layer_info, int32_t dataspace)
{
#if (DE_VERSION == 30)
	unsigned int transfer = (dataspace & HAL_DATASPACE_TRANSFER_MASK)
		>> HAL_DATASPACE_TRANSFER_SHIFT;
	unsigned int standard = (dataspace & HAL_DATASPACE_STANDARD_MASK)
		>> HAL_DATASPACE_STANDARD_SHIFT;
	unsigned int range = (HAL_DATASPACE_RANGE_FULL
			!= (dataspace & HAL_DATASPACE_RANGE_MASK)) ? 0 : 1; /* 0: limit. 1: full */

	/* color space table [standard][range] */
	const disp_color_space cs_table[][2] = {
		{DISP_UNDEF, DISP_UNDEF_F},
		{DISP_BT709, DISP_BT709_F},
		{DISP_BT470BG, DISP_BT470BG_F},
		{DISP_BT470BG, DISP_BT470BG_F},
		{DISP_BT601, DISP_BT601_F},
		{DISP_BT601, DISP_BT601_F},
		{DISP_BT2020NC, DISP_BT2020NC_F},
		{DISP_BT2020C, DISP_BT2020C_F},
		{DISP_FCC, DISP_FCC_F},
		{DISP_BT709, DISP_BT709_F},
		{DISP_BT709, DISP_BT709_F},
		{DISP_BT709, DISP_BT709_F},
	};
	if ((range < sizeof(cs_table[0]) / sizeof(cs_table[0][0]))
			&& (standard < sizeof(cs_table) / sizeof(cs_table[0]))) {
		layer_info->fb.color_space = cs_table[standard][range];
	} else {
		ALOGD("unknown dataspace standard(0x%x) range(0x%x)", standard, range);
		layer_info->fb.color_space = range ? DISP_UNDEF_F : DISP_UNDEF;
	}

	const disp_eotf eotf_table[] = {
		DISP_EOTF_UNDEF,
		DISP_EOTF_LINEAR,
		DISP_EOTF_IEC61966_2_1,
		DISP_EOTF_BT601,
		DISP_EOTF_GAMMA22,
		DISP_EOTF_GAMMA28,  /* HAL_DATASPACE_TRANSFER_GAMMA2_6 */
		DISP_EOTF_GAMMA28,
		DISP_EOTF_SMPTE2084,
		DISP_EOTF_ARIB_STD_B67
	};
	if (transfer < sizeof(eotf_table) / sizeof(eotf_table[0])) {
		layer_info->fb.eotf = eotf_table[transfer];
	} else {
		ALOGD("unknown dataspace Transfer(0x%x)", transfer);
		layer_info->fb.eotf = DISP_EOTF_UNDEF;
	}
	/* ALOGD("layer_info_fb: eotf=%d, cs=%d",
		layer_info->fb.eotf, layer_info->fb.color_space);
	*/
#else
	layer_info, dataspace;
#endif
	return 0;
}

static int setupDisplayInfo(Display_t *display, Layer_t *layer, disp_layer_config2 *layerConfig, int zorder)
{
	int  i = 0;
	private_handle_t *handle;
	int swap, s_left, s_right, s_top, s_bottom;
	int src_stride_w, src_stride_h,dst_stride_w, dst_stride_h;//all is pixel  unit
	int bpp0 = 4;
	disp_layer_info2 *info = &layerConfig->info;
	DisplayPrivate_t *hwdisplay;
	unsigned long arg[4] = {0};

	memset(info, 0, sizeof(disp_layer_info2));

	info->zorder = zorder;
	info->alpha_value = (int)ceilf(255 * layer->planeAlpha);

	if (layerIsBlended(layer)) {
		info->alpha_mode = 2;
	} else {
		info->alpha_mode = 1;
	}
	if (layerIsPremult(layer)) {
		info->fb.pre_multiply = 1;
	}
	if (layerConfig->channel == 0) {
		info->fb.pre_multiply = 0;
		info->alpha_mode = 1;
	}

	if(layer->buffer == NULL) {
		setupSolidColorLayer(layer, layerConfig);
		return 0;
	}

	handle = (private_handle_t *)layer->buffer;
	if (!handle) {
		ALOGE("buffer handle is NULL , type is %d ", layer->compositionType);
		return -1;
	}

	info->mode = LAYER_MODE_BUFFER;
	info->fb.fd = handle->share_fd;
	if (info->fb.fd < 0) {
		ALOGE("%s: LINE:%d fb.fd err.", __func__, __LINE__);
		goto err;
	}
	/* display now use  ucnt = bpp so is piexl x bpp byte align
	 * pitch[1] = DISPALIGN(size[1] * ucnt, align[1]);
	 * means:
	 * DISPALIGN( info->fb.size[i].width *bpp,info->fb.align[i])
	 */
	switch(handle->format) {
		case HAL_PIXEL_FORMAT_YV12:
			bpp0 = 1;
			break;
		case HAL_PIXEL_FORMAT_YCrCb_420_SP:
		case HAL_PIXEL_FORMAT_AW_NV12:
			bpp0 = 1;
			break;
		case HAL_PIXEL_FORMAT_RGB_888:
			bpp0 = 3;
		default:
			ALOGV("RGB");
	}
	dst_stride_w = src_stride_w = handle->stride;
	dst_stride_h = src_stride_h = handle->height;
	/* HAL_TRANSFORM_ROT_180 = HAL_TRANSFORM_FLIP_V | HAL_TRANSFORM_FLIP_H
	 * HAL_TRANSFORM_ROT_270 = HAL_TRANSFORM_ROT_180 | HAL_TRANSFORM_ROT_90
	 */
	s_left = layer->crop.left;
	s_top = layer->crop.top;
	s_right = layer->crop.right;
	s_bottom = layer->crop.bottom;
	/*when rotate beginning must stride is pixel allign */
	if (layer->transform != 0) {
		dst_stride_w = HWC_ALIGN(handle->stride* bpp0, handle->aw_byte_align[0]) / bpp0;
		dst_stride_h = HWC_ALIGN(handle->height* bpp0, handle->aw_byte_align[0]) / bpp0;
	}

	if((layer->transform & HAL_TRANSFORM_FLIP_V) == HAL_TRANSFORM_FLIP_V) {
		s_top = (src_stride_h - layer->crop.bottom)>0 ? (src_stride_h - layer->crop.bottom) : 0;
		s_bottom = (src_stride_h - layer->crop.top)>0 ? (src_stride_h - layer->crop.top) : src_stride_h;
	}

	if((layer->transform & HAL_TRANSFORM_FLIP_H) == HAL_TRANSFORM_FLIP_H) {
		s_right = (src_stride_w - layer->crop.left)>0 ? (src_stride_w  - layer->crop.left) : src_stride_w;
		s_left =  (src_stride_w - layer->crop.right)>0 ? (src_stride_w - layer->crop.right) : 0;
	}

	if((layer->transform & HAL_TRANSFORM_ROT_90) == HAL_TRANSFORM_ROT_90) {
		swap = s_left;
		s_left = (src_stride_h - s_bottom)>0 ? (src_stride_h - s_bottom) : 0;
		s_bottom = s_right;
		s_right = (src_stride_h - s_top)>0 ? (src_stride_h - s_top) : src_stride_h;
		s_top =	swap;
		swap = dst_stride_w;
		dst_stride_w = dst_stride_h;
		dst_stride_h = swap;
	}

	layer->crop.left = s_left;
	layer->crop.top = s_top;
	layer->crop.right = s_right;
	layer->crop.bottom = s_bottom;

	info->fb.crop.x = ((long long)(s_left) << 32);
	info->fb.crop.y = ((long long)(s_top) << 32);
	info->fb.crop.width = ((long long)(s_right - s_left) << 32);
	info->fb.crop.height = ((long long)(s_bottom - s_top) << 32);

	info->screen_win.x = layer->frame.left < 0 ? 0 : layer->frame.left;
	info->screen_win.y = layer->frame.top < 0 ? 0 : layer->frame.top;
	info->screen_win.width = layer->frame.right - layer->frame.left;
	info->screen_win.height = layer->frame.bottom - layer->frame.top;

	resize_layer(display, info , layer);

	info->fb.size[0].width =  dst_stride_w;
	info->fb.size[1].width =  dst_stride_w / 2;
	info->fb.size[2].width =  dst_stride_w / 2;
	info->fb.size[0].height = dst_stride_h;
	info->fb.size[1].height = dst_stride_h / 2;
	info->fb.size[2].height = dst_stride_h / 2;
	info->fb.align[0] = handle->aw_byte_align[0];
	info->fb.align[1] = handle->aw_byte_align[1];
	info->fb.align[2] = handle->aw_byte_align[2];
	switch(handle->format) {
		case HAL_PIXEL_FORMAT_RGBA_8888:
			info->fb.format = DISP_FORMAT_ABGR_8888;
			break;
		case HAL_PIXEL_FORMAT_RGBX_8888:
			info->fb.format = DISP_FORMAT_XBGR_8888;
			break;
		case HAL_PIXEL_FORMAT_BGRA_8888:
			info->fb.format = DISP_FORMAT_ARGB_8888;
			break;
		case HAL_PIXEL_FORMAT_BGRX_8888:
			info->fb.format = DISP_FORMAT_XRGB_8888;
			break;
		case HAL_PIXEL_FORMAT_RGB_888:
			info->fb.format = DISP_FORMAT_BGR_888;
			break;
		case HAL_PIXEL_FORMAT_RGB_565:
			info->fb.format = DISP_FORMAT_RGB_565;
			break;
		case HAL_PIXEL_FORMAT_YV12:
			info->fb.format = DISP_FORMAT_YUV420_P;
			break;
		case HAL_PIXEL_FORMAT_YCrCb_420_SP:
			info->fb.format = DISP_FORMAT_YUV420_SP_VUVU;
			break;
		case HAL_PIXEL_FORMAT_AW_NV12:
			info->fb.format = DISP_FORMAT_YUV420_SP_UVUV;
			break;
#if (DE_VERSION == 30)
		case HAL_PIXEL_FORMAT_AW_YV12_10bit:
		case HAL_PIXEL_FORMAT_AW_I420_10bit:
			info->fb.format = DISP_FORMAT_YUV420_P;
			break;
		case HAL_PIXEL_FORMAT_AW_NV21_10bit:
			info->fb.format = DISP_FORMAT_YUV420_SP_VUVU;
			break;
		case HAL_PIXEL_FORMAT_AW_NV12_10bit:
			info->fb.format = DISP_FORMAT_YUV420_SP_UVUV;
			break;
		case HAL_PIXEL_FORMAT_AW_P010_UV:
			info->fb.format = DISP_FORMAT_YUV420_SP_UVUV_10BIT;
			break;
		case HAL_PIXEL_FORMAT_AW_P010_VU:
			info->fb.format = DISP_FORMAT_YUV420_SP_VUVU_10BIT;
			break;
#endif
		default:
			ALOGE("DO not support format 0x%x in %s", handle->format, __FUNCTION__);
			goto err;
	}

	if (layerIsVideo(layer))
		setup_sunxi_metadata(layer, info);
	setup_layer_dataspace(info, layer->dataspace);

	hwdisplay = toHwDisplay(display);

	if(hwdisplay->type.mode == DISP_TV_MOD_1080P_24HZ_3D_FP) {
		if (layerIsVideo(layer)) {
			setup3dMode(display, info);
		} else {
			info->b_trd_out = 0;
			info->fb.flags = DISP_BF_STEREO_2D_DEPTH;
			info->fb.depth = 3;
		}
	}

	return 0;
err:
	return -1;
}

int de2memCtrlAddLayer(Display_t *display, Layer_t *layer)
{
	DESource_t *hw;
	DELayerPrivate_t *hwlayer;
	int i = 0;
	PipeInfo_t *pipe;
	hwc_rect_t rectx,rectx2,rectx3;
	float withS, hightS;
	float addmem;
	rectx = {0,0,0,0};
	rectx2 = rectx;
	rectx3 = rectx;

	hwlayer = toHwLayer(layer);
	hw = &DESource[display->displayId];
	pipe = &hw->Pipe[hwlayer->pipe];
	if (layer->transform & HAL_TRANSFORM_ROT_90) {
		withS = (layer->crop.top - layer->crop.bottom)/(layer->frame.right - layer->frame.left);
		hightS = 	(layer->crop.right- layer->crop.left)/(layer->frame.top - layer->frame.bottom);
	}else{
		withS = (layer->crop.right - layer->crop.left)/(layer->frame.right - layer->frame.left);
		hightS = 	(layer->crop.bottom - layer->crop.top)/(layer->frame.top - layer->frame.bottom);
	}
	addmem = ((layer->crop.right - layer->crop.left)
											*(layer->crop.bottom - layer->crop.top)
											* getBitsPerPixel(layer) / 8);
	if (pipe->layNum == 0)
		return (int)ceilf(addmem);

	for (i = 0; i < pipe->layNum; i++) {
		rectx2 = rectx;
		if (regionCrossed(&layer->frame, &pipe->hwcLayer[i]->frame, &rectx))
			addmem -= withS * (rectx.right - rectx.left) + hightS * (rectx.bottom - rectx.top);
		if (regionCrossed(&rectx2, &rectx, &rectx2))
			addmem += withS * (rectx2.right - rectx2.left) + hightS * (rectx2.bottom - rectx2.top);
		rectx3 = rectx2;
		if (i >= 2 && regionCrossed(&rectx3, &rectx, &rectx3))
			addmem += withS * (rectx3.right - rectx3.left) + hightS * (rectx3.bottom - rectx3.top);
	}

	return (int)ceilf(addmem);
}

int de2SetupLayer(Display_t *display, LayerSubmit_t *submitLayer)
{
	struct listnode *node;
	DESource_t *hw;
	Layer_t *layer;
	disp_layer_config2 *layerConfig;
	DELayerPrivate_t *hwlayer;
	int i = 0;
	int chn = display->displayId ? 2 : 4;

	hw = &DESource[display->displayId];
	layerConfig = hw->layerInfo;
	for (i = 0; i < chn * 4; i++) {
		layerConfig[i].enable = 0;
	}

	list_for_each(node, &submitLayer->layerNode) {
		layer = node_to_item(node, Layer_t, node);
		hwlayer = toHwLayer(layer);
		layerConfig[hwlayer->pipe * 4 + hwlayer->layerId].enable = 1;
		setupDisplayInfo(display, layer,
			&layerConfig[hwlayer->pipe * 4 + hwlayer->layerId], hwlayer->zOrder);
	}

	return 0;
}

static int diplayToScreen(int disp, disp_layer_config2 *config, int num, unsigned int synccount)
{
	int ret;
	unsigned long arg[4] = {0};

	arg[0] = disp;
	arg[1] = 1;
	ret = ioctl(dispFd, DISP_SHADOW_PROTECT, (unsigned long)arg);
	if (ret) {
		ALOGE("%d err: DISP_SHADOW_PROTECT failed", __LINE__);
		goto err;
	}

	arg[0] = disp;
	arg[1] = (unsigned long)(config);
	arg[2] = num;

	ret = ioctl(dispFd, DISP_LAYER_SET_CONFIG2, (unsigned long)arg);
	if (ret) {
		ALOGE("DISP_LAYER_SET_CONFIG failed !");
	}

	arg[0] = disp;
	arg[1] = HWC_SUBMIT_FENCE;
	arg[2] = synccount;
	arg[3] = 0;
	ret = ioctl(dispFd, DISP_HWC_COMMIT, (unsigned long)arg);
	if (ret) {
		ALOGE("fence commit err.");
	}

	arg[0] = disp;
	arg[1] = 0;
	ret = ioctl(dispFd, DISP_SHADOW_PROTECT, (unsigned long)arg);
	if (ret) {
		ALOGE("%d err: DISP_SHADOW_PROTECT failed", __LINE__);
		goto err;
	}

err:
	return ret;
}

static int de2commitToDisplay(Display_t *display, LayerSubmit_t *submitLayer)
{

	DisplayPrivate_t* hwdisplay;
	DisplayConfigPrivate_t *hwconfig;
	DESource_t *hw;
	disp_layer_config2 *layerConfig;
	int pipe = display->displayId ? 2 : 4;

	hwdisplay = toHwDisplay(display);
	hw = &DESource[display->displayId];
	layerConfig = hw->layerInfo;

	if (!checkSameConfig(&hw->currentConfig, submitLayer->currentConfig)) {
		hwconfig = toHwConfig(submitLayer->currentConfig);
		if (hwconfig) {
			/* TODO:Switch the mode for hdmi and other */
		}
	}

	return diplayToScreen(display->displayId, layerConfig, pipe * 4, submitLayer->sync.count);

}

static int de2DelayDeal(Display_t *display, LayerSubmit_t *submitLayer)
{
	unusedpara(display);
	unusedpara(submitLayer);
	return 0;
}

void showlayers(Display_t *display)
{
	DisplayPrivate_t *hwdisplay;
	DELayerPrivate_t *hwlayer;
	struct listnode *node;
	Layer_t *lay;
	private_handle_t *handle;

	char mem[100];
	memCtrlDump(mem);

	ALOGI("dispy%d cur:%d-%d-%d mem:%s\n", display->displayId, display->frameCount,
			display->commitThread->diplayCount, display->commitThread->SubmitCount,mem);

	ALOGI(" 	layer	   |  handle        |  format  | color  |"
			"bl|space|TR|pAlp|        crop      	      "
			"|        frame        |zOrder|hz|ch|id|duto\n"
			"--------------------------------------------------------------------------"
			"--------------------------------------------------------------------------\n");

	list_for_each(node, &display->layerSortedByZorder) {
		lay = node_to_item(node, Layer_t, node);
		if (lay->compositionType == HWC2_COMPOSITION_CLIENT_TARGET
				&& !display->needclientTarget)
			continue;
		handle = (private_handle_t *)lay->buffer;
		hwlayer = toHwLayer(lay);
		ALOGI("%16p|%16p|%10d|%08x|%2d|%5d|%2d|%4.2f|[%6.1f,%6.1f,%6.1f,%6.1f]"
				"|[%4d,%4d,%4d,%4d]|%6d|%2d|%2d|%2d|%s", lay,
				lay->buffer, handle != NULL ? handle->format : 0,
				(((unsigned int)lay->color.a<<24)|(unsigned int)(lay->color.r<<16)
				 |(unsigned int)(lay->color.g<<8)|(unsigned int)(lay->color.b)),
				lay->blendMode, lay->dataspace, lay->transform, lay->planeAlpha,
				lay->crop.left, lay->crop.top, lay->crop.right, lay->crop.bottom,
				lay->frame.left, lay->frame.top, lay->frame.right, lay->frame.bottom,
				lay->zorder, hwlayer->zOrder, hwlayer->pipe, hwlayer->layerId,
				hwcPrintInfo(lay->duetoFlag));
		}
	hwc_mem_dump(NULL);

	ALOGI("--------------------------------------------------------------------------"
			"--------------------------------------------------------------------------\n");

}

void de2Dump(Display_t *display, uint32_t* outSize, char* outBuffer, uint32_t max)
{
	uint32_t count = *outSize;
	DisplayPrivate_t *hwdisplay;
	DELayerPrivate_t *hwlayer;
	struct listnode *node;
	Layer_t *lay;
	private_handle_t *handle;
	hwdisplay = toHwDisplay(display);
	DESource_t *hw = &DESource[display->displayId];
	if (outBuffer == NULL) {
		*outSize += (display->nubmerLayer + display->needclientTarget + 3) * max;
		return;
	}
	if(!display->plugIn) {
		return;
	}

	list_for_each(node, &display->layerSortedByZorder) {
		lay = node_to_item(node, Layer_t, node);
		if (count > (max - 200)) {
			count += sprintf(outBuffer + count, "++++++++++++++++++not end+++++++++++++++++++\n");
			break;
		}
		if (lay->compositionType == HWC2_COMPOSITION_CLIENT_TARGET
				&& !display->needclientTarget)
			continue;
		handle = (private_handle_t *)lay->buffer;
		hwlayer = toHwLayer(lay);
		count += sprintf(outBuffer + count, "%16p|", lay);
		count += sprintf(outBuffer + count, "%16p|", lay->buffer);
		count += sprintf(outBuffer + count, "%10d|", handle != NULL ? handle->format : 0);
		count += sprintf(outBuffer + count, "%2d|", lay->blendMode);
		count += sprintf(outBuffer + count, "%5d|", lay->dataspace);
		count += sprintf(outBuffer + count, "%2d|", lay->transform);
		count += sprintf(outBuffer + count, "%4.2f|", lay->planeAlpha);
		if(handle != NULL)
			count += sprintf(outBuffer + count, "[%6.1f,%6.1f,%6.1f,%6.1f]|", lay->crop.left,
					lay->crop.top, lay->crop.right, lay->crop.bottom);
		else 
			count += sprintf(outBuffer + count, "[%06x,%06x,%06x,%06x]|", lay->color.a,
					lay->color.r, lay->color.g, lay->color.b);
		count += sprintf(outBuffer + count, "[%4d,%4d,%4d,%4d]|", lay->frame.left,
				lay->frame.top, lay->frame.right, lay->frame.bottom);
		count += sprintf(outBuffer + count, "%6d|", lay->zorder);
		count += sprintf(outBuffer + count, "%2d|", hwlayer->zOrder);
		count += sprintf(outBuffer + count, "%2d|", hwlayer->pipe);
		count += sprintf(outBuffer + count, "%2d|", hwlayer->layerId);
		count += sprintf(outBuffer + count, "%s\n", hwcPrintInfo(lay->duetoFlag));
	}
	count += sprintf(outBuffer + count, "disp:%d cur:%d-%d ker:%d\n"
			"--------------------------------------------------------------------------"
			"--------------------------------------------------------------------------\n",
			display->displayId, display->frameCount,display->commitThread->diplayCount, display->commitThread->SubmitCount);
	count += memCtrlDump(outBuffer + count);
	count += hwc_mem_dump(outBuffer + count);
	*outSize = count;
}

int32_t de2PresentDisplay(Display_t *display, struct sync_info *sync, int *layerSize)
{
	DisplayPrivate_t* hwdisplay;
	DESource_t *hw;
	unsigned long arg[4] = {0};
	char* outBuffer = NULL;
	uint32_t outSize = 0;

	arg[0] = display->displayId;
	arg[1] = HWC_ACQUIRE_FENCE;
	arg[2] = (unsigned long)sync;
	arg[3] = 0;
	if (ioctl(dispFd, DISP_HWC_COMMIT, (unsigned long)arg)) {
		ALOGE("hwc get realease fence err!");
		return -ENODEV;
	}
	*layerSize = sizeof(DELayerPrivate_t);
	if (showLayers())
		showlayers(display);
    return 0;
}

static int setActiveConfig(Display_t *display, DisplayConfig_t *config)
{
	unsigned long arg[4] = {0};
	DisplayConfigPrivate_t *hwconfig;
	DisplayPrivate_t *hwdisplay;
	hwconfig = toHwConfig(config);
	hwdisplay = toHwDisplay(display);

	arg[0] = display->displayId;
	arg[1] = hwdisplay->type.type;
	arg[2] = hwconfig->mode;
	hwdisplay->type.mode = hwconfig->mode;

	ALOGD("%s:display:%d  type: %d. mode: %d",
			__FUNCTION__, display->displayId, hwdisplay->type.type, hwconfig->mode);
	if (hwdisplay->type.type == DISP_OUTPUT_TYPE_LCD)
		return 0;

#ifndef HOMLET_PLATFORM
	if (ioctl(dispFd, DISP_DEVICE_SWITCH, (unsigned long)arg) == -1) {
		ALOGE("switch device failed!\n");
	}
#endif
	return 0;
}

int switchDisplay(Display_t *display, int type, int mode)
{
	DisplayPrivate_t *hwdisplay;
	DisplayConfigPrivate_t *hwconfig;
	hwdisplay = toHwDisplay(display);

	int i;
	if (type != (int)hwdisplay->type.type)
		/* if chang for cvbs etc ,will add code */
		return -1;

	for (i = 0; i < display->configNumber; i++) {
		hwconfig = toHwConfig(display->displayConfigList[i]);
		if (display->displayConfigList[i]
			&& hwconfig->mode == mode)
			break;
		else
			hwconfig = NULL;
	}
	if (hwconfig== NULL) {
		ALOGE("%s:hwconfig is NULL", __func__);
		return -1;
	}
	display->activeConfigId = i;
	return setActiveConfig(display, display->displayConfigList[i]);
}

Layer_t* de2creatLayer(Display_t* display)
{
	Layer_t *layer;
	DELayerPrivate_t *deLayer;
	unusedpara(display);

	layer= layerCacheGet(sizeof(DELayerPrivate_t));
	if (layer== NULL) {
		ALOGE("creat layer err...");
		return NULL;
	}
	deLayer = toHwLayer(layer);
	deLayer->pipe = -1;
	deLayer->layerId = -1;
	deLayer->zOrder = -1;
	return layer;
}

int initPermanentDisplay(Display_t *display)
{
	DisplayPrivate_t *hwdisplay;
    int refreshRate, xdpi, ydpi, vsync_period;
	int fbfd = -1;
	DisplayConfig_t *config;
	struct fb_var_screeninfo info;
	hwdisplay = toHwDisplay(display);

	fbfd = open("/dev/graphics/fb0", O_RDWR);
	if (fbfd < 0) {
        ALOGE( "Failed to open fb0 device, ret:%d, errno:%d\n", fbfd, errno);
		return -1;
    }
	if (ioctl(fbfd, FBIOGET_VSCREENINFO, &info) == -1) {
        ALOGE("FBIOGET_VSCREENINFO ioctl failed: %s", strerror(errno));
        return -1;
    }
	config = (DisplayConfig_t*)hwc_malloc(sizeof(DisplayConfig_t) + sizeof(DisplayConfigPrivate_t));
	if (config == NULL){
		ALOGE("malloc display config err...");
		return -1;
	}
    refreshRate = 1000000000000LLU /
                    (uint64_t(info.upper_margin + info.lower_margin + info.vsync_len + info.yres)
                    * ( info.left_margin  + info.right_margin + info.hsync_len + info.xres)
                    * info.pixclock);
    if (refreshRate == 0) {
        ALOGW("invalid refresh rate, assuming 60 Hz");
        refreshRate = 60;
    }
    if (info.width == 0)
        config->dpiX = 160000;
    else
        config->dpiX = 1000 * (info.xres * 25.4f) / info.width;

    if(info.height == 0)
        config->dpiY = 160000;
    else
        config->dpiY = 1000 * (info.yres * 25.4f) / info.height;
	display->displayConfigList = (DisplayConfig_t **)hwc_malloc(sizeof(DisplayConfig_t *));
	if (display->displayConfigList == NULL) {
		ALOGE("malloc lcd err...");
		return -1;
	}
    config->vsyncPeriod = 1000000000 / refreshRate;
	config->width = info.xres;
	config->height = info.yres;
	display->VarDisplayWidth = config->width;
	display->VarDisplayHeight = config->height;
	display->configNumber = 1;
	display->activeConfigId = 0;
	display->displayConfigList[0] = config;
	display->clientTargetLayer->crop.left = 0;
	display->clientTargetLayer->crop.right = config->width;
	display->clientTargetLayer->crop.top = 0;
	display->clientTargetLayer->crop.bottom = config->height;
	display->clientTargetLayer->frame.left = 0;
	display->clientTargetLayer->frame.right = config->width;
	display->clientTargetLayer->frame.top = 0;
	display->clientTargetLayer->frame.bottom = config->height;
    return 0;
}

int initVariableHdmi(Display_t *display)
{
	unsigned long arg[4] = {0};
	int i = 0, num = 0, ret = -1, numbconfig = 0, j = 0, fix = -1;
	DisplayConfig_t *displayconfig;
	DisplayConfigPrivate_t *hwconfig;
	bool all_support = 0;
	DisplayConfig_t *Configtemp;

	arg[0] = display->displayId;
	num = arraySizeOf(hdmi_support);
loop:
	for (i = 0; i < num; i++) {
		if (!all_support) {
			arg[1] = hdmi_support[i].mode;
			ret = ioctl(dispFd, DISP_HDMI_SUPPORT_MODE, arg);
		}
		if((ret > 0) || all_support
			|| (numbconfig && hdmi_support[i].mode == DISP_TV_MOD_1080P_24HZ_3D_FP))
			hdmi_support[i].support = 1;

		else {
			hdmi_support[i].support = 0;
			continue;
		}

		if (hdmi_support[i].mode == display->default_mode) {
			fix = numbconfig;
		}
		numbconfig++;
	}
	if (numbconfig == 0) {
		ALOGD("NO HDMI EDID can read, So we will all support");
		all_support = 1;
		goto loop;
	}

	display->displayConfigList = (DisplayConfig_t **)hwc_malloc(sizeof(DisplayConfig_t *) * numbconfig);

	if (display->displayConfigList == NULL) {
		ALOGE("initVariableHdmi malloc err...");
		return -1;
	}

	for (i = 0,j = 0; i < numbconfig && j < num; i++, j++) {
		while(!hdmi_support[j].support)
			j++;
		displayconfig = (DisplayConfig_t *)hwc_malloc((sizeof(DisplayConfig_t) + sizeof(DisplayConfigPrivate_t)));
		if (displayconfig == NULL) {
			ALOGE("hdmi config malloc err...");
			/* to free the configs */
			return -1;
		}
		/* Andorid O set the 1'st default. not the get active config to set default.
		  * Bug? so we  must set 1'st is the active config.
		  */
		hwconfig = toHwConfig(displayconfig);
		displayconfig->width = hdmi_support[j].width;
		displayconfig->height = hdmi_support[j].height;
		displayconfig->vsyncPeriod = 1000000000 / hdmi_support[j].refreshRate;
		hwconfig->mode = hdmi_support[j].mode;
		hwconfig->screenDisplay = {0, 0, displayconfig->width, displayconfig->height};
		display->displayConfigList[i] = displayconfig;
	}

	if (fix == -1)
		fix = 0;// 1'st is the default and the active config.see the up
	display->configNumber = numbconfig;

	hwconfig = toHwConfig(display->displayConfigList[fix]);
	ALOGD("disp:%d active:%d  mode:%d [%d x %d] maxid:%d", display->displayId, fix,
		hwconfig->mode,display->displayConfigList[fix]->width,display->displayConfigList[fix]->height, numbconfig);
	displayconfig = display->displayConfigList[0];
	display->displayConfigList[0] = display->displayConfigList[fix];
	display->displayConfigList[fix] = displayconfig;
	display->activeConfigId = 0;
	display->default_mode = hwconfig->mode;
	display->VarDisplayWidth = display->displayConfigList[0]->width;
	display->VarDisplayHeight = display->displayConfigList[0]->height;

	setActiveConfig(display, display->displayConfigList[0]);

	return 0;
}

int initVariableCvbs(Display_t *display)
{
	display;
	return 0;
}

extern int hdmifd;
int de2Init(Display_t* display)
{
	DisplayPrivate_t *hwdisplay;
	int ret = 0;
	int fd;
	int fb0Fd;
	submitThread_t *submitThread;
	unsigned long arg[4] = {0};
	long long deFreqtmp;
	char state;
	struct disp_output pricfg;

	pthread_mutex_lock(&display->listMutex);

	if (!list_empty(&display->layerSortedByZorder)) {
			ALOGE("%s:SurfaceFlinger do not destroyed the layer",__FUNCTION__);
			clearList(&display->layerSortedByZorder, 1);
	}
	display->clientTargetLayer = layerCacheGet(sizeof(DELayerPrivate_t));
	if(display->clientTargetLayer == NULL) {
		ALOGE("%s:creat clientTargetLayer err",__FUNCTION__);
		return -1;
	}
	list_init(&display->layerSortedByZorder);
	display->clientTargetLayer->compositionType = HWC2_COMPOSITION_CLIENT_TARGET;
	display->clientTargetLayer->zorder = CLIENT_TARGET_ZORDER;
	insertLayerByZorder(display->clientTargetLayer, &display->layerSortedByZorder);
	pthread_mutex_unlock(&display->listMutex);

	if (display->plugIn == 1) {
		display->retirfence = -1;
		display->nubmerLayer = 0;
		ALOGD("get a plug in display:%d", display->displayId);
		return 0;
	}

	hwdisplay = toHwDisplay(display);
	arg[0] = display->displayId;
	arg[1] = (unsigned long)&hwdisplay->type;
	ret = ioctl(dispFd, DISP_GET_OUTPUT, arg);
	if (ret)
		ALOGE("display get display type fail:%d...", ret);

	if (display->displayId == 1) {
		arg[0] = 0;
		arg[1] = (unsigned long)&pricfg;
		ioctl(dispFd, DISP_GET_OUTPUT, arg);
		if (pricfg.type != DISP_OUTPUT_TYPE_HDMI) {
			if (hdmifd > 0) {
				lseek(hdmifd, 5, SEEK_SET);
				read(hdmifd, &state, 1);
				if (state == '1') {
					hwdisplay->type.type = DISP_OUTPUT_TYPE_HDMI;
				}
			}
		}
	}

	switch (hwdisplay->type.type) {
		case DISP_OUTPUT_TYPE_LCD:
			initPermanentDisplay(display);
			break;
		case DISP_OUTPUT_TYPE_HDMI:
			initVariableHdmi(display);
			break;
		case DISP_OUTPUT_TYPE_TV:
			initVariableCvbs(display);
			break;
		case DISP_OUTPUT_TYPE_VGA:
			break;
		default:
			ALOGE("display get a dissupprt display type ...");
			return -1;
	}

	display->nubmerLayer = 0;
	display->vsyncEn = 1;
	display->plugIn = 1;
	display->retirfence = -1;
	display->hpercent = 100;
	display->vpercent = 100;
	display->dataspace_mode = DISPLAY_OUTPUT_DATASPACE_MODE_SDR;
	display->screenRadio = SCREEN_FULL;

	debugInitDisplay(display);

	submitThread = initSubmitThread(display);
	submitThread->setupLayer = de2SetupLayer;
	submitThread->commitToDisplay = de2commitToDisplay;
	submitThread->delayDeal = de2DelayDeal;

	arg[0] = display->displayId;
	arg[1] = HWC_NEW_CLIENT;
	arg[2] = (unsigned long)&deFreqtmp;
	if (ioctl(dispFd, DISP_HWC_COMMIT, (unsigned long)arg)) {
		ALOGE("start devcomposer failed !!!");
	}

	if (deFreq == 254000000 && deFreqtmp != 254000000)
		deFreq = deFreqtmp;
	ALOGD("init a new display:%d", display->displayId);

	return 0;
}

int clearAllLayers(int displayId)
{
	int ret = 0;
	unsigned long arg[4] = {0};
	unsigned int chn = displayId ? 2 : 4;
	unsigned int ly = 4;
	unsigned int i, j;

	struct disp_layer_config2 layersSet[chn][ly];
	memset(layersSet, 0, sizeof(layersSet));
	for(i = 0; i < chn; i++) {
		for(j = 0; j < ly; j++) {
			layersSet[i][j].enable = false;
			layersSet[i][j].channel = i;
			layersSet[i][j].layer_id = j;
		}
	}
	/* open protect. */
	arg[0] = displayId;
	arg[1] = 1;

	ret = ioctl(dispFd, DISP_SHADOW_PROTECT, (unsigned long)arg);
	if(ret != 0)
	{
		ALOGE("%d err: DISP_SHADOW_PROTECT failed", __LINE__);
		return -1;
	}

	arg[1] = (unsigned long)(&layersSet[0][0]);
	arg[2] = chn * ly;
	ret = ioctl(dispFd, DISP_LAYER_SET_CONFIG2, (unsigned long)arg);
	if(ret != 0)
	{
		ALOGE("%d err: DISP_LAYER_SET_CONFIG2 failed", __LINE__);
		return -1;
	}

	arg[0] = displayId;
	arg[1] = 0;
	ret = ioctl(dispFd, DISP_SHADOW_PROTECT, (unsigned long)arg);
	if(ret != 0)
	{
		ALOGE("%d err: DISP_SHADOW_PROTECT failed", __LINE__);
		return -1;
	}
	return 0;
}

int de2Deinit(Display_t *display)
{
	DisplayPrivate_t *displayPri = NULL;
	unsigned long arg[4] = {0};

	displayPri = toHwDisplay(display);
	deinitSubmitTread(display);
	debugDeDisplay(display);
	/* free the timeline */
	display->plugIn = 0;
	display->active = 0;

	clearAllLayers(display->displayId);

	pthread_mutex_lock(&display->listMutex);
	while (display->configNumber--) {
		if (display->displayConfigList[display->configNumber])
			hwc_free(display->displayConfigList[display->configNumber]);
		display->displayConfigList[display->configNumber] = NULL;
	}
	hwc_free(display->displayConfigList);
	display->displayConfigList = NULL;
	display->clientTargetLayer = NULL;
	clearList(&display->layerSortedByZorder, 1);
	pthread_mutex_unlock(&display->listMutex);

	arg[0] = display->displayId;
	arg[1] = HWC_DESTROY_CLIENT;

	if (ioctl(dispFd, DISP_HWC_COMMIT, (unsigned long)arg)) {
		ALOGE("destroy a timeline %d !!!", display->displayId);
	}
	return 0;
}

int32_t de2SetPowerMode(Display_t* display, int32_t mode)
{
   	DisplayPrivate_t *hwdisplay;
	hwdisplay = toHwDisplay(display);
    unsigned long arg[4] = {0};
    int ret = 0;
    unsigned long vsyncEn = 0;
    arg[0] = display->displayId;

    switch (mode) {
    case HWC2_POWER_MODE_OFF:
        arg[1] = 1;
        vsyncEn = 0;
	clearAllLayers(display->displayId);
        break;
    case HWC2_POWER_MODE_DOZE:
    case HWC2_POWER_MODE_DOZE_SUSPEND:
    case HWC2_POWER_MODE_ON:
        arg[1] = 0;
        vsyncEn = 1;
        break;
    }
	ALOGD("set mode %d %ld",mode, vsyncEn);
   	if (ioctl(dispFd, DISP_BLANK, arg)) {
        ALOGE("DISP_BLANK ioctl failed: %s", strerror(errno));
        return -1;
    }

    arg[1] = vsyncEn;
    if (ioctl(dispFd, DISP_VSYNC_EVENT_EN, arg)) {
            ALOGE("DISP_CMD_VSYNC_EVENT_EN ioctl failed: %s", strerror(errno));
            return -1;
    }
    display->vsyncEn = !!vsyncEn;

    return 0;
}

int32_t de2SetVsyncEnabled(Display_t *display, int32_t enabled)
{
	DisplayPrivate_t *hwdisplay;
	unsigned long arg[4] = {0};
	hwdisplay = toHwDisplay(display);

	arg[0] = display->displayId;
	arg[1] = (enabled == HWC2_VSYNC_ENABLE)?1:0;
	arg[1] = 0;
	ALOGV("set display%d vsync enable:%d",display->displayId, enabled);
	if (ioctl(dispFd, DISP_VSYNC_EVENT_EN, arg)) {
		ALOGE("DISP_CMD_VSYNC_EVENT_EN ioctl failed: %s", strerror(errno));
		return -1;
	}
	display->vsyncEn = !!enabled;

	return 0;
}

int setDisplayName(Display_t *display)
{
	DisplayPrivate_t *hwdisplay;
	hwdisplay = toHwDisplay(display);

	switch (hwdisplay->type.type) {
		case DISP_OUTPUT_TYPE_LCD:
			display->displayName = displayName[0];
			break;
		case DISP_OUTPUT_TYPE_HDMI:
			display->displayName = displayName[1];
			break;
		case DISP_OUTPUT_TYPE_TV:
			display->displayName = displayName[2];
			break;
		case DISP_OUTPUT_TYPE_VGA:
			display->displayName = displayName[3];
			break;
		default:
			display->displayName = displayName[1];
			break;
	}
	return 0;
}

int displayDeviceInit(Display_t ***display)
{
	int i = 0, j = 0, ret = -1, fixdiplay = 0;
	Display_t **dispArray, *disp;
	struct disp_output outPut;
	unsigned long arg[4] = {0};
	DisplayPrivate_t *hwdisplay;

	dispFd = open("/dev/disp", O_RDWR);
	if (dispFd < 0) {
		ALOGE("failed open disp device.");
		return 0;
	}

	/* if we have LCD or other permanent, set it primer display */
	dispArray = (Display_t **)hwc_malloc(DE_NUM * (sizeof(Display_t*)));
	if(dispArray == NULL) {
		ALOGE("Alloc display err, Can not initial the hwc module.");
		return -1;
	}
	/*  if support visual display will change the num
	 *  for disp 's varable data, must remember.......
	 */
	for (i = 0; i < DE_NUM; i++) {
		disp = (Display_t *)hwc_malloc(sizeof(Display_t) + sizeof(DisplayPrivate_t));
		if (disp == NULL) {
			ALOGE("Alloc display err, Can not initial the hwc module.");
			return -1;
		}
		hwdisplay = toHwDisplay(disp);
		disp->displayOpration = &sunxiDisplayOpr;
		disp->displayId = i;
		disp->default_mode = DISP_TV_MOD_1080P_60HZ;

		arg[0] = i;
		arg[1] = (unsigned long)&hwdisplay->type;
		ret = ioctl(dispFd, DISP_GET_OUTPUT, arg);
		if (ret > 0)
			ALOGV("get [Disp%d] output type is not NONE!\n", i);
		else
			ALOGE("get [Disp%d] output type is NONE!\n", i);

		if (hwdisplay->type.type == DISP_OUTPUT_TYPE_LCD) {
			ALOGD("find Permanent display:%d", i);
			fixdiplay = i + 1;
		}
		setDisplayName(disp);
		DESource[i].fixPipeNumber = (i == 0 ? 4 : 2);
		DESource[i].fixVideoPipeNum = 1;
		DESource[i].currentPipe = 0;

		DESource[i].layerInfo = (disp_layer_config2 *)hwc_malloc(DESource[i].fixPipeNumber * 4 * sizeof(disp_layer_config2));
		DESource[i].Pipe = (PipeInfo_t*)hwc_malloc(DESource[i].fixPipeNumber * sizeof(PipeInfo_t));

		if (DESource[i].layerInfo == NULL
				|| DESource[i].Pipe == NULL ) {
			ALOGE("Calloc DE resource err, Can not initial the hwc...");
			return 0;
		}
		list_init(&disp->layerSortedByZorder);
		pthread_mutex_init(&disp->listMutex, 0);

		for (j = 0; j < DESource[i].fixPipeNumber * 4; j++) {
			DESource[i].layerInfo[j].channel = j / 4;
			DESource[i].layerInfo[j].layer_id = j % 4;
			DESource[i].layerInfo[j].info.zorder = j;
		}
		dispArray[i] = disp;
	}
	if (!(fixdiplay & 1) && (fixdiplay & 2)){
		/* the perminent disp is primory display */
		disp = dispArray[0];
		dispArray[0] = dispArray[1];
		dispArray[1] = disp;
	}

	if (!strcmp(dispArray[0]->displayName, "hdmi"))
		dispArray[1]->displayName = displayName[2];


	ALOGD("display Id-Type:%d-%d and %d-%d", dispArray[0]->displayId, toHwDisplay(dispArray[0])->type.type,
			dispArray[1]->displayId, toHwDisplay(dispArray[1])->type.type);

	*display = dispArray;
	return DE_NUM;
}

int displayDeviceDeInit(Display_t **display)
{
	int i;
	if (dispFd < 0) {
		ALOGE("failed open disp device.");
		return 0;
	}
	close(dispFd);
	dispFd = -1;
	for (i = 0; i < DE_NUM; i++) {
		clearList(&display[i]->layerSortedByZorder, 1);
		display[i]->clientTargetLayer = NULL;
		hwc_free(DESource[i].layerInfo);
		hwc_free(DESource[i].Pipe);
		DESource[i].layerInfo = NULL;
		DESource[i].Pipe = NULL;
		deinitSubmitTread(display[i]);
		hwc_free(display[i]);
	}
	hwc_free(display);
	ALOGD(" displayDeviceDeInit ");
	return 0;
}

DisplayOpr sunxiDisplayOpr = {
	.createLayer = de2creatLayer,
	.AssignLayer = de2AssignLayer,
	.presentDisplay = de2PresentDisplay,
	.dump = de2Dump,
	.init = de2Init,
	.deInit = de2Deinit,
	.setPowerMode = de2SetPowerMode,
	.setVsyncEnabled = de2SetVsyncEnabled,
	.setActiveConfig = setActiveConfig,
	.memCtrlAddLayer = de2memCtrlAddLayer,
};
