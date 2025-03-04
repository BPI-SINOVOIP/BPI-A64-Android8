/*
 * (C) Copyright 2013-2016
 * Allwinner Technology Co., Ltd. <www.allwinnertech.com>
 *
 * SPDX-License-Identifier:     GPL-2.0+
 */
#include <common.h>
#include <spare_head.h>
#include <sunxi_mbr.h>
#include <boot_type.h>
#include <sys_partition.h>
#include <sys_config.h>
#include <sunxi_board.h>
#include <fastboot.h>
#include <android_misc.h>
#include <power/sunxi/pmu.h>
#include "debug_mode.h"
#include "sunxi_string.h"
#include "sunxi_serial.h"
#include <fdt_support.h>
#include <sys_config_old.h>
#include <arisc.h>
#include <sunxi_nand.h>
#include <mmc.h>
#include <asm/arch/dram.h>
#include <asm/arch/platform.h>
#include <smc.h>
#ifdef CONFIG_SUNXI_MULITCORE_BOOT
#include <cputask.h>
#endif

DECLARE_GLOBAL_DATA_PTR;

#define PARTITION_SETS_MAX_SIZE	 1024


int  __attribute__((weak)) sunxi_fastboot_status_read(void)
{
		return 0;
}
int __attribute__((weak)) mmc_request_update_boot0(int dev_num)
{
	return 0;
}
void  __attribute__((weak)) mmc_update_config_for_sdly(struct mmc *mmc)
{
	return;
}
void  __attribute__((weak)) mmc_update_config_for_dragonboard(int card_no)
{
	return;
}
struct mmc *  __attribute__((weak)) find_mmc_device(int dev_num)
{
	return NULL;
}

void sunxi_update_subsequent_processing(int next_work)
{
	printf("next work %d\n", next_work);
	switch(next_work)
	{
		case SUNXI_UPDATE_NEXT_ACTION_REBOOT:
		case SUNXI_UPDATA_NEXT_ACTION_SPRITE_TEST:
			printf("SUNXI_UPDATE_NEXT_ACTION_REBOOT\n");
			sunxi_board_restart(0);
			break;

		case SUNXI_UPDATE_NEXT_ACTION_SHUTDOWN:
			printf("SUNXI_UPDATE_NEXT_ACTION_SHUTDOWN\n");
			sunxi_board_shutdown();
			break;

		case SUNXI_UPDATE_NEXT_ACTION_REUPDATE:
			printf("SUNXI_UPDATE_NEXT_ACTION_REUPDATE\n");
			sunxi_board_run_fel();
			break;

		case SUNXI_UPDATE_NEXT_ACTION_BOOT:
		case SUNXI_UPDATE_NEXT_ACTION_NORMAL:
		default:
			printf("SUNXI_UPDATE_NEXT_ACTION_NULL\n");
			break;
	}

	return ;
}

static void  __set_part_name_for_nand(int index, char* part_name,int part_type)
{
	if(PART_TYPE_GPT == part_type)
	{
		sprintf(part_name, "nand0p%d", index+1);
	}
	else
	{
		sprintf(part_name, "nand%c", 'a' + index);
	}
}
static void __set_part_name_for_sdmmc(int index, char* part_name,int part_type, int part_total)
{
	if(PART_TYPE_GPT == part_type)
	{
		sprintf(part_name, "mmcblk0p%d", index+1);
	}
	else
	{
		if(index == 0)
		{
			strcpy(part_name, "mmcblk0p2");
		}
		else if( (index+1)==part_total)
		{
			strcpy(part_name, "mmcblk0p1");
		}
		else
		{
			sprintf(part_name, "mmcblk0p%d", index + 4);
		}
	}
}


