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

#include <gtest/gtest.h>

#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

#include <android-base/file.h>
#include <android-base/stringprintf.h>
#include <android-base/test_utils.h>

#include <map>
#include <memory>
#include <thread>

#include "command.h"
#include "environment.h"
#include "event_selection_set.h"
#include "get_test_data.h"
#include "record.h"
#include "record_file.h"
#include "test_util.h"
#include "thread_tree.h"

using namespace PerfFileFormat;

static std::unique_ptr<Command> RecordCmd() {
  return CreateCommandInstance("record");
}

static bool RunRecordCmd(std::vector<std::string> v,
                         const char* output_file = nullptr) {
  std::unique_ptr<TemporaryFile> tmpfile;
  std::string out_file;
  if (output_file != nullptr) {
    out_file = output_file;
  } else {
    tmpfile.reset(new TemporaryFile);
    out_file = tmpfile->path;
  }
  v.insert(v.end(), {"-o", out_file, "sleep", SLEEP_SEC});
  return RecordCmd()->Run(v);
}

TEST(record_cmd, no_options) { ASSERT_TRUE(RunRecordCmd({})); }

TEST(record_cmd, system_wide_option) {
  TEST_IN_ROOT(ASSERT_TRUE(RunRecordCmd({"-a"})));
}

TEST(record_cmd, sample_period_option) {
  ASSERT_TRUE(RunRecordCmd({"-c", "100000"}));
}

TEST(record_cmd, event_option) {
  ASSERT_TRUE(RunRecordCmd({"-e", "cpu-clock"}));
}

TEST(record_cmd, freq_option) {
  ASSERT_TRUE(RunRecordCmd({"-f", "99"}));
  ASSERT_TRUE(RunRecordCmd({"-F", "99"}));
}

TEST(record_cmd, output_file_option) {
  TemporaryFile tmpfile;
  ASSERT_TRUE(RecordCmd()->Run({"-o", tmpfile.path, "sleep", SLEEP_SEC}));
}

TEST(record_cmd, dump_kernel_mmap) {
  TemporaryFile tmpfile;
  ASSERT_TRUE(RunRecordCmd({}, tmpfile.path));
  std::unique_ptr<RecordFileReader> reader =
      RecordFileReader::CreateInstance(tmpfile.path);
  ASSERT_TRUE(reader != nullptr);
  std::vector<std::unique_ptr<Record>> records = reader->DataSection();
  ASSERT_GT(records.size(), 0U);
  bool have_kernel_mmap = false;
  for (auto& record : records) {
    if (record->type() == PERF_RECORD_MMAP) {
      const MmapRecord* mmap_record =
          static_cast<const MmapRecord*>(record.get());
      if (strcmp(mmap_record->filename, DEFAULT_KERNEL_MMAP_NAME) == 0 ||
          strcmp(mmap_record->filename, DEFAULT_KERNEL_MMAP_NAME_PERF) == 0) {
        have_kernel_mmap = true;
        break;
      }
    }
  }
  ASSERT_TRUE(have_kernel_mmap);
}

TEST(record_cmd, dump_build_id_feature) {
  TemporaryFile tmpfile;
  ASSERT_TRUE(RunRecordCmd({}, tmpfile.path));
  std::unique_ptr<RecordFileReader> reader =
      RecordFileReader::CreateInstance(tmpfile.path);
  ASSERT_TRUE(reader != nullptr);
  const FileHeader& file_header = reader->FileHeader();
  ASSERT_TRUE(file_header.features[FEAT_BUILD_ID / 8] &
              (1 << (FEAT_BUILD_ID % 8)));
  ASSERT_GT(reader->FeatureSectionDescriptors().size(), 0u);
}

TEST(record_cmd, tracepoint_event) {
  TEST_IN_ROOT(ASSERT_TRUE(RunRecordCmd({"-a", "-e", "sched:sched_switch"})));
}

TEST(record_cmd, branch_sampling) {
  if (IsBranchSamplingSupported()) {
    ASSERT_TRUE(RunRecordCmd({"-b"}));
    ASSERT_TRUE(RunRecordCmd({"-j", "any,any_call,any_ret,ind_call"}));
    ASSERT_TRUE(RunRecordCmd({"-j", "any,k"}));
    ASSERT_TRUE(RunRecordCmd({"-j", "any,u"}));
    ASSERT_FALSE(RunRecordCmd({"-j", "u"}));
  } else {
    GTEST_LOG_(INFO) << "This test does nothing as branch stack sampling is "
                        "not supported on this device.";
  }
}

