#define LOG_TAG "libcheckfile"

#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/system_properties.h>
#include <arpa/inet.h>
#include <netinet/in.h>
#include <stdlib.h>
#include <errno.h>
#include <unistd.h>
#include <dirent.h>
#include <linux/ioctl.h>

#include <cutils/log.h>
#include <utils/misc.h>

#include <fcntl.h>
#include <stdio.h>
#include <string.h>


#define MAX_LEN 96
#define PKEY 0x75
#define APP_TYPE_CPU        (0x00000001)
#define APP_TYPE_GPU        (0x00000002)
#define APP_TYPE_IO         (0x00000003)
#define APP_TYPE_CPU_GPU    (0x00000004)

static int inited = 0;
struct app_tags {
    const char *code;
    int type;
};

static const struct app_tags app[]=
{
    /* 1.com.antutu.ABenchMark */
    {"161a185b141b010001005b3437101b161d3814071e", APP_TYPE_CPU_GPU},
    /* 2.com.qihoo360.mobilesafe.opti */
    {"161a185b041c1d1a1a4643455b181a171c1910061413105b1a05011c", APP_TYPE_CPU_GPU},
    /* 3.com.qihoo360.mobilesafe.bench */
    {"161a185b041c1d1a1a4643455b181a171c1910061413105b17101b161d", APP_TYPE_CPU_GPU},
    /* 4.com.glbenchmark.glbenchmark27 */
    {"161a185b121917101b161d1814071e5b121917101b161d1814071e4742", APP_TYPE_GPU},
    /* 5.com.glbenchmark.glbenchmark30 */
    {"161a185b121917101b161d1814071e5b121917101b161d1814071e4645", APP_TYPE_GPU},
    /* 6.com.ludashi.benchmark */
    {"161a185b19001114061d1c5b17101b161d1814071e", APP_TYPE_CPU_GPU},
    /* 7.com.rightware.tdmm2v10jni.free */
    {"161a185b071c121d01021407105b01111818470344451f1b1c13071010", APP_TYPE_CPU_GPU},
    /* 8.com.futuremark.dmandroid.application */
    {"161a185b1300010007101814071e5b1118141b11071a1c115b140505191c1614011c1a1b", APP_TYPE_CPU_GPU},
    /* 9.com.greenecomputing.linpack */
    {"161a185b120710101b10161a180500011c1b125b191c1b0514161e", APP_TYPE_CPU},
    /* 10.com.tactel.electopia */
    {"161a185b0114160110195b10191016011a051c14", APP_TYPE_CPU_GPU},
    /* 11.eu.chainfire.cfbench */
    {"10005b161d141c1b131c07105b161317101b161d", APP_TYPE_CPU},
    /* 12.com.quicinc.vellamo */
    {"161a185b04001c161c1b165b0310191914181a", APP_TYPE_CPU_GPU},
    /* 13.com.aurorasoftworks.quadrant.ui.advanced */
    {"161a185b1400071a0714061a1301021a071e065b0400141107141b015b001c5b141103141b161011", APP_TYPE_CPU_GPU},
    /* 14.com.thread.jpct.bench */
    {"161a185b011d071010115b1f0516015b17101b161d", APP_TYPE_CPU_GPU},
    /* 15.se.nena.nenamark2 */
    {"06105b1b101b145b1b101b141814071e47", APP_TYPE_CPU_GPU},
    /* 16.com.epicgames.EpicCitadel */
    {"161a185b10051c1612141810065b30051c16361c0114111019", APP_TYPE_GPU},
    /* 17.com.ixia.ixchariot */
    {"161a185b1c0d1c145b1c0d161d14071c1a01", APP_TYPE_CPU},
    /* 18.com.magicandroidapps.iperf */
    {"161a185b1814121c16141b11071a1c11140505065b1c05100713", APP_TYPE_CPU},
    /* 19.com.qqfriends.com.music */
    {"161a185b040413071c101b11065b161a185b1800061c16", APP_TYPE_CPU_GPU},
    /* 20.com.powervr.Cat */
    {"161a185b051a02100703075b361401", APP_TYPE_GPU},
    /* 21.com.antutu.AbenchMark5 */
    {"161a185b141b010001005b3417101b161d3814071e40", APP_TYPE_CPU_GPU},
    /* 22.com.android.opengl.cts */
    {"161a185b141b11071a1c115b1a05101b12195b160106", APP_TYPE_CPU_GPU},
    /* 23.com.antutu.benchmark.bench64 */
    {"161a185b141b010001005b17101b161d1814071e5b17101b161d4341", APP_TYPE_CPU_GPU},
    /* 24.com.google.android.exoplayer.gts*/
    {"161a185b121a1a1219105b141b11071a1c115b100d1a0519140c10075b120106", APP_TYPE_CPU_GPU},
};

struct papp_tag {
    char name[MAX_LEN];
    int type;
};
static struct papp_tag papp[sizeof(app)/sizeof(*app)];
static int str2int(char * str)
{
   int value = 0;
   int sign = 1;
   int radix;
   if(*str == '-')
   {
      sign = -1;
      str++;
   }
   if(*str == '0' && (*(str+1) == 'x' || *(str+1) == 'X'))
   {
      radix = 16;
      str += 2;
   }
   else if(*str == '0')
   {
      radix = 8;
      str++;
   }
   else
      radix = 10;
   while(*str)
   {
      if(radix == 16)
      {
        if(*str >= '0' && *str <= '9')
           value = value * radix + *str - '0';
        else
           value = value * radix + (*str | 0x20) - 'a' + 10;
      }
      else
        value = value * radix + *str - '0';
      str++;
   }
   return sign*value;
}

static void decodeimpl(const char *code,char *name,unsigned char key)
{
    int clen = strlen(code);
    char buf[5] = {0};
    char *np = name;
    if(clen>MAX_LEN||name==0)
    {
        ALOGD("code len more than MAX_LEN");
        return;
    }
    char cy;
    for(int i=0;i<clen-1;i+=2)
    {
        sprintf(buf,"0x%c%c",code[i],code[i+1]);
        cy=str2int(buf);
        cy=cy^key;
        *np = cy;
        np++;
    }
}
void checkfile_init()
{
    ALOGD("checkfile_init");
    for(unsigned int i=0;i<sizeof(app)/sizeof(*app);i++)
    {
        decodeimpl(app[i].code, papp[i].name, PKEY);
        papp[i].type = app[i].type;
    }
}

int checkfile(const char *name)
{
    if(!inited)
    {
        checkfile_init();
        inited = 1;
    }
    if(!strncmp("com.antutu.benchmark.full",name,sizeof("com.antutu.benchmark.full"))) return  1;
    if(!strncmp("org.zeroxlab.benchmark",name,sizeof("org.zeroxlab.benchmark"))) return 1;
    if(!strncmp("com.greenecomputing.linpack",name,sizeof("com.greenecomputing.linpack"))) return 1;
    if(!strncmp("com.aurorasoftworks.quadrant.ui.professional",name,sizeof("com.aurorasoftworks.quadrant.ui.professional"))) return 1;

    for(unsigned int i=0;i<sizeof(app)/sizeof(*app);i++)
    {
        if(strstr(name,papp[i].name))
        {
            return papp[i].type;
        }
    }
    return 0;
}
