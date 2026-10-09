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

package com.duckduckgo.app.internalfeedback

import android.annotation.SuppressLint
import android.graphics.Bitmap
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

interface InternalFeedbackScreenshotStore {
    /**
     * Holds [screenshot] for the internal feedback form, replacing any previous one.
     */
    fun put(screenshot: Bitmap)

    /**
     * @return the held screenshot as a base64-encoded PNG, at most once and only within its expiry window (5 minutes), or `null` if there is none.
     * Waits for the encoding to finish if it's still in progress.
     */
    suspend fun takeEncodedScreenshot(): String?
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealInternalFeedbackScreenshotStore @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : InternalFeedbackScreenshotStore {

    private val heldPng = AtomicReference<Deferred<ByteArray?>?>(null)

    override fun put(screenshot: Bitmap) {
        val png = appCoroutineScope.async { encodePng(screenshot) }
        heldPng.getAndSet(png)?.cancel()
        // Frees the screenshot even if the form never asks for it. Does nothing if it was taken or replaced in the meantime.
        appCoroutineScope.launch {
            delay(SCREENSHOT_TTL)
            if (heldPng.compareAndSet(png, null)) png.cancel()
        }
    }

    @SuppressLint("AvoidComputationUsage")
    override suspend fun takeEncodedScreenshot(): String? {
        val png = heldPng.getAndSet(null)?.await() ?: return null
        return withContext(dispatcherProvider.computation()) { Base64.getEncoder().encodeToString(png) }
    }

    @SuppressLint("AvoidComputationUsage")
    private suspend fun encodePng(bitmap: Bitmap): ByteArray? = withContext(dispatcherProvider.computation()) {
        try {
            val stream = ByteArrayOutputStream()
            // The quality argument is ignored for PNG.
            if (bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) stream.toByteArray() else null
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        val SCREENSHOT_TTL = 5.minutes
    }
}
