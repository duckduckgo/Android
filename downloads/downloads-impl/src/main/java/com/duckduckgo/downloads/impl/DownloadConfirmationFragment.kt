/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.downloads.impl

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.utils.baseHost
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.downloads.api.DownloadConfirmationDialogListener
import com.duckduckgo.downloads.api.FileDownloader.PendingFileDownload
import com.duckduckgo.downloads.impl.DataUriParser.ParseResult
import com.duckduckgo.downloads.impl.RealDownloadConfirmation.Companion.PENDING_DOWNLOAD_KEY
import com.duckduckgo.downloads.impl.databinding.DownloadConfirmationBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.android.support.AndroidSupportInjection
import logcat.logcat
import java.io.File
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class DownloadConfirmationFragment : BottomSheetDialogFragment() {

    override fun getTheme(): Int = R.style.DownloadsBottomSheetDialogTheme

    val listener: DownloadConfirmationDialogListener
        get() {
            return if (parentFragment != null) {
                parentFragment as DownloadConfirmationDialogListener
            } else {
                activity as DownloadConfirmationDialogListener
            }
        }

    @Inject
    lateinit var filenameExtractor: FilenameExtractor

    @Inject
    lateinit var dataUriParser: DataUriParser

    private var file: File? = null

    private var pendingDownload: PendingFileDownload? = null

    private var storeKey: String? = null

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val binding = DownloadConfirmationBinding.inflate(inflater, container, false)

        storeKey = arguments?.getString(PENDING_DOWNLOAD_KEY)
        pendingDownload = storeKey?.let { PendingDownloadStore.get(it) }
        if (pendingDownload == null) {
            logcat { "PendingFileDownload not found; process may have been killed while dialog was visible" }
            listener.cancelDownload()
            dismiss()
            return binding.root
        }

        setupDownload()
        setupViews(binding)
        return binding.root
    }

    private fun setupDownload() {
        val download = pendingDownload ?: return
        file = if (!download.isDataUrl) {
            when (val filenameExtraction = filenameExtractor.extract(download)) {
                is FilenameExtractor.FilenameExtractionResult.Guess -> null
                is FilenameExtractor.FilenameExtractionResult.Extracted -> File(download.directory, filenameExtraction.filename)
            }
        } else {
            when (val parsed = dataUriParser.generate(download.url, download.fileName)) {
                is ParseResult.ParsedDataUri -> File(download.directory, parsed.filename.toString())
                else -> null
            }
        }
    }

    private fun setupViews(binding: DownloadConfirmationBinding) {
        val download = pendingDownload ?: return
        (dialog as BottomSheetDialog).behavior.state = BottomSheetBehavior.STATE_EXPANDED
        val fileName = file?.name ?: ""
        binding.downloadMessage.text = fileName
        binding.downloadMessageSubtitle.run {
            val host = if (download.isDataUrl) null else runCatching { Uri.parse(download.url).baseHost }.getOrNull()

            isVisible = !host.isNullOrBlank()
            text = getString(R.string.downloadConfirmationSubtitle, host)
        }
        binding.continueDownload.setOnClickListener {
            listener.continueDownload(download)
            dismiss()
        }
        binding.cancel.setOnClickListener {
            logcat { "Cancelled download for url ${download.url.take(200)}" }
            listener.cancelDownload()
            dismiss()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val isConfigChange = activity?.isChangingConfigurations == true
        if (!isConfigChange) {
            storeKey?.let { PendingDownloadStore.remove(it) }
        }
    }
}
