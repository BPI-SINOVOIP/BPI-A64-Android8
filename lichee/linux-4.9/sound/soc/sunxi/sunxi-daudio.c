/* sound\soc\sunxi\sunxi-daudio.c
 * (C) Copyright 2015-2017
 * Allwinner Technology Co., Ltd. <www.allwinnertech.com>
 * wolfgang huang <huangjinhui@allwinnertech.com>
 *
 * some simple description for this code
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License, or (at your option) any later version.
 *
 */

#include <linux/init.h>
#include <linux/module.h>
#include <linux/device.h>
#include <linux/clk.h>
#include <linux/of.h>
#include <linux/of_device.h>
#include <linux/of_address.h>
#include <linux/regmap.h>
#include <linux/dma/sunxi-dma.h>
#include <linux/pinctrl/consumer.h>
#include <linux/regulator/consumer.h>
#include <sound/core.h>
#include <sound/pcm.h>
#include <sound/dmaengine_pcm.h>
#include <sound/pcm_params.h>
#include <sound/initval.h>
#include <sound/soc.h>
#include <linux/delay.h>

#include "sunxi-daudio.h"
#include "sunxi-pcm.h"

#define	DRV_NAME	"sunxi-daudio"

#define	SUNXI_DAUDIO_EXTERNAL_TYPE	1
#define	SUNXI_DAUDIO_TDMHDMI_TYPE	2

#define SUNXI_DAUDIO_DRQDST(sunxi_daudio, x)			\
	((sunxi_daudio)->playback_dma_param.dma_drq_type_num =	\
				DRQDST_DAUDIO_##x##_TX)
#define SUNXI_DAUDIO_DRQSRC(sunxi_daudio, x)			\
	((sunxi_daudio)->capture_dma_param.dma_drq_type_num =	\
				DRQSRC_DAUDIO_##x##_RX)

struct sunxi_daudio_platform_data {
	unsigned int daudio_type;
	unsigned int external_type;
	unsigned int daudio_master;
	unsigned int pcm_lrck_period;
	unsigned int slot_width_select;
	unsigned int audio_format;
	unsigned int signal_inversion;
	unsigned int frame_type;
	unsigned int tdm_config;
	unsigned int tdm_num;
	unsigned int mclk_div;
};

struct voltage_supply {
	struct regulator *daudio_regulator;
	const char *regulator_name;
};

struct sunxi_daudio_info {
	struct device *dev;
	struct regmap *regmap;
	void __iomem *membase;
	struct voltage_supply vol_supply;
	struct clk *pllclk;
	struct clk *moduleclk;
	struct mutex mutex;
	struct sunxi_dma_params playback_dma_param;
	struct sunxi_dma_params capture_dma_param;
	struct pinctrl *pinctrl;
	struct pinctrl_state *pinstate;
	struct pinctrl_state *pinstate_sleep;
	struct sunxi_daudio_platform_data *pdata;
	unsigned int hub_mode;
	unsigned int hdmi_en;
};

static struct reg_label reg_labels[] = {
	REG_LABEL(SUNXI_DAUDIO_CTL),
	REG_LABEL(SUNXI_DAUDIO_FMT0),
	REG_LABEL(SUNXI_DAUDIO_FMT1),
	REG_LABEL(SUNXI_DAUDIO_INTSTA),
	REG_LABEL(SUNXI_DAUDIO_FIFOCTL),
	REG_LABEL(SUNXI_DAUDIO_FIFOSTA),
	REG_LABEL(SUNXI_DAUDIO_INTCTL),
	REG_LABEL(SUNXI_DAUDIO_CLKDIV),
	REG_LABEL(SUNXI_DAUDIO_TXCNT),
	REG_LABEL(SUNXI_DAUDIO_RXCNT),
	REG_LABEL(SUNXI_DAUDIO_CHCFG),
	REG_LABEL(SUNXI_DAUDIO_TX0CHSEL),
	REG_LABEL(SUNXI_DAUDIO_TX1CHSEL),
	REG_LABEL(SUNXI_DAUDIO_TX2CHSEL),
	REG_LABEL(SUNXI_DAUDIO_TX3CHSEL),
#if defined(SUNXI_DAUDIO_MODE_B)
	REG_LABEL(SUNXI_DAUDIO_TX0CHMAP0),
	REG_LABEL(SUNXI_DAUDIO_TX0CHMAP1),
	REG_LABEL(SUNXI_DAUDIO_TX1CHMAP0),
	REG_LABEL(SUNXI_DAUDIO_TX1CHMAP1),
	REG_LABEL(SUNXI_DAUDIO_TX2CHMAP0),
	REG_LABEL(SUNXI_DAUDIO_TX2CHMAP1),
	REG_LABEL(SUNXI_DAUDIO_TX3CHMAP0),
	REG_LABEL(SUNXI_DAUDIO_TX3CHMAP1),
	REG_LABEL(SUNXI_DAUDIO_RXCHSEL),
	REG_LABEL(SUNXI_DAUDIO_RXCHMAP0),
	REG_LABEL(SUNXI_DAUDIO_RXCHMAP1),
	REG_LABEL(SUNXI_DAUDIO_DEBUG),
#else
	REG_LABEL(SUNXI_DAUDIO_TX0CHMAP0),
	REG_LABEL(SUNXI_DAUDIO_TX1CHMAP0),
	REG_LABEL(SUNXI_DAUDIO_TX2CHMAP0),
	REG_LABEL(SUNXI_DAUDIO_TX3CHMAP0),
	REG_LABEL(SUNXI_DAUDIO_RXCHSEL),
	REG_LABEL(SUNXI_DAUDIO_RXCHMAP),
	REG_LABEL(SUNXI_DAUDIO_DEBUG),
#endif
	REG_LABEL_END,
};

static bool daudio_loop_en;
module_param(daudio_loop_en, bool, S_IRUGO | S_IWUSR);
MODULE_PARM_DESC(daudio_loop_en,
		"SUNXI Digital audio loopback debug(Y=enable, N=disable)");

static struct sunxi_daudio_info *sunxi_daudio_global[DAUDIO_NUM_MAX];
static int device_count;

/*
*	Some codec on electric timing need debugging
*/
int daudio_set_clk_onoff(struct snd_soc_dai *dai, u32 mask, u32 onoff)
{
	struct sunxi_daudio_info *sunxi_daudio = snd_soc_dai_get_drvdata(dai);

	switch (mask) {
	case SUNXI_DAUDIO_BCLK:
		if (onoff)
			regmap_update_bits(sunxi_daudio->regmap,
				SUNXI_DAUDIO_FIFOCTL,
				(1<<BCLK_OUT), (1<<BCLK_OUT));
		else
			regmap_update_bits(sunxi_daudio->regmap,
				SUNXI_DAUDIO_FIFOCTL,
				(1<<BCLK_OUT), (0<<BCLK_OUT));
	break;
	case SUNXI_DAUDIO_LRCK:
		if (onoff)
			regmap_update_bits(sunxi_daudio->regmap,
				SUNXI_DAUDIO_FIFOCTL,
				(1<<LRCK_OUT), (1<<LRCK_OUT));
		else
			regmap_update_bits(sunxi_daudio->regmap,
				SUNXI_DAUDIO_FIFOCTL,
				(1<<LRCK_OUT), (0<<LRCK_OUT));
		break;
	case SUNXI_DAUDIO_MCLK:
		if (onoff)
			regmap_update_bits(sunxi_daudio->regmap,
				SUNXI_DAUDIO_CLKDIV,
				(1<<MCLKOUT_EN), (1<<MCLKOUT_EN));
		else
			regmap_update_bits(sunxi_daudio->regmap,
				SUNXI_DAUDIO_CLKDIV,
				(1<<MCLKOUT_EN), (0<<MCLKOUT_EN));
		break;
	case SUNXI_DAUDIO_GEN:
		if (onoff)
			regmap_update_bits(sunxi_daudio->regmap,
				SUNXI_DAUDIO_CTL,
				(1<<GLOBAL_EN), (1<<GLOBAL_EN));
		else
			regmap_update_bits(sunxi_daudio->regmap,
				SUNXI_DAUDIO_CTL,
				(1<<GLOBAL_EN), (0<<GLOBAL_EN));
		break;
	default:
		return -EINVAL;
	}
	return 0;
}
EXPORT_SYMBOL_GPL(daudio_set_clk_onoff);


static int sunxi_daudio_get_hub_mode(struct snd_kcontrol *kcontrol,
			struct snd_ctl_elem_value *ucontrol)
{
	struct snd_soc_component *component = snd_kcontrol_chip(kcontrol);
	struct snd_soc_codec *codec = snd_soc_component_to_codec(component);
	struct sunxi_daudio_info *sunxi_daudio =
					snd_soc_codec_get_drvdata(codec);
	unsigned int reg_val;

	regmap_read(sunxi_daudio->regmap, SUNXI_DAUDIO_FIFOCTL, &reg_val);

	ucontrol->value.integer.value[0] = ((reg_val & (1<<HUB_EN)) ? 2 : 1);
	return 0;
}

static int sunxi_daudio_set_hub_mode(struct snd_kcontrol *kcontrol,
			struct snd_ctl_elem_value *ucontrol)
{
	struct snd_soc_component *component = snd_kcontrol_chip(kcontrol);
	struct snd_soc_codec *codec = snd_soc_component_to_codec(component);
	struct sunxi_daudio_info *sunxi_daudio =
					snd_soc_codec_get_drvdata(codec);

	switch (ucontrol->value.integer.value[0]) {
	case	0:
	case	1:
		regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_FIFOCTL,
				(1<<HUB_EN), (0<<HUB_EN));
		regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_CTL,
				(1<<CTL_TXEN), (0<<CTL_TXEN));
		break;
	case	2:
		regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_FIFOCTL,
				(1<<HUB_EN), (1<<HUB_EN));
		regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_CTL,
				(1<<CTL_TXEN), (1<<CTL_TXEN));
		break;
	default:
		return -EINVAL;
	}
	return 0;
}

