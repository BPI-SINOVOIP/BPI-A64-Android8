// Copyright (C) 2017 The Android Open Source Project
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

#include <llvm/Object/ELFObjectFile.h>
#include <llvm/Object/ELFTypes.h>
#include <llvm/Object/SymbolSize.h>
#include <llvm/Support/Endian.h>
#include <llvm/Support/raw_ostream.h>

#include <regex>
#include <set>
#include <string>
#include <vector>

using llvm::object::ObjectFile;
using llvm::object::ELFObjectFile;
using llvm::object::ELFFile;
using llvm::object::ELFType;
using llvm::object::ELFDataTypeTypedefHelper;

namespace abi_util {

std::set<std::string> CollectAllExportedHeaders(
    const std::vector<std::string> &exported_header_dirs);

class VersionScriptParser {
 public:

  enum LineScope {
    global,
    local,
  };

  VersionScriptParser(const std::string &version_script,
                      const std::string &arch,
                      const std::string &api);
  bool Parse();

  const std::set<std::string> &GetFunctions();

  const std::set<std::string> &GetGlobVars();

  const std::set<std::string> &GetFunctionRegexs();

  const std::set<std::string> &GetGlobVarRegexs();

 private:

  bool ParseInnerBlock(std::ifstream &symbol_ifstream);

  LineScope GetLineScope(std::string &line, LineScope scope);

  bool ParseSymbolLine(const std::string &line);

  bool SymbolInArchAndApiVersion(const std::string &line,
                                 const std::string &arch, int api);

  bool SymbolExported(const std::string &line, const std::string &arch,
                      int api);

  int ApiStrToInt(const std::string &api);

  void AddToVars(std::string &symbol);

  void AddToFunctions(std::string &symbol);

 private:
  const std::string &version_script_;
  const std::string &arch_;
  std::set<std::string> functions_;
  std::set<std::string> globvars_;
  // Added to speed up version script parsing and linking.
  std::set<std::string> function_regexs_;
  std::set<std::string> globvar_regexs_;
  int api_;
};

inline std::string FindAndReplace(const std::string &candidate_str,
                                  const std::string &find_str,
                                  const std::string &replace_str) {
  // Find all matches of find_str in candidate_str and return a new string with
  // all the matches replaced with replace_str
  std::regex match_expr(find_str);
  return std::regex_replace(candidate_str, match_expr, replace_str);
}


class SoFileParser {
public:
    static std::unique_ptr<SoFileParser> Create(const ObjectFile *obj);
    virtual const std::set<std::string> &GetFunctions() const = 0;
    virtual const std::set<std::string> &GetGlobVars() const = 0;
    virtual ~SoFileParser() {};
    virtual void GetSymbols() = 0;
};

template<typename T>
class ELFSoFileParser : public SoFileParser {
 public:
  const std::set<std::string> &GetFunctions() const override;

  const std::set<std::string> &GetGlobVars() const override;

  LLVM_ELF_IMPORT_TYPES_ELFT(T)
  typedef ELFFile<T> ELFO;
  typedef typename ELFO::Elf_Sym Elf_Sym;

  ELFSoFileParser(const ELFObjectFile<T> *obj) : obj_(obj) {}
  virtual ~ELFSoFileParser() override {};
  void GetSymbols() override;
 private:
  const ELFObjectFile<T> *obj_;
  std::set<std::string> functions_;
  std::set<std::string> globvars_;

 private:
  bool IsSymbolExported(const Elf_Sym *elf_sym) const;
};

} // namespace abi_util
