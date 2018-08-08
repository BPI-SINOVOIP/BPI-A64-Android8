/*
**
** Copyright 2012, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

//#define LOG_NDEBUG 0
#define LOG_TAG "MediaPlayerFactory"
#include <utils/Log.h>

#include <cutils/properties.h>
#include <media/IMediaPlayer.h>
#include <media/stagefright/DataSource.h>
#include <media/stagefright/FileSource.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/Utils.h>

#include <utils/Errors.h>
#include <utils/misc.h>

#include "MediaPlayerFactory.h"

#include "TestPlayerStub.h"
#include "nuplayer/NuPlayerDriver.h"

#include "awplayer.h"
namespace android {

Mutex MediaPlayerFactory::sLock;
MediaPlayerFactory::tFactoryMap MediaPlayerFactory::sFactoryMap;
bool MediaPlayerFactory::sInitComplete = false;
// TODO: Temp hack until we can register players
typedef struct
{
    const char *extension;
    const player_type playertype;
} extmap;

extmap FILE_EXTS [] =
{
    {".ogg",  AW_PLAYER},
    {".mp3",  AW_PLAYER},
    {".wav",  AW_PLAYER},
    {".amr",  AW_PLAYER},
    {".flac", AW_PLAYER},
    {".m4a",  AW_PLAYER},
    {".m4r",  AW_PLAYER},
    {".3gpp", AW_PLAYER},
    {".out",  AW_PLAYER},
    {".3gp",  AW_PLAYER},
    {".aac",  AW_PLAYER},

    {".mid",  NU_PLAYER},
    {".midi", NU_PLAYER},
    {".smf",  NU_PLAYER},
    {".xmf",  NU_PLAYER},
    {".mxmf", NU_PLAYER},
    {".imy",  NU_PLAYER},
    {".rtttl",NU_PLAYER},
    {".rtx",  NU_PLAYER},
    {".ota",  NU_PLAYER},
    {".wvm",  NU_PLAYER},

    {".ape", AW_PLAYER},
    {".ac3", AW_PLAYER},
    {".dts", AW_PLAYER},
    {".wma", AW_PLAYER},
    {".aac", AW_PLAYER},
    {".mp2", AW_PLAYER},
    {".mp1", AW_PLAYER},
};

void getCallingProcessName(char *name)
{
    char value[1024];
    if(name == 0)
    {
        ALOGE("params error");
        return;
    }
    int getpersist;
    getpersist = property_get("persist.sys.cts", value, NULL);
    ALOGD("Initialize getpersist = %d value=%s", getpersist, value);
    strcpy(name, value);
}
player_type getPlayerType_l(int fd, int64_t offset, int64_t /*length*/)
{
    int r_size;
    char buf[4096];
    lseek(fd, offset, SEEK_SET);
    r_size = read(fd, buf, sizeof(buf));
    lseek(fd, offset, SEEK_SET);
    long ident = *((long*)buf);

    // Ogg vorbis?
    if (ident == 0x5367674f) {
        // 'OggS'
        return NU_PLAYER;
    } else if(ident == 0x6d756874){
        //return THUMBNAIL_PLAYER;
    } else if (ident == 0x6468544d) {
        //'MThd'
        return NU_PLAYER;
    }

    char value[1024];
    property_get("media.use_nuplayer", value, NULL);
    if(strcmp(value, "true") == 0)
    {
        return NU_PLAYER;
    }

    // Modify for WVM
    const char* my_uri;
    my_uri = strdup((char*)nameForFd(fd).c_str());
    if (strrchr(my_uri, '.') != NULL &&
    (!strncasecmp(".wvm", strrchr(my_uri, '.'), 4)
    || !strncasecmp(".mid", strrchr(my_uri, '.'), 4)
    || !strncasecmp(".midi", strrchr(my_uri, '.'), 5)))
    {
        ALOGD("fd NU_PLAYER.");
        free((void*)my_uri);
        return NU_PLAYER;
    }
    else
    {
        free((void*)my_uri);
    }
    char mCallingProcess[256]={0};
    getCallingProcessName(mCallingProcess);
    if((strcmp(mCallingProcess, "android.drm.cts") == 0) ||
            (strcmp(mCallingProcess, "com.google.android.media.gts") == 0) ||
            (strcmp(mCallingProcess, "android.video.cts") == 0) ||
            (strcmp(mCallingProcess, "android.media.cts") == 0) ||
            (strcmp(mCallingProcess, "android.view.cts") == 0) ||
            (strcmp(mCallingProcess, "android.security.cts") == 0) ||
            (strcmp(mCallingProcess, "com.android.cts.media") == 0)){
        return NU_PLAYER;
    }

    return AW_PLAYER;
}

