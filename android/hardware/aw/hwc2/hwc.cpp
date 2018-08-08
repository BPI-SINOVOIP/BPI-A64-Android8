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
#include <utils/Trace.h>

#ifdef HOMLET_PLATFORM
#include "other/homlet.h"
#endif

static int numberDisplay;
static Display_t **mDisplay;
static int socketpair_fd[2];

char dumpchar[] = {
"     layer      |  handle        |  format  |"
"bl|space|TR|pAlp|     crop or color       "
"|       frame         |zOrder|hz|ch|id|duto\n"
};

int find_config(Display_t *dp, hwc2_config_t config)
{
	int	i;
	if (Hwc2ConfigToDisp(config) != dp->displayId) {
		goto bad;
	}
	i = Hwc2ConfigTohwConfig(config);
	if (i < dp->configNumber)
		return i;
bad:
	ALOGE("find a err display config%08x", config);
    return BAD_HWC_CONFIG;
}
void hwc_device_getCapabilities(struct hwc2_device* device, uint32_t* outCount,
    int32_t* /*hwc2_capability_t*/ outCapabilities)
{
	unusedpara(device);

    if(outCapabilities == NULL){
        *outCount = 0;
    }else{
        *outCapabilities = HWC2_CAPABILITY_INVALID;
    }
}

int32_t hwc_create_virtual_display(hwc2_device_t* device, uint32_t width, uint32_t height,
    int32_t* format, hwc2_display_t* outDisplay)
{
	unusedpara(device);
	unusedpara(width);
	unusedpara(height);
	unusedpara(format);
	unusedpara(outDisplay);

    ALOGE("ERROR %s: do not support virtual display", __FUNCTION__);
    return HWC2_ERROR_NO_RESOURCES;
}

int32_t hwc_destroy_virtual_display(hwc2_device_t* device, hwc2_display_t display)
{
	unusedpara(device);
	unusedpara(display);

    return HWC2_ERROR_NONE;
}

void hwc_dump(hwc2_device_t* device, uint32_t* outSize, char* outBuffer)
{
	int i = 0;
	uint32_t cout = 0;
	unusedpara(device);

	if (outBuffer != NULL) {
		cout += sprintf(outBuffer, "     layer      |  handle        |  format  |"
						"bl|space|TR|pAlp|       crop or color         "
						"|       frame         |zOrder|hz|ch|id|duto\n");
		cout += sprintf(outBuffer + cout,
			"--------------------------------------------------------------------------"
			"--------------------------------------------------------------------------\n");
	}else{
		cout += sizeof(dumpchar);
		*outSize = cout;
	}
	for (i = 0; i < numberDisplay; i++) {
       mDisplay[i]->displayOpration->dump(mDisplay[i], &cout, outBuffer, *outSize);
	}

	if (outBuffer == NULL)
		*outSize = cout;
}

uint32_t hwc_get_max_virtual_display_count(hwc2_device_t* device)
{
	unusedpara(device);

    return 0;
}

bool findDisplay(hwc2_display_t display)
{
    for(int i = 0; i < numberDisplay; i++){
        if(mDisplay[i] == ((Display_t *)display)) {
            return true;
        }
    }
    return false;
}

int32_t hwc_register_callback(hwc2_device_t* device, int32_t descriptor,
    hwc2_callback_data_t callbackData, hwc2_function_pointer_t pointer)
{
	/* hot plug surfaceflinger care second display,but must call primary display for first.
	  * vsync and refresh surfaceflinger care all.
	  */

	unusedpara(device);
	switch(descriptor){
        case HWC2_CALLBACK_HOTPLUG:
		registerEventCallback(0x03, descriptor, 1, callbackData, pointer);
		break;
        case HWC2_CALLBACK_REFRESH:
        case HWC2_CALLBACK_VSYNC:
		registerEventCallback(0x3, descriptor, 0, callbackData, pointer);
		break;
        default:
		ALOGE("ERROR %s: bad parameter", __FUNCTION__);
		return HWC2_ERROR_BAD_PARAMETER;
    }
    return HWC2_ERROR_NONE;
}

int32_t hwc_accept_display_changes(hwc2_device_t* device, hwc2_display_t display)
{
    Display_t *dp = (Display_t *)display;

	unusedpara(device);
    if(!findDisplay(display)) {
		ALOGE("ERROR %s:bad display", __FUNCTION__);
		return HWC2_ERROR_BAD_DISPLAY;
    }

    return HWC2_ERROR_NONE;
}

int32_t hwc_create_layer(hwc2_device_t* device, hwc2_display_t display, hwc2_layer_t* outLayer)
{
    Layer_t* layer;
	Display_t *dp = (Display_t *) display;
	DisplayOpr_t *opt;
	unusedpara(device);

    if(!findDisplay(display)){
        return HWC2_ERROR_BAD_DISPLAY;
    }
	dp->active = 1;
	opt = dp->displayOpration;
    layer = opt->createLayer(dp);
    if(layer == NULL){
        ALOGE("ERROR %s:not enought memory to allow!", __FUNCTION__);
        return HWC2_ERROR_NO_RESOURCES;
    }

    *outLayer = (hwc2_layer_t)layer;

	return HWC2_ERROR_NONE;
}

int32_t hwc_destroy_layer(hwc2_device_t* device, hwc2_display_t display, hwc2_layer_t layer)
{

    Layer_t* ly = (Layer*)layer;
    Display_t *dp = (Display_t *) display;
	unusedpara(device);

	if(!findDisplay(display)) {
        ALOGE("ERROR %s:bad display", __FUNCTION__);
        return HWC2_ERROR_BAD_DISPLAY;
    }

	memCtrlCheckResv(ly, false);
	pthread_mutex_lock(&dp->listMutex);
	if (deletLayerByZorder(ly, &dp->layerSortedByZorder)) {
		dp->nubmerLayer--;
    	layerCachePut(ly);
	}
	pthread_mutex_unlock(&dp->listMutex);
	if (!dp->plugIn) {
		/* fix android comper 2.1 hotplug remove the display' s bug  */
		return HWC2_ERROR_NO_RESOURCES;
	}
 	return HWC2_ERROR_NONE;
}

int32_t hwc_get_active_config(hwc2_device_t* device, hwc2_display_t display,
    hwc2_config_t* outConfig)
{
    Display_t *dp = (Display_t *) display;
	unusedpara(device);

    if(!findDisplay(display)) {
        ALOGE("ERROR %s:bad display", __FUNCTION__);
        return HWC2_ERROR_BAD_DISPLAY;
    }

    *outConfig = toHwc2Config(dp->displayId, dp->activeConfigId);
    return HWC2_ERROR_NONE;
}

