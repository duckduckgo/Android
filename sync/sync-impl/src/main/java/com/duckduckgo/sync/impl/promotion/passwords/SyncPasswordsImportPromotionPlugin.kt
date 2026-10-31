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

package com.duckduckgo.sync.impl.promotion.passwords

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.promotion.PasswordsImportScreenPromotionPlugin
import com.duckduckgo.autofill.api.promotion.PasswordsImportScreenPromotionPlugin.Callback
import com.duckduckgo.autofill.api.promotion.PasswordsImportScreenPromotionPlugin.Companion.PRIORITY_KEY_SYNC_PROMO
import com.duckduckgo.autofill.api.promotion.PasswordsImportScreenPromotionPlugin.ImportResult
import com.duckduckgo.codegen.annotations.ContributesViewModel
import com.duckduckgo.codegen.annotations.InjectWith
import com.duckduckgo.codegen.annotations.PriorityKey
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.android.support.AndroidSupportInjection
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.sync.impl.databinding.ViewSyncPasswordsImportPromotionBinding
import com.duckduckgo.sync.impl.pixels.SyncPixelName
import com.duckduckgo.sync.impl.pixels.SyncPixelParameters
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.PasswordsImport
import com.duckduckgo.sync.impl.promotion.passwords.SyncPasswordsImportPromotionViewModel.Command
import com.duckduckgo.sync.impl.promotion.passwords.SyncPasswordsImportPromotionViewModel.Command.LaunchSyncSetup
import com.duckduckgo.sync.impl.ui.SyncLaunchSource
import com.duckduckgo.sync.impl.ui.dashboard.SyncActivityStartSyncFlow
import dev.zacsweers.metro.ContributesIntoMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesIntoMap(scope = AppScope::class)
@PriorityKey(PRIORITY_KEY_SYNC_PROMO)
class SyncPasswordsImportPromotionPlugin @Inject constructor(
    private val promotion: PasswordsImportSyncPromotion,
) : PasswordsImportScreenPromotionPlugin {

    override suspend fun isEligible(data: ImportResult): Boolean {
        return promotion.canShowPromotion(data.successCount)
    }

    override fun createView(
        context: Context,
        callback: Callback,
    ): View {
        return SyncPasswordsImportPromotionView(context).apply {
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            this.callback = callback
        }
    }
}

@InjectWith(ViewScope::class)
class SyncPasswordsImportPromotionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    var callback: Callback? = null

    private val binding: ViewSyncPasswordsImportPromotionBinding by viewBinding()

    private val viewModel: SyncPasswordsImportPromotionViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[SyncPasswordsImportPromotionViewModel::class.java]
    }

    private var job: ConflatedJob = ConflatedJob()

    init {
        binding.promoMessage.onPrimaryButtonClicked { viewModel.setUpSync() }
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        job += viewModel.commands
            .onEach { processCommand(it) }
            .launchIn(findViewTreeLifecycleOwner()?.lifecycleScope!!)

        viewModel.recordImpression()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        job.cancel()
    }

    private fun processCommand(command: Command) {
        when (command) {
            LaunchSyncSetup -> launchSyncSetup()
        }
    }

    private fun launchSyncSetup() {
        globalActivityStarter.start(context, SyncActivityStartSyncFlow(source = SyncLaunchSource.PromotionPasswordsImport.value))
        callback?.dismissHost()
    }
}

@ContributesViewModel(ViewScope::class)
class SyncPasswordsImportPromotionViewModel @Inject constructor(
    private val promotionDataStore: SyncPromotionDataStore,
    private val pixel: Pixel,
) : ViewModel() {
    private val _commands = Channel<Command>()

    val commands = _commands.receiveAsFlow()

    private var actionJob: Job? = null

    fun recordImpression() {
        pixel.fire(SyncPixelName.SYNC_FEATURE_PROMOTION_DISPLAYED, pixelParams())
        viewModelScope.launch { promotionDataStore.recordPromoImpression(PasswordsImport) }
    }

    fun setUpSync() {
        if (actionJob?.isActive == true) return

        actionJob = viewModelScope.launch {
            pixel.fire(SyncPixelName.SYNC_FEATURE_PROMOTION_CONFIRMED, pixelParams())
            _commands.send(LaunchSyncSetup)
        }
    }

    private fun pixelParams() = mapOf(SyncPixelParameters.SYNC_FEATURE_PROMOTION_SOURCE to PIXEL_SOURCE)

    sealed interface Command {
        data object LaunchSyncSetup : Command
    }

    private companion object {
        const val PIXEL_SOURCE = "passwords_import"
    }
}
