/*
 * Copyright (c) 2024 DuckDuckGo
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
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.downloads.api.DownloadsFileActions
import com.squareup.anvil.annotations.ContributesBinding
import logcat.LogPriority.ERROR
import logcat.logcat
import java.io.File
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealDownloadsFileActions @Inject constructor(private val appBuildConfig: AppBuildConfig) : DownloadsFileActions {

    override fun openFile(applicationContext: Context, file: File): Boolean {
        return openFileAtPath(applicationContext, file.absolutePath)
    }

    override fun shareFile(applicationContext: Context, file: File): Boolean {
        return shareFileAtPath(applicationContext, file.absolutePath)
    }

    override fun openFileAtPath(applicationContext: Context, filePath: String): Boolean {
        val intent = createIntentToOpenFile(applicationContext, filePath)
        return applicationContext.packageManager?.let { packageManager ->
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(applicationContext, intent)
            } else {
                logcat(ERROR) { "Failed to resolve activity" }
                false
            }
        }
            ?: false
    }

    override fun shareFileAtPath(applicationContext: Context, filePath: String): Boolean {
        val intent = createShareIntent(applicationContext, filePath)
        return if (intent != null) startActivity(applicationContext, intent) else false
    }

    private fun startActivity(applicationContext: Context, intent: Intent): Boolean {
        return try {
            applicationContext.startActivity(intent)
            true
        } catch (error: ActivityNotFoundException) {
            logcat(ERROR) { "No suitable activity found to satisfy intent $intent" }
            false
        }
    }

    private fun createIntentToOpenFile(applicationContext: Context, filePath: String): Intent {
        val fileUri = getFilePathUri(applicationContext, filePath)
        return Intent().apply {
            setDataAndType(fileUri, applicationContext.contentResolver?.getType(fileUri))
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
    }

    private fun getFilePathUri(context: Context, filePath: String): Uri {
        return if (filePath.startsWith(CONTENT_URI_PREFIX)) {
            Uri.parse(filePath)
        } else {
            FileProvider.getUriForFile(context, "${appBuildConfig.applicationId}.provider", File(filePath))
        }
    }

    private fun createShareIntent(applicationContext: Context, filePath: String): Intent? {
        val fileUri = getFilePathUri(applicationContext, filePath)
        val intent =
            Intent().apply {
                setDataAndType(fileUri, applicationContext.contentResolver?.getType(fileUri))
                action = Intent.ACTION_SEND
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                putExtra(Intent.EXTRA_STREAM, fileUri)
            }
        return Intent.createChooser(
            intent,
            applicationContext.getString(R.string.downloadsShareTitle),
        )
            .apply {
                if (appBuildConfig.sdkInt >= Build.VERSION_CODES.Q) {
                    clipData = ClipData.newRawUri(fileUri.toString(), fileUri)
                }
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
    }

    companion object {
        private const val CONTENT_URI_PREFIX = "content://"
    }
}
