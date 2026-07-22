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
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
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
import com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo

@OptIn(KotlinPoetKspPreview::class)
class ContributesWorkerProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    companion object {
        private const val CONTRIBUTES_WORKER = "com.duckduckgo.anvil.annotations.ContributesWorker"
        private const val JAVAX_INJECT = "javax.inject.Inject"

        private val CONTRIBUTES_MULTIBINDING_CLASS = ClassName("com.squareup.anvil.annotations", "ContributesMultibinding")
        private val WORKER_INJECTOR_PLUGIN_CLASS = ClassName("com.duckduckgo.common.utils.plugins.worker", "WorkerInjectorPlugin")
        private val LISTENABLE_WORKER_CLASS = ClassName("androidx.work", "ListenableWorker")
        private val PROVIDER_CLASS = ClassName("javax.inject", "Provider")
        private val INJECT_CLASS = ClassName("javax.inject", "Inject")
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(CONTRIBUTES_WORKER).toList()
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
        val annotation = classDeclaration.findAnnotation(CONTRIBUTES_WORKER) ?: return

        // Extract scope
        val scope = annotation.getArgumentType("scope") ?: run {
            logger.error("Could not resolve scope parameter", classDeclaration)
            return
        }
        val scopeClassName = scope.toClassName()

        val className = classDeclaration.simpleName.asString()
        val packageName = classDeclaration.packageName.asString()
        val pluginName = "${className}_WorkerInjectorPlugin"

        // Find all @Inject-annotated properties
        val injectProperties = classDeclaration.getAllProperties()
            .filter { prop -> prop.annotations.any { it.isAnnotation(JAVAX_INJECT) } }
            .toList()

        // Build constructor properties (each wrapped in Provider<T>) and parameters
        data class PropAndParam(val property: PropertySpec, val parameter: ParameterSpec)
        val propsAndParams = injectProperties.map { prop ->
            val propType = prop.type.resolve().toTypeName()
            val providerType = PROVIDER_CLASS.parameterizedBy(propType)
            val propName = prop.simpleName.asString()

            // Collect non-@Inject annotations for passthrough to the parameter only
            val nonInjectAnnotations = prop.annotations
                .filter { !it.isAnnotation(JAVAX_INJECT) }
                .map { it.toAnnotationSpec() }
                .toList()

            val propertySpec = PropertySpec.builder(propName, providerType)
                .addModifiers(KModifier.PRIVATE)
                .build()

            val parameterSpec = ParameterSpec.builder(propName, providerType)
                .addAnnotations(nonInjectAnnotations)
                .build()

            PropAndParam(propertySpec, parameterSpec)
        }
        val propertySpecs = propsAndParams.map { it.property }
        val parameterSpecs = propsAndParams.map { it.parameter }

        // Build inject() method
        val injectFun = FunSpec.builder("inject")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("worker", LISTENABLE_WORKER_CLASS)
            .returns(Boolean::class)
            .apply {
                if (injectProperties.isNotEmpty()) {
                    addCode(buildInjectBody(className, injectProperties))
                } else {
                    addCode(buildEmptyInjectBody(className))
                }
            }
            .build()

        // Build constructor
        val constructor = FunSpec.constructorBuilder()
            .addAnnotation(INJECT_CLASS)
            .addParameters(parameterSpecs)
            .build()

        // Build class
        val typeSpec = TypeSpec.classBuilder(pluginName)
            .addAnnotation(
                AnnotationSpec.builder(CONTRIBUTES_MULTIBINDING_CLASS)
                    .addMember("%T::class", scopeClassName)
                    .build(),
            )
            .primaryConstructor(constructor)
            .addProperties(
                propertySpecs.map { p ->
                    p.toBuilder().initializer(p.name).build()
                },
            )
            .addSuperinterface(WORKER_INJECTOR_PLUGIN_CLASS)
            .addFunction(injectFun)
            .build()

        val fileSpec = FileSpec.builder(packageName, pluginName)
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

    private fun buildInjectBody(workerClassName: String, properties: List<KSPropertyDeclaration>): String {
        val sb = StringBuilder()
        sb.appendLine("if (worker is $workerClassName) {")
        for (prop in properties) {
            val name = prop.simpleName.asString()
            sb.appendLine("    worker.$name = $name.get()")
        }
        sb.appendLine("    return true")
        sb.appendLine("}")
        sb.append("return false\n")
        return sb.toString()
    }

    private fun buildEmptyInjectBody(workerClassName: String): String {
        val sb = StringBuilder()
        sb.appendLine("if (worker is $workerClassName) {")
        sb.appendLine("    return true")
        sb.appendLine("}")
        sb.append("return false\n")
        return sb.toString()
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

    private fun KSAnnotation.toAnnotationSpec(): AnnotationSpec {
        val annotationClassName = this.annotationType.resolve().toClassName()
        val builder = AnnotationSpec.builder(annotationClassName)
        this.arguments.forEach { arg ->
            val name = arg.name?.asString() ?: return@forEach
            val value = arg.value ?: return@forEach
            when (value) {
                is String -> builder.addMember("$name = %S", value)
                is Boolean -> builder.addMember("$name = %L", value)
                is Int -> builder.addMember("$name = %L", value)
                is Long -> builder.addMember("$name = %LL", value)
                is Float -> builder.addMember("$name = %Lf", value)
                is Double -> builder.addMember("$name = %L", value)
                is KSType -> builder.addMember("$name = %T::class", value.toClassName())
                else -> builder.addMember("$name = %L", value)
            }
        }
        return builder.build()
    }
}
