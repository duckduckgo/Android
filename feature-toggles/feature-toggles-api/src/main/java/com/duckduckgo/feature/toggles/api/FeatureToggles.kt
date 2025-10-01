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

package com.duckduckgo.feature.toggles.api

import com.duckduckgo.feature.toggles.api.Toggle.FeatureName
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.feature.toggles.api.Toggle.State.Cohort
import com.duckduckgo.feature.toggles.api.Toggle.State.CohortName
import com.duckduckgo.feature.toggles.internal.api.FeatureTogglesCallback
import org.apache.commons.math3.distribution.EnumeratedIntegerDistribution
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random

class FeatureToggles private constructor(
    private val store: Toggle.Store,
    private val appVersionProvider: () -> Int,
    private val flavorNameProvider: () -> String,
    private val featureName: String,
    private val appVariantProvider: () -> String?,
    private val forceDefaultVariant: () -> Unit,
    private val callback: FeatureTogglesCallback?,
) {

    private val featureToggleCache = mutableMapOf<Method, Toggle>()

    data class Builder(
        private var store: Toggle.Store? = null,
        private var appVersionProvider: () -> Int = { Int.MAX_VALUE },
        private var flavorNameProvider: () -> String = { "" },
        private var featureName: String? = null,
        private var appVariantProvider: () -> String? = { "" },
        private var forceDefaultVariant: () -> Unit = { /** noop **/ },
        private var callback: FeatureTogglesCallback? = null,
    ) {

        fun store(store: Toggle.Store) = apply { this.store = store }
        fun appVersionProvider(appVersionProvider: () -> Int) = apply { this.appVersionProvider = appVersionProvider }
        fun flavorNameProvider(flavorNameProvider: () -> String) = apply { this.flavorNameProvider = flavorNameProvider }
        fun featureName(featureName: String) = apply { this.featureName = featureName }
        fun appVariantProvider(variantName: () -> String?) = apply { this.appVariantProvider = variantName }
        fun forceDefaultVariantProvider(forceDefaultVariant: () -> Unit) = apply { this.forceDefaultVariant = forceDefaultVariant }
        fun callback(callback: FeatureTogglesCallback) = apply { this.callback = callback }
        fun build(): FeatureToggles {
            val missing = StringBuilder()
            if (this.store == null) {
                missing.append("store")
            }
            if (this.featureName == null) {
                missing.append(", featureName ")
            }
            if (missing.isNotBlank()) {
                throw IllegalArgumentException("This following parameters can't be null: $missing")
            }
            return FeatureToggles(
                store = this.store!!,
                appVersionProvider = appVersionProvider,
                flavorNameProvider = flavorNameProvider,
                featureName = featureName!!,
                appVariantProvider = appVariantProvider,
                forceDefaultVariant = forceDefaultVariant,
                callback = this.callback,
            )
        }
    }

    fun <T> create(toggles: Class<T>): T {
        validateFeatureInterface(toggles)

        return Proxy.newProxyInstance(
            toggles.classLoader,
            arrayOf(toggles),
        ) { _, method, args ->
            validateMethod(method, args.orEmpty())

            if (method.declaringClass === Any::class.java) {
                return@newProxyInstance method.invoke(this, args) as T
            }
            return@newProxyInstance loadToggleMethod(method)
        } as T
    }

    private fun loadToggleMethod(method: Method): Toggle {
        synchronized(featureToggleCache) {
            featureToggleCache[method]?.let { return it }

            val defaultValue = try {
                method.getAnnotation(Toggle.DefaultValue::class.java).defaultValue
            } catch (t: Throwable) {
                throw IllegalStateException("Feature toggle methods shall have annotated default value")
            }
            val resolvedDefaultValue = when (val value = defaultValue.toValue()) {
                is Boolean -> value
                is String -> value.lowercase() == flavorNameProvider.invoke().lowercase()
                else -> throw IllegalStateException("Unsupported default value type")
            }

            val isInternalAlwaysEnabledAnnotated: Boolean = runCatching {
                method.getAnnotation(Toggle.InternalAlwaysEnabled::class.java)
            }.getOrNull() != null
            val isExperiment: Boolean = runCatching {
                method.getAnnotation(Toggle.Experiment::class.java)
            }.getOrNull() != null

            return ToggleImpl(
                store = store,
                key = getToggleNameForMethod(method),
                defaultValue = resolvedDefaultValue,
                isInternalAlwaysEnabled = isInternalAlwaysEnabledAnnotated,
                isExperiment = isExperiment,
                appVersionProvider = appVersionProvider,
                flavorNameProvider = flavorNameProvider,
                appVariantProvider = appVariantProvider,
                forceDefaultVariant = forceDefaultVariant,
                callback = callback,
            ).also { featureToggleCache[method] = it }
        }
    }

    private fun validateFeatureInterface(feature: Class<*>) {
        if (!feature.isInterface) {
            throw IllegalArgumentException("Feature declarations must be interfaces")
        }
    }

    private fun getToggleNameForMethod(method: Method): String {
        if (method.name != "self") return "${featureName}_${method.name}"

        // This can throw but should never happen as it's guarded by codegen as well
        return featureName
    }

    private fun validateMethod(method: Method, args: Array<out Any>) {
        if (method.returnType != Toggle::class.java) {
            throw IllegalArgumentException("Feature method return types must be Toggle")
        }

        if (args.isNotEmpty()) {
            throw IllegalArgumentException("Feature methods must not have arguments")
        }
    }
}

