/*
 * drivers/power/axp/axp-charger.c
 * (C) Copyright 2010-2016
 * Allwinner Technology Co., Ltd. <www.allwinnertech.com>
 * Pannan <pannan@allwinnertech.com>
 *
 * axp charger APIs
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License, or (at your option) any later version.
 *
 */

#include <linux/kernel.h>
#include <linux/init.h>
#include <linux/delay.h>
#include <linux/device.h>
#include <linux/module.h>
#include <linux/platform_device.h>
#include <linux/workqueue.h>
#include <linux/types.h>
#include <linux/slab.h>
#include <linux/power_supply.h>
#include <linux/of_device.h>
#include <linux/interrupt.h>
#include "axp-core.h"
#include "axp-charger.h"

static int axp_power_key;
static enum AW_CHARGE_TYPE axp_usbcurflag = CHARGE_AC;
static enum AW_CHARGE_TYPE axp_usbvolflag = CHARGE_AC;

static struct axp_adc_res adc;
static bool battery_initialized;
static struct axp_config_info *axp_config_obj;

static DEFINE_SPINLOCK(axp_powerkey_lock);

void axp_powerkey_set(int value)
{
	spin_lock(&axp_powerkey_lock);
	axp_power_key = value;
	spin_unlock(&axp_powerkey_lock);
}
EXPORT_SYMBOL_GPL(axp_powerkey_set);

int axp_powerkey_get(void)
{
	int value;

	spin_lock(&axp_powerkey_lock);
	value = axp_power_key;
	spin_unlock(&axp_powerkey_lock);

	return value;
}
EXPORT_SYMBOL_GPL(axp_powerkey_get);

int axp_usbvol(enum AW_CHARGE_TYPE type)
{
	axp_usbvolflag = type;
	return 0;
}
EXPORT_SYMBOL_GPL(axp_usbvol);

int axp_usbcur(enum AW_CHARGE_TYPE type)
{
	axp_usbcurflag = type;
	return 0;
}
EXPORT_SYMBOL_GPL(axp_usbcur);

static inline void axp_read_adc(struct axp_charger_dev *chg_dev,
		struct axp_adc_res *adc)
{
	u8 tmp[2];
	struct axp_regmap *map = chg_dev->chip->regmap;

	axp_regmap_reads(map, chg_dev->spy_info->batt->bat_temp_offset,
				2, tmp);
	adc->ts_res = ((u16) tmp[0] << 8) | tmp[1];
}

static inline s32 axp_vts_to_temp(s32 data,
		const struct axp_config_info *axp_config)
{
	s32 temp;

	if (data < 80)
		return 30;
	else if (data < axp_config->pmu_bat_temp_para16)
		return 80;
	else if (data <= axp_config->pmu_bat_temp_para15) {
		temp = 70 + (axp_config->pmu_bat_temp_para15-data)*10/
		(axp_config->pmu_bat_temp_para15-axp_config->pmu_bat_temp_para16);
	} else if (data <= axp_config->pmu_bat_temp_para14) {
		temp = 60 + (axp_config->pmu_bat_temp_para14-data)*10/
		(axp_config->pmu_bat_temp_para14-axp_config->pmu_bat_temp_para15);
	} else if (data <= axp_config->pmu_bat_temp_para13) {
		temp = 55 + (axp_config->pmu_bat_temp_para13-data)*5/
		(axp_config->pmu_bat_temp_para13-axp_config->pmu_bat_temp_para14);
	} else if (data <= axp_config->pmu_bat_temp_para12) {
		temp = 50 + (axp_config->pmu_bat_temp_para12-data)*5/
		(axp_config->pmu_bat_temp_para12-axp_config->pmu_bat_temp_para13);
	} else if (data <= axp_config->pmu_bat_temp_para11) {
		temp = 45 + (axp_config->pmu_bat_temp_para11-data)*5/
		(axp_config->pmu_bat_temp_para11-axp_config->pmu_bat_temp_para12);
	} else if (data <= axp_config->pmu_bat_temp_para10) {
		temp = 40 + (axp_config->pmu_bat_temp_para10-data)*5/
		(axp_config->pmu_bat_temp_para10-axp_config->pmu_bat_temp_para11);
	} else if (data <= axp_config->pmu_bat_temp_para9) {
		temp = 30 + (axp_config->pmu_bat_temp_para9-data)*10/
		(axp_config->pmu_bat_temp_para9-axp_config->pmu_bat_temp_para10);
	} else if (data <= axp_config->pmu_bat_temp_para8) {
		temp = 20 + (axp_config->pmu_bat_temp_para8-data)*10/
		(axp_config->pmu_bat_temp_para8-axp_config->pmu_bat_temp_para9);
	} else if (data <= axp_config->pmu_bat_temp_para7) {
		temp = 10 + (axp_config->pmu_bat_temp_para7-data)*10/
		(axp_config->pmu_bat_temp_para7-axp_config->pmu_bat_temp_para8);
	} else if (data <= axp_config->pmu_bat_temp_para6) {
		temp = 5 + (axp_config->pmu_bat_temp_para6-data)*5/
		(axp_config->pmu_bat_temp_para6-axp_config->pmu_bat_temp_para7);
	} else if (data <= axp_config->pmu_bat_temp_para5) {
		temp = 0 + (axp_config->pmu_bat_temp_para5-data)*5/
		(axp_config->pmu_bat_temp_para5-axp_config->pmu_bat_temp_para6);
	} else if (data <= axp_config->pmu_bat_temp_para4) {
		temp = -5 + (axp_config->pmu_bat_temp_para4-data)*5/
		(axp_config->pmu_bat_temp_para4-axp_config->pmu_bat_temp_para5);
	} else if (data <= axp_config->pmu_bat_temp_para3) {
		temp = -10 + (axp_config->pmu_bat_temp_para3-data)*5/
		(axp_config->pmu_bat_temp_para3-axp_config->pmu_bat_temp_para4);
	} else if (data <= axp_config->pmu_bat_temp_para2) {
		temp = -15 + (axp_config->pmu_bat_temp_para2-data)*5/
		(axp_config->pmu_bat_temp_para2-axp_config->pmu_bat_temp_para3);
	} else if (data <= axp_config->pmu_bat_temp_para1) {
		temp = -25 + (axp_config->pmu_bat_temp_para1-data)*10/
		(axp_config->pmu_bat_temp_para1-axp_config->pmu_bat_temp_para2);
	} else
		temp = -25;
	return temp;
}

static inline s32 axp_vts_to_mV(u16 reg)
{
	return ((s32)(((reg >> 8) << 4) | (reg & 0x000F))) * 800 / 1000;
}

