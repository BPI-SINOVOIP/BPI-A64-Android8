/*
 * drivers/power/axp/axp-core.c
 * (C) Copyright 2010-2016
 * Allwinner Technology Co., Ltd. <www.allwinnertech.com>
 * Pannan <pannan@allwinnertech.com>
 *
 * axp common APIs
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License, or (at your option) any later version.
 *
 */

#include <linux/kernel.h>
#include <linux/mutex.h>
#include <linux/init.h>
#include <linux/device.h>
#include <linux/interrupt.h>
#include <linux/platform_device.h>
#include <linux/mfd/core.h>
#include <linux/delay.h>
#include <linux/slab.h>
#include <linux/i2c.h>
#ifdef CONFIG_SUNXI_ARISC
#include <linux/arisc/arisc.h>
#endif
#include "axp-core.h"

int axp_suspend_flag = AXP_NOT_SUSPEND;
struct axp_platform_ops ap_ops[AXP_ONLINE_SUM];
const char *axp_name[AXP_ONLINE_SUM];
static LIST_HEAD(axp_dev_list);
static DEFINE_SPINLOCK(axp_list_lock);
int axp_dev_register_count;
struct work_struct axp_irq_work;
/* used for mark whether a pwr_dm belongs to sys_pwr_dm or not. */
static u32 axp_sys_pwr_dm_mask;
static u32 axp_power_tree[VCC_MAX_INDEX] = {0};
static DEFINE_SPINLOCK(axp_pwr_data_lock);
int axp_usb_connect;
char g_androidboot_mode[10];
#define ANDROIDBOOT_MODE "androidboot.mode"
#define CHARGE_MODE "charger"
void axp_platform_ops_set(int pmu_num, struct axp_platform_ops *ops)
{
	ap_ops[pmu_num].usb_det = ops->usb_det;
	ap_ops[pmu_num].usb_vbus_output = ops->usb_vbus_output;
	ap_ops[pmu_num].cfg_pmux_para = ops->cfg_pmux_para;
	ap_ops[pmu_num].get_pmu_name = ops->get_pmu_name;
	ap_ops[pmu_num].get_pmu_dev  = ops->get_pmu_dev;
	ap_ops[pmu_num].pmu_regulator_save = ops->pmu_regulator_save;
	ap_ops[pmu_num].pmu_regulator_restore = ops->pmu_regulator_restore;
}
static int get_para_from_cmdline(const char *cmdline, const char *name, char *value, int maxsize)
{
	char *p = (char *)cmdline;
	char *value_p = value;
	int size = 0;

	if (!cmdline || !name || !value) {
		return -1;
	}

	for (; *p != 0;) {
		if (*p++ == ' ') {
			if (0 == strncmp(p, name, strlen(name))) {
				p += strlen(name);
				if (*p++ != '=') {
					continue;
				}
				while ((*p != 0) && (*p != ' ') && (++size < maxsize)) {
					*value_p++ = *p++;
				}
				*value_p = '\0';
				return value_p - value;
			}
		}
	}

	return 0;
}
static int axp_is_shutdown_charge_mode(void)
{
	u32 Is_Charger_Mode = 0;
	u8 ret = 0;
	ret = get_para_from_cmdline(saved_command_line, ANDROIDBOOT_MODE, g_androidboot_mode, sizeof(g_androidboot_mode));
	if (ret <= 0) {
		Is_Charger_Mode = 0;
	} else {
		if (strncmp(g_androidboot_mode, CHARGE_MODE, 8)) {
			Is_Charger_Mode = 0;
		} else {
			Is_Charger_Mode = 1;
		}
	}
	return Is_Charger_Mode;
}
s32 axp_usb_det(void)
{
	if (!ap_ops[0].usb_det || axp_is_shutdown_charge_mode())
		return 0;

	return ap_ops[0].usb_det();
}
EXPORT_SYMBOL_GPL(axp_usb_det);

s32 axp_usb_vbus_output(int high)
{
	if (!ap_ops[0].usb_vbus_output || axp_is_shutdown_charge_mode())
		return 0;
	return ap_ops[0].usb_vbus_output(high);
}
EXPORT_SYMBOL_GPL(axp_usb_vbus_output);

int axp_usb_is_connected(void)
{
	if (axp_is_shutdown_charge_mode())
		return 0;
	return axp_usb_connect;
}
EXPORT_SYMBOL_GPL(axp_usb_is_connected);

int config_pmux_para(int num, struct aw_pm_info *api, int *pmu_id)
{
	if (num >= AXP_ONLINE_SUM)
		return -EINVAL;

	if (ap_ops[num].cfg_pmux_para)
		return ap_ops[num].cfg_pmux_para(num, api, pmu_id);
	else
		return -EINVAL;
}
EXPORT_SYMBOL_GPL(config_pmux_para);

const char *get_pmu_cur_name(int pmu_num)
{
	if (ap_ops[pmu_num].get_pmu_name)
		return ap_ops[pmu_num].get_pmu_name();
	else
		return NULL;
}
EXPORT_SYMBOL_GPL(get_pmu_cur_name);

struct axp_dev *get_pmu_cur_dev(int pmu_num)
{
	if (ap_ops[pmu_num].get_pmu_dev)
		return ap_ops[pmu_num].get_pmu_dev();
	else
		return NULL;
}
EXPORT_SYMBOL_GPL(get_pmu_cur_dev);

int axp_mem_save(void)
{
	if (ap_ops[0].pmu_regulator_save)
		return ap_ops[0].pmu_regulator_save();
	return 0;
}
EXPORT_SYMBOL_GPL(axp_mem_save);

void axp_mem_restore(void)
{
	if (ap_ops[0].pmu_regulator_restore)
		return ap_ops[0].pmu_regulator_restore();
}
EXPORT_SYMBOL_GPL(axp_mem_restore);

int axp_get_pmu_num(const struct axp_compatible_name_mapping *mapping, int size)
{
	struct device_node *np;
	int i, j, pmu_num = -EINVAL;
	char node_name[8];
	const char *prop_name = NULL;

	for (i = 0; i < AXP_ONLINE_SUM; i++) {
		sprintf(node_name, "pmu%d", i);

		np = of_find_node_by_type(NULL, node_name);
		if (NULL == np) {
			BUG_ON(i == 0);
			break;
		}

		if (of_property_read_string(np, "compatible",
					&prop_name)) {
			pr_err("%s get failed\n", prop_name);
			break;
		}

		for (j = 0; j < size; j++) {
			if (!strcmp(prop_name, mapping[j].device_name)) {
				pmu_num = i;
				break;
			}
		}
	}

	return pmu_num;
}

