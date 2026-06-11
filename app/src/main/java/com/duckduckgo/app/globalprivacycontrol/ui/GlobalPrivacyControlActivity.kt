/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.globalprivacycontrol.ui

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.View
import android.widget.CompoundButton.OnCheckedChangeListener
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityGlobalPrivacyControlBinding
import com.duckduckgo.browser.api.ui.BrowserScreens.WebViewActivityWithParams
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeBucket
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeProvider
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class GlobalPrivacyControlActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var edgeToEdgeProvider: EdgeToEdgeProvider

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    private val binding: ActivityGlobalPrivacyControlBinding by viewBinding()

    private val viewModel: GlobalPrivacyControlViewModel by bindViewModel()

    private val toolbar
        get() = binding.includeToolbar.toolbar

    private val clickableSpan = object : ClickableSpan() {
        override fun onClick(widget: View) {
            viewModel.onLearnMoreSelected()
        }
    }

    private val globalPrivacyControlToggleListener = OnCheckedChangeListener { _, isChecked ->
        viewModel.onUserToggleGlobalPrivacyControl(isChecked)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val edgeToEdgeEnabled = edgeToEdgeProvider.isEnabled(EdgeToEdgeBucket.SETTINGS)
        if (edgeToEdgeEnabled) {
            val barStyle = if (isDarkThemeEnabled()) {
                SystemBarStyle.dark(Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            }
            enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            // Keep the navigation bar transparent in gesture nav; let the system draw a contrast
            // scrim only in 2/3-button nav so the buttons stay legible.
            if (Build.VERSION.SDK_INT >= 29) {
                window.isNavigationBarContrastEnforced = true
            }
        }

        setContentView(binding.root)
        setupToolbar(toolbar)
        if (edgeToEdgeEnabled) {
            configureEdgeToEdgeInsets()
        }
        configureUiEventHandlers()
        configureClickableLink()
        observeViewModel()
    }

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
        edgeToEdgeHandler.applyStatusBarInsets(binding.includeToolbar.appBarLayout)
        edgeToEdgeHandler.applyNavigationBarInsets(binding.globalPrivacyControlToggle, drawBehindGestureNav = true)
    }

    private fun configureClickableLink() {
        val htmlGPCText = getString(R.string.globalPrivacyControlDescription).html(this)
        val gpcSpannableString = SpannableStringBuilder(htmlGPCText)
        val urlSpans = htmlGPCText.getSpans(0, htmlGPCText.length, URLSpan::class.java)
        urlSpans?.forEach {
            gpcSpannableString.apply {
                setSpan(
                    clickableSpan,
                    gpcSpannableString.getSpanStart(it),
                    gpcSpannableString.getSpanEnd(it),
                    gpcSpannableString.getSpanFlags(it),
                )
                removeSpan(it)
                trim()
            }
        }
        binding.globalPrivacyControlDescription.apply {
            text = gpcSpannableString
            movementMethod = LinkMovementMethod.getInstance()
        }
    }

    private fun configureUiEventHandlers() {
        binding.globalPrivacyControlToggle.setOnCheckedChangeListener(globalPrivacyControlToggleListener)
    }

    private fun observeViewModel() {
        viewModel.viewState.observe(
            this,
            { viewState ->
                viewState?.let {
                    binding.globalPrivacyControlToggle.quietlySetIsChecked(it.globalPrivacyControlEnabled, globalPrivacyControlToggleListener)
                    binding.globalPrivacyControlToggle.isEnabled = it.globalPrivacyControlFeatureEnabled
                }
            },
        )

        viewModel.command.observe(
            this,
            { command ->
                command?.let {
                    when (it) {
                        is GlobalPrivacyControlViewModel.Command.OpenLearnMore -> openLearnMoreSite(it)
                    }
                }
            },
        )
    }

    private fun openLearnMoreSite(command: GlobalPrivacyControlViewModel.Command.OpenLearnMore) {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        globalActivityStarter.start(this, WebViewActivityWithParams(command.url, getString(command.titleId)), options)
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, GlobalPrivacyControlActivity::class.java)
        }
    }
}
