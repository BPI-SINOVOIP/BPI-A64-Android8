/*
 * Allwinner SoCs display driver.
 *
 * Copyright (C) 2016 Allwinner.
 *
 * This file is licensed under the terms of the GNU General Public
 * License version 2.  This program is licensed "as is" without any
 * warranty of any kind, whether express or implied.
 */

#if defined(CONFIG_DISP2_SUNXI_DEBUG)

#include "dev_disp_debugfs.h"

static struct dentry *my_dispdbg_root;


struct dispdbg_data {
	char command[32];
	char name[32];
	char start[32];
	char param[64];
	char info[256];
	char tmpbuf[318];
};
static struct dispdbg_data dispdbg_priv;

static void dispdbg_process(void)
{
	unsigned int start;
	int err;

	err = kstrtou32(dispdbg_priv.start, 10, &start);
	if (err) {
		pr_warn("Invalid para\n");
		return;
	}

	if (start != 1)
		return;

	if (!strncmp(dispdbg_priv.name, "layer", 5)) {
		char *p = dispdbg_priv.name + 5;
		char *token = p;
		unsigned int disp, chan, id;
		struct disp_layer *lyr = NULL;

		pr_warn("%s,%s\n", dispdbg_priv.command, dispdbg_priv.name);

		token = strsep(&p, " ");
		if (!token) {
			pr_warn("Invalid para\n");
			return;
		}
		err = kstrtou32(token, 10, &disp);
		if (err) {
			pr_warn("Invalid para\n");
			return;
		}
		token = strsep(&p, " ");
		if (!token) {
			pr_warn("Invalid para\n");
			return;
		}
		err = kstrtou32(token, 10, &chan);
		if (err) {
			pr_warn("Invalid para\n");
			return;
		}
		token = strsep(&p, " ");
		if (!token) {
			pr_warn("Invalid para\n");
			return;
		}
		err = kstrtou32(token, 10, &id);
		if (err) {
			pr_warn("Invalid para\n");
			return;
		}
		lyr = disp_get_layer(disp, chan, id);
		if (lyr == NULL) {
			sprintf(dispdbg_priv.info, "get %s fail!",
				dispdbg_priv.name);
			return;
		}
		if (!strncmp(dispdbg_priv.command, "enable", 6)) {
			/* lyr->enable(lyr); */
		} else if (!strncmp(dispdbg_priv.command, "disable", 7)) {
			/* lyr->disable(lyr); */
		} else if (!strncmp(dispdbg_priv.command, "getinfo", 7)) {
			lyr->dump(lyr, dispdbg_priv.info);
		} else {
			sprintf(dispdbg_priv.info,
				"not support command for %s!",
				dispdbg_priv.name);
			return;
		}
	} else if (!strncmp(dispdbg_priv.name, "lcd", 3)) {
		char *p = dispdbg_priv.name + 3;
		unsigned int disp;
		struct disp_device *lcd = NULL;

		err = kstrtou32(p, 10, &disp);
		if (err) {
			pr_warn("Invalid para\n");
			return;
		}
		lcd = disp_get_lcd(disp);
		if (lcd == NULL) {
			sprintf(dispdbg_priv.info, "get %s fail!",
				dispdbg_priv.name);
			return;
		}
		if (!strncmp(dispdbg_priv.command, "enable", 6)) {
			lcd->enable(lcd);
		} else if (!strncmp(dispdbg_priv.command, "disable", 7)) {
			lcd->disable(lcd);
		} else if (!strncmp(dispdbg_priv.command, "setbl", 6)) {
			int bl;

			err = kstrtou32(dispdbg_priv.param, 10, &bl);
			if (err) {
				pr_warn("Invalid para\n");
				return;
			}

			if (lcd->set_bright)
				lcd->set_bright(lcd, bl);
			else
				sprintf(dispdbg_priv.info,
					"set lcd%d backlight fail", disp);
		} else if (!strncmp(dispdbg_priv.command, "getbl", 5)) {
			int bl;

			if (lcd->get_bright) {
				bl = lcd->get_bright(lcd);
				sprintf(dispdbg_priv.info, "%d", bl);
			} else
				sprintf(dispdbg_priv.info,
					"get lcd%d backlight fail", disp);
		} else {
			sprintf(dispdbg_priv.info,
				"not support command for %s!",
				dispdbg_priv.name);
			return;
		}
	} else if (!strncmp(dispdbg_priv.name, "disp", 4)) {
		char *p = dispdbg_priv.name + 4;
		unsigned int disp;
		char *next;
		char *token;
		char *tosearch;
		struct disp_manager *mgr = NULL;

		err = kstrtou32(p, 10, &disp);
		if (err) {
			pr_warn("Invalid para\n");
			return;
		}
		mgr = disp_get_layer_manager(disp);
		if (mgr == NULL) {
			sprintf(dispdbg_priv.info, "get %s fail!",
				dispdbg_priv.name);
			return;
		}
		if (!strncmp(dispdbg_priv.command, "getinfo", 7)) {
			mgr->dump(mgr, dispdbg_priv.info);
		} else if (!strncmp(dispdbg_priv.command, "switch1", 7)) {
			struct disp_device_config config;
			tosearch = dispdbg_priv.param;
			next = strsep(&tosearch, " ");
			config.type = simple_strtoul(next, NULL, 0);
			next = strsep(&tosearch, " ");
			config.mode = simple_strtoul(next, NULL, 0);
			next = strsep(&tosearch, " ");
			config.format = simple_strtoul(next, NULL, 0);
			next = strsep(&tosearch, " ");
			config.bits = simple_strtoul(next, NULL, 0);
			next = strsep(&tosearch, " ");
			config.eotf = simple_strtoul(next, NULL, 0);
			next = strsep(&tosearch, " ");
			config.cs = simple_strtoul(next, NULL, 0);

			bsp_disp_device_set_config(disp, &config);

		} else if (!strncmp(dispdbg_priv.command, "switch", 6)) {
			u32 type, mode;
			tosearch = dispdbg_priv.param;
			token = strsep(&tosearch, " ");
			err = kstrtou32(token, 10, &type);
			if (err) {
				pr_warn("Invalid para\n");
				return;
			}
			token = strsep(&tosearch, " ");
			err = kstrtou32(token, 10, &mode);
			if (err) {
				pr_warn("Invalid para\n");
				return;
			}
			pr_warn("disp %d, type %d, mode%d\n", disp, type, mode);
			bsp_disp_device_switch(disp, type, mode);
		} else if (!strncmp(dispdbg_priv.command, "blank", 5)) {
			u32 level;
			struct disp_device *dispdev = mgr->device;

			if (dispdev == NULL) {
				sprintf(dispdbg_priv.info,
					"get device fail for disp %d!", disp);
				return;
			}

			err = kstrtou32(dispdbg_priv.param, 10, &level);
			if (err) {
				pr_warn("Invalid para\n");
				return;
			}
			pr_warn("disp %d, blank%d\n", disp, level);
			if (level == 0)
				dispdev->enable(dispdev);
			else
				dispdev->disable(dispdev);
		} else if (!strncmp(dispdbg_priv.command, "getxres", 7)) {
			u32 width, height;
			struct disp_device *dispdev = mgr->device;

			if (dispdev == NULL) {
				sprintf(dispdbg_priv.info,
					"get device fail for disp %d!", disp);
				return;
			}
			dispdev->get_resolution(dispdev, &width, &height);

			sprintf(dispdbg_priv.info, "%d", width);
		} else if (!strncmp(dispdbg_priv.command, "getyres", 7)) {
			u32 width, height;
			struct disp_device *dispdev = mgr->device;

			if (dispdev == NULL) {
				sprintf(dispdbg_priv.info,
					"get device fail for disp %d!", disp);
				return;
			}
			dispdev->get_resolution(dispdev, &width, &height);

			sprintf(dispdbg_priv.info, "%d", height);
		} else if (!strncmp(dispdbg_priv.command, "getfps", 6)) {
			u32 fps = bsp_disp_get_fps(disp);
			u32 count = 0;

			count =
			    sprintf(dispdbg_priv.info, "device:%d.%d fps\n",
				    fps / 10, fps % 10);
			/* composer_dump(dispdbg_priv.info+count); */
		} else if (!strncmp(dispdbg_priv.command, "suspend", 7)) {
			disp_suspend(NULL);
		} else if (!strncmp(dispdbg_priv.command, "resume", 6)) {
			disp_resume(NULL);
		} else if (!strncmp(dispdbg_priv.command, "vsync_enable", 12)) {
			unsigned int enable;

			err = kstrtou32(dispdbg_priv.param, 10, &enable);
			if (err) {
				pr_warn("Invalid para\n");
				return;
			}
			bsp_disp_vsync_event_enable(disp,
						    (enable == 1) ?
						    true : false);
		}  else if (!strncmp(dispdbg_priv.command, "setbl", 6)) {
			struct disp_device *lcd = mgr->device;
			int bl = 0;

			err = kstrtos32(dispdbg_priv.param, 10, &bl);
			if (lcd->set_bright)
				lcd->set_bright(lcd, bl);
			else
				sprintf(dispdbg_priv.info,
					"set lcd%d backlight fail", disp);
		} else if (!strncmp(dispdbg_priv.command, "getbl", 5)) {
			int bl;
			struct disp_device *lcd = mgr->device;

			if (lcd->get_bright) {
				bl = lcd->get_bright(lcd);
				sprintf(dispdbg_priv.info, "%d", bl);
			} else
				sprintf(dispdbg_priv.info,
					"get lcd%d backlight fail", disp);
		} else {
			sprintf(dispdbg_priv.info,
				"not support command for %s!",
				dispdbg_priv.name);
			return;
		}
	}
	/*eink debug */
#if defined(SUPPORT_EINK)
	else if (!strncmp(dispdbg_priv.name, "eink", 4)) {

		struct disp_eink_manager *eink_manager = NULL;
		enum eink_update_mode mode;
		int enable_decode = 0;
		struct area_info area;
		char *next;
		char *tosearch;
		char *token;

		eink_manager = disp_get_eink_manager(0);
		memset((void *)&area, 0, sizeof(struct area_info));

		if (!strncmp(dispdbg_priv.command, "update", 6)) {
			tosearch = dispdbg_priv.param;
			token = strsep(&tosearch, " ");
			err = kstrtou32(token, 10, &mode);
			if (err) {
				pr_warn("Invalid para\n");
				return;
			}

			token = strsep(&tosearch, " ");
			err = kstrtou32(token, 10, &area.x_top);
			if (err) {
				pr_warn("Invalid para\n");
				return;
			}
			next = strsep(&tosearch, " ");
			err = kstrtou32(token, 10, &area.y_top);
			if (err) {
				pr_warn("Invalid para\n");
				return;
			}
			next = strsep(&tosearch, " ");
			err = kstrtou32(token, 10, &area.x_bottom);
			if (err) {
				pr_warn("Invalid para\n");
				return;
			}
			next = strsep(&tosearch, " ");
			err = kstrtou32(token, 10, &area.y_bottom);
			if (err) {
				pr_warn("Invalid para\n");
				return;
			}

		} else if (!strncmp(dispdbg_priv.command, "disable", 7)) {
			if (eink_manager->disable)
				eink_manager->disable(eink_manager);
			else
				pr_warn("%s: eink disable err!\n", __func__);
		} else if (!strncmp(dispdbg_priv.command, "clearwd", 7)) {
			unsigned int overlap = 1;

			tosearch = dispdbg_priv.param;
			token = strsep(&tosearch, " ");
			err = kstrtou32(token, 10, &overlap);
			if (err) {
				pr_warn("Invalid para\n");
				return;
			}
			if (eink_manager->clearwd)
				eink_manager->clearwd(eink_manager, overlap);
			else
				pr_warn("%s: eink clearwd err!\n", __func__);
		} else if (!strncmp(dispdbg_priv.command, "decode", 6)) {
			tosearch = dispdbg_priv.param;
			token = strsep(&tosearch, " ");
			err = kstrtou32(token, 10, &enable_decode);
			if (err) {
				pr_warn("Invalid para\n");
				return;
			}
			if (eink_manager->decode)
				eink_manager->decode(eink_manager,
						     enable_decode);
			else
				pr_warn("%s: eink decode err!\n", __func__);
		} else if (!strncmp(dispdbg_priv.command, "continue", 8)) {
			tosearch = dispdbg_priv.param;
			token = strsep(&tosearch, " ");
			err = kstrtou32(token,
				       10,
				       &eink_manager->flush_continue_flag);
			if (err) {
				pr_warn("Invalid para\n");
				return;
			}
		}

	}
#endif
	else if (!strncmp(dispdbg_priv.name, "enhance", 7)) {
		char *p = dispdbg_priv.name + 7;
		unsigned int disp;
		struct disp_manager *mgr = NULL;
		struct disp_enhance *enhance = NULL;
		struct disp_enhance_para para;

		memset(&para, 0, sizeof(struct disp_enhance_para));
		err = kstrtou32(p, 10, &disp);
		if (err) {
			pr_warn("Invalid para\n");
			return;
		}
		mgr = disp_get_layer_manager(disp);
		if (mgr == NULL) {
			sprintf(dispdbg_priv.info, "get %s fail!",
				dispdbg_priv.name);
			return;
		}
		enhance = mgr->enhance;
		if (enhance == NULL) {
			sprintf(dispdbg_priv.info, "get %s fail!",
				dispdbg_priv.name);
			return;
		}

		if (!strncmp(dispdbg_priv.command, "getinfo", 7)) {
			if (enhance->dump)
				enhance->dump(enhance, dispdbg_priv.info);
		} else {
			sprintf(dispdbg_priv.info,
				"not support command for %s!",
				dispdbg_priv.name);
			return;
		}
	} else if (!strncmp(dispdbg_priv.name, "smbl", 4)) {
		char *p = dispdbg_priv.name + 4;
		unsigned int disp;
		struct disp_manager *mgr = NULL;
		struct disp_smbl *smbl = NULL;

		err = kstrtou32(p, 10, &disp);
		if (err) {
			pr_warn("Invalid para\n");
			return;
		}
		mgr = disp_get_layer_manager(disp);
		if (mgr == NULL) {
			sprintf(dispdbg_priv.info, "get %s fail!",
				dispdbg_priv.name);
			return;
		}
		smbl = mgr->smbl;
		if (smbl == NULL) {
			sprintf(dispdbg_priv.info, "get %s fail!",
				dispdbg_priv.name);
			return;
		}
		if (!strncmp(dispdbg_priv.command, "setinfo", 7)) {

		} else if (!strncmp(dispdbg_priv.command, "getinfo", 7)) {
			if (smbl->dump)
				smbl->dump(smbl, dispdbg_priv.info);
		} else {
			sprintf(dispdbg_priv.info,
				"not support command for %s!",
				dispdbg_priv.name);
			return;
		}
	} else if (!strncmp(dispdbg_priv.name, "hdmi", 4)) {
		char *p = dispdbg_priv.name + 4;
		unsigned int disp;
		unsigned int mode;

		err = kstrtou32(p, 10, &disp);
		if (err) {
			pr_warn("Invalid para\n");
			return;
		}
		if (!strncmp(dispdbg_priv.command, "is_support", 10)) {
			int is_support = 0;

			err = kstrtou32(dispdbg_priv.param, 10, &mode);
			if (err) {
				pr_warn("Invalid para\n");
				return;
			}

			is_support =
			    bsp_disp_hdmi_check_support_mode(disp,
					     (enum disp_output_type)mode);
			sprintf(dispdbg_priv.info, "%d", is_support);
		} else {
			sprintf(dispdbg_priv.info,
				"not support command for %s!",
				dispdbg_priv.name);
			return;
		}
	}
}

