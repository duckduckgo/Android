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
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo

/**
 * KSP processor that generates Active Plugins, i.e. those controlled via remote feature flags.
 *
 * Handles two annotations:
 *
 * **@ContributesActivePluginPoint** generates:
 * - A private trigger interface with @ContributesPluginPoint (picked up by the PluginPoint processor)
 * - A remote feature flag interface with @ContributesRemoteFeature
 * - A multi-process SharedPreferences store for the feature flag
 * - An active wrapper class implementing ActivePluginPoint<T> that filters by isActive() + feature flag
 * - A Dagger binding module for the wrapper
 * - A sentinel object in a fixed package for cross-module validation
 *
 * **@ContributesActivePlugin** generates:
 * - An ActivePlugin wrapper that delegates to the original and implements isActive() via toggle
 * - A remote feature flag interface with @ContributesRemoteFeature
 * - A multi-process SharedPreferences store
 * - Conditionally, a deferred validation marker for cross-module parentFeatureName validation
 */
@OptIn(KotlinPoetKspPreview::class, com.google.devtools.ksp.KspExperimental::class)
class ContributesActivePluginPointProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    companion object {
        private const val CONTRIBUTES_ACTIVE_PLUGIN_POINT = "com.duckduckgo.anvil.annotations.ContributesActivePluginPoint"
        private const val CONTRIBUTES_ACTIVE_PLUGIN = "com.duckduckgo.anvil.annotations.ContributesActivePlugin"

        private const val SENTINEL_PACKAGE = "com.duckduckgo.anvil.generated"
        private const val DEFERRED_SENTINEL_PACKAGE = "com.duckduckgo.anvil.generated.deferred"
        private const val DEFERRED_MARKER_PREFIX = "ActivePluginDeferredValidation_"

        private val APP_SCOPE_FQ = "com.duckduckgo.di.scopes.AppScope"

        // Anvil/Dagger annotations
        private val CONTRIBUTES_PLUGIN_POINT = ClassName("com.duckduckgo.anvil.annotations", "ContributesPluginPoint")
        private val CONTRIBUTES_REMOTE_FEATURE = ClassName("com.duckduckgo.anvil.annotations", "ContributesRemoteFeature")
        private val CONTRIBUTES_MULTIBINDING = ClassName("com.squareup.anvil.annotations", "ContributesMultibinding")
        private val CONTRIBUTES_BINDING = ClassName("com.squareup.anvil.annotations", "ContributesBinding")
        private val CONTRIBUTES_TO = ClassName("com.squareup.anvil.annotations", "ContributesTo")
        private val PRIORITY_KEY = ClassName("com.duckduckgo.anvil.annotations", "PriorityKey")
        private val REMOTE_FEATURE_STORE_NAMED = ClassName("com.duckduckgo.feature.toggles.api", "RemoteFeatureStoreNamed")

        // Feature toggles API
        private val TOGGLE = ClassName("com.duckduckgo.feature.toggles.api", "Toggle")
        private val TOGGLE_DEFAULT_VALUE = ClassName("com.duckduckgo.feature.toggles.api", "Toggle", "DefaultValue")
        private val DEFAULT_FEATURE_VALUE = ClassName("com.duckduckgo.feature.toggles.api", "Toggle", "DefaultFeatureValue")
        private val TOGGLE_STATE = ClassName("com.duckduckgo.feature.toggles.api", "Toggle", "State")
        private val TOGGLE_STORE = ClassName("com.duckduckgo.feature.toggles.api", "Toggle", "Store")
        private val TOGGLE_EXPERIMENT = ClassName("com.duckduckgo.feature.toggles.api", "Toggle", "Experiment")
        private val TOGGLE_INTERNAL_ALWAYS_ENABLED = ClassName("com.duckduckgo.feature.toggles.api", "Toggle", "InternalAlwaysEnabled")

        // Runtime types
        private val PLUGIN_POINT = ClassName("com.duckduckgo.common.utils.plugins", "PluginPoint")
        private val INTERNAL_ACTIVE_PLUGIN_POINT = ClassName("com.duckduckgo.common.utils.plugins", "InternalActivePluginPoint")
        private val DISPATCHER_PROVIDER = ClassName("com.duckduckgo.common.utils", "DispatcherProvider")
        private val SHARED_PREFERENCES_PROVIDER = ClassName("com.duckduckgo.data.store.api", "SharedPreferencesProvider")
        private val APP_COROUTINE_SCOPE = ClassName("com.duckduckgo.app.di", "AppCoroutineScope")
        private val COROUTINE_SCOPE = ClassName("kotlinx.coroutines", "CoroutineScope")
        private val SHARED_PREFERENCES = ClassName("android.content", "SharedPreferences")
        private val MOSHI = ClassName("com.squareup.moshi", "Moshi")
        private val JSON_ADAPTER = ClassName("com.squareup.moshi", "JsonAdapter")