TEST(record_cmd, event_modifier) {
  ASSERT_TRUE(RunRecordCmd({"-e", "cpu-cycles:u"}));
}

TEST(record_cmd, fp_callchain_sampling) {
  ASSERT_TRUE(RunRecordCmd({"--call-graph", "fp"}));
}

TEST(record_cmd, fp_callchain_sampling_warning_on_arm) {
  if (GetBuildArch() != ARCH_ARM) {
    GTEST_LOG_(INFO) << "This test does nothing as it only tests on arm arch.";
    return;
  }
  ASSERT_EXIT(
      {
        exit(RunRecordCmd({"--call-graph", "fp"}) ? 0 : 1);
      },
      testing::ExitedWithCode(0), "doesn't work well on arm");
}

TEST(record_cmd, system_wide_fp_callchain_sampling) {
  TEST_IN_ROOT(ASSERT_TRUE(RunRecordCmd({"-a", "--call-graph", "fp"})));
}

bool IsInNativeAbi() {
  static int in_native_abi = -1;
  if (in_native_abi == -1) {
    FILE* fp = popen("uname -m", "re");
    char buf[40];
    memset(buf, '\0', sizeof(buf));
    fgets(buf, sizeof(buf), fp);
    pclose(fp);
    std::string s = buf;
    in_native_abi = 1;
    if (GetBuildArch() == ARCH_X86_32 || GetBuildArch() == ARCH_X86_64) {
      if (s.find("86") == std::string::npos) {
        in_native_abi = 0;
      }
    } else if (GetBuildArch() == ARCH_ARM || GetBuildArch() == ARCH_ARM64) {
      if (s.find("arm") == std::string::npos && s.find("aarch64") == std::string::npos) {
        in_native_abi = 0;
      }
    }
  }
  return in_native_abi == 1;
}

TEST(record_cmd, dwarf_callchain_sampling) {
  OMIT_TEST_ON_NON_NATIVE_ABIS();
  ASSERT_TRUE(IsDwarfCallChainSamplingSupported());
  std::vector<std::unique_ptr<Workload>> workloads;
  CreateProcesses(1, &workloads);
  std::string pid = std::to_string(workloads[0]->GetPid());
  ASSERT_TRUE(RunRecordCmd({"-p", pid, "--call-graph", "dwarf"}));
  ASSERT_TRUE(RunRecordCmd({"-p", pid, "--call-graph", "dwarf,16384"}));
  ASSERT_FALSE(RunRecordCmd({"-p", pid, "--call-graph", "dwarf,65536"}));
  ASSERT_TRUE(RunRecordCmd({"-p", pid, "-g"}));
}

TEST(record_cmd, system_wide_dwarf_callchain_sampling) {
  OMIT_TEST_ON_NON_NATIVE_ABIS();
  ASSERT_TRUE(IsDwarfCallChainSamplingSupported());
  TEST_IN_ROOT(RunRecordCmd({"-a", "--call-graph", "dwarf"}));
}

TEST(record_cmd, no_unwind_option) {
  OMIT_TEST_ON_NON_NATIVE_ABIS();
  ASSERT_TRUE(IsDwarfCallChainSamplingSupported());
  ASSERT_TRUE(RunRecordCmd({"--call-graph", "dwarf", "--no-unwind"}));
  ASSERT_FALSE(RunRecordCmd({"--no-unwind"}));
}

TEST(record_cmd, post_unwind_option) {
  OMIT_TEST_ON_NON_NATIVE_ABIS();
  ASSERT_TRUE(IsDwarfCallChainSamplingSupported());
  std::vector<std::unique_ptr<Workload>> workloads;
  CreateProcesses(1, &workloads);
  std::string pid = std::to_string(workloads[0]->GetPid());
  ASSERT_TRUE(RunRecordCmd({"-p", pid, "--call-graph", "dwarf", "--post-unwind"}));
  ASSERT_FALSE(RunRecordCmd({"--post-unwind"}));
  ASSERT_FALSE(
      RunRecordCmd({"--call-graph", "dwarf", "--no-unwind", "--post-unwind"}));
}

