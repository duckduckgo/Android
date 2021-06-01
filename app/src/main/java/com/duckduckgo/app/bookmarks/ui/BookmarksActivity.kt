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
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.ConcatAdapter
import com.duckduckgo.app.bookmarks.model.SavedSite
import com.duckduckgo.app.bookmarks.service.ExportBookmarksResult
import com.duckduckgo.app.bookmarks.service.ImportBookmarksResult
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.R.id.action_search
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.view.DividerAdapter
import com.duckduckgo.app.global.view.html
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_bookmarks.bookmarkRootView
import kotlinx.android.synthetic.main.content_bookmarks.recycler
import kotlinx.android.synthetic.main.include_toolbar.toolbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

class BookmarksActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var faviconManager: FaviconManager

    lateinit var bookmarksAdapter: BookmarksAdapter
    lateinit var favoritesAdapter: FavoritesAdapter
    private var deleteDialog: AlertDialog? = null

    private val viewModel: BookmarksViewModel by bindViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bookmarks)
        setupToolbar(toolbar)
        setupBookmarksRecycler()
        observeViewModel()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
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
                        viewModel.exportBookmarks(selectedFile)
                    }
                }
            }
        }
    }

    private fun setupBookmarksRecycler() {
        bookmarksAdapter = BookmarksAdapter(layoutInflater, viewModel, this, faviconManager)
        favoritesAdapter = FavoritesAdapter(layoutInflater, viewModel, this, faviconManager)
        recycler.adapter = ConcatAdapter(favoritesAdapter, DividerAdapter(), bookmarksAdapter)
        recycler.itemAnimator = null
    }

    private fun observeViewModel() {
        viewModel.viewState.observe(
            this,
            { viewState ->
                viewState?.let {
                    favoritesAdapter.favoriteItems = it.favorites.map { FavoritesAdapter.FavoriteItem(it) }
                    bookmarksAdapter.bookmarkItems = it.bookmarks.map { BookmarksAdapter.BookmarkItem(it) }
                    invalidateOptionsMenu()
                }
            }
        )

        viewModel.command.observe(
            this,
            {
                when (it) {
                    is BookmarksViewModel.Command.ConfirmDeleteSavedSite -> confirmDeleteSavedSite(it.savedSite)
                    is BookmarksViewModel.Command.OpenSavedSite -> openSavedSite(it.savedSite)
                    is BookmarksViewModel.Command.ShowEditSavedSite -> showEditSavedSiteDialog(it.savedSite)
                    is BookmarksViewModel.Command.ImportedBookmarks -> showImportedBookmarks(it.importBookmarksResult)
                    is BookmarksViewModel.Command.ExportedBookmarks -> showExportedBookmarks(it.exportBookmarksResult)
                }
            }
        )
    }

    private fun showImportedBookmarks(result: ImportBookmarksResult) {
        when (result) {
            is ImportBookmarksResult.Error -> {
                showMessage(getString(R.string.importBookmarksError))
            }
            is ImportBookmarksResult.Success -> {
                if (result.bookmarks.isEmpty()) {
                    showMessage(getString(R.string.importBookmarksEmpty))
                } else {
                    showMessage(getString(R.string.importBookmarksSuccess, result.bookmarks.size))
                }
            }
        }
    }

    private fun showExportedBookmarks(result: ExportBookmarksResult) {
        when (result) {
            is ExportBookmarksResult.Error -> {
                showMessage(getString(R.string.exportBookmarksError))
            }
            ExportBookmarksResult.NoBookmarksExported -> {
                showMessage(getString(R.string.exportBookmarksEmpty))
            }
            ExportBookmarksResult.Success -> {
                showMessage(getString(R.string.exportBookmarksSuccess))
            }
        }
    }

    private fun showMessage(message: String) {
        Snackbar.make(
            bookmarkRootView,
            message,
            Snackbar.LENGTH_LONG
        ).show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
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
                        getString(R.string.importBookmarksFileTitle)
                    ),
                    IMPORT_BOOKMARKS_REQUEST_CODE
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
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val searchMenuItem = menu?.findItem(action_search)
        searchMenuItem?.isVisible = viewModel.viewState.value?.enableSearch == true
        val searchView = searchMenuItem?.actionView as SearchView
        searchView.setOnQueryTextListener(BookmarksEntityQueryListener(viewModel.viewState.value?.bookmarks, bookmarksAdapter))
        return super.onPrepareOptionsMenu(menu)
    }

    private fun showEditSavedSiteDialog(savedSite: SavedSite) {
        val dialog = EditSavedSiteDialogFragment.instance(savedSite)
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
            bookmarkRootView,
            message,
            Snackbar.LENGTH_LONG
        ).setAction(R.string.fireproofWebsiteSnackbarAction) {
            viewModel.insert(savedSite)
        }.show()
    }

    override fun onDestroy() {
        deleteDialog?.dismiss()
        super.onDestroy()
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, BookmarksActivity::class.java)
        }

        // Fragment Tags
        private const val EDIT_BOOKMARK_FRAGMENT_TAG = "EDIT_BOOKMARK"

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