int32_t hwc_get_changed_composition_types(hwc2_device_t* device, hwc2_display_t display,
    uint32_t* outNumElements, hwc2_layer_t* outLayers, int32_t* outTypes)
{
	Display_t* dp = (Display_t *)display;
	int num = 0;
	struct listnode *list;
	struct listnode *node;
	Layer_t *ly;
	unusedpara(device);

	if(!findDisplay(display)) {
		ALOGE("ERROR %s:bad display", __FUNCTION__);
		return HWC2_ERROR_BAD_DISPLAY;
	}

	list = &(dp->layerSortedByZorder);
	if(outLayers == NULL | outTypes == NULL){
		list_for_each(node, list) {
			ly = node_to_item(node, Layer, node);
			if (ly != NULL
					&& ly->typeChange
					&& ly->compositionType != HWC2_COMPOSITION_CLIENT_TARGET){
				num++;
			}
		}
		*outNumElements = num;
		return HWC2_ERROR_NONE;
	}else{
		list_for_each(node, list) {
			ly = node_to_item(node, Layer_t, node);
			if(ly != NULL && ly->typeChange && ly->compositionType != HWC2_COMPOSITION_CLIENT_TARGET){
				*outLayers = (hwc2_layer_t)ly;
				outLayers++;
				*outTypes = ly->compositionType;
				outTypes++;
				ALOGV("%s: layer=%p, type=%d", __FUNCTION__, ly, ly->compositionType);
			}
		}
		return HWC2_ERROR_NONE;
	}
}

int32_t hwc_get_client_target_support(hwc2_device_t* device, hwc2_display_t display,
    uint32_t width, uint32_t height, int32_t format, int32_t dataspace)
{
	unusedpara(device);

    ALOGV("%s display=%p, width=%d, height=%d, format=%d, dataspace=%x",
        __FUNCTION__, (void*)display, width, height, format, dataspace);

    if(!findDisplay(display)) {
        ALOGE("ERROR %s:bad display", __FUNCTION__);
        return HWC2_ERROR_BAD_DISPLAY;
    }
    if(format > HAL_PIXEL_FORMAT_BGRA_8888) {
        ALOGE("ERROR %s:unsupported", __FUNCTION__);
        return HWC2_ERROR_UNSUPPORTED;
    }
    return HWC2_ERROR_NONE;
}

int32_t hwc_get_color_modes(hwc2_device_t* device, hwc2_display_t display, uint32_t* outNumModes,
    int32_t* outModes)
{
	unusedpara(device);

    if(!findDisplay(display)) {
        ALOGE("ERROR %s:bad display", __FUNCTION__);
        return HWC2_ERROR_BAD_DISPLAY;
    }
    if (!outModes){
        *outNumModes = 1;
        return HWC2_ERROR_NONE;
    }else{
        *outModes = 1;

    }
    return HWC2_ERROR_NONE;
}

int32_t hwc_get_display_attribute(hwc2_device_t* device, hwc2_display_t display,
    hwc2_config_t config, int32_t attribute, int32_t* outValue)
{
    Display_t* dp = (Display_t*) display;
	DisplayConfig_t* cfg;
	int i;
	unusedpara(device);

    if(!findDisplay(display)) {
        ALOGE("ERROR %s:bad display", __FUNCTION__);
        return HWC2_ERROR_BAD_DISPLAY;
    }
	i = find_config(dp, config);
    if (i != BAD_HWC_CONFIG) {
        cfg = dp->displayConfigList[i];
        switch (attribute) {
            case HWC2_ATTRIBUTE_WIDTH:
                *outValue = cfg->width;
                break;
            case HWC2_ATTRIBUTE_HEIGHT:
                *outValue = cfg->height;
                break;
            case HWC2_ATTRIBUTE_VSYNC_PERIOD:
                *outValue = cfg->vsyncPeriod;
                break;
            case HWC2_ATTRIBUTE_DPI_X:
                *outValue = cfg->dpiX;
                break;
            case HWC2_ATTRIBUTE_DPI_Y:
                *outValue = cfg->dpiY;
                break;
            default:
                goto failed;
        }
        return HWC2_ERROR_NONE;
    }

failed:
    ALOGE("ERROR %s:bad config", __FUNCTION__);
    return HWC2_ERROR_BAD_CONFIG;

}

int32_t hwc_get_display_configs(hwc2_device_t* device, hwc2_display_t display,
    uint32_t* outNumConfigs, hwc2_config_t* outConfigs)
{
    Display_t *dp = (Display_t *) display;
	unusedpara(device);

    if(!findDisplay(display)) {
        ALOGE("ERROR %s:bad display", __FUNCTION__);
        return HWC2_ERROR_BAD_DISPLAY;
    }

    if(outConfigs == NULL){
        *outNumConfigs = dp->configNumber;
    }else{
        for(uint32_t i = 0; i < *outNumConfigs; i++){
            *outConfigs = toHwc2Config(dp->displayId, i);
            outConfigs++;
        }
    }

    return HWC2_ERROR_NONE;
}

int32_t hwc_get_display_name(hwc2_device_t* device, hwc2_display_t display, uint32_t* outSize,
    char* outName)
{
	Display_t *dp = (Display_t *) display;
	unusedpara(device);

    if(!findDisplay(display)) {
        ALOGE("ERROR %s:bad display", __FUNCTION__);
        return HWC2_ERROR_BAD_DISPLAY;
    }

    if(outName == NULL){
        *outSize = strlen(dp->displayName);
    }else{
        strncpy(outName, dp->displayName, *outSize);
    }
    return HWC2_ERROR_NONE;
}

int32_t hwc_get_display_requests(hwc2_device_t* device, hwc2_display_t display,
    int32_t* outDisplayRequests, uint32_t* outNumElements, hwc2_layer_t* outLayers,
    int32_t* outLayerRequests)
{
    Display_t* dp = (Display_t *)display;
	struct listnode *list = NULL;
    struct listnode *node;
    Layer_t *ly;
    int num = 0;

	unusedpara(device);

	/* JetCui: only get the FB clear flags dueto the hwc2.c */
    if(!findDisplay(display)) {
        ALOGE("ERROR %s:bad display", __FUNCTION__);
        return HWC2_ERROR_BAD_DISPLAY;
    }

	list = &(dp->layerSortedByZorder);
    if(outLayers == NULL || outLayerRequests == NULL){

        list_for_each(node, list) {
            ly = node_to_item(node, Layer_t, node);
			if (ly->compositionType == HWC2_COMPOSITION_CLIENT_TARGET) {
				if (dp->needclientTarget)
					*outDisplayRequests = HWC2_DISPLAY_REQUEST_FLIP_CLIENT_TARGET;
				continue;
			}
            if (ly != NULL && ly->clearClientTarget){
                num++;
            }
        }
        *outNumElements = num;
        return HWC2_ERROR_NONE;
    }else{
        list_for_each(node, list) {
            ly = node_to_item(node, Layer_t, node);
			if (ly->compositionType == HWC2_COMPOSITION_CLIENT_TARGET) {
				if (dp->needclientTarget)
					*outDisplayRequests = HWC2_DISPLAY_REQUEST_FLIP_CLIENT_TARGET;
				continue;
			}
            if (!ly->clearClientTarget){
                continue;
            }
            *outLayers = (hwc2_layer_t)ly;
            outLayers++;
            *outLayerRequests = HWC2_LAYER_REQUEST_CLEAR_CLIENT_TARGET;
            outLayerRequests++;
        }

        return HWC2_ERROR_NONE;
    }
}

