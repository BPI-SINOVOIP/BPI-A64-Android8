/*
 * Copyright (C) 2010 ARM Limited. All rights reserved.
 *
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
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

#include <errno.h>
#include <pthread.h>
#include <stdlib.h>

#include <cutils/log.h>
#include <cutils/atomic.h>
#include <hardware/hardware.h>
#include <hardware/gralloc.h>

#include <sys/sysinfo.h>

#include "gralloc_priv.h"
#include "alloc_device.h"
#include "framebuffer_device.h"

#if GRALLOC_ARM_UMP_MODULE
#include <ump/ump_ref_drv.h>
static int s_ump_is_open = 0;
#endif

#if GRALLOC_ARM_DMA_BUF_MODULE
#include <linux/ion.h>
#include <ion/ion.h>
#include <sys/mman.h>
#endif

static pthread_mutex_t s_map_lock = PTHREAD_MUTEX_INITIALIZER;

extern aw_mem_info_data aw_mem_info;

static void get_dram_size(void)
{
	struct sysinfo s_info;
    int err;
    err = sysinfo(&s_info);
	if(err)
		AERR("%s(%d): Failed to get dram size!\n", __func__, __LINE__);
	else
		aw_mem_info.dram_size = s_info.totalram/1024/1024;
}

static void get_secure_level(void)
{
	char secure_level[PROPERTY_VALUE_MAX] = {0};
	property_get("ro.sys.widevine_oemcrypto_level", secure_level, "3");
	if(atoi(secure_level))
		aw_mem_info.secure_level = atoi(secure_level);
}

static void get_ion_flush_cache_range(void)
{
	char ion_flush_cache_range[PROPERTY_VALUE_MAX] = {0};
	property_get("ro.sys.ion_flush_cache_range", ion_flush_cache_range, "0");
	if(atoi(ion_flush_cache_range) == 1)
		aw_mem_info.ion_flush_cache_range = 1;
}

static void get_carveout_enable(void)
{
	char carveout_enable[PROPERTY_VALUE_MAX] = {0};
	property_get("ro.sys.gralloc_carveout_enable", carveout_enable, "0");
	if(atoi(carveout_enable) == 1)
		aw_mem_info.carveout_enable = 1;
}

static void get_iommu_type(void)
{
	char iomem_type[PROPERTY_VALUE_MAX] = {0};

	/*
	 * ro.kernel.iomem.type
	 * 0xaf10: IOMMU
	 * 0xfa01: CMA
	 */
	property_get("ro.kernel.iomem.type", iomem_type, "0xfa01");
	if (!strncmp(iomem_type, "0xaf10", 6))
		aw_mem_info.iommu_enabled = true;
	else
		aw_mem_info.iommu_enabled = false;
}

static int gralloc_device_open(const hw_module_t *module, const char *name, hw_device_t **device)
{
	int status = -EINVAL;

	if (!strncmp(name, GRALLOC_HARDWARE_GPU0, MALI_GRALLOC_HARDWARE_MAX_STR_LEN))
	{
		status = alloc_device_open(module, name, device);
	}
	// else if (!strncmp(name, GRALLOC_HARDWARE_FB0, MALI_GRALLOC_HARDWARE_MAX_STR_LEN))
	// {
		// status = framebuffer_device_open(module, name, device);
	// }

	get_dram_size();
	get_secure_level();
	get_ion_flush_cache_range();
	get_carveout_enable();
	get_iommu_type();
	if (get_gralloc_debug())
	{
		AINF("Dram size is %d MB\n", aw_mem_info.dram_size);
		AINF("Secure level is %d\n", aw_mem_info.secure_level);
		AINF("ION flush Cache range: %s\n", aw_mem_info.ion_flush_cache_range == 1 ? "Yes" : "No");
		AINF("Carveout enable: %s\n",  aw_mem_info.carveout_enable == 1 ? "Yes" : "No");
		AINF("IOMMU is %s\n", aw_mem_info.iommu_enabled ? "enabled" : "disabled");
	}

	return status;
}

