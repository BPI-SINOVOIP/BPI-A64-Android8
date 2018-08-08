#ifndef __DEV_COMPOSER_H__
#define __DEV_COMPOSER_H__

/* cmds of DISP_HWC_COMMIT */

struct sync_info{
	int fd;
	unsigned int count;
};

enum {
	HWC_NEW_CLIENT = 1,//new a timeline and get the hwc source.
	HWC_DESTROY_CLIENT,
	HWC_ACQUIRE_FENCE,
	HWC_SUBMIT_FENCE,
};
#endif /* #ifndef __DEV_COMPOSER_H__ */
