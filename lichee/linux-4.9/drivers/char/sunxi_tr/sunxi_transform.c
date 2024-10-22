/* linux/drivers/char/sunxi_tr/sunxi_tr.c
 *
 * Copyright (c) 2014 Allwinnertech Co., Ltd.
 * Author: Tyle <tyle@allwinnertech.com>
 *
 * Transform driver for sunxi platform
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation.
*/

#include "transform.h"
#include <linux/compat.h>
#include <linux/idr.h>
#include <linux/dma-buf.h>

#define TR_CHAN_MAX 32
/* #define TR_POLL */
/* #define TR_CHECK_THREAD */

#if (defined CONFIG_ARCH_SUN50IW3P1)
#define USE_DMA_BUF
#endif

struct dmabuf_item {
	struct list_head list;
	int fd;
	struct dma_buf *buf;
	struct dma_buf_attachment *attachment;
	struct sg_table *sgt;
	dma_addr_t dma_addr;
	unsigned long long id;
};
struct sunxi_transform {
	int id; /* chan id */
	struct rb_node node;
	struct list_head list;
	bool requested; /* indicate if have request */
	bool busy; /* at busy state when transforming */
	bool error;/* indicate some error happens in this channel */
	unsigned long start_time; /* the time when starting transform */
	unsigned long timeout;/* ms */
	tr_info info;
};

struct sunxi_trdev {
	struct device       *dev;
	void __iomem        *base;
	int                 irq;
	struct clk          *clk;   /* clock gate for tr */
	spinlock_t          slock;
	struct mutex        mlock;
	struct list_head    trs;    /* transform chan list */
	unsigned int        count;  /* transform channel counter */
	struct sunxi_transform *cur_tr; /*curent transform channel processing*/
	bool busy;
	struct task_struct *task;
	struct idr idr;
	struct rb_node node;
	struct rb_root handles;
};

static struct sunxi_trdev *gsunxi_dev;

static struct cdev *tr_cdev;
static dev_t devid;
static struct class *tr_class;
static struct device *tr_dev;
static bool dev_init;
static struct mutex id_lock;
static struct device *dmabuf_dev;

u32 dbg_info;

/* #define struct sunxi_trdev *to_sunxi_trdev(dev)
 * container_of(dev, struct sunxi_trdev, dev)
 */
static int sunxi_tr_finish_procss(void);

#if !defined(CONFIG_OF)
static struct resource tr_resource[] = {
	[0] = {
		.start = (int __force)SUNXI_DE_VBASE,
		.end = (int __force)(SUNXI_DE_VBASE + SUNXI_DE_SIZE),
		.flags = IORESOURCE_MEM,
	},
	[1] = {
		.start = (int __force)SUNXI_IRQ_DEIRQ1,
		.end = (int __force)SUNXI_IRQ_DEIRQ1,
		.flags = IORESOURCE_IRQ,
	},
};
#endif

static ssize_t tr_debug_show(struct device *dev,
			     struct device_attribute *attr, char *buf)
{
	return sprintf(buf, "debug=%d\n", dbg_info);
}

static ssize_t tr_debug_store(struct device *dev,
			      struct device_attribute *attr,
			      const char *buf, size_t count)
{
	if (strncasecmp(buf, "1", 1) == 0)
		dbg_info = 1;
	else if (strncasecmp(buf, "0", 1) == 0)
		dbg_info = 0;
	else
		pr_err("Error input!\n");

	return count;
}

static DEVICE_ATTR(debug, 0660,
		   tr_debug_show, tr_debug_store);

static struct attribute *tr_attributes[] = {
	&dev_attr_debug.attr,
	NULL
};

static struct attribute_group tr_attribute_group = {
	.name = "attr",
	.attrs = tr_attributes
};

#ifdef USE_DMA_BUF
static int tr_dma_map(int fd, struct dmabuf_item *item)
{
	struct dma_buf *dmabuf;
	struct dma_buf_attachment *attachment;
	struct sg_table *sgt, *sgt_bak;
	struct scatterlist *sgl, *sgl_bak;
	s32 sg_count = 0;
	int ret = -1;
	int i;

	if (fd < 0) {
		pr_err("dma_buf_id(%d) is invalid\n", fd);
		goto exit;
	}
	dmabuf = dma_buf_get(fd);
	if (IS_ERR(dmabuf)) {
		pr_err("dma_buf_get failed\n");
		goto exit;
	}

	attachment = dma_buf_attach(dmabuf, dmabuf_dev);
	if (IS_ERR(attachment)) {
		pr_err("dma_buf_attach failed\n");
		goto err_buf_put;
	}
	sgt = dma_buf_map_attachment(attachment, DMA_FROM_DEVICE);
	if (IS_ERR_OR_NULL(sgt)) {
		pr_err("dma_buf_map_attachment failed\n");
		goto err_buf_detach;
	}

	/* create a private sgtable base on the given dmabuf */
	sgt_bak = kmalloc(sizeof(struct sg_table), GFP_KERNEL | __GFP_ZERO);
	if (sgt_bak == NULL) {
		pr_err("alloc sgt fail\n");
		goto err_buf_unmap;
	}
	ret = sg_alloc_table(sgt_bak, sgt->nents, GFP_KERNEL);
	if (ret != 0) {
		pr_err("alloc sgt fail\n");
		goto err_kfree;
	}
	sgl_bak = sgt_bak->sgl;
	for_each_sg(sgt->sgl, sgl, sgt->nents, i)  {
		sg_set_page(sgl_bak, sg_page(sgl), sgl->length, sgl->offset);
		sgl_bak = sg_next(sgl_bak);
	}

	sg_count = dma_map_sg_attrs(dmabuf_dev, sgt_bak->sgl,
			      sgt_bak->nents, DMA_FROM_DEVICE,
			      DMA_ATTR_SKIP_CPU_SYNC);

	if (sg_count != 1) {
		pr_err("dma_map_sg failed:%d\n", sg_count);
		goto err_sgt_free;
	}

	item->fd = fd;
	item->buf = dmabuf;
	item->sgt = sgt_bak;
	item->attachment = attachment;
	item->dma_addr = sg_dma_address(sgt_bak->sgl);
	ret = 0;

	goto exit;

err_sgt_free:
	sg_free_table(sgt_bak);
err_kfree:
	kfree(sgt_bak);
err_buf_unmap:
	/* unmap attachment sgt, not sgt_bak, because it's not alloc yet! */
	dma_buf_unmap_attachment(attachment, sgt, DMA_FROM_DEVICE);
err_buf_detach:
	dma_buf_detach(dmabuf, attachment);
err_buf_put:
	dma_buf_put(dmabuf);
exit:
	return ret;
}

