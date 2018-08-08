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
#include "../hwc.h"
#include <cutils/properties.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>

/*******************enhance and smt_backlight*****************/
char attr_array[][20] =
{
	{"enhance_mode"},
	{"enhance_bright"},
	{"enhance_contrast"},
	{"enhance_denoise"},
	{"enhance_detail"},
	{"enhance_edge"},
	{"enhance_saturation"},
	{"color_temperature"},

};

#define RM_STRENGTH_OFFSET (50)
char disp_attr[50] = "/sys/class/disp/disp/attr/";

enum {
	ENHANCE_MODE_ATTR = 0,
	ENHANCE_BRIGHT_ATTR,
	ENHANCE_CONTRAST_ATTR,
	ENHANCE_DENOISE_ATTR,
	ENHANCE_DETAIL_ATTR,
	ENHANCE_EDGE_ATTR,
	ENHANCE_SATURATION_ATTR,
	COLOR_TEMPERATURE_ATTR,
	ATTR_NUM,
};

char dump_src[40] = "/data/dump_layer";
extern int dispFd;

/*
  * setprop debug.hwc   on
  * setprop debug.hwc  fps  or on.fps
  * setprop debug.hwc  dump.1 or on.dump.d0z2
  * setprop debug.hwc  close.d1z1 or ....
  * setprop debug.hwc  show  or ....
  * setprop debug.hwc  off or off.dump.d0z2
  */
typedef struct debugPerDisp{
	double fPreTime;
	unsigned preFramecout;
}debugPerDisp_t;

typedef struct hwcDebugFlags{
	bool on;
	bool begin;
	bool showLyaer;
	bool showfps;
	bool dumpLayer;
	bool closeLayer;
	bool ctrlfps;
	int dumpDisplay;
	int dumpZorder;
	int closeDisplay;
	int closeZorder;
	debugPerDisp_t *debugDisp;
} hwcDebugFlags_t;

typedef struct hwcDebugmem{
	struct listnode node;
	int size;
	int data[0];
} hwcDebugmem_t;

typedef struct hwcEnhanceinfo{
	char *name;
	int value;
} hwcEnhanceinfo_t;

hwcEnhanceinfo_t *enhanceinfo;

static struct listnode memList;
static int memSize;
static pthread_mutex_t memMutex;
static bool memdebug = 0;

hwcDebugFlags_t hwdeg;

hwcDebugFlags_t *hwcDebug = &hwdeg;

static bool hwc_cmp(const char *s1, char *s2, unsigned int offset)
{
	char *cmp;
	if(offset > strlen(s2))
		return 0;
	cmp = s2 + offset;
	while (*s1 == *cmp && *s1 != 0 && *cmp != 0) {
		s1++;
		cmp++;
	}

	if (*s1 == 0)
		return 0;
	return 1;
}

static int charToZorder(char *s, int *val, int from, int to)
{
	int d = 0, i = 0;
	while((*s - '0') >= 0 && (*s - '0') <= 9){
		d = d * 10 + (*s - '0');
		s++;
		i++;
	}
	if ( to != -1){
		if (d >= from && d <= to) {
			*val = d;
		}
	}else {
		*val = d;
	}

	return i;
}

int readStringFromAttrFile(char const *fileName,
		char *values, const int len)
{
	int ret = 0;
	int fd = open(fileName, O_RDONLY);
	if(0 > fd) {
		ALOGW("open file:%s  for reading failed, errno=%d\n", fileName, errno);
		return -1;
	}
	ret = read(fd, values, len);
	close(fd);
	return ret;
}

int writeStringToAttrFile(char const *fileName,
		char const *values, const int len)
{
	int ret = 0;
	int fd = open(fileName, O_WRONLY);
	if(0 > fd) {
		ALOGW("open file:%s  for writing failed, errno=%d\n", fileName, errno);
		return -1;
	}

	if (0 <= write(fd, values, len))
		ret = 0;
	else
		ret = -1;
	close(fd);
	return ret;
}

int hwc_set_attr_info(int mode, int value)
{
	int preval = 0, ret = 0;
	hwcEnhanceinfo_t * attr_info = NULL;

	if (mode == COLOR_TEMPERATURE_ATTR) {
		value -= RM_STRENGTH_OFFSET;
	} else if (mode == ENHANCE_MODE_ATTR) {
		if (value < 0 || value > 3)
			return -1;
	} else {
		if (value < 0 || value > 10)
			return -1;
		/* enhance mode must open */
		if (enhanceinfo[ENHANCE_MODE_ATTR].value == 0) {
			ALOGD("must first open enhance mode");
			return -2;
		}
	}

	attr_info = &enhanceinfo[mode];
	ALOGD("set %s value %d", attr_info->name, value);

	if (attr_info->value != value) {
		char fileName[100];
		char values[5];
		sprintf(fileName, "%s%s", disp_attr, attr_info->name);
		sprintf(values, "%d", value);
		int ret = writeStringToAttrFile(fileName, values, strlen(values));
		if (ret) {
			ALOGE("set %s failed", attr_info->name);
		}else{
			attr_info->value = value;
		}
	}
	return ret;
}

