/*
 * A V4L2 driver for GalaxyCore GC0328 cameras.
 *
 */
#include <linux/init.h>
#include <linux/module.h>
#include <linux/slab.h>
#include <linux/i2c.h>
#include <linux/delay.h>
#include <linux/videodev2.h>
#include <linux/clk.h>
#include <media/v4l2-device.h>
#include <media/v4l2-mediabus.h>
#include <linux/io.h>

#include "camera.h"
#include "sensor_helper.h"

MODULE_AUTHOR("raymonxiu");
MODULE_DESCRIPTION("A low-level driver for GalaxyCore GC0328 sensors");
MODULE_LICENSE("GPL");

/* for internal driver debug */
#define DEV_DBG_EN     0
#if (DEV_DBG_EN == 1)
#define vfe_dev_dbg(x, arg...) pr_debug("[CSI_DEBUG][GC0328]"x, ##arg)
#else
#define vfe_dev_dbg(x, arg...)
#endif
#define vfe_dev_err(x, arg...) pr_err("[CSI_ERR][GC0328]"x, ##arg)
#define vfe_dev_print(x, arg...) pr_info("[CSI][GC0328]"x, ##arg)

/*
 *#define LOG_ERR_RET(x) { \
 *			  int ret; \
 *			  ret = x; \
 *			  if (ret < 0) { \
 *			    vfe_dev_err("error at %s\n", __func__); \
 *			    return ret; \
 *			  } \
 *			}
 */

/* define module timing */
#define MCLK (24*1000*1000)
#define VREF_POL          V4L2_MBUS_VSYNC_ACTIVE_HIGH
#define HREF_POL          V4L2_MBUS_HSYNC_ACTIVE_HIGH
#define CLK_POL           V4L2_MBUS_PCLK_SAMPLE_RISING
#define V4L2_IDENT_SENSOR 0x0328

/*
 * Our nominal (default) frame rate.
 */
#define SENSOR_FRAME_RATE 10

/*
 * The GC0328 sits on i2c with ID 0x42
 */
#define I2C_ADDR 0x42
#define SENSOR_NAME "gc0328c"
/*
 * Information we maintain about a known sensor.
 */
struct sensor_format_struct;  /* coming later */

struct cfg_array { /* coming later */
	struct regval_list *regs;
	int size;
};

static int LOG_ERR_RET(int x)
{
	int ret;

	ret = x;
	if (ret < 0)
		vfe_dev_err("error at %s\n", __func__);
	return ret;
}

static inline struct sensor_info *to_state(struct v4l2_subdev *sd)
{
	return container_of(sd, struct sensor_info, sd);
}

static struct regval_list sensor_default_regs[] = {
	{0xfe, 0x80},
	{0xfe, 0x80},
	{0xfc, 0x16},
	{0xfc, 0x16},
	{0xfc, 0x16},
	{0xfc, 0x16},
	{0xf1, 0x00},
	{0xf2, 0x00},
	{0xfe, 0x00},
	{0x4f, 0x00},
	{0x42, 0x00},
	{0x03, 0x00},
	{0x04, 0xc0},
	{0x77, 0x62},
	{0x78, 0x40},
	{0x79, 0x4d},

	{0xfe, 0x00},
	{0x0d, 0x01},
	{0x0e, 0xe8},
	{0x0f, 0x02},
	{0x10, 0x88},
	{0x09, 0x00},
	{0x0a, 0x00},
	{0x0b, 0x00},
	{0x0c, 0x00},
	{0x16, 0x00},
	{0x17, 0x14},
	{0x18, 0x0e},
	{0x19, 0x06},

	{0x1b, 0x48},
	{0x1f, 0xC8},
	{0x20, 0x01},
	{0x21, 0x78},
	{0x22, 0xb0},
	{0x23, 0x04}, /* 0x06 20140519 GC0328C */
	{0x24, 0x11},
	{0x26, 0x00},
	{0x50, 0x01}, /* crop mode */

	/* global gain for range */
	{0x70, 0x85},

	/* ///////////banding///////////// */
	{0x05, 0x01}, /* hb */
	{0x06, 0x06},
	{0x07, 0x00}, /* vb */
	{0x08, 0x70},
	{0xfe, 0x01},
	{0x29, 0x00}, /* anti-flicker step [11:8] */
	{0x2a, 0x96}, /* anti-flicker step [7:0] */
	{0x2b, 0x02}, /* exp level 0 14.28fps */
	{0x2c, 0x58},
	{0x2d, 0x02}, /* exp level 1 12.50fps */
	{0x2e, 0xee},
	{0x2f, 0x04}, /* exp level 2 10.00fps */
	{0x30, 0xb0},
	{0x31, 0x05}, /* exp level 3 7.14fps */
	{0x32, 0xdc},
	{0x33, 0x00},
	{0xfe, 0x00},