static void tr_dma_unmap(struct dmabuf_item *item)
{
	dma_unmap_sg_attrs(dmabuf_dev, item->sgt->sgl,
			      item->sgt->nents, DMA_FROM_DEVICE,
			      DMA_ATTR_SKIP_CPU_SYNC);
	dma_buf_unmap_attachment(item->attachment, item->sgt, DMA_FROM_DEVICE);
	sg_free_table(item->sgt);
	kfree(item->sgt);
	dma_buf_detach(item->buf, item->attachment);
	dma_buf_put(item->buf);
}

static struct tr_format_attr fmt_attr_tbl[] = {
	/*
	 *format	   bits
	 *			hor_rsample(u,v)
	 *				ver_rsample(u,v)
	 *				    uvc
	 *					interleave
	 *						factor
	 *							div
	 */
	{ TR_FORMAT_ARGB_8888, 8,  1, 1, 1, 1, 0, 1, 4, 1},
	{ TR_FORMAT_ABGR_8888, 8,  1, 1, 1, 1, 0, 1, 4, 1},
	{ TR_FORMAT_RGBA_8888, 8,  1, 1, 1, 1, 0, 1, 4, 1},
	{ TR_FORMAT_BGRA_8888, 8,  1, 1, 1, 1, 0, 1, 4, 1},
	{ TR_FORMAT_XRGB_8888, 8,  1, 1, 1, 1, 0, 1, 4, 1},
	{ TR_FORMAT_XBGR_8888, 8,  1, 1, 1, 1, 0, 1, 4, 1},
	{ TR_FORMAT_RGBX_8888, 8,  1, 1, 1, 1, 0, 1, 4, 1},
	{ TR_FORMAT_BGRX_8888, 8,  1, 1, 1, 1, 0, 1, 4, 1},
	{ TR_FORMAT_RGB_888, 8,  1, 1, 1, 1, 0, 1, 3, 1},
	{ TR_FORMAT_BGR_888, 8,  1, 1, 1, 1, 0, 1, 3, 1},
	{ TR_FORMAT_RGB_565, 8,  1, 1, 1, 1, 0, 1, 2, 1},
	{ TR_FORMAT_BGR_565, 8,  1, 1, 1, 1, 0, 1, 2, 1},
	{ TR_FORMAT_ARGB_4444, 8,  1, 1, 1, 1, 0, 1, 2, 1},
	{ TR_FORMAT_ABGR_4444, 8,  1, 1, 1, 1, 0, 1, 2, 1},
	{ TR_FORMAT_RGBA_4444, 8,  1, 1, 1, 1, 0, 1, 2, 1},
	{ TR_FORMAT_BGRA_4444, 8,  1, 1, 1, 1, 0, 1, 2, 1},
	{ TR_FORMAT_ARGB_1555, 8,  1, 1, 1, 1, 0, 1, 2, 1},
	{ TR_FORMAT_ABGR_1555, 8,  1, 1, 1, 1, 0, 1, 2, 1},
	{ TR_FORMAT_RGBA_5551, 8,  1, 1, 1, 1, 0, 1, 2, 1},
	{ TR_FORMAT_BGRA_5551, 8,  1, 1, 1, 1, 0, 1, 2, 1},

