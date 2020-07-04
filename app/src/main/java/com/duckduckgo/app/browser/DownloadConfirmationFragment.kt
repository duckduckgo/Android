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
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider.getUriForFile
import com.duckduckgo.app.browser.downloader.FileDownloader
import com.duckduckgo.app.browser.downloader.FileDownloader.FileDownloadListener
import com.duckduckgo.app.browser.downloader.FileDownloader.PendingFileDownload
import com.duckduckgo.app.browser.downloader.guessFileName
import com.duckduckgo.app.browser.downloader.isDataUrl
import com.duckduckgo.app.global.view.gone
import com.duckduckgo.app.global.view.leftDrawable
import com.duckduckgo.app.global.view.show
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.android.support.AndroidSupportInjection
import kotlinx.android.synthetic.main.download_confirmation.view.*
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlin.concurrent.thread

class DownloadConfirmationFragment : BottomSheetDialogFragment() {

    @Inject
    lateinit var downloader: FileDownloader

    lateinit var downloadListener: FileDownloadListener

    private val pendingDownload: PendingFileDownload by lazy {
        requireArguments()[PENDING_DOWNLOAD_BUNDLE_KEY] as PendingFileDownload
    }

    private var file: File? = null

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.download_confirmation, container, false)
        setupDownload()
        setupViews(view)
        return view
    }

    private fun setupDownload() {
        file = if (!pendingDownload.isDataUrl) File(pendingDownload.directory, pendingDownload.guessFileName()) else null
    }

    private fun setupViews(view: View) {
        view.downloadMessage.text = getString(R.string.downloadConfirmationSaveFileTitle, file?.name ?: "")
        view.replace.setOnClickListener {
            deleteFile()
            completeDownload(pendingDownload, downloadListener)
            dismiss()
        }
        view.continueDownload.setOnClickListener {
            completeDownload(pendingDownload, downloadListener)
            dismiss()
        }
        view.openWith.setOnClickListener {
            openFile()
            dismiss()
        }
        view.cancel.setOnClickListener {
            Timber.i("Cancelled download for url ${pendingDownload.url}")
            downloadListener.downloadCancelled()
            dismiss()
        }

        if (file?.exists() == true) {
            view.openWith.show()
            view.replace.show()
            view.continueDownload.text = getString(R.string.downloadConfirmationKeepBothFilesText)
            view.continueDownload.leftDrawable(R.drawable.ic_keepboth_brownish_24dp)
        } else {
            view.openWith.gone()
            view.replace.gone()
            view.continueDownload.text = getString(R.string.downloadConfirmationContinue)
            view.continueDownload.leftDrawable(R.drawable.ic_file_brownish_24dp)
        }
    }

    private fun deleteFile() {
        try {
            file?.delete()
        } catch (e: IOException) {
            Toast.makeText(activity, R.string.downloadConfirmationUnableToDeleteFileText, Toast.LENGTH_SHORT).show()
        }
    }

    private fun completeDownload(pendingDownload: PendingFileDownload, callback: FileDownloadListener) {
        thread {
            downloader.download(pendingDownload, callback)
        }
    }

    private fun openFile() {
        val intent = context?.let { createIntentToOpenFile(it) }
        activity?.packageManager?.let { packageManager ->
            if (intent?.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                Timber.e("No suitable activity found")
                Toast.makeText(activity, R.string.downloadConfirmationUnableToOpenFileText, Toast.LENGTH_SHORT).show()
            }
            downloadListener.downloadOpened()
        }
    }

    private fun createIntentToOpenFile(context: Context): Intent? {
        val file = file ?: return null
        val uri = getUriForFile(context, "${BuildConfig.APPLICATION_ID}.provider", file)
        val mime = activity?.contentResolver?.getType(uri) ?: return null
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, mime)
        return intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    companion object {

        private const val PENDING_DOWNLOAD_BUNDLE_KEY = "PENDING_DOWNLOAD_BUNDLE_KEY"

        fun instance(pendingDownload: PendingFileDownload, downloadListener: FileDownloadListener): DownloadConfirmationFragment {
            val fragment = DownloadConfirmationFragment()
            val bundle = Bundle()
            bundle.putSerializable(PENDING_DOWNLOAD_BUNDLE_KEY, pendingDownload)
            fragment.arguments = bundle
            fragment.downloadListener = downloadListener
            return fragment
        }
    }
}
