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

 
/* this is for A64 P3 , 
  * and ddr ram controler do not give us way to control it ,
  * so we will use experience
  */
struct mem_speed_limit_t{
	int ddrKHz;
	int demem;
};

typedef struct memCtrlInfo{
	int globlimit;// will varible for dvfs ddr
	int globcurlimit;
	int globcurrent;
	int globReseveMem;
	int dealReseveMem;
	int cnt;
	int numdisp;
	Display_t **display;
	int maxClientTarget;
}memCtrlInfo_t;

memCtrlInfo_t globCtrl;

static struct mem_speed_limit_t mem_speed_limit[3] =
{
#if (TARGET_BOARD_PLATFORM == tulip || TARGET_BOARD_PLATFORM == venus)

		 {672000, 37324800},
		 {552000, 29030400},
		 {432000, 20736000},
#elif (TARGET_BOARD_PLATFORM == uranus)

		{672000, 49152000},
		{552000, 49152000},
		{432000, 30736000},
#elif (TARGET_BOARD_PLATFORM == t8)
		{672000, 49152000},
		{552000, 49152000},
		{432000, 30736000},
#elif (TARGET_BOARD_PLATFORM == petrel)
		{672000, 49152000},
		{552000, 49152000},
		{432000, 30736000},
#else
#error "please select a platform\n"
#endif
};

void memCtrlUpdateMaxFb(void)
{
	int i;
	DisplayConfig_t *config;
	Display_t *display;
	Layer_t *layer;
	int max = 0;

	for(i = 0; i < globCtrl.numdisp; i++) {
		display = globCtrl.display[i];
		if (!display->plugIn) {
			max = 0;
			continue;
		}
		config = display->displayConfigList[display->activeConfigId];
		if( max < config->height * config->width * 4)
			max = config->height * config->width * 4;
	}
	if (globCtrl.maxClientTarget != 0)
		globCtrl.globReseveMem -= globCtrl.maxClientTarget;
	globCtrl.maxClientTarget = max;
	globCtrl.globReseveMem += max;
}

void memCtrlCheckResv(Layer_t *layer, bool add)
{
	int memcurrntresv;
	private_handle_t *handle;
	handle = (private_handle_t *)layer->buffer;

	if(handle == NULL)
		return;
	if (!layerIsVideo(layer)) {
		layer->memresrve = 0;
		return;
	}
	if(layer->memresrve == add)
		return;
	memcurrntresv = (int)ceilf((layer->crop.right - layer->crop.left)
										* (layer->crop.bottom - layer->crop.top)
										* getBitsPerPixel(layer) / 8);
	if (layer->transform)
		memcurrntresv *= 3;
	if (add)
		globCtrl.globReseveMem += memcurrntresv;
	else
		globCtrl.globReseveMem -= memcurrntresv;
	ALOGV("memCtrlCheckResv layer:%d %s", memcurrntresv, add?"add":"sub");

	layer->memresrve = add;
}

void memCtrlDealFBLayer(Layer_t *layer, bool add)
{
	int mem =  (int)ceilf((layer->crop.right - layer->crop.left)
											* (layer->crop.bottom - layer->crop.top)
											* getBitsPerPixel(layer) / 8);
	if (add)
		globCtrl.globReseveMem += mem;
	else
		globCtrl.globReseveMem -= mem;
	ALOGV("deal fb:%d %s",mem, add?"add":"sub");
}

void memCtrlDealLayer(Layer_t *layer, bool add)
{
	int mem =  (int)ceilf((layer->crop.right - layer->crop.left)
											* (layer->crop.bottom - layer->crop.top)
											* getBitsPerPixel(layer) / 8);
	if(!layer->memresrve)
		return;
	if (layer->transform)
		mem *= 3;
	if (add)
		globCtrl.dealReseveMem += mem;
	else
		globCtrl.dealReseveMem -= mem;

}

void memCtrlDelCur(int mem)
{
	globCtrl.globcurrent -= mem;
}

