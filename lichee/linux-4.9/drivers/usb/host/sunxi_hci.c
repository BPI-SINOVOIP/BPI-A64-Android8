/**
 * drivers/usb/host/sunxi_hci.c
 * (C) Copyright 2010-2015
 * Allwinner Technology Co., Ltd. <www.allwinnertech.com>
 * yangnaitian, 2011-5-24, create this file
 * javen, 2011-7-18, add clock and power switch
 *
 * sunxi HCI Driver
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License, or (at your option) any later version.
 *
 */

#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/delay.h>
#include <linux/ioport.h>
#include <linux/sched.h>
#include <linux/slab.h>
#include <linux/errno.h>
#include <linux/init.h>
#include <linux/timer.h>
#include <linux/list.h>
#include <linux/interrupt.h>
#include <linux/platform_device.h>
#include <linux/clk.h>
#include <linux/gpio.h>

#include <linux/debugfs.h>
#include <linux/seq_file.h>
#include <linux/dma-mapping.h>

#include <asm/byteorder.h>
#include <asm/io.h>
#include <asm/unaligned.h>
#include <linux/regulator/consumer.h>
#include  <linux/of.h>
#include  <linux/of_address.h>
#include  <linux/of_device.h>

#if defined(CONFIG_AW_AXP)
#include <linux/power/axp_depend.h>
#endif

#include  "sunxi_hci.h"

static u64 sunxi_hci_dmamask = DMA_BIT_MASK(32);
static DEFINE_MUTEX(usb_passby_lock);
static DEFINE_MUTEX(usb_vbus_lock);
static DEFINE_MUTEX(usb_clock_lock);

#ifndef CONFIG_OF
static char *usbc_name[4] = {"usbc0", "usbc1", "usbc2", "usbc3"};
#endif

#ifdef CONFIG_USB_SUNXI_USB_MANAGER
int usb_otg_id_status(void);
#endif

static struct sunxi_hci_hcd sunxi_ohci0;
static struct sunxi_hci_hcd sunxi_ohci1;
static struct sunxi_hci_hcd sunxi_ohci2;
static struct sunxi_hci_hcd sunxi_ohci3;
static struct sunxi_hci_hcd sunxi_ehci0;
static struct sunxi_hci_hcd sunxi_ehci1;
static struct sunxi_hci_hcd sunxi_ehci2;
static struct sunxi_hci_hcd sunxi_ehci3;
static struct sunxi_hci_hcd sunxi_xhci;

#define  USBPHYC_REG_o_PHYCTL		    0x0404

atomic_t usb1_set_vbus_cnt = ATOMIC_INIT(0);
atomic_t usb2_set_vbus_cnt = ATOMIC_INIT(0);
atomic_t usb3_set_vbus_cnt = ATOMIC_INIT(0);
atomic_t usb4_set_vbus_cnt = ATOMIC_INIT(0);

atomic_t usb1_enable_passly_cnt = ATOMIC_INIT(0);
atomic_t usb2_enable_passly_cnt = ATOMIC_INIT(0);
atomic_t usb3_enable_passly_cnt = ATOMIC_INIT(0);
atomic_t usb4_enable_passly_cnt = ATOMIC_INIT(0);

static s32 request_usb_regulator_io(struct sunxi_hci_hcd *sunxi_hci)
{
	if (sunxi_hci->regulator_io != NULL) {
		sunxi_hci->regulator_io_hdle =
				regulator_get(NULL, sunxi_hci->regulator_io);
		if (IS_ERR(sunxi_hci->regulator_io_hdle)) {
			DMSG_PANIC("ERR: some error happen, %s,regulator_io_hdle fail to get regulator!", sunxi_hci->hci_name);
			sunxi_hci->regulator_io_hdle = NULL;
			return 0;
		}
	}

	if (sunxi_hci->hsic_flag) {
		if (sunxi_hci->hsic_regulator_io != NULL) {
			sunxi_hci->hsic_regulator_io_hdle =
					regulator_get(NULL, sunxi_hci->hsic_regulator_io);
			if (IS_ERR(sunxi_hci->hsic_regulator_io_hdle)) {
				DMSG_PANIC("ERR: some error happen, %s, hsic_regulator_io_hdle fail to get regulator!", sunxi_hci->hci_name);
				sunxi_hci->hsic_regulator_io_hdle = NULL;
				return 0;
			}
		}
	}

	return 0;
}

static s32 release_usb_regulator_io(struct sunxi_hci_hcd *sunxi_hci)
{
	if (sunxi_hci->regulator_io != NULL)
		regulator_put(sunxi_hci->regulator_io_hdle);

	if (sunxi_hci->hsic_flag) {
		if (sunxi_hci->hsic_regulator_io != NULL)
			regulator_put(sunxi_hci->hsic_regulator_io_hdle);
	}

	return 0;
}

void __iomem *usb_phy_csr_add(struct sunxi_hci_hcd *sunxi_hci)
{
	return (sunxi_hci->otg_vbase + SUNXI_OTG_PHY_CTRL);
}

void __iomem *usb_phy_csr_read(struct sunxi_hci_hcd *sunxi_hci)
{
	switch (sunxi_hci->usbc_no) {
	case 0:
		return sunxi_hci->otg_vbase + SUNXI_OTG_PHY_STATUS;

	case 1:
		return sunxi_hci->usb_vbase + SUNXI_HCI_UTMI_PHY_STATUS;

	case 2:
		return sunxi_hci->usb_vbase + SUNXI_HCI_UTMI_PHY_STATUS;

	case 3:
		return sunxi_hci->usb_vbase + SUNXI_HCI_UTMI_PHY_STATUS;

	default:
		DMSG_PANIC("usb_phy_csr_read is failed in %d index\n",
			sunxi_hci->usbc_no);
		break;
	}

	return NULL;
}

void __iomem *usb_phy_csr_write(struct sunxi_hci_hcd *sunxi_hci)
{
	switch (sunxi_hci->usbc_no) {
	case 0:
		return sunxi_hci->otg_vbase + SUNXI_OTG_PHY_CTRL;

	case 1:
		return sunxi_hci->usb_vbase + SUNXI_HCI_PHY_CTRL;

	case 2:
		return sunxi_hci->usb_vbase + SUNXI_HCI_PHY_CTRL;

	case 3:
		return sunxi_hci->usb_vbase + SUNXI_HCI_PHY_CTRL;

	default:
		DMSG_PANIC("usb_phy_csr_write is failed in %d index\n",
			sunxi_hci->usbc_no);
		break;
	}

	return NULL;
}

