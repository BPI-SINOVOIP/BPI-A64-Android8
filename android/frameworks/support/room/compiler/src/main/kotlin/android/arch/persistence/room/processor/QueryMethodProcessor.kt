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

package android.arch.persistence.room.processor

import android.arch.persistence.room.Query
import android.arch.persistence.room.SkipQueryVerification
import android.arch.persistence.room.ext.hasAnnotation
import android.arch.persistence.room.parser.ParsedQuery
import android.arch.persistence.room.parser.QueryType
import android.arch.persistence.room.parser.SqlParser
import android.arch.persistence.room.solver.query.result.LiveDataQueryResultBinder
import android.arch.persistence.room.verifier.DatabaseVerificaitonErrors
import android.arch.persistence.room.verifier.DatabaseVerifier
import android.arch.persistence.room.vo.QueryMethod
import android.arch.persistence.room.vo.QueryParameter
import com.google.auto.common.AnnotationMirrors
import com.google.auto.common.MoreElements
import com.google.auto.common.MoreTypes
import com.squareup.javapoet.TypeName
import javax.lang.model.element.ExecutableElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind

class QueryMethodProcessor(baseContext: Context,
                           val containing: DeclaredType,
                           val executableElement: ExecutableElement,
                           val dbVerifier: DatabaseVerifier? = null) {
    val context = baseContext.fork(executableElement)

    fun process(): QueryMethod {
        val asMember = context.processingEnv.typeUtils.asMemberOf(containing, executableElement)
        val executableType = MoreTypes.asExecutable(asMember)

        val annotation = MoreElements.getAnnotationMirror(executableElement,
                Query::class.java).orNull()
        context.checker.check(annotation != null, executableElement,
                ProcessorErrors.MISSING_QUERY_ANNOTATION)

        val query = if (annotation != null) {
            val query = SqlParser.parse(
                    AnnotationMirrors.getAnnotationValue(annotation, "value").value.toString())
            context.checker.check(query.errors.isEmpty(), executableElement,
                    query.errors.joinToString("\n"))
            if (!executableElement.hasAnnotation(SkipQueryVerification::class)) {
                query.resultInfo = dbVerifier?.analyze(query.original)
            }
            if (query.resultInfo?.error != null) {
                context.logger.e(executableElement,
                        DatabaseVerificaitonErrors.cannotVerifyQuery(query.resultInfo!!.error!!))
            }

            context.checker.check(executableType.returnType.kind != TypeKind.ERROR,
                    executableElement, ProcessorErrors.CANNOT_RESOLVE_RETURN_TYPE,
                    executableElement)
            query
        } else {
            ParsedQuery.MISSING
        }

        val returnTypeName = TypeName.get(executableType.returnType)
        context.checker.notUnbound(returnTypeName, executableElement,
                ProcessorErrors.CANNOT_USE_UNBOUND_GENERICS_IN_QUERY_METHODS)

        if (query.type == QueryType.DELETE) {
            context.checker.check(
                    returnTypeName == TypeName.VOID || returnTypeName == TypeName.INT,
                    executableElement,
                    ProcessorErrors.DELETION_METHODS_MUST_RETURN_VOID_OR_INT
            )
        }
        val resultBinder = context.typeAdapterStore
                .findQueryResultBinder(executableType.returnType, query)
        context.checker.check(resultBinder.adapter != null || query.type != QueryType.SELECT,
                executableElement, ProcessorErrors.CANNOT_FIND_QUERY_RESULT_ADAPTER)
        if (resultBinder is LiveDataQueryResultBinder) {
            context.checker.check(query.type == QueryType.SELECT, executableElement,
                    ProcessorErrors.LIVE_DATA_QUERY_WITHOUT_SELECT)
        }

        val queryMethod = QueryMethod(
                element = executableElement,
                query = query,
                name = executableElement.simpleName.toString(),
                returnType = executableType.returnType,
                parameters = executableElement.parameters
                        .map { QueryParameterProcessor(
                                baseContext = context,
                                containing = containing,
                                element = it).process() },
                queryResultBinder = resultBinder)

        val missing = queryMethod.sectionToParamMapping
                .filter { it.second == null }
                .map { it.first.text }
        if (missing.isNotEmpty()) {
            context.logger.e(executableElement,
                    ProcessorErrors.missingParameterForBindVariable(missing))
        }

        val unused = queryMethod.parameters.filterNot { param ->
            queryMethod.sectionToParamMapping.any { it.second == param }
        }.map(QueryParameter::name)

        if (unused.isNotEmpty()) {
            context.logger.e(executableElement, ProcessorErrors.unusedQueryMethodParameter(unused))
        }
        return queryMethod
    }
}
