/*
 * Copyright 2016 The Android Open Source Project
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
#include "VtsProfilingInterface.h"

#include <cutils/properties.h>
#include <fcntl.h>
#include <fstream>
#include <string>

#include <android-base/logging.h>
#include <google/protobuf/text_format.h>

#include "VtsProfilingUtil.h"
#include "test/vts/proto/VtsDriverControlMessage.pb.h"
#include "test/vts/proto/VtsProfilingMessage.pb.h"

using namespace std;

namespace android {
namespace vts {

const int VtsProfilingInterface::kProfilingPointEntry = 1;
const int VtsProfilingInterface::kProfilingPointCallback = 2;
const int VtsProfilingInterface::kProfilingPointExit = 3;

VtsProfilingInterface::VtsProfilingInterface(const string& trace_file_path)
    : trace_file_path_(trace_file_path),
      trace_output_(nullptr),
      initialized_(false) {}

VtsProfilingInterface::~VtsProfilingInterface() {
  if (trace_output_) {
    trace_output_->Close();
  }
}

static int64_t NanoTime() {
  std::chrono::nanoseconds duration(
      std::chrono::steady_clock::now().time_since_epoch());
  return static_cast<int64_t>(duration.count());
}

VtsProfilingInterface& VtsProfilingInterface::getInstance(
    const string& trace_file_path) {
  static VtsProfilingInterface instance(trace_file_path);
  return instance;
}

void VtsProfilingInterface::Init() {
  if (initialized_) return;

  // Attach device info and timestamp for the trace file.
  char build_number[PROPERTY_VALUE_MAX];
  char device_id[PROPERTY_VALUE_MAX];
  char product_name[PROPERTY_VALUE_MAX];
  property_get("ro.build.version.incremental", build_number, "unknown_build");
  property_get("ro.serialno", device_id, "unknown_device");
  property_get("ro.build.product", product_name, "unknown_product");

  string file_path = trace_file_path_ + "_" + string(product_name) + "_" +
                     string(device_id) + "_" + string(build_number) + "_" +
                     to_string(NanoTime()) + ".vts.trace";

  LOG(INFO) << "Creating new profiler instance with file path: " << file_path;
  int fd = open(file_path.c_str(), O_RDWR | O_CREAT | O_EXCL,
                S_IRUSR | S_IWUSR | S_IRGRP | S_IROTH);
  if (fd < 0) {
    LOG(ERROR) << "Can not open trace file: " << file_path
               << " error: " << std::strerror(errno);
    return;
  }
  trace_output_.reset(new google::protobuf::io::FileOutputStream(fd));
  initialized_ = true;
}

bool VtsProfilingInterface::AddTraceEvent(
    android::hardware::details::HidlInstrumentor::InstrumentationEvent event,
    const char* package, const char* version, const char* interface,
    const FunctionSpecificationMessage& message) {
  if (!initialized_) {
    LOG(ERROR) << "Profiler not initialized. ";
    return false;
  }

  // Build the VTSProfilingRecord and print it to string.
  VtsProfilingRecord record;
  record.set_timestamp(NanoTime());
  record.set_event((InstrumentationEventType) static_cast<int>(event));
  record.set_package(package);
  record.set_version(stof(version));
  record.set_interface(interface);
  *record.mutable_func_msg() = message;

  // Write the record string to trace file.
  mutex_.lock();
  if (!writeOneDelimited(record, trace_output_.get())) {
    LOG(ERROR) << "Failed to write record";
  }
  if (!trace_output_->Flush()) {
    LOG(ERROR) << "Failed to flush: " << std::strerror(errno);
  }
  mutex_.unlock();

  return true;
}

}  // namespace vts
}  // namespace android
