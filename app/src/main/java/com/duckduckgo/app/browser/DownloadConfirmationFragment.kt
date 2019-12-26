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
import android.view.LayoutInflater
import com.duckduckgo.app.browser.downloader.NetworkFileDownloader
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.android.synthetic.main.download_confirmation.view.*

class DownloadConfirmationFragment(private val fileName: String, private val userDownloadAction: NetworkFileDownloader.UserDownloadAction) : BottomSheetDialogFragment() {

    @SuppressLint("RestrictedApi")
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        val view = LayoutInflater.from(context).inflate(R.layout.download_confirmation, null)
        dialog.setContentView(view)

        view.download_message.text = getString(R.string.download_filename, fileName)
        view.continue_download.setOnClickListener {
            userDownloadAction.accept()
            dismiss()
        }
        view.cancel.setOnClickListener {
            userDownloadAction.cancel()
            dismiss()
        }
    }
}