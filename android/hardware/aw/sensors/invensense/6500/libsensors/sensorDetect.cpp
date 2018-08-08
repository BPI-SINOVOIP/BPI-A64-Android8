
#define LOG_TAG "SensorDetect"
#include <hardware/sensors.h>
#include <fcntl.h>
#include <errno.h>
#include <dirent.h>
#include <math.h>
#include <poll.h>
#include <pthread.h>
#include <linux/input.h>
#include <cutils/atomic.h>
#include <cutils/log.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <sys/socket.h>
#include <cutils/misc.h>
#include <cutils/properties.h>
#include "sensors.h"

int sNumber = 0;
int flag0 = 0;
int flag1 = 0;
int found = 0;

struct sensor_info ligSensorInfo = {";", {0}, 0};
struct sensor_info proSensorInfo = {";", {0}, 0};

struct sensor_list proSensorList[] = {
    {
            "proximity", 0,
    },
      {
            "stk3x1x_ps", 0,
     },

};

struct sensor_list ligSensorList[] = {
    {
            "lightsensor", 0,
    }, {
            "stk3x1x_ls", 0,
    },

};

int getDevice(struct sensor_list list[], struct sensor_info *info,
        char buf[], char classPath[], int number )
{
    int ret = 0;

    if ((!strlen(buf)) || (list == NULL)) {
        return 0;
    }

    while(ret < number){
        if (!strncmp(buf, list[ret].name, strlen(buf))) {

            info->priData = list[ret].lsg;

            strncpy(info->sensorName, buf,strlen(buf));
            strncpy(info->classPath, classPath, strlen(classPath));
#ifdef DEBUG_SENSOR
            ALOGD("sensorName:%s,classPath:%s,lsg:%f\n",
                    info->sensorName,info->classPath, info->priData);
#endif

            return 1 ;
        }
        ret++;
    }

    return 0;

}

int searchDevice(char buf[], char classPath[])
{
    int ret = 0;
    ret = getDevice(ligSensorList, &ligSensorInfo, buf, classPath,
            ARRAY_SIZE(ligSensorList));

    if(ret == 1){
        sNumber++;
        return 1;
    }
    ret = getDevice(proSensorList, &proSensorInfo, buf, classPath,
            ARRAY_SIZE(proSensorList));

    if(ret == 1){
        sNumber++;
        return 1;
    }

    return 0;
}

int sensorsDetect(void)
{
    char *dirname =(char *) "/sys/class/input";
    char buf[256];
    char classPath[256];
    int res;
    DIR *dir;
    struct dirent *de;
    int fd = -1;
    int ret = 0;

    memset(&buf,0,sizeof(buf));

    dir = opendir(dirname);
    if (dir == NULL)
        return -1;

    while((de = readdir(dir))) {
        if (strncmp(de->d_name, "input", strlen("input")) != 0) {
            continue;
        }

        sprintf(classPath, "%s/%s", dirname, de->d_name);
        snprintf(buf, sizeof(buf), "%s/name", classPath);

        fd = open(buf, O_RDONLY);
        if (fd < 0) {
            continue;
        }

        if ((res = read(fd, buf, sizeof(buf))) < 0) {
            close(fd);
            continue;
        }
        buf[res - 1] = '\0';

#ifdef DEBUG_SENSOR
        ALOGD("buf:%s\n", buf);
#endif

        ret = searchDevice(buf, classPath);

        close(fd);
        fd = -1;
    }

    closedir(dir);

    return 0;
}
