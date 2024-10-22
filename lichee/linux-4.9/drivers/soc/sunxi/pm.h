/*
 * (C) Copyright 2010-2016
 * Allwinner Technology Co., Ltd. <www.allwinnertech.com>
 * fanqinghua <fanqinghua@allwinnertech.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License, or (at your option) any later version.
 */

#ifndef __AW_PM_H__
#define __AW_PM_H__
#include <linux/power/axp_depend.h>
/* the wakeup source of main cpu: cpu0 */
#define CPU0_WAKEUP_MSGBOX      (1<<0)
#define CPU0_WAKEUP_KEY         (1<<1)
#define CPU0_WAKEUP_EXINT       (1<<2)
#define CPU0_WAKEUP_IR          (1<<3)
#define CPU0_WAKEUP_ALARM       (1<<4)
#define CPU0_WAKEUP_USB         (1<<5)
#define CPU0_WAKEUP_TIMEOUT     (1<<6)
#define CPU0_WAKEUP_GPIO        (1<<7)

/* the wakeup source of assistant cpu: cpus */
#define CPUS_WAKEUP_LOWBATT     (1<<12)
#define CPUS_WAKEUP_USB         (1<<13)
#define CPUS_WAKEUP_AC          (1<<14)
#define CPUS_WAKEUP_ASCEND      (1<<15)
#define CPUS_WAKEUP_DESCEND     (1<<16)
#define CPUS_WAKEUP_SHORT_KEY   (1<<17)
#define CPUS_WAKEUP_LONG_KEY    (1<<18)
#define CPUS_WAKEUP_IR          (1<<19)
#define CPUS_WAKEUP_ALM0        (1<<20)
#define CPUS_WAKEUP_ALM1        (1<<21)
#define CPUS_WAKEUP_TIMEOUT     (1<<22)
#define CPUS_WAKEUP_GPIO        (1<<23)
#define CPUS_WAKEUP_USBMOUSE    (1<<24)
#define CPUS_WAKEUP_LRADC       (1<<25)
#define CPUS_WAKEUP_WLAN        (1<<26)
#define CPUS_WAKEUP_CODEC       (1<<27)
#define CPUS_WAKEUP_BAT_TEMP    (1<<28)
#define CPUS_WAKEUP_FULLBATT    (1<<29)
#define CPUS_WAKEUP_HMIC        (1<<30)
#define CPUS_WAKEUP_POWER_EXP   (1<<31)
#define CPUS_WAKEUP_KEY         (CPUS_WAKEUP_SHORT_KEY \
			| CPUS_WAKEUP_LONG_KEY)

/* define cpus wakeup src */
#define CPUS_MEM_WAKEUP          (CPUS_WAKEUP_LOWBATT  \
				| CPUS_WAKEUP_USB      \
				| CPUS_WAKEUP_AC       \
				| CPUS_WAKEUP_DESCEND  \
				| CPUS_WAKEUP_ASCEND   \
				| CPUS_WAKEUP_ALM0     \
				| CPUS_WAKEUP_GPIO     \
				| CPUS_WAKEUP_IR)

#define CPUS_BOOTFAST_WAKEUP     (CPUS_WAKEUP_LOWBATT  \
				| CPUS_WAKEUP_LONG_KEY \
				| CPUS_WAKEUP_ALM0     \
				| CPUS_WAKEUP_USB      \
				| CPUS_WAKEUP_AC)

#define CPU0_MEM_WAKEUP          (CPU0_WAKEUP_MSGBOX   \
				| CPU0_WAKEUP_EXINT    \
				| CPU0_WAKEUP_ALARM)

/* for format all the wakeup gpio into one word.*/
#define GPIO_PL_MAX_NUM             (11)    /* 0-11 */
#define GPIO_PM_MAX_NUM             (11)    /* 0-11 */
#define GPIO_AXP_MAX_NUM            (7)     /* 0-7 */

#define WAKEUP_GPIO_PL(num)         (1 << (num))
#define WAKEUP_GPIO_PM(num)         (1 << (num + 12))
#define WAKEUP_GPIO_AXP(num)        (1 << (num + 24))
#define WAKEUP_GPIO_GROUP(group)    (1 << (group - 'A'))

#define PM_PLL_C0       (0)
#define PM_PLL_C1       (1)
#define PM_PLL_AUDIO    (2)
#define PM_PLL_VIDEO0   (3)
#define PM_PLL_VE       (4)
#define PM_PLL_DRAM     (5)
#define PM_PLL_PERIPH   (6)
#define PM_PLL_GPU      (7)
#define PM_PLL_HSIC     (8)
#define PM_PLL_DE       (9)
#define PM_PLL_VIDEO1   (10)
#define PLL_PERIPH1     (11)
#define PLL_DRAM1       (12)
#define PLL_MIPI        (13)
#define PLL_NUM         (14)

