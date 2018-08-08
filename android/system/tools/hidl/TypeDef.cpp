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

#include "TypeDef.h"

#include <hidl-util/Formatter.h>
#include <android-base/logging.h>

namespace android {

TypeDef::TypeDef(const char* localName, const Location& location, Scope* parent, Type* type)
    : NamedType(localName, location, parent), mReferencedType(type) {}

const ScalarType *TypeDef::resolveToScalarType() const {
    CHECK(!"Should not be here");
    return NULL;
}

Type *TypeDef::referencedType() const {
    return mReferencedType;
}

bool TypeDef::isInterface() const {
    return false;
}

bool TypeDef::isEnum() const {
    CHECK(!"Should not be here");
    return false;
}

std::string TypeDef::typeName() const {
    return "typedef " + localName();
}

bool TypeDef::isTypeDef() const {
    return true;
}

bool TypeDef::needsEmbeddedReadWrite() const {
    CHECK(!"Should not be here");
    return false;
}

bool TypeDef::resultNeedsDeref() const {
    CHECK(!"Should not be here");
    return false;
}

status_t TypeDef::emitTypeDeclarations(Formatter &out) const {
    out << "typedef "
        << mReferencedType->getCppStackType()
        << " "
        << localName()
        << ";\n\n";

    return OK;
}

}  // namespace android