int usb_phyx_tp_write(struct sunxi_hci_hcd *sunxi_hci,
		int addr, int data, int len)
{
	int temp = 0;
	int j = 0;
	int reg_value = 0;
	int reg_temp = 0;
	int dtmp = 0;

	if (sunxi_hci->otg_vbase == NULL) {
		DMSG_PANIC("%s,otg_vbase is null\n", __func__);
		return -1;
	}

	if (usb_phy_csr_add(sunxi_hci) == NULL) {
		DMSG_PANIC("%s,phy_csr_add is null\n", __func__);
		return -1;
	}

	if (usb_phy_csr_write(sunxi_hci) == NULL) {

		DMSG_PANIC("%s,phy_csr_write is null\n", __func__);
		return -1;
	}

	reg_value = USBC_Readl(sunxi_hci->otg_vbase + SUNXI_OTG_PHY_CFG);
	reg_temp = reg_value;
	reg_value |= 0x01;
	USBC_Writel(reg_value, (sunxi_hci->otg_vbase + SUNXI_OTG_PHY_CFG));

	dtmp = data;
	for (j = 0; j < len; j++) {
		USBC_Writeb(addr + j, usb_phy_csr_add(sunxi_hci) + 1);

		temp = USBC_Readb(usb_phy_csr_write(sunxi_hci));
		temp &= ~(0x1 << 0);
		USBC_Writeb(temp, usb_phy_csr_write(sunxi_hci));

		temp = USBC_Readb(usb_phy_csr_add(sunxi_hci));
		temp &= ~(0x1 << 7);
		temp |= (dtmp & 0x1) << 7;
		USBC_Writeb(temp, usb_phy_csr_add(sunxi_hci));

		temp = USBC_Readb(usb_phy_csr_write(sunxi_hci));
		temp |= (0x1 << 0);
		USBC_Writeb(temp, usb_phy_csr_write(sunxi_hci));

		temp = USBC_Readb(usb_phy_csr_write(sunxi_hci));
		temp &= ~(0x1 << 0);
		USBC_Writeb(temp, usb_phy_csr_write(sunxi_hci));

		dtmp >>= 1;
	}

	USBC_Writel(reg_temp, (sunxi_hci->otg_vbase + SUNXI_OTG_PHY_CFG));
	return 0;
}
EXPORT_SYMBOL(usb_phyx_tp_write);

int usb_phyx_tp_read(struct sunxi_hci_hcd *sunxi_hci, int addr, int len)
{
	int temp = 0;
	int i = 0;
	int j = 0;
	int ret = 0;
	int reg_value = 0;
	int reg_temp = 0;

	if (sunxi_hci->otg_vbase == NULL) {
		DMSG_PANIC("%s,otg_vbase is null\n", __func__);
		return -1;
	}

	if (usb_phy_csr_add(sunxi_hci) == NULL) {
		DMSG_PANIC("%s,phy_csr_add is null\n", __func__);
		return -1;
	}

	if (usb_phy_csr_read(sunxi_hci) == NULL) {
		DMSG_PANIC("%s,phy_csr_read is null\n", __func__);
		return -1;
	}

	reg_value = USBC_Readl(sunxi_hci->otg_vbase + SUNXI_OTG_PHY_CFG);
	reg_temp = reg_value;
	reg_value |= 0x01;
	USBC_Writel(reg_value, (sunxi_hci->otg_vbase + SUNXI_OTG_PHY_CFG));

	for (j = len; j > 0; j--) {
		USBC_Writeb((addr + j - 1), usb_phy_csr_add(sunxi_hci) + 1);

		for (i = 0; i < 0x4; i++)
			;

		temp = USBC_Readb(usb_phy_csr_read(sunxi_hci));
		ret <<= 1;
		ret |= (temp & 0x1);
	}

	USBC_Writel(reg_temp, (sunxi_hci->otg_vbase + SUNXI_OTG_PHY_CFG));

	return ret;
}
EXPORT_SYMBOL(usb_phyx_tp_read);

#if defined(CONFIG_ARCH_SUN8IW12) || defined(CONFIG_ARCH_SUN50IW3) \
	|| defined(CONFIG_ARCH_SUN50IW6)
static void usb_hci_phy_txtune(struct sunxi_hci_hcd *sunxi_hci)
{
	int reg_value = 0;

	reg_value = USBC_Readl(sunxi_hci->usb_vbase + SUNXI_HCI_PHY_TUNE);
	reg_value |= 0x03 << 2;	/* TXRESTUNE */
	reg_value &= ~(0xf << 8);
	reg_value |= 0xc << 8;	/* TXVREFTUNE */
	USBC_Writel(reg_value, (sunxi_hci->usb_vbase + SUNXI_HCI_PHY_TUNE));
}
#endif

static void USBC_SelectPhyToHci(struct sunxi_hci_hcd *sunxi_hci)
{
	int reg_value = 0;

	reg_value = USBC_Readl(sunxi_hci->otg_vbase + SUNXI_OTG_PHY_CFG);
	reg_value &= ~(0x01);
	USBC_Writel(reg_value, (sunxi_hci->otg_vbase + SUNXI_OTG_PHY_CFG));
}

static void USBC_Clean_SIDDP(struct sunxi_hci_hcd *sunxi_hci)
{
	int reg_value = 0;

	reg_value = USBC_Readl(sunxi_hci->usb_vbase + SUNXI_HCI_PHY_CTRL);
	reg_value &= ~(0x01 << SUNXI_HCI_PHY_CTRL_SIDDQ);
	USBC_Writel(reg_value, (sunxi_hci->usb_vbase + SUNXI_HCI_PHY_CTRL));
}