int axp_mfd_cell_name_init(const struct axp_compatible_name_mapping *mapping,
				int count, int pmu_num,
				int size, struct mfd_cell *cells)
{
	int i, j, find = 0;

	for (j = 0; j < count; j++) {
		if ((mapping[j].mfd_name.powerkey_name != NULL)
				&& (strstr(mapping[j].mfd_name.powerkey_name,
				axp_name[pmu_num]) != NULL)) {
			find = 1;
			break;
		}

		if ((mapping[j].mfd_name.regulator_name != NULL)
				&& (strstr(mapping[j].mfd_name.regulator_name,
				axp_name[pmu_num]) != NULL)) {
			find = 1;
			break;
		}

		if ((mapping[j].mfd_name.charger_name != NULL)
				&& (strstr(mapping[j].mfd_name.charger_name,
				axp_name[pmu_num]) != NULL)) {
			find = 1;
			break;
		}

		if ((mapping[j].mfd_name.gpio_name != NULL)
				&& (strstr(mapping[j].mfd_name.gpio_name,
				axp_name[pmu_num]) != NULL)) {
			find = 1;
			break;
		}
	}

	if (find == 0) {
		pr_err("%s no axp mfd cell find\n", __func__);
		return -EINVAL;
	}

	for (i = 0; i < size; i++) {
		if (strstr(cells[i].name, "powerkey") != NULL)
			cells[i].of_compatible =
					mapping[j].mfd_name.powerkey_name;
		else if (strstr(cells[i].name, "regulator") != NULL)
			cells[i].of_compatible =
					mapping[j].mfd_name.regulator_name;
		else if (strstr(cells[i].name, "charger") != NULL)
			cells[i].of_compatible =
					mapping[j].mfd_name.charger_name;
		else if (strstr(cells[i].name, "gpio") != NULL)
			cells[i].of_compatible =
					mapping[j].mfd_name.gpio_name;
	}

	return 0;
}

#ifdef CONFIG_AXP_TWI_USED
static s32 __axp_read_i2c(struct i2c_client *client, u32 reg, u8 *val)
{
	s32 ret;

	ret = i2c_smbus_read_byte_data(client, reg);
	if (ret < 0) {
		dev_err(&client->dev, "failed reading at 0x%02x\n", reg);
		return ret;
	}

	*val = (u8)ret;

	return 0;
}

static s32 __axp_reads_i2c(struct i2c_client *client,
				int reg, int len, u8 *val)
{
	s32 ret;

	ret = i2c_smbus_read_i2c_block_data(client, reg, len, val);
	if (ret < 0) {
		dev_err(&client->dev, "failed reading from 0x%02x\n", reg);
		return ret;
	}

	return 0;
}

static s32 __axp_write_i2c(struct i2c_client *client, int reg, u8 val)
{
	s32 ret;

	/* axp_reg_debug(reg, 1, &val); */
	ret = i2c_smbus_write_byte_data(client, reg, val);
	if (ret < 0) {
		dev_err(&client->dev, "failed writing 0x%02x to 0x%02x\n",
				val, reg);
		return ret;
	}

	return 0;
}

static s32 __axp_writes_i2c(struct i2c_client *client,
				int reg, int len, u8 *val)
{
	s32 ret;

	/* axp_reg_debug(reg, len, val); */
	ret = i2c_smbus_write_i2c_block_data(client, reg, len, val);
	if (ret < 0) {
		dev_err(&client->dev, "failed writings to 0x%02x\n", reg);
		return ret;
	}

	return 0;
}

static inline s32 __axp_read_arisc_rsb(char devaddr, int reg,
			u8 *val, bool syncflag)
{
	return 0;
}
static inline s32 __axp_reads_arisc_rsb(char devaddr, int reg,
			int len, u8 *val, bool syncflag)
{
	return 0;
}
static inline s32 __axp_write_arisc_rsb(char devaddr, int reg,
			u8 val, bool syncflag)
{
	return 0;
}
static inline s32 __axp_writes_arisc_rsb(char devaddr, int reg,
			int len, u8 *val, bool syncflag)
{
	return 0;
}

static inline s32 __axp_read_arisc_twi(int reg, u8 *val, bool syncflag)
{
	return 0;
}
static inline s32 __axp_reads_arisc_twi(int reg, int len,
				u8 *val, bool syncflag)
{
	return 0;
}
static inline s32 __axp_write_arisc_twi(int reg, u8 val, bool syncflag)
{
	return 0;
}
static inline s32 __axp_writes_arisc_twi(int reg, int len,
				u8 *val, bool syncflag)
{
	return 0;
}
#else
static inline s32 __axp_read_i2c(struct i2c_client *client,
				u32 reg, u8 *val)
{
	return 0;
}
static inline s32 __axp_reads_i2c(struct i2c_client *client,
				int reg, int len, u8 *val)
{
	return 0;
}
static inline s32 __axp_write_i2c(struct i2c_client *client,
				int reg, u8 val)
{
	return 0;
}
static inline s32 __axp_writes_i2c(struct i2c_client *client,
				int reg, int len, u8 *val)
{
	return 0;
}

static s32 __axp_read_arisc_rsb(char devaddr, int reg, u8 *val, bool syncflag)
{
	s32 ret;
	u8 addr = (u8)reg;
	u8 data = 0;
	arisc_rsb_block_cfg_t rsb_data;
	u32 data_temp;

	rsb_data.len = 1;
	rsb_data.datatype = RSB_DATA_TYPE_BYTE;

	if (syncflag)
		rsb_data.msgattr = ARISC_MESSAGE_ATTR_HARDSYN;
	else
		rsb_data.msgattr = ARISC_MESSAGE_ATTR_SOFTSYN;

	rsb_data.devaddr = devaddr;
	rsb_data.regaddr = &addr;
	rsb_data.data = &data_temp;

	/* write axp registers */
	ret = arisc_rsb_read_block_data(&rsb_data);
	if (data_temp == 0)
		ret = arisc_rsb_read_block_data(&rsb_data);

	if (ret != 0) {
		pr_err("failed read to 0x%02x\n", reg);
		return ret;
	}

	data = (u8)data_temp;
	*val = data;

	return 0;
}

