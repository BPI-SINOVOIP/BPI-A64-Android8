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
import android.arch.persistence.room.ext.S
import android.arch.persistence.room.ext.T
import android.arch.persistence.room.ext.typeName
import android.arch.persistence.room.processor.Context
import android.arch.persistence.room.processor.ProcessorErrors
import android.arch.persistence.room.solver.CodeGenScope
import android.arch.persistence.room.verifier.QueryResultInfo
import android.arch.persistence.room.vo.Field
import android.arch.persistence.room.vo.FieldWithIndex
import android.arch.persistence.room.vo.Pojo
import android.arch.persistence.room.vo.RelationCollector
import android.arch.persistence.room.vo.Warning
import android.arch.persistence.room.writer.FieldReadWriteWriter
import com.squareup.javapoet.TypeName
import stripNonJava
import javax.lang.model.type.TypeMirror

/**
 * Creates the entity from the given info.
 * <p>
 * The info comes from the query processor so we know about the order of columns in the result etc.
 */
class PojoRowAdapter(context: Context, val info: QueryResultInfo,
                     val pojo: Pojo, out: TypeMirror) : RowAdapter(out) {
    val mapping: Mapping
    val relationCollectors: List<RelationCollector>

    init {
        // toMutableList documentation is not clear if it copies so lets be safe.
        val remainingFields = pojo.fields.mapTo(mutableListOf<Field>(), { it })
        val unusedColumns = arrayListOf<String>()
        val matchedFields = info.columns.map { column ->
            // first check remaining, otherwise check any. maybe developer wants to map the same
            // column into 2 fields. (if they want to post process etc)
            val field = remainingFields.firstOrNull { it.columnName == column.name } ?:
                    pojo.fields.firstOrNull { it.columnName == column.name }
            if (field == null) {
                unusedColumns.add(column.name)
                null
            } else {
                remainingFields.remove(field)
                field
            }
        }.filterNotNull()
        if (unusedColumns.isNotEmpty() || remainingFields.isNotEmpty()) {
            val warningMsg = ProcessorErrors.cursorPojoMismatch(
                    pojoTypeName = pojo.typeName,
                    unusedColumns = unusedColumns,
                    allColumns = info.columns.map { it.name },
                    unusedFields = remainingFields,
                    allFields = pojo.fields
            )
            context.logger.w(Warning.CURSOR_MISMATCH, null, warningMsg)
        }
        if (matchedFields.isEmpty()) {
            context.logger.e(ProcessorErrors.CANNOT_FIND_QUERY_RESULT_ADAPTER)
        }

        relationCollectors = RelationCollector.createCollectors(context, pojo.relations)

        mapping = Mapping(
                matchedFields = matchedFields,
                unusedColumns = unusedColumns,
                unusedFields = remainingFields
        )
    }

    fun relationTableNames(): List<String> {
        return relationCollectors.flatMap {
            val queryTableNames = it.loadAllQuery.tables.map { it.name }
            if (it.rowAdapter is PojoRowAdapter) {
                it.rowAdapter.relationTableNames() + queryTableNames
            } else {
                queryTableNames
            }
        }.distinct()
    }

    override fun onCursorReady(cursorVarName: String, scope: CodeGenScope) {
        relationCollectors.forEach { it.writeInitCode(scope) }
        mapping.fieldsWithIndices = mapping.matchedFields.map {
            val indexVar = scope.getTmpVar("_cursorIndexOf${it.name.stripNonJava().capitalize()}")
            scope.builder().addStatement("final $T $L = $L.getColumnIndexOrThrow($S)",
                    TypeName.INT, indexVar, cursorVarName, it.columnName)
            FieldWithIndex(field = it, indexVar = indexVar, alwaysExists = true)
        }
    }

    override fun convert(outVarName: String, cursorVarName: String, scope: CodeGenScope) {
        scope.builder().apply {
            FieldReadWriteWriter.readFromCursor(
                    outVar = outVarName,
                    outPojo = pojo,
                    cursorVar = cursorVarName,
                    fieldsWithIndices = mapping.fieldsWithIndices,
                    relationCollectors = relationCollectors,
                    scope = scope)
        }
    }

    override fun onCursorFinished(): ((CodeGenScope) -> Unit)? =
            if (relationCollectors.isEmpty()) {
                // it is important to return empty to notify that we don't need any post process
                // task
                null
            } else {
                { scope ->
                    relationCollectors.forEach { collector ->
                        collector.writeCollectionCode(scope)
                    }
                }
            }

    data class Mapping(val matchedFields: List<Field>,
                       val unusedColumns: List<String>,
                       val unusedFields: List<Field>) {
        // set when cursor is ready.
        lateinit var fieldsWithIndices: List<FieldWithIndex>
    }
}
