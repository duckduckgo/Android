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

import android.app.ApplicationExitInfo
import com.duckduckgo.app.browser.RendererExitLookup.Ambiguous
import com.duckduckgo.app.browser.RendererExitLookup.Found
import com.duckduckgo.app.browser.RendererExitLookup.NotFound
import org.junit.Assert.assertEquals
import org.junit.Test

class RendererExitSelectionTest {

    @Test
    fun whenOneRendererRecordInWindowThenItIsTheDeath() {
        val own = renderer(pid = 1, timestamp = 5_000)

        assertEquals(found(own), select(listOf(own), windowStart = 1_000, callbackTime = 5_010))
    }

    @Test
    fun whenTwoRendererRecordsInWindowThenAmbiguous() {
        val first = renderer(pid = 1, timestamp = 3_000)
        val second = renderer(pid = 2, timestamp = 5_000)

        assertEquals(Ambiguous, select(listOf(second, first), windowStart = 1_000, callbackTime = 5_010))
    }

    @Test
    fun whenRecordWrittenEvenJustAfterCallbackThenIgnored() {
        val own = renderer(pid = 1, timestamp = 5_000)
        val justAfter = renderer(pid = 2, timestamp = 5_011)

        assertEquals(found(own), select(listOf(justAfter, own), windowStart = 1_000, callbackTime = 5_010))
    }

    @Test
    fun whenRecordWrittenBeforePreviousCallbackThenIgnored() {
        val stale = renderer(pid = 1, timestamp = 900)
        val own = renderer(pid = 2, timestamp = 5_000)

        assertEquals(found(own), select(listOf(own, stale), windowStart = 1_000, callbackTime = 5_010))
    }

    @Test
    fun whenRendererWasTornDownUnusedThenIgnored() {
        val own = renderer(pid = 1, timestamp = 4_000)
        val android11Teardown = renderer(pid = 2, timestamp = 4_500, description = "isolated not needed")
        val android14Teardown = renderer(pid = 3, timestamp = 5_000, description = "[ISOLATED NOT NEEDED] isolated not needed")

        assertEquals(found(own), select(listOf(android14Teardown, android11Teardown, own), windowStart = 1_000, callbackTime = 5_010))
    }

    @Test
    fun whenCallbackDeliveredLongAfterDeathThenRecordStillMatched() {
        val own = renderer(pid = 1, timestamp = 2_000)

        assertEquals(found(own), select(listOf(own), windowStart = 1_000, callbackTime = 2_000 + 3_600_000))
    }

    @Test
    fun whenRecordPidAlreadyUsedThenIgnoredEvenIfItsTimestampMoved() {
        val updated = renderer(pid = 1, timestamp = 5_000, reason = ApplicationExitInfo.REASON_LOW_MEMORY)

        assertEquals(NotFound, select(listOf(updated), windowStart = 1_000, callbackTime = 5_010, usedPids = setOf(1)))
    }

    @Test
    fun whenRecordIsNotARendererThenIgnored() {
        val app = RendererExitRecord(1, "com.duckduckgo.mobile.android", null, RendererExit(ApplicationExitInfo.REASON_SIGNALED, 0, 5_000))

        assertEquals(NotFound, select(listOf(app), windowStart = 1_000, callbackTime = 5_010))
    }

    private fun select(
        records: List<RendererExitRecord>,
        windowStart: Long,
        callbackTime: Long,
        usedPids: Set<Int> = emptySet(),
    ) = selectRendererExit(records, windowStart, callbackTime, usedPids)

    private fun found(record: RendererExitRecord) = Found(pid = record.pid, exit = record.exit)

    private fun renderer(
        pid: Int,
        timestamp: Long,
        description: String? = null,
        reason: Int = ApplicationExitInfo.REASON_SIGNALED,
    ) = RendererExitRecord(
        pid = pid,
        processName = "com.google.android.webview:sandboxed_process0:org.chromium.content.app.SandboxedProcessService0:0",
        description = description,
        exit = RendererExit(reason = reason, pssKb = 0, timestampMillis = timestamp),
    )
}