	{ TR_FORMAT_YUV444_I_AYUV, 8,  1, 1, 1, 1, 0, 1, 3, 1},
	{ TR_FORMAT_YUV444_I_VUYA, 8,  1, 1, 1, 1, 0, 1, 3, 1},
	{ TR_FORMAT_YUV422_I_YVYU, 8,  1, 1, 1, 1, 0, 1, 2, 1},
	{ TR_FORMAT_YUV422_I_YUYV, 8,  1, 1, 1, 1, 0, 1, 2, 1},
	{ TR_FORMAT_YUV422_I_UYVY, 8,  1, 1, 1, 1, 0, 1, 2, 1},
	{ TR_FORMAT_YUV422_I_VYUY, 8,  1, 1, 1, 1, 0, 1, 2, 1},
	{ TR_FORMAT_YUV444_P, 8,  1, 1, 1, 1, 0, 1, 1, 1},
	{ TR_FORMAT_YUV422_P, 8,  2, 2, 1, 1, 0, 0, 2, 1},
	{ TR_FORMAT_YUV420_P, 8,  2, 2, 2, 2, 0, 0, 3, 2},
	{ TR_FORMAT_YUV411_P, 8,  4, 4, 1, 1, 0, 0, 3, 2},
	{ TR_FORMAT_YUV422_SP_UVUV, 8,  2, 2, 1, 1, 1, 0, 2, 1},
	{ TR_FORMAT_YUV422_SP_VUVU, 8,  2, 2, 1, 1, 1, 0, 2, 1},
	{ TR_FORMAT_YUV420_SP_UVUV, 8,  2, 2, 2, 2, 1, 0, 3, 2},
	{ TR_FORMAT_YUV420_SP_VUVU, 8,  2, 2, 2, 2, 1, 0, 3, 2},
	{ TR_FORMAT_YUV411_SP_UVUV, 8,  4, 4, 1, 1, 1, 0, 3, 2},
	{ TR_FORMAT_YUV411_SP_VUVU, 8,  4, 4, 1, 1, 1, 0, 3, 2},
};

s32 tr_set_info(tr_frame *tr_para, struct dmabuf_item *item)
{
	s32 ret = -1;
	u32 i = 0;
	u32 len = ARRAY_SIZE(fmt_attr_tbl);
	u32 y_width, y_height, u_width, u_height;
	u32 y_pitch, u_pitch;
	u32 y_size, u_size;

	tr_para->laddr[0] = item->dma_addr;

	if (tr_para->fmt >= TR_FORMAT_MAX) {
		pr_err("%s, format 0x%x is out of range\n", __func__,
			tr_para->fmt);
		goto exit;
	}

	for (i = 0; i < len; ++i) {

		if (fmt_attr_tbl[i].format == tr_para->fmt) {
			y_width = tr_para->pitch[0];
			y_height = tr_para->height[0];
			u_width = y_width/fmt_attr_tbl[i].hor_rsample_u;
			u_height = y_height/fmt_attr_tbl[i].ver_rsample_u;

			y_pitch = y_width;
			u_pitch = u_width * (fmt_attr_tbl[i].uvc + 1);

			y_size = y_pitch * y_height;
			u_size = u_pitch * u_height;
			tr_para->laddr[1] = tr_para->laddr[0] + y_size;
			tr_para->laddr[2] = tr_para->laddr[0] + y_size + u_size;
			if (tr_para->fmt == TR_FORMAT_YUV420_P) {
				/* v */
				tr_para->laddr[1] = tr_para->laddr[0] + y_size + u_size;
				tr_para->laddr[2] = tr_para->laddr[0] + y_size; /* u */
			}

			ret = 0;
			break;
		}
	}
	if (ret != 0)
		pr_err("%s, format 0x%x is invalid\n", __func__,
			tr_para->fmt);
exit:
	return ret;

}
#endif
#if defined(TR_CHECK_THREAD)
static int tr_thread(void *parg)
{
	while (1) {
		struct sunxi_transform *tr = NULL;
		unsigned long timeout = 0;

		if (kthread_should_stop())
			break;
		tr = gsunxi_dev->cur_tr;
		if (tr) {
			timeout = tr->start_time +
						msecs_to_jiffies(tr->timeout);
			if (time_after_eq(jiffies, timeout)) {
				pr_warn("%s, timeout(%d ms)\n", __func__,
				jiffies_to_msecs(jiffies - tr->start_time));
				de_tr_reset();
				tr->busy = false;
				tr->error = true;

				sunxi_tr_finish_procss();
			}
		}

		msleep(10);
	}

	return 0;
}
#endif

static int tr_check_timeout(void)
{
	struct sunxi_transform *tr = NULL;
	unsigned long timeout = 0;
	unsigned long flags;

	tr = gsunxi_dev->cur_tr;
	if (tr == NULL)
		return 0;

	spin_lock_irqsave(&gsunxi_dev->slock, flags);
	timeout = tr->start_time + msecs_to_jiffies(tr->timeout);
	if (tr->busy && time_after_eq(jiffies, timeout)) {
		tr->busy = false;
		tr->error = true;
		spin_unlock_irqrestore(&gsunxi_dev->slock, flags);
		de_tr_exception();
		pr_warn("%s, timeout(%d ms)\n", __func__,
				jiffies_to_msecs(jiffies - tr->start_time));
		sunxi_tr_finish_procss();
	} else
		spin_unlock_irqrestore(&gsunxi_dev->slock, flags);

	return 0;
}

/* find a tr which has request and a longest time to process */
static struct sunxi_transform *tr_find_proper_task(void)
{
	struct sunxi_transform *tr = NULL, *proper_tr = NULL;
	unsigned long min_time = jiffies;

