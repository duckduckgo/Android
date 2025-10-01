/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.subscriptions.impl.feedback

import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.StringRes
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.browser.api.ui.BrowserScreens.FeedbackActivityWithEmptyParams
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.getActivityParams
import com.duckduckgo.subscriptions.api.PrivacyProFeedbackScreens.GeneralPrivacyProFeedbackScreenNoParams
import com.duckduckgo.subscriptions.api.PrivacyProFeedbackScreens.PrivacyProAppFeedbackScreenWithParams
import com.duckduckgo.subscriptions.api.PrivacyProFeedbackScreens.PrivacyProFeedbackScreenWithParams
import com.duckduckgo.subscriptions.impl.R
import com.duckduckgo.subscriptions.impl.databinding.ActivityFeedbackBinding
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackViewModel.Command
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackViewModel.Command.FeedbackCancelled
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackViewModel.Command.FeedbackCompleted
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackViewModel.Command.FeedbackFailed
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackViewModel.Command.ShowHelpPages
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackViewModel.FeedbackFragmentState
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackViewModel.FeedbackMetadata
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackViewModel.ViewState
import com.duckduckgo.subscriptions.impl.ui.SubscriptionsWebViewActivityWithParams
import com.duckduckgo.subscriptions.impl.ui.SubscriptionsWebViewActivityWithParams.ToolbarConfig.CustomTitle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(PrivacyProFeedbackScreenWithParams::class)
@ContributeToActivityStarter(PrivacyProAppFeedbackScreenWithParams::class)
@ContributeToActivityStarter(GeneralPrivacyProFeedbackScreenNoParams::class)
class SubscriptionFeedbackActivity :
    DuckDuckGoActivity(),
    SubscriptionFeedbackGeneralFragment.Listener,
    SubscriptionFeedbackActionFragment.Listener,
    SubscriptionFeedbackCategoryFragment.Listener,
    SubscriptionFeedbackSubcategoryFragment.Listener,
    SubscriptionFeedbackSubmitFragment.Listener {

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter
    private val binding: ActivityFeedbackBinding by viewBinding()
    private val viewModel: SubscriptionFeedbackViewModel by bindViewModel()

    private val toolbar
        get() = binding.includeToolbar.toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(toolbar)
        observeViewModel()
        handleInitialState()
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    viewModel.handleBackPress()
                }
            },
        )
    }

    private fun handleInitialState() {
        val generalFeedbackParams =
            intent.getActivityParams(GeneralPrivacyProFeedbackScreenNoParams::class.java)
        if (generalFeedbackParams != null) {
            viewModel.allowUserToChooseFeedbackType()
            return
        }

        val feedbackScreenParams =
            intent.getActivityParams(PrivacyProFeedbackScreenWithParams::class.java)
        if (feedbackScreenParams != null) {
            viewModel.allowUserToChooseReportType(feedbackScreenParams.feedbackSource)
            return
        }

        intent.getActivityParams(PrivacyProAppFeedbackScreenWithParams::class.java)?.let {
            viewModel.allowUserToReportAppIssue(it.appName, it.appPackageName)
        }
    }

    override fun onBrowserFeedbackClicked() {
        globalActivityStarter.start(this, FeedbackActivityWithEmptyParams)
    }

    override fun onPproFeedbackClicked() {
        viewModel.onProFeedbackSelected()
    }

    override fun onUserClickedReportType(reportType: SubscriptionFeedbackReportType) {
        viewModel.onReportTypeSelected(reportType)
    }

    override fun onUserClickedCategory(category: SubscriptionFeedbackCategory) {
        viewModel.onCategorySelected(category)
    }

    override fun onUserClickedSubCategory(subCategory: SubscriptionFeedbackSubCategory) {
        viewModel.onSubcategorySelected(subCategory)
    }

    override fun onUserSubmit(
        description: String,
        email: String?,
    ) {
        viewModel.onSubmitFeedback(description, email)
    }

    override fun onFaqsOpened() {
        viewModel.onFaqOpenedFromSubmit()
    }

    private fun observeViewModel() {
        viewModel.viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .distinctUntilChanged()
            .onEach { renderViewState(it) }
            .launchIn(lifecycleScope)

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { handleCommands(it) }
            .launchIn(lifecycleScope)
    }

    private fun handleCommands(command: Command) {
        when (command) {
            is FeedbackFailed ->
                Toast.makeText(applicationContext, R.string.feedbackSubmitFailedMessage, Toast.LENGTH_LONG).show()

            is FeedbackCancelled -> finish()
            is FeedbackCompleted -> {
                Toast.makeText(applicationContext, R.string.feedbackSubmitCompletedMessage, Toast.LENGTH_LONG).show()
                finish()
            }

            is ShowHelpPages -> {
                globalActivityStarter.start(
                    this,
                    SubscriptionsWebViewActivityWithParams(
                        url = command.url,
                        toolbarConfig = CustomTitle(""), // empty toolbar
                    ),
                )
            }

            else -> {} // Do nothing
        }
    }

    private fun renderViewState(viewState: ViewState) {
        when (viewState.currentFragmentState) {
            is FeedbackFragmentState.FeedbackAction -> showActionScreen(
                viewState.currentFragmentState.title,
                viewState.isForward,
            )

            is FeedbackFragmentState.FeedbackCategory -> showCategoryScreen(
                viewState.currentFragmentState.title,
                viewState.isForward,
            )

            is FeedbackFragmentState.FeedbackSubCategory -> showSubCategoryScreen(
                viewState.currentFragmentState.title,
                viewState.feedbackMetadata,
                viewState.isForward,
            )

            is FeedbackFragmentState.FeedbackSubmit -> showFeedbackSubmitScreen(
                viewState.currentFragmentState.title,
                viewState.feedbackMetadata,
                viewState.isForward,
            )

            is FeedbackFragmentState.FeedbackGeneral -> showGeneralFeedbackScreen(
                viewState.currentFragmentState.title,
                viewState.isForward,
            )

            null -> {}
        }
    }

    private fun showGeneralFeedbackScreen(
        @StringRes title: Int,
        forward: Boolean,
    ) {
        setTitle(getString(title))
        updateFragment(
            SubscriptionFeedbackGeneralFragment.instance(),
            forward,
        )
    }

    private fun showFeedbackSubmitScreen(
        @StringRes title: Int,
        feedbackMetadata: FeedbackMetadata,
        isForward: Boolean,
    ) {
        setTitle(getString(title))
        updateFragment(
            SubscriptionFeedbackSubmitFragment.instance(
                reportType = feedbackMetadata.reportType!!,
            ),
            isForward,
        )
    }

    private fun showSubCategoryScreen(
        @StringRes title: Int,
        feedbackMetadata: FeedbackMetadata,
        isForward: Boolean,
    ) {
        setTitle(getString(title))
        updateFragment(
            SubscriptionFeedbackSubcategoryFragment.instance(
                category = feedbackMetadata.category!!,
            ),
            isForward,
        )
    }

    private fun showCategoryScreen(
        @StringRes title: Int,
        isForward: Boolean,
    ) {
        setTitle(getString(title))
        updateFragment(SubscriptionFeedbackCategoryFragment.instance(), isForward)
    }

    private fun showActionScreen(
        @StringRes title: Int,
        isForward: Boolean,
    ) {
        setTitle(getString(title))
        updateFragment(SubscriptionFeedbackActionFragment.instance(), isForward)
    }

    private fun updateFragment(
        fragment: DuckDuckGoFragment,
        isForward: Boolean,
    ) {
        val tag = fragment.javaClass.name
        if (supportFragmentManager.findFragmentByTag(tag) != null) return

        supportFragmentManager.commit {
            if (isForward) {
                setCustomAnimations(
                    com.duckduckgo.mobile.android.R.anim.slide_from_right,
                    com.duckduckgo.mobile.android.R.anim.slide_to_left,
                )
            } else {
                setCustomAnimations(
                    com.duckduckgo.mobile.android.R.anim.slide_from_left,
                    com.duckduckgo.mobile.android.R.anim.slide_to_right,
                )
            }
            replace(R.id.feedbackFragmentContainer, fragment, fragment.tag)
        }
    }
}
