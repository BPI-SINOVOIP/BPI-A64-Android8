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

#ifndef _HWC_H_
#define _HWC_H_
#include <fcntl.h>
#include <errno.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include <stdio.h>
#include <sys/mman.h>
#include <sys/socket.h>
#include <sys/epoll.h>

#include <hardware/hardware.h>
#include <hardware/hwcomposer2.h>
#include <cutils/log.h>
#include <system/graphics.h>
#include <cutils/list.h>
#include <utils/Condition.h>
#include <stdlib.h>
#include <sync/sync.h>
#include <sw_sync.h>
#include <linux/ion.h>
#include <utils/String8.h>
#include <utils/String16.h>
#include <cutils/list.h>
#include <hardware/hal_public.h>
#include <ion/ion.h>
#include <sync/sync.h>
#include "dev_composer.h"
#include <hardware/sunxi_display2.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <math.h>
#include <utils/Trace.h>
#include "other/sunxi_tr.h"

/************* Layer And Its operate API ****************/
#define HWC2_COMPOSITION_CLIENT_TARGET 0xFF
#define HAL_PIXEL_FORMAT_BGRX_8888 0x1ff
#define BAD_HWC_CONFIG  123123123
#define CLIENT_TARGET_ZORDER 65536
#define HAL_PIXEL_FORMAT_AW_NV12 0x101
#define HWC_ALIGN(x,a)	(((x) + (a) - 1L) & ~((a) - 1L))
#define ROTATE_ALIGN 64

using android::Mutex;
using android::Condition;

enum HIDL_set_cmd {
	HIDL_HDMI_MODE_CMD,
	HIDL_ENHANCE_MODE_CMD,
	HIDL_SML_MODE_CMD,
	HIDL_COLOR_TEMPERATURE_CMD,
	HIDL_SET3D_MODE,
	HIDL_SETMARGIN,
	HIDL_SETVIDEORATIO,
};

enum RatioType {
	SCREEN_AUTO,
	SCREEN_FULL,
};

enum DataSpace_cmd {
	DISPLAY_OUTPUT_DATASPACE_MODE_AUTO,
	DISPLAY_OUTPUT_DATASPACE_MODE_HDR,
	DISPLAY_OUTPUT_DATASPACE_MODE_SDR,
};

enum sunnxi_dueto_flags{
	HWC_LAYER = 0,
	NOCONTIG_MEM,
	SOLID_COLOR,
	TRANSFROM_RT,
	SCALE_OUT,
	SKIP_FLAGS,
	FORMAT_MISS,
	NO_V_PIPE,
	NO_U_PIPE,
	COLORT_HINT,
	CROSS_FB,
	NOT_ASSIGN,
	NO_BUFFER,
	MEM_CTRL,
	FORCE_GPU,
};
static const char *hwcPrintInfo(enum sunnxi_dueto_flags eError)
{
    switch(eError)
	{

#define AssignDUETO(x) \
	case x: \
		return #x;
	AssignDUETO(HWC_LAYER)
	AssignDUETO(NOCONTIG_MEM)
	AssignDUETO(SOLID_COLOR)
	AssignDUETO(TRANSFROM_RT)
	AssignDUETO(SCALE_OUT)
	AssignDUETO(SKIP_FLAGS)
	AssignDUETO(FORMAT_MISS)
	AssignDUETO(NO_V_PIPE)
	AssignDUETO(NO_U_PIPE)
	AssignDUETO(COLORT_HINT)
	AssignDUETO(CROSS_FB)
	AssignDUETO(NOT_ASSIGN)
	AssignDUETO(NO_BUFFER)
	AssignDUETO(MEM_CTRL)
	AssignDUETO(FORCE_GPU)
#undef AssignDUETO
	default:
		return "Unknown reason";
	}
}

typedef struct Layer{
	volatile int ref;
	int32_t compositionType;
	int32_t releaseFence;
	int32_t preReleaseFence;
	int32_t acquireFence;
	hwc_region_t damageRegion;
	buffer_handle_t buffer;
	int32_t blendMode;
	hwc_color_t color;
	int32_t dataspace;
	hwc_rect_t frame;
	float planeAlpha;
	const native_handle_t* stream;    //this value is setted when the layer is a sideband stream layer
	hwc_frect_t crop;
	int32_t transform;
	hwc_region_t visibleRegion;
	int32_t zorder;
	bool myselfHandle;
	bool typeChange;
	bool clearClientTarget;
	bool memresrve;
	void *trcache;
	enum sunnxi_dueto_flags duetoFlag;
	struct listnode node;
	int data[0];
} Layer_t;