void sunxi_fastboot_init(void)
{
	struct fastboot_ptentry fb_part;
	int index, part_total;
	char partition_sets[PARTITION_SETS_MAX_SIZE];
	char part_name[16];
	char *partition_index = partition_sets;
	int offset = 0;
	int temp_offset = 0;
	int storage_type = get_boot_storage_type();
	int part_type = 0;

	printf("--------fastboot partitions--------\n");
	part_total = sunxi_partition_get_total_num();
	part_type  = sunxi_partition_get_type();
	if((part_total <= 0) || (part_total > SUNXI_MBR_MAX_PART_COUNT))
	{
		printf("mbr not exist\n");

		return ;
	}
	printf("-total partitions:%d-\n", part_total);
	printf("%-12s  %-12s  %-12s\n", "-name-", "-start-", "-size-");

	memset(partition_sets, 0, PARTITION_SETS_MAX_SIZE);

	for(index = 0; index < part_total && index < SUNXI_MBR_MAX_PART_COUNT; index++)
	{
		sunxi_partition_get_name(index, &fb_part.name[0]);
		fb_part.start = sunxi_partition_get_offset(index) * 512;
		fb_part.length = sunxi_partition_get_size(index) * 512;
		fb_part.flags = 0;
		printf("%-12s: %-12x  %-12x\n", fb_part.name, fb_part.start, fb_part.length);

		memset(part_name, 0, 16);
		if(storage_type == STORAGE_NAND)
		{
			__set_part_name_for_nand(index, part_name, part_type);
		}
                else if(storage_type == STORAGE_NOR)
                {
                        sprintf(part_name, "mtdblock%d", index + 1);
                }
		else
		{
			__set_part_name_for_sdmmc(index, part_name, part_type,part_total);
		}

		temp_offset = strlen(fb_part.name) + strlen(part_name) + 2;
		if(temp_offset >= PARTITION_SETS_MAX_SIZE)
		{
			printf("partition_sets is too long, please reduces partition name\n");
			break;
		}
		//fastboot_flash_add_ptn(&fb_part);
		sprintf(partition_index, "%s@%s:", fb_part.name, part_name);
		offset += temp_offset;
		partition_index = partition_sets + offset;
	}

	partition_sets[offset-1] = '\0';
	partition_sets[PARTITION_SETS_MAX_SIZE - 1] = '\0';
	printf("-----------------------------------\n");

	setenv("partitions", partition_sets);
}


#define    ANDROID_NULL_MODE            0
#define    ANDROID_FASTBOOT_MODE		1
#define    ANDROID_RECOVERY_MODE		2
#define    USER_SELECT_MODE 			3
/*
************************************************************************************************************
*
*                                             function
*
*    name          :
*
*    parmeters     :
*
*    return        :
*
*    note          :
*
*
************************************************************************************************************
*/
static int detect_other_boot_mode(void)
{
	int ret1, ret2;
	int key_high, key_low;
	int keyvalue;

	keyvalue = uboot_spare_head.boot_ext[0].data[2];
	pr_msg("key %d\n", keyvalue);

	ret1 = script_parser_fetch("recovery_key", "key_max", &key_high, 1);
	ret2 = script_parser_fetch("recovery_key", "key_min", &key_low,  1);
	if((ret1) || (ret2))
	{
		pr_msg("cant find rcvy value\n");
	}
	else
	{
		pr_notice("recovery key high %d, low %d\n", key_high, key_low);
		if((keyvalue >= key_low) && (keyvalue <= key_high))
		{
			pr_notice("key found, android recovery\n");

			return ANDROID_RECOVERY_MODE;
		}
	}
	ret1 = script_parser_fetch("fastboot_key", "key_max", &key_high, 1);
	ret2 = script_parser_fetch("fastboot_key", "key_min", &key_low, 1);
	if((ret1) || (ret2))
	{
		pr_msg("cant find fstbt value\n");
	}
	else
	{
		pr_notice("fastboot key high %d, low %d\n", key_high, key_low);
		if((keyvalue >= key_low) && (keyvalue <= key_high))
		{
			pr_notice("key found, android fastboot\n");
			return ANDROID_FASTBOOT_MODE;
		}
	}

	return ANDROID_NULL_MODE;
}

int update_bootcmd(void)
{
	int   boot_mode;
	char  boot_commond[128];
	int   storage_type = get_boot_storage_type();

	if (gd->force_shell) {
		char delaytime[8];
		sprintf(delaytime, "%d", 3);
		setenv("bootdelay", delaytime);
	}

	memset(boot_commond, 0x0, 128);
	strcpy(boot_commond, getenv("bootcmd"));
	pr_msg("base bootcmd=%s\n", boot_commond);

	if ((storage_type == STORAGE_SD) ||
		(storage_type == STORAGE_EMMC)||
		storage_type == STORAGE_EMMC3) {
		sunxi_str_replace(boot_commond, "setargs_nand", "setargs_mmc");
		pr_msg("bootcmd set setargs_mmc\n");
	} else if (storage_type == STORAGE_NOR) {
		sunxi_str_replace(boot_commond, "setargs_nand", "setargs_nor");
	} else if (storage_type == STORAGE_NAND) {
		pr_msg("bootcmd set setargs_nand\n");
	}

	boot_mode = detect_other_boot_mode();

#ifdef CONFIG_DETECT_RTC_BOOT_MODE
	set_bootcmd_from_rtc(boot_mode, boot_commond);
#else
	set_bootcmd_from_misc(boot_mode, boot_commond);
#endif


	if (gd->need_shutdown) {
		sunxi_board_shutdown();
		for(;;);
	}

	setenv("bootcmd", boot_commond);
	pr_msg("to be run cmd=%s\n", boot_commond);

	return 0;
}

