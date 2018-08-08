/*
 * Copyright (C) 2016 The Android Open Source Project
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

#define LOG_TAG "audio_platform"
#define LOG_NDEBUG 0
#include <errno.h>
#include <stdlib.h>
#include <math.h>
#include <string.h>
#include <unistd.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <stdio.h>

#include <system/audio.h>
#include <audio_route/audio_route.h>
#include <cutils/log.h>
#include <expat.h>
#include "platform.h"
#include "tinyalsa/asoundlib.h"

/* log print attr for debug */
#define PRINT_ATTR 0

struct platform_info info;
static int profile_index = -1;

struct platform_plugins {
    struct audio_plugin *plugin;
    plugin_enable_flag_t enable_flag; /* Indicate conditions for enable this plugin */
};

struct audio_route {
    struct mixer *mixer;
    unsigned int num_mixer_ctls;
    struct mixer_state *mixer_state;

    unsigned int mixer_path_size;
    unsigned int num_mixer_paths;
    struct mixer_path *mixer_path;
};

extern struct audio_plugin dump_data;
extern struct audio_plugin audio_3d;
struct platform_plugins queue[] = {
    {&dump_data, PLUGIN_DUMP_DATA},
    /*{&audio_3d, PLUGIN_3D_AUDIO},*/
};

static void parse_root(const XML_Char **attr)
{
    UNUSED(attr);
    ALOGV("%s", __func__);
}

static void parse_platform_audio_plugins_config(const XML_Char **attr)
{
    ALOGV("%s", __func__);
    UNUSED(attr);
}

static void parse_plugin(const XML_Char **attr)
{
    ALOGV("%s", __func__);

    if (!strcmp(attr[1], "audio_3d_surround") && !strcmp(attr[3], "on")) {
        info.plugins_config |= PLUGIN_3D_AUDIO;
    } else if(!strcmp(attr[1], "dump_data") && !strcmp(attr[3], "on")) {
        info.plugins_config |= PLUGIN_DUMP_DATA;
    } else if(!strcmp(attr[1], "bp") && !strcmp(attr[3], "on")) {
        info.plugins_config |= PLUGIN_BP;
    }

    ALOGD("plugins_config:%#x", info.plugins_config);
}

static void parse_platform_devices_profile(const XML_Char **attr)
{
    UNUSED(attr);
    ALOGV("%s", __func__);
    if (profile_index > NUM_PROFILE -1) {
        ALOGE("profile_index:%d, max_index:%d.", profile_index, NUM_PROFILE-1);
        return;
    }

    profile_index++;

    info.profiles[profile_index] =
        calloc(1, sizeof(struct pdev_profile));
    if (!info.profiles[profile_index]) {
        ALOGE("can't calloc platform_devices_profile");
        return;
    }
}

static void parse_platform_devices(const XML_Char **attr)
{
    UNUSED(attr);
    ALOGV("%s: profile[%d] devices:%s", __func__, profile_index, attr[1]);
    info.profiles[profile_index]->devices = strdup(attr[1]);
}

static int get_card(const char *card_name)
{
    int ret;
    int fd;
    int i;
    char path[128];
    char name[64];

    for (i = 0; i < 10; i++) {
        sprintf(path, "/sys/class/sound/card%d/id", i);
        ret = access(path, F_OK);
        if (ret) {
            ALOGW("can't find node %s, use card0", path);
            return 0;
        }

        fd = open(path, O_RDONLY);
        if (fd <= 0) {
            ALOGE("can't open %s, use card0", path);
            return 0;
        }

        ret = read(fd, name, sizeof(name));
        close(fd);
        if (ret > 0) {
            name[ret-1] = '\0';
            if (!strcmp(name, card_name))
                return i;
        }
    }

    ALOGW("can't find card:%s, use card0", card_name);
    return 0;
}

