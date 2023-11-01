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

import com.duckduckgo.feature.toggles.api.Toggle.State
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.random.Random

class FeatureToggles private constructor(
    private val store: Toggle.Store,
    private val appVersionProvider: () -> Int,
    private val flavorNameProvider: () -> String,
    private val featureName: String,
) {

    private val featureToggleCache = mutableMapOf<Method, Toggle>()

    data class Builder(
        private var store: Toggle.Store? = null,
        private var appVersionProvider: () -> Int = { Int.MAX_VALUE },
        private var flavorNameProvider: () -> String = { "" },
        private var featureName: String? = null,
    ) {

        fun store(store: Toggle.Store) = apply { this.store = store }
        fun appVersionProvider(appVersionProvider: () -> Int) = apply { this.appVersionProvider = appVersionProvider }
        fun flavorNameProvider(flavorNameProvider: () -> String) = apply { this.flavorNameProvider = flavorNameProvider }
        fun featureName(featureName: String) = apply { this.featureName = featureName }
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
            return FeatureToggles(this.store!!, appVersionProvider, flavorNameProvider, featureName!!)
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
            val isInternalAlwaysEnabledAnnotated: Boolean = runCatching {
                method.getAnnotation(Toggle.InternalAlwaysEnabled::class.java)
            }.getOrNull() != null

            return ToggleImpl(
                store = store,
                key = getToggleNameForMethod(method),
                defaultValue = defaultValue,
                isInternalAlwaysEnabled = isInternalAlwaysEnabledAnnotated,
                appVersionProvider = appVersionProvider,
                flavorNameProvider = flavorNameProvider,
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
     * This is the method that SHALL be called to get whether a feature is enabled or not. DO NOT USE [getRawStoredState] for that
     * @return `true` if the feature should be enabled, `false` otherwise
     */
    fun isEnabled(): Boolean

    /**
     * The usage of this API is only useful for internal/dev settings/features
     * If you find yourself having to call this method in production code, then YOU'RE DOING SOMETHING WRONG
     *
     * @param state update the stored [State] of the feature flag
     */
    fun setEnabled(state: State)

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

    data class State(
        val remoteEnableState: Boolean? = null,
        val enable: Boolean = false,
        val minSupportedVersion: Int? = null,
        val enabledOverrideValue: Boolean? = null,
        val rollout: List<Double>? = null,
        val rolloutStep: Int? = null,
    )

    interface Store {
        fun set(key: String, state: State)

        fun get(key: String): State?
    }

    @Target(AnnotationTarget.FUNCTION)
    @Retention(AnnotationRetention.RUNTIME)
    annotation class DefaultValue(
        val defaultValue: Boolean,
    )

    @Target(AnnotationTarget.FUNCTION)
    @Retention(AnnotationRetention.RUNTIME)
    annotation class InternalAlwaysEnabled
}

internal class ToggleImpl constructor(
    private val store: Toggle.Store,
    private val key: String,
    private val defaultValue: Boolean,
    private val isInternalAlwaysEnabled: Boolean,
    private val appVersionProvider: () -> Int,
    private val flavorNameProvider: () -> String = { "" },
) : Toggle {
    override fun isEnabled(): Boolean {
        fun evaluateLocalEnable(state: State): Boolean {
            return state.enable && appVersionProvider.invoke() >= (state.minSupportedVersion ?: 0)
        }
        // check if it should always be enabled for internal builds
        if (isInternalAlwaysEnabled && flavorNameProvider.invoke().lowercase() == "internal") {
            return true
        }

        // normal check
        return store.get(key)?.let { state ->
            state.remoteEnableState?.let { remoteState ->
                remoteState && evaluateLocalEnable(state)
            } ?: evaluateLocalEnable(state)
        } ?: return defaultValue
    }

    @Suppress("NAME_SHADOWING")
    override fun setEnabled(state: Toggle.State) {
        var state = state

        // remote is disabled, store and skip everything
        if (state.remoteEnableState == false) {
            store.set(key, state)
            return
        }

        // local state is false (and remote state is enabled) try incremental rollout
        if (!state.enable) {
            state = calculateRolloutState(state)
        }

        // remote state is null, means app update. Propagate the local state to remote state
        if (state.remoteEnableState == null) {
            state = state.copy(remoteEnableState = state.enable)
        }

        // finally store the state
        store.set(key, state)
    }

    override fun getRawStoredState(): State? {
        return store.get(key)
    }

    private fun calculateRolloutState(
        state: State,
    ): State {
        fun sample(probability: Double): Boolean {
            val random = Random.nextDouble(100.0)
            return random < probability
        }
        val rolloutStep = state.rolloutStep

        // there is no rollout, return whatever the previous state was
        if (state.rollout.isNullOrEmpty()) {
            // when there is no rollout we don't continue calculating the state
            // however, if remote config has an enable value, ie. remoteEnableState we need to honour it
            // that covers eg. the fresh installed case
            return state.remoteEnableState?.let { remoteEnabledValue ->
                state.copy(enable = remoteEnabledValue)
            } ?: state
        }

        val sortedRollout = state.rollout.sorted().filter { it in 0.0..100.0 }
        if (sortedRollout.isEmpty()) return state

        when (rolloutStep) {
            // first time we see the rollout, pick the last step
            null -> {
                val step = sortedRollout.last()
                val isEnabled = sample(step.toDouble())
                return state.copy(
                    enable = isEnabled,
                    rolloutStep = sortedRollout.size,
                )
            }
            // this is an error and should not happen, don't change state
            0 -> {
                return state
            }
            else -> {
                val steps = sortedRollout.size
                val lastStep = state.rolloutStep

                for (s in lastStep until steps) {
                    // determine effective probability
                    val probability = (sortedRollout[s] - sortedRollout[s - 1]) / (100.0 - sortedRollout[s - 1])
                    if (sample(probability * 100.0)) {
                        return state.copy(
                            enable = true,
                            rolloutStep = s + 1,
                        )
                    }
                }
                return state.copy(rolloutStep = sortedRollout.size)
            }
        }
    }
}
