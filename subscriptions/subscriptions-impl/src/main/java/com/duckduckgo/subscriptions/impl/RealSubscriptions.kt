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

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.browser.api.ui.BrowserScreens.SettingsScreenNoParams
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.toTldPlusOne
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.RemoteFeatureStoreNamed
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.subscriptions.api.Product
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.BUY_URL
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.PRIVACY_PRO_ETLD
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.PRIVACY_PRO_PATH
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.PRIVACY_SUBSCRIPTIONS_PATH
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.repository.isActiveOrWaiting
import com.duckduckgo.subscriptions.impl.ui.SubscriptionsWebViewActivityWithParams
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@ContributesBinding(AppScope::class)
class RealSubscriptions @Inject constructor(
    private val subscriptionsManager: SubscriptionsManager,
    private val globalActivityStarter: GlobalActivityStarter,
    private val pixel: SubscriptionPixelSender,
) : Subscriptions {
    override suspend fun isSignedIn(): Boolean =
        subscriptionsManager.isSignedIn()

    override suspend fun getAccessToken(): String? {
        return when (val result = subscriptionsManager.getAccessToken()) {
            is AccessTokenResult.Success -> result.accessToken
            is AccessTokenResult.Failure -> null
        }
    }

    override fun getEntitlementStatus(): Flow<List<Product>> {
        return subscriptionsManager.entitlements
    }

    override suspend fun isEligible(): Boolean {
        val supportsEncryption = subscriptionsManager.canSupportEncryption()
        val isActive = subscriptionsManager.subscriptionStatus().isActiveOrWaiting()
        val isEligible = subscriptionsManager.getSubscriptionOffer().isNotEmpty()
        return isActive || (isEligible && supportsEncryption)
    }

    override fun getSubscriptionStatusFlow(): Flow<SubscriptionStatus> {
        return subscriptionsManager.subscriptionStatus
    }

    override suspend fun getSubscriptionStatus(): SubscriptionStatus {
        return subscriptionsManager.subscriptionStatus()
    }

    override suspend fun getAvailableProducts(): Set<Product> {
        return subscriptionsManager.getFeatures()
            .mapNotNull { feature -> Product.entries.firstOrNull { it.value == feature } }
            .toSet()
    }

    override fun launchPrivacyPro(context: Context, uri: Uri?) {
        val origin = uri?.getQueryParameter("origin")
        val settings = globalActivityStarter.startIntent(context, SettingsScreenNoParams) ?: return
        val privacyPro = globalActivityStarter.startIntent(
            context,
            SubscriptionsWebViewActivityWithParams(
                url = buildSubscriptionUrl(uri),
                origin = origin,
            ),
        ) ?: return
        val intents: Array<Intent> = listOf(settings, privacyPro).toTypedArray<Intent>()
        intents[0] = Intent(intents[0])
        if (!ContextCompat.startActivities(context, intents)) {
            val topIntent = Intent(intents[intents.size - 1])
            context.startActivity(topIntent)
        }
        pixel.reportPrivacyProRedirect()
    }

    override fun shouldLaunchPrivacyProForUrl(url: String): Boolean {
        return if (isPrivacyProUrl(url.toUri())) {
            runBlocking {
                isEligible()
            }
        } else {
            false
        }
    }

    override fun isPrivacyProUrl(uri: Uri): Boolean {
        val eTld = uri.host?.toTldPlusOne() ?: return false
        val size = uri.pathSegments.size
        val path = uri.pathSegments.firstOrNull()
        return eTld == PRIVACY_PRO_ETLD && size == 1 && (path == PRIVACY_PRO_PATH || path == PRIVACY_SUBSCRIPTIONS_PATH)
    }

    override suspend fun isFreeTrialEligible(): Boolean {
        return subscriptionsManager.isFreeTrialEligible()
    }

    private fun buildSubscriptionUrl(uri: Uri?): String {
        val queryParams = uri?.query
        return if (!queryParams.isNullOrBlank()) {
            "$BUY_URL?$queryParams"
        } else {
            BUY_URL
        }
    }
}

@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "privacyPro",
    toggleStore = PrivacyProFeatureStore::class,
)
interface PrivacyProFeature {
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun self(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun allowPurchase(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun useUnifiedFeedback(): Toggle

    // Kill switch
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun allowEmailFeedback(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun serpPromoCookie(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun authApiV2(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun isLaunchedROW(): Toggle

    // Kill switch
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun featuresApi(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun privacyProFreeTrial(): Toggle

    /**
     * Enables/Disables duckAi for subscribers (advanced models)
     * This flag is used to hide the feature in the native client and FE.
     * It will be used for the feature rollout and kill-switch if necessary.
     */
    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun duckAiPlus(): Toggle

    /**
     * Android supports v2 token, but still relies on old v1 subscription messaging.
     * We are introducing new JS messaging. Use this flag as kill-switch if necessary.
     * It doesn't control which version of messaging FE uses.
     */
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun enableNewSubscriptionMessages(): Toggle

    /**
     * When enabled, we signal FE if v2 is available, enabling v2 messaging
     * When disabled, FE works with old messaging (v1)
     * This flag will be used to select FE subscription messaging mode.
     * The value is added into GetFeatureConfig to allow FE to select the mode.
     */
    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun enableSubscriptionFlowsV2(): Toggle

    /**
     * Kill-switch for in-memory caching of auth v2 JWKs.
     */
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun authApiV2JwksCache(): Toggle

    /**
     * As part of Duck.ai we are adding new supported JS messages.
     * This is enabled by default, but can be disabled if necessary.
     * FF only controls native messaging (enabled/disabled).
     */
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun duckAISubscriptionMessaging(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun subscriptionRebranding(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun refreshSubscriptionPlanFeatures(): Toggle
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
@RemoteFeatureStoreNamed(PrivacyProFeature::class)
class PrivacyProFeatureStore @Inject constructor(
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val sharedPreferencesProvider: SharedPreferencesProvider,
    moshi: Moshi,
) : Toggle.Store {

    private val preferences: SharedPreferences by lazy {
        // migrate old values to new multiprocess shared prefs
        sharedPreferencesProvider.getSharedPreferences(FILENAME, multiprocess = true, migrate = true)
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
