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

package com.duckduckgo.app.feedback.ui.common

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.transaction
import androidx.lifecycle.Observer
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityFragmentWithToolbarBinding
import com.duckduckgo.app.feedback.ui.initial.InitialFeedbackFragment
import com.duckduckgo.app.feedback.ui.negative.FeedbackType.*
import com.duckduckgo.app.feedback.ui.negative.brokensite.BrokenSiteNegativeFeedbackFragment
import com.duckduckgo.app.feedback.ui.negative.mainreason.MainReasonNegativeFeedbackFragment
import com.duckduckgo.app.feedback.ui.negative.openended.ShareOpenEndedFeedbackFragment
import com.duckduckgo.app.feedback.ui.negative.subreason.SubReasonNegativeFeedbackFragment
import com.duckduckgo.app.feedback.ui.positive.initial.PositiveFeedbackLandingFragment
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.mobile.android.ui.view.hideKeyboard
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import timber.log.Timber

class FeedbackActivity :
    DuckDuckGoActivity(),
    InitialFeedbackFragment.InitialFeedbackListener,
    PositiveFeedbackLandingFragment.PositiveFeedbackLandingListener,
    ShareOpenEndedFeedbackFragment.OpenEndedFeedbackListener,
    MainReasonNegativeFeedbackFragment.MainReasonNegativeFeedbackListener,
    BrokenSiteNegativeFeedbackFragment.BrokenSiteFeedbackListener,
    SubReasonNegativeFeedbackFragment.DisambiguationNegativeFeedbackListener {

    private val viewModel: FeedbackViewModel by bindViewModel()

    private val binding: ActivityFragmentWithToolbarBinding by viewBinding()

    private val toolbar
        get() = binding.includeToolbar.toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(toolbar)
        configureObservers()
    }

    private fun configureObservers() {
        viewModel.command.observe(
            this,
            Observer {
                it?.let { command -> processCommand(command) }
            }
        )
        viewModel.updateViewCommand.observe(
            this,
            Observer {
                it?.let { viewState -> render(viewState) }
            }
        )
    }

    private fun processCommand(command: Command) {
        Timber.v("Processing command: $command")

        when (command) {
            is Command.Exit -> animateFinish(command.feedbackSubmitted)
            is Command.HideKeyboard -> hideKeyboard()
        }
    }

    private fun render(viewState: UpdateViewCommand) {
        Timber.v("ViewState is: $viewState")

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

    private fun showNegativeFeedbackSubReasonView(forwardDirection: Boolean, mainReason: MainReason) {
        val fragment = SubReasonNegativeFeedbackFragment.instance(mainReason)
        updateFragment(fragment, forwardDirection)
    }

    private fun showNegativeOpenEndedFeedbackView(forwardDirection: Boolean, mainReason: MainReason, subReason: SubReason? = null) {
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

    private fun updateFragment(fragment: FeedbackFragment, forwardDirection: Boolean) {
        val tag = fragment.javaClass.name
        if (supportFragmentManager.findFragmentByTag(tag) != null) return

        supportFragmentManager.transaction {
            this.applyTransition(forwardDirection)
            replace(R.id.fragmentContainer, fragment, fragment.tag)
        }
    }

    override fun onBackPressed() {
        viewModel.onBackPressed()
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
    override fun userProvidedNegativeOpenEndedFeedback(mainReason: MainReason, subReason: SubReason?, feedback: String) {
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

    override fun userSelectedSubReasonMissingBrowserFeatures(mainReason: MainReason, subReason: MissingBrowserFeaturesSubReasons) {
        viewModel.userSelectedSubReasonMissingBrowserFeatures(mainReason, subReason)
    }

    override fun userSelectedSubReasonSearchNotGoodEnough(mainReason: MainReason, subReason: SearchNotGoodEnoughSubReasons) {
        viewModel.userSelectedSubReasonSearchNotGoodEnough(mainReason, subReason)
    }

    override fun userSelectedSubReasonNeedMoreCustomization(mainReason: MainReason, subReason: CustomizationSubReasons) {
        viewModel.userSelectedSubReasonNeedMoreCustomization(mainReason, subReason)
    }

    override fun userSelectedSubReasonAppIsSlowOrBuggy(mainReason: MainReason, subReason: PerformanceSubReasons) {
        viewModel.userSelectedSubReasonAppIsSlowOrBuggy(mainReason, subReason)
    }

    /**
     * Negative feedback, broken site
     */
    override fun onProvidedBrokenSiteFeedback(feedback: String, url: String?) {
        viewModel.onProvidedBrokenSiteFeedback(feedback, url)
    }

    private fun hideKeyboard() {
        toolbar.hideKeyboard()
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, FeedbackActivity::class.java)
        }
    }
}

private fun FeedbackActivity.animateFinish(feedbackSubmitted: Boolean) {
    val resultCode =
        if (feedbackSubmitted) {
            RESULT_OK
        } else {
            RESULT_CANCELED
        }
    setResult(resultCode)

    finish()
    overridePendingTransition(R.anim.slide_from_left, R.anim.slide_to_right)
}

private fun FragmentTransaction.applyTransition(forwardDirection: Boolean) {
    if (forwardDirection) {
        setCustomAnimations(R.anim.slide_from_right, R.anim.slide_to_left)
    } else {
        setCustomAnimations(R.anim.slide_from_left, R.anim.slide_to_right)
    }
}
