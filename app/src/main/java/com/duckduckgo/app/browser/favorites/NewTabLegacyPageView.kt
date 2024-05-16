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

package com.duckduckgo.app.browser.favorites

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.text.Spanned
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.core.text.toSpannable
import androidx.core.view.isGone
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.HomeBackgroundLogo
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ViewNewTabLegacyBinding
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.favorites.FavoritesQuickAccessAdapter.Companion.QUICK_ACCESS_GRID_MAX_COLUMNS
import com.duckduckgo.app.browser.favorites.FavoritesQuickAccessAdapter.Companion.QUICK_ACCESS_ITEM_MAX_SIZE_DP
import com.duckduckgo.app.browser.favorites.FavoritesQuickAccessAdapter.QuickAccessFavorite
import com.duckduckgo.app.browser.favorites.NewTabLegacyPageViewModel.Command
import com.duckduckgo.app.browser.favorites.NewTabLegacyPageViewModel.Command.DeleteFavoriteConfirmation
import com.duckduckgo.app.browser.favorites.NewTabLegacyPageViewModel.Command.DismissMessage
import com.duckduckgo.app.browser.favorites.NewTabLegacyPageViewModel.Command.LaunchAppTPOnboarding
import com.duckduckgo.app.browser.favorites.NewTabLegacyPageViewModel.Command.LaunchDefaultBrowser
import com.duckduckgo.app.browser.favorites.NewTabLegacyPageViewModel.Command.LaunchPlayStore
import com.duckduckgo.app.browser.favorites.NewTabLegacyPageViewModel.Command.LaunchScreen
import com.duckduckgo.app.browser.favorites.NewTabLegacyPageViewModel.Command.SharePromoLinkRMF
import com.duckduckgo.app.browser.favorites.NewTabLegacyPageViewModel.Command.ShowEditSavedSiteDialog
import com.duckduckgo.app.browser.favorites.NewTabLegacyPageViewModel.Command.SubmitUrl
import com.duckduckgo.app.browser.favorites.NewTabLegacyPageViewModel.ViewState
import com.duckduckgo.app.browser.remotemessage.SharePromoLinkRMFBroadCastReceiver
import com.duckduckgo.app.browser.remotemessage.asMessage
import com.duckduckgo.app.browser.viewstate.SavedSiteChangedViewState
import com.duckduckgo.app.global.view.disableAnimation
import com.duckduckgo.app.global.view.enableAnimation
import com.duckduckgo.app.global.view.launchDefaultAppActivity
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.app.tabs.ui.GridViewColumnCalculator
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.makeSnackbarWithNoBottomInset
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.mobile.android.app.tracking.ui.AppTrackingProtectionScreens.AppTrackerOnboardingActivityWithEmptyParamsParams
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.GlobalActivityStarter.DeeplinkActivityParams
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.savedsites.api.models.SavedSite
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

