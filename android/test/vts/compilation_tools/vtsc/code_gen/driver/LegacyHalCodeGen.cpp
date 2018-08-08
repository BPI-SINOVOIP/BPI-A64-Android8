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

#include "code_gen/driver/LegacyHalCodeGen.h"

#include <fstream>
#include <iostream>
#include <sstream>
#include <string>

#include "test/vts/proto/ComponentSpecificationMessage.pb.h"

#include "VtsCompilerUtils.h"

using namespace std;
using namespace android;

namespace android {
namespace vts {

const char* const LegacyHalCodeGen::kInstanceVariableName = "legacyhal_";

void LegacyHalCodeGen::GenerateCppBodyFuzzFunction(
    Formatter& out, const ComponentSpecificationMessage& message,
    const string& fuzzer_extended_class_name) {
  out << "bool " << fuzzer_extended_class_name << "::Fuzz(" << "\n";
  out << "    FunctionSpecificationMessage* func_msg," << "\n";
  out << "    void** result, const string& callback_socket_name) {" << "\n";
  out.indent();
  out << "const char* func_name = func_msg->name().c_str();" << "\n";
  out << "cout << \"Function: \" << func_name << endl;" << "\n";

  for (auto const& api : message.interface().api()) {
    std::stringstream ss;

    out << "if (!strcmp(func_name, \"" << api.name() << "\")) {" << "\n";

    // args - definition;
    int arg_count = 0;
    for (auto const& arg : api.arg()) {
      out << "    " << GetCppVariableType(arg) << " ";
      out << "arg" << arg_count << " = ";
      if (arg_count == 0 && arg.type() == TYPE_PREDEFINED &&
          !strncmp(arg.predefined_type().c_str(),
                   message.original_data_structure_name().c_str(),
                   message.original_data_structure_name().length()) &&
          message.original_data_structure_name().length() > 0) {
        out << "reinterpret_cast<" << GetCppVariableType(arg) << ">("
            << kInstanceVariableName << ")";
      } else {
        out << GetCppInstanceType(arg);
      }
      out << ";" << "\n";
      out << "    cout << \"arg" << arg_count << " = \" << arg" << arg_count
          << " << endl;" << "\n";
      arg_count++;
    }

    out << "    ";
    out << "typedef void* (*";
    out << "func_type_" << api.name() << ")(...";
    out << ");" << "\n";

    // actual function call
    if (!api.has_return_type() || api.return_type().type() == TYPE_VOID) {
      out << "*result = NULL;" << "\n";
    } else {
      out << "*result = const_cast<void*>(reinterpret_cast<const void*>(";
    }
    out << "    ";
    out << "((func_type_" << api.name() << ") "
        << "target_loader_.GetLoaderFunction(\"" << api.name() << "\"))(";
    // out << "reinterpret_cast<" << message.original_data_structure_name()
    //    << "*>(" << kInstanceVariableName << ")->" << api.name() << "(";

    if (arg_count > 0) out << "\n";

    for (int index = 0; index < arg_count; index++) {
      out << "      arg" << index;
      if (index != (arg_count - 1)) {
        out << "," << "\n";
      }
    }
    if (api.has_return_type() || api.return_type().type() != TYPE_VOID) {
      out << "))";
    }
    out << ");" << "\n";
    out << "    return true;" << "\n";
    out << "  }" << "\n";
  }
  // TODO: if there were pointers, free them.
  out << "return false;" << "\n";
  out.unindent();
  out << "}" << "\n";
}

void LegacyHalCodeGen::GenerateCppBodyGetAttributeFunction(
    Formatter& out,
    const ComponentSpecificationMessage& /*message*/,
    const string& fuzzer_extended_class_name) {
  out << "bool " << fuzzer_extended_class_name << "::GetAttribute(" << "\n";
  out << "    FunctionSpecificationMessage* func_msg," << "\n";
  out << "    void** result) {" << "\n";
  out.indent();
  out << "const char* func_name = func_msg->name().c_str();" << "\n";
  out << "cout << \"Function: \" << __func__ << \" '\" << func_name << \"'\" << endl;"
      << "\n";
  out << "cerr << \"attribute not supported for legacy hal yet\" << endl;"
      << "\n";
  out << "return false;" << "\n";
  out.unindent();
  out << "}" << "\n";
}

void LegacyHalCodeGen::GenerateClassConstructionFunction(Formatter& out,
    const ComponentSpecificationMessage& /*message*/,
    const string& fuzzer_extended_class_name) {
  out << fuzzer_extended_class_name << "() : DriverBase(HAL_LEGACY) {}\n";
}

}  // namespace vts
}  // namespace android
