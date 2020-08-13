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

package com.duckduckgo.app.location.ui

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.view.gone
import com.duckduckgo.app.global.view.html
import com.duckduckgo.app.global.view.show
import com.duckduckgo.app.global.view.website
import com.duckduckgo.app.location.data.LocationPermissionEntity
import com.duckduckgo.app.location.data.LocationPermissionType
import kotlinx.android.synthetic.main.content_location_permissions.locationPermissionsNoSystemPermission
import kotlinx.android.synthetic.main.content_location_permissions.recycler
import kotlinx.android.synthetic.main.include_toolbar.toolbar

class LocationPermissionsActivity : DuckDuckGoActivity(), SiteLocationPermissionDialog.SiteLocationPermissionDialogListener {

    lateinit var adapter: LocationPermissionsAdapter
    private var deleteDialog: AlertDialog? = null

    private val viewModel: LocationPermissionsViewModel by bindViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location_permissions)
        setupToolbar(toolbar)
        setupRecyclerView()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        loadSystemPermission()
    }

    private fun setupRecyclerView() {
        adapter = LocationPermissionsAdapter(viewModel)
        recycler.adapter = adapter
    }

    private fun observeViewModel() {
        viewModel.viewState.observe(this, Observer { viewState ->
            viewState?.let {
                if (!it.systemLocationPermissionGranted) {
                    recycler.gone()
                    locationPermissionsNoSystemPermission.text = getString(R.string.preciseLocationNoSystemPermission).html(this)
                    locationPermissionsNoSystemPermission.show()
                } else {
                    recycler.show()
                    locationPermissionsNoSystemPermission.gone()
                    adapter.updatePermissions(it.locationPermissionEnabled, it.locationPermissionEntities)
                }
            }
        })

        viewModel.command.observe(this, Observer {
            when (it) {
                is LocationPermissionsViewModel.Command.ConfirmDeleteLocationPermission -> confirmDeleteWebsite(it.entity)
                is LocationPermissionsViewModel.Command.EditLocationPermissions -> editSiteLocationPermission(it.entity)
            }
        })
    }

    private fun loadSystemPermission() {
        viewModel.loadLocationPermissions(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }

    @Suppress("deprecation")
    private fun confirmDeleteWebsite(entity: LocationPermissionEntity) {
        val message = getString(R.string.preciseLocationDeleteConfirmMessage, entity.domain.website()).html(this)
        val title = getString(R.string.dialogConfirmTitle)
        deleteDialog = AlertDialog.Builder(this, R.style.AlertDialogTheme)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.yes) { _, _ -> viewModel.delete(entity) }
            .setNegativeButton(android.R.string.no) { _, _ -> }
            .create()
        deleteDialog?.show()
    }

    private fun editSiteLocationPermission(entity: LocationPermissionEntity) {
        val dialog = SiteLocationPermissionDialog.instance(entity.domain, true)
        dialog.show(supportFragmentManager, SiteLocationPermissionDialog.SITE_LOCATION_PERMISSION_TAG)
    }

    override fun onDestroy() {
        deleteDialog?.dismiss()
        super.onDestroy()
    }

    override fun onSiteLocationPermissionSelected(domain: String, permission: LocationPermissionType) {
        viewModel.onSiteLocationPermissionSelected(domain, permission)
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, LocationPermissionsActivity::class.java)
        }

    }
}
