#include <stdio.h>
#include <stdlib.h>
#include <utils/Log.h>
#include <fcntl.h>
#include <string.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <errno.h>
#include <stdio.h>
#include <cutils/properties.h>

#include "power-common.h"

static  void sysfs_write(const char *path, char *s)
{
    char buf[64];
    int len;
    int fd = open(path, O_WRONLY);
    if (fd < 0)
    {
        strerror_r(errno, buf, sizeof(buf));
        //ALOGE("Error opening %s: %s\n", path, buf);
        return;
    }
    len = write(fd, s, strlen(s));
    if (len < 0)
    {
        strerror_r(errno, buf, sizeof(buf));
        //ALOGE("Error writing to %s: %s\n", path, buf);
    }
    close(fd);
}

void  command_power_hint(int hint, void *data)
{
    switch (hint)
    {
        case  POWER_HINT_BOOTCOMPLETE:
           // ALOGI("++BOOTCOMPLETE MODE++");
            sysfs_write(CPU0LOCK,"1");
            sysfs_write(CPU0GOV,INTERACTIVE_GOVERNOR);
            sysfs_write(CPUHOT,"1");
        #if defined A64
            sysfs_write(DRAMPAUSE,"0");
        #endif
            break;
        case  POWER_HINT_BENCHMARK:
            if(data==NULL)
                break;
            //ALOGI("++BENCHMARK MODE++");
            char  *spid=(char *)data;
            sysfs_write(TASKS,spid);
            sysfs_write(ROOMAGE,ROOMAGE_BENCHMARK);

        #if defined A33 || A83T || A80
            sysfs_write(DRAMSCEN,DRAM_NORMAL);
        #endif

        #if defined A80
            sysfs_write(GPUFREQ,GPU_PERF);
        #endif

        #if defined A83T || A33
            sysfs_write(DRAMSCEN,DRAM_NORMAL);
            sysfs_write(GPUCOMMAND,"1");
        #endif
        #if defined A64
            sysfs_write(DRAMPAUSE, "1");
        #endif
            break;
        case POWER_HINT_NORMAL:
            //ALOGI("++NORMAL MODE++");
            sysfs_write(ROOMAGE,ROOMAGE_NORMAL);
        #if defined A33 || A83T || A80
            sysfs_write(DRAMSCEN,DRAM_NORMAL);
        #endif

        #if defined A83T || A33
            sysfs_write(DRAMSCEN,DRAM_NORMAL);
            sysfs_write(GPUCOMMAND,"0");
        #endif

        #if defined A80
            sysfs_write(GPUFREQ,GPU_NORMAL);
        #endif

        #if defined A64
            sysfs_write(TP_SUSPEND,"1");
            sysfs_write(DRAMPAUSE,"0");
        #endif


            break;
        case POWER_HINT_BG_MUSIC:
            //ALOGI("++MUSIC MODE++");
            sysfs_write(ROOMAGE, ROOMAGE_NORMAL);
        #if defined A33 || A83T || A80
            sysfs_write(DRAMSCEN,DRAM_BGMUSIC);
        #endif

        #if defined A80
            sysfs_write(GPUFREQ,GPU_NORMAL);
        #endif

        #if defined A83T || A33
            sysfs_write(GPUCOMMAND,"0");
        #endif
        #if  defined A64
            sysfs_write(TP_SUSPEND,"0");
        #endif
            break;
        default:
            break;
    }
 }