static int gralloc_register_buffer(gralloc_module_t const *module, buffer_handle_t handle)
{
	MALI_IGNORE(module);

	if (private_handle_t::validate(handle) < 0)
	{
		AERR("Registering invalid buffer 0x%p, returning error", handle);
		return -EINVAL;
	}

	// if this handle was created in this process, then we keep it as is.
	private_handle_t *hnd = (private_handle_t *)handle;

	int retval = -EINVAL;

	pthread_mutex_lock(&s_map_lock);

#if GRALLOC_ARM_UMP_MODULE

	if (!s_ump_is_open)
	{
		ump_result res = ump_open(); // MJOLL-4012: UMP implementation needs a ump_close() for each ump_open

		if (res != UMP_OK)
		{
			pthread_mutex_unlock(&s_map_lock);
			AERR("Failed to open UMP library with res=%d", res);
			return retval;
		}

		s_ump_is_open = 1;
	}

#endif

	hnd->pid = getpid();

	if (hnd->flags & private_handle_t::PRIV_FLAGS_FRAMEBUFFER)
	{
		AERR("Can't register buffer 0x%p as it is a framebuffer", handle);
	}
	else if (hnd->flags & private_handle_t::PRIV_FLAGS_USES_UMP)
	{
#if GRALLOC_ARM_UMP_MODULE
		hnd->ump_mem_handle = (int)ump_handle_create_from_secure_id(hnd->ump_id);

		if (UMP_INVALID_MEMORY_HANDLE != (ump_handle)hnd->ump_mem_handle)
		{
			hnd->base = ump_mapped_pointer_get((ump_handle)hnd->ump_mem_handle);

			if (0 != hnd->base)
			{
				hnd->writeOwner = 0;
				hnd->lockState &= ~(private_handle_t::LOCK_STATE_UNREGISTERED);

				pthread_mutex_unlock(&s_map_lock);
				return 0;
			}
			else
			{
				AERR("Failed to map UMP handle 0x%x", hnd->ump_mem_handle);
			}

			ump_reference_release((ump_handle)hnd->ump_mem_handle);
		}
		else
		{
			AERR("Failed to create UMP handle 0x%x", hnd->ump_mem_handle);
		}

#else
		AERR("Gralloc does not support UMP. Unable to register UMP memory for handle 0x%p", hnd);
#endif
	}
	else if (hnd->flags & private_handle_t::PRIV_FLAGS_USES_ION)
	{
#if GRALLOC_ARM_DMA_BUF_MODULE
		int ret;
		unsigned char *mappedAddress;
		size_t size = hnd->size;
		hw_module_t *pmodule = NULL;
		private_module_t *m = NULL;

		if (hw_get_module(GRALLOC_HARDWARE_MODULE_ID, (const hw_module_t **)&pmodule) == 0)
		{
			m = reinterpret_cast<private_module_t *>(pmodule);
		}
		else
		{
			AERR("Could not get gralloc module for handle: 0x%p", hnd);
			retval = -errno;
			goto cleanup;
		}

		/* the test condition is set to m->ion_client <= 0 here, because:
		 * 1) module structure are initialized to 0 if no initial value is applied
		 * 2) a second user process should get a ion fd greater than 0.
		 */
		if (m->ion_client <= 0)
		{
			/* a second user process must obtain a client handle first via ion_open before it can obtain the shared ion buffer*/
			m->ion_client = ion_open();

			if (m->ion_client < 0)
			{
				AERR("Could not open ion device for handle: 0x%p", hnd);
				retval = -errno;
				goto cleanup;
			}
		}

		mappedAddress = (unsigned char *)mmap(NULL, size, PROT_READ | PROT_WRITE, MAP_SHARED, hnd->share_fd, 0);

		if (MAP_FAILED == mappedAddress)
		{
			AERR("mmap( share_fd:%d ) failed with %s",  hnd->share_fd, strerror(errno));
			retval = -errno;
			goto cleanup;
		}

		hnd->base = mappedAddress + hnd->offset;
		hnd->lockState &= ~(private_handle_t::LOCK_STATE_UNREGISTERED);

		pthread_mutex_unlock(&s_map_lock);
		return 0;
#endif
	}
	else
	{
		AERR("registering non-UMP buffer not supported. flags = %d", hnd->flags);
	}

#if GRALLOC_ARM_DMA_BUF_MODULE
cleanup:
#endif
	pthread_mutex_unlock(&s_map_lock);
	return retval;
}

static void unmap_buffer(private_handle_t *hnd)
{
	if (hnd->flags & private_handle_t::PRIV_FLAGS_USES_UMP)
	{
#if GRALLOC_ARM_UMP_MODULE
		ump_mapped_pointer_release((ump_handle)hnd->ump_mem_handle);
		ump_reference_release((ump_handle)hnd->ump_mem_handle);
		hnd->ump_mem_handle = (int)UMP_INVALID_MEMORY_HANDLE;
#else
		AERR("Can't unregister UMP buffer for handle 0x%p. Not supported", hnd);
#endif
	}
	else if (hnd->flags & private_handle_t::PRIV_FLAGS_USES_ION)
	{
#if GRALLOC_ARM_DMA_BUF_MODULE
		void *base = (void *)hnd->base;
		size_t size = hnd->size;

		if (munmap(base, size) < 0)
		{
			AERR("Could not munmap base:0x%p size:%lu '%s'", base, (unsigned long)size, strerror(errno));
		}

#else
		AERR("Can't unregister DMA_BUF buffer for hnd %p. Not supported", hnd);
#endif

	}
	else
	{
		AERR("Unregistering unknown buffer is not supported. Flags = %d", hnd->flags);
	}

	hnd->base = 0;
	hnd->lockState = 0;
	hnd->writeOwner = 0;
}