/****************************************************************/

/********************Display ************************************/
enum CompositionState {
	INITSTATE,
	VALIDATEDISPLAY,
	ACCEPT_DISPLAY_CHANGES
};

typedef struct DisplayConfig{
	int width;
	int height;
	int vsyncPeriod;
	int dpiX;
	int dpiY;
	int data[0];
}DisplayConfig_t;

typedef struct DisplayConfigPrivate {
	hwc_rect_t screenDisplay;
	disp_tv_mode mode;
}DisplayConfigPrivate_t;

typedef struct
{
	disp_tv_mode    mode;
	int             width;
	int             height;
	int             refreshRate;
	bool support;
}tv_para_t;

typedef struct sunxiDisplay Display_t;
typedef struct DisplayOpr DisplayOpr_t;
typedef struct LayerSubmit LayerSubmit_t;

typedef struct submitThread{
	char thread_name[32];
	int priority;
	bool stop;
	pthread_t thread_id;
	struct listnode SubmitHead;
	Mutex *mutex;
	Condition *cond;
	unsigned SubmitCount;
	unsigned diplayCount;
	int32_t (*setupLayer)(Display_t* display, LayerSubmit_t*);
	int32_t (*commitToDisplay)(Display_t* display, LayerSubmit_t*);
	int32_t (*delayDeal)(Display_t* display, LayerSubmit_t*);
}submitThread_t;

typedef struct sunxiDisplay{
	int displayId;
	char *displayName;
	DisplayConfig_t **displayConfigList;
	int configNumber;
	int activeConfigId;
	int default_mode;
	bool vsyncEn;
	bool plugIn;
	bool active;
	bool forceClient;
	bool needclientTarget;
	int32_t colorTransformHint;
	unsigned frameCount;
	Layer_t *clientTargetLayer;
	uint32_t nubmerLayer;
	pthread_mutex_t listMutex;
	struct listnode layerSortedByZorder;
	DisplayOpr_t *displayOpration;
	submitThread_t *commitThread;
	int retirfence;
	int VarDisplayWidth;
	int VarDisplayHeight;
	int hpercent;
	int vpercent;
	int screenRadio;
	int dataspace_mode;
	int data[0];
}Display_t;

typedef struct DisplayOpr{

	Layer_t* (*createLayer)(Display_t* display);
	/*Try to assign layer
	*display: the display target, it contains the layerlist
	*return 1 if any layer's composition type is changed. or 0 if every layer's composition type are not changed.
	*/
	int32_t (*AssignLayer)(Display_t* display);
	/*Display this frame for the display
	*display: the display target, it contains the layerlist
	*return 1 if success
	*/
	int32_t (*presentDisplay)(Display_t* display, struct sync_info *, int*layersize);
	/*Dump the message of the Hardware.
	* It will be called when "dumpsys SurfaceFlinger"
	*/
	void (*dump)(Display_t *display, uint32_t *outSize, char *outBuffer,  uint32_t);

	/* Init the private data of this display
	* return 0 if success
	*/
	int32_t (*init)(Display_t *display);

	int32_t (*deInit)(Display_t *display);

	/* Sets the power mode of  the given display.
	* All displays must support HWC2_POWER_MODE_ON and HWC2_POWER_MODE_OFF. Whether a display
	* supports HWC2_POWER_MODE_DOZE or HWC2_POWER_MODE_DOZE_SUSPEND may be queried using
	* getDozeSupport.
	* return 0 if success
	*/
	int32_t (*setPowerMode)(Display_t *display, int32_t mode);

	/*Enables or disables the vsync signal for the given display.
	* return 0 for success setting, or not 0 when the enabled parameter was an invalid value
	*/
	int32_t (*setVsyncEnabled)(Display_t *display, int32_t enabled);
	int32_t (*setActiveConfig)(Display_t *display, DisplayConfig_t *config);
	int32_t (*memCtrlAddLayer)(Display_t *display, Layer_t*);
}DisplayOpr_t;
/*****************************************************************/

typedef struct LayerSubmit{
    struct listnode layerNode;
    struct sync_info sync;
    unsigned frameCount;
    DisplayConfig_t *currentConfig;
    struct listnode node;
}LayerSubmit_t;

typedef struct mesg_pair {
	int disp;
	int cmd;
	int data;
}mesg_pair_t;

static inline bool checkSkipLayer(Layer_t *layer)
{
	return layer->compositionType == HWC2_COMPOSITION_CLIENT;
}