int disable_node(char* name)
{

	int nodeoffset = 0;
	int ret = 0;

	if(strcmp(name,"mmc3") == 0)
	{
#ifndef CONFIG_MMC3_SUPPORT
		return 0;
#endif
	}
	nodeoffset = fdt_path_offset(working_fdt, name);
	ret = fdt_set_node_status(working_fdt,nodeoffset,FDT_STATUS_DISABLED,0);
	if(ret < 0)
	{
		printf("disable nand error: %s\n", fdt_strerror(ret));
	}
	return ret;
}

int  dragonboard_handler(void* dtb_base)
{
#ifdef CONFIG_SUNXI_DRAGONBOARD_SUPPORT
	int card_num = 0;

	int ret;
	printf("%s call\n",__func__);

#ifdef CONFIG_SUNXI_UBIFS
	if (sunxi_get_mtd_ubi_mode_status())
		ret = sunxi_nand_uboot_probe();
	else
#endif
		ret = nand_uboot_probe();

	/* nand_uboot_init(1) */
	if (!ret)
	{
		printf("try nand successed\n");
		disable_node("mmc2");
		disable_node("mmc3");
	}
	else
	{
		struct mmc *mmc_tmp = NULL;
#ifdef CONFIG_MMC3_SUPPORT
		card_num = 3;
#else
		card_num = 2;
#endif
		printf("try to find card%d \n", card_num);
		board_mmc_set_num(card_num);
		board_mmc_pre_init(card_num);
		mmc_tmp = find_mmc_device(card_num);
		if(!mmc_tmp)
		{
			return -1;
		}
		if (0 == mmc_init(mmc_tmp))
		{
			printf("try mmc successed \n");
			disable_node("nand0");
			if(card_num == 2)
				disable_node("mmc3");
			else if(card_num == 3)
				disable_node("mmc2");
		}
		else
		{
			printf("try card%d successed \n", card_num);
		}
		mmc_exit();
	}
#endif
	return 0;

}


