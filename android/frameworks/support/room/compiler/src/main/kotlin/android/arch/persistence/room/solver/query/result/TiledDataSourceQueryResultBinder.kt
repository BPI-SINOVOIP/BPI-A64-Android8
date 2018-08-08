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

import android.arch.persistence.room.ext.AndroidTypeNames
import android.arch.persistence.room.ext.CommonTypeNames
import android.arch.persistence.room.ext.L
import android.arch.persistence.room.ext.N
import android.arch.persistence.room.ext.RoomTypeNames
import android.arch.persistence.room.ext.typeName
import android.arch.persistence.room.solver.CodeGenScope
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import javax.lang.model.element.Modifier

class TiledDataSourceQueryResultBinder(val listAdapter : ListQueryResultAdapter?,
                                       val tableNames : List<String>)
            : QueryResultBinder(listAdapter) {
    val itemTypeName : TypeName = listAdapter?.rowAdapter?.out?.typeName() ?: TypeName.OBJECT
    val typeName : ParameterizedTypeName = ParameterizedTypeName.get(
            RoomTypeNames.LIMIT_OFFSET_DATA_SOURCE, itemTypeName)
    override fun convertAndReturn(roomSQLiteQueryVar: String, dbField: FieldSpec,
                                  scope: CodeGenScope) {
        val tableNamesList = tableNames.joinToString(",") { "\"$it\"" }
        val spec = TypeSpec.anonymousClassBuilder("$N, $L, $L",
                dbField, roomSQLiteQueryVar, tableNamesList).apply {
            superclass(typeName)
            addMethod(createConvertRowsMethod(scope))
        }.build()
        scope.builder().apply {
            addStatement("return $L", spec)
        }
    }

    fun createConvertRowsMethod(scope : CodeGenScope): MethodSpec =
            MethodSpec.methodBuilder("convertRows").apply {
                addAnnotation(Override::class.java)
                addModifiers(Modifier.PROTECTED)
                returns(ParameterizedTypeName.get(CommonTypeNames.LIST, itemTypeName))
                val cursorParam = ParameterSpec.builder(AndroidTypeNames.CURSOR, "cursor")
                        .build()
                addParameter(cursorParam)
                val resultVar = scope.getTmpVar("_res")
                val rowsScope = scope.fork()
                listAdapter?.convert(resultVar, cursorParam.name, rowsScope)
                addCode(rowsScope.builder().build())
                addStatement("return $L", resultVar)
            }.build()
}