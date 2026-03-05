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

import com.duckduckgo.anvil.annotations.ContributesActivePlugin
import com.duckduckgo.anvil.annotations.ContributesActivePluginPoint
import com.duckduckgo.anvil.annotations.ContributesPluginPoint
import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.feature.toggles.api.RemoteFeatureStoreNamed
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue
import com.duckduckgo.feature.toggles.api.Toggle.Experiment
import com.duckduckgo.feature.toggles.api.Toggle.InternalAlwaysEnabled
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
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.ABSTRACT
import com.squareup.kotlinpoet.KModifier.INTERNAL
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PRIVATE
import com.squareup.kotlinpoet.KModifier.SUSPEND
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import dagger.Binds
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * This Anvil code generator generates Active Plugins, ie. those that can be controlled via remote feature flag.
 * Active plugins and Active plugin points are generated using the [ContributesActivePluginPoint] and [ContributesActivePlugin] annotations.
 *
 * For classes annotated with [ContributesActivePluginPoint], this generator will
 * - generate a regular plugin point
 * - generate a wrapper around the normal plugin point to handle the associated remote feature flag
 * - generate a remote feature flag that will control the plugin point
 * - generate the bindings so that users can depend on ActivePluginPoint<T>
 *
 * For classes annotated with [ContributesActivePlugin] this generator will:
 * - generate a binding to contribute the plugin into the associated plugin point, using [ContributesMultibinding] and [PriorityKey]
 * - generate a remote feature flag that will control the plugin
 *
 * The business logic generated will ensure that:
 * - disabling a given remote feature flag associated to plugins will de-activate such plugin, ie. won't be return in getPlugins()
 * - disabling the remote feature associated to the plugin point will make getPlugins() method to return empty list, regardless of whether the
 * plugin is active or not
 *
 */
@OptIn(ExperimentalAnvilApi::class)
@AutoService(CodeGenerator::class)
class ContributesActivePluginPointCodeGenerator : CodeGenerator {

    private val activePluginPointAnnotations = listOf(
        ContributesActivePlugin::class,
        ContributesActivePluginPoint::class,
    )

    override fun isApplicable(context: AnvilContext): Boolean = true

    override fun generateCode(codeGenDir: File, module: ModuleDescriptor, projectFiles: Collection<KtFile>): Collection<GeneratedFileWithSources> {
        val annotated = projectFiles.classAndInnerClassReferences(module)
            .toList()
            .filter { reference -> reference.isAnnotatedWith(activePluginPointAnnotations.map { it.fqName }) }

        // First pass: collect plugin point featureNames declared explicitly in this module's source.
        // Used to validate @ContributesActivePlugin.parentFeatureName in the second pass.
        val localPluginPointFeatureNames: Set<String> = annotated
            .filter { it.isContributesActivePluginPoint() }
            .mapNotNull { ref ->
                ref.annotations
                    .firstOrNull { it.fqName == ContributesActivePluginPoint::class.fqName }
                    ?.pluginPointFeatureNameOrNull()
            }
            .toSet()

        // Phase 2: validate any deferred parentFeatureName markers emitted by sibling modules.
        // This is a no-op in leaf/impl modules (deferred package is empty in their deps).
        // In the composition root (:app), all sibling-module deferred markers are visible
        // alongside all sentinels, so mis-matched parentFeatureNames fail here.
        // localPluginPointFeatureNames is passed so that plugin points declared in the SAME
        // module (e.g. :app declaring both a plugin point and plugins that reference it via
        // deferred markers) are also accepted without requiring them to be in the classpath.
        validateDeferredMarkers(module, localPluginPointFeatureNames)

        // Second pass: generate code for all annotated classes.
        // deferredThisRun collects (parentFeatureName, vmClass) pairs for any deferred markers
        // emitted during this run, so we can validate them in the post-generation pass below.
        val deferredThisRun = mutableListOf<Pair<String, ClassReference.Psi>>()
        val generated = annotated
            .flatMap { generateActivePluginsPointAndPlugins(it, codeGenDir, module, localPluginPointFeatureNames, deferredThisRun) }
            .toMutableList().apply {
                // this.addAll(generatePluginPointRemoteFeature(codeGenDir, module))
            }.toList()

        // Phase 3: validate deferred markers emitted in this run against the sentinel registry.
        // If the registry has content, we have visibility of at least some plugin points. In
        // practice this means we are :app (which depends on all impl modules). Any parentFeatureName
        // whose sentinel is still missing after code generation is a typo, not a sibling reference.
        validateDeferredEmittedThisRun(module, localPluginPointFeatureNames, deferredThisRun)

        return generated
    }

