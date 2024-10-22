/*
* Sunxi SD/MMC host driver
*
* Copyright (C) 2015 AllWinnertech Ltd.
* Author: lixiang <lixiang@allwinnertech>
*
* This program is free software; you can redistribute it and/or modify
* it under the terms of the GNU General Public License version 2 as
* published by the Free Software Foundation.
*
* This program is distributed "as is" WITHOUT ANY WARRANTY of any
* kind, whether express or implied; without even the implied warranty
* of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*/


#include <linux/clk.h>
#include <linux/clk/sunxi.h>

#include <linux/gpio.h>
#include <linux/platform_device.h>
#include <linux/spinlock.h>
#include <linux/scatterlist.h>
#include <linux/dma-mapping.h>
#include <linux/slab.h>
#include <linux/reset.h>

#include <linux/of_address.h>
#include <linux/of_gpio.h>
#include <linux/of_platform.h>
#include <linux/stat.h>

#include <linux/mmc/host.h>
#include "sunxi-mmc.h"
#include "sunxi-mmc-debug.h"
#include "sunxi-mmc-export.h"
#include "sunxi-mmc-sun50iw1p1-2.h"


#define GPIO_BASE_ADDR	0x1c20800
/*nc platform no use these value*/
#define CCMU_BASE_ADDR_BEFORE_V2P1H	0x1c20000

#define SUNXI_MMC_MAX_HOST_PRT_ADDR  0x150
#define SUNXI_MMC_MAX_GPIO_PRT_ADDR	0x120
#define SUNXI_GPIOIC_PRT_EADDR	0x380
#define SUNXI_GPIOIC_PRT_SADDR	0x200

/*mmc bus clock gating register*/
#define SUNXI_BCLKG_SADDR  0x60
#define SUNXI_BCLKG_EADDR	0x80

/*mmc moudule clock register*/
#define SUNXI_CLK_PRT_SADDR  0x80
#define SUNXI_CLK_PRT_EADDR	0xa0

/*mmc bus soft reset register*/
#define SUNXI_BSRES_SADDR  0x2C0
#define SUNXI_BSRES_EADDR	0x2DC


/*NC mmc bus gating,reset,moudule clouck register*/
#define SUNXI_NCCM_EADDR	0x850
#define SUNXI_NCCM_SADDR	0x830

/*NC mmc PLL PERI register*/
#define SUNXI_PP_NCM_EADDR	0x2C
#define SUNXI_PP_NCM_SADDR	0x20

#define SUNXI_DEG_MAX_MAP_REG	0x900

static struct device_attribute dump_register[3];

void sunxi_mmc_dumphex32(struct sunxi_mmc_host *host, char *name, char *base,
			 int len)
{
	u32 i;

	pr_cont("dump %s registers:", name);
	for (i = 0; i < len; i += 4) {
		if (!(i&0xf))
			pr_cont("\n0x%p : ", base + i);
		pr_cont("0x%08x ", __raw_readl(host->reg_base+i));
	}
	pr_cont("\n");
}

void sunxi_mmc_dump_des(struct sunxi_mmc_host *host, char *base, int len)
{
	u32 i;

	pr_cont("dump des mem\n");
	for (i = 0; i < len; i += 4) {
		if (!(i&0xf))
			pr_cont("\n0x%p : ", base + i);
		pr_cont("0x%08x ", *(u32 *)(base+i));
	}
	pr_cont("\n");
}

static ssize_t maual_insert_show(struct device *dev,
				 struct device_attribute *attr, char *buf)
{
	int ret;

	ret =
	    snprintf(buf, PAGE_SIZE,
		     "Usage: \"echo 1 > insert\" to scan card\n");
	return ret;
}

static ssize_t maual_insert_store(struct device *dev,
				  struct device_attribute *attr,
				  const char *buf, size_t count)
{
	int ret;

	unsigned long insert = 0;
	struct platform_device *pdev = to_platform_device(dev);
	struct mmc_host *mmc = platform_get_drvdata(pdev);

	ret = kstrtoul(buf, 0, &insert);
	if (ret) {
		ret = -EINVAL;
		return ret;
	}

	dev_info(dev, "insert %ld\n", insert);
	if (insert)
		mmc_detect_change(mmc, 0);
	else
		dev_info(dev, "no detect change\n");

	ret = count;
	return ret;
}

