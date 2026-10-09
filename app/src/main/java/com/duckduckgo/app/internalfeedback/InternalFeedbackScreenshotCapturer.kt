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

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import androidx.core.graphics.createBitmap
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

interface InternalFeedbackScreenshotCapturer {
    /**
     * Copies what [window] currently shows and hands it to [InternalFeedbackScreenshotStore].
     * Returns once the pixels are copied. A failed copy is logged and nothing is stored; it doesn't throw.
     */
    suspend fun capture(window: Window)
}

@ContributesBinding(AppScope::class)
class RealInternalFeedbackScreenshotCapturer @Inject constructor(
    private val screenshotStore: InternalFeedbackScreenshotStore,
) : InternalFeedbackScreenshotCapturer {

    override suspend fun capture(window: Window) {
        val bitmap = try {
            copyPixels(window)
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            logcat(WARN) { "Internal feedback: screenshot capture failed: ${e.asLog()}" }
            null
        } ?: return
        screenshotStore.put(bitmap)
    }

    private suspend fun copyPixels(window: Window): Bitmap? {
        val decorView = window.decorView
        if (decorView.width == 0 || decorView.height == 0) return null
        val bitmap = createBitmap(decorView.width, decorView.height)
        val result = withTimeoutOrNull(PIXEL_COPY_TIMEOUT) {
            suspendCancellableCoroutine { continuation ->
                try {
                    PixelCopy.request(window, bitmap, { result -> continuation.resume(result) }, Handler(Looper.getMainLooper()))
                } catch (e: IllegalArgumentException) {
                    continuation.resume(PixelCopy.ERROR_SOURCE_INVALID)
                }
            }
        }
        if (result != PixelCopy.SUCCESS) {
            logcat(WARN) { "Internal feedback: screenshot capture failed: $result" }
            // After a timeout PixelCopy may still write into the bitmap, so it's left to the GC instead of being recycled.
            if (result != null) bitmap.recycle()
            return null
        }
        return bitmap
    }

    private companion object {
        val PIXEL_COPY_TIMEOUT = 1.seconds
    }
}