int main(int argc, char **argv)
{
    char propval[100]={0};
    if(argc<2 )
    {
        printf("missing arguments\n");
        return 0;
    }
    if(!strcmp(argv[1],"resume"))
    {
        if(argc!= 2)
        {
            printf("Usage:resume");
            return -1;
        }
        printf("quit debug mode!\n");
        property_set("sys.p_debug","false");
        property_set("sys.p_bootcomplete","true");
        property_set("sys.p_benchmark","true");
        property_set("sys.p_normal","true");
        property_set("sys.p_music","true");
        return 0;
    }

    if(!strcmp(argv[1],"enter"))
    {
        if(!strcmp("benchmark", argv[2]))
        {
            if(argc!=4)
            {
                printf("Usage:enter benchmark [pid0]\n");
                return -1;
            }
            property_get("sys.p_benchmark", propval,"false");
            if(!strcmp(propval,"false"))
            {
                printf("benchmark mode has been disabled!\n");
                return -1;
            }
            property_set("sys.p_debug","true");
            command_power_hint(POWER_HINT_BENCHMARK, argv[3]);
            printf("benchmark succeed!\n");
        }

        else if(!strcmp("normal",argv[2]))
        {
            if(argc!=3)
            {
                printf("Usage:enter normal\n");
                return -1;
            }
            property_get("sys.p_normal",propval,"false");
            if(!strcmp(propval,"false"))
            {
                printf("normal mode has been disabled!\n");
                return -1;
            }
            property_set("sys.p_debug","true");
            command_power_hint(POWER_HINT_NORMAL, "1");
            printf("normal succeed!\n");
        }
        else if(!strcmp("music",argv[2]))
        {
            if(argc!=3)
            {
                printf("Usage:enter music\n");
                return -1;
            }
            property_get("sys.p_music" , propval,"false");
            if(!strcmp(propval,"false"))
            {
                printf("music mode has been disabled!\n");
                return -1;
            }
            property_set("sys.p_debug","true");
            command_power_hint(POWER_HINT_BG_MUSIC, "2");
            printf("music succeed!\n");
        }

        else if(!strcmp("bootcomplete",argv[2]))
        {
            if(argc!=3)
            {
                printf("Usage:enter bootcomplete\n");
                return -1;
            }
            property_get("sys.p_bootcomplete",propval,"false");
            if(!strcmp(propval,"false"))
            {
                printf("benchmark mode has been disabled!\n");
                return -1;
            }
            property_set("sys.p_debug","true");
            command_power_hint(POWER_HINT_BOOTCOMPLETE,"false");
            printf("bootcomplete succeed!\n");
        }
        else
        {
            printf("un-defined command:%s!\n", argv[1]);
            return -1;
        }
    }

    else if(!strcmp(argv[1],"enable"))
    {
        if(!strcmp("benchmark", argv[2]))
        {
            if(argc!=3)
            {
                printf("Usage:enable benchmark");
                return -1;
            }
            property_set("sys.p_benchmark","true");
            printf("enable benchmark succeed!\n");
        }
        else if(!strcmp("normal", argv[2]))
        {
            if(argc!=3)
            {
                printf("Usage:enable normal\n");
                return -1;
            }
            property_set("sys.p_normal","true");
            printf("enable normal  succeed!\n");
        }

        else if(!strcmp("music", argv[2]))
        {
            if(argc!=3)
            {
                printf("Usage:enable music\n");
                return -1;
            }
            property_set("sys.p_music","true");
            printf("enable music succeed!\n");
        }

        else  if(!strcmp("bootcomplete", argv[2]))
        {
            if(argc!=3)
            {
                printf("Usage:enable bootcomplete\n");
                return -1;
            }
            property_set("sys.p_bootcomplete","true");
            printf("enable bootcomplete succeed!\n");
        }
        else
        {
            printf("un-defined command:%s!\n",argv[1] );
            return -1;
        }
    }


    else if(!strcmp(argv[1],"disable"))
    {
        if(!strcmp("benchmark", argv[2]))
        {
            if(argc!=3)
            {
                printf("Usage:disable benchmark");
                return -1;
            }
            property_set("sys.p_benchmark","false");
            printf("disable benchmark succeed!\n");
        }
        else if(!strcmp("normal", argv[2]))
        {
            if(argc!=3)
            {
                printf("Usage:disable normal\n");
                return -1;
            }
            property_set("sys.p_normal","false");
            printf("disable normal succeed!\n");
        }
        else if(!strcmp("music", argv[2]))
        {
            if(argc!=3)
            {
                printf("Usage:disable music\n");
                return -1;
            }
            property_set("sys.p_music","false");
            printf("disable music succeed!\n");
        }

        else  if(!strcmp("bootcomplete", argv[2]))
        {
            if(argc!=3)
            {
                printf("Usage:disable bootcomplete\n");
                return -1;
            }
            property_set("sys.p_bootcomplete","false");
            printf("disable bootcomplete succeed!\n");
        }
        else
        {
            printf("un-defined command:%s\n", argv[1]);
            return -1;
        }
    }

    else
    {
        printf("un-defined command:%s\n",argv[1]);
        return -1;
    }
    return 0;
}

