/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#include <fcntl.h>
#include <string.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <errno.h>
#include <stdio.h>

#define  LOG_TAG "AW_PowerHAL"
#include <utils/Log.h>
#include <hardware/hardware.h>
#include <hardware/power.h>
#include <cutils/properties.h>
#include <pthread.h>
#include <unistd.h>
#include "power-common.h"


#define HINT_HANDLED (0)
#define HINT_NONE (-1)


static pthread_mutex_t s_interaction_lock = PTHREAD_MUTEX_INITIALIZER;


char propdebug[100]={0};

int sustained_performance_mode = 0;
int vr_mode = 0;
int launch_mode = 0;
int low_power_mode = 0;
int benchmark_mode = 0;
int music_mode = 0;
static void sysfs_write(const char *path, char *s)
{
    char buf[64];
    int len;
    int fd = open(path, O_WRONLY);
    if (fd < 0) {
        strerror_r(errno, buf, sizeof(buf));
        ALOGE("Error opening %s: %s\n", path, buf);
        return;
    }
    len = write(fd, s, strlen(s));
    if (len < 0) {
        strerror_r(errno, buf, sizeof(buf));
        ALOGE("Error writing to %s: %s\n", path, buf);
    }
    close(fd);
}


static void power_init(struct power_module *module)
{
    //ALOGI("power_init: init success!!");
}

static void power_set_interactive(struct power_module *module, int on)
{
    //ALOGI("power_set_interactive: %d\n",on);
    //ALOGI("power_set_interactive: init success!!");
    if(!on || low_power_mode) {
        sysfs_write(ROOMAGE,ROOMAGE_SCREEN_OFF);
    #if defined A83T || A33 || A63 || VR9
        sysfs_write(GPUCOMMAND,"0");
    #endif
        benchmark_mode = 0;
    } else {
        sysfs_write(ROOMAGE,ROOMAGE_NORMAL);
    }

}

static void set_feature(struct power_module *module, feature_t feature, int state)
{
    //ALOGI("set_feature: init success!!");
    struct power_module *aw = (struct power_module *) module;
    switch(feature) {
        default:
            ALOGW("Error setting the feature, it doesn't exist %d\n", feature);
        break;
    }

}

static ssize_t get_number_of_platform_modes(struct power_module *module) {
    //ALOGI("get_number_of_platform_modes: init success!!");
    return 0;
}

static int get_platform_low_power_stats(struct power_module *module,
    power_state_platform_sleep_state_t *list) {
    //ALOGI("get_platform_low_power_stats: init success!!");
    return 0;
}

static int get_voter_list(struct power_module *module, size_t *voter) {
    ALOGI("get_voter_list: init success!!");
    return 0;
}