static int open_clock(struct sunxi_hci_hcd *sunxi_hci, u32 ohci)
{
	mutex_lock(&usb_clock_lock);

	/*
	 * otg and hci share the same phy in fpga,
	 * so need switch phy to hci here.
	 * Notice: not need any more on new platforms.
	 */

	/* otg and hci0 Controller Shared phy in SUN50I */
	if (sunxi_hci->usbc_no == HCI0_USBC_NO)
		USBC_SelectPhyToHci(sunxi_hci);

	/* To fix hardware design issue. */
#if defined(CONFIG_ARCH_SUN8IW12) || defined(CONFIG_ARCH_SUN50IW3) \
	|| defined(CONFIG_ARCH_SUN50IW6)
	usb_hci_phy_txtune(sunxi_hci);
#endif

	if (sunxi_hci->ahb &&
			sunxi_hci->mod_usbphy &&
			!sunxi_hci->clk_is_open) {
		sunxi_hci->clk_is_open = 1;
		if (clk_prepare_enable(sunxi_hci->ahb))
			DMSG_PANIC("ERR:try to prepare_enable %s_ahb failed!\n",
				sunxi_hci->hci_name);
		udelay(10);

		if (sunxi_hci->hsic_flag) {
			if (sunxi_hci->hsic_ctrl_flag) {
				if (sunxi_hci->hsic_enable_flag) {
					if (clk_prepare_enable(sunxi_hci->pll_hsic))
						DMSG_PANIC("ERR:try to prepare_enable %s pll_hsic failed!\n",
							sunxi_hci->hci_name);

					if (clk_prepare_enable(sunxi_hci->clk_usbhsic12m))
						DMSG_PANIC("ERR:try to prepare_enable %s clk_usbhsic12m failed!\n",
							sunxi_hci->hci_name);

					if (clk_prepare_enable(sunxi_hci->hsic_usbphy))
						DMSG_PANIC("ERR:try to prepare_enable %s_hsic_usbphy failed!\n",
							sunxi_hci->hci_name);
				}
			} else {
				if (clk_prepare_enable(sunxi_hci->pll_hsic))
					DMSG_PANIC("ERR:try to prepare_enable %s pll_hsic failed!\n",
						sunxi_hci->hci_name);

				if (clk_prepare_enable(sunxi_hci->clk_usbhsic12m))
					DMSG_PANIC("ERR:try to prepare_enable %s clk_usbhsic12m failed!\n",
						sunxi_hci->hci_name);

				if (clk_prepare_enable(sunxi_hci->hsic_usbphy))
					DMSG_PANIC("ERR:try to prepare_enable %s_hsic_usbphy failed!\n",
						sunxi_hci->hci_name);
			}
		} else {
			if (clk_prepare_enable(sunxi_hci->mod_usbphy))
				DMSG_PANIC("ERR:try to prepare_enable %s_usbphy failed!\n",
					sunxi_hci->hci_name);
		}

		udelay(10);

	} else {
		DMSG_PANIC("[%s]: wrn: open clock failed, (0x%p, 0x%p, %d, 0x%p)\n",
			sunxi_hci->hci_name,
			sunxi_hci->ahb,
			sunxi_hci->mod_usbphy,
			sunxi_hci->clk_is_open,
			sunxi_hci->mod_usb);
	}

	USBC_Clean_SIDDP(sunxi_hci);
#if !defined(CONFIG_ARCH_SUN8IW12) && !defined(CONFIG_ARCH_SUN50IW3) \
	&& !defined(CONFIG_ARCH_SUN8IW6) && !defined(CONFIG_ARCH_SUN50IW6)
	usb_phyx_tp_write(sunxi_hci, 0x2a, 3, 2);
#endif
	mutex_unlock(&usb_clock_lock);

	return 0;
}

static int close_clock(struct sunxi_hci_hcd *sunxi_hci, u32 ohci)
{
	if (sunxi_hci->ahb &&
			sunxi_hci->mod_usbphy &&
			sunxi_hci->clk_is_open) {
		sunxi_hci->clk_is_open = 0;

		if (sunxi_hci->hsic_flag) {
			if (sunxi_hci->hsic_ctrl_flag) {
				if (sunxi_hci->hsic_enable_flag) {
					clk_disable_unprepare(sunxi_hci->clk_usbhsic12m);
					clk_disable_unprepare(sunxi_hci->hsic_usbphy);
					clk_disable_unprepare(sunxi_hci->pll_hsic);
				}
			} else {
				clk_disable_unprepare(sunxi_hci->clk_usbhsic12m);
				clk_disable_unprepare(sunxi_hci->hsic_usbphy);
				clk_disable_unprepare(sunxi_hci->pll_hsic);
			}
		} else {
			clk_disable_unprepare(sunxi_hci->mod_usbphy);
		}

		clk_disable_unprepare(sunxi_hci->ahb);
		udelay(10);
	} else {
		DMSG_PANIC("[%s]: wrn: open clock failed, (0x%p, 0x%p, %d, 0x%p)\n",
			sunxi_hci->hci_name,
			sunxi_hci->ahb,
			sunxi_hci->mod_usbphy,
			sunxi_hci->clk_is_open,
			sunxi_hci->mod_usb);
	}
	return 0;
}

static int usb_get_hsic_phy_ctrl(int value, int enable)
{
	if (enable) {
		value |= (0x07<<8);
		value |= (0x01<<1);
		value |= (0x01<<0);
		value |= (0x01<<16);
		value |= (0x01<<20);
	} else {
		value &= ~(0x07<<8);
		value &= ~(0x01<<1);
		value &= ~(0x01<<0);
		value &= ~(0x01<<16);
		value &= ~(0x01<<20);
	}

	return value;
}

