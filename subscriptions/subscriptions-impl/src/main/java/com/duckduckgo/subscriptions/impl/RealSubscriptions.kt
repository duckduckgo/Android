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

package com.duckduckgo.subscriptions.impl

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.RemoteFeatureStoreNamed
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.mobile.android.vpn.prefs.VpnSharedPreferencesProvider
import com.duckduckgo.subscriptions.api.Product
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.repository.isActiveOrWaiting
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@ContributesBinding(AppScope::class)
class RealSubscriptions @Inject constructor(
    private val subscriptionsManager: SubscriptionsManager,
    private val privacyProFeature: PrivacyProFeature,
    private val subscriptionPixelSender: SubscriptionPixelSender,
) : Subscriptions {
    override suspend fun getAccessToken(): String? {
        if (!isEnabled()) return null

        return when (val result = subscriptionsManager.getAccessToken()) {
            is AccessToken.Success -> result.accessToken
            is AccessToken.Failure -> null
        }
    }

    override fun getEntitlementStatus(): Flow<List<Product>> {
        return subscriptionsManager.entitlements.map {
            if (!isEnabled()) emptyList() else it
        }
    }

    override suspend fun isEnabled(): Boolean {
        return privacyProFeature.isLaunched().isEnabled().also { enabled ->
            if (enabled) {
                subscriptionPixelSender.reportSubscriptionIsEnabled()
            }
        }
    }

    override suspend fun isEligible(): Boolean {
        val supportsEncryption = subscriptionsManager.canSupportEncryption()
        val isActive = subscriptionsManager.subscriptionStatus().isActiveOrWaiting()
        val isEligible = subscriptionsManager.getSubscriptionOffer() != null
        return isActive || (isEligible && supportsEncryption)
    }
}

@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "privacyPro",
    toggleStore = PrivacyProFeatureStore::class,
)
interface PrivacyProFeature {
    @Toggle.DefaultValue(false)
    fun self(): Toggle

    @Toggle.DefaultValue(false)
    fun isLaunched(): Toggle

    @Toggle.DefaultValue(false)
    fun allowPurchase(): Toggle
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
@RemoteFeatureStoreNamed(PrivacyProFeature::class)
class PrivacyProFeatureStore @Inject constructor(
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val vpnSharedPreferencesProvider: VpnSharedPreferencesProvider,
    moshi: Moshi,
) : Toggle.Store {

    private val preferences: SharedPreferences by lazy {
        // migrate old values to new multiprocess shared prefs
        vpnSharedPreferencesProvider.getSharedPreferences(FILENAME, multiprocess = true, migrate = true)
    }
    private val stateAdapter: JsonAdapter<State> by lazy {
        moshi.newBuilder().add(KotlinJsonAdapterFactory()).build().adapter(State::class.java)
    }

    override fun set(
        key: String,
        state: State,
    ) {
        preferences.save(key, state)
    }

    override fun get(key: String): State? {
        return preferences.getString(key, null)?.let {
            stateAdapter.fromJson(it)
        }
    }

    private fun SharedPreferences.save(key: String, state: State) {
        coroutineScope.launch(dispatcherProvider.io()) {
            edit(commit = true) { putString(key, stateAdapter.toJson(state)) }
        }
    }

    companion object {
        // This is the backwards compatible value
        const val FILENAME = "com.duckduckgo.feature.toggle.privacyPro"
    }
}