/* ##########command############### */
static int dispdbg_command_open(struct inode *inode, struct file *file)
{
	return 0;
}
static int dispdbg_command_release(struct inode *inode, struct file *file)
{
	return 0;
}
static ssize_t dispdbg_command_read(struct file *file, char __user *buf,
				    size_t count, loff_t *ppos)
{
	int len = strlen(dispdbg_priv.command);

	strcpy(dispdbg_priv.tmpbuf, dispdbg_priv.command);
	dispdbg_priv.tmpbuf[len] = 0x0A;
	dispdbg_priv.tmpbuf[len + 1] = 0x0;
	len = strlen(dispdbg_priv.tmpbuf);
	if (len) {
		if (*ppos >= len)
			return 0;
		if (count >= len)
			count = len;
		if (count > (len - *ppos))
			count = (len - *ppos);

		if (copy_to_user
		    ((void __user *)buf, (const void *)dispdbg_priv.tmpbuf,
		     (unsigned long)len)) {
			pr_warn("copy_to_user fail\n");
			return 0;
		}
		*ppos += count;
	} else
		count = 0;
	return count;
}

static ssize_t dispdbg_command_write(struct file *file, const char __user *buf,
				     size_t count, loff_t *ppos)
{
	if (count >= sizeof(dispdbg_priv.command))
		return 0;
	if (copy_from_user(dispdbg_priv.command, buf, count)) {
		pr_warn("copy_from_user fail\n");
		return 0;
	}

	if (dispdbg_priv.command[count - 1] == 0x0A)
		dispdbg_priv.command[count - 1] = 0;
	else
		dispdbg_priv.command[count] = 0;
	return count;
}