static inline void axp_update_ictemp_status(struct axp_charger_dev *chg_dev)
{
	u16 tmp;
	u8 temp_val[2];
	struct axp_regmap *map = chg_dev->chip->regmap;

	axp_regmap_reads(map, chg_dev->pmic_temp_offset, 2, temp_val);
	tmp = (temp_val[0] << 4) + (temp_val[1] & 0x0F);
	chg_dev->ic_temp = (s32) tmp * 1063 / 10000  - 2667 / 10;

}

static inline void axp_update_temp_status(struct axp_charger_dev *chg_dev)
{
	u16 tmp;
	u8 temp_val[2];
	s32 bat_temp_mv;
	struct axp_regmap *map = chg_dev->chip->regmap;

	chg_dev->adc = &adc;
	axp_read_adc(chg_dev, &adc);

	axp_regmap_reads(map, chg_dev->pmic_temp_offset, 2, temp_val);
	tmp = (temp_val[0] << 4) + (temp_val[1] & 0x0F);
	chg_dev->ic_temp = (s32) tmp * 1063 / 10000  - 2667 / 10;

	tmp = chg_dev->adc->ts_res;
	bat_temp_mv = axp_vts_to_mV(tmp);
	chg_dev->bat_temp = axp_vts_to_temp(bat_temp_mv, axp_config_obj);

}

/*
 * acin not presence + vbus no presence -> battery presence
 */
static int pwrsrc_parse_bat_det(struct axp_battery_info *batt, u8 val)
{
	if (!(val & ((1 << batt->acpresent_bit) | (1 << batt->vbuspresent_bit))))
		return 1;
	else
		return 0;
}

static int det_parse_bat_det(struct axp_battery_info *batt, u8 val)
{
	if ((val & (1 << batt->det_bit)) && (val & (1 << batt->det_valid_bit)))
		return 1;
	else
		return 0;
}

void axp_charger_update_state(struct axp_charger_dev *chg_dev)
{
	u8 val;
	u8 pwrsrc;
	struct axp_ac_info *ac = chg_dev->spy_info->ac;
	struct axp_usb_info *usb = chg_dev->spy_info->usb;
	struct axp_battery_info *batt = chg_dev->spy_info->batt;
	struct axp_regmap *map = chg_dev->chip->regmap;

	axp_regmap_read(map, batt->det_offset, &val);
	axp_regmap_read(map, batt->pwrsrc_offset, &pwrsrc);

	mutex_lock(&chg_dev->charger_lock);
	if (batt->det_unused == 0) {
		if (batt->det_valid == 1) {
			chg_dev->bat_det = pwrsrc_parse_bat_det(batt, pwrsrc);
			if (chg_dev->bat_det == 0)
				chg_dev->bat_det = det_parse_bat_det(batt, val);
		} else if (batt->det_valid == 0) {
			chg_dev->bat_det = (val & 1 << batt->det_bit) ? 1 : 0;
		}
	} else if (batt->det_unused == 1) {
		chg_dev->bat_det = 0;
	}
	mutex_unlock(&chg_dev->charger_lock);

	axp_regmap_read(map, ac->det_offset, &val);
	mutex_lock(&chg_dev->charger_lock);
	chg_dev->ac_det = (val & 1 << ac->det_bit) ? 1 : 0;
	mutex_unlock(&chg_dev->charger_lock);

	if (usb->det_unused == 0) {
		axp_regmap_read(map, usb->det_offset, &val);
		mutex_lock(&chg_dev->charger_lock);
		chg_dev->usb_det = (val & 1 << usb->det_bit) ? 1 : 0;
		mutex_unlock(&chg_dev->charger_lock);
	} else if (usb->det_unused == 1) {
		chg_dev->usb_det = 0;
	}

	axp_regmap_read(map, ac->valid_offset, &val);
	mutex_lock(&chg_dev->charger_lock);
	chg_dev->ac_valid = (val & 1 << ac->valid_bit) ? 1 : 0;
	mutex_unlock(&chg_dev->charger_lock);

	if (usb->det_unused == 0) {
		axp_regmap_read(map, usb->valid_offset, &val);
		mutex_lock(&chg_dev->charger_lock);
		chg_dev->usb_valid = (val & 1 << usb->valid_bit) ? 1 : 0;
		mutex_unlock(&chg_dev->charger_lock);
	} else if (usb->det_unused == 1) {
		chg_dev->usb_valid = 0;
	}

	chg_dev->ext_valid = (chg_dev->ac_det || chg_dev->usb_det);

	axp_regmap_read(map, ac->in_short_offset, &val);
	mutex_lock(&chg_dev->charger_lock);
	chg_dev->in_short = (val & 1 << ac->in_short_bit) ? 1 : 0;
	if (!chg_dev->in_short)
		chg_dev->ac_charging = chg_dev->ac_valid;
	mutex_unlock(&chg_dev->charger_lock);

	axp_regmap_read(map, batt->cur_direction_offset, &val);
	mutex_lock(&chg_dev->charger_lock);
	if (val & 1 << batt->cur_direction_bit)
		chg_dev->bat_current_direction = 1;
	else
		chg_dev->bat_current_direction = 0;
	mutex_unlock(&chg_dev->charger_lock);

	axp_regmap_read(map, batt->chgstat_offset, &val);
	mutex_lock(&chg_dev->charger_lock);
	chg_dev->charging = (val & 1 << batt->chgstat_bit) ? 1 : 0;
	mutex_unlock(&chg_dev->charger_lock);
}


void axp_charger_update_value(struct axp_charger_dev *chg_dev)
{
	struct axp_ac_info *ac = chg_dev->spy_info->ac;
	struct axp_usb_info *usb = chg_dev->spy_info->usb;
	struct axp_battery_info *batt = chg_dev->spy_info->batt;
	int bat_vol, bat_cur, bat_discur, ac_vol, ac_cur, usb_vol, usb_cur;

	bat_vol = batt->get_vbat(chg_dev);
	bat_cur = batt->get_ibat(chg_dev);
	bat_discur = batt->get_disibat(chg_dev);
	ac_vol  = ac->get_ac_voltage(chg_dev);
	ac_cur  = ac->get_ac_current(chg_dev);
	usb_vol = usb->get_usb_voltage(chg_dev);
	usb_cur = usb->get_usb_current(chg_dev);

	mutex_lock(&chg_dev->charger_lock);
	chg_dev->bat_vol = bat_vol;
	chg_dev->bat_cur = bat_cur;
	chg_dev->bat_discur = bat_discur;
	chg_dev->ac_vol  = ac_vol;
	chg_dev->ac_cur  = ac_cur;
	chg_dev->usb_vol = usb_vol;
	chg_dev->usb_cur = usb_cur;
	mutex_unlock(&chg_dev->charger_lock);
}