	list_for_each_entry(tr, &gsunxi_dev->trs, list) {
		bool condition1 = (true == tr->requested);
		bool condition2 = time_after_eq(min_time, tr->start_time);

		if (condition1 && condition2) {
			min_time = tr->start_time;
			proper_tr = tr;
		} else {
			/* printk("find_task: %d,%d, %ld,%ld\n", condition1,
			 * condition2, min_time, tr->start_time);
			 */
		}
	}

	return proper_tr;
}

/* protect by @slock */
static int tr_process_next_proper_task(u32 from)
{
	unsigned long flags;
	struct sunxi_transform *tr = NULL;
	int ret = -1;

	spin_lock_irqsave(&gsunxi_dev->slock, flags);
	if (gsunxi_dev->busy) {
		spin_unlock_irqrestore(&gsunxi_dev->slock, flags);
		return -1;
	}

	/* find a tr which has request */
	tr = tr_find_proper_task();
	if (tr != NULL) {
		/* process request */
		gsunxi_dev->busy = true;
		tr->busy = true;
		tr->error = false;
		tr->start_time = jiffies;
		tr->requested = false;

		gsunxi_dev->cur_tr = tr;
	}
	spin_unlock_irqrestore(&gsunxi_dev->slock, flags);

	if (tr != NULL)
		ret = de_tr_set_cfg(&tr->info);

	return ret;
}

static int sunxi_tr_finish_procss(void)
{
	unsigned long flags;

	/* correct interrupt */
	spin_lock_irqsave(&gsunxi_dev->slock, flags);
	if (gsunxi_dev->cur_tr) {
		gsunxi_dev->cur_tr->busy = false;
		gsunxi_dev->cur_tr = NULL;
		gsunxi_dev->busy = false;
	}
	spin_unlock_irqrestore(&gsunxi_dev->slock, flags);

	tr_process_next_proper_task(0);

	return 0;
}

static struct sunxi_transform *tr_get_by_id(int id)
{
	 struct sunxi_transform *tr;

	 mutex_lock(&gsunxi_dev->mlock);
	 tr = idr_find(&gsunxi_dev->idr, id);
	 mutex_unlock(&gsunxi_dev->mlock);

	 return tr ? tr : ERR_PTR(-EINVAL);
}

/*
 * sunxi_tr_request - request transform channel
 * On success, returns transform handle.  On failure, returns 0.
 */

unsigned int sunxi_tr_request(void)
{
	struct sunxi_transform *tr = NULL;
	unsigned long flags;
	unsigned int count = 0;
	struct rb_node **p = &gsunxi_dev->handles.rb_node;
	struct rb_node *parent = NULL;
	struct sunxi_transform *entry;
	int id;

	if (gsunxi_dev->count > TR_CHAN_MAX) {
		pr_warn("%s(), user number have exceed max number %d\n",
		    __func__, TR_CHAN_MAX);
		return 0;
	}

	tr = kzalloc(sizeof(struct sunxi_transform), GFP_KERNEL);
	if (!tr) {
		pr_warn("alloc fail\n");
		return 0;
	}

	RB_CLEAR_NODE(&tr->node);
	tr->requested = false;
	tr->busy = false;
	tr->error = false;
	tr->timeout = 50; /* default 50ms timeout */
	tr->start_time = jiffies;
	id = idr_alloc(&gsunxi_dev->idr, tr, 1, 0, GFP_KERNEL);
	if (id < 0)
		return 0;
	tr->id = id;

	mutex_lock(&gsunxi_dev->mlock);
	while (*p) {
		parent = *p;
		entry = rb_entry(parent, struct sunxi_transform, node);

		if (tr < entry)
			p = &(*p)->rb_left;

		else if (tr > entry)
			p = &(*p)->rb_right;
		else
			pr_warn("%s: tr already found.\n", __func__);
	}
	rb_link_node(&tr->node, parent, p);
	rb_insert_color(&tr->node, &gsunxi_dev->handles);
	mutex_unlock(&gsunxi_dev->mlock);

	spin_lock_irqsave(&gsunxi_dev->slock, flags);
	list_add_tail(&tr->list, &gsunxi_dev->trs);
	gsunxi_dev->count++;
	count = gsunxi_dev->count;
	spin_unlock_irqrestore(&gsunxi_dev->slock, flags);

	mutex_lock(&gsunxi_dev->mlock);
	if (count == 1) {
		if (gsunxi_dev->clk)
			clk_prepare_enable(gsunxi_dev->clk);
		de_tr_init();

#if defined(TR_CHECK_THREAD)
		gsunxi_dev->task = kthread_create(tr_thread,
				(void *)0, "tr_thread");
		if (IS_ERR(gsunxi_dev->task)) {
			pr_warn("Unable to start kernel thread %s.\n",
					"tr_thread");
			gsunxi_dev->task = NULL;
		} else
			wake_up_process(gsunxi_dev->task);
#endif
	}
	mutex_unlock(&gsunxi_dev->mlock);
	TR_INFO_MSG("%s, count=%d\n", __func__, count);

	return id;
}
EXPORT_SYMBOL_GPL(sunxi_tr_request);
/*
 * sunxi_tr_release - release transform channel
 * @hdl: transform handle which return by sunxi_tr_request
 * On success, returns 0. On failure, returns ERR_PTR(-errno).
 */
