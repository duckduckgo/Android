/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.anvil.compiler

import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.google.auto.service.AutoService
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.api.*
import com.squareup.anvil.compiler.internal.asClassName
import com.squareup.anvil.compiler.internal.buildFile
import com.squareup.anvil.compiler.internal.fqName
import com.squareup.anvil.compiler.internal.reference.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import javax.inject.Inject

/** This Anvil code generator allows generates a backend service API and its dagger bindings. */
@OptIn(ExperimentalAnvilApi::class)
@AutoService(CodeGenerator::class)
class ContributeToActivityStarterCodeGenerator : CodeGenerator {

    override fun isApplicable(context: AnvilContext): Boolean = true

    override fun generateCode(codeGenDir: File, module: ModuleDescriptor, projectFiles: Collection<KtFile>): Collection<GeneratedFile> {
        return projectFiles.classAndInnerClassReferences(module)
            .toList()
            .filter { it.isAnnotatedWith(ContributeToActivityStarter::class.fqName) }
            .flatMap {
                listOf(
                    generateParameterToActivityMapper(it, codeGenDir, module),
                )
            }
            .toList()
    }

    private fun generateParameterToActivityMapper(vmClass: ClassReference.Psi, codeGenDir: File, module: ModuleDescriptor): GeneratedFile {
        val generatedPackage = vmClass.packageFqName.toString()
        val moduleClassName = "${vmClass.shortName}_ActivityMapper"

        if (!vmClass.directSuperTypeReferences().map { it.asClassReference() }.contains(duckduckgoActivityFqName.toClassReference(module))) {
            throw AnvilCompilationException(
                "${vmClass.fqName} must extend $duckduckgoActivityFqName",
                element = vmClass.clazz.identifyingElement,
            )
        }

        val mapperAnnotations = vmClass.annotations.filter { it.fqName == ContributeToActivityStarter::class.fqName }

        val content = FileSpec.buildFile(generatedPackage, moduleClassName) {
            for (annotation in mapperAnnotations) {
                val paramsType = annotation.paramsTypeOrNull()!!
                val screenName = annotation.screenNameOrNull().orEmpty()
                addType(createMapperClass(paramsType, screenName, vmClass, module)).build()
            }
        }

        return createGeneratedFile(codeGenDir, generatedPackage, moduleClassName, content)
    }