    /**
     * Phase 2 of the two-phase deferred validation.
     *
     * Scans [DEFERRED_SENTINEL_PACKAGE] for any [DEFERRED_MARKER_PREFIX]* objects emitted by
     * sibling modules (modules that could not see the plugin-point sentinel at their own
     * compile time because they are not transitive dependencies of that module).
     *
     * For each deferred marker, checks whether the expected
     * `ActivePluginPointRegistry_<parentFeatureName>` sentinel exists in [SENTINEL_PACKAGE].
     * If not, the parentFeatureName is invalid and we fail with a compile error.
     *
     * This method is a no-op in modules whose dependency graph does not include any
     * sentinel-producing impl modules (i.e. the deferred package is empty).
     */
    private fun validateDeferredMarkers(module: ModuleDescriptor, localPluginPointFeatureNames: Set<String>) {
        // Only validate at modules that also declare plugin points. Intermediate modules that
        // happen to have sentinels in their classpath (from dependencies) but do NOT declare
        // plugin points themselves should NOT validate deferred markers — their view of the
        // sentinel universe is incomplete. The composition root (:app) always declares at
        // least one plugin point (pluginPointNewTabPagePlugin), so it always validates.
        if (localPluginPointFeatureNames.isEmpty()) return

        val deferredScope = module.getPackage(FqName(DEFERRED_SENTINEL_PACKAGE)).memberScope
        val deferredDescriptors = deferredScope.getContributedDescriptors(DescriptorKindFilter.CLASSIFIERS)
        if (deferredDescriptors.isEmpty()) return

        val sentinelScope = module.getPackage(FqName(SENTINEL_PACKAGE)).memberScope
        val knownSentinels = sentinelScope.getContributedDescriptors(DescriptorKindFilter.CLASSIFIERS)

        val availableFeatureNames = (
            knownSentinels
                .map { it.name.asString() }
                .filter { it.startsWith("ActivePluginPointRegistry_") }
                .map { it.removePrefix("ActivePluginPointRegistry_") } +
                localPluginPointFeatureNames
            ).sorted()

        for (descriptor in deferredDescriptors) {
            val name = descriptor.name.asString()
            if (!name.startsWith(DEFERRED_MARKER_PREFIX)) continue
            // Marker names include the emitting class as a suffix separated by "__" to ensure
            // uniqueness across modules. e.g. ActivePluginDeferredValidation_<parent>__<Class>
            val parentFeatureName = name.removePrefix(DEFERRED_MARKER_PREFIX).substringBefore("__")
            val foundInSentinel = sentinelScope.getContributedClassifier(
                Name.identifier("ActivePluginPointRegistry_$parentFeatureName"),
                NoLookupLocation.FROM_BACKEND,
            ) != null
            val foundLocally = parentFeatureName in localPluginPointFeatureNames
            if (!foundInSentinel && !foundLocally) {
                throw AnvilCompilationException(
                    "parentFeatureName \"$parentFeatureName\" does not match any @ContributesActivePluginPoint.\n" +
                        "Known plugin point featureNames: ${availableFeatureNames.joinToString(", ")}",
                )
            }
        }
    }

    /**
     * Phase 3: validates deferred markers emitted during the current [generateCode] run.
     *
     * Deferred markers written to disk in the second pass are not yet in the [ModuleDescriptor]
     * classpath, so [validateDeferredMarkers] (which runs before code generation) cannot catch
     * them. This method fills that gap: if the sentinel registry has any content, we have
     * visibility of at least some plugin points — in practice, this means we are :app. A missing
     * sentinel at this point is a typo, not a legitimate sibling-module reference.
     *
     * If the registry is empty, we have no view of plugin points and cannot validate — the
     * deferred marker will be picked up and validated by :app's [validateDeferredMarkers].
     */
    private fun validateDeferredEmittedThisRun(
        module: ModuleDescriptor,
        localPluginPointFeatureNames: Set<String>,
        deferredThisRun: List<Pair<String, ClassReference.Psi>>,
    ) {
        if (deferredThisRun.isEmpty()) return
        val registryScope = module.getPackage(FqName(SENTINEL_PACKAGE)).memberScope
        val registryHasContent = registryScope.getContributedDescriptors(DescriptorKindFilter.CLASSIFIERS).isNotEmpty()
        if (!registryHasContent) return

        val available by lazy {
            (
                registryScope.getContributedDescriptors(DescriptorKindFilter.CLASSIFIERS)
                    .map { it.name.asString() }
                    .filter { it.startsWith("ActivePluginPointRegistry_") }
                    .map { it.removePrefix("ActivePluginPointRegistry_") } +
                    localPluginPointFeatureNames
                ).sorted()
        }

        for ((parentFeatureName, vmClass) in deferredThisRun) {
            if (parentFeatureName in localPluginPointFeatureNames) continue
            val sentinelFound = registryScope.getContributedClassifier(
                Name.identifier("ActivePluginPointRegistry_$parentFeatureName"),
                NoLookupLocation.FROM_BACKEND,
            ) != null
            if (!sentinelFound) {
                throw AnvilCompilationException(
                    "${vmClass.fqName}: parentFeatureName \"$parentFeatureName\" does not match " +
                        "any @ContributesActivePluginPoint.\n" +
                        "Known plugin point featureNames: ${available.joinToString(", ")}",
                    element = vmClass.clazz.identifyingElement,
                )
            }
        }
    }

    private fun generateActivePluginsPointAndPlugins(
        vmClass: ClassReference.Psi,
        codeGenDir: File,
        module: ModuleDescriptor,
        localPluginPointFeatureNames: Set<String>,
        deferredThisRun: MutableList<Pair<String, ClassReference.Psi>>,
    ): List<GeneratedFileWithSources> {
        return if (vmClass.isContributesActivePluginPoint()) {
            generatedActivePluginPoint(vmClass, codeGenDir, module)
        } else {
            generatedActivePlugin(vmClass, codeGenDir, module, localPluginPointFeatureNames, deferredThisRun)
        }
    }