static const char *daudio_format_function[] = {"null",
			"hub_disable", "hub_enable"};
static const struct soc_enum daudio_format_enum[] = {
	SOC_ENUM_SINGLE_EXT(ARRAY_SIZE(daudio_format_function),
			daudio_format_function),
};

/* dts pcm Audio Mode Select */
static const struct snd_kcontrol_new sunxi_daudio_controls[] = {
	SOC_ENUM_EXT("sunxi daudio audio hub mode", daudio_format_enum[0],
		sunxi_daudio_get_hub_mode, sunxi_daudio_set_hub_mode),
	SOC_SINGLE("sunxi daudio loopback debug", SUNXI_DAUDIO_CTL,
		LOOP_EN, 1, 0),
};

static void sunxi_daudio_txctrl_enable(struct sunxi_daudio_info *sunxi_daudio,
					int enable)
{
	pr_debug("Enter %s, enable %d\n", __func__, enable);
	if (enable) {
		/* HDMI audio Transmit Clock just enable at startup */
		if (sunxi_daudio->pdata->daudio_type
			!= SUNXI_DAUDIO_TDMHDMI_TYPE)
			regmap_update_bits(sunxi_daudio->regmap,
					SUNXI_DAUDIO_CTL,
					(1<<CTL_TXEN), (1<<CTL_TXEN));
		regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_INTCTL,
					(1<<TXDRQEN), (1<<TXDRQEN));
	} else {
		regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_INTCTL,
					(1<<TXDRQEN), (0<<TXDRQEN));
		if (sunxi_daudio->pdata->daudio_type
			!= SUNXI_DAUDIO_TDMHDMI_TYPE)
			regmap_update_bits(sunxi_daudio->regmap,
					SUNXI_DAUDIO_CTL,
					(1<<CTL_TXEN), (0<<CTL_TXEN));
	}
	pr_debug("End %s, enable %d\n", __func__, enable);
}

static void sunxi_daudio_rxctrl_enable(struct sunxi_daudio_info *sunxi_daudio,
					int enable)
{
	if (enable) {
		regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_CTL,
				(1<<CTL_RXEN), (1<<CTL_RXEN));
		regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_INTCTL,
				(1<<RXDRQEN), (1<<RXDRQEN));
	} else {
		regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_INTCTL,
				(1<<RXDRQEN), (0<<RXDRQEN));
		regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_CTL,
				(1<<CTL_RXEN), (0<<CTL_RXEN));
	}
}

static int sunxi_daudio_global_enable(struct sunxi_daudio_info *sunxi_daudio,
					int enable)
{
	if (enable) {
		regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_CTL,
				(1<<SDO0_EN), (1<<SDO0_EN));
		if (sunxi_daudio->hdmi_en) {
			regmap_update_bits(sunxi_daudio->regmap,
				SUNXI_DAUDIO_CTL, (1<<SDO1_EN), (1<<SDO1_EN));
			regmap_update_bits(sunxi_daudio->regmap,
				SUNXI_DAUDIO_CTL, (1<<SDO2_EN), (1<<SDO2_EN));
			regmap_update_bits(sunxi_daudio->regmap,
				SUNXI_DAUDIO_CTL, (1<<SDO3_EN), (1<<SDO3_EN));
		}
		regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_CTL,
				(1<<GLOBAL_EN), (1<<GLOBAL_EN));
	} else {
		regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_CTL,
				(1<<GLOBAL_EN), (0<<GLOBAL_EN));
		regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_CTL,
				(1<<SDO0_EN), (0<<SDO0_EN));
		if (sunxi_daudio->hdmi_en) {
			regmap_update_bits(sunxi_daudio->regmap,
				SUNXI_DAUDIO_CTL, (1<<SDO1_EN), (0<<SDO1_EN));
			regmap_update_bits(sunxi_daudio->regmap,
				SUNXI_DAUDIO_CTL, (1<<SDO2_EN), (0<<SDO2_EN));
			regmap_update_bits(sunxi_daudio->regmap,
				SUNXI_DAUDIO_CTL, (1<<SDO3_EN), (0<<SDO3_EN));
		}
	}
	return 0;
}

static int sunxi_daudio_mclk_setting(struct sunxi_daudio_info *sunxi_daudio)
{
	unsigned int mclk_div;

	if (sunxi_daudio->pdata->mclk_div) {
		switch (sunxi_daudio->pdata->mclk_div) {
		case	1:
			mclk_div = SUNXI_DAUDIO_MCLK_DIV_1;
			break;
		case	2:
			mclk_div = SUNXI_DAUDIO_MCLK_DIV_2;
			break;
		case	4:
			mclk_div = SUNXI_DAUDIO_MCLK_DIV_3;
			break;
		case	6:
			mclk_div = SUNXI_DAUDIO_MCLK_DIV_4;
			break;
		case	8:
			mclk_div = SUNXI_DAUDIO_MCLK_DIV_5;
			break;
		case	12:
			mclk_div = SUNXI_DAUDIO_MCLK_DIV_6;
			break;
		case	16:
			mclk_div = SUNXI_DAUDIO_MCLK_DIV_7;
			break;
		case	24:
			mclk_div = SUNXI_DAUDIO_MCLK_DIV_8;
			break;
		case	32:
			mclk_div = SUNXI_DAUDIO_MCLK_DIV_9;
			break;
		case	48:
			mclk_div = SUNXI_DAUDIO_MCLK_DIV_10;
			break;
		case	64:
			mclk_div = SUNXI_DAUDIO_MCLK_DIV_11;
			break;
		case	96:
			mclk_div = SUNXI_DAUDIO_MCLK_DIV_12;
			break;
		case	128:
			mclk_div = SUNXI_DAUDIO_MCLK_DIV_13;
			break;
		case	176:
			mclk_div = SUNXI_DAUDIO_MCLK_DIV_14;
			break;
		case	192:
			mclk_div = SUNXI_DAUDIO_MCLK_DIV_15;
			break;
		default:
			dev_err(sunxi_daudio->dev, "unsupport  mclk_div\n");
			return -EINVAL;
		}
		/* setting Mclk as external codec input clk */
		regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_CLKDIV,
			(SUNXI_DAUDIO_MCLK_DIV_MASK<<MCLK_DIV),
			(mclk_div<<MCLK_DIV));
		regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_CLKDIV,
				(1<<MCLKOUT_EN), (1<<MCLKOUT_EN));
	} else {
		regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_CLKDIV,
				(1<<MCLKOUT_EN), (0<<MCLKOUT_EN));
	}
	return 0;
}

