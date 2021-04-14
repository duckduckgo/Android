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
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.bookmarks.model.Favorite
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.R.id.action_search
import com.duckduckgo.app.browser.R.menu.bookmark_activity_menu
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.baseHost
import com.duckduckgo.app.global.view.gone
import com.duckduckgo.app.global.view.html
import com.duckduckgo.app.global.view.show
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_bookmarks.*
import kotlinx.android.synthetic.main.content_bookmarks.*
import kotlinx.android.synthetic.main.include_toolbar.*
import kotlinx.android.synthetic.main.popup_window_bookmarks_menu.view.*
import kotlinx.android.synthetic.main.view_bookmark_entry.view.*
import kotlinx.android.synthetic.main.view_location_permissions_section_title.view.*
import kotlinx.coroutines.launch
import timber.log.Timber
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
        recycler.adapter = ConcatAdapter(favoritesAdapter, bookmarksAdapter)
    }

    private fun observeViewModel() {
        viewModel.viewState.observe(
            this,
            Observer { viewState ->
                viewState?.let {
                    if (it.showBookmarks) showBookmarks() else hideBookmarks()
                    favoritesAdapter.bookmarkItems = it.favorites.map { FavoritesAdapter.Favorite(it) }
                    bookmarksAdapter.bookmarkItems = it.bookmarks
                    invalidateOptionsMenu()
                }
            }
        )

        viewModel.command.observe(
            this,
            Observer {
                when (it) {
                    is BookmarksViewModel.Command.ConfirmDeleteBookmark -> confirmDeleteBookmark(it.bookmark)
                    is BookmarksViewModel.Command.OpenBookmark -> openBookmark(it.bookmark)
                    is BookmarksViewModel.Command.ShowEditBookmark -> showEditBookmarkDialog(it.bookmark)
                }
            }
        )
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(bookmark_activity_menu, menu)
        val searchItem = menu?.findItem(action_search)
        val searchView = searchItem?.actionView as SearchView
        //searchView.setOnQueryTextListener(BookmarksEntityQueryListener(viewModel.viewState.value?.bookmarks, adapter))
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.findItem(action_search)?.isVisible = viewModel.viewState.value?.enableSearch == true
        return super.onPrepareOptionsMenu(menu)
    }

    private fun showEditBookmarkDialog(bookmark: BookmarkEntity) {
        val dialog = EditBookmarkDialogFragment.instance(bookmark.id, bookmark.title, bookmark.url)
        dialog.show(supportFragmentManager, EDIT_BOOKMARK_FRAGMENT_TAG)
        dialog.listener = viewModel
    }

    private fun showBookmarks() {
        recycler.show()
        emptyBookmarks.gone()
    }

    private fun hideBookmarks() {
        recycler.gone()
        emptyBookmarks.show()
    }

    private fun openBookmark(bookmark: BookmarkEntity) {
        startActivity(BrowserActivity.intent(this, bookmark.url))
        finish()
    }

    private fun confirmDeleteBookmark(bookmark: BookmarkEntity) {
        val message = getString(R.string.bookmarkDeleteConfirmationMessage, bookmark.title).html(this)
        viewModel.delete(bookmark)
        Snackbar.make(
            bookmarkRootView,
            message,
            Snackbar.LENGTH_LONG
        ).setAction(R.string.fireproofWebsiteSnackbarAction) {
            viewModel.insert(bookmark)
        }.show()

    }

    private fun delete(bookmark: BookmarkEntity) {
        viewModel.delete(bookmark)
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

    class BookmarksAdapter(
        private val layoutInflater: LayoutInflater,
        private val viewModel: BookmarksViewModel,
        private val lifecycleOwner: LifecycleOwner,
        private val faviconManager: FaviconManager
    ) : RecyclerView.Adapter<BookmarkScreenViewHolders>() {

        companion object {
            const val BOOKMARK_SECTION_TITLE_TYPE = 0
            const val EMPTY_STATE_TYPE = 1
            const val BOOKMARK_TYPE = 2

            const val BOOKMARK_SECTION_TITLE_SIZE = 1
        }

        var bookmarkItems: List<BookmarkEntity> = emptyList()
            set(value) {
                field = value
                notifyDataSetChanged()
            }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookmarkScreenViewHolders {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                BOOKMARK_TYPE -> {
                    val view = inflater.inflate(R.layout.view_bookmark_entry, parent, false)
                    return BookmarkScreenViewHolders.BookmarksViewHolder(layoutInflater, view, viewModel, lifecycleOwner, faviconManager)
                }
                BOOKMARK_SECTION_TITLE_TYPE -> {
                    val view = inflater.inflate(R.layout.view_location_permissions_section_title, parent, false)
                    return BookmarkScreenViewHolders.SectionTitle(view)
                }
                else -> throw IllegalArgumentException("viewType not found")
            }
        }

        override fun getItemCount(): Int {
            return headerItemsSize() + bookmarkItems.size
        }

        override fun onBindViewHolder(holder: BookmarkScreenViewHolders, position: Int) {
            when (holder) {
                is BookmarkScreenViewHolders.BookmarksViewHolder -> {
                    holder.update(bookmarkItems[position - headerItemsSize()])
                }
                is BookmarkScreenViewHolders.SectionTitle -> {
                    holder.bind()
                }
            }
        }

        override fun getItemViewType(position: Int): Int {
            return if (position == 0 ) {
                BOOKMARK_SECTION_TITLE_TYPE
            } else {
                BOOKMARK_TYPE
            }
        }

        private fun headerItemsSize(): Int {
            return BOOKMARK_SECTION_TITLE_SIZE
        }
    }

    sealed class BookmarkScreenViewHolders(itemView: View) : RecyclerView.ViewHolder(itemView) {

        class SectionTitle(itemView: View) : BookmarkScreenViewHolders(itemView) {
            fun bind() {
                itemView.locationPermissionsSectionTitle.setText(R.string.bookmarksSectionTitle)
            }
        }

        class BookmarksViewHolder(
            private val layoutInflater: LayoutInflater,
            itemView: View,
            private val viewModel: BookmarksViewModel,
            private val lifecycleOwner: LifecycleOwner,
            private val faviconManager: FaviconManager
        ) : BookmarkScreenViewHolders(itemView) {

            lateinit var bookmark: BookmarkEntity

            fun update(bookmark: BookmarkEntity) {
                this.bookmark = bookmark

                itemView.overflowMenu.contentDescription = itemView.context.getString(
                    R.string.bookmarkOverflowContentDescription,
                    bookmark.title
                )

                itemView.title.text = bookmark.title
                itemView.url.text = parseDisplayUrl(bookmark.url)
                loadFavicon(bookmark.url)

                itemView.overflowMenu.setOnClickListener {
                    showOverFlowMenu(itemView.overflowMenu, bookmark)
                }

                itemView.setOnClickListener {
                    viewModel.onSelected(bookmark)
                }
            }

            private fun loadFavicon(url: String) {
                lifecycleOwner.lifecycleScope.launch {
                    faviconManager.loadToViewFromPersisted(url, itemView.favicon)
                }
            }

            private fun parseDisplayUrl(urlString: String): String {
                val uri = Uri.parse(urlString)
                return uri.baseHost ?: return urlString
            }

            private fun showOverFlowMenu(anchor: ImageView, bookmark: BookmarkEntity) {
                val popupMenu = BookmarksPopupMenu(layoutInflater)
                val view = popupMenu.contentView
                popupMenu.apply {
                    onMenuItemClicked(view.editBookmark) { editBookmark(bookmark) }
                    onMenuItemClicked(view.deleteBookmark) { deleteBookmark(bookmark) }
                }
                popupMenu.show(itemView, anchor)
            }

            private fun editBookmark(bookmark: BookmarkEntity) {
                Timber.i("Editing bookmark ${bookmark.title}")
                viewModel.onEditBookmarkRequested(bookmark)
            }

            private fun deleteBookmark(bookmark: BookmarkEntity) {
                Timber.i("Deleting bookmark ${bookmark.title}")
                viewModel.onDeleteRequested(bookmark)
            }
        }
    }

    class FavoritesAdapter(
        private val layoutInflater: LayoutInflater,
        private val viewModel: BookmarksViewModel,
        private val lifecycleOwner: LifecycleOwner,
        private val faviconManager: FaviconManager
    ) : RecyclerView.Adapter<FavoriteViewHolder>() {

        companion object {
            const val FAVORITE_SECTION_TITLE_TYPE = 0
            const val BOOKMARK_SECTION_TITLE_TYPE = 1
            const val EMPTY_STATE_TYPE = 2
            const val BOOKMARK_TYPE = 3
            const val FAVORITE_TYPE = 4
        }

        data class Favorite(val favorite: com.duckduckgo.app.bookmarks.model.Favorite)

        var bookmarkItems: List<Favorite> = emptyList()
            set(value) {
                field = value
                notifyDataSetChanged()
            }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoriteViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                FAVORITE_TYPE -> {
                    val view = inflater.inflate(R.layout.view_bookmark_entry, parent, false)
                    return FavoriteViewHolder(layoutInflater, view, viewModel, lifecycleOwner, faviconManager)
                }
                else -> throw IllegalArgumentException("viewType not found")
            }
        }

        override fun getItemCount(): Int {
            return bookmarkItems.size
        }

        override fun onBindViewHolder(holder: FavoriteViewHolder, position: Int) {
            when (holder) {
                is FavoriteViewHolder -> {
                    holder.update(bookmarkItems[position].favorite)
                }
            }
        }

        override fun getItemViewType(position: Int): Int {
            return FAVORITE_TYPE
        }
    }


    class FavoriteViewHolder(
        private val layoutInflater: LayoutInflater,
        itemView: View,
        private val viewModel: BookmarksViewModel,
        private val lifecycleOwner: LifecycleOwner,
        private val faviconManager: FaviconManager
    ) : RecyclerView.ViewHolder(itemView) {

        lateinit var favorite: Favorite

        fun update(favorite: Favorite) {
            this.favorite = favorite

            itemView.overflowMenu.contentDescription = itemView.context.getString(
                R.string.bookmarkOverflowContentDescription,
                favorite.title
            )

            itemView.title.text = favorite.title
            itemView.url.text = parseDisplayUrl(favorite.url)
            loadFavicon(favorite.url)

            itemView.overflowMenu.setOnClickListener {
                //showOverFlowMenu(itemView.overflowMenu, favorite)
            }

            itemView.setOnClickListener {
                //viewModel.onSelected(favorite)
            }
        }

        private fun loadFavicon(url: String) {
            lifecycleOwner.lifecycleScope.launch {
                faviconManager.loadToViewFromPersisted(url, itemView.favicon)
            }
        }

        private fun parseDisplayUrl(urlString: String): String {
            val uri = Uri.parse(urlString)
            return uri.baseHost ?: return urlString
        }
    }
}
