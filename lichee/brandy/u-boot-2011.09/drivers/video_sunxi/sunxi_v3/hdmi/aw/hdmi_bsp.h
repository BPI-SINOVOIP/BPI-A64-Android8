/*
 * Allwinner SoCs hdmi driver.
 *
 * Copyright (C) 2016 Allwinner.
 *
 * This file is licensed under the terms of the GNU General Public
 * License version 2.  This program is licensed "as is" without any
 * warranty of any kind, whether express or implied.
 */

#ifndef __HDMI_BSP_H_
#define __HDMI_BSP_H_

#define LINUX_OS

#ifdef LINUX_OS
typedef void (*hdmi_udelay) (unsigned long us);
#ifndef NULL
#define NULL 0
#endif
#define hdmi_udelay(x) \
	do {\
		if (__hdmi_udelay) \
			__hdmi_udelay(x); \
	} while (0)
#else
#define hdmi_udelay(x) udelay(x)
#endif

enum color_space {
	BT601 = 1,
	BT709,
	EXT_CSC,
};

struct video_para {
	unsigned int			vic;
	enum color_space	csc;
	unsigned char			is_hdmi;
	unsigned char			is_yuv;
	unsigned char			is_hcts;
	unsigned int    pixel_clk;
	unsigned int    clk_div;
	unsigned int    pixel_repeat;
	unsigned int    x_res;
	unsigned int    y_res;
	unsigned int    hor_total_time;
	unsigned int    hor_back_porch;
	unsigned int    hor_front_porch;
	unsigned int    hor_sync_time;
	unsigned int    ver_total_time;
	unsigned int    ver_back_porch;
	unsigned int    ver_front_porch;
	unsigned int    ver_sync_time;
	unsigned int    hor_sync_polarity; /* 0: negative, 1: positive */
	unsigned int    ver_sync_polarity; /* 0: negative, 1: positive */
	unsigned int    b_interlace;
};

enum audio_type {
	PCM = 1,
	AC3,
	MPEG1,
	MP3,
	MPEG2,
	AAC,
	DTS,
	ATRAC,
	OBA,
	DDP,
	DTS_HD,
	MAT,
	DST,
	WMA_PRO,
};

struct audio_para {
	enum	audio_type	type;
	unsigned char			ca;
	unsigned int			sample_rate;
	unsigned int			sample_bit;
	unsigned int			ch_num;
	unsigned int			vic;
};

#ifdef LINUX_OS
int api_set_func(hdmi_udelay udelay);
#endif
void bsp_hdmi_set_addr(unsigned int base_addr);
void bsp_hdmi_init(void);
void bsp_hdmi_set_video_en(unsigned char enable);
int bsp_hdmi_video(struct video_para *video);
int bsp_hdmi_audio(struct audio_para *audio);
int bsp_hdmi_ddc_read(char cmd, char pointer, char offset, int nbyte, char *pbuf);
unsigned int bsp_hdmi_get_hpd(void);
void bsp_hdmi_standby(void);
void bsp_hdmi_hrst(void);
void bsp_hdmi_hdl(void);
void bsp_hdmi_hdcp_err_check(void);
/* @version: 0:A, 1:B, 2:C, 3:D */
void bsp_hdmi_set_version(unsigned int version);

#endif
