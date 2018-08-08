/*
 *
 */ 
#define LOG_TAG "audio_hw_primary_homlet"
#define LOG_NDEBUG 0
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <linux/netlink.h>
#include <sys/select.h>
#include <signal.h>
#include <asm/types.h>
#include <errno.h>
#include <pthread.h>
#include <tinyalsa/asoundlib.h>
#include <cutils/log.h>

//#include "list.h"

/* Netlink allow 32 bus max */
#define	NETLINK_PCM_SUNXI	31
/* maximum payload size, enough pcm xrun communcations */
#define	MAX_PAYLOAD	1024

static struct sockaddr_nl src_addr, dest_addr;
static struct nlmsghdr *nlhdr = NULL;
static struct iovec iov;
static struct msghdr msg;
volatile sig_atomic_t running = 1;

#define SUNXI_MAX_CARDS		4
#define AUDIO_MAP_CNT  16
#define NAME_LEN 20
#define PATH_LEN 30

typedef struct name_map_t
{
    char name[NAME_LEN];
    int card_num;
    int active;
}name_map;

static name_map audio_name_map[AUDIO_MAP_CNT] =
{
    {"sndacx00codec",        -1,        1},
    {"sndhdmi",              -1,        1},
    {"sndspdif",             -1,        0},
    {"snddaudio2",           -1,        1},
};
int int_map()
{
    char path[PATH_LEN] = {0};
    char name[NAME_LEN] = {0};
    int index = 0;
    int ret = -1;
    int fd = -1;
    for(; index < AUDIO_MAP_CNT; index++)
    {
        memset(path, 0, PATH_LEN);
        memset(name, 0, NAME_LEN);
        sprintf(path, "/proc/asound/card%d/id", index);

        fd = open(path, O_RDONLY, 0);
        if(fd < 0)
        {return -1;}

        ret = read(fd, name, NAME_LEN);
        if(ret < 0)
        {return -2;};

        int innerindex = 0;
        for(; innerindex < AUDIO_MAP_CNT; innerindex++)
        {
            if((strlen(audio_name_map[innerindex].name) != 0) && (strncmp(audio_name_map[innerindex].name, name, strlen(audio_name_map[innerindex].name)) == 0))
            {
                audio_name_map[innerindex].card_num = index;
            }
            else
            {}
        }
        close(fd);
    }
    return 1;
}

int ret_codec_card()
{
    return audio_name_map[0].card_num;
}
int ret_hdmi_card()
{
    return audio_name_map[1].card_num;
}
int ret_daudio2_card()
{
    return audio_name_map[3].card_num;
}

/* plat configure, should be using as config load dymic */
int pcm_plat[SUNXI_MAX_CARDS] = {-1, -1, -1, -1};

void init_pcm_plat()
{
    pcm_plat[1] = ret_hdmi_card();
    pcm_plat[3] = ret_codec_card();
    pcm_plat[2] = ret_daudio2_card();
}

struct pcm *aw_pcm[2 * SUNXI_MAX_CARDS];

static int pcm_thread_process(int card, int dir, int open, struct pcm_config *config)
{
    int device = 0;
    struct pcm *pcm;
    int prepare_error;
    /* if open */
    if (open) 
    {
        pcm = pcm_open(card, device, dir, config);
        if (!pcm || !pcm_is_ready(pcm)) {
            fprintf(stderr, "Unable to open PCM device %d (%s)\n",
                device, pcm_get_error(pcm));
            return 0;
        }
		
        prepare_error = pcm_prepare(pcm);
        if (prepare_error) {
            pcm_close(pcm);
            return prepare_error;
        }
        if (dir)
            aw_pcm[2 * card] = pcm;
		else
            aw_pcm[2 * card + 1] = pcm;
    } else {
        if (dir) {
            pcm = aw_pcm[2 * card];
            aw_pcm[2 * card] = NULL;
        }
        else {
            pcm = aw_pcm[2 * card + 1];
            aw_pcm[2 * card + 1] = NULL;
        }
        if(pcm != NULL)
            pcm_close(pcm);
    }
    return 0;
}