    private fun generatedActivePluginPoint(vmClass: ClassReference.Psi, codeGenDir: File, module: ModuleDescriptor): List<GeneratedFileWithSources> {
        val generatedPackage = vmClass.packageFqName.toString()
        val pluginPointClassFileName = "${vmClass.shortName}_ActivePluginPoint"
        val pluginPointClassName = "Trigger_${vmClass.shortName}_ActivePluginPoint"
        val pluginPointWrapperClassName = "${vmClass.shortName}_PluginPoint_ActiveWrapper"
        val pluginPointWrapperBindingModuleClassName = "${vmClass.shortName}_PluginPoint_ActiveWrapper_Binding_Module"
        val pluginPointRemoteFeatureClassName = "${vmClass.shortName}_ActivePluginPoint_RemoteFeature"
        val pluginPointRemoteFeatureStoreClassName = "${vmClass.shortName}_ActivePluginPoint_RemoteFeature_MultiProcessStore"
        val scope = vmClass.annotations.firstOrNull { it.fqName == ContributesActivePluginPoint::class.fqName }?.scopeOrNull(0)!!
        val pluginClassType = vmClass.pluginClassName(ContributesActivePluginPoint::class.fqName) ?: vmClass.asClassName()
        val featureName = vmClass.annotations
            .firstOrNull { it.fqName == ContributesActivePluginPoint::class.fqName }
            ?.pluginPointFeatureNameOrNull()
            ?: throw AnvilCompilationException(
                "${vmClass.fqName}: @ContributesActivePluginPoint requires a non-blank featureName.",
                element = vmClass.clazz.identifyingElement,
            )
        if (!featureName.startsWith("pluginPoint")) {
            throw AnvilCompilationException(
                "${vmClass.fqName}: @ContributesActivePluginPoint featureName must start with \"pluginPoint\" (got \"$featureName\").",
                element = vmClass.clazz.identifyingElement,
            )
        }

        // Check if there's another plugin point class that has the same class simplename
        // we can't allow that because the backing remote feature would be the same
        val existingFeature = featureBackedClassNames.putIfAbsent(featureName, vmClass.fqName)
        if (existingFeature != null) {
            throw AnvilCompilationException(
                "${vmClass.fqName} plugin point naming is duplicated, previous found in $existingFeature",
                element = vmClass.clazz.identifyingElement,
            )
        }
        if (scope.fqName != appScopeFqName) {
            throw AnvilCompilationException(
                "${vmClass.fqName}: Active plugins can only be used in 'AppScope'.",
                element = vmClass.clazz.identifyingElement,
            )
        }

        val content = FileSpec.buildFile(generatedPackage, pluginPointClassFileName) {
            addImport(DefaultFeatureValue::class.fqName.parent().asString(), DefaultFeatureValue::class.fqName.shortName().asString())
            addImport(DefaultFeatureValue::class.fqName.asString(), DefaultFeatureValue.TRUE.toString())
            addImport(DefaultFeatureValue::class.fqName.asString(), DefaultFeatureValue.FALSE.toString())
            addImport(DefaultFeatureValue::class.fqName.asString(), DefaultFeatureValue.INTERNAL.toString())
            // This is the normal plugin point
            addType(
                TypeSpec.interfaceBuilder(pluginPointClassName)
                    .addModifiers(PRIVATE)
                    .addAnnotation(
                        AnnotationSpec.builder(ContributesPluginPoint::class)
                            .addMember("scope = %T::class", scope.asClassName())
                            .addMember("boundType = %T::class", pluginClassType)
                            .build(),
                    )
                    .addAnnotation(
                        AnnotationSpec.builder(Suppress::class)
                            .addMember("%S", "unused")
                            .build(),
                    )
                    .build(),
            ).build()

            // Generate the feature flag that guards the plugin points
            addType(
                TypeSpec.interfaceBuilder(pluginPointRemoteFeatureClassName)
                    .addAnnotation(
                        AnnotationSpec.builder(ContributesRemoteFeature::class)
                            .addMember("scope = %T::class", scope.asClassName())
                            .addMember("featureName = %S", featureName)
                            .addMember("toggleStore = %T::class", ClassName(packageName, pluginPointRemoteFeatureStoreClassName))
                            .build(),
                    )
                    .addFunction(
                        FunSpec.builder("self")
                            .addModifiers(ABSTRACT)
                            .addAnnotation(
                                AnnotationSpec.builder(Toggle.DefaultValue::class)
                                    .addMember("defaultValue = %L", "DefaultFeatureValue.TRUE")
                                    .build(),
                            )
                            .returns(Toggle::class)
                            .build(),
                    )
                    .build(),
            ).build()

            // This is the plugin point active wrapper. Depends on the normal plugin point above and wraps to allow "active" behavior, that is
            // just return the plugins that have the remote feature enabled.
            addType(
                TypeSpec.classBuilder(pluginPointWrapperClassName).apply {
                    primaryConstructor(
                        FunSpec.constructorBuilder()
                            .addAnnotation(AnnotationSpec.builder(Inject::class).build())
                            .addParameter("toggle", ClassName(generatedPackage, pluginPointRemoteFeatureClassName))
                            .addParameter(
                                ParameterSpec.builder(
                                    "pluginPoint",
                                    pluginPointFqName.asClassName(module).parameterizedBy(
                                        pluginClassType.copy(
                                            annotations = listOf(AnnotationSpec.builder(JvmSuppressWildcards::class).build()),
                                        ),
                                    ),
                                ).build(),
                            )
                            .addParameter(ParameterSpec.builder("dispatcherProvider", dispatcherProviderFqName.asClassName(module)).build())
                            .build(),
                    )
                    addProperty(
                        PropertySpec.builder("toggle", ClassName(generatedPackage, pluginPointRemoteFeatureClassName), PRIVATE)
                            .initializer("toggle")
                            .build(),
                    )
                    addProperty(
                        PropertySpec.builder(
                            "pluginPoint",
                            pluginPointFqName.asClassName(module).parameterizedBy(
                                pluginClassType.copy(
                                    annotations = listOf(AnnotationSpec.builder(JvmSuppressWildcards::class).build()),
                                ),
                            ),
                            PRIVATE,
                        ).initializer("pluginPoint").build(),
                    )
                    addProperty(
                        PropertySpec.builder(
                            "dispatcherProvider",
                            dispatcherProviderFqName.asClassName(module),
                            PRIVATE,
                        )
                            .initializer("dispatcherProvider")
                            .build(),
                    )

                    addSuperinterface(
                        activePluginPointFqName
                            .asClassName(module)
                            .parameterizedBy(
                                pluginClassType.copy(annotations = listOf(AnnotationSpec.builder(JvmSuppressWildcards::class).build())),
                            ),
                    )

                    addFunction(
                        FunSpec.builder("getPlugins")
                            .addModifiers(OVERRIDE, SUSPEND)
                            .returns(Collection::class.fqName.asClassName(module).parameterizedBy(pluginClassType))
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
                }.build(),
            )

            // create the multiprocess remote feature store
            createRemoteFeatureFlagMultiprocessStore(
                scope = scope,
                module = module,
                pluginRemoteFeatureClassName = pluginPointRemoteFeatureClassName,
                pluginRemoteFeatureStoreClassName = pluginPointRemoteFeatureStoreClassName,
                parentFeatureName = featureName,
            )

            // Finally we're gonna create the dagger binding module, which will bind the plugin point wrapper type to the ActivePluginPoint<T> type
            addType(
                TypeSpec.classBuilder(pluginPointWrapperBindingModuleClassName)
                    .addAnnotation(AnnotationSpec.builder(dagger.Module::class).build())
                    .addAnnotation(
                        AnnotationSpec
                            .builder(ContributesTo::class).addMember("scope = %T::class", scope.asClassName())
                            .build(),
                    )
                    .addModifiers(ABSTRACT)
                    .addFunction(
                        FunSpec.builder("binds$pluginPointWrapperClassName")
                            .addModifiers(ABSTRACT)
                            .addAnnotation(Binds::class.asClassName())
                            .addParameter(
                                ParameterSpec.builder(
                                    "pluginPoint",
                                    ClassName(generatedPackage, pluginPointWrapperClassName),
                                    // FqName(pluginPointWrapperClassName).asClassName(module)
                                ).build(),
                            )
                            .returns(
                                activePluginPointFqName
                                    .asClassName(module)
                                    .parameterizedBy(
                                        pluginClassType.copy(annotations = listOf(AnnotationSpec.builder(JvmSuppressWildcards::class).build())),
                                    ),
                            )
                            .build(),
                    )
                    .build(),
            )
        }

        val mainFile = createGeneratedFile(codeGenDir, generatedPackage, pluginPointClassFileName, content, setOf(vmClass.containingFileAsJavaFile))

        // Generate a sentinel object in a fixed package so that plugins in other modules can
        // verify this plugin point exists via ModuleDescriptor at their own codegen time
        // (cross-module parentFeatureName validation).
        val sentinelClassName = "ActivePluginPointRegistry_$featureName"
        val sentinelContent = FileSpec.buildFile(SENTINEL_PACKAGE, sentinelClassName) {
            addType(
                TypeSpec.objectBuilder(sentinelClassName)
                    .addModifiers(INTERNAL)
                    .addAnnotation(
                        AnnotationSpec.builder(Suppress::class)
                            .addMember("%S", "unused")
                            .build(),
                    )
                    .build(),
            )
        }
        val sentinelFile =
            createGeneratedFile(codeGenDir, SENTINEL_PACKAGE, sentinelClassName, sentinelContent, setOf(vmClass.containingFileAsJavaFile))

        return listOf(mainFile, sentinelFile)
    }

    private fun generatedActivePlugin(
        vmClass: ClassReference.Psi,
        codeGenDir: File,
        module: ModuleDescriptor,
        localPluginPointFeatureNames: Set<String>,
        deferredThisRun: MutableList<Pair<String, ClassReference.Psi>>,
    ): List<GeneratedFileWithSources> {
        val scope = vmClass.annotations.firstOrNull { it.fqName == ContributesActivePlugin::class.fqName }?.scopeOrNull(0)!!
        val boundType = vmClass.annotations.firstOrNull { it.fqName == ContributesActivePlugin::class.fqName }?.boundTypeOrNull()!!
        val featureDefaultValue = vmClass.annotations.firstOrNull {
            it.fqName == ContributesActivePlugin::class.fqName
        }?.defaultActiveValueOrNull() ?: DefaultFeatureValue.TRUE
        val parentFeatureName = vmClass.annotations
            .firstOrNull { it.fqName == ContributesActivePlugin::class.fqName }
            ?.activePluginParentFeatureNameOrNull()
            ?: throw AnvilCompilationException(
                "${vmClass.fqName}: @ContributesActivePlugin requires a non-blank parentFeatureName.",
                element = vmClass.clazz.identifyingElement,
            )
        val featureName = vmClass.annotations
            .firstOrNull { it.fqName == ContributesActivePlugin::class.fqName }
            ?.activePluginFeatureNameOrNull()
            ?: throw AnvilCompilationException(
                "${vmClass.fqName}: @ContributesActivePlugin requires a non-blank featureName.",
                element = vmClass.clazz.identifyingElement,
            )
        if (!featureName.startsWith("plugin") || featureName.startsWith("pluginPoint")) {
            throw AnvilCompilationException(
                "${vmClass.fqName}: @ContributesActivePlugin featureName must start with \"plugin\" but not \"pluginPoint\" (got \"$featureName\").",
                element = vmClass.clazz.identifyingElement,
            )
        }
        if (!parentFeatureName.startsWith("pluginPoint")) {
            throw AnvilCompilationException(
                "${vmClass.fqName}: @ContributesActivePlugin parentFeatureName must start with \"pluginPoint\" (got \"$parentFeatureName\").",
                element = vmClass.clazz.identifyingElement,
            )
        }
        if (parentFeatureName.contains("__")) {
            throw AnvilCompilationException(
                "${vmClass.fqName}: parentFeatureName can't contain \"__\" — it's used as a separator in deferred validation marker class names " +
                    "(got \"$parentFeatureName\").",
                element = vmClass.clazz.identifyingElement,
            )
        }

        // Validate parentFeatureName.
        // Same-module: check the set collected in generateCode's first pass.
        // Direct-dependency module: sentinel emitted by the plugin point's codegen is visible —
        //   look it up directly. If found, validation passes immediately.
        // Sibling module (or no plugin points compiled yet): sentinel is not in scope.
        //   → Always emit a DeferredValidation marker so the composition root (:app) can
        //     re-validate once it has both the sentinels and all sibling-module outputs on
        //     its classpath. We do NOT throw here: "sentinel package has other content" does
        //     not mean we have a complete view of all plugin points (other sentinels may live
        //     in sibling modules not on this module's classpath).
        var emitDeferredMarker = false
        val foundLocally = parentFeatureName in localPluginPointFeatureNames
        if (!foundLocally) {
            val registryScope = module.getPackage(FqName(SENTINEL_PACKAGE)).memberScope
            val sentinelFound = registryScope.getContributedClassifier(
                Name.identifier("ActivePluginPointRegistry_$parentFeatureName"),
                NoLookupLocation.FROM_BACKEND,
            ) != null
            if (!sentinelFound) {
                // Sentinel missing — could be a sibling module or a typo. Defer to :app.
                emitDeferredMarker = true
            }
            // else: sentinel found — direct dependency, validation passed, no marker needed.
        }
        val generatedPackage = vmClass.packageFqName.toString()
        val pluginClassName = "${vmClass.shortName}_ActivePlugin"
        val pluginRemoteFeatureClassName = "${vmClass.shortName}_ActivePlugin_RemoteFeature"
        val pluginRemoteFeatureStoreClassName = "${vmClass.shortName}_ActivePlugin_RemoteFeature_MultiProcessStore"
        val pluginPriority = vmClass.annotations.firstOrNull { it.fqName == ContributesActivePlugin::class.fqName }?.priorityOrNull()
        val pluginSupportExperiments = vmClass.annotations.firstOrNull {
            it.fqName == ContributesActivePlugin::class.fqName
        }?.isExperimentOrNull() ?: false
        val pluginInternalAlwaysEnabled = vmClass.annotations.firstOrNull {
            it.fqName == ContributesActivePlugin::class.fqName
        }?.internalAlwaysEnabledOrNull() ?: false

        // Check if there's another plugin class, in the same plugin point, that has the same class simplename
        // we can't allow that because the backing remote feature would be the same
        val existingFeature = featureBackedClassNames.putIfAbsent("${featureName}_$parentFeatureName", vmClass.fqName)
        if (existingFeature != null) {
            throw AnvilCompilationException(
                "${vmClass.fqName} plugin name is duplicated, previous found in $existingFeature",
                element = vmClass.clazz.identifyingElement,
            )
        }
        if (scope.fqName != appScopeFqName) {
            throw AnvilCompilationException(
                "${vmClass.fqName}: Active plugins can only be used in 'AppScope'.",
                element = vmClass.clazz.identifyingElement,
            )
        }

        val content = FileSpec.buildFile(generatedPackage, pluginClassName) {
            addImport(DefaultFeatureValue::class.fqName.parent().asString(), DefaultFeatureValue::class.fqName.shortName().asString())
            addImport(DefaultFeatureValue::class.fqName.asString(), DefaultFeatureValue.TRUE.toString())
            addImport(DefaultFeatureValue::class.fqName.asString(), DefaultFeatureValue.FALSE.toString())
            addImport(DefaultFeatureValue::class.fqName.asString(), DefaultFeatureValue.INTERNAL.toString())
            // First create the class that will contribute the active plugin.
            // We do expect that the plugins are define using the "ContributesActivePlugin" annotation but are also injected
            // using @Inject in the constructor, as the concrete plugin type is use as delegate.
            addType(
                TypeSpec.classBuilder(pluginClassName).apply {
                    addAnnotation(
                        AnnotationSpec.builder(ContributesMultibinding::class)
                            .addMember("scope = %T::class", scope.asClassName())
                            .addMember("boundType = %T::class", boundType.asClassName())
                            .build(),
                    )
                    // If the active plugin defined a priority then add the right annotation
                    pluginPriority?.let {
                        addAnnotation(
                            AnnotationSpec.builder(PriorityKey::class)
                                .addMember("%L", it.toString())
                                .build(),
                        )
                    }

                    // primary constructor and parameters. We need the active plugin and the remote feature toggle
                    primaryConstructor(
                        FunSpec.constructorBuilder()
                            .addAnnotation(AnnotationSpec.builder(Inject::class).build())
                            .addParameter("activePlugin", vmClass.asClassName())
                            .addParameter("toggle", ClassName(generatedPackage, pluginRemoteFeatureClassName))
                            .build(),
                    )
                    addProperty(
                        PropertySpec.builder("activePlugin", vmClass.asClassName(), PRIVATE)
                            .initializer("activePlugin")
                            .build(),
                    )
                    addProperty(
                        PropertySpec.builder("toggle", ClassName(generatedPackage, pluginRemoteFeatureClassName), PRIVATE)
                            .initializer("toggle")
                            .build(),
                    )

                    addSuperinterface(
                        boundType.asClassName(),
                        delegate = CodeBlock.of("activePlugin"),
                    )

                    addFunction(
                        FunSpec.builder("isActive")
                            .addModifiers(OVERRIDE, SUSPEND)
                            .returns(Boolean::class)
                            .addCode(CodeBlock.of("return toggle.$featureName().isEnabled()"))
                            .build(),
                    )
                }.build(),
            ).build()

            // Now generate the feature flag that guards the plugin
            addType(
                TypeSpec.interfaceBuilder(pluginRemoteFeatureClassName).apply {
                    addAnnotation(
                        AnnotationSpec.builder(ContributesRemoteFeature::class)
                            .addMember("scope = %T::class", scope.asClassName())
                            .addMember("featureName = %S", parentFeatureName)
                            .addMember("toggleStore = %T::class", ClassName(packageName, pluginRemoteFeatureStoreClassName))
                            .build(),
                    )
                    addFunction(
                        FunSpec.builder("self")
                            .addModifiers(ABSTRACT)
                            .addAnnotation(
                                AnnotationSpec.builder(Toggle.DefaultValue::class)
                                    // The parent feature toggle is the one guarding the plugin point, for convention is default enabled.
                                    .addMember("defaultValue = %L", "DefaultFeatureValue.TRUE")
                                    .build(),
                            )
                            .returns(Toggle::class)
                            .build(),
                    )
                    addFunction(
                        FunSpec.builder(featureName).apply {
                            addModifiers(ABSTRACT)
                            addAnnotation(
                                AnnotationSpec.builder(Toggle.DefaultValue::class)
                                    .addMember("defaultValue = %L", featureDefaultValue)
                                    .build(),
                            )
                            // If the active plugin defines [supportExperiments = true] we mark it as Experiment
                            if (pluginSupportExperiments) {
                                addAnnotation(AnnotationSpec.builder(Experiment::class).build())
                            }
                            // If the active plugin defines [internalAlwaysEnabled = true] we mark it as InternalAlwaysEnabled
                            if (pluginInternalAlwaysEnabled) {
                                addAnnotation(AnnotationSpec.builder(InternalAlwaysEnabled::class).build())
                            }

                            returns(Toggle::class)
                        }
                            .build(),
                    )
                }.build(),
            ).build()

            // generate the feature flag multi-process store
            createRemoteFeatureFlagMultiprocessStore(
                scope,
                module,
                pluginRemoteFeatureStoreClassName,
                pluginRemoteFeatureClassName,
                parentFeatureName,
            )
        }

        val mainFile = createGeneratedFile(codeGenDir, generatedPackage, pluginClassName, content, setOf(vmClass.containingFileAsJavaFile))

        // Emit a deferred marker so the composition root can validate this parentFeatureName
        // once it has all sibling-module sentinels on its classpath.
        // The emitting class name is appended (separated by "__") to guarantee uniqueness
        // across modules: two sibling modules can both emit a marker for the same
        // parentFeatureName without producing duplicate class files at DEX merge time.
        val deferredMarkerFile = if (emitDeferredMarker) {
            val markerClassName = "${DEFERRED_MARKER_PREFIX}${parentFeatureName}__${vmClass.shortName}"
            deferredThisRun.add(parentFeatureName to vmClass)
            if (emittedDeferredMarkers.putIfAbsent(markerClassName, true) == null) {
                val markerContent = FileSpec.buildFile(DEFERRED_SENTINEL_PACKAGE, markerClassName) {
                    addType(
                        TypeSpec.objectBuilder(markerClassName)
                            .addModifiers(INTERNAL)
                            .addAnnotation(
                                AnnotationSpec.builder(Suppress::class)
                                    .addMember("%S", "unused")
                                    .build(),
                            )
                            .build(),
                    )
                }
                createGeneratedFile(codeGenDir, DEFERRED_SENTINEL_PACKAGE, markerClassName, markerContent, setOf(vmClass.containingFileAsJavaFile))
            } else {
                null
            }
        } else {
            null
        }

        return listOfNotNull(mainFile, deferredMarkerFile)
    }

    private fun FileSpec.Builder.createRemoteFeatureFlagMultiprocessStore(
        scope: ClassReference,
        module: ModuleDescriptor,
        pluginRemoteFeatureStoreClassName: String,
        pluginRemoteFeatureClassName: String,
        parentFeatureName: String,
    ): FileSpec {
        val preferencesName = "com.duckduckgo.feature.toggle.$parentFeatureName.mp.store"

        // needed for the launch() and prefs.edit() {} inside the createToggleStoreImplementation()
        addImport("kotlinx.coroutines", "launch")
        addImport("androidx.core.content", "edit")
        addImport("com.squareup.moshi.kotlin.reflect", "KotlinJsonAdapterFactory")

        return addType(
            TypeSpec.classBuilder(pluginRemoteFeatureStoreClassName).apply {
                addAnnotation(
                    AnnotationSpec.builder(ContributesBinding::class)
                        .addMember("scope = %T::class", scope.asClassName())
                        .build(),
                )

                addAnnotation(
                    AnnotationSpec.builder(RemoteFeatureStoreNamed::class)
                        .addMember("value = %T::class", ClassName(packageName, pluginRemoteFeatureClassName))
                        .build(),
                )

                addSuperinterface(Toggle.Store::class)

                primaryConstructor(
                    FunSpec.constructorBuilder()
                        .addAnnotation(AnnotationSpec.builder(Inject::class).build())
                        .addParameter(
                            ParameterSpec.builder("coroutineScope", coroutineScopeFqName.asClassName(module))
                                .addAnnotation(appCoroutineScopeFqName.asClassName(module))
                                .build(),
                        )
                        .addParameter("dispatcherProvider", dispatcherProviderFqName.asClassName(module))
                        .addParameter("sharedPreferencesProvider", sharedPreferencesProviderFqName.asClassName(module))
                        .addParameter("moshi", moshiFqName.asClassName(module))
                        .build(),
                )
                addProperty(
                    PropertySpec.builder("coroutineScope", coroutineScopeFqName.asClassName(module), PRIVATE)
                        .initializer("coroutineScope")
                        .build(),
                )
                addProperty(
                    PropertySpec.builder("dispatcherProvider", dispatcherProviderFqName.asClassName(module), PRIVATE)
                        .initializer("dispatcherProvider")
                        .build(),
                )
                addProperty(
                    PropertySpec.builder("sharedPreferencesProvider", sharedPreferencesProviderFqName.asClassName(module), PRIVATE)
                        .initializer("sharedPreferencesProvider")
                        .build(),
                )
                addProperty(
                    PropertySpec.builder("moshi", moshiFqName.asClassName(module), PRIVATE)
                        .initializer("moshi")
                        .build(),
                )
                addProperty(
                    PropertySpec.builder("preferences", sharedPreferencesFqName.asClassName(module), PRIVATE)
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
                        jsonAdapterFqName.asClassName(module).parameterizedBy(Toggle.State::class.asClassName()),
                        PRIVATE,
                    )
                        .delegate(
                            CodeBlock.builder()
                                .beginControlFlow("lazy")
                                .add(
                                    """
                                            moshi.newBuilder().add(KotlinJsonAdapterFactory()).build().adapter(%T::class.java)
                                    """.trimIndent(),
                                    Toggle.State::class.asClassName(),
                                )
                                .endControlFlow()
                                .build(),
                        )
                        .build(),
                )

                addFunctions(createToggleStoreImplementation(module))
            }.build(),
        ).build()
    }
    private fun createToggleStoreImplementation(module: ModuleDescriptor): List<FunSpec> {
        return listOf(
            FunSpec.builder("set")
                .addModifiers(OVERRIDE)
                .addParameter("key", String::class.asClassName())
                .addParameter("state", Toggle.State::class.asClassName())
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
            FunSpec.builder("get")
                .addModifiers(OVERRIDE)
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
                .returns(Toggle.State::class.asClassName().copy(nullable = true))
                .build(),
        )
    }

