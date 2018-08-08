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

@file:Suppress("HasPlatformType")

package android.arch.persistence.room.solver

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Database
import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey
import android.arch.persistence.room.Query
import android.arch.persistence.room.RoomProcessor
import android.arch.persistence.room.TypeConverter
import android.arch.persistence.room.TypeConverters
import android.arch.persistence.room.ext.RoomTypeNames
import android.arch.persistence.room.ext.S
import android.arch.persistence.room.ext.T
import android.arch.persistence.room.processor.ProcessorErrors.CANNOT_BIND_QUERY_PARAMETER_INTO_STMT
import com.google.common.truth.Truth
import com.google.testing.compile.CompileTester
import com.google.testing.compile.JavaFileObjects
import com.google.testing.compile.JavaSourcesSubjectFactory
import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterSpec
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import javax.lang.model.element.Modifier
import javax.tools.JavaFileObject

@RunWith(JUnit4::class)
class CustomTypeConverterResolutionTest {
    fun TypeSpec.toJFO() : JavaFileObject {
        return JavaFileObjects.forSourceString("foo.bar.${this.name}",
                "package foo.bar;\n" + toString())
    }

    companion object {
        val ENTITY = ClassName.get("foo.bar", "MyEntity")
        val DB = ClassName.get("foo.bar", "MyDb")
        val DAO = ClassName.get("foo.bar", "MyDao")

        val CUSTOM_TYPE = ClassName.get("foo.bar", "CustomType")
        val CUSTOM_TYPE_JFO = JavaFileObjects.forSourceLines(CUSTOM_TYPE.toString(),
                """
                package ${CUSTOM_TYPE.packageName()};
                public class ${CUSTOM_TYPE.simpleName()} {
                    public int value;
                }
                """)
        val CUSTOM_TYPE_CONVERTER = ClassName.get("foo.bar", "MyConverter")
        val CUSTOM_TYPE_CONVERTER_JFO = JavaFileObjects.forSourceLines(
                CUSTOM_TYPE_CONVERTER.toString(),
                """
                package ${CUSTOM_TYPE_CONVERTER.packageName()};
                public class ${CUSTOM_TYPE_CONVERTER.simpleName()} {
                    @${TypeConverter::class.java.canonicalName}
                    public static $CUSTOM_TYPE toCustom(int value) {
                        return null;
                    }
                    @${TypeConverter::class.java.canonicalName}
                    public static int fromCustom($CUSTOM_TYPE input) {
                        return 0;
                    }
                }
                """)
    }

    @Test
    fun useFromDatabase_forEntity() {
        val entity = createEntity(hasCustomField = true)
        val database = createDatabase(hasConverters = true, hasDao = true)
        val dao = createDao(hasQueryReturningEntity = true, hasQueryWithCustomParam = true)
        run(entity.toJFO(), dao.toJFO(), database.toJFO()).compilesWithoutError()
    }

    @Test
    fun useFromDatabase_forQueryParameter() {
        val entity = createEntity()
        val database = createDatabase(hasConverters = true, hasDao = true)
        val dao = createDao(hasQueryWithCustomParam = true)
        run(entity.toJFO(), dao.toJFO(), database.toJFO()).compilesWithoutError()
    }

    @Test
    fun useFromDatabase_forReturnValue() {
        val entity = createEntity(hasCustomField = true)
        val database = createDatabase(hasConverters = true, hasDao = true)
        val dao = createDao(hasQueryReturningEntity = true)
        run(entity.toJFO(), dao.toJFO(), database.toJFO()).compilesWithoutError()
    }

    @Test
    fun useFromDao_forQueryParameter() {
        val entity = createEntity()
        val database = createDatabase(hasDao = true)
        val dao = createDao(hasConverters = true, hasQueryReturningEntity = true,
                hasQueryWithCustomParam = true)
        run(entity.toJFO(), dao.toJFO(), database.toJFO()).compilesWithoutError()
    }

    @Test
    fun useFromEntity_forReturnValue() {
        val entity = createEntity(hasCustomField = true, hasConverters = true)
        val database = createDatabase(hasDao = true)
        val dao = createDao(hasQueryReturningEntity = true)
        run(entity.toJFO(), dao.toJFO(), database.toJFO()).compilesWithoutError()
    }

    @Test
    fun useFromEntityField_forReturnValue() {
        val entity = createEntity(hasCustomField = true, hasConverterOnField = true)
        val database = createDatabase(hasDao = true)
        val dao = createDao(hasQueryReturningEntity = true)
        run(entity.toJFO(), dao.toJFO(), database.toJFO()).compilesWithoutError()
    }

    @Test
    fun useFromEntity_forQueryParameter() {
        val entity = createEntity(hasCustomField = true, hasConverters = true)
        val database = createDatabase(hasDao = true)
        val dao = createDao(hasQueryWithCustomParam = true)
        run(entity.toJFO(), dao.toJFO(), database.toJFO())
                .failsToCompile().withErrorContaining(CANNOT_BIND_QUERY_PARAMETER_INTO_STMT)
    }

    @Test
    fun useFromEntityField_forQueryParameter() {
        val entity = createEntity(hasCustomField = true, hasConverterOnField = true)
        val database = createDatabase(hasDao = true)
        val dao = createDao(hasQueryWithCustomParam = true)
        run(entity.toJFO(), dao.toJFO(), database.toJFO())
                .failsToCompile().withErrorContaining(CANNOT_BIND_QUERY_PARAMETER_INTO_STMT)
    }

