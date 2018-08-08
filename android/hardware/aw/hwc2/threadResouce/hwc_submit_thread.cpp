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

#include "hwc.h"

int submitLayerToDisplay(Display_t *disp, LayerSubmit_t *submitLayer)
{
	submitThread_t *myThread = disp->commitThread;

	myThread->mutex->lock();

	list_add_tail(&myThread->SubmitHead, &submitLayer->node);
	myThread->cond->signal();
	myThread->mutex->unlock();
	return 0;
}

void inline submitDelayWork(Display_t *disp, LayerSubmit_t *submitLayer)
{
	submitThread_t *myThread;

	myThread = disp->commitThread;
	myThread->delayDeal(disp, submitLayer);
	submitLayerCachePut(submitLayer);
}

void* submitThreadLoop(void *display)
{
	Display_t *disp;
	submitThread_t *myThread;
	struct listnode comHead, *commitHead, *node, *node2, *node3, *node4;
	LayerSubmit_t *submitLayer = NULL, *preSubmit = NULL;
	Layer_t *layer;
	bool ctrlfps = 0;

	disp = (Display_t *)display;
	myThread = disp->commitThread;
	commitHead = &comHead;
	list_init(commitHead);
	ALOGD("new a thread to commit the display:%d", disp->displayId);
	setpriority(PRIO_PROCESS, 0, HAL_PRIORITY_URGENT_DISPLAY);

	while (!myThread->stop) {
		updateDebugFlags();
		myThread->mutex->lock();
		if (list_empty(&myThread->SubmitHead)) {
			myThread->cond->waitRelative(*myThread->mutex, 16000000);
		}

		if(!disp->active) {
			myThread->mutex->unlock();
			continue;
		}
		showfps(disp);
		ctrlfps = debugctrlfps();
		if (list_empty(&myThread->SubmitHead)) {
			myThread->mutex->unlock();
			ALOGV("16ms not fresh");
			continue;
		}
		memCtrlLimmitSet(disp, 2000000);
		/* replace the list */
		commitHead->next = myThread->SubmitHead.next;
		myThread->SubmitHead.next->prev = commitHead;
		commitHead->prev = myThread->SubmitHead.prev;
		myThread->SubmitHead.prev->next = commitHead;
		list_init(&myThread->SubmitHead);

		myThread->mutex->unlock();

		list_for_each_safe(node, node2, commitHead) {
			submitLayer = node_to_item(node, LayerSubmit_t, node);

			list_for_each_safe(node3, node4, &submitLayer->layerNode) {
				layer = node_to_item(node3, Layer_t, node);
				if (layer->acquireFence >= 0 && !ctrlfps) {
					if (sync_wait((int)layer->acquireFence, 3000)) {
						ALOGE("submit loop waite aquire fence err %d", layer->acquireFence);
						/* dump fence */
					}
				}
				/* gralloc unlock guarantee sync the buffer... */
				/*if (checkSwWrite(layer))
					syncLayerBuffer(layer);
				*/
				/* for debug */
				dumpLayerZorder(layer, submitLayer->frameCount);
			}

			/* no block so may after waite fence,  set up layer info to disp config2 info  */
			myThread->setupLayer(disp, submitLayer);

			myThread->commitToDisplay(disp, submitLayer);

			/* wait for vsync  and a vsync send 1 frame ,
			  * so we wait for the last releasefence
			  */
			list_remove(&submitLayer->node);

			if (preSubmit != NULL) {
				/*sync_wait(preSubmit->sync.fd,
					(disp->displayConfigList[disp->activeConfigId]->vsyncPeriod / 1000000 +1));
				*/
				submitDelayWork(disp, preSubmit);
			}

			myThread->diplayCount = submitLayer->frameCount;
			myThread->SubmitCount = submitLayer->sync.count;

			preSubmit = submitLayer;
			if (ctrlfps)
				usleep(20000);
		}
	}
	if (preSubmit != NULL)
		submitDelayWork(disp, preSubmit);
	return NULL;
}

submitThread_t* initSubmitThread(Display_t *disp)
{
	submitThread_t* myThread = (submitThread_t*)hwc_malloc(sizeof(submitThread_t));
	if (myThread == NULL) {
		ALOGE("malloc an err....");
		return NULL;
	}
	myThread->mutex = new Mutex();
	myThread->cond = new Condition();

	list_init(&myThread->SubmitHead);
	disp->commitThread = myThread;
	pthread_create(&myThread->thread_id, NULL, submitThreadLoop, disp);

	return myThread;
}

void deinitSubmitTread(Display_t *disp)
{
	submitThread_t* myThread = disp->commitThread;
	if(myThread== NULL)
		return;
	myThread->stop = 1;
	pthread_join(myThread->thread_id, NULL);
	delete(myThread->mutex);
	delete(myThread->cond);
	disp->commitThread = NULL;
	clearList(&myThread->SubmitHead, 0);
	hwc_free(myThread);
	disp->commitThread = NULL;
}