static void axp_usb_ac_check_status(struct axp_charger_dev *chg_dev)
{
	chg_dev->usb_pc_charging = (((CHARGE_USB_20 == axp_usbcurflag)
					|| (CHARGE_USB_30 == axp_usbcurflag))
					&& (chg_dev->ext_valid));
	chg_dev->usb_adapter_charging = ((0 == chg_dev->ac_valid)
					&& (chg_dev->usb_valid == 1)
					&& (CHARGE_USB_20 != axp_usbcurflag)
					&& (CHARGE_USB_30 != axp_usbcurflag)
					&& (chg_dev->ext_valid));
	if (chg_dev->in_short)
		chg_dev->ac_charging = ((chg_dev->usb_adapter_charging == 0)
					&& (chg_dev->ac_valid == 1)
					&& (chg_dev->usb_pc_charging == 0)
					&& (chg_dev->ext_valid));
	else
		chg_dev->ac_charging = chg_dev->ac_valid;

	power_supply_changed(chg_dev->ac);
	power_supply_changed(chg_dev->usb);
	AXP_DEBUG(AXP_CHG, chg_dev->chip->pmu_num,
			"ac_valid:%d usb_valid:%d in_short:%d\n",
			chg_dev->ac_valid, chg_dev->usb_valid, chg_dev->in_short);
	AXP_DEBUG(AXP_CHG, chg_dev->chip->pmu_num,
			"ac_charging=%d\n", chg_dev->ac_charging);
	AXP_DEBUG(AXP_CHG, chg_dev->chip->pmu_num,
			"usb_pc_charging=%d\n", chg_dev->usb_pc_charging);
	AXP_DEBUG(AXP_CHG, chg_dev->chip->pmu_num,
			"usb_adapter_charging=%d\n",
			chg_dev->usb_adapter_charging);
	AXP_DEBUG(AXP_CHG, chg_dev->chip->pmu_num,
			"usb_det=%d ac_det=%d\n",
			chg_dev->usb_det, chg_dev->ac_det);
}

static void axp_charger_update_usb_state(unsigned long data)
{
	struct axp_charger_dev *chg_dev = (struct axp_charger_dev *)data;

	axp_usb_ac_check_status(chg_dev);

	if (chg_dev->bat_det)
		schedule_delayed_work(&(chg_dev->usbwork), 0);
}

static void axp_usb(struct work_struct *work)
{
	struct axp_charger_dev *chg_dev = container_of(work,
					struct axp_charger_dev, usbwork.work);
	struct axp_usb_info *usb = chg_dev->spy_info->usb;
	struct axp_ac_info *ac = chg_dev->spy_info->ac;

	AXP_DEBUG(AXP_CHG, chg_dev->chip->pmu_num,
				"[axp_usb] axp_usbcurflag = %d\n",
				axp_usbcurflag);
	axp_charger_update_state(chg_dev);

	if (chg_dev->in_short) {
		/* usb and ac in short*/
		if (!chg_dev->usb_valid) {
			/*usb or usb adapter can not be used*/
			AXP_DEBUG(AXP_CHG, chg_dev->chip->pmu_num,
				"USB not insert!\n");
			usb->set_usb_ihold(chg_dev, 500);
		} else if (CHARGE_USB_20 == axp_usbcurflag) {
			if (usb->usb_pc_cur) {
				AXP_DEBUG(AXP_CHG, chg_dev->chip->pmu_num,
						"set usb_pc_cur %d mA\n",
						usb->usb_pc_cur);
				usb->set_usb_ihold(chg_dev, usb->usb_pc_cur);
			} else {
				AXP_DEBUG(AXP_CHG, chg_dev->chip->pmu_num,
						"set usb_pc_cur 500 mA\n");
				usb->set_usb_ihold(chg_dev, 500);
			}
		} else if (CHARGE_USB_30 == axp_usbcurflag) {
			AXP_DEBUG(AXP_CHG, chg_dev->chip->pmu_num,
						"set usb_pc_cur 900 mA\n");
			usb->set_usb_ihold(chg_dev, 900);
		} else {
			/* usb adapter */
			if (usb->usb_ad_cur) {
				AXP_DEBUG(AXP_CHG, chg_dev->chip->pmu_num,
						"set usb_ad_cur %d mA\n",
							usb->usb_ad_cur);
			} else {
				AXP_DEBUG(AXP_CHG, chg_dev->chip->pmu_num,
						"set usb_ad_cur no limit\n");
			}
			usb->set_usb_ihold(chg_dev, usb->usb_ad_cur);
		}

		if (CHARGE_USB_20 == axp_usbvolflag) {
			if (usb->usb_pc_vol) {
				AXP_DEBUG(AXP_CHG, chg_dev->chip->pmu_num,
						"set usb_pc_vol %d mV\n",
							usb->usb_pc_vol);
				usb->set_usb_vhold(chg_dev, usb->usb_pc_vol);
			}
		} else if (CHARGE_USB_30 == axp_usbvolflag) {
			AXP_DEBUG(AXP_CHG, chg_dev->chip->pmu_num,
						"set usb_pc_vol 4700 mV\n");
			usb->set_usb_vhold(chg_dev, 4700);
		} else {
			if (usb->usb_ad_vol) {
				AXP_DEBUG(AXP_CHG, chg_dev->chip->pmu_num,
						"set usb_ad_vol %d mV\n",
							usb->usb_ad_vol);
				usb->set_usb_vhold(chg_dev, usb->usb_ad_vol);
			}
		}
	} else {
		if (!chg_dev->ac_valid && !chg_dev->usb_valid) {
			/*usb and ac can not be used*/
			AXP_DEBUG(AXP_CHG, chg_dev->chip->pmu_num,
						"AC and USB not insert!\n");
			usb->set_usb_ihold(chg_dev, 500);
		} else if (CHARGE_USB_20 == axp_usbcurflag) {
			if (usb->usb_pc_cur) {
				AXP_DEBUG(AXP_CHG, chg_dev->chip->pmu_num,
						"set usb_pc_cur %d mA\n",
							usb->usb_pc_cur);
				usb->set_usb_ihold(chg_dev, usb->usb_pc_cur);
			} else {
				AXP_DEBUG(AXP_CHG, chg_dev->chip->pmu_num,
						"set usb_pc_cur 500 mA\n");
				usb->set_usb_ihold(chg_dev, 500);
			}
		} else if (CHARGE_USB_30 == axp_usbcurflag) {
			AXP_DEBUG(AXP_CHG, chg_dev->chip->pmu_num,
						"set usb_pc_cur 900 mA\n");
			usb->set_usb_ihold(chg_dev, 900);
		} else {
			if ((usb->usb_ad_cur)) {
				AXP_DEBUG(AXP_CHG,
						chg_dev->chip->pmu_num,
						"set adapter cur %d mA\n",
						usb->usb_ad_cur);
			} else {
				AXP_DEBUG(AXP_CHG,
						chg_dev->chip->pmu_num,
						"set adapter cur no limit\n");
			}
			usb->set_usb_ihold(chg_dev, usb->usb_ad_cur);
		}

		if (CHARGE_USB_20 == axp_usbvolflag) {
			if (usb->usb_pc_vol) {
				AXP_DEBUG(AXP_CHG, chg_dev->chip->pmu_num,
						"set usb_pc_vol %d mV\n",
							usb->usb_pc_vol);
				usb->set_usb_vhold(chg_dev, usb->usb_pc_vol);
			}
		} else if (CHARGE_USB_30 == axp_usbvolflag) {
			AXP_DEBUG(AXP_CHG, chg_dev->chip->pmu_num,
						"set usb_pc_vol 4700 mV\n");
			usb->set_usb_vhold(chg_dev, 4700);
		} else {
			if (ac->ac_vol) {
				AXP_DEBUG(AXP_CHG, chg_dev->chip->pmu_num,
						"set ac_vol %d mV\n",
							ac->ac_vol);
				ac->set_ac_vhold(chg_dev, ac->ac_vol);
			}
		}
	}
}