static int sunxi_daudio_init_fmt(struct sunxi_daudio_info *sunxi_daudio,
				unsigned int fmt)
{
	unsigned int offset, mode;
	unsigned int lrck_polarity, brck_polarity;

	switch (fmt & SND_SOC_DAIFMT_MASTER_MASK) {
	case	SND_SOC_DAIFMT_CBM_CFM:
		regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_CTL,
				(SUNXI_DAUDIO_LRCK_OUT_MASK<<LRCK_OUT),
				(SUNXI_DAUDIO_LRCK_OUT_DISABLE<<LRCK_OUT));
		break;
	case	SND_SOC_DAIFMT_CBS_CFS:
		regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_CTL,
				(SUNXI_DAUDIO_LRCK_OUT_MASK<<LRCK_OUT),
				(SUNXI_DAUDIO_LRCK_OUT_ENABLE<<LRCK_OUT));
		break;
	default:
		dev_err(sunxi_daudio->dev, "unknown maser/slave format\n");
		return -EINVAL;
	}

	switch (fmt & SND_SOC_DAIFMT_FORMAT_MASK) {
	case	SND_SOC_DAIFMT_I2S:
		offset = SUNXI_DAUDIO_TX_OFFSET_1;
		mode = SUNXI_DAUDIO_MODE_CTL_I2S;
		break;
	case	SND_SOC_DAIFMT_RIGHT_J:
		offset = SUNXI_DAUDIO_TX_OFFSET_0;
		mode = SUNXI_DAUDIO_MODE_CTL_RIGHT;
		break;
	case	SND_SOC_DAIFMT_LEFT_J:
		offset = SUNXI_DAUDIO_TX_OFFSET_0;
		mode = SUNXI_DAUDIO_MODE_CTL_LEFT;
		break;
	case	SND_SOC_DAIFMT_DSP_A:
		offset = SUNXI_DAUDIO_TX_OFFSET_1;
		mode = SUNXI_DAUDIO_MODE_CTL_PCM;
		break;
	case	SND_SOC_DAIFMT_DSP_B:
		offset = SUNXI_DAUDIO_TX_OFFSET_0;
		mode = SUNXI_DAUDIO_MODE_CTL_PCM;
		break;
	default:
		dev_err(sunxi_daudio->dev, "format setting failed\n");
		return -EINVAL;
	}
	regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_CTL,
			(SUNXI_DAUDIO_MODE_CTL_MASK<<MODE_SEL),
			(mode<<MODE_SEL));
	regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_TX0CHSEL,
			(SUNXI_DAUDIO_TX_OFFSET_MASK<<TX_OFFSET),
			(offset<<TX_OFFSET));
	if (sunxi_daudio->hdmi_en) {
		regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_TX1CHSEL,
			(SUNXI_DAUDIO_TX_OFFSET_MASK<<TX_OFFSET),
			(offset<<TX_OFFSET));
		regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_TX2CHSEL,
			(SUNXI_DAUDIO_TX_OFFSET_MASK<<TX_OFFSET),
			(offset<<TX_OFFSET));
		regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_TX3CHSEL,
			(SUNXI_DAUDIO_TX_OFFSET_MASK<<TX_OFFSET),
			(offset<<TX_OFFSET));
	}

	regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_RXCHSEL,
			(SUNXI_DAUDIO_RX_OFFSET_MASK<<RX_OFFSET),
			(offset<<RX_OFFSET));

	switch (fmt & SND_SOC_DAIFMT_INV_MASK) {
	case	SND_SOC_DAIFMT_NB_NF:
		lrck_polarity = SUNXI_DAUDIO_LRCK_POLARITY_NOR;
		brck_polarity = SUNXI_DAUDIO_BCLK_POLARITY_NOR;
		break;
	case	SND_SOC_DAIFMT_NB_IF:
		lrck_polarity = SUNXI_DAUDIO_LRCK_POLARITY_INV;
		brck_polarity = SUNXI_DAUDIO_BCLK_POLARITY_NOR;
		break;
	case	SND_SOC_DAIFMT_IB_NF:
		lrck_polarity = SUNXI_DAUDIO_LRCK_POLARITY_NOR;
		brck_polarity = SUNXI_DAUDIO_BCLK_POLARITY_INV;
		break;
	case	SND_SOC_DAIFMT_IB_IF:
		lrck_polarity = SUNXI_DAUDIO_LRCK_POLARITY_INV;
		brck_polarity = SUNXI_DAUDIO_BCLK_POLARITY_INV;
		break;
	default:
		dev_err(sunxi_daudio->dev, "invert clk setting failed\n");
		return -EINVAL;
	}
	regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_FMT0,
			(1<<LRCK_POLARITY), (lrck_polarity<<LRCK_POLARITY));
	regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_FMT0,
			(1<<BRCK_POLARITY), (brck_polarity<<BRCK_POLARITY));
	return 0;
}

static int sunxi_daudio_init(struct sunxi_daudio_info *sunxi_daudio)
{
	struct sunxi_daudio_platform_data *pdat = sunxi_daudio->pdata;

	regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_FMT0,
			(1<<LRCK_WIDTH),
			(pdat->frame_type<<LRCK_WIDTH));
	regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_FMT0,
			(SUNXI_DAUDIO_LRCK_PERIOD_MASK)<<LRCK_PERIOD,
			((pdat->pcm_lrck_period-1)<<LRCK_PERIOD));

	regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_FMT0,
			(SUNXI_DAUDIO_SLOT_WIDTH_MASK<<SLOT_WIDTH),
			(((pdat->slot_width_select>>2)-1)<<SLOT_WIDTH));

	/*
	 * MSB on the transmit format, always be first.
	 * default using Linear-PCM, without no companding.
	 * A-law<Eourpean standard> or U-law<US-Japan> not working ok.
	 */
	regmap_write(sunxi_daudio->regmap,
			SUNXI_DAUDIO_FMT1, SUNXI_DAUDIO_FMT1_DEF);

	sunxi_daudio_init_fmt(sunxi_daudio, (pdat->audio_format
		| (pdat->signal_inversion<<SND_SOC_DAIFMT_SIG_SHIFT)
		| (pdat->daudio_master<<SND_SOC_DAIFMT_MASTER_SHIFT)));

	return sunxi_daudio_mclk_setting(sunxi_daudio);
}

static int sunxi_daudio_hw_params(struct snd_pcm_substream *substream,
		struct snd_pcm_hw_params *params, struct snd_soc_dai *dai)
{
	struct sunxi_daudio_info *sunxi_daudio = snd_soc_dai_get_drvdata(dai);
	struct snd_soc_pcm_runtime *rtd = substream->private_data;
	struct snd_soc_card *card = rtd->card;
	struct sunxi_hdmi_priv *sunxi_hdmi = snd_soc_card_get_drvdata(card);
#ifdef SUNXI_HDMI_AUDIO_ENABLE
	unsigned int reg_val;
#endif

