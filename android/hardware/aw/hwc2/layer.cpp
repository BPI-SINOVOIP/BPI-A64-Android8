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
#include <cutils/list.h>

#define LAYER_CACHE_SHRINK_NUM 16
static struct listnode cacheList;
static int cache_cout;
static pthread_mutex_t chaceMutex;

static struct listnode submitCacheList;
static int submit_cache_cout;
static pthread_mutex_t submitChaceMutex;

void layerCacheInit(void)
{
	pthread_mutex_init(&chaceMutex, 0);
	list_init(&cacheList);
	cache_cout = 0;

	pthread_mutex_init(&submitChaceMutex, 0);
	list_init(&submitCacheList);
	submit_cache_cout = 0;
}

void layerCacheDeinit(void)
{
	Layer_t *layer = NULL;
	LayerSubmit_t *submitLayer = NULL;
	struct listnode *node = NULL;

    list_for_each(node, &cacheList) {
	    layer = node_to_item(node, Layer_t, node);
		hwc_free(layer);
	}
	list_for_each(node, &submitCacheList) {
	   submitLayer = node_to_item(node, LayerSubmit_t, node);
	   hwc_free(submitLayer);
	}
	cache_cout = 0;
	submit_cache_cout = 0;
	return;
}

void createList(struct listnode *list) {
    list_init(list);
}

Layer_t* layerCacheGet(int size)
{
	Layer_t *layer = NULL;
	struct listnode *node = NULL;

	pthread_mutex_lock(&chaceMutex);
	if (cache_cout > 0) {
		cache_cout--;
		node = list_head(&cacheList);
		list_remove(node);
		list_init(node);
        layer = node_to_item(node, Layer_t, node);
	}
	pthread_mutex_unlock(&chaceMutex);
	if (layer != NULL)
		goto deal;
	layer = (Layer_t *)hwc_malloc(sizeof(Layer_t)+ size);
	if (layer == NULL){
		ALOGE("%s:malloc layer err...",__FUNCTION__);
		return NULL;
	}

deal:
	memset(layer, 0, sizeof(Layer_t)+ size);
	layer->releaseFence = -1;
	layer->preReleaseFence = -1;
	layer->acquireFence = -1;
	layer->myselfHandle = 0;
	layer->buffer = NULL;
	layer->ref = 1;
	list_init(&layer->node);

	return layer;
}

LayerSubmit_t* submitLayerCacheGet(void)
{
	LayerSubmit_t *submitLayer = NULL;
	struct listnode *node = NULL;

	pthread_mutex_lock(&submitChaceMutex);
	if (submit_cache_cout > 0) {
		submit_cache_cout--;
		node = list_head(&submitCacheList);
		list_remove(node);
		list_init(node);
        submitLayer = node_to_item(node, LayerSubmit_t, node);
	}
	pthread_mutex_unlock(&submitChaceMutex);
	if (submitLayer != NULL)
		goto deal;

	submitLayer = (LayerSubmit_t *)hwc_malloc(sizeof(LayerSubmit_t));
deal:
	list_init(&submitLayer->node);
	list_init(&submitLayer->layerNode);
	submitLayer->sync.fd = -1;
	submitLayer->sync.count = 0;
	submitLayer->currentConfig = NULL;

	return submitLayer;
}

void layerCachePut(Layer_t *layer)
{
	private_handle_t *handle;
	handle = (private_handle_t *)layer->buffer;
	/* other fd source.if nesesory,
	  * check if close surfaceFlinger's fd
	  */
	pthread_mutex_lock(&chaceMutex);
	layer->ref--;
	if (layer->ref > 0) {
		pthread_mutex_unlock(&chaceMutex);
		return;
	}
	pthread_mutex_unlock(&chaceMutex);
	if (layer->myselfHandle) {
		if (layer->buffer != NULL && handle->share_fd >= 0)
			close(handle->share_fd);
		layer->myselfHandle = 0;
		hwc_free((void*)layer->buffer);
		
	}
	if (layer->releaseFence >= 0) {
		close(layer->releaseFence);
		layer->releaseFence = -1;
	}
	if (layer->acquireFence >= 0) {
		close(layer->acquireFence);
		layer->acquireFence = -1;
	}
	if (layer->preReleaseFence >= 0) {
		close(layer->preReleaseFence);
		layer->preReleaseFence = -1;
	}

	trCachePut(layer);
	layer->buffer = NULL;
	layer->memresrve = 0;
	layer->trcache = NULL;
	list_remove(&layer->node);
	list_init(&layer->node);

	pthread_mutex_lock(&chaceMutex);
	if (cache_cout > LAYER_CACHE_SHRINK_NUM) {
        hwc_free(layer);
		pthread_mutex_unlock(&chaceMutex);
		return;
	}

	cache_cout++;
	list_add_tail(&cacheList, &layer->node);
	pthread_mutex_unlock(&chaceMutex);

}