int update_fdt_para_for_kernel(void* dtb_base)
{
	int ret;
	int nodeoffset = 0;
	uint storage_type = 0;
	struct mmc *mmc =NULL;
	int dev_num = 0;
	extern void display_update_dtb(void);

	storage_type = get_boot_storage_type_ext();

	//update sdhc dbt para
	if(storage_type == STORAGE_EMMC || storage_type == STORAGE_EMMC3)
	{
		dev_num = (storage_type == STORAGE_EMMC)? 2:3;
		mmc = find_mmc_device(dev_num);
		if (mmc == NULL) {
			printf("can't find valid mmc %d\n", dev_num);
			return -1;
		}
		if (mmc->cfg->platform_caps.sample_mode == AUTO_SAMPLE_MODE) {
			mmc_update_config_for_sdly(mmc);
		}
	}

	//fix nand&sdmmc
	switch(storage_type)
	{
		case STORAGE_NAND:
			disable_node("mmc2");
			disable_node("mmc3");
			disable_node("spi0");
			#ifdef CONFIG_STORAGE_MEDIA_SPINAND
				disable_node("spinand");
			#endif
			break;
		case STORAGE_SPI_NAND:
			disable_node("nand0");
			disable_node("mmc2");
			disable_node("spi0");
			break;
		case STORAGE_EMMC:
			disable_node("nand0");
			disable_node("mmc3");
			disable_node("spi0");
			break;
		case STORAGE_EMMC3:
			disable_node("nand0");
			disable_node("mmc2");
			break;
		case STORAGE_SD:
		{
			uint32_t dragonboard_test = 0;
			ret = script_parser_fetch("target", "dragonboard_test", (int *)&dragonboard_test, 1);
			if(dragonboard_test == 1)
			{
				dragonboard_handler(dtb_base);
				mmc_update_config_for_dragonboard(2);
				#ifdef CONFIG_MMC3_SUPPORT
				mmc_update_config_for_dragonboard(3);
				#endif
			}
			else
			{
				disable_node("nand0");
				disable_node("mmc2");
				disable_node("mmc3");
			}
		}
			break;
		case STORAGE_NOR:
			disable_node("nand0");
			disable_node("mmc2");
			break;
		default:
			break;

	}

	//fix memory
	ret = fdt_fixup_memory(dtb_base, gd->bd->bi_dram[0].start ,gd->bd->bi_dram[0].size);
	if(ret < 0)
	{
		return -1;
	}

#if defined(CONFIG_ARCH_SUN50IW6P1) ||  defined(CONFIG_ARCH_SUN50IW3P1)\
	|| defined(CONFIG_ARCH_SUN50IW1P1) || defined(CONFIG_ARCH_SUN8IW7P1)
	ulong drm_base = 0, drm_size = 0;

	if ((gd->securemode == SUNXI_SECURE_MODE_WITH_SECUREOS) &&
		(!smc_tee_probe_drm_configure(&drm_base, &drm_size))) {
		pr_msg("drm_base=0x%lx\n", drm_base);
		pr_msg("drm_size=0x%lx\n", drm_size);
		ret = fdt_add_mem_rsv(dtb_base, drm_base, drm_size);
		if (ret)
			printf("##add mem rsv error: %s : %s\n",
				__func__, fdt_strerror(ret));
	}
#endif

	//fix dram para
	uint32_t *dram_para = NULL;
	dram_para = (uint32_t *)uboot_spare_head.boot_data.dram_para;
	pr_msg("update dtb dram start\n");
	nodeoffset = fdt_path_offset(dtb_base, "/dram");
	if(nodeoffset<0)
	{
		printf("## error: %s : %s\n", __func__,fdt_strerror(ret));
		return -1;
	}
	fdt_setprop_u32(dtb_base,nodeoffset, "dram_clk", dram_para[0]);
	fdt_setprop_u32(dtb_base,nodeoffset, "dram_type", dram_para[1]);
	fdt_setprop_u32(dtb_base,nodeoffset, "dram_zq", dram_para[2]);
	fdt_setprop_u32(dtb_base,nodeoffset, "dram_odt_en", dram_para[3]);
	fdt_setprop_u32(dtb_base,nodeoffset, "dram_para1", dram_para[4]);
	fdt_setprop_u32(dtb_base,nodeoffset, "dram_para2", dram_para[5]);
	fdt_setprop_u32(dtb_base,nodeoffset, "dram_mr0", dram_para[6]);
	fdt_setprop_u32(dtb_base,nodeoffset, "dram_mr1", dram_para[7]);
	fdt_setprop_u32(dtb_base,nodeoffset, "dram_mr2", dram_para[8]);
	fdt_setprop_u32(dtb_base,nodeoffset, "dram_mr3", dram_para[9]);
	fdt_setprop_u32(dtb_base,nodeoffset, "dram_tpr0", dram_para[10]);
	fdt_setprop_u32(dtb_base,nodeoffset, "dram_tpr1", dram_para[11]);
	fdt_setprop_u32(dtb_base,nodeoffset, "dram_tpr2", dram_para[12]);
	fdt_setprop_u32(dtb_base,nodeoffset, "dram_tpr3", dram_para[13]);
	fdt_setprop_u32(dtb_base,nodeoffset, "dram_tpr4", dram_para[14]);
	fdt_setprop_u32(dtb_base,nodeoffset, "dram_tpr5", dram_para[15]);
	fdt_setprop_u32(dtb_base,nodeoffset, "dram_tpr6", dram_para[16]);
	fdt_setprop_u32(dtb_base,nodeoffset, "dram_tpr7", dram_para[17]);
	fdt_setprop_u32(dtb_base,nodeoffset, "dram_tpr8", dram_para[18]);
	fdt_setprop_u32(dtb_base,nodeoffset, "dram_tpr9", dram_para[19]);
	fdt_setprop_u32(dtb_base,nodeoffset, "dram_tpr10", dram_para[20]);
	fdt_setprop_u32(dtb_base,nodeoffset, "dram_tpr11", dram_para[21]);
	fdt_setprop_u32(dtb_base,nodeoffset, "dram_tpr12", dram_para[22]);
	fdt_setprop_u32(dtb_base,nodeoffset, "dram_tpr13", dram_para[23]);
	pr_msg("update dtb dram  end\n");

	return 0;
}