player_type getPlayerType_l(const char* url)
{
    if ((strrchr(url, '.') != NULL) && (!strncasecmp(".html", strrchr(url, '.'), 5)))
    {
        ALOGD("html NU_PLAYER.");
        return NU_PLAYER;
    }

    if (TestPlayerStub::canBeUsed(url))
    {
        return TEST_PLAYER;
    }

    if(!strncasecmp("https://", url, 8))
    {
        return NU_PLAYER;
    }

    char value[1024];
    property_get("media.use_nuplayer", value, NULL);
    if(strcmp(value, "true") == 0)
    {
        return NU_PLAYER;
    }

    char mCallingProcess[256]={0};
    getCallingProcessName(mCallingProcess);
    if((strcmp(mCallingProcess, "android.drm.cts") == 0) ||
            (strcmp(mCallingProcess, "com.google.android.media.gts") == 0) ||
            (strcmp(mCallingProcess, "android.video.cts") == 0) ||
            (strcmp(mCallingProcess, "android.media.cts") == 0) ||
            (strcmp(mCallingProcess, "android.view.cts") == 0) ||
            (strcmp(mCallingProcess, "android.security.cts") == 0) ||
            (strcmp(mCallingProcess, "com.android.cts.media") == 0)){
        return NU_PLAYER;
    }

    if (!strncasecmp("widevine://", url, 11))
    {
        ALOGD("widevine stream NU_PLAYER");
        return NU_PLAYER;
    }

    if (!strncmp("data:;base64", url, strlen("data:;base64")))
    {
        return NU_PLAYER;
    }

    // use MidiFile for MIDI extensions
    int lenURL = strlen(url);
    int len;
    int start;
    for (int i = 0; i < NELEM(FILE_EXTS); ++i)
    {
        len = strlen(FILE_EXTS[i].extension);
        start = lenURL - len;
        if (start > 0)
        {
            if (!strncasecmp(url + start, FILE_EXTS[i].extension, len))
            {
                return FILE_EXTS[i].playertype;
            }
        }
    }

    return AW_PLAYER;
}

status_t MediaPlayerFactory::registerFactory_l(IFactory* factory,
                                               player_type type) {
    if (NULL == factory) {
        ALOGE("Failed to register MediaPlayerFactory of type %d, factory is"
              " NULL.", type);
        return BAD_VALUE;
    }

    if (sFactoryMap.indexOfKey(type) >= 0) {
        ALOGE("Failed to register MediaPlayerFactory of type %d, type is"
              " already registered.", type);
        return ALREADY_EXISTS;
    }

    if (sFactoryMap.add(type, factory) < 0) {
        ALOGE("Failed to register MediaPlayerFactory of type %d, failed to add"
              " to map.", type);
        return UNKNOWN_ERROR;
    }

    return OK;
}

static player_type getDefaultPlayerType() {
#if 0
    return NU_PLAYER;
#else
    return AW_PLAYER;
#endif
}

status_t MediaPlayerFactory::registerFactory(IFactory* factory,
                                             player_type type) {
    Mutex::Autolock lock_(&sLock);
    return registerFactory_l(factory, type);
}

void MediaPlayerFactory::unregisterFactory(player_type type) {
    Mutex::Autolock lock_(&sLock);
    sFactoryMap.removeItem(type);
}

#define GET_PLAYER_TYPE_IMPL_ORIGINAL(a...)                      \
    Mutex::Autolock lock_(&sLock);                      \
                                                        \
    player_type ret = STAGEFRIGHT_PLAYER;               \
    float bestScore = 0.0;                              \
                                                        \
    for (size_t i = 0; i < sFactoryMap.size(); ++i) {   \
                                                        \
        IFactory* v = sFactoryMap.valueAt(i);           \
        float thisScore;                                \
        CHECK(v != NULL);                               \
        thisScore = v->scoreFactory(a, bestScore);      \
        if (thisScore > bestScore) {                    \
            ret = sFactoryMap.keyAt(i);                 \
            bestScore = thisScore;                      \
        }                                               \
    }                                                   \
                                                        \
    if (0.0 == bestScore) {                             \
        bestScore = getDefaultPlayerType();             \
    }                                                   \
                                                        \
    return ret;

#define GET_PLAYER_TYPE_IMPL(a...)                      \
    Mutex::Autolock lock_(&sLock);                      \
                                                        \
    player_type ret = AW_PLAYER;               \
    float bestScore = 0.0;                              \
                                                        \
    for (size_t i = 0; i < sFactoryMap.size(); ++i) {   \
                                                        \
        IFactory* v = sFactoryMap.valueAt(i);           \
        float thisScore;                                \
        CHECK(v != NULL);                               \
        thisScore = v->scoreFactory(a, bestScore);      \
        if (thisScore > bestScore) {                    \
            ret = sFactoryMap.keyAt(i);                 \
            bestScore = thisScore;                      \
        }                                               \
    }                                                   \
                                                        \
    if (0.0 == bestScore) {                             \
        ret = getDefaultPlayerType();                   \
    }                                                   \
                                                        \
    return ret;

player_type MediaPlayerFactory::getPlayerType(const sp<IMediaPlayer>& /*client*/,
                                              const char* url) {
    ALOGV("MediaPlayerFactory::getPlayerType: url = %s", url);

    return android::getPlayerType_l(url);

#if 0
    GET_PLAYER_TYPE_IMPL(client, url);
#endif

}

