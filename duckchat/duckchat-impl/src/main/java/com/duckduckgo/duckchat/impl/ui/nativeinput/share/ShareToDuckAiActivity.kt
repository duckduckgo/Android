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

package com.duckduckgo.duckchat.impl.ui.nativeinput.share

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.core.content.IntentCompat
import com.duckduckgo.app.tabs.Attachment
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.codegen.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.api.DuckChatEntryPoint
import com.duckduckgo.duckchat.impl.DuckChatInternal
import logcat.LogPriority
import logcat.logcat
import javax.inject.Inject

/**
 * Exists purely to pull out the shared text, images and files and attach them to a new Duck.ai chat.
 * This needs to be its own Activity so that we can customize the label that is user-facing, presented in the share sheet and "Open with".
 */
@InjectWith(ActivityScope::class)
class ShareToDuckAiActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var browserNav: BrowserNav

    @Inject
    lateinit var duckChatInternal: DuckChatInternal

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }
        val attachments = listOfNotNull(text?.let(Attachment::Text)) + sharedFiles(intent).map(Attachment::File)
        if (attachments.isNotEmpty()) {
            duckChatInternal.reportDuckChatEntry(DuckChatEntryPoint.SHARE_SHEET, opensNewTab = true, hasPrompt = false)
            try {
                startActivity(
                    browserNav.openDuckChat(
                        this,
                        duckChatUrl = duckChatInternal.getDuckChatUrl(query = "", autoPrompt = false),
                        attachments = attachments,
                    ),
                )
            } catch (e: SecurityException) {
                logcat(LogPriority.WARN) { "ShareToDuckAiActivity couldn't forward the shared files: ${e.message}" }
            }
        } else {
            logcat(LogPriority.WARN) { "ShareToDuckAiActivity launched with nothing to share" }
        }
        finish()
    }

    private fun sharedFiles(intent: Intent): List<Uri> =
        runCatching {
            when (intent.action) {
                Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
                Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                Intent.ACTION_VIEW -> listOfNotNull(intent.data)
                else -> emptyList()
            }
        }.getOrDefault(emptyList()).filter(::canRead)

    private fun canRead(uri: Uri?): Boolean =
        uri != null &&
            (
                uri.authority?.substringAfterLast('@') == "$packageName.provider" ||
                    checkCallingOrSelfUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED
                )
}