static void __usb_passby(struct sunxi_hci_hcd *sunxi_hci, u32 enable,
			     atomic_t *usb_enable_passly_cnt)
{
	unsigned long reg_value = 0;

	reg_value = USBC_Readl(sunxi_hci->usb_vbase + SUNXI_USB_PMU_IRQ_ENABLE);
	if (enable && (atomic_read(usb_enable_passly_cnt) == 0)) {
		if (sunxi_hci->hsic_flag) {
			reg_value = usb_get_hsic_phy_ctrl(reg_value, enable);
		} else {
			/* AHB Master interface INCR8 enable */
			reg_value |= (1 << 10);
			/* AHB Master interface burst type INCR4 enable */
			reg_value |= (1 << 9);
			/* AHB Master interface INCRX align enable */
			reg_value |= (1 << 8);
			if (sunxi_hci->usbc_no == HCI0_USBC_NO)
#ifdef SUNXI_USB_FPGA
				/* enable ULPI, disable UTMI */
				reg_value |= (0 << 0);
#else
				/* enable UTMI, disable ULPI */
				reg_value |= (1 << 0);
#endif
			else
				/* ULPI bypass enable */
				reg_value |= (1 << 0);
		}
	} else if (!enable && (atomic_read(usb_enable_passly_cnt) == 1)) {
		if (sunxi_hci->hsic_flag) {
			reg_value = usb_get_hsic_phy_ctrl(reg_value, enable);
		} else {
			/* AHB Master interface INCR8 disable */
			reg_value &= ~(1 << 10);
			/* AHB Master interface burst type INCR4 disable */
			reg_value &= ~(1 << 9);
			/* AHB Master interface INCRX align disable */
			reg_value &= ~(1 << 8);
			/* ULPI bypass disable */
			reg_value &= ~(1 << 0);
		}
	}
	USBC_Writel(reg_value,
		(sunxi_hci->usb_vbase + SUNXI_USB_PMU_IRQ_ENABLE));

	if (enable)
		atomic_add(1, usb_enable_passly_cnt);
	else
		atomic_sub(1, usb_enable_passly_cnt);
}

static void usb_passby(struct sunxi_hci_hcd *sunxi_hci, u32 enable)
{
	spinlock_t lock;
	unsigned long flags = 0;

	mutex_lock(&usb_passby_lock);

	spin_lock_init(&lock);
	spin_lock_irqsave(&lock, flags);

	/* enable passby */
	if (sunxi_hci->usbc_no == HCI0_USBC_NO) {
		__usb_passby(sunxi_hci, enable, &usb1_enable_passly_cnt);
	} else if (sunxi_hci->usbc_no == HCI1_USBC_NO) {
		__usb_passby(sunxi_hci, enable, &usb2_enable_passly_cnt);
	} else if (sunxi_hci->usbc_no == HCI2_USBC_NO) {
		__usb_passby(sunxi_hci, enable, &usb3_enable_passly_cnt);
	} else if (sunxi_hci->usbc_no == HCI3_USBC_NO) {
		__usb_passby(sunxi_hci, enable, &usb4_enable_passly_cnt);
	} else {
		DMSG_PANIC("EER: unknown usbc_no(%d)\n", sunxi_hci->usbc_no);
		spin_unlock_irqrestore(&lock, flags);

		mutex_unlock(&usb_passby_lock);
		return;
	}

	spin_unlock_irqrestore(&lock, flags);

	mutex_unlock(&usb_passby_lock);
}

static int alloc_pin(struct sunxi_hci_hcd *sunxi_hci)
{
	u32 ret = 1;

	if (sunxi_hci->drv_vbus_gpio_valid) {
		ret = gpio_request(sunxi_hci->drv_vbus_gpio_set.gpio, NULL);
		if (ret != 0) {
			DMSG_PANIC("request %s gpio:%d\n",
				sunxi_hci->hci_name, sunxi_hci->drv_vbus_gpio_set.gpio);
		} else {
			gpio_direction_output(sunxi_hci->drv_vbus_gpio_set.gpio, 0);
		}
	}

	if (sunxi_hci->hsic_flag) {
		/* Marvell 4G HSIC ctrl */
		if (sunxi_hci->usb_host_hsic_rdy_valid) {
			ret = gpio_request(sunxi_hci->usb_host_hsic_rdy.gpio, NULL);
			if (ret != 0) {
				DMSG_PANIC("ERR: gpio_request failed\n");
				sunxi_hci->usb_host_hsic_rdy_valid = 0;
			} else {
				gpio_direction_output(sunxi_hci->usb_host_hsic_rdy.gpio, 0);
			}
		}

		/* SMSC usb3503 HSIC HUB ctrl */
		if (sunxi_hci->usb_hsic_usb3503_flag) {
			if (sunxi_hci->usb_hsic_hub_connect_valid) {
				ret = gpio_request(sunxi_hci->usb_hsic_hub_connect.gpio, NULL);
				if (ret != 0) {
					DMSG_PANIC("ERR: gpio_request failed\n");
					sunxi_hci->usb_hsic_hub_connect_valid = 0;
				} else {
					gpio_direction_output(sunxi_hci->usb_hsic_hub_connect.gpio, 1);
				}
			}

			if (sunxi_hci->usb_hsic_int_n_valid) {
				ret = gpio_request(sunxi_hci->usb_hsic_int_n.gpio, NULL);
				if (ret != 0) {
					DMSG_PANIC("ERR: gpio_request failed\n");
					sunxi_hci->usb_hsic_int_n_valid = 0;
				} else {
					gpio_direction_output(sunxi_hci->usb_hsic_int_n.gpio, 1);
				}
			}

			msleep(20);

			if (sunxi_hci->usb_hsic_reset_n_valid) {
				ret = gpio_request(sunxi_hci->usb_hsic_reset_n.gpio, NULL);
				if (ret != 0) {
					DMSG_PANIC("ERR: gpio_request failed\n");
					sunxi_hci->usb_hsic_reset_n_valid = 0;
				} else {
					gpio_direction_output(sunxi_hci->usb_hsic_reset_n.gpio, 1);
				}
			}

			/**
			 * usb3503 device goto hub connect status
			 * is need 100ms after reset
			 */
			msleep(100);
		}
	}

	return 0;
}

static void free_pin(struct sunxi_hci_hcd *sunxi_hci)
{
	if (sunxi_hci->drv_vbus_gpio_valid) {
		gpio_free(sunxi_hci->drv_vbus_gpio_set.gpio);
		sunxi_hci->drv_vbus_gpio_valid = 0;
	}

	if (sunxi_hci->hsic_flag) {
		/* Marvell 4G HSIC ctrl */
		if (sunxi_hci->usb_host_hsic_rdy_valid) {
			gpio_free(sunxi_hci->usb_host_hsic_rdy.gpio);
			sunxi_hci->usb_host_hsic_rdy_valid = 0;
		}

		/* SMSC usb3503 HSIC HUB ctrl */
		if (sunxi_hci->usb_hsic_usb3503_flag) {
			if (sunxi_hci->usb_hsic_hub_connect_valid) {
				gpio_free(sunxi_hci->usb_hsic_hub_connect.gpio);
				sunxi_hci->usb_hsic_hub_connect_valid = 0;
			}

			if (sunxi_hci->usb_hsic_int_n_valid) {
				gpio_free(sunxi_hci->usb_hsic_int_n.gpio);
				sunxi_hci->usb_hsic_int_n_valid = 0;
			}

			if (sunxi_hci->usb_hsic_reset_n_valid) {
				gpio_free(sunxi_hci->usb_hsic_reset_n.gpio);
				sunxi_hci->usb_hsic_reset_n_valid = 0;
			}
		}
	}
}