	/* /////////////AWB////////////// */
	{0xfe, 0x01},
	{0x50, 0x00},
	{0x4f, 0x00},
	{0x4c, 0x01},
	{0x4f, 0x00},
	{0x4f, 0x00},
	{0x4f, 0x00},
	{0x4f, 0x00},
	{0x4f, 0x00},
	{0x4d, 0x30},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4d, 0x40},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4d, 0x50},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4d, 0x60},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4d, 0x70},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4f, 0x01},
	{0x50, 0x88},
	{0xfe, 0x00},

	/* ////////// BLK////////////////////// */
	{0xfe, 0x00},
	{0x27, 0xb7},
	{0x28, 0x7F},
	{0x29, 0x20},
	{0x33, 0x20},
	{0x34, 0x20},
	{0x35, 0x20},
	{0x36, 0x20},
	{0x32, 0x08},
	{0x3b, 0x00},
	{0x3c, 0x00},
	{0x3d, 0x00},
	{0x3e, 0x00},
	{0x47, 0x00},
	{0x48, 0x00},

	/* ////////// block enable///////////// */
	{0x40, 0x7f},
	{0x41, 0x26},
	{0x42, 0xfb},
	{0x44, 0x02}, /* yuv */
	{0x45, 0x00},
	{0x46, 0x03},
	{0x4f, 0x01},
	{0x4b, 0x01},
	{0x50, 0x01},

	/* /////////////DN & EEINTP/////////// */
	{0x7e, 0x0a},
	{0x7f, 0x03},
	{0x81, 0x15},
	{0x82, 0x85},
	{0x83, 0x03},
	{0x84, 0xe5},
	{0x90, 0xac},
	{0x92, 0x02},
	{0x94, 0x02},
	{0x95, 0x32},

	/* ///////////////YCP/////////////// */
	{0xd1, 0x28},
	{0xd2, 0x28},
	{0xd3, 0x40},
	{0xdd, 0x58},
	{0xde, 0x36},
	{0xe4, 0x88},
	{0xe5, 0x40},
	{0xd7, 0x0e},

	/* //////////rgb gamma //////////// */
	{0xfe, 0x00},
	{0xbf, 0x0e},
	{0xc0, 0x1c},
	{0xc1, 0x34},
	{0xc2, 0x48},
	{0xc3, 0x5a},
	{0xc4, 0x6e},
	{0xc5, 0x80},
	{0xc6, 0x9c},
	{0xc7, 0xb4},
	{0xc8, 0xc7},
	{0xc9, 0xd7},
	{0xca, 0xe3},
	{0xcb, 0xed},
	{0xcc, 0xf2},
	{0xcd, 0xf8},
	{0xce, 0xfd},
	{0xcf, 0xff},

	/* ///////////Y gamma////////// */
	{0xfe, 0x00},
	{0x63, 0x00},
	{0x64, 0x05},
	{0x65, 0x0b},
	{0x66, 0x19},
	{0x67, 0x2e},
	{0x68, 0x40},
	{0x69, 0x54},
	{0x6a, 0x66},
	{0x6b, 0x86},
	{0x6c, 0xa7},
	{0x6d, 0xc6},
	{0x6e, 0xe4},
	{0x6f, 0xff},

	/* ////////////ASDE///////////// */
	{0xfe, 0x01},
	{0x18, 0x02},
	{0xfe, 0x00},
	{0x98, 0x00},
	{0x9b, 0x20},
	{0x9c, 0x80},
	{0xa4, 0x10},
	{0xa8, 0xB0},
	{0xaa, 0x40},
	{0xa2, 0x23},
	{0xad, 0x01},

	/* ////////////abs/////////// */
	{0xfe, 0x01},
	{0x9c, 0x02},
	{0x9e, 0xc0},
	{0x9f, 0x40},

	/* //////////// AEC//////////// */
	{0x08, 0xa0},
	{0x09, 0xe8},
	{0x10, 0x00},
	{0x11, 0x11},
	{0x12, 0x10},
	{0x13, 0x98},
	{0x15, 0xfc},
	{0x18, 0x03},
	{0x21, 0xc0},
	{0x22, 0x60},
	{0x23, 0x30},
	{0x25, 0x00},
	{0x24, 0x14},
	{0x3d, 0x80},
	{0x3e, 0x40},

	/* //////////////AWB/////////// */
	{0xfe, 0x01},
	{0x51, 0x88},
	{0x52, 0x12},
	{0x53, 0x80},
	{0x54, 0x60},
	{0x55, 0x01},
	{0x56, 0x02},
	{0x58, 0x00},
	{0x5b, 0x02},
	{0x5e, 0xa4},
	{0x5f, 0x8a},
	{0x61, 0xdc},
	{0x62, 0xdc},
	{0x70, 0xfc},
	{0x71, 0x10},
	{0x72, 0x30},
	{0x73, 0x0b},
	{0x74, 0x0b},
	{0x75, 0x01},
	{0x76, 0x00},
	{0x77, 0x40},
	{0x78, 0x70},
	{0x79, 0x00},
	{0x7b, 0x00},
	{0x7c, 0x71},
	{0x7d, 0x00},
	{0x80, 0x70},
	{0x81, 0x58},
	{0x82, 0x98},
	{0x83, 0x60},
	{0x84, 0x58},
	{0x85, 0x50},
	{0xfe, 0x00},

	/* //////////////LSC//////////////// */
	{0xfe, 0x01},
	{0xc0, 0x10},
	{0xc1, 0x0c},
	{0xc2, 0x0a},
	{0xc6, 0x0e},
	{0xc7, 0x0b},
	{0xc8, 0x0a},
	{0xba, 0x26},
	{0xbb, 0x1c},
	{0xbc, 0x1d},
	{0xb4, 0x23},
	{0xb5, 0x1c},
	{0xb6, 0x1a},
	{0xc3, 0x00},
	{0xc4, 0x00},
	{0xc5, 0x00},
	{0xc9, 0x00},
	{0xca, 0x00},
	{0xcb, 0x00},
	{0xbd, 0x00},
	{0xbe, 0x00},
	{0xbf, 0x00},
	{0xb7, 0x07},
	{0xb8, 0x05},
	{0xb9, 0x05},
	{0xa8, 0x07},
	{0xa9, 0x06},
	{0xaa, 0x00},
	{0xab, 0x04},
	{0xac, 0x00},
	{0xad, 0x02},
	{0xae, 0x0d},
	{0xaf, 0x05},
	{0xb0, 0x00},
	{0xb1, 0x07},
	{0xb2, 0x03},
	{0xb3, 0x00},
	{0xa4, 0x00},
	{0xa5, 0x00},
	{0xa6, 0x00},
	{0xa7, 0x00},
	{0xa1, 0x3c},
	{0xa2, 0x50},
	{0xfe, 0x00},

	/* /////////////CCT /////////// */
	{0xb1, 0x12},
	{0xb2, 0xf5},
	{0xb3, 0xfe},
	{0xb4, 0xe0},
	{0xb5, 0x15},
	{0xb6, 0xc8},

	/* /////////skin CC for front //////// */
	{0xb1, 0x00},
	{0xb2, 0x00},
	{0xb3, 0x05},
	{0xb4, 0xf0},
	{0xb5, 0x00},
	{0xb6, 0x00},

	/* ///////////////AWB//////////////// */
	{0xfe, 0x01},
	{0x50, 0x00},
	{0xfe, 0x01},
	{0x4f, 0x00},
	{0x4c, 0x01},
	{0x4f, 0x00},
	{0x4f, 0x00},
	{0x4f, 0x00},
	{0x4d, 0x34},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x02},
	{0x4e, 0x02},
	{0x4d, 0x44},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4d, 0x53},
	{0x4e, 0x00},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4e, 0x04},
	{0x4d, 0x65},
	{0x4e, 0x04},
	{0x4d, 0x73},
	{0x4e, 0x20},
	{0x4d, 0x83},
	{0x4e, 0x20},
	{0x4f, 0x01},
	{0x50, 0x88},
	/* ///////////output////////// */
	{0xfe, 0x00},
	{0xf1, 0x07},
	{0xf2, 0x01},
};

/*
 * The white balance settings
 * Here only tune the R G B channel gain.
 * The white balance enalbe bit is modified in sensor_s_autowb and sensor_s_wb
 */
static struct regval_list sensor_wb_manual[] = {
	{0xfe, 0x00},
};

static struct regval_list sensor_wb_auto_regs[] = {
	{0xfe, 0x00},
	{0x42, 0xfe}
};

static struct regval_list sensor_wb_incandescence_regs[] = {
	{0xfe, 0x00},
	{0x42, 0xfd},
	{0x77, 0x48},
	{0x78, 0x40},
	{0x79, 0x5c},

};

static struct regval_list sensor_wb_fluorescent_regs[] = {
	{0xfe, 0x00},
	{0x42, 0xfd},
	{0x77, 0x40},
	{0x78, 0x42},
	{0x79, 0x50},

};

static struct regval_list sensor_wb_tungsten_regs[] = {
	{0xfe, 0x00},
	{0x42, 0xfd},
	{0x77, 0x40},
	{0x78, 0x54},
	{0x79, 0x70},

};

static struct regval_list sensor_wb_horizon[] = {
	/* null */
};

static struct regval_list sensor_wb_daylight_regs[] = {
	{0xfe, 0x00},
	{0x42, 0xfd},
	{0x77, 0x74},
	{0x78, 0x52},
	{0x79, 0x40},

};

static struct regval_list sensor_wb_flash[] = {
	/* null */
};


static struct regval_list sensor_wb_cloud_regs[] = {
	{0xfe, 0x00},
	{0x42, 0xfd},
	{0x77, 0x8c}, /* WB_manual_gain */
	{0x78, 0x50},
	{0x79, 0x40},
};

static struct regval_list sensor_wb_shade[] = {
	/* null */
};

static struct cfg_array sensor_wb[] = {
	{
		/* V4L2_WHITE_BALANCE_MANUAL */
		.regs = sensor_wb_manual,
		.size = ARRAY_SIZE(sensor_wb_manual),
	},
	{
		/* V4L2_WHITE_BALANCE_AUTO */
		.regs = sensor_wb_auto_regs,
		.size = ARRAY_SIZE(sensor_wb_auto_regs),
	},
	{
		/* V4L2_WHITE_BALANCE_INCANDESCENT */
		.regs = sensor_wb_incandescence_regs,
		.size = ARRAY_SIZE(sensor_wb_incandescence_regs),
	},
	{
		/* V4L2_WHITE_BALANCE_FLUORESCENT */
		.regs = sensor_wb_fluorescent_regs,
		.size = ARRAY_SIZE(sensor_wb_fluorescent_regs),
	},
	{
		/* V4L2_WHITE_BALANCE_FLUORESCENT_H */
		.regs = sensor_wb_tungsten_regs,
		.size = ARRAY_SIZE(sensor_wb_tungsten_regs),
	},
	{
		/* V4L2_WHITE_BALANCE_HORIZON */
		.regs = sensor_wb_horizon,
		.size = ARRAY_SIZE(sensor_wb_horizon),
	},
	{
		/* V4L2_WHITE_BALANCE_DAYLIGHT */
		.regs = sensor_wb_daylight_regs,
		.size = ARRAY_SIZE(sensor_wb_daylight_regs),
	},
	{
		/* V4L2_WHITE_BALANCE_FLASH */
		.regs = sensor_wb_flash,
		.size = ARRAY_SIZE(sensor_wb_flash),
	},
	{
		/* V4L2_WHITE_BALANCE_CLOUDY */
		.regs = sensor_wb_cloud_regs,
		.size = ARRAY_SIZE(sensor_wb_cloud_regs),
	},
	{
		/* V4L2_WHITE_BALANCE_SHADE */
		.regs = sensor_wb_shade,
		.size = ARRAY_SIZE(sensor_wb_shade),
	},
};


/*
 * The color effect settings
 */
static struct regval_list sensor_colorfx_none_regs[] = {
	{0x43, 0x00}
};

static struct regval_list sensor_colorfx_bw_regs[] = {
	{0x43, 0x02},
	{0xda, 0x00},
	{0xdb, 0x00}
};

static struct regval_list sensor_colorfx_sepia_regs[] = {
	{0x43, 0x02},
	{0xda, 0xd0},
	{0xdb, 0x28}
};

static struct regval_list sensor_colorfx_negative_regs[] = {
	{0x43, 0x01}
};

static struct regval_list sensor_colorfx_emboss_regs[] = {

};