int sunxi_tr_release(unsigned int id)
{
	struct sunxi_transform *tr = tr_get_by_id(id);
	unsigned long flags;
	unsigned int count = 0;

	if (IS_ERR_OR_NULL(tr)) {
		pr_warn("%s, hdl is invalid!\n", __func__);
		return -EINVAL;
	}


	mutex_lock(&gsunxi_dev->mlock);
	idr_remove(&gsunxi_dev->idr, tr->id);
	if (!RB_EMPTY_NODE(&tr->node))
		rb_erase(&tr->node, &gsunxi_dev->handles);
	mutex_unlock(&gsunxi_dev->mlock);

	spin_lock_irqsave(&gsunxi_dev->slock, flags);
	list_del(&tr->list);
	gsunxi_dev->count--;
	count = gsunxi_dev->count;
	kfree((void *)tr);
	spin_unlock_irqrestore(&gsunxi_dev->slock, flags);

	mutex_lock(&gsunxi_dev->mlock);
	if (count == 0) {
#if defined(TR_CHECK_THREAD)
		kthread_stop(gsunxi_dev->task);
		gsunxi_dev->task = NULL;
#endif
		de_tr_exit();
		if (gsunxi_dev->clk)
			clk_disable(gsunxi_dev->clk);
	}
	mutex_unlock(&gsunxi_dev->mlock);
	TR_INFO_MSG("%s, count=%d\n", __func__, count);

	return 0;
}
EXPORT_SYMBOL_GPL(sunxi_tr_release);
/*
 * sunxi_tr_commit - commit an transform request
 * @hdl: transform handle which return by sunxi_tr_request
 * On success, returns 0. On failure, returns ERR_PTR(-errno).
 */
int sunxi_tr_commit(unsigned int id, tr_info *info)
{
	int ret = 0;
	struct sunxi_transform *tr = tr_get_by_id(id);
#ifdef USE_DMA_BUF
	struct dmabuf_item *src_item = NULL;
	struct dmabuf_item *dst_item = NULL;

	src_item = kmalloc(sizeof(struct dmabuf_item),
			      GFP_KERNEL | __GFP_ZERO);
	if (src_item == NULL) {
		pr_err("malloc memory of size %ld fail!\n",
		       sizeof(struct dmabuf_item));
		goto EXIT;
	}
	dst_item = kmalloc(sizeof(struct dmabuf_item),
			      GFP_KERNEL | __GFP_ZERO);
	if (dst_item == NULL) {
		pr_err("malloc memory of size %ld fail!\n",
		       sizeof(struct dmabuf_item));
		goto FREE_SRC;
	}

#endif

	if (IS_ERR_OR_NULL(tr)) {
		pr_warn("%s, hdl is invalid\n", __func__);
		return -EINVAL;
	}

	TR_INFO_MSG("Input info:\n");
	TR_INFO_MSG("	Format: 0x%x\n", info->src_frame.fmt);
	TR_INFO_MSG("	Pitch: %d,%d,%d\n", info->src_frame.pitch[0],
		     info->src_frame.pitch[1], info->src_frame.pitch[2]);
	TR_INFO_MSG("	RECT X:%d\n", info->src_rect.x);
	TR_INFO_MSG("	RECT Y:%d\n", info->src_rect.y);
	TR_INFO_MSG("	RECT W:%d\n", info->src_rect.w);
	TR_INFO_MSG("	RECT H:%d\n", info->src_rect.h);

	TR_INFO_MSG("Output info:\n");
	TR_INFO_MSG("	Format: 0x%x\n", info->dst_frame.fmt);
	TR_INFO_MSG("	Pitch: %d,%d,%d\n", info->dst_frame.pitch[0],
		     info->dst_frame.pitch[1], info->dst_frame.pitch[2]);
	TR_INFO_MSG("	RECT X:%d\n", info->dst_rect.x);
	TR_INFO_MSG("	RECT Y:%d\n", info->dst_rect.y);
	TR_INFO_MSG("	RECT W:%d\n", info->dst_rect.w);
	TR_INFO_MSG("	RECT H:%d\n", info->dst_rect.h);

	if (!tr->requested && !tr->busy) {
		memcpy(&tr->info, info, sizeof(tr_info));
		tr->requested = true;

#ifdef USE_DMA_BUF
		ret = tr_dma_map(tr->info.src_frame.fd, src_item);
		if (ret != 0) {
			pr_err("map src_item fail!\n");
			goto FREE_DST;
		}
		ret = tr_dma_map(tr->info.dst_frame.fd, dst_item);
		if (ret != 0) {
			pr_err("map dst_item fail!\n");
			goto SRC_DMA_UNMAP;
		}

		tr_set_info(&tr->info.src_frame, src_item);
		tr_set_info(&tr->info.dst_frame, dst_item);
#endif
		TR_INFO_MSG("InputAddr: 0x%x,0x%x,0x%x\n",
				tr->info.src_frame.laddr[0],
				tr->info.src_frame.laddr[1],
				tr->info.src_frame.laddr[2]);
		TR_INFO_MSG("OutputAddr: 0x%x,0x%x,0x%x\n",
				tr->info.dst_frame.laddr[0],
				tr->info.dst_frame.laddr[1],
				tr->info.dst_frame.laddr[2]);

		ret = tr_process_next_proper_task(1);
	}

#if defined(TR_POLL)
{
	int wait_cnt = 5, delay_ms = 10, i = 0;

	while ((i < wait_cnt) && (de_tr_irq_query() != 0)) {
		msleep(delay_ms);
		i++;
	}
	if (wait_cnt >= 5)
		pr_warn("%s, timeout !!!\n", __func__);
	sunxi_tr_finish_procss();
}
#endif
#ifdef USE_DMA_BUF
	tr_dma_unmap(dst_item);
SRC_DMA_UNMAP:
	tr_dma_unmap(src_item);
FREE_DST:
	kfree(dst_item);
FREE_SRC:
	kfree(src_item);
EXIT:
	return ret;
#else
	return ret;
#endif
}
EXPORT_SYMBOL_GPL(sunxi_tr_commit);
/*
 * sunxi_tr_query - query transform status
 * @hdl: transform handle which return by sunxi_tr_request
 * On finish, returns 0. On failure, returns ERR_PTR(-errno).
 * On busy,returns 1.
 */
