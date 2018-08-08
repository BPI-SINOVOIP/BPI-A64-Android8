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

#define ATRACE_TAG ATRACE_TAG_GRAPHICS
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
#include <sys/eventfd.h>

#include "../hwc.h"

#define UEVENT_MSG_LEN  2048

typedef struct callbackInfo {
    hwc2_callback_data_t data;
    hwc2_function_pointer_t pointer;
	int hwDisplayBit;
	int currentStatus;// for hotplug reduplicat
	int zOrder;
	struct listnode node;
}callbackInfo_t;

typedef void (*HWC2_PFN_GLOBAL_VSYNC)(hwc2_display_t display, uint64_t framenumber, int64_t timestamp);
typedef void (*HWC2_PFN_DEVICE_MANAGER)(hwc2_display_t display, bool hotplug);

typedef enum callbackType {
    CALLBACK_VSYNC = 0,
    CALLBACK_HOTPLUG,
    CALLBACK_REFRESH,
    CALLBACK_NUMBER,
} callbackType_t;

typedef struct eventThreadContext {
	pthread_mutex_t listMutex;
	struct listnode callbackHead[CALLBACK_NUMBER];
	Display_t **display;
	int numberDisplay;
	pthread_t thread_id;
	int stop;
	int epoll_fd;
	int socketpair_fd;
}eventThreadContext_t;

int hdmifd = -1;
eventThreadContext_t eventContext;
#define EPOLL_COUNT 6
static void *eventThreadLoop(void *user);

static Display_t *findDisplay(int hwid)
{
	eventThreadContext_t *context;
	int i = 0;
	Display_t *disp;

	context = &eventContext;
	for (i = 0; i < context->numberDisplay; i++) {
		disp = context->display[i];
		if (disp->displayId == hwid)
			return disp;
	}
	return NULL;
}

static void addCallbackZorder(struct listnode *head, callbackInfo_t *cb)
{
	struct listnode *node;
	callbackInfo_t *it;

	list_for_each(node, head) {
		it = node_to_item(node, callbackInfo_t, node);
		if (it->zOrder >= cb->zOrder)
			break;
	}
	list_add_tail(node, &cb->node);
}

void callRefresh(Display_t *display)
{
	struct listnode *node;
	callbackInfo_t *callback;
	HWC2_PFN_REFRESH refresh;
	eventThreadContext_t *context = &eventContext;
	int hwid = display->displayId;

	pthread_mutex_lock(&context->listMutex);

	list_for_each(node, &context->callbackHead[CALLBACK_REFRESH]) {
		callback = node_to_item(node, callbackInfo_t, node);
		if (callback->hwDisplayBit & 1<< hwid) {
			refresh = (HWC2_PFN_REFRESH)callback->pointer;
			if (refresh != NULL)
				refresh(callback->data, (hwc2_display_t)display);
		}
	}
	pthread_mutex_unlock(&context->listMutex);
}

static void
callHotplug(eventThreadContext_t *context, Display_t *display, int32_t connect)
{
	struct listnode *node;
	callbackInfo_t *callback;
	HWC2_PFN_HOTPLUG hotplug;
	HWC2_PFN_REFRESH refresh;

	pthread_mutex_lock(&context->listMutex);

	list_for_each(node, &context->callbackHead[CALLBACK_HOTPLUG]) {
		callback = node_to_item(node, callbackInfo_t, node);
		if ((callback->hwDisplayBit & 1<< display->displayId)
			 && ((callback->currentStatus ^ (connect << display->displayId))
			     & (1 << display->displayId))) {
			hotplug = (HWC2_PFN_HOTPLUG)callback->pointer;
			callback->currentStatus ^= (1 << display->displayId);
			if (hotplug != NULL) {
				ALOGD("%s:call display[%d] hotplug [%d] back zoder:%d",
					__FUNCTION__, display->displayId,connect, callback->zOrder);
				hotplug(callback->data, (hwc2_display_t)display,
					connect ? HWC2_CONNECTION_CONNECTED : HWC2_CONNECTION_DISCONNECTED);
			}
		}
	}

	pthread_mutex_unlock(&context->listMutex);
}

