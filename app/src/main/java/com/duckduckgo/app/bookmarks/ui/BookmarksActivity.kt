/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.bookmarks.ui

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ConcatAdapter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.bookmarks.model.BookmarkFolder
import com.duckduckgo.app.bookmarks.model.BookmarkFolderBranch
import com.duckduckgo.app.bookmarks.model.SavedSite
import com.duckduckgo.app.bookmarks.service.ExportSavedSitesResult
import com.duckduckgo.app.bookmarks.service.ImportSavedSitesResult
import com.duckduckgo.app.bookmarks.ui.bookmarkfolders.AddBookmarkFolderDialogFragment
import com.duckduckgo.app.bookmarks.ui.bookmarkfolders.BookmarkFoldersActivity.Companion.KEY_BOOKMARK_FOLDER_ID
import com.duckduckgo.app.bookmarks.ui.bookmarkfolders.BookmarkFoldersAdapter
import com.duckduckgo.app.bookmarks.ui.bookmarkfolders.EditBookmarkFolderDialogFragment
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.R.plurals
import com.duckduckgo.app.browser.databinding.ActivityBookmarksBinding
import com.duckduckgo.app.browser.databinding.ContentBookmarksBinding
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.extensions.html
import com.duckduckgo.app.global.view.DividerAdapter
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.view.SearchBar
import com.duckduckgo.mobile.android.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.mobile.android.ui.view.dialog.TextAlertDialogBuilder.EventListener
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class BookmarksActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var faviconManager: FaviconManager

    @Inject
    lateinit var dispatchers: DispatcherProvider

    lateinit var bookmarksAdapter: BookmarksAdapter
    lateinit var favoritesAdapter: FavoritesAdapter
    lateinit var bookmarkFoldersAdapter: BookmarkFoldersAdapter
    lateinit var searchListener: BookmarksEntityQueryListener

    private var deleteDialog: AlertDialog? = null
    private var searchMenuItem: MenuItem? = null

    private val viewModel: BookmarksViewModel by bindViewModel()

    private val binding: ActivityBookmarksBinding by viewBinding()
    private lateinit var contentBookmarksBinding: ContentBookmarksBinding

    private val toolbar
        get() = binding.toolbar

    private val searchBar
        get() = binding.searchBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        contentBookmarksBinding = ContentBookmarksBinding.bind(binding.root)
        setContentView(binding.root)
        configureToolbar()

        val parentFolderId = getParentFolderId()
        setupBookmarksRecycler(parentFolderId)
        observeViewModel(parentFolderId)

        viewModel.fetchBookmarksAndFolders(parentFolderId)
    }

    private fun configureToolbar() {
        setupToolbar(toolbar)
        supportActionBar?.title = getParentFolderName()
    }

    private fun getParentFolderName() =
        intent.extras?.getString(KEY_BOOKMARK_FOLDER_NAME)
            ?: getString(R.string.bookmarksActivityTitle)

    private fun getParentFolderId() = intent.extras?.getLong(KEY_BOOKMARK_FOLDER_ID)
        ?: ROOT_FOLDER_ID

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

    private fun setupBookmarksRecycler(parentId: Long) {
        if (parentId == ROOT_FOLDER_ID) {
            bookmarksAdapter = BookmarksAdapter(layoutInflater, viewModel, this, faviconManager, dispatchers)
            favoritesAdapter = FavoritesAdapter(layoutInflater, viewModel, this, faviconManager, dispatchers)
            bookmarkFoldersAdapter = BookmarkFoldersAdapter(layoutInflater, viewModel, parentId)
            contentBookmarksBinding.recycler.adapter = ConcatAdapter(favoritesAdapter, DividerAdapter(), bookmarkFoldersAdapter, bookmarksAdapter)
        } else {
            bookmarksAdapter = BookmarksAdapter(layoutInflater, viewModel, this, faviconManager, dispatchers)
            bookmarkFoldersAdapter = BookmarkFoldersAdapter(layoutInflater, viewModel, parentId)
            contentBookmarksBinding.recycler.adapter = ConcatAdapter(bookmarkFoldersAdapter, bookmarksAdapter)
        }
        contentBookmarksBinding.recycler.itemAnimator = null
    }

    private fun observeViewModel(parentId: Long) {
        viewModel.viewState.observe(
            this,
        ) { viewState ->
            viewState?.let { state ->
                if (parentId == ROOT_FOLDER_ID) {
                    favoritesAdapter.setItems(state.favorites.map { FavoritesAdapter.FavoriteItem(it) })
                }
                bookmarksAdapter.setItems(state.bookmarks.map { BookmarksAdapter.BookmarkItem(it) }, state.bookmarkFolders.isEmpty())
                bookmarkFoldersAdapter.bookmarkFolderItems = state.bookmarkFolders.map { BookmarkFoldersAdapter.BookmarkFolderItem(it) }
                setSearchMenuItemVisibility()
            }
        }

        viewModel.command.observe(
            this,
        ) {
            when (it) {
                is BookmarksViewModel.Command.ConfirmDeleteSavedSite -> confirmDeleteSavedSite(it.savedSite)
                is BookmarksViewModel.Command.OpenSavedSite -> openSavedSite(it.savedSite)
                is BookmarksViewModel.Command.ShowEditSavedSite -> showEditSavedSiteDialog(it.savedSite)
                is BookmarksViewModel.Command.ImportedSavedSites -> showImportedSavedSites(it.importSavedSitesResult)
                is BookmarksViewModel.Command.ExportedSavedSites -> showExportedSavedSites(it.exportSavedSitesResult)
                is BookmarksViewModel.Command.OpenBookmarkFolder -> openBookmarkFolder(it.bookmarkFolder)
                is BookmarksViewModel.Command.ShowEditBookmarkFolder -> editBookmarkFolder(it.bookmarkFolder)
                is BookmarksViewModel.Command.DeleteBookmarkFolder -> deleteBookmarkFolder(it.bookmarkFolder)
                is BookmarksViewModel.Command.ConfirmDeleteBookmarkFolder -> confirmDeleteBookmarkFolder(it.bookmarkFolder, it.folderBranch)
            }
        }
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
        setSearchMenuItemVisibility()
        initializeSearchBar()
        return super.onPrepareOptionsMenu(menu)
    }

    private fun initializeSearchBar() {
        searchListener = BookmarksEntityQueryListener(viewModel, bookmarksAdapter, bookmarkFoldersAdapter)
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
        viewModel.fetchBookmarksAndFolders()
        searchBar.handle(SearchBar.Event.ShowSearchBar)
    }

    private fun hideSearchBar() {
        toolbar.show()
        viewModel.fetchBookmarksAndFolders(getParentFolderId())
        searchBar.handle(SearchBar.Event.DismissSearchBar)
    }

    private fun setSearchMenuItemVisibility() {
        searchMenuItem?.isVisible = viewModel.viewState.value?.enableSearch == true || getParentFolderId() != ROOT_FOLDER_ID
    }

    private fun showEditSavedSiteDialog(savedSite: SavedSite) {
        val dialog = EditSavedSiteDialogFragment.instance(savedSite, getParentFolderId(), getParentFolderName())
        dialog.show(supportFragmentManager, EDIT_BOOKMARK_FRAGMENT_TAG)
        dialog.listener = viewModel
    }

    private fun openSavedSite(savedSite: SavedSite) {
        startActivity(BrowserActivity.intent(this, savedSite.url))
        finish()
    }

    private fun confirmDeleteSavedSite(savedSite: SavedSite) {
        val message = getString(R.string.bookmarkDeleteConfirmationMessage, savedSite.title).html(this)
        Snackbar.make(
            binding.root,
            message,
            Snackbar.LENGTH_LONG,
        ).setAction(R.string.fireproofWebsiteSnackbarAction) {
            viewModel.insert(savedSite)
        }.show()
    }

    private fun confirmDeleteBookmarkFolder(
        bookmarkFolder: BookmarkFolder,
        folderBranch: BookmarkFolderBranch,
    ) {
        val message = getString(R.string.bookmarkDeleteConfirmationMessage, bookmarkFolder.name).html(this)
        Snackbar.make(
            binding.root,
            message,
            Snackbar.LENGTH_LONG,
        ).setAction(R.string.fireproofWebsiteSnackbarAction) {
            viewModel.insertDeletedFolderBranch(folderBranch)
        }.show()
    }

    private fun openBookmarkFolder(bookmarkFolder: BookmarkFolder) {
        startActivity(intent(this, bookmarkFolder))
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
            .setTitle(R.string.delete)
            .setMessage(getMessageString(bookmarkFolder))
            .setPositiveButton(R.string.yes)
            .setNegativeButton(R.string.no)
            .addEventListener(
                object : EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onBookmarkFolderDeleted(bookmarkFolder)
                    }
                },
            )
            .show()
    }

    fun getMessageString(bookmarkFolder: BookmarkFolder): SpannableString {
        val totalItems = bookmarkFolder.numBookmarks + bookmarkFolder.numFolders
        val message = getString(R.string.bookmarkFolderDeleteDialogMessage)
        val string = SpannableString(
            resources.getQuantityString(
                plurals.bookmarkFolderDeleteDialogMessage,
                totalItems,
                message,
                bookmarkFolder.name,
                totalItems,
            ),
        )
        string.setSpan(StyleSpan(Typeface.BOLD), message.length, message.length + bookmarkFolder.name.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return string
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        with(supportFragmentManager) {
            findFragmentByTag(EDIT_BOOKMARK_FRAGMENT_TAG)?.let { dialog ->
                (dialog as EditSavedSiteDialogFragment).listener = viewModel
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
        fun intent(
            context: Context,
            bookmarkFolder: BookmarkFolder? = null,
        ): Intent {
            val intent = Intent(context, BookmarksActivity::class.java)
            bookmarkFolder?.let {
                val bundle = Bundle()
                bundle.putLong(KEY_BOOKMARK_FOLDER_ID, bookmarkFolder.id)
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
        private const val ROOT_FOLDER_ID = 0L

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
