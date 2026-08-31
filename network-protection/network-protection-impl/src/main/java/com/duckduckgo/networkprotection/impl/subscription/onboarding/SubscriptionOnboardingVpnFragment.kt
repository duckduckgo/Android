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

package com.duckduckgo.networkprotection.impl.subscription.onboarding

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.spans.DuckDuckGoClickableSpan
import com.duckduckgo.common.ui.view.addClickableSpan
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.networkprotection.impl.R
import com.duckduckgo.networkprotection.impl.databinding.FragmentSubscriptionOnboardingVpnBinding
import com.duckduckgo.networkprotection.impl.subscription.onboarding.SubscriptionOnboardingVpnViewModel.Command
import com.duckduckgo.networkprotection.impl.subscription.onboarding.SubscriptionOnboardingVpnViewModel.VPNActivationError
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingFeature
import com.duckduckgo.subscriptions.api.SubscriptionScreens.SubscriptionOnboardingFeatureInfoScreen
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec
import com.google.android.material.progressindicator.IndeterminateDrawable
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

@InjectWith(FragmentScope::class)
class SubscriptionOnboardingVpnFragment : DuckDuckGoFragment(R.layout.fragment_subscription_onboarding_vpn) {

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    private val binding: FragmentSubscriptionOnboardingVpnBinding by viewBinding()