static int gralloc_unregister_buffer(gralloc_module_t const *module, buffer_handle_t handle)
{
	MALI_IGNORE(module);

	if (private_handle_t::validate(handle) < 0)
	{
		AERR("unregistering invalid buffer 0x%p, returning error", handle);
		return -EINVAL;
	}

	private_handle_t *hnd = (private_handle_t *)handle;

	AERR_IF(hnd->lockState & private_handle_t::LOCK_STATE_READ_MASK, "[unregister] handle %p still locked (state=%08x)", hnd, hnd->lockState);

	if (hnd->flags & private_handle_t::PRIV_FLAGS_FRAMEBUFFER)
	{
		AERR("Can't unregister buffer 0x%p as it is a framebuffer", handle);
	}
	else if (hnd->pid == getpid()) // never unmap buffers that were not registered in this process
	{
		pthread_mutex_lock(&s_map_lock);

		hnd->lockState &= ~(private_handle_t::LOCK_STATE_MAPPED);

		/* if handle is still locked, the unmapping would not happen until unlocked*/
		if (!(hnd->lockState & private_handle_t::LOCK_STATE_WRITE))
		{
			unmap_buffer(hnd);
		}

		hnd->lockState |= private_handle_t::LOCK_STATE_UNREGISTERED;

		pthread_mutex_unlock(&s_map_lock);
	}
	else
	{
		AERR("Trying to unregister buffer 0x%p from process %d that was not created in current process: %d", hnd, hnd->pid, getpid());
	}

	return 0;
}

static int gralloc_lock(gralloc_module_t const *module, buffer_handle_t handle, int usage, int l, int t, int w, int h, void **vaddr)
{


	if (private_handle_t::validate(handle) < 0)
	{
		AERR("Locking invalid buffer 0x%p, returning error", handle);
		return -EINVAL;
	}

	private_handle_t *hnd = (private_handle_t *)handle;

	pthread_mutex_lock(&s_map_lock);

	if (hnd->lockState & private_handle_t::LOCK_STATE_UNREGISTERED)
	{
		AERR("Locking on an unregistered buffer 0x%p, returning error", hnd);
		pthread_mutex_unlock(&s_map_lock);
		return -EINVAL;
	}

	if (hnd->flags & private_handle_t::PRIV_FLAGS_USES_UMP || hnd->flags & private_handle_t::PRIV_FLAGS_USES_ION)
	{
		hnd->writeOwner = (usage & GRALLOC_USAGE_SW_WRITE_OFTEN) == GRALLOC_USAGE_SW_WRITE_OFTEN;
	}

	hnd->lockState |= private_handle_t::LOCK_STATE_WRITE;

	pthread_mutex_unlock(&s_map_lock);

	if (usage & (GRALLOC_USAGE_SW_READ_MASK | GRALLOC_USAGE_SW_WRITE_MASK))
	{
		*vaddr = (void *)hnd->base;
	}

	MALI_IGNORE(module);
	MALI_IGNORE(l);
	MALI_IGNORE(t);
	MALI_IGNORE(w);
	MALI_IGNORE(h);
	return 0;
}