static struct regval_list sensor_colorfx_sketch_regs[] = {

};

static struct regval_list sensor_colorfx_sky_blue_regs[] = {
	{0x43, 0x02},
	{0xda, 0x50},
	{0xdb, 0xe0},

};

static struct regval_list sensor_colorfx_grass_green_regs[] = {
	{0x43, 0x02},
	{0xda, 0xc0},
	{0xdb, 0xc0},
};

static struct regval_list sensor_colorfx_skin_whiten_regs[] = {
	/* NULL */
};

static struct regval_list sensor_colorfx_vivid_regs[] = {
	/* NULL */
};

static struct regval_list sensor_colorfx_aqua_regs[] = {
	/* null */
};

static struct regval_list sensor_colorfx_art_freeze_regs[] = {
	/* null */
};

static struct regval_list sensor_colorfx_silhouette_regs[] = {
	/* null */
};

static struct regval_list sensor_colorfx_solarization_regs[] = {
	/* null */
};

static struct regval_list sensor_colorfx_antique_regs[] = {
	/* null */
};

static struct regval_list sensor_colorfx_set_cbcr_regs[] = {
	/* null */
};

static struct cfg_array sensor_colorfx[] = {
	{
		/* V4L2_COLORFX_NONE = 0, */
		.regs = sensor_colorfx_none_regs,
		.size = ARRAY_SIZE(sensor_colorfx_none_regs),
	},
	{
		/* V4L2_COLORFX_BW   = 1, */
		.regs = sensor_colorfx_bw_regs,
		.size = ARRAY_SIZE(sensor_colorfx_bw_regs),
	},
	{
		/* V4L2_COLORFX_SEPIA  = 2, */
		.regs = sensor_colorfx_sepia_regs,
		.size = ARRAY_SIZE(sensor_colorfx_sepia_regs),
	},
	{
		/* V4L2_COLORFX_NEGATIVE = 3, */
		.regs = sensor_colorfx_negative_regs,
		.size = ARRAY_SIZE(sensor_colorfx_negative_regs),
	},
	{
		/* V4L2_COLORFX_EMBOSS = 4, */
		.regs = sensor_colorfx_emboss_regs,
		.size = ARRAY_SIZE(sensor_colorfx_emboss_regs),
	},
	{
		/* V4L2_COLORFX_SKETCH = 5, */
		.regs = sensor_colorfx_sketch_regs,
		.size = ARRAY_SIZE(sensor_colorfx_sketch_regs),
	},
	{
		/* V4L2_COLORFX_SKY_BLUE = 6, */
		.regs = sensor_colorfx_sky_blue_regs,
		.size = ARRAY_SIZE(sensor_colorfx_sky_blue_regs),
	},
	{
		/* V4L2_COLORFX_GRASS_GREEN = 7, */
		.regs = sensor_colorfx_grass_green_regs,
		.size = ARRAY_SIZE(sensor_colorfx_grass_green_regs),
	},
	{
		/* V4L2_COLORFX_SKIN_WHITEN = 8, */
		.regs = sensor_colorfx_skin_whiten_regs,
		.size = ARRAY_SIZE(sensor_colorfx_skin_whiten_regs),
	},
	{
		/* V4L2_COLORFX_VIVID = 9, */
		.regs = sensor_colorfx_vivid_regs,
		.size = ARRAY_SIZE(sensor_colorfx_vivid_regs),
	},
	{
		/* V4L2_COLORFX_AQUA = 10, */
		.regs = sensor_colorfx_aqua_regs,
		.size = ARRAY_SIZE(sensor_colorfx_aqua_regs),
	},
	{
		/* V4L2_COLORFX_ART_FREEZE = 11, */
		.regs = sensor_colorfx_art_freeze_regs,
		.size = ARRAY_SIZE(sensor_colorfx_art_freeze_regs),
	},
	{
		/* V4L2_COLORFX_SILHOUETTE = 12, */
		.regs = sensor_colorfx_silhouette_regs,
		.size = ARRAY_SIZE(sensor_colorfx_silhouette_regs),
	},
	{
		/* V4L2_COLORFX_SOLARIZATION = 13, */
		.regs = sensor_colorfx_solarization_regs,
		.size = ARRAY_SIZE(sensor_colorfx_solarization_regs),
	},
	{
		/* V4L2_COLORFX_ANTIQUE = 14, */
		.regs = sensor_colorfx_antique_regs,
		.size = ARRAY_SIZE(sensor_colorfx_antique_regs),
	},
	{
		/* V4L2_COLORFX_SET_CBCR = 15, */
		.regs = sensor_colorfx_set_cbcr_regs,
		.size = ARRAY_SIZE(sensor_colorfx_set_cbcr_regs),
	},
};

/*
 * The brightness setttings
 */
static struct regval_list sensor_brightness_neg4_regs[] = {
	/* NULL */
};

static struct regval_list sensor_brightness_neg3_regs[] = {
	/* NULL */
};

static struct regval_list sensor_brightness_neg2_regs[] = {
	/* NULL */
};

static struct regval_list sensor_brightness_neg1_regs[] = {
	/* NULL */
};

static struct regval_list sensor_brightness_zero_regs[] = {
	/* NULL */
};

static struct regval_list sensor_brightness_pos1_regs[] = {
	/* NULL */
};

static struct regval_list sensor_brightness_pos2_regs[] = {
	/* NULL */
};

static struct regval_list sensor_brightness_pos3_regs[] = {
	/* NULL */
};

static struct regval_list sensor_brightness_pos4_regs[] = {
	/* NULL */
};

static struct cfg_array sensor_brightness[] = {
	{
		.regs = sensor_brightness_neg4_regs,
		.size = ARRAY_SIZE(sensor_brightness_neg4_regs),
	},
	{
		.regs = sensor_brightness_neg3_regs,
		.size = ARRAY_SIZE(sensor_brightness_neg3_regs),
	},
	{
		.regs = sensor_brightness_neg2_regs,
		.size = ARRAY_SIZE(sensor_brightness_neg2_regs),
	},
	{
		.regs = sensor_brightness_neg1_regs,
		.size = ARRAY_SIZE(sensor_brightness_neg1_regs),
	},
	{
		.regs = sensor_brightness_zero_regs,
		.size = ARRAY_SIZE(sensor_brightness_zero_regs),
	},
	{
		.regs = sensor_brightness_pos1_regs,
		.size = ARRAY_SIZE(sensor_brightness_pos1_regs),
	},
	{
		.regs = sensor_brightness_pos2_regs,
		.size = ARRAY_SIZE(sensor_brightness_pos2_regs),
	},
	{
		.regs = sensor_brightness_pos3_regs,
		.size = ARRAY_SIZE(sensor_brightness_pos3_regs),
	},
	{
		.regs = sensor_brightness_pos4_regs,
		.size = ARRAY_SIZE(sensor_brightness_pos4_regs),
	},
};

/*
 * The contrast setttings
 */
static struct regval_list sensor_contrast_neg4_regs[] = {
	/* NULL */
};

static struct regval_list sensor_contrast_neg3_regs[] = {
	/* NULL */
};

static struct regval_list sensor_contrast_neg2_regs[] = {
	/* NULL */
};

static struct regval_list sensor_contrast_neg1_regs[] = {
	/* NULL */
};

static struct regval_list sensor_contrast_zero_regs[] = {
	/* NULL */
};

static struct regval_list sensor_contrast_pos1_regs[] = {
	/* NULL */
};

static struct regval_list sensor_contrast_pos2_regs[] = {
	/* NULL */
};

static struct regval_list sensor_contrast_pos3_regs[] = {
	/* NULL */
};

static struct regval_list sensor_contrast_pos4_regs[] = {
};

static struct cfg_array sensor_contrast[] = {
	{
		.regs = sensor_contrast_neg4_regs,
		.size = ARRAY_SIZE(sensor_contrast_neg4_regs),
	},
	{
		.regs = sensor_contrast_neg3_regs,
		.size = ARRAY_SIZE(sensor_contrast_neg3_regs),
	},
	{
		.regs = sensor_contrast_neg2_regs,
		.size = ARRAY_SIZE(sensor_contrast_neg2_regs),
	},
	{
		.regs = sensor_contrast_neg1_regs,
		.size = ARRAY_SIZE(sensor_contrast_neg1_regs),
	},
	{
		.regs = sensor_contrast_zero_regs,
		.size = ARRAY_SIZE(sensor_contrast_zero_regs),
	},
	{
		.regs = sensor_contrast_pos1_regs,
		.size = ARRAY_SIZE(sensor_contrast_pos1_regs),
	},
	{
		.regs = sensor_contrast_pos2_regs,
		.size = ARRAY_SIZE(sensor_contrast_pos2_regs),
	},
	{
		.regs = sensor_contrast_pos3_regs,
		.size = ARRAY_SIZE(sensor_contrast_pos3_regs),
	},
	{
		.regs = sensor_contrast_pos4_regs,
		.size = ARRAY_SIZE(sensor_contrast_pos4_regs),
	},
};