	switch (params_format(params)) {
	case	SNDRV_PCM_FORMAT_S16_LE:
		/*
		 * Special procesing for hdmi, HDMI card name is
		 * "sndhdmi" or sndhdmiraw. if card not HDMI,
		 * strstr func just return NULL, jump to right section.
		 * Not HDMI card, sunxi_hdmi maybe a NULL pointer.
		 */
		if (sunxi_daudio->pdata->daudio_type ==
				SUNXI_DAUDIO_TDMHDMI_TYPE
				&& (sunxi_hdmi->hdmi_format > 1)) {
			regmap_update_bits(sunxi_daudio->regmap,
				SUNXI_DAUDIO_FMT0,
				(SUNXI_DAUDIO_SR_MASK<<SAMPLE_RESOLUTION),
				(SUNXI_DAUDIO_SR_24BIT<<SAMPLE_RESOLUTION));
			regmap_update_bits(sunxi_daudio->regmap,
					SUNXI_DAUDIO_FIFOCTL,
					(SUNXI_DAUDIO_TXIM_MASK<<TXIM),
					(SUNXI_DAUDIO_TXIM_VALID_MSB<<TXIM));
		} else {
			regmap_update_bits(sunxi_daudio->regmap,
				SUNXI_DAUDIO_FMT0,
				(SUNXI_DAUDIO_SR_MASK<<SAMPLE_RESOLUTION),
				(SUNXI_DAUDIO_SR_16BIT<<SAMPLE_RESOLUTION));
			if (substream->stream == SNDRV_PCM_STREAM_PLAYBACK)
				regmap_update_bits(sunxi_daudio->regmap,
					SUNXI_DAUDIO_FIFOCTL,
					(SUNXI_DAUDIO_TXIM_MASK<<TXIM),
					(SUNXI_DAUDIO_TXIM_VALID_LSB<<TXIM));
			else
				regmap_update_bits(sunxi_daudio->regmap,
					SUNXI_DAUDIO_FIFOCTL,
					(SUNXI_DAUDIO_RXOM_MASK<<RXOM),
					(SUNXI_DAUDIO_RXOM_EXPH<<RXOM));
		}
		break;
	case	SNDRV_PCM_FORMAT_S20_3LE:
	case	SNDRV_PCM_FORMAT_S24_LE:
		regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_FMT0,
				(SUNXI_DAUDIO_SR_MASK<<SAMPLE_RESOLUTION),
				(SUNXI_DAUDIO_SR_24BIT<<SAMPLE_RESOLUTION));
		if (substream->stream == SNDRV_PCM_STREAM_PLAYBACK)
			regmap_update_bits(sunxi_daudio->regmap,
					SUNXI_DAUDIO_FIFOCTL,
					(SUNXI_DAUDIO_TXIM_MASK<<TXIM),
					(SUNXI_DAUDIO_TXIM_VALID_LSB<<TXIM));
		else
			regmap_update_bits(sunxi_daudio->regmap,
					SUNXI_DAUDIO_FIFOCTL,
					(SUNXI_DAUDIO_RXOM_MASK<<RXOM),
					(SUNXI_DAUDIO_RXOM_EXPH<<RXOM));
		break;
	case	SNDRV_PCM_FORMAT_S32_LE:
		regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_FMT0,
				(SUNXI_DAUDIO_SR_MASK<<SAMPLE_RESOLUTION),
				(SUNXI_DAUDIO_SR_32BIT<<SAMPLE_RESOLUTION));
		if (substream->stream == SNDRV_PCM_STREAM_PLAYBACK)
			regmap_update_bits(sunxi_daudio->regmap,
					SUNXI_DAUDIO_FIFOCTL,
					(SUNXI_DAUDIO_TXIM_MASK<<TXIM),
					(SUNXI_DAUDIO_TXIM_VALID_LSB<<TXIM));
		else
			regmap_update_bits(sunxi_daudio->regmap,
					SUNXI_DAUDIO_FIFOCTL,
					(SUNXI_DAUDIO_RXOM_MASK<<RXOM),
					(SUNXI_DAUDIO_RXOM_EXPH<<RXOM));
		break;
	default:
		dev_err(sunxi_daudio->dev, "unrecognized format\n");
		return -EINVAL;
	}

	if (substream->stream == SNDRV_PCM_STREAM_PLAYBACK) {
		regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_CHCFG,
				(SUNXI_DAUDIO_TX_SLOT_MASK<<TX_SLOT_NUM),
				((params_channels(params)-1)<<TX_SLOT_NUM));
		if (sunxi_daudio->hdmi_en == 0) {
#ifdef SUNXI_DAUDIO_MODE_B
			regmap_write(sunxi_daudio->regmap,
				SUNXI_DAUDIO_TX0CHMAP0, SUNXI_DEFAULT_CHMAP0);
			regmap_write(sunxi_daudio->regmap,
				SUNXI_DAUDIO_TX0CHMAP1, SUNXI_DEFAULT_CHMAP1);
#else
			regmap_write(sunxi_daudio->regmap,
				SUNXI_DAUDIO_TX0CHMAP0, SUNXI_DEFAULT_CHMAP);
#endif
			regmap_update_bits(sunxi_daudio->regmap,
				SUNXI_DAUDIO_TX0CHSEL,
				(SUNXI_DAUDIO_TX_CHSEL_MASK<<TX_CHSEL),
				((params_channels(params)-1)<<TX_CHSEL));
			regmap_update_bits(sunxi_daudio->regmap,
				SUNXI_DAUDIO_TX0CHSEL,
				(SUNXI_DAUDIO_TX_CHEN_MASK<<TX_CHEN),
				((1<<params_channels(params))-1)<<TX_CHEN);
		} else {
			/* HDMI multi-channel processing */
#ifdef SUNXI_HDMI_AUDIO_ENABLE
#ifdef SUNXI_DAUDIO_MODE_B
			regmap_write(sunxi_daudio->regmap,
					SUNXI_DAUDIO_TX0CHMAP1, 0x10);
			if (sunxi_hdmi->hdmi_format > 1) {
				regmap_write(sunxi_daudio->regmap,
						SUNXI_DAUDIO_TX1CHMAP1, 0x32);
				regmap_write(sunxi_daudio->regmap,
						SUNXI_DAUDIO_TX2CHMAP1, 0x54);
				regmap_write(sunxi_daudio->regmap,
						SUNXI_DAUDIO_TX3CHMAP1, 0x76);
			} else {
				if (params_channels(params) > 2)
					regmap_write(sunxi_daudio->regmap,
						SUNXI_DAUDIO_TX1CHMAP1, 0x23);
				if (params_channels(params) > 4) {
					if (params_channels(params) == 6)
						regmap_write(
							sunxi_daudio->regmap,
							SUNXI_DAUDIO_TX2CHMAP1,
							0x54);
					else
						regmap_write(
							sunxi_daudio->regmap,
							SUNXI_DAUDIO_TX2CHMAP1,
							0x76);
				}
				if (params_channels(params) > 6)
					regmap_write(sunxi_daudio->regmap,
							SUNXI_DAUDIO_TX3CHMAP1,
							0x54);
			}
#else
			regmap_write(sunxi_daudio->regmap,
					SUNXI_DAUDIO_TX0CHMAP0, 0x10);
			if (sunxi_hdmi->hdmi_format > 1) {
				/* support for HBR */
				regmap_write(sunxi_daudio->regmap,
						SUNXI_DAUDIO_TX1CHMAP0, 0x32);
				regmap_write(sunxi_daudio->regmap,
						SUNXI_DAUDIO_TX2CHMAP0, 0x54);
				regmap_write(sunxi_daudio->regmap,
						SUNXI_DAUDIO_TX3CHMAP0, 0x76);
			} else {
				/* LPCM 5.1 & 7.1 support */
				if (params_channels(params) > 2)
					regmap_write(sunxi_daudio->regmap,
						SUNXI_DAUDIO_TX1CHMAP0, 0x23);
				if (params_channels(params) > 4) {
					if (params_channels(params) == 6)
						regmap_write(
							sunxi_daudio->regmap,
							SUNXI_DAUDIO_TX2CHMAP0,
							0x54);
					else
						regmap_write(
							sunxi_daudio->regmap,
							SUNXI_DAUDIO_TX2CHMAP0,
							0x76);
				}
				if (params_channels(params) > 6)
					regmap_write(sunxi_daudio->regmap,
							SUNXI_DAUDIO_TX3CHMAP0,
							0x54);
			}
#endif
			regmap_update_bits(sunxi_daudio->regmap,
					SUNXI_DAUDIO_TX0CHSEL,
					0x01 << TX_CHSEL, 0x01 << TX_CHSEL);
			regmap_update_bits(sunxi_daudio->regmap,
					SUNXI_DAUDIO_TX0CHSEL,
					0x03 << TX_CHEN, 0x03 << TX_CHEN);
			regmap_update_bits(sunxi_daudio->regmap,
					SUNXI_DAUDIO_TX1CHSEL,
					0x01 << TX_CHSEL, 0x01 << TX_CHSEL);
			regmap_update_bits(sunxi_daudio->regmap,
					SUNXI_DAUDIO_TX1CHSEL,
					(0x03)<<TX_CHEN, 0x03 << TX_CHEN);
			regmap_update_bits(sunxi_daudio->regmap,
					SUNXI_DAUDIO_TX2CHSEL,
					0x01 << TX_CHSEL, 0x01 << TX_CHSEL);
			regmap_update_bits(sunxi_daudio->regmap,
					SUNXI_DAUDIO_TX2CHSEL,
					(0x03)<<TX_CHEN, 0x03 << TX_CHEN);
			regmap_update_bits(sunxi_daudio->regmap,
					SUNXI_DAUDIO_TX3CHSEL,
					0x01 << TX_CHSEL, 0x01 << TX_CHSEL);
			regmap_update_bits(sunxi_daudio->regmap,
					SUNXI_DAUDIO_TX3CHSEL,
					(0x03)<<TX_CHEN, 0x03 << TX_CHEN);
#endif	/* HDMI */
		}
	} else {
#ifdef SUNXI_DAUDIO_MODE_B
		regmap_write(sunxi_daudio->regmap,
				SUNXI_DAUDIO_RXCHMAP0, SUNXI_DEFAULT_CHMAP0);
		regmap_write(sunxi_daudio->regmap,
				SUNXI_DAUDIO_RXCHMAP1, SUNXI_DEFAULT_CHMAP1);
#else
		regmap_write(sunxi_daudio->regmap,
				SUNXI_DAUDIO_RXCHMAP, SUNXI_DEFAULT_CHMAP);
#endif
		regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_CHCFG,
				(SUNXI_DAUDIO_RX_SLOT_MASK<<RX_SLOT_NUM),
				((params_channels(params)-1)<<RX_SLOT_NUM));
		regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_RXCHSEL,
				(SUNXI_DAUDIO_RX_CHSEL_MASK<<RX_CHSEL),
				((params_channels(params)-1)<<RX_CHSEL));
	}

#ifdef SUNXI_HDMI_AUDIO_ENABLE
	/* Special processing for HDMI hub playback to enable hdmi module */
	if (sunxi_daudio->pdata->daudio_type == SUNXI_DAUDIO_TDMHDMI_TYPE) {
		mutex_lock(&sunxi_daudio->mutex);
		regmap_read(sunxi_daudio->regmap,
				SUNXI_DAUDIO_FIFOCTL, &reg_val);
		sunxi_daudio->hub_mode = (reg_val & (1<<HUB_EN));
		if (sunxi_daudio->hub_mode) {
			sunxi_hdmi_codec_hw_params(substream, params, NULL);
			sunxi_hdmi_codec_prepare(substream, NULL);
		}
		mutex_unlock(&sunxi_daudio->mutex);
	}
#endif

	return 0;
}

static int sunxi_daudio_set_fmt(struct snd_soc_dai *dai, unsigned int fmt)
{
	struct sunxi_daudio_info *sunxi_daudio = snd_soc_dai_get_drvdata(dai);

	sunxi_daudio_init_fmt(sunxi_daudio, fmt);
	return 0;
}

static int sunxi_daudio_set_sysclk(struct snd_soc_dai *dai,
			int clk_id, unsigned int freq, int dir)
{
	struct sunxi_daudio_info *sunxi_daudio = snd_soc_dai_get_drvdata(dai);

	if (clk_set_rate(sunxi_daudio->pllclk, freq)) {
		dev_err(sunxi_daudio->dev, "set pllclk rate failed\n");
    printk("set sysclk error");
		return -EBUSY;
	}
	return 0;
}