static int dispdbg_name_open(struct inode *inode, struct file *file)
{
	return 0;
}
static int dispdbg_name_release(struct inode *inode, struct file *file)
{
	return 0;
}
static ssize_t dispdbg_name_read(struct file *file, char __user *buf,
				 size_t count, loff_t *ppos)
{
	int len = strlen(dispdbg_priv.name);

	strcpy(dispdbg_priv.tmpbuf, dispdbg_priv.name);
	dispdbg_priv.tmpbuf[len] = 0x0A;
	dispdbg_priv.tmpbuf[len + 1] = 0x0;
	len = strlen(dispdbg_priv.tmpbuf);
	if (len) {
		if (*ppos >= len)
			return 0;
		if (count >= len)
			count = len;
		if (count > (len - *ppos))
			count = (len - *ppos);

		if (copy_to_user
		    ((void __user *)buf, (const void *)dispdbg_priv.tmpbuf,
		     (unsigned long)len)) {
			pr_warn("copy_to_user fail\n");
			return 0;
		}

		*ppos += count;
	} else
		count = 0;
	return count;
}

static ssize_t dispdbg_name_write(struct file *file, const char __user *buf,
				  size_t count, loff_t *ppos)
{
	if (count >= sizeof(dispdbg_priv.name))
		return 0;
	if (copy_from_user(dispdbg_priv.name, buf, count)) {
		pr_warn("copy_from_user fail\n");
		return 0;
	}

	if (dispdbg_priv.name[count - 1] == 0x0A)
		dispdbg_priv.name[count - 1] = 0;
	else
		dispdbg_priv.name[count] = 0;
	return count;
}

