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
#include <hardware/sunxi_metadata_def.h>

#define ION_IOC_SUNXI_PHYS_ADDR 7
int ionfd = -1;

typedef struct {
    ion_user_handle_t handle;
    unsigned int phys_addr;
    unsigned int size;
}sunxi_phys_data;

#ifndef ION_HEAP_SECURE_MASK
#define ION_HEAP_TYPE_SUNXI_START (ION_HEAP_TYPE_CUSTOM + 1)
#define ION_HEAP_TYPE_SECURE     (ION_HEAP_TYPE_SUNXI_START)
#define ION_HEAP_SECURE_MASK     (1<<ION_HEAP_TYPE_SECURE)
#endif /* ION_HEAP_SECURE_MASK */

int Iondeviceinit(void)
{
	ionfd = open("/dev/ion", O_RDWR);
	if(ionfd < 0) {
		ALOGE("Failed to open ion device");
		return -1;
	}
	return 0;
}

int IondeviceDeinit(void)
{
	if (ionfd >= 0)
		close(ionfd);
	ionfd = -1;
	return 0;
}

unsigned int ionGetPhyAddress(int sharefd)
{
    int ret = -1;
    struct ion_custom_data custom_data;
	sunxi_phys_data phys_data;
    ion_handle_data freedata;
    ion_user_handle_t handle;

	ret = ion_import(ionfd, sharefd, &handle);
	if (ret < 0) {
		ALOGE("ion import err:%d share_fd:%d",ret, sharefd);
		return 0;
	}
    custom_data.cmd = ION_IOC_SUNXI_PHYS_ADDR;
	phys_data.handle = handle;
	custom_data.arg = (unsigned long)&phys_data;
	ret = ioctl(ionfd, ION_IOC_CUSTOM, &custom_data);
	if (ret < 0) {
        ALOGV("%s: ION_IOC_CUSTOM failed(ret=%d)", __func__, ret);
		/* kernel 4.9 ion has not the ioctl,  */
		//return 0;
    }
    ion_free(ionfd, handle);
    return phys_data.phys_addr;  
}

void syncLayerBuffer(Layer_t *layer)
{
	private_handle_t *handle = NULL;
	handle = (private_handle_t *)layer->buffer;

	ion_sync_fd(ionfd, handle->share_fd);
}

int ionAllocBuffer(int size, bool mustconfig, bool is_secure)
{
	int ret, share_fd;

	if (is_secure){
		ret = ion_alloc_fd(ionfd, size,
			4096,  ION_HEAP_SECURE_MASK, 0, &share_fd);
		if (ret < 0) {
			 ALOGD("alloc err from ION_HEAP_SECURE_MASK");
			 return ret;
		}
	} else {
		if (!mustconfig) {
			ret = ion_alloc_fd(ionfd, size,
			4096, ION_HEAP_SYSTEM_MASK, 0, &share_fd);
			if (ret == 0)
				return share_fd;
		}
		ret = ion_alloc_fd(ionfd, size,
			4096, ION_HEAP_TYPE_DMA_MASK, 0, &share_fd);
		if (ret == 0)
			return share_fd;
		ret = ion_alloc_fd(ionfd, size,
					4096, ION_HEAP_SYSTEM_CONTIG_MASK, 0, &share_fd);
		if (ret == 0)
			return share_fd;

	}
	ALOGD("alloc buffer from ion err:%d", ret);
	return ret;
}

#if (GRALLOC_SUNXI_METADATA_BUF & (DE_VERSION == 30))
unsigned int ionGetMetadataFlag(buffer_handle_t handle)
{
    if (NULL != handle) {
        private_handle_t *hdl = (private_handle_t *)handle;
        int ret = 0;
        ion_user_handle_t ion_handle;
        unsigned char *map_ptr = NULL;
        int map_fd = -1;
        unsigned int flag = 0;

        if (0 == hdl->ion_metadata_size) {
            ALOGW("ion_metadata_size is 0");
            return 0;
        }

        ret = ion_import(ionfd, hdl->metadata_fd, &ion_handle);
        if (ret) {
            ALOGD("ion import failed, ret=%d, metadata_fd=%d!",
                  ret, hdl->metadata_fd);
            return 0;
        }
        ret = ion_map(ionfd, ion_handle, hdl->ion_metadata_size,
                      PROT_READ, MAP_SHARED, 0, &map_ptr, &map_fd);
        if (ret) {
            ALOGE("ion_map failed, ret=%d\n", ret);
            if (0 <= map_fd)
                close(map_fd);
            ion_free(ionfd, ion_handle);
            return 0;
        }

        struct sunxi_metadata *ptr = (struct sunxi_metadata *)map_ptr;
        flag = ptr->flag;

        /*
        struct afbc_header *p = &(ptr->afbc_head);
        ALOGD("&&&&& afbc header:");
           ALOGD("%u,%u,%u,%u;\n"
           "%u,%u,%u,%u;\n"
           "%u,%u,%u,%u;\n"
           "%u,%u,%u,%u\n"
           "%u,%u,%u @@@.",
           p->signature, p->filehdr_size, p->version, p->body_size,
           p->ncomponents, p->header_layout, p->yuv_transform, p->block_split,
           p->inputbits[0], p->inputbits[1], p->inputbits[2], p->inputbits[3],
           p->block_width, p->block_height, p->width, p->height,
           p->left_crop, p->top_crop, p->block_layout);
           */

        ret = munmap(map_ptr, hdl->ion_metadata_size);
        if (0 != ret) {
            ALOGD("munmap sunxi metadata failed ! ret=%d", ret);
        }
        close(map_fd);
        ion_free(ionfd, ion_handle);
        return (flag & SUNXI_METADATA_FLAG_AFBC_HEADER);
    } else {
        ALOGE("%s,%d", __FUNCTION__, __LINE__);
    }
    return 0;
}
#endif
