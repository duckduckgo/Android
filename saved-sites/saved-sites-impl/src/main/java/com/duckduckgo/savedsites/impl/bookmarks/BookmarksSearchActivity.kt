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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.recyclerview.widget.SimpleItemAnimator
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.SearchBar
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.saved.sites.impl.R
import com.duckduckgo.saved.sites.impl.databinding.ActivityBookmarksSearchBinding
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.OpenSavedSite
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksViewModel.Command.OpenBookmarkFolder
import javax.inject.Inject
import android.view.MenuItem
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSitesNames

@InjectWith(ActivityScope::class)
class BookmarksSearchActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var faviconManager: FaviconManager

    private val binding: ActivityBookmarksSearchBinding by viewBinding()
    private val viewModel: BookmarksViewModel by bindViewModel()
    
    private lateinit var bookmarksAdapter: BookmarksAdapter
    private lateinit var searchListener: BookmarksQueryListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupBookmarksRecycler()
        observeViewModel()
        initializeSearchBar()

        viewModel.fetchAllBookmarksAndFolders()
    }

    private fun setupBookmarksRecycler() {
        bookmarksAdapter = BookmarksAdapter(
            viewModel,
            this,
            faviconManager,
            onBookmarkClick = { bookmark ->
                viewModel.onSelected(bookmark)
            },
            onBookmarkOverflowClick = { anchor, bookmark ->
                showBookmarkOverFlowMenu(anchor, bookmark)
            },
            onLongClick = {
                // Do nothing on long click in search mode
            },
            onBookmarkFolderClick = { _, bookmarkFolder ->
                viewModel.onBookmarkFolderSelected(bookmarkFolder)
            },
            onBookmarkFolderOverflowClick = { anchor, bookmarkFolder ->
                showFolderOverflowMenu(anchor, bookmarkFolder)
            },
        )
        bookmarksAdapter.isInSearchMode = true
        binding.recycler.adapter = bookmarksAdapter
        (binding.recycler.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
    }

    private fun initializeSearchBar() {
        searchListener = BookmarksQueryListener(viewModel, bookmarksAdapter)
        binding.searchBar.onAction {
            when (it) {
                is SearchBar.Action.PerformUpAction -> finish()
                is SearchBar.Action.PerformSearch -> if (this::searchListener.isInitialized) {
                    searchListener.onQueryTextChange(it.searchText)
                }
            }
        }
        
        binding.searchBar.handle(SearchBar.Event.ShowSearchBar)
    }

    private fun observeViewModel() {
        viewModel.viewState.observe(this) { viewState ->
            viewState?.let { state ->
                val items = state.sortedItems
                bookmarksAdapter.setItems(
                    items,
                    items.isEmpty(),
                    false,
                )
            }
        }

        viewModel.command.observe(this) {
            when (it) {
                is OpenSavedSite -> {
                    val resultIntent = Intent().apply {
                        putExtra(RESULT_URL_EXTRA, it.savedSiteUrl)
                    }
                    setResult(Activity.RESULT_OK, resultIntent)
                    finish()
                }
                is OpenBookmarkFolder -> {
                    val resultIntent = Intent().apply {
                        putExtra(RESULT_FOLDER, it.bookmarkFolder)
                    }
                    setResult(Activity.RESULT_OK, resultIntent)
                    finish()
                }
                else -> {
                    // Other commands are handled by the parent activity
                }
            }
        }
    }

    private fun showFolderOverflowMenu(
        anchor: View,
        bookmarkFolder: BookmarkFolder,
    ) {
        val wrapper = ContextThemeWrapper(this, com.duckduckgo.mobile.android.R.style.Widget_DuckDuckGo_PopupMenu)
        val popup = PopupMenu(wrapper, anchor)
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
        popup.show()
    }

    private fun showBookmarkOverFlowMenu(
        anchor: View,
        bookmark: SavedSite.Bookmark,
    ) {
        val wrapper = ContextThemeWrapper(this, com.duckduckgo.mobile.android.R.style.Widget_DuckDuckGo_PopupMenu)
        val popup = PopupMenu(wrapper, anchor)
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
                    if (bookmark.isFavorite) {
                        viewModel.removeFavorite(bookmark)
                    } else {
                        viewModel.addFavorite(bookmark)
                    }
                }
                R.id.bookmark_delete -> {
                    viewModel.onDeleteSavedSiteRequested(bookmark)
                    viewModel.onBookmarkItemDeletedFromOverflowMenu()
                }
            }
            true
        }
        popup.show()
    }

    override fun onDestroy() {
        if (this::searchListener.isInitialized) {
            searchListener.cancelSearch()
        }
        super.onDestroy()
    }

    companion object {
        const val RESULT_URL_EXTRA = "RESULT_URL_EXTRA"
        const val RESULT_FOLDER = "RESULT_FOLDER"

        fun intent(
            context: Context,
        ): Intent {
            return Intent(context, BookmarksSearchActivity::class.java).apply {}
        }
    }
} 
