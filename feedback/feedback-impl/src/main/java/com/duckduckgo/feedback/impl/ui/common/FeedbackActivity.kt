/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.feedback.impl.ui.common

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.transaction
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.hideKeyboard
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeBucket
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.feedback.api.FeedbackScreenNoParams
import com.duckduckgo.feedback.impl.R
import com.duckduckgo.feedback.impl.databinding.ActivityShareFeedbackBinding
import com.duckduckgo.feedback.impl.ui.initial.InitialFeedbackFragment
import com.duckduckgo.feedback.impl.ui.negative.FeedbackType.*
import com.duckduckgo.feedback.impl.ui.negative.brokensite.BrokenSiteNegativeFeedbackFragment
import com.duckduckgo.feedback.impl.ui.negative.mainreason.MainReasonNegativeFeedbackFragment
import com.duckduckgo.feedback.impl.ui.negative.openended.ShareOpenEndedFeedbackFragment
import com.duckduckgo.feedback.impl.ui.negative.subreason.SubReasonNegativeFeedbackFragment
import com.duckduckgo.feedback.impl.ui.positive.initial.PositiveFeedbackLandingFragment
import logcat.LogPriority.VERBOSE
import logcat.logcat
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(FeedbackScreenNoParams::class, screenName = "feedback")
class FeedbackActivity :
    DuckDuckGoActivity(),
    InitialFeedbackFragment.InitialFeedbackListener,
    PositiveFeedbackLandingFragment.PositiveFeedbackLandingListener,
    ShareOpenEndedFeedbackFragment.OpenEndedFeedbackListener,
    MainReasonNegativeFeedbackFragment.MainReasonNegativeFeedbackListener,
    BrokenSiteNegativeFeedbackFragment.BrokenSiteFeedbackListener,
    SubReasonNegativeFeedbackFragment.DisambiguationNegativeFeedbackListener {

    private val viewModel: FeedbackViewModel by bindViewModel()

    private val binding: ActivityShareFeedbackBinding by viewBinding()

    private val toolbar
        get() = binding.includeToolbar.toolbar

    @Inject
    lateinit var edgeToEdgeProvider: EdgeToEdgeProvider

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val edgeToEdgeEnabled = edgeToEdgeProvider.isEnabled(EdgeToEdgeBucket.MISC)
        if (edgeToEdgeEnabled) {
            enableTransparentEdgeToEdge()
        }
        setContentView(binding.root)
        setupToolbar(toolbar)
        if (edgeToEdgeEnabled) {
            configureEdgeToEdgeInsets()
        }
        configureObservers()
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    viewModel.onBackPressed()
                }
            },
        )
    }

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
        edgeToEdgeHandler.applyStatusBarInsets(binding.includeToolbar.appBarLayout)
        edgeToEdgeHandler.applyNavigationBarInsets(binding.fragmentContainer, drawBehindGestureNav = false)
    }

    private fun configureObservers() {
        viewModel.command.observe(this) {
            it?.let { command -> processCommand(command) }
        }
        viewModel.updateViewCommand.observe(this) {
            it?.let { viewState -> render(viewState) }
        }
    }

    private fun processCommand(command: Command) {
        logcat(VERBOSE) { "Processing command: $command" }

        when (command) {
            is Command.Exit -> animateFinish(command.feedbackSubmitted)
            is Command.HideKeyboard -> hideKeyboard()
        }
    }

    private fun render(viewState: UpdateViewCommand) {
        logcat(VERBOSE) { "ViewState is: $viewState" }

        when (val state = viewState.fragmentViewState) {
            is FragmentState.InitialAppEnjoymentClarifier -> showInitialFeedbackView(state.forwardDirection)
            is FragmentState.PositiveFeedbackFirstStep -> showPositiveFeedbackView(state.forwardDirection)
            is FragmentState.PositiveShareFeedback -> showSharePositiveFeedbackView(state.forwardDirection)
            is FragmentState.NegativeFeedbackMainReason -> showNegativeFeedbackMainReasonView(state.forwardDirection)
            is FragmentState.NegativeFeedbackSubReason -> showNegativeFeedbackSubReasonView(state.forwardDirection, state.mainReason)
            is FragmentState.NegativeOpenEndedFeedback -> showNegativeOpenEndedFeedbackView(state.forwardDirection, state.mainReason, state.subReason)
            is FragmentState.NegativeWebSitesBrokenFeedback -> showNegativeWebSiteBrokenView(state.forwardDirection)
        }
    }

    private fun showSharePositiveFeedbackView(forwardDirection: Boolean) {
        val fragment = ShareOpenEndedFeedbackFragment.instancePositiveFeedback()
        updateFragment(fragment, forwardDirection)
    }

    private fun showNegativeFeedbackMainReasonView(forwardDirection: Boolean) {
        val fragment = MainReasonNegativeFeedbackFragment.instance()
        updateFragment(fragment, forwardDirection)
    }

    private fun showNegativeFeedbackSubReasonView(
        forwardDirection: Boolean,
        mainReason: MainReason,
    ) {
        val fragment = SubReasonNegativeFeedbackFragment.instance(mainReason)
        updateFragment(fragment, forwardDirection)
    }

    private fun showNegativeOpenEndedFeedbackView(
        forwardDirection: Boolean,
        mainReason: MainReason,
        subReason: SubReason? = null,
    ) {
        val fragment = ShareOpenEndedFeedbackFragment.instanceNegativeFeedback(mainReason, subReason)
        updateFragment(fragment, forwardDirection)
    }

    private fun showInitialFeedbackView(forwardDirection: Boolean) {
        val fragment = InitialFeedbackFragment.instance()
        updateFragment(fragment, forwardDirection)
    }

    private fun showPositiveFeedbackView(forwardDirection: Boolean) {
        val fragment = PositiveFeedbackLandingFragment.instance()
        updateFragment(fragment, forwardDirection)
    }

    private fun showNegativeWebSiteBrokenView(forwardDirection: Boolean) {
        val fragment = BrokenSiteNegativeFeedbackFragment.instance()
        updateFragment(fragment, forwardDirection)
    }

    private fun updateFragment(
        fragment: FeedbackFragment,
        forwardDirection: Boolean,
    ) {
        val tag = fragment.javaClass.name
        if (supportFragmentManager.findFragmentByTag(tag) != null) return

        supportFragmentManager.transaction {
            this.applyTransition(forwardDirection)
            replace(R.id.fragmentContainer, fragment, fragment.tag)
        }
    }

    /**
     * Initial feedback page listeners
     */
    override fun userSelectedPositiveFeedback() {
        viewModel.userSelectedPositiveFeedback()
    }

    override fun userSelectedNegativeFeedback() {
        viewModel.userSelectedNegativeFeedback()
    }

    override fun userCancelled() {
        viewModel.userWantsToCancel()
    }

    /**
     * Positive feedback listeners
     */
    override fun userSelectedToRateApp() {
        viewModel.userSelectedToRateApp()
    }

    override fun userSelectedToGiveFeedback() {
        viewModel.userSelectedToGiveFeedback()
    }

    override fun userGavePositiveFeedbackNoDetails() {
        viewModel.userGavePositiveFeedbackNoDetails()
    }

    override fun userProvidedPositiveOpenEndedFeedback(feedback: String) {
        viewModel.userProvidedPositiveOpenEndedFeedback(feedback)
    }

    /**
     * Negative feedback listeners
     */
    override fun userProvidedNegativeOpenEndedFeedback(
        mainReason: MainReason,
        subReason: SubReason?,
        feedback: String,
    ) {
        viewModel.userProvidedNegativeOpenEndedFeedback(mainReason, subReason, feedback)
    }

    /**
     * Negative feedback main reason selection
     */
    override fun userSelectedNegativeFeedbackMainReason(type: MainReason) {
        viewModel.userSelectedNegativeFeedbackMainReason(type)
    }

    /**
     * Negative feedback subReason selection
     */

    override fun userSelectedSubReasonMissingBrowserFeatures(
        mainReason: MainReason,
        subReason: MissingBrowserFeaturesSubReasons,
    ) {
        viewModel.userSelectedSubReasonMissingBrowserFeatures(mainReason, subReason)
    }

    override fun userSelectedSubReasonSearchNotGoodEnough(
        mainReason: MainReason,
        subReason: SearchNotGoodEnoughSubReasons,
    ) {
        viewModel.userSelectedSubReasonSearchNotGoodEnough(mainReason, subReason)
    }

    override fun userSelectedSubReasonNeedMoreCustomization(
        mainReason: MainReason,
        subReason: CustomizationSubReasons,
    ) {
        viewModel.userSelectedSubReasonNeedMoreCustomization(mainReason, subReason)
    }

    override fun userSelectedSubReasonAppIsSlowOrBuggy(
        mainReason: MainReason,
        subReason: PerformanceSubReasons,
    ) {
        viewModel.userSelectedSubReasonAppIsSlowOrBuggy(mainReason, subReason)
    }

    /**
     * Negative feedback, broken site
     */
    override fun onProvidedBrokenSiteFeedback(
        feedback: String,
        url: String?,
    ) {
        viewModel.onProvidedBrokenSiteFeedback(feedback, url)
    }

    private fun hideKeyboard() {
        toolbar.hideKeyboard()
    }
}

private fun FeedbackActivity.animateFinish(feedbackSubmitted: Boolean) {
    val resultCode = if (feedbackSubmitted) RESULT_OK else RESULT_CANCELED
    setResult(resultCode)
    finish()
    overridePendingTransition(com.duckduckgo.mobile.android.R.anim.slide_from_left, com.duckduckgo.mobile.android.R.anim.slide_to_right)
}

private fun FragmentTransaction.applyTransition(forwardDirection: Boolean) {
    if (forwardDirection) {
        setCustomAnimations(com.duckduckgo.mobile.android.R.anim.slide_from_right, com.duckduckgo.mobile.android.R.anim.slide_to_left)
    } else {
        setCustomAnimations(com.duckduckgo.mobile.android.R.anim.slide_from_left, com.duckduckgo.mobile.android.R.anim.slide_to_right)
    }
}