TEST(record_cmd, existing_processes) {
  std::vector<std::unique_ptr<Workload>> workloads;
  CreateProcesses(2, &workloads);
  std::string pid_list = android::base::StringPrintf(
      "%d,%d", workloads[0]->GetPid(), workloads[1]->GetPid());
  ASSERT_TRUE(RunRecordCmd({"-p", pid_list}));
}

TEST(record_cmd, existing_threads) {
  std::vector<std::unique_ptr<Workload>> workloads;
  CreateProcesses(2, &workloads);
  // Process id can also be used as thread id in linux.
  std::string tid_list = android::base::StringPrintf(
      "%d,%d", workloads[0]->GetPid(), workloads[1]->GetPid());
  ASSERT_TRUE(RunRecordCmd({"-t", tid_list}));
}

TEST(record_cmd, no_monitored_threads) { ASSERT_FALSE(RecordCmd()->Run({""})); }

TEST(record_cmd, more_than_one_event_types) {
  ASSERT_TRUE(RunRecordCmd({"-e", "cpu-cycles,cpu-clock"}));
  ASSERT_TRUE(RunRecordCmd({"-e", "cpu-cycles", "-e", "cpu-clock"}));
}

TEST(record_cmd, mmap_page_option) {
  ASSERT_TRUE(RunRecordCmd({"-m", "1"}));
  ASSERT_FALSE(RunRecordCmd({"-m", "0"}));
  ASSERT_FALSE(RunRecordCmd({"-m", "7"}));
}

static void CheckKernelSymbol(const std::string& path, bool need_kallsyms,
                              bool* success) {
  *success = false;
  std::unique_ptr<RecordFileReader> reader =
      RecordFileReader::CreateInstance(path);
  ASSERT_TRUE(reader != nullptr);
  std::vector<std::unique_ptr<Record>> records = reader->DataSection();
  bool has_kernel_symbol_records = false;
  for (const auto& record : records) {
    if (record->type() == SIMPLE_PERF_RECORD_KERNEL_SYMBOL) {
      has_kernel_symbol_records = true;
    }
  }
  bool require_kallsyms = need_kallsyms && CheckKernelSymbolAddresses();
  ASSERT_EQ(require_kallsyms, has_kernel_symbol_records);
  *success = true;
}

TEST(record_cmd, kernel_symbol) {
  TemporaryFile tmpfile;
  ASSERT_TRUE(RunRecordCmd({"--no-dump-symbols"}, tmpfile.path));
  bool success;
  CheckKernelSymbol(tmpfile.path, true, &success);
  ASSERT_TRUE(success);
  ASSERT_TRUE(RunRecordCmd({"--no-dump-symbols", "--no-dump-kernel-symbols"}, tmpfile.path));
  CheckKernelSymbol(tmpfile.path, false, &success);
  ASSERT_TRUE(success);
}

// Check if the dso/symbol records in perf.data matches our expectation.
static void CheckDsoSymbolRecords(const std::string& path,
                                  bool can_have_dso_symbol_records,
                                  bool* success) {
  *success = false;
  std::unique_ptr<RecordFileReader> reader =
      RecordFileReader::CreateInstance(path);
  ASSERT_TRUE(reader != nullptr);
  std::vector<std::unique_ptr<Record>> records = reader->DataSection();
  bool has_dso_record = false;
  bool has_symbol_record = false;
  std::map<uint64_t, bool> dso_hit_map;
  for (const auto& record : records) {
    if (record->type() == SIMPLE_PERF_RECORD_DSO) {
      has_dso_record = true;
      uint64_t dso_id = static_cast<const DsoRecord*>(record.get())->dso_id;
      ASSERT_EQ(dso_hit_map.end(), dso_hit_map.find(dso_id));
      dso_hit_map.insert(std::make_pair(dso_id, false));
    } else if (record->type() == SIMPLE_PERF_RECORD_SYMBOL) {
      has_symbol_record = true;
      uint64_t dso_id = static_cast<const SymbolRecord*>(record.get())->dso_id;
      auto it = dso_hit_map.find(dso_id);
      ASSERT_NE(dso_hit_map.end(), it);
      it->second = true;
    }
  }
  if (can_have_dso_symbol_records) {
    // It is possible that there are no samples hitting functions having symbol.
    // In that case, there are no dso/symbol records.
    ASSERT_EQ(has_dso_record, has_symbol_record);
    for (auto& pair : dso_hit_map) {
      ASSERT_TRUE(pair.second);
    }
  } else {
    ASSERT_FALSE(has_dso_record);
    ASSERT_FALSE(has_symbol_record);
  }
  *success = true;
}