static bool layerIsPremult(Layer_t *layer)
{
    return (layer->blendMode == HWC2_BLEND_MODE_PREMULTIPLIED);
}

static inline int layerIsProtected(Layer_t *layer)
{
	private_handle_t *handle = NULL;

	handle = (private_handle_t *)layer->buffer;

	if (handle == NULL)
		return false;

	return handle->usage & GRALLOC_USAGE_PROTECTED;
}

static bool checkFloatSame(float x, float y)
{
	if (x >= y)
		return x - y < 0.001 ? 1 : 0;
	else
		return y - x < 0.001 ? 1 : 0;
}

static bool layerIsTransform(Layer_t *lay)
{
    if (lay->transform > 0) {
        return true;
    }

    return false;
}

static inline bool checkSoildLayer(Layer_t *layer)
{
	if (layer->compositionType == HWC2_COMPOSITION_SOLID_COLOR)
		return 1;
	return 0;
}

static inline bool layerIsPixelBlended(Layer_t *layer)
{
    return (layer->blendMode != HWC2_BLEND_MODE_INVALID
	    || layer->blendMode != HWC2_BLEND_MODE_NONE
	    || (checkSoildLayer(layer) && layer->color.a != 255));
}

static inline bool layerIsBlended(Layer_t *layer)
{
    return (layer->blendMode != HWC2_BLEND_MODE_INVALID
	    || layer->blendMode != HWC2_BLEND_MODE_NONE
	    || (checkSoildLayer(layer) && layer->color.a != 255));
}

static inline bool checkNullBuffer(Layer_t *layer)
{
	if (layer->buffer == NULL)
		return 1;
	return 0;
}

static inline int getBitsPerPixel(Layer_t *layer)
{
	private_handle_t *handle = NULL;

	handle = (private_handle_t *)layer->buffer;
	if (handle == NULL)
		return 0;
	switch(handle->format) {
	case HAL_PIXEL_FORMAT_RGBA_8888:
	case HAL_PIXEL_FORMAT_RGBX_8888:
	case HAL_PIXEL_FORMAT_BGRA_8888:
	case HAL_PIXEL_FORMAT_BGRX_8888:
		return 32;
	case HAL_PIXEL_FORMAT_RGB_888:
		return 24;
	case HAL_PIXEL_FORMAT_RGB_565:
		return 16;
	case HAL_PIXEL_FORMAT_YV12:
	case HAL_PIXEL_FORMAT_YCrCb_420_SP:
		return 12;
	default:
		return 0;
	}
}
static inline int getPlanFormat(Layer_t *layer)
{
	private_handle_t *handle = NULL;

	handle = (private_handle_t *)layer->buffer;
	if (handle == NULL)
		return 0;
	switch(handle->format) {
	case HAL_PIXEL_FORMAT_RGBA_8888:
	case HAL_PIXEL_FORMAT_RGBX_8888:
	case HAL_PIXEL_FORMAT_BGRA_8888:
	case HAL_PIXEL_FORMAT_BGRX_8888:
		return 1;
	case HAL_PIXEL_FORMAT_RGB_888:
		return 1;
	case HAL_PIXEL_FORMAT_RGB_565:
		return 1;
	case HAL_PIXEL_FORMAT_YV12:
		return 3;
	case HAL_PIXEL_FORMAT_YCrCb_420_SP:
		return 2;
	case HAL_PIXEL_FORMAT_AW_NV12:
		return 2;
	default:
		return 0;
	}
}

#define unusedpara(x) x=x
#define toHwc2Config(disp, i) (i | (disp<<30) |(55<<10))
#define Hwc2ConfigToDisp(config)  (config>>30)
#define Hwc2ConfigTohwConfig(config) (config & (~((3<<30)|(55<<10))))

extern void layerCacheInit(void);
extern void layerCacheDeinit(void);
extern void createList(struct listnode *list);
extern bool deletLayerByZorder(Layer_t *element, struct listnode *list);

extern int sizeList(struct listnode *list);
extern bool insertLayerByZorder(Layer *element, struct listnode *list);
extern void showLayer(struct Layer *lay);
extern bool regionCrossed(hwc_rect_t *rect0, hwc_rect_t *rect1, hwc_rect_t *rectx);
extern bool checkLayerCross(Layer_t *srclayer, Layer_t *destlayer);
extern void calcLayerFactor(Layer_t *layer, float *width, float *hight);
extern char* layerType(Layer_t *layer);
extern void layerCachePut(Layer_t *layer);
extern void submitLayerCachePut(LayerSubmit_t *submitLayer);
extern int displayDeviceInit(Display_t ***display);
extern bool debugctrlfps(void);

