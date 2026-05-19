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
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo

@OptIn(KotlinPoetKspPreview::class)
class ContributesSubComponentProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val isMetro: Boolean,
) : SymbolProcessor {

    companion object {
        private const val INJECT_WITH = "com.duckduckgo.anvil.annotations.InjectWith"

        private val CONTRIBUTES_TO_CLASS = ClassName("com.squareup.anvil.annotations", "ContributesTo")
        private val MERGE_SUBCOMPONENT_CLASS = ClassName("com.squareup.anvil.annotations", "MergeSubcomponent")
        private val CONTRIBUTES_SUBCOMPONENT_CLASS = ClassName("com.squareup.anvil.annotations", "ContributesSubcomponent")
        private val CONTRIBUTES_SUBCOMPONENT_FACTORY_CLASS = ClassName("com.squareup.anvil.annotations", "ContributesSubcomponent", "Factory")
        private val SINGLE_INSTANCE_IN_CLASS = ClassName("dagger", "SingleInstanceIn")
        private val SUBCOMPONENT_FACTORY_CLASS = ClassName("dagger", "Subcomponent", "Factory")
        private val BINDS_INSTANCE_CLASS = ClassName("dagger", "BindsInstance")
        private val BINDS_CLASS = ClassName("dagger", "Binds")
        private val MODULE_CLASS = ClassName("dagger", "Module")
        private val INTO_MAP_CLASS = ClassName("dagger.multibindings", "IntoMap")
        private val CLASS_KEY_CLASS = ClassName("dagger.multibindings", "ClassKey")
        private val ANDROID_INJECTOR_CLASS = ClassName("dagger.android", "AndroidInjector")
        private val ANDROID_INJECTOR_FACTORY_CLASS = ClassName("dagger.android", "AndroidInjector", "Factory")

        private val APP_SCOPE = ClassName("com.duckduckgo.di.scopes", "AppScope")
        private val ACTIVITY_SCOPE = ClassName("com.duckduckgo.di.scopes", "ActivityScope")
        private val FRAGMENT_SCOPE = ClassName("com.duckduckgo.di.scopes", "FragmentScope")
        private val VIEW_SCOPE = ClassName("com.duckduckgo.di.scopes", "ViewScope")
        private val SERVICE_SCOPE = ClassName("com.duckduckgo.di.scopes", "ServiceScope")
        private val RECEIVER_SCOPE = ClassName("com.duckduckgo.di.scopes", "ReceiverScope")
        private val VPN_SCOPE = ClassName("com.duckduckgo.di.scopes", "VpnScope")

        private fun getParentScope(scope: ClassName): ClassName {
            return when (scope) {
                ACTIVITY_SCOPE, RECEIVER_SCOPE, VPN_SCOPE, SERVICE_SCOPE -> APP_SCOPE
                FRAGMENT_SCOPE, VIEW_SCOPE -> ACTIVITY_SCOPE
                else -> throw IllegalArgumentException("$scope scope is not currently supported")
            }
        }
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(INJECT_WITH).toList()
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
        val annotation = classDeclaration.findAnnotation(INJECT_WITH) ?: return

        val scopeType = annotation.getArgumentType("scope") ?: run {
            logger.error("Could not resolve scope parameter", classDeclaration)
            return
        }
        val scopeClassName = scopeType.toClassName()

        if (scopeClassName == ACTIVITY_SCOPE) {
            generateActivityInjector(classDeclaration, scopeClassName)
        } else {
            generateSubComponent(classDeclaration, scopeClassName)
            generateSubComponentModule(classDeclaration, scopeClassName, annotation)
        }
    }

    private fun generateActivityInjector(
        classDeclaration: KSClassDeclaration,
        scopeClassName: ClassName,
    ) {
        val className = classDeclaration.simpleName.asString()
        val packageName = classDeclaration.packageName.asString()
        val injectorName = "${className}_Injector"
        val classType = classDeclaration.toClassName()

        val typeSpec = TypeSpec.interfaceBuilder(injectorName)
            .addAnnotation(
                AnnotationSpec.builder(CONTRIBUTES_TO_CLASS)
                    .addMember("scope = %T::class", scopeClassName)
                    .build(),
            )
            .addFunction(
                FunSpec.builder("inject")
                    .addModifiers(KModifier.ABSTRACT)
                    .addParameter(
                        ParameterSpec.builder("activity", classType).build(),
                    )
                    .build(),
            )
            .build()

        val fileSpec = FileSpec.builder(packageName, injectorName)
            .addType(typeSpec)
            .build()

        writeFile(fileSpec, classDeclaration)
    }

    private fun generateSubComponent(
        classDeclaration: KSClassDeclaration,
        scopeClassName: ClassName,
    ) {
        val className = classDeclaration.simpleName.asString()
        val packageName = classDeclaration.packageName.asString()
        val subComponentName = "${className}_SubComponent"
        val classType = classDeclaration.toClassName()
        val parentScope = getParentScope(scopeClassName)
        val subComponentClassName = ClassName(packageName, subComponentName)

        // Always use @ContributesSubcomponent (with parentScope) instead of @MergeSubcomponent.
        // Metro's interop recognizes @ContributesSubcomponent and establishes the parent-child
        // scope relationship. @MergeSubcomponent doesn't carry parentScope and Metro can't
        // determine the hierarchy.
        val subComponentAnnotation = AnnotationSpec.builder(CONTRIBUTES_SUBCOMPONENT_CLASS)
            .addMember("scope = %T::class", scopeClassName)
            .addMember("parentScope = %T::class", parentScope)
            .build()

        val factoryAnnotation = AnnotationSpec.builder(CONTRIBUTES_SUBCOMPONENT_FACTORY_CLASS).build()

        // Factory interface
        val factoryType = TypeSpec.interfaceBuilder("Factory")
            .addSuperinterface(
                ANDROID_INJECTOR_FACTORY_CLASS.parameterizedBy(classType, subComponentClassName),
            )
            .addAnnotation(factoryAnnotation)
            .addFunction(
                FunSpec.builder("create")
                    .addModifiers(KModifier.OVERRIDE, KModifier.ABSTRACT)
                    .addParameter(
                        ParameterSpec.builder("instance", classType)
                            .addAnnotation(BINDS_INSTANCE_CLASS)
                            .build(),
                    )
                    .returns(subComponentClassName)
                    .build(),
            )
            .build()

        // ParentComponent fun interface
        val parentComponentType = TypeSpec.funInterfaceBuilder("ParentComponent")
            .addAnnotation(
                AnnotationSpec.builder(CONTRIBUTES_TO_CLASS)
                    .addMember("scope = %T::class", parentScope)
                    .build(),
            )
            .addFunction(
                FunSpec.builder("provide${className}ComponentFactory")
                    .addModifiers(KModifier.ABSTRACT)
                    .returns(subComponentClassName.nestedClass("Factory"))
                    .build(),
            )
            .build()

        // Main SubComponent interface
        // In Metro mode, @SingleInstanceIn is omitted — Metro derives the scope from
        // @ContributesSubcomponent(scope = ...) and adding @SingleInstanceIn would cause
        // a "multiple scope annotations" error. In AnvilDagger mode, Dagger requires the
        // explicit scope annotation on the generated subcomponent.
        val typeSpec = TypeSpec.interfaceBuilder(subComponentName)
            .apply {
                if (!isMetro) {
                    addAnnotation(
                        AnnotationSpec.builder(SINGLE_INSTANCE_IN_CLASS)
                            .addMember("scope = %T::class", scopeClassName)
                            .build(),
                    )
                }
            }
            .addAnnotation(subComponentAnnotation)
            .addSuperinterface(ANDROID_INJECTOR_CLASS.parameterizedBy(classType))
            .addType(factoryType)
            .addType(parentComponentType)
            .build()

        val fileSpec = FileSpec.builder(packageName, subComponentName)
            .addType(typeSpec)
            .build()

        writeFile(fileSpec, classDeclaration)
    }

    private fun generateSubComponentModule(
        classDeclaration: KSClassDeclaration,
        scopeClassName: ClassName,
        annotation: KSAnnotation,
    ) {
        val className = classDeclaration.simpleName.asString()
        val packageName = classDeclaration.packageName.asString()
        val subComponentName = "${className}_SubComponent"
        val moduleName = "${subComponentName}_Module"
        val classType = classDeclaration.toClassName()
        val parentScope = getParentScope(scopeClassName)
        val subComponentClassName = ClassName(packageName, subComponentName)

        // Determine bindingKey
        val bindingKeyType = annotation.getArgumentType("bindingKey")
        val bindingKeyClassName = if (bindingKeyType != null && bindingKeyType.declaration.qualifiedName?.asString() != "kotlin.Unit") {
            bindingKeyType.toClassName()
        } else {
            classType
        }

        val typeSpec = TypeSpec.classBuilder(moduleName)
            .addAnnotation(AnnotationSpec.builder(MODULE_CLASS).build())
            .addAnnotation(
                AnnotationSpec.builder(CONTRIBUTES_TO_CLASS)
                    .addMember("scope = %T::class", parentScope)
                    .build(),
            )
            .addModifiers(KModifier.ABSTRACT)
            .addFunction(
                FunSpec.builder("bind${subComponentName}Factory")
                    .addParameter(
                        "factory",
                        subComponentClassName.nestedClass("Factory"),
                    )
                    .addAnnotation(AnnotationSpec.builder(BINDS_CLASS).build())
                    .addAnnotation(AnnotationSpec.builder(INTO_MAP_CLASS).build())
                    .addAnnotation(
                        AnnotationSpec.builder(CLASS_KEY_CLASS)
                            .addMember("%T::class", bindingKeyClassName)
                            .build(),
                    )
                    .addModifiers(KModifier.ABSTRACT)
                    .returns(
                        ANDROID_INJECTOR_FACTORY_CLASS.parameterizedBy(STAR, STAR),
                    )
                    .build(),
            )
            .build()

        val fileSpec = FileSpec.builder(packageName, moduleName)
            .addType(typeSpec)
            .build()

        writeFile(fileSpec, classDeclaration)
    }

    private fun writeFile(fileSpec: FileSpec, classDeclaration: KSClassDeclaration) {
        val containingFile = classDeclaration.containingFile
        val dependencies = if (containingFile != null) {
            Dependencies(aggregating = false, containingFile)
        } else {
            Dependencies(aggregating = false)
        }
        fileSpec.writeTo(codeGenerator, dependencies)
    }

    private fun KSClassDeclaration.findAnnotation(fqName: String): KSAnnotation? {
        return annotations.firstOrNull { ann ->
            ann.annotationType.resolve().declaration.qualifiedName?.asString() == fqName
        }
    }

    private fun KSAnnotation.getArgumentType(name: String): KSType? {
        return arguments.firstOrNull { it.name?.asString() == name }?.value as? KSType
    }
}