static s32 __axp_reads_arisc_rsb(char devaddr, int reg,
				int len, u8 *val, bool syncflag)
{
	s32 ret, i, rd_len;
	u8 addr[AXP_TRANS_BYTE_MAX];
	u8 data[AXP_TRANS_BYTE_MAX];
	u8 *cur_data = val;
	arisc_rsb_block_cfg_t rsb_data;
	u32 data_temp[AXP_TRANS_BYTE_MAX];

	/* fetch first register address */
	while (len > 0) {
		rd_len = min(len, AXP_TRANS_BYTE_MAX);
		for (i = 0; i < rd_len; i++)
			addr[i] = reg++;

		rsb_data.len = rd_len;
		rsb_data.datatype = RSB_DATA_TYPE_BYTE;

		if (syncflag)
			rsb_data.msgattr = ARISC_MESSAGE_ATTR_HARDSYN;
		else
			rsb_data.msgattr = ARISC_MESSAGE_ATTR_SOFTSYN;

		rsb_data.devaddr = devaddr;
		rsb_data.regaddr = addr;
		rsb_data.data = data_temp;

		/* read axp registers */
		ret = arisc_rsb_read_block_data(&rsb_data);

		if (ret != 0) {
			pr_err("failed reads to 0x%02x\n", reg);
			return ret;
		}

		for (i = 0; i < rd_len; i++)
			data[i] = (u8)data_temp[i];

		/* copy data to user buffer */
		memcpy(cur_data, data, rd_len);
		cur_data = cur_data + rd_len;

		/* process next time read */
		len -= rd_len;
	}

	return 0;
}

static s32 __axp_write_arisc_rsb(char devaddr, int reg, u8 val, bool syncflag)
{
	s32 ret;
	u8 addr = (u8)reg;
	arisc_rsb_block_cfg_t rsb_data;
	u32 data;

	/* axp_reg_debug(reg, 1, &val); */

	data = (unsigned int)val;
	rsb_data.len = 1;
	rsb_data.datatype = RSB_DATA_TYPE_BYTE;

	if (syncflag)
		rsb_data.msgattr = ARISC_MESSAGE_ATTR_HARDSYN;
	else
		rsb_data.msgattr = ARISC_MESSAGE_ATTR_SOFTSYN;

	rsb_data.devaddr = devaddr;
	rsb_data.regaddr = &addr;
	rsb_data.data = &data;

	/* write axp registers */
	ret = arisc_rsb_write_block_data(&rsb_data);
	if (ret != 0) {
		pr_err("failed writing to 0x%02x\n", reg);
		return ret;
	}

	return 0;
}

static s32 __axp_writes_arisc_rsb(char devaddr, int reg,
				int len, u8 *val, bool syncflag)
{
	s32 ret = 0, i, first_flag, wr_len;
	u8 addr[AXP_TRANS_BYTE_MAX];
	u8 data[AXP_TRANS_BYTE_MAX];
	arisc_rsb_block_cfg_t rsb_data;
	u32 data_temp[AXP_TRANS_BYTE_MAX];

	/* axp_reg_debug(reg, len, val); */

	/* fetch first register address */
	first_flag = 1;
	addr[0] = (u8)reg;
	len = len + 1;  /* + first reg addr */
	len = len >> 1; /* len = len / 2 */

	while (len > 0) {
		wr_len = min(len, AXP_TRANS_BYTE_MAX);
		for (i = 0; i < wr_len; i++) {
			if (first_flag) {
				/* skip the first reg addr */
				data[i] = *val++;
				first_flag = 0;
			} else {
				addr[i] = *val++;
				data[i] = *val++;
			}
		}

		for (i = 0; i < wr_len; i++)
			data_temp[i] = (unsigned int)data[i];

		rsb_data.len = wr_len;
		rsb_data.datatype = RSB_DATA_TYPE_BYTE;

		if (syncflag)
			rsb_data.msgattr = ARISC_MESSAGE_ATTR_HARDSYN;
		else
			rsb_data.msgattr = ARISC_MESSAGE_ATTR_SOFTSYN;

		rsb_data.devaddr = devaddr;
		rsb_data.regaddr = addr;
		rsb_data.data = data_temp;

		/* write axp registers */
		ret = arisc_rsb_write_block_data(&rsb_data);
		if (ret != 0) {
			pr_err("failed writings to 0x%02x\n", reg);
			return ret;
		}

		/* process next time write */
		len -= wr_len;
	}

	return 0;
}

static s32 __axp_read_arisc_twi(int reg, u8 *val, bool syncflag)
{
	return 0;
}

static s32 __axp_reads_arisc_twi(int reg, int len, u8 *val, bool syncflag)
{
	return 0;
}

static s32 __axp_write_arisc_twi(int reg, u8 val, bool syncflag)
{

	return 0;
}

static s32 __axp_writes_arisc_twi(int reg, int len, u8 *val, bool syncflag)
{
	return 0;
}
#endif

static s32 _axp_write(struct axp_regmap *map, s32 reg, u8 val, bool sync)
{
	s32 ret = 0;

	pr_debug("%s: map->type = 0x%x, reg = 0x%x, val = %u, sync= %d.\n",
		__func__, map->type, reg, val, sync);

	if (map->type == AXP_REGMAP_I2C)
		ret = __axp_write_i2c(map->client, reg, val);
	else if (map->type == AXP_REGMAP_ARISC_RSB)
		ret = __axp_write_arisc_rsb(map->rsbaddr, reg, val, sync);
	else if (map->type == AXP_REGMAP_ARISC_TWI)
		ret = __axp_write_arisc_twi(reg, val, sync);

	return ret;
}

static s32 _axp_writes(struct axp_regmap *map, s32 reg,
				s32 len, u8 *val, bool sync)
{
	s32 ret = 0, i;
	s32 wr_len, rw_reg;
	u8 wr_val[32];

	pr_debug("%s: map->type = 0x%x, reg = 0x%x, val addr = 0x%p, sync= %d.\n",
		__func__, map->type, reg, val, sync);

	while (len) {
		wr_len = min(len, 15);
		rw_reg = reg++;
		wr_val[0] = *val++;

		for (i = 1; i < wr_len; i++) {
			wr_val[i*2-1] = reg++;
			wr_val[i*2] = *val++;
		}

		if (map->type == AXP_REGMAP_I2C)
			ret = __axp_writes_i2c(map->client,
					rw_reg, 2*wr_len-1, wr_val);
		else if (map->type == AXP_REGMAP_ARISC_RSB)
			ret = __axp_writes_arisc_rsb(map->rsbaddr,
					rw_reg, 2*wr_len-1, wr_val, sync);
		else if (map->type == AXP_REGMAP_ARISC_TWI)
			ret = __axp_writes_arisc_twi(rw_reg,
					2*wr_len-1, wr_val, sync);

		if (ret)
			return ret;

		len -= wr_len;
	}

	return 0;
}