#define BUS_C0          (0)
#define BUS_C1          (1)
#define BUS_AXI0        (2)
#define BUS_AXI1        (3)
#define BUS_AHB1        (4)
#define BUS_AHB2        (5)
#define BUS_APB1        (6)
#define BUS_APB2        (7)
#define BUS_NUM	        (8)
#define IO_NUM      (2)

#define OSC_HOSC_BIT    (3)
#define OSC_LOSC_BIT    (2)
#define OSC_LDO1_BIT    (1)
#define OSC_LDO0_BIT    (0)

typedef enum power_dm {
	DM_CPUA = 0, /* 0  */
	DM_CPUB,     /* 1  */
	DM_DRAM,     /* 2  */
	DM_GPU,      /* 3  */
	DM_SYS,      /* 4  */
	DM_VPU,      /* 5  */
	DM_CPUS,     /* 6  */
	DM_DRAMPLL,  /* 7  */
	DM_ADC,      /* 8  */
	DM_PL,       /* 9  */
	DM_PM,       /* 10 */
	DM_IO,       /* 11 */
	DM_CPVDD,    /* 12 */
	DM_LDOIN,    /* 13 */
	DM_PLL,      /* 14 */
	DM_LPDDR,    /* 15 */
	DM_TEST,     /* 16 */
	DM_RES1,     /* 17 */
	DM_RES2,     /* 18 */
	DM_RES3,     /* 19 */
	DM_MAX,      /* 20 */
} power_dm_e;

typedef struct {
	unsigned int factor1;
	unsigned int factor2;
	unsigned int factor3;
	unsigned int factor4;
} pll_para_t;

typedef struct {
	unsigned int src;
	unsigned int pre_div;
	unsigned int div_ratio;
	unsigned int n;
	unsigned int m;
} bus_para_t;

typedef struct super_standby_para {
	/* cpus wakeup event types */
	unsigned int event;
	/* cpux resume code src */
	unsigned int resume_code_src;
	/* cpux resume code length */
	unsigned int resume_code_length;
	/* cpux resume entry */
	unsigned int resume_entry;
	/* wakeup after timeout seconds */
	unsigned int timeout;
	unsigned int gpio_enable_bitmap;
	unsigned int cpux_gpiog_bitmap;
	unsigned int pextended_standby;
} super_standby_t;

typedef enum {
	CLK_SRC_NONE = 0x0, /* invalid source clock id */

	CLK_SRC_LOSC,   /* LOSC, 33/50/67:32768Hz, 73:16MHz/512=31250*/
	CLK_SRC_IOSC,   /* InternalOSC,  33/50/67:700KHZ, 73:16MHz*/
	CLK_SRC_HOSC,   /* HOSC, 24MHZ clock*/
	CLK_SRC_AXI,    /* AXI clock*/
	CLK_SRC_16M,    /* 16M for the backdoor*/

	CLK_SRC_PLL1,   /* PLL1 clock */
	CLK_SRC_PLL2,   /* PLL2 clock */
	CLK_SRC_PLL3,   /* PLL3 clock */
	CLK_SRC_PLL4,   /* PLL4 clock */
	CLK_SRC_PLL5,   /* PLL5 clock */
	CLK_SRC_PLL6,   /* PLL6 clock */
	CLK_SRC_PLL7,   /* PLL7 clock */
	CLK_SRC_PLL8,   /* PLL8 clock */
	CLK_SRC_PLL9,   /* PLL9 clock */
	CLK_SRC_PLL10,  /* PLL10 clock */
	CLK_SRC_PLL11,  /* PLL10 clock */

	CLK_SRC_CPUS,   /* cpus clock */
	CLK_SRC_C0,     /* cluster0 clock */
	CLK_SRC_C1,     /* cluster1 clock */
	CLK_SRC_AXI0,   /* AXI0 clock */
	CLK_SRC_AXI1,   /* AXI0 clock */
	CLK_SRC_AHB0,   /* AHB0 clock */
	CLK_SRC_AHB1,   /* AHB1 clock */
	CLK_SRC_AHB2,   /* AHB2 clock */
	CLK_SRC_APB0,   /* APB0 clock */
	CLK_SRC_APB1,   /* APB1 clock */
	CLK_SRC_APB2,   /* APB2 clock */
} clk_src_e;