void sunxi_set_host_hisc_rdy(struct sunxi_hci_hcd *sunxi_hci, int is_on)
{
	if (sunxi_hci->usb_host_hsic_rdy_valid) {
		/* set config, output */
		gpio_direction_output(sunxi_hci->usb_host_hsic_rdy.gpio, is_on);
	}
}
EXPORT_SYMBOL(sunxi_set_host_hisc_rdy);

void sunxi_set_host_vbus(struct sunxi_hci_hcd *sunxi_hci, int is_on)
{
	if (sunxi_hci->drv_vbus_type == USB_DRV_VBUS_TYPE_GIPO) {
		if (sunxi_hci->drv_vbus_gpio_valid)
			__gpio_set_value(sunxi_hci->drv_vbus_gpio_set.gpio,
					is_on);
	} else if (sunxi_hci->drv_vbus_type == USB_DRV_VBUS_TYPE_AXP) {
#if defined(CONFIG_AW_AXP)
		axp_usb_vbus_output(is_on);
#endif
	}
}
EXPORT_SYMBOL(sunxi_set_host_vbus);

static void __sunxi_set_vbus(struct sunxi_hci_hcd *sunxi_hci, int is_on)
{
	/* set power flag */
	sunxi_hci->power_flag = is_on;

	if ((sunxi_hci->regulator_io != NULL) &&
			(sunxi_hci->regulator_io_hdle != NULL)) {
		if (is_on) {
			if (regulator_enable(sunxi_hci->regulator_io_hdle) < 0)
				DMSG_INFO("%s: regulator_enable fail\n",
					sunxi_hci->hci_name);
		} else {
			if (regulator_disable(sunxi_hci->regulator_io_hdle) < 0)
				DMSG_INFO("%s: regulator_disable fail\n",
					sunxi_hci->hci_name);
		}
	}

	if (sunxi_hci->hsic_flag) {
		if ((sunxi_hci->hsic_regulator_io != NULL) &&
				(sunxi_hci->hsic_regulator_io_hdle != NULL)) {
			if (is_on) {
				if (regulator_enable(sunxi_hci->hsic_regulator_io_hdle) < 0)
					DMSG_INFO("%s: hsic_regulator_enable fail\n",
						sunxi_hci->hci_name);
			} else {
				if (regulator_disable(sunxi_hci->hsic_regulator_io_hdle) < 0)
					DMSG_INFO("%s: hsic_regulator_disable fail\n",
						sunxi_hci->hci_name);
			}
		}
	}

/**
 * No care of usb0 vbus when otg connect pc
 * setup system without battery and to return.
 */
#ifdef CONFIG_USB_SUNXI_USB_MANAGER
#if !defined(CONFIG_ARCH_SUN8IW6)
	if (sunxi_hci->usbc_no == HCI0_USBC_NO) {
		if (is_on) {
			if (usb_otg_id_status() == 1)
				return;
		}
	}
#endif
#endif

	if (sunxi_hci->drv_vbus_type == USB_DRV_VBUS_TYPE_GIPO) {
		if (sunxi_hci->drv_vbus_gpio_valid)
			__gpio_set_value(sunxi_hci->drv_vbus_gpio_set.gpio,
					is_on);
	} else if (sunxi_hci->drv_vbus_type == USB_DRV_VBUS_TYPE_AXP) {
#if defined(CONFIG_AW_AXP)
		axp_usb_vbus_output(is_on);
#endif
	}
}

static void sunxi_set_vbus(struct sunxi_hci_hcd *sunxi_hci, int is_on)
{

	DMSG_DEBUG("[%s]: sunxi_set_vbus cnt %d\n",
		sunxi_hci->hci_name,
		(sunxi_hci->usbc_no == 1) ?
			atomic_read(&usb1_set_vbus_cnt) :
			atomic_read(&usb2_set_vbus_cnt));

	mutex_lock(&usb_vbus_lock);

	if (sunxi_hci->usbc_no == HCI0_USBC_NO) {
		if (is_on && (atomic_read(&usb1_set_vbus_cnt) == 0))
			__sunxi_set_vbus(sunxi_hci, is_on);  /* power on */
		else if (!is_on && atomic_read(&usb1_set_vbus_cnt) == 1)
			__sunxi_set_vbus(sunxi_hci, is_on);  /* power off */

		if (is_on)
			atomic_add(1, &usb1_set_vbus_cnt);
		else
			atomic_sub(1, &usb1_set_vbus_cnt);
	} else if (sunxi_hci->usbc_no == HCI1_USBC_NO) {
		if (is_on && (atomic_read(&usb2_set_vbus_cnt) == 0))
			__sunxi_set_vbus(sunxi_hci, is_on);  /* power on */
		else if (!is_on && atomic_read(&usb2_set_vbus_cnt) == 1)
			__sunxi_set_vbus(sunxi_hci, is_on);  /* power off */

		if (is_on)
			atomic_add(1, &usb2_set_vbus_cnt);
		else
			atomic_sub(1, &usb2_set_vbus_cnt);
	} else if (sunxi_hci->usbc_no == HCI2_USBC_NO) {
		if (is_on && (atomic_read(&usb3_set_vbus_cnt) == 0))
			__sunxi_set_vbus(sunxi_hci, is_on);  /* power on */
		else if (!is_on && atomic_read(&usb3_set_vbus_cnt) == 1)
			__sunxi_set_vbus(sunxi_hci, is_on);  /* power off */

		if (is_on)
			atomic_add(1, &usb3_set_vbus_cnt);
		else
			atomic_sub(1, &usb3_set_vbus_cnt);
	} else if (sunxi_hci->usbc_no == HCI3_USBC_NO) {
		if (is_on && (atomic_read(&usb4_set_vbus_cnt) == 0))
			__sunxi_set_vbus(sunxi_hci, is_on);  /* power on */
		else if (!is_on && atomic_read(&usb4_set_vbus_cnt) == 1)
			__sunxi_set_vbus(sunxi_hci, is_on);  /* power off */

		if (is_on)
			atomic_add(1, &usb4_set_vbus_cnt);
		else
			atomic_sub(1, &usb4_set_vbus_cnt);
	} else {
		DMSG_INFO("[%s]: sunxi_set_vbus no: %d\n",
			sunxi_hci->hci_name, sunxi_hci->usbc_no);
	}

	mutex_unlock(&usb_vbus_lock);
}