    private fun createMapperClass(
        paramsType: ClassReference,
        screenName: String,
        vmClass: ClassReference.Psi,
        module: ModuleDescriptor,
    ): TypeSpec {
        val moduleClassName = "${vmClass.shortName}_${paramsType.shortName}_Mapper"

        return TypeSpec.classBuilder(moduleClassName)
            .addAnnotation(
                AnnotationSpec
                    .builder(ContributesMultibinding::class).addMember("scope = %T::class", appScopeFqName.asClassName(module))
                    .build(),
            )
            .addSuperinterface(paramToActivityMapperFqName.asClassName(module))
            .addPrimaryConstructor()
            .addProperty(
                PropertySpec.builder("moshi", moshi.asClassName(module), KModifier.PRIVATE)
                    .initializer("Moshi.Builder().add(%T()).build()", kotlinJsonObjectAdapter.asClassName(module))
                    .build(),
            )
            .addFunction(
                FunSpec.builder("map")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter(
                        "activityParams",
                        activityParamsFqName.asClassName(module),
                    )
                    .returns(
                        Class::class.asClassName().parameterizedBy(
                            WildcardTypeName.producerOf(appCompatActivityFqName.asClassName(module)),
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
                        paramsType.asClassName(),
                        vmClass.asClassName(),
                    )
                    .build(),
            )
            .apply {
                if (screenName.isBlank()) {
                    addFunction(emptyDeepLinkMapper(module))
                } else {
                    addFunction(createDeeplinkMapper(module, paramsType, screenName))
                    addFunction(createTryCreateObjectInstance(module))
                    addFunction(createTryCreateActivityParamsInstance(module))
                }
            }
            .build()
    }

    private fun TypeSpec.Builder.addPrimaryConstructor(): TypeSpec.Builder {
        val constructor = FunSpec.constructorBuilder()
            .addAnnotation(AnnotationSpec.builder(Inject::class).build())
            .build()

        return this.primaryConstructor(constructor)
    }

    private fun AnnotationReference.paramsTypeOrNull(): ClassReference? {
        return argumentAt("paramsType", 0)?.value()
    }

    private fun AnnotationReference.screenNameOrNull(): String? {
        return argumentAt("screenName", 1)?.value() as? String?
    }

    private fun emptyDeepLinkMapper(module: ModuleDescriptor): FunSpec {
        return FunSpec.builder("map")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter(
                "deeplinkActivityParams",
                deeplinkActivityParamsFqName.asClassName(module),
            )
            .returns(
                activityParamsFqName.asClassName(module).copy(nullable = true),
            )
            .addCode(
                """
                    return null
                """.trimIndent(),
            )
            .build()
    }

    private fun createDeeplinkMapper(module: ModuleDescriptor, paramsType: ClassReference, screenName: String): FunSpec {
        return FunSpec.builder("map")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter(
                "deeplinkActivityParams",
                deeplinkActivityParamsFqName.asClassName(module),
            )
            .returns(
                activityParamsFqName.asClassName(module).copy(nullable = true),
            )
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
                paramsType.asClassName(),
                paramsType.asClassName(),
            )
            .build()
    }

    private fun createTryCreateObjectInstance(module: ModuleDescriptor): FunSpec {
        return FunSpec.builder("tryCreateObjectInstance")
            .addModifiers(KModifier.PRIVATE)
            .addParameter("clazz", Class::class.asClassName().parameterizedBy(WildcardTypeName.producerOf(activityParamsFqName.asClassName(module))))
            .addCode(
                CodeBlock.builder()
                    .add(
                        """
                            return kotlin.runCatching {
                                %T.getRawType(clazz).kotlin.objectInstance as %T
                            }.getOrNull()
                        """.trimIndent(),
                        moshiTypes.asClassName(module),
                        activityParamsFqName.asClassName(module),
                    ).build(),
            )
            .returns(activityParamsFqName.asClassName(module).copy(nullable = true))
            .build()
    }

    private fun createTryCreateActivityParamsInstance(module: ModuleDescriptor): FunSpec {
        return FunSpec.builder("tryCreateActivityParams")
            .addModifiers(KModifier.PRIVATE)
            .addParameter("clazz", Class::class.asClassName().parameterizedBy(WildcardTypeName.producerOf(activityParamsFqName.asClassName(module))))
            .addParameter(
                "deeplinkActivityParams",
                deeplinkActivityParamsFqName.asClassName(module),
            )
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
            .returns(activityParamsFqName.asClassName(module).copy(nullable = true))
            .build()
    }

    companion object {
        private val appScopeFqName = FqName("com.duckduckgo.di.scopes.AppScope")
        private val paramToActivityMapperFqName = FqName("com.duckduckgo.navigation.api.GlobalActivityStarter.ParamToActivityMapper")
        private val activityParamsFqName = FqName("com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams")
        private val deeplinkActivityParamsFqName = FqName("com.duckduckgo.navigation.api.GlobalActivityStarter.DeeplinkActivityParams")
        private val appCompatActivityFqName = FqName("androidx.appcompat.app.AppCompatActivity")
        private val duckduckgoActivityFqName = FqName("com.duckduckgo.common.ui.DuckDuckGoActivity")
        private val moshi = FqName("com.squareup.moshi.Moshi")
        private val kotlinJsonObjectAdapter = FqName("com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory")
        private val moshiTypes = FqName("com.squareup.moshi.Types")
    }
}