int sunxi_mmc_res_start_addr(const char * const res_str,
		resource_size_t *res_addr)
{
	struct device_node *np = NULL;
	int ret = 0;
	struct resource res;

	if (res_str == NULL || res_addr == NULL) {
		pr_err("input arg is error\n");
		return -EINVAL;
	}

	np = of_find_node_by_type(NULL, res_str);
	if (IS_ERR(np)) {
		pr_err("Can not find device type\n");
		return -EINVAL;
	}

	ret = of_address_to_resource(np, 0, &res);
	if (ret || !res.start) {
		pr_err("Can not find resouce\n");
		return -EINVAL;
	}
	*res_addr = res.start;

	return 0;
}

void sunxi_dump_reg(struct mmc_host *mmc)
{
	int i = 0;
	struct sunxi_mmc_host *host = mmc_priv(mmc);
	int ret = 0;
	void __iomem *gpio_ptr =  NULL;
	void __iomem *ccmu_ptr =  NULL;
	resource_size_t res_saddr_ccmu;
	resource_size_t res_saddr_gpio;

	ret = sunxi_mmc_res_start_addr("clocks", &res_saddr_ccmu);
	if (ret < 0)
		return;
	ccmu_ptr = ioremap(res_saddr_ccmu, SUNXI_DEG_MAX_MAP_REG);
	if (ccmu_ptr == NULL) {
		pr_err("Can not map ccmu resource\n");
		return;
	}

	ret = sunxi_mmc_res_start_addr("pio", &res_saddr_gpio);
	if (ret < 0)
		return;
	gpio_ptr = ioremap(res_saddr_gpio, SUNXI_DEG_MAX_MAP_REG);
	if (gpio_ptr == NULL) {
		pr_err("Can not map gpio resource\n");
		return;
	}

	pr_cont("Dump %s (p%x) regs :\n", mmc_hostname(mmc), host->phy_index);
	for (i = 0; i < SUNXI_MMC_MAX_HOST_PRT_ADDR; i += 4) {
		if (!(i&0xf))
			pr_cont("\n0x%p : ", (host->reg_base + i));
		pr_cont("%08x ", readl(host->reg_base + i));
	}
	pr_cont("\n");


	pr_cont("Dump gpio regs:\n");
	for (i = 0; i < SUNXI_MMC_MAX_GPIO_PRT_ADDR; i += 4) {
		if (!(i&0xf))
			pr_cont("\n0x%p : ", (gpio_ptr + i));
		pr_cont("%08x ", readl(gpio_ptr + i));
	}
	pr_cont("\n");

	pr_cont("Dump gpio irqc regs:\n");
	for (i = SUNXI_GPIOIC_PRT_SADDR; i < SUNXI_GPIOIC_PRT_EADDR; i += 4) {
		if (!(i&0xf))
			pr_cont("\n0x%p : ", (gpio_ptr + i));
		pr_cont("%08x ", readl(gpio_ptr + i));
	}
	pr_cont("\n");

	if (res_saddr_ccmu == CCMU_BASE_ADDR_BEFORE_V2P1H) {
		pr_cont("Dump ccmu regs:gating\n");
		for (i = SUNXI_BCLKG_SADDR; i < SUNXI_BCLKG_EADDR; i += 4) {
			if (!(i&0xf))
				pr_cont("\n0x%p : ", (ccmu_ptr + i));
			pr_cont("%08x ", readl(ccmu_ptr + i));
		}
		pr_cont("\n");

		pr_cont("Dump ccmu regs:module clk\n");
		for (i = SUNXI_CLK_PRT_SADDR; i < SUNXI_CLK_PRT_EADDR; i += 4) {
			if (!(i&0xf))
				pr_cont("\n0x%p : ", (ccmu_ptr + i));
			pr_cont("%08x ", readl(ccmu_ptr + i));
		}
		pr_cont("\n");

		pr_cont("Dump ccmu regs:reset\n");
		for (i = SUNXI_BSRES_SADDR; i < SUNXI_BSRES_EADDR; i += 4) {
			if (!(i&0xf))
				pr_cont("\n0x%p : ", (ccmu_ptr + i));
			pr_cont("%08x ", readl(ccmu_ptr + i));
		}
		pr_cont("\n");
	} else {
		pr_cont("Dump ccmu regs:pll,gating,reset,module clk\n");

		for (i = SUNXI_PP_NCM_SADDR; i < SUNXI_PP_NCM_EADDR; i += 4) {
			if (!(i&0xf))
				pr_cont("\n0x%p : ", (ccmu_ptr + i));
			pr_cont("%08x ", readl(ccmu_ptr + i));
		}
		pr_cont("\n");

		for (i = SUNXI_NCCM_SADDR; i < SUNXI_NCCM_EADDR; i += 4) {
			if (!(i&0xf))
				pr_cont("\n0x%p : ", (ccmu_ptr + i));
			pr_cont("%08x ", readl(ccmu_ptr + i));
		}
		pr_cont("\n");
	}

	iounmap(gpio_ptr);
	iounmap(ccmu_ptr);

}

