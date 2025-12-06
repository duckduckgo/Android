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

package com.duckduckgo.remote.messaging.impl.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.browser.api.ui.BrowserScreens.WebViewActivityWithParams
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.remote.messaging.impl.databinding.ViewCardsListRemoteMessageBinding
import com.duckduckgo.remote.messaging.impl.mappers.drawable
import com.duckduckgo.remote.messaging.impl.ui.CardsListRemoteMessageViewModel.Command
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.logcat
import javax.inject.Inject

@InjectWith(ViewScope::class)
class CardsListRemoteMessageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    @Inject
    lateinit var modalSurfaceAdapter: ModalSurfaceAdapter

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

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

        conflatedStateJob += viewModel.viewState
            .onEach { render(it) }
            .launchIn(coroutineScope!!)

        conflatedCommandJob += viewModel.commands
            .onEach { processCommand(it) }
            .launchIn(coroutineScope!!)

        setupUi()

        viewModel.init(messageId)
    }

    override fun onDetachedFromWindow() {
        conflatedStateJob.cancel()
        conflatedCommandJob.cancel()
        super.onDetachedFromWindow()
    }

    private fun setupUi() {
        modalSurfaceAdapter.setListener(viewModel)
        binding.cardItemsRecyclerView.adapter = modalSurfaceAdapter

        binding.closeButton.setOnClickListener {
            viewModel.onCloseButtonClicked()
        }
        binding.actionButton.setOnClickListener {
            viewModel.onActionButtonClicked()
        }
    }

    private fun render(viewState: CardsListRemoteMessageViewModel.ViewState?) {
        viewState?.cardsLists?.let {
            modalSurfaceAdapter.submitList(it.listItems)
            binding.headerImage.setImageResource(it.placeholder.drawable(true))
            binding.headerTitle.text = it.titleText
            binding.actionButton.text = it.primaryActionText
        }
    }

    private fun processCommand(command: Command) {
        when (command) {
            is Command.SubmitUrlInContext -> {
                globalActivityStarter.start(
                    context,
                    WebViewActivityWithParams(
                        url = command.url,
                        "",
                    ),
                )
            }
            is Command.DismissMessage -> {
                listener?.onDismiss()
            }
            is Command.LaunchDefaultCredentialProvider -> {
                launchDefaultCredentialProvider()
            }
            else -> {
                // Unsupported command, close.
                listener?.onDismiss()
            }
        }
    }

    @SuppressLint("DenyListedApi")
    private fun launchDefaultCredentialProvider() {
        runCatching {
            val intent = if (appBuildConfig.sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                Intent(Settings.ACTION_CREDENTIAL_PROVIDER).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
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

    interface CardsListRemoteMessageListener {
        fun onDismiss()
    }
}