bool memCtrlAddLayer(Display_t *display, Layer_t *layer, int* pAddmem)
{
	DisplayOpr_t *opt;
	int addmem = 0, add = 0, srcmem = 0;
	opt = display->displayOpration;
	if (checkSoildLayer(layer)) {
		*pAddmem = 0;
		return true;
	}
	srcmem = (int)ceilf((layer->crop.right - layer->crop.left)
							* (layer->crop.bottom - layer->crop.top)
							* getBitsPerPixel(layer) / 8);

	addmem = opt->memCtrlAddLayer(display, layer);
	add = addmem;
	if (layer->memresrve) {
		add = addmem - srcmem;
		if (add > 0)
			ALOGE("Cal the mem err %d", add);
	}
	if(layer->compositionType == HWC2_COMPOSITION_CLIENT_TARGET)
		memCtrlDealFBLayer(layer, false);
	if (add + globCtrl.globReseveMem - globCtrl.dealReseveMem + globCtrl.globcurrent > globCtrl.globcurlimit){
		ALOGV("memctrl layer:%p: %d %d %d  %d  %d  %d",layer, add, globCtrl.globlimit,
			globCtrl.globcurlimit, globCtrl.globcurrent, globCtrl.globReseveMem, globCtrl.dealReseveMem);
		return false;
	}
	globCtrl.globcurrent += addmem;
	
	*pAddmem = addmem;
	return true;
}

void memContrlComplet(Display_t *display)
{
	int max = 0;
	unusedpara(display);
	for (int i = 0; i < globCtrl.numdisp; i++){
		max +=  globCtrl.display[i]->active;
	}
	if (globCtrl.cnt < max)
		return;
	globCtrl.cnt = 0;
}

void memResetPerframe(Display_t *display)
{
	unusedpara(display);

	if (globCtrl.cnt == 0) {
		globCtrl.globcurrent = 0;
		globCtrl.dealReseveMem = 0;
	}
	globCtrl.cnt++;

}

void memCtrlLimmitSet(Display_t *display, int screen)
{
	DisplayConfig_t *config;
	Display_t *displaytmp;

	if (display->forceClient) {
		for(int i = 0; i < globCtrl.numdisp; i++) {
			displaytmp = globCtrl.display[i];
			if (!displaytmp->plugIn)
				continue;
			config = displaytmp->displayConfigList[displaytmp->activeConfigId];
			if (i == 0)
				globCtrl.globcurlimit = config->height * config->width * 4;
			else
				globCtrl.globcurlimit += config->height * config->width * 4;
		}
	}else{
		globCtrl.globcurlimit += screen;
		if (globCtrl.globcurlimit > globCtrl.globlimit)
			globCtrl.globcurlimit = globCtrl.globlimit;
	}
}

int memCtrlDump(char* outBuffer)
{
	return sprintf(outBuffer, "GL:%d GCL:%d GC:%d GR:%d DR:%d\n", globCtrl.globlimit,
			globCtrl.globcurlimit, globCtrl.globcurrent, globCtrl.globReseveMem, globCtrl.dealReseveMem);

}
void memCtrlInit(Display_t **display, int num)
{
	int ddrFreFd;
	globCtrl.globcurrent = 0;
	globCtrl.globReseveMem = 0;
	globCtrl.numdisp = num;
	globCtrl.display = display;

	ddrFreFd = open("/sys/class/devfreq/dramfreq/max_freq", O_RDONLY);
    if(ddrFreFd >= 0)
    {
        char val_ddr[10] = {0x0,};
        int ret = -1, i = 0, speed = 0;
        ret = read(ddrFreFd, &val_ddr, 6);
	    ALOGD("the ddr speed is %s",val_ddr);
        close(ddrFreFd);
        while(ret--) {
            speed *= 10;
            if ( val_ddr[i] >= '0' && val_ddr[i] <= '9') {
                speed += val_ddr[i++] - 48;
            } else {
                speed = 552000;//defalt ddr max speed
                break;
            }
        }
        i = 0;
        ret = sizeof(mem_speed_limit)/sizeof(mem_speed_limit_t);
        for (i =0; i< ret; i++) {
            if (mem_speed_limit[i].ddrKHz <= speed) {
                break;
            }
        }
        if (i == ret) {
            i--;
        }
        globCtrl.globlimit = mem_speed_limit[i].demem;
    } else {
        ALOGD("open /sys/class/devfreq/dramfreq/max_freq err.");
		globCtrl.globlimit  = mem_speed_limit[1].demem;
    }
	globCtrl.globcurlimit = globCtrl.globlimit;
}

