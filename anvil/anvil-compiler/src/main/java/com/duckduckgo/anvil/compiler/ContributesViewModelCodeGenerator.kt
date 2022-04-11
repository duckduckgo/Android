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

import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.google.auto.service.AutoService
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.api.*
import com.squareup.anvil.compiler.internal.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.allConstructors
import java.io.File
import javax.inject.Inject

/**
 * This Anvil code generator allows inject ViewModel without manually creating the ViewModel factory
 */
@OptIn(ExperimentalAnvilApi::class)
@AutoService(CodeGenerator::class)
class ContributesViewModelCodeGenerator : CodeGenerator {

    override fun isApplicable(context: AnvilContext): Boolean = true

    override fun generateCode(codeGenDir: File, module: ModuleDescriptor, projectFiles: Collection<KtFile>): Collection<GeneratedFile> {
        return projectFiles.classesAndInnerClass(module)
            .filter { it.hasAnnotation(ContributesViewModel::class.fqName, module) }
            .flatMap {
                listOf(generateFactoryPlugin(it, codeGenDir, module))
            }
            .toList()
    }

    private fun generateFactoryPlugin(vmClass: KtClassOrObject, codeGenDir: File, module: ModuleDescriptor): GeneratedFile {
        val generatedPackage = vmClass.containingKtFile.packageFqName.toString()
        val factoryClassName = "${vmClass.name}_ViewModelFactory"
        val scope = vmClass.scope(ContributesViewModel::class.fqName, module)
        val isSingleInstanceInScope = vmClass.hasAnnotation(singleInstanceAnnotationFqName, module)
        val constructor = vmClass.allConstructors.singleOrNull { it.hasAnnotation(Inject::class.fqName, module) }
        val defaultParameterValues = constructor?.valueParameters?.singleOrNull { it.hasDefaultValue() }
        if (isSingleInstanceInScope) {
            throw AnvilCompilationException(
                "${vmClass.requireFqName()} cannot be annotated with @SingleInstanceIn",
                element = vmClass.identifyingElement,
            )
        }
        if (constructor == null) {
            throw AnvilCompilationException(
                "${vmClass.requireFqName()} must have an @Inject constructor",
                element = vmClass.identifyingElement,
            )
        }
        if (defaultParameterValues != null) {
            throw AnvilCompilationException(
                "${vmClass.requireFqName()} constructor parameters must not have default values",
                element = vmClass.identifyingElement,
            )
        }
        val content = FileSpec.buildFile(generatedPackage, factoryClassName) {
            addType(
                TypeSpec.classBuilder(factoryClassName)
                    .addSuperinterface(duckduckgoViewModelFactoryPluginFqName.asClassName(module))
                    .addAnnotation(
                        AnnotationSpec.builder(ContributesMultibinding::class)
                            .addMember("%T::class", scope.asClassName(module))
                            .build()
                    )
                    .primaryConstructor(
                        PropertySpec
                            .builder("viewModelProvider", ClassName("javax.inject", "Provider").parameterizedBy(vmClass.asClassName()))
                            .addModifiers(KModifier.PRIVATE)
                            .build()
                    )
                    .addFunction(
                        FunSpec.builder("create")
                            .addModifiers(KModifier.OVERRIDE)
                            .addTypeVariable(
                                TypeVariableName("T", ClassName("androidx.lifecycle", "ViewModel").copy(nullable = true))
                            )
                            .addParameter(
                                ParameterSpec.builder(
                                    "modelClass",
                                    ClassName("java.lang", "Class").parameterizedBy(TypeVariableName("T"))
                                ).build()
                            )
                            .returns(TypeVariableName("T").copy(nullable = true))
                            .addCode(
                                """
                                    with(modelClass) {
                                        return when {
                                            isAssignableFrom(%T::class.java) -> (viewModelProvider.get() as T)
                                            else -> null
                                        }
                                    }
                                """.trimIndent(),
                                vmClass.asClassName()
                            )
                            .build()
                    )
                    .build()
            ).build()
        }

        return createGeneratedFile(codeGenDir, generatedPackage, factoryClassName, content)
    }

    companion object {
        private val duckduckgoViewModelFactoryPluginFqName = FqName("com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin")
        private val singleInstanceAnnotationFqName = FqName("dagger.SingleInstanceIn")
    }

    private fun TypeSpec.Builder.primaryConstructor(vararg properties: PropertySpec): TypeSpec.Builder {
        val propertySpecs = properties.map { p -> p.toBuilder().initializer(p.name).build() }
        val parameters = propertySpecs.map { ParameterSpec.builder(it.name, it.type).build() }
        val constructor = FunSpec.constructorBuilder()
            .addParameters(parameters)
            .addAnnotation(AnnotationSpec.builder(Inject::class).build())
            .build()

        return this
            .primaryConstructor(constructor)
            .addProperties(propertySpecs)
    }
}