    private fun ClassReference.Psi.isContributesActivePlugin(): Boolean {
        return this.annotations.firstOrNull { it.fqName == ContributesActivePlugin::class.fqName } != null
    }

    private fun ClassReference.Psi.isContributesActivePluginPoint(): Boolean {
        return this.annotations.firstOrNull { it.fqName == ContributesActivePluginPoint::class.fqName } != null
    }

    /** Reads `featureName` from @ContributesActivePluginPoint (index 2). Returns null when blank/unset. */
    @OptIn(ExperimentalAnvilApi::class)
    private fun AnnotationReference.pluginPointFeatureNameOrNull(): String? =
        (argumentAt("featureName", 2)?.value() as? String)?.takeIf { it.isNotBlank() }

    /** Reads `featureName` from @ContributesActivePlugin (index 6). Returns null when blank/unset. */
    @OptIn(ExperimentalAnvilApi::class)
    private fun AnnotationReference.activePluginFeatureNameOrNull(): String? =
        (argumentAt("featureName", 6)?.value() as? String)?.takeIf { it.isNotBlank() }

    /** Reads `parentFeatureName` from @ContributesActivePlugin (index 7). Returns null when blank/unset. */
    @OptIn(ExperimentalAnvilApi::class)
    private fun AnnotationReference.activePluginParentFeatureNameOrNull(): String? =
        (argumentAt("parentFeatureName", 7)?.value() as? String)?.takeIf { it.isNotBlank() }