static void
callVsync(eventThreadContext_t *context, int32_t hwid, int64_t timestamp)
{

	struct listnode *node;
	callbackInfo_t *callback;
    HWC2_PFN_VSYNC vsync_function;
	Display_t *display;

	display = findDisplay(hwid);
	if (display ==  NULL || !display->vsyncEn) 
		return;

	pthread_mutex_lock(&context->listMutex);

	list_for_each(node, &context->callbackHead[CALLBACK_VSYNC]) {
		callback = node_to_item(node, callbackInfo_t, node);
		if (callback->hwDisplayBit & 1<< hwid) {
			vsync_function = (HWC2_PFN_VSYNC)callback->pointer;
			if (vsync_function != NULL) {
				vsync_function(callback->data, (hwc2_display_t)display, timestamp);
				ALOGV("callVsync:%lld", timestamp);
			}
		}
	}
	pthread_mutex_unlock(&context->listMutex);

}

hwc2_error_t
registerEventCallback(int bitMapDisplay, int32_t descriptor, int zOrder,
    hwc2_callback_data_t callback_data, hwc2_function_pointer_t pointer)
{
	hwc2_error_t ret = HWC2_ERROR_NONE;
	callbackInfo_t *cb;
	struct listnode *headNode, *node;
	int i = 0, bit = 0;
	Display_t *display;
    eventThreadContext_t *context = &eventContext;
	switch (descriptor) {
		case HWC2_CALLBACK_HOTPLUG:
			headNode = &context->callbackHead[CALLBACK_HOTPLUG];
			break;
		case HWC2_CALLBACK_VSYNC:
			headNode = &context->callbackHead[CALLBACK_VSYNC];
			break;
		case HWC2_CALLBACK_REFRESH:
			headNode = &context->callbackHead[CALLBACK_REFRESH];
			break;
defalut:
			ret = HWC2_ERROR_UNSUPPORTED;
			ALOGE("unsupport callbakc descriptor %d", descriptor);
			goto bad;
	}
	pthread_mutex_lock(&context->listMutex);
	list_for_each(node, headNode) {
		cb = node_to_item(node, callbackInfo_t, node);
		if(cb->zOrder == zOrder) {
			ALOGD("reuse callback:%p  zoder:%d call:%p", cb, zOrder, pointer);
			list_remove(&cb->node);
			list_init(&cb->node);
			goto deal;
		}
	}
	cb = (callbackInfo_t *)hwc_malloc(sizeof(callbackInfo_t));
	if (cb == NULL) {
		ALOGE("%s:malloc callbackInfo err.", __FUNCTION__);
		pthread_mutex_unlock(&context->listMutex);
		goto bad;
	}
	memset(cb, 0, sizeof(*cb));
	list_init(&cb->node);
	ALOGD("New call back:%d zorder:%d  %p", descriptor, zOrder, cb);

deal:
	cb->data = callback_data;
	cb->pointer = pointer;
	cb->zOrder = zOrder;
	addCallbackZorder(headNode, cb);

	for (i = 0; i < context->numberDisplay; i++) {
		if (bitMapDisplay & (1<<i)) {
			bit |= 1<<context->display[i]->displayId;
		}
		if (pointer == NULL)
			context->display[i]->vsyncEn = 0;
	}
	cb->hwDisplayBit = bit;
	cb->currentStatus = 0;
	pthread_mutex_unlock(&context->listMutex);
	ALOGD("add call back:%d zorder:%d  %p  %08x  call:%p",
		descriptor, zOrder, cb, cb->hwDisplayBit, pointer);
	if (descriptor == HWC2_CALLBACK_HOTPLUG && zOrder == 1 && pointer != NULL) {
		for (i = 0; i < context->numberDisplay; i++) {
			display = context->display[i];
			/* primary display is defualt plugin */
			if (display->plugIn || i == 0)
				callHotplug(context, display, 1);
		}
		if (context->stop == 1) {
			context->stop = 0;
			pthread_create(&context->thread_id, NULL, eventThreadLoop, context);
		}
	}

	return ret;
bad:
	return HWC2_ERROR_BAD_PARAMETER;
}

