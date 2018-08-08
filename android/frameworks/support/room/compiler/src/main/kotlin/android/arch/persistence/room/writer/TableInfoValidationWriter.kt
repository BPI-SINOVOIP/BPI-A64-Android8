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

package android.arch.persistence.room.writer

import android.arch.persistence.room.ext.CommonTypeNames
import android.arch.persistence.room.ext.L
import android.arch.persistence.room.ext.N
import android.arch.persistence.room.ext.RoomTypeNames
import android.arch.persistence.room.ext.S
import android.arch.persistence.room.ext.T
import android.arch.persistence.room.ext.typeName
import android.arch.persistence.room.parser.SQLTypeAffinity
import android.arch.persistence.room.solver.CodeGenScope
import android.arch.persistence.room.vo.Entity
import com.squareup.javapoet.ParameterSpec
import com.squareup.javapoet.ParameterizedTypeName
import stripNonJava
import java.util.Arrays
import java.util.HashMap
import java.util.HashSet

class TableInfoValidationWriter(val entity : Entity) {
    fun write(dbParam : ParameterSpec, scope : CodeGenScope) {
        val suffix = entity.tableName.stripNonJava().capitalize()
        val expectedInfoVar = scope.getTmpVar("_info$suffix")
        scope.builder().apply {
            val columnListVar = scope.getTmpVar("_columns$suffix")
            val columnListType = ParameterizedTypeName.get(HashMap::class.typeName(),
                    CommonTypeNames.STRING, RoomTypeNames.TABLE_INFO_COLUMN)

            addStatement("final $T $L = new $T($L)", columnListType, columnListVar,
                    columnListType, entity.fields.size)
            entity.fields.forEach { field ->
                addStatement("$L.put($S, new $T($S, $S, $L, $L))",
                        columnListVar, field.columnName, RoomTypeNames.TABLE_INFO_COLUMN,
                        /*name*/ field.columnName,
                        /*type*/ field.affinity?.name ?: SQLTypeAffinity.TEXT.name,
                        /*nonNull*/ field.nonNull,
                        /*pkeyPos*/ entity.primaryKey.fields.indexOf(field) + 1)
            }

            val foreignKeySetVar = scope.getTmpVar("_foreignKeys$suffix")
            val foreignKeySetType = ParameterizedTypeName.get(HashSet::class.typeName(),
                    RoomTypeNames.TABLE_INFO_FOREIGN_KEY)
            addStatement("final $T $L = new $T($L)", foreignKeySetType, foreignKeySetVar,
                    foreignKeySetType, entity.foreignKeys.size)
            entity.foreignKeys.forEach {
                val myColumnNames = it.childFields
                        .joinToString(",") { "\"${it.columnName}\"" }
                val refColumnNames = it.parentColumns
                        .joinToString(",") { "\"$it\"" }
                addStatement("$L.add(new $T($S, $S, $S," +
                        "$T.asList($L), $T.asList($L)))", foreignKeySetVar,
                        RoomTypeNames.TABLE_INFO_FOREIGN_KEY,
                        /*parent table*/ it.parentTable,
                        /*on delete*/ it.onDelete.sqlName,
                        /*on update*/ it.onUpdate.sqlName,
                        Arrays::class.typeName(),
                        /*parent names*/ myColumnNames,
                        Arrays::class.typeName(),
                        /*parent column names*/ refColumnNames)
            }

            addStatement("final $T $L = new $T($S, $L, $L)",
                    RoomTypeNames.TABLE_INFO, expectedInfoVar, RoomTypeNames.TABLE_INFO,
                    entity.tableName, columnListVar, foreignKeySetVar)

            val existingVar = scope.getTmpVar("_existing$suffix")
            addStatement("final $T $L = $T.read($N, $S)",
                    RoomTypeNames.TABLE_INFO, existingVar, RoomTypeNames.TABLE_INFO,
                    dbParam, entity.tableName)

            beginControlFlow("if (! $L.equals($L))", expectedInfoVar, existingVar).apply {
                addStatement("throw new $T($S + $L + $S + $L)",
                        IllegalStateException::class.typeName(),
                        "Migration didn't properly handle ${entity.tableName}" +
                                "(${entity.element.qualifiedName}).\n Expected:\n",
                        expectedInfoVar, "\n Found:\n", existingVar)
            }
            endControlFlow()
        }
    }
}