@InjectWith(ViewScope::class)
class NewTabLegacyPageView @JvmOverloads constructor(
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
    lateinit var gridViewColumnCalculator: GridViewColumnCalculator

    @Inject
    lateinit var faviconManager: FaviconManager

    @Inject
    lateinit var pixel: Pixel

    private var coroutineScope: CoroutineScope? = null

    private val binding: ViewNewTabLegacyBinding by viewBinding()

    private lateinit var quickAccessAdapter: FavoritesQuickAccessAdapter
    private lateinit var quickAccessItemTouchHelper: ItemTouchHelper

    private val homeBackgroundLogo by lazy { HomeBackgroundLogo(binding.ddgLogo) }

    private val viewModel: NewTabLegacyPageViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[NewTabLegacyPageViewModel::class.java]
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        ViewTreeLifecycleOwner.get(this)?.lifecycle?.addObserver(viewModel)

        @SuppressLint("NoHardcodedCoroutineDispatcher")
        coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        viewModel.viewState
            .onEach { render(it) }
            .launchIn(coroutineScope!!)

        viewModel.commands()
            .onEach { processCommands(it) }
            .launchIn(coroutineScope!!)

        configureViews()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        ViewTreeLifecycleOwner.get(this)?.lifecycle?.removeObserver(viewModel)
        coroutineScope?.cancel()
        coroutineScope = null
    }

    private fun configureViews() {
        configureHomeTabQuickAccessGrid()
        setOnClickListener(null)
    }

    private fun configureHomeTabQuickAccessGrid() {
        configureQuickAccessGridLayout(binding.quickAccessRecyclerView)
        quickAccessAdapter = createQuickAccessAdapter(originPixel = AppPixelName.FAVORITE_HOMETAB_ITEM_PRESSED) { viewHolder ->
            binding.quickAccessRecyclerView.enableAnimation()
            quickAccessItemTouchHelper.startDrag(viewHolder)
        }
        quickAccessItemTouchHelper = createQuickAccessItemHolder(binding.quickAccessRecyclerView, quickAccessAdapter)
        binding.quickAccessRecyclerView.adapter = quickAccessAdapter
        binding.quickAccessRecyclerView.disableAnimation()
    }

    private fun createQuickAccessAdapter(
        originPixel: AppPixelName,
        onMoveListener: (RecyclerView.ViewHolder) -> Unit,
    ): FavoritesQuickAccessAdapter {
        return FavoritesQuickAccessAdapter(
            ViewTreeLifecycleOwner.get(this)!!,
            faviconManager,
            onMoveListener,
            {
                pixel.fire(originPixel)
                submitUrl(it.favorite.url)
            },
            { viewModel.onEditSavedSiteRequested(it.favorite) },
            { viewModel.onDeleteFavoriteRequested(it.favorite) },
            { viewModel.onDeleteFavoriteRequested(it.favorite) },
        )
    }

    private fun createQuickAccessItemHolder(
        recyclerView: RecyclerView,
        apapter: FavoritesQuickAccessAdapter,
    ): ItemTouchHelper {
        return ItemTouchHelper(
            QuickAccessDragTouchItemListener(
                apapter,
                object : QuickAccessDragTouchItemListener.DragDropListener {
                    override fun onListChanged(listElements: List<QuickAccessFavorite>) {
                        viewModel.onQuickAccessListChanged(listElements)
                        recyclerView.disableAnimation()
                    }
                },
            ),
        ).also {
            it.attachToRecyclerView(recyclerView)
        }
    }

    private fun configureQuickAccessGridLayout(recyclerView: RecyclerView) {
        val numOfColumns = gridViewColumnCalculator.calculateNumberOfColumns(QUICK_ACCESS_ITEM_MAX_SIZE_DP, QUICK_ACCESS_GRID_MAX_COLUMNS)
        val layoutManager = GridLayoutManager(context, numOfColumns)
        recyclerView.layoutManager = layoutManager
        val sidePadding = gridViewColumnCalculator.calculateSidePadding(QUICK_ACCESS_ITEM_MAX_SIZE_DP, numOfColumns)
        recyclerView.setPadding(sidePadding, recyclerView.paddingTop, sidePadding, recyclerView.paddingBottom)
    }

    private fun render(viewState: ViewState) {
        if (viewState.message != null) {
            homeBackgroundLogo.hideLogo()
            showRemoteMessage(viewState.message, viewState.newMessage)
        } else {
            binding.messageCta.gone()
        }
        if (viewState.favourites.isEmpty()) {
            binding.quickAccessRecyclerView.gone()
            homeBackgroundLogo.showLogo()
            binding.ddgLogo.show()
        } else {
            binding.quickAccessRecyclerView.show()
            homeBackgroundLogo.hideLogo()
            quickAccessAdapter.submitList(viewState.favourites.map { QuickAccessFavorite(it) })
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
            is ShowEditSavedSiteDialog -> editSavedSite(command.savedSiteChangedViewState)
            is DeleteFavoriteConfirmation -> confirmDeleteSavedSite(
                command.savedSite,
                context.getString(R.string.favoriteDeleteConfirmationMessage).toSpannable(),
            ) {
                viewModel.onDeleteFavoriteSnackbarDismissed(command.savedSite)
            }
        }
    }

    private fun launchDefaultBrowser() {
        context.launchDefaultAppActivity()
    }

    private fun launchAppTPOnboardingScreen() {
        globalActivityStarter.start(context, AppTrackerOnboardingActivityWithEmptyParamsParams)
    }

    private fun launchSharePromoRMFPageChooser(url: String, shareTitle: String) {
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
            Timber.w(e, "Activity not found")
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

    private fun editSavedSite(savedSiteChangedViewState: SavedSiteChangedViewState) {
        // val addBookmarkDialog = EditSavedSiteDialogFragment.instance(
        //     savedSiteChangedViewState.savedSite,
        //     savedSiteChangedViewState.bookmarkFolder?.id ?: SavedSitesNames.BOOKMARKS_ROOT,
        //     savedSiteChangedViewState.bookmarkFolder?.name,
        // )
        // addBookmarkDialog.show(childFragmentManager, ADD_SAVED_SITE_FRAGMENT_TAG)
        // addBookmarkDialog.listener = viewModel
        // addBookmarkDialog.deleteBookmarkListener = viewModel
    }

    private fun confirmDeleteSavedSite(
        savedSite: SavedSite,
        message: Spanned,
        onDeleteSnackbarDismissed: (SavedSite) -> Unit,
    ) {
        binding.root.makeSnackbarWithNoBottomInset(
            message,
            Snackbar.LENGTH_LONG,
        ).setAction(R.string.fireproofWebsiteSnackbarAction) {
            viewModel.undoDelete(savedSite)
        }
            .addCallback(
                object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                    override fun onDismissed(
                        transientBottomBar: Snackbar?,
                        event: Int,
                    ) {
                        if (event != DISMISS_EVENT_ACTION) {
                            onDeleteSnackbarDismissed(savedSite)
                        }
                    }
                },
            )
            .show()
    }

    private fun showRemoteMessage(
        message: RemoteMessage,
        newMessage: Boolean,
    ) {
        val shouldRender = newMessage || binding.messageCta.isGone

        if (shouldRender) {
            binding.messageCta.show()
            viewModel.onMessageShown()
            binding.messageCta.setMessage(message.asMessage())
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
}