static inline int hwc_get_attr_mode(hwcEnhanceinfo_t *attr_info)
{
	char fileName[100];

	sprintf(fileName, "%s%s", disp_attr, attr_info->name);

	char values[8] = {0};
	int len = readStringFromAttrFile(fileName, values, sizeof(values) / sizeof(values[0]));
	if (0 < len) {
		return atoi(values);
	}

	return 0;
}

/*Smart Backlight */
int hwc_set_smt_backlight(int dispId, int mode)
{
	char smbl_mode[PROPERTY_VALUE_MAX] = {0};
	int value = 0, fd = 0;
	unsigned long arg[4] = {0};
	struct disp_rect win;

	if (dispId != 0) {//HDMI not support
		return -1;
	}
	if(mode < 0 || mode >2)
		return -1;
	if (mode) {
		arg[0] = dispId;
		win.x = 0;
		win.y = 0;
		win.width = ioctl(dispFd, DISP_GET_SCN_WIDTH, arg);
		win.height = ioctl(dispFd, DISP_GET_SCN_HEIGHT, arg);
		arg[1] = (unsigned long)&win;

		if (mode == 2) {
			/* demo mode.*/
			if (win.width > win.height) {
				win.width /= 2;
			} else {
				win.height /= 2;
			}
			ioctl(dispFd, DISP_SMBL_SET_WINDOW, arg);
			ioctl(dispFd, DISP_SMBL_ENABLE, arg);
		} else {
			/* enable smbl.*/
			ioctl(dispFd, DISP_SMBL_SET_WINDOW, arg);
			ioctl(dispFd, DISP_SMBL_ENABLE, arg);
		}
			ALOGV("WIN w=%d, h=%d", win.width, win.height);
	} else {
		ioctl(dispFd, DISP_SMBL_DISABLE, arg);
	}
	return 0;
}

int hwc_set_enhance_mode(int display, int cmd2, int data)
{
	unusedpara(display);
	return hwc_set_attr_info(cmd2, data);
}

int hwc_set_color_temperature(int display, int cmd2, int data)
{
	unusedpara(display);
	unusedpara(cmd2);
	return hwc_set_attr_info(COLOR_TEMPERATURE_ATTR, data);
}

void enhanceInit(void)
{
	/* just first display support enhance function ,
	  * so alloc 1 
	  */
	enhanceinfo = (hwcEnhanceinfo_t *)hwc_malloc(sizeof(hwcEnhanceinfo_t) * ATTR_NUM);
	if (enhanceinfo == NULL)
		ALOGD("enhance info init err...");
	for (int i = 0; i < ATTR_NUM; i++) {
		enhanceinfo[i].name = attr_array[i];
		enhanceinfo[i].value = hwc_get_attr_mode(&enhanceinfo[i]);
	}
}