    @OptIn(ExperimentalAnvilApi::class)
    private fun AnnotationReference.defaultActiveValueOrNull(): DefaultFeatureValue? {
        val rawValue = argumentAt("defaultActiveValue", 2)?.value() as FqName? ?: return null

        return if (rawValue.parent() == DefaultFeatureValue::class.fqName) {
            DefaultFeatureValue.valueOf(rawValue.shortName().toString())
        } else {
            null
        }
    }

    @OptIn(ExperimentalAnvilApi::class)
    private fun AnnotationReference.priorityOrNull(): Int? = argumentAt("priority", 3)?.value()

    @OptIn(ExperimentalAnvilApi::class)
    private fun AnnotationReference.isExperimentOrNull(): Boolean? = argumentAt("supportExperiments", 4)?.value()

    @OptIn(ExperimentalAnvilApi::class)
    private fun AnnotationReference.internalAlwaysEnabledOrNull(): Boolean? = argumentAt("internalAlwaysEnabled", 5)?.value()

    private fun ClassReference.Psi.pluginClassName(
        fqName: FqName,
    ): ClassName? {
        return annotations
            .first { it.fqName == fqName }
            .argumentAt(name = "boundType", index = 1)
            ?.annotation
            ?.boundTypeOrNull()
            ?.asClassName()
    }

