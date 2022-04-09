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

import com.duckduckgo.anvil.annotations.InjectWith
import com.google.auto.service.AutoService
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.annotations.MergeSubcomponent
import com.squareup.anvil.compiler.api.*
import com.squareup.anvil.compiler.internal.*
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.argumentAt
import com.squareup.anvil.compiler.internal.reference.asClassName
import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import dagger.Binds
import dagger.Subcomponent
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

/**
 * This Anvil code generator allows inject ViewModel without manually creating the ViewModel factory
 */
@OptIn(ExperimentalAnvilApi::class)
@AutoService(CodeGenerator::class)
class ContributesSubComponentCodeGenerator : CodeGenerator {

    override fun isApplicable(context: AnvilContext): Boolean = true

    override fun generateCode(codeGenDir: File, module: ModuleDescriptor, projectFiles: Collection<KtFile>): Collection<GeneratedFile> {
        return projectFiles.classAndInnerClassReferences(module)
            .toList()
            .filter { it.isAnnotatedWith(InjectWith::class.fqName) }
            .flatMap {
                listOf(
                    generateSubcomponentFactory(it, codeGenDir, module),
                    generateSubcomponentFactoryBindingModule(it, codeGenDir, module),
                )
            }
            .toList()
    }

    private fun generateSubcomponentFactory(vmClass: ClassReference.Psi, codeGenDir: File, module: ModuleDescriptor): GeneratedFile {
        val generatedPackage = vmClass.packageFqName.toString()
        val subcomponentFactoryClassName = vmClass.subComponentName()
        val scope = vmClass.annotations.first { it.fqName == InjectWith::class.fqName }.scopeOrNull(0)!!

        val content = FileSpec.buildFile(generatedPackage, subcomponentFactoryClassName) {
            addType(
                TypeSpec.interfaceBuilder(subcomponentFactoryClassName)
                    .addAnnotation(
                        AnnotationSpec
                            .builder(singleInstanceAnnotationFqName.asClassName(module)).addMember("scope = %T::class", scope.asClassName())
                            .build()
                    )
                    .addAnnotation(AnnotationSpec.builder(MergeSubcomponent::class).addMember("scope = %T::class", scope.asClassName()).build())
                    .addSuperinterface(duckduckgoAndroidInjectorFqName.asClassName(module).parameterizedBy(vmClass.asClassName()))
                    .addType(
                        TypeSpec.interfaceBuilder("Factory")
                            .addSuperinterface(
                                duckduckgoAndroidInjectorFqName.asClassName(module)
                                    .nestedClass("Factory")
                                    .parameterizedBy(vmClass.asClassName())
                            )
                            .addAnnotation(AnnotationSpec.builder(Subcomponent.Factory::class).build())
                            .build()
                    )
                    .addType(generateParentComponentInterface(vmClass, codeGenDir, module))
                    .build()
            ).build()
        }

        return createGeneratedFile(codeGenDir, generatedPackage, subcomponentFactoryClassName, content)
    }

    private fun generateParentComponentInterface(vmClass: ClassReference.Psi, codeGenDir: File, module: ModuleDescriptor): TypeSpec {
        val generatedPackage = vmClass.packageFqName.toString()
        val componentClassNAme = vmClass.subComponentName()
        val scope = vmClass.annotations.first { it.fqName == InjectWith::class.fqName }.scopeOrNull(0)!!

        return TypeSpec.funInterfaceBuilder("ParentComponent")
            .addAnnotation(
                AnnotationSpec
                    .builder(ContributesTo::class).addMember("scope = %T::class", scope.fqName.getParentScope(module).asClassName(module))
                    .build()
            )
            .addFunction(
                FunSpec.builder("provide${vmClass.shortName}ComponentFactory")
                    .addModifiers(KModifier.ABSTRACT)
                    .returns(
                        FqName("$generatedPackage.$componentClassNAme").asClassName(module).nestedClass("Factory")
                    )
                    .build()
            )
            .build()

    }

