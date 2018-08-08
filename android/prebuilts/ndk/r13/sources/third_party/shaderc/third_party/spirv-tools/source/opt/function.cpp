// Copyright (c) 2016 Google Inc.
//
// Permission is hereby granted, free of charge, to any person obtaining a
// copy of this software and/or associated documentation files (the
// "Materials"), to deal in the Materials without restriction, including
// without limitation the rights to use, copy, modify, merge, publish,
// distribute, sublicense, and/or sell copies of the Materials, and to
// permit persons to whom the Materials are furnished to do so, subject to
// the following conditions:
//
// The above copyright notice and this permission notice shall be included
// in all copies or substantial portions of the Materials.
//
// MODIFICATIONS TO THIS FILE MAY MEAN IT NO LONGER ACCURATELY REFLECTS
// KHRONOS STANDARDS. THE UNMODIFIED, NORMATIVE VERSIONS OF KHRONOS
// SPECIFICATIONS AND HEADER INFORMATION ARE LOCATED AT
//    https://www.khronos.org/registry/
//
// THE MATERIALS ARE PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
// EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
// IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
// CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
// TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
// MATERIALS OR THE USE OR OTHER DEALINGS IN THE MATERIALS.

#include "function.h"

namespace spvtools {
namespace ir {

void Function::ForEachInst(const std::function<void(Instruction*)>& f) {
  def_inst_.ForEachInst(f);
  for (auto& param : params_) param.ForEachInst(f);
  for (auto& bb : blocks_) bb.ForEachInst(f);
  end_inst_.ForEachInst(f);
}

void Function::ToBinary(std::vector<uint32_t>* binary, bool skip_nop) const {
  def_inst_.ToBinary(binary, skip_nop);
  for (const auto& param : params_) param.ToBinary(binary, skip_nop);
  for (const auto& bb : blocks_) bb.ToBinary(binary, skip_nop);
  end_inst_.ToBinary(binary, skip_nop);
}

}  // namespace ir
}  // namespace spvtools
