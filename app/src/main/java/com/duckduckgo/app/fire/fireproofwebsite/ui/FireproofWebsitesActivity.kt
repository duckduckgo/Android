/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.fire.fireproofwebsite.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.fire.FireproofWebsiteEntity
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.baseHost
import com.duckduckgo.app.global.faviconLocation
import com.duckduckgo.app.global.image.GlideApp
import kotlinx.android.synthetic.main.content_preserve_website.*
import kotlinx.android.synthetic.main.include_toolbar.*
import kotlinx.android.synthetic.main.view_preserved_website_entry.view.*
import org.jetbrains.anko.alert
import timber.log.Timber

class FireproofWebsitesActivity : DuckDuckGoActivity() {

    lateinit var adapter: FireproofWebsiteAdapter
    private var deleteDialog: AlertDialog? = null

    private val viewModel: FireproofWebsitesViewModel by bindViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preserve_website)
        setupActionBar()
        setupFireproofWebsiteRecycler()
        observeViewModel()
    }

    private fun setupFireproofWebsiteRecycler() {
        adapter = FireproofWebsiteAdapter(viewModel)
        recycler.adapter = adapter
    }

    private fun setupActionBar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun observeViewModel() {
        viewModel.viewState.observe(this, Observer<FireproofWebsitesViewModel.ViewState> { viewState ->
            viewState?.let {
                adapter.fireproofWebsites = it.fireproofWebsitesEntities
            }
        })

        viewModel.command.observe(this, Observer {
            when (it) {
                is FireproofWebsitesViewModel.Command.ConfirmDeletePreservedWebsite -> confirmDeleteWebsite(it.entity)
            }
        })
    }

    @Suppress("deprecation")
    private fun confirmDeleteWebsite(entity: FireproofWebsiteEntity) {
        val message =
            Html.fromHtml(getString(R.string.bookmarkDeleteConfirmMessage, entity.domain))
        val title = getString(R.string.bookmarkDeleteConfirmTitle)
        deleteDialog = alert(message, title) {
            positiveButton(android.R.string.yes) { viewModel.delete(entity) }
            negativeButton(android.R.string.no) { }
        }.build()
        deleteDialog?.show()
    }

    override fun onDestroy() {
        deleteDialog?.dismiss()
        super.onDestroy()
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, FireproofWebsitesActivity::class.java)
        }
    }
}

class FireproofWebsiteAdapter(
    private val viewModel: FireproofWebsitesViewModel
) : Adapter<PreservedWebsiteViewHolder>() {

    var fireproofWebsites: List<FireproofWebsiteEntity> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreservedWebsiteViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.view_preserved_website_entry, parent, false)
        return PreservedWebsiteViewHolder(view, viewModel)
    }

    override fun onBindViewHolder(holder: PreservedWebsiteViewHolder, position: Int) {
        holder.update(fireproofWebsites[position])
    }

    override fun getItemCount(): Int {
        return fireproofWebsites.size
    }
}

class PreservedWebsiteViewHolder(itemView: View, private val viewModel: FireproofWebsitesViewModel) :
    ViewHolder(itemView) {

    lateinit var entity: FireproofWebsiteEntity

    fun update(entity: FireproofWebsiteEntity) {
        this.entity = entity

        itemView.overflowMenu.contentDescription = itemView.context.getString(
            R.string.bookmarkOverflowContentDescription,
            entity.title
        )

        itemView.title.text = entity.title
        itemView.url.text = parseDisplayUrl(entity.domain)
        loadFavicon(entity.originalUrl)

        itemView.overflowMenu.setOnClickListener {
            showOverFlowMenu(itemView.overflowMenu, entity)
        }
    }

    private fun loadFavicon(url: String) {
        val faviconUrl = Uri.parse(url).faviconLocation()

        GlideApp.with(itemView)
            .load(faviconUrl)
            .placeholder(R.drawable.ic_globe_gray_16dp)
            .error(R.drawable.ic_globe_gray_16dp)
            .into(itemView.favicon)
    }

    private fun parseDisplayUrl(urlString: String): String {
        val uri = Uri.parse(urlString)
        return uri.baseHost ?: return urlString
    }

    private fun showOverFlowMenu(overflowMenu: ImageView, entity: FireproofWebsiteEntity) {
        val popup = PopupMenu(overflowMenu.context, overflowMenu)
        popup.inflate(R.menu.fireproof_website_individual_overflow_menu)
        popup.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.delete -> {
                    deleteEntity(entity); true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun deleteEntity(entity: FireproofWebsiteEntity) {
        Timber.i("Deleting website with domain: ${entity.domain}")
        viewModel.onDeleteRequested(entity)
    }
}