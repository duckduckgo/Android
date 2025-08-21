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

package com.duckduckgo.app.browser.newtab

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.HomeBackgroundLogo
import com.duckduckgo.app.browser.databinding.ViewNewTabLegacyBinding
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.newtab.NewTabLegacyPageViewModel.Command
import com.duckduckgo.app.browser.newtab.NewTabLegacyPageViewModel.Command.DismissMessage
import com.duckduckgo.app.browser.newtab.NewTabLegacyPageViewModel.Command.LaunchAppTPOnboarding
import com.duckduckgo.app.browser.newtab.NewTabLegacyPageViewModel.Command.LaunchDefaultBrowser
import com.duckduckgo.app.browser.newtab.NewTabLegacyPageViewModel.Command.LaunchPlayStore
import com.duckduckgo.app.browser.newtab.NewTabLegacyPageViewModel.Command.LaunchScreen
import com.duckduckgo.app.browser.newtab.NewTabLegacyPageViewModel.Command.SharePromoLinkRMF
import com.duckduckgo.app.browser.newtab.NewTabLegacyPageViewModel.Command.SubmitUrl
import com.duckduckgo.app.browser.newtab.NewTabLegacyPageViewModel.NewTabLegacyPageViewModelFactory
import com.duckduckgo.app.browser.newtab.NewTabLegacyPageViewModel.NewTabLegacyPageViewModelProviderFactory
import com.duckduckgo.app.browser.newtab.NewTabLegacyPageViewModel.ViewState
import com.duckduckgo.app.browser.remotemessage.SharePromoLinkRMFBroadCastReceiver
import com.duckduckgo.app.browser.remotemessage.asMessage
import com.duckduckgo.app.global.view.launchDefaultAppActivity
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.mobile.android.app.tracking.ui.AppTrackingProtectionScreens.AppTrackerOnboardingActivityWithEmptyParamsParams
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.GlobalActivityStarter.DeeplinkActivityParams
import com.duckduckgo.remote.messaging.api.RemoteMessage
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat

@InjectWith(ViewScope::class)
class NewTabLegacyPageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
    private val showLogo: Boolean = true,
    private val onHasContent: ((Boolean) -> Unit)? = null,
) : LinearLayout(context, attrs, defStyle) {

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var browserNav: BrowserNav

    @Inject
    lateinit var faviconManager: FaviconManager

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var appTheme: AppTheme

    @Inject
    lateinit var dispatchers: DispatcherProvider

    private val binding: ViewNewTabLegacyBinding by viewBinding()

    private val homeBackgroundLogo by lazy { HomeBackgroundLogo(binding.ddgLogo) }

    @Inject
    lateinit var viewModelFactory: NewTabLegacyPageViewModelFactory

    private val viewModel: NewTabLegacyPageViewModel by lazy {
        val providerFactory = NewTabLegacyPageViewModelProviderFactory(viewModelFactory, showDaxLogo = showLogo)
        ViewModelProvider(owner = findViewTreeViewModelStoreOwner()!!, factory = providerFactory)[NewTabLegacyPageViewModel::class.java]
    }

    private val conflatedStateJob = ConflatedJob()
    private val conflatedCommandJob = ConflatedJob()

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        findViewTreeLifecycleOwner()?.lifecycle?.addObserver(viewModel)

        conflatedStateJob += viewModel.viewState
            .onEach { render(it) }
            .launchIn(findViewTreeLifecycleOwner()?.lifecycleScope!!)

        conflatedCommandJob += viewModel.commands()
            .onEach { processCommands(it) }
            .launchIn(findViewTreeLifecycleOwner()?.lifecycleScope!!)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        findViewTreeLifecycleOwner()?.lifecycle?.removeObserver(viewModel)
        conflatedStateJob.cancel()
        conflatedCommandJob.cancel()
    }

    private fun render(viewState: ViewState) {
        logcat { "New Tab: render $viewState" }

        onHasContent?.invoke(viewState.hasContent)

        if (viewState.shouldShowLogo) {
            homeBackgroundLogo.showLogo()
        } else {
            homeBackgroundLogo.hideLogo()
        }
        if (viewState.message != null && viewState.onboardingComplete) {
            showRemoteMessage(viewState.message, viewState.newMessage)
        } else {
            binding.messageCta.gone()
            if (viewState.lowPriorityMessage != null) {
                showLowPriorityMessage(viewState.lowPriorityMessage)
            }
        }

        if (viewState.favourites.isEmpty()) {
            binding.focusedFavourites.gone()
        } else {
            binding.focusedFavourites.show()
        }
    }

    private fun processCommands(command: Command) {
        when (command) {
            is DismissMessage -> {}
            is LaunchAppTPOnboarding -> launchAppTPOnboardingScreen()
            is LaunchDefaultBrowser -> launchDefaultBrowser()
            is LaunchPlayStore -> viewModel.openPlayStore(command.appPackage)
            is LaunchScreen -> launchScreen(command.screen, command.payload)
            is SharePromoLinkRMF -> launchSharePromoRMFPageChooser(command.url, command.shareTitle)
            is SubmitUrl -> submitUrl(command.url)
        }
    }

    private fun launchDefaultBrowser() {
        context.launchDefaultAppActivity()
    }

    private fun launchAppTPOnboardingScreen() {
        globalActivityStarter.start(context, AppTrackerOnboardingActivityWithEmptyParamsParams)
    }

    private fun launchSharePromoRMFPageChooser(
        url: String,
        shareTitle: String,
    ) {
        val share = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, url)
            putExtra(Intent.EXTRA_TITLE, shareTitle)
            type = "text/plain"
        }

        val pi = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, SharePromoLinkRMFBroadCastReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            context.startActivity(Intent.createChooser(share, null, pi.intentSender))
        } catch (e: ActivityNotFoundException) {
            logcat(WARN) { "Activity not found: ${e.asLog()}" }
        }
    }

    private fun launchScreen(
        screen: String,
        payload: String,
    ) {
        context?.let {
            globalActivityStarter.start(it, DeeplinkActivityParams(screenName = screen, jsonArguments = payload), null)
        }
    }

    private fun submitUrl(url: String) {
        context.startActivity(browserNav.openInCurrentTab(context, url))
    }

    private fun showRemoteMessage(
        message: RemoteMessage,
        newMessage: Boolean,
    ) {
        val parentVisible = (this.parent as? View)?.isVisible ?: false
        val shouldRender = parentVisible && (newMessage || binding.messageCta.isGone)

        if (shouldRender) {
            binding.messageCta.setMessage(message.asMessage(isLightModeEnabled = appTheme.isLightModeEnabled()))
            binding.messageCta.onCloseButtonClicked {
                viewModel.onMessageCloseButtonClicked()
            }
            binding.messageCta.onPrimaryActionClicked {
                viewModel.onMessagePrimaryButtonClicked()
            }
            binding.messageCta.onSecondaryActionClicked {
                viewModel.onMessageSecondaryButtonClicked()
            }
            binding.messageCta.onPromoActionClicked {
                viewModel.onMessageActionButtonClicked()
            }
            binding.messageCta.show()
            viewModel.onMessageShown()
        }
    }

    private fun showLowPriorityMessage(message: LowPriorityMessage) {
        val parentVisible = (this.parent as? View)?.isVisible ?: false
        val shouldRender = parentVisible && binding.messageCta.isGone

        if (shouldRender) {
            binding.messageCta.setMessage(message.message)
            binding.messageCta.onCloseButtonClicked {
                findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                    message.onCloseButtonClicked()
                    removeLowPriorityMessage()
                }
            }
            binding.messageCta.onPrimaryActionClicked {
                findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                    message.onPrimaryButtonClicked()
                    viewModel.onLowPriorityMessagePrimaryButtonClicked()
                    removeLowPriorityMessage()
                }
            }
            binding.messageCta.onSecondaryActionClicked {
                findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                    message.onSecondaryButtonClicked()
                    removeLowPriorityMessage()
                }
            }
            binding.messageCta.show()
            message.onMessageShown()
        }
    }

    private fun removeLowPriorityMessage() {
        binding.messageCta.gone()
    }
}
