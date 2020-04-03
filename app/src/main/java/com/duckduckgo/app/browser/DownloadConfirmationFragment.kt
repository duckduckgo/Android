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
import com.duckduckgo.app.global.view.gone
import com.duckduckgo.app.global.view.leftDrawable
import com.duckduckgo.app.global.view.show
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.android.synthetic.main.download_confirmation.view.*
import timber.log.Timber
import java.io.File

class DownloadConfirmationFragment(
    private val downloadFileData: DownloadFileData,
    private val userDownloadAction: UserDownloadAction
) : BottomSheetDialogFragment() {

    interface UserDownloadAction {
        fun accept()
        fun acceptAndReplace()
        fun cancel()
    }

    class DownloadFileData(val file: File?, val alreadyDownloaded: Boolean)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.download_confirmation, container, false)
        setupViews(view)
        return view
    }

    private fun setupViews(view: View) {
        view.downloadMessage.text = getString(R.string.downloadConfirmationSaveFileTitle, downloadFileData.file?.name ?: "")
        view.openWith.setOnClickListener {
            openFile()
            dismiss()
        }
        view.replace.setOnClickListener {
            userDownloadAction.acceptAndReplace()
            dismiss()
        }
        view.continueDownload.setOnClickListener {
            userDownloadAction.accept()
            dismiss()
        }
        view.cancel.setOnClickListener {
            userDownloadAction.cancel()
            dismiss()
        }

        if (downloadFileData.alreadyDownloaded) {
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

    private fun openFile() {
        val intent = context?.let { createIntentToOpenFile(it) }
        activity?.packageManager?.let { packageManager ->
            if (intent?.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                Timber.e("No suitable activity found")
                Toast.makeText(activity, R.string.downloadConfirmationUnableToOpenFileText, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createIntentToOpenFile(context: Context): Intent? {
        val file = downloadFileData.file ?: return null
        val uri = getUriForFile(context, "${BuildConfig.APPLICATION_ID}.provider", file)
        val mime = activity?.contentResolver?.getType(uri) ?: return null
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, mime)
        return intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}