typedef struct {
	/*
	 * for state bitmap:
	 * bitx = 1: keep state.
	 * bitx = 0: mean close corresponding power src.
	 */
	unsigned int state;
	/* bitx=1, the corresponding state is effect,
	 * otherwise, the corresponding power is in charge in device driver.
	 * sys_mask&state: bitx=1,
	 * mean the power is on,
	 * for the "on" state power,
	 * u need care about the voltage.;
	 * ((~sys_mask)|state): bitx=0, mean the power is close;
	 *
	 * pwr_dm_state bitmap
	 * actually: we care about the pwr_dm voltage,
	 * such as: we want to keep the vdd_sys at 1.0v at standby period.
	 * we actually do not care how to do it.
	 * it can be sure that cpus can do it with the pmu's help.
	 */
	unsigned int sys_mask;
	unsigned int volt[VCC_MAX_INDEX]; /* unsigned short is 16bit width. */
} pwr_dm_state_t;

/* selfresh_flag must be compatible with vdd_sys pwr state.*/
typedef struct pm_dram_para {
	unsigned int selfresh_flag;
	unsigned int crc_en;
	unsigned int crc_start;
	unsigned int crc_len;
} pm_dram_para_t;

typedef struct cpus_clk_para {
	unsigned int cpus_id;
} cpus_para_t;

typedef struct io_state_config {
	unsigned int paddr;
	unsigned int value_mask; /* specify the effect bit.*/
	unsigned int value;
} io_state_config_t;

typedef struct soc_io_para {
	/*
	 * hold: mean before power off vdd_sys, whether hold gpio pad or not.
	 * this flag only effect: when vdd_sys is powered_off;
	 * this flag only affect hold&unhold operation.
	 * the recommended hold sequence is as follow:
	 * backup_io_cfg -> cfg_io(enter_low_power_mode)
	 * -> hold -> assert vdd_sys_reset -> poweroff vdd_sys.
	 * the recommended unhold sequence is as follow:
	 * poweron vdd_sys -> de_assert vdd_sys -> restore_io_cfg -> unhold.
	 */
	unsigned int hold_flag;

	/*
	 * note: only specific bit mark by value_mask is effect.
	 * IO_NUM: only uart and jtag needed care.
	 */
	io_state_config_t io_state[IO_NUM];
} soc_io_para_t;

typedef struct cpux_clk_para {
	/*
	 * Hosc: losc: ldo1: ldo0
	 * the osc bit map is as follow:
	 * bit3: bit2: bit1:bit0
	 * Hosc: losc: ldo1: ldo0
	 */
	int osc_en;

	/*
	 * for a83, pll bitmap as follow:
	 *
	 * pll11(video1):	pll10(de):  pll9(hsic)
	 * bit7:	    bit6:	bit5:	    bit4:
	 * pll8(gpu): pll6(periph): pll5(dram): pll4(ve):
	 * bit3:		bit2:		bit1:	    bit0
	 * pll3(video):	pll2(audio):	c1cpux:	    c0cpux
	 *
	 */

	/* for disable bitmap:
	 * bitx = 0: close
	 * bitx = 1: do not care.
	 */
	int init_pll_dis;

	/* for enable bitmap:
	 * bitx = 0: do not care.
	 * bitx = 1: open
	 */
	int exit_pll_en;

	/*
	 * set corresponding bit if it's pll factors need to be set some value.
	 */
	int pll_change;

	/*
	 * fill in the enabled pll freq factor sequently.
	 * unit is khz pll6: 0x90041811
	 * factor n/m/k/p already do the pretreatment of the minus one
	 */
	pll_para_t pll_factor[PLL_NUM];
	int bus_change;

	/*
	 * bus_src: ahb1, apb2 src;
	 * option:  pllx:axi:hosc:losc
	 */
	bus_para_t bus_factor[BUS_NUM];
} cpux_clk_para_t;

typedef struct soc_pwr_dep {
	/* id of scene_lock */
	unsigned int id;

	pwr_dm_state_t  soc_pwr_dm_state;
	pm_dram_para_t  soc_dram_state;
	soc_io_para_t   soc_io_state;
	cpux_clk_para_t cpux_clk_state;
} soc_pwr_dep_t;

typedef struct extended_standby {
	/* id of extended standby */
	unsigned int id;

	/* for: 808 || 809+806 || 803 || 813
	 * support 4 pmu: each pmu_id range is: 0-255;
	 * pmu_id <--> pmu_name have directly mapping,
	 * u can get the pmu_name from sys_config.fex files.;
	 * bitmap as follow:
	 * pmu0: 0-7 bit; pmu1: 8-15 bit; pmu2: 16-23 bit; pmu3: 24-31 bit
	 */
	unsigned int pmu_id;

	/* a33, a80, a83,...,
	 * for compatible reason, different soc,
	 * each bit have different meaning.
	 */
	unsigned int soc_id;

	pwr_dm_state_t soc_pwr_dm_state;
	pm_dram_para_t soc_dram_state;
	soc_io_para_t soc_io_state;
	cpux_clk_para_t cpux_clk_state;
} extended_standby_t;

#endif /* __AW_PM_H__ */