static int sunxi_get_hci_base(struct platform_device *pdev,
		struct sunxi_hci_hcd *sunxi_hci)
{
	struct device_node *np = pdev->dev.of_node;
	struct resource res;
	int ret = 0;

	sunxi_hci->usb_vbase  = of_iomap(np, 0);
	if (sunxi_hci->usb_vbase == NULL) {
		dev_err(&pdev->dev, "%s, can't get vbase resource\n",
			sunxi_hci->hci_name);
		return -EINVAL;
	}

	sunxi_hci->otg_vbase  = of_iomap(np, 2);
	if (sunxi_hci->otg_vbase == NULL) {
		dev_err(&pdev->dev, "%s, can't get otg_vbase resource\n",
			sunxi_hci->hci_name);
		return -EINVAL;
	}

	ret = of_address_to_resource(np, 0, &res);
	if (ret)
		dev_err(&pdev->dev, "could not get regs\n");

	sunxi_hci->usb_base_res = &res;

	return 0;
}

static int sunxi_get_ohci_clock_src(struct platform_device *pdev,
		struct sunxi_hci_hcd *sunxi_hci)
{
	struct device_node *np = pdev->dev.of_node;

	sunxi_hci->clk_usbohci12m = of_clk_get(np, 2);
	if (IS_ERR(sunxi_hci->clk_usbohci12m)) {
		sunxi_hci->clk_usbohci12m = NULL;
		DMSG_INFO("%s get usb clk_usbohci12m clk failed.\n",
			sunxi_hci->hci_name);
	}

	sunxi_hci->clk_hoscx2 = of_clk_get(np, 3);
	if (IS_ERR(sunxi_hci->clk_hoscx2)) {
		sunxi_hci->clk_hoscx2 = NULL;
		DMSG_INFO("%s get usb clk_hoscx2 clk failed.\n",
			sunxi_hci->hci_name);
	}

	sunxi_hci->clk_hosc = of_clk_get(np, 4);
	if (IS_ERR(sunxi_hci->clk_hosc)) {
		sunxi_hci->clk_hosc = NULL;
		DMSG_INFO("%s get usb clk_hosc failed.\n",
			sunxi_hci->hci_name);
	}

	sunxi_hci->clk_losc = of_clk_get(np, 5);
	if (IS_ERR(sunxi_hci->clk_losc)) {
		sunxi_hci->clk_losc = NULL;
		DMSG_INFO("%s get usb clk_losc clk failed.\n",
			sunxi_hci->hci_name);
	}

	return 0;
}


static int sunxi_get_hci_clock(struct platform_device *pdev,
		struct sunxi_hci_hcd *sunxi_hci)
{
	struct device_node *np = pdev->dev.of_node;

	sunxi_hci->ahb = of_clk_get(np, 1);
	if (IS_ERR(sunxi_hci->ahb)) {
		sunxi_hci->ahb = NULL;
		DMSG_PANIC("ERR: %s get usb ahb_otg clk failed.\n",
			sunxi_hci->hci_name);
		return -EINVAL;
	}

	sunxi_hci->mod_usbphy = of_clk_get(np, 0);
	if (IS_ERR(sunxi_hci->mod_usbphy)) {
		sunxi_hci->mod_usbphy = NULL;
		DMSG_PANIC("ERR: %s get usb mod_usbphy failed.\n",
			sunxi_hci->hci_name);
		return -EINVAL;
	}

	if (sunxi_hci->hsic_flag) {
		sunxi_hci->hsic_usbphy = of_clk_get(np, 2);
		if (IS_ERR(sunxi_hci->hsic_usbphy)) {
			sunxi_hci->hsic_usbphy = NULL;
			DMSG_PANIC("ERR: %s get usb hsic_usbphy failed.\n",
				sunxi_hci->hci_name);
		}

		sunxi_hci->clk_usbhsic12m = of_clk_get(np, 3);
		if (IS_ERR(sunxi_hci->clk_usbhsic12m)) {
			sunxi_hci->clk_usbhsic12m = NULL;
			DMSG_PANIC("ERR: %s get usb clk_usbhsic12m failed.\n",
				sunxi_hci->hci_name);
		}

		sunxi_hci->pll_hsic = of_clk_get(np, 4);
		if (IS_ERR(sunxi_hci->pll_hsic)) {
			sunxi_hci->pll_hsic = NULL;
			DMSG_PANIC("ERR: %s get usb pll_hsic failed.\n",
				sunxi_hci->hci_name);
		}
	}

	return 0;
}

static int get_usb_cfg(struct platform_device *pdev,
		struct sunxi_hci_hcd *sunxi_hci)
{
	struct device_node *usbc_np = NULL;
	char np_name[10];
	int ret = -1;

	sprintf(np_name, "usbc%d", sunxi_get_hci_num(pdev));
	usbc_np = of_find_node_by_type(NULL, np_name);

	/* usbc enable */
	ret = of_property_read_string(usbc_np,
			"status", &sunxi_hci->used_status);
	if (ret) {
		DMSG_PRINT("get %s used is fail, %d\n",
			sunxi_hci->hci_name, -ret);
		sunxi_hci->used = 0;
	} else if (!strcmp(sunxi_hci->used_status, "okay")) {
		sunxi_hci->used = 1;
	} else {
		 sunxi_hci->used = 0;
	}

	/* usbc init_state */
	ret = of_property_read_u32(usbc_np,
			KEY_USB_HOST_INIT_STATE, &sunxi_hci->host_init_state);
	if (ret) {
		DMSG_PRINT("get %s init_state is fail, %d\n",
			sunxi_hci->hci_name, -ret);
	}

	sunxi_hci->hsic_flag = 0;

