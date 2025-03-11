/*
 * Copyright (c) 2022 DuckDuckGo
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

import com.duckduckgo.anvil.annotations.ContributesNonCachingServiceApi
import com.duckduckgo.anvil.annotations.ContributesServiceApi
import com.duckduckgo.anvil.annotations.ContributesServiceApiWithTimeout
import com.google.auto.service.AutoService
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.api.*
import com.squareup.anvil.compiler.internal.asClassName
import com.squareup.anvil.compiler.internal.buildFile
import com.squareup.anvil.compiler.internal.fqName
import com.squareup.anvil.compiler.internal.reference.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import dagger.Lazy
import dagger.Provides
import java.io.File
import javax.inject.Named
import okhttp3.OkHttpClient
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import retrofit2.Retrofit

/** This Anvil code generator allows generates a backend service API and its dagger bindings. */
@OptIn(ExperimentalAnvilApi::class)
@AutoService(CodeGenerator::class)
class ContributesServiceApiCodeGenerator : CodeGenerator {

    private val serviceApiAnnotations = listOf(
        ContributesServiceApi::class,
        ContributesNonCachingServiceApi::class,
        ContributesServiceApiWithTimeout::class,
    )

    override fun isApplicable(context: AnvilContext): Boolean = true

    override fun generateCode(codeGenDir: File, module: ModuleDescriptor, projectFiles: Collection<KtFile>): Collection<GeneratedFile> {
        return projectFiles.classAndInnerClassReferences(module)
            .toList()
            .filter { reference -> reference.isAnnotatedWith(serviceApiAnnotations.map { it.fqName }) }
            .flatMap {
                listOf(
                    generateServiceApiModule(it, codeGenDir, module),
                )
            }
            .toList()
    }

    private fun generateServiceApiModule(vmClass: ClassReference.Psi, codeGenDir: File, module: ModuleDescriptor): GeneratedFile {
        val generatedPackage = vmClass.packageFqName.toString()
        val moduleClassName = "${vmClass.shortName}_Module"

        // check only one service api annotation is present
        val serviceAnnotations = vmClass.fqNameIntersect(serviceApiAnnotations.map { it.fqName })
        if (serviceAnnotations.size > 1) {
            throw AnvilCompilationException(
                "Only one of ${serviceApiAnnotations.map { it.simpleName }} can be used on a class",
                element = vmClass.clazz.identifyingElement,
            )
        }
        val qualifierAnnotations = vmClass.filterQualifierAnnotations()

        val serviceAnnotation = serviceAnnotations.first()

        val scope = vmClass.annotations.first { it.fqName == serviceAnnotation }.scopeOrNull(0)!!
        val serviceClassName = vmClass.serviceApiClassName(serviceAnnotation) ?: vmClass.asClassName()

        if (!vmClass.isInterface()) {
            throw AnvilCompilationException(
                "${vmClass.fqName} must be an interface",
                element = vmClass.clazz.identifyingElement,
            )
        }

        val namedApiType = serviceAnnotation.resolvedNamedApiType()

        val timeoutMillis: Long? = if (serviceAnnotation == ContributesServiceApiWithTimeout::class.fqName) {
            vmClass.annotations.firstOrNull { it.fqName == serviceAnnotation }
                ?.argumentAt(name = "timeoutMillis", index = 2)
                ?.value()
        } else {
            null
        }

        val content = FileSpec.buildFile(generatedPackage, moduleClassName) {
            addType(
                TypeSpec.objectBuilder(moduleClassName)
                    .addAnnotation(dagger.Module::class)
                    .addAnnotation(
                        AnnotationSpec
                            .builder(ContributesTo::class).addMember("scope = %T::class", scope.asClassName())
                            .build(),
                    )
                    .addFunction(
                        FunSpec.builder("provides${serviceClassName.simpleName}")
                            .addAnnotation(Provides::class)
                            .apply {
                                // Add qualifier annotations as well
                                qualifierAnnotations.forEach { qualifier ->
                                    addAnnotation(qualifier.toAnnotationSpec())
                                }
                            }
                            .let { functionSpec ->
                                if (timeoutMillis != null && timeoutMillis > 0L) {
                                    functionSpec
                                        .addParameter(
                                            ParameterSpec.builder("retrofitBuilder", Retrofit.Builder::class.asClassName())
                                                .addAnnotation(
                                                    AnnotationSpec
                                                        .builder(Named::class)
                                                        .addMember("value = %S", namedApiType)
                                                        .build(),
                                                )
                                                .build(),
                                        )
                                        .addParameter(
                                            ParameterSpec.builder(
                                                "okHttpClientBuilder",
                                                Lazy::class.asClassName().parameterizedBy(OkHttpClient.Builder::class.asClassName()),
                                            )
                                                .addAnnotation(
                                                    AnnotationSpec
                                                        .builder(Named::class)
                                                        .addMember("value = %S", namedApiType)
                                                        .build(),
                                                )
                                                .build(),
                                        )
                                        .addCode(
                                            """
                                                val okHttpClient by lazy { okHttpClientBuilder.get().callTimeout($timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS).build() }
                                                val retrofit = retrofitBuilder.callFactory {
                                                    okHttpClient.newCall(it) 
                                                }.build()
                                                return retrofit.create(%T::class.java)
                                            """.trimIndent(),
                                            serviceClassName,
                                        )
                                } else {
                                    functionSpec
                                        .addParameter(
                                            ParameterSpec.builder("retrofit", retrofitFqName.asClassName(module))
                                                .addAnnotation(
                                                    AnnotationSpec
                                                        .builder(Named::class)
                                                        .addMember("value = %S", namedApiType)
                                                        .build(),
                                                )
                                                .build(),
                                        )
                                        .addCode(
                                            """
                                                return retrofit.create(%T::class.java)
                                            """.trimIndent(),
                                            serviceClassName,
                                        )
                                }
                            }
                            .returns(serviceClassName)
                            .build(),
                    )
                    .build(),
            ).build()
        }

        return createGeneratedFile(codeGenDir, generatedPackage, moduleClassName, content)
    }

    private fun ClassReference.Psi.serviceApiClassName(
        fqName: FqName,
    ): ClassName? {
        return annotations
            .first { it.fqName == fqName }
            .argumentAt(name = "boundType", index = 1)
            ?.annotation
            ?.boundTypeOrNull()
            ?.asClassName()
    }

    private fun FqName.resolvedNamedApiType(): String {
        return when (this) {
            ContributesServiceApi::class.fqName -> "api"
            ContributesNonCachingServiceApi::class.fqName -> "nonCaching"
            ContributesServiceApiWithTimeout::class.fqName -> "apiWithTimeout"
            else -> throw AnvilCompilationException("Unknown service api annotation: $this")
        }
    }

    companion object {
        private val retrofitFqName = FqName("retrofit2.Retrofit")
    }
}
