/*
 *  mma865x.c - Linux kernel modules for 3-Axis Orientation/Motion
 *  Detection Sensor MMA8652/MMA8653
 *
 *  Copyright (C) 2010-2011 Freescale Semiconductor, Inc. All Rights Reserved.
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

#include <linux/module.h>
#include <linux/init.h>
#include <linux/slab.h>
#include <linux/i2c.h>
#include <linux/mutex.h>
#include <linux/delay.h>
#include <linux/interrupt.h>
#include <linux/irq.h>
#include <linux/hwmon-sysfs.h>
#include <linux/err.h>
#include <linux/hwmon.h>
#include <linux/input-polldev.h>
#include <linux/device.h>
#include "../init-input.h"

//#include <mach/system.h>
//#include <mach/hardware.h>

#include <linux/hrtimer.h>
#include <linux/ktime.h>

#ifdef CONFIG_HAS_EARLYSUSPEND
#include <linux/earlysuspend.h>
#endif

#if defined(CONFIG_HAS_EARLYSUSPEND) || defined(CONFIG_PM)
#include <linux/pm.h>
#endif

#define assert(expr)\
	if (!(expr)) {\
		printk(KERN_ERR "Assertion failed! %s,%d,%s,%s\n",\
			__FILE__, __LINE__, __func__, #expr);\
	}
#define MMA865X_DRV_NAME        "mma865x"
#define SENSOR_NAME             MMA865X_DRV_NAME
#define MMA865X_I2C_ADDR        0x1D
#define MMA8652_ID              0x4A
#define MMA8653_ID              0x5A

#define POLL_INTERVAL_MIN       1
#define POLL_INTERVAL_MAX       1000
#define POLL_INTERVAL           100 /* msecs */
/* if sensor is standby ,set POLL_STOP_TIME to slow down the poll */
#define POLL_STOP_TIME          200  


#define INPUT_FUZZ              32
#define INPUT_FLAT              32
#define MODE_CHANGE_DELAY_MS    100

#define MMA865X_STATUS_ZYXDR    0x08
#define MMA865X_BUF_SIZE        7

static struct device *hwmon_dev;
static struct i2c_client *mma865x_i2c_client;

