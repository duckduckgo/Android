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
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.databinding.ActivityApptpCompanyTrackersActivityBinding
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class AppTPCompanyTrackersActivity : DuckDuckGoActivity() {

    private val binding: ActivityApptpCompanyTrackersActivityBinding by viewBinding()
    private val viewModel: AppTPCompanyTrackersViewModel by bindViewModel()

    private val itemsAdapter = AppTPCompanyDetailsAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)!!
        val appName = intent.getStringExtra(EXTRA_APP_NAME)!!
        val date = intent.getStringExtra(EXTRA_DATE)!!

        setContentView(binding.root)
        with(binding.includeToolbar.defaultToolbar) {
            setupToolbar(this)
            this.title = appName
        }
        title = appName

        binding.activityRecyclerView.adapter = itemsAdapter

        lifecycleScope.launch {
            viewModel.getTrackersForAppFromDate(
                date,
                packageName
            )
                .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
                .collect {
                    findViewById<TextView>(R.id.tracking_attempts).text =
                        resources.getQuantityString(R.plurals.atp_CompanyDetailsTrackingAttemptsTitle, it.totalTrackingAttempts, it.totalTrackingAttempts)
                    itemsAdapter.updateData(it.trackingCompanies)
                }
        }

    }

    override fun onBackPressed() {
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
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