int32_t hwc_get_display_type(hwc2_device_t* device, hwc2_display_t display, int32_t* outType)
{
	unusedpara(device);
	

    if(!findDisplay(display)){
        ALOGE("ERROR %s:bad display", __FUNCTION__);
        return HWC2_ERROR_BAD_DISPLAY;
    }
    *outType = HWC2_DISPLAY_TYPE_PHYSICAL;
    return HWC2_ERROR_NONE;
}

int32_t hwc_get_doze_support(hwc2_device_t* device, hwc2_display_t display, int32_t* outSupport)
{
	unusedpara(device);
	

    if(!findDisplay(display)){
        ALOGE("ERROR %s:bad display", __FUNCTION__);
        return HWC2_ERROR_BAD_DISPLAY;
    }
    *outSupport = 0;
    return HWC2_ERROR_NONE;
}

int32_t hwc_get_hdr_capabilities(hwc2_device_t* device, hwc2_display_t display,
    uint32_t* outNumTypes,int32_t* outTypes, float* outMaxLuminance,
    float* outMaxAverageLuminace, float* outMinLuminance)
{
	unusedpara(device);
	unusedpara(outMaxLuminance);
	unusedpara(outMaxAverageLuminace);
	unusedpara(outMinLuminance);
    if(!findDisplay(display)){
        ALOGE("ERROR %s:bad display", __FUNCTION__);
        return HWC2_ERROR_BAD_DISPLAY;
    }
    if(outTypes == NULL){
        *outNumTypes = 0;
    }
    return HWC2_ERROR_NONE;
}

int32_t hwc_get_release_fences(hwc2_device_t* device, hwc2_display_t display,
    uint32_t* outNumElements, hwc2_layer_t* outLayers, int32_t* outFences)
{

	Display_t *dp = (Display_t *)display;
	struct listnode *list = &(dp->layerSortedByZorder);
	struct listnode *node;
	Layer_t *ly;
	hwc2_layer_t* retLy = outLayers;
	int32_t* retFence = outFences;
	unusedpara(device);

	if(!findDisplay(display)){
		ALOGE("ERROR %s:bad display", __FUNCTION__);
		return HWC2_ERROR_BAD_DISPLAY;
	}

	if (outLayers == NULL || outFences == NULL){
		*outNumElements = dp->nubmerLayer;
		ALOGV("%s :display=%d, nubmerlayer=%d", __FUNCTION__, dp->displayId, *outNumElements);
	}else{
		if (*outNumElements != dp->nubmerLayer)
			ALOGE("%s:has %u but need %d ",__FUNCTION__,*outNumElements, dp->nubmerLayer);
		list_for_each(node, list) {
			ly = node_to_item(node, Layer_t, node);
			if(ly->compositionType == HWC2_COMPOSITION_CLIENT_TARGET){
				continue;
			}
			*outLayers = (hwc2_layer_t)ly;
			outLayers++;

			ALOGV("%s: frame:%d, ID=%d, layer=%p, Fence=%d", __FUNCTION__, dp->frameCount-1, dp->displayId, ly, ly->preReleaseFence);
			if (ly->preReleaseFence >= 0) {
				*outFences = dup(ly->preReleaseFence);
				close(ly->preReleaseFence);
				ly->preReleaseFence = -1;
			} else {
				*outFences = -1;
			}
			ly->preReleaseFence = ly->releaseFence;
			ly->releaseFence = -1;
			outFences++;
		}
	}
	return HWC2_ERROR_NONE;
}

int32_t hwc_present_display(hwc2_device_t* device, hwc2_display_t display,
    int32_t* outRetireFence)
{
	ATRACE_CALL();

	int privateLayerSize = 0;
	LayerSubmit_t *submitLayer;
	Layer_t *layer, *layer2;
	Display_t *dp;
	DisplayOpr_t *dispOpr;
	struct listnode *node;
	unusedpara(device);
	struct sync_info sync;

	if (!findDisplay(display)) {
		ALOGE("ERROR %s:bad display", __FUNCTION__);
		return HWC2_ERROR_BAD_DISPLAY;
	}

	dp = (Display_t*) display;
	dp->active = 1;
	dispOpr = dp->displayOpration;

	pthread_mutex_lock(&dp->listMutex);
	if(dispOpr->presentDisplay(dp, &sync, &privateLayerSize)) {
		pthread_mutex_unlock(&dp->listMutex);
		ALOGE("ERROR %s:present err...", __FUNCTION__);
		return HWC2_ERROR_NO_RESOURCES;
	}
	submitLayer = submitLayerCacheGet();
	if (submitLayer == NULL) {
		pthread_mutex_unlock(&dp->listMutex);
		ALOGE("get submit layer err...");
		return HWC2_ERROR_NO_RESOURCES; 
	}	

	list_for_each(node, &dp->layerSortedByZorder) {
		layer = node_to_item(node, Layer_t, node);

		if (layer->releaseFence >= 0)
			close(layer->releaseFence);
		layer->releaseFence = -1;

		if (layer->compositionType != HWC2_COMPOSITION_CLIENT) {
			if (layer->compositionType == HWC2_COMPOSITION_CLIENT_TARGET
					&& !dp->needclientTarget)
				goto deal;

			layer2 = layerDup(layer, privateLayerSize);
			if (layer2 == NULL) {
				pthread_mutex_unlock(&dp->listMutex);
				ALOGE("layer dup err...");
				goto err_setup_layer;
			}

			if (layer->transform == 0) {
				if (layer->compositionType != HWC2_COMPOSITION_CLIENT_TARGET)
					layer->releaseFence = dup(sync.fd);
			} else {
				if (layer->compositionType != HWC2_COMPOSITION_CLIENT_TARGET)
					layer->releaseFence = get_rotate_fence_fd(layer2, dp, sync.fd, dp->frameCount);
			}

			ALOGV("%s:%p frame:%d fence:%d  sync:%d", __FUNCTION__, layer, dp->frameCount, layer->releaseFence, sync.count);
			list_add_tail(&submitLayer->layerNode, &layer2->node);
		}
deal:
		if (layer->acquireFence >= 0)
			close(layer->acquireFence);
		layer->acquireFence = -1;
	}

	pthread_mutex_unlock(&dp->listMutex);
	submitLayer->frameCount = dp->frameCount;
	submitLayer->currentConfig = dp->displayConfigList[dp->activeConfigId];
	submitLayer->sync = sync;

	submitLayerToDisplay(dp, submitLayer);
	*outRetireFence = dp->retirfence;
	dp->retirfence = dup(sync.fd);
	dp->frameCount++;

	return HWC2_ERROR_NONE;

err_setup_layer:
	ALOGE("hwc set err....");
	return HWC2_ERROR_NO_RESOURCES;

}