static ssize_t dump_host_reg_show(struct device *dev,
				  struct device_attribute *attr, char *buf)
{
	char *p = buf;
	int i = 0;
	struct platform_device *pdev = to_platform_device(dev);
	struct mmc_host *mmc = platform_get_drvdata(pdev);
	struct sunxi_mmc_host *host = mmc_priv(mmc);

	p += sprintf(p, "Dump sdmmc regs:\n");
	for (i = 0; i < SUNXI_MMC_MAX_HOST_PRT_ADDR; i += 4) {
		if (!(i&0xf))
			p += sprintf(p, "\n0x%p : ", (host->reg_base + i));
		p += sprintf(p, "%08x ", readl(host->reg_base + i));
	}
	p += sprintf(p, "\n");

	return p - buf;

}

static ssize_t dump_gpio_reg_show(struct device *dev,
				  struct device_attribute *attr, char *buf)
{
	char *p = buf;
	int i = 0;
	void __iomem *gpio_ptr =  NULL;
	resource_size_t res_saddr_gpio;
	int ret = 0;

	ret = sunxi_mmc_res_start_addr("pio", &res_saddr_gpio);
	if (ret < 0)
		goto out;

	gpio_ptr = ioremap(res_saddr_gpio, SUNXI_DEG_MAX_MAP_REG);
	if (!gpio_ptr) {
		pr_err("Can not map gpio resource\n");
		goto out;
	}

	p += sprintf(p, "Dump gpio regs:\n");
	for (i = 0; i < SUNXI_MMC_MAX_GPIO_PRT_ADDR; i += 4) {
		if (!(i&0xf))
			p += sprintf(p, "\n0x%p : ", (gpio_ptr + i));
		p += sprintf(p, "%08x ", readl(gpio_ptr + i));
	}
	p += sprintf(p, "\n");

	p += sprintf(p, "Dump gpio irqc regs:\n");
	for (i = SUNXI_GPIOIC_PRT_SADDR; i < SUNXI_GPIOIC_PRT_EADDR; i += 4) {
		if (!(i&0xf))
			p += sprintf(p, "\n0x%p : ", (gpio_ptr + i));
		p += sprintf(p, "%08x ", readl(gpio_ptr + i));
	}
	p += sprintf(p, "\n");

	iounmap(gpio_ptr);
out:
	return p-buf;

}

static ssize_t dump_ccmu_reg_show(struct device *dev,
				  struct device_attribute *attr, char *buf)
{
	char *p = buf;
	int i = 0;
	void __iomem *ccmu_ptr =  NULL;
	int ret = 0;
	resource_size_t res_saddr_ccmu;

	ret = sunxi_mmc_res_start_addr("clocks", &res_saddr_ccmu);
	if (ret < 0)
		goto out;

	ccmu_ptr = ioremap(res_saddr_ccmu, SUNXI_DEG_MAX_MAP_REG);
	if (!ccmu_ptr) {
		pr_err("Can not map ccmu resource\n");
		goto out;
	}

