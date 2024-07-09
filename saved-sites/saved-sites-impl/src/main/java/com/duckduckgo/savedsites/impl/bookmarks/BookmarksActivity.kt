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
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.SimpleItemAnimator
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.browser.api.ui.BrowserScreens.BookmarksScreenNoParams
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.SearchBar
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.R as commonR
import com.duckduckgo.saved.sites.impl.R
import com.duckduckgo.saved.sites.impl.databinding.ActivityBookmarksBinding
import com.duckduckgo.saved.sites.impl.databinding.ContentBookmarksBinding
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.savedsites.api.service.ExportSavedSitesResult
import com.duckduckgo.savedsites.api.service.ImportSavedSitesResult
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.ConfirmDeleteBookmarkFolder
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.ConfirmDeleteSavedSite
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.DeleteBookmarkFolder
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.ExportedSavedSites
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.ImportedSavedSites
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.LaunchBookmarkImport
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.OpenBookmarkFolder
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.OpenSavedSite
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.ShowEditBookmarkFolder
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.ShowEditSavedSite
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.ShowFaviconsPrompt
import com.duckduckgo.savedsites.impl.dialogs.AddBookmarkFolderDialogFragment
import com.duckduckgo.savedsites.impl.dialogs.EditBookmarkFolderDialogFragment
import com.duckduckgo.savedsites.impl.dialogs.EditSavedSiteDialogFragment
import com.duckduckgo.savedsites.impl.folders.BookmarkFoldersActivity.Companion.KEY_BOOKMARK_FOLDER_ID
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(BookmarksScreenNoParams::class, screenName = "bookmarks")
class BookmarksActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var faviconManager: FaviconManager

    @Inject
    lateinit var browserNav: BrowserNav

    private lateinit var bookmarksAdapter: BookmarksAdapter
    private lateinit var searchListener: BookmarksQueryListener

    private var deleteDialog: AlertDialog? = null
    private var searchMenuItem: MenuItem? = null
    private var exportMenuItem: MenuItem? = null

    private val viewModel: BookmarksViewModel by bindViewModel()

    private val binding: ActivityBookmarksBinding by viewBinding()
    private lateinit var contentBookmarksBinding: ContentBookmarksBinding

    private val toolbar
        get() = binding.toolbar

    private val searchBar
        get() = binding.searchBar

    private val startBookmarkFoldersActivityForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == RESULT_OK) {
                result.data?.getStringExtra(SAVED_SITE_URL_EXTRA)?.let {
                    viewModel.onBookmarkFoldersActivityResult(it)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        contentBookmarksBinding = ContentBookmarksBinding.bind(binding.root)
        setContentView(binding.root)
        configureToolbar()

        setupBookmarksRecycler()
        observeViewModel()

        viewModel.fetchBookmarksAndFolders(getParentFolderId())
    }

    private fun configureToolbar() {
        setupToolbar(toolbar)
        supportActionBar?.title = getParentFolderName()
    }

    private fun getParentFolderName() =
        intent.extras?.getString(KEY_BOOKMARK_FOLDER_NAME)
            ?: getString(R.string.bookmarksActivityTitle)

    private fun getParentFolderId() = intent.extras?.getString(KEY_BOOKMARK_FOLDER_ID)
        ?: SavedSitesNames.BOOKMARKS_ROOT

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            IMPORT_BOOKMARKS_REQUEST_CODE -> {
                if (resultCode == RESULT_OK) {
                    val selectedFile = data?.data
                    if (selectedFile != null) {
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
        }
    }

    private fun setupBookmarksRecycler() {
        bookmarksAdapter = BookmarksAdapter(layoutInflater, viewModel, this, faviconManager)
        contentBookmarksBinding.recycler.adapter = bookmarksAdapter

        val callback = BookmarkItemTouchHelperCallback(bookmarksAdapter)
        val itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(contentBookmarksBinding.recycler)

        (contentBookmarksBinding.recycler.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
    }

    private fun observeViewModel() {
        viewModel.viewState.observe(this) { viewState ->
            viewState?.let { state ->
                val items = state.bookmarkItems ?: emptyList()
                bookmarksAdapter.setItems(
                    items,
                    state.bookmarkItems != null && state.bookmarkItems.isEmpty() && getParentFolderId() == SavedSitesNames.BOOKMARKS_ROOT,
                    false,
                )
                setSearchMenuItemVisibility()
                exportMenuItem?.isEnabled = items.isNotEmpty()
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
                is LaunchBookmarkImport -> launchBookmarkImport()
                is ShowFaviconsPrompt -> showFaviconsPrompt()
            }
        }
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

    private fun launchBookmarkImport() {
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.bookmark_activity_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.bookmark_import -> {
                launchBookmarkImport()
            }

            R.id.bookmark_export -> {
                val intent = Intent()
                    .setType("text/html")
                    .setAction(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .putExtra(Intent.EXTRA_TITLE, EXPORT_BOOKMARKS_FILE_NAME)

                startActivityForResult(intent, EXPORT_BOOKMARKS_REQUEST_CODE)
            }

            R.id.action_add_folder -> {
                val parentId = getParentFolderId()
                val parentFolderName = getParentFolderName()
                val dialog = AddBookmarkFolderDialogFragment.instance(parentId, parentFolderName)
                dialog.show(supportFragmentManager, ADD_BOOKMARK_FOLDER_FRAGMENT_TAG)
                dialog.listener = viewModel
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
        setSearchMenuItemVisibility()
        initializeSearchBar()
        return super.onPrepareOptionsMenu(menu)
    }

    private fun initializeSearchBar() {
        searchListener = BookmarksQueryListener(viewModel, bookmarksAdapter)
        searchMenuItem?.setOnMenuItemClickListener {
            showSearchBar()
            return@setOnMenuItemClickListener true
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
        toolbar.gone()
        viewModel.fetchAllBookmarksAndFolders()
        searchBar.handle(SearchBar.Event.ShowSearchBar)
        bookmarksAdapter.isInSearchMode = true
    }

    private fun hideSearchBar() {
        toolbar.show()
        viewModel.fetchBookmarksAndFolders(getParentFolderId())
        searchBar.handle(SearchBar.Event.DismissSearchBar)
        bookmarksAdapter.isInSearchMode = false
    }

    private fun setSearchMenuItemVisibility() {
        searchMenuItem?.isVisible = viewModel.viewState.value?.enableSearch == true || getParentFolderId() != SavedSitesNames.BOOKMARKS_ROOT
    }

    private fun showEditSavedSiteDialog(savedSite: SavedSite) {
        val dialog = EditSavedSiteDialogFragment.instance(savedSite, getParentFolderId(), getParentFolderName())
        dialog.show(supportFragmentManager, EDIT_BOOKMARK_FRAGMENT_TAG)
        dialog.listener = viewModel
        dialog.deleteBookmarkListener = viewModel
    }

    private fun openSavedSite(url: String) {
        if (intent.action == Intent.ACTION_VIEW) {
            browserNav.openInNewTab(this, url)
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
            .setDestructiveButtons(true)
            .setPositiveButton(R.string.deleteSavedSiteConfirmationDialogDelete)
            .setNegativeButton(R.string.deleteSavedSiteConfirmationDialogCancel)
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

        private val EXPORT_BOOKMARKS_FILE_NAME: String
            get() = "bookmarks_ddg_${formattedTimestamp()}.html"

        private fun formattedTimestamp(): String = formatter.format(Date())
        private val formatter: SimpleDateFormat = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