/*
 * The saturation setttings
 */
static struct regval_list sensor_saturation_neg4_regs[] = {
	/* NULL */
};

static struct regval_list sensor_saturation_neg3_regs[] = {
	/* NULL */
};

static struct regval_list sensor_saturation_neg2_regs[] = {
	/* NULL */
};

static struct regval_list sensor_saturation_neg1_regs[] = {
	/* NULL */
};

static struct regval_list sensor_saturation_zero_regs[] = {
	/* NULL */
};

static struct regval_list sensor_saturation_pos1_regs[] = {
	/* NULL */
};

static struct regval_list sensor_saturation_pos2_regs[] = {
	/* NULL */
};

static struct regval_list sensor_saturation_pos3_regs[] = {
	/* NULL */
};

static struct regval_list sensor_saturation_pos4_regs[] = {
	/* NULL */
};

static struct cfg_array sensor_saturation[] = {
	{
		.regs = sensor_saturation_neg4_regs,
		.size = ARRAY_SIZE(sensor_saturation_neg4_regs),
	},
	{
		.regs = sensor_saturation_neg3_regs,
		.size = ARRAY_SIZE(sensor_saturation_neg3_regs),
	},
	{
		.regs = sensor_saturation_neg2_regs,
		.size = ARRAY_SIZE(sensor_saturation_neg2_regs),
	},
	{
		.regs = sensor_saturation_neg1_regs,
		.size = ARRAY_SIZE(sensor_saturation_neg1_regs),
	},
	{
		.regs = sensor_saturation_zero_regs,
		.size = ARRAY_SIZE(sensor_saturation_zero_regs),
	},
	{
		.regs = sensor_saturation_pos1_regs,
		.size = ARRAY_SIZE(sensor_saturation_pos1_regs),
	},
	{
		.regs = sensor_saturation_pos2_regs,
		.size = ARRAY_SIZE(sensor_saturation_pos2_regs),
	},
	{
		.regs = sensor_saturation_pos3_regs,
		.size = ARRAY_SIZE(sensor_saturation_pos3_regs),
	},
	{
		.regs = sensor_saturation_pos4_regs,
		.size = ARRAY_SIZE(sensor_saturation_pos4_regs),
	},
};

/*
 * The exposure target setttings
 */
static struct regval_list sensor_ev_neg4_regs[] = {
	{0xfe, 0x00},
	{0xd5, 0xb8},
	{0xfe, 0x01},
	{0x13, 0x28},
	{0xfe, 0x00}
};

static struct regval_list sensor_ev_neg3_regs[] = {
	{0xfe, 0x00},
	{0xd5, 0xd0},
	{0xfe, 0x01},
	{0x13, 0x30},
	{0xfe, 0x00}
};

static struct regval_list sensor_ev_neg2_regs[] = {
	{0xfe, 0x00},
	{0xd5, 0xe0},
	{0xfe, 0x01},
	{0x13, 0x38},
	{0xfe, 0x00}
};

static struct regval_list sensor_ev_neg1_regs[] = {
	{0xfe, 0x00},
	{0xd5, 0xf0},
	{0xfe, 0x01},
	{0x13, 0x40},
	{0xfe, 0x00}
};

static struct regval_list sensor_ev_zero_regs[] = {
	{0xfe, 0x00},
	{0xd5, 0x00},
	{0xfe, 0x01},
	{0x13, 0x45},
	{0xfe, 0x00}
};

static struct regval_list sensor_ev_pos1_regs[] = {
	{0xfe, 0x00},
	{0xd5, 0x10},
	{0xfe, 0x01},
	{0x13, 0x58},
	{0xfe, 0x00}
};

static struct regval_list sensor_ev_pos2_regs[] = {
	{0xfe, 0x00},
	{0xd5, 0x20},
	{0xfe, 0x01},
	{0x13, 0x60},
	{0xfe, 0x00}
};

static struct regval_list sensor_ev_pos3_regs[] = {
	{0xfe, 0x00},
	{0xd5, 0x30},
	{0xfe, 0x01},
	{0x13, 0x68},
	{0xfe, 0x00}
};

static struct regval_list sensor_ev_pos4_regs[] = {
	{0xfe, 0x00},
	{0xd5, 0x48},
	{0xfe, 0x01},
	{0x13, 0x70},
	{0xfe, 0x00}
};

static struct cfg_array sensor_ev[] = {
	{
		.regs = sensor_ev_neg4_regs,
		.size = ARRAY_SIZE(sensor_ev_neg4_regs),
	},
	{
		.regs = sensor_ev_neg3_regs,
		.size = ARRAY_SIZE(sensor_ev_neg3_regs),
	},
	{
		.regs = sensor_ev_neg2_regs,
		.size = ARRAY_SIZE(sensor_ev_neg2_regs),
	},
	{
		.regs = sensor_ev_neg1_regs,
		.size = ARRAY_SIZE(sensor_ev_neg1_regs),
	},
	{
		.regs = sensor_ev_zero_regs,
		.size = ARRAY_SIZE(sensor_ev_zero_regs),
	},
	{
		.regs = sensor_ev_pos1_regs,
		.size = ARRAY_SIZE(sensor_ev_pos1_regs),
	},
	{
		.regs = sensor_ev_pos2_regs,
		.size = ARRAY_SIZE(sensor_ev_pos2_regs),
	},
	{
		.regs = sensor_ev_pos3_regs,
		.size = ARRAY_SIZE(sensor_ev_pos3_regs),
	},
	{
		.regs = sensor_ev_pos4_regs,
		.size = ARRAY_SIZE(sensor_ev_pos4_regs),
	},
};

/*
 * Here we'll try to encapsulate the changes for just the output
 * video format.
 *
 */
static struct regval_list sensor_fmt_yuv422_yuyv[] = {
	{0x44, 0x02} /* YCbYCr */
};

static struct regval_list sensor_fmt_yuv422_yvyu[] = {
	{0x44, 0x03} /* YCrYCb */
};

static struct regval_list sensor_fmt_yuv422_vyuy[] = {
	{0x44, 0x01} /* CrYCbY */
};

static struct regval_list sensor_fmt_yuv422_uyvy[] = {
	{0x44, 0x00} /* CbYCrY */
};

static struct regval_list sensor_fmt_raw[] = {
	{0x44, 0x17} /* raw */
};


static int sensor_g_hflip(struct v4l2_subdev *sd, __s32 *value)
{
	int ret;
	struct sensor_info *info = to_state(sd);
	data_type val;

	ret = sensor_write(sd, 0xfe, 0x00);
	if (ret < 0) {
		vfe_dev_err("sensor_write err at sensor_g_hflip!\n");
		return ret;
	}

	ret = sensor_read(sd, 0x17, &val);
	if (ret < 0) {
		vfe_dev_err("sensor_read err at sensor_g_hflip!\n");
		return ret;
	}

	val &= (1<<0);
	val = val>>0; /* 0x14 bit0 is mirror */

	*value = val;

	info->hflip = *value;
	return 0;
}

static int sensor_s_hflip(struct v4l2_subdev *sd, int value)
{
	int ret;
	struct sensor_info *info = to_state(sd);
	data_type val;

	ret = sensor_write(sd, 0xfe, 0x00);
	if (ret < 0) {
		vfe_dev_err("sensor_write err at sensor_s_hflip!\n");
		return ret;
	}

	ret = sensor_read(sd, 0x17, &val);
	if (ret < 0) {
		vfe_dev_err("sensor_read err at sensor_s_hflip!\n");
		return ret;
	}

	switch (value) {
	case 0:
	  val &= 0xfc;
		break;
	case 1:
		val |= (0x01|(info->vflip<<1));
		break;
	default:
		return -EINVAL;
	}
	ret = sensor_write(sd, 0x17, val);
	if (ret < 0) {
		vfe_dev_err("sensor_write err at sensor_s_hflip!\n");
		return ret;
	}

	usleep_range(10000, 12000);

	info->hflip = value;
	return 0;
}

static int sensor_g_vflip(struct v4l2_subdev *sd, __s32 *value)
{
	int ret;
	struct sensor_info *info = to_state(sd);
	data_type val;

	ret = sensor_write(sd, 0xfe, 0x00);
	if (ret < 0) {
		vfe_dev_err("sensor_write err at sensor_g_vflip!\n");
		return ret;
	}

	ret = sensor_read(sd, 0x17, &val);
	if (ret < 0) {
		vfe_dev_err("sensor_read err at sensor_g_vflip!\n");
		return ret;
	}

	val &= (1<<1);
	val = val>>1; /* 0x14 bit1 is upsidedown */

	*value = val;

	info->vflip = *value;
	return 0;
}

