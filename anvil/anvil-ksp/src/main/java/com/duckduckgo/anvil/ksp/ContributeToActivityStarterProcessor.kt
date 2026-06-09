/*
 * Copyright (c) 2024 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.anvil.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo

@OptIn(KotlinPoetKspPreview::class)
class ContributeToActivityStarterProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    companion object {
        private const val CONTRIBUTE_TO_ACTIVITY_STARTER =
            "com.duckduckgo.anvil.annotations.ContributeToActivityStarter"
        private const val DUCKDUCKGO_ACTIVITY =
            "com.duckduckgo.common.ui.DuckDuckGoActivity"

        private val CONTRIBUTES_MULTIBINDING_CLASS =
            ClassName("com.squareup.anvil.annotations", "ContributesMultibinding")
        private val APP_SCOPE_CLASS =
            ClassName("com.duckduckgo.di.scopes", "AppScope")
        private val PARAM_TO_ACTIVITY_MAPPER_CLASS =
            ClassName("com.duckduckgo.navigation.api", "GlobalActivityStarter", "ParamToActivityMapper")
        private val ACTIVITY_PARAMS_CLASS =
            ClassName("com.duckduckgo.navigation.api", "GlobalActivityStarter", "ActivityParams")
        private val DEEPLINK_ACTIVITY_PARAMS_CLASS =
            ClassName("com.duckduckgo.navigation.api", "GlobalActivityStarter", "DeeplinkActivityParams")
        private val APP_COMPAT_ACTIVITY_CLASS =
            ClassName("androidx.appcompat.app", "AppCompatActivity")
        private val MOSHI_CLASS =
            ClassName("com.squareup.moshi", "Moshi")
        private val KOTLIN_JSON_ADAPTER_FACTORY_CLASS =
            ClassName("com.squareup.moshi.kotlin.reflect", "KotlinJsonAdapterFactory")
        private val MOSHI_TYPES_CLASS =
            ClassName("com.squareup.moshi", "Types")
        private val INJECT_CLASS =
            ClassName("javax.inject", "Inject")
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(CONTRIBUTE_TO_ACTIVITY_STARTER).toList()
        val deferred = mutableListOf<KSAnnotated>()

        for (symbol in symbols) {
            if (!symbol.validate()) {
                deferred.add(symbol)
                continue
            }

            if (symbol !is KSClassDeclaration) continue

            processClass(symbol)
        }

        return deferred
    }

    private fun processClass(classDeclaration: KSClassDeclaration) {
        // Validation: must extend DuckDuckGoActivity
        if (!classDeclaration.extendsDuckDuckGoActivity()) {
            logger.error(
                "${classDeclaration.qualifiedName?.asString()} must extend $DUCKDUCKGO_ACTIVITY",
                classDeclaration,
            )
            return
        }

        val className = classDeclaration.simpleName.asString()
        val packageName = classDeclaration.packageName.asString()
        val fileName = "${className}_ActivityMapper"

        val activityClassName = classDeclaration.toClassName()

        // Find all @ContributeToActivityStarter annotations (it's @Repeatable)
        val annotations = classDeclaration.annotations.filter { ann ->
            ann.annotationType.resolve().declaration.qualifiedName?.asString() == CONTRIBUTE_TO_ACTIVITY_STARTER
        }.toList()

        val fileSpecBuilder = FileSpec.builder(packageName, fileName)

        for (annotation in annotations) {
            val paramsType = annotation.getArgumentType("paramsType") ?: run {
                logger.error("Could not resolve paramsType parameter", classDeclaration)
                return
            }
            val paramsClassName = paramsType.toClassName()
            val screenName = annotation.getArgumentString("screenName").orEmpty()

            val mapperClassName = "${className}_${paramsClassName.simpleName}_Mapper"

            val typeSpec = createMapperClass(
                mapperClassName = mapperClassName,
                activityClassName = activityClassName,
                paramsClassName = paramsClassName,
                screenName = screenName,
            )

            fileSpecBuilder.addType(typeSpec)
        }

        val fileSpec = fileSpecBuilder.build()

        val containingFile = classDeclaration.containingFile
        val dependencies = if (containingFile != null) {
            Dependencies(aggregating = false, containingFile)
        } else {
            Dependencies(aggregating = false)
        }

        fileSpec.writeTo(codeGenerator, dependencies)
    }

    private fun createMapperClass(
        mapperClassName: String,
        activityClassName: ClassName,
        paramsClassName: ClassName,
        screenName: String,
    ): TypeSpec {
        val constructor = FunSpec.constructorBuilder()
            .addAnnotation(INJECT_CLASS)
            .build()

        val moshiProperty = PropertySpec.builder("moshi", MOSHI_CLASS, KModifier.PRIVATE)
            .initializer("Moshi.Builder().add(%T()).build()", KOTLIN_JSON_ADAPTER_FACTORY_CLASS)
            .build()

        val mapActivityParamsFun = FunSpec.builder("map")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("activityParams", ACTIVITY_PARAMS_CLASS)
            .returns(
                Class::class.asClassName().parameterizedBy(
                    WildcardTypeName.producerOf(APP_COMPAT_ACTIVITY_CLASS),
                ).copy(nullable = true),
            )
            .addCode(
                """
                    return if (activityParams is %T) {
                        %T::class.java
                    } else {
                        null
                    }
                """.trimIndent(),
                paramsClassName,
                activityClassName,
            )
            .build()

        return TypeSpec.classBuilder(mapperClassName)
            .addAnnotation(
                AnnotationSpec.builder(CONTRIBUTES_MULTIBINDING_CLASS)
                    .addMember("scope = %T::class", APP_SCOPE_CLASS)
                    .build(),
            )
            .primaryConstructor(constructor)
            .addSuperinterface(PARAM_TO_ACTIVITY_MAPPER_CLASS)
            .addProperty(moshiProperty)
            .addFunction(mapActivityParamsFun)
            .apply {
                if (screenName.isBlank()) {
                    addFunction(emptyDeeplinkMapper())
                } else {
                    addFunction(createDeeplinkMapper(paramsClassName, screenName))
                    addFunction(createTryCreateObjectInstance())
                    addFunction(createTryCreateActivityParams())
                }
            }
            .build()
    }

    private fun emptyDeeplinkMapper(): FunSpec {
        return FunSpec.builder("map")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("deeplinkActivityParams", DEEPLINK_ACTIVITY_PARAMS_CLASS)
            .returns(ACTIVITY_PARAMS_CLASS.copy(nullable = true))
            .addCode("return null\n")
            .build()
    }

    private fun createDeeplinkMapper(paramsClassName: ClassName, screenName: String): FunSpec {
        return FunSpec.builder("map")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("deeplinkActivityParams", DEEPLINK_ACTIVITY_PARAMS_CLASS)
            .returns(ACTIVITY_PARAMS_CLASS.copy(nullable = true))
            .addCode(
                """
                    val screenName = deeplinkActivityParams.screenName
                    if (screenName.isNullOrEmpty()) {
                        return null
                    }

                    val definedScreenName = %S
                    if (definedScreenName.isNullOrEmpty()) {
                        return null
                    }

                    return if (screenName == definedScreenName) {
                        if (deeplinkActivityParams.jsonArguments.isEmpty()) {
                            val instance = tryCreateObjectInstance(%T::class.java)
                            if (instance != null) {
                                return instance
                            }
                        }
                        tryCreateActivityParams(%T::class.java, deeplinkActivityParams)
                    } else {
                        null
                    }
                """.trimIndent(),
                screenName,
                paramsClassName,
                paramsClassName,
            )
            .build()
    }

    private fun createTryCreateObjectInstance(): FunSpec {
        return FunSpec.builder("tryCreateObjectInstance")
            .addModifiers(KModifier.PRIVATE)
            .addParameter(
                "clazz",
                Class::class.asClassName().parameterizedBy(
                    WildcardTypeName.producerOf(ACTIVITY_PARAMS_CLASS),
                ),
            )
            .returns(ACTIVITY_PARAMS_CLASS.copy(nullable = true))
            .addCode(
                CodeBlock.builder()
                    .add(
                        """
                            return kotlin.runCatching {
                                %T.getRawType(clazz).kotlin.objectInstance as %T
                            }.getOrNull()
                        """.trimIndent(),
                        MOSHI_TYPES_CLASS,
                        ACTIVITY_PARAMS_CLASS,
                    ).build(),
            )
            .build()
    }

    private fun createTryCreateActivityParams(): FunSpec {
        return FunSpec.builder("tryCreateActivityParams")
            .addModifiers(KModifier.PRIVATE)
            .addParameter(
                "clazz",
                Class::class.asClassName().parameterizedBy(
                    WildcardTypeName.producerOf(ACTIVITY_PARAMS_CLASS),
                ),
            )
            .addParameter("deeplinkActivityParams", DEEPLINK_ACTIVITY_PARAMS_CLASS)
            .returns(ACTIVITY_PARAMS_CLASS.copy(nullable = true))
            .addCode(
                CodeBlock.builder()
                    .add(
                        """
                            return kotlin.runCatching {
                                moshi.adapter(clazz).fromJson(deeplinkActivityParams.jsonArguments)
                            }.getOrNull()
                        """.trimIndent(),
                    ).build(),
            )
            .build()
    }

    private fun KSClassDeclaration.extendsDuckDuckGoActivity(): Boolean {
        return hasSuperType(DUCKDUCKGO_ACTIVITY)
    }

    private fun KSClassDeclaration.hasSuperType(fqName: String): Boolean {
        for (superTypeRef in superTypes) {
            val superDecl = superTypeRef.resolve().declaration
            if (superDecl.qualifiedName?.asString() == fqName) return true
            if (superDecl is KSClassDeclaration && superDecl.hasSuperType(fqName)) return true
        }
        return false
    }

    private fun KSAnnotation.getArgumentType(name: String): KSType? {
        return arguments.firstOrNull { it.name?.asString() == name }?.value as? KSType
    }

    private fun KSAnnotation.getArgumentString(name: String): String? {
        return arguments.firstOrNull { it.name?.asString() == name }?.value as? String
    }
}