    @Test
    fun useFromQueryMethod_forQueryParameter() {
        val entity = createEntity()
        val database = createDatabase(hasDao = true)
        val dao = createDao(hasQueryWithCustomParam = true, hasMethodConverters = true)
        run(entity.toJFO(), dao.toJFO(), database.toJFO()).compilesWithoutError()
    }

    @Test
    fun useFromQueryParameter_forQueryParameter() {
        val entity = createEntity()
        val database = createDatabase(hasDao = true)
        val dao = createDao(hasQueryWithCustomParam = true, hasParameterConverters = true)
        run(entity.toJFO(), dao.toJFO(), database.toJFO()).compilesWithoutError()
    }

    fun run(vararg jfos : JavaFileObject): CompileTester {
        return Truth.assertAbout(JavaSourcesSubjectFactory.javaSources())
                .that(jfos.toList() + CUSTOM_TYPE_JFO + CUSTOM_TYPE_CONVERTER_JFO)
                .processedWith(RoomProcessor())
    }

    private fun createEntity(hasCustomField : Boolean = false,
                             hasConverters : Boolean = false,
                             hasConverterOnField : Boolean = false) : TypeSpec {
        if (hasConverterOnField && hasConverters) {
            throw IllegalArgumentException("cannot have both converters")
        }
        return TypeSpec.classBuilder(ENTITY).apply {
            addAnnotation(Entity::class.java)
            addModifiers(Modifier.PUBLIC)
            if (hasCustomField) {
                addField(FieldSpec.builder(CUSTOM_TYPE, "myCustomField", Modifier.PUBLIC).apply {
                    if (hasConverterOnField) {
                        addAnnotation(createConvertersAnnotation())
                    }
                }.build())
            }
            if (hasConverters) {
                addAnnotation(createConvertersAnnotation())
            }
            addField(FieldSpec.builder(TypeName.INT, "id", Modifier.PUBLIC).apply {
                addAnnotation(PrimaryKey::class.java)
            }.build())
        }.build()
    }

    private fun createDatabase(hasConverters : Boolean = false,
                               hasDao : Boolean = false) : TypeSpec {
        return TypeSpec.classBuilder(DB).apply {
            addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
            superclass(RoomTypeNames.ROOM_DB)
            if (hasConverters) {
                addAnnotation(createConvertersAnnotation())
            }
            addField(FieldSpec.builder(TypeName.INT, "id", Modifier.PUBLIC).apply {
                addAnnotation(PrimaryKey::class.java)
            }.build())
            if (hasDao) {
                addMethod(MethodSpec.methodBuilder("getDao").apply {
                    addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    returns(DAO)
                }.build())
            }
            addAnnotation(
                    AnnotationSpec.builder(Database::class.java).apply {
                        addMember("entities", "{$T.class}", ENTITY)
                        addMember("version", "42")
                    }.build()
            )
        }.build()
    }

    private fun createDao(hasConverters : Boolean = false,
                          hasQueryReturningEntity : Boolean = false,
                          hasQueryWithCustomParam : Boolean = false,
                          hasMethodConverters : Boolean = false,
                          hasParameterConverters : Boolean = false) : TypeSpec {
        val annotationCount = listOf(hasMethodConverters, hasConverters, hasParameterConverters)
                .map { if (it) 1 else 0 }.sum()
        if (annotationCount > 1) {
            throw IllegalArgumentException("cannot set both of these")
        }
        if (hasParameterConverters && !hasQueryWithCustomParam) {
            throw IllegalArgumentException("inconsistent")
        }
        return TypeSpec.classBuilder(DAO).apply {
            addAnnotation(Dao::class.java)
            addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
            if (hasConverters) {
                addAnnotation(createConvertersAnnotation())
            }
            if (hasQueryReturningEntity) {
                addMethod(MethodSpec.methodBuilder("loadAll").apply {
                    addAnnotation(AnnotationSpec.builder(Query::class.java).apply {
                        addMember("value", S, "SELECT * FROM ${ENTITY.simpleName()} LIMIT 1")
                    }.build())
                    addModifiers(Modifier.ABSTRACT)
                    returns(ENTITY)
                }.build())
            }
            if (hasQueryWithCustomParam) {
                addMethod(MethodSpec.methodBuilder("queryWithCustom").apply {
                    addAnnotation(AnnotationSpec.builder(Query::class.java).apply {
                        addMember("value", S, "SELECT COUNT(*) FROM ${ENTITY.simpleName()} where" +
                                " id IN(:customs)")
                    }.build())
                    if (hasMethodConverters) {
                        addAnnotation(createConvertersAnnotation())
                    }
                    addParameter(ParameterSpec.builder(CUSTOM_TYPE, "customs").apply {
                        if (hasParameterConverters) {
                            addAnnotation(createConvertersAnnotation())
                        }
                    }.build())
                    addModifiers(Modifier.ABSTRACT)
                    returns(TypeName.INT)
                }.build())
            }
        }.build()
    }

    private fun createConvertersAnnotation(): AnnotationSpec {
        return AnnotationSpec.builder(TypeConverters::class.java)
                .addMember("value", "$T.class", CUSTOM_TYPE_CONVERTER).build()
    }
}
