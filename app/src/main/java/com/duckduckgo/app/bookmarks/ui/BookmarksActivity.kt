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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.ConcatAdapter
import com.duckduckgo.app.bookmarks.model.SavedSite
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.R.id.action_search
import com.duckduckgo.app.browser.R.menu.bookmark_activity_menu
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.view.DividerAdapter
import com.duckduckgo.app.global.view.html
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_bookmarks.*
import kotlinx.android.synthetic.main.content_bookmarks.*
import kotlinx.android.synthetic.main.include_toolbar.*
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

    private fun setupBookmarksRecycler() {
        bookmarksAdapter = BookmarksAdapter(layoutInflater, viewModel, this, faviconManager)
        favoritesAdapter = FavoritesAdapter(layoutInflater, viewModel, this, faviconManager)
        recycler.adapter = ConcatAdapter(favoritesAdapter, DividerAdapter(), bookmarksAdapter)
        recycler.itemAnimator = null
    }

    private fun observeViewModel() {
        viewModel.viewState.observe(
            this,
            Observer { viewState ->
                viewState?.let {
                    favoritesAdapter.favoriteItems = it.favorites.map { FavoritesAdapter.FavoriteItem(it) }
                    bookmarksAdapter.bookmarkItems = it.bookmarks.map { BookmarksAdapter.BookmarkItem(it) }
                    invalidateOptionsMenu()
                }
            }
        )

        viewModel.command.observe(
            this,
            Observer {
                when (it) {
                    is BookmarksViewModel.Command.ConfirmDeleteSavedSite -> confirmDeleteSavedSite(it.savedSite)
                    is BookmarksViewModel.Command.OpenSavedSite -> openBookmark(it.savedSite)
                    is BookmarksViewModel.Command.ShowEditSavedSite -> showEditSavedSiteDialog(it.savedSite)
                }
            }
        )
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(bookmark_activity_menu, menu)
        val searchItem = menu?.findItem(action_search)
        val searchView = searchItem?.actionView as SearchView
        searchView.setOnQueryTextListener(BookmarksEntityQueryListener(viewModel.viewState.value?.bookmarks, bookmarksAdapter))
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.findItem(action_search)?.isVisible = viewModel.viewState.value?.enableSearch == true
        return super.onPrepareOptionsMenu(menu)
    }

    private fun showEditSavedSiteDialog(savedSite: SavedSite) {
        val dialog = EditBookmarkDialogFragment.instance(savedSite)
        dialog.show(supportFragmentManager, EDIT_BOOKMARK_FRAGMENT_TAG)
        dialog.listener = viewModel
    }

    private fun openBookmark(savedSite: SavedSite) {
        startActivity(BrowserActivity.intent(this, savedSite.url))
        finish()
    }

    private fun confirmDeleteSavedSite(savedSite: SavedSite) {
        val message = getString(R.string.bookmarkDeleteConfirmationMessage, savedSite.title).html(this)
        viewModel.delete(savedSite)
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
    }
}
