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

import com.google.devtools.ksp.getDeclaredFunctions
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
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo

@OptIn(KotlinPoetKspPreview::class)
class ContributesRemoteFeatureProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    companion object {
        private const val CONTRIBUTES_REMOTE_FEATURE = "com.duckduckgo.anvil.annotations.ContributesRemoteFeature"
        private const val TOGGLE_DEFAULT_VALUE = "com.duckduckgo.feature.toggles.api.Toggle.DefaultValue"
        private const val REMOTE_FEATURE_STORE_NAMED = "com.duckduckgo.feature.toggles.api.RemoteFeatureStoreNamed"
        private const val SUPPRESS_FQ = "kotlin.Suppress"

        // Anvil annotations
        private val CONTRIBUTES_MULTIBINDING = ClassName("com.squareup.anvil.annotations", "ContributesMultibinding")
        private val CONTRIBUTES_BINDING = ClassName("com.squareup.anvil.annotations", "ContributesBinding")
        private val CONTRIBUTES_TO = ClassName("com.squareup.anvil.annotations", "ContributesTo")

        // Feature toggles API
        private val PRIVACY_FEATURE_PLUGIN = ClassName("com.duckduckgo.privacy.config.api", "PrivacyFeaturePlugin")
        private val TOGGLE = ClassName("com.duckduckgo.feature.toggles.api", "Toggle")
        private val TOGGLE_STORE = ClassName("com.duckduckgo.feature.toggles.api", "Toggle", "Store")
        private val TOGGLE_STATE = ClassName("com.duckduckgo.feature.toggles.api", "Toggle", "State")
        private val TOGGLE_STATE_TARGET = ClassName("com.duckduckgo.feature.toggles.api", "Toggle", "State", "Target")
        private val TOGGLE_STATE_COHORT = ClassName("com.duckduckgo.feature.toggles.api", "Toggle", "State", "Cohort")
        private val FEATURE_TOGGLES = ClassName("com.duckduckgo.feature.toggles.api", "FeatureToggles")
        private val FEATURE_TOGGLES_INVENTORY = ClassName("com.duckduckgo.feature.toggles.api", "FeatureTogglesInventory")
        private val FEATURE_SETTINGS = ClassName("com.duckduckgo.feature.toggles.api", "FeatureSettings")
        private val FEATURE_SETTINGS_STORE = ClassName("com.duckduckgo.feature.toggles.api", "FeatureSettings", "Store")
        private val FEATURE_EXCEPTION = ClassName("com.duckduckgo.feature.toggles.api", "FeatureException")
        private val REMOTE_FEATURE_STORE_NAMED_CLASS = ClassName("com.duckduckgo.feature.toggles.api", "RemoteFeatureStoreNamed")

        // Feature toggles internal API
        private val FEATURE_TOGGLES_CALLBACK = ClassName("com.duckduckgo.feature.toggles.internal.api", "FeatureTogglesCallback")
        private val JSON_FEATURE = ClassName("com.duckduckgo.feature.toggles.internal.api", "JsonFeature")
        private val JSON_EXCEPTION = ClassName("com.duckduckgo.feature.toggles.internal.api", "JsonException")

        // App build config
        private val APP_BUILD_CONFIG = ClassName("com.duckduckgo.appbuildconfig.api", "AppBuildConfig")
        private val BUILD_FLAVOR_INTERNAL = ClassName("com.duckduckgo.appbuildconfig.api", "BuildFlavor", "INTERNAL")

        // Variant manager
        private val VARIANT_MANAGER = ClassName("com.duckduckgo.experiments.api", "VariantManager")

        // Dagger
        private val SINGLE_INSTANCE_IN = ClassName("dagger", "SingleInstanceIn")
        private val DAGGER_MODULE = ClassName("dagger", "Module")
        private val DAGGER_PROVIDES = ClassName("dagger", "Provides")
        private val DAGGER_LAZY = ClassName("dagger", "Lazy")
        private val INTO_SET = ClassName("dagger.multibindings", "IntoSet")
        private val INJECT = ClassName("javax.inject", "Inject")

