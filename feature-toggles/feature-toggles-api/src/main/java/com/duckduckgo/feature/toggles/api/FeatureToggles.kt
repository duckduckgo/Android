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

import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class FeatureToggles private constructor(
    private val store: Toggle.Store,
    private val appVersionProvider: () -> Int,
    private val featureName: String,
) {

    private val featureToggleCache = mutableMapOf<Method, Toggle>()

    data class Builder(
        private var store: Toggle.Store? = null,
        private var appVersionProvider: () -> Int = { Int.MAX_VALUE },
        private var featureName: String? = null,
    ) {

        fun store(store: Toggle.Store) = apply { this.store = store }
        fun appVersionProvider(appVersionProvider: () -> Int) = apply { this.appVersionProvider = appVersionProvider }
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
            return FeatureToggles(this.store!!, appVersionProvider, featureName!!)
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

            return ToggleImpl(store, getToggleNameForMethod(method), defaultValue, appVersionProvider).also { featureToggleCache[method] = it }
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
    fun isEnabled(): Boolean

    fun setEnabled(state: State)

    data class State(
        val enable: Boolean = false,
        val minSupportedVersion: Int? = null,
        val enabledOverrideValue: Boolean? = null,
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
}

internal class ToggleImpl constructor(
    private val store: Toggle.Store,
    private val key: String,
    private val defaultValue: Boolean,
    private val appVersionProvider: () -> Int,
) : Toggle {
    override fun isEnabled(): Boolean {
        store.get(key)?.let { state ->
            return state.enable && appVersionProvider.invoke() >= (state.minSupportedVersion ?: 0)
        } ?: return defaultValue
    }

    override fun setEnabled(state: Toggle.State) {
        store.set(key, state)
    }
}
