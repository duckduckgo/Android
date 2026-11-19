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

package com.duckduckgo.app.browser.tabs.transition

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

private const val PICTURE_WAIT_MS = 50L

/**
 * A copy of what the window shows. Null when it can't be copied in time (not drawn yet, zero size, copy error).
 * PixelCopy reads what's on screen, so it also gets hardware bitmaps (e.g. tab previews).
 */
suspend fun Window.capturePicture(timeoutMs: Long = PICTURE_WAIT_MS): Bitmap? {
    val width = decorView.width
    val height = decorView.height
    if (width == 0 || height == 0) return null
    val picture = createBitmap(width, height)
    return withTimeoutOrNull(timeoutMs.milliseconds) {
        suspendCancellableCoroutine { continuation ->
            try {
                PixelCopy.request(
                    this@capturePicture,
                    picture,
                    { result -> if (continuation.isActive) continuation.resume(picture.takeIf { result == PixelCopy.SUCCESS }) },
                    Handler(Looper.getMainLooper()),
                )
            } catch (_: IllegalArgumentException) {
                // The window has no surface to copy from, e.g. it's being torn down.
                continuation.resume(null)
            }
        }
    }
}
