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

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.core.content.FileProvider.getUriForFile
import com.duckduckgo.app.browser.downloader.NetworkFileDownloadManager.DownloadFileData
import com.duckduckgo.app.browser.downloader.NetworkFileDownloadManager.UserDownloadAction
import com.duckduckgo.app.global.view.gone
import com.duckduckgo.app.global.view.show
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.android.synthetic.main.download_confirmation.view.*
import timber.log.Timber

class DownloadConfirmationFragment(
    private val downloadFileData: DownloadFileData,
    private val userDownloadAction: UserDownloadAction
) : BottomSheetDialogFragment() {

    @SuppressLint("RestrictedApi")
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        val view = LayoutInflater.from(context).inflate(R.layout.download_confirmation, null)
        dialog.setContentView(view)
        setupViews(view)
    }

    private fun setupViews(view: View) {
        view.download_message.text =
            getString(R.string.download_filename, downloadFileData.file.name)
        view.open_with.setOnClickListener {
            openFile()
            dismiss()
        }
        view.replace.setOnClickListener {
            userDownloadAction.acceptAndReplace()
            dismiss()
        }
        view.keep_both.setOnClickListener {
            userDownloadAction.accept()
            dismiss()
        }
        view.continue_download.setOnClickListener {
            userDownloadAction.accept()
            dismiss()
        }
        view.cancel.setOnClickListener {
            userDownloadAction.cancel()
            dismiss()
        }

        if (downloadFileData.alreadyDownloaded) {
            view.already_downloaded_options.show()
            view.continue_download.gone()
        } else {
            view.already_downloaded_options.gone()
            view.continue_download.show()
        }
    }

    private fun openFile() {
        val uri = getUriForFile(
            context!!,
            "${BuildConfig.APPLICATION_ID}.provider",
            downloadFileData.file
        )
        val mime = activity?.contentResolver?.getType(uri)

        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, mime)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Timber.e(e,"No suitable activity found")
            Toast.makeText(activity, "Can't open file", Toast.LENGTH_SHORT).show()
        }
    }
}