static int sunxi_daudio_set_clkdiv(struct snd_soc_dai *dai,
				int clk_id, int clk_div)
{
	struct sunxi_daudio_info *sunxi_daudio = snd_soc_dai_get_drvdata(dai);
	unsigned int bclk_div, div_ratio;

	if (sunxi_daudio->pdata->tdm_config)
		/* I2S/TDM two channel mode */
		div_ratio = clk_div/(2 * sunxi_daudio->pdata->pcm_lrck_period);
	else
		/* PCM mode */
		div_ratio = clk_div / sunxi_daudio->pdata->pcm_lrck_period;

	switch (div_ratio) {
	case	1:
		bclk_div = SUNXI_DAUDIO_BCLK_DIV_1;
		break;
	case	2:
		bclk_div = SUNXI_DAUDIO_BCLK_DIV_2;
		break;
	case	4:
		bclk_div = SUNXI_DAUDIO_BCLK_DIV_3;
		break;
	case	6:
		bclk_div = SUNXI_DAUDIO_BCLK_DIV_4;
		break;
	case	8:
		bclk_div = SUNXI_DAUDIO_BCLK_DIV_5;
		break;
	case	12:
		bclk_div = SUNXI_DAUDIO_BCLK_DIV_6;
		break;
	case	16:
		bclk_div = SUNXI_DAUDIO_BCLK_DIV_7;
		break;
	case	24:
		bclk_div = SUNXI_DAUDIO_BCLK_DIV_8;
		break;
	case	32:
		bclk_div = SUNXI_DAUDIO_BCLK_DIV_9;
		break;
	case	48:
		bclk_div = SUNXI_DAUDIO_BCLK_DIV_10;
		break;
	case	64:
		bclk_div = SUNXI_DAUDIO_BCLK_DIV_11;
		break;
	case	96:
		bclk_div = SUNXI_DAUDIO_BCLK_DIV_12;
		break;
	case	128:
		bclk_div = SUNXI_DAUDIO_BCLK_DIV_13;
		break;
	case	176:
		bclk_div = SUNXI_DAUDIO_BCLK_DIV_14;
		break;
	case	192:
		bclk_div = SUNXI_DAUDIO_BCLK_DIV_15;
		break;
	default:
		dev_err(sunxi_daudio->dev, "unsupport clk_div\n");
		return -EINVAL;
	}

	/* setting bclk to driver external codec bit clk */
	regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_CLKDIV,
			(SUNXI_DAUDIO_BCLK_DIV_MASK<<BCLK_DIV),
			(bclk_div<<BCLK_DIV));
	return 0;
}

static int sunxi_daudio_dai_startup(struct snd_pcm_substream *substream,
				struct snd_soc_dai *dai)
{
	struct sunxi_daudio_info *sunxi_daudio = snd_soc_dai_get_drvdata(dai);

	/* FIXME: As HDMI module to play audio, it need at least 1100ms to sync.
	 * if we not wait we lost audio data to playback, or we wait for 1100ms
	 * to playback, user experience worst than you can imagine. So we need
	 * to cutdown that sync time by keeping clock signal on. we just enable
	 * it at startup and resume, cutdown it at remove and suspend time.
	 */
	if (sunxi_daudio->pdata->daudio_type == SUNXI_DAUDIO_TDMHDMI_TYPE)
		regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_CTL,
				(1<<CTL_TXEN), (1<<CTL_TXEN));

	if (substream->stream == SNDRV_PCM_STREAM_PLAYBACK)
		snd_soc_dai_set_dma_data(dai, substream,
					&sunxi_daudio->playback_dma_param);
	else
		snd_soc_dai_set_dma_data(dai, substream,
					&sunxi_daudio->capture_dma_param);

	return 0;
}

static int sunxi_daudio_trigger(struct snd_pcm_substream *substream,
				int cmd, struct snd_soc_dai *dai)
{
	struct sunxi_daudio_info *sunxi_daudio = snd_soc_dai_get_drvdata(dai);

	switch (cmd) {
	case	SNDRV_PCM_TRIGGER_START:
	case	SNDRV_PCM_TRIGGER_RESUME:
	case	SNDRV_PCM_TRIGGER_PAUSE_RELEASE:
		if (substream->stream == SNDRV_PCM_STREAM_PLAYBACK) {
			if (daudio_loop_en)
				regmap_update_bits(sunxi_daudio->regmap,
						SUNXI_DAUDIO_CTL,
						(1<<LOOP_EN), (1<<LOOP_EN));
			else
				regmap_update_bits(sunxi_daudio->regmap,
						SUNXI_DAUDIO_CTL,
						(1<<LOOP_EN), (0<<LOOP_EN));
			sunxi_daudio_txctrl_enable(sunxi_daudio, 1);
		} else {
			sunxi_daudio_rxctrl_enable(sunxi_daudio, 1);
		}
		break;
	case	SNDRV_PCM_TRIGGER_STOP:
	case	SNDRV_PCM_TRIGGER_SUSPEND:
	case	SNDRV_PCM_TRIGGER_PAUSE_PUSH:
		if (substream->stream == SNDRV_PCM_STREAM_PLAYBACK)
			sunxi_daudio_txctrl_enable(sunxi_daudio, 0);
		else
			sunxi_daudio_rxctrl_enable(sunxi_daudio, 0);
		break;
	default:
		return -EINVAL;
	}
	return 0;
}

static int sunxi_daudio_prepare(struct snd_pcm_substream *substream,
				struct snd_soc_dai *dai)
{
	unsigned int i;
	struct sunxi_daudio_info *sunxi_daudio = snd_soc_dai_get_drvdata(dai);

	if (substream->stream == SNDRV_PCM_STREAM_PLAYBACK) {
		for (i = 0 ; i < SUNXI_DAUDIO_FTX_TIMES ; i++) {
			regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_FIFOCTL,
				(1<<FIFO_CTL_FTX), (1<<FIFO_CTL_FTX));
			mdelay(1);
		}
		regmap_write(sunxi_daudio->regmap, SUNXI_DAUDIO_TXCNT, 0);
	} else {
		regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_FIFOCTL,
				(1<<FIFO_CTL_FRX), (1<<FIFO_CTL_FRX));
		regmap_write(sunxi_daudio->regmap, SUNXI_DAUDIO_RXCNT, 0);
	}
	return 0;
}

static int sunxi_daudio_probe(struct snd_soc_dai *dai)
{
	struct sunxi_daudio_info *sunxi_daudio = snd_soc_dai_get_drvdata(dai);

	mutex_init(&sunxi_daudio->mutex);

	sunxi_daudio_init(sunxi_daudio);
	return 0;
}

static void sunxi_daudio_shutdown(struct snd_pcm_substream *substream,
				struct snd_soc_dai *dai)
{
	struct sunxi_daudio_info *sunxi_daudio = snd_soc_dai_get_drvdata(dai);

	/* Special processing for HDMI hub playback to shutdown hdmi module */
	if (sunxi_daudio->pdata->daudio_type == SUNXI_DAUDIO_TDMHDMI_TYPE) {
		mutex_lock(&sunxi_daudio->mutex);
		if (sunxi_daudio->hub_mode)
			sunxi_hdmi_codec_shutdown(substream, NULL);
		mutex_unlock(&sunxi_daudio->mutex);
	}
}

static int sunxi_daudio_remove(struct snd_soc_dai *dai)
{
	struct sunxi_daudio_info *sunxi_daudio = snd_soc_dai_get_drvdata(dai);

	if (sunxi_daudio->pdata->daudio_type == SUNXI_DAUDIO_TDMHDMI_TYPE)
		regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_CTL,
				(1<<CTL_TXEN), (0<<CTL_TXEN));
	return 0;
}

static int sunxi_daudio_suspend(struct snd_soc_dai *dai)
{
	struct sunxi_daudio_info *sunxi_daudio = snd_soc_dai_get_drvdata(dai);
	int ret = 0;

	pr_debug("[daudio] suspend .%s start\n", dev_name(sunxi_daudio->dev));

	/* Global disable I2S/TDM module */
	sunxi_daudio_global_enable(sunxi_daudio, 0);

	if (sunxi_daudio->pdata->daudio_type == SUNXI_DAUDIO_TDMHDMI_TYPE)
		regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_CTL,
				(1<<CTL_TXEN), (0<<CTL_TXEN));

	clk_disable_unprepare(sunxi_daudio->moduleclk);
	clk_disable_unprepare(sunxi_daudio->pllclk);

	if (sunxi_daudio->pdata->external_type) {
		ret = pinctrl_select_state(sunxi_daudio->pinctrl,
				sunxi_daudio->pinstate_sleep);
		if (ret) {
			pr_warn("[daudio]select pin sleep state failed\n");
			return ret;
		}
		devm_pinctrl_put(sunxi_daudio->pinctrl);
	}

	if (sunxi_daudio->vol_supply.daudio_regulator)
		regulator_disable(sunxi_daudio->vol_supply.daudio_regulator);

	pr_debug("[daudio] suspend .%s end \n", dev_name(sunxi_daudio->dev));

	return 0;
}