int32_t hwc_set_active_config(hwc2_device_t* device, hwc2_display_t display,
    hwc2_config_t config)
{
	DisplayConfig_t* cfg;
	Display_t *dp = (Display_t *) display;
	int i, ret;
	unusedpara(device);

	if(!findDisplay(display)){
		ALOGE("ERROR %s:bad display", __FUNCTION__);
		return HWC2_ERROR_BAD_DISPLAY;
	}
	i = find_config(dp, config);
	if (i != BAD_HWC_CONFIG) {
		dp->activeConfigId = i;
		ret = dp->displayOpration->setActiveConfig(dp, dp->displayConfigList[i]);
		if (ret < 0) {
			ALOGE("ERROR %s:bad switch config", __FUNCTION__);
			return HWC2_ERROR_BAD_CONFIG;
		}
		return HWC2_ERROR_NONE;
	}

	ALOGE("ERROR %s:bad config", __FUNCTION__);
	return HWC2_ERROR_BAD_CONFIG;
}

int32_t hwc_set_client_target(hwc2_device_t* device, hwc2_display_t display,
    buffer_handle_t target, int32_t acquireFence, int32_t dataspace, hwc_region_t damage)
{

	Layer_t *layer;
	Display_t *dp;
	unusedpara(device);

	if(!findDisplay(display)){
		ALOGE("ERROR %s:bad display", __FUNCTION__);
		return HWC2_ERROR_BAD_DISPLAY;
	}
	dp = (Display_t*)display;

	layer = dp->clientTargetLayer;
	layer->compositionType = HWC2_COMPOSITION_CLIENT_TARGET;
	layer->acquireFence = acquireFence;
	layer->releaseFence = -1;
	layer->buffer = target;
	layer->crop.left = 0;
	layer->crop.right = dp->displayConfigList[dp->activeConfigId]->width;
	layer->crop.top = 0;
	layer->crop.bottom = dp->displayConfigList[dp->activeConfigId]->height;
	layer->damageRegion = damage;
	layer->dataspace = dataspace;
	layer->frame.left = 0;
	layer->frame.right = dp->displayConfigList[dp->activeConfigId]->width;
	layer->frame.top = 0;
	layer->frame.bottom = dp->displayConfigList[dp->activeConfigId]->height;
	layer->transform = 0;
	layer->typeChange = false;
	layer->zorder = CLIENT_TARGET_ZORDER;
	layer->blendMode = 2;
	layer->planeAlpha = 1.0;

	ALOGV("%s : create FbLayer=%p, acquirefence=%d, dataspace=%d, buffer=%p, target=%p",
			__FUNCTION__, layer, acquireFence, dataspace, layer->buffer, target);
	return HWC2_ERROR_NONE;
}

int32_t hwc_set_color_mode(hwc2_device_t* device, hwc2_display_t display, int32_t mode)
{
    //Fix Me:cannot find struct android_color_mode_t in <system/graphics.h>
	unusedpara(device);
	unusedpara(display);
	unusedpara(mode);

    return HWC2_ERROR_NONE;
}

int32_t hwc_set_color_transform(hwc2_device_t* device, hwc2_display_t display,
    const float* matrix, int32_t hint)
{
    Display_t* dp = (Display_t*)display;
	unusedpara(matrix);
	unusedpara(device);

    if(!findDisplay(display)){
        ALOGE("ERROR %s:bad display", __FUNCTION__);
        return HWC2_ERROR_BAD_DISPLAY;
    }
    dp->colorTransformHint = hint;
    return HWC2_ERROR_NONE;

}

int32_t hwc_set_output_buffer(hwc2_device_t* device, hwc2_display_t display,
    buffer_handle_t buffer, int32_t releaseFence)
{
	unusedpara(device);
	unusedpara(display);
	unusedpara(buffer);
	unusedpara(releaseFence);

    ALOGE("%s : we do not support virtual display yet,should not be called", __FUNCTION__);
    return HWC2_ERROR_UNSUPPORTED;
}

int32_t hwc_set_power_mode(hwc2_device_t* device, hwc2_display_t display, int32_t mode)
{
    Display_t* dp = (Display_t*) display;
	unusedpara(device);

    if(!findDisplay(display)){
        ALOGE("%s : bad display %p", __FUNCTION__, (void*)display);
        return HWC2_ERROR_BAD_DISPLAY;
    }

    if(!dp->displayOpration->setPowerMode(dp, mode)){
        return HWC2_ERROR_NONE;
    }
    return HWC2_ERROR_UNSUPPORTED;
}

int32_t hwc_set_vsync_enabled(hwc2_device_t* device, hwc2_display_t display, int32_t enabled)
{
	Display_t *dp = (Display_t*) display;
	unusedpara(device);

    if(!findDisplay(display)){
        ALOGE("%s : bad display %p", __FUNCTION__, (void*)display);
        return HWC2_ERROR_BAD_DISPLAY;
    }
    if(!dp->displayOpration->setVsyncEnabled((Display_t *)display, enabled)){
        return HWC2_ERROR_NONE;
    }
    return HWC2_ERROR_BAD_PARAMETER;
}

int32_t hwc_validate_display(hwc2_device_t* device, hwc2_display_t display,
    uint32_t* outNumTypes, uint32_t* outNumRequests)
{
	ATRACE_CALL();

	Display_t *dp = (Display_t*) display;
	struct listnode* list;
	struct listnode *node;
	Layer_t *ly;
	uint32_t numTypes = 0, numRequests = 0;
	hwc2_error_t ret = HWC2_ERROR_NO_RESOURCES;
	unusedpara(device);

	if(!findDisplay(display)){
		ALOGE("%s : bad display %p", __FUNCTION__, (void*)display);
		return HWC2_ERROR_BAD_DISPLAY;
	}

	list = &(dp->layerSortedByZorder);
	*outNumRequests = 0;
	*outNumRequests = 0;

	ALOGV("ID=%d, Before assign, size=%d", dp->displayId, dp->nubmerLayer);
	pthread_mutex_lock(&dp->listMutex);

	dp->displayOpration->AssignLayer(dp);

	list = &(dp->layerSortedByZorder);
	list_for_each(node, list) {
		ly = node_to_item(node, Layer, node);
		if(ly != NULL && ly->typeChange
				&& ly->compositionType != HWC2_COMPOSITION_CLIENT_TARGET) {
			numTypes++;
		}
		if (ly->clearClientTarget)
			numRequests++;
	}
	*outNumRequests = numRequests;
	*outNumTypes = numTypes;
	if (numTypes || numRequests) {
		ret = HWC2_ERROR_HAS_CHANGES;
	}else{
		ret = HWC2_ERROR_NONE;
	}
	ALOGV("%s: %d layer changes and numRequests %d.\n",__FUNCTION__, numTypes, numRequests);

	pthread_mutex_unlock(&dp->listMutex);

	return ret;
}