interface Toggle {
    /**
     * @return returns the [FeatureName]
     */
    fun featureName(): FeatureName

    /**
     * This method
     * - Enrolls the user if not previously enrolled (ie. no cohort currently assigned) AND [isEnabled].
     * - Is idempotent, ie. calling the function multiple times has the same effect as calling it once.
     *
     * @return `true` when the first enrolment is done, `false` in any other subsequent call
     */
    suspend fun enroll(): Boolean

    /**
     * This method
     *    - Returns whether the feature flag state is enabled or disabled.
     *    - It is not affected by experiment cohort assignment. It just checks whether the feature is enabled or not.
     *    - It considers all other constraints like targets, minSupportedVersion, etc.
     *
     * @return `true` if the feature should be enabled, `false` otherwise
     */
    fun isEnabled(): Boolean

    /**
     * The usage of this API is only useful for internal/dev settings/features
     * If you find yourself having to call this method in production code, then YOU'RE DOING SOMETHING WRONG
     *
     * @param state update the stored [State] of the feature flag
     */
    fun setRawStoredState(state: State)

    /**
     * The usage of this API is only useful for internal/dev settings/features
     * If you find yourself having to call this method in production code, then YOU'RE DOING SOMETHING WRONG
     * The raw state is the stored state. [isEnabled] method takes the raw state and computes whether the feature should be enabled or disabled.
     * eg. by factoring in [State.minSupportedVersion] amongst others.
     *
     * You should never use individual properties on that raw state, eg. [State.enable] to decide whether the feature is enabled/disabled.
     *
     * @return the raw [State] store for this feature flag.
     */
    fun getRawStoredState(): State?

    /**
     * @return a JSON string containing the `settings`` of the feature or null if not present in the remote config
     */
    fun getSettings(): String?

    /**
     * Convenience method that checks if [getCohort] is not null
     *  - WARNING: This method does not check if the experiment is still enabled or not.
     * @return `true` if the user is enrolled in the experiment and `false` otherwise
     */
    suspend fun isEnrolled(): Boolean

    /**
     * @return `true` if the user is enrolled in the given cohort and the experiment is enabled or `false` otherwise
     */
    suspend fun isEnrolledAndEnabled(cohort: CohortName): Boolean

    /**
     * @return the list of domain exceptions`exceptions` of the feature or empty list if not present in the remote config
     */
    fun getExceptions(): List<FeatureException>