static int sensor_s_vflip(struct v4l2_subdev *sd, int value)
{
	int ret;
	struct sensor_info *info = to_state(sd);
	data_type val;

	ret = sensor_write(sd, 0xfe, 0x00);
	if (ret < 0) {
		vfe_dev_err("sensor_write err at sensor_s_vflip!\n");
		return ret;
	}

	ret = sensor_read(sd, 0x17, &val);
	if (ret < 0) {
		vfe_dev_err("sensor_read err at sensor_s_vflip!\n");
		return ret;
	}

	switch (value) {
	case 0:
	  val &= 0xfc;
		break;
	case 1:
		val |= (0x02|info->hflip);
		break;
	default:
		return -EINVAL;
	}
	ret = sensor_write(sd, 0x17, val);
	if (ret < 0) {
		vfe_dev_err("sensor_write err at sensor_s_vflip!\n");
		return ret;
	}

	usleep_range(10000, 12000);

	info->vflip = value;
	return 0;
}

static int sensor_g_autogain(struct v4l2_subdev *sd, __s32 *value)
{
	return -EINVAL;
}

static int sensor_s_autogain(struct v4l2_subdev *sd, int value)
{
	return -EINVAL;
}

static int sensor_g_autoexp(struct v4l2_subdev *sd, __s32 *value)
{
	int ret;
	struct sensor_info *info = to_state(sd);
	data_type val;

	ret = sensor_write(sd, 0xfe, 0x00);
	if (ret < 0) {
		vfe_dev_err("sensor_write err at sensor_g_autoexp!\n");
		return ret;
	}

	ret = sensor_read(sd, 0x4f, &val);
	if (ret < 0) {
		vfe_dev_err("sensor_read err at sensor_g_autoexp!\n");
		return ret;
	}

	val &= 0x01;
	if (val == 0x01)
		*value = V4L2_EXPOSURE_AUTO;
	else
		*value = V4L2_EXPOSURE_MANUAL;

	info->autoexp = *value;
	return 0;
}

static int sensor_s_autoexp(struct v4l2_subdev *sd,
				enum v4l2_exposure_auto_type value)
{
	int ret;
	struct sensor_info *info = to_state(sd);
	data_type val;

	ret = sensor_write(sd, 0xfe, 0x00);
	if (ret < 0) {
		vfe_dev_err("sensor_write err at sensor_s_autoexp!\n");
		return ret;
	}

	ret = sensor_read(sd, 0x4f, &val);
	if (ret < 0) {
		vfe_dev_err("sensor_read err at sensor_s_autoexp!\n");
		return ret;
	}

	switch (value) {
	case V4L2_EXPOSURE_AUTO:
	  val |= 0x01;
		break;
	case V4L2_EXPOSURE_MANUAL:
		val &= 0xfe;
		break;
	case V4L2_EXPOSURE_SHUTTER_PRIORITY:
		return -EINVAL;
	case V4L2_EXPOSURE_APERTURE_PRIORITY:
		return -EINVAL;
	default:
		return -EINVAL;
	}

	ret = sensor_write(sd, 0x4f, val);
	if (ret < 0) {
		vfe_dev_err("sensor_write err at sensor_s_autoexp!\n");
		return ret;
	}

	usleep_range(10000, 12000);

	info->autoexp = value;
	return 0;
}

static int sensor_g_autowb(struct v4l2_subdev *sd, int *value)
{
	int ret;
	struct sensor_info *info = to_state(sd);
	data_type val;

	ret = sensor_write(sd, 0xfe, 0x00);
	if (ret < 0) {
		vfe_dev_err("sensor_write err at sensor_g_autowb!\n");
		return ret;
	}

	ret = sensor_read(sd, 0x42, &val);
	if (ret < 0) {
		vfe_dev_err("sensor_read err at sensor_g_autowb!\n");
		return ret;
	}

	val &= (1<<1);
	val = val>>1; /* 0x22 bit1 is awb enable */

	*value = val;
	info->autowb = *value;

	return 0;
}

static int sensor_s_autowb(struct v4l2_subdev *sd, int value)
{
	int ret;
	struct sensor_info *info = to_state(sd);
	data_type val;

	ret = sensor_write_array(sd, sensor_wb_auto_regs,
				ARRAY_SIZE(sensor_wb_auto_regs));
	if (ret < 0) {
		vfe_dev_err("sensor_write_array err at sensor_s_autowb!\n");
		return ret;
	}

	ret = sensor_write(sd, 0xfe, 0x00);
	if (ret < 0) {
		vfe_dev_err("sensor_write err at sensor_s_autowb!\n");
		return ret;
	}
	ret = sensor_read(sd, 0x42, &val);
	if (ret < 0) {
		vfe_dev_err("sensor_read err at sensor_s_autowb!\n");
		return ret;
	}

	switch (value) {
	case 0:
		val &= 0xfd;
		break;
	case 1:
		val |= 0x02;
		break;
	default:
		break;
	}
	ret = sensor_write(sd, 0x42, val);
	if (ret < 0) {
		vfe_dev_err("sensor_write err at sensor_s_autowb!\n");
		return ret;
	}

	usleep_range(10000, 12000);

	info->autowb = value;
	return 0;
}

static int sensor_g_hue(struct v4l2_subdev *sd, __s32 *value)
{
	return -EINVAL;
}

static int sensor_s_hue(struct v4l2_subdev *sd, int value)
{
	return -EINVAL;
}

static int sensor_g_gain(struct v4l2_subdev *sd, __s32 *value)
{
	return -EINVAL;
}

static int sensor_s_gain(struct v4l2_subdev *sd, int value)
{
	return -EINVAL;
}
/* ****************************end of **************************** */

static int sensor_g_brightness(struct v4l2_subdev *sd, __s32 *value)
{
	struct sensor_info *info = to_state(sd);

	*value = info->brightness;
	return 0;
}

static int sensor_s_brightness(struct v4l2_subdev *sd, int value)
{
	struct sensor_info *info = to_state(sd);

	if (info->brightness == value)
		return 0;

	if (value < 0 || value > 8)
		return -ERANGE;

	LOG_ERR_RET(sensor_write_array(sd, sensor_brightness[value].regs,
				sensor_brightness[value].size));

	info->brightness = value;
	return 0;
}

static int sensor_g_contrast(struct v4l2_subdev *sd, __s32 *value)
{
	struct sensor_info *info = to_state(sd);

	*value = info->contrast;
	return 0;
}

static int sensor_s_contrast(struct v4l2_subdev *sd, int value)
{
	struct sensor_info *info = to_state(sd);

	if (info->contrast == value)
		return 0;

	if (value < 0 || value > 8)
		return -ERANGE;

	LOG_ERR_RET(sensor_write_array(sd, sensor_contrast[value].regs,
				sensor_contrast[value].size));

	info->contrast = value;
	return 0;
}

static int sensor_g_saturation(struct v4l2_subdev *sd, __s32 *value)
{
	struct sensor_info *info = to_state(sd);

	*value = info->saturation;
	return 0;
}

static int sensor_s_saturation(struct v4l2_subdev *sd, int value)
{
	struct sensor_info *info = to_state(sd);

	if (info->saturation == value)
		return 0;

	if (value < 0 || value > 8)
		return -ERANGE;

	LOG_ERR_RET(sensor_write_array(sd, sensor_saturation[value].regs,
				sensor_saturation[value].size));

	info->saturation = value;
	return 0;
}

static int sensor_g_exp_bias(struct v4l2_subdev *sd, __s32 *value)
{
	struct sensor_info *info = to_state(sd);

	*value = info->exp_bias;
	return 0;
}

static int sensor_s_exp_bias(struct v4l2_subdev *sd, int value)
{
	struct sensor_info *info = to_state(sd);

	if (info->exp_bias == value)
		return 0;

	if (value < 0 || value > 8)
		return -ERANGE;

	LOG_ERR_RET(sensor_write_array(sd, sensor_ev[value].regs,
				sensor_ev[value].size));

	info->exp_bias = value;
	return 0;
}

static int sensor_g_wb(struct v4l2_subdev *sd, int *value)
{
	struct sensor_info *info = to_state(sd);
	enum v4l2_auto_n_preset_white_balance *wb_type =
				(enum v4l2_auto_n_preset_white_balance *)value;

	*wb_type = info->wb;

	return 0;
}