int32_t hwc_set_cursor_position(hwc2_device_t* device, hwc2_display_t display,
    hwc2_layer_t layer, int32_t x, int32_t y)
{
	unusedpara(device);
	unusedpara(display);
	unusedpara(layer);
	unusedpara(x);
	unusedpara(y);

    ALOGE("%s : (Warning) we do not support cursor layer alone.", __FUNCTION__);
    return HWC2_ERROR_NONE;
}

int32_t hwc_set_layer_buffer(hwc2_device_t* device, hwc2_display_t display,
    hwc2_layer_t layer, buffer_handle_t buffer, int32_t acquireFence){

	Layer_t *ly = (Layer_t *)layer;
	Display_t* dp = (Display_t *)display;
	unusedpara(device);

	ly->buffer = buffer;
	ly->acquireFence = acquireFence;
	memCtrlCheckResv(ly, true);
	return HWC2_ERROR_NONE;
}

int32_t hwc_set_layer_surface_damage(hwc2_device_t* device, hwc2_display_t display,
    hwc2_layer_t layer, hwc_region_t damage)
{
    Layer_t *ly = (Layer_t *)layer;
	unusedpara(device);
    if(!findDisplay(display)){
        ALOGE("%s : bad display %p", __FUNCTION__, (void*)display);
        return HWC2_ERROR_BAD_DISPLAY;
    }

    ly->damageRegion = damage;
    return HWC2_ERROR_NONE;
}

int32_t hwc_set_layer_blend_mode(hwc2_device_t* device, hwc2_display_t display,
    hwc2_layer_t layer, int32_t mode)
{
    Layer_t* ly = (Layer_t *) layer;
	unusedpara(device);
    if(!findDisplay(display)){
        ALOGE("%s : bad display %p", __FUNCTION__, (void*)display);
        return HWC2_ERROR_BAD_DISPLAY;
    }

    ly->blendMode = mode;
    return HWC2_ERROR_NONE;
}

int32_t hwc_set_layer_color(hwc2_display_t* device, hwc2_display_t display, hwc2_layer_t layer,
    hwc_color_t color)
{
	Layer_t *ly = (Layer_t *)layer;
	unusedpara(device);

	if(!findDisplay(display)){
        ALOGE("%s : bad display %p", __FUNCTION__, (void*)display);
        return HWC2_ERROR_BAD_DISPLAY;
    }

    ly->color = color;
    return HWC2_ERROR_NONE;
}
/* JetCui:
  * before hwc prepare, is surfaceFlinger want type.is client,must client
  * after hwc prepare, is surfaceFlinger accept the chang(type).
  * if SurfaceFlinger want device, but HWC can chang it to client.
  */
int32_t hwc_set_layer_composition_type(hwc2_device_t* device, hwc2_display_t display,
    hwc2_layer_t layer, int32_t type)
{
    Layer_t *ly = (Layer_t *)layer;
	Display_t *dp = (Display_t *)display;
	unusedpara(device);

	if(!findDisplay(display)){
        ALOGE("%s : bad display %p", __FUNCTION__, (void*)display);
        return HWC2_ERROR_BAD_DISPLAY;
    }
    ly->compositionType = type;
    return HWC2_ERROR_NONE;
}

int32_t hwc_set_layer_dataspace(hwc2_device_t* device, hwc2_display_t display,
    hwc2_layer_t layer, int32_t dataspace)
{
	Layer_t *ly = (Layer_t *) layer;
	unusedpara(device);

	if(!findDisplay(display)){
		ALOGE("%s : bad display %p", __FUNCTION__, (void*)display);
		return HWC2_ERROR_BAD_DISPLAY;
	}

	ly->dataspace = dataspace;
	return HWC2_ERROR_NONE;
}

int32_t hwc_set_layer_display_frame(hwc2_device_t* device, hwc2_display_t display,
    hwc2_layer_t layer, hwc_rect_t frame)
{
    Layer_t *ly = (Layer_t *)layer;
	Display_t *dp = (Display_t *)display;
	unusedpara(device);
    if(!findDisplay(display)){
        ALOGE("%s : bad display %p", __FUNCTION__, (void*)display);
        return HWC2_ERROR_BAD_DISPLAY;
    }

    ly->frame = frame;
    return HWC2_ERROR_NONE;
}

int32_t hwc_set_layer_plane_alpha(hwc2_device_t* device, hwc2_display_t display,
    hwc2_layer_t layer, float alpha)
{
    Layer_t *ly = (Layer_t *)layer;
	unusedpara(device);

	if(!findDisplay(display)){
        ALOGE("%s : bad display %p", __FUNCTION__, (void*)display);
        return HWC2_ERROR_BAD_DISPLAY;
    }

    ly->planeAlpha = alpha;
    return HWC2_ERROR_NONE;
}

int32_t hwc_set_layer_sideband_stream(hwc2_device_t* device, hwc2_display_t display,
    hwc2_layer_t layer, const native_handle_t* stream)
{
    Layer_t *ly = (Layer_t *) layer;
	unusedpara(device);
    if(!findDisplay(display)){
        ALOGE("%s : bad display %p", __FUNCTION__, (void*)display);
        return HWC2_ERROR_BAD_DISPLAY;
    }

    ly->stream = stream;
    return HWC2_ERROR_NONE;
}

int32_t hwc_set_layer_source_crop(hwc2_device_t* device, hwc2_display_t display,
    hwc2_layer_t layer, hwc_frect_t crop)
{
    Layer_t *ly = (Layer_t *) layer;
	unusedpara(device);

	if(!findDisplay(display)){
        ALOGE("%s : bad display %p", __FUNCTION__, (void*)display);
        return HWC2_ERROR_BAD_DISPLAY;
    }
    ly->crop = crop;	
    return HWC2_ERROR_NONE;
}

int32_t hwc_set_layer_transform(hwc2_device_t* device, hwc2_display_t display,
    hwc2_layer_t layer, int32_t transform)
{
    Layer_t *ly = (Layer_t *) layer;
	Display_t *dp = (Display_t *) display;
	unusedpara(device);
    if(!findDisplay(display)){
        ALOGE("%s : bad display %p", __FUNCTION__, dp);
        return HWC2_ERROR_BAD_DISPLAY;
    }

	ly->transform = transform;
	if (transform == 0) {
		trCachePut(ly);
		ly->trcache = NULL;
	}

    return HWC2_ERROR_NONE;
}

int32_t hwc_set_layer_visible_region(hwc2_device_t* device, hwc2_display_t display,
    hwc2_layer_t layer, hwc_region_t visible)
{
	Layer_t *ly = (Layer_t *)layer;
	Display_t *dp = (Display_t*)display;
	unusedpara(device);
    if(!findDisplay(display)){
        ALOGE("%s : bad display %p", __FUNCTION__, dp);
        return HWC2_ERROR_BAD_DISPLAY;
    }

    ly->visibleRegion = visible;

    return HWC2_ERROR_NONE;
}