    /**
     * WARNING: This method always returns the cohort assigned regardless it the experiment is still enabled or not.
     * @return a [Cohort] if one has been assigned or `null` otherwise.
     */
    suspend fun getCohort(): Cohort?

    /**
     * This represents the state of a [Toggle]
     * @param remoteEnableState is the enabled/disabled state in the remote config
     * @param enable is the ultimate (computed) enabled state
     * @param minSupportedVersion is the lowest Android version for which this toggle can be enabled
     * @param rollout is the rollout specified in remote config
     * @param rolloutThreshold is the percentile for which this flag will be enabled. It's a value between 0-1
     *  Example: If [rolloutThreshold] = 0.3, if [rollout] is  <0.3 then the toggle will be disabled
     * @param targets specified the target audience for this toggle. If the user is not within the targets the toggle will be disabled
     * @param metadataInfo Some metadata info about the toggle. It is not stored and its computed when calling [getRawStoredState].
     */
    data class State(
        val remoteEnableState: Boolean? = null,
        val enable: Boolean = false,
        val minSupportedVersion: Int? = null,
        val rollout: List<Double>? = null,
        val rolloutThreshold: Double? = null,
        val targets: List<Target> = emptyList(),
        val metadataInfo: String? = null,
        val cohorts: List<Cohort> = emptyList(),
        val assignedCohort: Cohort? = null,
        val settings: String? = null,
        val exceptions: List<FeatureException> = emptyList(),
    ) {
        data class Target(
            val variantKey: String?,
            val localeCountry: String?,
            val localeLanguage: String?,
            val isReturningUser: Boolean?,
            val isPrivacyProEligible: Boolean?,
            val minSdkVersion: Int?,
        )
        data class Cohort(
            val name: String,
            val weight: Int,

            /**
             * Represents serialized [ZonedDateTime] with "America/New_York" zone ID.
             *
             * This is nullable because only assigned cohort should have a value here, it's ET timezone
             */
            val enrollmentDateET: String? = null,
        )
        interface CohortName {
            val cohortName: String
        }
    }

    /**
     * The feature
     * [name] the name of the feature
     * [parentName] the name of the parent feature, or `null` if the feature has no parent (root feature)
     */
    data class FeatureName(
        val parentName: String?,
        val name: String,
    )

    interface Store {
        fun set(key: String, state: State)

        fun get(key: String): State?
    }

    /**
     * It is possible to add feature [Target]s.
     * To do that, just add the property inside the [Target] and implement the [TargetMatcherPlugin] to do the matching
     */
    interface TargetMatcherPlugin {
        /**
         * Implement this method when adding a new target property.
         * @return `true` if the target matches else false
         */
        fun matchesTargetProperty(target: State.Target): Boolean
    }

    /**
     * This annotation is required.
     * It specifies the default value of the feature flag when it's not remotely defined
     */
    @Target(AnnotationTarget.FUNCTION)
    @Retention(AnnotationRetention.RUNTIME)
    annotation class DefaultValue(
        val defaultValue: DefaultFeatureValue,
    )

    enum class DefaultFeatureValue {
        FALSE,
        TRUE,
        INTERNAL,
        ;

        fun toValue(): Any {
            return when (this) {
                FALSE -> false
                TRUE -> true
                INTERNAL -> "internal"
            }
        }
    }

    /**
     * This annotation is optional.
     * It will make the feature flag ALWAYS enabled for internal builds
     */
    @Target(AnnotationTarget.FUNCTION)
    @Retention(AnnotationRetention.RUNTIME)
    annotation class InternalAlwaysEnabled

    /**
     * This annotation should be used in feature flags that related to experimentation.
     * It will make the feature flag to set the default variant if [isEnabled] is called BEFORE any variant has been allocated.
     * It will make the feature flag to consider the target variants during the [isEnabled] evaluation.
     */
    @Target(AnnotationTarget.FUNCTION)
    @Retention(AnnotationRetention.RUNTIME)
    annotation class Experiment
}

