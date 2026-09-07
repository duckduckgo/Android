/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.subscriptions.impl.onboarding.features

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Bundle
import android.os.Environment
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.addClickableLink
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.makeSnackbarWithNoBottomInset
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeBucket
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.downloads.api.DOWNLOAD_SNACKBAR_DELAY
import com.duckduckgo.downloads.api.DOWNLOAD_SNACKBAR_LENGTH
import com.duckduckgo.downloads.api.DownloadCommand
import com.duckduckgo.downloads.api.DownloadStateListener
import com.duckduckgo.downloads.api.DownloadsFileActions
import com.duckduckgo.downloads.api.FileDownloader
import com.duckduckgo.downloads.api.FileDownloader.PendingFileDownload
import com.duckduckgo.navigation.api.getActivityParams
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingFeature
import com.duckduckgo.subscriptions.api.SubscriptionScreens.SubscriptionOnboardingFeatureInfoScreen
import com.duckduckgo.subscriptions.impl.R
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.ITR_SUMMARY_OF_BENEFITS_URL
import com.duckduckgo.subscriptions.impl.databinding.ActivitySubscriptionOnboardingFeatureInfoBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Full-screen detail for a single subscription feature, opened from the features-summary step. Toolbar
 * shows a close (X) and no title; closing returns to the features step. Per-feature copy and highlights
 * come from [OnboardingFeature], whose content layout is inflated into the screen.
 */