int sunxi_tr_query(unsigned int id)
{
	int status = 0;
	struct sunxi_transform *tr = tr_get_by_id(id);

	if (IS_ERR_OR_NULL(tr)) {
		pr_warn("%s, hdl is invalid!\n", __func__);
		return 0;
	}
#if !defined(TR_CHECK_THREAD)
	tr_check_timeout();
#endif

	if (tr->requested || tr->busy)
		status = 1; /* busy */
	else if (tr->error)
		status = -1; /*error:timeout */
	else
		status = 0;/* finish */

	return status;

#if 0
	timeout = tr->start_time + msecs_to_jiffies(tr->timeout);
	if (time_after_eq(jiffies, timeout)) {
		pr_warn("%s, timeout(%d ms)\n", __func__,
				jiffies_to_msecs(jiffies - tr->start_time));
		de_tr_reset();
		tr->busy = false;
		return -1;
	}

	return tr->busy?1:0;
#endif
}
EXPORT_SYMBOL_GPL(sunxi_tr_query);
/*
 * sunxi_tr_set_timeout - set transform timeout(ms)
 * @hdl: transform hdl
 * @timeout: time(ms)
 * On success, returns 0.  On failure, returns ERR_PTR(-errno).
 */
int sunxi_tr_set_timeout(unsigned int id, unsigned long timeout /* ms */)
{
	struct sunxi_transform *tr = tr_get_by_id(id);

	if (IS_ERR_OR_NULL(tr)) {
		pr_warn("%s, hdl is invalid!\n", __func__);
		return -EINVAL;
	}

	if (timeout == 0) {
		pr_warn("%s, para error(timeout=0)!\n", __func__);
		return -EINVAL;
	}
	tr->timeout = timeout;

	return 0;
}

static irqreturn_t sunxi_tr_interrupt(int irq, void *dev_id)
{
	int ret = 0;

	/* get irq status */
	ret = de_tr_irq_query();
	if (ret == 0)
		sunxi_tr_finish_procss();
	/* clear irq status */
	return IRQ_HANDLED;
}

static int tr_open(struct inode *inode, struct file *file)
{
	return 0;
}

static int tr_release(struct inode *inode, struct file *file)
{
	return 0;
}
static ssize_t tr_read(struct file *file, char __user *buf,
			size_t count, loff_t *ppos)
{
	return 0;
}

static ssize_t tr_write(struct file *file, const char __user *buf,
			size_t count, loff_t *ppos)
{
	return 0;
}

static u64 sunxi_tr_dma_mask = DMA_BIT_MASK(32);
/* static int __devinit tr_probe(struct platform_device *pdev) */
static int tr_probe(struct platform_device *pdev)
{
#if !defined(CONFIG_OF)
	struct resource	*res;
#endif
	struct sunxi_trdev *sunxi_dev = NULL;
	int ret = 0;
	int irq;

	pr_info("enter %s\n", __func__);

	dmabuf_dev = &pdev->dev;
	dmabuf_dev->dma_mask = &sunxi_tr_dma_mask;
	dmabuf_dev->coherent_dma_mask = DMA_BIT_MASK(32);
	sunxi_dev = kzalloc(sizeof(struct sunxi_trdev), GFP_KERNEL);
	if (!sunxi_dev)
		return -ENOMEM;

	/* register base */
#if !defined(CONFIG_OF)
	res = platform_get_resource(pdev, IORESOURCE_MEM, 0);
	if (!res) {
		dev_err(&pdev->dev, "get reource MEM fail\n");
		ret = -EINVAL;
		goto io_err;
	}
	sunxi_dev->base = (void __iomem *)res->start;
#else
	sunxi_dev->base = of_iomap(pdev->dev.of_node, 0);
	if (sunxi_dev->base == NULL) {
		dev_err(&pdev->dev, "unable to map transform registers\n");
		ret = -EINVAL;
		goto io_err;
	}
#endif
	/* irq */
#if !defined(CONFIG_OF)
	irq = platform_get_irq(pdev, 0);
	if (irq < 0) {
		ret = irq;
		goto io_err;
	}
#else
	irq = irq_of_parse_and_map(pdev->dev.of_node, 0);
	if (!irq)
		dev_err(&pdev->dev,
			"irq_of_parse_and_map irq fail for transform\n");
#endif
	sunxi_dev->irq = irq;
	ret = request_irq(irq, sunxi_tr_interrupt, IRQF_SHARED,
					dev_name(&pdev->dev), sunxi_dev);
	if (ret) {
		dev_err(&pdev->dev, "NO IRQ found!!!\n");
		goto iomap_err;
	}

	/* clk init */
	sunxi_dev->clk = of_clk_get(pdev->dev.of_node, 0);
	if (IS_ERR(sunxi_dev->clk))
		dev_err(&pdev->dev, "fail to get clk\n");

	gsunxi_dev = sunxi_dev;
	platform_set_drvdata(pdev, sunxi_dev);
	INIT_LIST_HEAD(&sunxi_dev->trs);
	spin_lock_init(&sunxi_dev->slock);
	mutex_init(&sunxi_dev->mlock);
	mutex_init(&id_lock);
	idr_init(&sunxi_dev->idr);
	sunxi_dev->dev = &pdev->dev;
	sunxi_dev->handles = RB_ROOT;
	dev_init = true;

	ret = sysfs_create_group(&tr_dev->kobj, &tr_attribute_group);
	if (ret < 0)
		pr_err("sysfs_create_file fail!\n");

	/* init hw */
	de_tr_set_base((uintptr_t)sunxi_dev->base);

	pr_info("exit %s\n", __func__);
	return 0;

iomap_err:
	iounmap(sunxi_dev->base);
io_err:
	kfree(sunxi_dev);

	return ret;
}

