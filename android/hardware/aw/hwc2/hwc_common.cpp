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

#include "hwc.h"
#include <hardware/sunxi_metadata_def.h>

bool regionCrossed(hwc_rect_t *rect0, hwc_rect_t *rect1, hwc_rect_t *rectx)
{
    hwc_rect_t tmprect;

    tmprect.left = rect0->left > rect1->left ? rect0->left : rect1->left;
    tmprect.right = rect0->right < rect1->right ? rect0->right : rect1->right;
    tmprect.top = rect0->top > rect1->top ? rect0->top : rect1->top;
    tmprect.bottom = rect0->bottom < rect1->bottom ? rect0->bottom : rect1->bottom;

    if((tmprect.left < tmprect.right) && (tmprect.top < tmprect.bottom))
    {
        if(rectx != NULL)
        {
            *rectx = tmprect;
        }
        return 1;//return 1 is crossed
    }
    if(rectx != NULL)
    {
        rectx->bottom = 0;
        rectx->left = 0;
        rectx->right = 0;
        rectx->top = 0;
    }
    return 0;
}

bool checkLayerCross(Layer_t *srclayer, Layer_t *destlayer)
{
	hwc_rect_t rectx;
	return regionCrossed(&srclayer->frame, &destlayer->frame, &rectx);
}

void calcLayerFactor(Layer_t *layer, float *width, float *hight)
{
	float srcW, srcH, swap;
	int dstW,dstH;

	dstW = layer->frame.right - layer->frame.left;
	dstH = layer->frame.bottom - layer->frame.top;
	srcW = layer->crop.right - layer->crop.left;
	srcH = layer->crop.bottom - layer->crop.top;
	if(layer->transform & HAL_TRANSFORM_ROT_90) {
		swap = srcW;
		srcW = srcH;
		srcH = swap;
	}
	*width = dstW / srcW;
	*hight = dstH / srcH;

}

bool layerIsScale(Display_t *display, Layer_t *layer)
{
    float wFactor = 1;
    float hFactor = 1;
	unusedpara(display);
	if (layer->compositionType == HWC2_COMPOSITION_SOLID_COLOR)
		return false;
    calcLayerFactor(layer, &wFactor, &hFactor);

    return !checkFloatSame(wFactor, 1.0) || !checkFloatSame(hFactor, 1.0);
}

bool layerIsVideo(Layer_t *layer)
{
	private_handle_t *handle = NULL;

	handle = (private_handle_t *)layer->buffer;

	if (handle == NULL) {
		return false;
	}
	if (layer->compositionType == HWC2_COMPOSITION_SOLID_COLOR) {
		/* Buffer handle is null */
		return false;
	}

	switch (handle->format) {

	case HAL_PIXEL_FORMAT_YV12:
	case HAL_PIXEL_FORMAT_YCrCb_420_SP:
	case HAL_PIXEL_FORMAT_AW_NV12:
#if (DE_VERSION == 30)
	case HAL_PIXEL_FORMAT_AW_YV12_10bit:
	case HAL_PIXEL_FORMAT_AW_I420_10bit:
	case HAL_PIXEL_FORMAT_AW_NV21_10bit:
	case HAL_PIXEL_FORMAT_AW_NV12_10bit:
	case HAL_PIXEL_FORMAT_AW_P010_UV:
	case HAL_PIXEL_FORMAT_AW_P010_VU:
#endif
		return true;
	default:
		return false;
	}
}

bool isSameForamt(Layer_t *layer1, Layer_t *layer2)
{
	private_handle_t *handle1 = NULL;
	private_handle_t *handle2 = NULL;
	handle1 = (private_handle_t *)layer1->buffer;
	handle2 = (private_handle_t *)layer2->buffer;

	/* THK handle == NULL */
	if (handle1 == NULL)
		return false;
	if ((handle1 == NULL) ^ (handle2 == NULL))
		return false;
	if (handle1->format != handle2->format) {
		return false;
	}
    return true;
}

int is_afbc_buf(buffer_handle_t handle)
{
	if (NULL != handle) {
#if GRALLOC_SUNXI_METADATA_BUF
		private_handle_t *hdl = (private_handle_t *)handle;
		int metadata_fd = hdl->metadata_fd;
		unsigned int flag = hdl->ion_metadata_flag;
		if ((0 <= metadata_fd)
				&& (flag & SUNXI_METADATA_FLAG_AFBC_HEADER)) {
			return 1;
		}
#endif
	}
	return 0;
}

bool checkDealContiMem(Layer_t *layer)
{
	private_handle_t *handle = NULL;
	handle = (private_handle_t *)layer->buffer;
	if (handle == NULL)
		return false;

	if (!(handle->flags & SUNXI_MEM_CONTIGUOUS))
		return false;
	return true;
}

bool checkSwWrite(Layer_t *layer)
{
	private_handle_t *handle = NULL;
	handle = (private_handle_t *)layer->buffer;
	if (handle == NULL)
		return false;
	return (handle->usage & GRALLOC_USAGE_SW_WRITE_MASK);
}

char info[7][20] = {
	{"Device"},
	{"Client"},
	{"Solid"},
	{"Cursor"},
	{"Sideband"},
	{"Target"},
	{"unkown"},
};

char* layerType(Layer_t *layer)
{
	switch (layer->compositionType)
	{
	case HWC2_COMPOSITION_DEVICE:
		return info[0];
	case HWC2_COMPOSITION_CLIENT:
		return info[1];
	case HWC2_COMPOSITION_SOLID_COLOR:
		return info[2];	
	case HWC2_COMPOSITION_CURSOR:
		return info[3];
	case HWC2_COMPOSITION_SIDEBAND:
		return info[4];
	case HWC2_COMPOSITION_CLIENT_TARGET:
		return info[5];
	default:
		return info[6];

	}
}

