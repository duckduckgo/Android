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

package com.duckduckgo.downloads.impl

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.downloads.api.DownloadsFileActions
import java.io.File
import javax.inject.Inject

/**
 * Receives the tap on a completed-download notification and hands the file to an external viewer. Android 12+ forbids a
 * BroadcastReceiver from starting an Activity, so this indirection has to be an Activity.
 */
@InjectWith(ActivityScope::class)
class OpenDownloadedFileActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var downloadsFileActions: DownloadsFileActions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val filePath = intent.getStringExtra(FILE_PATH_EXTRA)
        if (filePath != null) {
            if (!downloadsFileActions.openFile(this, File(filePath))) {
                Toast.makeText(applicationContext, R.string.downloadsCannotOpenFileErrorMessage, Toast.LENGTH_LONG).show()
            }
        }

        finish()
    }

    companion object {
        private const val FILE_PATH_EXTRA = "FILE_PATH_EXTRA"

        fun intent(context: Context, file: File): Intent {
            return Intent(context, OpenDownloadedFileActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(FILE_PATH_EXTRA, file.absolutePath)
            }
        }
    }
}
