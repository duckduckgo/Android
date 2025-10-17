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

package com.duckduckgo.savedsites.impl.bookmarks

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.os.BundleCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.SimpleItemAnimator
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.autofill.api.ImportFromGoogle
import com.duckduckgo.autofill.api.ImportFromGoogle.ImportFromGoogleResult
import com.duckduckgo.browser.api.ui.BrowserScreens.BookmarksScreenNoParams
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.menu.PopupMenu
import com.duckduckgo.common.ui.view.SearchBar
import com.duckduckgo.common.ui.view.button.ButtonType.DESTRUCTIVE
import com.duckduckgo.common.ui.view.button.ButtonType.GHOST_ALT
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.saved.sites.impl.R
import com.duckduckgo.saved.sites.impl.databinding.ActivityBookmarksBinding
import com.duckduckgo.saved.sites.impl.databinding.ContentBookmarksBinding
import com.duckduckgo.saved.sites.impl.databinding.PopupBookmarksMenuBinding
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.savedsites.api.promotion.BookmarksScreenPromotionPlugin
import com.duckduckgo.savedsites.api.service.ExportSavedSitesResult
import com.duckduckgo.savedsites.api.service.ImportSavedSitesResult
import com.duckduckgo.savedsites.impl.BookmarksSortingFeature
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.ConfirmDeleteBookmarkFolder
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.ConfirmDeleteSavedSite
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.DeleteBookmarkFolder
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.ExportedSavedSites
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.ImportedSavedSites
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.LaunchAddFolder
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.LaunchBookmarkExport
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.LaunchBookmarkImportFile
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.LaunchSyncSettings
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.OpenBookmarkFolder
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.OpenSavedSite
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.ReevalutePromotions
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.ShowBookmarkImportDialog
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.ShowBrowserMenu
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.ShowEditBookmarkFolder
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.ShowEditSavedSite
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.ShowFaviconsPrompt
import com.duckduckgo.savedsites.impl.dialogs.AddBookmarkFolderDialogFragment
import com.duckduckgo.savedsites.impl.dialogs.EditBookmarkFolderDialogFragment
import com.duckduckgo.savedsites.impl.dialogs.EditSavedSiteDialogFragment
import com.duckduckgo.savedsites.impl.folders.BookmarkFoldersActivity.Companion.KEY_BOOKMARK_FOLDER_ID
import com.duckduckgo.savedsites.impl.importing.ImportFromGoogleBookmarksPreImportDialog
import com.duckduckgo.savedsites.impl.importing.ImportFromGoogleBookmarksPreImportDialog.Companion.BUNDLE_RESULT_KEY
import com.duckduckgo.savedsites.impl.importing.ImportFromGoogleBookmarksPreImportDialog.Companion.FRAGMENT_RESULT_KEY
import com.duckduckgo.savedsites.impl.importing.ImportFromGoogleBookmarksPreImportDialog.ImportBookmarksPreImportResult
import com.duckduckgo.savedsites.impl.store.SortingMode
import com.duckduckgo.savedsites.impl.store.SortingMode.MANUAL
import com.duckduckgo.savedsites.impl.store.SortingMode.NAME
import com.duckduckgo.sync.api.SyncActivityWithEmptyParams
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as commonR

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(BookmarksScreenNoParams::class, screenName = "bookmarks")
class BookmarksActivity : DuckDuckGoActivity(), BookmarksScreenPromotionPlugin.Callback {

    @Inject
    lateinit var faviconManager: FaviconManager

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var browserNav: BrowserNav

    @Inject
    lateinit var screenPromotionPlugins: PluginPoint<BookmarksScreenPromotionPlugin>

    @Inject
    lateinit var dispatchers: DispatcherProvider

    @Inject
    lateinit var bookmarksSortingFeature: BookmarksSortingFeature

    @Inject
    lateinit var importFromGoogle: ImportFromGoogle