static s32 _axp_read(struct axp_regmap *map, s32 reg, u8 *val, bool sync)
{
	s32 ret = 0;

	if (map->type == AXP_REGMAP_I2C)
		ret = __axp_read_i2c(map->client, reg, val);
	else if (map->type == AXP_REGMAP_ARISC_RSB)
		ret = __axp_read_arisc_rsb(map->rsbaddr, reg, val, sync);
	else if (map->type == AXP_REGMAP_ARISC_TWI)
		ret = __axp_read_arisc_twi(reg, val, sync);

	pr_debug("%s: map->type = 0x%x, reg = 0x%x, val = 0x%hhx, sync= %d.\n",
		__func__, map->type, reg, *val, sync);
	return ret;
}

static s32 _axp_reads(struct axp_regmap *map, s32 reg,
				s32 len, u8 *val, bool sync)
{
	s32 ret = 0;

	pr_debug("%s: map->type = 0x%x, reg = 0x%x, val addr = 0x%p, sync= %d.\n",
		__func__, map->type, reg, val, sync);

	if (map->type == AXP_REGMAP_I2C)
		ret = __axp_reads_i2c(map->client, reg, len, val);
	else if (map->type == AXP_REGMAP_ARISC_RSB)
		ret = __axp_reads_arisc_rsb(map->rsbaddr, reg, len, val, sync);
	else if (map->type == AXP_REGMAP_ARISC_TWI)
		ret = __axp_reads_arisc_twi(reg, len, val, sync);

	return ret;
}

s32 axp_regmap_write(struct axp_regmap *map, s32 reg, u8 val)
{
	s32 ret = 0;

	mutex_lock(&map->lock);
	ret = _axp_write(map, reg, val, false);
	mutex_unlock(&map->lock);

	return ret;
}
EXPORT_SYMBOL_GPL(axp_regmap_write);

s32 axp_regmap_writes(struct axp_regmap *map, s32 reg, s32 len, u8 *val)
{
	s32 ret = 0;

	mutex_lock(&map->lock);
	ret = _axp_writes(map, reg, len, val, false);
	mutex_unlock(&map->lock);

	return ret;
}
EXPORT_SYMBOL_GPL(axp_regmap_writes);

s32 axp_regmap_read(struct axp_regmap *map, s32 reg, u8 *val)
{
	return _axp_read(map, reg, val, false);
}
EXPORT_SYMBOL_GPL(axp_regmap_read);

s32 axp_regmap_reads(struct axp_regmap *map, s32 reg, s32 len, u8 *val)
{
	return _axp_reads(map, reg, len, val, false);
}
EXPORT_SYMBOL_GPL(axp_regmap_reads);

s32 axp_regmap_set_bits(struct axp_regmap *map, s32 reg, u8 bit_mask)
{
	u8 reg_val;
	s32 ret = 0;

	mutex_lock(&map->lock);
	ret = _axp_read(map, reg, &reg_val, false);

	if (ret)
		goto out;

	if ((reg_val & bit_mask) != bit_mask) {
		reg_val |= bit_mask;
		ret = _axp_write(map, reg, reg_val, false);
	}

out:
	mutex_unlock(&map->lock);

	return ret;
}
EXPORT_SYMBOL_GPL(axp_regmap_set_bits);

s32 axp_regmap_clr_bits(struct axp_regmap *map, s32 reg, u8 bit_mask)
{
	u8 reg_val;
	s32 ret = 0;

	mutex_lock(&map->lock);
	ret = _axp_read(map, reg, &reg_val, false);

	if (ret)
		goto out;

	if (reg_val & bit_mask) {
		reg_val &= ~bit_mask;
		ret = _axp_write(map, reg, reg_val, false);
	}

out:
	mutex_unlock(&map->lock);

	return ret;
}
EXPORT_SYMBOL_GPL(axp_regmap_clr_bits);

s32 axp_regmap_update(struct axp_regmap *map, s32 reg, u8 val, u8 mask)
{
	u8 reg_val;
	s32 ret = 0;
#ifdef CONFIG_ARCH_SUN50IW1P1
	s32 err_num = 0;
#endif

	mutex_lock(&map->lock);
	ret = _axp_read(map, reg, &reg_val, false);
	if (ret)
		goto out;

#ifdef CONFIG_ARCH_SUN50IW1P1
	/* in sun50iw1p1 P4 platform, we found that when we read the reg_(0x12)
	 * of PMU, the return value may be 0 because of the abnormal communication
	 * between CPUs and PMU; we can Cyclic reading the value of reg_(0x12)
	 * until it is not 0 to evade this situation using software method.
	 * when the real reason is found out, we can delete this patch.
	 * */
	if (reg == 0x12 && reg_val == 0x00) {
		while (err_num++ <= 3) {
			ret = _axp_read(map, reg, &reg_val, false);
			if (ret)
				goto out;
			if (reg_val)
				break;
		}
	}
#endif
	if ((reg_val & mask) != val) {
		reg_val = (reg_val & ~mask) | val;
		ret = _axp_write(map, reg, reg_val, false);
	}

out:
	mutex_unlock(&map->lock);

	return ret;
}
EXPORT_SYMBOL_GPL(axp_regmap_update);


s32 axp_regmap_set_bits_sync(struct axp_regmap *map, s32 reg, u8 bit_mask)
{
	u8 reg_val;
	s32 ret = 0;
#ifndef CONFIG_AXP_TWI_USED
	unsigned long irqflags;

	spin_lock_irqsave(&map->spinlock, irqflags);
#else
	mutex_lock(&map->lock);
#endif

	ret = _axp_read(map, reg, &reg_val, true);
	if (ret)
		goto out;

	if ((reg_val & bit_mask) != bit_mask) {
		reg_val |= bit_mask;
		ret = _axp_write(map, reg, reg_val, true);
	}

out:
#ifndef CONFIG_AXP_TWI_USED
	spin_unlock_irqrestore(&map->spinlock, irqflags);
#else
	mutex_unlock(&map->lock);
#endif

	return ret;
}
EXPORT_SYMBOL_GPL(axp_regmap_set_bits_sync);

