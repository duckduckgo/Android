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

package com.duckduckgo.sync.impl.promotion.devicemigration

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.codegen.annotations.ContributesActivePlugin
import com.duckduckgo.codegen.annotations.InjectWith
import com.duckduckgo.common.ui.view.MessageCta.Message
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.android.support.AndroidSupportInjection
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.newtabpage.api.NewTabPageSectionPlugin
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.ViewSyncAfterDeviceTransferNewTabSectionBinding
import com.duckduckgo.sync.impl.promotion.devicemigration.SyncAfterDeviceTransferViewModel.Command
import com.duckduckgo.sync.impl.promotion.devicemigration.SyncAfterDeviceTransferViewModel.Command.Hide
import com.duckduckgo.sync.impl.promotion.devicemigration.SyncAfterDeviceTransferViewModel.Command.OpenSyncSetup
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@InjectWith(ViewScope::class)
class SyncAfterDeviceTransferNewTabSectionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    private val binding: ViewSyncAfterDeviceTransferNewTabSectionBinding by viewBinding()

    private val viewModel: SyncAfterDeviceTransferViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[SyncAfterDeviceTransferViewModel::class.java]
    }

    private val conflatedJob = ConflatedJob()

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        conflatedJob += viewModel.commands()
            .onEach { processCommand(it) }
            .launchIn(findViewTreeLifecycleOwner()?.lifecycleScope!!)

        configureMessage()
    }

    override fun onDetachedFromWindow() {
        conflatedJob.cancel()
        super.onDetachedFromWindow()
    }

    private fun processCommand(command: Command) {
        when (command) {
            is OpenSyncSetup -> globalActivityStarter.start(context, command.params)
            Hide -> binding.syncAfterDeviceTransferMessage.gone()
        }
    }

    private fun configureMessage() {
        with(binding.syncAfterDeviceTransferMessage) {
            setMessage(
                Message(
                    topIllustration = R.drawable.sync_feature_96,
                    title = context.getString(R.string.syncAfterDeviceTransferTitle),
                    subtitle = context.getString(R.string.syncAfterDeviceTransferBodyLoss) +
                        PARAGRAPH_BREAK +
                        context.getString(R.string.syncAfterDeviceTransferBodyEncryption),
                    action = context.getString(R.string.syncAfterDeviceTransferTurnOnSync),
                ),
            )
            onPrimaryActionClicked { viewModel.onTurnOnSyncClicked() }
            onCloseButtonClicked { viewModel.onDismissClicked() }
        }
    }

    private companion object {
        // MessageCta renders the subtitle as HTML, so plain newlines would be collapsed.
        const val PARAGRAPH_BREAK = "<br><br>"
    }
}

/**
 * Has no counterpart in the new tab page settings, so the page shows it above the sections the user can reorder.
 * Priority places it after the Indonesia message and before remote messages.
 */
@ContributesActivePlugin(
    AppScope::class,
    boundType = NewTabPageSectionPlugin::class,
    priority = NewTabPageSectionPlugin.PRIORITY_INDONESIA_MESSAGE + 1,
    featureName = "pluginSyncAfterDeviceTransferNewTabSectionPlugin",
    parentFeatureName = "pluginPointNewTabPageSectionPlugin",
)
class SyncAfterDeviceTransferNewTabSectionPlugin @Inject constructor(
    private val prompt: SyncAfterDeviceTransferPrompt,
) : NewTabPageSectionPlugin {

    override val name = "SYNC_AFTER_DEVICE_TRANSFER"

    override fun getView(context: Context): View = SyncAfterDeviceTransferNewTabSectionView(context)

    override suspend fun isUserEnabled(): Boolean = prompt.shouldShow()
}
