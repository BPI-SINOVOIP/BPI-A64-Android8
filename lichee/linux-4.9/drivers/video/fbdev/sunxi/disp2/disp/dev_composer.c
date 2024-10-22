/*
 * Allwinner SoCs display driver.
 *
 * Copyright (C) 2018 Allwinner.
 *
 * This file is licensed under the terms of the GNU General Public
 * License version 2.  This program is licensed "as is" without any
 * warranty of any kind, whether express or implied.
 */

#include <linux/fence.h>
#include <linux/sync_file.h>
#include <linux/file.h>
#include "dev_disp.h"
#include <video/sunxi_display2.h>

enum {
	HWC_NEW_CLIENT = 1,
	HWC_DESTROY_CLIENT,
	HWC_ACQUIRE_FENCE,
	HWC_SUBMIT_FENCE,
};

struct sync_info {
	int fd;
	unsigned int count;
};

struct hwc_fence {
	struct list_head node;
	struct fence base;
};

struct display_sync {
	unsigned timeline_count;
	unsigned submmit_count;
	unsigned current_count;
	unsigned skip_count;
	unsigned free_count;
	u64 context;
	spinlock_t fence_lock;
	struct list_head fence_list;
	char name[7];
	bool active_disp;
};

struct composer_private_data {
	struct disp_drv_info *dispopt;
	bool b_no_output;
	struct display_sync display_sync[DISP_SCREEN_NUM];
};

static struct composer_private_data composer_priv;

static inline struct display_sync *get_display_sync(struct fence *fence)
{
	return container_of(fence->lock, struct display_sync, fence_lock);
}

static inline struct hwc_fence *get_hwc_fence(struct fence *fence)
{
	return container_of(fence, struct hwc_fence, base);
}

static const char *hwc_timeline_get_driver_name (struct fence *fence)
{
	return "sunxi_hwc";
}

static const char *hwc_timeline_get_timeline_name(struct fence *fence)
{
	struct display_sync *parent;
	parent = get_display_sync(fence);
	return parent->name;
}

static bool hwc_timeline_enable_signaling(struct fence *fence)
{
	return true;
}

static bool hwc_timeline_fence_signaled(struct fence *fence)
{
	struct hwc_fence *hwc_fence;
	struct display_sync *display_sync;

	hwc_fence = get_hwc_fence(fence);
	display_sync = get_display_sync(fence);
	return hwc_fence->base.seqno - display_sync->current_count > INT_MAX;
}

static void hwc_timeline_fence_release(struct fence *fence)
{
	struct hwc_fence *hwc_fence;
	struct display_sync *display_sync;

	display_sync = get_display_sync(fence);
	hwc_fence = get_hwc_fence(fence);
	if (fence->seqno - display_sync->current_count < INT_MAX) {
		printk(KERN_ERR "Other user put the fence:%u,check it\n", fence->seqno);
		return;
	}
	kfree(hwc_fence);
	display_sync->free_count++;
}

static const struct fence_ops hwc_timeline_fence_ops = {
	.get_driver_name = hwc_timeline_get_driver_name,
	.get_timeline_name = hwc_timeline_get_timeline_name,
	.enable_signaling = hwc_timeline_enable_signaling,
	.signaled = hwc_timeline_fence_signaled,
	.wait = fence_default_wait,
	.release = hwc_timeline_fence_release,
};

static void disp_composer_proc(u32 sel)
{
	struct hwc_fence *fence, *next;
	unsigned long flags;
	struct display_sync *display_sync = &composer_priv.display_sync[sel];
	bool all_relaease;

	if (sel >= DISP_SCREEN_NUM)
		return;
	all_relaease = composer_priv.b_no_output || !display_sync->active_disp;
	if (display_sync->current_count + 1 < display_sync->submmit_count)
		display_sync->skip_count +=
			display_sync->submmit_count - 1 - display_sync->current_count;

	display_sync->current_count = display_sync->submmit_count;
	spin_lock_irqsave(&display_sync->fence_lock, flags);
	list_for_each_entry_safe(fence, next, &display_sync->fence_list,
				 node) {
		if (fence->base.seqno - display_sync->current_count > INT_MAX
			|| all_relaease) {
			list_del_init(&fence->node);
			fence_signal_locked(&fence->base);
			fence_put(&fence->base);
		} else {
			break;
		}
	}
	spin_unlock_irqrestore(&display_sync->fence_lock, flags);
}

