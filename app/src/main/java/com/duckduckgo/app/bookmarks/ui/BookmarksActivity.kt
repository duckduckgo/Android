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

import android.app.Activity
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.RecyclerView.Adapter
import android.support.v7.widget.RecyclerView.ViewHolder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.ViewModelFactory
import kotlinx.android.synthetic.main.content_bookmarks.*
import kotlinx.android.synthetic.main.include_toolbar.*
import kotlinx.android.synthetic.main.view_bookmark_entry.view.*
import org.jetbrains.anko.alert
import org.jetbrains.anko.doAsync
import javax.inject.Inject

class BookmarksActivity: DuckDuckGoActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory
    lateinit var adapter: BookmarksAdapter

    private val viewModel: BookmarksViewModel by lazy {
        ViewModelProviders.of(this, viewModelFactory).get(BookmarksViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bookmarks)
        setupActionBar()
        setupBookmarksRecycler()
        observeViewModel()
    }

    private fun setupBookmarksRecycler() {
        adapter = BookmarksAdapter(applicationContext, viewModel)
        recycler.adapter = adapter
    }

    private fun setupActionBar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun observeViewModel() {
        viewModel.viewState.observe(this, Observer<BookmarksViewModel.ViewState> { viewState ->
            viewState?.let {
                adapter.bookmarks = it.bookmarks
            }
        })

        viewModel.command.observe(this, Observer {
            when(it) {
                is BookmarksViewModel.Command.ConfirmDeleteBookmark -> confirmDeleteBookmark(it.bookmark)
                is BookmarksViewModel.Command.OpenBookmark -> openBookmark(it.bookmark)
            }
         })
    }

    private fun openBookmark(bookmark: BookmarkEntity) {
        val intent = Intent(bookmark.url)
        setResult(OPEN_URL_RESULT_CODE, intent)
        finish()
    }

    private fun confirmDeleteBookmark(bookmark: BookmarkEntity) {
        val message = getString(R.string.bookmarkDeleteConfirmMessage, bookmark.title)
        val title = getString(R.string.bookmarkDeleteConfirmTitle)
        alert(message, title) {
            positiveButton(android.R.string.yes) { delete(bookmark) }
            negativeButton(android.R.string.no) { }
        }.show()
    }

    private fun delete(bookmark: BookmarkEntity) {
        doAsync {
            viewModel.delete(bookmark)
        }
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, BookmarksActivity::class.java)
        }

        val OPEN_URL_RESULT_CODE = Activity.RESULT_FIRST_USER
    }

    class BookmarksAdapter(val context: Context, val viewModel: BookmarksViewModel): Adapter<BookmarksViewHolder>() {

        var bookmarks: List<BookmarkEntity> = emptyList()
            set(value) {
                field = value
                notifyDataSetChanged()
            }


        override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): BookmarksViewHolder {
            val inflater = LayoutInflater.from(context)
            val view = inflater.inflate(R.layout.view_bookmark_entry, parent, false)
            return BookmarksViewHolder(view, viewModel)
        }

        override fun onBindViewHolder(holder: BookmarksViewHolder?, position: Int) {
            holder?.update(bookmarks[position])
        }

        override fun getItemCount(): Int {
            return bookmarks.size
        }

    }

    class BookmarksViewHolder(itemView: View?, val viewModel: BookmarksViewModel) : ViewHolder(itemView) {

        lateinit var bookmark: BookmarkEntity

        fun update(bookmark: BookmarkEntity) {
            this.bookmark = bookmark

            itemView.delete.contentDescription = itemView.context.getString(R.string.deleteBookmarkContentDescription, bookmark.title)
            itemView.title.text = bookmark.title

            itemView.delete.setOnClickListener {
                viewModel.onDeleteRequested(bookmark)
            }

            itemView.setOnClickListener {
                viewModel.onSelected(bookmark)
            }
        }

    }

}