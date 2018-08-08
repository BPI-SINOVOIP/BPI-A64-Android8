// Copyright (C) 2016 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#ifndef _SCOPED_MINIJAIL_H_
#define _SCOPED_MINIJAIL_H_

#include <memory>

#include "libminijail.h"

namespace {
struct ScopedMinijailDeleter {
    inline void operator()(minijail *j) const {
        if (j) {
            minijail_destroy(j);
        }
    }
};
}

using ScopedMinijail = std::unique_ptr<minijail, ScopedMinijailDeleter>;

#endif /* _SCOPED_MINIJAIL_H_ */