	p += sprintf(p, "Dump ccmu\n");
	if (res_saddr_ccmu == CCMU_BASE_ADDR_BEFORE_V2P1H) {

		p += sprintf(p, "Dump ccmu regs:gating\n");
		for (i = SUNXI_BCLKG_SADDR; i < SUNXI_BCLKG_EADDR; i += 4) {
			if (!(i&0xf))
				p += sprintf(p, "\n0x%p : ", (ccmu_ptr + i));
			p += sprintf(p, "%08x ", readl(ccmu_ptr + i));
		}
		p += sprintf(p, "\n");

		p += sprintf(p, "Dump ccmu regs:module clk\n");
		for (i = SUNXI_CLK_PRT_SADDR; i < SUNXI_CLK_PRT_EADDR; i += 4) {
			if (!(i&0xf))
				p += sprintf(p, "\n0x%p : ", (ccmu_ptr + i));
			p += sprintf(p, "%08x ", readl(ccmu_ptr + i));
		}
		p += sprintf(p, "\n");

		p += sprintf(p, "Dump ccmu regs:reset\n");
		for (i = SUNXI_BSRES_SADDR; i < SUNXI_BSRES_EADDR; i += 4) {
			if (!(i&0xf))
				p += sprintf(p, "\n0x%p : ", (ccmu_ptr + i));
			p += sprintf(p, "%08x ", readl(ccmu_ptr + i));
		}
		p += sprintf(p, "\n");

	} else {
		p += sprintf(p, "Dump ccmu regs:pll,gating,reset,module clk\n");

		for (i = SUNXI_PP_NCM_SADDR; i < SUNXI_PP_NCM_EADDR; i += 4) {
			if (!(i&0xf))
				p += sprintf(p, "\n0x%p : ", (ccmu_ptr + i));
			p += sprintf(p, "%08x ", readl(ccmu_ptr + i));
		}
		p += sprintf(p, "\n");

		for (i = SUNXI_NCCM_SADDR; i < SUNXI_NCCM_EADDR; i += 4) {
			if (!(i&0xf))
				p += sprintf(p, "\n0x%p : ", (ccmu_ptr + i));
			p += sprintf(p, "%08x ", readl(ccmu_ptr + i));
		}
		p += sprintf(p, "\n");
	}
	p += sprintf(p, "\n");

	iounmap(ccmu_ptr);

out:
	return p-buf;

}

static ssize_t dump_clk_dly_show(struct device *dev,
				 struct device_attribute *attr, char *buf)
{
	char *p = buf;
	struct platform_device *pdev = to_platform_device(dev);
	struct mmc_host *mmc = platform_get_drvdata(pdev);
	struct sunxi_mmc_host *host = mmc_priv(mmc);

	if (host->sunxi_mmc_dump_dly_table)
		host->sunxi_mmc_dump_dly_table(host);
	else
		dev_warn(mmc_dev(mmc), "not found the dump dly table\n");

	return p - buf;
}

int mmc_create_sys_fs(struct sunxi_mmc_host *host, struct platform_device *pdev)
{
	int ret;

	host->maual_insert.show = maual_insert_show;
	host->maual_insert.store = maual_insert_store;
	sysfs_attr_init(&(host->maual_insert.attr));
	host->maual_insert.attr.name = "sunxi_insert";
	host->maual_insert.attr.mode = S_IRUGO | S_IWUSR;
	ret = device_create_file(&pdev->dev, &host->maual_insert);
	if (ret)
		return ret;

	host->dump_register = dump_register;
	host->dump_register[0].show = dump_host_reg_show;
	sysfs_attr_init(&(host->dump_register[0].attr));
	host->dump_register[0].attr.name = "sunxi_dump_host_register";
	host->dump_register[0].attr.mode = S_IRUGO;
	ret = device_create_file(&pdev->dev, &host->dump_register[0]);
	if (ret)
		return ret;

	host->dump_register[1].show = dump_gpio_reg_show;
	sysfs_attr_init(&(host->dump_register[1].attr));
	host->dump_register[1].attr.name = "sunxi_dump_gpio_register";
	host->dump_register[1].attr.mode = S_IRUGO;
	ret = device_create_file(&pdev->dev, &host->dump_register[1]);
	if (ret)
		return ret;

	host->dump_register[2].show = dump_ccmu_reg_show;
	sysfs_attr_init(&(host->dump_register[2].attr));
	host->dump_register[2].attr.name = "sunxi_dump_ccmu_register";
	host->dump_register[2].attr.mode = S_IRUGO;
	ret = device_create_file(&pdev->dev, &host->dump_register[2]);
	if (ret)
		return ret;

	host->dump_clk_dly.show = dump_clk_dly_show;
	sysfs_attr_init(&(host->dump_clk_dly.attr));
	host->dump_clk_dly.attr.name = "sunxi_dump_clk_dly";
	host->dump_clk_dly.attr.mode = S_IRUGO;
	ret = device_create_file(&pdev->dev, &host->dump_clk_dly);
	if (ret)
		return ret;

	return ret;
}

void mmc_remove_sys_fs(struct sunxi_mmc_host *host,
		       struct platform_device *pdev)
{
	device_remove_file(&pdev->dev, &host->maual_insert);
	device_remove_file(&pdev->dev, &host->dump_register[0]);
	device_remove_file(&pdev->dev, &host->dump_register[1]);
	device_remove_file(&pdev->dev, &host->dump_register[2]);
	device_remove_file(&pdev->dev, &host->dump_clk_dly);
}
