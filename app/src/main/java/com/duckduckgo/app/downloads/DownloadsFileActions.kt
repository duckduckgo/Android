/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.app.downloads

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.duckduckgo.app.browser.R
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import java.io.File
import javax.inject.Inject
import timber.log.Timber

class DownloadsFileActions @Inject constructor(private val appBuildConfig: AppBuildConfig) {

    fun openFile(applicationContext: Context, file: File): Boolean {
        val intent = createIntentToOpenFile(applicationContext, file)
        return applicationContext.packageManager?.let { packageManager ->
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(applicationContext, intent)
            } else {
                Timber.e("Failed to resolve activity")
                false
            }
        }
            ?: false
    }

    fun shareFile(applicationContext: Context, file: File): Boolean {
        val intent = createShareIntent(applicationContext, file)
        return if (intent != null) startActivity(applicationContext, intent) else false
    }

    private fun startActivity(applicationContext: Context, intent: Intent): Boolean {
        return try {
            applicationContext.startActivity(intent)
            true
        } catch (error: ActivityNotFoundException) {
            Timber.e("No suitable activity found to satisfy intent $intent")
            false
        }
    }

    private fun createIntentToOpenFile(applicationContext: Context, file: File): Intent {
        val fileUri = getFilePathUri(applicationContext, file)
        return Intent().apply {
            setDataAndType(fileUri, applicationContext.contentResolver?.getType(fileUri))
            // TODO setDataAndType(fileUri, "application/vnd.android.package-archive") handle APKs (is this needed?)
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
    }

    private fun getFilePathUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(context, "${appBuildConfig.applicationId}.provider", file)
    }

    private fun createShareIntent(applicationContext: Context, file: File): Intent? {
        val fileUri = getFilePathUri(applicationContext, file)
        val intent =
            Intent().apply {
                setDataAndType(fileUri, applicationContext.contentResolver?.getType(fileUri))
                action = Intent.ACTION_SEND
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                putExtra(Intent.EXTRA_STREAM, fileUri)
            }
        return Intent.createChooser(
            intent, applicationContext.getString(R.string.downloadsShareTitle)
        )
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Show a thumbnail preview of the file to be shared on Android Q and above.
                    clipData = ClipData.newRawUri(fileUri.toString(), fileUri)
                }
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
    }
}
