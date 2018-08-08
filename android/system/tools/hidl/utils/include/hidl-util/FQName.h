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

#ifndef FQNAME_H_

#define FQNAME_H_

#include <android-base/macros.h>
#include <string>
#include <vector>

namespace android {

struct FQName {
    explicit FQName();
    explicit FQName(const std::string &s);

    FQName(const std::string &package,
           const std::string &version,
           const std::string &name,
           const std::string &valueName = "");

    // a synonym to FQName(names.join("."))
    FQName(const std::vector<std::string> &names);

    FQName(const FQName& other);

    bool isValid() const;
    bool isIdentifier() const;
    bool setTo(const std::string &s);

    void applyDefaults(
            const std::string &defaultPackage,
            const std::string &defaultVersion);

    std::string package() const;
    // Return version in the form "@1.0" if it is present, otherwise empty string.
    std::string atVersion() const;
    // Return version in the form "1.0" if it is present, otherwise empty string.
    std::string version() const;
    // Return version in the form "V1_0" if it is present, otherwise empty string.
    std::string sanitizedVersion() const;
    // Return true only if version is present.
    bool hasVersion() const;

    // The next two methods return the name part of the FQName, that is, the
    // part after the version field.  For example:
    //
    // package android.hardware.tests.foo@1.0;
    // interface IFoo {
    //    struct bar {
    //        struct baz {
    //            ...
    //        };
    //    };
    // };
    //
    // package android.hardware.tests.bar@1.0;
    // import android.hardware.tests.foo@1.0;
    // interface {
    //    struct boo {
    //        IFoo.bar.baz base;
    //    };
    // }
    //
    // The FQName for base is android.hardware.tests.foo@1.0::IFoo.bar.baz; so
    // FQName::name() will return "IFoo.bar.baz". FQName::names() will return
    // std::vector<std::string>{"IFoo","bar","baz"}

    std::string name() const;
    std::vector<std::string> names() const;

    // The next two methods returns two parts of the FQName, that is,
    // the first part package + version + name, the second part valueName.
    FQName typeName() const;
    std::string valueName() const;

    // has package version and name
    bool isFullyQualified() const;

    // true if:
    // 1. (package)?(version)?(name):(valueName)
    // 2. (valueName), aka a single identifier
    bool isValidValueName() const;

    void print() const;
    std::string string() const;

    bool operator<(const FQName &other) const;
    bool operator==(const FQName &other) const;
    bool operator!=(const FQName &other) const;

    // Must be called on an interface
    // android.hardware.foo@1.0::IBar
    // -> Bar
    std::string getInterfaceBaseName() const;

    // Must be called on an interface
    // android.hardware.foo@1.0::IBar
    // -> IBar
    std::string getInterfaceName() const;

    // Must be called on an interface
    // android.hardware.foo@1.0::IBar
    // -> IHwBar
    std::string getInterfaceHwName() const;

    // Must be called on an interface
    // android.hardware.foo@1.0::IBar
    // -> BpBar
    std::string getInterfaceProxyName() const;

    // Must be called on an interface
    // android.hardware.foo@1.0::IBar
    // -> BnBar
    std::string getInterfaceStubName() const;

    // Must be called on an interface
    // android.hardware.foo@1.0::IBar
    // -> BsBar
    std::string getInterfacePassthroughName() const;

    // Must be called on an interface
    // android.hardware.foo@1.0::IBar
    // -> android.hardware.foo@1.0::BpBar
    FQName getInterfaceProxyFqName() const;

    // Must be called on an interface
    // android.hardware.foo@1.0::IBar
    // -> android.hardware.foo@1.0::BnBar
    FQName getInterfaceStubFqName() const;

    // Must be called on an interface
    // android.hardware.foo@1.0::IBar
    // -> android.hardware.foo@1.0::BsBar
    FQName getInterfacePassthroughFqName() const;

    // Replace whatever after :: with "types"
    // android.hardware.foo@1.0::Abc.Type:VALUE
    // -> android.hardware.foo@1.0::types
    FQName getTypesForPackage() const;

    // android.hardware.foo@1.0::Abc.Type:VALUE
    // -> android.hardware.foo@1.0
    FQName getPackageAndVersion() const;

    // the following comments all assume that the FQName
    // is android.hardware.foo@1.0::IBar.Baz.Bam

    // returns highest type in the hidl namespace, i.e.
    // android.hardware.foo@1.0::IBar
    FQName getTopLevelType() const;

    // returns an unambiguous fully qualified name which can be
    // baked into a token, i.e.
    // android_hardware_Foo_V1_0_IBar_Baz
    std::string tokenName() const;

    // Returns an absolute C++ namespace prefix, i.e.
    // ::android::hardware::Foo::V1_0.
    std::string cppNamespace() const;

    // Returns a name qualified assuming we are in cppNamespace, i.e.
    // IBar::Baz.
    std::string cppLocalName() const;

    // Returns a fully qualified absolute C++ type name, i.e.
    // ::android::hardware::Foo::V1_0::IBar::Baz.
    std::string cppName() const;

    // Returns the java package name, i.e. "android.hardware.Foo.V1_0".
    std::string javaPackage() const;

    // Returns the fully qualified java type name,
    // i.e. "android.hardware.Foo.V1_0.IBar.Baz"
    std::string javaName() const;

    bool endsWith(const FQName &other) const;

    // If this is android.hardware@1.0::IFoo
    // package = "and" -> false
    // package = "android" -> true
    // package = "android.hardware@1.0" -> false
    bool inPackage(const std::string &package) const;

    void getPackageComponents(std::vector<std::string> *components) const;

    void getPackageAndVersionComponents(
            std::vector<std::string> *components,
            bool cpp_compatible) const;

    // return major and minor version if they exist, else abort program.
    // Existence of version can be checked via hasVersion().
    size_t getPackageMajorVersion() const;
    size_t getPackageMinorVersion() const;

    // minor-- if result doesn't underflow, else abort.
    FQName downRev() const;

private:
    bool mValid;
    bool mIsIdentifier;
    std::string mPackage;
    // mMajor == 0 means empty.
    size_t mMajor = 0;
    size_t mMinor = 0;
    std::string mName;
    std::string mValueName;

    void setVersion(const std::string &v);
    void clearVersion();
    void parseVersion(const std::string &majorStr, const std::string &minorStr);
};

static const FQName gIBaseFqName = FQName{"android.hidl.base@1.0::IBase"};
static const FQName gIBasePackageFqName = FQName{"android.hidl.base"};
static const FQName gIManagerFqName = FQName{"android.hidl.manager@1.0::IServiceManager"};
static const FQName gIManagerPackageFqName = FQName{"android.hidl.manager"};

}  // namespace android

#endif  // FQNAME_H_
