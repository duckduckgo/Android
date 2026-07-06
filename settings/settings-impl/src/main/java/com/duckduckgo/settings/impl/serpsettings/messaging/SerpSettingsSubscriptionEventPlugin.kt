/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.settings.impl.serpsettings.messaging

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.contentscopescripts.api.ContentScopeScriptsSubscriptionEventPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.duckduckgo.settings.impl.serpsettings.store.SerpSettingsDataStore
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.json.JSONObject
import javax.inject.Inject

/**
 * Pushes the current native SERP settings snapshot to an open SERP via a content-scope subscription event, so a
 * SERP page that is already loaded picks up native changes live, without a full re-bootstrap.
 *
 * This is the native half of the SERP frontend's generic `nativeSync` mechanism (`NativeStorageForSettings`). The
 * `serpSettings` bridge has three messages: `getNativeSettings` (FE→native request, see [GetNativeSettingsHandler]),
 * `updateNativeSettings` (FE→native notify, see [UpdateNativeSettingsHandler]), and `nativeSettingsDidChange`
 * (native→FE subscribe, emitted here).
 *
 * Per the frontend contract, the live channel always emits the **full current state** of every native-synced
 * setting (e.g. `{ "kbe": "...", "kbj": "..." }`) and the receiver reconciles against that snapshot — a key absent
 * from the snapshot is read as that key's default. We therefore push the stored blob verbatim (native only stores
 * the allowlisted nativeSync keys) and never the `{noNativeSettings: true}` form, which is `getNativeSettings`-only.
 *
 * The pipeline is already wired: [BrowserTabViewModel.onViewResumed] iterates every
 * [ContentScopeScriptsSubscriptionEventPlugin] and sends each event via `contentScopeScripts.sendSubscriptionEvent`.
 * That callback is not a suspend function, so we keep a synchronous snapshot of the blob, collected from the store
 * for app lifetime (`@SingleInstanceIn` so there's a single collector). `@Volatile`: written on the IO collector,
 * read on whatever thread the subscription-event fan-out runs on.
 *
 * NOTE: inert until the SERP frontend re-subscribes `nativeSettingsDidChange` (removed in a Nov 2025 refactor, being
 * restored by the frontend `nativeSync` work).
 */
@ContributesMultibinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class SerpSettingsSubscriptionEventPlugin @Inject constructor(
    serpSettingsDataStore: SerpSettingsDataStore,
    dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope appCoroutineScope: CoroutineScope,
) : ContentScopeScriptsSubscriptionEventPlugin {

    @Volatile
    private var cachedBlob: String? = null

    init {
        serpSettingsDataStore.observeSerpSettings()
            .onEach { cachedBlob = it }
            .flowOn(dispatcherProvider.io())
            .launchIn(appCoroutineScope)
    }

    override fun getSubscriptionEventData(): SubscriptionEventData =
        SubscriptionEventData(
            featureName = FEATURE_NAME,
            subscriptionName = SUBSCRIPTION_NAME,
            params = cachedBlob.toSnapshot(),
        )

    // Full-state snapshot of native-owned settings. Empty/malformed storage → empty object (every key absent,
    // which the SERP reconciles as "all defaults"), never a partial diff and never noNativeSettings.
    private fun String?.toSnapshot(): JSONObject {
        if (this.isNullOrEmpty()) return JSONObject()
        return runCatching { JSONObject(this) }.getOrElse { JSONObject() }
    }

    companion object {
        private const val FEATURE_NAME = "serpSettings"
        private const val SUBSCRIPTION_NAME = "nativeSettingsDidChange"
    }
}