    private fun generateSubcomponentFactoryBindingModule(vmClass: ClassReference.Psi, codeGenDir: File, module: ModuleDescriptor): GeneratedFile {
        val generatedPackage = vmClass.packageFqName.toString()
        val moduleClassName = "${vmClass.subComponentName()}_Module"
        val scope = vmClass.annotations.first { it.fqName == InjectWith::class.fqName }.scopeOrNull(0)!!
        val bindingClassKey = vmClass.bindingKey(InjectWith::class.fqName) ?: vmClass.fqName

        val content = FileSpec.buildFile(generatedPackage, moduleClassName) {
            addType(
                TypeSpec.classBuilder(moduleClassName)
                    .addAnnotation(AnnotationSpec.builder(dagger.Module::class).build())
                    .addAnnotation(
                        AnnotationSpec
                            .builder(ContributesTo::class).addMember("scope = %T::class", scope.fqName.getParentScope(module).asClassName(module))
                            .build()
                    )
                    .addModifiers(KModifier.ABSTRACT)
                    .addFunction(
                        FunSpec.builder("bind${vmClass.subComponentName()}Factory")
                            .addParameter(
                                "factory",
                                FqName("$generatedPackage.${vmClass.subComponentName()}").asClassName(module).nestedClass("Factory")
                            )
                            .addAnnotation(AnnotationSpec.builder(Binds::class).build())
                            .addAnnotation(AnnotationSpec.builder(IntoMap::class).build())
                            .addAnnotation(
                                AnnotationSpec.builder(ClassKey::class).addMember("%T::class", bindingClassKey.asClassName(module)).build()
                            )
                            .addModifiers(KModifier.ABSTRACT)
                            .returns(duckduckgoAndroidInjectorFqName.asClassName(module).nestedClass("Factory").parameterizedBy(STAR))
                            .build()
                    )
                    .build()
            ).build()
        }

        return createGeneratedFile(codeGenDir, generatedPackage, moduleClassName, content)

    }

    private fun FqName.getParentScope(module: ModuleDescriptor): FqName {
        return when (this.asClassName(module)) {
            activityScopeFqName.asClassName(module) -> appScopeFqName
            receiverScopeFqName.asClassName(module) -> appScopeFqName
            vpnScopeFqName.asClassName(module) -> appScopeFqName
            quickSettingsScopeFqName.asClassName(module) -> appScopeFqName
            fragmentScopeFqName.asClassName(module) -> activityScopeFqName
            else -> throw AnvilCompilationException("${this.asClassName(module)} scope is not currently supported")
        }
    }

    private fun ClassReference.Psi.subComponentName(): String {
        return "${shortName}_SubComponent"
    }

    private fun ClassReference.Psi.bindingKey(
        fqName: FqName,
    ): FqName? {
        return annotations
            .first { it.fqName == fqName }
            .argumentAt(name = "bindingKey", index = 1)
            ?.annotation
            ?.bindingKeyOrNull()
            ?.fqName
    }
    companion object {
        private val duckduckgoAndroidInjectorFqName = FqName("dagger.android.AndroidInjector")
        private val singleInstanceAnnotationFqName = FqName("dagger.SingleInstanceIn")
        private val appScopeFqName = FqName("com.duckduckgo.di.scopes.AppScope")
        private val activityScopeFqName = FqName("com.duckduckgo.di.scopes.ActivityScope")
        private val fragmentScopeFqName = FqName("com.duckduckgo.di.scopes.FragmentScope")
        private val receiverScopeFqName = FqName("com.duckduckgo.di.scopes.ReceiverScope")
        private val vpnScopeFqName = FqName("com.duckduckgo.di.scopes.VpnScope")
        private val quickSettingsScopeFqName = FqName("com.duckduckgo.di.scopes.QuickSettingsScope")
    }
}
