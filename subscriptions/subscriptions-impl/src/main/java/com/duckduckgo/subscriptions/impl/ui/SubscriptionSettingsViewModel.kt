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
import com.duckduckgo.codegen.annotations.ContributesViewModel
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.subscriptions.api.ActiveOfferType
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.SubscriptionUnifiedFeedback
import com.duckduckgo.subscriptions.api.SubscriptionUnifiedFeedback.SubscriptionFeedbackSource.SUBSCRIPTION_SETTINGS
import com.duckduckgo.subscriptions.impl.R
import com.duckduckgo.subscriptions.impl.SubscriptionTier
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.LIST_MONTHLY_PLUS_PLANS
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.LIST_MONTHLY_PRO_PLANS
import com.duckduckgo.subscriptions.impl.SubscriptionsFeature
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.onboarding.SubscriptionOnboardingProgress
import com.duckduckgo.subscriptions.impl.onboarding.experiment.SubscriptionOnboardingExperiments
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.repository.PendingPlan
import com.duckduckgo.subscriptions.impl.repository.Subscription
import com.duckduckgo.subscriptions.impl.store.SubscriptionOnboardingStore
import com.duckduckgo.subscriptions.impl.store.SubscriptionOnboardingStore.Companion.MAX_COMPLETED_ENTRY_POINT_VIEWS
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.DismissWithdrawalDialog
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.FinishSignOut
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.GoToActivationScreen
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.GoToEditEmailScreen
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.GoToPortal
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.GoToWithdrawalHelp
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.LaunchOnboarding
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.ShowWithdrawalDialog
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.Command.ShowWithdrawalDialogError
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.SubscriptionDuration.Monthly
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.SubscriptionDuration.Yearly
import com.duckduckgo.subscriptions.impl.ui.SubscriptionSettingsViewModel.ViewState.Ready
import com.duckduckgo.subscriptions.impl.withdrawal.Refund
import com.duckduckgo.subscriptions.impl.withdrawal.SubscriptionWithdrawal
import com.duckduckgo.subscriptions.impl.withdrawal.WithdrawalEligibility
import com.duckduckgo.subscriptions.impl.withdrawal.WithdrawalResult
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.logcat
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Date
import javax.inject.Inject

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
@ContributesViewModel(ActivityScope::class)
class SubscriptionSettingsViewModel @Inject constructor(
    private val subscriptionsManager: SubscriptionsManager,
    private val pixelSender: SubscriptionPixelSender,
    private val subscriptionUnifiedFeedback: SubscriptionUnifiedFeedback,
    private val subscriptionsFeature: SubscriptionsFeature,
    private val onboardingProgress: SubscriptionOnboardingProgress,
    private val onboardingStore: SubscriptionOnboardingStore,
    private val subscriptionOnboardingExperiments: SubscriptionOnboardingExperiments,
    private val currentTimeProvider: CurrentTimeProvider,
    private val subscriptionWithdrawal: SubscriptionWithdrawal,
) : ViewModel(), DefaultLifecycleObserver {

    private val command = Channel<Command>(1, DROP_OLDEST)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    private val _viewState = MutableStateFlow<ViewState>(ViewState.Loading)
    val viewState = _viewState.asStateFlow()

    private var withdrawalOption: WithdrawalOption? = null
    private var withdrawalEligibilityRequested = false
    private var withdrawalInProgress = false

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
        val monthlyPlans = LIST_MONTHLY_PLUS_PLANS + LIST_MONTHLY_PRO_PLANS
        val type = when (subscription.productId) {
            in monthlyPlans -> Monthly
            else -> Yearly
        }

        // Use firstOrNull() for UI display
        val pendingPlan = if (subscriptionsFeature.showPendingPlanHint().isEnabled()) {
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
        val pendingPlanDisplayNameResId = pendingPlan?.let {
            getPendingPlanDisplayName(it.tier, it.billingPeriod)
        }
        val pendingEffectiveDateShort = pendingPlan?.let {
            DateFormat.getInstanceForSkeleton("MMMMd").format(Date(it.effectiveAt))
        }
        val pendingTierNameResId = pendingPlan?.let {
            getPendingTierNameResId(it.tier)
        }

        val effectiveTier = if (subscriptionsFeature.showPendingPlanHint().isEnabled()) {
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
                showFeedback = subscriptionUnifiedFeedback.shouldUseUnifiedFeedback(source = SUBSCRIPTION_SETTINGS),
                activeOffers = subscription.activeOffers,
                isProTierEnabled = subscriptionsFeature.allowProTierPurchase().isEnabled(),
                subscriptionTier = subscription.tier,
                pendingPlan = pendingPlan,
                pendingEffectiveDate = pendingEffectiveDate,
                isPendingDowngrade = isPendingDowngrade,
                pendingPlanDisplayNameResId = pendingPlanDisplayNameResId,
                pendingEffectiveDateShort = pendingEffectiveDateShort,
                pendingTierNameResId = pendingTierNameResId,
                effectiveTier = effectiveTier,
                onboardingEntryPoint = onboardingEntryPoint(subscription),
                withdrawal = withdrawalOption.takeIf { subscriptionWithdrawal.isWithdrawable(subscription) },
            ),
        )

        loadWithdrawalOptionIfNeeded(subscription)
    }

    private fun completedWithdrawalState(refund: Refund?): CompletedWithdrawalState {
        return CompletedWithdrawalState(
            date = DateFormat.getInstanceForSkeleton("ddMMMMyyyy").format(Date(currentTimeProvider.currentTimeMillis())),
            refundAmount = refund?.let { formatAmount(it.amount, it.currency) },
        )
    }

    private suspend fun loadWithdrawalOptionIfNeeded(subscription: Subscription) {
        if (!subscriptionWithdrawal.isWithdrawable(subscription) || withdrawalEligibilityRequested) return
        withdrawalEligibilityRequested = true

        val eligibility = subscriptionWithdrawal.getEligibility() ?: return
        val option = eligibility.toWithdrawalOption(subscription)
        withdrawalOption = option
        _viewState.update { state -> if (state is Ready) state.copy(withdrawal = option) else state }
    }

    private fun WithdrawalEligibility.toWithdrawalOption(subscription: Subscription): WithdrawalOption {
        return WithdrawalOption(
            deadline = DateFormat.getInstanceForSkeleton("ddMMMMyyyy").format(Date(eligibleUntil)),
            refundAmount = formatAmount(refundAmount, refundCurrency),
            tierNameResId = getPendingTierNameResId(subscription.tier),
        )
    }

    private fun formatAmount(amount: BigDecimal, currencyCode: String): String {
        val code = currencyCode.uppercase()
        return runCatching {
            NumberFormat.getCurrencyInstance().apply { currency = Currency.getInstance(code) }.format(amount)
        }.getOrElse { "${amount.toPlainString()} $code" }
    }

    fun onWithdrawClicked() {
        val option = (_viewState.value as? Ready)?.withdrawal ?: return
        pixelSender.reportSubscriptionSettingsWithdrawalClick()
        pixelSender.reportSubscriptionSettingsWithdrawalDialogShown()
        viewModelScope.launch {
            command.send(ShowWithdrawalDialog(tierNameResId = option.tierNameResId, refundAmount = option.refundAmount))
        }
    }

    fun onWithdrawalConfirmed() {
        if (withdrawalInProgress) return
        withdrawalInProgress = true
        pixelSender.reportSubscriptionSettingsWithdrawalDialogConfirmClick()

        viewModelScope.launch {
            val result = subscriptionWithdrawal.withdraw()
            if (result == WithdrawalResult.Failed) {
                command.send(ShowWithdrawalDialogError)
            } else {
                withdrawalOption = null
                val completedWithdrawal = (result as? WithdrawalResult.Success)?.let { completedWithdrawalState(it.refund) }
                _viewState.update { state ->
                    if (state is Ready) state.copy(withdrawal = null, completedWithdrawal = completedWithdrawal) else state
                }
                command.send(DismissWithdrawalDialog)
            }
            withdrawalInProgress = false
        }
    }

    fun onWithdrawalDialogDismissed() {
        pixelSender.reportSubscriptionSettingsWithdrawalDialogDismissed()
    }

    fun onWithdrawalHelpLinkClicked() {
        viewModelScope.launch { command.send(GoToWithdrawalHelp) }
    }

    private val completedEntryPointAllowed: Boolean by lazy {
        val allowed = onboardingStore.completedEntryPointViews() < MAX_COMPLETED_ENTRY_POINT_VIEWS
        if (allowed) onboardingStore.incrementCompletedEntryPointViews()
        allowed
    }

    private suspend fun onboardingEntryPoint(subscription: Subscription): OnboardingEntryPoint? {
        if (!subscriptionOnboardingExperiments.isTreatment()) return null
        if (!subscription.isActive()) return null

        val percentage = onboardingProgress.completionPercentage()
        return if (percentage >= COMPLETE_PERCENTAGE) {
            if (completedEntryPointAllowed) OnboardingEntryPoint(percentage = COMPLETE_PERCENTAGE) else null
        } else {
            val withinPurchaseWindow =
                currentTimeProvider.currentTimeMillis() - subscription.startedAt <= INCOMPLETE_CARD_WINDOW_MILLIS
            if (withinPurchaseWindow) OnboardingEntryPoint(percentage = percentage) else null
        }
    }

    fun onContinueSetupClicked() {
        viewModelScope.launch { command.send(LaunchOnboarding) }
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
    ): Int {
        return when (tier) {
            SubscriptionTier.PLUS -> {
                if (billingPeriod.equals("monthly", ignoreCase = true)) R.string.planNamePlusMonthly else R.string.planNamePlusYearly
            }
            SubscriptionTier.PRO -> {
                if (billingPeriod.equals("monthly", ignoreCase = true)) R.string.planNameProMonthly else R.string.planNameProYearly
            }
            SubscriptionTier.UNKNOWN -> {
                // fallback
                if (billingPeriod.equals("monthly", ignoreCase = true)) R.string.planNamePlusMonthly else R.string.planNamePlusYearly
            }
        }
    }

    private fun getPendingTierNameResId(tier: SubscriptionTier): Int {
        return when (tier) {
            SubscriptionTier.PLUS -> R.string.planTierNamePlus
            SubscriptionTier.PRO -> R.string.planTierNamePro
            SubscriptionTier.UNKNOWN -> R.string.planTierNamePlus // fallback
        }
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
        data object LaunchOnboarding : Command()
        data class ShowWithdrawalDialog(val tierNameResId: Int, val refundAmount: String) : Command()
        data object GoToWithdrawalHelp : Command()
        data object DismissWithdrawalDialog : Command()
        data object ShowWithdrawalDialogError : Command()
    }

    data class OnboardingEntryPoint(val percentage: Int)

    data class CompletedWithdrawalState(
        val date: String,
        val refundAmount: String?,
    )

    data class WithdrawalOption(
        val deadline: String,
        val refundAmount: String,
        val tierNameResId: Int,
    )

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
            val isProTierEnabled: Boolean,
            val subscriptionTier: SubscriptionTier,
            val pendingPlan: PendingPlan? = null,
            val pendingEffectiveDate: String? = null,
            val isPendingDowngrade: Boolean? = null,
            val pendingPlanDisplayNameResId: Int? = null,
            val pendingEffectiveDateShort: String? = null,
            val pendingTierNameResId: Int? = null,
            val effectiveTier: SubscriptionTier,
            val onboardingEntryPoint: OnboardingEntryPoint? = null,
            val withdrawal: WithdrawalOption? = null,
            val completedWithdrawal: CompletedWithdrawalState? = null,
        ) : ViewState()
    }

    companion object {
        private const val COMPLETE_PERCENTAGE = 100
        private const val INCOMPLETE_CARD_WINDOW_MILLIS = 14L * 24 * 60 * 60 * 1000
    }
}