static void pcm_event_process(char *buf)
{
    char *widget_str;
    char *tmp_str;
    int count = 0;
    int opt_card, opt_open, opt_dir;
    int opt_format;
    int num;
    struct pcm_config config;

    printf("%s\n", buf);

    tmp_str = strtok(buf, ":=/");

    while (tmp_str != NULL)
    {
        count ++;
        switch(count) {
        case 2:
            widget_str = tmp_str;
            if (strncmp(widget_str, "I2S", 3) == 0) {
                num = widget_str[3] - '0';
                opt_card = pcm_plat[num];
                if(opt_card < 0 )
                { return ; }
                if (strncmp(widget_str+4, "IN", 2) == 0)
                    opt_dir = PCM_IN;
                else
                    opt_dir = PCM_OUT;
            } else {
                return ;
            }
            break;
        case 3:
            opt_open = atoi(tmp_str);
            break;
        case 5:
            config.channels = atoi(tmp_str);
            break;
        case 6:
            config.rate = atoi(tmp_str);
            break;
        case 7:
            /* alsa format define should be do some convert */
            opt_format = atoi(tmp_str);
            switch (opt_format) {
            case 0 :
                config.format = PCM_FORMAT_S8;
                break;
            case 2:
                config.format = PCM_FORMAT_S16_LE;
                break;
            case 6:
                config.format = PCM_FORMAT_S24_LE;
                break;
            case 10:
                config.format = PCM_FORMAT_S32_LE;
                break;
            default:
                printf("not support format type\n");
                return;
            }
            break;
        default:
            break;
        }
        tmp_str = strtok(NULL, ":=/");
    }

    ALOGD("card =%d, dir=%d, open=%d, config=%d/%d/%d\n",
        opt_card, opt_dir, opt_open, config.channels, config.rate, config.format);
			
    config.period_size = 1024;
    config.period_count = 4;
    config.start_threshold = 0;
    config.stop_threshold = 0;
    config.silence_threshold = 0;

    pcm_thread_process(opt_card, opt_dir, opt_open, &config);	
    return ;
}

/*void signal_handler(int signum)
{
    running = 0;
}*/

void* pcm_event_thread(void* arg)
{
    int_map();
    init_pcm_plat();
    int ret;
    int sockfd;
    fd_set readset;
    struct timeval timeout;
    int count = 0;
    int checkret = 0;
    ALOGD("pcm_event_thread.");

    //signal(SIGTERM, signal_handler);
    //signal(SIGINT, signal_handler);
    //signal(SIGHUP, signal_handler);

    sockfd = socket(PF_NETLINK, SOCK_RAW, NETLINK_PCM_SUNXI);
    if (sockfd < 0)	{
        perror("socket create failed\n");
        return 0;
    }

    memset(&src_addr, 0, sizeof(src_addr));
    src_addr.nl_family = AF_NETLINK;
    src_addr.nl_pid = getpid();	/* self pid */

    ret = bind(sockfd, (struct sockaddr *)&src_addr, sizeof(src_addr));
    if (ret > 0) {
        perror("socket bind failed\n");
        return 0;
    }

    memset(&dest_addr, 0, sizeof(dest_addr));
    dest_addr.nl_family = AF_NETLINK;
    dest_addr.nl_pid = 0;	/* for linux kernel */
    dest_addr.nl_groups = 0;	/* unicast */

    nlhdr = (struct nlmsghdr *)malloc(NLMSG_SPACE(MAX_PAYLOAD));
    memset(nlhdr, 0, NLMSG_SPACE(MAX_PAYLOAD));
    nlhdr->nlmsg_len = NLMSG_SPACE(MAX_PAYLOAD);
    nlhdr->nlmsg_pid = getpid();
    nlhdr->nlmsg_flags = 0;

    /* start the pcm_netlink unicast */
    strcpy(NLMSG_DATA(nlhdr), "start");

    iov.iov_base = (void *)nlhdr;
    iov.iov_len = nlhdr->nlmsg_len;
    msg.msg_name = (void *)&dest_addr;
    msg.msg_namelen = sizeof(dest_addr);
    msg.msg_iov = &iov;
    msg.msg_iovlen = 1;

    fprintf(stdout, "Start pcm xrun debug test\n");
    sendmsg(sockfd, &msg, 0);

    while(running) {
        FD_ZERO(&readset);
        FD_SET(sockfd, &readset);
        timeout.tv_sec = 1;
        timeout.tv_usec = 0;

        ret = select(FD_SETSIZE, &readset, NULL, NULL, &timeout);
        if (ret < 0) {
            perror("select failed\n");
            break;
        } else if (ret = 0) {
            /* timeout */
            continue;
        } else {
            if (FD_ISSET(sockfd, &readset)) {
                ret = recvmsg(sockfd, &msg, 0);
                if (ret < 0) {
                    /* error recv msg */
                    return 0;
                } else {
                    pcm_event_process(NLMSG_DATA(nlhdr));
                }
            }
        }
    }

    strcpy(NLMSG_DATA(nlhdr), "close");
    fprintf(stdout, "Close sunxi netlink app thread\n");
    sendmsg(sockfd, &msg, 0);

    sleep(1);
    close(sockfd);
    return 0;
}


static pthread_t event_thread;

// warning: no protect!!!
int pcm_event_thread_start() {
    ALOGD("pcm_event_thread start.");
    if (pthread_create(&event_thread, NULL, pcm_event_thread, NULL)){
        ALOGE("%s() pthread_create usb_to_output_thread failed!!!", __func__);
        return -1;
    }
    pthread_detach(event_thread);
    return 0;
}

void pcm_event_thread_stop() {
    ALOGD("pcm_event_thread stop.");
    running = 0;
}

int main1() {
    pcm_event_thread(NULL);
    return 0;
}

