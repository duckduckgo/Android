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

import androidx.appcompat.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.text.HtmlCompat
import androidx.lifecycle.Observer
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityFireproofWebsitesBinding
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteEntity
import com.duckduckgo.app.fire.fireproofwebsite.data.website
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.google.android.material.snackbar.Snackbar
import javax.inject.Inject

class FireproofWebsitesActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var faviconManager: FaviconManager

    @Inject
    lateinit var variantManager: VariantManager

    lateinit var adapter: FireproofWebsiteAdapter

    private val binding: ActivityFireproofWebsitesBinding by viewBinding()

    private var deleteDialog: AlertDialog? = null

    private val viewModel: FireproofWebsitesViewModel by bindViewModel()

    private val toolbar
        get() = binding.includeToolbar.toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(toolbar)
        setupFireproofWebsiteRecycler()
        observeViewModel()
    }

    private fun setupFireproofWebsiteRecycler() {
        adapter = FireproofWebsiteAdapter(viewModel, this, faviconManager, variantManager)
        binding.recycler.adapter = adapter
    }

    private fun observeViewModel() {
        viewModel.viewState.observe(
            this,
            Observer { viewState ->
                viewState?.let {
                    adapter.loginDetectionEnabled = it.loginDetectionEnabled
                    adapter.fireproofWebsites = it.fireproofWebsitesEntities
                }
            }
        )

        viewModel.command.observe(
            this,
            Observer {
                when (it) {
                    is FireproofWebsitesViewModel.Command.ConfirmDeleteFireproofWebsite -> confirmDeleteWebsite(it.entity)
                }
            }
        )
    }

    @Suppress("deprecation")
    private fun confirmDeleteWebsite(entity: FireproofWebsiteEntity) {
        val message = HtmlCompat.fromHtml(getString(R.string.fireproofWebsiteRemovalConfirmation, entity.website()), HtmlCompat.FROM_HTML_MODE_LEGACY)
        viewModel.delete(entity)
        Snackbar.make(
            binding.root,
            message,
            Snackbar.LENGTH_LONG
        ).setAction(R.string.fireproofWebsiteSnackbarAction) {
            viewModel.onSnackBarUndoFireproof(entity)
        }.show()
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