static int dispdbg_param_open(struct inode *inode, struct file *file)
{
	return 0;
}
static int dispdbg_param_release(struct inode *inode, struct file *file)
{
	return 0;
}
static ssize_t dispdbg_param_read(struct file *file, char __user *buf,
				  size_t count, loff_t *ppos)
{
	int len = strlen(dispdbg_priv.param);

	strcpy(dispdbg_priv.tmpbuf, dispdbg_priv.param);
	dispdbg_priv.tmpbuf[len] = 0x0A;
	dispdbg_priv.tmpbuf[len + 1] = 0x0;
	len = strlen(dispdbg_priv.tmpbuf);
	if (len) {
		if (*ppos >= len)
			return 0;
		if (count >= len)
			count = len;
		if (count > (len - *ppos))
			count = (len - *ppos);
		if (copy_to_user
		    ((void __user *)buf, (const void *)dispdbg_priv.tmpbuf,
		     (unsigned long)len)) {
			pr_warn("copy_to_user fail\n");
			return 0;
		}
		*ppos += count;
	} else
		count = 0;
	return count;
}
static ssize_t dispdbg_param_write(struct file *file, const char __user *buf,
				   size_t count, loff_t *ppos)
{
	if (count >= sizeof(dispdbg_priv.param))
		return 0;
	if (copy_from_user(dispdbg_priv.param, buf, count)) {
		pr_warn("copy_from_user fail\n");
		return 0;
	}

	if (dispdbg_priv.param[count - 1] == 0x0A)
		dispdbg_priv.param[count - 1] = 0;
	else
		dispdbg_priv.param[count] = 0;
	return count;
}

