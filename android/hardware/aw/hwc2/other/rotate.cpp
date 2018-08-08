/*
 * Copyright (C) Allwinner Tech All Rights Reserved
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
#include <utils/Trace.h>
	 
#include <cutils/log.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <pthread.h>
#include <sys/time.h>
#include <sys/resource.h>
#include <cutils/uevent.h>
#include <system/graphics.h>
#include <cutils/properties.h>

#include "../hwc.h"
#include "sunxi_tr.h"

/* sync with submit thread ,may need 2 (waite sync afrer sbumit),
  * may 3(wait sync befor submit)
  */

typedef struct {
	unsigned long clienId;
	int timeout;
	int trErrCnt;
}tr_per_disp_t;


static int trfd = -1;
static int sw_sync_fd = -1;
static unsigned sw_sync_count = 0;
static unsigned inc_count = 0;
static  int trCacheCnt = 0;
int trBitMap;
tr_per_disp_t *tr_disp;
static pthread_mutex_t trCacheMutex;

char thread_name[32];
int priority;
static pthread_t thread_id;
static struct listnode SubmitHead;
static Mutex *rotate_mutex;
static Condition *rotate_cond;

typedef struct rotate_info {
	struct listnode node;
	int waite_fence;
	int dst_waite_fence;
	unsigned int syncCount;
	unsigned int gsyncCount;
	Layer_t *layer2;
	Display_t *disp;
}rotate_info_t;

#define TR_CACHE_SHRINK_NUM 16
static struct listnode rcacheList;
static int rcache_cout;
static pthread_mutex_t rchaceMutex;

#ifndef USE_IOMMU
bool mustconfig = 1;
#else
bool mustconfig = 0;
#endif

rotate_info_t* trlistCacheGet(void)
{
	rotate_info_t *tr_info = NULL;
	struct listnode *node = NULL;

	pthread_mutex_lock(&rchaceMutex);
	if (rcache_cout > 0) {
		rcache_cout--;
		node = list_head(&rcacheList);
		list_remove(node);
		list_init(node);
        tr_info = node_to_item(node, rotate_info_t, node);
	}
	pthread_mutex_unlock(&rchaceMutex);
	if (tr_info != NULL)
		goto deal;
	tr_info = (rotate_info_t *)hwc_malloc(sizeof(rotate_info_t));
	if (tr_info == NULL){
		ALOGE("%s:malloc tr_info err...",__FUNCTION__);
		return NULL;
	}

deal:
	memset(tr_info, 0, sizeof(tr_info));
	tr_info->dst_waite_fence = -1;
	tr_info->waite_fence = -1;
	tr_info->layer2 = NULL;
	list_init(&tr_info->node);

	return tr_info;
}

void trlistCachePut(rotate_info_t *tr_info)
{
	if (tr_info->waite_fence >= 0) {
		close(tr_info->waite_fence);
		tr_info->waite_fence = -1;;
		
	}
	if (tr_info->dst_waite_fence >= 0) {
		close(tr_info->dst_waite_fence);
		tr_info->dst_waite_fence = -1;
	}

	list_remove(&tr_info->node);
	list_init(&tr_info->node);
	tr_info->layer2 = NULL;

	pthread_mutex_lock(&rchaceMutex);
	if (rcache_cout > TR_CACHE_SHRINK_NUM) {
        hwc_free(tr_info);
		pthread_mutex_unlock(&rchaceMutex);
		return;
	}

	rcache_cout++;
	list_add_tail(&rcacheList, &tr_info->node);
	pthread_mutex_unlock(&rchaceMutex);

}

void* rotateThreadLoop(void *list);

