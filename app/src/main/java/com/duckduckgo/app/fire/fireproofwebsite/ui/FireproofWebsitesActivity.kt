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
import android.view.Menu
import android.view.MenuItem
import androidx.core.text.HtmlCompat
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityFireproofWebsitesBinding
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteEntity
import com.duckduckgo.app.fire.fireproofwebsite.data.website
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.settings.db.SettingsSharedPreferences.LoginDetectorPrefsMapper.AutomaticFireproofSetting
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.google.android.material.snackbar.Snackbar
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class FireproofWebsitesActivity : DuckDuckGoActivity(), FireproofSettingsSelectorFragment.Listener {

    @Inject
    lateinit var faviconManager: FaviconManager

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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_fireproof_websites_activity, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.removeAll -> {
                viewModel.removeAllRequested()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupFireproofWebsiteRecycler() {
        adapter = FireproofWebsiteAdapter(viewModel, this, faviconManager)
        binding.recycler.adapter = adapter
    }

    private fun observeViewModel() {
        viewModel.viewState.observe(this) { viewState ->
            viewState?.let {
                adapter.automaticFireproofSetting = it.automaticFireproofSetting
                adapter.fireproofWebsites = it.fireproofWebsitesEntities
            }
        }

        viewModel.command.observe(this) {
            when (it) {
                is FireproofWebsitesViewModel.Command.ConfirmRemoveFireproofWebsite -> confirmRemoveWebsite(it.entity)
                is FireproofWebsitesViewModel.Command.ConfirmRemoveAllFireproofWebsites -> confirmRemoveAllWebsites(it.removedWebsitesEntities)
                is FireproofWebsitesViewModel.Command.ShowAutomaticFireproofSettingSelectionDialog ->
                    showAutomaticFireproofSettingSelectionDialog(it.automaticFireproofSetting)
            }
        }
    }

    private fun showAutomaticFireproofSettingSelectionDialog(automaticFireproofSetting: AutomaticFireproofSetting) {
        val dialog = FireproofSettingsSelectorFragment.create(automaticFireproofSetting)
        dialog.show(supportFragmentManager, FIREPROOF_SETTING_SELECTOR_DIALOG_TAG)
    }

    @Suppress("deprecation")
    private fun confirmRemoveWebsite(entity: FireproofWebsiteEntity) {
        val message = HtmlCompat.fromHtml(getString(R.string.fireproofWebsiteRemovalConfirmation, entity.website()), HtmlCompat.FROM_HTML_MODE_LEGACY)
        viewModel.remove(entity)
        Snackbar.make(
            binding.root,
            message,
            Snackbar.LENGTH_LONG
        ).setAction(R.string.fireproofWebsiteSnackbarAction) {
            viewModel.onSnackBarUndoFireproof(entity)
        }.show()
    }

    private fun confirmRemoveAllWebsites(removedWebsitesEntities: List<FireproofWebsiteEntity>) {
        val message = HtmlCompat.fromHtml(getString(R.string.fireproofWebsiteRemoveAllConfirmation), HtmlCompat.FROM_HTML_MODE_LEGACY)
        viewModel.removeAllWebsites()
        Snackbar.make(
            binding.root,
            message,
            Snackbar.LENGTH_LONG
        ).setAction(R.string.fireproofWebsiteSnackbarAction) {
            viewModel.onSnackBarUndoRemoveAllWebsites(removedWebsitesEntities)
        }.show()
    }

    override fun onDestroy() {
        deleteDialog?.dismiss()
        super.onDestroy()
    }

    companion object {
        private const val FIREPROOF_SETTING_SELECTOR_DIALOG_TAG = "FIREPROOF_SETTING_SELECTOR_DIALOG_TAG"

        fun intent(context: Context): Intent {
            return Intent(context, FireproofWebsitesActivity::class.java)
        }
    }

    override fun onAutomaticFireproofSettingSelected(selectedSetting: AutomaticFireproofSetting) {
        viewModel.onAutomaticFireproofSettingChanged(selectedSetting)
    }
}
