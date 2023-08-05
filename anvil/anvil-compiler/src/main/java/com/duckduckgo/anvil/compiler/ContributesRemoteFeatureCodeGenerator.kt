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
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import dagger.BindsOptionalOf
import dagger.Provides
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
                            .addParameter("appBuildConfig", appBuildConfig.asClassName(module))
                            .addCode(
                                """
                            return %T.Builder()
                                .store(toggleStore)
                                .appVersionProvider({ appBuildConfig.versionCode })
                                .featureName(%S)
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
                        if (!customStorePresence.exceptionStorePresent) {
                            addFunction(
                                FunSpec.builder("providesNoopExceptionsStore")
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
                                            FeatureExceptions::class.asClassName(),
                                        ),
                                    )
                                    .returns(FeatureExceptions.Store::class.asClassName())
                                    .build(),
                            )
                        }
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
                                        "exceptionStore",
                                        FeatureExceptions.Store::class,
                                    )
                                    .addAnnotation(
                                        AnnotationSpec
                                            .builder(RemoteFeatureStoreNamed::class).addMember("value = %T::class", boundType.asClassName())
                                            .build(),

                                    )
                                    .build(),
                            )
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
                            .addParameter("context", context.asClassName(module))
                            .build(),
                    )
                    .addProperty(
                        PropertySpec
                            .builder(
                                "exceptionStore",
                                FeatureExceptions.Store::class,
                                KModifier.PRIVATE,
                            )
                            .initializer("exceptionStore")
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
                    .addFunction(createStoreOverride(module))
                    .addFunctions(createToggleStoreImplementation(module))
                    .addFunction(createCompareAndSetHash())
                    .addFunction(createParseJsonFun(module))
                    .addFunction(createParseExceptions(module))
                    .addFunction(createInvokeMethod(boundType))
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
                    .addFunction(
                        FunSpec.builder("bindOptionalExceptionsStore")
                            .addModifiers(KModifier.ABSTRACT)
                            .addAnnotation(BindsOptionalOf::class.asClassName())
                            .addAnnotation(
                                AnnotationSpec.builder(RemoteFeatureStoreNamed::class.asClassName())
                                    .addMember("value = %T::class", boundType.asClassName())
                                    .build(),
                            )
                            .returns(FeatureExceptions.Store::class.java.asClassName())
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

    private fun createStoreOverride(module: ModuleDescriptor): FunSpec {
        return FunSpec.builder("store")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter(ParameterSpec.builder("featureName", String::class.asClassName()).build())
            .addParameter(ParameterSpec.builder("jsonString", String::class.asClassName()).build())
            .addCode(
                """
                if (featureName == this.featureName) {
                    val feature = parseJson(jsonString) ?: return false
                    
                    if (compareAndSetHash(feature.hash)) return true
        
                    val exceptions = parseExceptions(feature)
                    exceptionStore.insertAll(exceptions)
        
                    val isEnabled = (feature.state == "enabled") || (appBuildConfig.flavor == %T && feature.state == "internal")
                    this.feature.get().invokeMethod("self").setEnabled(Toggle.State(isEnabled, feature.minSupportedVersion))
        
                    // Handle sub-features
                    feature.features?.forEach { subfeature ->
                        subfeature.value.let { jsonObject ->
                            val jsonToggle = JsonToggle(jsonObject)
                            this.feature.get().invokeMethod(subfeature.key).setEnabled(
                                Toggle.State(
                                    enable = jsonToggle.state == "enabled" || (appBuildConfig.flavor == %T && jsonToggle.state == "internal"),
                                    minSupportedVersion = jsonToggle.minSupportedVersion?.toInt(),
                                ),
                            )
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
            .addParameter("jsonFeature", FqName("JsonFeature").asClassName(module).copy(nullable = true))
            .addCode(
                CodeBlock.builder()
                    .add(
                        """
                            val featureExceptions = mutableListOf<%T>()
                            jsonFeature?.exceptions?.map { ex ->
                                featureExceptions.add(%T(ex.domain, ex.reason))
                            }
                            return featureExceptions.toList()
                        """.trimIndent(),
                        FeatureExceptions.FeatureException::class.fqName.asClassName(module),
                        FeatureExceptions.FeatureException::class.fqName.asClassName(module),
                    ).build(),
            )
            .returns(List::class.asClassName().parameterizedBy(FeatureExceptions.FeatureException::class.asClassName()))
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
                        "map",
                        Map::class.asClassName().parameterizedBy(String::class.asClassName(), Any::class.asClassName().copy(nullable = true)),
                    )
                    .build(),
            )
            // Property map that matches params to generate data class
            .addProperty(
                PropertySpec
                    .builder(
                        "map",
                        Map::class.asClassName().parameterizedBy(String::class.asClassName(), Any::class.asClassName().copy(nullable = true)),
                    )
                    .initializer("map")
                    .build(),
            )
            .addProperty(
                PropertySpec
                    .builder(
                        "attributes",
                        Map::class.asClassName().parameterizedBy(String::class.asClassName(), Any::class.asClassName().copy(nullable = true)),
                    )
                    .addModifiers(KModifier.PRIVATE)
                    .initializer(CodeBlock.of("map.withDefault { null }"))
                    .build(),
            )
            .addProperty(
                PropertySpec
                    .builder("state", String::class.asClassName().copy(nullable = true))
                    .delegate("attributes")
                    .build(),
            )
            .addProperty(
                PropertySpec
                    .builder("minSupportedVersion", Double::class.asClassName().copy(nullable = true))
                    .delegate("attributes")
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
                                    Map::class.asClassName().parameterizedBy(
                                        String::class.asClassName(),
                                        Any::class.asClassName().copy(nullable = true),
                                    ),
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
                            Map::class.asClassName().parameterizedBy(String::class.asClassName(), Any::class.asClassName().copy(nullable = true)),
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
                    .addParameter("reason", String::class.asClassName())
                    .build(),
            )
            .addProperty(PropertySpec.builder("domain", String::class.asClassName()).initializer("domain").build())
            .addProperty(PropertySpec.builder("reason", String::class.asClassName()).initializer("reason").build())
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
        var exceptionStore = false
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
            if (this != null && this.directSuperTypeReferences()
                .none { it.asClassReferenceOrNull()?.fqName == FeatureSettings.Store::class.fqName }
            ) {
                throw AnvilCompilationException(
                    "${vmClass.fqName} [settingsStore] must extend [FeatureSettings.Store]",
                    element = vmClass.clazz.identifyingElement,
                )
            }
        }
        with(annotation.exceptionsStoreOrNull()) {
            exceptionStore = this != null
            if (this != null && this.directSuperTypeReferences()
                .none { it.asClassReferenceOrNull()?.fqName == FeatureExceptions.Store::class.fqName }
            ) {
                throw AnvilCompilationException(
                    "${vmClass.fqName} [exceptionsStore] must extend [FeatureExceptions.Store]",
                    element = vmClass.clazz.identifyingElement,
                )
            }
        }
        with(annotation.toggleStoreOrNull()) {
            toggleStore = this != null
            if (this != null && this.directSuperTypeReferences().none { it.asClassReferenceOrNull()?.fqName == Toggle.Store::class.fqName }) {
                throw AnvilCompilationException(
                    "${vmClass.fqName} [toggleStore] must extend [Toggle.Store]",
                    element = vmClass.clazz.identifyingElement,
                )
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

        // validate function self() must be present
        if (boundType.declaredFunctions().none { it.name == "self" }) {
            throw AnvilCompilationException(
                "${boundType.fqName} must have a function self()",
                element = vmClass.clazz.identifyingElement,
            )
        }

        return CustomStorePresence(
            exceptionStorePresent = exceptionStore,
            toggleStorePresent = toggleStore,
            settingsStorePresent = settingsStore,
        )
    }

    private fun AnnotationReference.featureNameOrNull(): String? {
        return argumentAt("featureName", 2)?.value()
    }

    private fun AnnotationReference.settingsStoreOrNull(): ClassReference? {
        return argumentAt("settingsStore", 3)?.value()
    }

    private fun AnnotationReference.exceptionsStoreOrNull(): ClassReference? {
        return argumentAt("exceptionsStore", 4)?.value()
    }

    private fun AnnotationReference.toggleStoreOrNull(): ClassReference? {
        return argumentAt("toggleStore", 5)?.value()
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
    val exceptionStorePresent: Boolean = false,
    val toggleStorePresent: Boolean = false,
)
