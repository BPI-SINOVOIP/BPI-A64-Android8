/* Sunxi Remote Controller
 *
 * keymap imported from ir-keymaps.c
 *
 * Copyright (c) 2014 by allwinnertech
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

#include <media/rc-map.h>
#include "sunxi-ir-rx.h"

/*It is used for sunxi legacy ir addr mapping in kernel mode*/
#ifdef CONFIG_SUNXI_KEYMAPPING_SUPPORT
static u32 match_addr[MAX_ADDR_NUM];
static u32 match_num;
#endif
static struct rc_map_table sunxi_nec_scan[] = {
	{ 0xc61712, KEY_POWER },
	{ 0xc61701, KEY_UP },
	{ 0xc61719, KEY_LEFT },
	{ 0xc61711, KEY_RIGHT },
	{ 0xc61709, KEY_DOWN },
	{ 0xc61740, KEY_ENTER },
	{ 0xc6170f, KEY_HOME },
	{ 0xc6170d, KEY_MENU },
	{ 0xc6171c, KEY_VOLUMEUP },
	{ 0xc6177f, KEY_VOLUMEDOWN },
};

#ifdef CONFIG_SUNXI_KEYMAPPING_SUPPORT
static u32 sunxi_key_mapping(u32 code)
{
	u32 i;

	for (i = 0; i < match_num; i++) {
		if (match_addr[i] == ((code >> 8) & 0xffffUL))
			return code;
	}
	return KEY_RESERVED;
}
#endif
static struct rc_map_list sunxi_map = {
	.map = {
		.scan    = sunxi_nec_scan,
		.size    = ARRAY_SIZE(sunxi_nec_scan),
	#ifdef CONFIG_SUNXI_KEYMAPPING_SUPPORT
		.mapping = (void *)sunxi_key_mapping,
	#endif
		.rc_type = RC_TYPE_NEC,	/* Legacy IR type */
		.name    = RC_MAP_SUNXI,
	}
};

#ifdef CONFIG_SUNXI_KEYMAPPING_SUPPORT
static void init_addr(u32 *addr, u32 addr_num)
{
	u32 *temp_addr = match_addr;

	if (addr_num > MAX_ADDR_NUM)
		addr_num = MAX_ADDR_NUM;
	match_num = addr_num;
	while (addr_num--)
		*temp_addr++ = (*addr++)&0xffffUL;
}
#endif

#ifdef CONFIG_SUNXI_KEYMAPPING_SUPPORT
int init_sunxi_ir_map_ext(void *addr, int num)
{
	init_addr(addr, num);
	return rc_map_register(&sunxi_map);
}
#else
int init_sunxi_ir_map(void)
{
	return rc_map_register(&sunxi_map);
}
#endif

void exit_sunxi_ir_map(void)
{
	rc_map_unregister(&sunxi_map);
}