static int dispdbg_start_open(struct inode *inode, struct file *file)
{
	return 0;
}
static int dispdbg_start_release(struct inode *inode, struct file *file)
{
	return 0;
}
static ssize_t dispdbg_start_read(struct file *file, char __user *buf,
				  size_t count, loff_t *ppos)
{
	int len = strlen(dispdbg_priv.start);

	strcpy(dispdbg_priv.tmpbuf, dispdbg_priv.start);
	dispdbg_priv.tmpbuf[len] = 0x0A;
	dispdbg_priv.tmpbuf[len + 1] = 0x0;
	len = strlen(dispdbg_priv.tmpbuf);
	if (len) {
		if (*ppos >= len)
			return 0;
		if (count >= len)
			count = len;
		if (count > (len - *ppos))
			count = (len - *ppos);
		if (copy_to_user
		    ((void __user *)buf, (const void *)dispdbg_priv.tmpbuf,
		     (unsigned long)len)) {
			pr_warn("copy_to_user fail\n");
			return 0;
		}
		*ppos += count;
	} else
		count = 0;
	return count;
}
static ssize_t dispdbg_start_write(struct file *file, const char __user *buf,
				   size_t count, loff_t *ppos)
{
	if (count >= sizeof(dispdbg_priv.start))
		return 0;
	if (copy_from_user(dispdbg_priv.start, buf, count)) {
		pr_warn("copy_from_user fail\n");
		return 0;
	}

	if (dispdbg_priv.start[count - 1] == 0x0A)
		dispdbg_priv.start[count - 1] = 0;
	else
		dispdbg_priv.start[count] = 0;
	dispdbg_process();
	return count;
}