static void parse_snd_card_config(const XML_Char **attr)
{
    ALOGV("%s", __func__);
    unsigned int i = 0;
    struct pdev_profile *profile;
    struct card_config card_config;

    if (profile_index >=0 && profile_index < NUM_PROFILE) {
        profile = info.profiles[profile_index];
    } else {
        ALOGE("profile_index:%d, max_index:%d.", profile_index, NUM_PROFILE-1);
        return;
    }

    for (i = 0;  attr[i]; i += 2) {
        ALOGV_IF(PRINT_ATTR, "attr[%d]:%s=%s", i, attr[i], attr[i+1]);

        if (!strcmp(attr[i], "card_name")) {
            card_config.card = get_card(attr[i+1]);
        } else if (!strcmp(attr[i], "device")) {
            card_config.port = atoi(attr[i+1]);
        } else if (!strcmp(attr[i], "channels")) {
            card_config.channels = atoi(attr[i+1]);
        } else if (!strcmp(attr[i], "rate")) {
            card_config.rate = atoi(attr[i+1]);
        } else if (!strcmp(attr[i], "period_size")) {
            card_config.period_size = atoi(attr[i+1]);
        } else if (!strcmp(attr[i], "period_count")) {
            card_config.period_count = atoi(attr[i+1]);
        }
    }

    /* find type attr */
    for (i = 0;  attr[i]; i += 2) {
        if (!strcmp(attr[i], "type")) {
            ALOGV_IF(PRINT_ATTR, "attr[%d]:%s=%s", i, attr[i], attr[i+1]);
            if (!strcmp(attr[i+1], "frontend")) {
                memcpy(&profile->frontend, &card_config,
                       sizeof(struct card_config));
            } else if(!strcmp(attr[i+1], "in_backend")) {
                int count = profile->in_bec;
                memcpy(&profile->in_be[count], &card_config,
                       sizeof(struct card_config));
                profile->in_bec++;
            } else if(!strcmp(attr[i+1], "out_backend")) {
                int count = profile->out_bec;
                memcpy(&profile->out_be[count], &card_config,
                       sizeof(struct card_config));
                profile->out_bec++;
            } else if(!strcmp(attr[i+1], "backend")) {
                int in_count = profile->in_bec;
                int out_count = profile->out_bec;
                memcpy(&profile->in_be[in_count], &card_config,
                       sizeof(struct card_config));
                memcpy(&profile->out_be[out_count], &card_config,
                       sizeof(struct card_config));
                profile->in_bec++;
                profile->out_bec++;
            } else {
                ALOGE("unknow type:%s", attr[i+1]);
                return;
            }
        }
    }
}

static void parse_platform_device_path(const XML_Char **attr)
{
    UNUSED(attr);
    ALOGV("%s", __func__);
}

static void parse_device_path_map(const XML_Char **attr)
{
    unsigned int i = 0;
    int pdev = OUT_NONE;
    char *path = NULL;

    /* get device and path value */
    for (i = 0;  attr[i]; i += 2) {
        if (!strcmp(attr[i], "device")) {
            pdev = str2pdev(attr[i+1]);
        } else if (!strcmp(attr[i], "path")) {
            path = strdup(attr[i+1]);
            break;
        }
    }

    info.pdev_path[pdev] = path;
    ALOGV_IF(PRINT_ATTR, "device:%d,path:%s", pdev, path);
}

static void start_tag(void *userdata __unused, const XML_Char *tag_name,
                      const XML_Char **attr)
{

    if (!strcmp(tag_name, "snd_card_config")) {
        parse_snd_card_config(attr);
    } else if (!strcmp(tag_name, "platform_devices_profile")) {
        parse_platform_devices_profile(attr);
    } else if (!strcmp(tag_name, "platform_devices")) {
        parse_platform_devices(attr);
    } else if (!strcmp(tag_name, "platform_device_path")) {
        parse_platform_device_path(attr);
    } else if (!strcmp(tag_name, "device_path_map")) {
        parse_device_path_map(attr);
    } else if (!strcmp(tag_name, "platform_audio_plugins_config")) {
        parse_platform_audio_plugins_config(attr);
    } else if (!strcmp(tag_name, "plugin")) {
        parse_plugin(attr);
    }
}