	if (sunxi_hci->usbc_no == HCI1_USBC_NO) {
		ret = of_property_read_u32(usbc_np,
				KEY_USB_HSIC_USBED, &sunxi_hci->hsic_flag);
		if (ret)
			sunxi_hci->hsic_flag = 0;

		if (sunxi_hci->hsic_flag) {
			if (!strncmp(sunxi_hci->hci_name,
					"ohci", strlen("ohci"))) {
				DMSG_PRINT("HSIC is no susport in %s, and to return\n",
					sunxi_hci->hci_name);
				sunxi_hci->used = 0;
				return 0;
			}

			/* hsic regulator_io */
			ret = of_property_read_string(usbc_np,
					KEY_USB_HSIC_REGULATOR_IO,
					&sunxi_hci->hsic_regulator_io);
			if (ret) {
				DMSG_PRINT("get %s, hsic_regulator_io is fail, %d\n",
					sunxi_hci->hci_name, -ret);
				sunxi_hci->hsic_regulator_io = NULL;
			} else {
				if (!strcmp(sunxi_hci->hsic_regulator_io, "nocare")) {
					DMSG_PRINT("get %s, hsic_regulator_io is no nocare\n",
						sunxi_hci->hci_name);
					sunxi_hci->hsic_regulator_io = NULL;
				}
			}

			/* Marvell 4G HSIC ctrl */
			ret = of_property_read_u32(usbc_np,
					KEY_USB_HSIC_CTRL,
					&sunxi_hci->hsic_ctrl_flag);
			if (ret) {
				DMSG_PRINT("get %s usb_hsic_ctrl is fail, %d\n",
					sunxi_hci->hci_name, -ret);
				sunxi_hci->hsic_ctrl_flag = 0;
			}
			if (sunxi_hci->hsic_ctrl_flag) {
				sunxi_hci->usb_host_hsic_rdy.gpio =
						of_get_named_gpio(usbc_np,
							KEY_USB_HSIC_RDY_GPIO, 0);
				if (gpio_is_valid(sunxi_hci->usb_host_hsic_rdy.gpio)) {
					sunxi_hci->usb_host_hsic_rdy_valid = 1;
				} else {
					sunxi_hci->usb_host_hsic_rdy_valid = 0;
					DMSG_PRINT("get %s drv_vbus_gpio is fail\n",
						sunxi_hci->hci_name);
				}
			} else {
				sunxi_hci->usb_host_hsic_rdy_valid = 0;
			}

			/* SMSC usb3503 HSIC HUB ctrl */
			ret = of_property_read_u32(usbc_np,
					"usb_hsic_usb3503_flag",
					&sunxi_hci->usb_hsic_usb3503_flag);
			if (ret) {
				DMSG_PRINT("get %s usb_hsic_usb3503_flag is fail, %d\n",
					sunxi_hci->hci_name, -ret);
				sunxi_hci->usb_hsic_usb3503_flag = 0;
			}


			if (sunxi_hci->usb_hsic_usb3503_flag) {
				sunxi_hci->usb_hsic_hub_connect.gpio =
						of_get_named_gpio(usbc_np,
							"usb_hsic_hub_connect_gpio", 0);
				if (gpio_is_valid(sunxi_hci->usb_hsic_hub_connect.gpio)) {
					sunxi_hci->usb_hsic_hub_connect_valid = 1;
				} else {
					sunxi_hci->usb_hsic_hub_connect_valid = 0;
					DMSG_PRINT("get %s usb_hsic_hub_connect is fail\n",
						sunxi_hci->hci_name);
				}


				sunxi_hci->usb_hsic_int_n.gpio =
						of_get_named_gpio(usbc_np,
							"usb_hsic_int_n_gpio", 0);
				if (gpio_is_valid(sunxi_hci->usb_hsic_int_n.gpio)) {
					sunxi_hci->usb_hsic_int_n_valid = 1;
				} else {
					sunxi_hci->usb_hsic_int_n_valid = 0;
					DMSG_PRINT("get %s usb_hsic_int_n is fail\n",
						sunxi_hci->hci_name);
				}


				sunxi_hci->usb_hsic_reset_n.gpio =
						of_get_named_gpio(usbc_np,
							"usb_hsic_reset_n_gpio", 0);
				if (gpio_is_valid(sunxi_hci->usb_hsic_reset_n.gpio)) {
					sunxi_hci->usb_hsic_reset_n_valid = 1;
				} else {
					sunxi_hci->usb_hsic_reset_n_valid = 0;
					DMSG_PRINT("get %s usb_hsic_reset_n is fail\n",
						sunxi_hci->hci_name);
				}

			} else {
				sunxi_hci->usb_hsic_hub_connect_valid = 0;
				sunxi_hci->usb_hsic_int_n_valid = 0;
				sunxi_hci->usb_hsic_reset_n_valid = 0;
			}

		} else {
			sunxi_hci->hsic_ctrl_flag = 0;
			sunxi_hci->usb_host_hsic_rdy_valid = 0;
			sunxi_hci->usb_hsic_hub_connect_valid = 0;
			sunxi_hci->usb_hsic_int_n_valid = 0;
			sunxi_hci->usb_hsic_reset_n_valid = 0;
		}
	}

	/* usbc wakeup_suspend */
	ret = of_property_read_u32(usbc_np,
			KEY_USB_WAKEUP_SUSPEND,
			&sunxi_hci->wakeup_suspend);
	if (ret) {
		DMSG_PRINT("get %s wakeup_suspend is fail, %d\n",
			sunxi_hci->hci_name, -ret);
	}

	/* usbc drv_vbus */
	ret = of_property_read_string(usbc_np,
			KEY_USB_DRVVBUS_GPIO,
			&sunxi_hci->drv_vbus_name);
	if (ret) {
		DMSG_PRINT("get drv_vbus is fail, %d\n", -ret);
		sunxi_hci->drv_vbus_gpio_valid = 0;
	} else {
		if (strncmp(sunxi_hci->drv_vbus_name, "axp_ctrl", 8) == 0) {
			sunxi_hci->drv_vbus_type = USB_DRV_VBUS_TYPE_AXP;
			sunxi_hci->drv_vbus_gpio_valid = 0;
		} else {
			/* get drv vbus gpio */
			sunxi_hci->drv_vbus_gpio_set.gpio =
					of_get_named_gpio(usbc_np,
						KEY_USB_DRVVBUS_GPIO, 0);
			if (gpio_is_valid(sunxi_hci->drv_vbus_gpio_set.gpio)) {
				sunxi_hci->drv_vbus_gpio_valid = 1;
				sunxi_hci->drv_vbus_type =
						USB_DRV_VBUS_TYPE_GIPO;
			} else {
				sunxi_hci->drv_vbus_gpio_valid = 0;
			}
		}
	}