static int sunxi_daudio_resume(struct snd_soc_dai *dai)
{
	struct sunxi_daudio_info *sunxi_daudio = snd_soc_dai_get_drvdata(dai);
	int ret;

	pr_debug("[%s] resume .%s start\n", __func__,
		dev_name(sunxi_daudio->dev));

	if (sunxi_daudio->vol_supply.daudio_regulator) {
		ret = regulator_enable(sunxi_daudio->vol_supply.daudio_regulator);
		if (ret) {
			dev_err(sunxi_daudio->dev,
				"resume enable duaido vcc-pin failed\n");
				return ret;
		}
	}

	if (clk_prepare_enable(sunxi_daudio->pllclk)) {
		dev_err(sunxi_daudio->dev, "pllclk resume failed\n");
		ret = -EBUSY;
		goto err_resume_out;
	}

	if (clk_prepare_enable(sunxi_daudio->moduleclk)) {
		dev_err(sunxi_daudio->dev, "moduleclk resume failed\n");
		ret = -EBUSY;
		goto err_pllclk_disable;
	}

	if (sunxi_daudio->pdata->external_type) {
		sunxi_daudio->pinctrl = devm_pinctrl_get(sunxi_daudio->dev);
		if (IS_ERR_OR_NULL(sunxi_daudio)) {
			dev_err(sunxi_daudio->dev, "pinctrl resume get fail\n");
			ret = -ENOMEM;
			goto err_moduleclk_disable;
		}

		sunxi_daudio->pinstate = pinctrl_lookup_state(
				sunxi_daudio->pinctrl, PINCTRL_STATE_DEFAULT);
		if (IS_ERR_OR_NULL(sunxi_daudio->pinstate)) {
			dev_err(sunxi_daudio->dev,
				"pinctrl default state get failed\n");
			ret = -EINVAL;
			goto err_pinctrl_put;
		}

		sunxi_daudio->pinstate_sleep = pinctrl_lookup_state(
				sunxi_daudio->pinctrl, PINCTRL_STATE_SLEEP);
		if (IS_ERR_OR_NULL(sunxi_daudio->pinstate_sleep)) {
			dev_err(sunxi_daudio->dev,
				"pinctrl sleep state get failed\n");
			ret = -EINVAL;
			goto err_pinctrl_put;
		}

		ret = pinctrl_select_state(sunxi_daudio->pinctrl,
					sunxi_daudio->pinstate);
		if (ret)
			dev_warn(sunxi_daudio->dev,
				"daudio set pinctrl default state fail\n");
	}

	sunxi_daudio_init(sunxi_daudio);

	/* Global enable I2S/TDM module */
	sunxi_daudio_global_enable(sunxi_daudio, 1);

	if (sunxi_daudio->pdata->daudio_type == SUNXI_DAUDIO_TDMHDMI_TYPE)
		regmap_update_bits(sunxi_daudio->regmap, SUNXI_DAUDIO_CTL,
				(1<<CTL_TXEN), (1<<CTL_TXEN));

	pr_debug("[%s] resume .%s end\n", __func__,
			dev_name(sunxi_daudio->dev));

	return 0;

err_pinctrl_put:
	devm_pinctrl_put(sunxi_daudio->pinctrl);
err_moduleclk_disable:
	clk_disable_unprepare(sunxi_daudio->moduleclk);
err_pllclk_disable:
	clk_disable_unprepare(sunxi_daudio->pllclk);
err_resume_out:
	if (sunxi_daudio->vol_supply.daudio_regulator)
		regulator_disable(sunxi_daudio->vol_supply.daudio_regulator);
	return ret;
}

/*
 * ex:
 * param 1: 0 read;1 write
 * param 2: 0 daudio0; 1 daudio1;
 * param 3: address;
 * param 4: write value;
 * read:
 *	echo 0,0 > daudio_reg_debug
 *	echo 0,1 > daudio_reg_debug
 *	echo 0,0,0x4 > daudio_reg_debug
 *	echo 0,1,0x0a > daudio_reg_debug
 * write:
 *	echo 1,0,0x00,0xa > daudio_reg_debug
 *	echo 1,0,0x00,0xff > daudio_reg_debug
 */
static ssize_t daudio_class_debug_store(struct class *class,
		struct class_attribute *attr, const char *buf, size_t count)
{
	int ret;
	int rw_flag;
	int reg_val_read;
	int input_reg_val = 0;
	int input_reg_group = 0;
	int input_reg_offset = 0;
	int i = 0;

	ret = sscanf(buf, "%d,%d,0x%x,0x%x", &rw_flag, &input_reg_group,
			&input_reg_offset, &input_reg_val);

	if (!(rw_flag == 1 || rw_flag == 0)) {
		pr_err("not rw_flag\n");
		return count;
	}

	if ((input_reg_group >= DAUDIO_NUM_MAX) ||
		!sunxi_daudio_global[input_reg_group]) {
		pr_err("not exist daudio[%d] driver or device\n",
						input_reg_group);
		pr_err("Daudio device list:\n\n");
		for (i = 0; i < DAUDIO_NUM_MAX; i++) {
			if (sunxi_daudio_global[i] != NULL)
				pr_err("    Daudio[%d]\n", i);
		}
		return count;
	}

	pr_err("Dump daudio reg:\n");

	if (rw_flag) {
		if (ret == 4) {
			writel(input_reg_val,
				sunxi_daudio_global[input_reg_group]->membase +
					input_reg_offset);
			reg_val_read = readl(
				sunxi_daudio_global[input_reg_group]->membase +
					input_reg_offset);
			pr_err("\n\n Daudio[%d] Reg[0x%x] : 0x%x\n\n",
				input_reg_group, input_reg_offset,
				reg_val_read);
		} else {
			pr_err("\nnum of params invalid\n");
		}
	} else {
		switch (ret) {
		case 2:
			while (reg_labels[i].name != NULL) {
				pr_err("%s 0x%p: 0x%x\n", reg_labels[i].name,
				(sunxi_daudio_global[input_reg_group]->membase +
							reg_labels[i].address),
				readl(sunxi_daudio_global[input_reg_group]->membase +
							reg_labels[i].address));
				i++;
			}
			break;
		case 3:
			reg_val_read = readl(
				sunxi_daudio_global[input_reg_group]->membase +
					input_reg_offset);
			pr_err("\n\n Daudio[%d] Reg[0x%x] : 0x%x\n\n",
				input_reg_group, input_reg_offset,
				reg_val_read);
			break;
		default:
			pr_err("\nnum of params invalid\n");
			break;
		}
	}

	return count;
}

static ssize_t daudio_class_debug_show(struct class *class,
			struct class_attribute *attr, char *buf)
{
	int count = 0;
	int i = 0;

	count += sprintf(buf, "Example(:daudio_num->%d):\n", DAUDIO_NUM_MAX);
	count += sprintf(buf + count, "param 1: 0 read; 1 write;\n");
	count += sprintf(buf + count, "param 2: 0 daudio0; 1 dauido1...;\n");
	count += sprintf(buf + count, "param 3: address\n");
	count += sprintf(buf + count, "param 4: reg value\n\n");
	count += sprintf(buf + count, "echo 0,0 > daudio_reg_debug\n");
	count += sprintf(buf + count, "echo 0,1,0x4 > daudio_reg_debug\n");
	count += sprintf(buf + count, "echo 1,0,0x4,0x1 > daudio_reg_debug\n");
	count += sprintf(buf + count, "echo 1,1,0x14,0xa > daudio_reg_debug\n\n");

	count += sprintf(buf + count, "daudio device list:\n\n");
	for (i = 0; i < DAUDIO_NUM_MAX; i++) {
		if (sunxi_daudio_global[i] != NULL)
			count += sprintf(buf + count, "    Daudio[%d]\n", i);
	}

	return count;
}

static struct class_attribute daudio_class_attrs[] = {
	__ATTR(daudio_reg_debug, S_IRUGO|S_IWUSR, daudio_class_debug_show, daudio_class_debug_store),
	__ATTR_NULL
};
static struct class daudio_class = {
	.name = "daudio",
	.class_attrs = daudio_class_attrs,
};

#define	SUNXI_DAUDIO_RATES	(SNDRV_PCM_RATE_8000_192000 \
				| SNDRV_PCM_RATE_KNOT)

static struct snd_soc_dai_ops sunxi_daudio_dai_ops = {
	.hw_params = sunxi_daudio_hw_params,
	.set_sysclk = sunxi_daudio_set_sysclk,
	.set_clkdiv = sunxi_daudio_set_clkdiv,
	.set_fmt = sunxi_daudio_set_fmt,
	.startup = sunxi_daudio_dai_startup,
	.trigger = sunxi_daudio_trigger,
	.prepare = sunxi_daudio_prepare,
	.shutdown = sunxi_daudio_shutdown,
};

static struct snd_soc_dai_driver sunxi_daudio_dai = {
	.probe = sunxi_daudio_probe,
	.suspend = sunxi_daudio_suspend,
	.resume = sunxi_daudio_resume,
	.remove = sunxi_daudio_remove,
	.playback = {
		.channels_min = 1,
		.channels_max = 8,
		.rates = SUNXI_DAUDIO_RATES,
		.formats = SNDRV_PCM_FMTBIT_S16_LE
			| SNDRV_PCM_FMTBIT_S20_3LE
			| SNDRV_PCM_FMTBIT_S24_LE
			| SNDRV_PCM_FMTBIT_S32_LE,
	},
	.capture = {
		.channels_min = 1,
		.channels_max = 8,
		.rates = SUNXI_DAUDIO_RATES,
		.formats = SNDRV_PCM_FMTBIT_S16_LE
			| SNDRV_PCM_FMTBIT_S20_3LE
			| SNDRV_PCM_FMTBIT_S24_LE
			| SNDRV_PCM_FMTBIT_S32_LE,
	},
	.ops = &sunxi_daudio_dai_ops,
};