static int hwc_aquire_fence(int disp, void *user_fence)
{
	struct display_sync *dispsync = NULL;
	struct hwc_fence *fence;
	struct sync_file *sync_file;
	unsigned long flags;
	struct sync_info sync;

	dispsync = &composer_priv.display_sync[disp];

	if (!dispsync->active_disp)
		return -ENODEV;

	fence = kzalloc(sizeof(struct hwc_fence), GFP_KERNEL);
	if (fence == NULL) {
		printk(KERN_ERR "kzlloc display pt fail\n");
		goto err_quire;
	}

	fence_init(&fence->base, &hwc_timeline_fence_ops, &dispsync->fence_lock,
		   dispsync->context, ++dispsync->timeline_count);
	sync_file = sync_file_create(&fence->base);
	if (!sync_file) {
		kfree(fence);
		goto err_quire;
	}

	sync.fd = get_unused_fd_flags(O_CLOEXEC);
	if (sync.fd < 0) {
		printk(KERN_ERR "get unused fd fail\n");
		kfree(fence);
		goto err_quire;
	}
	fd_install(sync.fd, sync_file->file);
	sync.count = fence->base.seqno;
	if (copy_to_user((void __user *)user_fence, &sync, sizeof(sync)))
		printk(KERN_ERR "copy_to_user fail.\n");

	spin_lock_irqsave(&dispsync->fence_lock, flags);
	list_add_tail(&fence->node, &dispsync->fence_list);
	spin_unlock_irqrestore(&dispsync->fence_lock, flags);

	set_bit(FENCE_FLAG_ENABLE_SIGNAL_BIT, &fence->base.flags);

	return 0;

err_quire:
	return -ENXIO;
}

static inline int hwc_submit(int disp, unsigned int sbcount)
{
	composer_priv.display_sync[disp].submmit_count = sbcount;
	if (composer_priv.b_no_output)
		disp_composer_proc(disp);
	return 0;
}

static int get_de_clk_rate(unsigned int disp, int *usr)
{
	struct disp_manager *disp_mgr;
	int rate = 254000000;

	if (DISP_SCREEN_NUM <= disp) {
		printk("%s: disp=%d\n", __func__, disp);
		return -1;
	}

	disp_mgr = composer_priv.dispopt->mgr[disp];
	if (disp_mgr && disp_mgr->get_clk_rate)
		rate = disp_mgr->get_clk_rate(disp_mgr);
	put_user(rate, usr);
	return 0;
}

static int hwc_new_client(int disp, int *user)
{
	if (composer_priv.display_sync[disp].active_disp == true)
		return 0;
	composer_priv.display_sync[disp].timeline_count = 0;
	composer_priv.display_sync[disp].submmit_count = 0;
	composer_priv.display_sync[disp].free_count = 0;
	composer_priv.display_sync[disp].current_count = 0;
	composer_priv.display_sync[disp].skip_count = 0;
	composer_priv.display_sync[disp].active_disp = true;
	get_de_clk_rate(disp, (int *)user);
	return 0;
}

static int hwc_destroy_client(int disp)
{
	composer_priv.display_sync[disp].active_disp = false;
	disp_composer_proc(disp);
	return 0;
}

static int hwc_ioctl(unsigned int cmd, unsigned long arg)
{
	int ret = -EFAULT;

	if (cmd == DISP_HWC_COMMIT) {
		unsigned long *ubuffer;
		ubuffer = (unsigned long *)arg;
		switch (ubuffer[1]) {
		case HWC_NEW_CLIENT:
			ret = hwc_new_client((int)ubuffer[0], (int *)ubuffer[2]);
			break;
		case HWC_DESTROY_CLIENT:
			ret = hwc_destroy_client((int)ubuffer[0]);
			break;
		case HWC_ACQUIRE_FENCE:
			ret = hwc_aquire_fence(ubuffer[0], (void *)ubuffer[2]);
			break;
		case HWC_SUBMIT_FENCE:
			ret = hwc_submit(ubuffer[0], ubuffer[2]);
			break;
		default:
			pr_warn("hwc give a err iotcl.\n");
		}
	}
	return ret;
}

static int hwc_suspend(void)
{
	int i;

	composer_priv.b_no_output = 1;
	for (i = 0; i < DISP_SCREEN_NUM; i++)
		disp_composer_proc(i);
	return 0;
}

static int hwc_resume(void)
{
	composer_priv.b_no_output = 0;
	return 0;
}

int hwc_dump(char *buf)
{
	int i = 0, count = 0;

	for (i = 0; i < DISP_SCREEN_NUM; i++) {
		if (composer_priv.display_sync[i].active_disp) {
			count += sprintf(buf + count,
				"disp[%1d]all:%u, sub:%u, cur:%u, free:%u, skip:%u\n",
				i,
				composer_priv.display_sync[i].timeline_count,
				composer_priv.display_sync[i].submmit_count,
				composer_priv.display_sync[i].current_count,
				composer_priv.display_sync[i].free_count,
				composer_priv.display_sync[i].skip_count);
		}
	}
	return count;
}

s32 composer_init(struct disp_drv_info *psg_disp_drv)
{
	int i;

	disp_register_ioctl_func(DISP_HWC_COMMIT, hwc_ioctl);
#if defined(CONFIG_COMPAT)
	disp_register_compat_ioctl_func(DISP_HWC_COMMIT, hwc_ioctl);
#endif
	disp_register_sync_finish_proc(disp_composer_proc);
	disp_register_standby_func(hwc_suspend, hwc_resume);
	composer_priv.dispopt = psg_disp_drv;
	for (i = 0; i < DISP_SCREEN_NUM; i++) {
		INIT_LIST_HEAD(&composer_priv.display_sync[i].fence_list);
		spin_lock_init(&composer_priv.display_sync[i].fence_lock);
		composer_priv.display_sync[i].context = fence_context_alloc(1);
		sprintf(composer_priv.display_sync[i].name, "disp_%d", i);
		composer_priv.display_sync[i].name[6] = 0;
	}
	return 0;
}
