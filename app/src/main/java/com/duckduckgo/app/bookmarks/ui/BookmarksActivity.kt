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
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
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
import kotlinx.android.synthetic.main.content_bookmarks.emptyBookmarks
import kotlinx.android.synthetic.main.content_bookmarks.recycler
import kotlinx.android.synthetic.main.include_toolbar.toolbar
import kotlinx.android.synthetic.main.popup_window_bookmarks_menu.view.deleteBookmark
import kotlinx.android.synthetic.main.popup_window_bookmarks_menu.view.editBookmark
import kotlinx.android.synthetic.main.view_bookmark_entry.view.favicon
import kotlinx.android.synthetic.main.view_bookmark_entry.view.overflowMenu
import kotlinx.android.synthetic.main.view_bookmark_entry.view.title
import kotlinx.android.synthetic.main.view_bookmark_entry.view.url
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

class BookmarksActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var faviconManager: FaviconManager

    lateinit var adapter: BookmarksAdapter
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
        adapter = BookmarksAdapter(layoutInflater, viewModel, this, faviconManager)
        recycler.adapter = adapter
    }

    private fun observeViewModel() {
        viewModel.viewState.observe(this, Observer { viewState ->
            viewState?.let {
                if (it.showBookmarks) showBookmarks() else hideBookmarks()
                adapter.bookmarks = it.bookmarks
                invalidateOptionsMenu()
            }
        })

        viewModel.command.observe(this, Observer {
            when (it) {
                is BookmarksViewModel.Command.ConfirmDeleteBookmark -> confirmDeleteBookmark(it.bookmark)
                is BookmarksViewModel.Command.OpenBookmark -> openBookmark(it.bookmark)
                is BookmarksViewModel.Command.ShowEditBookmark -> showEditBookmarkDialog(it.bookmark)
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(bookmark_activity_menu, menu)
        val searchItem = menu?.findItem(action_search)
        val searchView = searchItem?.actionView as SearchView
        searchView.setOnQueryTextListener(BookmarksEntityQueryListener(viewModel.viewState.value?.bookmarks, adapter))
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
        val message = getString(R.string.bookmarkDeleteSnackbarMessage, bookmark.title).html(this)
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
    ) : Adapter<BookmarksViewHolder>() {

        var bookmarks: List<BookmarkEntity> = emptyList()
            set(value) {
                field = value
                notifyDataSetChanged()
            }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookmarksViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val view = inflater.inflate(R.layout.view_bookmark_entry, parent, false)
            return BookmarksViewHolder(layoutInflater, view, viewModel, lifecycleOwner, faviconManager)
        }

        override fun onBindViewHolder(holder: BookmarksViewHolder, position: Int) {
            holder.update(bookmarks[position])
        }

        override fun getItemCount(): Int {
            return bookmarks.size
        }
    }

    class BookmarksViewHolder(
        private val layoutInflater: LayoutInflater,
        itemView: View,
        private val viewModel: BookmarksViewModel,
        private val lifecycleOwner: LifecycleOwner,
        private val faviconManager: FaviconManager
    ) : ViewHolder(itemView) {

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