        // Dagger
        private val MODULE = ClassName("dagger", "Module")
        private val BINDS = ClassName("dagger", "Binds")
        private val INJECT = ClassName("javax.inject", "Inject")
        private val SUPPRESS = ClassName("kotlin", "Suppress")
    }

    private val featureBackedClassNames = mutableMapOf<String, String>()
    private val emittedDeferredMarkers = mutableSetOf<String>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val pluginPointSymbols = resolver.getSymbolsWithAnnotation(CONTRIBUTES_ACTIVE_PLUGIN_POINT).toList()
        val pluginSymbols = resolver.getSymbolsWithAnnotation(CONTRIBUTES_ACTIVE_PLUGIN).toList()

        val deferred = mutableListOf<KSAnnotated>()

        // Separate valid from deferred
        val validPluginPoints = mutableListOf<KSClassDeclaration>()
        for (symbol in pluginPointSymbols) {
            if (!symbol.validate()) {
                deferred.add(symbol)
                continue
            }
            if (symbol is KSClassDeclaration) validPluginPoints.add(symbol)
        }

        val validPlugins = mutableListOf<KSClassDeclaration>()
        for (symbol in pluginSymbols) {
            if (!symbol.validate()) {
                deferred.add(symbol)
                continue
            }
            if (symbol is KSClassDeclaration) validPlugins.add(symbol)
        }

        // Phase 1: Collect local plugin point featureNames
        val localPluginPointFeatureNames = validPluginPoints.mapNotNull { classDecl ->
            classDecl.findAnnotation(CONTRIBUTES_ACTIVE_PLUGIN_POINT)?.getArgumentString("featureName")?.takeIf { it.isNotBlank() }
        }.toSet()

        // Phase 2: Validate deferred markers from sibling modules
        if (localPluginPointFeatureNames.isNotEmpty()) {
            validateDeferredMarkers(resolver, localPluginPointFeatureNames)
        }

        // Generate code for plugin points
        for (classDecl in validPluginPoints) {
            processActivePluginPoint(classDecl, resolver)
        }

        // Generate code for plugins, collecting deferred markers
        val deferredThisRun = mutableListOf<Pair<String, KSClassDeclaration>>()
        for (classDecl in validPlugins) {
            processActivePlugin(classDecl, resolver, localPluginPointFeatureNames, deferredThisRun)
        }

        // Phase 3: Validate deferred markers emitted this run
        validateDeferredEmittedThisRun(resolver, localPluginPointFeatureNames, deferredThisRun)

        return deferred
    }

    // ========================================================================
    // Cross-module validation
    // ========================================================================

    private fun validateDeferredMarkers(resolver: Resolver, localPluginPointFeatureNames: Set<String>) {
        val deferredDeclarations = resolver.getDeclarationsFromPackage(DEFERRED_SENTINEL_PACKAGE)
            .filterIsInstance<KSClassDeclaration>()
            .toList()

        if (deferredDeclarations.isEmpty()) return

        val sentinelDeclarations = resolver.getDeclarationsFromPackage(SENTINEL_PACKAGE)
            .filterIsInstance<KSClassDeclaration>()
            .toList()

        val availableFeatureNames = (
            sentinelDeclarations
                .map { it.simpleName.asString() }
                .filter { it.startsWith("ActivePluginPointRegistry_") }
                .map { it.removePrefix("ActivePluginPointRegistry_") } +
                localPluginPointFeatureNames
            ).sorted()

        for (decl in deferredDeclarations) {
            val name = decl.simpleName.asString()
            if (!name.startsWith(DEFERRED_MARKER_PREFIX)) continue

            val parentFeatureName = name.removePrefix(DEFERRED_MARKER_PREFIX).substringBefore("__")

            val foundInSentinel = resolver.getClassDeclarationByName(
                resolver.getKSNameFromString("$SENTINEL_PACKAGE.ActivePluginPointRegistry_$parentFeatureName"),
            ) != null
            val foundLocally = parentFeatureName in localPluginPointFeatureNames

            if (!foundInSentinel && !foundLocally) {
                logger.error(
                    "parentFeatureName \"$parentFeatureName\" does not match any @ContributesActivePluginPoint.\n" +
                        "Known plugin point featureNames: ${availableFeatureNames.joinToString(", ")}",
                )
            }
        }
    }

    private fun validateDeferredEmittedThisRun(
        resolver: Resolver,
        localPluginPointFeatureNames: Set<String>,
        deferredThisRun: List<Pair<String, KSClassDeclaration>>,
    ) {
        if (deferredThisRun.isEmpty()) return

        val registryDeclarations = resolver.getDeclarationsFromPackage(SENTINEL_PACKAGE)
            .filterIsInstance<KSClassDeclaration>()
            .toList()
        if (registryDeclarations.isEmpty()) return

        val available by lazy {
            (
                registryDeclarations
                    .map { it.simpleName.asString() }
                    .filter { it.startsWith("ActivePluginPointRegistry_") }
                    .map { it.removePrefix("ActivePluginPointRegistry_") } +
                    localPluginPointFeatureNames
                ).sorted()
        }

        for ((parentFeatureName, classDecl) in deferredThisRun) {
            if (parentFeatureName in localPluginPointFeatureNames) continue
            val sentinelFound = resolver.getClassDeclarationByName(
                resolver.getKSNameFromString("$SENTINEL_PACKAGE.ActivePluginPointRegistry_$parentFeatureName"),
            ) != null
            if (!sentinelFound) {
                logger.error(
                    "${classDecl.qualifiedName?.asString()}: parentFeatureName \"$parentFeatureName\" does not match " +
                        "any @ContributesActivePluginPoint.\n" +
                        "Known plugin point featureNames: ${available.joinToString(", ")}",
                    classDecl,
                )
            }
        }
    }

    // ========================================================================
    // @ContributesActivePluginPoint processing
    // ========================================================================

    @Suppress("UNUSED_PARAMETER")
    private fun processActivePluginPoint(classDecl: KSClassDeclaration, resolver: Resolver) {
        val fqName = classDecl.qualifiedName?.asString() ?: return
        val annotation = classDecl.findAnnotation(CONTRIBUTES_ACTIVE_PLUGIN_POINT) ?: return

        // Validate scope
        val scopeType = annotation.getArgumentType("scope") ?: run {
            logger.error("$fqName: Could not resolve scope parameter", classDecl)
            return
        }
        val scopeClassName = scopeType.toClassName()
        val scopeFqName = scopeType.declaration.qualifiedName?.asString()

        if (scopeFqName != APP_SCOPE_FQ) {
            logger.error("$fqName: Active plugins can only be used in 'AppScope'.", classDecl)
            return
        }

        // Extract featureName
        val featureName = annotation.getArgumentString("featureName")?.takeIf { it.isNotBlank() } ?: run {
            logger.error("$fqName: @ContributesActivePluginPoint requires a non-blank featureName.", classDecl)
            return
        }

        if (!featureName.startsWith("pluginPoint")) {
            logger.error(
                "$fqName: @ContributesActivePluginPoint featureName must start with \"pluginPoint\" (got \"$featureName\").",
                classDecl,
            )
            return
        }

        // Duplicate check
        val existingFeature = featureBackedClassNames.putIfAbsent(featureName, fqName)
        if (existingFeature != null) {
            logger.error("$fqName plugin point naming is duplicated, previous found in $existingFeature", classDecl)
            return
        }

        // Extract boundType
        val boundTypeType = annotation.getArgumentType("boundType")
        val pluginClassType = if (boundTypeType != null && boundTypeType.declaration.qualifiedName?.asString() != "kotlin.Unit") {
            boundTypeType.toClassName()
        } else {
            classDecl.toClassName()
        }

        val packageName = classDecl.packageName.asString()
        val simpleName = classDecl.simpleName.asString()

        val containingFile = classDecl.containingFile
        val dependencies = if (containingFile != null) {
            Dependencies(aggregating = false, containingFile)
        } else {
            Dependencies(aggregating = false)
        }

        generateActivePluginPointFile(
            packageName = packageName,
            simpleName = simpleName,
            scopeClassName = scopeClassName,
            pluginClassType = pluginClassType,
            featureName = featureName,
            dependencies = dependencies,
        )

        generateSentinelFile(featureName, dependencies)
    }

    private fun generateActivePluginPointFile(
        packageName: String,
        simpleName: String,
        scopeClassName: ClassName,
        pluginClassType: ClassName,
        featureName: String,
        dependencies: Dependencies,
    ) {
        val pluginPointClassFileName = "${simpleName}_ActivePluginPoint"
        val triggerClassName = "Trigger_${simpleName}_ActivePluginPoint"
        val wrapperClassName = "${simpleName}_PluginPoint_ActiveWrapper"
        val wrapperBindingModuleClassName = "${simpleName}_PluginPoint_ActiveWrapper_Binding_Module"
        val remoteFeatureClassName = "${simpleName}_ActivePluginPoint_RemoteFeature"
        val remoteFeatureStoreClassName = "${simpleName}_ActivePluginPoint_RemoteFeature_MultiProcessStore"

        val preferencesName = "com.duckduckgo.feature.toggle.$featureName.mp.store"

        val jvmSuppressWildcardsAnnotation = AnnotationSpec.builder(JvmSuppressWildcards::class).build()

        val fileSpec = FileSpec.builder(packageName, pluginPointClassFileName)
            .addImport("com.duckduckgo.feature.toggles.api.Toggle", "DefaultFeatureValue")
            .addImport("com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue", "TRUE")
            .addImport("com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue", "FALSE")
            .addImport("com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue", "INTERNAL")
            // Trigger interface
            .addType(
                TypeSpec.interfaceBuilder(triggerClassName)
                    .addModifiers(KModifier.PRIVATE)
                    .addAnnotation(
                        AnnotationSpec.builder(CONTRIBUTES_PLUGIN_POINT)
                            .addMember("scope = %T::class", scopeClassName)
                            .addMember("boundType = %T::class", pluginClassType)
                            .build(),
                    )
                    .addAnnotation(
                        AnnotationSpec.builder(SUPPRESS)
                            .addMember("%S", "unused")
                            .build(),
                    )
                    .build(),
            )
            // Remote feature interface
            .addType(
                TypeSpec.interfaceBuilder(remoteFeatureClassName)
                    .addAnnotation(
                        AnnotationSpec.builder(CONTRIBUTES_REMOTE_FEATURE)
                            .addMember("scope = %T::class", scopeClassName)
                            .addMember("featureName = %S", featureName)
                            .addMember("toggleStore = %T::class", ClassName(packageName, remoteFeatureStoreClassName))
                            .build(),
                    )
                    .addFunction(
                        FunSpec.builder("self")
                            .addModifiers(KModifier.ABSTRACT)
                            .addAnnotation(
                                AnnotationSpec.builder(TOGGLE_DEFAULT_VALUE)
                                    .addMember("defaultValue = %L", "DefaultFeatureValue.TRUE")
                                    .build(),
                            )
                            .returns(TOGGLE)
                            .build(),
                    )
                    .build(),
            )
            // Active wrapper class
            .addType(
                TypeSpec.classBuilder(wrapperClassName)
                    .primaryConstructor(
                        FunSpec.constructorBuilder()
                            .addAnnotation(AnnotationSpec.builder(INJECT).build())
                            .addParameter("toggle", ClassName(packageName, remoteFeatureClassName))
                            .addParameter(
                                ParameterSpec.builder(
                                    "pluginPoint",
                                    PLUGIN_POINT.parameterizedBy(
                                        pluginClassType.copy(annotations = listOf(jvmSuppressWildcardsAnnotation)),
                                    ),
                                ).build(),
                            )
                            .addParameter(ParameterSpec.builder("dispatcherProvider", DISPATCHER_PROVIDER).build())
                            .build(),
                    )
                    .addProperty(
                        PropertySpec.builder("toggle", ClassName(packageName, remoteFeatureClassName), KModifier.PRIVATE)
                            .initializer("toggle")
                            .build(),
                    )
                    .addProperty(
                        PropertySpec.builder(
                            "pluginPoint",
                            PLUGIN_POINT.parameterizedBy(
                                pluginClassType.copy(annotations = listOf(jvmSuppressWildcardsAnnotation)),
                            ),
                            KModifier.PRIVATE,
                        ).initializer("pluginPoint").build(),
                    )
                    .addProperty(
                        PropertySpec.builder("dispatcherProvider", DISPATCHER_PROVIDER, KModifier.PRIVATE)
                            .initializer("dispatcherProvider")
                            .build(),
                    )
                    .addSuperinterface(
                        INTERNAL_ACTIVE_PLUGIN_POINT.parameterizedBy(
                            pluginClassType.copy(annotations = listOf(jvmSuppressWildcardsAnnotation)),
                        ),
                    )
                    .addFunction(
                        FunSpec.builder("getPlugins")
                            .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
                            .returns(ClassName("kotlin.collections", "Collection").parameterizedBy(pluginClassType))
                            .addCode(
                                CodeBlock.of(
                                    """
                                        return kotlinx.coroutines.withContext(dispatcherProvider.io()) {
                                            if (toggle.self().isEnabled()) {
                                                pluginPoint.getPlugins().filter { it.isActive() }
                                            } else {
                                                emptyList()
                                            }
                                        }
                                    """.trimIndent(),
                                ),
                            )
                            .build(),
                    )
                    .build(),
            )
            // Multi-process store
            .apply {
                addImport("kotlinx.coroutines", "launch")
                addImport("androidx.core.content", "edit")
                addImport("com.squareup.moshi.kotlin.reflect", "KotlinJsonAdapterFactory")
                addType(
                    createMultiProcessStore(
                        packageName = packageName,
                        scopeClassName = scopeClassName,
                        storeClassName = remoteFeatureStoreClassName,
                        featureClassName = remoteFeatureClassName,
                        preferencesName = preferencesName,
                    ),
                )
            }
            // Binding module
            .addType(
                TypeSpec.classBuilder(wrapperBindingModuleClassName)
                    .addAnnotation(AnnotationSpec.builder(MODULE).build())
                    .addAnnotation(
                        AnnotationSpec.builder(CONTRIBUTES_TO)
                            .addMember("scope = %T::class", scopeClassName)
                            .build(),
                    )
                    .addModifiers(KModifier.ABSTRACT)
                    .addFunction(
                        FunSpec.builder("binds$wrapperClassName")
                            .addModifiers(KModifier.ABSTRACT)
                            .addAnnotation(BINDS)
                            .addParameter(
                                ParameterSpec.builder("pluginPoint", ClassName(packageName, wrapperClassName)).build(),
                            )
                            .returns(
                                INTERNAL_ACTIVE_PLUGIN_POINT.parameterizedBy(
                                    pluginClassType.copy(annotations = listOf(jvmSuppressWildcardsAnnotation)),
                                ),
                            )
                            .build(),
                    )
                    .build(),
            )
            .build()

        fileSpec.writeTo(codeGenerator, dependencies)
    }

    private fun generateSentinelFile(featureName: String, dependencies: Dependencies) {
        val sentinelClassName = "ActivePluginPointRegistry_$featureName"
        val fileSpec = FileSpec.builder(SENTINEL_PACKAGE, sentinelClassName)
            .addType(
                TypeSpec.objectBuilder(sentinelClassName)
                    .addModifiers(KModifier.INTERNAL)
                    .addAnnotation(
                        AnnotationSpec.builder(SUPPRESS)
                            .addMember("%S", "unused")
                            .build(),
                    )
                    .build(),
            )
            .build()

        fileSpec.writeTo(codeGenerator, dependencies)
    }

    // ========================================================================
    // @ContributesActivePlugin processing
    // ========================================================================

    private fun processActivePlugin(
        classDecl: KSClassDeclaration,
        resolver: Resolver,
        localPluginPointFeatureNames: Set<String>,
        deferredThisRun: MutableList<Pair<String, KSClassDeclaration>>,
    ) {
        val fqName = classDecl.qualifiedName?.asString() ?: return
        val annotation = classDecl.findAnnotation(CONTRIBUTES_ACTIVE_PLUGIN) ?: return

        // Validate scope
        val scopeType = annotation.getArgumentType("scope") ?: run {
            logger.error("$fqName: Could not resolve scope parameter", classDecl)
            return
        }
        val scopeClassName = scopeType.toClassName()
        val scopeFqName = scopeType.declaration.qualifiedName?.asString()

        if (scopeFqName != APP_SCOPE_FQ) {
            logger.error("$fqName: Active plugins can only be used in 'AppScope'.", classDecl)
            return
        }

        // Extract boundType
        val boundType = annotation.getArgumentType("boundType") ?: run {
            logger.error("$fqName: Could not resolve boundType parameter", classDecl)
            return
        }
        val boundTypeClassName = boundType.toClassName()

        // Extract featureName
        val featureName = annotation.getArgumentString("featureName")?.takeIf { it.isNotBlank() } ?: run {
            logger.error("$fqName: @ContributesActivePlugin requires a non-blank featureName.", classDecl)
            return
        }

        if (!featureName.startsWith("plugin") || featureName.startsWith("pluginPoint")) {
            logger.error(
                "$fqName: @ContributesActivePlugin featureName must start with \"plugin\" but not \"pluginPoint\" (got \"$featureName\").",
                classDecl,
            )
            return
        }

        // Extract parentFeatureName
        val parentFeatureName = annotation.getArgumentString("parentFeatureName")?.takeIf { it.isNotBlank() } ?: run {
            logger.error("$fqName: @ContributesActivePlugin requires a non-blank parentFeatureName.", classDecl)
            return
        }

        if (!parentFeatureName.startsWith("pluginPoint")) {
            logger.error(
                "$fqName: @ContributesActivePlugin parentFeatureName must start with \"pluginPoint\" (got \"$parentFeatureName\").",
                classDecl,
            )
            return
        }

        if (parentFeatureName.contains("__")) {
            logger.error(
                "$fqName: parentFeatureName can't contain \"__\" — it's used as a separator in deferred validation marker class names " +
                    "(got \"$parentFeatureName\").",
                classDecl,
            )
            return
        }

        // Extract other parameters
        val defaultActiveValue = annotation.getArgumentEnum("defaultActiveValue")
        val priority = annotation.getArgumentInt("priority")
        val supportExperiments = annotation.getArgumentBoolean("supportExperiments") ?: false
        val internalAlwaysEnabled = annotation.getArgumentBoolean("internalAlwaysEnabled") ?: false

        // Duplicate check
        val simpleName = classDecl.simpleName.asString()
        val existingFeature = featureBackedClassNames.putIfAbsent("${featureName}_$parentFeatureName", fqName)
        if (existingFeature != null) {
            logger.error("$fqName plugin name is duplicated, previous found in $existingFeature", classDecl)
            return
        }

        // Validate parentFeatureName against known plugin points
        var emitDeferredMarker = false
        val foundLocally = parentFeatureName in localPluginPointFeatureNames
        if (!foundLocally) {
            val sentinelFound = resolver.getClassDeclarationByName(
                resolver.getKSNameFromString("$SENTINEL_PACKAGE.ActivePluginPointRegistry_$parentFeatureName"),
            ) != null
            if (!sentinelFound) {
                emitDeferredMarker = true
            }
        }

        val packageName = classDecl.packageName.asString()
        val containingFile = classDecl.containingFile
        val dependencies = if (containingFile != null) {
            Dependencies(aggregating = false, containingFile)
        } else {
            Dependencies(aggregating = false)
        }

        generateActivePluginFile(
            packageName = packageName,
            simpleName = simpleName,
            classDecl = classDecl,
            scopeClassName = scopeClassName,
            boundTypeClassName = boundTypeClassName,
            featureName = featureName,
            parentFeatureName = parentFeatureName,
            defaultActiveValue = defaultActiveValue,
            priority = priority,
            supportExperiments = supportExperiments,
            internalAlwaysEnabled = internalAlwaysEnabled,
            dependencies = dependencies,
        )

        // Emit deferred marker if needed
        if (emitDeferredMarker) {
            val markerClassName = "${DEFERRED_MARKER_PREFIX}${parentFeatureName}__$simpleName"
            deferredThisRun.add(parentFeatureName to classDecl)
            if (emittedDeferredMarkers.add(markerClassName)) {
                val markerFileSpec = FileSpec.builder(DEFERRED_SENTINEL_PACKAGE, markerClassName)
                    .addType(
                        TypeSpec.objectBuilder(markerClassName)
                            .addModifiers(KModifier.INTERNAL)
                            .addAnnotation(
                                AnnotationSpec.builder(SUPPRESS)
                                    .addMember("%S", "unused")
                                    .build(),
                            )
                            .build(),
                    )
                    .build()
                markerFileSpec.writeTo(codeGenerator, dependencies)
            }
        }
    }

    private fun generateActivePluginFile(
        packageName: String,
        simpleName: String,
        classDecl: KSClassDeclaration,
        scopeClassName: ClassName,
        boundTypeClassName: ClassName,
        featureName: String,
        parentFeatureName: String,
        defaultActiveValue: String?,
        priority: Int?,
        supportExperiments: Boolean,
        internalAlwaysEnabled: Boolean,
        dependencies: Dependencies,
    ) {
        val pluginClassName = "${simpleName}_ActivePlugin"
        val remoteFeatureClassName = "${simpleName}_ActivePlugin_RemoteFeature"
        val remoteFeatureStoreClassName = "${simpleName}_ActivePlugin_RemoteFeature_MultiProcessStore"
        val preferencesName = "com.duckduckgo.feature.toggle.$parentFeatureName.mp.store"
        val originalClassName = classDecl.toClassName()

        val defaultValueLiteral = defaultActiveValue ?: "DefaultFeatureValue.TRUE"

        val fileSpec = FileSpec.builder(packageName, pluginClassName)
            .addImport("com.duckduckgo.feature.toggles.api.Toggle", "DefaultFeatureValue")
            .addImport("com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue", "TRUE")
            .addImport("com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue", "FALSE")
            .addImport("com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue", "INTERNAL")
            // Active plugin wrapper
            .addType(
                TypeSpec.classBuilder(pluginClassName).apply {
                    addAnnotation(
                        AnnotationSpec.builder(CONTRIBUTES_MULTIBINDING)
                            .addMember("scope = %T::class", scopeClassName)
                            .addMember("boundType = %T::class", boundTypeClassName)
                            .build(),
                    )
                    priority?.let { p ->
                        if (p != 0) {
                            addAnnotation(
                                AnnotationSpec.builder(PRIORITY_KEY)
                                    .addMember("%L", p.toString())
                                    .build(),
                            )
                        }
                    }
                    primaryConstructor(
                        FunSpec.constructorBuilder()
                            .addAnnotation(AnnotationSpec.builder(INJECT).build())
                            .addParameter("activePlugin", originalClassName)
                            .addParameter("toggle", ClassName(packageName, remoteFeatureClassName))
                            .build(),
                    )
                    addProperty(
                        PropertySpec.builder("activePlugin", originalClassName, KModifier.PRIVATE)
                            .initializer("activePlugin")
                            .build(),
                    )
                    addProperty(
                        PropertySpec.builder("toggle", ClassName(packageName, remoteFeatureClassName), KModifier.PRIVATE)
                            .initializer("toggle")
                            .build(),
                    )
                    addSuperinterface(boundTypeClassName, delegate = CodeBlock.of("activePlugin"))
                    addFunction(
                        FunSpec.builder("isActive")
                            .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
                            .returns(Boolean::class)
                            .addCode(CodeBlock.of("return toggle.$featureName().isEnabled()"))
                            .build(),
                    )
                }.build(),
            )
            // Remote feature interface
            .addType(
                TypeSpec.interfaceBuilder(remoteFeatureClassName).apply {
                    addAnnotation(
                        AnnotationSpec.builder(CONTRIBUTES_REMOTE_FEATURE)
                            .addMember("scope = %T::class", scopeClassName)
                            .addMember("featureName = %S", parentFeatureName)
                            .addMember("toggleStore = %T::class", ClassName(packageName, remoteFeatureStoreClassName))
                            .build(),
                    )
                    addFunction(
                        FunSpec.builder("self")
                            .addModifiers(KModifier.ABSTRACT)
                            .addAnnotation(
                                AnnotationSpec.builder(TOGGLE_DEFAULT_VALUE)
                                    .addMember("defaultValue = %L", "DefaultFeatureValue.TRUE")
                                    .build(),
                            )
                            .returns(TOGGLE)
                            .build(),
                    )
                    addFunction(
                        FunSpec.builder(featureName).apply {
                            addModifiers(KModifier.ABSTRACT)
                            addAnnotation(
                                AnnotationSpec.builder(TOGGLE_DEFAULT_VALUE)
                                    .addMember("defaultValue = %L", defaultValueLiteral)
                                    .build(),
                            )
                            if (supportExperiments) {
                                addAnnotation(AnnotationSpec.builder(TOGGLE_EXPERIMENT).build())
                            }
                            if (internalAlwaysEnabled) {
                                addAnnotation(AnnotationSpec.builder(TOGGLE_INTERNAL_ALWAYS_ENABLED).build())
                            }
                            returns(TOGGLE)
                        }.build(),
                    )
                }.build(),
            )
            // Multi-process store
            .apply {
                addImport("kotlinx.coroutines", "launch")
                addImport("androidx.core.content", "edit")
                addImport("com.squareup.moshi.kotlin.reflect", "KotlinJsonAdapterFactory")
                addType(
                    createMultiProcessStore(
                        packageName = packageName,
                        scopeClassName = scopeClassName,
                        storeClassName = remoteFeatureStoreClassName,
                        featureClassName = remoteFeatureClassName,
                        preferencesName = preferencesName,
                    ),
                )
            }
            .build()

        fileSpec.writeTo(codeGenerator, dependencies)
    }

    // ========================================================================
    // Shared: multi-process store generation
    // ========================================================================

    private fun createMultiProcessStore(
        packageName: String,
        scopeClassName: ClassName,
        storeClassName: String,
        featureClassName: String,
        preferencesName: String,
    ): TypeSpec {
        return TypeSpec.classBuilder(storeClassName).apply {
            addAnnotation(
                AnnotationSpec.builder(CONTRIBUTES_BINDING)
                    .addMember("scope = %T::class", scopeClassName)
                    .build(),
            )
            addAnnotation(
                AnnotationSpec.builder(REMOTE_FEATURE_STORE_NAMED)
                    .addMember("value = %T::class", ClassName(packageName, featureClassName))
                    .build(),
            )
            addSuperinterface(TOGGLE_STORE)
            primaryConstructor(
                FunSpec.constructorBuilder()
                    .addAnnotation(AnnotationSpec.builder(INJECT).build())
                    .addParameter(
                        ParameterSpec.builder("coroutineScope", COROUTINE_SCOPE)
                            .addAnnotation(APP_COROUTINE_SCOPE)
                            .build(),
                    )
                    .addParameter("dispatcherProvider", DISPATCHER_PROVIDER)
                    .addParameter("sharedPreferencesProvider", SHARED_PREFERENCES_PROVIDER)
                    .addParameter("moshi", MOSHI)
                    .build(),
            )
            addProperty(
                PropertySpec.builder("coroutineScope", COROUTINE_SCOPE, KModifier.PRIVATE)
                    .initializer("coroutineScope")
                    .build(),
            )
            addProperty(
                PropertySpec.builder("dispatcherProvider", DISPATCHER_PROVIDER, KModifier.PRIVATE)
                    .initializer("dispatcherProvider")
                    .build(),
            )
            addProperty(
                PropertySpec.builder("sharedPreferencesProvider", SHARED_PREFERENCES_PROVIDER, KModifier.PRIVATE)
                    .initializer("sharedPreferencesProvider")
                    .build(),
            )
            addProperty(
                PropertySpec.builder("moshi", MOSHI, KModifier.PRIVATE)
                    .initializer("moshi")
                    .build(),
            )
            addProperty(
                PropertySpec.builder("preferences", SHARED_PREFERENCES, KModifier.PRIVATE)
                    .delegate(
                        CodeBlock.builder()
                            .beginControlFlow("lazy")
                            .add(
                                """
                                    sharedPreferencesProvider.getSharedPreferences("$preferencesName", multiprocess = true, migrate = false)
                                """.trimIndent(),
                            )
                            .endControlFlow()
                            .build(),
                    )
                    .build(),
            )
            addProperty(
                PropertySpec.builder(
                    "stateAdapter",
                    JSON_ADAPTER.parameterizedBy(TOGGLE_STATE),
                    KModifier.PRIVATE,
                )
                    .delegate(
                        CodeBlock.builder()
                            .beginControlFlow("lazy")
                            .add(
                                """
                                    moshi.newBuilder().add(KotlinJsonAdapterFactory()).build().adapter(%T::class.java)
                                """.trimIndent(),
                                TOGGLE_STATE,
                            )
                            .endControlFlow()
                            .build(),
                    )
                    .build(),
            )
            addFunction(
                FunSpec.builder("set")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("key", String::class.asClassName())
                    .addParameter("state", TOGGLE_STATE)
                    .addCode(
                        CodeBlock.of(
                            """
                                coroutineScope.launch(dispatcherProvider.io()) {
                                    preferences.edit(commit = true) { putString(key, stateAdapter.toJson(state)) }
                                }
                            """.trimIndent(),
                        ),
                    )
                    .build(),
            )
            addFunction(
                FunSpec.builder("get")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("key", String::class.asClassName())
                    .addCode(
                        CodeBlock.of(
                            """
                                return preferences.getString(key, null)?.let {
                                    stateAdapter.fromJson(it)
                                }
                            """.trimIndent(),
                        ),
                    )
                    .returns(TOGGLE_STATE.copy(nullable = true))
                    .build(),
            )
        }.build()
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private fun KSClassDeclaration.findAnnotation(fqName: String): KSAnnotation? {
        return annotations.firstOrNull { ann ->
            ann.annotationType.resolve().declaration.qualifiedName?.asString() == fqName
        }
    }

    private fun KSAnnotation.getArgumentType(name: String): KSType? {
        return arguments.firstOrNull { it.name?.asString() == name }?.value as? KSType
    }

    private fun KSAnnotation.getArgumentString(name: String): String? {
        return arguments.firstOrNull { it.name?.asString() == name }?.value as? String
    }

    private fun KSAnnotation.getArgumentInt(name: String): Int? {
        return arguments.firstOrNull { it.name?.asString() == name }?.value as? Int
    }

    private fun KSAnnotation.getArgumentBoolean(name: String): Boolean? {
        return arguments.firstOrNull { it.name?.asString() == name }?.value as? Boolean
    }

    /**
     * Returns the enum constant string representation suitable for KotlinPoet code generation.
     * For DefaultFeatureValue, returns "DefaultFeatureValue.TRUE" / "DefaultFeatureValue.FALSE" / "DefaultFeatureValue.INTERNAL".
     *
     * KSP 1.x returns enum values as KSType; KSP 2.x may return them as KSClassDeclaration (ENUM_ENTRY)
     * or as a String. This method handles all representations.
     */
    private fun KSAnnotation.getArgumentEnum(name: String): String? {
        val arg = arguments.firstOrNull { it.name?.asString() == name }?.value ?: return null
        return when (arg) {
            is KSType -> {
                val enumName = arg.declaration.simpleName.asString()
                "DefaultFeatureValue.$enumName"
            }
            is KSClassDeclaration -> {
                val enumName = arg.simpleName.asString()
                "DefaultFeatureValue.$enumName"
            }
            is String -> {
                // KSP2 may return the simple name directly
                "DefaultFeatureValue.$arg"
            }
            else -> {
                // Last resort: try toString and extract the enum entry name
                val str = arg.toString()
                val entryName = str.substringAfterLast(".")
                if (entryName in listOf("TRUE", "FALSE", "INTERNAL")) {
                    "DefaultFeatureValue.$entryName"
                } else {
                    logger.warn("Unexpected enum value type for $name: ${arg::class.simpleName} = $arg")
                    null
                }
            }
        }
    }
}