    private lateinit var bookmarksAdapter: BookmarksAdapter
    private lateinit var searchListener: BookmarksQueryListener

    private lateinit var itemTouchHelperCallback: BookmarkItemTouchHelperCallback
    private lateinit var itemTouchHelper: ItemTouchHelper

    private var deleteDialog: AlertDialog? = null
    private var searchMenuItem: MenuItem? = null
    private var exportMenuItem: MenuItem? = null

    private val viewModel: BookmarksViewModel by bindViewModel()

    private val binding: ActivityBookmarksBinding by viewBinding()
    private lateinit var contentBookmarksBinding: ContentBookmarksBinding

    private val toolbar
        get() =
            if (bookmarksSortingFeature.self().isEnabled()) {
                binding.toolbarSorting
            } else {
                binding.toolbar
            }

    private val searchBar
        get() =
            if (bookmarksSortingFeature.self().isEnabled()) {
                binding.searchBarSorting
            } else {
                binding.searchBar
            }

    private val startBookmarkFoldersActivityForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == RESULT_OK) {
                result.data?.getStringExtra(SAVED_SITE_URL_EXTRA)?.let {
                    viewModel.onBookmarkFoldersActivityResult(it)
                }
            }
        }

    private val syncActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.userReturnedFromSyncSettings()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        contentBookmarksBinding = ContentBookmarksBinding.bind(binding.root)
        setContentView(binding.root)
        configureToolbar()

        setupBookmarksRecycler()
        observeViewModel()
        observeItemsToDisplay()
        initializeSearchBar()
        configureImportBookmarksDialog()

        viewModel.fetchBookmarksAndFolders(getParentFolderId())
    }

    private fun configureImportBookmarksDialog() {
        supportFragmentManager.setFragmentResultListener(FRAGMENT_RESULT_KEY, this) { _, bundle ->
            val result = BundleCompat.getParcelable(bundle, BUNDLE_RESULT_KEY, ImportBookmarksPreImportResult::class.java)
            when (result) {
                ImportBookmarksPreImportResult.ImportBookmarksFromGoogle -> launchBookmarkImportWebFlow()
                ImportBookmarksPreImportResult.SelectBookmarksFile -> launchBookmarkImportChooseFile()
                ImportBookmarksPreImportResult.Cancel, null -> { /* No-op */ }
            }
        }
    }

    private fun configureToolbar() {
        if (bookmarksSortingFeature.self().isEnabled()) {
            binding.browserMenu.setOnClickListener {
                viewModel.onBrowserMenuPressed()
            }
            binding.searchMenu.setOnClickListener {
                showSearchBar()
            }
            binding.addFolderMenu.setOnClickListener {
                viewModel.onAddFolderClicked()
            }
            binding.appBarLayout.gone()
        } else {
            binding.appBarLayoutSorting.gone()
        }

        setupToolbar(toolbar)
        setToolbarTitle(getParentFolderName())
    }

    private fun setToolbarTitle(title: String) {
        if (bookmarksSortingFeature.self().isEnabled()) {
            binding.toolbarTitle.text = title
        } else {
            supportActionBar?.title = title
        }
    }

    private fun getParentFolderName() =
        intent.extras?.getString(KEY_BOOKMARK_FOLDER_NAME)
            ?: getString(R.string.bookmarksActivityTitle)

    private fun getParentFolderId() = intent.extras?.getString(KEY_BOOKMARK_FOLDER_ID)
        ?: SavedSitesNames.BOOKMARKS_ROOT

    private fun configurePromotionsContainer() {
        lifecycleScope.launch(dispatchers.main()) {
            val state = viewModel.viewState.value

            if (state?.canShowPromo == false) {
                contentBookmarksBinding.promotionContainer.gone()
                return@launch
            }

            val promotionView = contentBookmarksBinding.promotionContainer.getFirstEligiblePromo(numberBookmarks = state?.bookmarkItems?.size ?: 0)
            if (promotionView == null) {
                contentBookmarksBinding.promotionContainer.gone()
            } else {
                contentBookmarksBinding.promotionContainer.showPromotion(promotionView)
            }
        }
    }

    private fun ViewGroup.showPromotion(promotionView: View) {
        val alreadyShowing = if (this.childCount == 0) {
            false
        } else {
            promotionView::class.qualifiedName == this.children.first()::class.qualifiedName
        }

        if (!alreadyShowing) {
            this.removeAllViews()
            this.addView(promotionView)
        }

        this.show()
    }

    private suspend fun ViewGroup.getFirstEligiblePromo(numberBookmarks: Int): View? {
        val context = this.context ?: return null
        return screenPromotionPlugins.getPlugins().firstNotNullOfOrNull { it.getView(context, numberBookmarks) }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            IMPORT_BOOKMARKS_REQUEST_CODE -> {
                if (resultCode == RESULT_OK) {
                    val selectedFile = data?.data
                    if (selectedFile != null) {
                        dismissImportBookmarksDialog()
                        viewModel.importBookmarks(selectedFile)
                    }
                }
            }

            EXPORT_BOOKMARKS_REQUEST_CODE -> {
                if (resultCode == RESULT_OK) {
                    val selectedFile = data?.data
                    if (selectedFile != null) {
                        viewModel.exportSavedSites(selectedFile)
                    }
                }
            }

            GOOGLE_BOOKMARK_IMPORT_REQUEST_CODE -> onProcessGoogleBookmarkImportResult(data)
        }
    }

    private fun onProcessGoogleBookmarkImportResult(data: Intent?) {
        lifecycleScope.launch {
            val result = importFromGoogle.parseResult(data)

            when (result) {
                is ImportFromGoogleResult.Success -> dismissImportBookmarksDialog()
                is ImportFromGoogleResult.Error -> dismissImportBookmarksDialog()
                is ImportFromGoogleResult.UserCancelled -> {
                    // User cancelled - no action needed
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        return if (bookmarksSortingFeature.self().isEnabled()) {
            false
        } else {
            menuInflater.inflate(R.menu.bookmark_activity_menu, menu)
            true
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.bookmark_import -> {
                viewModel.onImportBookmarksClicked()
            }

            R.id.bookmark_export -> {
                viewModel.onExportBookmarksClicked()
            }

            R.id.action_add_folder -> {
                viewModel.onAddFolderClicked()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        searchMenuItem = menu.findItem(R.id.action_search)
        exportMenuItem = menu.findItem(R.id.bookmark_export)
        if (viewModel.viewState.value?.bookmarkItems?.isEmpty() == true) {
            val textColorAttr = commonR.attr.daxColorTextDisabled
            val spannable = SpannableString(getString(R.string.exportBookmarksMenu))
            spannable.setSpan(ForegroundColorSpan(binding.root.context.getColorFromAttr(textColorAttr)), 0, spannable.length, 0)
            exportMenuItem?.title = spannable
            exportMenuItem?.isEnabled = false
        }
        searchMenuItem?.isVisible = viewModel.viewState.value?.enableSearch == true || getParentFolderId() != SavedSitesNames.BOOKMARKS_ROOT
        initializeSearchBar()
        return super.onPrepareOptionsMenu(menu)
    }

    private fun setupBookmarksRecycler() {
        bookmarksAdapter = BookmarksAdapter(
            viewModel,
            lifecycleOwner = this,
            dispatcherProvider = dispatchers,
            faviconManager,
            onBookmarkClick = { bookmark ->
                viewModel.onSelected(bookmark)
            },
            onBookmarkOverflowClick = { anchor, bookmark ->
                showBookmarkOverFlowMenu(anchor, bookmark)
            },
            onLongClick = {
                if (viewModel.viewState.value?.sortingMode == NAME) {
                    Snackbar.make(binding.root, R.string.popupBookmarksPreventReordering, Snackbar.LENGTH_LONG).show()
                }
            },
            onBookmarkFolderClick = { anchor, bookmarkFolder ->
                viewModel.onBookmarkFolderSelected(bookmarkFolder)
            },
            onBookmarkFolderOverflowClick = { anchor, bookmarkFolder ->
                showFolderOverflowMenu(anchor, bookmarkFolder)
            },
        )
        contentBookmarksBinding.recycler.adapter = bookmarksAdapter

        itemTouchHelperCallback = BookmarkItemTouchHelperCallback(bookmarksAdapter)
        itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)

        (contentBookmarksBinding.recycler.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
    }

    private fun observeViewModel() {
        viewModel.viewState.observe(this) { viewState ->
            viewState?.let { state ->
                if (state.sortingMode == MANUAL) {
                    bookmarksAdapter.isReorderingEnabled = true
                    itemTouchHelper.attachToRecyclerView(contentBookmarksBinding.recycler)
                } else {
                    bookmarksAdapter.isReorderingEnabled = false
                    itemTouchHelper.attachToRecyclerView(null)
                }

                binding.searchMenu.isVisible =
                    viewModel.viewState.value?.enableSearch == true || getParentFolderId() != SavedSitesNames.BOOKMARKS_ROOT
                configurePromotionsContainer()
            }
        }

        viewModel.command.observe(
            this,
        ) {
            when (it) {
                is ConfirmDeleteSavedSite -> confirmDeleteSavedSite(it.savedSite)
                is OpenSavedSite -> openSavedSite(it.savedSiteUrl)
                is ShowEditSavedSite -> showEditSavedSiteDialog(it.savedSite)
                is ImportedSavedSites -> showImportedSavedSites(it.importSavedSitesResult)
                is ExportedSavedSites -> showExportedSavedSites(it.exportSavedSitesResult)
                is OpenBookmarkFolder -> openBookmarkFolder(it.bookmarkFolder)
                is ShowEditBookmarkFolder -> editBookmarkFolder(it.bookmarkFolder)
                is DeleteBookmarkFolder -> deleteBookmarkFolder(it.bookmarkFolder)
                is ConfirmDeleteBookmarkFolder -> confirmDeleteBookmarkFolder(it.bookmarkFolder)
                is ShowFaviconsPrompt -> showFaviconsPrompt()
                is LaunchSyncSettings -> launchSyncSettings()
                is ReevalutePromotions -> configurePromotionsContainer()
                is ShowBrowserMenu -> showBookmarksPopupMenu(it.buttonsDisabled, it.sortingMode)
                is LaunchBookmarkImportFile -> launchBookmarkImportChooseFile()
                is ShowBookmarkImportDialog -> showBookmarkImportDialog()
                is LaunchBookmarkExport -> launchBookmarkExport()
                is LaunchAddFolder -> launchAddFolder()
            }
        }
    }

    private fun showBookmarkImportDialog() {
        val dialog = ImportFromGoogleBookmarksPreImportDialog.instance()
        dialog.show(supportFragmentManager, DIALOG_TAG_IMPORT_BOOKMARKS)
    }

    private fun dismissImportBookmarksDialog() {
        val dialog = supportFragmentManager.findFragmentByTag(DIALOG_TAG_IMPORT_BOOKMARKS)
        dialog?.let {
            if (it is DialogFragment) {
                it.dismiss()
            }
        }
    }

    private fun observeItemsToDisplay() {
        viewModel.itemsToDisplay.onEach { items ->
            bookmarksAdapter.setItems(
                items,
                showEmptyHint = items.isEmpty() && getParentFolderId() == SavedSitesNames.BOOKMARKS_ROOT,
                showEmptySearchHint = false,
                detectMoves = true,
            )
            exportMenuItem?.isEnabled = items.isNotEmpty()
        }.launchIn(lifecycleScope)
    }

    private fun launchSyncSettings() {
        val intent = globalActivityStarter.startIntent(this, SyncActivityWithEmptyParams)
        syncActivityLauncher.launch(intent)
    }

    private fun showFaviconsPrompt() {
        val faviconPrompt = FaviconPromptSheet.Builder(this)
            .addEventListener(
                object : FaviconPromptSheet.EventListener() {
                    override fun onFaviconsFetchingPromptDismissed(fetchingEnabled: Boolean) {
                        viewModel.onFaviconsFetchingEnabled(fetchingEnabled, getParentFolderId())
                    }
                },
            )
        faviconPrompt.show()
    }

    @Suppress("DEPRECATION")
    private fun launchBookmarkImportWebFlow() {
        lifecycleScope.launch {
            val intent = importFromGoogle.getBookmarksImportLaunchIntent()
            if (intent != null) {
                startActivityForResult(intent, GOOGLE_BOOKMARK_IMPORT_REQUEST_CODE)
            } else {
                showMessage(getString(R.string.importBookmarksError))
            }
        }
    }

    private fun launchBookmarkImportChooseFile() {
        val intent = Intent()
            .setType("text/html")
            .setAction(Intent.ACTION_GET_CONTENT)

        startActivityForResult(
            Intent.createChooser(
                intent,
                getString(R.string.importBookmarksFileTitle),
            ),
            IMPORT_BOOKMARKS_REQUEST_CODE,
        )
    }

    private fun launchBookmarkExport() {
        val intent = Intent()
            .setType("text/html")
            .setAction(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_TITLE, EXPORT_BOOKMARKS_FILE_NAME)

        startActivityForResult(intent, EXPORT_BOOKMARKS_REQUEST_CODE)
    }

    private fun launchAddFolder() {
        val parentId = getParentFolderId()
        val parentFolderName = getParentFolderName()
        val dialog = AddBookmarkFolderDialogFragment.instance(parentId, parentFolderName)
        dialog.show(supportFragmentManager, ADD_BOOKMARK_FOLDER_FRAGMENT_TAG)
        dialog.listener = viewModel
    }

    private fun showImportedSavedSites(result: ImportSavedSitesResult) {
        when (result) {
            is ImportSavedSitesResult.Error -> {
                showMessage(getString(R.string.importBookmarksError))
            }

            is ImportSavedSitesResult.Success -> {
                if (result.savedSites.isEmpty()) {
                    showMessage(getString(R.string.importBookmarksEmpty))
                } else {
                    showMessage(getString(R.string.importBookmarksSuccess, result.savedSites.size))
                }
            }
        }
    }

    private fun showExportedSavedSites(result: ExportSavedSitesResult) {
        when (result) {
            is ExportSavedSitesResult.Error -> {
                showMessage(getString(R.string.exportBookmarksError))
            }

            ExportSavedSitesResult.NoSavedSitesExported -> {
                showMessage(getString(R.string.exportBookmarksEmpty))
            }

            ExportSavedSitesResult.Success -> {
                showMessage(getString(R.string.exportBookmarksSuccess))
            }
        }
    }

    private fun showMessage(message: String) {
        Snackbar.make(
            binding.root,
            message,
            Snackbar.LENGTH_LONG,
        ).show()
    }

    private fun initializeSearchBar() {
        searchListener = BookmarksQueryListener(viewModel, bookmarksAdapter, dispatchers)
        if (bookmarksSortingFeature.self().isEnabled()) {
            binding.searchMenu.setOnClickListener {
                showSearchBar()
            }
            binding.searchBar.gone()
        } else {
            searchMenuItem?.setOnMenuItemClickListener {
                showSearchBar()
                return@setOnMenuItemClickListener true
            }
            binding.searchBarSorting.gone()
        }

        searchBar.onAction {
            when (it) {
                is SearchBar.Action.PerformUpAction -> hideSearchBar()
                is SearchBar.Action.PerformSearch -> if (this::searchListener.isInitialized) {
                    searchListener.onQueryTextChange(it.searchText)
                }
            }
        }
    }

    override fun onBackPressed() {
        if (searchBar.isVisible) {
            hideSearchBar()
        } else {
            super.onBackPressed()
        }
    }

    private fun showSearchBar() {
        viewModel.fetchAllBookmarksAndFolders()
        toolbar.gone()
        searchBar.handle(SearchBar.Event.ShowSearchBar)
        bookmarksAdapter.isInSearchMode = true
    }

    private fun showBookmarksPopupMenu(
        buttonsDisabled: Boolean,
        sortingMode: SortingMode,
    ) {
        val popupMenu = PopupMenu(
            layoutInflater,
            R.layout.popup_bookmarks_menu,
        )

        val popupBinding = PopupBookmarksMenuBinding.bind(popupMenu.contentView)

        if (buttonsDisabled) {
            popupBinding.exportBookmarks.setDisabled()
            popupBinding.sortManually.setDisabled()
            popupBinding.sortByName.setDisabled()
        } else {
            when (sortingMode) {
                MANUAL -> {
                    popupBinding.sortManually.setTrailingIconResource(com.duckduckgo.mobile.android.R.drawable.ic_check_24)
                }
                NAME -> {
                    popupBinding.sortByName.setTrailingIconResource(com.duckduckgo.mobile.android.R.drawable.ic_check_24)
                }
            }
        }

        popupMenu.apply {
            onMenuItemClicked(popupBinding.sortByName) {
                viewModel.onSortingModeSelected(NAME)
            }
            onMenuItemClicked(popupBinding.sortManually) {
                viewModel.onSortingModeSelected(MANUAL)
            }
            onMenuItemClicked(popupBinding.importBookmarks) {
                viewModel.onImportBookmarksClicked()
            }
            onMenuItemClicked(popupBinding.exportBookmarks) {
                viewModel.onExportBookmarksClicked()
            }
        }
        popupMenu.show(binding.root, binding.browserMenu)
    }

    private fun hideSearchBar() {
        toolbar.show()
        viewModel.fetchBookmarksAndFolders(getParentFolderId())
        searchBar.handle(SearchBar.Event.DismissSearchBar)
        bookmarksAdapter.isInSearchMode = false
    }

    private fun showEditSavedSiteDialog(savedSite: SavedSite) {
        val dialog = EditSavedSiteDialogFragment.instance(savedSite, getParentFolderId(), getParentFolderName())
        dialog.show(supportFragmentManager, EDIT_BOOKMARK_FRAGMENT_TAG)
        dialog.listener = viewModel
        dialog.deleteBookmarkListener = viewModel
    }

    private fun openSavedSite(url: String) {
        if (intent.action == Intent.ACTION_VIEW) {
            startActivity(browserNav.openInNewTab(this, url))
        } else {
            val resultValue = Intent()
            resultValue.putExtra(SAVED_SITE_URL_EXTRA, url)
            setResult(RESULT_OK, resultValue)
        }
        finish()
    }

    private fun confirmDeleteSavedSite(savedSite: SavedSite) {
        val message = getString(R.string.bookmarkDeleteConfirmationMessage, savedSite.title).html(this)
        Snackbar.make(
            binding.root,
            message,
            Snackbar.LENGTH_LONG,
        )
            .setAction(R.string.fireproofWebsiteSnackbarAction) {
                viewModel.undoDelete(savedSite)
            }
            .addCallback(
                object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                    override fun onDismissed(
                        transientBottomBar: Snackbar?,
                        event: Int,
                    ) {
                        if (event != DISMISS_EVENT_ACTION) {
                            viewModel.onDeleteSavedSiteSnackbarDismissed(savedSite)
                        }
                    }
                },
            )
            .show()
    }

    private fun confirmDeleteBookmarkFolder(
        bookmarkFolder: BookmarkFolder,
    ) {
        val message = getString(R.string.bookmarkDeleteConfirmationMessage, bookmarkFolder.name).html(this)
        Snackbar.make(
            binding.root,
            message,
            Snackbar.LENGTH_LONG,
        ).setAction(R.string.fireproofWebsiteSnackbarAction) {
            viewModel.undoDelete(bookmarkFolder)
        }
            .addCallback(
                object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                    override fun onDismissed(
                        transientBottomBar: Snackbar?,
                        event: Int,
                    ) {
                        if (event != DISMISS_EVENT_ACTION) {
                            viewModel.onDeleteBookmarkFolderSnackbarDismissed(bookmarkFolder)
                        }
                    }
                },
            ).show()
    }

    private fun openBookmarkFolder(bookmarkFolder: BookmarkFolder) {
        startBookmarkFoldersActivityForResult.launch(intent(this, bookmarkFolder))
    }

    private fun editBookmarkFolder(bookmarkFolder: BookmarkFolder) {
        val parentId = getParentFolderId()
        val parentFolderName = getParentFolderName()
        val dialog = EditBookmarkFolderDialogFragment.instance(parentId, parentFolderName, bookmarkFolder)
        dialog.show(supportFragmentManager, EDIT_BOOKMARK_FOLDER_FRAGMENT_TAG)
        dialog.listener = viewModel
    }

    private fun deleteBookmarkFolder(bookmarkFolder: BookmarkFolder) {
        TextAlertDialogBuilder(this)
            .setTitle(getString(R.string.deleteFolder, bookmarkFolder.name))
            .setMessage(getMessageString(bookmarkFolder))
            .setPositiveButton(R.string.deleteSavedSiteConfirmationDialogDelete, DESTRUCTIVE)
            .setNegativeButton(R.string.deleteSavedSiteConfirmationDialogCancel, GHOST_ALT)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onDeleteFolderAccepted(bookmarkFolder)
                    }
                },
            )
            .show()
    }

    private fun getMessageString(bookmarkFolder: BookmarkFolder): String {
        val totalItems = bookmarkFolder.numBookmarks + bookmarkFolder.numFolders
        return resources.getQuantityString(
            R.plurals.bookmarkFolderDeleteMessage,
            totalItems,
            totalItems,
        )
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        with(supportFragmentManager) {
            findFragmentByTag(EDIT_BOOKMARK_FRAGMENT_TAG)?.let { dialog ->
                (dialog as EditSavedSiteDialogFragment).listener = viewModel
                dialog.deleteBookmarkListener = viewModel
            }
            findFragmentByTag(ADD_BOOKMARK_FOLDER_FRAGMENT_TAG)?.let { dialog ->
                (dialog as AddBookmarkFolderDialogFragment).listener = viewModel
            }
            findFragmentByTag(EDIT_BOOKMARK_FOLDER_FRAGMENT_TAG)?.let { dialog ->
                (dialog as EditBookmarkFolderDialogFragment).listener = viewModel
            }
        }
    }

    override fun onDestroy() {
        deleteDialog?.dismiss()
        if (this::searchListener.isInitialized) {
            searchListener.cancelSearch()
        }
        super.onDestroy()
    }

    override fun onPromotionDismissed() {
        viewModel.onPromotionDismissed()
    }

    private fun showFolderOverflowMenu(
        anchor: View,
        bookmarkFolder: BookmarkFolder,
    ) {
        val wrapper = ContextThemeWrapper(this, com.duckduckgo.mobile.android.R.style.Widget_DuckDuckGo_PopupMenu)
        val popup = androidx.appcompat.widget.PopupMenu(wrapper, anchor)
        popup.menuInflater.inflate(R.menu.bookmark_folder_popup_menu, popup.menu)

        popup.setOnMenuItemClickListener { menuItem: MenuItem ->
            when (menuItem.itemId) {
                R.id.bookmark_folder_edit -> {
                    viewModel.onEditBookmarkFolderRequested(bookmarkFolder)
                }

                R.id.bookmark_folder_delete -> {
                    viewModel.onDeleteBookmarkFolderRequested(bookmarkFolder)
                }
            }
            true
        }
        popup.setOnDismissListener {
            // Respond to popup being dismissed.
        }
        // Show the popup menu.
        popup.show()
    }

    private fun showBookmarkOverFlowMenu(
        anchor: View,
        bookmark: SavedSite.Bookmark,
    ) {
        val wrapper = ContextThemeWrapper(this, com.duckduckgo.mobile.android.R.style.Widget_DuckDuckGo_PopupMenu)
        val popup = androidx.appcompat.widget.PopupMenu(wrapper, anchor)
        popup.menuInflater.inflate(R.menu.bookmark_popup_menu, popup.menu)

        if (bookmark.isFavorite) {
            popup.menu.findItem(R.id.bookmark_add_to_favorites).title = getString(R.string.removeFromFavorites)
        } else {
            popup.menu.findItem(R.id.bookmark_add_to_favorites).title = getString(R.string.addToFavoritesMenu)
        }

        popup.setOnMenuItemClickListener { menuItem: MenuItem ->
            when (menuItem.itemId) {
                R.id.bookmark_edit -> {
                    viewModel.onEditSavedSiteRequested(bookmark)
                }

                R.id.bookmark_add_to_favorites -> {
                    addRemoveFavorite(bookmark)
                }

                R.id.bookmark_delete -> {
                    viewModel.onDeleteSavedSiteRequested(bookmark)
                    viewModel.onBookmarkItemDeletedFromOverflowMenu()
                }
            }
            true
        }
        popup.setOnDismissListener {
            // Respond to popup being dismissed.
        }
        // Show the popup menu.
        popup.show()
    }

    private fun addRemoveFavorite(bookmark: SavedSite.Bookmark) {
        if (bookmark.isFavorite) {
            viewModel.removeFavorite(bookmark)
        } else {
            viewModel.addFavorite(bookmark)
        }
    }

    companion object {
        const val SAVED_SITE_URL_EXTRA = "SAVED_SITE_URL_EXTRA"

        fun intent(
            context: Context,
            bookmarkFolder: BookmarkFolder? = null,
        ): Intent {
            val intent = Intent(context, BookmarksActivity::class.java)
            bookmarkFolder?.let {
                val bundle = Bundle()
                bundle.putString(KEY_BOOKMARK_FOLDER_ID, bookmarkFolder.id)
                bundle.putString(KEY_BOOKMARK_FOLDER_NAME, bookmarkFolder.name)
                intent.putExtras(bundle)
            }
            return intent
        }

        // Fragment Tags
        private const val EDIT_BOOKMARK_FRAGMENT_TAG = "EDIT_BOOKMARK"

        private const val ADD_BOOKMARK_FOLDER_FRAGMENT_TAG = "ADD_BOOKMARK_FOLDER"
        private const val EDIT_BOOKMARK_FOLDER_FRAGMENT_TAG = "EDIT_BOOKMARK_FOLDER"

        private const val KEY_BOOKMARK_FOLDER_NAME = "KEY_BOOKMARK_FOLDER_NAME"

        private const val IMPORT_BOOKMARKS_REQUEST_CODE = 111
        private const val EXPORT_BOOKMARKS_REQUEST_CODE = 112
        private const val GOOGLE_BOOKMARK_IMPORT_REQUEST_CODE = 113

        private val EXPORT_BOOKMARKS_FILE_NAME: String
            get() = "bookmarks_ddg_${formattedTimestamp()}.html"

        private fun formattedTimestamp(): String = formatter.format(Date())
        private val formatter: SimpleDateFormat = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        private const val DIALOG_TAG_IMPORT_BOOKMARKS = "ImportBookmarksPreImportDialog"
    }
}
