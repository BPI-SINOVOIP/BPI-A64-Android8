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
#include <android/hidl/memory/1.0/IMemory.h>
#include <hidl/HidlSupport.h>

namespace android {
namespace hardware {

/**
 * Returns the IMemory instance corresponding to a hidl_memory object.
 * If the shared memory cannot be fetched, this returns nullptr.
 */
sp<android::hidl::memory::V1_0::IMemory> mapMemory(const hidl_memory &memory);

}  // namespace hardware
}  // namespace android