static const struct snd_soc_component_driver sunxi_daudio_component = {
	.name		= DRV_NAME,
	.controls	= sunxi_daudio_controls,
	.num_controls	= ARRAY_SIZE(sunxi_daudio_controls),
};

static struct sunxi_daudio_platform_data sunxi_daudio = {
	.daudio_type = SUNXI_DAUDIO_EXTERNAL_TYPE,
	.external_type = 1,
};

static struct sunxi_daudio_platform_data sunxi_tdmhdmi = {
	.daudio_type = SUNXI_DAUDIO_TDMHDMI_TYPE,
	.external_type = 0,
	.audio_format = 1,
	.signal_inversion = 1,
	.daudio_master = 4,
	.pcm_lrck_period = 32,
	.slot_width_select = 32,
	.tdm_config = 1,
	.mclk_div = 0,
};

static const struct of_device_id sunxi_daudio_of_match[] = {
	{
		.compatible = "allwinner,sunxi-daudio",
		.data = &sunxi_daudio,
	},
	{
		.compatible = "allwinner,sunxi-tdmhdmi",
		.data = &sunxi_tdmhdmi,
	},
	{},
};
MODULE_DEVICE_TABLE(of, sunxi_daudio_of_match);

static const struct regmap_config sunxi_daudio_regmap_config = {
	.reg_bits = 32,
	.reg_stride = 4,
	.val_bits = 32,
	.max_register = SUNXI_DAUDIO_DEBUG,
	.cache_type = REGCACHE_NONE,
};