@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(SubscriptionOnboardingFeatureInfoScreen::class)
class SubscriptionOnboardingFeatureInfoActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var edgeToEdgeProvider: EdgeToEdgeProvider

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject
    lateinit var fileDownloader: FileDownloader

    @Inject
    lateinit var downloadCallback: DownloadStateListener

    @Inject
    lateinit var downloadsFileActions: DownloadsFileActions

    private val binding: ActivitySubscriptionOnboardingFeatureInfoBinding by viewBinding()

    private val downloadMessagesJob = ConflatedJob()

    private val feature: OnboardingFeature by lazy {
        val screenFeature = intent.getActivityParams(SubscriptionOnboardingFeatureInfoScreen::class.java)?.feature
            ?: SubscriptionOnboardingFeature.VPN
        when (screenFeature) {
            SubscriptionOnboardingFeature.VPN -> OnboardingFeature.VPN
            SubscriptionOnboardingFeature.ITR -> OnboardingFeature.ITR
            SubscriptionOnboardingFeature.DUCK_AI -> OnboardingFeature.DUCK_AI
            SubscriptionOnboardingFeature.PIR -> OnboardingFeature.PIR
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val edgeToEdgeEnabled = edgeToEdgeProvider.isEnabled(EdgeToEdgeBucket.MISC)
        if (edgeToEdgeEnabled) {
            enableTransparentEdgeToEdge()
        }

        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        binding.includeToolbar.toolbar.setNavigationIcon(com.duckduckgo.mobile.android.R.drawable.ic_close_24)
        supportActionBar?.title = ""

        val surfaceColor = getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorSurface)
        binding.includeToolbar.appBarLayout.setBackgroundColor(surfaceColor)
        binding.includeToolbar.toolbar.setBackgroundColor(surfaceColor)
        binding.subscriptionOnboardingFeatureInfoIcon.setImageResource(feature.iconRes)
        binding.subscriptionOnboardingFeatureInfoTitle.setText(feature.titleRes)
        binding.subscriptionOnboardingFeatureInfoDescription.setText(feature.descriptionRes)
        layoutInflater.inflate(feature.contentRes, binding.subscriptionOnboardingFeatureInfoContent, true)

        if (feature == OnboardingFeature.ITR) {
            setupSummaryOfBenefitsLink()
        }

        if (edgeToEdgeEnabled) {
            edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
            edgeToEdgeHandler.applyStatusBarInsets(binding.includeToolbar.appBarLayout, installScrim = false)
            edgeToEdgeHandler.applyScrollableNavigationBarInsets(binding.subscriptionOnboardingFeatureInfoScrollView)
        }
    }

    override fun onResume() {
        if (feature == OnboardingFeature.ITR) {
            launchDownloadMessagesJob()
        }
        super.onResume()
    }

    override fun onPause() {
        downloadMessagesJob.cancel()
        super.onPause()
    }

    private fun setupSummaryOfBenefitsLink() {
        binding.subscriptionOnboardingFeatureInfoContent
            .findViewById<DaxTextView>(R.id.subscriptionOnboardingFeatureInfoLegalFooter)
            .addClickableLink(
                annotation = "summary_of_benefits_link",
                textSequence = getText(R.string.subscriptionOnboardingFeatureInfoItrLegalFooter),
            ) {
                if (hasWriteStoragePermission()) {
                    downloadSummaryOfBenefits()
                } else {
                    requestWriteStoragePermission()
                }
            }
    }

    private fun downloadSummaryOfBenefits() {
        fileDownloader.enqueueDownload(
            PendingFileDownload(
                url = ITR_SUMMARY_OF_BENEFITS_URL,
                mimeType = PDF_MIME_TYPE,
                subfolder = Environment.DIRECTORY_DOWNLOADS,
                browserMode = BrowserMode.REGULAR,
            ),
        )
    }

    @Suppress("NewApi")
    private fun hasWriteStoragePermission(): Boolean {
        return appBuildConfig.sdkInt >= 30 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PERMISSION_GRANTED
    }

    private fun requestWriteStoragePermission() {
        requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PERMISSION_GRANTED
        ) {
            downloadSummaryOfBenefits()
        }
    }

    private fun launchDownloadMessagesJob() {
        downloadMessagesJob += lifecycleScope.launch {
            downloadCallback.commands().cancellable().collect {
                processFileDownloadedCommand(it)
            }
        }
    }

    private fun processFileDownloadedCommand(command: DownloadCommand) {
        when (command) {
            is DownloadCommand.ShowDownloadStartedMessage -> downloadStarted(command)
            is DownloadCommand.ShowDownloadFailedMessage -> downloadFailed(command)
            is DownloadCommand.ShowDownloadSuccessMessage -> downloadSucceeded(command)
        }
    }

    @SuppressLint("WrongConstant")
    private fun downloadStarted(command: DownloadCommand.ShowDownloadStartedMessage) {
        binding.root.makeSnackbarWithNoBottomInset(getString(command.messageId, command.fileName), DOWNLOAD_SNACKBAR_LENGTH).show()
    }

    private fun downloadFailed(command: DownloadCommand.ShowDownloadFailedMessage) {
        val downloadFailedSnackbar = binding.root.makeSnackbarWithNoBottomInset(getString(command.messageId), Snackbar.LENGTH_LONG)
        binding.root.postDelayed({ downloadFailedSnackbar.show() }, DOWNLOAD_SNACKBAR_DELAY)
    }

    private fun downloadSucceeded(command: DownloadCommand.ShowDownloadSuccessMessage) {
        val downloadSucceededSnackbar = binding.root.makeSnackbarWithNoBottomInset(
            getString(command.messageId, command.fileName),
            Snackbar.LENGTH_LONG,
        )
            .apply {
                this.setAction(R.string.downloadsDownloadFinishedActionName) {
                    val result = downloadsFileActions.openFile(context, File(command.filePath))
                    if (!result) {
                        view.makeSnackbarWithNoBottomInset(getString(R.string.downloadsCannotOpenFileErrorMessage), Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        binding.root.postDelayed({ downloadSucceededSnackbar.show() }, DOWNLOAD_SNACKBAR_DELAY)
    }

    companion object {
        private const val PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE = 200
        private const val PDF_MIME_TYPE = "application/pdf"
    }
}

enum class OnboardingFeature(
    @DrawableRes val iconRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @LayoutRes val contentRes: Int,
) {
    VPN(
        iconRes = R.drawable.vpn_feature_128,
        titleRes = R.string.subscriptionOnboardingFeatureInfoVpnTitle,
        descriptionRes = R.string.subscriptionOnboardingFeatureInfoVpnDescription,
        contentRes = R.layout.content_subscription_onboarding_feature_info_vpn,
    ),
    ITR(
        iconRes = R.drawable.identity_theft_restoration_feature_128,
        titleRes = R.string.subscriptionOnboardingFeatureInfoItrTitle,
        descriptionRes = R.string.subscriptionOnboardingFeatureInfoItrDescription,
        contentRes = R.layout.content_subscription_onboarding_feature_info_idtr,
    ),
    DUCK_AI(
        iconRes = R.drawable.duckai_ddg_feature_128,
        titleRes = R.string.subscriptionOnboardingFeatureInfoDuckAiTitle,
        descriptionRes = R.string.subscriptionOnboardingFeatureInfoDuckAiDescription,
        contentRes = R.layout.content_subscription_onboarding_feature_info_duckai,
    ),
    PIR(
        iconRes = R.drawable.personal_information_remover_feature_128,
        titleRes = R.string.subscriptionOnboardingFeatureInfoPirTitle,
        descriptionRes = R.string.subscriptionOnboardingFeatureInfoPirDescription,
        contentRes = R.layout.content_subscription_onboarding_feature_info_pir,
    ),
}