static int sensor_s_wb(struct v4l2_subdev *sd,
				enum v4l2_auto_n_preset_white_balance value)
{
	struct sensor_info *info = to_state(sd);

	if (info->capture_mode == V4L2_MODE_IMAGE)
		return 0;

	LOG_ERR_RET(sensor_write_array(sd, sensor_wb[value].regs,
				sensor_wb[value].size));

	if (value == V4L2_WHITE_BALANCE_AUTO)
		info->autowb = 1;
	else
		info->autowb = 0;

	info->wb = value;
	return 0;
}

static int sensor_g_colorfx(struct v4l2_subdev *sd,
				__s32 *value)
{
	struct sensor_info *info = to_state(sd);
	enum v4l2_colorfx *clrfx_type = (enum v4l2_colorfx *)value;

	*clrfx_type = info->clrfx;
	return 0;
}

static int sensor_s_colorfx(struct v4l2_subdev *sd,
				enum v4l2_colorfx value)
{
	struct sensor_info *info = to_state(sd);

	if (info->clrfx == value)
		return 0;

	LOG_ERR_RET(sensor_write_array(sd, sensor_colorfx[value].regs,
				sensor_colorfx[value].size));

	info->clrfx = value;
	return 0;
}

static int sensor_g_flash_mode(struct v4l2_subdev *sd,
				__s32 *value)
{
	struct sensor_info *info = to_state(sd);
	enum v4l2_flash_led_mode *flash_mode =
				(enum v4l2_flash_led_mode *)value;

	*flash_mode = info->flash_mode;
	return 0;
}

static int sensor_s_flash_mode(struct v4l2_subdev *sd,
				enum v4l2_flash_led_mode value)
{
	struct sensor_info *info = to_state(sd);

	info->flash_mode = value;
	return 0;
}

/*
 * Stuff that knows about the sensor.
 */
static int sensor_power(struct v4l2_subdev *sd, int on)
{
	cci_lock(sd);
	switch (on) {
	case CSI_SUBDEV_STBY_ON:
		vfe_dev_dbg("CSI_SUBDEV_STBY_ON\n");
		vfe_gpio_write(sd, PWDN, CSI_GPIO_HIGH);
		usleep_range(5000, 12000);
		vfe_set_mclk(sd, OFF);
		break;
	case CSI_SUBDEV_STBY_OFF:
		vfe_dev_dbg("CSI_SUBDEV_STBY_OFF\n");
		vfe_set_mclk_freq(sd, MCLK);
		vfe_set_mclk(sd, ON);
		usleep_range(5000, 12000);
		vfe_gpio_write(sd, PWDN, CSI_GPIO_LOW);
		usleep_range(5000, 12000);
		break;
	case CSI_SUBDEV_PWR_ON:
		vfe_dev_dbg("CSI_SUBDEV_PWR_ON\n");
		vfe_gpio_set_status(sd, PWDN, 1);  /* set the gpio to output */
		vfe_gpio_set_status(sd, RESET, 1); /* set the gpio to output */
		vfe_gpio_write(sd, PWDN, CSI_GPIO_HIGH);
		vfe_gpio_write(sd, RESET, CSI_GPIO_LOW);
		vfe_gpio_write(sd, POWER_EN, CSI_GPIO_HIGH);
		usleep_range(10000, 12000);
		vfe_set_mclk_freq(sd, MCLK);
		vfe_set_mclk(sd, ON);
		usleep_range(10000, 12000);
		vfe_set_pmu_channel(sd, IOVDD, ON);
		vfe_set_pmu_channel(sd, AVDD, ON);
		vfe_set_pmu_channel(sd, DVDD, ON);
		vfe_set_pmu_channel(sd, AFVDD, ON);
		usleep_range(10000, 12000);
		vfe_gpio_write(sd, PWDN, CSI_GPIO_LOW);
		usleep_range(10000, 12000);
		vfe_gpio_write(sd, RESET, CSI_GPIO_HIGH);
		usleep_range(10000, 12000);
		break;
	case CSI_SUBDEV_PWR_OFF:
		vfe_dev_dbg("CSI_SUBDEV_PWR_OFF\n");
		vfe_gpio_write(sd, PWDN, CSI_GPIO_HIGH);
		usleep_range(10000, 12000);
		vfe_gpio_write(sd, RESET, CSI_GPIO_LOW);
		usleep_range(10000, 12000);
		vfe_gpio_write(sd, POWER_EN, CSI_GPIO_LOW);
		vfe_set_pmu_channel(sd, AFVDD, OFF);
		vfe_set_pmu_channel(sd, DVDD, OFF);
		vfe_set_pmu_channel(sd, AVDD, OFF);
		vfe_set_pmu_channel(sd, IOVDD, OFF);
		usleep_range(5000, 12000);
		vfe_set_mclk(sd, OFF);
		usleep_range(5000, 12000);
		vfe_gpio_set_status(sd, RESET, 0); /* set the gpio to input */
		vfe_gpio_set_status(sd, PWDN, 0);  /* set the gpio to input */
		break;
	default:
		return -EINVAL;
	}
	cci_unlock(sd);
	return 0;
}

static int sensor_reset(struct v4l2_subdev *sd, u32 val)
{
	switch (val) {
	case 0:
		vfe_gpio_write(sd, RESET, CSI_GPIO_HIGH);
		usleep_range(10000, 12000);
		break;
	case 1:
		vfe_gpio_write(sd, RESET, CSI_GPIO_LOW);
		usleep_range(10000, 12000);
		break;
	default:
		return -EINVAL;
	}
	return 0;
}

static int sensor_detect(struct v4l2_subdev *sd)
{
	int ret;
	data_type val;

	ret = sensor_write(sd, 0xfe, 0x00);
	if (ret < 0) {
		vfe_dev_err("sensor_write err at sensor_detect!\n");
		return ret;
	}

	ret = sensor_read(sd, 0xf0, &val);
	if (ret < 0) {
		vfe_dev_err("sensor_read err at sensor_detect!\n");
		return ret;
	}

	if (val != 0x9D)
		return -ENODEV;

	return 0;
}

static int sensor_init(struct v4l2_subdev *sd, u32 val)
{
	int ret;

	vfe_dev_dbg("sensor_init\n");

	/*Make sure it is a target sensor*/
	ret = sensor_detect(sd);
	if (ret) {
		vfe_dev_err("chip found is not an target chip.\n");
		return ret;
	}
	sensor_write_array(sd, sensor_default_regs,
				ARRAY_SIZE(sensor_default_regs));
	msleep(500);
	return 0;
}

static int sensor_g_exif(struct v4l2_subdev *sd,
				struct sensor_exif_attribute *exif)
{
	int ret = 0;  /* gain_val, exp_val; */
	unsigned  int  temp = 0, shutter = 0, gain = 0;
	data_type val;

	sensor_write(sd, 0xfe, 0x00);
	/* sensor_write(sd, 0xb6, 0x02); */

	/*read shutter */
	sensor_read(sd, 0x03, &val);
	temp |= (val << 8);
	sensor_read(sd, 0x04, &val);
	temp |= (val & 0xff);
	shutter = temp;

	sensor_read(sd, 0x71, &val);
	gain = val;
	exif->fnumber = 280;
	exif->focal_length = 425;
	exif->brightness = 125;
	exif->flash_fire = 0;
	exif->iso_speed = 50*((50 + CLIP(gain-0x40, 0, 0xff)*5)/50);
	exif->exposure_time_num = 1;
	exif->exposure_time_den = 8000/shutter;
	return ret;
}

static long sensor_ioctl(struct v4l2_subdev *sd, unsigned int cmd, void *arg)
{
	int ret = 0;
	/* struct sensor_info *info = to_state(sd); */
	switch (cmd) {
	case GET_SENSOR_EXIF:
		sensor_g_exif(sd, (struct sensor_exif_attribute *)arg);
		break;
	default:
		return -EINVAL;
	}
	return ret;
}


/*
 * Store information about the video data format.
 */