static void end_tag(void *userdata __unused, const XML_Char *tag_name)
{
    UNUSED(tag_name);
    //ALOGV("%s, tag_name:%s", __func__, tag_name);
}

static int parse_xml()
{
    XML_Parser parser;
    FILE *file;
    int ret = 0;
    int bytes_read;
    void *buf;
    static const uint32_t kBufSize = 1024;


    file = fopen(PLATFORM_INFO_XML_PATH, "r");
    if (!file) {
        ALOGD("%s: Failed to open %s, using defaults.",
            __func__, PLATFORM_INFO_XML_PATH);
        ret = -ENODEV;
        goto done;
    }
    ALOGV("%s:line:%d:file:%p", __func__, __LINE__, file);

    parser = XML_ParserCreate(NULL);
    if (!parser) {
        ALOGE("%s: Failed to create XML parser!", __func__);
        ret = -ENODEV;
        goto err_close_file;
    }
    ALOGV("%s:line:%d:parser:%p", __func__, __LINE__, parser);

    XML_SetElementHandler(parser, start_tag, end_tag);

    while (1) {
        buf = XML_GetBuffer(parser, kBufSize);
        if (buf == NULL) {
            ALOGE("%s: XML_GetBuffer failed", __func__);
            ret = -ENOMEM;
            goto err_free_parser;
        }

        bytes_read = fread(buf, 1, kBufSize, file);
        if (bytes_read < 0) {
            ALOGE("%s: fread failed, bytes read = %d", __func__, bytes_read);
             ret = bytes_read;
            goto err_free_parser;
        }

        if (XML_ParseBuffer(parser, bytes_read,
                            bytes_read == 0) == XML_STATUS_ERROR) {
            ALOGE("%s: XML_ParseBuffer failed, for %s",
                __func__, PLATFORM_INFO_XML_PATH);
            ret = -EINVAL;
            goto err_free_parser;
        }

        if (bytes_read == 0)
            break;
    }

err_free_parser:
    XML_ParserFree(parser);
err_close_file:
    fclose(file);
done:
    return ret;
}

struct platform *platform_init()
{
    ALOGV("platform_init");
    struct platform *platform = NULL;
    int card;
    int port;
    struct pcm_config pcm_conf;

    platform = calloc(1, sizeof(struct platform));
    if (!platform) {
        ALOGE("can't calloc platform");
        return NULL;
    }

    parse_xml();

    platform->info = &info;

    /* select SPK snd card id as default mixer card id.
     * NOTE: There may be multiple snd card in the future.
     */
    get_platform_snd_card_config(&card, &port, &pcm_conf, OUT_SPK, platform);
    platform->info->mixer_config.card = card;

    /* load mixer config from mixer xml file */
    platform->ar = audio_route_init(platform->info->mixer_config.card,
                                    MIXER_PATHS_XML_PATH);

    platform->uc = get_use_case();

    return platform;
}

void platform_exit(struct platform *platform)
{
    ALOGV("platform_exit");
    struct platform_info *info = platform->info;
    unsigned int i;

    /* free platform device profile */
    for (i = 0; i < ARRAY_SIZE(info->profiles); i++) {
        if (!info->profiles[i]) {
            free(info->profiles[i]);
            info->profiles[i] = NULL;
        }
    }

    /* free device path */
    for (i = 0; i < ARRAY_SIZE(info->pdev_path); i++) {
        if (!info->pdev_path[i]) {
            free(info->pdev_path[i]);
            info->pdev_path[i] = NULL;
        }
    }

    if (platform->ar)
        audio_route_free(platform->ar);

    if (platform)
        free(platform);
}

static int snd_card_config_dump(const struct card_config *card, int fd)
{
    dprintf(fd, "\t\tcard_config:\n"
                "\t\t\tcard:%d\n"
                "\t\t\tport:%d\n"
                "\t\t\tperiod_count:%d\n"
                "\t\t\tperiod_size:%d\n"
                "\t\t\tchannels:%d\n"
                "\t\t\trate:%d\n",
                card->card,
                card->port,
                card->period_count,
                card->period_size,
                card->channels,
                card->rate);
    return 0;
}


