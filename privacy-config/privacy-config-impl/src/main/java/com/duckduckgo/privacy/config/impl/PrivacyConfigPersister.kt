/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.privacy.config.impl

import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import androidx.core.content.edit
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.experiments.api.VariantManager
import com.duckduckgo.privacy.config.api.PrivacyFeaturePlugin
import com.duckduckgo.privacy.config.impl.VariantManagerPlugin.Companion.VARIANT_MANAGER_FEATURE_NAME
import com.duckduckgo.privacy.config.impl.di.ConfigPersisterPreferences
import com.duckduckgo.privacy.config.impl.models.JsonPrivacyConfig
import com.duckduckgo.privacy.config.store.PrivacyConfig
import com.duckduckgo.privacy.config.store.PrivacyConfigDatabase
import com.duckduckgo.privacy.config.store.PrivacyConfigRepository
import com.duckduckgo.privacy.config.store.PrivacyFeatureTogglesRepository
import com.duckduckgo.privacy.config.store.UnprotectedTemporaryEntity
import com.duckduckgo.privacy.config.store.features.unprotectedtemporary.UnprotectedTemporaryRepository
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import javax.inject.Inject
import javax.inject.Qualifier
import org.threeten.bp.LocalDateTime
import org.threeten.bp.format.DateTimeFormatter
import timber.log.Timber

interface PrivacyConfigPersister {
    suspend fun persistPrivacyConfig(
        jsonPrivacyConfig: JsonPrivacyConfig,
        eTag: String? = null,
    )
}

private const val PRIVACY_SIGNATURE_KEY = "plugin_signature"

@WorkerThread
@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealPrivacyConfigPersister @Inject constructor(
    private val privacyFeaturePluginPoint: PluginPoint<PrivacyFeaturePlugin>,
    @PluginVariantManager private val variantManagerPlugin: PrivacyFeaturePlugin,
    private val privacyFeatureTogglesRepository: PrivacyFeatureTogglesRepository,
    private val unprotectedTemporaryRepository: UnprotectedTemporaryRepository,
    private val privacyConfigRepository: PrivacyConfigRepository,
    private val database: PrivacyConfigDatabase,
    private val listener: PrivacyConfigUpdateListener,
    @ConfigPersisterPreferences private val persisterPreferences: SharedPreferences,
) : PrivacyConfigPersister {

    override suspend fun persistPrivacyConfig(
        jsonPrivacyConfig: JsonPrivacyConfig,
        eTag: String?,
    ) {
        val privacyConfig = privacyConfigRepository.get()
        val newVersion = jsonPrivacyConfig.version
        val previousVersion = privacyConfig?.version ?: 0
        val currentPluginHashCode = privacyFeaturePluginPoint.signature()
        val previousPluginHashCode = persisterPreferences.getSignature()

        val shouldPersist = newVersion > previousVersion || (newVersion == previousVersion && currentPluginHashCode != previousPluginHashCode)

        Timber.v(
            "Should persist privacy config: %s. version=(existing: %s, new: %s), hash=(existing: %s, new: %s)",
            shouldPersist,
            previousVersion,
            newVersion,
            previousPluginHashCode,
            currentPluginHashCode,
        )

        if (shouldPersist) {
            database.runInTransaction {
                persisterPreferences.setSignature(currentPluginHashCode)
                privacyFeatureTogglesRepository.deleteAll()
                privacyConfigRepository.insert(
                    PrivacyConfig(
                        version = jsonPrivacyConfig.version,
                        readme = jsonPrivacyConfig.readme,
                        eTag = eTag,
                        timestamp = FORMATTER_SECONDS.format(
                            LocalDateTime.now(),
                        ),
                    ),
                )
                val unProtectedExceptions = mutableListOf<UnprotectedTemporaryEntity>()
                val unprotectedList = jsonPrivacyConfig.unprotectedTemporary
                unprotectedList.map {
                    unProtectedExceptions.add(UnprotectedTemporaryEntity(it.domain, it.reason.orEmpty()))
                }
                unprotectedTemporaryRepository.updateAll(unProtectedExceptions)
                // First store the variants...
                jsonPrivacyConfig.variantManager?.let { jsonObject ->
                    variantManagerPlugin.store(VARIANT_MANAGER_FEATURE_NAME, jsonObject.toString())
                }
                // Then feature flags
                jsonPrivacyConfig.features.forEach { feature ->
                    feature.value?.let { jsonObject ->
                        privacyFeaturePluginPoint.getPlugins().firstOrNull { feature.key == it.featureName }?.let { featurePlugin ->
                            featurePlugin.store(feature.key, jsonObject.toString())
                        }
                    }
                }
            }
            listener.privacyConfigUpdated()
        }
    }

    private fun SharedPreferences.getSignature(): Int {
        return getInt(PRIVACY_SIGNATURE_KEY, 0)
    }

    private fun SharedPreferences.setSignature(value: Int) {
        edit {
            putInt(PRIVACY_SIGNATURE_KEY, value)
        }
    }

    companion object {
        private val FORMATTER_SECONDS: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    }
}

@VisibleForTesting
fun PluginPoint<PrivacyFeaturePlugin>.signature(): Int {
    return this.getPlugins().sumOf { it.featureName.hashCode() }
}

@Retention(AnnotationRetention.BINARY)
@Qualifier
private annotation class PluginVariantManager

@ContributesTo(AppScope::class)
@Module
object VariantManagerPluginModule {
    @Provides
    @PluginVariantManager
    fun provideVariantManagerPlugin(variantManager: VariantManager): PrivacyFeaturePlugin {
        return VariantManagerPlugin(variantManager)
    }
}