static int dispdbg_info_open(struct inode *inode, struct file *file)
{
	return 0;
}
static int dispdbg_info_release(struct inode *inode, struct file *file)
{
	return 0;
}

static ssize_t dispdbg_info_read(struct file *file, char __user *buf,
				 size_t count, loff_t *ppos)
{
	int len = strlen(dispdbg_priv.info);

	strcpy(dispdbg_priv.tmpbuf, dispdbg_priv.info);
	dispdbg_priv.tmpbuf[len] = 0x0A;
	dispdbg_priv.tmpbuf[len + 1] = 0x0;
	len = strlen(dispdbg_priv.tmpbuf);

	if (len) {
		if (*ppos >= len)
			return 0;
		if (count >= len)
			count = len;
		if (count > (len - *ppos))
			count = (len - *ppos);
		if (copy_to_user
		    ((void __user *)buf, (const void *)dispdbg_priv.tmpbuf,
		     (unsigned long)len)) {
			pr_warn("copy_to_user fail\n");
			return 0;
		}
		*ppos += count;
	} else
		count = 0;
	return count;
}
static ssize_t dispdbg_info_write(struct file *file, const char __user *buf,
				  size_t count, loff_t *ppos)
{
	if (count >= sizeof(dispdbg_priv.info))
		return 0;
	if (copy_from_user(dispdbg_priv.info, buf, count)) {
		pr_warn("copy_from_user fail\n");
		return 0;
	}

	if (dispdbg_priv.info[count - 1] == 0x0A)
		dispdbg_priv.info[count - 1] = 0;
	else
		dispdbg_priv.info[count] = 0;
	return count;
}
static const struct file_operations command_ops = {
	.write = dispdbg_command_write,
	.read = dispdbg_command_read,
	.open = dispdbg_command_open,
	.release = dispdbg_command_release,
};
static const struct file_operations name_ops = {
	.write = dispdbg_name_write,
	.read = dispdbg_name_read,
	.open = dispdbg_name_open,
	.release = dispdbg_name_release,
};