static void
vsyncUeventParse(eventThreadContext_t *context, const char *msg)
{
    while (*msg) {

        if (!strncmp(msg, "VSYNC", strlen("VSYNC"))) {
            msg += strlen("VSYNC");
            int32_t vsync_id = *(msg) - '0';
            int64_t timestamp = strtoull(msg + 2, NULL, 0);
            callVsync(context, vsync_id, timestamp);
        }
        while (*msg++);
    }
}

static void
hotplugUeventParse(eventThreadContext_t *context, const char *msg)
{
	char switch_name[32];
	Display_t *display;
	char state;
	if (hdmifd < 0)
		return;
	while (*msg) {
		int match = 0;
		if (!strncmp(msg, "DEVTYPE=", strlen("DEVTYPE="))) {
			msg += strlen("DEVTYPE=");

			int length = 0;
			while (*(msg + length) != 0 && *(msg + length) != '\n')
				length++;
			strncpy(switch_name, msg, length);
			switch_name[length] = 0;
			for (int i = 0; i < context->numberDisplay; i++) {
				display = context->display[i];

				if (strcmp(context->display[0]->displayName, "hdmi")) {
					if (!strcmp(display->displayName, switch_name))
						match = 1;
				}
			}
			if (match) {
				lseek(hdmifd, 5, SEEK_SET);
				read(hdmifd, &state, 1);
				ALOGD("Receive %s hotplug state[%d]", switch_name, state-48);
				callHotplug(context, display, state == '1'? 1 : 0);
			}
		}
		while (*msg++);
	}
}

extern enum display_3d_mode hdmi_3D_mode;