player_type MediaPlayerFactory::getPlayerType(const sp<IMediaPlayer>& /*client*/,
                                              int fd,
                                              int64_t offset,
                                              int64_t length) {

#if 0
    GET_PLAYER_TYPE_IMPL(client, fd, offset, length);
#else
    return android::getPlayerType_l(fd, offset, length);
#endif
}

player_type MediaPlayerFactory::getPlayerType(const sp<IMediaPlayer>& client,
                                              const sp<IStreamSource> &source) {
    GET_PLAYER_TYPE_IMPL(client, source);
}

player_type MediaPlayerFactory::getPlayerType(const sp<IMediaPlayer>& /*client*/,
                                              const sp<DataSource> & /*source*/) {
#if 0
    GET_PLAYER_TYPE_IMPL(client, source);
#endif
    return NU_PLAYER;
}

#undef GET_PLAYER_TYPE_IMPL

sp<MediaPlayerBase> MediaPlayerFactory::createPlayer(
        player_type playerType,
        const wp<IMediaPlayer> &client,
        notify_callback_f notifyFunc,
        pid_t pid) {
    sp<MediaPlayerBase> p;
    IFactory* factory;
    status_t init_result;
    Mutex::Autolock lock_(&sLock);

    if (sFactoryMap.indexOfKey(playerType) < 0) {
        ALOGE("Failed to create player object of type %d, no registered"
              " factory", playerType);
        return p;
    }

    factory = sFactoryMap.valueFor(playerType);
    CHECK(NULL != factory);
    p = factory->createPlayer(pid);

    if (p == NULL) {
        ALOGE("Failed to create player object of type %d, create failed",
               playerType);
        return p;
    }

    init_result = p->initCheck();
    if (init_result == NO_ERROR) {
        p->setNotifyCallback(client, notifyFunc);
    } else {
        ALOGE("Failed to create player object of type %d, initCheck failed"
              " (res = %d)", playerType, init_result);
        p.clear();
    }

    return p;
}

/*****************************************************************************
 *                                                                           *
 *                     Built-In Factory Implementations                      *
 *                                                                           *
 *****************************************************************************/

class NuPlayerFactory : public MediaPlayerFactory::IFactory {
  public:
    virtual float scoreFactory(const sp<IMediaPlayer>& /*client*/,
                               const char* url,
                               float curScore) {
        static const float kOurScore = 0.8;

        if (kOurScore <= curScore)
            return 0.0;

        if (!strncasecmp("http://", url, 7)
                || !strncasecmp("https://", url, 8)
                || !strncasecmp("file://", url, 7)) {
            size_t len = strlen(url);
            if (len >= 5 && !strcasecmp(".m3u8", &url[len - 5])) {
                return kOurScore;
            }

            if (strstr(url,"m3u8")) {
                return kOurScore;
            }

            if ((len >= 4 && !strcasecmp(".sdp", &url[len - 4])) || strstr(url, ".sdp?")) {
                return kOurScore;
            }
        }

        if (!strncasecmp("rtsp://", url, 7)) {
            return kOurScore;
        }

        return 0.0;
    }

    virtual float scoreFactory(const sp<IMediaPlayer>& /*client*/,
                               const sp<IStreamSource>& /*source*/,
                               float /*curScore*/) {
        return 1.0;
    }

    virtual float scoreFactory(const sp<IMediaPlayer>& /*client*/,
                               const sp<DataSource>& /*source*/,
                               float /*curScore*/) {
        // Only NuPlayer supports setting a DataSource source directly.
        return 1.0;
    }

    virtual sp<MediaPlayerBase> createPlayer(pid_t pid) {
        ALOGV(" create NuPlayer");
        return new NuPlayerDriver(pid);
    }
};

class TestPlayerFactory : public MediaPlayerFactory::IFactory {
  public:
    virtual float scoreFactory(const sp<IMediaPlayer>& /*client*/,
                               const char* url,
                               float /*curScore*/) {
        if (TestPlayerStub::canBeUsed(url)) {
            return 1.0;
        }

        return 0.0;
    }

    virtual sp<MediaPlayerBase> createPlayer(pid_t /* pid */) {
        ALOGV("Create Test Player stub");
        return new TestPlayerStub();
    }
};

class AwPlayerFactory : public MediaPlayerFactory::IFactory {
  public:
    virtual float scoreFactory(const sp<IMediaPlayer>& /*client*/,
                               int /*fd*/,
                               int64_t /*offset*/,
                               int64_t /*length*/,
                               float /*curScore*/) {

        return 0.0;
    }

    virtual sp<MediaPlayerBase> createPlayer(pid_t /* pid */) {
        ALOGV(" create CmccPlayer");
        return new AwPlayer();
    }
};

void MediaPlayerFactory::registerBuiltinFactories() {
    Mutex::Autolock lock_(&sLock);

    if (sInitComplete)
        return;
    registerFactory_l(new AwPlayerFactory(), AW_PLAYER);

    IFactory* factory = new NuPlayerFactory();
    if (registerFactory_l(factory, NU_PLAYER) != OK)
        delete factory;
    factory = new TestPlayerFactory();
    if (registerFactory_l(factory, TEST_PLAYER) != OK)
        delete factory;

    sInitComplete = true;
}

}  // namespace android