void updateDebugFlags(void)
{
	char property[PROPERTY_VALUE_MAX];
	char *ps_fix = property;
	char *begin = NULL;
	unsigned int offset;

	if (property_get("debug.hwc.showfps", ps_fix, NULL) >= 0) {
		if (!hwc_cmp("off", ps_fix, 0)
			|| !hwc_cmp("0", ps_fix, 0)) {
			if ((!hwc_cmp("off.fps", ps_fix, 0)
				|| !hwc_cmp("0", ps_fix, 0))
				  && hwcDebug->showfps == 1) {
				hwcDebug->showfps = 0;
				ALOGD("hwc close show fps mode");
			}
			if (!hwc_cmp("off.dump", ps_fix, 0)
				&& hwcDebug->dumpLayer == 1) {
				hwcDebug->dumpLayer = 0;
				ALOGD("hwc close dump layer mode");
			}
			if ((!hwc_cmp("off.show", ps_fix, 0) || !hwc_cmp("0", ps_fix, 0))
				&& hwcDebug->showLyaer == 1) {
				hwcDebug->showLyaer = 0;
				ALOGD("hwc close show layers mode");
			}
			if (!hwc_cmp("off.close", ps_fix, 0)
				&& hwcDebug->closeLayer == 1) {
				hwcDebug->closeLayer = 0;
				ALOGD("hwc close close layer mode");
			}
			if (!hwc_cmp("off.ctrlfps", ps_fix, 0)
				&& hwcDebug->ctrlfps == 1) {
				hwcDebug->ctrlfps = 0;
				ALOGD("hwc close ctrlfps mode");
			}
			if(hwc_cmp("off.", ps_fix, 0)
				&& hwcDebug->on == 1) {
				hwcDebug->on = 0;
				ALOGD("hwc debug close all");
			}
		}

		if (!hwc_cmp("on", ps_fix, 0)
			|| !hwc_cmp("2", ps_fix, 0)
			|| !hwc_cmp("1", ps_fix, 0)
			|| hwcDebug->on) {
			if (hwcDebug->on == 0) {
				ALOGD("####hwc open debug mode:%s####", ps_fix);
			}
			hwcDebug->on = 1;
			if(hwcDebug->showfps == 0
				&& (!hwc_cmp("on.fps", ps_fix, 0)
					|| !hwc_cmp("fps", ps_fix, 0)
					|| !hwc_cmp("1", ps_fix, 0))) {
				hwcDebug->showfps = 1;
				ALOGD("hwc open show fps mode");
			}
			if(hwcDebug->ctrlfps == 0
				&& !hwc_cmp("on.ctrlfps", ps_fix, 0)) {
				hwcDebug->ctrlfps = 1;
				ALOGD("hwc open ctrl fps mode");
			}

			if(hwcDebug->dumpLayer == 0
				&& (!hwc_cmp("on.dump", ps_fix, 0) || !hwc_cmp("dump", ps_fix, 0))) {
				hwcDebug->dumpLayer = 1;
				ALOGD("hwc open dump layer mode:%s", ps_fix);
				// on.dump.d0z4 or on.dump.d0z65536 etc...
				if (!hwc_cmp("on.dump.d", ps_fix, 0))
					begin = ps_fix + 9;
				else if (!hwc_cmp("dump.d", ps_fix, 0))
					begin = ps_fix + 6;
				else
					return;
				offset = charToZorder(begin, &hwcDebug->dumpDisplay, 0, 1);
				if (!offset) {
						hwcDebug->dumpLayer = 0;
						return;
				}
				if (hwc_cmp("z", begin+offset, 0)) {
					hwcDebug->dumpLayer = 0;
					return;
				}
				begin += offset + 1;
				if (!charToZorder(begin, &hwcDebug->dumpZorder, 0, -1)) {
						hwcDebug->dumpLayer = 0;
						return;
				}
				ALOGD("hwc open dump layer mode OK [%d][%d]",
						hwcDebug->dumpDisplay, hwcDebug->dumpZorder);
			}
			if(hwcDebug->showLyaer == 0
				&& (!hwc_cmp("show", ps_fix, 0)
					|| !hwc_cmp("on.show", ps_fix, 0)
					|| !hwc_cmp("2", ps_fix, 0))) {
				hwcDebug->showLyaer = 1;
				ALOGD("hwc open show layer mode");
			}
			if( hwcDebug->closeLayer == 0
				&& (!hwc_cmp("close", ps_fix, 0) || !hwc_cmp("on.close", ps_fix, 0))) {
				hwcDebug->closeLayer = 1;
				ALOGD("hwc open close layer mode");
				if (!hwc_cmp("on.close", ps_fix, 0))
					begin = ps_fix + 8;
				else
					begin = ps_fix + 5;
				offset = charToZorder(begin, &hwcDebug->closeDisplay, 0, 1);
				if (!offset) {
						hwcDebug->closeLayer = 0;
						return;
				}
				begin += offset + 1;
				if (!charToZorder(begin, &hwcDebug->dumpZorder, 0, -1)) {
						hwcDebug->closeLayer = 0;
				}
			}
		}
	}
}

void closeLayerZorder(Layer_t *layer, struct disp_layer_config2 *config2)
{
	if (!hwcDebug->on || !hwcDebug->closeLayer)
		return;
	if (layer->zorder != hwcDebug->closeZorder)
		return;
	config2->enable = 0;
}

void dumpLayerZorder(Layer_t *layer,unsigned int framecout)
{
	void *addr_0 = NULL;
    int size = 0;
    int fd = 0;
    int ret = -1;
	private_handle_t *handle = NULL;

	if (!hwcDebug->on || !hwcDebug->dumpLayer)
		return;
	if (layer->zorder != hwcDebug->dumpZorder)
		return;

	handle = (private_handle_t *)layer->buffer;
	if (handle == NULL) {
		ALOGD("dump null buffer %d",framecout);
		return;
	}

	sprintf(dump_src, "/data/dump_%d_%d", framecout, layer->zorder);
    fd = ::open(dump_src, O_RDWR|O_CREAT, 0644);
    if(fd < 0) {
        ALOGD("open %s %d", dump_src, fd);
        return ;
    }
    size = handle->stride * handle->height * getBitsPerPixel(layer) / 8;
    ALOGD("### Width:%d Height:%d Size:%d at frame:%d###",
            handle->stride, handle->height, size, framecout);
    addr_0 = ::mmap(NULL, size, PROT_READ|PROT_WRITE, MAP_SHARED,
                    handle->share_fd, 0);
    ret = ::write(fd, addr_0, size);
    if(ret != size) {
        ALOGD("write %s err %d", dump_src, ret);
    }
    ::munmap(addr_0,size);
    close(fd);

}