static inline int trFormatToHal(unsigned char tr)
{
    switch(tr) {
        case TR_FORMAT_YUV420_SP_VUVU:
			return HAL_PIXEL_FORMAT_YCrCb_420_SP;
        case TR_FORMAT_YUV420_P:
			return HAL_PIXEL_FORMAT_YV12;
		case TR_FORMAT_YUV420_SP_UVUV:
			return HAL_PIXEL_FORMAT_AW_NV12;
		case TR_FORMAT_ABGR_8888:
			return HAL_PIXEL_FORMAT_RGBA_8888;
		case TR_FORMAT_XBGR_8888:
			return HAL_PIXEL_FORMAT_RGBX_8888;
		case TR_FORMAT_ARGB_8888:
			return HAL_PIXEL_FORMAT_BGRA_8888;
		case TR_FORMAT_XRGB_8888:
			return HAL_PIXEL_FORMAT_BGRX_8888;
		case TR_FORMAT_BGR_888:
			return HAL_PIXEL_FORMAT_RGB_888;
		case TR_FORMAT_BGR_565:
			return DISP_FORMAT_RGB_565;
        default :
            return TR_FORMAT_YUV420_P;
    }

}

static inline tr_pixel_format halToTRFormat(int halFformat)
{
    switch(halFformat) {
	    case HAL_PIXEL_FORMAT_YCrCb_420_SP:
		    return TR_FORMAT_YUV420_SP_VUVU;
	    case HAL_PIXEL_FORMAT_YV12:
		    return TR_FORMAT_YUV420_P;
	    case HAL_PIXEL_FORMAT_AW_NV12:
		    return TR_FORMAT_YUV420_SP_UVUV;
	    case HAL_PIXEL_FORMAT_RGBA_8888:
	    case HAL_PIXEL_FORMAT_RGBX_8888:
	    case HAL_PIXEL_FORMAT_BGRA_8888:
	    case HAL_PIXEL_FORMAT_BGRX_8888:
		    return TR_FORMAT_ARGB_8888;
	    case HAL_PIXEL_FORMAT_RGB_888:
		    return TR_FORMAT_RGB_888;
	    case HAL_PIXEL_FORMAT_RGB_565:
		    return TR_FORMAT_RGB_565;
	    default :
		    return TR_FORMAT_YUV420_P;
    }
}

int hwc_rotate_query(unsigned long tr_handle)
{
    unsigned long arg[4] = {0};
    int ret = -1;
    arg[0] = tr_handle;
    ret = ioctl(trfd, TR_QUERY, (unsigned long)arg);
    return ret;
}

bool hwc_rotate_request(unsigned long *clinet)
{
    unsigned long arg[4] = {0};
    int ret;
    arg[0] = (unsigned long)clinet;
    ret = ioctl(trfd, TR_REQUEST, (unsigned long)&arg);
    if(ret < 0) {
        ALOGE("request ratate module err");
        return 0;
    }
    return 1;
}

bool hwc_rotate_commit(unsigned long client, tr_info *tr_info)
{
    unsigned long arg[4] = {0};
    int ret = -1;
    arg[0] = client;
    arg[1] = (unsigned long)tr_info;
    ret = ioctl(trfd, TR_COMMIT, (unsigned long)arg);
    if (ret < 0) {
        ALOGE("commit rotate err");
        return 0;
    }
    return !ret;
}

void hwc_rotate_release(unsigned long client)
{
    unsigned long arg[4] = {0};
    int ret = -1;
    arg[0] = client;
    ret = ioctl(trfd, TR_RELEASE, (unsigned long)arg);
    if (ret < 0) {
        ALOGD("release rotate err");
    }
}

bool hwc_rotate_settimeout(unsigned long client, unsigned long ms_time)
{
    unsigned long arg[4] = {0};
    int ret = -1;
    arg[0] = client;
    arg[1] = ms_time;
    ret = ioctl(trfd, TR_SET_TIMEOUT, (unsigned long)arg);
    return !ret;
}

int rotateDeviceInit(void)
{

	trfd = open("/dev/transform", O_RDWR);
	if (trfd < 0) {
		ALOGE("Failed to open transform device");
		return -1;
	}
	tr_disp = (tr_per_disp_t *)hwc_malloc(sizeof(tr_per_disp_t));
	if(tr_disp == NULL) {
		close(trfd);
		trfd = -1;
		ALOGE("Failed to alloc client for hwc");
		return -1;
	}
	sw_sync_fd = sw_sync_timeline_create();
	if (sw_sync_fd < 0) {
		close(trfd);
		trfd = -1;
		hwc_free(tr_disp);
		tr_disp = NULL;
		ALOGE("Failed to init sy_sync for hwc");
		return -1;
	}
	rotate_mutex = new Mutex();
	rotate_cond = new Condition();

	pthread_mutex_init(&rchaceMutex, 0);
	list_init(&rcacheList);
	rcache_cout = 0;
	list_init(&SubmitHead);
	pthread_create(&thread_id, NULL, rotateThreadLoop, &SubmitHead);

	pthread_mutex_init(&trCacheMutex, 0);
	return 0;
}