static int tr_remove(struct platform_device *pdev)
{
	struct sunxi_trdev *sunxi_dev = NULL;

	pr_info("tr_remove enter\n");

	sunxi_dev = (struct sunxi_trdev *)platform_get_drvdata(pdev);
	if (sunxi_dev && (sunxi_dev->count != 0)) {
		struct sunxi_transform *tr = NULL, *tr_tmp = NULL;
		unsigned long flags;

		pr_warn("%s(), there are still %d t'users, force release them\n",
		    __func__, sunxi_dev->count);
		spin_lock_irqsave(&sunxi_dev->slock, flags);
		list_for_each_entry_safe(tr, tr_tmp, &sunxi_dev->trs, list) {
			list_del(&tr->list);
			sunxi_dev->count--;
			kfree((void *)tr);
		}
		spin_unlock_irqrestore(&sunxi_dev->slock, flags);
		if (sunxi_dev->clk)
			clk_disable(sunxi_dev->clk);
	}
	if (sunxi_dev) {
		dev_init = false;
		free_irq(sunxi_dev->irq, sunxi_dev);
		clk_put(sunxi_dev->clk);
#if defined(CONFIG_OF)
		iounmap(sunxi_dev->base);
#endif
		kfree(sunxi_dev);
		platform_set_drvdata(pdev, NULL);

		sysfs_remove_group(&tr_dev->kobj, &tr_attribute_group);

		return 0;
	}

	return -1;
}

static int tr_suspend(struct platform_device *pdev, pm_message_t state)
{
	pr_info("enter %s\n", __func__);

	pr_info("exit %s\n", __func__);

	return 0;
}


static int tr_resume(struct platform_device *pdev)
{
	pr_info("%s\n", __func__);

	if (gsunxi_dev->count != 0)
		de_tr_init();
	pr_info("exit %s\n", __func__);

	return 0;
}

static void tr_shutdown(struct platform_device *pdev)
{

}

static long tr_ioctl(struct file *file, unsigned int cmd, unsigned long arg)
{
	unsigned long karg[4];
	unsigned long ubuffer[4] = {0};
	long ret = -1;

	if (copy_from_user((void *)karg, (void __user *)arg,
				4*sizeof(unsigned long))) {
		pr_warn("copy_from_user fail\n");
		return -EFAULT;
	}

	ubuffer[0] = *(unsigned long *)karg;
	ubuffer[1] = (*(unsigned long *)(karg+1));
	ubuffer[2] = (*(unsigned long *)(karg+2));
	ubuffer[3] = (*(unsigned long *)(karg+3));

	switch (cmd) {
	case TR_REQUEST:
	{
		/* request a chan */
		unsigned int id;

		id = sunxi_tr_request();
		TR_INFO_MSG("TR_REQUEST id:%d\n", id);
		if (put_user(id, (unsigned int __user *)ubuffer[0])) {
			pr_err("%s: put_user fail\n", __func__);
			return -EFAULT;
		}

		if (id == 0)
			ret = -EFAULT;
		else
			ret = 0;
		break;
	}

	case TR_COMMIT:
	{
		tr_info info;

		if (copy_from_user(&info, (void __user *)ubuffer[1],
				sizeof(tr_info)))	{
			pr_warn("%s, copy_from_user fail\n", __func__);
			return  -EFAULT;
		}
		ret = sunxi_tr_commit(ubuffer[0], &info);

		break;
	}

	case TR_RELEASE:
	{
		/* release a chan */
		ret = sunxi_tr_release(ubuffer[0]);
		break;
	}

	case TR_QUERY:
	{
		/* query status */
		ret = sunxi_tr_query(ubuffer[0]);
		break;
	}

	case TR_SET_TIMEOUT:
	{
		/* set timeout */
		ret = sunxi_tr_set_timeout(ubuffer[0], ubuffer[1]);
		break;
	}

	default:
		break;
	}

	return ret;
}

