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

package com.duckduckgo.subscriptions.impl.ui

import android.annotation.SuppressLint
import android.icu.text.DateFormat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.subscriptions.api.ActiveOfferType
import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback
import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback.PrivacyProFeedbackSource.SUBSCRIPTION_SETTINGS
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.impl.PrivacyProFeature
import com.duckduckgo.subscriptions.impl.SubscriptionTier
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.MONTHLY_PLAN_ROW
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.MONTHLY_PLAN_US
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.repository.PendingPlan
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.FinishSignOut
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.GoToActivationScreen
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.GoToEditEmailScreen
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.GoToPortal
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.ShowSwitchPlanDialog
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.SubscriptionDuration.Monthly
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.SubscriptionDuration.Yearly
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.ViewState.Ready
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import logcat.logcat
import java.util.Date
import javax.inject.Inject

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
@ContributesViewModel(ActivityScope::class)
class SubscriptionSettingsViewModel @Inject constructor(
    private val subscriptionsManager: SubscriptionsManager,
    private val pixelSender: SubscriptionPixelSender,
    private val privacyProUnifiedFeedback: PrivacyProUnifiedFeedback,
    private val privacyProFeature: PrivacyProFeature,
) : ViewModel(), DefaultLifecycleObserver {

    private val command = Channel<Command>(1, DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    private val _viewState = MutableStateFlow<ViewState>(ViewState.Loading)
    val viewState = _viewState.asStateFlow()

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)

        subscriptionsManager.subscriptionStatus
            .distinctUntilChanged()
            .onEach {
                emitChanges()
            }.launchIn(viewModelScope)
    }

    override fun onResume(owner: LifecycleOwner) {
        viewModelScope.launch { emitChanges() }
    }

    private suspend fun emitChanges() {
        val account = subscriptionsManager.getAccount() ?: return
        val subscription = subscriptionsManager.getSubscription() ?: return
        logcat {
            "SubscriptionSettingsViewModel: Subscription found with tier ${subscription.tier}"
        }

        val formatter = DateFormat.getInstanceForSkeleton("ddMMMMyyyy")
        val date = formatter.format(Date(subscription.expiresOrRenewsAt))
        val type = when (subscription.productId) {
            MONTHLY_PLAN_US, MONTHLY_PLAN_ROW -> Monthly
            else -> Yearly
        }

        val switchPlanAvailable = subscriptionsManager.isSwitchPlanAvailable()
        val savingsPercentage = if (switchPlanAvailable && type == Monthly) {
            subscriptionsManager.getSwitchPlanPricing(isUpgrade = true)?.savingsPercentage
        } else {
            null
        }

        // Use firstOrNull() for UI display
        val pendingPlan = if (privacyProFeature.showPendingPlanHint().isEnabled()) {
            subscription.pendingPlans.firstOrNull()
        } else {
            null
        }

        val pendingEffectiveDate = pendingPlan?.let {
            formatter.format(Date(it.effectiveAt))
        }
        val isPendingDowngrade = pendingPlan?.let {
            isPendingPlanDowngrade(
                currentTier = subscription.tier,
                currentDuration = type,
                pendingTier = it.tier,
                pendingBillingPeriod = it.billingPeriod,
            )
        }
        val pendingPlanDisplayName = pendingPlan?.let {
            getPendingPlanDisplayName(it.tier, it.billingPeriod)
        }

        val effectiveTier = if (privacyProFeature.showPendingPlanHint().isEnabled()) {
            pendingPlan?.tier ?: subscription.tier
        } else {
            subscription.tier
        }

        _viewState.emit(
            Ready(
                date = date,
                duration = type,
                status = subscription.status,
                platform = subscription.platform,
                email = account.email?.takeUnless { it.isBlank() },
                showFeedback = privacyProUnifiedFeedback.shouldUseUnifiedFeedback(source = SUBSCRIPTION_SETTINGS),
                activeOffers = subscription.activeOffers,
                switchPlanAvailable = switchPlanAvailable,
                savingsPercentage = savingsPercentage,
                isProTierEnabled = privacyProFeature.allowProTierPurchase().isEnabled(),
                subscriptionTier = subscription.tier,
                pendingPlan = pendingPlan,
                pendingEffectiveDate = pendingEffectiveDate,
                isPendingDowngrade = isPendingDowngrade,
                pendingPlanDisplayName = pendingPlanDisplayName,
                effectiveTier = effectiveTier,
            ),
        )
    }

    fun onEditEmailButtonClicked() {
        viewModelScope.launch {
            command.send(GoToEditEmailScreen)
        }
    }

    fun onAddToDeviceButtonClicked() {
        viewModelScope.launch {
            command.send(GoToActivationScreen)
        }
    }

    fun goToStripe() {
        viewModelScope.launch {
            val url = subscriptionsManager.getPortalUrl() ?: return@launch
            command.send(GoToPortal(url))
        }
    }

    fun removeFromDevice() {
        pixelSender.reportSubscriptionSettingsRemoveFromDeviceClick()

        viewModelScope.launch {
            subscriptionsManager.signOut()
            command.send(FinishSignOut)
        }
    }

    fun onSwitchPlanClicked(currentDuration: SubscriptionDuration) {
        viewModelScope.launch {
            val switchType = when (currentDuration) {
                Monthly -> SwitchPlanType.UPGRADE_TO_YEARLY
                Yearly -> SwitchPlanType.DOWNGRADE_TO_MONTHLY
            }
            command.send(ShowSwitchPlanDialog(switchType))
        }
    }

    fun onSwitchPlanSuccess() {
        viewModelScope.launch {
            emitChanges()
        }
    }

    private fun isPendingPlanDowngrade(
        currentTier: SubscriptionTier,
        currentDuration: SubscriptionDuration,
        pendingTier: SubscriptionTier,
        pendingBillingPeriod: String,
    ): Boolean {
        // Tier downgrade: PRO -> PLUS
        if (currentTier == SubscriptionTier.PRO && pendingTier == SubscriptionTier.PLUS) {
            return true
        }
        // Tier upgrade: PLUS -> PRO
        if (currentTier == SubscriptionTier.PLUS && pendingTier == SubscriptionTier.PRO) {
            return false
        }
        // Same tier - check billing period: Yearly -> Monthly is downgrade
        // This should not happen as replacement mode is not deferred within same product, but we handle it just in case
        val isPendingMonthly = pendingBillingPeriod.equals("monthly", ignoreCase = true)
        return currentDuration == Yearly && isPendingMonthly
    }

    private fun getPendingPlanDisplayName(
        tier: SubscriptionTier,
        billingPeriod: String,
    ): String {
        val tierName = when (tier) {
            SubscriptionTier.PLUS -> "Plus"
            SubscriptionTier.PRO -> "Pro"
            SubscriptionTier.UNKNOWN -> "Plus" // fallback
        }
        val periodName = if (billingPeriod.equals("monthly", ignoreCase = true)) "Monthly" else "Yearly"
        return "$tierName $periodName"
    }

    sealed class SubscriptionDuration {
        data object Monthly : SubscriptionDuration()
        data object Yearly : SubscriptionDuration()
    }

    sealed class Command {
        data object FinishSignOut : Command()
        data object GoToEditEmailScreen : Command()
        data object GoToActivationScreen : Command()
        data class GoToPortal(val url: String) : Command()
        data class ShowSwitchPlanDialog(val switchType: SwitchPlanType) : Command()
    }

    enum class SwitchPlanType {
        UPGRADE_TO_YEARLY,
        DOWNGRADE_TO_MONTHLY,
    }

    sealed class ViewState {
        data object Loading : ViewState()

        data class Ready(
            val date: String,
            val duration: SubscriptionDuration,
            val status: SubscriptionStatus,
            val platform: String,
            val email: String?,
            val showFeedback: Boolean = false,
            val activeOffers: List<ActiveOfferType>,
            val switchPlanAvailable: Boolean,
            val savingsPercentage: Int?,
            val isProTierEnabled: Boolean,
            val subscriptionTier: SubscriptionTier,
            val pendingPlan: PendingPlan? = null,
            val pendingEffectiveDate: String? = null,
            val isPendingDowngrade: Boolean? = null,
            val pendingPlanDisplayName: String? = null,
            val effectiveTier: SubscriptionTier,
        ) : ViewState()
    }
}