int rotateDeviceDeInit(int num)
{
	if(trfd < 0) {
		return 0;
	}
	while(num--) {
		if(tr_disp->clienId != 0)
			hwc_rotate_release(tr_disp->clienId);
	}
	pthread_join(thread_id, NULL);
	delete(rotate_mutex);
	delete(rotate_cond);
	close(sw_sync_fd);
	close(trfd);
	trfd = -1;
	hwc_free(tr_disp);
	tr_disp = NULL;

	return 0;
}

static inline tr_mode toTrMode(unsigned int mode)
{
    tr_mode ret = TR_ROT_0;
    switch(mode)
    {
        case HAL_TRANSFORM_FLIP_H:
            ret = TR_HFLIP;
        break;
        case HAL_TRANSFORM_FLIP_V:
            ret = TR_VFLIP;
        break;
        case HAL_TRANSFORM_ROT_90:
            ret = TR_ROT_90;
        break;
        case HAL_TRANSFORM_ROT_180:
            ret = TR_ROT_180;
        break;
        case HAL_TRANSFORM_ROT_270:
            ret = TR_ROT_270;
        break;
        case (HAL_TRANSFORM_FLIP_H | HAL_TRANSFORM_ROT_90):
            ret = TR_VFLIP_ROT_90;
        break;
        case (HAL_TRANSFORM_FLIP_V | HAL_TRANSFORM_ROT_90):
            ret = TR_HFLIP_ROT_90;
        break;
        default:
            ret = TR_ROT_0;
    }
    return ret;
}

int culateTimeout(Layer_t *layer)
{
	private_handle_t *handle;
	handle = (private_handle_t *)layer->buffer;

    unsigned int dst = handle->width * handle->height;
    if (dst > 2073600) {
        return 100;
    }
    if (dst > 1024000) {
        return 50;
    }
    return 32;
}

void trCachePut(Layer_t *layer)
{
	tr_cache_Array *cache;
	cache = (tr_cache_Array *)layer->trcache;

	if (cache == NULL)
		return;

	pthread_mutex_lock(&trCacheMutex);
	cache->ref--;
	if (cache->ref > 0) {
		pthread_mutex_unlock(&trCacheMutex);
		return ;
	}
	pthread_mutex_unlock(&trCacheMutex);

	for (int i = 0; i < NOMORL_CACHE_N; i++) {
		close(cache->array[i].share_fd);
		if(cache->array[i].releasefd >=0)
			close(cache->array[i].releasefd);
	}
	hwc_free(cache);
	layer->trcache =  NULL;
}

void* trCacheGet(Layer_t *layer)
{
	tr_cache_Array *cache;
	cache = (tr_cache_Array *)layer->trcache;
	if (cache == NULL)
		return NULL;
	cache->ref++;
	return (void*)cache;
}

tr_cache_t* acquireLastValid(Layer_t *layer)
{
	tr_cache_Array *aCache = (tr_cache_Array *)layer->trcache;
	int i = 0;
	tr_cache_t *ccache = NULL;
	unsigned int last = 0;

	for (i = 0; i < NOMORL_CACHE_N; i++) {
		if (aCache->array[i].sync_cnt > last
			&& aCache->array[i].valid) {
			ccache = &aCache->array[i];
			last = aCache->array[i].sync_cnt;
		}
	}

	return ccache;
}

