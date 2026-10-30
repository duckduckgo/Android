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
import android.widget.FrameLayout
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.promotion.PasswordsScreenPromotionPlugin.Callback
import com.duckduckgo.codegen.annotations.ContributesViewModel
import com.duckduckgo.codegen.annotations.InjectWith
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.android.support.AndroidSupportInjection
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.sync.impl.ConnectedDevicesRepository
import com.duckduckgo.sync.impl.databinding.ViewSyncPasswordsSetUpPromotionBinding
import com.duckduckgo.sync.impl.pixels.SyncPixelName
import com.duckduckgo.sync.impl.pixels.SyncPixelParameters
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.PasswordsScreen
import com.duckduckgo.sync.impl.promotion.passwords.SyncPasswordsSetUpPromotionViewModel.Command
import com.duckduckgo.sync.impl.promotion.passwords.SyncPasswordsSetUpPromotionViewModel.Command.DismissPromotion
import com.duckduckgo.sync.impl.promotion.passwords.SyncPasswordsSetUpPromotionViewModel.Command.LaunchSyncSetup
import com.duckduckgo.sync.impl.ui.SyncLaunchSource
import com.duckduckgo.sync.impl.ui.dashboard.SyncActivityStartSyncFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@InjectWith(ViewScope::class)
class SyncPasswordsSetUpPromotionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    private val binding: ViewSyncPasswordsSetUpPromotionBinding by viewBinding()

    private val viewModel: SyncPasswordsSetUpPromotionViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[SyncPasswordsSetUpPromotionViewModel::class.java]
    }

    private var job: ConflatedJob = ConflatedJob()

    init {
        binding.promoMessage.apply {
            onPrimaryButtonClicked { viewModel.setUpSync() }
            onCloseButtonClicked { viewModel.dismissPromotion() }
        }
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
            DismissPromotion -> (context as? Callback)?.onPromotionDismissed()
            LaunchSyncSetup -> launchSyncSetup()
        }
    }

    private fun launchSyncSetup() {
        globalActivityStarter.start(context, SyncActivityStartSyncFlow(source = SyncLaunchSource.PromotionPasswords.value))
    }
}

@ContributesViewModel(ViewScope::class)
class SyncPasswordsSetUpPromotionViewModel @Inject constructor(
    private val connectedDevicesRepository: ConnectedDevicesRepository,
    private val promotionDataStore: SyncPromotionDataStore,
    private val pixel: Pixel,
) : ViewModel() {
    private val _commands = Channel<Command>()

    val commands = _commands.receiveAsFlow()

    private var actionJob: Job? = null

    // The passwords screen creates a new promotion view whenever its list updates, so we record one impression per screen.
    private var isImpressionRecorded = false

    init {
        viewModelScope.launch {
            // A count above 0 means this device joined a sync account, so we no longer ask the user to set up sync.
            connectedDevicesRepository.latestConnectedDeviceCountFlow().firstOrNull { it > 0 }
            _commands.send(DismissPromotion)
        }
    }

    fun recordImpression() {
        if (isImpressionRecorded) return
        isImpressionRecorded = true

        pixel.fire(SyncPixelName.SYNC_FEATURE_PROMOTION_DISPLAYED, pixelParams())
        viewModelScope.launch { promotionDataStore.recordPromoImpression(PasswordsScreen) }
    }

    fun setUpSync() {
        if (actionJob?.isActive == true) return

        actionJob = viewModelScope.launch {
            pixel.fire(SyncPixelName.SYNC_FEATURE_PROMOTION_CONFIRMED, pixelParams())
            _commands.send(LaunchSyncSetup)
        }
    }

    fun dismissPromotion() {
        if (actionJob?.isActive == true) return

        actionJob = viewModelScope.launch {
            pixel.fire(SyncPixelName.SYNC_FEATURE_PROMOTION_DISMISSED, pixelParams())
            promotionDataStore.recordPromoDismissed(PasswordsScreen)
            _commands.send(DismissPromotion)
        }
    }

    private fun pixelParams() = mapOf(SyncPixelParameters.SYNC_FEATURE_PROMOTION_SOURCE to PIXEL_SOURCE)

    sealed interface Command {
        data object DismissPromotion : Command
        data object LaunchSyncSetup : Command
    }

    private companion object {
        const val PIXEL_SOURCE = "passwords"
    }
}