TEST(record_cmd, no_dump_symbols) {
  TemporaryFile tmpfile;
  ASSERT_TRUE(RunRecordCmd({}, tmpfile.path));
  bool success;
  CheckDsoSymbolRecords(tmpfile.path, true, &success);
  ASSERT_TRUE(success);
  ASSERT_TRUE(RunRecordCmd({"--no-dump-symbols"}, tmpfile.path));
  CheckDsoSymbolRecords(tmpfile.path, false, &success);
  ASSERT_TRUE(success);
  OMIT_TEST_ON_NON_NATIVE_ABIS();
  ASSERT_TRUE(IsDwarfCallChainSamplingSupported());
  std::vector<std::unique_ptr<Workload>> workloads;
  CreateProcesses(1, &workloads);
  std::string pid = std::to_string(workloads[0]->GetPid());
  ASSERT_TRUE(RunRecordCmd({"-p", pid, "-g"}, tmpfile.path));
  CheckDsoSymbolRecords(tmpfile.path, true, &success);
  ASSERT_TRUE(success);
  ASSERT_TRUE(RunRecordCmd({"-p", pid, "-g", "--no-dump-symbols"}, tmpfile.path));
  CheckDsoSymbolRecords(tmpfile.path, false, &success);
  ASSERT_TRUE(success);
}

TEST(record_cmd, dump_kernel_symbols) {
  if (!IsRoot()) {
    GTEST_LOG_(INFO) << "Test requires root privilege";
    return;
  }
  TemporaryFile tmpfile;
  ASSERT_TRUE(RunRecordCmd({"-a", "-o", tmpfile.path, "sleep", "1"}));
  std::unique_ptr<RecordFileReader> reader = RecordFileReader::CreateInstance(tmpfile.path);
  ASSERT_TRUE(reader != nullptr);
  std::map<int, SectionDesc> section_map = reader->FeatureSectionDescriptors();
  ASSERT_NE(section_map.find(FEAT_FILE), section_map.end());
  std::string file_path;
  uint32_t file_type;
  uint64_t min_vaddr;
  std::vector<Symbol> symbols;
  size_t read_pos = 0;
  bool has_kernel_symbols = false;
  while (reader->ReadFileFeature(read_pos, &file_path, &file_type, &min_vaddr, &symbols)) {
    if (file_type == DSO_KERNEL && !symbols.empty()) {
      has_kernel_symbols = true;
    }
  }
  ASSERT_TRUE(has_kernel_symbols);
}

TEST(record_cmd, group_option) {
  ASSERT_TRUE(RunRecordCmd({"--group", "cpu-cycles,cpu-clock", "-m", "16"}));
  ASSERT_TRUE(RunRecordCmd({"--group", "cpu-cycles,cpu-clock", "--group",
                            "cpu-cycles:u,cpu-clock:u", "--group",
                            "cpu-cycles:k,cpu-clock:k", "-m", "16"}));
}

TEST(record_cmd, symfs_option) { ASSERT_TRUE(RunRecordCmd({"--symfs", "/"})); }

TEST(record_cmd, duration_option) {
  TemporaryFile tmpfile;
  ASSERT_TRUE(RecordCmd()->Run({"--duration", "1.2", "-p",
                                std::to_string(getpid()), "-o", tmpfile.path, "--in-app"}));
  ASSERT_TRUE(
      RecordCmd()->Run({"--duration", "1", "-o", tmpfile.path, "sleep", "2"}));
}

TEST(record_cmd, support_modifier_for_clock_events) {
  for (const std::string& e : {"cpu-clock", "task-clock"}) {
    for (const std::string& m : {"u", "k"}) {
      ASSERT_TRUE(RunRecordCmd({"-e", e + ":" + m})) << "event " << e << ":"
                                                     << m;
    }
  }
}

TEST(record_cmd, handle_SIGHUP) {
  TemporaryFile tmpfile;
  std::thread thread([]() {
    sleep(1);
    kill(getpid(), SIGHUP);
  });
  thread.detach();
  ASSERT_TRUE(RecordCmd()->Run({"-o", tmpfile.path, "sleep", "1000000"}));
}