static int platform_devices_profile_dump(
           const struct pdev_profile *prof, int fd)
{
    unsigned int i;

    dprintf(fd, "\tplatform_devices_profile_dump:\n"
                "\t\tdevices:%s\n",
                prof->devices);

    dprintf(fd, "\t\tfrontend dump:\n");
    snd_card_config_dump(&prof->frontend, fd);

    for (i = 0; i < prof->in_bec; i++) {
        dprintf(fd, "\t\tin_backend[%d] dump:\n", i);
        snd_card_config_dump(&prof->in_be[i], fd);
    }

    for (i = 0; i < prof->out_bec; i++) {
        dprintf(fd, "\t\tout_backend[%d] dump:\n", i);
        snd_card_config_dump(&prof->out_be[i], fd);
    }

    return 0;
}

static int platform_info_dump(const struct platform_info *info, int fd)
{
    int i;
    struct pdev_profile *prof = NULL;

    dprintf(fd, "\tplatform_info_dump:\n"
                "\t\tplugins_config:%#x\n"
                "\t\tmixer card_id:%d\n",
                info->plugins_config,
                info->mixer_config.card);

    for (i = 0; i < NUM_PROFILE; i++) {
        prof = info->profiles[i];
        if (prof) {
            platform_devices_profile_dump(prof, fd);
        }
    }

    dprintf(fd, "\tplatform device path dump:\n");
    for (i = 0; i < NUM_PLATFORM_DEVICE; i++) {
        if (info->pdev_path[i]) {
            dprintf(fd, "\t\tdevice_path[%d]=%s\n",
                    i, info->pdev_path[i]);
        }
    }

    return 0;
}

int platform_dump(const struct platform *platform, int fd)
{
    ALOGV("platform_dump");

    struct platform_info *info = platform->info;

    dprintf(fd, "\tplatform_dump:\n"
                "\t\tar:%p\n"
                "\t\tinfo:%p\n"
                "\t\tin_backends[0]:%p\n"
                "\t\tout_backends[0]:%p\n"
                "\t\tuc:%#x\n",
                platform->ar,
                platform->info,
                platform->in_backends[0],
                platform->out_backends[0],
                platform->uc);

    if (info) {
        platform_info_dump(info, fd);
    }

    return 0;
}

int reset_platform_path(struct platform *platform)
{
    struct platform_info *info = platform->info;

    audio_route_reset(platform->ar);
    audio_route_update_mixer(platform->ar);

    return 0;
}

int update_platform_path(struct platform *platform)
{
    audio_route_update_mixer(platform->ar);

    return 0;
}

int get_platform_phone_device(audio_devices_t devices,
                              const struct platform *platform)
{
    struct platform_info *info = platform->info;
    int pdev = OUT_NONE;

    if (platform->uc & UC_DPHONE) {
        if (devices & AUDIO_DEVICE_OUT_EARPIECE) {
            pdev = (platform->uc & UC_EAR) ? DPH_EAR : DPH_SPK;
        } else if (devices & AUDIO_DEVICE_OUT_SPEAKER) {
            pdev = DPH_SPK;
        } else if (devices & AUDIO_DEVICE_OUT_WIRED_HEADPHONE) {
            pdev = DPH_HP;
        } else if (devices & AUDIO_DEVICE_OUT_WIRED_HEADSET) {
            pdev = DPH_HS;
        } else if (devices & AUDIO_DEVICE_OUT_ALL_SCO) {
            pdev = DPH_BTSCO;
        } else {
            pdev = DPH_SPK;
        }
    } else {
        if (devices & AUDIO_DEVICE_OUT_EARPIECE) {
            pdev = (platform->uc & UC_EAR) ? APH_EAR : APH_SPK;
        } else if (devices & AUDIO_DEVICE_OUT_SPEAKER) {
            pdev = APH_SPK;
        } else if (devices & AUDIO_DEVICE_OUT_WIRED_HEADPHONE) {
            pdev = APH_HP;
        } else if (devices & AUDIO_DEVICE_OUT_WIRED_HEADSET) {
            pdev = APH_HS;
        } else if (devices & AUDIO_DEVICE_OUT_ALL_SCO) {
            pdev = APH_BTSCO;
        } else {
            pdev = APH_SPK;
        }
    }

    char str[50] = "null";
    ALOGV("devices(%#x):platform phone device:%s(%#x)",
          devices, pdev2str(str, pdev), pdev);

    return pdev;
}

