/*
 * Copyright (c) 2023 DuckDuckGo
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

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.feature.toggles.api.*
import com.google.auto.service.AutoService
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.api.*
import com.squareup.anvil.compiler.internal.asClassName
import com.squareup.anvil.compiler.internal.buildFile
import com.squareup.anvil.compiler.internal.fqName
import com.squareup.anvil.compiler.internal.reference.*
import com.squareup.anvil.compiler.internal.reference.ClassReference.Psi
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import dagger.BindsOptionalOf
import dagger.Provides
import dagger.multibindings.IntoSet
import java.io.File
import javax.inject.Inject
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addToStdlib.ifFalse

@OptIn(ExperimentalAnvilApi::class)
@AutoService(CodeGenerator::class)
class ContributesRemoteFeatureCodeGenerator : CodeGenerator {

    override fun isApplicable(context: AnvilContext): Boolean = true

    override fun generateCode(
        codeGenDir: File,
        module: ModuleDescriptor,
        projectFiles: Collection<KtFile>,
    ): Collection<GeneratedFile> {
        return projectFiles.classAndInnerClassReferences(module)
            .toList()
            .filter { it.isAnnotatedWith(ContributesRemoteFeature::class.fqName) }
            .map { validateRemoteFeatureInterface(it, module) to it }
            .flatMap { info ->
                val (customStorePresence, classRef) = info
                listOf(
                    generateRemoteFeature(classRef, codeGenDir, module, customStorePresence),
                    generateFeatureToggleProxy(classRef, codeGenDir, module, customStorePresence),
//                    generateOptionalBindings(it, codeGenDir, module),
                )
            }
            .toList()
    }

    private fun generateFeatureToggleProxy(
        vmClass: ClassReference.Psi,
        codeGenDir: File,
        module: ModuleDescriptor,
        customStorePresence: CustomStorePresence,
    ): GeneratedFile {
        val generatedPackage = vmClass.packageFqName.toString()
        val generatedClassName = "${vmClass.shortName}_ProxyModule"
        val annotation = vmClass.annotations.first { it.fqName == ContributesRemoteFeature::class.fqName }
        val boundType = annotation.boundTypeOrNull() ?: vmClass
        val scope = vmClass.annotations.first { it.fqName == ContributesRemoteFeature::class.fqName }.scopeOrNull()!!
        val featureName = annotation.featureNameOrNull()

        val content = FileSpec.buildFile(generatedPackage, generatedClassName) {
            addType(
                TypeSpec.objectBuilder(generatedClassName)
                    .addAnnotation(AnnotationSpec.builder(dagger.Module::class).build())
                    .addAnnotation(
                        AnnotationSpec
                            .builder(ContributesTo::class).addMember("scope = %T::class", scope.asClassName())
                            .build(),
                    )
                    .addFunction(
                        FunSpec.builder("provides${boundType.shortName}")
                            .addAnnotation(Provides::class.asClassName())
                            .addAnnotation(
                                AnnotationSpec.builder(singleInstanceAnnotationFqName.asClassName(module))
                                    .addMember("scope = %T::class", scope.asClassName())
                                    .build(),
                            )
                            .addParameter(
                                ParameterSpec
                                    .builder("toggleStore", Toggle.Store::class.asClassName())
                                    .addAnnotation(
                                        AnnotationSpec.builder(RemoteFeatureStoreNamed::class.asClassName())
                                            .addMember("value = %T::class", boundType.asClassName())
                                            .build(),
                                    )
                                    .build(),
                            )
                            .addParameter("callback", featureTogglesCallback.asClassName(module))
                            .addParameter("appBuildConfig", appBuildConfig.asClassName(module))
                            .addParameter("variantManager", variantManager.asClassName(module))
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
                                FeatureToggles::class.asClassName(),
                                featureName,
                                boundType.asClassName(),
                            )
                            .returns(boundType.asClassName())
                            .build(),
                    ).apply {
                        if (!customStorePresence.settingsStorePresent) {
                            addFunction(
                                FunSpec.builder("providesNoopSettingsStore")
                                    .addAnnotation(Provides::class.asClassName())
                                    .addAnnotation(
                                        AnnotationSpec.builder(RemoteFeatureStoreNamed::class.asClassName())
                                            .addMember("value = %T::class", boundType.asClassName())
                                            .build(),
                                    )
                                    .addCode(
                                        CodeBlock.of(
                                            """
                                                return %T.EMPTY_STORE
                                            """.trimIndent(),
                                            FeatureSettings::class.asClassName(),
                                        ),
                                    )
                                    .returns(FeatureSettings.Store::class.asClassName())
                                    .build(),
                            )
                        }
                        addFunction(
                            FunSpec.builder("provides${boundType.shortName}Inventory")
                                .addAnnotation(Provides::class.asClassName())
                                .addAnnotation(
                                    AnnotationSpec.builder(singleInstanceAnnotationFqName.asClassName(module))
                                        .addMember("scope = %T::class", scope.asClassName())
                                        .build(),
                                )
                                .addAnnotation(IntoSet::class.asClassName())
                                .addParameter("feature", boundType.asClassName())
                                .addCode(
                                    CodeBlock.of(
                                        """
                                            return object : FeatureTogglesInventory {
                                                override suspend fun getAll(): List<Toggle> {
                                                    return feature.javaClass.declaredMethods.mapNotNull { method ->
                                                        if (method.genericReturnType.toString().contains(Toggle::class.java.canonicalName!!)) {
                                                            method.invoke(feature) as Toggle
                                                        } else {
                                                            null
                                                        }
                                                    }
                                                }
                                            }
                                        """.trimIndent(),
                                    ),
                                )
                                .returns(FeatureTogglesInventory::class.asClassName())
                                .build(),
                        )
                    }
                    .build(),
            )
        }

        return createGeneratedFile(codeGenDir, generatedPackage, generatedClassName, content)
    }

    private fun generateRemoteFeature(
        vmClass: ClassReference.Psi,
        codeGenDir: File,
        module: ModuleDescriptor,
        customStorePresence: CustomStorePresence,
    ): GeneratedFile {
        val generatedPackage = vmClass.packageFqName.toString()
        val generatedClassName = "${vmClass.shortName}_RemoteFeature"
        val annotation = vmClass.annotations.first { it.fqName == ContributesRemoteFeature::class.fqName }
        val boundType = annotation.boundTypeOrNull() ?: vmClass
        val featureName = vmClass.annotations.first { it.fqName == ContributesRemoteFeature::class.fqName }.featureNameOrNull()!!
        val scope = vmClass.annotations.first { it.fqName == ContributesRemoteFeature::class.fqName }.scopeOrNull()!!

        val content = FileSpec.buildFile(generatedPackage, generatedClassName) {
            addType(
                TypeSpec.classBuilder(generatedClassName)
                    .addAnnotations(
                        buildList {
                            add(
                                AnnotationSpec
                                    .builder(ContributesMultibinding::class)
                                    .addMember("scope = %T::class", scope.asClassName())
                                    .addMember("boundType = %T::class", privacyFeaturePlugin.asClassName(module))
                                    .addMember("ignoreQualifier = true")
                                    .build(),
                            )
                            customStorePresence.toggleStorePresent.ifFalse {
                                add(
                                    AnnotationSpec
                                        .builder(ContributesBinding::class)
                                        .addMember("scope = %T::class", scope.asClassName())
                                        .addMember("boundType = %T::class", Toggle.Store::class.asClassName())
                                        .build(),
                                )
                                add(
                                    AnnotationSpec
                                        .builder(RemoteFeatureStoreNamed::class).addMember("value = %T::class", boundType.asClassName())
                                        .build(),
                                )
                            }
                            add(
                                AnnotationSpec
                                    .builder(singleInstanceAnnotationFqName.asClassName(module))
                                    .addMember("scope = %T::class", scope.asClassName())
                                    .build(),
                            )
                        },
                    )
                    .addSuperinterface(privacyFeaturePlugin.asClassName(module))
                    .addSuperinterface(Toggle.Store::class.asClassName())
                    .primaryConstructor(
                        FunSpec.constructorBuilder()
                            .addAnnotation(AnnotationSpec.builder(Inject::class).build())
                            .addParameter(
                                ParameterSpec
                                    .builder(
                                        "settingsStore",
                                        FeatureSettings.Store::class,
                                    )
                                    .addAnnotation(
                                        AnnotationSpec
                                            .builder(RemoteFeatureStoreNamed::class).addMember("value = %T::class", boundType.asClassName())
                                            .build(),

                                    )
                                    .build(),
                            )
                            .addParameter("feature", dagger.Lazy::class.asClassName().parameterizedBy(boundType.asClassName()))
                            .addParameter("appBuildConfig", appBuildConfig.asClassName(module))
                            .addParameter("variantManager", variantManager.asClassName(module))
                            .addParameter("context", context.asClassName(module))
                            .build(),
                    )
                    .addProperty(
                        PropertySpec
                            .builder(
                                "settingsStore",
                                FeatureSettings.Store::class,
                                KModifier.PRIVATE,
                            )
                            .initializer("settingsStore")
                            .build(),
                    )
                    .addProperty(
                        PropertySpec.builder("feature", dagger.Lazy::class.asClassName().parameterizedBy(boundType.asClassName()), KModifier.PRIVATE)
                            .initializer("feature")
                            .build(),
                    )
                    .addProperty(
                        PropertySpec.builder("appBuildConfig", appBuildConfig.asClassName(module), KModifier.PRIVATE)
                            .initializer("appBuildConfig")
                            .build(),
                    )
                    .addProperty(
                        PropertySpec.builder("variantManager", variantManager.asClassName(module), KModifier.PRIVATE)
                            .initializer("variantManager")
                            .build(),
                    )
                    .addProperty(
                        PropertySpec.builder("context", context.asClassName(module), KModifier.PRIVATE)
                            .initializer("context")
                            .build(),
                    )
                    .addProperty(
                        PropertySpec.builder("moshi", moshi.asClassName(module), KModifier.PRIVATE)
                            .initializer("Moshi.Builder().add(%T()).build()", jsonObjectAdapter.asClassName(module))
                            .build(),
                    )
                    .addProperty(createSharedPreferencesProperty(generatedPackage, featureName, module))
                    .addProperty(createFeatureNameProperty(featureName))
                    .addFunction(createFeatureHashcode(module))
                    .addFunction(createStoreOverride(module))
                    .addFunctions(createToggleStoreImplementation(module))
                    .addFunction(createCompareAndSetHash())
                    .addFunction(createParseJsonFun(module))
                    .addFunction(createParseExceptions(module))
                    .addFunction(createInvokeMethod(boundType))
                    .addType(createJsonRolloutDataClass(generatedPackage, module))
                    .addType(createJsonRolloutStepDataClass(generatedPackage, module))
                    .addType(createJsonToggleTargetDataClass(generatedPackage, module))
                    .addType(createJsonToggleCohortDataClass(generatedPackage, module))
                    .addType(createJsonToggleDataClass(generatedPackage, module))
                    .addType(createJsonFeatureDataClass(generatedPackage, module))
                    .addType(createJsonExceptionDataClass(generatedPackage, module))
                    .addType(createJsonObjectAdapterClass(generatedPackage, module))
                    .build(),
            )
        }

        return createGeneratedFile(codeGenDir, generatedPackage, generatedClassName, content)
    }

    private fun generateOptionalBindings(
        vmClass: ClassReference.Psi,
        codeGenDir: File,
        module: ModuleDescriptor,
    ): GeneratedFile {
        val generatedPackage = vmClass.packageFqName.toString()
        val generatedClassName = "${vmClass.shortName}_OptionalBindings_Module"
        val annotation = vmClass.annotations.first { it.fqName == ContributesRemoteFeature::class.fqName }
        val boundType = annotation.boundTypeOrNull() ?: vmClass
        val scope = vmClass.annotations.first { it.fqName == ContributesRemoteFeature::class.fqName }.scopeOrNull()!!

        val content = FileSpec.buildFile(generatedPackage, generatedClassName) {
            addType(
                TypeSpec.classBuilder(generatedClassName)
                    .addModifiers(KModifier.ABSTRACT)
                    .addAnnotation(AnnotationSpec.builder(dagger.Module::class).build())
                    .addAnnotation(
                        AnnotationSpec
                            .builder(ContributesTo::class).addMember("scope = %T::class", scope.asClassName())
                            .build(),
                    )
                    .addFunction(
                        FunSpec.builder("bindOptionalSettingsStore")
                            .addModifiers(KModifier.ABSTRACT)
                            .addAnnotation(BindsOptionalOf::class.asClassName())
                            .addAnnotation(
                                AnnotationSpec.builder(RemoteFeatureStoreNamed::class.asClassName())
                                    .addMember("value = %T::class", boundType.asClassName())
                                    .build(),
                            )
                            .returns(FeatureSettings.Store::class.java.asClassName())
                            .build(),
                    )
                    .build(),
            )
        }

        return createGeneratedFile(codeGenDir, generatedPackage, generatedClassName, content)
    }

    private fun createFeatureNameProperty(featureName: String): PropertySpec {
        return PropertySpec.builder("featureName", String::class.asClassName())
            .addModifiers(KModifier.OVERRIDE)
            .initializer(CodeBlock.of("%S", featureName))
            .build()
    }

    private fun createSharedPreferencesProperty(
        generatedPackage: String,
        featureName: String,
        module: ModuleDescriptor,
    ): PropertySpec {
        val filename = "com.duckduckgo.feature.toggle.$featureName"
        return PropertySpec.builder("preferences", sharedPreferences.asClassName(module))
            .addModifiers(KModifier.PRIVATE)
            .getter(
                FunSpec.builder("get()")
                    .addCode(CodeBlock.of("return context.getSharedPreferences(%S, Context.MODE_PRIVATE)", filename))
                    .build(),
            )
            .build()
    }

    private fun createFeatureHashcode(module: ModuleDescriptor): FunSpec {
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
                    okioBuffer.asClassName(module),
                ),
            )
            .returns(String::class.asClassName().copy(nullable = true))
            .build()
    }
    private fun createStoreOverride(module: ModuleDescriptor): FunSpec {
        return FunSpec.builder("store")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter(ParameterSpec.builder("featureName", String::class.asClassName()).build())
            .addParameter(ParameterSpec.builder("jsonString", String::class.asClassName()).build())
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
                buildFlavorInternal.asClassName(module),
                buildFlavorInternal.asClassName(module),
            )
            .returns(Boolean::class.asClassName())
            .build()
    }

    private fun createToggleStoreImplementation(module: ModuleDescriptor): List<FunSpec> {
        return listOf(
            FunSpec.builder("set")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("key", String::class.asClassName())
                .addParameter("state", Toggle.State::class.asClassName())
                .addCode(
                    CodeBlock.of(
                        """
                            val jsonAdapter = moshi.adapter(%T::class.java)
                            preferences.edit().putString(key, jsonAdapter.toJson(state)).apply()
                        """.trimIndent(),
                        Toggle.State::class.asClassName(),
                    ),
                )
                .build(),
            FunSpec.builder("get")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("key", String::class.asClassName())
                .addCode(
                    CodeBlock.of(
                        """
                            val jsonAdapter = moshi.adapter(%T::class.java)
                            return kotlin.runCatching { jsonAdapter.fromJson(preferences.getString(key, null)) }.getOrNull()
                        """.trimIndent(),
                        Toggle.State::class.asClassName(),
                    ),
                )
                .returns(Toggle.State::class.asClassName().copy(nullable = true))
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

    private fun createParseJsonFun(module: ModuleDescriptor): FunSpec {
        return FunSpec.builder("parseJson")
            .addModifiers(KModifier.PRIVATE)
            .addParameter("jsonString", String::class.asClassName())
            .addCode(
                CodeBlock.builder()
                    .add(
                        """
                            val jsonAdapter = moshi.adapter(JsonFeature::class.java)
                            return jsonAdapter.fromJson(jsonString)
                        """.trimIndent(),
                    ).build(),
            )
            .returns(FqName("JsonFeature").asClassName(module).copy(nullable = true))
            .build()
    }

    private fun createParseExceptions(module: ModuleDescriptor): FunSpec {
        return FunSpec.builder("parseExceptions")
            .addModifiers(KModifier.PRIVATE)
            .addParameter("exceptions", List::class.asClassName().parameterizedBy(FqName("JsonException").asClassName(module)))
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
                        FeatureException::class.fqName.asClassName(module),
                        FeatureException::class.fqName.asClassName(module),
                    ).build(),
            )
            .returns(List::class.asClassName().parameterizedBy(FeatureException::class.asClassName()))
            .build()
    }

    private fun createInvokeMethod(boundType: ClassReference): FunSpec {
        return FunSpec.builder("invokeMethod")
            .receiver(boundType.asClassName())
            .addParameter("name", String::class)
            .addCode(
                """
                val toggle = kotlin.runCatching {
                    this.javaClass.getDeclaredMethod(name)
                }.getOrNull()?.invoke(this) as Toggle
                return toggle
                """.trimIndent(),
            )
            .returns(Toggle::class)
            .build()
    }

    private fun createJsonRolloutDataClass(
        generatedPackage: String,
        module: ModuleDescriptor,
    ): TypeSpec {
        return TypeSpec.classBuilder(FqName("$generatedPackage.JsonToggleRollout").asClassName(module))
            .addModifiers(KModifier.PRIVATE)
            .addModifiers(KModifier.DATA)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter(
                        "steps",
                        List::class.asClassName().parameterizedBy(FqName("JsonToggleRolloutStep").asClassName(module)),
                    )
                    .build(),
            )
            .addProperty(
                PropertySpec.builder(
                    "steps",
                    List::class.asClassName().parameterizedBy(FqName("JsonToggleRolloutStep").asClassName(module)),
                ).initializer("steps")
                    .build(),
            )
            .build()
    }

    private fun createJsonRolloutStepDataClass(
        generatedPackage: String,
        module: ModuleDescriptor,
    ): TypeSpec {
        return TypeSpec.classBuilder(FqName("$generatedPackage.JsonToggleRolloutStep").asClassName(module))
            .addModifiers(KModifier.PRIVATE)
            .addModifiers(KModifier.DATA)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("percent", Double::class.asClassName())
                    .build(),
            )
            .addProperty(PropertySpec.builder("percent", Double::class.asClassName()).initializer("percent").build())
            .build()
    }

    private fun createJsonToggleTargetDataClass(
        generatedPackage: String,
        module: ModuleDescriptor,
    ): TypeSpec {
        return TypeSpec.classBuilder(FqName("$generatedPackage.JsonToggleTarget").asClassName(module))
            .addModifiers(KModifier.PRIVATE)
            .addModifiers(KModifier.DATA)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("variantKey", String::class.asClassName())
                    .addParameter("localeCountry", String::class.asClassName())
                    .addParameter("localeLanguage", String::class.asClassName())
                    .addParameter("isReturningUser", Boolean::class.asClassName().copy(nullable = true))
                    .addParameter("isPrivacyProEligible", Boolean::class.asClassName().copy(nullable = true))
                    .addParameter("minSdkVersion", Int::class.asClassName().copy(nullable = true))
                    .build(),
            )
            .addProperty(PropertySpec.builder("variantKey", String::class.asClassName()).initializer("variantKey").build())
            .addProperty(PropertySpec.builder("localeCountry", String::class.asClassName()).initializer("localeCountry").build())
            .addProperty(PropertySpec.builder("localeLanguage", String::class.asClassName()).initializer("localeLanguage").build())
            .addProperty(
                PropertySpec.builder("isReturningUser", Boolean::class.asClassName().copy(nullable = true)).initializer("isReturningUser").build(),
            )
            .addProperty(
                PropertySpec
                    .builder("isPrivacyProEligible", Boolean::class.asClassName().copy(nullable = true))
                    .initializer("isPrivacyProEligible")
                    .build(),
            )
            .addProperty(
                PropertySpec
                    .builder("minSdkVersion", Int::class.asClassName().copy(nullable = true))
                    .initializer("minSdkVersion")
                    .build(),
            )
            .build()
    }

    private fun createJsonToggleCohortDataClass(
        generatedPackage: String,
        module: ModuleDescriptor,
    ): TypeSpec {
        return TypeSpec.classBuilder(FqName("$generatedPackage.JsonToggleCohort").asClassName(module))
            .addModifiers(KModifier.PRIVATE)
            .addModifiers(KModifier.DATA)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("name", String::class.asClassName())
                    .addParameter("weight", Int::class.asClassName())
                    .build(),
            )
            .addProperty(PropertySpec.builder("name", String::class.asClassName()).initializer("name").build())
            .addProperty(PropertySpec.builder("weight", Int::class.asClassName()).initializer("weight").build())
            .build()
    }

    private fun createJsonToggleDataClass(
        generatedPackage: String,
        module: ModuleDescriptor,
    ): TypeSpec {
        return TypeSpec.classBuilder(FqName("$generatedPackage.JsonToggle").asClassName(module))
            .addModifiers(KModifier.PRIVATE)
            .addModifiers(KModifier.DATA)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter(
                        "state",
                        String::class.asClassName().copy(nullable = true),
                    )
                    .addParameter(
                        "minSupportedVersion",
                        Double::class.asClassName().copy(nullable = true),
                    )
                    .addParameter(
                        "rollout",
                        FqName("JsonToggleRollout").asClassName(module).copy(nullable = true),
                    )
                    .addParameter(
                        "targets",
                        List::class.asClassName().parameterizedBy(FqName("JsonToggleTarget").asClassName(module)),
                    )
                    .addParameter(
                        "cohorts",
                        List::class.asClassName().parameterizedBy(FqName("JsonToggleCohort").asClassName(module)),
                    )
                    .addParameter("settings", FqName("org.json.JSONObject").asClassName(module).copy(nullable = true))
                    .addParameter(
                        ParameterSpec
                            .builder(
                                "exceptions",
                                List::class.asClassName().parameterizedBy(FqName("JsonException").asClassName(module)),
                            )
                            .build(),
                    )
                    .build(),
            )
            .addProperty(
                PropertySpec
                    .builder("state", String::class.asClassName().copy(nullable = true))
                    .initializer("state")
                    .build(),
            )
            .addProperty(
                PropertySpec
                    .builder("minSupportedVersion", Double::class.asClassName().copy(nullable = true))
                    .initializer("minSupportedVersion")
                    .build(),
            )
            .addProperty(
                PropertySpec
                    .builder("rollout", FqName("JsonToggleRollout").asClassName(module).copy(nullable = true))
                    .initializer("rollout")
                    .build(),
            )
            .addProperty(
                PropertySpec
                    .builder("targets", List::class.asClassName().parameterizedBy(FqName("JsonToggleTarget").asClassName(module)))
                    .initializer("targets")
                    .build(),
            )
            .addProperty(
                PropertySpec
                    .builder("cohorts", List::class.asClassName().parameterizedBy(FqName("JsonToggleCohort").asClassName(module)))
                    .initializer("cohorts")
                    .build(),
            )
            .addProperty(
                PropertySpec
                    .builder("settings", FqName("org.json.JSONObject").asClassName(module).copy(nullable = true))
                    .initializer("settings")
                    .build(),
            )
            .addProperty(
                PropertySpec.builder(
                    "exceptions",
                    List::class.asClassName().parameterizedBy(FqName("JsonException").asClassName(module)),
                ).initializer("exceptions")
                    .build(),
            )
            .build()
    }

    private fun createJsonFeatureDataClass(
        generatedPackage: String,
        module: ModuleDescriptor,
    ): TypeSpec {
        return TypeSpec.classBuilder(FqName("$generatedPackage.JsonFeature").asClassName(module))
            .addModifiers(KModifier.PRIVATE)
            .addModifiers(KModifier.DATA)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("state", String::class.asClassName().copy(nullable = true))
                    .addParameter("hash", String::class.asClassName().copy(nullable = true))
                    .addParameter("minSupportedVersion", Int::class.asClassName().copy(nullable = true))
                    .addParameter("settings", FqName("org.json.JSONObject").asClassName(module).copy(nullable = true))
                    .addParameter(
                        ParameterSpec
                            .builder(
                                "exceptions",
                                List::class.asClassName().parameterizedBy(FqName("JsonException").asClassName(module)),
                            )
                            .build(),
                    )
                    .addParameter(
                        ParameterSpec
                            .builder(
                                "features",
                                Map::class.asClassName().parameterizedBy(
                                    String::class.asClassName(),
                                    FqName("JsonToggle").asClassName(module),
                                ).copy(nullable = true),
                            )
                            .build(),
                    )
                    .build(),
            )
            // properties that match params to generate data class
            .addProperty(PropertySpec.builder("state", String::class.asClassName().copy(nullable = true)).initializer("state").build())
            .addProperty(PropertySpec.builder("hash", String::class.asClassName().copy(nullable = true)).initializer("hash").build())
            .addProperty(
                PropertySpec.builder("minSupportedVersion", Int::class.asClassName().copy(nullable = true))
                    .initializer("minSupportedVersion")
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("settings", FqName("org.json.JSONObject").asClassName(module).copy(nullable = true))
                    .initializer("settings")
                    .build(),
            )
            .addProperty(
                PropertySpec.builder(
                    "exceptions",
                    List::class.asClassName().parameterizedBy(FqName("JsonException").asClassName(module)),
                ).initializer("exceptions")
                    .build(),
            )
            .addProperty(
                PropertySpec
                    .builder(
                        "features",
                        Map::class.asClassName().parameterizedBy(
                            String::class.asClassName(),
                            FqName("JsonToggle").asClassName(module),
                        ).copy(nullable = true),
                    )
                    .initializer("features")
                    .build(),
            )
            .build()
    }

    private fun createJsonExceptionDataClass(
        generatedPackage: String,
        module: ModuleDescriptor,
    ): TypeSpec {
        return TypeSpec.classBuilder(FqName("$generatedPackage.JsonException").asClassName(module))
            .addModifiers(KModifier.PRIVATE)
            .addModifiers(KModifier.DATA)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("domain", String::class.asClassName())
                    .addParameter("reason", String::class.asClassName().copy(nullable = true))
                    .build(),
            )
            .addProperty(PropertySpec.builder("domain", String::class.asClassName()).initializer("domain").build())
            .addProperty(PropertySpec.builder("reason", String::class.asClassName().copy(nullable = true)).initializer("reason").build())
            .build()
    }

    private fun createJsonObjectAdapterClass(
        generatedPackage: String,
        module: ModuleDescriptor,
    ): TypeSpec {
        return TypeSpec.classBuilder("JSONObjectAdapter")
            .addModifiers(KModifier.PRIVATE)
            .addFunction(
                FunSpec.builder("fromJson")
                    .addAnnotation(fromJsonAnnotation.asClassName(module))
                    .addParameter("reader", JsonReader.asClassName(module))
                    .addCode(
                        CodeBlock.of(
                            """
                                return (reader.readJsonValue() as? Map<*, *>)?.let { data ->
                                    try {
                                        %T(data)
                                    } catch (e: %T) {
                                        return null
                                    }
                                }
                            """.trimIndent(),
                            JSONObject.asClassName(module),
                            JSONException.asClassName(module),
                        ),
                    )
                    .returns(JSONObject.asClassName(module).copy(nullable = true))
                    .build(),
            )
            .addFunction(
                FunSpec.builder("toJson")
                    .addAnnotation(toJsonAnnotation.asClassName(module))
                    .addParameter("writer", JsonWriter.asClassName(module))
                    .addParameter("value", JSONObject.asClassName(module).copy(nullable = true))
                    .addCode(
                        CodeBlock.of(
                            """
                                value?.let { writer.run { value(%T().writeUtf8(value.toString())) } }
                            """.trimIndent(),
                            okioBuffer.asClassName(module),
                        ),
                    )
                    .build(),
            )
            .build()
    }

    private fun validateRemoteFeatureInterface(
        vmClass: ClassReference.Psi,
        module: ModuleDescriptor,
    ): CustomStorePresence {
        fun requireFeatureAndStoreCrossReference(vmClass: Psi, storeClass: ClassReference) {
            // check if the store is annotated with RemoteFeatureStoreNamed
            if (storeClass.annotations.none { it.fqName == RemoteFeatureStoreNamed::class.fqName }) {
                throw AnvilCompilationException(
                    "${storeClass.fqName} shall be annotated with [RemoteFeatureStoreNamed]",
                    element = vmClass.clazz.identifyingElement,
                )
            } else {
                // lastly, check that both the feature and store reference each other
                val storedDefineFeature = storeClass.annotations
                    .first { it.fqName == RemoteFeatureStoreNamed::class.fqName }
                    .remoteFeatureStoreValueOrNull()

                // check the boundType to ensure triggers work as expected
                val annotation = vmClass.annotations.first { it.fqName == ContributesRemoteFeature::class.fqName }
                val featureClass = annotation.boundTypeOrNull() ?: vmClass
                if (storedDefineFeature?.fqName != featureClass.fqName) {
                    throw AnvilCompilationException(
                        "${vmClass.fqName} and ${featureClass.fqName} don't reference each other",
                        element = vmClass.clazz.identifyingElement,
                    )
                }
            }
        }

        var settingsStore = false
        var toggleStore = false

        // validate type must be an interface
        if (!vmClass.isInterface()) {
            throw AnvilCompilationException(
                "${vmClass.fqName} must be an interface",
                element = vmClass.clazz.identifyingElement,
            )
        }

        // validate only ContributesRemoteFeature and/or Suppress is added
        val invalidAnnotations = vmClass.annotations.filter {
            it.fqName != ContributesRemoteFeature::class.fqName
        }.filter {
            it.fqName != Suppress::class.fqName
        }
        if (invalidAnnotations.isNotEmpty()) {
            throw AnvilCompilationException(
                "${vmClass.fqName} can only be annotated with @ContributesRemoteFeature or @Suppress",
                element = vmClass.clazz.identifyingElement,
            )
        }

        // Validate ContributesRemoteFeature annotation
        val annotation = vmClass.annotations.first { it.fqName == ContributesRemoteFeature::class.fqName }
        if (annotation.scopeOrNull(0) == null) {
            throw AnvilCompilationException(
                "${vmClass.fqName} annotated with @ContributesRemoteFeature must define [scope]",
                element = vmClass.clazz.identifyingElement,
            )
        }
        if (annotation.featureNameOrNull() == null) {
            throw AnvilCompilationException(
                "${vmClass.fqName} annotated with @ContributesRemoteFeature must define [featureName]",
                element = vmClass.clazz.identifyingElement,
            )
        }
        with(annotation.settingsStoreOrNull()) {
            settingsStore = this != null
            if (this != null) {
                // check that the Store is actually a [FeatureSettings.Store]
                if (this.directSuperTypeReferences()
                        .none { it.asClassReferenceOrNull()?.fqName == FeatureSettings.Store::class.fqName }
                ) {
                    throw AnvilCompilationException(
                        "${vmClass.fqName} [settingsStore] must extend [FeatureSettings.Store]",
                        element = vmClass.clazz.identifyingElement,
                    )
                }

                requireFeatureAndStoreCrossReference(vmClass, this)
            }
        }
        with(annotation.toggleStoreOrNull()) {
            toggleStore = this != null
            if (this != null) {
                if (this.directSuperTypeReferences().none { it.asClassReferenceOrNull()?.fqName == Toggle.Store::class.fqName }) {
                    throw AnvilCompilationException(
                        "${vmClass.fqName} [toggleStore] must extend [Toggle.Store]",
                        element = vmClass.clazz.identifyingElement,
                    )
                }
                requireFeatureAndStoreCrossReference(vmClass, this)
            }
        }

        // shadow vmClass in case we have a bound type
        val boundType = annotation.boundTypeOrNull() ?: vmClass

        // validate all functions must return [Toggle]
        if (boundType.declaredFunctions().any { it.returnType().asClassReferenceOrNull()?.fqName != Toggle::class.fqName }) {
            throw AnvilCompilationException(
                "${boundType.fqName} can only contain functions that return [Toggle]",
                element = vmClass.clazz.identifyingElement,
            )
        }
        // validate functions can't have parameters
        if (boundType.declaredFunctions().any { it.parameters.isNotEmpty() }) {
            throw AnvilCompilationException(
                "${boundType.fqName} can only contain functions without parameters",
                element = vmClass.clazz.identifyingElement,
            )
        }

        // validate functions must be annotated with DefaultValue
        if (boundType.declaredFunctions().any { !it.isAnnotatedWith(Toggle.DefaultValue::class.fqName) }) {
            throw AnvilCompilationException(
                "All functions in ${boundType.fqName} must be annotated with [Toggle.DefaultValue]",
                element = vmClass.clazz.identifyingElement,
            )
        }

        // validate single DefaultValue annotation is present
        boundType.declaredFunctions().forEach { f ->
            if (f.annotations.count { it.fqName == Toggle.DefaultValue::class.fqName } > 1) {
                throw AnvilCompilationException(
                    "All functions in ${boundType.fqName} must be annotated with single [Toggle.DefaultValue]",
                    element = vmClass.clazz.identifyingElement,
                )
            }
        }

        // validate function self() must be present
        if (boundType.declaredFunctions().none { it.name == "self" }) {
            throw AnvilCompilationException(
                "${boundType.fqName} must have a function self()",
                element = vmClass.clazz.identifyingElement,
            )
        }

        return CustomStorePresence(
            toggleStorePresent = toggleStore,
            settingsStorePresent = settingsStore,
        )
    }

    private enum class ContributesRemoteFeatureValues {
        SCOPE,
        BOUND_TYPE,
        FEATURE_NAME,
        SETTINGS_STORE,
        TOGGLE_STORE,
    }

    private fun AnnotationReference.remoteFeatureStoreValueOrNull(): ClassReference? {
        return argumentAt("value", ContributesRemoteFeatureValues.SCOPE.ordinal)?.value()
    }

    private fun AnnotationReference.featureNameOrNull(): String? {
        return argumentAt("featureName", ContributesRemoteFeatureValues.FEATURE_NAME.ordinal)?.value()
    }

    private fun AnnotationReference.settingsStoreOrNull(): ClassReference? {
        return argumentAt("settingsStore", ContributesRemoteFeatureValues.SETTINGS_STORE.ordinal)?.value()
    }

    private fun AnnotationReference.toggleStoreOrNull(): ClassReference? {
        return argumentAt("toggleStore", ContributesRemoteFeatureValues.TOGGLE_STORE.ordinal)?.value()
    }

    private fun ClassReference.declaredFunctions(): List<MemberFunctionReference> {
        return functions
            .filter { it.name != "equals" }
            .filter { it.name != "hashCode" }
            .filter { it.name != "toString" }
    }

    companion object {
        private val sharedPreferences = FqName("android.content.SharedPreferences")
        private val context = FqName("android.content.Context")
        private val privacyFeaturePlugin = FqName("com.duckduckgo.privacy.config.api.PrivacyFeaturePlugin")
        private val appBuildConfig = FqName("com.duckduckgo.appbuildconfig.api.AppBuildConfig")
        private val featureTogglesCallback = FqName("com.duckduckgo.feature.toggles.internal.api.FeatureTogglesCallback")
        private val variantManager = FqName("com.duckduckgo.experiments.api.VariantManager")
        private val buildFlavorInternal = FqName("com.duckduckgo.appbuildconfig.api.BuildFlavor.INTERNAL")
        private val moshi = FqName("com.squareup.moshi.Moshi")
        private val jsonObjectAdapter = FqName("JSONObjectAdapter")
        private val singleInstanceAnnotationFqName = FqName("dagger.SingleInstanceIn")
        private val fromJsonAnnotation = FqName("com.squareup.moshi.FromJson")
        private val toJsonAnnotation = FqName("com.squareup.moshi.ToJson")
        private val JSONObject = FqName("org.json.JSONObject")
        private val JSONException = FqName("org.json.JSONException")
        private val JsonWriter = FqName("com.squareup.moshi.JsonWriter")
        private val JsonReader = FqName("com.squareup.moshi.JsonReader")
        private val okioBuffer = FqName("okio.Buffer")
    }
}

private data class CustomStorePresence(
    val settingsStorePresent: Boolean = false,
    val toggleStorePresent: Boolean = false,
)