int32_t hwc_set_layer_z_order(hwc2_device_t* device, hwc2_display_t display,
    hwc2_layer_t layer, uint32_t z)
{

	Layer_t *ly = (Layer_t *)layer;
    Display_t* dp = (Display_t *) display;
	unusedpara(device);
    if(!findDisplay(display)){
        ALOGE("%s : bad display %p", __FUNCTION__, (void*)display);
        return HWC2_ERROR_BAD_DISPLAY;
    }
	ly->zorder = z;
	pthread_mutex_lock(&dp->listMutex);
    dp->nubmerLayer += insertLayerByZorder(ly, &dp->layerSortedByZorder);
	pthread_mutex_unlock(&dp->listMutex);

    return HWC2_ERROR_NONE;
}

int hwc_set_hdmi_mode(int display, int mode)
{
	mesg_pair_t mesg;
	mesg.disp = display;
	mesg.cmd = 1;
	mesg.data = mode;

	return write(socketpair_fd[0], &mesg, sizeof(mesg)) == sizeof(mesg);
}

int hwc_set_3d_mode(int display, int mode)
{
	mesg_pair_t mesg;
	mesg.disp = display;
	mesg.cmd = 2;
	mesg.data = mode;

	return write(socketpair_fd[0],&mesg, sizeof(mesg)) == sizeof(mesg);
}

int hwc_setMargin(int display, int hpercent, int vpercent);
int hwc_setVideoRatio(int display, int radioType);

int hwc_set_margin(int display, int data)
{
	int hpercent, vpercent;
	hpercent = (data & 0xffff0000) >> 16;
	vpercent = data & 0xffff;

	hwc_setMargin(display, hpercent, vpercent);
	return 0;
}

int hwc_set_screenfull(int display, int enable)
{
#if 0
	Display_t **dp = mDisplay;

	for(int i = 0; i < numberDisplay; i++) {
		if (dp[i]->displayId == display)
			dp[i]->ScreenFull = enable;
	}
#endif
	display, enable;
	return 0;
}

/* hwc_set_display_command
  *  this is HIDL call for set display arg,
  *
  */
typedef int (*SUNXI_SET_DISPLY_COMMAND)(int display, int cmd1, int cmd2, int data);

int hwc_set_display_command(int display, int cmd1, int cmd2, int data)
{
	int ret = -1;
	switch (cmd1) {
	case HIDL_HDMI_MODE_CMD:
		ret = hwc_set_hdmi_mode(display, cmd2);
	break;
	case HIDL_ENHANCE_MODE_CMD:
		ret = hwc_set_enhance_mode(display, cmd2, data);
	break;
	case HIDL_SML_MODE_CMD:
		ret = hwc_set_smt_backlight(display, data);
	break;
	case HIDL_COLOR_TEMPERATURE_CMD:
		ret = hwc_set_color_temperature(display, cmd2, data);
	break;
	case HIDL_SET3D_MODE:
		ret = hwc_set_3d_mode(display, cmd2);
	break;
	case HIDL_SETMARGIN:
		ret = hwc_set_margin(display, data);
	break;
	case HIDL_SETVIDEORATIO:
		ret = hwc_set_screenfull(display, data);
	break;
	default:
		ALOGD("give us a err cmd");
	}
	return ret;
}

int hwc_setDataSpacemode(int display, int dataspace_mode)
{
	Display_t **dp = mDisplay;
	for (int i = 0; i < numberDisplay; i++) {
		if (dp[i]->displayId == display)
			dp[i]->dataspace_mode = dataspace_mode;
	}
	return 0;
}

int hwc_callback_DataSpacemode(int display, int dataspace_mode)
{
	unusedpara(display);
	unusedpara(dataspace_mode);
	return 0;
}

int hwc_setSwitchdevice(int display)
{
	unusedpara(display);
	return 0;
}

/* API for H6 */
int hwc_setHotplug(int display, int enable)
{
	Display_t **dp = mDisplay;
	for (int i = 0; i < numberDisplay; i++) {
		if (dp[i]->displayId == display)
			dp[i]->plugIn = enable;
	}

	return 0;
}

int hwc_setBlank(int display)
{
	int ret;
	ret = clearAllLayers(display);
	if (ret)
		ALOGE("Clear all layers failed!");
	return 0;
}

tv_para_t tv_mode[]=
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
	{DISP_TV_MOD_4096_2160P_24HZ,  4096,   2160, 24, 0},
	{DISP_TV_MOD_4096_2160P_25HZ,  4096,   2160, 25, 0},
	{DISP_TV_MOD_4096_2160P_30HZ,  4096,   2160, 30, 0},
	{DISP_TV_MOD_3840_2160P_60HZ,  3840,   2160, 60, 0},
	{DISP_TV_MOD_4096_2160P_60HZ,  4096,   2160, 60, 0},
	{DISP_TV_MOD_3840_2160P_50HZ,  3840,   2160, 50, 0},
	{DISP_TV_MOD_4096_2160P_50HZ,  4096,   2160, 50, 0},
	{DISP_TV_MOD_1080P_24HZ_3D_FP, 1920,   1080, 24, 0},
	{DISP_TV_MOD_720P_50HZ_3D_FP,  1280,   720, 50, 0},
	{DISP_TV_MOD_720P_60HZ_3D_FP,  1280,   720, 60, 0},
};
int hwc_setOutputMode(int display, int type, int mode)
{
	Display_t **dp = mDisplay;
	DisplayConfigPrivate_t *hwconfig;
	DisplayConfig_t *dispconfig;
	int i, num;
	int ScreenWidth, ScreenHeight;

	if (type == DISP_OUTPUT_TYPE_HDMI) {
		num = sizeof(tv_mode)/sizeof(tv_mode[0]);
		for (i = 0; i < num; i++) {
			if (tv_mode[i].mode == mode) {
				ScreenWidth = tv_mode[i].width;
				ScreenHeight = tv_mode[i].height;
			}
		}
	}

	for (int i = 0; i < numberDisplay; i++) {
		if (dp[i]->displayId == display) {
			dp[i]->VarDisplayWidth = ScreenWidth;
			dp[i]->VarDisplayHeight = ScreenHeight;
		}
	}
#if 0
	for (int i = 0; i < numberDisplay; i++) {
		if (dp[i]->displayId == display) {
			dispconfig = dp[i]->displayConfigList[dp[i]->activeConfigId];
			hwconfig = (DisplayConfigPrivate_t*)dispconfig->data;
			hwconfig->screenDisplay.left = 0;
			hwconfig->screenDisplay.right = ScreenWidth;
			hwconfig->screenDisplay.top = 0;
			hwconfig->screenDisplay.bottom = ScreenHeight;
		}
	}
#endif
	return 0;
}

