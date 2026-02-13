/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.remote.messaging.impl.modal.cardslist

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.browser.api.ui.BrowserScreens.WebViewActivityWithParams
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.common.utils.playstore.PlayStoreUtils
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.mobile.android.app.tracking.ui.AppTrackingProtectionScreens.AppTrackerOnboardingActivityWithEmptyParamsParams
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.GlobalActivityStarter.DeeplinkActivityParams
import com.duckduckgo.remote.messaging.impl.R
import com.duckduckgo.remote.messaging.impl.databinding.ViewCardsListRemoteMessageBinding
import com.duckduckgo.remote.messaging.impl.modal.cardslist.CardsListRemoteMessageViewModel.Command
import com.duckduckgo.remote.messaging.impl.modal.cardslist.CardsListRemoteMessageViewModel.Command.DismissMessage
import com.duckduckgo.remote.messaging.impl.modal.cardslist.CardsListRemoteMessageViewModel.Command.LaunchAppTPOnboarding
import com.duckduckgo.remote.messaging.impl.modal.cardslist.CardsListRemoteMessageViewModel.Command.LaunchDefaultBrowser
import com.duckduckgo.remote.messaging.impl.modal.cardslist.CardsListRemoteMessageViewModel.Command.LaunchDefaultCredentialProvider
import com.duckduckgo.remote.messaging.impl.modal.cardslist.CardsListRemoteMessageViewModel.Command.LaunchPlayStore
import com.duckduckgo.remote.messaging.impl.modal.cardslist.CardsListRemoteMessageViewModel.Command.LaunchScreen
import com.duckduckgo.remote.messaging.impl.modal.cardslist.CardsListRemoteMessageViewModel.Command.SharePromoLinkRMF
import com.duckduckgo.remote.messaging.impl.modal.cardslist.CardsListRemoteMessageViewModel.Command.SubmitUrl
import com.duckduckgo.remote.messaging.impl.modal.cardslist.CardsListRemoteMessageViewModel.Command.SubmitUrlInContext
import com.duckduckgo.remote.messaging.impl.newtab.SharePromoLinkBroadCastReceiver
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import javax.inject.Inject

@InjectWith(ViewScope::class)
class CardsListRemoteMessageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    @Inject
    lateinit var cardsListAdapter: CardsListAdapter

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var playStoreUtils: PlayStoreUtils

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject
    lateinit var browserNav: BrowserNav

    private val binding: ViewCardsListRemoteMessageBinding by viewBinding()

    private val viewModel: CardsListRemoteMessageViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[CardsListRemoteMessageViewModel::class.java]
    }

    private val conflatedStateJob = ConflatedJob()
    private val conflatedCommandJob = ConflatedJob()

    var listener: CardsListRemoteMessageListener? = null
    var messageId: String? = null

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        findViewTreeLifecycleOwner()?.lifecycle?.addObserver(viewModel)

        val coroutineScope = findViewTreeLifecycleOwner()?.lifecycleScope

        setupUi()
        viewModel.init(messageId)

        conflatedStateJob += viewModel.viewState
            .onEach { render(it) }
            .launchIn(coroutineScope!!)

        conflatedCommandJob += viewModel.commands
            .onEach { processCommand(it) }
            .launchIn(coroutineScope!!)
    }

    override fun onDetachedFromWindow() {
        conflatedStateJob.cancel()
        conflatedCommandJob.cancel()
        super.onDetachedFromWindow()
    }

    private fun setupUi() {
        cardsListAdapter.setListener(viewModel)
        cardsListAdapter.headerImageLoadListener = object : CardsListAdapter.HeaderImageLoadListener {
            override fun onImageLoadSuccess() {
                viewModel.onRemoteImageLoadSuccess()
            }

            override fun onImageLoadFailed() {
                viewModel.onRemoteImageLoadFailed()
            }
        }
        binding.cardItemsRecyclerView.adapter = cardsListAdapter

        binding.closeButton.setOnClickListener {
            viewModel.onCloseButtonClicked()
        }
        binding.actionButton.setOnClickListener {
            viewModel.onActionButtonClicked()
        }
    }

    private fun render(viewState: CardsListRemoteMessageViewModel.ViewState?) {
        viewState?.let {
            cardsListAdapter.submitList(it.modalListItems)
            binding.actionButton.text = it.primaryActionText
            viewModel.onMessageShown()
        }
    }

    private fun processCommand(command: Command) {
        when (command) {
            is SubmitUrl -> submitUrl(command.url)
            is SubmitUrlInContext -> submitUrlInContext(command.url)
            is DismissMessage -> dismiss()
            is LaunchDefaultCredentialProvider -> launchDefaultCredentialProvider()
            is LaunchAppTPOnboarding -> launchAppTPOnboarding()
            is LaunchDefaultBrowser -> launchDefaultBrowser()
            is LaunchPlayStore -> openPlayStore(command.appPackage)
            is SharePromoLinkRMF -> launchSharePromoRMFPageChooser(command.url, command.shareTitle)
            is LaunchScreen -> launchScreen(command.screen, command.payload)
        }
    }

    private fun submitUrl(url: String) {
        context.startActivity(browserNav.openInCurrentTab(context, url))
    }

    private fun submitUrlInContext(url: String) {
        globalActivityStarter.start(
            context,
            WebViewActivityWithParams(
                url = url,
                "",
            ),
        )
    }

    private fun dismiss() {
        listener?.onDismiss()
    }

    private fun launchAppTPOnboarding() {
        globalActivityStarter.start(context, AppTrackerOnboardingActivityWithEmptyParamsParams)
    }

    @SuppressLint("DenyListedApi")
    private fun launchDefaultCredentialProvider() {
        runCatching {
            val intent = if (appBuildConfig.sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                Intent(Settings.ACTION_CREDENTIAL_PROVIDER).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            } else {
                Intent(Settings.ACTION_SETTINGS)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.onFailure {
            logcat { "RMF: Error launching credential provider / system settings." }
        }
    }

    private fun launchDefaultBrowser() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            intent.putExtra(":settings:fragment_args_key", "default_browser")
            intent.putExtra(":settings:show_fragment_args", bundleOf(":settings:fragment_args_key" to "default_browser"))
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            val errorMessage = context.getString(R.string.cannotLaunchDefaultAppSettings)
            logcat(WARN) { errorMessage }
            Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openPlayStore(appPackage: String) {
        playStoreUtils.launchPlayStore(appPackage)
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
            Intent(context, SharePromoLinkBroadCastReceiver::class.java),
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

    interface CardsListRemoteMessageListener {
        fun onDismiss()
    }
}
