/*
 * Copyright (C) 2015 The Android Open Source Project
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

#include "device.h"
#include "roots.h"
#include "screen_ui.h"
#include <unistd.h>

#define FIRST_BOOT_FLAG "/bootloader/data.notfirstrun"

class AwDevice : public Device {

  public:
    AwDevice(RecoveryUI* ui) :
        Device(ui) {
    }

    bool PostWipeData() {
        ensure_path_mounted(FIRST_BOOT_FLAG);
        unlink(FIRST_BOOT_FLAG);
        return true;
    }
};

class AwRecoveryUI : public ScreenRecoveryUI {
  public:
    AwRecoveryUI() {
      touch_screen_allowed_ = true;
    }
};

Device* make_device() {
  return new AwDevice(new AwRecoveryUI);
}
