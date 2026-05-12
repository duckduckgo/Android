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

import com.google.devtools.ksp.getConstructors
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
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo

@OptIn(KotlinPoetKspPreview::class)
class ContributesViewModelProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    companion object {
        private const val CONTRIBUTES_VIEW_MODEL = "com.duckduckgo.anvil.annotations.ContributesViewModel"
        private const val JAVAX_INJECT = "javax.inject.Inject"
        private const val SINGLE_INSTANCE_IN = "dagger.SingleInstanceIn"

        private val CONTRIBUTES_MULTIBINDING_CLASS = ClassName("com.squareup.anvil.annotations", "ContributesMultibinding")
        private val VIEW_MODEL_FACTORY_PLUGIN_CLASS = ClassName("com.duckduckgo.common.utils.plugins.view_model", "ViewModelFactoryPlugin")
        private val VIEW_MODEL_CLASS = ClassName("androidx.lifecycle", "ViewModel")
        private val PROVIDER_CLASS = ClassName("javax.inject", "Provider")
        private val INJECT_CLASS = ClassName("javax.inject", "Inject")
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(CONTRIBUTES_VIEW_MODEL).toList()
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
        val annotation = classDeclaration.findAnnotation(CONTRIBUTES_VIEW_MODEL) ?: return

        // Validation: must not be annotated with @SingleInstanceIn
        if (classDeclaration.findAnnotation(SINGLE_INSTANCE_IN) != null) {
            logger.error(
                "${classDeclaration.qualifiedName?.asString()} cannot be annotated with @SingleInstanceIn",
                classDeclaration,
            )
            return
        }

        // Validation: must have exactly one @Inject constructor
        val injectConstructors = classDeclaration.getConstructors().filter { constructor ->
            constructor.annotations.any { it.isAnnotation(JAVAX_INJECT) }
        }.toList()

        if (injectConstructors.size != 1) {
            logger.error(
                "${classDeclaration.qualifiedName?.asString()} must have an @Inject constructor",
                classDeclaration,
            )
            return
        }

        val constructor = injectConstructors.single()

        // Validation: constructor parameters must not have default values
        val hasDefaultValues = constructor.parameters.any { it.hasDefault }
        if (hasDefaultValues) {
            logger.error(
                "${classDeclaration.qualifiedName?.asString()} constructor parameters must not have default values",
                classDeclaration,
            )
            return
        }

        // Extract scope
        val scope = annotation.getArgumentType("scope") ?: run {
            logger.error("Could not resolve scope parameter", classDeclaration)
            return
        }
        val scopeClassName = scope.toClassName()

        val className = classDeclaration.simpleName.asString()
        val packageName = classDeclaration.packageName.asString()
        val factoryName = "${className}_ViewModelFactory"

        val vmClassName = classDeclaration.toClassName()

        // Build constructor with Provider<ViewModel> parameter
        val providerType = PROVIDER_CLASS.parameterizedBy(vmClassName)

        val viewModelProviderProperty = PropertySpec.builder("viewModelProvider", providerType)
            .addModifiers(KModifier.PRIVATE)
            .build()

        val constructorSpec = FunSpec.constructorBuilder()
            .addAnnotation(INJECT_CLASS)
            .addParameter(ParameterSpec.builder("viewModelProvider", providerType).build())
            .build()

        // Build create() method
        val typeVariableT = TypeVariableName("T", VIEW_MODEL_CLASS.copy(nullable = true))
        val createFun = FunSpec.builder("create")
            .addModifiers(KModifier.OVERRIDE)
            .addTypeVariable(typeVariableT)
            .addParameter(
                ParameterSpec.builder(
                    "modelClass",
                    ClassName("java.lang", "Class").parameterizedBy(TypeVariableName("T")),
                ).build(),
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
                vmClassName,
            )
            .build()

        // Build class
        val typeSpec = TypeSpec.classBuilder(factoryName)
            .addAnnotation(
                AnnotationSpec.builder(CONTRIBUTES_MULTIBINDING_CLASS)
                    .addMember("%T::class", scopeClassName)
                    .build(),
            )
            .primaryConstructor(constructorSpec)
            .addProperty(viewModelProviderProperty.toBuilder().initializer("viewModelProvider").build())
            .addSuperinterface(VIEW_MODEL_FACTORY_PLUGIN_CLASS)
            .addFunction(createFun)
            .build()

        val fileSpec = FileSpec.builder(packageName, factoryName)
            .addType(typeSpec)
            .build()

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

    private fun KSAnnotation.isAnnotation(fqName: String): Boolean {
        return annotationType.resolve().declaration.qualifiedName?.asString() == fqName
    }
}