#ifdef CONFIG_DETECT_RTC_BOOT_MODE
char *set_bootcmd_from_rtc(int mode, char *bootcmd)
{
	u8	bootmode_flag = 0;
	if (mode == ANDROID_NULL_MODE) {
		/* read RTC flag*/
		bootmode_flag = sunxi_get_bootmode_flag();
	} else if (mode == ANDROID_RECOVERY_MODE) {
		bootmode_flag = SUNXI_BOOT_RECOVERY_FLAG;
	} else if (mode == ANDROID_FASTBOOT_MODE) {
		bootmode_flag = SUNXI_FASTBOOT_FLAG;
	}
	/*clear rtc*/
	sunxi_set_bootmode_flag(0);

	switch (bootmode_flag) {
	case SUNXI_EFEX_CMD_FLAG:
		pr_msg("find efex cmd\n");
		sunxi_board_run_fel();
		break;
	case SUNXI_BOOT_RECOVERY_FLAG:
		pr_msg("Recovery detected, will boot recovery\n");
		sunxi_str_replace(bootcmd, "boot_normal", "boot_recovery");
		break;
	case SUNXI_FASTBOOT_FLAG:
		pr_msg("Fastboot detected, will boot fastboot\n");
		sunxi_str_replace(bootcmd, "boot_normal", "boot_fastboot");
		break;
	default:
		break;
	}
	return bootcmd;
}
#endif

char *set_bootcmd_from_misc(int mode, char *bootcmd)
{
	u32   misc_offset = 0;
	char  misc_args[2048];
	char  misc_fill[2048];
	struct bootloader_message *misc_message;

	misc_message = (struct bootloader_message *)misc_args;
	memset(misc_args, 0x0, 2048);
	memset(misc_fill, 0xff, 2048);

	switch (mode) {
	case ANDROID_RECOVERY_MODE:
		strcpy(misc_message->command, "recovery");
		break;
	case ANDROID_FASTBOOT_MODE:
		strcpy(misc_message->command, "bootloader");
		break;
	case ANDROID_NULL_MODE:
		{
			misc_offset = sunxi_partition_get_offset_byname("misc");
			if (!misc_offset) {
				pr_msg("no misc partition is found\n");
			} else {
				pr_msg("misc partition found\n");
				sunxi_flash_read(misc_offset, 2048/512, misc_args);
			}
		}
		break;
	default:
		break;
	}
	if (!strcmp(misc_message->command, "efex")) {
		puts("find efex cmd\n");
		sunxi_flash_write(misc_offset, 2048/512, misc_fill);
		sunxi_board_run_fel();
		return 0;
	} else if (!strcmp(misc_message->command, "recovery")) {
		puts("Recovery detected, will boot recovery\n");
		sunxi_str_replace(bootcmd, "boot_normal", "boot_recovery");
	} else if (!strcmp(misc_message->command, "bootloader")) {
		puts("Fastboot detected, will boot fastboot\n");
		sunxi_str_replace(bootcmd, "boot_normal", "boot_fastboot");
		if (misc_offset)
			sunxi_flash_write(misc_offset, 2048/512, misc_fill);
	}
	return bootcmd;
}

int get_boot_work_mode(void)
{
	return uboot_spare_head.boot_data.work_mode;
}

int get_boot_storage_type_ext(void)
{
	/* get real storage type that from BROM at boot mode*/
	return uboot_spare_head.boot_data.storage_type;
}

int get_boot_storage_type(void)
{
	/* we think that nand and spi-nand are the same storage medium */
	/* so we can use the same process to deal with them */
	if(uboot_spare_head.boot_data.storage_type == STORAGE_NAND
		|| uboot_spare_head.boot_data.storage_type == STORAGE_SPI_NAND)
	{
		return STORAGE_NAND;
	}
	return uboot_spare_head.boot_data.storage_type;
}

void set_boot_storage_type(int storage_type)
{
	uboot_spare_head.boot_data.storage_type = storage_type;
}


u32 get_boot_dram_para_addr(void)
{
	return (u32)uboot_spare_head.boot_data.dram_para;
}

u32 get_boot_dram_para_size(void)
{
	return 32*4;
}

