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

package com.duckduckgo.remote.messaging.impl.newtab

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributesActivePlugin
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.mobile.android.app.tracking.ui.AppTrackingProtectionScreens.AppTrackerOnboardingActivityWithEmptyParamsParams
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.GlobalActivityStarter.DeeplinkActivityParams
import com.duckduckgo.newtabpage.api.NewTabPageSection
import com.duckduckgo.newtabpage.api.NewTabPageSectionPlugin
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.api.RemoteMessageModel
import com.duckduckgo.remote.messaging.impl.R
import com.duckduckgo.remote.messaging.impl.databinding.ViewRemoteMessageBinding
import com.duckduckgo.remote.messaging.impl.mappers.asMessage
import com.duckduckgo.remote.messaging.impl.newtab.RemoteMessageViewModel.Command
import com.duckduckgo.remote.messaging.impl.newtab.RemoteMessageViewModel.Command.DismissMessage
import com.duckduckgo.remote.messaging.impl.newtab.RemoteMessageViewModel.Command.LaunchAppTPOnboarding
import com.duckduckgo.remote.messaging.impl.newtab.RemoteMessageViewModel.Command.LaunchDefaultBrowser
import com.duckduckgo.remote.messaging.impl.newtab.RemoteMessageViewModel.Command.LaunchPlayStore
import com.duckduckgo.remote.messaging.impl.newtab.RemoteMessageViewModel.Command.LaunchScreen
import com.duckduckgo.remote.messaging.impl.newtab.RemoteMessageViewModel.Command.SharePromoLinkRMF
import com.duckduckgo.remote.messaging.impl.newtab.RemoteMessageViewModel.Command.SubmitUrl
import com.duckduckgo.remote.messaging.impl.newtab.RemoteMessageViewModel.ViewState
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import javax.inject.Inject

@InjectWith(ViewScope::class)
class RemoteMessageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayout(context, attrs, defStyle) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var browserNav: BrowserNav

    @Inject
    lateinit var appTheme: AppTheme

    @Inject
    lateinit var dispatchers: DispatcherProvider

    private val binding: ViewRemoteMessageBinding by viewBinding()

    private val viewModel: RemoteMessageViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[RemoteMessageViewModel::class.java]
    }

    private val conflatedStateJob = ConflatedJob()
    private val conflatedCommandJob = ConflatedJob()

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        findViewTreeLifecycleOwner()?.lifecycle?.addObserver(viewModel)

        val coroutineScope = findViewTreeLifecycleOwner()?.lifecycleScope

        conflatedStateJob += viewModel.viewState
            .onEach { render(it) }
            .launchIn(coroutineScope!!)

        conflatedCommandJob += viewModel.commands()
            .onEach { processCommands(it) }
            .launchIn(coroutineScope!!)
    }

    override fun onDetachedFromWindow() {
        conflatedStateJob.cancel()
        conflatedCommandJob.cancel()
        super.onDetachedFromWindow()
    }

    private fun render(viewState: ViewState) {
        if (viewState.message != null) {
            showRemoteMessage(viewState.message, viewState.newMessage)
        } else {
            binding.messageCta.gone()
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

    private fun showRemoteMessage(
        message: RemoteMessage,
        newMessage: Boolean,
    ) {
        val shouldRender = newMessage || binding.root.visibility == View.GONE

        if (shouldRender) {
            binding.messageCta.show()
            viewModel.onMessageShown()
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
        }
    }

    private fun launchAppTPOnboardingScreen() {
        globalActivityStarter.start(context, AppTrackerOnboardingActivityWithEmptyParamsParams)
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

    private fun launchScreen(
        screen: String,
        payload: String,
    ) {
        context?.let {
            globalActivityStarter.start(it, DeeplinkActivityParams(screenName = screen, jsonArguments = payload), null)
        }
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

    private fun submitUrl(url: String) {
        context.startActivity(browserNav.openInCurrentTab(context, url))
    }
}

@ContributesActivePlugin(
    AppScope::class,
    boundType = NewTabPageSectionPlugin::class,
    priority = NewTabPageSectionPlugin.PRIORITY_REMOTE_MESSAGE,
)
class RemoteMessageNewTabSectionPlugin @Inject constructor(
    private val remoteMessageModel: RemoteMessageModel,
) : NewTabPageSectionPlugin {
    override val name = NewTabPageSection.REMOTE_MESSAGING_FRAMEWORK.name

    override fun getView(context: Context): View {
        return RemoteMessageView(context)
    }

    override suspend fun isUserEnabled(): Boolean {
        val message = remoteMessageModel.getActiveMessage()
        return message != null
    }
}