static const struct file_operations start_ops = {
	.write = dispdbg_start_write,
	.read = dispdbg_start_read,
	.open = dispdbg_start_open,
	.release = dispdbg_start_release,
};
static const struct file_operations param_ops = {
	.write = dispdbg_param_write,
	.read = dispdbg_param_read,
	.open = dispdbg_param_open,
	.release = dispdbg_param_release,
};
static const struct file_operations info_ops = {
	.write = dispdbg_info_write,
	.read = dispdbg_info_read,
	.open = dispdbg_info_open,
	.release = dispdbg_info_release,
};

#if defined(CONFIG_SUNXI_MPP)
ssize_t disp_mpp_read(struct file *file, char __user *buf,
				 size_t count, loff_t *ppos)
{
	struct disp_manager *mgr = NULL;
	struct disp_device *dispdev = NULL;
	int num_screens, screen_id;
	int num_layers, layer_id;
	int num_chans, chan_id;
	size_t buf_cnt = 0;

	num_screens = bsp_disp_feat_get_num_screens();
	for (screen_id = 0; screen_id < num_screens; screen_id++) {
		int width = 0, height = 0;
		int fps = 0;
		struct disp_health_info info;

		mgr = disp_get_layer_manager(screen_id);
		if (mgr == NULL)
			continue;
		dispdev = mgr->device;
		if (dispdev == NULL)
			continue;
		dispdev->get_resolution(dispdev, &width, &height);
		fps = bsp_disp_get_fps(screen_id);
		bsp_disp_get_health_info(screen_id, &info);

		if (!dispdev->is_enabled(dispdev))
			continue;
		buf_cnt += sprintf(buf + buf_cnt, "screen %d:\n", screen_id);
		buf_cnt += sprintf(buf + buf_cnt, "de_rate %d hz, ref_fps:%d\n",
				 mgr->get_clk_rate(mgr),
				 dispdev->get_fps(dispdev));
		buf_cnt += mgr->dump(mgr, buf + buf_cnt);
		/* output */
		if (dispdev->type == DISP_OUTPUT_TYPE_LCD) {
			buf_cnt += sprintf(buf + buf_cnt,
				"\tlcd output\tbacklight(%3d)\tfps:%d.%d",
				dispdev->get_bright(dispdev), fps / 10,
				fps % 10);
		} else if (dispdev->type == DISP_OUTPUT_TYPE_HDMI) {
			unsigned int mode = dispdev->get_mode(dispdev);

			buf_cnt += sprintf(buf + buf_cnt,
					 "\thdmi output mode(%d)\tfps:%d.%d",
					 mode, fps / 10, fps % 10);
		} else if (dispdev->type == DISP_OUTPUT_TYPE_TV) {
			unsigned int mode = dispdev->get_mode(dispdev);

			buf_cnt += sprintf(buf + buf_cnt,
					 "\ttv output mode(%d)\tfps:%d.%d",
					 mode, fps / 10, fps % 10);
		} else if (dispdev->type == DISP_OUTPUT_TYPE_VGA) {
			unsigned int mode = dispdev->get_mode(dispdev);

			buf_cnt += sprintf(buf + buf_cnt,
					 "\tvga output mode(%d)\tfps:%d.%d",
					 mode, fps / 10, fps % 10);
		}
		if (dispdev->type != DISP_OUTPUT_TYPE_NONE) {
			buf_cnt += sprintf(buf + buf_cnt, "\t%4ux%4u\n",
					 width, height);
			buf_cnt += sprintf(buf + buf_cnt,
					"\terr:%u\tskip:%u\tirq:%llu\tvsync:%u\n",
					info.error_cnt, info.skip_cnt,
					info.irq_cnt, info.vsync_cnt);
		}

		num_chans = bsp_disp_feat_get_num_channels(screen_id);

		/* layer info */
		for (chan_id = 0; chan_id < num_chans; chan_id++) {
			num_layers =
			    bsp_disp_feat_get_num_layers_by_chn(screen_id,
								chan_id);
			for (layer_id = 0; layer_id < num_layers; layer_id++) {
				struct disp_layer *lyr = NULL;
				struct disp_layer_config config;

				lyr = disp_get_layer(screen_id, chan_id,
						     layer_id);
				config.channel = chan_id;
				config.layer_id = layer_id;
				mgr->get_layer_config(mgr, &config, 1);
				if (lyr && (true == config.enable) && lyr->dump)
					buf_cnt +=
					    lyr->dump(lyr, buf + buf_cnt);
			}
		}
	}


#if defined(CONFIG_DISP2_SUNXI_COMPOSER)
	buf_cnt += composer_dump(buf + buf_cnt);
#endif
	if (*ppos >= buf_cnt)
		return 0;
	if (count >= buf_cnt)
		count = buf_cnt;
	if (count > (buf_cnt - *ppos))
		count = (buf_cnt - *ppos);

	*ppos += count;

	return count;
}