s32 axp_regmap_clr_bits_sync(struct axp_regmap *map, s32 reg, u8 bit_mask)
{
	u8 reg_val;
	s32 ret = 0;
#ifndef CONFIG_AXP_TWI_USED
	unsigned long irqflags;

	spin_lock_irqsave(&map->spinlock, irqflags);
#else
	mutex_lock(&map->lock);
#endif

	ret = _axp_read(map, reg, &reg_val, true);
	if (ret)
		goto out;

	if (reg_val & bit_mask) {
		reg_val &= ~bit_mask;
		ret = _axp_write(map, reg, reg_val, true);
	}

out:
#ifndef CONFIG_AXP_TWI_USED
	spin_unlock_irqrestore(&map->spinlock, irqflags);
#else
	mutex_unlock(&map->lock);
#endif

	return ret;
}
EXPORT_SYMBOL_GPL(axp_regmap_clr_bits_sync);

s32 axp_regmap_update_sync(struct axp_regmap *map, s32 reg, u8 val, u8 mask)
{
	u8 reg_val;
	s32 ret = 0;
#ifndef CONFIG_AXP_TWI_USED
	unsigned long irqflags;

	spin_lock_irqsave(&map->spinlock, irqflags);
#else
	mutex_lock(&map->lock);
#endif

	ret = _axp_read(map, reg, &reg_val, true);
	if (ret)
		goto out;

	if ((reg_val & mask) != val) {
		reg_val = (reg_val & ~mask) | val;
		ret = _axp_write(map, reg, reg_val, true);
	}

out:
#ifndef CONFIG_AXP_TWI_USED
	spin_unlock_irqrestore(&map->spinlock, irqflags);
#else
	mutex_unlock(&map->lock);
#endif

	return ret;
}
EXPORT_SYMBOL_GPL(axp_regmap_update_sync);

struct axp_regmap *axp_regmap_init_i2c(struct device *dev)
{
	struct axp_regmap *map = NULL;

	map = devm_kzalloc(dev, sizeof(*map), GFP_KERNEL);
	if (IS_ERR_OR_NULL(map)) {
		pr_err("%s: not enough memory!\n", __func__);
		return NULL;
	}

	map->type = AXP_REGMAP_I2C;
	map->client = to_i2c_client(dev);
	mutex_init(&map->lock);

	return map;
}
EXPORT_SYMBOL_GPL(axp_regmap_init_i2c);

struct axp_regmap *axp_regmap_init_arisc_rsb(struct device *dev, u8 addr)
{
	struct axp_regmap *map = NULL;
	map = devm_kzalloc(dev, sizeof(*map), GFP_KERNEL);
	if (IS_ERR_OR_NULL(map)) {
		pr_err("%s: not enough memory!\n", __func__);
		return NULL;
	}

	map->type = AXP_REGMAP_ARISC_RSB;
	map->rsbaddr = addr;
#ifndef CONFIG_AXP_TWI_USED
	spin_lock_init(&map->spinlock);
#endif
	mutex_init(&map->lock);

	return map;
}
EXPORT_SYMBOL_GPL(axp_regmap_init_arisc_rsb);

struct axp_regmap *axp_regmap_init_arisc_twi(struct device *dev)
{
	struct axp_regmap *map = NULL;

	map = devm_kzalloc(dev, sizeof(*map), GFP_KERNEL);
	if (IS_ERR_OR_NULL(map)) {
		pr_err("%s: not enough memory!\n", __func__);
		return NULL;
	}

	map->type = AXP_REGMAP_ARISC_TWI;
#ifndef CONFIG_AXP_TWI_USED
	spin_lock_init(&map->spinlock);
#endif
	mutex_init(&map->lock);

	return map;
}
EXPORT_SYMBOL_GPL(axp_regmap_init_arisc_twi);

static void __do_irq(int pmu_num, struct axp_irq_chip_data *irq_data)
{
	u64 irqs = 0;
	u8 reg_val[8];
	u32 i = 0;
	u32 j = 0;
	void *idata;

	if (irq_data == NULL)
		return;

	axp_regmap_reads(irq_data->map, irq_data->chip->status_base,
			irq_data->chip->num_regs, reg_val);

	for (i = 0; i < irq_data->chip->num_regs; i++)
		irqs |= (u64)reg_val[i] << (i * AXP_REG_WIDTH);

	irqs &= irq_data->irqs_enabled;
	if (irqs == 0)
		return;

	AXP_DEBUG(AXP_INT, pmu_num, "irqs enabled = 0x%llx\n",
				irq_data->irqs_enabled);
	AXP_DEBUG(AXP_INT, pmu_num, "irqs = 0x%llx\n", irqs);

	for_each_set_bit(j, (unsigned long *)&irqs, irq_data->num_irqs) {
		if (irq_data->irqs[j].handler) {
			idata = irq_data->irqs[j].data;
			irq_data->irqs[j].handler(j, idata);
		}
	}

	for (i = 0; i < irq_data->chip->num_regs; i++) {
		if (reg_val[i] != 0) {
			axp_regmap_write(irq_data->map,
				irq_data->chip->status_base + i, reg_val[i]);
			udelay(30);
		}
	}
}

static void axp_irq_work_func(struct work_struct *work)
{
	struct axp_dev *adev = NULL;

	list_for_each_entry(adev, &axp_dev_list, list) {
		__do_irq(adev->pmu_num, adev->irq_data);
	}


	sunxi_nmi_clear_status();
	sunxi_nmi_enable();
}

static irqreturn_t axp_irq(int irq, void *data)
{
	struct axp_dev *adev = NULL;

	sunxi_nmi_disable();

	if (axp_suspend_flag == AXP_NOT_SUSPEND) {
		schedule_work(&axp_irq_work);
	} else if (axp_suspend_flag == AXP_WAS_SUSPEND) {
		list_for_each_entry(adev, &axp_dev_list, list) {
			if (adev->irq_data->wakeup_event) {
				adev->irq_data->wakeup_event();
				axp_suspend_flag = AXP_SUSPEND_WITH_IRQ;
			}
		}
	}

	return IRQ_HANDLED;
}

