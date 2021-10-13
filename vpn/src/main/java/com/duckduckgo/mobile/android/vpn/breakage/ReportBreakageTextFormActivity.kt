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

package com.duckduckgo.mobile.android.vpn.breakage

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import androidx.core.text.HtmlCompat
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.databinding.ActivityReportBreakageTextFormBinding
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import kotlinx.coroutines.launch
import javax.inject.Inject

class ReportBreakageTextFormActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var deviceShieldPixels: DeviceShieldPixels

    @Inject
    lateinit var metadataReporter: ReportBreakageMetadataReporter

    private val binding: ActivityReportBreakageTextFormBinding by viewBinding()

    private val toolbar
        get() = binding.includeToolbar.defaultToolbar

    private lateinit var appPackageId: String

    private val reportBreakageLoginInfo = registerForActivityResult(ReportBreakageContract()) { issueReport ->
        if (!issueReport.isEmpty()) {
            lifecycleScope.launch {
                val issue = issueReport.copy(
                    appPackageId = appPackageId,
                    description = binding.appBreakageFormFeedbackInput.text.toString(),
                    customMetadata = Base64.encodeToString(metadataReporter.getVpnStateMetadata(issueReport.appPackageId).toByteArray(), Base64.NO_WRAP)
                )
                deviceShieldPixels.sendAppBreakageReport(issue.toMap())

                setResult(RESULT_OK, Intent().apply { issue.addToIntent(this) })
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The value should never be "unknown" we just do this because getStringExtra returns nullable
        appPackageId = intent.getStringExtra(APP_PACKAGE_ID_EXTRA) ?: "unknown"

        setContentView(binding.root)
        setupToolbar(toolbar)
        setupViews()
    }

    fun setupViews() {
        binding.appBreakageFormDisclaimer.text = HtmlCompat.fromHtml(getString(R.string.atp_ReportBreakageFormDisclaimerText), HtmlCompat.FROM_HTML_MODE_LEGACY)
        binding.ctaNextFormText.setOnClickListener {
            reportBreakageLoginInfo.launch(ReportBreakageScreen.LoginInformation(appPackageId))
        }
    }

    companion object {
        private const val APP_PACKAGE_ID_EXTRA = "APP_PACKAGE_ID_EXTRA"

        fun intent(context: Context, appPackageId: String): Intent {
            return Intent(context, ReportBreakageTextFormActivity::class.java).apply {
                putExtra(APP_PACKAGE_ID_EXTRA, appPackageId)
            }
        }
    }
}