    private val viewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[SubscriptionOnboardingVpnViewModel::class.java]
    }

    private val vpnPermissionRequest = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onVpnPermissionGranted()
        } else {
            viewModel.onVpnPermissionDenied()
        }
    }

    private var vpnOn = false
    private var showingInfo = false
    private var lastRenderedState: ScreenState? = null
    private var transition: ValueAnimator? = null

    private val buttonSpinner: IndeterminateDrawable<CircularProgressIndicatorSpec> by lazy {
        val density = resources.displayMetrics.density
        val spec = CircularProgressIndicatorSpec(requireContext(), null).apply {
            indicatorSize = (BUTTON_SPINNER_SIZE_DP * density).toInt()
            trackThickness = (BUTTON_SPINNER_THICKNESS_DP * density).toInt()
            indicatorColors = intArrayOf(binding.subscriptionOnboardingVpnNextButton.currentTextColor)
        }
        IndeterminateDrawable.createCircularDrawable(requireContext(), spec)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lastRenderedState = null
        applyState(ScreenState.VPN_OFF, animate = false)
        observeViewState()
        observeCommands()
        binding.subscriptionOnboardingVpnNextButton.setOnClickListener {
            viewModel.onPrimaryCtaClicked()
        }
        binding.subscriptionOnboardingVpnSkipButton.setOnClickListener {
            viewModel.onSkipClicked()
        }
    }

    override fun onDestroyView() {
        transition?.cancel()
        transition = null
        super.onDestroyView()
    }

    private fun enableVpn() {
        val permissionIntent = VpnService.prepare(requireContext())
        if (permissionIntent == null) {
            viewModel.onVpnPermissionGranted()
        } else {
            vpnPermissionRequest.launch(permissionIntent)
        }
    }

    private fun setHeaderTextWithLearnMore(@StringRes textResId: Int) {
        binding.subscriptionOnboardingVpnHeaderText.addClickableSpan(
            getText(textResId),
            spans = listOf(
                "learn_more_link" to object : DuckDuckGoClickableSpan() {
                    override fun onClick(widget: View) {
                        globalActivityStarter.start(
                            requireContext(),
                            SubscriptionOnboardingFeatureInfoScreen(SubscriptionOnboardingFeature.VPN),
                        )
                    }
                },
            ),
        )
    }

    private fun observeViewState() {
        viewModel.viewState()
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach { state ->
                state.ipAddress?.let { binding.subscriptionOnboardingVpnIpAddressValue.text = it }
                state.location?.let { binding.subscriptionOnboardingVpnIpAddressLocation.text = it }
                state.newIpAddress?.let { binding.subscriptionOnboardingVpnNewIpAddressValue.text = it }
                state.newLocation?.let { binding.subscriptionOnboardingVpnNewIpAddressLocation.text = it }

                if (state.showingVPNInfoBanners) {
                    if (!showingInfo) showInfoPage()
                } else {
                    state.toScreenState()?.let(::renderScreenState)
                }
                renderActivating(state.activating)
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun observeCommands() {
        viewModel.commands
            .flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
            .onEach { command ->
                when (command) {
                    Command.RequestVpnPermission -> enableVpn()
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun renderActivating(loading: Boolean) {
        binding.subscriptionOnboardingVpnNextButton.icon = if (loading) buttonSpinner else null
    }

    private fun SubscriptionOnboardingVpnViewModel.ViewState.toScreenState(): ScreenState? = when {
        vpnEnabled == true -> ScreenState.VPN_ON
        vpnActivationError == VPNActivationError.PERMISSION_DENIED -> ScreenState.ACTIVATION_ERROR_PERMISSION
        vpnActivationError == VPNActivationError.CONNECTION_FAILED -> ScreenState.ACTIVATION_ERROR_GENERAL
        vpnEnabled == false -> ScreenState.VPN_OFF
        else -> null
    }

   private fun renderScreenState(state: ScreenState) {
        if (state == lastRenderedState) return
        val wasOn = lastRenderedState == ScreenState.VPN_ON
        val isOn = state == ScreenState.VPN_ON
        val animate = lastRenderedState != null && wasOn != isOn
        lastRenderedState = state
        applyState(state, animate)
    }

    private fun showInfoPage() = with(binding) {
        showingInfo = true
        transition?.cancel()
        subscriptionOnboardingVpnHeaderImage.setImageResource(R.drawable.vpn_lock_feature_128)
        subscriptionOnboardingVpnHeaderTitle.setText(R.string.subscriptionOnboardingVpnInfoTitle)
        subscriptionOnboardingVpnStatusContent.gone()
        subscriptionOnboardingVpnInfoContent.show()
        subscriptionOnboardingVpnNextButton.icon = null
        subscriptionOnboardingVpnNextButton.setText(R.string.subscriptionOnboardingVpnInfoGotIt)
        subscriptionOnboardingVpnSkipButton.gone()
    }

    private fun applyState(state: ScreenState, animate: Boolean) = with(binding) {
        vpnOn = state == ScreenState.VPN_ON
        subscriptionOnboardingVpnStatusContent.show()
        subscriptionOnboardingVpnInfoContent.gone()

        when (state) {
            ScreenState.VPN_ON -> {
                subscriptionOnboardingVpnHeaderImage.setImageResource(R.drawable.vpn_lock_feature_128)
                subscriptionOnboardingVpnHeaderTitle.setText(R.string.subscriptionOnboardingVpnHeaderTitleOn)
                subscriptionOnboardingVpnHeaderText.show()
                setHeaderTextWithLearnMore(R.string.subscriptionOnboardingVpnHeaderTextOn)
                subscriptionOnboardingVpnNextButton.setText(R.string.subscriptionOnboardingVpnNext)
                subscriptionOnboardingVpnSkipButton.gone()
            }

            ScreenState.VPN_OFF -> {
                subscriptionOnboardingVpnHeaderImage.setImageResource(R.drawable.vpn_disabled_feature_128)
                subscriptionOnboardingVpnHeaderTitle.setText(R.string.subscriptionOnboardingVpnHeaderTitle)
                subscriptionOnboardingVpnHeaderText.show()
                setHeaderTextWithLearnMore(R.string.subscriptionOnboardingVpnHeaderText)
                subscriptionOnboardingVpnNextButton.setText(R.string.subscriptionOnboardingVpnTurnOn)
                subscriptionOnboardingVpnSkipButton.gone()
            }

            ScreenState.ACTIVATION_ERROR_PERMISSION -> {
                subscriptionOnboardingVpnHeaderImage.setImageResource(R.drawable.critical_update_feature_128)
                subscriptionOnboardingVpnHeaderTitle.setText(R.string.subscriptionOnboardingVpnErrorTitle)
                subscriptionOnboardingVpnHeaderText.show()
                subscriptionOnboardingVpnHeaderText.setText(R.string.subscriptionOnboardingVpnErrorText)
                subscriptionOnboardingVpnNextButton.setText(R.string.subscriptionOnboardingVpnTryAgain)
                subscriptionOnboardingVpnSkipButton.show()
            }

            ScreenState.ACTIVATION_ERROR_GENERAL -> {
                subscriptionOnboardingVpnHeaderImage.setImageResource(R.drawable.critical_update_feature_128)
                subscriptionOnboardingVpnHeaderTitle.setText(R.string.subscriptionOnboardingVpnErrorTitle)
                subscriptionOnboardingVpnHeaderText.gone()
                subscriptionOnboardingVpnNextButton.setText(R.string.subscriptionOnboardingVpnTryAgain)
                subscriptionOnboardingVpnSkipButton.show()
            }
        }

        if (vpnOn) {
            subscriptionOnboardingVpnIpAddressTitle.setText(R.string.subscriptionOnboardingVpnIpAddressTitleOn)
            subscriptionOnboardingVpnNewIpAddressContainer.show()
            subscriptionOnboardingVpnIpAddressInfo.gone()
        } else {
            subscriptionOnboardingVpnIpAddressTitle.setText(R.string.subscriptionOnboardingVpnIpAddressTitle)
            subscriptionOnboardingVpnNewIpAddressContainer.gone()
            subscriptionOnboardingVpnIpAddressInfo.show()
        }

        val benefitIcon = if (vpnOn) R.drawable.check_circle_color_24 else R.drawable.alert_recolorable_24

        transition?.cancel()
        if (animate) {
            animateBenefitsAndBlur(benefitIcon, vpnOn)
        } else {
            setBenefitIcons(benefitIcon)
            benefitRows().forEach { row ->
                row.alpha = 1f
                row.translationX = 0f
            }
            applyBlurInstant(vpnOn)
        }
    }

    private fun animateBenefitsAndBlur(@DrawableRes benefitIcon: Int, redacted: Boolean) = with(binding) {
        val rows = benefitRows()
        val slide = rows.firstOrNull { it.width > 0 }?.width?.toFloat()
            ?: resources.displayMetrics.widthPixels.toFloat()
        var cancelled = false

        setBenefitIcons(benefitIcon)
        rows.forEach { row ->
            row.alpha = 0f
            row.translationX = -slide
        }

        if (Build.VERSION.SDK_INT < 31) applyBlurInstant(redacted)

        transition = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = TRANSITION_DURATION_MS
            addUpdateListener {
                val fraction = it.animatedFraction
                if (Build.VERSION.SDK_INT >= 31) {
                    val radius = (if (redacted) fraction else 1f - fraction) * BLUR_RADIUS
                    subscriptionOnboardingVpnIpAddressValue.blur(radius)
                    subscriptionOnboardingVpnIpAddressLocation.blur(radius)
                }
                rows.forEach { row ->
                    row.alpha = fraction
                    row.translationX = -slide * (1f - fraction)
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (cancelled) return
                    rows.forEach { row ->
                        row.alpha = 1f
                        row.translationX = 0f
                    }
                    if (Build.VERSION.SDK_INT >= 31) {
                        subscriptionOnboardingVpnIpAddressValue.blur(if (redacted) BLUR_RADIUS else 0f)
                        subscriptionOnboardingVpnIpAddressLocation.blur(if (redacted) BLUR_RADIUS else 0f)
                    }
                }
            })
            start()
        }
    }

    private fun applyBlurInstant(redacted: Boolean) = with(binding) {
        if (Build.VERSION.SDK_INT >= 31) {
            subscriptionOnboardingVpnIpAddressValue.blur(if (redacted) BLUR_RADIUS else 0f)
            subscriptionOnboardingVpnIpAddressLocation.blur(if (redacted) BLUR_RADIUS else 0f)
        } else {
            subscriptionOnboardingVpnIpAddressValue.redactWithBar(redacted, CommonR.attr.daxColorPrimaryText)
            subscriptionOnboardingVpnIpAddressLocation.redactWithBar(redacted, CommonR.attr.daxColorSecondaryText)
        }
    }

    private fun setBenefitIcons(@DrawableRes iconRes: Int) {
        benefitIcons().forEach { it.setImageResource(iconRes) }
    }

    private fun benefitRows(): List<View> = with(binding) {
        listOf(
            subscriptionOnboardingVpnBenefitShielding,
            subscriptionOnboardingVpnBenefitLocation,
            subscriptionOnboardingVpnBenefitHarmful,
        )
    }

    private fun benefitIcons(): List<ImageView> = with(binding) {
        listOf(
            subscriptionOnboardingVpnBenefitShieldingIcon,
            subscriptionOnboardingVpnBenefitLocationIcon,
            subscriptionOnboardingVpnBenefitHarmfulIcon,
        )
    }

    @RequiresApi(31)
    private fun TextView.blur(radius: Float) {
        setRenderEffect(
            if (radius <= 0.5f) null else RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP),
        )
    }

    private fun TextView.redactWithBar(redacted: Boolean, @AttrRes restoreColorAttr: Int) {
        if (redacted) {
            setBackgroundResource(R.drawable.subscription_onboarding_vpn_ip_redaction)
            setTextColor(Color.TRANSPARENT)
        } else {
            background = null
            setTextColor(context.getColorFromAttr(restoreColorAttr))
        }
    }

    private enum class ScreenState {
        VPN_OFF,
        VPN_ON,
        ACTIVATION_ERROR_PERMISSION,
        ACTIVATION_ERROR_GENERAL
    }

    companion object {
        private const val TRANSITION_DURATION_MS = 1000L
        private const val BLUR_RADIUS = 12f
        private const val BUTTON_SPINNER_SIZE_DP = 20
        private const val BUTTON_SPINNER_THICKNESS_DP = 2
    }
}
