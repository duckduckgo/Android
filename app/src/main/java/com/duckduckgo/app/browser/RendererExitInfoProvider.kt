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

package com.duckduckgo.app.browser

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.SystemClock
import androidx.annotation.WorkerThread
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import javax.inject.Inject

interface RendererExitInfoProvider {
    val isSupported: Boolean

    /** Looks up the system's exit record for the renderer death the app was notified of at [callbackTimeMillis] (wall clock). */
    @WorkerThread
    fun findRendererExit(callbackTimeMillis: Long): RendererExitLookup
}

sealed interface RendererExitLookup {
    data class Found(val pid: Int, val exit: RendererExit) : RendererExitLookup

    /** More than one renderer died in the window, and nothing tells which record belongs to this death. */
    data object Ambiguous : RendererExitLookup

    data object NotFound : RendererExitLookup
}

/**
 * @param reason one of the ApplicationExitInfo.REASON_* constants
 * @param pssKb the last PSS the system sampled for the renderer before it died, or 0 if it was never sampled
 * @param timestampMillis when the system recorded the death (wall clock)
 */
data class RendererExit(
    val reason: Int,
    val pssKb: Long,
    val timestampMillis: Long,
)

data class RendererExitRecord(
    val pid: Int,
    val processName: String,
    val description: String?,
    val exit: RendererExit,
)

/**
 * Records carry no link to the WebView whose renderer died, and renderers of the Fire Mode profile and of the PIR
 * process are filed under this app too. So only records written after the previous lookup's callback and no later than
 * this callback qualify: the death can't be earlier than that, and a frozen app can be notified long after it. A single
 * candidate is this death; several are reported as ambiguous rather than guessed. Renderers torn down because nothing
 * used them ("isolated not needed") never notify the app, so they are never candidates.
 */
internal fun selectRendererExit(
    records: List<RendererExitRecord>,
    windowStartMillis: Long,
    callbackTimeMillis: Long,
    usedPids: Set<Int>,
): RendererExitLookup {
    val candidates = records
        .filter { it.processName.contains(RENDERER_PROCESS_MARKER) }
        .filterNot { it.description.orEmpty().contains(ISOLATED_NOT_NEEDED, ignoreCase = true) }
        .filter { it.exit.timestampMillis > windowStartMillis && it.exit.timestampMillis <= callbackTimeMillis }
        .filterNot { it.pid in usedPids }
    return when (candidates.size) {
        0 -> RendererExitLookup.NotFound
        1 -> candidates.single().let { RendererExitLookup.Found(pid = it.pid, exit = it.exit) }
        else -> RendererExitLookup.Ambiguous
    }
}

private const val RENDERER_PROCESS_MARKER = ":sandboxed_process"
private const val ISOLATED_NOT_NEEDED = "isolated not needed"

@ContributesBinding(AppScope::class)
class RealRendererExitInfoProvider @Inject constructor(
    private val context: Context,
) : RendererExitInfoProvider {

    private var previousCallbackTimeMillis: Long? = null

    // The system can update a record's reason and timestamp after writing it, so the pid identifies it.
    private val usedPids = LinkedHashSet<Int>()

    override val isSupported: Boolean = Build.VERSION.SDK_INT >= 30

    @Synchronized
    override fun findRendererExit(callbackTimeMillis: Long): RendererExitLookup {
        if (Build.VERSION.SDK_INT < 30) return RendererExitLookup.NotFound
        val activityManager = context.getSystemService(ActivityManager::class.java) ?: return RendererExitLookup.NotFound
        // The renderer is an isolated process of the WebView provider, but the system files its exit under this app.
        val records = runCatching { activityManager.getHistoricalProcessExitReasons(null, 0, MAX_RECORDS) }
            .getOrNull()
            .orEmpty()
            .map {
                RendererExitRecord(
                    pid = it.pid,
                    processName = it.processName,
                    description = it.description,
                    exit = RendererExit(reason = it.reason, pssKb = it.pss, timestampMillis = it.timestamp),
                )
            }
        val windowStart = previousCallbackTimeMillis ?: processStartMillis()
        previousCallbackTimeMillis = callbackTimeMillis
        return selectRendererExit(records, windowStart, callbackTimeMillis, usedPids).also { lookup ->
            if (lookup is RendererExitLookup.Found) {
                usedPids += lookup.pid
                if (usedPids.size > MAX_USED) usedPids.remove(usedPids.first())
            }
        }
    }

    private fun processStartMillis(): Long =
        System.currentTimeMillis() - (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime())

    private companion object {
        const val MAX_RECORDS = 16
        const val MAX_USED = 16
    }
}
