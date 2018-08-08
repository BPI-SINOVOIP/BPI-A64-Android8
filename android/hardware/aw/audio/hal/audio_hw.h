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

#ifndef _AUDIO_HW_H_
#define _AUDIO_HW_H_

#include <system/audio.h>
#include <hardware/audio.h>
#include <pthread.h>
#include "tinyalsa/asoundlib.h"
#include "audio_data_dump.h"

/* sample rate */
#define RATE_8K     8000
#define RATE_11K    11025
#define RATE_12K    12000
#define RATE_16K    16000
#define RATE_22K    22050
#define RATE_24K    24000
#define RATE_32K    32000
#define RATE_44K    44100
#define RATE_48K    48000
#define RATE_96K    96000
#define RATE_192K   192000


#define DEFAULT_CHANNEL_COUNT 2
#define DEFAULT_OUTPUT_SAMPLING_RATE RATE_44K
#define DEFAULT_OUTPUT_PERIOD_SIZE 1024
#define DEFAULT_OUTPUT_PERIOD_COUNT 2

#define DEFAULT_INPUT_SAMPLING_RATE RATE_44K
#define DEFAULT_INPUT_PERIOD_SIZE 1024
#define DEFAULT_INPUT_PERIOD_COUNT 2

struct sunxi_audio_device {
    struct audio_hw_device device;

    pthread_mutex_t lock; /* see note below on mutex acquisition order */

    struct sunxi_stream_in *active_input;
    struct sunxi_stream_out *active_output;
    int mode;
    int out_devices;
    int in_devices;
    bool mic_muted;

    struct platform *platform;
};

struct sunxi_stream_out {
    struct audio_stream_out stream;

    pthread_mutex_t lock; /* see note below on mutex acquisition order */

    int standby;
    uint32_t sample_rate;
    audio_channel_mask_t channel_mask;
    audio_channel_mask_t channel_mask_vts; /* add for vts */
    audio_format_t format;
    audio_devices_t devices;
    audio_output_flags_t flags;
    bool muted;

    /* total frames written, not cleared when entering standby */
    uint64_t written;

    int card;
    int port;
    struct pcm_config config;
    struct pcm *pcm;
    struct audio_data_dump dd_write_out;

    struct sunxi_audio_device *dev;
};

struct sunxi_stream_in {
    struct audio_stream_in stream;

    pthread_mutex_t lock; /* see note below on mutex acquisition order */

    int standby;
    uint32_t sample_rate;
    audio_channel_mask_t channel_mask;
    audio_format_t format;
    audio_devices_t devices;
    audio_output_flags_t flags;
    bool muted;
    int64_t frames_read; /* total frames read, not cleared when entering standby */

    int card;
    int port;
    struct pcm_config config;
    struct pcm *pcm;
    struct audio_data_dump dd_read_in;

    struct sunxi_audio_device *dev;
};

#endif