int hwc_setMargin(int display, int hpercent, int vpercent)
{
	Display_t **dp = mDisplay;
	for (int i = 0; i < numberDisplay; i++) {
		if (dp[i]->displayId == display) {
			dp[i]->hpercent = hpercent;
			dp[i]->vpercent = vpercent;
		}
	}
	return 0;
}

int hwc_setVideoRatio(int display, int radioType)
{
	Display_t **dp = mDisplay;

	switch(radioType)
	{
		case SCREEN_AUTO:
		case SCREEN_FULL:
			for(int i = 0; i < numberDisplay; i++)
			{
				if (dp[i]->displayId == display)
					dp[i]->screenRadio = radioType;
			}
			break;
		default:
			break;
	}
	return 0;
}

/* hwc_device_getFunction(..., descriptor)
 * Returns a function pointer which implements the requested description
 *
 * Parameters:
 *  descriptor - the function to return
 * Returns either a function pointer implementing the requested descriptor
 *  or NULL if the described function is not supported by this device.
 */
template <typename PFN, typename T>
static hwc2_function_pointer_t asFP(T function){
    return reinterpret_cast<hwc2_function_pointer_t>(function);
}

hwc2_function_pointer_t hwc_device_getFunction(struct hwc2_device* device,
    int32_t /*hwc2_function_descriptor_t*/ descriptor)
{
    unusedpara(device);
    switch(descriptor){
    case HWC2_FUNCTION_ACCEPT_DISPLAY_CHANGES:
        return asFP<HWC2_PFN_ACCEPT_DISPLAY_CHANGES>(
            hwc_accept_display_changes);
    case HWC2_FUNCTION_CREATE_LAYER:
        return asFP<HWC2_PFN_CREATE_LAYER>(
            hwc_create_layer);
    case HWC2_FUNCTION_CREATE_VIRTUAL_DISPLAY:
        return asFP<HWC2_PFN_CREATE_VIRTUAL_DISPLAY>(
            hwc_create_virtual_display);
    case HWC2_FUNCTION_DESTROY_LAYER:
        return asFP<HWC2_PFN_DESTROY_LAYER>(
            hwc_destroy_layer);
    case HWC2_FUNCTION_DESTROY_VIRTUAL_DISPLAY:
        return asFP<HWC2_PFN_DESTROY_VIRTUAL_DISPLAY>(
            hwc_destroy_virtual_display);
    case HWC2_FUNCTION_DUMP:
        return asFP<HWC2_PFN_DUMP>(hwc_dump);
    case HWC2_FUNCTION_GET_ACTIVE_CONFIG:
        return asFP<HWC2_PFN_GET_ACTIVE_CONFIG>(
            hwc_get_active_config);
    case HWC2_FUNCTION_GET_CHANGED_COMPOSITION_TYPES:
        return asFP<HWC2_PFN_GET_CHANGED_COMPOSITION_TYPES>(
            hwc_get_changed_composition_types);
    case HWC2_FUNCTION_GET_CLIENT_TARGET_SUPPORT:
        return asFP<HWC2_PFN_GET_CLIENT_TARGET_SUPPORT>(
            hwc_get_client_target_support);
    case HWC2_FUNCTION_GET_COLOR_MODES:
        return asFP<HWC2_PFN_GET_COLOR_MODES>(
            hwc_get_color_modes);
    case HWC2_FUNCTION_GET_DISPLAY_ATTRIBUTE:
        return asFP<HWC2_PFN_GET_DISPLAY_ATTRIBUTE>(
            hwc_get_display_attribute);
    case HWC2_FUNCTION_GET_DISPLAY_CONFIGS:
        return asFP<HWC2_PFN_GET_DISPLAY_CONFIGS>(
            hwc_get_display_configs);
    case HWC2_FUNCTION_GET_DISPLAY_NAME:
        return asFP<HWC2_PFN_GET_DISPLAY_NAME>(
            hwc_get_display_name);
    case HWC2_FUNCTION_GET_DISPLAY_REQUESTS:
        return asFP<HWC2_PFN_GET_DISPLAY_REQUESTS>(
            hwc_get_display_requests);
    case HWC2_FUNCTION_GET_DISPLAY_TYPE:
        return asFP<HWC2_PFN_GET_DISPLAY_TYPE>(
            hwc_get_display_type);
    case HWC2_FUNCTION_GET_DOZE_SUPPORT:
        return asFP<HWC2_PFN_GET_DOZE_SUPPORT>(
            hwc_get_doze_support);
    case HWC2_FUNCTION_GET_HDR_CAPABILITIES:
        return asFP<HWC2_PFN_GET_HDR_CAPABILITIES>(
            hwc_get_hdr_capabilities);
    case HWC2_FUNCTION_GET_MAX_VIRTUAL_DISPLAY_COUNT:
        return asFP<HWC2_PFN_GET_MAX_VIRTUAL_DISPLAY_COUNT>(
            hwc_get_max_virtual_display_count);
    case HWC2_FUNCTION_GET_RELEASE_FENCES:
        return asFP<HWC2_PFN_GET_RELEASE_FENCES>(
            hwc_get_release_fences);
    case HWC2_FUNCTION_PRESENT_DISPLAY:
        return asFP<HWC2_PFN_PRESENT_DISPLAY>(
            hwc_present_display);
    case HWC2_FUNCTION_REGISTER_CALLBACK:
        return asFP<HWC2_PFN_REGISTER_CALLBACK>(
            hwc_register_callback);
    case HWC2_FUNCTION_SET_ACTIVE_CONFIG:
        return asFP<HWC2_PFN_SET_ACTIVE_CONFIG>(
            hwc_set_active_config);
    case HWC2_FUNCTION_SET_CLIENT_TARGET:
        return asFP<HWC2_PFN_SET_CLIENT_TARGET>(
            hwc_set_client_target);
    case HWC2_FUNCTION_SET_COLOR_MODE:
        return asFP<HWC2_PFN_SET_COLOR_MODE>(
            hwc_set_color_mode);
    case HWC2_FUNCTION_SET_COLOR_TRANSFORM:
        return asFP<HWC2_PFN_SET_COLOR_TRANSFORM>(
            hwc_set_color_transform);
    case HWC2_FUNCTION_SET_CURSOR_POSITION:
        return asFP<HWC2_PFN_SET_CURSOR_POSITION>(
            hwc_set_cursor_position);
    case HWC2_FUNCTION_SET_LAYER_BLEND_MODE:
        return asFP<HWC2_PFN_SET_LAYER_BLEND_MODE>(
            hwc_set_layer_blend_mode);
    case HWC2_FUNCTION_SET_LAYER_BUFFER:
        return asFP<HWC2_PFN_SET_LAYER_BUFFER>(
            hwc_set_layer_buffer);
    case HWC2_FUNCTION_SET_LAYER_COLOR:
        return asFP<HWC2_PFN_SET_LAYER_COLOR>(
            hwc_set_layer_color);
    case HWC2_FUNCTION_SET_LAYER_COMPOSITION_TYPE:
        return asFP<HWC2_PFN_SET_LAYER_COMPOSITION_TYPE>(
            hwc_set_layer_composition_type);
    case HWC2_FUNCTION_SET_LAYER_DATASPACE:
        return asFP<HWC2_PFN_SET_LAYER_DATASPACE>(
            hwc_set_layer_dataspace);
    case HWC2_FUNCTION_SET_LAYER_DISPLAY_FRAME:
        return asFP<HWC2_PFN_SET_LAYER_DISPLAY_FRAME>(
            hwc_set_layer_display_frame);
    case HWC2_FUNCTION_SET_LAYER_PLANE_ALPHA:
        return asFP<HWC2_PFN_SET_LAYER_PLANE_ALPHA>(
            hwc_set_layer_plane_alpha);
    case HWC2_FUNCTION_SET_LAYER_SIDEBAND_STREAM:
        return asFP<HWC2_PFN_SET_LAYER_SIDEBAND_STREAM>(
            hwc_set_layer_sideband_stream);
    case HWC2_FUNCTION_SET_LAYER_SOURCE_CROP:
        return asFP<HWC2_PFN_SET_LAYER_SOURCE_CROP>(
            hwc_set_layer_source_crop);
    case HWC2_FUNCTION_SET_LAYER_SURFACE_DAMAGE:
        return asFP<HWC2_PFN_SET_LAYER_SURFACE_DAMAGE>(
            hwc_set_layer_surface_damage);
    case HWC2_FUNCTION_SET_LAYER_TRANSFORM:
        return asFP<HWC2_PFN_SET_LAYER_TRANSFORM>(
            hwc_set_layer_transform);
    case HWC2_FUNCTION_SET_LAYER_VISIBLE_REGION:
        return asFP<HWC2_PFN_SET_LAYER_VISIBLE_REGION>(
            hwc_set_layer_visible_region);
    case HWC2_FUNCTION_SET_LAYER_Z_ORDER:
        return asFP<HWC2_PFN_SET_LAYER_Z_ORDER>(
            hwc_set_layer_z_order);
    case HWC2_FUNCTION_SET_OUTPUT_BUFFER:
        return asFP<HWC2_PFN_SET_OUTPUT_BUFFER>(
            hwc_set_output_buffer);
    case HWC2_FUNCTION_SET_POWER_MODE:
        return asFP<HWC2_PFN_SET_POWER_MODE>(
            hwc_set_power_mode);
    case HWC2_FUNCTION_SET_VSYNC_ENABLED:
        return asFP<HWC2_PFN_SET_VSYNC_ENABLED>(
            hwc_set_vsync_enabled);
    case HWC2_FUNCTION_VALIDATE_DISPLAY:
        return asFP<HWC2_PFN_VALIDATE_DISPLAY>(
            hwc_validate_display);
	case HWC2_FUNCTION_SUNXI_SET_DISPLY:
		return asFP<SUNXI_SET_DISPLY_COMMAND>(
            hwc_set_display_command);
    }
    return NULL;
}