void axp_battery_update_vol(struct axp_charger_dev *chg_dev)
{
	s32 rest_vol = 0;
	struct axp_battery_info *batt = chg_dev->spy_info->batt;

	rest_vol = batt->get_rest_cap(chg_dev);

	mutex_lock(&chg_dev->charger_lock);
	if (rest_vol > 100) {
		AXP_DEBUG(AXP_SPLY, chg_dev->chip->pmu_num,
			"AXP rest_vol = %d\n", rest_vol);
		chg_dev->rest_vol = 100;
	} else {
		chg_dev->rest_vol = rest_vol;
	}
	mutex_unlock(&chg_dev->charger_lock);

	AXP_DEBUG(AXP_SPLY, chg_dev->chip->pmu_num,
			"charger->rest_vol = %d\n", chg_dev->rest_vol);
}

static enum power_supply_property axp_battery_props[] = {
	POWER_SUPPLY_PROP_MODEL_NAME,
	POWER_SUPPLY_PROP_STATUS,
	POWER_SUPPLY_PROP_PRESENT,
	POWER_SUPPLY_PROP_ONLINE,
	POWER_SUPPLY_PROP_HEALTH,
	POWER_SUPPLY_PROP_TECHNOLOGY,
	POWER_SUPPLY_PROP_CHARGE_COUNTER,
	POWER_SUPPLY_PROP_VOLTAGE_MAX_DESIGN,
	POWER_SUPPLY_PROP_VOLTAGE_MIN_DESIGN,
	POWER_SUPPLY_PROP_VOLTAGE_NOW,
	POWER_SUPPLY_PROP_CURRENT_NOW,
	POWER_SUPPLY_PROP_ENERGY_FULL_DESIGN,
	POWER_SUPPLY_PROP_CAPACITY,
	POWER_SUPPLY_PROP_TEMP,
};

static enum power_supply_property axp_ac_props[] = {
	POWER_SUPPLY_PROP_MODEL_NAME,
	POWER_SUPPLY_PROP_PRESENT,
	POWER_SUPPLY_PROP_ONLINE,
	POWER_SUPPLY_PROP_VOLTAGE_NOW,
	POWER_SUPPLY_PROP_CURRENT_NOW,
};

static enum power_supply_property axp_usb_props[] = {
	POWER_SUPPLY_PROP_MODEL_NAME,
	POWER_SUPPLY_PROP_PRESENT,
	POWER_SUPPLY_PROP_ONLINE,
	POWER_SUPPLY_PROP_VOLTAGE_NOW,
	POWER_SUPPLY_PROP_CURRENT_NOW,
};

static void axp_battery_check_status(struct axp_charger_dev *chg_dev,
					union power_supply_propval *val)
{
	if (chg_dev->bat_det) {
		if (chg_dev->ext_valid) {
			if (chg_dev->rest_vol == 100)
				val->intval = POWER_SUPPLY_STATUS_FULL;
			else if (chg_dev->charging)
				val->intval = POWER_SUPPLY_STATUS_CHARGING;
			else
				val->intval = POWER_SUPPLY_STATUS_NOT_CHARGING;
		} else {
			val->intval = POWER_SUPPLY_STATUS_DISCHARGING;
		}
	} else {
		val->intval = POWER_SUPPLY_STATUS_FULL;
	}
}

static void axp_battery_check_health(struct axp_charger_dev *chg_dev,
					union power_supply_propval *val)
{
	struct axp_battery_info *batt = chg_dev->spy_info->batt;
	val->intval = batt->get_bat_health(chg_dev);
}

static s32 axp_battery_get_property(struct power_supply *psy,
					enum power_supply_property psp,
					union power_supply_propval *val)
{
	struct axp_charger_dev *chg_dev = power_supply_get_drvdata(psy);
	s32 ret = 0;

	switch (psp) {
	case POWER_SUPPLY_PROP_STATUS:
		axp_battery_check_status(chg_dev, val);
		break;
	case POWER_SUPPLY_PROP_HEALTH:
		axp_battery_check_health(chg_dev, val);
		break;
	case POWER_SUPPLY_PROP_TECHNOLOGY:
		val->intval = chg_dev->battery_info->technology;
		break;
	case POWER_SUPPLY_PROP_CHARGE_COUNTER:
		val->intval = chg_dev->coulumb_counter * 1000;
		break;
	case POWER_SUPPLY_PROP_VOLTAGE_MAX_DESIGN:
		val->intval = chg_dev->battery_info->voltage_max_design;
		break;
	case POWER_SUPPLY_PROP_VOLTAGE_MIN_DESIGN:
		val->intval = chg_dev->battery_info->voltage_min_design;
		break;
	case POWER_SUPPLY_PROP_VOLTAGE_NOW:
		val->intval = chg_dev->bat_vol * 1000;
		break;
	case POWER_SUPPLY_PROP_CURRENT_NOW:
		val->intval = (chg_dev->bat_cur - chg_dev->bat_discur) * 1000;
		break;
	case POWER_SUPPLY_PROP_MODEL_NAME:
		val->strval = psy->desc->name;
		break;
	case POWER_SUPPLY_PROP_ENERGY_FULL_DESIGN:
		val->intval = chg_dev->battery_info->energy_full_design;
		break;
	case POWER_SUPPLY_PROP_CAPACITY:
		val->intval = chg_dev->rest_vol;
		break;
	case POWER_SUPPLY_PROP_ONLINE: {
		/* in order to get hardware state,
		 * we must update charger state now.
		 * by sunny at 2012-12-23 11:06:15.
		 */
		axp_charger_update_state(chg_dev);
		val->intval = !chg_dev->bat_current_direction;
		break;
	}
	case POWER_SUPPLY_PROP_PRESENT:
		val->intval = chg_dev->bat_det;
		break;
	case POWER_SUPPLY_PROP_TEMP:
		val->intval = chg_dev->bat_temp * 10;
		break;
	default:
		ret = -EINVAL;
		break;
	}