int get_platform_device(audio_mode_t mode, audio_devices_t devices,
                        const struct platform *platform)
{
    struct platform_info *info = platform->info;
    int pdev = OUT_NONE;

    /* 1. get platform device */
    if (devices & AUDIO_DEVICE_BIT_IN) {
        audio_devices_t indev = devices & (~AUDIO_DEVICE_BIT_IN);
        /* record device */
        if (indev & AUDIO_DEVICE_IN_BUILTIN_MIC) {
            pdev = (platform->uc & UC_DMIC) ? IN_DMIC : IN_AMIC;
        } else if (indev & AUDIO_DEVICE_IN_WIRED_HEADSET) {
            pdev = IN_HPMIC;
        } else if (indev & AUDIO_DEVICE_IN_ALL_SCO) {
            pdev = IN_BTSCO;
        } else {
            pdev = IN_AMIC;
        }
    } else {
        /* playback device */
        int headphone = AUDIO_DEVICE_OUT_WIRED_HEADSET |
                        AUDIO_DEVICE_OUT_WIRED_HEADPHONE;

        if (devices & AUDIO_DEVICE_OUT_EARPIECE) {
            pdev = OUT_EAR;
        } else if (devices & AUDIO_DEVICE_OUT_SPEAKER &&
                   devices & headphone) {
            pdev = (platform->uc & UC_DUAL_SPK) ? OUT_DULSPK_HP : OUT_SPK_HP;
        } else if (devices & AUDIO_DEVICE_OUT_SPEAKER) {
            pdev = (platform->uc & UC_DUAL_SPK) ? OUT_DULSPK : OUT_SPK;
        } else if (devices & headphone) {
            pdev = OUT_HP;
        } else if (devices & AUDIO_DEVICE_OUT_ALL_SCO) {
            pdev = OUT_BTSCO;
        } else if (devices & AUDIO_DEVICE_OUT_AUX_DIGITAL) {
            pdev = OUT_HDMI;
        } else {
            pdev = OUT_SPK;
        }
    }

    /* phone record and playback */
    if (AUDIO_MODE_IN_CALL == mode) {
        if (devices & AUDIO_DEVICE_BIT_IN) {
            pdev = (platform->uc & UC_DPHONE)? IN_DPH_REC : IN_APH_REC;
        } else {
            pdev = (platform->uc & UC_DPHONE)? OUT_DPH_PLAY : OUT_APH_PLAY;
        }
    }

    /* TODO: force use platform device */

    char str[50] = "null";
    ALOGV("mode(%#x),devices(%#x):platform device:%s(%#x)",
          mode, devices, pdev2str(str, pdev), pdev);

    return pdev;
}

int get_platform_path(char *path_name, const struct platform *platform,
                      int pdev)
{
    struct platform_info *info = platform->info;
    strcpy(path_name, info->pdev_path[pdev]);
    return 0;
}

int apply_platform_path(struct platform *platform, char *mixer_path)
{
    return audio_route_apply_path(platform->ar, mixer_path);
}

int apply_and_update_platform_path(struct platform *platform, char *mixer_path)
{
    return audio_route_apply_and_update_path(platform->ar, mixer_path);
}