static void *eventThreadLoop(void *user)
{
	int ueventfd;
	int recvlen = 0;
	char msg[UEVENT_MSG_LEN + 2];
	char state;
	Display_t *display = NULL;
	struct epoll_event eventItems[EPOLL_COUNT];
	int eventCount;

	eventThreadContext_t *context = (eventThreadContext_t *)user;
	setpriority(PRIO_PROCESS, 0, HAL_PRIORITY_URGENT_DISPLAY);

	if (hdmifd == -1) {
		hdmifd = open("/sys/class/extcon/hdmi/state", O_RDONLY);
		if (hdmifd < 0) {
			ALOGE("open hdmi state err %d...", hdmifd);
			goto init_epoll;
		}
		lseek(hdmifd, 5, SEEK_SET);
		read(hdmifd, &state, 1);
		ALOGD("hdmi pulgin when init hwc[%d]", state-48);
		if (state == '1')
			for (int i = 0; i < context->numberDisplay; i++) {
				display = context->display[i];
				if (strcmp(context->display[0]->displayName, "hdmi")) {
					if (!strcmp(display->displayName, "hdmi"))
						callHotplug(context, display, state == '1'? 1 : 0);
				}
			}
	}

init_epoll:
	context->epoll_fd = epoll_create(EPOLL_COUNT);

	ueventfd = uevent_open_socket(64*1024, true);
	if (ueventfd < 0) {
		ALOGE("uevent_open_socket error");
		return NULL;
	}
	struct epoll_event eventItem;
	memset(& eventItem, 0, sizeof(epoll_event)); // zero out unused members of data field union
	eventItem.events = EPOLLIN;
	eventItem.data.fd = ueventfd;
	int result = epoll_ctl(context->epoll_fd, EPOLL_CTL_ADD, ueventfd, &eventItem);
	if (result != 0)
		ALOGD("creat ueventfd epoll event err %d", result);
	eventItem.events = EPOLLIN;
	eventItem.data.fd = context->socketpair_fd;
	result = epoll_ctl(context->epoll_fd, EPOLL_CTL_ADD, context->socketpair_fd, &eventItem);
	if (result != 0)
		ALOGD("creat socketpair_fd epoll event err %d fd %d", result,  context->socketpair_fd);

	while (!context->stop) {
		eventCount = epoll_wait(context->epoll_fd, eventItems, EPOLL_COUNT, -1);
		for (int i = 0; i < eventCount; i++) {
			int fd = eventItems[i].data.fd;
        	uint32_t epollEvents = eventItems[i].events;
        	if (fd == ueventfd) {
            	if (epollEvents & EPOLLIN) {
					recvlen = uevent_kernel_multicast_recv(ueventfd, msg, UEVENT_MSG_LEN);
					if (recvlen <= 0 || recvlen >= UEVENT_MSG_LEN)
						continue;

					msg[recvlen] = 0;
					msg[recvlen+1] = 0;

					vsyncUeventParse(context, msg);
					hotplugUeventParse(context, msg);
            	}
			}else if (fd == context->socketpair_fd) {
				/* update the commit thread info */
				int cout;
				mesg_pair_t cmd_esg;
				while(read(context->socketpair_fd, &cmd_esg, sizeof(cmd_esg))
					== sizeof(cmd_esg)) {
					switch(cmd_esg.cmd) {
						case 1:// switch hdmi mode
							ALOGD("chang hdmi[%d] mode[%d] from user",cmd_esg.disp, cmd_esg.data);
							if (cmd_esg.disp != 1 ||
								context->display[cmd_esg.disp]->default_mode == cmd_esg.data)
								break;
							callHotplug(context, context->display[cmd_esg.disp], 0);
							context->display[cmd_esg.disp]->default_mode = cmd_esg.data;

							callHotplug(context, context->display[cmd_esg.disp], 1);
						break;
					case 2://3D
						if (((enum display_3d_mode)cmd_esg.data != DISPLAY_3D_LEFT_RIGHT_HDMI) &&
								((enum display_3d_mode)cmd_esg.data != DISPLAY_3D_TOP_BOTTOM_HDMI))
							switchDisplay(context->display[1],
								DISP_OUTPUT_TYPE_HDMI, DISP_TV_MOD_1080P_60HZ);
						else
							switchDisplay(context->display[1],
									DISP_OUTPUT_TYPE_HDMI, DISP_TV_MOD_1080P_24HZ_3D_FP);

						hdmi_3D_mode = (enum display_3d_mode)cmd_esg.data;
						break;

						default:
							ALOGD("not a right mesg");
					}
				}
			}
		}
	}
	close(ueventfd);
	return NULL;
}

int eventThreadInit(Display_t **display, int number, int socketpair_fd)
{
	int it = 0;
	eventThreadContext_t *context= &eventContext;

	for (it = 0; it < CALLBACK_NUMBER; it++) {
		createList(&context->callbackHead[it]);
	}
	pthread_mutex_init(&context->listMutex, 0);
	context->display = display;
	context->numberDisplay = number;
	context->stop = 1;
	context->socketpair_fd = socketpair_fd;
	ALOGD("init event thread Ok read socket fd:%d", socketpair_fd);
    return 0;
}

void eventThreadDeinit(void)
{
	eventThreadContext_t *context = &eventContext;
	struct listnode *node;
	callbackInfo_t *cb;
	int i = 0;
	context->stop = 1;
	pthread_join(context->thread_id, NULL);

	for (i =0; i < CALLBACK_NUMBER; i++) {
		list_for_each(node, &context->callbackHead[i]) {
				cb = node_to_item(node, callbackInfo_t, node);
				list_remove(&cb->node);
				hwc_free(cb);
		}
		list_init(&context->callbackHead[i]);
	}

	context->display = NULL;
	context->numberDisplay = 0;
	ALOGD("destroyed event thread");
    return;
}