internal class ToggleImpl constructor(
    private val store: Toggle.Store,
    private val key: String,
    private val defaultValue: Boolean,
    private val isInternalAlwaysEnabled: Boolean,
    private val isExperiment: Boolean,
    private val appVersionProvider: () -> Int,
    private val flavorNameProvider: () -> String = { "" },
    private val appVariantProvider: () -> String?,
    private val forceDefaultVariant: () -> Unit,
    private val callback: FeatureTogglesCallback?,
) : Toggle {

    override fun equals(other: Any?): Boolean {
        if (other !is Toggle) {
            return false
        }
        return this.featureName() == other.featureName()
    }

    override fun hashCode(): Int {
        return this.featureName().hashCode()
    }

    override fun featureName(): FeatureName {
        val parts = key.split("_")
        return if (parts.size == 2) {
            FeatureName(name = parts[1], parentName = parts[0])
        } else {
            FeatureName(name = parts[0], parentName = null)
        }
    }

    override suspend fun enroll(): Boolean {
        return enrollInternal()
    }

    private fun enrollInternal(force: Boolean = false): Boolean {
        // if the Toggle is not enabled, then we don't enroll
        if (isEnabled() == false) {
            return false
        }

        store.get(key)?.let { state ->
            if (force || state.assignedCohort == null) {
                val updatedState = state.copy(assignedCohort = assignCohortRandomly(state.cohorts, state.targets)).also {
                    it.assignedCohort?.let { cohort ->
                        callback?.onCohortAssigned(this.featureName().name, cohort.name, cohort.enrollmentDateET!!)
                    }
                }
                store.set(key, updatedState)
                return updatedState.assignedCohort != null
            }
        }

        return false
    }

    override fun isEnabled(): Boolean {
        // This fun is in there because it should never be called outside this method
        fun Toggle.State.evaluateTargetMatching(isExperiment: Boolean): Boolean {
            val variant = appVariantProvider.invoke()
            // no targets then consider always treated
            if (this.targets.isEmpty()) {
                return true
            }
            // if it's an experiment we only check target variants and ignore all the rest
            // this is because the (retention) experiments define their targets some place else
            val variantTargets = this.targets.mapNotNull { it.variantKey }
            if (isExperiment && variantTargets.isNotEmpty()) {
                return variantTargets.contains(variant)
            }
            // finally, check all other targets
            val nonVariantTargets = this.targets.filter { it.variantKey == null }

            // callback should never be null, but if it is, consider targets a match
            return callback?.matchesToggleTargets(nonVariantTargets) ?: true
        }

        // This fun is in there because it should never be called outside this method
        fun evaluateLocalEnable(state: State, isExperiment: Boolean): Boolean {
            // variants are only considered for Experiment feature flags
            val doTargetsMatch = state.evaluateTargetMatching(isExperiment)

            return state.enable &&
                doTargetsMatch &&
                appVersionProvider.invoke() >= (state.minSupportedVersion ?: 0)
        }
        // check if it should always be enabled for internal builds
        if (isInternalAlwaysEnabled && flavorNameProvider.invoke().lowercase() == "internal") {
            return true
        }
        // If there's not assigned variant yet and is an experiment feature, set default variant
        if (appVariantProvider.invoke() == null && isExperiment) {
            forceDefaultVariant.invoke()
        }

        // normal check
        return store.get(key)?.let { state ->
            state.remoteEnableState?.let { remoteState ->
                remoteState && evaluateLocalEnable(state, isExperiment)
            } ?: evaluateLocalEnable(state, isExperiment)
        } ?: return defaultValue
    }

    @Suppress("NAME_SHADOWING")
    override fun setRawStoredState(state: Toggle.State) {
        var state = state

        // remote is disabled, store and skip everything
        if (state.remoteEnableState == false) {
            store.set(key, state)
            return
        }

        state = evaluateRolloutThreshold(state)

        // remote state is null, means app update. Propagate the local state to remote state
        if (state.remoteEnableState == null) {
            state = state.copy(remoteEnableState = state.enable)
        }

        // finally store the state
        store.set(key, state)
    }

    override fun getRawStoredState(): State? {
        val metadata = listOf(
            isExperiment to "Retention Experiment",
            isInternalAlwaysEnabled to "Internal builds forced-enabled",
        )
        val info = metadata.filter { it.first }.joinToString(",") { it.second }
        return store.get(key)?.copy(metadataInfo = info)
    }

    private fun evaluateRolloutThreshold(
        inputState: State,
    ): State {
        fun checkAndSetRolloutThreshold(state: State): State {
            if (state.rolloutThreshold == null) {
                val random = Random.nextDouble(100.0)
                return state.copy(rolloutThreshold = random)
            }
            return state
        }

        val state = checkAndSetRolloutThreshold(inputState)

        // there is no rollout, return whatever the previous state was
        if (state.rollout.isNullOrEmpty()) {
            // when there is no rollout we don't continue calculating the state
            // however, if remote config has an enable value, ie. remoteEnableState we need to honour it
            // that covers eg. the fresh installed case
            return state.remoteEnableState?.let { remoteEnabledValue ->
                state.copy(enable = remoteEnabledValue)
            } ?: state
        }

        val scopedRolloutRange = state.rollout.filter { it in 0.0..100.0 }
        if (scopedRolloutRange.isEmpty()) return state

        return state.copy(
            enable = (state.rolloutThreshold ?: 0.0) <= scopedRolloutRange.last(),
        )
    }

    private fun assignCohortRandomly(
        cohorts: List<Cohort>,
        targets: List<State.Target>,
    ): Cohort? {
        fun getRandomCohort(cohorts: List<Cohort>): Cohort? {
            return kotlin.runCatching {
                @Suppress("NAME_SHADOWING") // purposely shadowing to ensure positive weights
                val cohorts = cohorts.filter { it.weight >= 0 }

                val indexArray = IntArray(cohorts.size) { i -> i }
                val weightArray = cohorts.map { it.weight.toDouble() }.toDoubleArray()

                val randomIndex = EnumeratedIntegerDistribution(indexArray, weightArray)

                cohorts[randomIndex.sample()]
            }.getOrNull()
        }
        fun containsAndMatchCohortTargets(targets: List<State.Target>): Boolean {
            return callback?.matchesToggleTargets(targets) ?: true
        }

        // In the remote config, targets is a list, but it should not be. So we pick the first one (?)
        if (!containsAndMatchCohortTargets(targets)) {
            return null
        }

        @Suppress("NAME_SHADOWING") // purposely shadowing to make sure we remove invalid variants
        val cohorts = cohorts.filter { it.weight >= 0 }

        val totalWeight = cohorts.sumOf { it.weight }
        if (totalWeight == 0) {
            // no variant active
            return null
        }

        return getRandomCohort(cohorts)?.copy(
            enrollmentDateET = ZonedDateTime.now(ZoneId.of("America/New_York")).toString(),
        )
    }

    override fun getSettings(): String? = store.get(key)?.settings

    override fun getExceptions(): List<FeatureException> {
        return store.get(key)?.exceptions.orEmpty()
    }

    override suspend fun getCohort(): Cohort? {
        val state = store.get(key)
        state?.assignedCohort?.let { assignedCohort ->
            // cohort is assigned and assignedCohort is no longer in remote config, then re-enroll
            if (!state.cohorts.map { it.name }.contains(assignedCohort.name)) {
                enrollInternal(force = true)
            }
        }

        return store.get(key)?.assignedCohort
    }

    override suspend fun isEnrolled(): Boolean = getCohort() != null

    override suspend fun isEnrolledAndEnabled(cohort: CohortName): Boolean {
        return cohort.cohortName.lowercase() == getCohort()?.name?.lowercase() && isEnabled()
    }
}