        // Android
        private val SHARED_PREFERENCES = ClassName("android.content", "SharedPreferences")
        private val CONTEXT = ClassName("android.content", "Context")

        // Other
        private val MOSHI = ClassName("com.squareup.moshi", "Moshi")
        private val OKIO_BUFFER = ClassName("okio", "Buffer")

        private val UNIT_CLASS_NAME = ClassName("kotlin", "Unit")
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(CONTRIBUTES_REMOTE_FEATURE).toList()
        val deferred = mutableListOf<KSAnnotated>()

        for (symbol in symbols) {
            if (!symbol.validate()) {
                deferred.add(symbol)
                continue
            }

            if (symbol !is KSClassDeclaration) continue

            val customStorePresence = validateRemoteFeatureInterface(symbol) ?: continue
            generateRemoteFeature(symbol, customStorePresence)
            generateFeatureToggleProxy(symbol, customStorePresence)
        }

        return deferred
    }

    // ========================================================================
    // Validation
    // ========================================================================

    private data class CustomStorePresence(
        val settingsStorePresent: Boolean = false,
        val toggleStorePresent: Boolean = false,
    )

    private fun validateRemoteFeatureInterface(classDecl: KSClassDeclaration): CustomStorePresence? {
        val fqName = classDecl.qualifiedName?.asString() ?: return null

        // Must be an interface
        if (classDecl.classKind != ClassKind.INTERFACE) {
            logger.error("$fqName must be an interface", classDecl)
            return null
        }

        // Validate only @ContributesRemoteFeature and/or @Suppress
        val invalidAnnotations = classDecl.annotations.filter { ann ->
            val annFq = ann.annotationType.resolve().declaration.qualifiedName?.asString()
            annFq != CONTRIBUTES_REMOTE_FEATURE && annFq != SUPPRESS_FQ
        }.toList()
        if (invalidAnnotations.isNotEmpty()) {
            logger.error(
                "$fqName can only be annotated with @ContributesRemoteFeature or @Suppress",
                classDecl,
            )
            return null
        }

        val annotation = classDecl.findAnnotation(CONTRIBUTES_REMOTE_FEATURE) ?: return null

        // Validate scope
        if (annotation.getArgumentType("scope") == null) {
            logger.error(
                "$fqName annotated with @ContributesRemoteFeature must define [scope]",
                classDecl,
            )
            return null
        }

        // Validate featureName
        if (annotation.getArgumentString("featureName") == null) {
            logger.error(
                "$fqName annotated with @ContributesRemoteFeature must define [featureName]",
                classDecl,
            )
            return null
        }

        var settingsStorePresent = false
        var toggleStorePresent = false

        // Validate settingsStore
        val settingsStoreType = annotation.getArgumentType("settingsStore")
        if (settingsStoreType != null && settingsStoreType.declaration.qualifiedName?.asString() != "kotlin.Unit") {
            settingsStorePresent = true
            val settingsStoreDecl = settingsStoreType.declaration as? KSClassDeclaration
            if (settingsStoreDecl != null) {
                // Check it extends FeatureSettings.Store
                val extendsStore = settingsStoreDecl.superTypes.any { superRef ->
                    val superDecl = superRef.resolve().declaration
                    superDecl.qualifiedName?.asString() == "com.duckduckgo.feature.toggles.api.FeatureSettings.Store"
                }
                if (!extendsStore) {
                    logger.error("$fqName [settingsStore] must extend [FeatureSettings.Store]", classDecl)
                    return null
                }
                if (!validateFeatureAndStoreCrossReference(classDecl, settingsStoreDecl, annotation)) return null
            }
        }

        // Validate toggleStore
        val toggleStoreType = annotation.getArgumentType("toggleStore")
        if (toggleStoreType != null && toggleStoreType.declaration.qualifiedName?.asString() != "kotlin.Unit") {
            toggleStorePresent = true
            val toggleStoreDecl = toggleStoreType.declaration as? KSClassDeclaration
            if (toggleStoreDecl != null) {
                val extendsStore = toggleStoreDecl.superTypes.any { superRef ->
                    val superDecl = superRef.resolve().declaration
                    superDecl.qualifiedName?.asString() == "com.duckduckgo.feature.toggles.api.Toggle.Store"
                }
                if (!extendsStore) {
                    logger.error("$fqName [toggleStore] must extend [Toggle.Store]", classDecl)
                    return null
                }
                if (!validateFeatureAndStoreCrossReference(classDecl, toggleStoreDecl, annotation)) return null
            }
        }

        // Resolve boundType for function validation
        val boundTypeDecl = resolveBoundType(classDecl, annotation)

        val declaredFunctions = boundTypeDecl.getDeclaredFunctions()
            .filter { it.simpleName.asString() !in setOf("equals", "hashCode", "toString") }
            .toList()

        val boundFqName = boundTypeDecl.qualifiedName?.asString() ?: fqName

        // All functions must return Toggle
        if (declaredFunctions.any { fn ->
                val returnType = fn.returnType?.resolve()?.declaration?.qualifiedName?.asString()
                returnType != "com.duckduckgo.feature.toggles.api.Toggle"
            }
        ) {
            logger.error("$boundFqName can only contain functions that return [Toggle]", classDecl)
            return null
        }

        // Functions can't have parameters
        if (declaredFunctions.any { it.parameters.isNotEmpty() }) {
            logger.error("$boundFqName can only contain functions without parameters", classDecl)
            return null
        }

        // All functions must be annotated with @Toggle.DefaultValue
        if (declaredFunctions.any { fn ->
                fn.annotations.none { ann ->
                    ann.annotationType.resolve().declaration.qualifiedName?.asString() == TOGGLE_DEFAULT_VALUE
                }
            }
        ) {
            logger.error(
                "All functions in $boundFqName must be annotated with [Toggle.DefaultValue]",
                classDecl,
            )
            return null
        }

        // Validate single DefaultValue annotation per function
        for (fn in declaredFunctions) {
            val count = fn.annotations.count { ann ->
                ann.annotationType.resolve().declaration.qualifiedName?.asString() == TOGGLE_DEFAULT_VALUE
            }
            if (count > 1) {
                logger.error(
                    "All functions in $boundFqName must be annotated with single [Toggle.DefaultValue]",
                    classDecl,
                )
                return null
            }
        }

        // Must have self() function
        if (declaredFunctions.none { it.simpleName.asString() == "self" }) {
            logger.error("$boundFqName must have a function self()", classDecl)
            return null
        }

        return CustomStorePresence(
            settingsStorePresent = settingsStorePresent,
            toggleStorePresent = toggleStorePresent,
        )
    }

    private fun validateFeatureAndStoreCrossReference(
        featureDecl: KSClassDeclaration,
        storeDecl: KSClassDeclaration,
        annotation: KSAnnotation,
    ): Boolean {
        val featureFq = featureDecl.qualifiedName?.asString() ?: return false
        val storeFq = storeDecl.qualifiedName?.asString() ?: return false

        // Check store is annotated with @RemoteFeatureStoreNamed
        val storeNamedAnn = storeDecl.annotations.firstOrNull { ann ->
            ann.annotationType.resolve().declaration.qualifiedName?.asString() == REMOTE_FEATURE_STORE_NAMED
        }
        if (storeNamedAnn == null) {
            logger.error("$storeFq shall be annotated with [RemoteFeatureStoreNamed]", featureDecl)
            return false
        }

        // Check they cross-reference each other
        val storeReferencedType = storeNamedAnn.getArgumentType("value")
        val storeReferencedFq = storeReferencedType?.declaration?.qualifiedName?.asString()

        val boundTypeType = annotation.getArgumentType("boundType")
        val featureClassFq = if (boundTypeType != null && boundTypeType.declaration.qualifiedName?.asString() != "kotlin.Unit") {
            boundTypeType.declaration.qualifiedName?.asString()
        } else {
            featureFq
        }

        if (storeReferencedFq != featureClassFq) {
            logger.error("$featureFq and $featureClassFq don't reference each other", featureDecl)
            return false
        }

        return true
    }

    private fun resolveBoundType(classDecl: KSClassDeclaration, annotation: KSAnnotation): KSClassDeclaration {
        val boundTypeType = annotation.getArgumentType("boundType")
        if (boundTypeType != null && boundTypeType.declaration.qualifiedName?.asString() != "kotlin.Unit") {
            return boundTypeType.declaration as KSClassDeclaration
        }
        return classDecl
    }

    // ========================================================================
    // _RemoteFeature generation
    // ========================================================================

    private fun generateRemoteFeature(classDecl: KSClassDeclaration, customStorePresence: CustomStorePresence) {
        val annotation = classDecl.findAnnotation(CONTRIBUTES_REMOTE_FEATURE) ?: return
        val packageName = classDecl.packageName.asString()
        val className = "${classDecl.simpleName.asString()}_RemoteFeature"
        val boundType = resolveBoundType(classDecl, annotation)
        val boundTypeClassName = boundType.toClassName()
        val featureName = annotation.getArgumentString("featureName")!!
        val scope = annotation.getArgumentType("scope")!!.toClassName()

        val typeSpec = TypeSpec.classBuilder(className)
            .addAnnotations(buildRemoteFeatureAnnotations(scope, boundTypeClassName, customStorePresence))
            .addSuperinterface(PRIVACY_FEATURE_PLUGIN)
            .addSuperinterface(TOGGLE_STORE)
            .primaryConstructor(buildRemoteFeatureConstructor(boundTypeClassName))
            .addProperty(
                PropertySpec.builder("settingsStore", FEATURE_SETTINGS_STORE, KModifier.PRIVATE)
                    .initializer("settingsStore")
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("feature", DAGGER_LAZY.parameterizedBy(boundTypeClassName), KModifier.PRIVATE)
                    .initializer("feature")
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("appBuildConfig", APP_BUILD_CONFIG, KModifier.PRIVATE)
                    .initializer("appBuildConfig")
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("variantManager", VARIANT_MANAGER, KModifier.PRIVATE)
                    .initializer("variantManager")
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("context", CONTEXT, KModifier.PRIVATE)
                    .initializer("context")
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("moshi", MOSHI, KModifier.PRIVATE)
                    .initializer("REMOTE_FEATURE_MOSHI")
                    .build(),
            )
            .addProperty(createSharedPreferencesProperty(featureName))
            .addProperty(createFeatureNameProperty(featureName))
            .addFunction(createFeatureHashcode())
            .addFunction(createStoreOverride())
            .addFunctions(createToggleStoreImplementation())
            .addFunction(createCompareAndSetHash())
            .addFunction(createParseJsonFun())
            .addFunction(createParseExceptions())
            .addFunction(createInvokeMethod(boundTypeClassName))
            .build()

        val fileSpec = FileSpec.builder(packageName, className)
            .addImport("com.duckduckgo.feature.toggles.internal.api", "REMOTE_FEATURE_MOSHI")
            .addType(typeSpec)
            .build()

        val containingFile = classDecl.containingFile
        val dependencies = if (containingFile != null) {
            Dependencies(aggregating = false, containingFile)
        } else {
            Dependencies(aggregating = false)
        }

        fileSpec.writeTo(codeGenerator, dependencies)
    }

    private fun buildRemoteFeatureAnnotations(
        scope: ClassName,
        boundTypeClassName: ClassName,
        customStorePresence: CustomStorePresence,
    ): List<AnnotationSpec> {
        return buildList {
            add(
                AnnotationSpec.builder(CONTRIBUTES_MULTIBINDING)
                    .addMember("scope = %T::class", scope)
                    .addMember("boundType = %T::class", PRIVACY_FEATURE_PLUGIN)
                    .addMember("ignoreQualifier = true")
                    .build(),
            )
            if (!customStorePresence.toggleStorePresent) {
                add(
                    AnnotationSpec.builder(CONTRIBUTES_BINDING)
                        .addMember("scope = %T::class", scope)
                        .addMember("boundType = %T::class", TOGGLE_STORE)
                        .build(),
                )
                add(
                    AnnotationSpec.builder(REMOTE_FEATURE_STORE_NAMED_CLASS)
                        .addMember("value = %T::class", boundTypeClassName)
                        .build(),
                )
            }
            add(
                AnnotationSpec.builder(SINGLE_INSTANCE_IN)
                    .addMember("scope = %T::class", scope)
                    .build(),
            )
        }
    }

    private fun buildRemoteFeatureConstructor(boundTypeClassName: ClassName): FunSpec {
        return FunSpec.constructorBuilder()
            .addAnnotation(AnnotationSpec.builder(INJECT).build())
            .addParameter(
                ParameterSpec.builder("settingsStore", FEATURE_SETTINGS_STORE)
                    .addAnnotation(
                        AnnotationSpec.builder(REMOTE_FEATURE_STORE_NAMED_CLASS)
                            .addMember("value = %T::class", boundTypeClassName)
                            .build(),
                    )
                    .build(),
            )
            .addParameter("feature", DAGGER_LAZY.parameterizedBy(boundTypeClassName))
            .addParameter("appBuildConfig", APP_BUILD_CONFIG)
            .addParameter("variantManager", VARIANT_MANAGER)
            .addParameter("context", CONTEXT)
            .build()
    }

    private fun createFeatureNameProperty(featureName: String): PropertySpec {
        return PropertySpec.builder("featureName", String::class)
            .addModifiers(KModifier.OVERRIDE)
            .initializer(CodeBlock.of("%S", featureName))
            .build()
    }

    private fun createSharedPreferencesProperty(featureName: String): PropertySpec {
        val filename = "com.duckduckgo.feature.toggle.$featureName"
        return PropertySpec.builder("preferences", SHARED_PREFERENCES)
            .addModifiers(KModifier.PRIVATE)
            .getter(
                FunSpec.builder("get()")
                    .addCode(CodeBlock.of("return context.getSharedPreferences(%S, Context.MODE_PRIVATE)", filename))
                    .build(),
            )
            .build()
    }

    private fun createFeatureHashcode(): FunSpec {
        return FunSpec.builder("hash")
            .addModifiers(KModifier.OVERRIDE)
            .addCode(
                CodeBlock.of(
                    """
                        try {
                            // try to hash with all sub-features
                            val concatMethodNames = this.feature.get().javaClass
                                .declaredMethods
                                .map { it.name }
                                .sorted()
                                .joinToString(separator = "")
                            val hash = %T().writeUtf8(concatMethodNames).md5().hex()
                            return hash
                        } catch(e: Throwable) {
                            // fallback to just featureName
                            return this.featureName
                        }
                    """.trimIndent(),
                    OKIO_BUFFER,
                ),
            )
            .returns(String::class.asClassName().copy(nullable = true))
            .build()
    }

    private fun createStoreOverride(): FunSpec {
        return FunSpec.builder("store")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter(ParameterSpec.builder("featureName", String::class).build())
            .addParameter(ParameterSpec.builder("jsonString", String::class).build())
            .addCode(
                """
                if (featureName == this.featureName) {
                    val feature = parseJson(jsonString) ?: return false

                    // feature hash is the hash of the feature + hash coming from remote config
                    // this way we evaluate either when remote config has changes OR when feature changes
                    // when the feature.hash (remote config) is null we always re-evaluate
                    if (feature.hash != null) {
                        val _hash = hash() + feature.hash
                        if (compareAndSetHash(_hash)) return true
                    }

                    val exceptions = parseExceptions(feature.exceptions)

                    val isEnabled = (feature.state == "enabled") || (appBuildConfig.flavor == %T && feature.state == "internal")
                    this.feature.get().invokeMethod("self").setRawStoredState(
                        Toggle.State(
                            remoteEnableState = isEnabled,
                            enable = isEnabled,
                            minSupportedVersion = feature.minSupportedVersion,
                            targets = emptyList(),
                            cohorts = emptyList(),
                            settings = feature.settings?.toString(),
                            exceptions = exceptions,
                        )
                    )

                    // Handle sub-features
                    feature.features?.forEach { subfeature ->
                        subfeature.value.let { jsonToggle ->
                            // try-catch to just skip any issues with a particular
                            // sub-feature and continue with the rest of them
                            try {
                                val previousState = this.feature.get().invokeMethod(subfeature.key).getRawStoredState()
                                // we try to honour the previous state
                                // else we resort to compute it using isEnabled()
                                val previousStateValue = previousState?.enable ?: this.feature.get().invokeMethod(subfeature.key).isEnabled()

                                val previousRolloutThreshold = previousState?.rolloutThreshold
                                val previousAssignedCohort = previousState?.assignedCohort
                                val newStateValue = (jsonToggle.state == "enabled" || (appBuildConfig.flavor == %T && jsonToggle.state == "internal"))
                                val targets = jsonToggle?.targets?.map { target ->
                                    Toggle.State.Target(
                                        variantKey = target.variantKey,
                                        localeCountry = target.localeCountry,
                                        localeLanguage = target.localeLanguage,
                                        isReturningUser = target.isReturningUser,
                                        isPrivacyProEligible = target.isPrivacyProEligible,
                                        entitlement = target.entitlement,
                                        minSdkVersion = target.minSdkVersion,
                                    )
                                } ?: emptyList()
                                val cohorts = jsonToggle?.cohorts?.map { cohort ->
                                    Toggle.State.Cohort(
                                        name = cohort.name,
                                        weight = cohort.weight,
                                    )
                                } ?: emptyList()
                                val settings = jsonToggle?.settings?.toString()
                                val subFeatureExceptions = parseExceptions(jsonToggle.exceptions)
                                this.feature.get().invokeMethod(subfeature.key).setRawStoredState(
                                    Toggle.State(
                                        remoteEnableState = newStateValue,
                                        enable = previousStateValue,
                                        minSupportedVersion = jsonToggle.minSupportedVersion?.toInt(),
                                        rollout = jsonToggle?.rollout?.steps?.map { it.percent },
                                        rolloutThreshold = previousRolloutThreshold,
                                        assignedCohort = previousAssignedCohort,
                                        targets = targets,
                                        cohorts = cohorts,
                                        settings = settings,
                                        exceptions = subFeatureExceptions,
                                    ),
                                )
                            } catch(e: Throwable) {
                                // noop
                            }
                        }
                    }

                    // handle settings
                    feature.settings?.let {
                        settingsStore.store(it.toString())
                    }

                    return true
                }
                return false
                """.trimIndent(),
                BUILD_FLAVOR_INTERNAL,
                BUILD_FLAVOR_INTERNAL,
            )
            .returns(Boolean::class)
            .build()
    }

    private fun createToggleStoreImplementation(): List<FunSpec> {
        return listOf(
            FunSpec.builder("set")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("key", String::class)
                .addParameter("state", TOGGLE_STATE)
                .addCode(
                    CodeBlock.of(
                        """
                            val jsonAdapter = moshi.adapter(%T::class.java)
                            preferences.edit().putString(key, jsonAdapter.toJson(state)).apply()
                        """.trimIndent(),
                        TOGGLE_STATE,
                    ),
                )
                .build(),
            FunSpec.builder("get")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("key", String::class)
                .addCode(
                    CodeBlock.of(
                        """
                            val jsonAdapter = moshi.adapter(%T::class.java)
                            return kotlin.runCatching { jsonAdapter.fromJson(preferences.getString(key, null)) }.getOrNull()
                        """.trimIndent(),
                        TOGGLE_STATE,
                    ),
                )
                .returns(TOGGLE_STATE.copy(nullable = true))
                .build(),
        )
    }

    private fun createCompareAndSetHash(): FunSpec {
        return FunSpec.builder("compareAndSetHash")
            .addModifiers(KModifier.PRIVATE)
            .addParameter("hash", String::class.asClassName().copy(nullable = true))
            .addCode(
                CodeBlock.builder()
                    .add(
                        """
                            if (hash == null) return false
                            val currentHash = preferences.getString("hash", null)
                            if (hash == currentHash) return true
                            preferences.edit().putString("hash", hash).apply()
                            return false
                        """.trimIndent(),
                    )
                    .build(),
            )
            .returns(Boolean::class)
            .build()
    }

    private fun createParseJsonFun(): FunSpec {
        return FunSpec.builder("parseJson")
            .addModifiers(KModifier.PRIVATE)
            .addParameter("jsonString", String::class)
            .addCode(
                CodeBlock.builder()
                    .add(
                        """
                            val jsonAdapter = moshi.adapter(JsonFeature::class.java)
                            return jsonAdapter.fromJson(jsonString)
                        """.trimIndent(),
                    ).build(),
            )
            .returns(JSON_FEATURE.copy(nullable = true))
            .build()
    }

    private fun createParseExceptions(): FunSpec {
        return FunSpec.builder("parseExceptions")
            .addModifiers(KModifier.PRIVATE)
            .addParameter("exceptions", List::class.asClassName().parameterizedBy(JSON_EXCEPTION))
            .addCode(
                CodeBlock.builder()
                    .add(
                        """
                            val featureExceptions = mutableListOf<%T>()
                            exceptions?.map { ex ->
                                featureExceptions.add(%T(ex.domain, ex.reason))
                            }
                            return featureExceptions.toList()
                        """.trimIndent(),
                        FEATURE_EXCEPTION,
                        FEATURE_EXCEPTION,
                    ).build(),
            )
            .returns(List::class.asClassName().parameterizedBy(FEATURE_EXCEPTION))
            .build()
    }

    private fun createInvokeMethod(boundTypeClassName: ClassName): FunSpec {
        return FunSpec.builder("invokeMethod")
            .receiver(boundTypeClassName)
            .addParameter("name", String::class)
            .addCode(
                """
                val toggle = kotlin.runCatching {
                    this.javaClass.getDeclaredMethod(name)
                }.getOrNull()?.invoke(this) as Toggle
                return toggle
                """.trimIndent(),
            )
            .returns(TOGGLE)
            .build()
    }

    // ========================================================================
    // _ProxyModule generation
    // ========================================================================

    private fun generateFeatureToggleProxy(classDecl: KSClassDeclaration, customStorePresence: CustomStorePresence) {
        val annotation = classDecl.findAnnotation(CONTRIBUTES_REMOTE_FEATURE) ?: return
        val packageName = classDecl.packageName.asString()
        val boundType = resolveBoundType(classDecl, annotation)
        val boundTypeClassName = boundType.toClassName()
        val generatedClassName = "${classDecl.simpleName.asString()}_ProxyModule"
        val featureName = annotation.getArgumentString("featureName")!!
        val scope = annotation.getArgumentType("scope")!!.toClassName()

        val declaredFunctions = boundType.getDeclaredFunctions()
            .filter { it.simpleName.asString() !in setOf("equals", "hashCode", "toString") }
            .toList()

        val typeSpecBuilder = TypeSpec.objectBuilder(generatedClassName)
            .addAnnotation(AnnotationSpec.builder(DAGGER_MODULE).build())
            .addAnnotation(
                AnnotationSpec.builder(CONTRIBUTES_TO)
                    .addMember("scope = %T::class", scope)
                    .build(),
            )
            .addFunction(
                FunSpec.builder("provides${boundType.simpleName.asString()}")
                    .addAnnotation(DAGGER_PROVIDES)
                    .addAnnotation(
                        AnnotationSpec.builder(SINGLE_INSTANCE_IN)
                            .addMember("scope = %T::class", scope)
                            .build(),
                    )
                    .addParameter(
                        ParameterSpec.builder("toggleStore", TOGGLE_STORE)
                            .addAnnotation(
                                AnnotationSpec.builder(REMOTE_FEATURE_STORE_NAMED_CLASS)
                                    .addMember("value = %T::class", boundTypeClassName)
                                    .build(),
                            )
                            .build(),
                    )
                    .addParameter("callback", FEATURE_TOGGLES_CALLBACK)
                    .addParameter("appBuildConfig", APP_BUILD_CONFIG)
                    .addParameter("variantManager", VARIANT_MANAGER)
                    .addCode(
                        """
                            return %T.Builder()
                                .store(toggleStore)
                                .appVersionProvider({ appBuildConfig.versionCode })
                                .flavorNameProvider({ appBuildConfig.flavor.name })
                                .featureName(%S)
                                .appVariantProvider({ appBuildConfig.variantName })
                                .callback(callback)
                                // save empty variants will force the default variant to be set
                                .forceDefaultVariantProvider({ variantManager.updateVariants(emptyList()) })
                                .build()
                                .create(%T::class.java)
                        """.trimIndent(),
                        FEATURE_TOGGLES,
                        featureName,
                        boundTypeClassName,
                    )
                    .returns(boundTypeClassName)
                    .build(),
            )

        if (!customStorePresence.settingsStorePresent) {
            typeSpecBuilder.addFunction(
                FunSpec.builder("providesNoopSettingsStore")
                    .addAnnotation(DAGGER_PROVIDES)
                    .addAnnotation(
                        AnnotationSpec.builder(REMOTE_FEATURE_STORE_NAMED_CLASS)
                            .addMember("value = %T::class", boundTypeClassName)
                            .build(),
                    )
                    .addCode(
                        CodeBlock.of(
                            """
                                return %T.EMPTY_STORE
                            """.trimIndent(),
                            FEATURE_SETTINGS,
                        ),
                    )
                    .returns(FEATURE_SETTINGS_STORE)
                    .build(),
            )
        }

        typeSpecBuilder.addFunction(
            FunSpec.builder("provides${boundType.simpleName.asString()}Inventory")
                .addAnnotation(DAGGER_PROVIDES)
                .addAnnotation(
                    AnnotationSpec.builder(SINGLE_INSTANCE_IN)
                        .addMember("scope = %T::class", scope)
                        .build(),
                )
                .addAnnotation(INTO_SET)
                .addParameter("feature", boundTypeClassName)
                .addCode(
                    CodeBlock.of(
                        """
                            return object : %T {
                                override suspend fun getAll(): %T<%T> {
                                    return listOf(
                                        ${declaredFunctions.joinToString(
                            separator = ",\n                        ",
                        ) { "feature.${it.simpleName.asString()}()" }}
                                    )
                                }
                            }
                        """.trimIndent(),
                        FEATURE_TOGGLES_INVENTORY,
                        List::class.asClassName(),
                        TOGGLE,
                    ),
                )
                .returns(FEATURE_TOGGLES_INVENTORY)
                .build(),
        )

        val fileSpec = FileSpec.builder(packageName, generatedClassName)
            .addType(typeSpecBuilder.build())
            .build()

        val containingFile = classDecl.containingFile
        val dependencies = if (containingFile != null) {
            Dependencies(aggregating = false, containingFile)
        } else {
            Dependencies(aggregating = false)
        }

        fileSpec.writeTo(codeGenerator, dependencies)
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
}