#ifdef CONFIG_COMPAT
static long tr_compat_ioctl32(struct file *file, unsigned int cmd,
				unsigned long arg)
{
	compat_uptr_t karg[4];
	unsigned long __user *ubuffer;
	int ret = 0;

	if (copy_from_user((void *)karg, (void __user *)arg,
		4 * sizeof(compat_uptr_t))) {
		pr_err("copy_from_user fail\n");
		return -EFAULT;
	}

	ubuffer = compat_alloc_user_space(4 * sizeof(unsigned long));
	if (!access_ok(VERIFY_WRITE, ubuffer, 4 * sizeof(unsigned long)))
		return -EFAULT;

	if (put_user(karg[0], &ubuffer[0])
			|| put_user(karg[1], &ubuffer[1])
			|| put_user(karg[2], &ubuffer[2])
			|| put_user(karg[3], &ubuffer[3])) {
		pr_err("put_user fail\n");
		return -EFAULT;
	}

	switch (cmd) {
	case TR_REQUEST:
	{
		/* request a chan */
		compat_uint_t id = 0;

		id = sunxi_tr_request();

		if (put_user(id, (compat_uint_t __user *)ubuffer[0])) {
			pr_err("%s, put tr user failed.", __func__);
			ret = -EFAULT;
		} else {
			ret = 0;
		}

		break;
	}

	case TR_COMMIT:
	{
		tr_info info;
		compat_uint_t id = karg[0];

		ret = copy_from_user(&info, (void __user *)ubuffer[1],
					sizeof(tr_info));
		if (ret) {
			pr_warn("%s, tr copy_from_user fail\n", __func__);
			return  -EFAULT;
		}

		ret = sunxi_tr_commit(id, &info);

		break;
	}

	case TR_RELEASE:
	{
		/* release a chan */
		compat_uint_t id = karg[0];

		ret = sunxi_tr_release(id);
		break;
	}

	case TR_QUERY:
	{
		/* query status */
		compat_uint_t id = karg[0];

		ret = sunxi_tr_query((unsigned long)id);
		break;
	}

	case TR_SET_TIMEOUT:
	{
		/* set timeout */
		compat_uint_t id = karg[0];
		compat_uptr_t timeout = karg[1];

		ret = sunxi_tr_set_timeout(id, (unsigned long)timeout);
		break;
	}

	default:
		break;
	}

	return ret;
}
#endif

static const struct file_operations tr_fops = {
	.owner    = THIS_MODULE,
	.open     = tr_open,
	.release  = tr_release,
	.write    = tr_write,
	.read     = tr_read,
	.unlocked_ioctl = tr_ioctl,

#ifdef CONFIG_COMPAT
	.compat_ioctl = tr_compat_ioctl32,
#endif
};

#if !defined(CONFIG_OF)
static struct platform_device tr_device = {
	.name           = "transform",
	.id             = -1,
	.num_resources  = ARRAY_SIZE(tr_resource),
	.resource       = tr_resource,
	.dev = {

	},
};
#else
static const struct of_device_id sunxi_tr_match[] = {
	{ .compatible = "allwinner,sun50i-tr", },
	{ .compatible = "allwinner,sun8iw11-tr", },
	{},
};
#endif

static struct platform_driver tr_driver = {
	.probe    = tr_probe,
	.remove   = tr_remove,
	.suspend  = tr_suspend,
	.resume   = tr_resume,
	.shutdown = tr_shutdown,
	.driver = {

		.name   = "transform",
		.owner  = THIS_MODULE,
		.of_match_table = sunxi_tr_match,
	},
};

static int __init tr_module_init(void)
{
	int ret = 0, err;

	alloc_chrdev_region(&devid, 0, 1, "transform");
	tr_cdev = cdev_alloc();
	cdev_init(tr_cdev, &tr_fops);
	tr_cdev->owner = THIS_MODULE;
	err = cdev_add(tr_cdev, devid, 1);
	if (err) {
		pr_warn("cdev_add fail\n");
		return -1;
	}

	tr_class = class_create(THIS_MODULE, "transform");
	if (IS_ERR(tr_class))	{
		pr_warn("class_create fail\n");
		return -1;
	}

	tr_dev = device_create(tr_class, NULL, devid, NULL, "transform");
#if !defined(CONFIG_OF)
	ret = platform_device_register(&tr_device);
#endif

	if (ret == 0)
		ret = platform_driver_register(&tr_driver);

	return ret;
}

static void __exit tr_module_exit(void)
{
	platform_driver_unregister(&tr_driver);
#if !defined(CONFIG_OF)
	platform_device_unregister(&tr_device);
#endif
	device_destroy(tr_class,  devid);
	class_destroy(tr_class);

	cdev_del(tr_cdev);
}

module_init(tr_module_init);
module_exit(tr_module_exit);

MODULE_AUTHOR("tyle");
MODULE_DESCRIPTION("transform driver");
MODULE_LICENSE("GPL");
MODULE_ALIAS("platform:transform");

