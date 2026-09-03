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

package com.duckduckgo.subscriptions.impl.onboarding.itr

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Environment
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.view.addClickableLink
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.makeSnackbarWithNoBottomInset
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.downloads.api.DOWNLOAD_SNACKBAR_DELAY
import com.duckduckgo.downloads.api.DOWNLOAD_SNACKBAR_LENGTH
import com.duckduckgo.downloads.api.DownloadCommand
import com.duckduckgo.downloads.api.DownloadStateListener
import com.duckduckgo.downloads.api.DownloadsFileActions
import com.duckduckgo.downloads.api.FileDownloader
import com.duckduckgo.downloads.api.FileDownloader.PendingFileDownload
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingController
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepOutcome.COMPLETED
import com.duckduckgo.subscriptions.impl.R
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.ITR_SUMMARY_OF_BENEFITS_URL
import com.duckduckgo.subscriptions.impl.databinding.FragmentSubscriptionOnboardingItrBinding
import com.duckduckgo.subscriptions.impl.onboarding.features.OnboardingFeature
import com.duckduckgo.subscriptions.impl.onboarding.itr.SubscriptionOnboardingItrStepPlugin.Companion.ITR_STEP_ID
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Identity Theft Restoration step of the native subscription onboarding. Shows the same content as the ITR
 * feature-detail screen, but with an always-visible Activate button and the content scrolling behind it.
 * Reports back through [SubscriptionOnboardingController] so it stays decoupled from the host activity.
 */
@InjectWith(FragmentScope::class)
class SubscriptionOnboardingItrFragment : DuckDuckGoFragment(R.layout.fragment_subscription_onboarding_itr) {

    @Inject
    lateinit var controller: SubscriptionOnboardingController

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject
    lateinit var fileDownloader: FileDownloader

    @Inject
    lateinit var downloadCallback: DownloadStateListener

    @Inject
    lateinit var downloadsFileActions: DownloadsFileActions

    private val binding: FragmentSubscriptionOnboardingItrBinding by viewBinding()

    private val downloadMessagesJob = ConflatedJob()

    private val writeStoragePermission = registerForActivityResult(RequestPermission()) { granted ->
        if (granted) {
            downloadSummaryOfBenefits()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val feature = OnboardingFeature.ITR
        binding.subscriptionOnboardingItrIcon.setImageResource(feature.iconRes)
        binding.subscriptionOnboardingItrTitle.setText(feature.titleRes)
        binding.subscriptionOnboardingItrDescription.setText(feature.descriptionRes)
        layoutInflater.inflate(feature.contentRes, binding.subscriptionOnboardingItrContent, true)

        setupSummaryOfBenefitsLink()

        binding.subscriptionOnboardingItrActivateButton.setOnClickListener {
            controller.onStepFinished(ITR_STEP_ID, COMPLETED)
        }

        setupScrollFade()
    }

    override fun onResume() {
        launchDownloadMessagesJob()
        super.onResume()
    }

    override fun onPause() {
        downloadMessagesJob.cancel()
        super.onPause()
    }

    private fun setupScrollFade() {
        // A gradient can't reference ?attr colors, so derive it from the surface the content sits on. Starting
        // from a zero-alpha surface rather than Color.TRANSPARENT keeps the hue constant across the ramp.
        val surfaceColor = requireContext().getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorSurface)
        binding.subscriptionOnboardingItrScrollFade.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(ColorUtils.setAlphaComponent(surfaceColor, 0), surfaceColor),
        )

        binding.subscriptionOnboardingItrScrollView.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { _, _, _, _, _ -> updateScrollFade() },
        )
        // The per-feature content is inflated above, so the first reliable measurement is the next layout pass.
        binding.subscriptionOnboardingItrScrollView.doOnLayout { updateScrollFade() }
    }

    private fun updateScrollFade() {
        binding.subscriptionOnboardingItrScrollFade.isVisible =
            binding.subscriptionOnboardingItrScrollView.canScrollVertically(1)
    }

    private fun setupSummaryOfBenefitsLink() {
        binding.subscriptionOnboardingItrContent
            .findViewById<DaxTextView>(R.id.subscriptionOnboardingFeatureInfoLegalFooter)
            .addClickableLink(
                annotation = "summary_of_benefits_link",
                textSequence = getText(R.string.subscriptionOnboardingFeatureInfoItrLegalFooter),
            ) {
                if (hasWriteStoragePermission()) {
                    downloadSummaryOfBenefits()
                } else {
                    writeStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
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
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PERMISSION_GRANTED
    }

    private fun launchDownloadMessagesJob() {
        downloadMessagesJob += viewLifecycleOwner.lifecycleScope.launch {
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
        private const val PDF_MIME_TYPE = "application/pdf"
    }
}
