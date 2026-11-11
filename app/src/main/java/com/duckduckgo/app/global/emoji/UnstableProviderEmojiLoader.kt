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

package com.duckduckgo.app.global.emoji

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.ParcelFileDescriptor
import androidx.core.provider.FontRequest
import androidx.core.provider.FontsContractCompat
import androidx.emoji2.text.EmojiCompat
import androidx.emoji2.text.MetadataRepo
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Loads the EmojiCompat font from the system emoji font provider (Play Services), like emoji2's default config, but only
 * through an unstable provider connection.
 *
 * The default config opens the font with ContentResolver.openFileDescriptor, which holds a stable provider reference while
 * the file is open. ActivityManager kills every client holding a stable reference when the provider process dies, so a
 * Play Services restart or self-update during that window kills the app. With an unstable client, the provider dying only
 * fails this load.
 */
internal class UnstableProviderEmojiLoader(context: Context) : EmojiCompat.MetadataRepoLoader {

    private val context = context.applicationContext

    private val fontRequest: FontRequest? by lazy { emojiFontRequest() }

    fun hasProvider(): Boolean = fontRequest != null

    override fun load(loaderCallback: EmojiCompat.MetadataRepoLoaderCallback) {
        runCatching { loadMetadataRepo() }
            .onSuccess { loaderCallback.onLoaded(it) }
            .onFailure { loaderCallback.onFailed(it) }
    }

    private fun loadMetadataRepo(): MetadataRepo {
        val request = fontRequest ?: error("No system emoji font provider")
        val result = FontsContractCompat.fetchFonts(context, null, request)
        check(result.statusCode == FontsContractCompat.FontFamilyResult.STATUS_OK) { "fetchFonts status ${result.statusCode}" }
        val font = result.fonts.firstOrNull() ?: error("No emoji font returned")
        check(font.resultCode == FontsContractCompat.Columns.RESULT_CODE_OK) { "Emoji font result ${font.resultCode}" }

        val client = context.contentResolver.acquireUnstableContentProviderClient(font.uri) ?: error("Emoji font provider unavailable")
        return client.use {
            val fd = it.openFile(font.uri, "r") ?: error("Unable to open emoji font")
            val typeface = Typeface.Builder(fd.fileDescriptor).build()
            MetadataRepo.create(typeface, fd.mapAndClose())
        }
    }

    private fun ParcelFileDescriptor.mapAndClose(): MappedByteBuffer =
        ParcelFileDescriptor.AutoCloseInputStream(this).channel.use { channel ->
            channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
        }

    @Suppress("DEPRECATION")
    private fun emojiFontRequest(): FontRequest? {
        val packageManager = context.packageManager
        val provider = packageManager.queryIntentContentProviders(Intent(ACTION_LOAD_EMOJI_FONT), 0)
            .mapNotNull { it.providerInfo }
            .firstOrNull { it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 }
            ?: return null
        val signatures = packageManager.getPackageInfo(provider.packageName, PackageManager.GET_SIGNATURES).signatures
            ?.map { it.toByteArray() }
            .orEmpty()
        return FontRequest(provider.authority, provider.packageName, EMOJI_FONT_QUERY, listOf(signatures))
    }

    private companion object {
        const val ACTION_LOAD_EMOJI_FONT = "androidx.content.action.LOAD_EMOJI_FONT"
        const val EMOJI_FONT_QUERY = "emojicompat-emoji-font"
    }
}
