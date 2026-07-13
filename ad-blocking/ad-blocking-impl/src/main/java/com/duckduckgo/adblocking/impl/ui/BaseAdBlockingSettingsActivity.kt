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

package com.duckduckgo.adblocking.impl.ui

import android.view.View
import android.widget.CompoundButton
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.adblocking.api.duckplayer.DuckPlayerSettingsNoParams
import com.duckduckgo.adblocking.impl.R
import com.duckduckgo.browser.api.ui.BrowserScreens
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.spans.DuckDuckGoClickableSpan
import com.duckduckgo.common.ui.view.addClickableSpan
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.listitem.DaxListItem
import com.duckduckgo.common.ui.view.listitem.OneLineListItem
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeBucket
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeProvider
import com.duckduckgo.navigation.api.GlobalActivityStarter
import dev.zacsweers.metro.HasMemberInjections
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/**
 * Shared logic for the Ad Blocking settings screen.
 */
@HasMemberInjections
abstract class BaseAdBlockingSettingsActivity : DuckDuckGoActivity() {

    protected val viewModel: AdBlockingSettingsViewModel by bindViewModel()

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var edgeToEdgeProvider: EdgeToEdgeProvider

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    protected abstract val toolbar: Toolbar
    protected abstract val blockAdsToggle: OneLineListItem
    protected abstract val adBlockingDescription: DaxTextView
    protected abstract val duckPlayerEntry: DaxListItem
    protected abstract val duckPlayerDescription: DaxTextView

    protected abstract val rootView: View
    protected abstract val appBarLayout: View
    protected abstract val contentScrollView: View

    protected open val learnMoreScreenTitle: Int = R.string.ad_blocking_settings_title

    private val edgeToEdgeEnabled: Boolean by lazy { edgeToEdgeProvider.isEnabled(EdgeToEdgeBucket.SETTINGS) }

    private val blockAdsToggleListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        viewModel.onBlockAdsToggled(isChecked)
    }

    protected fun maybeEnableEdgeToEdge() {
        if (edgeToEdgeEnabled) {
            enableTransparentEdgeToEdge()
        }
    }

    protected fun configure() {
        setupToolbar(toolbar)

        duckPlayerEntry.setClickListener { viewModel.onDuckPlayerClicked() }
        duckPlayerDescription.setOnClickListener { viewModel.onDuckPlayerClicked() }

        if (edgeToEdgeEnabled) {
            configureEdgeToEdgeInsets()
        }

        observeViewModel()
    }

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(rootView)
        edgeToEdgeHandler.applyStatusBarInsets(appBarLayout)
        edgeToEdgeHandler.applyNavigationBarInsets(contentScrollView, drawBehindGestureNav = true)
    }

    protected open fun render(state: AdBlockingSettingsViewModel.ViewState) {
        blockAdsToggle.quietlySetIsChecked(state.isEnabled, blockAdsToggleListener)
        renderDescription(state.showConsentDescription)
    }

    private fun renderDescription(showConsentDescription: Boolean?) {
        if (showConsentDescription == null) {
            adBlockingDescription.gone()
            return
        }
        val descriptionRes = if (showConsentDescription) {
            R.string.ad_blocking_settings_description_with_consent
        } else {
            R.string.ad_blocking_settings_description
        }
        adBlockingDescription.addClickableSpan(
            textSequence = getText(descriptionRes),
            spans = listOf(
                "learn_more_link" to object : DuckDuckGoClickableSpan() {
                    override fun onClick(widget: View) {
                        viewModel.onLearnMoreClicked()
                    }
                },
            ),
        )
        adBlockingDescription.show()
    }

    private fun observeViewModel() {
        viewModel.viewState
            .flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
            .onEach { render(it) }
            .launchIn(lifecycleScope)

        viewModel.commands
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { command ->
                when (command) {
                    is AdBlockingSettingsViewModel.Command.OpenLearnMore -> globalActivityStarter.start(
                        this,
                        BrowserScreens.WebViewActivityWithParams(
                            url = command.url,
                            screenTitle = getString(learnMoreScreenTitle),
                        ),
                    )
                    AdBlockingSettingsViewModel.Command.OpenDuckPlayerSettings -> globalActivityStarter.start(
                        this,
                        DuckPlayerSettingsNoParams,
                    )
                }
            }
            .launchIn(lifecycleScope)
    }
}