	return ret;
}

static s32 axp_ac_get_property(struct power_supply *psy,
					enum power_supply_property psp,
					union power_supply_propval *val)
{
	struct axp_charger_dev *chg_dev = power_supply_get_drvdata(psy);
	s32 ret = 0;

	switch (psp) {
	case POWER_SUPPLY_PROP_MODEL_NAME:
		val->strval = psy->desc->name;
		break;
	case POWER_SUPPLY_PROP_PRESENT:
	case POWER_SUPPLY_PROP_ONLINE:
		val->intval = (chg_dev->ac_charging
					|| chg_dev->usb_adapter_charging);
		break;
	case POWER_SUPPLY_PROP_VOLTAGE_NOW:
		val->intval = chg_dev->ac_vol * 1000;
		break;
	case POWER_SUPPLY_PROP_CURRENT_NOW:
		val->intval = chg_dev->ac_cur * 1000;
		break;
	default:
		ret = -EINVAL;
		break;
	}

	return ret;
}

static s32 axp_usb_get_property(struct power_supply *psy,
					enum power_supply_property psp,
					union power_supply_propval *val)
{
	struct axp_charger_dev *chg_dev = power_supply_get_drvdata(psy);
	s32 ret = 0;

	switch (psp) {
	case POWER_SUPPLY_PROP_MODEL_NAME:
		val->strval = psy->desc->name;
		break;
	case POWER_SUPPLY_PROP_PRESENT:
	case POWER_SUPPLY_PROP_ONLINE:
		val->intval = chg_dev->usb_pc_charging;
		break;
	case POWER_SUPPLY_PROP_VOLTAGE_NOW:
		val->intval = chg_dev->usb_vol * 1000;
		break;
	case POWER_SUPPLY_PROP_CURRENT_NOW:
		val->intval = chg_dev->usb_cur * 1000;
		break;
	default:
		ret = -EINVAL;
		break;
	}

	return ret;
}

static char *supply_list[] = {
	"battery",
};
static const struct power_supply_desc batt_desc = {
	.name = "battery",
	.type = POWER_SUPPLY_TYPE_BATTERY,
	.get_property = axp_battery_get_property,
	.properties = axp_battery_props,
	.num_properties = ARRAY_SIZE(axp_battery_props),
	.use_for_apm = 1,
};

static const struct power_supply_desc ac_desc = {
	.name = "ac",
	.type = POWER_SUPPLY_TYPE_MAINS,
	.get_property = axp_ac_get_property,
	.properties = axp_ac_props,
	.num_properties = ARRAY_SIZE(axp_ac_props),
};

static const struct power_supply_desc usb_desc = {
	.name = "usb",
	.type = POWER_SUPPLY_TYPE_USB,
	.get_property = axp_usb_get_property,
	.properties = axp_usb_props,
	.num_properties = ARRAY_SIZE(axp_usb_props),
};

static struct power_supply_config psy_cfg = {
	.supplied_to = supply_list,
	.num_supplicants = ARRAY_SIZE(supply_list),
};

static void axp_charging_monitor(struct work_struct *work)
{
	struct axp_charger_dev *chg_dev = container_of(work,
					struct axp_charger_dev, work.work);
	struct power_supply_config psy_cfg = {};
	static s32 pre_rest_vol;
	static bool pre_bat_curr_dir;
	static int pre_axp_usbvolflag;
	axp_charger_update_state(chg_dev);

	/* if no battery exist, then return */
	if (!chg_dev->bat_det) {

		axp_update_ictemp_status(chg_dev);

		AXP_DEBUG(AXP_MISC, chg_dev->chip->pmu_num,
				"charger->ic_temp = %d\n", chg_dev->ic_temp);
		schedule_delayed_work(&chg_dev->work, chg_dev->interval);
		return;
	}
	/* if battery hadn't been detectd before, register it as power supply
	 * now */
	if (!battery_initialized) {
			psy_cfg.drv_data = chg_dev;
			chg_dev->batt = power_supply_register(chg_dev->dev,
					&batt_desc, &psy_cfg);
			battery_initialized = true;
	}

	axp_charger_update_value(chg_dev);
	axp_update_temp_status(chg_dev);

	AXP_DEBUG(AXP_SPLY, chg_dev->chip->pmu_num,
			"charger->ic_temp = %d\n", chg_dev->ic_temp);
	AXP_DEBUG(AXP_SPLY, chg_dev->chip->pmu_num,
			"charger->bat_temp = %d\n", chg_dev->bat_temp);
	AXP_DEBUG(AXP_SPLY, chg_dev->chip->pmu_num,
			"charger->bat_vol = %d\n", chg_dev->bat_vol);
	AXP_DEBUG(AXP_SPLY, chg_dev->chip->pmu_num,
			"charger->bat_cur = %d\n", chg_dev->bat_cur);
	AXP_DEBUG(AXP_SPLY, chg_dev->chip->pmu_num,
			"charger->bat_discur = %d\n", chg_dev->bat_discur);
	AXP_DEBUG(AXP_SPLY, chg_dev->chip->pmu_num,
			"charger->is_charging = %d\n", chg_dev->charging);
	AXP_DEBUG(AXP_SPLY, chg_dev->chip->pmu_num,
			"charger->bat_current_direction = %d\n",
			chg_dev->bat_current_direction);
	AXP_DEBUG(AXP_SPLY, chg_dev->chip->pmu_num,
			"charger->ext_valid = %d\n", chg_dev->ext_valid);

	if (chg_dev->private_debug)
		chg_dev->private_debug(chg_dev);

	axp_battery_update_vol(chg_dev);

	/* if battery volume changed, inform uevent */
	if ((chg_dev->rest_vol - pre_rest_vol)
			|| (chg_dev->bat_current_direction != pre_bat_curr_dir)
		) {
		AXP_DEBUG(AXP_SPLY, chg_dev->chip->pmu_num,
				"battery vol change: %d->%d\n",
				pre_rest_vol, chg_dev->rest_vol);
		pre_rest_vol = chg_dev->rest_vol;
		pre_bat_curr_dir = chg_dev->bat_current_direction;
		power_supply_changed(chg_dev->batt);
	}
	if (axp_usbvolflag != pre_axp_usbvolflag) {
		AXP_DEBUG(AXP_SPLY, chg_dev->chip->pmu_num,
				"axp_usbvolflag vol change: %d->%d\n",
				pre_axp_usbvolflag, axp_usbvolflag);
		pre_axp_usbvolflag = axp_usbvolflag;
		if (timer_pending(&chg_dev->usb_status_timer))
			del_timer_sync(&chg_dev->usb_status_timer);
		mod_timer(&chg_dev->usb_status_timer,
			jiffies + msecs_to_jiffies(5 * 1000));
	}


	/* reschedule for the next time */
	schedule_delayed_work(&chg_dev->work, chg_dev->interval);
}

