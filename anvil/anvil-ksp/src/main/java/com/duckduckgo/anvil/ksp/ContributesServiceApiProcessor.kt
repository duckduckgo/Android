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
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo

@OptIn(KotlinPoetKspPreview::class)
class ContributesServiceApiProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    companion object {
        private const val CONTRIBUTES_SERVICE_API = "com.duckduckgo.anvil.annotations.ContributesServiceApi"
        private const val CONTRIBUTES_NON_CACHING_SERVICE_API = "com.duckduckgo.anvil.annotations.ContributesNonCachingServiceApi"
        private const val JAVAX_QUALIFIER = "javax.inject.Qualifier"

        private val MODULE_CLASS = ClassName("dagger", "Module")
        private val PROVIDES_CLASS = ClassName("dagger", "Provides")
        private val CONTRIBUTES_TO_CLASS = ClassName("com.squareup.anvil.annotations", "ContributesTo")
        private val NAMED_CLASS = ClassName("javax.inject", "Named")
        private val RETROFIT_CLASS = ClassName("retrofit2", "Retrofit")
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val serviceApiSymbols = resolver.getSymbolsWithAnnotation(CONTRIBUTES_SERVICE_API)
        val nonCachingSymbols = resolver.getSymbolsWithAnnotation(CONTRIBUTES_NON_CACHING_SERVICE_API)

        val allSymbols = (serviceApiSymbols + nonCachingSymbols).toList()

        val deferred = mutableListOf<KSAnnotated>()

        for (symbol in allSymbols) {
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

        // Find which service API annotations are present
        val hasServiceApi = classDeclaration.findAnnotation(CONTRIBUTES_SERVICE_API)
        val hasNonCachingServiceApi = classDeclaration.findAnnotation(CONTRIBUTES_NON_CACHING_SERVICE_API)

        // Validation: only one annotation allowed
        if (hasServiceApi != null && hasNonCachingServiceApi != null) {
            logger.error(
                "Only one of [ContributesServiceApi, ContributesNonCachingServiceApi] can be used on a class",
                classDeclaration,
            )
            return
        }

        val annotation = hasServiceApi ?: hasNonCachingServiceApi ?: return
        val namedValue = if (hasServiceApi != null) "api" else "nonCaching"

        // Extract scope
        val scope = annotation.getArgumentType("scope") ?: run {
            logger.error("Could not resolve scope parameter", classDeclaration)
            return
        }
        val scopeClassName = scope.toClassName()

        // Extract boundType (default is Unit)
        val boundType = annotation.getArgumentType("boundType")
        val serviceClassName = if (boundType != null && boundType.declaration.qualifiedName?.asString() != "kotlin.Unit") {
            boundType.toClassName()
        } else {
            classDeclaration.toClassName()
        }

        // Collect qualifier annotations from the interface
        val qualifierAnnotations = classDeclaration.annotations.filter { ann ->
            isQualifierAnnotation(ann)
        }.toList()

        // Generate the module
        val packageName = classDeclaration.packageName.asString()
        val moduleClassName = "${classDeclaration.simpleName.asString()}_Module"

        val providesFunction = FunSpec.builder("provides${serviceClassName.simpleName}")
            .addAnnotation(PROVIDES_CLASS)
            .apply {
                qualifierAnnotations.forEach { qualifier ->
                    addAnnotation(qualifier.toAnnotationSpec())
                }
            }
            .addParameter(
                ParameterSpec.builder("retrofit", RETROFIT_CLASS)
                    .addAnnotation(
                        AnnotationSpec.builder(NAMED_CLASS)
                            .addMember("value = %S", namedValue)
                            .build(),
                    )
                    .build(),
            )
            .returns(serviceClassName)
            .addCode(
                "return retrofit.create(%T::class.java)\n",
                serviceClassName,
            )
            .build()

        val moduleType = TypeSpec.objectBuilder(moduleClassName)
            .addAnnotation(MODULE_CLASS)
            .addAnnotation(
                AnnotationSpec.builder(CONTRIBUTES_TO_CLASS)
                    .addMember("scope = %T::class", scopeClassName)
                    .build(),
            )
            .addFunction(providesFunction)
            .build()

        val fileSpec = FileSpec.builder(packageName, moduleClassName)
            .addType(moduleType)
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

    private fun isQualifierAnnotation(annotation: KSAnnotation): Boolean {
        val annotationDeclaration = annotation.annotationType.resolve().declaration as? KSClassDeclaration ?: return false
        return annotationDeclaration.annotations.any { metaAnnotation ->
            metaAnnotation.annotationType.resolve().declaration.qualifiedName?.asString() == JAVAX_QUALIFIER
        }
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
                // For enum values and other types, add as literal
                else -> builder.addMember("$name = %L", value)
            }
        }
        return builder.build()
    }
}
