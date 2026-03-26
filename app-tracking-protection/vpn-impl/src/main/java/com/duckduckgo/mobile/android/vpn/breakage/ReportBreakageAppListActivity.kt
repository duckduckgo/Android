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
import android.text.Annotation
import android.text.SpannableString
import android.text.Spanned
import android.text.SpannedString
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.databinding.ActivityReportBreakageAppListBinding
import com.duckduckgo.mobile.android.vpn.di.AppTpBreakageCategories
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.ui.AppBreakageCategory
import com.duckduckgo.mobile.android.vpn.ui.OpenVpnReportBreakageFrom
import com.duckduckgo.navigation.api.getActivityParams
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(OpenVpnReportBreakageFrom::class)
class ReportBreakageAppListActivity : DuckDuckGoActivity(), ReportBreakageAppListAdapter.Listener {

    @Inject
    lateinit var deviceShieldPixels: DeviceShieldPixels

    @Inject
    lateinit var appFeedbackContract: Provider<AppFeedbackContract>

    @Inject
    lateinit var reportBreakageContract: Provider<ReportBreakageContract>

    @Inject
    @AppTpBreakageCategories
    lateinit var breakageCategories: List<AppBreakageCategory>

    private val viewModel: ReportBreakageAppListViewModel by bindViewModel()

    private lateinit var adapter: ReportBreakageAppListAdapter

    private lateinit var launchedFrom: String

    private val binding: ActivityReportBreakageAppListBinding by viewBinding()

    private val toolbar
        get() = binding.includeToolbar.toolbar

    private lateinit var reportBreakage: ActivityResultLauncher<ReportBreakageScreen>

    private lateinit var sendAppFeedback: ActivityResultLauncher<Void?>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        reportBreakage = registerForActivityResult(reportBreakageContract.get()) { result ->
            if (!result.isEmpty()) {
                viewModel.onBreakageSubmitted(result)
            }
        }

        sendAppFeedback = registerForActivityResult(appFeedbackContract.get()) { resultOk ->
            if (resultOk) {
                Toast.makeText(this, R.string.atp_ThanksForTheFeedback, Toast.LENGTH_LONG).show()
                finish()
            }
        }

        launchedFrom = intent.getActivityParams(OpenVpnReportBreakageFrom::class.java)?.launchFrom ?: "unknown"

        setContentView(binding.root)
        setupToolbar(toolbar)
        setupRecycler()

        setupViews()
        observeViewModel()
    }

    override fun onStart() {
        super.onStart()
        deviceShieldPixels.didShowReportBreakageAppList()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        onSupportNavigateUp()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onInstalledAppSelected(
        installedApp: InstalledApp,
        position: Int,
    ) {
        viewModel.onAppSelected(installedApp)
    }

    private fun addClickableLink(
        annotation: String,
        text: CharSequence,
        onClick: () -> Unit,
    ): SpannableString {
        val fullText = text as SpannedString
        val spannableString = SpannableString(fullText)
        val annotations = fullText.getSpans(0, fullText.length, Annotation::class.java)
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                onClick()
            }
        }

        annotations?.find { it.value == annotation }?.let {
            spannableString.apply {
                setSpan(
                    clickableSpan,
                    fullText.getSpanStart(it),
                    fullText.getSpanEnd(it),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                setSpan(
                    UnderlineSpan(),
                    fullText.getSpanStart(it),
                    fullText.getSpanEnd(it),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                setSpan(
                    ForegroundColorSpan(
                        getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorAccentBlue),
                    ),
                    fullText.getSpanStart(it),
                    fullText.getSpanEnd(it),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
        return spannableString
    }

    private fun setupViews() {
        binding.deviceShieldReportBreakageAppListSkeleton.startShimmer()
        binding.ctaSubmitAppBreakage.setOnClickListener {
            viewModel.onSubmitBreakage()
        }
        setupRecycler()
        with(binding.appBreakageReportFeature) {
            text = addClickableLink(
                USE_THIS_FORM_ANNOTATION,
                getText(R.string.atp_ReportBreakageAppFeature),
            ) { sendAppFeedback.launch(null) }
            movementMethod = LinkMovementMethod.getInstance()
        }
    }

    private fun setupRecycler() {
        adapter = ReportBreakageAppListAdapter(this)
        binding.reportBreakageAppsRecycler.adapter = adapter
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.getInstalledApps()
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collect { renderViewState(it) }
        }
        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun processCommand(command: ReportBreakageAppListView.Command) {
        when (command) {
            is ReportBreakageAppListView.Command.LaunchBreakageForm -> {
                reportBreakage.launch(
                    ReportBreakageScreen.IssueDescriptionForm(
                        origin = runCatching { launchedFrom }.getOrDefault("unknown"),
                        breakageCategories = breakageCategories,
                        appName = command.selectedApp.name,
                        appPackageId = command.selectedApp.packageName,
                    ),
                )
            }
            is ReportBreakageAppListView.Command.SendBreakageInfo -> {
                val intent = Intent().apply {
                    command.issueReport.addToIntent(this)
                }
                setResult(RESULT_OK, intent)
                finish()
            }
        }
    }

    private fun renderViewState(viewState: ReportBreakageAppListView.State) {
        binding.deviceShieldReportBreakageAppListSkeleton.stopShimmer()
        adapter.update(viewState.installedApps)
        binding.ctaSubmitAppBreakage.isEnabled = viewState.canSubmit
        binding.deviceShieldReportBreakageAppListSkeleton.gone()
    }

    companion object {
        private const val USE_THIS_FORM_ANNOTATION = "use_this_form_link"
        internal fun intent(context: Context): Intent {
            return Intent(context, ReportBreakageAppListActivity::class.java)
        }
    }
}
