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
import com.duckduckgo.subscriptions.api.Product.DuckAiPlus
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.PRIVACY_SUBSCRIPTIONS_PATH
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.SUBSCRIPTIONS_ETLD
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.SUBSCRIPTIONS_PATH
import com.duckduckgo.subscriptions.impl.internal.SubscriptionsUrlProvider
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.repository.isActiveOrWaiting
import com.duckduckgo.subscriptions.impl.ui.SubscriptionsWebViewActivityWithParams
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Lazy
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealSubscriptions @Inject constructor(
    private val subscriptionsManager: SubscriptionsManager,
    private val globalActivityStarter: GlobalActivityStarter,
    private val pixel: SubscriptionPixelSender,
    private val subscriptionsFeature: Lazy<SubscriptionsFeature>,
    private val dispatcherProvider: DispatcherProvider,
    private val subscriptionsUrlProvider: SubscriptionsUrlProvider,
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
        return subscriptionsManager.entitlements.map { list ->
            withContext(dispatcherProvider.io()) {
                if (subscriptionsFeature.get().duckAiPlus().isEnabled().not()) {
                    list.filterNot { entitlement -> entitlement == DuckAiPlus }
                } else {
                    list
                }
            }
        }
    }

    override suspend fun isEligible(): Boolean {
        val supportsEncryption = subscriptionsManager.canSupportEncryption()
        val isActive = subscriptionsManager.subscriptionStatus().isActiveOrWaiting()
        val isEligible = subscriptionsManager.getSubscriptionOffer().isNotEmpty()

        if (!subscriptionsFeature.get().allowPurchase().isEnabled()) {
            return isActive
        }

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
            .let {
                withContext(dispatcherProvider.io()) {
                    if (subscriptionsFeature.get().duckAiPlus().isEnabled().not()) {
                        it.filterNot { feature -> feature == DuckAiPlus }
                    } else {
                        it
                    }
                }
            }.toSet()
    }

    override fun launchSubscription(context: Context, uri: Uri?) {
        val origin = uri?.getQueryParameter("origin")
        val settings = globalActivityStarter.startIntent(context, SettingsScreenNoParams) ?: return
        val subscriptionIntent = globalActivityStarter.startIntent(
            context,
            SubscriptionsWebViewActivityWithParams(
                url = buildSubscriptionUrl(uri),
                origin = origin,
            ),
        ) ?: return
        val intents: Array<Intent> = listOf(settings, subscriptionIntent).toTypedArray<Intent>()
        intents[0] = Intent(intents[0])
        if (!ContextCompat.startActivities(context, intents)) {
            val topIntent = Intent(intents[intents.size - 1])
            context.startActivity(topIntent)
        }
        pixel.reportSubscriptionRedirect()
    }

    override fun shouldLaunchSubscriptionForUrl(url: String): Boolean {
        return if (isSubscriptionUrl(url.toUri())) {
            runBlocking {
                isEligible()
            }
        } else {
            false
        }
    }

    override fun isSubscriptionUrl(uri: Uri): Boolean {
        val eTld = uri.host?.toTldPlusOne() ?: return false
        val size = uri.pathSegments.size
        val path = uri.pathSegments.firstOrNull()
        return eTld == SUBSCRIPTIONS_ETLD && size == 1 && (path == SUBSCRIPTIONS_PATH || path == PRIVACY_SUBSCRIPTIONS_PATH)
    }

    override suspend fun isFreeTrialEligible(): Boolean {
        return subscriptionsManager.isFreeTrialEligible()
    }

    private fun buildSubscriptionUrl(uri: Uri?): String {
        val queryParams = uri?.query
        return if (!queryParams.isNullOrBlank()) {
            "${subscriptionsUrlProvider.buyUrl}?$queryParams"
        } else {
            subscriptionsUrlProvider.buyUrl
        }
    }
}

@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "privacyPro",
    toggleStore = SubscriptionsFeatureStore::class,
)
interface SubscriptionsFeature {
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun self(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun allowPurchase(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun useUnifiedFeedback(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun serpPromoCookie(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun authApiV2(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun privacyProFreeTrial(): Toggle

    /**
     * Enables/Disables duckAi for subscribers (advanced models)
     * This flag is used to hide the feature in the native client and FE.
     */
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun duckAiPlus(): Toggle

    /**
     * When enabled, we signal FE if v2 is available, enabling v2 messaging
     * When disabled, FE works with old messaging (v1)
     * This flag will be used to select FE subscription messaging mode.
     * The value is added into GetFeatureConfig to allow FE to select the mode.
     * Note: best to remove together with v1 clean up.
     */
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun enableSubscriptionFlowsV2(): Toggle

    /**
     * Kill-switch for in-memory caching of auth v2 JWKs.
     */
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun authApiV2JwksCache(): Toggle

    /**
     * Controls Duck.ai <> subscription JS messaging.
     * Enabled by default.
     * When Disabled, no subscription messaging supported.
     * FF only controls native messaging (enabled/disabled).
     */
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun duckAISubscriptionMessaging(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun refreshSubscriptionPlanFeatures(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun supportsAlternateStripePaymentFlow(): Toggle

    @Toggle.DefaultValue(defaultValue = DefaultFeatureValue.TRUE)
    fun sendSubscriptionPurchaseWideEvent(): Toggle

    @Toggle.DefaultValue(defaultValue = DefaultFeatureValue.TRUE)
    fun sendAuthTokenRefreshWideEvent(): Toggle

    @Toggle.DefaultValue(defaultValue = DefaultFeatureValue.TRUE)
    fun sendSubscriptionSwitchWideEvent(): Toggle

    @Toggle.DefaultValue(defaultValue = DefaultFeatureValue.TRUE)
    fun sendSubscriptionRestoreWideEvent(): Toggle

    @Toggle.DefaultValue(defaultValue = DefaultFeatureValue.FALSE)
    fun blackFridayOffer2025(): Toggle

    @Toggle.DefaultValue(defaultValue = DefaultFeatureValue.TRUE)
    fun handleSubscriptionsWebViewRenderProcessCrash(): Toggle

    @Toggle.DefaultValue(defaultValue = DefaultFeatureValue.TRUE)
    fun sendFreeTrialConversionWideEvent(): Toggle

    /**
     * When enabled, the native app will respond to the getSubscriptionTierOptions message
     * with the new tier-based payload structure supporting Plus/Pro tiers.
     * The flag is exposed to FE via getFeatureConfig.
     */
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun tierMessagingEnabled(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun allowProTierPurchase(): Toggle

    /**
     * When enabled, pending plan hint is displayed to users.
     * When disabled, pending plans hint is not shown (kill switch for pending plans UI).
     */
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun showPendingPlanHint(): Toggle

    /**
     * When enabled, a VPN reminder notification will be scheduled for day 2 of the free trial.
     */
    @Toggle.DefaultValue(defaultValue = DefaultFeatureValue.FALSE)
    fun vpnReminderNotification(): Toggle

    @Toggle.DefaultValue(defaultValue = DefaultFeatureValue.TRUE)
    fun handleExpiredStateWhenSubscriptionChangeSelected(): Toggle

    @Toggle.DefaultValue(defaultValue = DefaultFeatureValue.TRUE)
    fun fetchProTierEntitlements(): Toggle

    @Toggle.DefaultValue(defaultValue = DefaultFeatureValue.TRUE)
    fun schedulePaywallNotSeenPixels(): Toggle
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
@RemoteFeatureStoreNamed(SubscriptionsFeature::class)
class SubscriptionsFeatureStore @Inject constructor(
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