static void power_hint(struct power_module *module, power_hint_t hint,
                       void *data) {
        switch (hint) {
            case POWER_HINT_VSYNC:
                 //ALOGD("LAUNCH HINT:POWER_HINT_VSYNC");
                break;

            case POWER_HINT_INTERACTION:
                //ALOGD("LAUNCH HINT:POWER_HINT_INTERACTION");
                break;

            case POWER_ROTATION:
                ALOGI("==ROTATION MODE==");
                //sysfs_write(ROOMAGE,ROOMAGE_LAUNCH);
                break;

            case POWER_HINT_LOW_POWER:
                ALOGD("LAUNCH HINT:POWER_HINT_LOW_POWER");
                break;
            /* Sustained performance mode:
                      * All CPUs are capped to ~0.8GHz
                      */
            /* VR+Sustained performance mode:
                      * All CPUs are locked to ~1.2GHz
                      */
            case POWER_HINT_SUSTAINED_PERFORMANCE:
            {
                ALOGD("LAUNCH HINT:POWER_HINT_SUSTAINED_PERFORMANCE");
                pthread_mutex_lock(&s_interaction_lock);
                if (data && sustained_performance_mode == 0) {
                    if (vr_mode == 0) { // Sustained mode only.
                        if (launch_mode == 1) {
                            launch_mode = 0;
                        }
                        sysfs_write(THERMAL_EMUL_TEMP,THERMAL_CLOSE_EMUL_TEMP);
                        sysfs_write(ROOMAGE,ROOMAGE_SUSTAINED);
                    } else if (vr_mode == 1) { // Sustained + VR mode.
                        sysfs_write(THERMAL_EMUL_TEMP,THERMAL_CLOSE_EMUL_TEMP);
                        sysfs_write(ROOMAGE,ROOMAGE_VR_SUSTAINED);
                    }
                    sustained_performance_mode = 1;
                } else if (!data && sustained_performance_mode == 1) {
                    if (vr_mode == 1) { // Switch back to VR Mode.
                        sysfs_write(THERMAL_EMUL_TEMP,THERMAL_CLOSE_EMUL_TEMP);
                        sysfs_write(ROOMAGE,ROOMAGE_VR);
                    }
                    sustained_performance_mode = 0;
                }
                pthread_mutex_unlock(&s_interaction_lock);
            }
            break;

            /* VR mode:
                       * All CPUs are locked at ~1.4GHz
                       */
            case POWER_HINT_VR_MODE:
            {
                ALOGD("LAUNCH HINT:POWER_HINT_VR_MODE");
                pthread_mutex_lock(&s_interaction_lock);
                if (data && vr_mode == 0) {
                    if (sustained_performance_mode == 0) { // VR mode only.
                        if (launch_mode == 1) {
                            launch_mode = 0;
                        }
                        sysfs_write(THERMAL_EMUL_TEMP,THERMAL_CLOSE_EMUL_TEMP);
                        sysfs_write(ROOMAGE,ROOMAGE_VR);
                    } else if (sustained_performance_mode == 1) { // Sustained + VR mode.
                        sysfs_write(THERMAL_EMUL_TEMP,THERMAL_CLOSE_EMUL_TEMP);
                        sysfs_write(ROOMAGE,ROOMAGE_VR_SUSTAINED);
                    }
                    vr_mode = 1;
                } else if (!data && vr_mode == 1) {
                    if (sustained_performance_mode == 1) { // Switch back to sustained Mode.
                        sysfs_write(THERMAL_EMUL_TEMP,THERMAL_CLOSE_EMUL_TEMP);
                        sysfs_write(ROOMAGE,ROOMAGE_SUSTAINED);
                    }
                    vr_mode = 0;
                }
                pthread_mutex_unlock(&s_interaction_lock);
            }
            break;

            case POWER_HINT_LAUNCH:
            {
                if (sustained_performance_mode || vr_mode || music_mode) {
                    return;
                }
                //ALOGD("LAUNCH HINT: %s", data ? "ON" : "OFF");
                if (data && launch_mode == 0) {
                    if (benchmark_mode == 1) {
                        benchmark_mode = 0;
                    #if defined A83T || A33 || A63 || VR9
                        sysfs_write(GPUCOMMAND,"0");
                    #endif
                    }
                    launch_mode = 1;
                    sysfs_write(ROOMAGE,ROOMAGE_LAUNCH);

                    ALOGI("==LAUNCH MODE==");
                } else if (data == NULL && launch_mode == 1) {
                    if (benchmark_mode == 1) {
                        launch_mode = 0;
                        ALOGI("Activity launch benchmark_mode");
                        return;
                    }
                    launch_mode = 0;
                    sysfs_write(THERMAL_EMUL_TEMP,THERMAL_OPEN_EMUL_TEMP);
                    sysfs_write(ROOMAGE,ROOMAGE_NORMAL);
                    ALOGI("==LAUNCH_NORMAL MODE==");
                }
            }
            break;

            case POWER_HINT_HOME:
                if (sustained_performance_mode || vr_mode) {
                    return;
                }
                benchmark_mode = 0;
                music_mode = 0;
                ALOGI("==HOME MODE==");
                sysfs_write(THERMAL_EMUL_TEMP,THERMAL_OPEN_EMUL_TEMP);
                sysfs_write(ROOMAGE,ROOMAGE_HOME);

            #if defined A33 || defined A83T || defined A80
                sysfs_write(DRAMSCEN,DRAM_HOME);
            #endif

            #if defined A80
                sysfs_write(GPUFREQ,GPU_NORMAL);
            #endif

            #if defined A83T || A33 || A63 || VR9
                sysfs_write(GPUCOMMAND,"0");
            #endif

            #if defined A64 || A63 || VR9
                sysfs_write(DRAMPAUSE,"0");
            #endif
            break;

            case POWER_HINT_BOOTCOMPLETE:
                ALOGI("==BOOTCOMPLETE MODE==");
                //sysfs_write(CPU0LOCK,"1");
                sysfs_write(CPU0GOV,INTERACTIVE_GOVERNOR);
                sysfs_write(CPUHOT,"1");
            #if defined A64 || A63 || VR9
                sysfs_write(DRAMPAUSE,"0");
            #endif
                break;
            case POWER_HINT_BENCHMARK:
                if(data==NULL) {
                    ALOGD("LAUNCH HINT:data==NULL");
                    return;
                }
                if (sustained_performance_mode || vr_mode) {
                    ALOGD("LAUNCH HINT:sustained_performance_mode");
                    return;
                }
                benchmark_mode = 1;
                ALOGI("==BENCHMARK MODE==");
                /*
                int  ipid=*((int *)data);
                char  spid[20]= {0};
                sprintf(spid,"%d", ipid);
           ALOGD("LAUNCH HINT:%s", spid);
                sysfs_write(TASKS,spid);*/
                sysfs_write(THERMAL_EMUL_TEMP,THERMAL_CLOSE_EMUL_TEMP);
                sysfs_write(ROOMAGE,ROOMAGE_BENCHMARK);
            #if defined A33 || A83T || A80
                sysfs_write(DRAMSCEN,DRAM_NORMAL);
            #endif

            #if defined A80
                sysfs_write(GPUFREQ,GPU_PERF);
            #endif

            #if defined A83T || A33 || A63 || VR9
                sysfs_write(GPUCOMMAND,"1");
            #endif
            #if defined A64 || A63 || VR9
                sysfs_write(DRAMPAUSE, "1");
            #endif
                break;
            case POWER_HINT_NORMAL:
                if (sustained_performance_mode || vr_mode ||benchmark_mode || launch_mode) {
                    return;
                }
                benchmark_mode = 0;
                music_mode = 0;
                ALOGI("==NORMAL MODE==");
                sysfs_write(CPU0GOV,INTERACTIVE_GOVERNOR);
                sysfs_write(THERMAL_EMUL_TEMP,THERMAL_OPEN_EMUL_TEMP);
                sysfs_write(ROOMAGE,ROOMAGE_NORMAL);
            #if defined A33 || A83T || A80
                sysfs_write(DRAMSCEN,DRAM_NORMAL);
            #endif

            #if defined A80
                sysfs_write(GPUFREQ,GPU_NORMAL);
            #endif

            #if defined A83T || A33 || A63 || VR9
                sysfs_write(GPUCOMMAND,"0");
            #endif

            #if defined A64 || A63 || VR9
                sysfs_write(DRAMPAUSE,"0");
            #endif
                break;
            case POWER_HINT_BG_MUSIC:
                benchmark_mode = 0;
                music_mode = 1;
                ALOGI("==MUSIC MODE==");
                sysfs_write(THERMAL_EMUL_TEMP,THERMAL_OPEN_EMUL_TEMP);
                sysfs_write(ROOMAGE, ROOMAGE_MUSIC);
            #if defined A33 || A83T || A80
                sysfs_write(DRAMSCEN,DRAM_BGMUSIC);
            #endif

            #if defined A63 || VR9
 //               sysfs_write(DRAMPAUSE,"0");
            #endif

            #if defined A80
                sysfs_write(GPUFREQ,GPU_NORMAL);
            #endif

            #if defined A83T || A33 || A63 || VR9
                sysfs_write(GPUCOMMAND,"0");
            #endif

                break;
            case POWER_HINT_DISABLE_TOUCH:

            #if defined A64 || A63 || VR9
                if (data == NULL) {
                    ALOGW("Wrong parameters to control touchscreen runtime suspend!!!");
                    return;
                }
                ALOGI("==DISABLE TOUCH==");
                int state = *((int *)data);
                char tp_state[2]= {0};
                sprintf(tp_state,"%d", state);
                sysfs_write(TP_SUSPEND, tp_state);
                //ALOGI("==Touchscreen runtime suspend %s !", state == 1 ? "ON" : "OFF");
             #endif
                break;
            default:
                //ALOGD("LAUNCH HINT:default");
                break;
           }
}

static struct hw_module_methods_t power_module_methods = {
    .open = NULL,
};

struct power_module HAL_MODULE_INFO_SYM = {
    .common = {
        .tag = HARDWARE_MODULE_TAG,
        .module_api_version = POWER_MODULE_API_VERSION_0_2,
        .hal_api_version = HARDWARE_HAL_API_VERSION,
        .id = POWER_HARDWARE_MODULE_ID,
        .name = "AW Power HAL",
        .author = "The Android Open Source Project",
        .methods = &power_module_methods,
    },

    .init = power_init,
    .setInteractive = power_set_interactive,
    .powerHint = power_hint,
    .setFeature = set_feature,
    .get_number_of_platform_modes = get_number_of_platform_modes,
    .get_platform_low_power_stats = get_platform_low_power_stats,
    .get_voter_list = get_voter_list
};