static struct sensor_format_struct {
	__u8 *desc;
	/* __u32 pixelformat; */
	u32 mbus_code; /* linux-4.4 */
	struct regval_list *regs;
	int regs_size;
	int bpp;   /* Bytes per pixel */
} sensor_formats[] = {
	{
		.desc       = "YUYV 4:2:2",
		.mbus_code  = MEDIA_BUS_FMT_YUYV8_2X8, /* linux-4.4 */
		.regs       = sensor_fmt_yuv422_yuyv,
		.regs_size  = ARRAY_SIZE(sensor_fmt_yuv422_yuyv),
		.bpp        = 2,
	},
	{
		.desc       = "YVYU 4:2:2",
		.mbus_code  = MEDIA_BUS_FMT_YVYU8_2X8, /* linux-4.4 */
		.regs       = sensor_fmt_yuv422_yvyu,
		.regs_size  = ARRAY_SIZE(sensor_fmt_yuv422_yvyu),
		.bpp        = 2,
	},
	{
		.desc       = "UYVY 4:2:2",
		.mbus_code  = MEDIA_BUS_FMT_UYVY8_2X8, /* linux-4.4 */
		.regs       = sensor_fmt_yuv422_uyvy,
		.regs_size  = ARRAY_SIZE(sensor_fmt_yuv422_uyvy),
		.bpp        = 2,
	},
	{
		.desc       = "VYUY 4:2:2",
		.mbus_code  = MEDIA_BUS_FMT_VYUY8_2X8, /* linux-4.4 */
		.regs       = sensor_fmt_yuv422_vyuy,
		.regs_size  = ARRAY_SIZE(sensor_fmt_yuv422_vyuy),
		.bpp        = 2,
	},
	{
		.desc       = "Raw RGB Bayer",
		.mbus_code  = MEDIA_BUS_FMT_SBGGR8_1X8, /* linux-4.4 */
		.regs       = sensor_fmt_raw,
		.regs_size  = ARRAY_SIZE(sensor_fmt_raw),
		.bpp        = 1
	},
};
#define N_FMTS ARRAY_SIZE(sensor_formats)


/*
 * Then there is the issue of window sizes.  Try to capture the info here.
 */
static struct sensor_win_size
sensor_win_sizes[] = {
	/* VGA */
	{
		.width      = VGA_WIDTH,
		.height     = VGA_HEIGHT,
		.hoffset    = 0,
		.voffset    = 0,
		.regs       = NULL,
		.regs_size  = 0,
		.set_size   = NULL,
	},
};

#define N_WIN_SIZES (ARRAY_SIZE(sensor_win_sizes))

static int sensor_enum_code(struct v4l2_subdev *sd,
				struct v4l2_subdev_pad_config *cfg,
				struct v4l2_subdev_mbus_code_enum *code)
{
	if (code->index >= N_FMTS)
		return -EINVAL;

	code->code = sensor_formats[code->index].mbus_code;
	return 0;
}

static int sensor_enum_frame_size(struct v4l2_subdev *sd,
				struct v4l2_subdev_pad_config *cfg,
				struct v4l2_subdev_frame_size_enum *fse)
{
	if (fse->index > N_WIN_SIZES-1)
		return -EINVAL;

	fse->min_width = sensor_win_sizes[fse->index].width;
	fse->max_width = fse->min_width;
	fse->min_height = sensor_win_sizes[fse->index].height;
	fse->max_height = fse->min_height;

	return 0;
}


static int sensor_try_fmt_internal(struct v4l2_subdev *sd,
				struct v4l2_mbus_framefmt *fmt,
				struct sensor_format_struct **ret_fmt,
				struct sensor_win_size **ret_wsize)
{
	int index;
	struct sensor_win_size *wsize;

	for (index = 0; index < N_FMTS; index++)
		if (sensor_formats[index].mbus_code == fmt->code)
			break;

	if (index >= N_FMTS)
		return -EINVAL;

	if (ret_fmt != NULL)
		*ret_fmt = sensor_formats + index;

	/*
	 * Fields: the sensor devices claim to be progressive.
	 */
	fmt->field = V4L2_FIELD_NONE;

	/*
	 * Round requested image size down to the nearest
	 * we support, but not below the smallest.
	 */
	for (wsize = sensor_win_sizes; wsize < sensor_win_sizes + N_WIN_SIZES; wsize++)
		if (fmt->width >= wsize->width && fmt->height >= wsize->height)
			break;

	if (wsize >= sensor_win_sizes + N_WIN_SIZES)
		wsize--;   /* Take the smallest one */
	if (ret_wsize != NULL)
		*ret_wsize = wsize;
	/*
	 * Note the size we'll actually handle.
	 */
	fmt->width = wsize->width;
	fmt->height = wsize->height;

	return 0;
}

static int sensor_get_fmt(struct v4l2_subdev *sd,
				struct v4l2_subdev_pad_config *cfg,
				struct v4l2_subdev_format *fmat) /* linux-4.4 */
{
	struct v4l2_mbus_framefmt *fmt = &fmat->format;

	return sensor_try_fmt_internal(sd, fmt, NULL, NULL);
}

static int sensor_g_mbus_config(struct v4l2_subdev *sd,
				struct v4l2_mbus_config *cfg)
{
	cfg->type = V4L2_MBUS_PARALLEL;
	cfg->flags = V4L2_MBUS_MASTER | VREF_POL | HREF_POL | CLK_POL;

	return 0;
}

/*
 * Set a format.
 */
static int sensor_set_fmt(struct v4l2_subdev *sd,
				struct v4l2_subdev_pad_config *cfg,
				struct v4l2_subdev_format *fmat) /* linux-4.4 */
{
	int ret;
	struct v4l2_mbus_framefmt *fmt = &fmat->format;
	struct sensor_format_struct *sensor_fmt;
	struct sensor_win_size *wsize;
	struct sensor_info *info = to_state(sd);

	vfe_dev_dbg("sensor_s_fmt\n");
	ret = sensor_try_fmt_internal(sd, fmt, &sensor_fmt, &wsize);
	if (ret)
		return ret;


	sensor_write_array(sd, sensor_fmt->regs, sensor_fmt->regs_size);

	ret = 0;
	if (wsize->regs) {
		ret = sensor_write_array(sd, wsize->regs, wsize->regs_size);
		if (ret < 0)
			return ret;
	}

	if (wsize->set_size) {
		ret = wsize->set_size(sd);
		if (ret < 0)
			return ret;
	}

	info->fmt = sensor_fmt;
	info->width = wsize->width;
	info->height = wsize->height;

	return 0;
}

/*
 * Implement G/S_PARM.  There is a "high quality" mode we could try
 * to do someday; for now, we just do the frame rate tweak.
 */
static int sensor_g_parm(struct v4l2_subdev *sd, struct v4l2_streamparm *parms)
{
	struct v4l2_captureparm *cp = &parms->parm.capture;
	/* struct sensor_info *info = to_state(sd); */

	if (parms->type != V4L2_BUF_TYPE_VIDEO_CAPTURE)
		return -EINVAL;

	memset(cp, 0, sizeof(struct v4l2_captureparm));
	cp->capability = V4L2_CAP_TIMEPERFRAME;
	cp->timeperframe.numerator = 1;
	cp->timeperframe.denominator = SENSOR_FRAME_RATE;

	return 0;
}

static int sensor_s_parm(struct v4l2_subdev *sd, struct v4l2_streamparm *parms)
{
	return 0;
}


/*
 * Code for dealing with controls.
 * fill with different sensor module
 * different sensor module has different settings here
 * if not support the follow function ,retrun -EINVAL
 */

/* ******************************begin of ***************************** */

static int sensor_g_ctrl(struct v4l2_ctrl *ctrl)
{
	struct sensor_info *info =
			container_of(ctrl->handler, struct sensor_info, handler);
	struct v4l2_subdev *sd = &info->sd;

	switch (ctrl->id) {
	case V4L2_CID_BRIGHTNESS:
		return sensor_g_brightness(sd, &ctrl->val);
	case V4L2_CID_CONTRAST:
		return sensor_g_contrast(sd, &ctrl->val);
	case V4L2_CID_SATURATION:
		return sensor_g_saturation(sd, &ctrl->val);
	case V4L2_CID_HUE:
		return sensor_g_hue(sd, &ctrl->val);
	case V4L2_CID_VFLIP:
		return sensor_g_vflip(sd, &ctrl->val);
	case V4L2_CID_HFLIP:
		return sensor_g_hflip(sd, &ctrl->val);
	case V4L2_CID_GAIN:
		return sensor_g_gain(sd, &ctrl->val);
	case V4L2_CID_AUTOGAIN:
		return sensor_g_autogain(sd, &ctrl->val);
	case V4L2_CID_EXPOSURE:
	case V4L2_CID_AUTO_EXPOSURE_BIAS:
		return sensor_g_exp_bias(sd, &ctrl->val);
	case V4L2_CID_EXPOSURE_AUTO:
		return sensor_g_autoexp(sd, &ctrl->val);
	case V4L2_CID_AUTO_N_PRESET_WHITE_BALANCE:
		return sensor_g_wb(sd, &ctrl->val);
	case V4L2_CID_AUTO_WHITE_BALANCE:
		return sensor_g_autowb(sd, &ctrl->val);
	case V4L2_CID_COLORFX:
		return sensor_g_colorfx(sd, &ctrl->val);
	case V4L2_CID_FLASH_LED_MODE:
		return sensor_g_flash_mode(sd, &ctrl->val);
	}
	return -EINVAL;
}