extern Layer_t* layerDup(Layer_t *layer, int priveSize);
extern LayerSubmit_t* submitLayerCacheGet(void);
extern int submitLayerToDisplay(Display_t *disp, LayerSubmit_t *submitLayer);
extern bool checkSwWrite(Layer_t *layer);

extern bool layerIsVideo(Layer_t *layer);
extern int is_afbc_buf(buffer_handle_t handle);
extern bool checkSoildLayer(Layer_t *layer);
extern bool layerIsScale(Display_t *display, Layer_t *layer);
extern bool checkDealContiMem(Layer_t *layer);
extern bool isSameForamt(Layer_t *layer1, Layer_t *layer2);
extern Layer_t* layerCacheGet(int size);
extern submitThread_t* initSubmitThread(Display_t *disp);
extern void deinitSubmitTread(Display_t *disp);
extern int switchDisplay(Display_t *display, int type, int mode);

extern hwc2_error_t registerEventCallback(int bitMapDisplay, int32_t descriptor, int zOrder,
    hwc2_callback_data_t callback_data, hwc2_function_pointer_t pointer);
extern int eventThreadInit(Display_t **display, int number, int socketpair_fd);
extern void eventThreadDeinit(void);
extern void clearList(struct listnode *list, bool layer);
extern void deinitSubmitTread(Display_t *disp);
extern void callRefresh(Display_t *display);
extern int submitTransformLayer(Layer_t *layer);

extern int clearAllLayers(int displayId);
extern int displayDeviceDeInit(Display_t **display);
/* ION */
extern int Iondeviceinit(void);
extern int IondeviceDeinit(void);
extern int ionAllocBuffer(int size, bool mustconfig, bool is_secure);
extern unsigned int ionGetPhyAddress(int sharefd);
extern void syncLayerBuffer(Layer_t *layer);
extern unsigned int ionGetMetadataFlag(buffer_handle_t handle);

/* For debug */
extern void debugInit(int num);
extern void debugDeinit(void);
extern bool showLayers(void);
extern void showfps(Display_t *display);
extern void dumpLayerZorder(Layer_t *layer,unsigned int framecout);
extern void closeLayerZorder(Layer_t *layer, struct disp_layer_config2 *config2);
extern void updateDebugFlags(void);
extern void debugInitDisplay(Display_t *display);
extern void debugDeDisplay(Display_t *display);
/* For Enhace */
extern int hwc_set_enhance_mode(int display, int cmd2, int data);
/* For Smart Backlight */
extern int hwc_set_smt_backlight(int dispId, int mode);
/* For Color Tempreture */
extern int hwc_set_color_temperature(int display, int cmd2, int data);
/* For displayD */
extern int hwc_setDataSpacemode(int display, int dataspace_mode);
extern int hwc_setBlank(int display);
extern int hwc_setOutputMode(int display, int type, int mode);
extern int hwc_setMargin(int display, int hpercent, int vpercent);
extern int hwc_setVideoRatio(int display, int radioType);

/* memctrl */
extern void memCtrlInit(Display_t **display, int num);
extern void memContrlComplet(Display_t *display);
extern void memResetPerframe(Display_t *display);
extern void memCtrlLimmitSet(Display_t *display, int screen);
extern void memCtrlUpdateMaxFb(void);
extern void memCtrlDealLayer(Layer_t *layer, bool add);
extern void memCtrlDealFBLayer(Layer_t *layer, bool add);
extern void memCtrlResetCur(Display_t *display, int memsize);
extern int memCtrlDump(char* outBuffer);
extern void memCtrlCheckResv(Layer_t *layer, bool add);
extern void memCtrlDelCur(int mem);
bool memCtrlAddLayer(Display_t *display, Layer_t *layer, int* pAddmem);

/* rotate ==  transform */
extern bool supportTR(Display_t *display, Layer_t *layer);
extern int rotateDeviceDeInit(int num);
extern int rotateDeviceInit(void);
extern int submitTransformLayer(Display_t *display, Layer_t *layer, unsigned int syncCount);
extern void trCachePut(Layer_t *layer);
extern void* trCacheGet(Layer_t *layer);
extern void trResetErr(void);
extern int get_rotate_fence_fd(Layer_t *layer,Display_t *disp,  int fence, unsigned int syncCount);
extern void deal_rotate_fence(tr_cache_Array *cache);
/* mem debug */
extern void *hwc_malloc(int size);
extern void hwc_free(void *mem);
extern int hwc_mem_dump(char* outBuffer);
extern void hwc_mem_debug_init(void);

#endif