void submitLayerCachePut(LayerSubmit_t *submitLayer)
{
	Layer_t *layer = NULL;
	struct listnode *node, *node2;

	list_for_each_safe(node, node2, &submitLayer->layerNode) {
		layer = node_to_item(node, Layer_t, node);
		list_remove(&layer->node);
		list_init(&layer->node);
		layerCachePut(layer);
	}
	if (submitLayer->sync.fd >= 0)
		close(submitLayer->sync.fd);
	submitLayer->sync.fd = -1;

	list_remove(&submitLayer->node);
	list_init(&submitLayer->node);
	list_init(&submitLayer->layerNode);

	pthread_mutex_lock(&submitChaceMutex);
	if (submit_cache_cout > LAYER_CACHE_SHRINK_NUM) {
        hwc_free(submitLayer);
		pthread_mutex_unlock(&submitChaceMutex);
		return;
	}

	submit_cache_cout++;
	list_add_tail(&submitCacheList, &submitLayer->node);
	pthread_mutex_unlock(&submitChaceMutex);

}

Layer_t* layerDup(Layer_t *layer, int priveSize)
{
	Layer_t *duplayer = layerCacheGet(priveSize);
	private_handle_t *handle = NULL, *handle2 = NULL;

	memcpy(duplayer, layer, sizeof(Layer_t)+priveSize);
	list_init(&duplayer->node);

	if (layer->acquireFence >= 0)
		duplayer->acquireFence = dup(layer->acquireFence);
	/* dup the gralloc handle */
	duplayer->myselfHandle = 0;
	if (layer->buffer != NULL) {
		duplayer->myselfHandle = 1;
		handle2 = (private_handle_t *)hwc_malloc(sizeof(private_handle_t));
		handle = (private_handle_t *)duplayer->buffer;
		*handle2= *handle;
		/* other fd source.if nesesory */
		handle2->share_fd = dup(handle->share_fd);
		duplayer->buffer = handle2;
	}
	duplayer->releaseFence = -1;
	duplayer->preReleaseFence = -1;
	duplayer->trcache = (void *)trCacheGet(layer);
	duplayer->ref = 1;
	return duplayer;
}

void clearList(struct listnode *list, bool lay)
{
    struct listnode *node;
	Layer_t *layer;
	LayerSubmit_t *submitLayer;

    while (!list_empty(list)) {
        node = list_head(list);
        list_remove(node);
		if(lay) {
			layer = node_to_item(node, Layer_t, node);
        	layerCachePut(layer);
		}else{
			submitLayer = node_to_item(node, LayerSubmit_t, node);
			submitLayerCachePut(submitLayer);
		}
    }
}

bool insertLayerByZorder(Layer_t *element, struct listnode *list)
{
    struct listnode *node;
    Layer_t *layer;
	int i = 0;
	bool addone = 1;

    if (list_empty(list)) {
        list_add_tail(list, &element->node);
        return addone;
    }
	list_for_each(node, list) {
		layer = node_to_item(node, Layer_t, node);
		if(layer == element)
			addone = 0;
	}
    list_remove(&element->node);
    list_for_each(node, list) {
        layer = node_to_item(node, Layer_t, node);
        if (layer->zorder > element->zorder){
            break;
        }else if (layer->zorder == element->zorder) {
        	/* when resident layers will reorder, not bug  */
            ALOGV("warning: there is same zoder in layer list.\n");
        }
    }
    list_add_tail(node, &element->node);
	return addone;
}

bool deletLayerByZorder(Layer_t *element, struct listnode *list)
{
	struct listnode *node;
	Layer_t *layer;

	list_for_each(node, list) {
		layer = node_to_item(node, Layer_t, node);
		if (layer == element){
			list_remove(&element->node);
			list_init(&element->node);
			return true;
		}
	}
	return false;
}