int enable_platform_backend_pcm(int pdev,
                                struct platform *platform,
                                int direction)
{

    unsigned int count = 0;
    struct card_config *backend = NULL;

    int card = 0;
    int port = 0;
    struct pcm_config pcm_conf;
    struct platform_info *info = platform->info;
    struct pdev_profile *profile = NULL;
    char pdev_name[50];
    unsigned int i;

    pdev2str(pdev_name, pdev);

    /* 1. find platform_device profile */
    for (i = 0; i < ARRAY_SIZE(info->profiles); i++) {
        profile = info->profiles[i];
        if (profile) {
            if (strstr(profile->devices, pdev_name)) {
                break;
            }
        }
    }

    if (!profile) {
        ALOGE("can't get profile.");
        return -1;
    }

    if (direction == PCM_OUT) {
        count = profile->out_bec;
        backend = profile->out_be;
    } else if (direction == PCM_IN) {
        count = profile->in_bec;
        backend = profile->in_be;
    } else {
        ALOGE("unkown direction:%d", direction);
    }

    /* 2. open and start backend pcm */
    for (i = 0; i < count; i++) {
        card = backend[i].card;
        port = backend[i].port;
        pcm_conf.rate = backend[i].rate;
        pcm_conf.channels = backend[i].channels;
        pcm_conf.period_count = backend[i].period_count;
        pcm_conf.period_size = backend[i].period_size;

        ALOGD("enable backend_pcm:card(%d),port(%d),direction(%#x),"
              "rate(%d),ch(%d)"
              "period_count(%d),period_size(%d)",
              card, port, direction,
              pcm_conf.rate, pcm_conf.channels,
              pcm_conf.period_count, pcm_conf.period_size);

        struct pcm *backend_pcm = pcm_open(card, port, direction, &pcm_conf);
        if (!pcm_is_ready(backend_pcm)) {
            ALOGE("cannot open backend_pcm driver: %s",
                  pcm_get_error(backend_pcm));
            pcm_close(backend_pcm);
            return -ENOMEM;
        }

        pcm_start(backend_pcm);

        if (direction == PCM_OUT)
            platform->out_backends[platform->out_bec++] = backend_pcm;
        else if (direction == PCM_IN)
            platform->in_backends[platform->in_bec++] = backend_pcm;
    }

    return 0;
}

int disable_platform_backend_pcm(struct platform *platform, int direction)
{
    unsigned int i;

    ALOGV("disable backend pcm(direction:%s)",
          direction==PCM_OUT ? "PCM_OUT":"PCM_IN");

    if (PCM_OUT == direction)
        goto OUT;
    else if (PCM_IN == direction)
        goto IN;
    else
        return 0;

OUT:
    /* close out backend pcm */
    for (i = 0; i < ARRAY_SIZE(platform->out_backends); i++) {
        if (platform->out_backends[i]) {
            pcm_close(platform->out_backends[i]);
            platform->out_backends[i] = NULL;
        }
    }
    platform->out_bec = 0;
    return 0;

IN:
    /* close in backend pcm */
    for (i = 0; i < ARRAY_SIZE(platform->in_backends); i++) {
        if (platform->in_backends[i]) {
            pcm_close(platform->in_backends[i]);
            platform->in_backends[i] = NULL;
        }
    }
    platform->in_bec = 0;
    return 0;
}

int get_platform_snd_card_config(int *card, int *port,
                                 struct pcm_config *pcm_conf,
                                 int platform_device,
                                 const struct platform *platform)
{
    struct platform_info *info = platform->info;
    struct pdev_profile *profile = NULL;
    char dev_name[50];
    unsigned int i;

    pdev2str(dev_name, platform_device);

    /* find platform_device profile */
    for (i = 0; i < ARRAY_SIZE(info->profiles); i++) {
        profile = info->profiles[i];
        if (profile) {
            if (strstr(profile->devices, dev_name)) {
                break;
            }
        }
    }

    if (!profile) {
        ALOGE("can't get snd_card_config.");
        return -1;
    }

    /* get snd_card_config */
    *card = profile->frontend.card;
    *port = profile->frontend.port;
    pcm_conf->rate = profile->frontend.rate;
    pcm_conf->channels = profile->frontend.channels;
    pcm_conf->period_count = profile->frontend.period_count;
    pcm_conf->period_size = profile->frontend.period_size;

    return 0;
}

