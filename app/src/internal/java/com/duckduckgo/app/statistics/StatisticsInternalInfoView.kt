/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.statistics

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.Toast
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.databinding.ViewStatisticsAttributedMetricsBinding
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.referral.AppReferrerDataStore
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ViewScope
import dagger.android.support.AndroidSupportInjection
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@InjectWith(ViewScope::class)
class StatisticsInternalInfoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayout(context, attrs, defStyle) {

    @Inject lateinit var store: StatisticsDataStore

    @Inject lateinit var referrerDataStore: AppReferrerDataStore

    @Inject lateinit var appInstallStore: AppInstallStore

    private val binding: ViewStatisticsAttributedMetricsBinding by viewBinding()

    private val dateETFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("US/Eastern")
    }
    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        binding.retentionAtb.apply {
            text = store.appRetentionAtb ?: "unknown"
        }

        binding.retentionAtbSave.setOnClickListener {
            store.appRetentionAtb = binding.retentionAtb.text
            Toast.makeText(this.context, "App Retention Atb updated", Toast.LENGTH_SHORT).show()
        }

        binding.searchAtb.apply {
            text = store.searchRetentionAtb ?: "unknown"
        }

        binding.searchAtbSave.setOnClickListener {
            store.searchRetentionAtb = binding.searchAtb.text
            Toast.makeText(this.context, "Search Atb updated", Toast.LENGTH_SHORT).show()
        }

        binding.originInput.apply {
            text = referrerDataStore.utmOriginAttributeCampaign ?: "unknown"
        }

        binding.originInputSave.setOnClickListener {
            referrerDataStore.utmOriginAttributeCampaign = binding.originInput.text.toString()
            Toast.makeText(this.context, "Origin updated", Toast.LENGTH_SHORT).show()
        }

        binding.installDateInput.apply {
            val timestamp = appInstallStore.installTimestamp
            text = dateETFormat.format(Date(timestamp))
        }

        binding.installDateSave.setOnClickListener {
            try {
                val date = dateETFormat.parse(binding.installDateInput.text)
                if (date != null) {
                    appInstallStore.installTimestamp = date.time
                    Toast.makeText(this.context, "Install date updated", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this.context, "Invalid date format. Use yyyy-MM-dd", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this.context, "Invalid date format. Use yyyy-MM-dd", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