tr_cache_t* dequeueTrBuffer(Layer_t *layer, rotate_info_t *rt_info)
{
	tr_cache_t *ccache = NULL;
	tr_cache_Array *aCache = (tr_cache_Array *)layer->trcache;
	int i;
	unsigned int little = rt_info->gsyncCount;

	ccache = &aCache->array[rt_info->gsyncCount%NOMORL_CACHE_N];
/*
	if (ccache->sync_cnt + NOMORL_CACHE_N >= syncCount) {
		 no think roll 828 days will roll.... 
		for (i = 0; i < NOMORL_CACHE_N; i++) {
			if (aCache->array[i].sync_cnt < little) {
				ccache = &aCache->array[i];
				little = aCache->array[i].sync_cnt;
			}
		}
	}
*/
	if (ccache->releasefd >= 0) {
		if (sync_wait((int)ccache->releasefd, 3000)) {
				ALOGE("dequeueTrBuffer waite aquire fence err %d current:%u disp:%u",
					ccache->releasefd, rt_info->gsyncCount,rt_info->disp->commitThread->diplayCount);
						/* dump fence */
		}
		close(ccache->releasefd);
		ccache->releasefd = -1;
	}

	return ccache;
}

void* dequeueTrCache(Layer_t *layer)
{
	private_handle_t *handle;
	handle = (private_handle_t *)layer->buffer;
	tr_cache_Array *aCache =  NULL, *yuCache = NULL;
	int size, i, allin;

	size = HWC_ALIGN(handle->width, ROTATE_ALIGN) * HWC_ALIGN(handle->height, ROTATE_ALIGN)
			* getBitsPerPixel(layer) / 8;
	size = HWC_ALIGN(size, 4096);
	if (layer->trcache != NULL) {
		yuCache =(tr_cache_Array *) layer->trcache;
		if (yuCache->size >= size)
			return layer->trcache;
		trCachePut(layer);
	}

	aCache = (tr_cache_Array *)hwc_malloc(sizeof(tr_cache_Array));
	if (aCache == NULL) {
		ALOGE("malloc cache array err");
		return NULL;
	}
	ALOGV("layer:%p:  %p:size:%d x %d size:%d",layer, aCache,handle->width, handle->height, size);

	memset(aCache, 0, sizeof(tr_cache_Array));

	for (i= 0; i < NOMORL_CACHE_N; i++) {
		aCache->array[i].share_fd= ionAllocBuffer(size, mustconfig, layerIsProtected(layer));
		if (aCache->array[i].share_fd < 0)
			goto err;
		aCache->array[i].sync_cnt = -NOMORL_CACHE_N;
		aCache->array[i].releasefd = -1;
	}
	aCache->ref = 1;
	aCache->size = size;

	return (void *)aCache;
err:
	for (i= 0; i < NOMORL_CACHE_N; i++) {
		if(aCache->array[i].share_fd >= 0)
			close(aCache->array[i].share_fd);
	}
	hwc_free(aCache);
	ALOGE("dequeueTrCache err for ion err");
	return NULL;
}

void trResetErr(void)
{
	if (trfd < 0)
		return;
	tr_disp->trErrCnt = 0;
}

bool trHarewareRistrict(Layer_t *layer)
{
	private_handle_t *handle;
	handle = (private_handle_t *)layer->buffer;
	int stride0;
	if (handle == NULL)
		return false;
	/* just for yuv stride*/
	if (((long long)handle->width) * handle->height > 1100 * 1950)
		return false;
	stride0 = HWC_ALIGN(handle->width, handle->aw_byte_align[0]);
	switch(handle->format) {
	case HAL_PIXEL_FORMAT_RGBA_8888:
	case HAL_PIXEL_FORMAT_RGBX_8888:
	case HAL_PIXEL_FORMAT_BGRA_8888:
	case HAL_PIXEL_FORMAT_BGRX_8888:
		stride0 = HWC_ALIGN(handle->width * 4, handle->aw_byte_align[0]);
		if (stride0 % 4)
			return false;
		break;
	case HAL_PIXEL_FORMAT_RGB_565:
		stride0 = HWC_ALIGN(handle->width * 2, handle->aw_byte_align[0]);
		if (stride0 % 2)
			return false;
		break;
	case HAL_PIXEL_FORMAT_YV12:
		//if (stride0%4)
			return false;
		break;
	case HAL_PIXEL_FORMAT_YCrCb_420_SP:
	case HAL_PIXEL_FORMAT_AW_NV12:
		//if (stride0%2)
			return false;
		break;
	case HAL_PIXEL_FORMAT_RGB_888:
		stride0 = HWC_ALIGN(handle->width * 3, handle->aw_byte_align[0]);
		/* display use info->fb.crop.x and x is pixel , 
		  * so if rotate, but the blank is the begin,
		  * but not pixel's allign,
		  */
		if (stride0%3)
			return false;
		break;
	default:
		return false;
	}
	return true;
}