static const struct file_operations vo_ops = {
	.owner      = THIS_MODULE,
	.read = disp_mpp_read,
	.open = dispdbg_name_open,
	.release = dispdbg_name_release,
};
#endif /*endif CONFIG_SUNXI_MPP */

int dispdbg_init(void)
{
	my_dispdbg_root = debugfs_create_dir("dispdbg", NULL);
	if (!debugfs_create_file
	    ("command", 0644, my_dispdbg_root, NULL, &command_ops))
		goto Fail;
	if (!debugfs_create_file
	    ("name", 0644, my_dispdbg_root, NULL, &name_ops))
		goto Fail;
	if (!debugfs_create_file
	    ("start", 0644, my_dispdbg_root, NULL, &start_ops))
		goto Fail;
	if (!debugfs_create_file
	    ("param", 0644, my_dispdbg_root, NULL, &param_ops))
		goto Fail;
	if (!debugfs_create_file
	    ("info", 0644, my_dispdbg_root, NULL, &info_ops))
		goto Fail;
#if defined(CONFIG_SUNXI_MPP)
	if (debugfs_mpp_root) {
		if (!debugfs_create_file("vo", 0644, debugfs_mpp_root, NULL,
					 &vo_ops))
			goto Fail;
	}
#endif /*endif CONFIG_SUNXI_MPP */
	return 0;

Fail:
	debugfs_remove_recursive(my_dispdbg_root);
	my_dispdbg_root = NULL;
	return -ENOENT;
}

int dispdbg_exit(void)
{
	if (my_dispdbg_root != NULL) {
		debugfs_remove_recursive(my_dispdbg_root);
		my_dispdbg_root = NULL;
	}
	return 0;
}
#endif /*endif CONFIG_DISP2_SUNXI_DEBUG */