struct axp_irq_chip_data *axp_irq_chip_register(struct axp_regmap *map,
			int irq_no, int irq_flags,
			struct axp_regmap_irq_chip *irq_chip,
			void (*wakeup_event)(void))
{
	struct axp_irq_chip_data *irq_data = NULL;
	struct axp_regmap_irq *irqs = NULL;
	int i, err = 0;

	irq_data = kzalloc(sizeof(*irq_data), GFP_KERNEL);
	if (IS_ERR_OR_NULL(irq_data)) {
		pr_err("axp irq data: not enough memory for irq data\n");
		return NULL;
	}

	irq_data->map = map;
	irq_data->chip = irq_chip;
	irq_data->num_irqs = AXP_REG_WIDTH * irq_chip->num_regs;

	irqs = kzalloc(irq_chip->num_regs * AXP_REG_WIDTH * sizeof(*irqs),
				GFP_KERNEL);
	if (IS_ERR_OR_NULL(irqs)) {
		pr_err("axp irq data: not enough memory for irq disc\n");
		goto free_irq_data;
	}

	mutex_init(&irq_data->lock);
	irq_data->irqs = irqs;
	irq_data->irqs_enabled = 0;
	irq_data->wakeup_event = wakeup_event;

	/* disable all irq and clear all irq pending */
	for (i = 0; i < irq_chip->num_regs; i++) {
		axp_regmap_clr_bits(map, irq_chip->enable_base + i, 0xff);
		axp_regmap_set_bits(map, irq_chip->status_base + i, 0xff);
	}

#ifdef CONFIG_DUAL_AXP_USED
	if (axp_dev_register_count == 1) {
		err = request_irq(irq_no, axp_irq, irq_flags, "axp", irq_data);
		goto irq_out;
	} else if (axp_dev_register_count == 2) {
		return irq_data;
	}
#else
	err = request_irq(irq_no, axp_irq, irq_flags, irq_chip->name, irq_data);
#endif

#ifdef CONFIG_DUAL_AXP_USED
irq_out:
#endif
	if (err)
		goto free_irqs;

	INIT_WORK(&axp_irq_work, axp_irq_work_func);

	sunxi_nmi_set_trigger(IRQF_TRIGGER_LOW);
	sunxi_nmi_clear_status();
	sunxi_nmi_enable();

	return irq_data;

free_irqs:
	kfree(irqs);
free_irq_data:
	kfree(irq_data);

	return NULL;
}
EXPORT_SYMBOL_GPL(axp_irq_chip_register);

void axp_irq_chip_unregister(int irq, struct axp_irq_chip_data *irq_data)
{
	int i;
	struct axp_regmap *map = irq_data->map;

	free_irq(irq, irq_data);

	/* disable all irq and clear all irq pending */
	for (i = 0; i < irq_data->chip->num_regs; i++) {
		axp_regmap_clr_bits(map,
				irq_data->chip->enable_base + i, 0xff);
		axp_regmap_write(map,
				irq_data->chip->status_base + i, 0xff);
	}

	kfree(irq_data->irqs);
	kfree(irq_data);

	sunxi_nmi_disable();
}
EXPORT_SYMBOL_GPL(axp_irq_chip_unregister);

int axp_request_irq(struct axp_dev *adev, int irq_no,
				irq_handler_t handler, void *data)
{
	struct axp_irq_chip_data *irq_data = adev->irq_data;
	struct axp_regmap_irq *irqs = NULL;
	int reg, ret;
	u8 mask;

	if (!irq_data || irq_no < 0 || irq_no >= irq_data->num_irqs || !handler)
		return -1;

	irqs = irq_data->irqs;

	mutex_lock(&irq_data->lock);
	irqs[irq_no].handler = handler;
	irqs[irq_no].data = data;
	irq_data->irqs_enabled |= ((u64)0x1 << irq_no);
	reg = irq_no / AXP_REG_WIDTH;
	reg += irq_data->chip->enable_base;
	mask = 1 << (irq_no % AXP_REG_WIDTH);
	ret = axp_regmap_set_bits(adev->regmap, reg, mask);
	mutex_unlock(&irq_data->lock);

	return ret;
}
EXPORT_SYMBOL_GPL(axp_request_irq);

int axp_enable_irq(struct axp_dev *adev, int irq_no)
{
	struct axp_irq_chip_data *irq_data = adev->irq_data;
	int reg, ret = 0;
	u8 mask;

	if (!irq_data || irq_no < 0 || irq_no >= irq_data->num_irqs)
		return -1;

	if (irq_data->irqs[irq_no].handler) {
		mutex_lock(&irq_data->lock);
		reg = irq_no / AXP_REG_WIDTH;
		reg += irq_data->chip->enable_base;
		mask = 1 << (irq_no % AXP_REG_WIDTH);
		ret = axp_regmap_set_bits(adev->regmap, reg, mask);
		mutex_unlock(&irq_data->lock);
	}

	return ret;
}
EXPORT_SYMBOL_GPL(axp_enable_irq);

int axp_disable_irq(struct axp_dev *adev, int irq_no)
{
	struct axp_irq_chip_data *irq_data = adev->irq_data;
	int reg, ret = 0;
	u8 mask;

	if (!irq_data || irq_no < 0 || irq_no >= irq_data->num_irqs)
		return -1;

	mutex_lock(&irq_data->lock);
	reg = irq_no / AXP_REG_WIDTH;
	reg += irq_data->chip->enable_base;
	mask = 1 << (irq_no % AXP_REG_WIDTH);
	ret = axp_regmap_clr_bits(adev->regmap, reg, mask);
	mutex_unlock(&irq_data->lock);

	return ret;
}
EXPORT_SYMBOL_GPL(axp_disable_irq);

int axp_free_irq(struct axp_dev *adev, int irq_no)
{
	struct axp_irq_chip_data *irq_data = adev->irq_data;
	int reg;
	u8 mask;

	if (!irq_data || irq_no < 0 || irq_no >= irq_data->num_irqs)
		return -1;

	mutex_lock(&irq_data->lock);
	if (irq_data->irqs[irq_no].handler) {
		reg = irq_no / AXP_REG_WIDTH;
		reg += irq_data->chip->enable_base;
		mask = 1 << (irq_no % AXP_REG_WIDTH);
		axp_regmap_clr_bits(adev->regmap, reg, mask);
		irq_data->irqs[irq_no].data = NULL;
		irq_data->irqs[irq_no].handler = NULL;
	}
	mutex_unlock(&irq_data->lock);

	return 0;
}
EXPORT_SYMBOL_GPL(axp_free_irq);

