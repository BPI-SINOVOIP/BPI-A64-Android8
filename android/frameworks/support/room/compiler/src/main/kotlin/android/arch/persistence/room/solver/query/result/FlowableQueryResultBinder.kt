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
import android.arch.persistence.room.ext.RoomRxJava2TypeNames
import android.arch.persistence.room.ext.T
import android.arch.persistence.room.ext.arrayTypeName
import android.arch.persistence.room.ext.typeName
import android.arch.persistence.room.solver.CodeGenScope
import android.arch.persistence.room.writer.DaoWriter
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeSpec
import javax.lang.model.element.Modifier
import javax.lang.model.type.TypeMirror

/**
 * Binds the result as an RxJava2 Flowable.
 */
class FlowableQueryResultBinder(val typeArg: TypeMirror, val queryTableNames: List<String>,
                                adapter: QueryResultAdapter?)
    : BaseObservableQueryResultBinder(adapter) {
    override fun convertAndReturn(roomSQLiteQueryVar: String, dbField: FieldSpec,
                                  scope: CodeGenScope) {
        val callableImpl = TypeSpec.anonymousClassBuilder("").apply {
            val typeName = typeArg.typeName()
            superclass(ParameterizedTypeName.get(java.util.concurrent.Callable::class.typeName(),
                    typeName))
            addMethod(MethodSpec.methodBuilder("call").apply {
                returns(typeName)
                addException(Exception::class.typeName())
                addModifiers(Modifier.PUBLIC)
                createRunQueryAndReturnStatements(this, roomSQLiteQueryVar, scope)
            }.build())
            addMethod(createFinalizeMethod(roomSQLiteQueryVar))
        }.build()
        scope.builder().apply {
            val tableNamesList = queryTableNames.joinToString(",") { "\"$it\"" }
            addStatement("return $T.createFlowable($N, new $T{$L}, $L)",
                    RoomRxJava2TypeNames.RX_ROOM, DaoWriter.dbField,
                    String::class.arrayTypeName(), tableNamesList, callableImpl)
        }
    }
}