enum {
	DEBUG_INIT = 1U << 0,
	DEBUG_CONTROL_INFO = 1U << 1,
	DEBUG_DATA_INFO = 1U << 2,
	DEBUG_SUSPEND = 1U << 3,
};
static u32 debug_mask = 0;
#define dprintk(level_mask, fmt, arg...)	if (unlikely(debug_mask & level_mask)) \
	printk(KERN_DEBUG fmt , ## arg)

module_param_named(debug_mask, debug_mask, int, 0644);

/* register enum for mma865x registers */
enum {
	MMA865X_STATUS = 0x00,
	MMA865X_OUT_X_MSB,
	MMA865X_OUT_X_LSB,
	MMA865X_OUT_Y_MSB,
	MMA865X_OUT_Y_LSB,
	MMA865X_OUT_Z_MSB,
	MMA865X_OUT_Z_LSB,

	MMA865X_F_SETUP = 0x09,
	MMA865X_TRIG_CFG,
	MMA865X_SYSMOD,
	MMA865X_INT_SOURCE,
	MMA865X_WHO_AM_I,
	MMA865X_XYZ_DATA_CFG,
	MMA865X_HP_FILTER_CUTOFF,

	MMA865X_PL_STATUS,
	MMA865X_PL_CFG,
	MMA865X_PL_COUNT,
	MMA865X_PL_BF_ZCOMP,
	MMA865X_P_L_THS_REG,

	MMA865X_FF_MT_CFG,
	MMA865X_FF_MT_SRC,
	MMA865X_FF_MT_THS,
	MMA865X_FF_MT_COUNT,

	MMA865X_TRANSIENT_CFG = 0x1D,
	MMA865X_TRANSIENT_SRC,
	MMA865X_TRANSIENT_THS,
	MMA865X_TRANSIENT_COUNT,

	MMA865X_PULSE_CFG,
	MMA865X_PULSE_SRC,
	MMA865X_PULSE_THSX,
	MMA865X_PULSE_THSY,
	MMA865X_PULSE_THSZ,
	MMA865X_PULSE_TMLT,
	MMA865X_PULSE_LTCY,
	MMA865X_PULSE_WIND,

	MMA865X_ASLP_COUNT,
	MMA865X_CTRL_REG1,
	MMA865X_CTRL_REG2,
	MMA865X_CTRL_REG3,
	MMA865X_CTRL_REG4,
	MMA865X_CTRL_REG5,

	MMA865X_OFF_X,
	MMA865X_OFF_Y,
	MMA865X_OFF_Z,

	MMA865X_REG_END,
};

/* Addresses to scan */
static const unsigned short normal_i2c[2] = {0x1D,I2C_CLIENT_END};
static __u32 twi_id = 0;

/* The sensitivity is represented in counts/g. In 2g mode the
sensitivity is 1024 counts/g. In 4g mode the sensitivity is 512
counts/g and in 8g mode the sensitivity is 256 counts/g.
 */
enum {
	MODE_2G = 0,
	MODE_4G,
	MODE_8G,
};

enum {
	MMA_STANDBY = 0,
	MMA_ACTIVED,
};
struct mma865x_data_axis {
	short x;
	short y;
	short z;
};
static struct mma865x_data {
	struct i2c_client * client;
	struct input_polled_dev *poll_dev;
	struct mutex data_lock;
	atomic_t active;
	int position;
	u8 chip_id;
	int mode;
	
	struct mutex init_mutex;
#ifdef CONFIG_HAS_EARLYSUSPEND
	struct early_suspend early_suspend;
#endif
#if defined(CONFIG_HAS_EARLYSUSPEND) || defined(CONFIG_PM)
	volatile int suspend_indator;
	volatile int MMA865X_REG1;
	volatile int MMA865X_DATA_REG;
#endif
	struct hrtimer hr_timer;
	struct work_struct wq_hrtimer;
	ktime_t ktime;
} g_mma865x_data;


static struct input_polled_dev *mma865x_idev;
static const unsigned short i2c_address[2] = {0x1D,0x1D};
static int i2c_num = 0;
static struct sensor_config_info gsensor_info = {
	.input_type = GSENSOR_TYPE,
};

static void mma865x_resume_events(struct work_struct *work);
static void mma865x_init_events(struct work_struct *work);
static struct workqueue_struct *mma865x_resume_wq;
static struct workqueue_struct *mma865x_init_wq;
static DECLARE_WORK(mma865x_resume_work, mma865x_resume_events);
static DECLARE_WORK(mma865x_init_work, mma865x_init_events);

#ifdef CONFIG_HAS_EARLYSUSPEND
static void mma865x_early_suspend(struct early_suspend *h);
static void mma865x_late_resume(struct early_suspend *h);
#endif

static int mma865x_device_init(struct i2c_client *client)
{
	int val = 0;
	int result1;
	int result2;
	struct mma865x_data *pdata = i2c_get_clientdata(client);

	dprintk(DEBUG_INIT, "mma865x resume init\n");
	
	mutex_lock(&pdata->init_mutex);
	result1 = i2c_smbus_write_byte_data(client, MMA865X_CTRL_REG1, 0);
	result2 = i2c_smbus_write_byte_data(client, MMA865X_XYZ_DATA_CFG,pdata->mode);	
	mutex_unlock(&pdata->init_mutex);
	if ((result1 < 0) || (result2 < 0))
		goto out;

	msleep(MODE_CHANGE_DELAY_MS);

	if ((atomic_read(&g_mma865x_data.active)) == MMA_ACTIVED) {
		mutex_lock(&g_mma865x_data.init_mutex);
	   	val = i2c_smbus_read_byte_data(mma865x_i2c_client,MMA865X_CTRL_REG1);
	   	i2c_smbus_write_byte_data(mma865x_i2c_client, MMA865X_CTRL_REG1, val|0x01);
		mutex_unlock(&g_mma865x_data.init_mutex);
	}

	dprintk(DEBUG_INIT, "mma865x resume init end\n");
	return 0;
out:
	dev_err(&client->dev, "error when init mma865x:(%d)(%d)", result1, result2);
	if (result1 < 0)
		return result1;
	else
		return result2;
}
static int mma865x_device_stop(struct i2c_client *client)
{
	u8 val;
	struct mma865x_data *pdata = i2c_get_clientdata(client);
	
	mutex_lock(&pdata->init_mutex);
	val = i2c_smbus_read_byte_data(client, MMA865X_CTRL_REG1);
	i2c_smbus_write_byte_data(client, MMA865X_CTRL_REG1,val & 0xfe);
	mutex_unlock(&pdata->init_mutex);
	return 0;
}


/**
 * gsensor_detect - Device detection callback for automatic device creation
 * return value:  
 *                    = 0; success;
 *                    < 0; err
 */

static int gsensor_detect(struct i2c_client *client, struct i2c_board_info *info)
{
	struct i2c_adapter *adapter = client->adapter;
	int ret;

	dprintk(DEBUG_INIT, "%s enter \n", __func__);
	
	if (!i2c_check_functionality(adapter, I2C_FUNC_SMBUS_BYTE_DATA))
            return -ENODEV;
            
	if (twi_id == adapter->nr) {
		for (i2c_num = 0; i2c_num < (sizeof(i2c_address)/sizeof(i2c_address[0]));i2c_num++) {	    
			client->addr = i2c_address[i2c_num];
			dprintk(DEBUG_INIT, "%s:addr= 0x%x,i2c_num:%d\n",__func__,client->addr,i2c_num);
			ret = i2c_smbus_read_byte_data(client,MMA865X_WHO_AM_I);
			dprintk(DEBUG_INIT, "Read ID value is :%d",ret);
			if ((ret &0x00FF) == MMA8652_ID) {
				dprintk(DEBUG_INIT, "Freescale Sensortec Device detected!\n" );
				strlcpy(info->type, SENSOR_NAME, I2C_NAME_SIZE);
				return 0; 
    
			}else if ((ret &0x00FF) == MMA8653_ID) {         	  	
				dprintk(DEBUG_INIT, "Freescale Sensortec Device detected!\n" \
    				"mma8653 registered I2C driver!\n");  
				strlcpy(info->type, SENSOR_NAME, I2C_NAME_SIZE);
				return 0; 
			}                                                                     
		}
        
		pr_info("%s:Freescale Sensortec Device not found, \
		maybe the other gsensor equipment! \n",__func__);
		return -ENODEV;
	} else {
		return -ENODEV;
	}
}
			
static int mma865x_read_data(short *x, short *y, short *z) {
	u8	tmp_data[7];



	if (i2c_smbus_read_i2c_block_data(mma865x_i2c_client,MMA865X_OUT_X_MSB,7,tmp_data) < 7) {
		dev_err(&mma865x_i2c_client->dev, "i2c block read failed\n");
			return -3;
	}

	*x = ((tmp_data[0] << 8) & 0xff00) | tmp_data[1];
	*y = ((tmp_data[2] << 8) & 0xff00) | tmp_data[3];
	*z = ((tmp_data[4] << 8) & 0xff00) | tmp_data[5];

	*x = (short)(*x) >> 4;
	*y = (short)(*y) >> 4;
	*z = (short)(*z) >> 4;


	/*if (mma_status.mode == MODE_4G){
		(*x)=(*x)<<1;
		(*y)=(*y)<<1;
		(*z)=(*z)<<1;
	}
	else if (mma_status.mode == MODE_8G){
		(*x)=(*x)<<2;
		(*y)=(*y)<<2;
		(*z)=(*z)<<2;
	}*/

	return 0;
}

static void report_abs(void)
{
	short x,y,z;
	int result;
	
	
	result=i2c_smbus_read_byte_data(mma865x_i2c_client, MMA865X_STATUS);
	if (!(result & 0x08)) {
		dprintk(DEBUG_DATA_INFO, "mma865x check new data\n");
		return;		/* wait for new data */
	}

	if (mma865x_read_data(&x,&y,&z) != 0) {
		dprintk(DEBUG_DATA_INFO, "mma865x data read failed\n");
		return;
	}
	dprintk(DEBUG_DATA_INFO, "x= 0x%hx, y = 0x%hx, z = 0x%hx\n", x, y, z);
	input_report_abs(mma865x_idev->input, ABS_X, x);
	input_report_abs(mma865x_idev->input, ABS_Y, y);
	input_report_abs(mma865x_idev->input, ABS_Z, z);
	input_sync(mma865x_idev->input);
}

static void mma865x_dev_poll(struct input_polled_dev *dev)
{
#if 0
	report_abs();
#endif
}

static ssize_t mma865x_enable_show(struct device *dev,
				   struct device_attribute *attr, char *buf)
{
	struct input_polled_dev *poll_dev = dev_get_drvdata(dev);
	struct mma865x_data *pdata = (struct mma865x_data *)(poll_dev->private);
	struct i2c_client *client = pdata->client;
	u8 val;
	int enable;
	
	mutex_lock(&pdata->data_lock);
	val = i2c_smbus_read_byte_data(client, MMA865X_CTRL_REG1);  
	if((val & 0x01) && (atomic_read(&pdata->active) == MMA_ACTIVED))
		enable = 1;
	else
		enable = 0;
	mutex_unlock(&pdata->data_lock);
	return sprintf(buf, "%d\n", enable);
}

static ssize_t mma865x_enable_store(struct device *dev,
				    struct device_attribute *attr,
				    const char *buf, size_t count)
{
	struct input_polled_dev *poll_dev = dev_get_drvdata(dev);
	struct mma865x_data *pdata = (struct mma865x_data *)(poll_dev->private);
	struct i2c_client *client = pdata->client;
	int ret;
	unsigned long enable;
	u8 val = 0;
	enable = simple_strtoul(buf, NULL, 10);    
	mutex_lock(&pdata->data_lock);
	enable = (enable > 0) ? 1 : 0;
	if (enable && (atomic_read(&pdata->active) == MMA_STANDBY)) {
		mutex_lock(&pdata->init_mutex);
		val = i2c_smbus_read_byte_data(client,MMA865X_CTRL_REG1);
		ret = i2c_smbus_write_byte_data(client, MMA865X_CTRL_REG1, val|0x01);
		mutex_unlock(&pdata->init_mutex);
		if(!ret) {
#if defined(CONFIG_HAS_EARLYSUSPEND) || defined(CONFIG_PM)
			if (pdata->suspend_indator == 0)
#endif
			#if 0
				mma865x_idev->input->open(mma865x_idev->input);
			#else
				hrtimer_start(&g_mma865x_data.hr_timer, g_mma865x_data.ktime, HRTIMER_MODE_REL);
			#endif
			atomic_set(&pdata->active, MMA_ACTIVED);

			dprintk(DEBUG_CONTROL_INFO, "mma enable setting active \n");
		} else
			dprintk(DEBUG_CONTROL_INFO, "mma enable setting active failed\n");				
		
	}
	else if (enable == 0  && (atomic_read(&pdata->active) == MMA_ACTIVED)) {
		mutex_lock(&pdata->init_mutex);
		val = i2c_smbus_read_byte_data(client,MMA865X_CTRL_REG1);
		ret = i2c_smbus_write_byte_data(client, MMA865X_CTRL_REG1,val & 0xFE);
		mutex_unlock(&pdata->init_mutex);
		if (!ret) {
			#if 0
				mma865x_idev->input->close(mma865x_idev->input);
			#else
				hrtimer_cancel(&g_mma865x_data.hr_timer);
			#endif
			atomic_set(&pdata->active, MMA_STANDBY);

			dprintk(DEBUG_CONTROL_INFO, "mma enable setting inactive \n");
		} else
			dprintk(DEBUG_CONTROL_INFO, "mma enable setting inactive failed\n");
	}
	mutex_unlock(&pdata->data_lock);
	return count;
}



static ssize_t mma865x_delay_store(struct device *dev,struct device_attribute *attr,
		const char *buf, size_t count)
{
	unsigned long data;
	int error;

	error = kstrtoul(buf, 10, &data);
	if (error)
		return error;
	if (data > POLL_INTERVAL_MAX)
		data = POLL_INTERVAL_MAX;
	mma865x_idev->poll_interval = data;
	g_mma865x_data.ktime = ktime_set(0, data * NSEC_PER_MSEC);
	return count;
}

static DEVICE_ATTR(enable, 0664,
		   mma865x_enable_show, mma865x_enable_store);
static DEVICE_ATTR(delay, 0664,
		   NULL, mma865x_delay_store);


static struct attribute *mma865x_attributes[] = {
	&dev_attr_enable.attr,
	&dev_attr_delay.attr,
	NULL
};

static const struct attribute_group mma865x_attr_group = {
	.attrs = mma865x_attributes,
};

static void mma865x_init_events (struct work_struct *work)
{
	mma865x_device_init(mma865x_i2c_client);
}

static void wq_func_hrtimer(struct work_struct *work)
{
	report_abs();
}

static enum hrtimer_restart my_hrtimer_callback(struct hrtimer *timer)
{
	schedule_work(&g_mma865x_data.wq_hrtimer);
	hrtimer_forward_now(&g_mma865x_data.hr_timer, g_mma865x_data.ktime);
	return HRTIMER_RESTART;
}

static int mma865x_probe(struct i2c_client *client,
				   const struct i2c_device_id *id)
{
	int result,chip_id;
	struct input_dev *idev;
	struct mma865x_data *pdata = &g_mma865x_data;
	struct i2c_adapter *adapter;

	dprintk(DEBUG_INIT, "mma865x probe i2c address is %d \n",i2c_address[i2c_num]);
	client->addr =i2c_address[i2c_num];
	mma865x_i2c_client = client;
	adapter = to_i2c_adapter(client->dev.parent);
	result = i2c_check_functionality(adapter,
					 I2C_FUNC_SMBUS_BYTE |
					 I2C_FUNC_SMBUS_BYTE_DATA);
	assert(result);
	
	chip_id = i2c_smbus_read_byte_data(client, MMA865X_WHO_AM_I);

	if (chip_id != MMA8652_ID && chip_id != MMA8653_ID) {
		dev_err(&client->dev,
			"read chip ID 0x%x is not equal to 0x%x or 0x%x!\n",
			result, MMA8652_ID, MMA8653_ID);
		result = -EINVAL;
		goto err_out;
	}

	hwmon_dev = hwmon_device_register(&client->dev);
	assert(!(IS_ERR(hwmon_dev)));

	/* Initialize the MMA865X chip */
	pdata->client = client;
	pdata->chip_id = chip_id;
	pdata->mode = MODE_2G;
	mutex_init(&pdata->data_lock);
	i2c_set_clientdata(client,pdata);

	mutex_init(&pdata->init_mutex);

	mma865x_init_wq = create_singlethread_workqueue("mma865x_init");
	if (mma865x_init_wq == NULL) {
		printk("create mma865x_init_wq fail!\n");
		return -ENOMEM;
	}

	/* Initialize the MMA8452 chip */
	atomic_set(&pdata->active, MMA_STANDBY);
	queue_work(mma865x_init_wq, &mma865x_init_work);
	
	mma865x_idev = input_allocate_polled_device();
	if (!mma865x_idev) {
		result = -ENOMEM;
		dev_err(&client->dev, "alloc poll device failed!\n");
		goto err_alloc_poll_device;
	}
	mma865x_idev->poll = mma865x_dev_poll;
	mma865x_idev->poll_interval = POLL_STOP_TIME;
	mma865x_idev->poll_interval_max = POLL_INTERVAL_MAX;
	mma865x_idev->private = pdata;
	idev = mma865x_idev->input;
	idev->name = MMA865X_DRV_NAME;
	//idev->uniq = mma865x_id2name(pdata->chip_id);
	idev->id.bustype = BUS_I2C;
	idev->evbit[0] = BIT_MASK(EV_ABS);
	input_set_abs_params(idev, ABS_X, -0x7fff, 0x7fff, INPUT_FUZZ, INPUT_FLAT);
	input_set_abs_params(idev, ABS_Y, -0x7fff, 0x7fff, INPUT_FUZZ, INPUT_FLAT);
	input_set_abs_params(idev, ABS_Z, -0x7fff, 0x7fff, INPUT_FUZZ, INPUT_FLAT);
	result = input_register_polled_device(mma865x_idev);
	if (result) {
		dev_err(&client->dev, "register poll device failed!\n");
		goto err_register_polled_device;
	}
	mma865x_idev->input->close(mma865x_idev->input);
	result = sysfs_create_group(&mma865x_idev->input->dev.kobj, &mma865x_attr_group);
	if (result) {
		dev_err(&client->dev, "create device file failed!\n");
		result = -EINVAL;
		goto err_create_sysfs;
	}
	
	pdata->client  = client;
	pdata->poll_dev = mma865x_idev;
	i2c_set_clientdata(client, pdata);

	
	mma865x_resume_wq = create_singlethread_workqueue("mma865x_resume");
	if (mma865x_resume_wq == NULL) {
		printk("create mma865x_resume_wq fail!\n");
		return -ENOMEM;
	}

#ifdef CONFIG_HAS_EARLYSUSPEND
	g_mma865x_data.early_suspend.level = EARLY_SUSPEND_LEVEL_BLANK_SCREEN + 3;
	g_mma865x_data.early_suspend.suspend = mma865x_early_suspend;
	g_mma865x_data.early_suspend.resume = mma865x_late_resume;
	register_early_suspend(&g_mma865x_data.early_suspend);
	g_mma865x_data.suspend_indator = 0;
#endif
	hrtimer_init(&g_mma865x_data.hr_timer, CLOCK_MONOTONIC, HRTIMER_MODE_REL);
	g_mma865x_data.hr_timer.function = my_hrtimer_callback;
	INIT_WORK(&g_mma865x_data.wq_hrtimer, wq_func_hrtimer);

	dprintk(DEBUG_INIT, "mma865x device driver probe successfully\n");
	return 0;
err_create_sysfs:
err_register_polled_device:
	input_free_polled_device(mma865x_idev);
err_alloc_poll_device:
err_out:	
	return result;
}
static int mma865x_remove(struct i2c_client *client)
{	
	mma865x_device_stop(mma865x_i2c_client);
#ifdef CONFIG_HAS_EARLYSUSPEND	
	unregister_early_suspend(&g_mma865x_data.early_suspend);
#endif
	cancel_work_sync(&mma865x_resume_work);
	destroy_workqueue(mma865x_resume_wq);
	sysfs_remove_group(&mma865x_idev->input->dev.kobj, &mma865x_attr_group);
	#if 0
		mma865x_idev->input->close(mma865x_idev->input);
	#else
		hrtimer_cancel(&g_mma865x_data.hr_timer);
	#endif
	input_unregister_polled_device(mma865x_idev);
	input_free_polled_device(mma865x_idev);
	cancel_work_sync(&mma865x_init_work);
	destroy_workqueue(mma865x_init_wq);
	hwmon_device_unregister(hwmon_dev);
	i2c_set_clientdata(mma865x_i2c_client, NULL);

	return 0;
}

static void mma865x_resume_events (struct work_struct *work)
{
#if defined(CONFIG_HAS_EARLYSUSPEND) || defined(CONFIG_PM)	
	int val = 0;
	int result1 = 0;
	int result2 = 0;

	dprintk(DEBUG_INIT, "mma865x device init\n");
	
	mutex_lock(&g_mma865x_data.init_mutex);
	result1 = i2c_smbus_write_byte_data(mma865x_i2c_client, MMA865X_CTRL_REG1, 0); 
	result2 = i2c_smbus_write_byte_data(mma865x_i2c_client, MMA865X_XYZ_DATA_CFG,
						g_mma865x_data.MMA865X_DATA_REG);
	mutex_unlock(&g_mma865x_data.init_mutex);
	if ((result1 < 0) || (result2 < 0))
		printk("error when resume mma865x:(%d)(%d)", result1, result2);
		
	msleep(MODE_CHANGE_DELAY_MS);

	if ((atomic_read(&g_mma865x_data.active)) == MMA_ACTIVED) {
		mutex_lock(&g_mma865x_data.init_mutex);
	   	val = i2c_smbus_read_byte_data(mma865x_i2c_client,MMA865X_CTRL_REG1);
	   	i2c_smbus_write_byte_data(mma865x_i2c_client, MMA865X_CTRL_REG1, val|0x01);
		mutex_unlock(&g_mma865x_data.init_mutex);
		dprintk(DEBUG_SUSPEND, "mma865x active\n");
	}
	#if 0
		mma865x_idev->input->open(mma865x_idev->input);
	#else
		hrtimer_start(&g_mma865x_data.hr_timer, g_mma865x_data.ktime, HRTIMER_MODE_REL);
	#endif
	dprintk(DEBUG_INIT, "mma865x device init end\n");
	return;
#endif
}

#ifdef CONFIG_HAS_EARLYSUSPEND
static void mma865x_early_suspend(struct early_suspend *h)
{
	int err;
	struct mma865x_data *pdata =
		container_of(h, struct mma865x_data, early_suspend);
	
	dprintk(DEBUG_SUSPEND, "mma865x early suspend\n");
	pdata->suspend_indator = 1;

	flush_workqueue(mma865x_resume_wq);

	pdata->MMA865X_DATA_REG = i2c_smbus_read_byte_data(mma865x_i2c_client,
							MMA865X_XYZ_DATA_CFG);
	#if 0
		mma865x_idev->input->close(mma865x_idev->input);
	#else
		hrtimer_cancel(&g_mma865x_data.hr_timer);
	#endif
		if ((atomic_read(&pdata->active)) == MMA_ACTIVED)
			mma865x_device_stop(mma865x_i2c_client);

		if (gsensor_info.sensor_power_ldo != NULL) {
			err = regulator_disable(gsensor_info.sensor_power_ldo);
			if (err)
				printk("bma865x power down failed\n");
		/* msleep(500); */
	}

	return; 
}

static void mma865x_late_resume(struct early_suspend *h)
{
	int val = 0;

	struct mma865x_data *pdata =
		container_of(h, struct mma865x_data, early_suspend);
	
	dprintk(DEBUG_SUSPEND, "mma865x late resume %d\n", (atomic_read(&pdata->active)));
		if (gsensor_info.sensor_power_ldo != NULL) {
		val = regulator_enable(gsensor_info.sensor_power_ldo);
		msleep(100);
		}
	
		if ((atomic_read(&pdata->active)) == MMA_ACTIVED) {
			mutex_lock(&pdata->init_mutex);
	   		val = i2c_smbus_read_byte_data(mma865x_i2c_client,MMA865X_CTRL_REG1);
	   		i2c_smbus_write_byte_data(mma865x_i2c_client, MMA865X_CTRL_REG1, val|0x01);
			mutex_unlock(&pdata->init_mutex);
			dprintk(DEBUG_SUSPEND, "mma865x active\n");
		}
			#if 0
				mma865x_idev->input->open(mma865x_idev->input);
			#else
				hrtimer_start(&g_mma865x_data.hr_timer, g_mma865x_data.ktime, HRTIMER_MODE_REL);
			#endif
		queue_work(mma865x_resume_wq, &mma865x_resume_work);
	pdata->suspend_indator = 0;

	return;
	  
}
#else
#ifdef CONFIG_PM
static int mma865x_resume(struct device *dev)
{
	
	struct i2c_client *client =  to_i2c_client(dev);
	struct mma865x_data *pdata = i2c_get_clientdata(client);
	int val = 0;
	dprintk(DEBUG_SUSPEND, "mma865x resume %d\n", (atomic_read(&pdata->active)));

	if (gsensor_info.sensor_power_ldo != NULL) {
		val = regulator_enable(gsensor_info.sensor_power_ldo);
		msleep(100);
		}

		if ((atomic_read(&pdata->active)) == MMA_ACTIVED) {
			mutex_lock(&pdata->init_mutex);
	   		val = i2c_smbus_read_byte_data(mma865x_i2c_client,MMA865X_CTRL_REG1);
	   		i2c_smbus_write_byte_data(mma865x_i2c_client, MMA865X_CTRL_REG1, val|0x01);
			mutex_unlock(&pdata->init_mutex);
			dprintk(DEBUG_SUSPEND, "mma865x active\n");
		}
			#if 0
				mma865x_idev->input->open(mma865x_idev->input);
			#else
				hrtimer_start(&g_mma865x_data.hr_timer, g_mma865x_data.ktime, HRTIMER_MODE_REL);
			#endif
		queue_work(mma865x_resume_wq, &mma865x_resume_work);

	pdata->suspend_indator = 0;
	
	return 0;
}

static int mma865x_suspend(struct device *dev, pm_message_t mesg)
{
	
	struct i2c_client *client =  to_i2c_client(dev);
	struct mma865x_data *pdata = i2c_get_clientdata(client);
	int err = 0;
	dprintk(DEBUG_SUSPEND, "mma865x suspend\n");
	pdata->suspend_indator = 1;

	flush_workqueue(mma865x_resume_wq);

		pdata->MMA865X_DATA_REG = i2c_smbus_read_byte_data(mma865x_i2c_client,
							MMA865X_XYZ_DATA_CFG);
		#if 0
			mma865x_idev->input->close(mma865x_idev->input);
		#else
			hrtimer_cancel(&g_mma865x_data.hr_timer);
		#endif
		if ((atomic_read(&pdata->active)) == MMA_ACTIVED)
			mma865x_device_stop(mma865x_i2c_client);

		if (gsensor_info.sensor_power_ldo != NULL) {
			err = regulator_disable(gsensor_info.sensor_power_ldo);
			/* msleep(500); */
		}
	return err;
}
#endif
#endif

static const struct i2c_device_id mma865x_id[] = {
	{MMA865X_DRV_NAME, 0},
	{ }
};
MODULE_DEVICE_TABLE(i2c, mma865x_id);

static const struct of_device_id mma865x_of_match[] = {
	{.compatible = "allwinner,sun8i-gsensor-para"},
	{},
};

static struct i2c_driver mma865x_driver = {
	.class = I2C_CLASS_HWMON,
	.driver = {
		.name	= MMA865X_DRV_NAME,
		.owner	= THIS_MODULE,
		.of_match_table = mma865x_of_match,
		.suspend = mma865x_suspend,
		.resume = mma865x_resume,
	},
	.probe	= mma865x_probe,
	.remove	= mma865x_remove,
	.id_table = mma865x_id,
	.detect = gsensor_detect,
	.address_list = normal_i2c,
};

static int __init mma865x_init(void)
{
	int err;
	if (input_sensor_startup(&(gsensor_info.input_type))) {
		printk("%s: gsensor-bma2x2 fetch paras err.\n", __func__);
		return -1;
	} else{
		err = input_sensor_init(&(gsensor_info.input_type));
		if (0 != err) {
			printk("%s: gsensor-mma865x initialize platform err.\n", __func__);
		}
	}
	twi_id = gsensor_info.twi_id;

	if (gsensor_info.sensor_power_ldo != NULL) {
		err = regulator_enable(gsensor_info.sensor_power_ldo);
		msleep(500);
		}


	return i2c_add_driver(&mma865x_driver);
}


static void __exit mma865x_exit(void)
{
	dprintk(DEBUG_INIT, "remove mma865x i2c driver.\n");
	i2c_del_driver(&mma865x_driver);
	input_set_power_enable(&(gsensor_info.input_type),0);
}

MODULE_AUTHOR("Freescale Semiconductor, Inc.");
MODULE_DESCRIPTION("MMA865X 3-Axis Orientation/Motion Detection Sensor driver");
MODULE_LICENSE("GPL");

module_init(mma865x_init);
module_exit(mma865x_exit);