bool debugctrlfps(void)
{
	return hwcDebug->ctrlfps;
}

void showfps(Display_t *display)
{
    double fCurrentTime = 0.0;
	timeval tv = { 0, 0 };

	if (!display->plugIn)
		return;
	gettimeofday(&tv, NULL);
	fCurrentTime = tv.tv_sec + tv.tv_usec / 1.0e6;

	if (fCurrentTime - hwcDebug->debugDisp[display->displayId].fPreTime >= 1) {
        if (hwcDebug->showfps && hwcDebug->on) {
	        ALOGD(">>>Display:%d fps:: %d\n", display->displayId,
                    (int)(((display->frameCount - hwcDebug->debugDisp[display->displayId].preFramecout) * 1.0f+0.59)
                        / (fCurrentTime - hwcDebug->debugDisp[display->displayId].fPreTime)));
	    }

		if (display->frameCount - hwcDebug->debugDisp[display->displayId].preFramecout < 8) {
			if (!display->forceClient && display->frameCount > 100) {
				 callRefresh(display);
			}
			/* display->forceClient = 1; */
		}else{
			display->forceClient = 0;
		}
        hwcDebug->debugDisp[display->displayId].fPreTime = fCurrentTime;
	    hwcDebug->debugDisp[display->displayId].preFramecout = display->frameCount;
	}
}

bool showLayers(void)
{
	if (!hwcDebug->on || !hwcDebug->showLyaer)
			return false;
	return true;
}

hwcDebugmem_t* to_hwc_mem(void *mem)
{
	hwcDebugmem_t mems;
	int size = (char * )mems.data - (char *)&mems;

	return (hwcDebugmem_t*)(((char *)mem) - size);
}

void *hwc_malloc(int size)
{
	hwcDebugmem_t *mem;
	int realsize;
	realsize = sizeof(hwcDebugmem_t) + size;
	mem = (hwcDebugmem_t *)malloc(realsize);
	if (mem == NULL) {
		ALOGE("alloc mem err");
		return NULL;
	}
	memset(mem, 0, realsize);
	mem->size = size;
	if (memdebug){
		list_init(&mem->node);
		pthread_mutex_lock(&memMutex);
		list_add_tail(&memList, &mem->node);
		pthread_mutex_unlock(&memMutex);
	}

	memSize += size;
	return (void *)mem->data;
}

void hwc_free(void *mem)
{
	hwcDebugmem_t *mem2;

	mem2 = to_hwc_mem(mem);
	if (memdebug){
		pthread_mutex_lock(&memMutex);
		list_remove(&mem2->node);
		list_init(&mem2->node);
		pthread_mutex_unlock(&memMutex);
	}
	memSize -= mem2->size;
	free(mem2);
}

int hwc_mem_dump(char* outBuffer)
{
	if (outBuffer != NULL) {
		return sprintf(outBuffer, "memmalloc:%d\n", memSize);
	}else{
		ALOGD("memmalloc:%d\n", memSize);
	}
	return 0;
}

void hwc_mem_debug_init(void)
{
	pthread_mutex_init(&memMutex, 0);
	list_init(&memList);
	memSize = 0;
}

void debugInit(int num)
{
    double fCurrentTime = 0.0;
	timeval tv = { 0, 0 };
	gettimeofday(&tv, NULL);
	fCurrentTime = tv.tv_sec + tv.tv_usec / 1.0e6;

	hwcDebug->debugDisp = (debugPerDisp_t *)hwc_malloc(sizeof(debugPerDisp_t) * num);
	if (hwcDebug->debugDisp == NULL) {
		ALOGE("malloc err for debuginit");
		return ;
	}
	for(int i = 0;i < num; i++) {
		hwcDebug->debugDisp[i].fPreTime = fCurrentTime;
	}
	enhanceInit();
}

void debugDeinit()
{
	hwc_free(hwcDebug->debugDisp);
}

void debugInitDisplay(Display_t *display)
{
	double fCurrentTime = 0.0;
	timeval tv = { 0, 0 };
	gettimeofday(&tv, NULL);
	fCurrentTime = tv.tv_sec + tv.tv_usec / 1.0e6;
	hwcDebug->debugDisp[display->displayId].fPreTime = fCurrentTime;
	hwcDebug->debugDisp[display->displayId].preFramecout = 0;

}

void debugDeDisplay(Display_t *display)
{
	hwcDebug->debugDisp[display->displayId].fPreTime = 0;
	hwcDebug->debugDisp[display->displayId].preFramecout = 0;
}
