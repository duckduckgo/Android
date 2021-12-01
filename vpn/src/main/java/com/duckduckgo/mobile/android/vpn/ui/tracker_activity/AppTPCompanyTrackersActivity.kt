/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.ui.tracker_activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.extensions.safeGetApplicationIcon
import com.duckduckgo.mobile.android.ui.TextDrawable
import com.duckduckgo.mobile.android.ui.view.addClickableLink
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageContract
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageScreen
import com.duckduckgo.mobile.android.vpn.databinding.ActivityApptpCompanyTrackersActivityBinding
import com.duckduckgo.mobile.android.vpn.ui.onboarding.DeviceShieldFAQActivity
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.include_company_trackers_toolbar.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class AppTPCompanyTrackersActivity : DuckDuckGoActivity() {

    private val binding: ActivityApptpCompanyTrackersActivityBinding by viewBinding()
    private val viewModel: AppTPCompanyTrackersViewModel by bindViewModel()

    private val itemsAdapter = AppTPCompanyDetailsAdapter()

    private val reportBreakage = registerForActivityResult(ReportBreakageContract()) {
        if (!it.isEmpty()) {
            Snackbar.make(binding.root, R.string.atp_ReportBreakageSent, Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        with(binding.includeToolbar) {
            setupToolbar(defaultToolbar)
            app_name.text = getAppName()
            Glide.with(applicationContext)
                .load(packageManager.safeGetApplicationIcon(getPackage()))
                .error(TextDrawable.asIconDrawable(getAppName()))
                .into(appIcon)
        }

        binding.trackingLearnMore.addClickableLink("learn_more_link", getText(R.string.atp_CompanyDetailsTrackingLearnMore)) {
            DeviceShieldFAQActivity.intent(this).also {
                startActivity(it)
            }
        }

        observeViewModel()
        binding.activityRecyclerView.adapter = itemsAdapter

    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.getTrackersForAppFromDate(
                getDate(),
                getPackage()
            )
                .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
                .collect {
                    binding.trackingAttempts.text = resources.getQuantityString(R.plurals.atp_CompanyDetailsTrackingAttemptsTitle, it.totalTrackingAttempts, it.totalTrackingAttempts)
                    binding.includeToolbar.appTrackdAgo.text = it.lastTrackerBlockedAgo
                    itemsAdapter.updateData(it.trackingCompanies)
                }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_company_trackers_activity, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.reportIssue -> {
                launchFeedback(); true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun launchFeedback() {
        reportBreakage.launch(ReportBreakageScreen.LoginInformation(getAppName(), getPackage()))
    }

    override fun onBackPressed() {
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun getDate(): String {
        return intent.getStringExtra(EXTRA_DATE)!!
    }

    private fun getAppName(): String {
        return intent.getStringExtra(EXTRA_APP_NAME)!!
    }

    private fun getPackage(): String {
        return intent.getStringExtra(EXTRA_PACKAGE_NAME)!!
    }

    companion object {
        private const val EXTRA_PACKAGE_NAME = "EXTRA_PACKAGE_NAME"
        private const val EXTRA_APP_NAME = "EXTRA_APP_NAME"
        private const val EXTRA_DATE = "EXTRA_DATE"

        fun intent(context: Context, packageName: String, appDisplayName: String, bucket: String): Intent {
            val intent = Intent(context, AppTPCompanyTrackersActivity::class.java)
            intent.putExtra(EXTRA_PACKAGE_NAME, packageName)
            intent.putExtra(EXTRA_APP_NAME, appDisplayName)
            intent.putExtra(EXTRA_DATE, bucket)
            return intent
        }
    }

}