bool supportTR(Display_t *display, Layer_t *layer)
{
	if (trfd < 0)
		return false;
	if (tr_disp->trErrCnt > 3) {
		ALOGV("display:%d rotate has 3 contig err", display->displayId);
		return false;
	}
	if (!trHarewareRistrict(layer))
		return false;
	layer->trcache = dequeueTrCache(layer);

	return layer->trcache != NULL;
}

bool layerToTrinfo(Layer_t *layer, tr_info *tr_inf, tr_cache_t *bCache)
{
	private_handle_t *handle;
	handle = (private_handle_t *)layer->buffer;
    int i = 0;
    int cnt = getPlanFormat(layer);

	memset(tr_inf, 0, sizeof(tr_info));
    tr_inf->src_frame.fmt = halToTRFormat(handle->format);
    unsigned int w_stride, h_stride;

        unsigned long addr = 0;
    tr_inf->mode = toTrMode(layer->transform);

	int wScale[3] = { 1,2,2};
	int hScale[3] = {1,2,2};
	int bpp[3] = { 4, 2, 1};
	addr = ionGetPhyAddress(handle->share_fd);

	if (addr == 0 && mustconfig) {
		ALOGD("%p: %d get ion addr err....",handle, handle->share_fd);
		return 0;
	}
	switch(handle->format) {
		case HAL_PIXEL_FORMAT_YV12:
			bpp[1] = 1;
			bpp[0] = 1;
			break;
		case HAL_PIXEL_FORMAT_YCrCb_420_SP:
		case HAL_PIXEL_FORMAT_AW_NV12:
			bpp[1] = 2;
			bpp[0] = 1;
			break;
		case HAL_PIXEL_FORMAT_RGB_888:
			bpp[0] = 3;
		default:
			ALOGV("RGB");
	}
    i = 0;
    while (i < cnt) {
	    tr_inf->src_frame.haddr[i] = 0;
	    tr_inf->src_frame.laddr[i] = addr;

		/* rotate is piexl cal
		  * pitch[0] = info->src_frame.pitch[0] * ycnt;
		  * pitch[1] = info->src_frame.pitch[1] * ucnt;
		  * pitch[2] = info->src_frame.pitch[2] * ucnt;
		  */
        tr_inf->src_frame.pitch[i] = HWC_ALIGN(handle->width * bpp[i] / wScale[i], handle->aw_byte_align[i]) / bpp[i];
        tr_inf->src_frame.height[i] = handle->height/hScale[i];
	addr += tr_inf->src_frame.pitch[i] * bpp[i] * tr_inf->src_frame.height[i];
        i++;
    }

    if (cnt == 3) {//YV12
	    tr_inf->src_frame.laddr[2] = tr_inf->src_frame.laddr[0] +
		    tr_inf->src_frame.pitch[0] * tr_inf->src_frame.height[0];
	    tr_inf->src_frame.laddr[1] = tr_inf->src_frame.laddr[2] +
		    tr_inf->src_frame.pitch[2] * tr_inf->src_frame.height[2];
    }

    tr_inf->src_frame.fd = -1;
    /* tr_inf->src_frame.fd = handle->share_fd;*/
    tr_inf->src_rect.x = 0;
    tr_inf->src_rect.y = 0;
    tr_inf->src_rect.w = HWC_ALIGN(handle->width * bpp[0], handle->aw_byte_align[0])/ bpp[0];
    tr_inf->src_rect.h = handle->height;

    addr = ionGetPhyAddress(bCache->share_fd);
    if (addr == 0 && mustconfig) {
	    ALOGD("get ion addr err....");
	    return 0;
    }
    tr_inf->dst_frame.fmt = halToTRFormat(handle->format);
    if (cnt != 1) {
		/* yuv only support  TR_FORMAT_YUV420_P output*/
    	tr_inf->dst_frame.fmt = TR_FORMAT_YUV420_P;
    }
    tr_inf->dst_rect.x = 0;
    tr_inf->dst_rect.y = 0;
    if (layer->transform & HAL_TRANSFORM_ROT_90) {
        w_stride =handle->height;
        h_stride = handle->width;
    } else {
        w_stride = handle->width;
        h_stride = handle->height;
    }
	w_stride = HWC_ALIGN(w_stride * bpp[0], ROTATE_ALIGN) / bpp[0];
	h_stride =  HWC_ALIGN(h_stride * bpp[0], ROTATE_ALIGN) / bpp[0];
    tr_inf->dst_frame.pitch[0] = w_stride;
    tr_inf->dst_frame.height[0] = h_stride;
    if (cnt != 1) {
        tr_inf->dst_frame.pitch[1] = w_stride/2;
        tr_inf->dst_frame.height[1] = h_stride/2;
        tr_inf->dst_frame.pitch[2] = w_stride/2;
        tr_inf->dst_frame.height[2] = h_stride/2;
    }
    tr_inf->dst_rect.w = w_stride;
    tr_inf->dst_rect.h = h_stride;

    tr_inf->dst_frame.haddr[0] = 0;
    tr_inf->dst_frame.haddr[1] = 0;
    tr_inf->dst_frame.haddr[2] = 0;
    tr_inf->dst_frame.laddr[0] = addr;

    /*YUV format we only support YV12  --> TR_FORMAT_YUV420_P*/
    /*tr_info->dst_frame.laddr[0]  --> Y*/
    /*tr_info->dst_frame.laddr[1]  --> U*/
    /*tr_info->dst_frame.laddr[2]  --> V*/

    if (cnt != 1) {
	    tr_inf->dst_frame.laddr[2] = tr_inf->dst_frame.laddr[0] +
		    tr_inf->dst_frame.pitch[0] * tr_inf->dst_frame.height[0];
	    tr_inf->dst_frame.laddr[1] = tr_inf->dst_frame.laddr[2] +
		    tr_inf->dst_frame.pitch[2] * tr_inf->dst_frame.height[2];
    }
	/* tr_inf->dst_frame.fd = bCache->share_fd; */
	tr_inf->dst_frame.fd = -1;

    return 1;
}