    companion object {
        internal val featureBackedClassNames = ConcurrentHashMap<String, FqName>()

        /**
         * Fixed package where sentinel objects are generated for each plugin point.
         * Sentinel FQN: `$SENTINEL_PACKAGE.ActivePluginPointRegistry_<featureName>`
         * Used for cross-module parentFeatureName validation via ModuleDescriptor.
         */
        internal const val SENTINEL_PACKAGE = "com.duckduckgo.anvil.generated"

        /**
         * Fixed package where deferred validation markers are emitted by sibling impl modules.
         * Marker FQN: `$DEFERRED_SENTINEL_PACKAGE.${DEFERRED_MARKER_PREFIX}<parentFeatureName>__<EmittingClass>`
         *
         * The emitting class name suffix (after "__") makes each marker unique per emitting
         * class, preventing DEX duplicate class errors when multiple sibling modules each emit
         * a marker for the same parentFeatureName.
         *
         * Emitted when a @ContributesActivePlugin has a parentFeatureName but the sentinel
         * registry is empty (sibling module — plugin point not on our classpath).
         * The composition root (:app) validates these markers against the sentinel registry.
         */
        internal const val DEFERRED_SENTINEL_PACKAGE = "com.duckduckgo.anvil.generated.deferred"

        /** Prefix for deferred validation marker class names. */
        internal const val DEFERRED_MARKER_PREFIX = "ActivePluginDeferredValidation_"

        /**
         * Tracks which deferred marker class names have already been emitted in this
         * module's compilation run, to prevent duplicate file creation if the same class
         * is processed more than once.
         * Key is the full marker class name (includes emitting class name as suffix).
         */
        internal val emittedDeferredMarkers = ConcurrentHashMap<String, Boolean>()

        private val pluginPointFqName = FqName("com.duckduckgo.common.utils.plugins.PluginPoint")
        private val dispatcherProviderFqName = FqName("com.duckduckgo.common.utils.DispatcherProvider")
        private val activePluginPointFqName = FqName("com.duckduckgo.common.utils.plugins.InternalActivePluginPoint")
        private val coroutineScopeFqName = FqName("kotlinx.coroutines.CoroutineScope")
        private val sharedPreferencesProviderFqName = FqName("com.duckduckgo.data.store.api.SharedPreferencesProvider")
        private val moshiFqName = FqName("com.squareup.moshi.Moshi")
        private val appCoroutineScopeFqName = FqName("com.duckduckgo.app.di.AppCoroutineScope")
        private val sharedPreferencesFqName = FqName("android.content.SharedPreferences")
        private val jsonAdapterFqName = FqName("com.squareup.moshi.JsonAdapter")
        private val appScopeFqName = FqName("com.duckduckgo.di.scopes.AppScope")
    }
}
