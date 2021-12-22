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

package com.duckduckgo.app.browser

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.downloader.FileDownloader.PendingFileDownload
import com.duckduckgo.app.browser.downloader.FilenameExtractor
import com.duckduckgo.app.browser.downloader.isDataUrl
import com.duckduckgo.di.scopes.FragmentScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.android.support.AndroidSupportInjection
import kotlinx.android.synthetic.main.download_confirmation.view.*
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class DownloadConfirmationFragment : BottomSheetDialogFragment() {

    val listener: DownloadConfirmationDialogListener
        get() = parentFragment as DownloadConfirmationDialogListener

    @Inject
    lateinit var filenameExtractor: FilenameExtractor

    private var file: File? = null

    private val pendingDownload: PendingFileDownload by lazy {
        requireArguments()[PENDING_DOWNLOAD_BUNDLE_KEY] as PendingFileDownload
    }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.download_confirmation, container, false)
        setupDownload()
        setupViews(view)
        return view
    }

    private fun setupDownload() {
        file = if (!pendingDownload.isDataUrl) {
            when (val filenameExtraction = filenameExtractor.extract(pendingDownload)) {
                is FilenameExtractor.FilenameExtractionResult.Guess -> null
                is FilenameExtractor.FilenameExtractionResult.Extracted -> File(pendingDownload.directory, filenameExtraction.filename)
            }
        } else {
            null
        }
    }

    private fun setupViews(view: View) {
        val fileName = file?.name ?: ""
        view.downloadMessage.text = fileName
        view.continueDownload.setOnClickListener {
            listener.continueDownload(pendingDownload)
            dismiss()
        }
        view.cancel.setOnClickListener {
            Timber.i("Cancelled download for url ${pendingDownload.url}")
            listener.cancelDownload()
            dismiss()
        }
    }

    interface DownloadConfirmationDialogListener {
        fun continueDownload(pendingFileDownload: PendingFileDownload)
        fun cancelDownload()
    }

    companion object {

        private const val PENDING_DOWNLOAD_BUNDLE_KEY = "PENDING_DOWNLOAD_BUNDLE_KEY"

        fun instance(pendingDownload: PendingFileDownload): DownloadConfirmationFragment {
            val fragment = DownloadConfirmationFragment()
            fragment.isCancelable = false
            val bundle = Bundle()
            bundle.putSerializable(PENDING_DOWNLOAD_BUNDLE_KEY, pendingDownload)
            fragment.arguments = bundle
            return fragment
        }
    }
}