int trAflterDeal(Layer_t *layer, tr_cache_t *bCache, tr_info *trInfo)
{
	private_handle_t *handle;
	handle = (private_handle_t *)layer->buffer;
	close(handle->share_fd);
	handle->share_fd = dup(bCache->share_fd);
	handle->aw_byte_align[0] = ROTATE_ALIGN;
	handle->aw_byte_align[1] = ROTATE_ALIGN / 2;
	handle->aw_byte_align[2] = ROTATE_ALIGN / 2;
	if (trInfo->dst_frame.fmt == TR_FORMAT_YUV420_P)
		handle->format = HAL_PIXEL_FORMAT_YV12;
	return 0;
}

int submitTransformLayer(rotate_info_t *rt_info, unsigned int syncCount)
{
	ATRACE_CALL();
	Layer_t *layer;
	private_handle_t *handle;

	layer = rt_info->layer2;
	handle = (private_handle_t *)layer->buffer;
	tr_cache_t *bCache = NULL;
	tr_info trInfo;
	int timeout = 0, ret = -1, i = 0;
	bool last = 1;
	/* if 2 screen use the same tr layer, we must reduce this case
	*/
	if (tr_disp->clienId == 0) {
		if(!hwc_rotate_request(&tr_disp->clienId)) {
			tr_disp->trErrCnt = 99;
			return -1;
		}
	}
	/* maybe crach for aCache== NULL, but amost impossible ,so no care*/
	bCache = dequeueTrBuffer(layer, rt_info);
	if (!layerToTrinfo(layer, &trInfo, bCache)) {
		goto last;
	}
	timeout = culateTimeout(layer);
	if (tr_disp->timeout != timeout) {
		hwc_rotate_settimeout(tr_disp->clienId, timeout);
		tr_disp->timeout = timeout;
	}
	//ALOGD("before  rotate:%u %d x %d ",syncCount,handle->width, handle->height);
	if (!hwc_rotate_commit(tr_disp->clienId, &trInfo)) {
		bCache->valid = 0;
		tr_disp->trErrCnt++;
	}

	i = (timeout * 1000/16);//video 30 fps
	while (ret != 0 && i > 0) {
		ret = hwc_rotate_query(tr_disp->clienId);
		if (ret == -1) {
			break;
		}
		usleep(16);
		i--;
	}

	if (i <= 0 || ret == -1) {
		ALOGD("rotate timeout or err:%d %d ", i, ret);
		tr_disp->trErrCnt++;
		bCache->valid = 0;
		goto last;
	}
	bCache->valid = 1;
	last = 0;
	bCache->sync_cnt = syncCount;
	bCache->releasefd = dup(rt_info->dst_waite_fence);
	tr_disp->trErrCnt = 0;;
last:
	//ALOGD("after rotate:%u  ",syncCount);
	if (last)
		bCache = acquireLastValid(layer);
	if (!bCache) {
		tr_disp->trErrCnt = 4;
		/* here will screen display err */
		return -1;
	}
	trAflterDeal(layer, bCache, &trInfo);
	return 0;
}

