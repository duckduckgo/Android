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
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Environment
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.ui.view.addClickableLink
import com.duckduckgo.common.ui.view.makeSnackbarWithNoBottomInset
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.downloads.api.DOWNLOAD_SNACKBAR_DELAY
import com.duckduckgo.downloads.api.DOWNLOAD_SNACKBAR_LENGTH
import com.duckduckgo.downloads.api.DownloadCommand
import com.duckduckgo.downloads.api.DownloadStateListener
import com.duckduckgo.downloads.api.DownloadsFileActions
import com.duckduckgo.downloads.api.FileDownloader
import com.duckduckgo.downloads.api.FileDownloader.PendingFileDownload
import com.duckduckgo.subscriptions.impl.R
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.ITR_SUMMARY_OF_BENEFITS_URL
import com.duckduckgo.subscriptions.impl.databinding.ViewSummaryOfBenefitsFooterBinding
import com.google.android.material.snackbar.Snackbar
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.io.File
import javax.inject.Inject

@InjectWith(ViewScope::class)
class SummaryOfBenefitsFooterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject
    lateinit var fileDownloader: FileDownloader

    @Inject
    lateinit var downloadCallback: DownloadStateListener

    @Inject
    lateinit var downloadsFileActions: DownloadsFileActions

    private val binding = ViewSummaryOfBenefitsFooterBinding.inflate(LayoutInflater.from(context), this)

    private val downloadMessagesJob = ConflatedJob()
    var onWriteStoragePermissionRequired: (() -> Unit)? = null

    fun onWriteStoragePermissionGranted() {
        downloadSummaryOfBenefits()
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        binding.summaryOfBenefitsFooterText.addClickableLink(
            annotation = "summary_of_benefits_link",
            textSequence = context.getText(R.string.subscriptionOnboardingFeatureInfoItrLegalFooter),
        ) {
            if (hasWriteStoragePermission()) {
                downloadSummaryOfBenefits()
            } else {
                onWriteStoragePermissionRequired?.invoke()
            }
        }

        val lifecycleOwner = findViewTreeLifecycleOwner() ?: return
        downloadMessagesJob += downloadCallback.commands()
            .flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.RESUMED)
            .onEach { processFileDownloadedCommand(it) }
            .launchIn(lifecycleOwner.lifecycleScope)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        downloadMessagesJob.cancel()
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
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PERMISSION_GRANTED
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
        makeSnackbarWithNoBottomInset(context.getString(command.messageId, command.fileName), DOWNLOAD_SNACKBAR_LENGTH).show()
    }

    private fun downloadFailed(command: DownloadCommand.ShowDownloadFailedMessage) {
        val downloadFailedSnackbar = makeSnackbarWithNoBottomInset(context.getString(command.messageId), Snackbar.LENGTH_LONG)
        postDelayed({ downloadFailedSnackbar.show() }, DOWNLOAD_SNACKBAR_DELAY)
    }

    private fun downloadSucceeded(command: DownloadCommand.ShowDownloadSuccessMessage) {
        val downloadSucceededSnackbar = makeSnackbarWithNoBottomInset(
            context.getString(command.messageId, command.fileName),
            Snackbar.LENGTH_LONG,
        )
            .apply {
                this.setAction(R.string.downloadsDownloadFinishedActionName) {
                    val result = downloadsFileActions.openFile(context, File(command.filePath))
                    if (!result) {
                        view.makeSnackbarWithNoBottomInset(
                            context.getString(R.string.downloadsCannotOpenFileErrorMessage),
                            Snackbar.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        postDelayed({ downloadSucceededSnackbar.show() }, DOWNLOAD_SNACKBAR_DELAY)
    }

    companion object {
        private const val PDF_MIME_TYPE = "application/pdf"
    }
}