int hwc_device_close(struct hw_device_t* device)
{
	unusedpara(device);
	/* TODO */
    return 0;
}

void deviceManger(void *data, hwc2_display_t display, hwc2_connection_t connection)
{
	Display_t *dp = (Display_t*)display;
	DisplayOpr_t *opt;
	int ret = 1;
	unusedpara(data);

	if(!findDisplay(display)){
		ALOGE("%s : bad display %p", __FUNCTION__, dp);
		return;
	}
	opt = dp->displayOpration;
	switch (connection) {
		/* think about surfaceFlinger switch it ,but not hotplug */
		case HWC2_CONNECTION_CONNECTED:
			ret = opt->init(dp);
			break;
		case HWC2_CONNECTION_DISCONNECTED:
			ret = opt->deInit(dp);
			break;
		default:
			ALOGE("give us an error connection[%d]",connection);
	}
	memCtrlUpdateMaxFb();

}

static int hwc_device_open(const struct hw_module_t* module, const char* id,
    struct hw_device_t** device)
{
    hwc2_device_t* hwcDevice;
    hw_device_t* hwDevice;
    int ret = 0;

    if(strcmp(id, HWC_HARDWARE_COMPOSER)){
        return -EINVAL;
    }
    hwcDevice = (hwc2_device_t*)malloc(sizeof(hwc2_device_t));
    if(!hwcDevice){
        ALOGE("%s: Failed to allocate memory", __func__);
        return -ENOMEM;
    }
    memset(hwcDevice, 0, sizeof(hwc2_device_t));
    hwDevice = (hw_device_t*)hwcDevice;

    hwcDevice->common.tag = HARDWARE_DEVICE_TAG;
    hwcDevice->common.version = HWC_DEVICE_API_VERSION_2_0;
    hwcDevice->common.module = const_cast<hw_module_t*>(module);
    hwcDevice->common.close = hwc_device_close;
    hwcDevice->getCapabilities = hwc_device_getCapabilities;
    hwcDevice->getFunction = hwc_device_getFunction;
    *device = hwDevice;
	numberDisplay = displayDeviceInit(&mDisplay);
	if (numberDisplay > 2) {
		ALOGD("Display support visual display");
	}
	if (numberDisplay < 0) {
		ALOGE("initial the hwc err...");
		return -ENODEV;
	}
	ret = socketpair(AF_LOCAL, SOCK_STREAM, 0, socketpair_fd);
	if (ret != 0) {
		ALOGE("socket pair err %d", ret);
	}
	layerCacheInit();
	Iondeviceinit();
	rotateDeviceInit();
	eventThreadInit(mDisplay, numberDisplay, socketpair_fd[1]);
	debugInit(numberDisplay);
	memCtrlInit(mDisplay, numberDisplay);
	registerEventCallback(0x03, HWC2_CALLBACK_HOTPLUG, 0,
		NULL, (hwc2_function_pointer_t)deviceManger);

#ifdef HOMLET_PLATFORM
	init_homlet_service();
#endif
	ALOGD("open completely successful ");
    return 0;
}

//define the module methods
static struct hw_module_methods_t hwc_module_methods = {
    .open = hwc_device_open,
};

//define the entry point of the module
struct hw_module_t HAL_MODULE_INFO_SYM = {
    .tag = HARDWARE_MODULE_TAG,
    .version_major = 1,
    .version_minor = 0,
    .id = HWC_HARDWARE_MODULE_ID,
    .name = "Allwinner Hwcomposer Module",
    .author = "Jet Cui",
    .methods = &hwc_module_methods,
};
static void __attribute__((constructor))hal_Init(void) {
	hwc_mem_debug_init();
	ALOGD("hwc init constructor");
}

static void __attribute__((destructor)) hal_exit(void){
	IondeviceDeinit();
	rotateDeviceDeInit(numberDisplay);
	eventThreadDeinit();
	displayDeviceDeInit(mDisplay);
	layerCacheDeinit();
	ALOGD("hwc deinit destructor");
}