static int gralloc_unlock(gralloc_module_t const *module, buffer_handle_t handle)
{
	MALI_IGNORE(module);

	if (private_handle_t::validate(handle) < 0)
	{
		AERR("Unlocking invalid buffer 0x%p, returning error", handle);
		return -EINVAL;
	}

	private_handle_t *hnd = (private_handle_t *)handle;

	if (hnd->flags & private_handle_t::PRIV_FLAGS_USES_UMP && hnd->writeOwner)
	{
#if GRALLOC_ARM_UMP_MODULE
		ump_cpu_msync_now((ump_handle)hnd->ump_mem_handle, UMP_MSYNC_CLEAN_AND_INVALIDATE, (void *)hnd->base, hnd->size);
#else
		AERR("Buffer 0x%p is UMP type but it is not supported", hnd);
#endif
	}
	else if (hnd->flags & private_handle_t::PRIV_FLAGS_USES_ION && hnd->writeOwner)
	{
#if GRALLOC_ARM_DMA_BUF_MODULE
		hw_module_t *pmodule = NULL;
		private_module_t *m = NULL;

		if (hw_get_module(GRALLOC_HARDWARE_MODULE_ID, (const hw_module_t **)&pmodule) == 0)
		{
			m = reinterpret_cast<private_module_t *>(pmodule);
			if (aw_flush_cache(m->ion_client, (void *)hnd->base, hnd->share_fd, (size_t)hnd->size))
				AERR("Failed to flush Cache, err = %d\n", errno);
		}
		else
		{
			AERR("Couldnot get gralloc module for handle 0x%p\n", handle);
		}

#endif
	}

	pthread_mutex_lock(&s_map_lock);

	hnd->lockState &= ~(private_handle_t::LOCK_STATE_WRITE);

	/* if the handle has already been unregistered, unmap it here*/
	if (hnd->lockState & private_handle_t::LOCK_STATE_UNREGISTERED)
	{
		unmap_buffer(hnd);
	}

	pthread_mutex_unlock(&s_map_lock);

	return 0;
}

#define GRALLOC_ALIGN( value, base ) (((value) + ((base) - 1)) & ~((base) - 1))

static int gralloc_lock_ycbcr(struct gralloc_module_t const* module,
            buffer_handle_t handle, int usage,
            int l, int t, int w, int h,
            struct android_ycbcr *ycbcr)
{
	private_handle_t* hnd = (private_handle_t*)handle;
	int ystride = GRALLOC_ALIGN(hnd->width, 16);
	int cstride, aligned_height;

	switch (hnd->format)
	{
		case HAL_PIXEL_FORMAT_YCrCb_420_SP:
			aligned_height = GRALLOC_ALIGN(hnd->height, 16);
			ycbcr->y  = hnd->base;
			ycbcr->cr = (void *)((uintptr_t)hnd->base + ystride * aligned_height);
			ycbcr->cb = (void *)((uintptr_t)ycbcr->cr + 1);
			ycbcr->ystride = ystride;
			ycbcr->cstride = ystride;
			ycbcr->chroma_step = 2;
			break;

		case HAL_PIXEL_FORMAT_YV12:
			cstride = GRALLOC_ALIGN(ystride / 2, 16);
			aligned_height = GRALLOC_ALIGN(hnd->height, 2);
			ycbcr->y  = hnd->base;
			ycbcr->cr = (void *)((uintptr_t)hnd->base + ystride * aligned_height);
			ycbcr->cb = (void *)((uintptr_t)ycbcr->cr + cstride * aligned_height / 2);
			ycbcr->ystride = ystride;
			ycbcr->cstride = cstride;
			ycbcr->chroma_step = 1;
			break;

		default:
			AERR("%s: Invalid format 0x%x", __FUNCTION__, hnd->format);
			return -EINVAL;
	}

	MALI_IGNORE(module);
	MALI_IGNORE(usage);
	MALI_IGNORE(l);
	MALI_IGNORE(t);
	MALI_IGNORE(w);
	MALI_IGNORE(h);

	return 0;
}

// There is one global instance of the module

static struct hw_module_methods_t gralloc_module_methods =
{
	.open = gralloc_device_open
};

private_module_t::private_module_t()
{
#define INIT_ZERO(obj) (memset(&(obj),0,sizeof((obj))))

	base.common.tag = HARDWARE_MODULE_TAG;
	base.common.version_major = 1;
	base.common.version_minor = 0;
	base.common.id = GRALLOC_HARDWARE_MODULE_ID;
	base.common.name = "Graphics Memory Allocator Module";
	base.common.author = "ARM Ltd.";
	base.common.methods = &gralloc_module_methods;
	base.common.dso = NULL;
	INIT_ZERO(base.common.reserved);

	base.registerBuffer = gralloc_register_buffer;
	base.unregisterBuffer = gralloc_unregister_buffer;
	base.lock = gralloc_lock;
	base.unlock = gralloc_unlock;
	base.lock_ycbcr = gralloc_lock_ycbcr;
	base.perform = NULL;
	INIT_ZERO(base.reserved_proc);

	framebuffer = NULL;
	flags = 0;
	numBuffers = 0;
	bufferMask = 0;
	pthread_mutex_init(&(lock), NULL);
	currentBuffer = NULL;
	INIT_ZERO(info);
	INIT_ZERO(finfo);
	xdpi = 0.0f;
	ydpi = 0.0f;
	fps = 0.0f;

#undef INIT_ZERO
};

/*
 * HAL_MODULE_INFO_SYM will be initialized using the default constructor
 * implemented above
 */
struct private_module_t HAL_MODULE_INFO_SYM;