void axp_change(struct axp_charger_dev *chg_dev)
{
	AXP_DEBUG(AXP_INT, chg_dev->chip->pmu_num, "battery state change\n");
	axp_charger_update_state(chg_dev);
	axp_charger_update_value(chg_dev);
	if (chg_dev->bat_det && battery_initialized)
		power_supply_changed(chg_dev->batt);
}
EXPORT_SYMBOL_GPL(axp_change);

void axp_usbac_in(struct axp_charger_dev *chg_dev)
{
	struct axp_usb_info *usb = chg_dev->spy_info->usb;

	axp_usbcur(CHARGE_AC);
	axp_usbvol(CHARGE_AC);

	AXP_DEBUG(AXP_CHG, chg_dev->chip->pmu_num, "axp ac/usb in!\n");

	if (timer_pending(&chg_dev->usb_status_timer))
		del_timer_sync(&chg_dev->usb_status_timer);

	/* must limit the current now,
	 * and will again fix it while usb/ac detect finished!
	*/
	if (usb->usb_pc_cur)
		usb->set_usb_ihold(chg_dev, usb->usb_pc_cur);
	else
		usb->set_usb_ihold(chg_dev, 500);

	/* this is about 3.5s,
	* while the flag set in usb drivers after usb plugged
	*/
	mod_timer(&chg_dev->usb_status_timer,
				jiffies + msecs_to_jiffies(5000));
	axp_usb_ac_check_status(chg_dev);
}
EXPORT_SYMBOL_GPL(axp_usbac_in);

void axp_usbac_out(struct axp_charger_dev *chg_dev)
{
	AXP_DEBUG(AXP_CHG, chg_dev->chip->pmu_num, "axp ac/usb out!\n");

	if (timer_pending(&chg_dev->usb_status_timer))
		del_timer_sync(&chg_dev->usb_status_timer);

	/* if we plugged usb & ac at the same time,
	 * then unpluged ac quickly while the usb driver
	 * do not finished detecting,
	 * the charger type is error!So delay the charger type report 2s
	*/
	mod_timer(&chg_dev->usb_status_timer,
					jiffies + msecs_to_jiffies(2000));
	axp_usb_ac_check_status(chg_dev);
}
EXPORT_SYMBOL_GPL(axp_usbac_out);

void axp_capchange(struct axp_charger_dev *chg_dev)
{
	struct power_supply_config psy_cfg = {};

	AXP_DEBUG(AXP_INT, chg_dev->chip->pmu_num, "battery change\n");

	axp_charger_update_state(chg_dev);
	axp_charger_update_value(chg_dev);
	axp_battery_update_vol(chg_dev);

	if (chg_dev->bat_det) {
		AXP_DEBUG(AXP_INT, chg_dev->chip->pmu_num, "rest_vol = %d\n",
				chg_dev->rest_vol);
		if (!battery_initialized) {
			psy_cfg.drv_data = chg_dev;
			chg_dev->batt = power_supply_register(chg_dev->dev,
					&batt_desc, &psy_cfg);
			schedule_delayed_work(&chg_dev->usbwork, 0);
			schedule_delayed_work(&chg_dev->work, 0);
			power_supply_changed(chg_dev->batt);
			battery_initialized = true;
		}
	} else {
		if (battery_initialized) {
			cancel_delayed_work_sync(&chg_dev->work);
			cancel_delayed_work_sync(&chg_dev->usbwork);
			power_supply_unregister(chg_dev->batt);
			chg_dev->batt = NULL;
			battery_initialized = false;
		}
	}
}
EXPORT_SYMBOL_GPL(axp_capchange);

irqreturn_t axp_usb_in_isr(int irq, void *data)
{
	struct axp_charger_dev *chg_dev = data;

	axp_usb_connect = 1;
	axp_change(chg_dev);
	axp_usbac_in(chg_dev);

	return IRQ_HANDLED;
}

irqreturn_t axp_usb_out_isr(int irq, void *data)
{
	struct axp_charger_dev *chg_dev = data;

	axp_usb_connect = 0;
	axp_change(chg_dev);
	axp_usbac_out(chg_dev);

	return IRQ_HANDLED;
}

irqreturn_t axp_ac_in_isr(int irq, void *data)
{
	struct axp_charger_dev *chg_dev = data;

	axp_change(chg_dev);
	axp_usbac_in(chg_dev);

	return IRQ_HANDLED;
}

irqreturn_t axp_ac_out_isr(int irq, void *data)
{
	struct axp_charger_dev *chg_dev = data;

	axp_change(chg_dev);
	axp_usbac_out(chg_dev);

	return IRQ_HANDLED;
}

irqreturn_t axp_capchange_isr(int irq, void *data)
{
	struct axp_charger_dev *chg_dev = data;

	axp_capchange(chg_dev);

	return IRQ_HANDLED;
}

irqreturn_t axp_change_isr(int irq, void *data)
{
	struct axp_charger_dev *chg_dev = data;

	axp_change(chg_dev);

	return IRQ_HANDLED;
}

irqreturn_t axp_low_warning1_isr(int irq, void *data)
{
	struct axp_charger_dev *chg_dev = data;

	axp_change(chg_dev);

	return IRQ_HANDLED;
}

irqreturn_t axp_low_warning2_isr(int irq, void *data)
{
	struct axp_charger_dev *chg_dev = data;

	axp_change(chg_dev);

	return IRQ_HANDLED;
}

void axp_charger_suspend(struct axp_charger_dev *chg_dev)
{
	struct axp_battery_info *batt = chg_dev->spy_info->batt;

	axp_charger_update_state(chg_dev);

	if (chg_dev->bat_det) {
		schedule_delayed_work(&chg_dev->usbwork, 0);
		flush_delayed_work(&chg_dev->usbwork);
		cancel_delayed_work_sync(&chg_dev->work);
		cancel_delayed_work_sync(&chg_dev->usbwork);

		batt->set_chg_cur(chg_dev, batt->suspend_chgcur);
	}
}
EXPORT_SYMBOL_GPL(axp_charger_suspend);

