/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.arch.persistence.room.solver.query.result

import android.arch.persistence.room.ext.L
import android.arch.persistence.room.ext.N
import android.arch.persistence.room.solver.CodeGenScope
import android.arch.persistence.room.writer.DaoWriter
import com.squareup.javapoet.FieldSpec

class CursorQueryResultBinder : QueryResultBinder(NO_OP_RESULT_ADAPTER) {
    override fun convertAndReturn(roomSQLiteQueryVar: String, dbField: FieldSpec,
                                  scope: CodeGenScope) {
        scope.builder().apply {
            addStatement("return $N.query($L)", DaoWriter.dbField, roomSQLiteQueryVar)
        }
    }
    companion object {
        private val NO_OP_RESULT_ADAPTER = object : QueryResultAdapter(null) {
            override fun convert(outVarName: String, cursorVarName: String, scope: CodeGenScope) {
            }
        }
    }
}
