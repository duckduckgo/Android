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

import com.duckduckgo.anvil.annotations.ContributesWorker
import com.google.auto.service.AutoService
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.GeneratedFile
import com.squareup.anvil.compiler.api.GeneratedFileWithSources
import com.squareup.anvil.compiler.api.createGeneratedFile
import com.squareup.anvil.compiler.internal.asClassName
import com.squareup.anvil.compiler.internal.buildFile
import com.squareup.anvil.compiler.internal.fqName
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.asClassName
import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import java.io.File
import java.lang.StringBuilder
import javax.inject.Inject
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile

/**
 * This Anvil code generator allows injection dependencies into the Workers (WorkerInjectorPlugin) without manually creating a WorkerInjectorPlugin
 */
@OptIn(ExperimentalAnvilApi::class)
@AutoService(CodeGenerator::class)
class ContributesWorkerCodeGenerator : CodeGenerator {
    override fun isApplicable(context: AnvilContext): Boolean = true

    override fun generateCode(
        codeGenDir: File,
        module: ModuleDescriptor,
        projectFiles: Collection<KtFile>,
    ): Collection<GeneratedFileWithSources> = projectFiles.classAndInnerClassReferences(module)
        .toList()
        .filter { it.isAnnotatedWith(ContributesWorker::class.fqName) }
        .flatMap {
            listOf(generateWorkedInjectorPlugin(it, codeGenDir, module))
        }
        .toList()

    private fun generateWorkedInjectorPlugin(
        vmClass: ClassReference.Psi,
        codeGenDir: File,
        module: ModuleDescriptor,
    ): GeneratedFileWithSources {
        val generatedPackage = vmClass.packageFqName.toString()
        val workerPluginName = "${vmClass.shortName}_WorkerInjectorPlugin"
        val scope = vmClass.annotations.first { it.fqName == ContributesWorker::class.fqName }.scopeOrNull(0)
        val workerName = vmClass.shortName
        val dependencies = vmClass.properties
            .filter { it.isAnnotatedWith(Inject::class.fqName) }
            .map {
                PropertySpec
                    .builder(it.name, ClassName("javax.inject", "Provider").parameterizedBy(it.type().asTypeName()))
                    .addModifiers(KModifier.PRIVATE)
                    .apply {
                        // carry over the property annotations
                        it.annotations.filter { ann -> ann.fqName != Inject::class.fqName }.forEach { annotation ->
                            addAnnotation(
                                AnnotationSpec.builder(annotation.fqName.asClassName(module))
                                    .apply {
                                        annotation.arguments.forEach { argument ->
                                            argument.name?.let { name -> addMember("%N = %S", name, argument.value()) }
                                                ?: addMember("%S", argument.value())
                                        }
                                    }
                                    .build(),
                            )
                        }
                    }
                    .build()
            }.toTypedArray()

        val content = FileSpec.buildFile(generatedPackage, workerPluginName) {
            addType(
                TypeSpec.classBuilder(workerPluginName)
                    .addAnnotation(
                        AnnotationSpec.builder(ContributesMultibinding::class)
                            .addMember("%T::class", scope!!.asClassName())
                            .build(),
                    )
                    .addSuperinterface(duckduckgoWorkerInjectorPluginFqName.asClassName(module))
                    .primaryConstructor(dependencies)
                    .addFunction(
                        FunSpec.builder("inject")
                            .addParameter(
                                "worker",
                                listenableWorkerFqName.asClassName(module),
                            )
                            .addModifiers(KModifier.OVERRIDE)
                            .returns(TypeVariableName("Boolean"))
                            .addCode(generateCode(dependencies, workerName))
                            .build(),
                    )
                    .build(),
            )
        }

        return createGeneratedFile(codeGenDir, generatedPackage, workerPluginName, content, vmClass.containingFileAsJavaFile)
    }

    private fun generateCode(
        properties: Array<PropertySpec>,
        workerName: String,
    ): String {
        val builder = StringBuilder().appendLine("if (worker is $workerName) {")
        properties.forEach {
            builder.appendLine(it.generateDependencyAssignmentString().prependIndent())
        }
        builder.appendLine(
            """
                   return true
               }
               return false
            """.trimIndent(),
        )

        return builder.toString().trimIndent()
    }

    private fun PropertySpec.generateDependencyAssignmentString(): String {
        return "worker.$name = $name.get()"
    }

    private fun TypeSpec.Builder.primaryConstructor(properties: Array<PropertySpec>): TypeSpec.Builder {
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

    companion object {
        private val duckduckgoWorkerInjectorPluginFqName = FqName("com.duckduckgo.common.utils.plugins.worker.WorkerInjectorPlugin")
        private val listenableWorkerFqName = FqName("androidx.work.ListenableWorker")
    }
}