void axp_charger_resume(struct axp_charger_dev *chg_dev)
{
	struct axp_battery_info *batt = chg_dev->spy_info->batt;

	axp_charger_update_state(chg_dev);
	axp_charger_update_value(chg_dev);
	axp_battery_update_vol(chg_dev);

	batt->set_chg_cur(chg_dev, batt->runtime_chgcur);

	power_supply_changed(chg_dev->ac);
	power_supply_changed(chg_dev->usb);

	if (chg_dev->bat_det) {
		power_supply_changed(chg_dev->batt);
		schedule_delayed_work(&chg_dev->work, chg_dev->interval);
		schedule_delayed_work(&chg_dev->usbwork,
					msecs_to_jiffies(7 * 1000));
	}
}
EXPORT_SYMBOL_GPL(axp_charger_resume);

void axp_charger_shutdown(struct axp_charger_dev *chg_dev)
{
	struct axp_battery_info *batt = chg_dev->spy_info->batt;
	axp_charger_update_state(chg_dev);

	if (chg_dev->bat_det) {
		schedule_delayed_work(&chg_dev->usbwork, 0);
		flush_delayed_work(&chg_dev->usbwork);
		cancel_delayed_work_sync(&chg_dev->work);
		cancel_delayed_work_sync(&chg_dev->usbwork);
		batt->set_chg_cur(chg_dev, batt->shutdown_chgcur);

	}
}
EXPORT_SYMBOL_GPL(axp_charger_shutdown);

struct axp_charger_dev *axp_power_supply_register(struct device *dev,
					struct axp_dev *axp_dev,
					struct power_supply_info *battery_info,
					struct axp_supply_info *info)
{
	struct axp_charger_dev *chg_dev;

	chg_dev = devm_kzalloc(dev, sizeof(*chg_dev), GFP_KERNEL);
	if (chg_dev == NULL)
		return NULL;

	chg_dev->dev = dev;
	chg_dev->spy_info = info;
	chg_dev->chip = axp_dev;
	chg_dev->battery_info = battery_info;
	psy_cfg.drv_data = chg_dev;

	mutex_init(&chg_dev->charger_lock);

	axp_charger_update_state(chg_dev);
	if (chg_dev->bat_det) {
		chg_dev->batt = power_supply_register(dev,
				&batt_desc, &psy_cfg);
		if (IS_ERR(chg_dev->batt))
			goto err_ps_register;
		battery_initialized = true;
	}

	chg_dev->ac = power_supply_register(dev, &ac_desc, &psy_cfg);
	if (IS_ERR(chg_dev->ac)) {
		if (chg_dev->bat_det) {
			power_supply_unregister(chg_dev->batt);
			chg_dev->batt = NULL;
			goto err_ps_register;
		}
	}

	chg_dev->usb = power_supply_register(dev, &usb_desc, &psy_cfg);
	if (IS_ERR(chg_dev->usb)) {
		power_supply_unregister(chg_dev->ac);
		chg_dev->ac = NULL;
		if (chg_dev->bat_det) {
			power_supply_unregister(chg_dev->batt);
			chg_dev->batt = NULL;
			goto err_ps_register;
		}
	}

	if (info->ac->ac_vol && info->ac->set_ac_vhold)
		info->ac->set_ac_vhold(chg_dev, info->ac->ac_vol);

	if (info->usb->usb_pc_vol && info->usb->set_usb_vhold)
		info->usb->set_usb_vhold(chg_dev, info->usb->usb_pc_vol);

	if (info->batt->runtime_chgcur && info->batt->set_chg_cur)
		info->batt->set_chg_cur(chg_dev, info->batt->runtime_chgcur);

	setup_timer(&chg_dev->usb_status_timer,
			axp_charger_update_usb_state, (unsigned long)chg_dev);
	INIT_DELAYED_WORK(&(chg_dev->usbwork), axp_usb);

	axp_usb_ac_check_status(chg_dev);
	axp_battery_update_vol(chg_dev);

	chg_dev->interval = msecs_to_jiffies(10 * 1000);
	INIT_DELAYED_WORK(&chg_dev->work, axp_charging_monitor);

	schedule_delayed_work(&chg_dev->work, chg_dev->interval);
	if (timer_pending(&chg_dev->usb_status_timer))
		del_timer_sync(&chg_dev->usb_status_timer);
	mod_timer(&chg_dev->usb_status_timer,
			jiffies + msecs_to_jiffies(20 * 1000));


	return chg_dev;

err_ps_register:
	return NULL;
}
EXPORT_SYMBOL_GPL(axp_power_supply_register);

void axp_power_supply_unregister(struct axp_charger_dev *chg_dev)
{
	del_timer_sync(&chg_dev->usb_status_timer);
	power_supply_unregister(chg_dev->usb);
	chg_dev->batt = NULL;
	power_supply_unregister(chg_dev->ac);
	chg_dev->ac = NULL;

	if (chg_dev->bat_det && battery_initialized) {
		cancel_delayed_work_sync(&chg_dev->work);
		cancel_delayed_work_sync(&chg_dev->usbwork);
		power_supply_unregister(chg_dev->batt);
		chg_dev->batt = NULL;
		battery_initialized = false;
	}
}
EXPORT_SYMBOL_GPL(axp_power_supply_unregister);

int axp_charger_dt_parse(struct device_node *node,
			struct axp_config_info *axp_config)
{
	if (!of_device_is_available(node)) {
		pr_err("%s: failed\n", __func__);
		return -1;
	}

