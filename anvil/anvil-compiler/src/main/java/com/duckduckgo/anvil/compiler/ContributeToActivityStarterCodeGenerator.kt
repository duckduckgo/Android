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
import java.io.File
import javax.inject.Inject
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile

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
                addType(createMapperClass(paramsType, vmClass, module)).build()
            }
        }

        return createGeneratedFile(codeGenDir, generatedPackage, moduleClassName, content)
    }

    private fun createMapperClass(paramsType: ClassReference, vmClass: ClassReference.Psi, module: ModuleDescriptor): TypeSpec {
        val moduleClassName = "${vmClass.shortName}_${paramsType.shortName}_Mapper"

        return TypeSpec.classBuilder(moduleClassName)
            .addAnnotation(
                AnnotationSpec
                    .builder(ContributesMultibinding::class).addMember("scope = %T::class", appScopeFqName.asClassName(module))
                    .build(),
            )
            .addSuperinterface(paramToActivityMapperFqName.asClassName(module))
            .addPrimaryConstructor()
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

    companion object {
        private val appScopeFqName = FqName("com.duckduckgo.di.scopes.AppScope")
        private val paramToActivityMapperFqName = FqName("com.duckduckgo.navigation.api.GlobalActivityStarter.ParamToActivityMapper")
        private val activityParamsFqName = FqName("com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams")
        private val appCompatActivityFqName = FqName("androidx.appcompat.app.AppCompatActivity")
        private val duckduckgoActivityFqName = FqName("com.duckduckgo.common.ui.DuckDuckGoActivity")
    }
}
