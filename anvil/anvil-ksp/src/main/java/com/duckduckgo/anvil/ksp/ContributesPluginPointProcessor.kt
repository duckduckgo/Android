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
import com.google.devtools.ksp.symbol.ClassKind
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
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo

@OptIn(KotlinPoetKspPreview::class)
class ContributesPluginPointProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    companion object {
        private const val CONTRIBUTES_PLUGIN_POINT = "com.duckduckgo.anvil.annotations.ContributesPluginPoint"

        private val INJECT_CLASS = ClassName("javax.inject", "Inject")
        private val MODULE_CLASS = ClassName("dagger", "Module")
        private val BINDS_CLASS = ClassName("dagger", "Binds")
        private val MULTIBINDS_CLASS = ClassName("dagger.multibindings", "Multibinds")
        private val CONTRIBUTES_TO_CLASS = ClassName("com.squareup.anvil.annotations", "ContributesTo")
        private val PLUGIN_POINT_CLASS = ClassName("com.duckduckgo.common.utils.plugins", "PluginPoint")
        private val DAGGER_SET_CLASS = ClassName("com.duckduckgo.di", "DaggerSet")
        private val DAGGER_MAP_CLASS = ClassName("com.duckduckgo.di", "DaggerMap")
        private val COLLECTION_CLASS = ClassName("kotlin.collections", "Collection")
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(CONTRIBUTES_PLUGIN_POINT).toList()
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
        // Validation: must be an interface
        if (classDeclaration.classKind != ClassKind.INTERFACE) {
            logger.error(
                "${classDeclaration.qualifiedName?.asString()} must be an interface",
                classDeclaration,
            )
            return
        }

        val annotation = classDeclaration.findAnnotation(CONTRIBUTES_PLUGIN_POINT) ?: return

        // Extract scope
        val scope = annotation.getArgumentType("scope") ?: run {
            logger.error("Could not resolve scope parameter", classDeclaration)
            return
        }
        val scopeClassName = scope.toClassName()

        // Extract boundType (default is Unit)
        val boundType = annotation.getArgumentType("boundType")
        val pluginClassName = if (boundType != null && boundType.declaration.qualifiedName?.asString() != "kotlin.Unit") {
            boundType.toClassName()
        } else {
            classDeclaration.toClassName()
        }

        val packageName = classDeclaration.packageName.asString()
        val simpleName = classDeclaration.simpleName.asString()

        val containingFile = classDeclaration.containingFile
        val dependencies = if (containingFile != null) {
            Dependencies(aggregating = false, containingFile)
        } else {
            Dependencies(aggregating = false)
        }

        generatePluginPoint(packageName, simpleName, pluginClassName, dependencies)
        generateBindingModule(packageName, simpleName, scopeClassName, pluginClassName, dependencies)
    }

    private fun generatePluginPoint(
        packageName: String,
        simpleName: String,
        pluginClassName: ClassName,
        dependencies: Dependencies,
    ) {
        val pluginPointClassName = "${simpleName}_PluginPoint"

        val setPluginsType = DAGGER_SET_CLASS.parameterizedBy(pluginClassName)
        val mapPluginsType = DAGGER_MAP_CLASS.parameterizedBy(INT, pluginClassName)
        val collectionType = COLLECTION_CLASS.parameterizedBy(pluginClassName)

        val setPluginsProperty = PropertySpec.builder("setPlugins", setPluginsType)
            .addModifiers(KModifier.PRIVATE)
            .build()

        val mapPluginsProperty = PropertySpec.builder("mapPlugins", mapPluginsType)
            .addModifiers(KModifier.PRIVATE)
            .build()

        val constructorSpec = FunSpec.constructorBuilder()
            .addAnnotation(INJECT_CLASS)
            .addParameter(ParameterSpec.builder("setPlugins", setPluginsType).build())
            .addParameter(ParameterSpec.builder("mapPlugins", mapPluginsType).build())
            .build()

        val sortedPluginsProperty = PropertySpec.builder("sortedPlugins", collectionType)
            .addModifiers(KModifier.PRIVATE)
            .delegate(
                CodeBlock.builder()
                    .beginControlFlow("lazy")
                    .add(
                        """
                            mapPlugins.entries
                            .sortedWith(compareBy({ it.key }, { it.value.javaClass.name }))
                            .map { it.value }
                            .toMutableList()
                            .apply {
                                addAll(setPlugins.toList().sortedBy { it.javaClass.name })
                            }
                        """.trimIndent(),
                    )
                    .endControlFlow()
                    .build(),
            )
            .build()

        val getPluginsFun = FunSpec.builder("getPlugins")
            .addModifiers(KModifier.OVERRIDE)
            .returns(collectionType)
            .addComment("Sort plugins by class name to ensure execution consistency")
            .addCode("return sortedPlugins\n")
            .build()

        val typeSpec = TypeSpec.classBuilder(pluginPointClassName)
            .addSuperinterface(PLUGIN_POINT_CLASS.parameterizedBy(pluginClassName))
            .primaryConstructor(constructorSpec)
            .addProperty(setPluginsProperty.toBuilder().initializer("setPlugins").build())
            .addProperty(mapPluginsProperty.toBuilder().initializer("mapPlugins").build())
            .addProperty(sortedPluginsProperty)
            .addFunction(getPluginsFun)
            .build()

        val fileSpec = FileSpec.builder(packageName, pluginPointClassName)
            .addType(typeSpec)
            .build()

        fileSpec.writeTo(codeGenerator, dependencies)
    }

    private fun generateBindingModule(
        packageName: String,
        simpleName: String,
        scopeClassName: ClassName,
        pluginClassName: ClassName,
        dependencies: Dependencies,
    ) {
        val moduleClassName = "${simpleName}_PluginPoint_Module"
        val pluginPointImplClassName = ClassName(packageName, "${simpleName}_PluginPoint")

        val setType = DAGGER_SET_CLASS.parameterizedBy(pluginClassName)
        val mapType = DAGGER_MAP_CLASS.parameterizedBy(INT, pluginClassName)
        val pluginPointType = PLUGIN_POINT_CLASS.parameterizedBy(pluginClassName)

        val bindSetEmptyFun = FunSpec.builder("bindSetEmpty${simpleName}_PluginPoint")
            .addAnnotation(MULTIBINDS_CLASS)
            .addModifiers(KModifier.ABSTRACT)
            .returns(setType)
            .build()

        val bindMapEmptyFun = FunSpec.builder("bindMapEmpty${simpleName}_PluginPoint")
            .addAnnotation(MULTIBINDS_CLASS)
            .addModifiers(KModifier.ABSTRACT)
            .returns(mapType)
            .build()

        val bindPluginPointFun = FunSpec.builder("bind${simpleName}_PluginPoint")
            .addAnnotation(BINDS_CLASS)
            .addModifiers(KModifier.ABSTRACT)
            .addParameter("pluginPoint", pluginPointImplClassName)
            .returns(pluginPointType)
            .build()

        val typeSpec = TypeSpec.classBuilder(moduleClassName)
            .addAnnotation(MODULE_CLASS)
            .addAnnotation(
                AnnotationSpec.builder(CONTRIBUTES_TO_CLASS)
                    .addMember("scope = %T::class", scopeClassName)
                    .build(),
            )
            .addModifiers(KModifier.ABSTRACT)
            .addFunction(bindSetEmptyFun)
            .addFunction(bindMapEmptyFun)
            .addFunction(bindPluginPointFun)
            .build()

        val fileSpec = FileSpec.builder(packageName, moduleClassName)
            .addType(typeSpec)
            .build()

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
