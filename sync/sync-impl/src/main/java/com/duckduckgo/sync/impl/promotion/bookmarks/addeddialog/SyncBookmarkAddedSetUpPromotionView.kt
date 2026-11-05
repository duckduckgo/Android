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

package com.duckduckgo.sync.impl.promotion.bookmarks.addeddialog

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget.FrameLayout
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.codegen.annotations.InjectWith
import com.duckduckgo.common.ui.menu.PopupMenu
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.quietlySetIsChecked
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.android.support.AndroidSupportInjection
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.sync.impl.ConnectedDevicesRepository
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.ViewSyncBookmarkAddedSetUpPromotionBinding
import com.duckduckgo.sync.impl.pixels.SyncPixelName
import com.duckduckgo.sync.impl.pixels.SyncPixelParameters
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.BookmarkAddedDialog
import com.duckduckgo.sync.impl.promotion.bookmarks.addeddialog.SyncBookmarkAddedSetUpPromotionInteractor.Command
import com.duckduckgo.sync.impl.promotion.bookmarks.addeddialog.SyncBookmarkAddedSetUpPromotionInteractor.Command.DismissPromotion
import com.duckduckgo.sync.impl.promotion.bookmarks.addeddialog.SyncBookmarkAddedSetUpPromotionInteractor.Command.LaunchSyncSetup
import com.duckduckgo.sync.impl.ui.SyncLaunchSource
import com.duckduckgo.sync.impl.ui.dashboard.SyncActivityStartSyncFlow
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@InjectWith(ViewScope::class)
class SyncBookmarkAddedSetUpPromotionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    @Inject
    lateinit var interactorFactory: SyncBookmarkAddedSetUpPromotionInteractor.Factory

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    private val binding: ViewSyncBookmarkAddedSetUpPromotionBinding by viewBinding()

    // We never show the switch as on: turning it on only starts the setup, and Sync being enabled removes the promotion.
    private val switchListener: OnCheckedChangeListener = OnCheckedChangeListener { _, _ ->
        binding.promoSwitch.quietlySetIsChecked(false, switchListener)
        interactor?.setUpSync()
    }

    // We don't use a ViewModel because the bookmark added dialog has no ViewModelStoreOwner, and falling back to the activity
    // would keep the ViewModel and its work alive long after the dialog is gone. An interactor tied to attach and detach ends with the dialog.
    private var interactor: SyncBookmarkAddedSetUpPromotionInteractor? = null

    init {
        binding.promoCard.clipToOutline = true
        binding.promoCard.setOnClickListener { interactor?.setUpSync() }
        binding.promoSwitch.setOnCheckedChangeListener(switchListener)
        binding.overflowIcon.setOnClickListener { showOverflowMenu(it) }
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        interactor?.cancel()
        val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.main())
        interactor = interactorFactory.create(scope)
        interactor?.commands
            ?.onEach { processCommand(it) }
            ?.launchIn(scope)
        interactor?.recordImpression()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        interactor?.cancel()
        interactor = null
    }

    private fun showOverflowMenu(anchor: View) {
        val popupMenu = PopupMenu(LayoutInflater.from(context), R.layout.popup_window_sync_bookmark_added_set_up_promotion_menu)
        popupMenu.apply {
            onMenuItemClicked(contentView.findViewById(R.id.hide)) { interactor?.dismissPromotion() }
            onMenuItemClicked(contentView.findViewById(R.id.turnOnSync)) { interactor?.setUpSync() }
        }
        popupMenu.show(this, anchor)
    }

    private fun processCommand(command: Command) {
        when (command) {
            DismissPromotion -> gone()
            LaunchSyncSetup -> launchSyncSetup()
        }
    }

    private fun launchSyncSetup() {
        globalActivityStarter.start(context, SyncActivityStartSyncFlow(source = SyncLaunchSource.PromotionBookmarkAddedDialog))
    }
}

class SyncBookmarkAddedSetUpPromotionInteractor @AssistedInject constructor(
    @Assisted private val scope: CoroutineScope,
    private val connectedDevicesRepository: ConnectedDevicesRepository,
    private val promotionDataStore: SyncPromotionDataStore,
    private val pixel: Pixel,
) {
    private val _commands = Channel<Command>()

    val commands = _commands.receiveAsFlow()

    private var actionJob: Job? = null

    init {
        scope.launch {
            // A count above 0 means this device joined a sync account, so we no longer ask the user to set up sync.
            connectedDevicesRepository.latestConnectedDeviceCountFlow().firstOrNull { it > 0 }
            _commands.send(DismissPromotion)
        }
    }

    fun cancel() {
        scope.cancel()
    }

    fun recordImpression() {
        pixel.fire(SyncPixelName.SYNC_FEATURE_PROMOTION_DISPLAYED, pixelParams())
        scope.launch { promotionDataStore.recordPromoImpression(BookmarkAddedDialog) }
    }

    fun setUpSync() {
        if (actionJob?.isActive == true) return

        actionJob = scope.launch {
            pixel.fire(SyncPixelName.SYNC_FEATURE_PROMOTION_CONFIRMED, pixelParams())
            _commands.send(LaunchSyncSetup)
        }
    }

    fun dismissPromotion() {
        if (actionJob?.isActive == true) return

        actionJob = scope.launch {
            pixel.fire(SyncPixelName.SYNC_FEATURE_PROMOTION_DISMISSED, pixelParams())
            promotionDataStore.recordPromoDismissed(BookmarkAddedDialog)
            _commands.send(DismissPromotion)
        }
    }

    private fun pixelParams() = mapOf(SyncPixelParameters.SYNC_FEATURE_PROMOTION_SOURCE to PIXEL_SOURCE)

    sealed interface Command {
        data object DismissPromotion : Command
        data object LaunchSyncSetup : Command
    }

    @AssistedFactory
    interface Factory {
        fun create(scope: CoroutineScope): SyncBookmarkAddedSetUpPromotionInteractor
    }

    private companion object {
        const val PIXEL_SOURCE = "bookmark_added_dialog"
    }
}