int get_rotate_fence_fd(Layer_t *layer2,
	Display_t *disp, int releasefence, unsigned int syncCount)
{
	char name[20];
	int count;
	int fence_fd = -1;
	rotate_info_t *tr_info;

	tr_info = trlistCacheGet();
	if (tr_info == NULL)
		return -1;

	count = sprintf(name, "tr_fence_%u\n", sw_sync_count);
	name[count+1] ='0';
	tr_info->syncCount = ++sw_sync_count;

	fence_fd = sw_sync_fence_create(sw_sync_fd, name, tr_info->syncCount);
	tr_info->waite_fence = layer2->acquireFence;
	layer2->acquireFence = dup(fence_fd);
	tr_info->layer2 = layer2;
	tr_info->dst_waite_fence = dup(releasefence);
	tr_info->gsyncCount = syncCount;
	tr_info->disp = disp;

	layer2->ref++;
	if (inc_count + 2 < tr_info->syncCount)
		ALOGV("hw rotate so slowly:%u   %u",
			tr_info->syncCount, inc_count);

	rotate_mutex->lock();
	list_add_tail(&SubmitHead, &tr_info->node);
	rotate_cond->signal();
	rotate_mutex->unlock();

	return fence_fd;
}

void deal_rotate_fence(rotate_info_t *tr_info)
{
	inc_count++;
	if (inc_count != tr_info->syncCount)
		ALOGD("some wrong about rotate fence:%d %d...", inc_count, tr_info->syncCount);
	sw_sync_timeline_inc(sw_sync_fd, 1);
}

void* rotateThreadLoop(void *list)
{
	struct listnode comHead, *commitHead, *node, *node2, *node3, *rotate_list;
	rotate_info_t *tr_info;
	rotate_list = (struct listnode*)list;

	commitHead = &comHead;
	list_init(commitHead);
	ALOGD("new a rotate thread to commit the display");
	setpriority(PRIO_PROCESS, 0, HAL_PRIORITY_URGENT_DISPLAY);

	while(1) {
		rotate_mutex->lock();
		if (list_empty(rotate_list))
			rotate_cond->wait(*rotate_mutex);

		commitHead->next = rotate_list->next;
		rotate_list->next->prev = commitHead;
		commitHead->prev = rotate_list->prev;
		rotate_list->prev->next = commitHead;
		list_init(rotate_list);
		rotate_mutex->unlock();

		list_for_each_safe(node, node2, commitHead) {
			tr_info = node_to_item(node, rotate_info_t, node);
			if (tr_info->waite_fence >= 0) {
					if (sync_wait((int)tr_info->waite_fence, 3000)) {
						ALOGE("rotateThreadLoop waite aquire fence err %d", tr_info->waite_fence);
						/* dump fence */
					}
			}
			submitTransformLayer(tr_info, tr_info->gsyncCount);
			deal_rotate_fence(tr_info);
			layerCachePut(tr_info->layer2);
			trlistCachePut(tr_info);
		}
		list_init(commitHead);
	}

}