static int sensor_s_ctrl(struct v4l2_ctrl *ctrl)
{
	struct sensor_info *info =
			container_of(ctrl->handler, struct sensor_info, handler);
	struct v4l2_subdev *sd = &info->sd;

	switch (ctrl->id) {
	case V4L2_CID_BRIGHTNESS:
		return sensor_s_brightness(sd, ctrl->val);
	case V4L2_CID_CONTRAST:
		return sensor_s_contrast(sd, ctrl->val);
	case V4L2_CID_SATURATION:
		return sensor_s_saturation(sd, ctrl->val);
	case V4L2_CID_HUE:
		return sensor_s_hue(sd, ctrl->val);
	case V4L2_CID_VFLIP:
		return sensor_s_vflip(sd, ctrl->val);
	case V4L2_CID_HFLIP:
		return sensor_s_hflip(sd, ctrl->val);
	case V4L2_CID_GAIN:
		return sensor_s_gain(sd, ctrl->val);
	case V4L2_CID_AUTOGAIN:
		return sensor_s_autogain(sd, ctrl->val);
	case V4L2_CID_EXPOSURE:
	case V4L2_CID_AUTO_EXPOSURE_BIAS:
		return sensor_s_exp_bias(sd, ctrl->val);
	case V4L2_CID_EXPOSURE_AUTO:
		return sensor_s_autoexp(sd,
			(enum v4l2_exposure_auto_type) ctrl->val);
	case V4L2_CID_AUTO_N_PRESET_WHITE_BALANCE:
		return sensor_s_wb(sd,
			(enum v4l2_auto_n_preset_white_balance) ctrl->val);
	case V4L2_CID_AUTO_WHITE_BALANCE:
		return sensor_s_autowb(sd, ctrl->val);
	case V4L2_CID_COLORFX:
		return sensor_s_colorfx(sd, (enum v4l2_colorfx) ctrl->val);
	case V4L2_CID_FLASH_LED_MODE:
		return sensor_s_flash_mode(sd,
			(enum v4l2_flash_led_mode) ctrl->val);
	}
	return -EINVAL;
}


/* ----------------------------------------------------------------------- */
static const struct v4l2_ctrl_ops sensor_ctrl_ops = {
	.g_volatile_ctrl = sensor_g_ctrl,
	.s_ctrl = sensor_s_ctrl,
};

static const struct v4l2_subdev_core_ops sensor_core_ops = {
	.reset = sensor_reset,
	.init = sensor_init,
	.s_power = sensor_power,
	.ioctl = sensor_ioctl,
};

static const struct v4l2_subdev_video_ops sensor_video_ops = {
	.s_parm = sensor_s_parm,
	.g_parm = sensor_g_parm,
	.g_mbus_config = sensor_g_mbus_config,
};

static const struct v4l2_subdev_pad_ops sensor_pad_ops = {
	.enum_mbus_code = sensor_enum_code,
	.enum_frame_size = sensor_enum_frame_size,
	.get_fmt = sensor_get_fmt,
	.set_fmt = sensor_set_fmt,
};

static const struct v4l2_subdev_ops sensor_ops = {
	.core = &sensor_core_ops,
	.video = &sensor_video_ops,
	.pad = &sensor_pad_ops,
};

/* ----------------------------------------------------------------------- */
static struct cci_driver cci_drv = {
	.name = SENSOR_NAME,
	.addr_width = CCI_BITS_8,
	.data_width = CCI_BITS_8,
};

static const s64 exp_bias_qmenu[] = {
	-4, -3, -2, -1, 0, 1, 2, 3, 4,
};

static int sensor_init_controls(struct v4l2_subdev *sd, const struct v4l2_ctrl_ops *ops)
{
	struct sensor_info *info = to_state(sd);
	struct v4l2_ctrl_handler *handler = &info->handler;
	struct v4l2_ctrl *ctrl;
	int ret = 0;

	v4l2_ctrl_handler_init(handler, 15);

	v4l2_ctrl_new_std(handler, ops, V4L2_CID_BRIGHTNESS, 0, 255, 1, 128);
	v4l2_ctrl_new_std(handler, ops, V4L2_CID_CONTRAST, 0, 128, 1, 0);
	v4l2_ctrl_new_std(handler, ops, V4L2_CID_SATURATION, -4, 4, 1, 1);
	v4l2_ctrl_new_std(handler, ops, V4L2_CID_HUE,  -180, 180, 1, 0);
	v4l2_ctrl_new_std(handler, ops, V4L2_CID_VFLIP, 0, 1, 1, 0);
	v4l2_ctrl_new_std(handler, ops, V4L2_CID_HFLIP, 0, 1, 1, 0);
	ctrl = v4l2_ctrl_new_std(handler, ops, V4L2_CID_GAIN, 1*16, 64*16-1, 1, 1*16);
	if (ctrl != NULL)
		ctrl->flags |= V4L2_CTRL_FLAG_VOLATILE;
	v4l2_ctrl_new_std(handler, ops, V4L2_CID_AUTOGAIN, 0, 1, 1, 1);
	ctrl = v4l2_ctrl_new_std(handler, ops, V4L2_CID_EXPOSURE, 0, 65536*16, 1, 0);
	if (ctrl != NULL)
		ctrl->flags |= V4L2_CTRL_FLAG_VOLATILE;
	v4l2_ctrl_new_int_menu(handler, ops, V4L2_CID_AUTO_EXPOSURE_BIAS,
				ARRAY_SIZE(exp_bias_qmenu) - 1,
				ARRAY_SIZE(exp_bias_qmenu)/2, exp_bias_qmenu);
	v4l2_ctrl_new_std_menu(handler, ops, V4L2_CID_EXPOSURE_AUTO,
				V4L2_EXPOSURE_APERTURE_PRIORITY, 0, V4L2_EXPOSURE_AUTO);
	v4l2_ctrl_new_std_menu(handler, ops, V4L2_CID_AUTO_N_PRESET_WHITE_BALANCE,
				V4L2_WHITE_BALANCE_SHADE, 0, V4L2_WHITE_BALANCE_AUTO);
	v4l2_ctrl_new_std(handler, ops, V4L2_CID_AUTO_WHITE_BALANCE, 0, 1, 1, 1);
	v4l2_ctrl_new_std_menu(handler, ops, V4L2_CID_COLORFX,
				V4L2_COLORFX_SET_CBCR, 0, V4L2_COLORFX_NONE);
	v4l2_ctrl_new_std_menu(handler, ops, V4L2_CID_FLASH_LED_MODE,
		V4L2_FLASH_LED_MODE_RED_EYE, 0, V4L2_FLASH_LED_MODE_NONE);

	if (handler->error) {
		ret = handler->error;
		v4l2_ctrl_handler_free(handler);
	}

	sd->ctrl_handler = handler;

	return ret;
}

static int sensor_probe(struct i2c_client *client,
				const struct i2c_device_id *id)
{
	struct v4l2_subdev *sd;
	struct sensor_info *info;

	info = kzalloc(sizeof(struct sensor_info), GFP_KERNEL);
	if (info == NULL)
		return -ENOMEM;
	sd = &info->sd;
	sensor_init_controls(sd, &sensor_ctrl_ops);
	cci_dev_probe_helper(sd, client, &sensor_ops, &cci_drv);

	info->fmt = &sensor_formats[0];
	info->brightness = 0;
	info->contrast = 0;
	info->saturation = 0;
	info->hue = 0;
	info->hflip = 0;
	info->vflip = 0;
	info->gain = 0;
	info->autogain = 1;
	info->exp = 0;
	info->autoexp = 0;
	info->autowb = 1;
	info->wb = 0;
	info->clrfx = 0;

	return 0;
}


static int sensor_remove(struct i2c_client *client)
{
	struct v4l2_subdev *sd;

	sd = cci_dev_remove_helper(client, &cci_drv);
	kfree(to_state(sd));
	return 0;
}

static const struct i2c_device_id sensor_id[] = {
	{ SENSOR_NAME, 0 },
	{ }
};
MODULE_DEVICE_TABLE(i2c, sensor_id);
static struct i2c_driver sensor_driver = {
	.driver = {
		.owner = THIS_MODULE,
		.name = SENSOR_NAME,
	},
	.probe = sensor_probe,
	.remove = sensor_remove,
	.id_table = sensor_id,
};

static __init int init_sensor(void)
{
	return cci_dev_init_helper(&sensor_driver);
}

static __exit void exit_sensor(void)
{
	cci_dev_exit_helper(&sensor_driver);
}

module_init(init_sensor);
module_exit(exit_sensor);