int axp_gpio_irq_register(struct axp_dev *adev, int irq_no,
				irq_handler_t handler, void *data)
{
	struct axp_irq_chip_data *irq_data = adev->irq_data;
	struct axp_regmap_irq *irqs = NULL;

	if (!irq_data || irq_no < 0 || irq_no >= irq_data->num_irqs || !handler)
		return -1;
	irqs = irq_data->irqs;
	mutex_lock(&irq_data->lock);
	irq_data->irqs_enabled |= ((u64)0x1 << irq_no);
	irqs[irq_no].handler = handler;
	irqs[irq_no].data = data;
	mutex_unlock(&irq_data->lock);

	return 0;
}

int axp_mfd_add_devices(struct axp_dev *axp_dev)
{
	int ret;
	unsigned long irqflags;

	ret = mfd_add_devices(axp_dev->dev, -1,
		axp_dev->cells, axp_dev->nr_cells, NULL, 0, NULL);
	if (ret)
		goto fail;

	dev_set_drvdata(axp_dev->dev, axp_dev);

	spin_lock_irqsave(&axp_list_lock, irqflags);
	list_add(&axp_dev->list, &axp_dev_list);
	axp_dev_register_count++;
	spin_unlock_irqrestore(&axp_list_lock, irqflags);

	return 0;

fail:
	return ret;
}
EXPORT_SYMBOL_GPL(axp_mfd_add_devices);

int axp_mfd_remove_devices(struct axp_dev *axp_dev)
{
	mfd_remove_devices(axp_dev->dev);

	return 0;
}
EXPORT_SYMBOL_GPL(axp_mfd_remove_devices);

int axp_dt_parse(struct device_node *node, int pmu_num,
			struct axp_config_info *axp_config)
{
	if (!of_device_is_available(node)) {
		pr_err("%s: failed\n", __func__);
		return -1;
	}

	if (of_property_read_u32(node, "pmu_id", &axp_config->pmu_id)) {
		pr_err("%s: get pmu_id failed\n", __func__);
		return -1;
	}

	if (of_property_read_string(node, "compatible", &axp_name[pmu_num])) {
		pr_err("%s: get pmu name failed\n", __func__);
		return -1;
	}

	if (of_property_read_u32(node, "pmu_vbusen_func",
		&axp_config->pmu_vbusen_func))
		axp_config->pmu_vbusen_func = 1;

	if (of_property_read_u32(node, "pmu_reset",
		&axp_config->pmu_reset))
		axp_config->pmu_reset = 0;

	if (of_property_read_u32(node, "pmu_irq_wakeup",
			&axp_config->pmu_irq_wakeup))
		axp_config->pmu_irq_wakeup = 0;

	if (of_property_read_u32(node, "pmu_hot_shutdown",
		&axp_config->pmu_hot_shutdown))
		axp_config->pmu_hot_shutdown = 1;

	if (of_property_read_u32(node, "pmu_inshort",
		&axp_config->pmu_inshort))
		axp_config->pmu_inshort = 0;

	if (of_property_read_u32(node, "pmu_reset_shutdown_en",
		&axp_config->pmu_reset_shutdown_en))
		axp_config->pmu_reset_shutdown_en = 0;

	if (of_property_read_u32(node, "pmu_as_slave",
		&axp_config->pmu_as_slave))
		axp_config->pmu_as_slave = 0;

	return 0;
}
EXPORT_SYMBOL_GPL(axp_dt_parse);

unsigned int axp_get_sys_pwr_dm_mask(void)
{
	unsigned long irqflags;
	u32 ret = 0;

	spin_lock_irqsave(&axp_pwr_data_lock, irqflags);
	ret = axp_sys_pwr_dm_mask;
	spin_unlock_irqrestore(&axp_pwr_data_lock, irqflags);

	return ret;
}

void axp_set_sys_pwr_dm_mask(u32 bitmap, u32 enable)
{
	unsigned long irqflags;

	spin_lock_irqsave(&axp_pwr_data_lock, irqflags);
	if (enable)
		axp_sys_pwr_dm_mask |= (0x1 << bitmap);
	else
		axp_sys_pwr_dm_mask &= ~(0x1 << bitmap);
	spin_unlock_irqrestore(&axp_pwr_data_lock, irqflags);

	pr_debug("%s: sys_pwr_dm_mask = 0x%x\n", __func__, axp_sys_pwr_dm_mask);
}

void axp_set_pwr_regu_tree(u32 value, u32 bitmap)
{
	unsigned long irqflags;

	spin_lock_irqsave(&axp_pwr_data_lock, irqflags);
	axp_power_tree[bitmap] = value;
	spin_unlock_irqrestore(&axp_pwr_data_lock, irqflags);

	pr_debug("%s: axp_power_tree[%d] = 0x%x\n", __func__, bitmap, value);
}

void axp_get_pwr_regu_tree(unsigned int *p)
{
	memcpy((void *)p, (void *)axp_power_tree, sizeof(axp_power_tree));
}
EXPORT_SYMBOL_GPL(axp_get_pwr_regu_tree);

s32 axp_check_sys_id(const char *supply_id)
{
	s32 i = 0;

	for (i = 0; i < VCC_MAX_INDEX; i++) {
		if (strcmp(pwr_dm_bitmap_name_mapping[i].id_name, supply_id)
				== 0)
			return i;
	}

	return -1;
}

char *axp_get_sys_id(u32 bitmap)
{
	if ((bitmap < 0) || (bitmap >= VCC_MAX_INDEX))
		return NULL;

	return (char *)&(pwr_dm_bitmap_name_mapping[bitmap].id_name);
}

s32 axp_get_ldo_dependence(const char *ldo_name, s32 index,
				s32 (*get_dep_cb)(const char *))
{
	s32 ret;

	ret = (*get_dep_cb)(ldo_name);
	if (ret < 0) {
		pr_err("%s: get regu dependence failed\n", __func__);
		return -1;
	} else {
		axp_set_pwr_regu_tree(ret, index);
	}

	return 0;
}

/*
 * function:  get sys_pwr_dm_id name.
 * input: sys_pwr_domain bitmap.
 * return:
 * failed:  NULL.
 * success: sys_pwr_dm_id.
 */
char *axp_get_sys_pwr_dm_id(u32 bitmap)
{
	return axp_get_sys_id(bitmap);
}