TEST(record_cmd, stop_when_no_more_targets) {
  TemporaryFile tmpfile;
  std::atomic<int> tid(0);
  std::thread thread([&]() {
    tid = gettid();
    sleep(1);
  });
  thread.detach();
  while (tid == 0);
  ASSERT_TRUE(RecordCmd()->Run({"-o", tmpfile.path, "-t", std::to_string(tid), "--in-app"}));
}

TEST(record_cmd, donot_stop_when_having_targets) {
  std::vector<std::unique_ptr<Workload>> workloads;
  CreateProcesses(1, &workloads);
  std::string pid = std::to_string(workloads[0]->GetPid());
  uint64_t start_time_in_ns = GetSystemClock();
  TemporaryFile tmpfile;
  ASSERT_TRUE(RecordCmd()->Run({"-o", tmpfile.path, "-p", pid, "--duration", "3"}));
  uint64_t end_time_in_ns = GetSystemClock();
  ASSERT_GT(end_time_in_ns - start_time_in_ns, static_cast<uint64_t>(2e9));
}

TEST(record_cmd, start_profiling_fd_option) {
  int pipefd[2];
  ASSERT_EQ(0, pipe(pipefd));
  int read_fd = pipefd[0];
  int write_fd = pipefd[1];
  ASSERT_EXIT(
      {
        close(read_fd);
        exit(RunRecordCmd({"--start_profiling_fd", std::to_string(write_fd)}) ? 0 : 1);
      },
      testing::ExitedWithCode(0), "");
  close(write_fd);
  std::string s;
  ASSERT_TRUE(android::base::ReadFdToString(read_fd, &s));
  close(read_fd);
  ASSERT_EQ("STARTED", s);
}

TEST(record_cmd, record_meta_info_feature) {
  TemporaryFile tmpfile;
  ASSERT_TRUE(RunRecordCmd({}, tmpfile.path));
  std::unique_ptr<RecordFileReader> reader = RecordFileReader::CreateInstance(tmpfile.path);
  ASSERT_TRUE(reader != nullptr);
  std::unordered_map<std::string, std::string> info_map;
  ASSERT_TRUE(reader->ReadMetaInfoFeature(&info_map));
  ASSERT_NE(info_map.find("simpleperf_version"), info_map.end());
}

// See http://b/63135835.
TEST(record_cmd, cpu_clock_for_a_long_time) {
  std::vector<std::unique_ptr<Workload>> workloads;
  CreateProcesses(1, &workloads);
  std::string pid = std::to_string(workloads[0]->GetPid());
  TemporaryFile tmpfile;
  ASSERT_TRUE(RecordCmd()->Run(
      {"-e", "cpu-clock", "-o", tmpfile.path, "-p", pid, "--duration", "3"}));
}

TEST(record_cmd, dump_regs_for_tracepoint_events) {
  OMIT_TEST_ON_NON_NATIVE_ABIS();
  // Check if the kernel can dump registers for tracepoint events.
  // If not, probably a kernel patch below is missing:
  // "5b09a094f2 arm64: perf: Fix callchain parse error with kernel tracepoint events"
  std::vector<std::unique_ptr<Workload>> workloads;
  CreateProcesses(1, &workloads);
  std::string pid = std::to_string(workloads[0]->GetPid());
  TemporaryFile tmpfile;
  ASSERT_TRUE(RecordCmd()->Run({"-o", tmpfile.path, "-p", pid, "-e", "sched:sched_switch",
                                "-g", "--no-unwind", "--duration", "1"}));

  // If the kernel patch is missing, all regs dumped in sample records are zero.
  std::unique_ptr<RecordFileReader> reader = RecordFileReader::CreateInstance(tmpfile.path);
  CHECK(reader != nullptr);
  std::unique_ptr<Record> r;
  bool regs_all_zero = true;
  while (reader->ReadRecord(r) && r && regs_all_zero) {
    if (r->type() != PERF_RECORD_SAMPLE) {
      continue;
    }
    SampleRecord* s = static_cast<SampleRecord*>(r.get());
    for (size_t i = 0; i < s->regs_user_data.reg_nr; ++i) {
      if (s->regs_user_data.regs[i] != 0u) {
        regs_all_zero = false;
        break;
      }
    }
  }
  ASSERT_FALSE(regs_all_zero);
}
