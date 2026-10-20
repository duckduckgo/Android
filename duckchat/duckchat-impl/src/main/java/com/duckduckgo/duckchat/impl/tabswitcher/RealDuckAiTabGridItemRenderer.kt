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

package com.duckduckgo.duckchat.impl.tabswitcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.FireMode
import com.duckduckgo.browsermode.api.RegularMode
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.api.tabswitcher.DuckAiTabGridItemContent
import com.duckduckgo.duckchat.api.tabswitcher.DuckAiTabGridItemRenderer
import com.duckduckgo.duckchat.api.tabswitcher.DuckAiTabGridItemView
import com.duckduckgo.duckchat.api.toChatIdOrNull
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.voice.VoiceSessionStateManager
import com.duckduckgo.duckchat.store.impl.DuckAiChatPreview
import com.duckduckgo.duckchat.store.impl.DuckAiChatStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@ContributesBinding(ActivityScope::class)
@SingleIn(ActivityScope::class)
class RealDuckAiTabGridItemRenderer @Inject constructor(
    @param:RegularMode private val regularChatStore: DuckAiChatStore,
    @param:FireMode private val fireChatStore: DuckAiChatStore,
    private val duckChatFeature: DuckChatFeature,
    private val voiceSessionStateManager: VoiceSessionStateManager,
    private val duckChat: DuckChat,
    private val dispatchers: DispatcherProvider,
    @param:AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : DuckAiTabGridItemRenderer {

    // Lives as long as the tab switcher, so each chat is read and parsed once per opening and shows its state at that point.
    private val chatPreviews = ConcurrentHashMap<Pair<BrowserMode, String>, DuckAiChatPreview>()

    // A read that throws would throw again, and every list emission (each selection tap included) would otherwise retry and log it.
    private val unreadableChats = ConcurrentHashMap.newKeySet<Pair<BrowserMode, String>>()

    // Checked once per opening rather than for every Duck.ai tab on every list emission.
    private val nativeStorageReady = ConcurrentHashMap<BrowserMode, Boolean>()

    // Holders are recreated whenever the view type changes, such as entering selection mode, so each would otherwise re-read and re-decode.
    private val thumbnails = object : LruCache<Pair<BrowserMode, String>, Bitmap>(THUMBNAIL_CACHE_BYTES) {
        override fun sizeOf(key: Pair<BrowserMode, String>, value: Bitmap): Int = value.byteCount
    }

    // Content that fails to resolve falls back to the host's card instead of failing the whole list.
    override suspend fun resolve(tabId: String, url: String?, mode: BrowserMode): DuckAiTabGridItemContent? = withContext(dispatchers.io()) {
        try {
            resolveContent(tabId, url, mode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(WARN) { "Duck.ai tab content failed to resolve: ${e.asLog()}" }
            null
        }
    }

    private suspend fun resolveContent(tabId: String, url: String?, mode: BrowserMode): DuckAiTabGridItemContent? {
        if (voiceSessionStateManager.isVoiceSessionActive(tabId)) return RealDuckAiTabGridItemContent.ActiveVoice

        val chatId = url?.toUri()?.toChatIdOrNull(duckChat) ?: return RealDuckAiTabGridItemContent.Empty

        val store = storeFor(mode)
        if (!isNativeStorageReady(store, mode)) return null
        return chatPreview(store, mode, chatId)?.toContent(mode)
    }

    override fun create(context: Context): DuckAiTabGridItemView = DuckAiTabGridCardView(
        context = context,
        thumbnailLoader = ::loadThumbnail,
        dispatchers = dispatchers,
        coroutineScope = appCoroutineScope,
    )

    @VisibleForTesting
    internal suspend fun loadThumbnail(fileRef: String, mode: BrowserMode): Bitmap? {
        val key = mode to fileRef
        thumbnails[key]?.let { return it }
        // The card loads on the app scope, so a failure here would crash the app rather than just leave the logo in place.
        return try {
            val bytes = storeFor(mode).readFileRef(fileRef)?.bytes ?: return null
            decodeSampled(bytes)?.also { thumbnails.put(key, it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(WARN) { "Duck.ai tab thumbnail failed to load: ${e.asLog()}" }
            null
        } catch (e: OutOfMemoryError) {
            logcat(WARN) { "Duck.ai tab thumbnail too large to decode: ${e.asLog()}" }
            null
        }
    }

    private fun decodeSampled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val width = bounds.outWidth
        val height = bounds.outHeight
        var sampleSize = 1
        while (width / (sampleSize * 2) >= THUMBNAIL_MAX_PX && height / (sampleSize * 2) >= THUMBNAIL_MAX_PX) {
            sampleSize *= 2
        }
        // A very wide or tall image keeps its short side above the target, so its long side alone could take tens of MB.
        while ((width / sampleSize).toLong() * (height / sampleSize) > THUMBNAIL_MAX_PIXELS) {
            sampleSize *= 2
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sampleSize })
    }

    private suspend fun chatPreview(store: DuckAiChatStore, mode: BrowserMode, chatId: String): DuckAiChatPreview? {
        val key = mode to chatId
        chatPreviews[key]?.let { return it }
        if (key in unreadableChats) return null
        // A chat that can't be read, such as a row over the CursorWindow limit, falls back to the host's card.
        return try {
            store.getChatPreview(chatId)?.also { chatPreviews[key] = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            unreadableChats += key
            logcat(WARN) { "Duck.ai chat preview failed to read: ${e.asLog()}" }
            null
        }
    }

    private suspend fun isNativeStorageReady(store: DuckAiChatStore, mode: BrowserMode): Boolean =
        nativeStorageReady.getOrPut(mode) { store.hasMigrated() && duckChatFeature.useNativeStorageChatData().isEnabled() }

    private fun storeFor(mode: BrowserMode): DuckAiChatStore = when (mode) {
        BrowserMode.REGULAR -> regularChatStore
        BrowserMode.FIRE -> fireChatStore
    }

    // A chat with nothing to preview shows the empty card rather than falling back to the host's card.
    private fun DuckAiChatPreview.toContent(mode: BrowserMode): RealDuckAiTabGridItemContent {
        val title = chat.title
        if (chat.isImageGeneration) {
            val fileRef = chat.fileRefs.lastOrNull() ?: return RealDuckAiTabGridItemContent.Empty
            return RealDuckAiTabGridItemContent.ImageChat(title, fileRef, mode)
        }
        // Long chats would otherwise copy their whole last reply into every card.
        val snippet = lastMessageText?.take(SNIPPET_CHARACTER_CAP)
        if (chat.isVoice) {
            return snippet?.let { RealDuckAiTabGridItemContent.VoiceTranscript(title, it) } ?: RealDuckAiTabGridItemContent.Empty
        }
        if (isAwaitingReply) return RealDuckAiTabGridItemContent.Thinking(title)
        return snippet?.let { RealDuckAiTabGridItemContent.Chat(title, it) } ?: RealDuckAiTabGridItemContent.Empty
    }

    private companion object {
        const val SNIPPET_CHARACTER_CAP = 500
        const val THUMBNAIL_MAX_PX = 512
        const val THUMBNAIL_MAX_PIXELS = 1024 * 1024
        const val THUMBNAIL_CACHE_BYTES = 8 * 1024 * 1024
    }
}