	AXP_OF_PROP_READ(pmu_battery_rdc,              BATRDC);
	AXP_OF_PROP_READ(pmu_battery_cap,                4000);
	AXP_OF_PROP_READ(pmu_batdeten,                      1);
	AXP_OF_PROP_READ(pmu_chg_ic_temp,                   0);
	AXP_OF_PROP_READ(pmu_runtime_chgcur, INTCHGCUR / 1000);
	AXP_OF_PROP_READ(pmu_suspend_chgcur,             1200);
	AXP_OF_PROP_READ(pmu_shutdown_chgcur,            1200);
	AXP_OF_PROP_READ(pmu_init_chgvol,    INTCHGVOL / 1000);
	AXP_OF_PROP_READ(pmu_init_chgend_rate,  INTCHGENDRATE);
	AXP_OF_PROP_READ(pmu_init_chg_enabled,              1);
	AXP_OF_PROP_READ(pmu_init_bc_en,                    0);
	AXP_OF_PROP_READ(pmu_init_adc_freq,        INTADCFREQ);
	AXP_OF_PROP_READ(pmu_init_adcts_freq,     INTADCFREQC);
	AXP_OF_PROP_READ(pmu_init_chg_pretime,  INTCHGPRETIME);
	AXP_OF_PROP_READ(pmu_init_chg_csttime,  INTCHGCSTTIME);
	AXP_OF_PROP_READ(pmu_batt_cap_correct,              1);
	AXP_OF_PROP_READ(pmu_chg_end_on_en,                 0);
	AXP_OF_PROP_READ(ocv_coulumb_100,                   0);
	AXP_OF_PROP_READ(pmu_bat_para1,               OCVREG0);
	AXP_OF_PROP_READ(pmu_bat_para2,               OCVREG1);
	AXP_OF_PROP_READ(pmu_bat_para3,               OCVREG2);
	AXP_OF_PROP_READ(pmu_bat_para4,               OCVREG3);
	AXP_OF_PROP_READ(pmu_bat_para5,               OCVREG4);
	AXP_OF_PROP_READ(pmu_bat_para6,               OCVREG5);
	AXP_OF_PROP_READ(pmu_bat_para7,               OCVREG6);
	AXP_OF_PROP_READ(pmu_bat_para8,               OCVREG7);
	AXP_OF_PROP_READ(pmu_bat_para9,               OCVREG8);
	AXP_OF_PROP_READ(pmu_bat_para10,              OCVREG9);
	AXP_OF_PROP_READ(pmu_bat_para11,              OCVREGA);
	AXP_OF_PROP_READ(pmu_bat_para12,              OCVREGB);
	AXP_OF_PROP_READ(pmu_bat_para13,              OCVREGC);
	AXP_OF_PROP_READ(pmu_bat_para14,              OCVREGD);
	AXP_OF_PROP_READ(pmu_bat_para15,              OCVREGE);
	AXP_OF_PROP_READ(pmu_bat_para16,              OCVREGF);
	AXP_OF_PROP_READ(pmu_bat_para17,             OCVREG10);
	AXP_OF_PROP_READ(pmu_bat_para18,             OCVREG11);
	AXP_OF_PROP_READ(pmu_bat_para19,             OCVREG12);
	AXP_OF_PROP_READ(pmu_bat_para20,             OCVREG13);
	AXP_OF_PROP_READ(pmu_bat_para21,             OCVREG14);
	AXP_OF_PROP_READ(pmu_bat_para22,             OCVREG15);
	AXP_OF_PROP_READ(pmu_bat_para23,             OCVREG16);
	AXP_OF_PROP_READ(pmu_bat_para24,             OCVREG17);
	AXP_OF_PROP_READ(pmu_bat_para25,             OCVREG18);
	AXP_OF_PROP_READ(pmu_bat_para26,             OCVREG19);
	AXP_OF_PROP_READ(pmu_bat_para27,             OCVREG1A);
	AXP_OF_PROP_READ(pmu_bat_para28,             OCVREG1B);
	AXP_OF_PROP_READ(pmu_bat_para29,             OCVREG1C);
	AXP_OF_PROP_READ(pmu_bat_para30,             OCVREG1D);
	AXP_OF_PROP_READ(pmu_bat_para31,             OCVREG1E);
	AXP_OF_PROP_READ(pmu_bat_para32,             OCVREG1F);
	AXP_OF_PROP_READ(pmu_ac_vol,                     4400);
	AXP_OF_PROP_READ(pmu_usbpc_vol,                  4400);
	AXP_OF_PROP_READ(pmu_ac_cur,                        0);
	AXP_OF_PROP_READ(pmu_usbpc_cur,                     0);
	AXP_OF_PROP_READ(pmu_pwroff_vol,                 3300);
	AXP_OF_PROP_READ(pmu_pwron_vol,                  2900);
	AXP_OF_PROP_READ(pmu_battery_warning_level1,       15);
	AXP_OF_PROP_READ(pmu_battery_warning_level2,        0);
	AXP_OF_PROP_READ(pmu_restvol_adjust_time,          30);
	AXP_OF_PROP_READ(pmu_ocv_cou_adjust_time,          60);
	AXP_OF_PROP_READ(pmu_chgled_func,                   0);
	AXP_OF_PROP_READ(pmu_chgled_type,                   0);
	AXP_OF_PROP_READ(pmu_bat_temp_enable,               0);
	AXP_OF_PROP_READ(pmu_bat_charge_ltf,             0xA5);
	AXP_OF_PROP_READ(pmu_bat_charge_htf,             0x1F);
	AXP_OF_PROP_READ(pmu_bat_shutdown_ltf,           0xFC);
	AXP_OF_PROP_READ(pmu_bat_shutdown_htf,           0x16);
	AXP_OF_PROP_READ(pmu_bat_temp_para1,                0);
	AXP_OF_PROP_READ(pmu_bat_temp_para2,                0);
	AXP_OF_PROP_READ(pmu_bat_temp_para3,                0);
	AXP_OF_PROP_READ(pmu_bat_temp_para4,                0);
	AXP_OF_PROP_READ(pmu_bat_temp_para5,                0);
	AXP_OF_PROP_READ(pmu_bat_temp_para6,                0);
	AXP_OF_PROP_READ(pmu_bat_temp_para7,                0);
	AXP_OF_PROP_READ(pmu_bat_temp_para8,                0);
	AXP_OF_PROP_READ(pmu_bat_temp_para9,                0);
	AXP_OF_PROP_READ(pmu_bat_temp_para10,               0);
	AXP_OF_PROP_READ(pmu_bat_temp_para11,               0);
	AXP_OF_PROP_READ(pmu_bat_temp_para12,               0);
	AXP_OF_PROP_READ(pmu_bat_temp_para13,               0);
	AXP_OF_PROP_READ(pmu_bat_temp_para14,               0);
	AXP_OF_PROP_READ(pmu_bat_temp_para15,               0);
	AXP_OF_PROP_READ(pmu_bat_temp_para16,               0);
	AXP_OF_PROP_READ(pmu_bat_unused,                    0);
	AXP_OF_PROP_READ(power_start,                       0);
	AXP_OF_PROP_READ(pmu_ocv_en,                        1);
	AXP_OF_PROP_READ(pmu_cou_en,                        1);
	AXP_OF_PROP_READ(pmu_update_min_time,   UPDATEMINTIME);

	axp_config_obj = axp_config;
	return 0;
}
EXPORT_SYMBOL_GPL(axp_charger_dt_parse);

MODULE_DESCRIPTION("ALLWINNERTECH axp charger");
MODULE_AUTHOR("pannan");
MODULE_LICENSE("GPL");