unsigned int platform_get_value_by_name(struct platform *platform, const char *name)
{
    struct audio_route *ar = platform->ar;
    unsigned int val = 0;
    enum mixer_ctl_type type;
    unsigned int num_values;
    struct mixer_ctl *ctl;
    unsigned int i;

    ctl = mixer_get_ctl_by_name(ar->mixer, name);

    if (!ctl) {
        ALOGE("Invalid mixer control");
        return -1;
    }

    type = mixer_ctl_get_type(ctl);

    num_values = mixer_ctl_get_num_values(ctl);

    for (i = 0; i < num_values; i++) {
        switch (type)
        {
            case MIXER_CTL_TYPE_INT:
            val = mixer_ctl_get_value(ctl, i);
            break;
            case MIXER_CTL_TYPE_BOOL:
            val = mixer_ctl_get_value(ctl, i);
            break;

            default:
            printf(" unknown");
            printf(" now we only support int and bool ");
            break;
        };
    }
    return val;
}

void platform_set_value(struct platform *platform, int id, int value, unsigned int num_values)
{
	struct audio_route *ar = platform->ar;
    struct mixer_ctl *ctl;
    enum mixer_ctl_type type;
    unsigned int num_ctl_values;
    unsigned int i;

    ctl = mixer_get_ctl(ar->mixer, id);

    if (!ctl) {
        return;
    }

    type = mixer_ctl_get_type(ctl);
    num_ctl_values = mixer_ctl_get_num_values(ctl);

    if (num_values == 1) {
        /* Set all values the same */

        for (i = 0; i < num_ctl_values; i++) {
            if (mixer_ctl_set_value(ctl, i, value)) {
                ALOGE("Error: invalid value");
                return;
            }
        }
    } else {
        /* Set multiple values */
        ALOGE("not support mutiple values yet!!");
    }
}

int platform_plugins_process(const struct platform *platform, int flag)
{
    struct platform_info *info = platform->info;
    unsigned int i;

    for(i = 0; i < ARRAY_SIZE(queue); i++) {
        if (info->plugins_config & queue[i].enable_flag) {
            if (queue[i].plugin)
                plugin_process(queue[i].plugin, flag);
        }
    }

    return 0;
}

int platform_plugins_process_select_devices(const struct platform *platform,
                                            int flag,
                                            int mode, int out_devices,
                                            int in_devices)
{
    struct platform_info *info = platform->info;
    unsigned int i;

    for(i = 0; i < ARRAY_SIZE(queue); i++) {
        if (info->plugins_config & queue[i].enable_flag) {
            if (queue[i].plugin)
                plugin_process_select_devices(queue[i].plugin, flag, mode,
                                              out_devices, in_devices);
        }
    }

    return 0;
}

int platform_plugins_process_start_stream(const struct platform *platform,
                                          int flag,
                                          struct pcm_config config)
{
    struct platform_info *info = platform->info;
    unsigned int i;

    for(i = 0; i < ARRAY_SIZE(queue); i++) {
        if (info->plugins_config & queue[i].enable_flag) {
            if (queue[i].plugin)
                plugin_process_start_stream(queue[i].plugin, flag, config);
        }
    }

    return 0;
}

int platform_plugins_process_read_write(const struct platform *platform,
                                        int flag,
                                        struct pcm_config config, void *buffer,
                                        size_t bytes)
{
    struct platform_info *info = platform->info;
    unsigned int i;

    for(i = 0; i < ARRAY_SIZE(queue); i++) {
        if (info->plugins_config & queue[i].enable_flag) {
            if (queue[i].plugin)
                plugin_process_read_write(queue[i].plugin, flag, config,
                                          buffer, bytes);
        }
    }

    return 0;
}
