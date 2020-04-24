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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import androidx.annotation.StringRes
import androidx.core.text.HtmlCompat
import androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.fire.FireproofWebsiteEntity
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.faviconLocation
import com.duckduckgo.app.global.image.GlideApp
import kotlinx.android.synthetic.main.content_fireproof_websites.*
import kotlinx.android.synthetic.main.include_toolbar.*
import kotlinx.android.synthetic.main.item_autocomplete_bookmark_suggestion.view.*
import kotlinx.android.synthetic.main.view_fireproof_website_description.view.*
import kotlinx.android.synthetic.main.view_fireproof_website_entry.view.*
import org.jetbrains.anko.alert
import timber.log.Timber
import java.lang.IllegalArgumentException

class FireproofWebsitesActivity : DuckDuckGoActivity() {

    lateinit var adapter: FireproofWebsiteAdapter
    private var deleteDialog: AlertDialog? = null

    private val viewModel: FireproofWebsitesViewModel by bindViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fireproof_websites)
        setupActionBar()
        setupFireproofWebsiteRecycler()
        observeViewModel()
    }

    private fun setupFireproofWebsiteRecycler() {
        adapter = FireproofWebsiteAdapter(viewModel, R.string.fireproofWebsiteFeatureDescription)
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
        val message = HtmlCompat.fromHtml(getString(R.string.fireproofWebsiteDeleteConfirmMessage, entity.domain), FROM_HTML_MODE_LEGACY)
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
    private val viewModel: FireproofWebsitesViewModel,
    @StringRes private val listDescriptionStringRes: Int
) : RecyclerView.Adapter<FireproofWebSiteViewHolder>() {

    companion object Type {
        const val FIREPROOF_WEBSITE_TYPE = 0
        const val DESCRIPTION_TYPE = 1
    }

    var fireproofWebsites: List<FireproofWebsiteEntity> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FireproofWebSiteViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            FIREPROOF_WEBSITE_TYPE -> {
                val view = inflater.inflate(R.layout.view_fireproof_website_entry, parent, false)
                FireproofWebSiteViewHolder.PreservedWebsiteViewHolder(view, viewModel)
            }
            DESCRIPTION_TYPE -> {
                val view = inflater.inflate(R.layout.view_fireproof_website_description, parent, false)
                FireproofWebSiteViewHolder.FireproofWebsiteDescriptionViewHolder(view)
            }
            else -> throw IllegalArgumentException("viewType not found")
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if ((fireproofWebsites.size - 1) < position) {
            DESCRIPTION_TYPE
        } else {
            FIREPROOF_WEBSITE_TYPE
        }
    }

    override fun onBindViewHolder(holder: FireproofWebSiteViewHolder, position: Int) {
        when (holder) {
            is FireproofWebSiteViewHolder.FireproofWebsiteDescriptionViewHolder -> holder.bind(listDescriptionStringRes)
            is FireproofWebSiteViewHolder.PreservedWebsiteViewHolder -> holder.bind(fireproofWebsites[position])
        }
    }

    override fun getItemCount(): Int {
        return fireproofWebsites.size + 1
    }
}

sealed class FireproofWebSiteViewHolder(itemView: View) : ViewHolder(itemView) {

    class FireproofWebsiteDescriptionViewHolder(itemView: View) : FireproofWebSiteViewHolder(itemView) {
        fun bind(@StringRes text: Int) = with(itemView) {
            fireproofWebsiteDescription.setText(text)
        }
    }

    class PreservedWebsiteViewHolder(itemView: View, private val viewModel: FireproofWebsitesViewModel) : FireproofWebSiteViewHolder(itemView) {

        lateinit var entity: FireproofWebsiteEntity

        fun bind(entity: FireproofWebsiteEntity) {
            this.entity = entity

            itemView.overflowMenu.contentDescription = itemView.context.getString(
                R.string.bookmarkOverflowContentDescription,
                entity.title
            )

            itemView.fireproofWebsiteEntryTitle.text = entity.domain
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
                .into(itemView.fireproofWebsiteEntryFavicon)
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
}