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

package com.duckduckgo.sync.impl.promotion.bookmarks

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.codegen.annotations.ContributesViewModel
import com.duckduckgo.codegen.annotations.InjectWith
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.android.support.AndroidSupportInjection
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.savedsites.api.promotion.BookmarksScreenPromotionPlugin
import com.duckduckgo.savedsites.api.promotion.BookmarksScreenPromotionPlugin.Callback
import com.duckduckgo.sync.impl.ConnectedDevicesRepository
import com.duckduckgo.sync.impl.databinding.ViewSyncBookmarksMultiDevicePromotionBinding
import com.duckduckgo.sync.impl.pixels.SyncPixelName
import com.duckduckgo.sync.impl.pixels.SyncPixelParameters
import com.duckduckgo.sync.impl.promotion.SyncGetOnOtherPlatformsLaunchSource
import com.duckduckgo.sync.impl.promotion.SyncGetOnOtherPlatformsParams
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.BookmarksScreenMultiDevice
import com.duckduckgo.sync.impl.promotion.bookmarks.SyncBookmarksMultiDevicePromotionViewModel.Command
import com.duckduckgo.sync.impl.promotion.bookmarks.SyncBookmarksMultiDevicePromotionViewModel.Command.DismissPromotion
import com.duckduckgo.sync.impl.promotion.bookmarks.SyncBookmarksMultiDevicePromotionViewModel.Command.LaunchGetDesktopApp
import com.duckduckgo.sync.impl.promotion.bookmarks.SyncBookmarksMultiDevicePromotionViewModel.Command.LaunchSyncSettings
import com.duckduckgo.sync.impl.ui.SyncLaunchSource
import com.duckduckgo.sync.impl.ui.dashboard.SyncActivityStartSyncFlow
import dev.zacsweers.metro.ContributesIntoSet
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesIntoSet(scope = AppScope::class)
class SyncBookmarksMultiDevicePromotionPlugin @Inject constructor(
    private val promotion: SyncBookmarksMultiDevicePromotion,
) : BookmarksScreenPromotionPlugin {

    override suspend fun getView(context: Context, numberSavedBookmarks: Int): View? {
        return if (promotion.canShowPromotion(numberSavedBookmarks)) {
            SyncBookmarksMultiDevicePromotionView(context)
        } else {
            null
        }
    }
}

@InjectWith(ViewScope::class)
class SyncBookmarksMultiDevicePromotionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    private val binding: ViewSyncBookmarksMultiDevicePromotionBinding by viewBinding()

    private val viewModel: SyncBookmarksMultiDevicePromotionViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[SyncBookmarksMultiDevicePromotionViewModel::class.java]
    }

    private var job: ConflatedJob = ConflatedJob()

    init {
        binding.promoMessage.apply {
            onPrimaryButtonClicked { viewModel.syncBookmarks() }
            onSecondaryButtonClicked { viewModel.getDesktopApp() }
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
            LaunchSyncSettings -> launchSyncSettings()
            LaunchGetDesktopApp -> launchGetDesktopApp()
        }
    }

    private fun launchSyncSettings() {
        globalActivityStarter.start(context, SyncActivityStartSyncFlow(source = SyncLaunchSource.PromotionBookmarksMultiDevice.value))
    }

    private fun launchGetDesktopApp() {
        globalActivityStarter.start(context, SyncGetOnOtherPlatformsParams(source = SyncGetOnOtherPlatformsLaunchSource.SOURCE_PROMOTION_BOOKMARKS))
    }
}

@ContributesViewModel(ViewScope::class)
class SyncBookmarksMultiDevicePromotionViewModel @Inject constructor(
    private val connectedDevicesRepository: ConnectedDevicesRepository,
    private val promotionDataStore: SyncPromotionDataStore,
    private val pixel: Pixel,
) : ViewModel() {
    private val _commands = Channel<Command>()

    val commands = _commands.receiveAsFlow()

    private var actionJob: Job? = null

    init {
        viewModelScope.launch {
            // We only invite users syncing with this device alone, so another device joining or this device leaving ends the promotion.
            connectedDevicesRepository.latestConnectedDeviceCountFlow().firstOrNull { it != 1 }
            _commands.send(DismissPromotion)
        }
    }

    fun recordImpression() {
        pixel.fire(SyncPixelName.SYNC_FEATURE_PROMOTION_DISPLAYED, pixelParams())
        viewModelScope.launch { promotionDataStore.recordPromoImpression(BookmarksScreenMultiDevice) }
    }

    fun syncBookmarks() {
        if (actionJob?.isActive == true) return

        actionJob = viewModelScope.launch {
            pixel.fire(SyncPixelName.SYNC_FEATURE_PROMOTION_CONFIRMED, pixelParams())
            _commands.send(LaunchSyncSettings)
        }
    }

    fun getDesktopApp() {
        if (actionJob?.isActive == true) return

        actionJob = viewModelScope.launch {
            _commands.send(LaunchGetDesktopApp)
        }
    }

    fun dismissPromotion() {
        if (actionJob?.isActive == true) return

        actionJob = viewModelScope.launch {
            pixel.fire(SyncPixelName.SYNC_FEATURE_PROMOTION_DISMISSED, pixelParams())
            promotionDataStore.recordPromoDismissed(BookmarksScreenMultiDevice)
            _commands.send(DismissPromotion)
        }
    }

    private fun pixelParams() = mapOf(SyncPixelParameters.SYNC_FEATURE_PROMOTION_SOURCE to PIXEL_SOURCE)

    sealed interface Command {
        data object DismissPromotion : Command
        data object LaunchSyncSettings : Command
        data object LaunchGetDesktopApp : Command
    }

    private companion object {
        const val PIXEL_SOURCE = "bookmarks_multi_device"
    }
}