u32 get_boot_dram_update_flag(void)
{
	u32 flag = 0;
	//boot pass dram para to uboot
	//dram_tpr13:bit31
	//0:uboot update boot0  1: not
	__dram_para_t *pdram = (__dram_para_t *)uboot_spare_head.boot_data.dram_para;
	flag = (pdram->dram_tpr13>>31)&0x1;
	return flag == 0 ? 1:0;
}

void set_boot_dram_update_flag(u32 *dram_para)
{
	//dram_tpr13:bit31
	//0:uboot update boot0  1: not
	u32 flag = 0;
	__dram_para_t *pdram = (__dram_para_t *)dram_para;
	flag = pdram->dram_tpr13;
	flag |= (1<<31);
	pdram->dram_tpr13 = flag;
}

u32 get_pmu_byte_from_boot0(void)
{
	return uboot_spare_head.boot_ext[0].data[0];
}

/*
************************************************************************************************************
*
*                                             function
*
*    name          :	get_debugmode_flag
*
*    parmeters     :
*
*    return        :
*
*    note          :	guoyingyang@allwinnertech.com
*
*
************************************************************************************************************
*/
int get_debugmode_flag(void)
{
	int debug_mode = 0;

	if(get_boot_work_mode() != WORK_MODE_BOOT)
	{
		gd->debug_mode = 1;
		return 0;
	}

	 if (!script_parser_fetch("platform", "debug_mode", &debug_mode, 1))
	{
		gd->debug_mode = debug_mode;
	}
	else
	{
		gd->debug_mode = 1;
	}
	return 0;
}

/*
************************************************************************************************************
*
*                                             function
*
*    name          :	update_dram_para_for_ota
*
*    parmeters     :
*
*    return        :
*
*    note          :	after ota, should restore dram para to flash.
*
*
************************************************************************************************************
*/

int update_dram_para_for_ota(void)
{
	extern int do_reset(cmd_tbl_t *cmdtp, int flag, int argc, char * const argv[]);

#ifdef CONFIG_ARCH_SUN50IW1P1
	return 0;
#endif

#ifdef FPGA_PLATFORM
	return 0;
#else
	int ret = 0;
	int card_num = 2;

	if (get_boot_storage_type() == STORAGE_EMMC3)
	{
		card_num = 3;
	}

	if( (get_boot_work_mode() == WORK_MODE_BOOT) &&
		(STORAGE_SD != get_boot_storage_type()))
	{
		if(get_boot_dram_update_flag()|| mmc_request_update_boot0(card_num))
		{
			printf("begin to update boot0 atfer ota\n");
			ret = sunxi_flash_update_boot0();
			printf("update boot0 %s\n", ret==0?"success":"fail");
			//if update fail, should reset the system
			if(ret)
			{
				do_reset(NULL, 0, 0,NULL);
			}
		}
	}
	return ret;
#endif
}

int board_late_init(void)
{
	int ret  = 0;
	if (get_boot_work_mode() == WORK_MODE_BOOT) {

		sunxi_fastboot_status_read();
		sunxi_fastboot_init();
		update_dram_para_for_ota();
		update_bootcmd();
		ret = update_fdt_para_for_kernel(working_fdt);

#ifdef CONFIG_SUNXI_USER_KEY
		/* update mac/wifi serial info in env */
		extern int update_user_data(void);
		update_user_data();
#endif

#ifdef CONFIG_SUNXI_SERIAL
		sunxi_set_serial_num();
#endif
		return 0;
	}

	return ret;
}

uint sunxi_generate_checksum(void *buffer, uint length, uint src_sum)
{
#ifndef CONFIG_USE_NEON_SIMD
	uint *buf;
	uint count;
	uint sum;

	count = length >> 2;
	sum = 0;
	buf = (__u32 *)buffer;
	do
	{
		sum += *buf++;
		sum += *buf++;
		sum += *buf++;
		sum += *buf++;
	}while( ( count -= 4 ) > (4-1) );

	while( count-- > 0 )
		sum += *buf++;
#else
	uint sum;

	sum = add_sum_neon(buffer, length);
#endif
	sum = sum - src_sum + STAMP_VALUE;

    return sum;
}

int sunxi_verify_checksum(void *buffer, uint length, uint src_sum)
{
	uint sum;
	sum = sunxi_generate_checksum(buffer, length, src_sum);

	debug("src sum=%x, check sum=%x\n", src_sum, sum);
	if( sum == src_sum )
		return 0;
	else
		return -1;
}