	/* usbc regulator_io */
	ret = of_property_read_string(usbc_np,
			KEY_USB_REGULATOR_IO,
			&sunxi_hci->regulator_io);
	if (ret) {
		DMSG_PRINT("get %s, regulator_io is fail, %d\n",
			sunxi_hci->hci_name, -ret);
		sunxi_hci->regulator_io = NULL;
	} else {
		if (!strcmp(sunxi_hci->regulator_io, "nocare")) {
			DMSG_PRINT("get %s, regulator_io is no nocare\n",
				sunxi_hci->hci_name);
			sunxi_hci->regulator_io = NULL;
		}
	}

	return 0;
}

int sunxi_get_hci_num(struct platform_device *pdev)
{
	struct device_node *np = pdev->dev.of_node;
	int ret = 0;
	int hci_num = 0;

	ret = of_property_read_u32(np, HCI_USBC_NO, &hci_num);
	if (ret)
		DMSG_PANIC("get hci_ctrl_num is fail, %d\n", -ret);

	return hci_num;
}

static int sunxi_get_hci_name(struct platform_device *pdev,
		struct sunxi_hci_hcd *sunxi_hci)
{
	struct device_node *np = pdev->dev.of_node;

	sprintf(sunxi_hci->hci_name, "%s", np->name);

	return 0;
}

static int sunxi_get_hci_irq_no(struct platform_device *pdev,
		struct sunxi_hci_hcd *sunxi_hci)
{
	sunxi_hci->irq_no = platform_get_irq(pdev, 0);

	return 0;
}

static int sunxi_get_hci_resource(struct platform_device *pdev,
		struct sunxi_hci_hcd *sunxi_hci, int usbc_no)
{
	if (sunxi_hci == NULL) {
		dev_err(&pdev->dev, "sunxi_hci is NULL\n");
		return -1;
	}

	memset(sunxi_hci, 0, sizeof(struct sunxi_hci_hcd));

	sunxi_hci->usbc_no = usbc_no;
	sunxi_get_hci_name(pdev, sunxi_hci);
	get_usb_cfg(pdev, sunxi_hci);

	if (sunxi_hci->used == 0) {
		DMSG_INFO("sunxi %s is no enable\n", sunxi_hci->hci_name);
		return -1;
	}

	sunxi_get_hci_base(pdev, sunxi_hci);
	sunxi_get_hci_clock(pdev, sunxi_hci);
	sunxi_get_hci_irq_no(pdev, sunxi_hci);

	request_usb_regulator_io(sunxi_hci);
	sunxi_hci->open_clock	= open_clock;
	sunxi_hci->close_clock	= close_clock;
	sunxi_hci->set_power	= sunxi_set_vbus;
	sunxi_hci->usb_passby	= usb_passby;

	alloc_pin(sunxi_hci);

	pdev->dev.platform_data = sunxi_hci;
	return 0;
}

int exit_sunxi_hci(struct sunxi_hci_hcd *sunxi_hci)
{
	release_usb_regulator_io(sunxi_hci);
	free_pin(sunxi_hci);
	return 0;
}

int init_sunxi_hci(struct platform_device *pdev, int usbc_type)
{
	struct sunxi_hci_hcd *sunxi_hci = NULL;
	int usbc_no = 0;
	int hci_num = -1;
	int ret = -1;

#ifdef CONFIG_OF
	pdev->dev.dma_mask = &sunxi_hci_dmamask;
	pdev->dev.coherent_dma_mask = DMA_BIT_MASK(32);
#endif

	hci_num = sunxi_get_hci_num(pdev);

	if (usbc_type == SUNXI_USB_XHCI) {
		usbc_no = hci_num;
		sunxi_hci = &sunxi_xhci;
	} else {
		switch (hci_num) {
		case HCI0_USBC_NO:
			usbc_no = HCI0_USBC_NO;
			if (usbc_type == SUNXI_USB_EHCI) {
				sunxi_hci = &sunxi_ehci0;
			} else if (usbc_type == SUNXI_USB_OHCI) {
				sunxi_hci = &sunxi_ohci0;
			} else {
				dev_err(&pdev->dev, "get hci num fail: %d\n", hci_num);
				return -1;
			}
			break;

		case HCI1_USBC_NO:
			usbc_no = HCI1_USBC_NO;
			if (usbc_type == SUNXI_USB_EHCI) {
				sunxi_hci = &sunxi_ehci1;
			} else if (usbc_type == SUNXI_USB_OHCI) {
				sunxi_hci = &sunxi_ohci1;
			} else {
				dev_err(&pdev->dev, "get hci num fail: %d\n", hci_num);
				return -1;
			}
			break;

		case HCI2_USBC_NO:
			usbc_no = HCI2_USBC_NO;
			if (usbc_type == SUNXI_USB_EHCI) {
				sunxi_hci = &sunxi_ehci2;
			} else if (usbc_type == SUNXI_USB_OHCI) {
				sunxi_hci = &sunxi_ohci2;
			} else {
				dev_err(&pdev->dev, "get hci num fail: %d\n", hci_num);
				return -1;
			}
			break;

		case HCI3_USBC_NO:
			usbc_no = HCI3_USBC_NO;
			if (usbc_type == SUNXI_USB_EHCI) {
				sunxi_hci = &sunxi_ehci3;
			} else if (usbc_type == SUNXI_USB_OHCI) {
				sunxi_hci = &sunxi_ohci3;
			} else {
				dev_err(&pdev->dev, "get hci num fail: %d\n", hci_num);
				return -1;
			}
			break;

		default:
			dev_err(&pdev->dev, "get hci num fail: %d\n", hci_num);
			return -1;
		}
	}

	ret = sunxi_get_hci_resource(pdev, sunxi_hci, usbc_no);
	if (ret != 0)
		return ret;

	if (usbc_type == SUNXI_USB_OHCI)
		ret = sunxi_get_ohci_clock_src(pdev, sunxi_hci);

	return ret;
}
EXPORT_SYMBOL(init_sunxi_hci);

MODULE_LICENSE("GPL");
