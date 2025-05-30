/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.common.ui.experiments.visual.store

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.ui.experiments.visual.ExperimentalUIThemingFeature
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Original reason for creating this lazy provider is in https://app.asana.com/1/137249556945/task/1210355090527279/comment/1210422281011749?focus=true.
 *
 * Provides [VisualDesignExperimentDataStoreImpl] with an option to initialize it on a background thread
 * to avoid blocking the main thread during app startup.
 *
 * The data store performs synchronous checks of feature flags and experiments during initialization
 * to correctly set up its state flows. This is essential, as the state is used to determine the app’s main
 * theme and must be ready before any themeable activity launches to prevent flickering or incorrect
 * theming.
 *
 * Previously, the first accessor (e.g., Dagger initializer or theme manager) would block the main
 * thread while the store initialized. Offloading this work to a worker thread eliminates that risk.
 *
 * This provider is used in [com.duckduckgo.app.launch.LaunchBridgeActivity] to trigger initialization
 * while the splash screen is shown, ensuring the store is ready for synchronous access afterward.
 *
 * Access before the splash screen ends is not expected. A debug-only assertion guards
 * against premature access, allowing issues to be caught during development without impacting release builds
 * (in case there are flows we've not considered or tested yet but can happen in production).
 */
@ContributesBinding(
    scope = AppScope::class,
    boundType = VisualDesignExperimentDataStore::class,
)
@ContributesBinding(
    scope = AppScope::class,
    boundType = VisualDesignExperimentDataStoreInitializer::class,
)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PrivacyConfigCallbackPlugin::class,
)
@SingleInstanceIn(scope = AppScope::class)
class VisualDesignExperimentDataStoreLazyProvider @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val experimentalUIThemingFeature: ExperimentalUIThemingFeature,
    private val featureTogglesInventory: FeatureTogglesInventory,
    private val dispatcherProvider: DispatcherProvider,
    private val visualDesignExperimentDataStoreImplFactory: VisualDesignExperimentDataStoreImplFactory,
) : VisualDesignExperimentDataStoreInitializer, VisualDesignExperimentDataStore, PrivacyConfigCallbackPlugin {

    private val initMutex = Mutex()

    private var _store: VisualDesignExperimentDataStoreImpl? = null

    private val store: VisualDesignExperimentDataStore
        get() {
            val ref = _store
            assert(ref != null) { "VisualDesignExperimentDataStore is not initialized." }
            return ref ?: runBlocking { initialize() }
        }

    override suspend fun initialize(): VisualDesignExperimentDataStore = initMutex.withLock {
        suspend fun createStore(): VisualDesignExperimentDataStoreImpl = withContext(dispatcherProvider.io()) {
            visualDesignExperimentDataStoreImplFactory.create(
                appCoroutineScope = appCoroutineScope,
                experimentalUIThemingFeature = experimentalUIThemingFeature,
                featureTogglesInventory = featureTogglesInventory,
            )
        }

        _store ?: createStore().also { _store = it }
    }

    override fun onPrivacyConfigDownloaded() {
        // store fetches latest flags on initialization, so updates are only needed if the store has already been initialized before
        _store?.onPrivacyConfigDownloaded()
    }

    override val isExperimentEnabled: StateFlow<Boolean>
        get() = store.isExperimentEnabled
    override val isDuckAIPoCEnabled: StateFlow<Boolean>
        get() = store.isDuckAIPoCEnabled
    override val anyConflictingExperimentEnabled: StateFlow<Boolean>
        get() = store.anyConflictingExperimentEnabled
    override fun changeExperimentFlagPreference(enabled: Boolean) = store.changeExperimentFlagPreference(enabled)
    override fun changeDuckAIPoCFlagPreference(enabled: Boolean) = store.changeDuckAIPoCFlagPreference(enabled)
}