static int sunxi_daudio_dev_probe(struct platform_device *pdev)
{
	struct resource res, *memregion;
	const struct of_device_id *match;
	void __iomem *sunxi_daudio_membase;
	struct sunxi_daudio_info *sunxi_daudio;
	struct device_node *np = pdev->dev.of_node;
	unsigned int temp_val;
	int ret;

	match = of_match_device(sunxi_daudio_of_match, &pdev->dev);
	if (match) {
		sunxi_daudio = devm_kzalloc(&pdev->dev,
					sizeof(struct sunxi_daudio_info),
					GFP_KERNEL);
		if (!sunxi_daudio) {
			dev_err(&pdev->dev, "alloc sunxi_daudio failed\n");
			ret = -ENOMEM;
			goto err_node_put;
		}
		dev_set_drvdata(&pdev->dev, sunxi_daudio);
		sunxi_daudio->dev = &pdev->dev;

		sunxi_daudio->pdata = devm_kzalloc(&pdev->dev,
				sizeof(struct sunxi_daudio_platform_data),
				GFP_KERNEL);
		if (!sunxi_daudio->pdata) {
			dev_err(&pdev->dev,
				"alloc sunxi daudio platform data failed\n");
			ret = -ENOMEM;
			goto err_devm_kfree;
		}

		memcpy(sunxi_daudio->pdata, match->data,
			sizeof(struct sunxi_daudio_platform_data));
	} else {
		dev_err(&pdev->dev, "node match failed\n");
		return -EINVAL;
	}

	ret = of_address_to_resource(np, 0, &res);
	if (ret) {
		dev_err(&pdev->dev, "parse device node resource failed\n");
		ret = -EINVAL;
		goto err_devm_kfree;
	}

	memregion = devm_request_mem_region(&pdev->dev, res.start,
					resource_size(&res), DRV_NAME);
	if (!memregion) {
		dev_err(&pdev->dev, "Memory region already claimed\n");
		ret = -EBUSY;
		goto err_devm_kfree;
	}

	sunxi_daudio_membase = ioremap(res.start, resource_size(&res));
	if (!sunxi_daudio_membase) {
		dev_err(&pdev->dev, "ioremap failed\n");
		ret = -EBUSY;
		goto err_devm_kfree;
	}

	sunxi_daudio->membase = sunxi_daudio_membase;

	sunxi_daudio->regmap = devm_regmap_init_mmio(&pdev->dev,
					sunxi_daudio_membase,
					&sunxi_daudio_regmap_config);
	if (IS_ERR(sunxi_daudio->regmap)) {
		dev_err(&pdev->dev, "regmap init failed\n");
		ret = PTR_ERR(sunxi_daudio->regmap);
		goto err_iounmap;
	}

	sunxi_daudio->pllclk = of_clk_get(np, 0);
	if (IS_ERR_OR_NULL(sunxi_daudio->pllclk)) {
		dev_err(&pdev->dev, "pllclk get failed\n");
		ret = PTR_ERR(sunxi_daudio->pllclk);
		goto err_iounmap;
	}

	sunxi_daudio->moduleclk = of_clk_get(np, 1);
	if (IS_ERR_OR_NULL(sunxi_daudio->moduleclk)) {
		dev_err(&pdev->dev, "moduleclk get failed\n");
		ret = PTR_ERR(sunxi_daudio->moduleclk);
		goto err_pllclk_put;
	}

	if (clk_set_parent(sunxi_daudio->moduleclk, sunxi_daudio->pllclk)) {
		dev_err(&pdev->dev, "set parent of moduleclk to pllclk fail\n");
		ret = -EBUSY;
		goto err_moduleclk_put;
	}
	clk_prepare_enable(sunxi_daudio->pllclk);
	clk_prepare_enable(sunxi_daudio->moduleclk);

	if (sunxi_daudio->pdata->external_type) {
		sunxi_daudio->pinctrl = devm_pinctrl_get(&pdev->dev);
		if (IS_ERR_OR_NULL(sunxi_daudio->pinctrl)) {
			dev_err(&pdev->dev, "pinctrl get failed\n");
			ret = -EINVAL;
			goto err_moduleclk_put;
		}

		sunxi_daudio->pinstate = pinctrl_lookup_state(
				sunxi_daudio->pinctrl, PINCTRL_STATE_DEFAULT);
		if (IS_ERR_OR_NULL(sunxi_daudio->pinstate)) {
			dev_err(&pdev->dev, "pinctrl default state get fail\n");
			ret = -EINVAL;
			goto err_pinctrl_put;
		}

		sunxi_daudio->pinstate_sleep = pinctrl_lookup_state(
				sunxi_daudio->pinctrl, PINCTRL_STATE_SLEEP);
		if (IS_ERR_OR_NULL(sunxi_daudio->pinstate_sleep)) {
			dev_err(&pdev->dev, "pinctrl sleep state get failed\n");
			ret = -EINVAL;
			goto err_pinctrl_put;
		}

		ret = pinctrl_select_state(sunxi_daudio->pinctrl,
					sunxi_daudio->pinstate);
		if (ret)
			dev_warn(sunxi_daudio->dev,
				"daudio set pinctrl default state fail\n");
	}

	switch (sunxi_daudio->pdata->daudio_type) {
	case	SUNXI_DAUDIO_EXTERNAL_TYPE:
		ret = of_property_read_u32(np, "tdm_num", &temp_val);
		if (ret < 0) {
			dev_warn(&pdev->dev, "tdm configuration missing\n");
			/*
			 * warnning just continue,
			 * making tdm_num as default setting
			 */
			sunxi_daudio->pdata->tdm_num = 0;
		} else {
			/*
			 * FIXME, for channel number mess,
			 * so just not check channel overflow
			 */
			sunxi_daudio->pdata->tdm_num = temp_val;
		}

		sunxi_daudio->playback_dma_param.src_maxburst = 4;
		sunxi_daudio->playback_dma_param.dst_maxburst = 4;

		sunxi_daudio->capture_dma_param.dma_addr =
					res.start + SUNXI_DAUDIO_RXFIFO;
		sunxi_daudio->capture_dma_param.src_maxburst = 4;
		sunxi_daudio->capture_dma_param.dst_maxburst = 4;

		sunxi_daudio->playback_dma_param.dma_addr =
					res.start + SUNXI_DAUDIO_TXFIFO;
		switch (sunxi_daudio->pdata->tdm_num) {
		case 0:
			SUNXI_DAUDIO_DRQDST(sunxi_daudio, 0);
			SUNXI_DAUDIO_DRQSRC(sunxi_daudio, 0);
			break;
		case 1:
			SUNXI_DAUDIO_DRQDST(sunxi_daudio, 1);
			SUNXI_DAUDIO_DRQSRC(sunxi_daudio, 1);
			break;
		case 2:
			SUNXI_DAUDIO_DRQDST(sunxi_daudio, 2);
			SUNXI_DAUDIO_DRQSRC(sunxi_daudio, 2);
			break;
		case 3:
			SUNXI_DAUDIO_DRQDST(sunxi_daudio, 3);
			SUNXI_DAUDIO_DRQSRC(sunxi_daudio, 3);
			break;
		default:
			dev_warn(&pdev->dev, "tdm_num setting overflow\n");
			ret = -EFAULT;
			goto err_pinctrl_put;
			break;
		}

		sunxi_daudio->vol_supply.regulator_name = NULL;
		ret = of_property_read_string(np, "daudio_regulator",
				&sunxi_daudio->vol_supply.regulator_name);
		if (ret < 0) {
			dev_warn(&pdev->dev, "regulator missing or invalid\n");
			sunxi_daudio->vol_supply.daudio_regulator = NULL;
		} else {
			sunxi_daudio->vol_supply.daudio_regulator =
				regulator_get(NULL, sunxi_daudio->vol_supply.regulator_name);
			if (IS_ERR(sunxi_daudio->vol_supply.daudio_regulator)) {
				pr_err("get duaido[%d] vcc-pin failed\n",
					sunxi_daudio->pdata->tdm_num);
				ret = -EFAULT;
				goto err_pinctrl_put;
			} else {
				ret = regulator_set_voltage(
					sunxi_daudio->vol_supply.daudio_regulator,
					3300000, 3300000);
				if (ret) {
					pr_err("set duaido[%d] voltage failed\n",
						sunxi_daudio->pdata->tdm_num);
					ret = -EFAULT;
					goto err_regulator_get;
				}
				ret = regulator_enable(
					sunxi_daudio->vol_supply.daudio_regulator);
				if (ret) {
					pr_err("enable duaido[%d] vcc-pin failed\n",
						sunxi_daudio->pdata->tdm_num);
					ret = -EFAULT;
					goto err_regulator_get;
				}
			}
		}

		ret = of_property_read_u32(np, "daudio_master", &temp_val);
		if (ret < 0) {
			dev_warn(&pdev->dev, "daudio_master configuration missing or invalid\n");
			/*
			 * default setting SND_SOC_DAIFMT_CBS_CFS mode
			 * codec clk & FRM slave
			 */
			sunxi_daudio->pdata->daudio_master = 4;
		} else {
			sunxi_daudio->pdata->daudio_master = temp_val;
		}

		ret = of_property_read_u32(np, "pcm_lrck_period", &temp_val);
		if (ret < 0) {
			dev_warn(&pdev->dev, "pcm_lrck_period configuration missing or invalid\n");
			sunxi_daudio->pdata->pcm_lrck_period = 0;
		} else {
			sunxi_daudio->pdata->pcm_lrck_period = temp_val;
		}

		ret = of_property_read_u32(np, "slot_width_select", &temp_val);
		if (ret < 0) {
			dev_warn(&pdev->dev, "slot_width_select configuration missing or invalid\n");
			sunxi_daudio->pdata->slot_width_select = 0;
		} else {
			sunxi_daudio->pdata->slot_width_select = temp_val;
		}

		ret = of_property_read_u32(np, "audio_format", &temp_val);
		if (ret < 0) {
			dev_warn(&pdev->dev, "audio_format configuration missing or invalid\n");
			sunxi_daudio->pdata->audio_format = 1;
		} else {
			sunxi_daudio->pdata->audio_format = temp_val;
		}

		ret = of_property_read_u32(np, "signal_inversion", &temp_val);
		if (ret < 0) {
			dev_warn(&pdev->dev, "signal_inversion configuration missing or invalid\n");
			sunxi_daudio->pdata->signal_inversion = 1;
		} else {
			sunxi_daudio->pdata->signal_inversion = temp_val;
		}

		ret = of_property_read_u32(np, "frametype", &temp_val);
		if (ret < 0) {
			dev_warn(&pdev->dev, "frametype configuration missing or invalid\n");
			sunxi_daudio->pdata->frame_type = 0;
		} else {
			sunxi_daudio->pdata->frame_type = temp_val;
		}

		ret = of_property_read_u32(np, "tdm_config", &temp_val);
		if (ret < 0) {
			dev_warn(&pdev->dev, "tdm_config configuration missing or invalid\n");
			sunxi_daudio->pdata->tdm_config = 1;
		} else {
			sunxi_daudio->pdata->tdm_config = temp_val;
		}

		ret = of_property_read_u32(np, "mclk_div", &temp_val);
		if (ret < 0)
			sunxi_daudio->pdata->mclk_div = 0;
		else
			sunxi_daudio->pdata->mclk_div = temp_val;

		of_node_put(np);
		break;
	case	SUNXI_DAUDIO_TDMHDMI_TYPE:
#ifdef SUNXI_HDMI_AUDIO_ENABLE
		sunxi_daudio->playback_dma_param.dma_addr =
				res.start + SUNXI_DAUDIO_TXFIFO;
		sunxi_daudio->playback_dma_param.dma_drq_type_num =
					DRQDST_HDMI_TX;
		sunxi_daudio->playback_dma_param.src_maxburst = 8;
		sunxi_daudio->playback_dma_param.dst_maxburst = 8;
		sunxi_daudio->hdmi_en = 1;
#endif
		break;
	default:
		dev_err(&pdev->dev, "missing digital audio type\n");
		ret = -EINVAL;
		goto err_moduleclk_put;
	}

	ret = snd_soc_register_component(&pdev->dev, &sunxi_daudio_component,
					&sunxi_daudio_dai, 1);
	if (ret) {
		dev_err(&pdev->dev, "component register failed\n");
		ret = -ENOMEM;
		goto err_regulator_disable;
	}

	switch (sunxi_daudio->pdata->daudio_type) {
	case	SUNXI_DAUDIO_EXTERNAL_TYPE:
		ret = asoc_dma_platform_register(&pdev->dev, 0);
		if (ret) {
			dev_err(&pdev->dev, "register ASoC platform failed\n");
			ret = -ENOMEM;
			goto err_unregister_component;
		}
		break;
	case	SUNXI_DAUDIO_TDMHDMI_TYPE:
		ret = asoc_dma_platform_register(&pdev->dev,
				SND_DMAENGINE_PCM_FLAG_NO_DT);
		if (ret) {
			dev_err(&pdev->dev, "register ASoC platform failed\n");
			ret = -ENOMEM;
			goto err_unregister_component;
		}
		break;
	default:
		dev_err(&pdev->dev, "missing digital audio type\n");
		ret = -EINVAL;
		goto err_unregister_component;
	}

	if (device_count++ == 0) {
		ret = class_register(&daudio_class);
		if (ret)
		pr_warn("daudio: Failed to create debugfs directory\n");
	}
	sunxi_daudio_global[sunxi_daudio->pdata->tdm_num] = sunxi_daudio;

	sunxi_daudio_global_enable(sunxi_daudio, 1);

	return 0;

err_unregister_component:
	snd_soc_unregister_component(&pdev->dev);
err_regulator_disable:
	if (sunxi_daudio->vol_supply.daudio_regulator)
		regulator_disable(sunxi_daudio->vol_supply.daudio_regulator);
err_regulator_get:
	if (sunxi_daudio->vol_supply.daudio_regulator)
		regulator_put(sunxi_daudio->vol_supply.daudio_regulator);
err_pinctrl_put:
	if (sunxi_daudio->pdata->external_type)
		devm_pinctrl_put(sunxi_daudio->pinctrl);
err_moduleclk_put:
	clk_put(sunxi_daudio->moduleclk);
err_pllclk_put:
	clk_put(sunxi_daudio->pllclk);
err_iounmap:
	iounmap(sunxi_daudio_membase);
err_devm_kfree:
	devm_kfree(&pdev->dev, sunxi_daudio);
err_node_put:
	of_node_put(np);
	return ret;
}

static int __exit sunxi_daudio_dev_remove(struct platform_device *pdev)
{
	struct sunxi_daudio_info *sunxi_daudio = dev_get_drvdata(&pdev->dev);

	if (--device_count == 0)
		class_unregister(&daudio_class);

	if (sunxi_daudio->pdata->external_type)
		devm_pinctrl_put(sunxi_daudio->pinctrl);

	if (sunxi_daudio->vol_supply.daudio_regulator) {
		regulator_disable(sunxi_daudio->vol_supply.daudio_regulator);
		regulator_put(sunxi_daudio->vol_supply.daudio_regulator);
	}

	snd_soc_unregister_component(&pdev->dev);
	clk_put(sunxi_daudio->moduleclk);
	clk_put(sunxi_daudio->pllclk);
	devm_kfree(&pdev->dev, sunxi_daudio);
	return 0;
}

static struct platform_driver sunxi_daudio_driver = {
	.probe = sunxi_daudio_dev_probe,
	.remove = __exit_p(sunxi_daudio_dev_remove),
	.driver = {
		.name = DRV_NAME,
		.owner = THIS_MODULE,
		.of_match_table = sunxi_daudio_of_match,
	},
};

module_platform_driver(sunxi_daudio_driver);

MODULE_AUTHOR("wolfgang huang <huangjinhui@allwinnertech.com>");
MODULE_DESCRIPTION("SUNXI DAI AUDIO ASoC Interface");
MODULE_LICENSE("GPL");
MODULE_ALIAS("platform:sunxi-daudio");