/*
 * function: judge whether pwr_dm is part of sys_pwr_domain.
 * input: pwr_dm name, such as: "vdd_sys".
 * return:
 * nonnegative number: the sys_pwr_domain bitmap.
 * -1: the input pwr_dm is not belong to sys_pwr_domain.
 */
int axp_is_sys_pwr_dm_id(const char *id)
{
	s32 sys_id_conut = 0;

	sys_id_conut = axp_check_sys_id(id);
	if (sys_id_conut >= 0)
		return sys_id_conut;
	else
		return -1;
}

/*
 * function: judge whether sys_pwr_domain is active.
 * input: sys_pwr_domain bitmap.
 * return:
 * 1: the input sys_pwr_domain is active.
 * 0: the input sys_pwr_domain is not active.
 */
int axp_is_sys_pwr_dm_active(u32 bitmap)
{
	u32 sys_pwr_mask = 0;

	sys_pwr_mask = axp_get_sys_pwr_dm_mask();
	if (sys_pwr_mask & (0x1 << bitmap))
		return 1;
	else
		return 0;
}

/*
 * function: add the pwr_dm specified by input id to sys_pwr_dm;
 */
int axp_add_sys_pwr_dm(const char *id)
{
	s32 ret = 0, sys_id_conut = 0;
	char ldo_name[20] = {0};
	u32 sys_pwr_mask = 0;

	sys_id_conut = axp_check_sys_id(id);
	if (sys_id_conut < 0) {
		pr_err("%s: %s not sys id.\n", __func__, id);
		return -1;
	} else {
		sys_pwr_mask = axp_get_sys_pwr_dm_mask();
		if (sys_pwr_mask & (0x1 << sys_id_conut)) {
			pr_info("%s: sys_pwr_mask=0x%x, sys_mask already set\n",
				__func__, sys_pwr_mask);
			return 1;
		}
	}

	ret = axp_get_ldo_name(id, (char *)&ldo_name);
	if (ret < 0) {
		pr_err("%s: get ldo name for id: %s failed\n", __func__, id);
		return -1;
	}

	ret = axp_check_ldo_alwayson((const char *)&ldo_name);
	if (ret == 0) {
		if (axp_set_ldo_alwayson((const char *)&ldo_name, 1)) {
			pr_err("%s: %s axp_set_ldo_alwayson failed\n",
				__func__, ldo_name);
			return -1;
		}
	} else if (ret == 1) {
		pr_err("%s: %s ldo already alwayson\n", __func__, ldo_name);
	} else {
		pr_err("%s: %s set err.\n", __func__, ldo_name);
		return -1;
	}

	axp_set_sys_pwr_dm_mask(sys_id_conut, 1);

	return 0;
}
EXPORT_SYMBOL_GPL(axp_add_sys_pwr_dm);

int axp_del_sys_pwr_dm(const char *id)
{
	s32 ret = 0, sys_id_conut = 0, i = 0;
	char ldo_name[20] = {0};
	char sys_ldo_name[20] = {0};
	u32 sys_pwr_mask = 0;
	char *sys_id;

	sys_id_conut = axp_check_sys_id(id);
	if (sys_id_conut < 0) {
		pr_err("%s: %s not sys id\n", __func__, id);
		return -1;
	} else {
		sys_pwr_mask = axp_get_sys_pwr_dm_mask();
		if (!(sys_pwr_mask & (0x1 << sys_id_conut)))
			return 1;
	}

	ret = axp_get_ldo_name(id, (char *)&ldo_name);
	if (ret < 0) {
		pr_err("%s: get ldo name for id: %s failed\n", __func__, id);
		return -1;
	}

	ret = axp_check_ldo_alwayson((const char *)&ldo_name);
	if (ret == 0) {
		pr_err("%s: %s ldo is already not alwayson\n",
				__func__, ldo_name);
	} else if (ret == 1) {
		for (i = 0; i < VCC_MAX_INDEX; i++) {
			if (sys_id_conut == i)
				continue;
			if (axp_is_sys_pwr_dm_active(i)) {
				sys_id = axp_get_sys_pwr_dm_id(i);
				ret = axp_get_ldo_name(sys_id,
						(char *)&sys_ldo_name);
				if (ret < 0) {
					pr_err("%s: get sys_ldo_name failed\n",
							__func__);
					return -1;
				}

				if (strcmp(sys_ldo_name, ldo_name) == 0) {
					axp_set_sys_pwr_dm_mask(sys_id_conut,
							0);
					return 0;
				}
			}
		}

		if (axp_set_ldo_alwayson((const char *)&ldo_name, 0)) {
			pr_err("%s: %s axp_set_ldo_alwayson failed\n",
				__func__, ldo_name);
			return -1;
		}
	} else {
		pr_err("%s: %s set err\n", __func__, ldo_name);
		return -1;
	}

	axp_set_sys_pwr_dm_mask(sys_id_conut, 0);

	return 0;
}
EXPORT_SYMBOL_GPL(axp_del_sys_pwr_dm);

int init_sys_pwr_dm(void)
{
	axp_add_sys_pwr_dm("vdd-cpua");
	/*axp_add_sys_pwr_dm("vdd-cpub"); */
	axp_add_sys_pwr_dm("vcc-dram");
	/*axp_add_sys_pwr_dm("vdd-gpu"); */
	axp_add_sys_pwr_dm("vdd-sys");
	/*axp_add_sys_pwr_dm("vdd-vpu"); */
	axp_add_sys_pwr_dm("vdd-cpus");
	/*axp_add_sys_pwr_dm("vdd-drampll"); */
	axp_add_sys_pwr_dm("vcc-lpddr");
	/*axp_add_sys_pwr_dm("vcc-adc"); */
	axp_add_sys_pwr_dm("vcc-pl");
	/*axp_add_sys_pwr_dm("vcc-pm"); */
	axp_add_sys_pwr_dm("vcc-io");
	/*axp_add_sys_pwr_dm("vcc-cpvdd"); */
	/*axp_add_sys_pwr_dm("vcc-ldoin"); */
	axp_add_sys_pwr_dm("vcc-pll");
	axp_add_sys_pwr_dm("vcc-pc");

	return 0;
}

EXPORT_SYMBOL_GPL(init_sys_pwr_dm);

MODULE_DESCRIPTION("ALLWINNERTECH axp core");
MODULE_AUTHOR("pannan");
MODULE_LICENSE("GPL");
