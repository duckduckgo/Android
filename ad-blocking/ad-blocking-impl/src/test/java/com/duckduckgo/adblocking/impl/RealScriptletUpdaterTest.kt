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

package com.duckduckgo.adblocking.impl

import com.duckduckgo.adblocking.impl.domain.RealScriptletUpdater
import com.duckduckgo.adblocking.impl.domain.ScriptletSignatureValidator
import com.duckduckgo.adblocking.impl.domain.ScriptletUpdateResult
import com.duckduckgo.adblocking.impl.domain.ScriptletValidationResult
import com.duckduckgo.adblocking.impl.remoteconfig.AdBlockingExtensionSettings
import com.duckduckgo.adblocking.impl.remoteconfig.ScriptletEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException

class RealScriptletUpdaterTest {

    private val repository: AdBlockingExtensionRepository = mock()
    private val downloader: ScriptletDownloader = mock()
    private val validator: ScriptletSignatureValidator = mock()

    private val updater = RealScriptletUpdater(repository, downloader, validator)

    private val isolatedPath = "scriptlets/isolated/ublock-filters.js"
    private val mainPath = "scriptlets/main/ublock-filters.js"
    private val isolatedEntry = ScriptletEntry(url = "https://cdn.example/isolated.js", signature = "iso-sig")
    private val mainEntry = ScriptletEntry(url = "https://cdn.example/main.js", signature = "main-sig")
    private val isolatedBytes = "isolated-bytes".toByteArray()
    private val mainBytes = "main-bytes".toByteArray()

    private val validSettings = AdBlockingExtensionSettings(
        version = "2026.3.9",
        scriptlets = mapOf(isolatedPath to isolatedEntry, mainPath to mainEntry),
    )

    @Test
    fun whenStoredVersionMatchesRemoteVersionThenUpdateReturnsSuccess() = runTest {
        whenever(repository.getStoredVersion()).thenReturn("2026.3.9")

        assertEquals(ScriptletUpdateResult.Success, updater.update(validSettings))
        verify(downloader, never()).download(any())
        verify(repository, never()).storeScriptlets(any(), any())
    }

    @Test
    fun whenSingleDownloadFailsThenUpdateReturnsRetryAndDoesNotPersist() = runTest {
        whenever(repository.getStoredVersion()).thenReturn("0.0.0")
        whenever(downloader.download(isolatedEntry.url)).thenReturn(kotlin.Result.success(isolatedBytes))
        whenever(downloader.download(mainEntry.url)).thenReturn(kotlin.Result.failure(IOException("boom")))
        whenever(validator.validate(isolatedBytes, isolatedEntry.signature)).thenReturn(ScriptletValidationResult.Valid)

        assertEquals(ScriptletUpdateResult.Retry, updater.update(validSettings))
        verify(repository, never()).storeScriptlets(any(), any())
    }

    @Test
    fun whenValidationFailsForAnyScriptletThenUpdateReturnsRetryAndDoesNotPersist() = runTest {
        whenever(repository.getStoredVersion()).thenReturn("0.0.0")
        whenever(downloader.download(isolatedEntry.url)).thenReturn(kotlin.Result.success(isolatedBytes))
        whenever(downloader.download(mainEntry.url)).thenReturn(kotlin.Result.success(mainBytes))
        whenever(validator.validate(isolatedBytes, isolatedEntry.signature)).thenReturn(ScriptletValidationResult.Valid)
        whenever(validator.validate(mainBytes, mainEntry.signature))
            .thenReturn(ScriptletValidationResult.Invalid.SignatureVerificationFailed)

        assertEquals(ScriptletUpdateResult.Retry, updater.update(validSettings))
        verify(repository, never()).storeScriptlets(any(), any())
    }

    @Test
    fun whenAllScriptletsValidThenUpdateReturnsSuccessAndStoresAllScriptlets() = runTest {
        whenever(repository.getStoredVersion()).thenReturn("0.0.0")
        whenever(downloader.download(isolatedEntry.url)).thenReturn(kotlin.Result.success(isolatedBytes))
        whenever(downloader.download(mainEntry.url)).thenReturn(kotlin.Result.success(mainBytes))
        whenever(validator.validate(isolatedBytes, isolatedEntry.signature)).thenReturn(ScriptletValidationResult.Valid)
        whenever(validator.validate(mainBytes, mainEntry.signature)).thenReturn(ScriptletValidationResult.Valid)

        assertEquals(ScriptletUpdateResult.Success, updater.update(validSettings))
        verify(repository).storeScriptlets(
            eq("2026.3.9"),
            check { stored ->
                assertEquals(setOf(isolatedPath, mainPath), stored.keys)
                assertEquals(String(isolatedBytes), String(stored.getValue(isolatedPath)))
                assertEquals(String(mainBytes), String(stored.getValue(mainPath)))
            },
        )
    }

    @Test
    fun whenSettingsHasNoScriptletsThenUpdateReturnsSuccessAndStoresEmptyMap() = runTest {
        whenever(repository.getStoredVersion()).thenReturn("0.0.0")

        assertEquals(ScriptletUpdateResult.Success, updater.update(validSettings.copy(scriptlets = emptyMap())))
        verify(downloader, never()).download(any())
        verify(repository).storeScriptlets(eq("2026.3.9"), eq(emptyMap()))
    }

    @Test
    fun whenScriptletContentIsEmptyThenItIsNotStored() = runTest {
        val emptyBytes = ByteArray(0)
        whenever(repository.getStoredVersion()).thenReturn("0.0.0")
        whenever(downloader.download(isolatedEntry.url)).thenReturn(kotlin.Result.success(emptyBytes))
        whenever(downloader.download(mainEntry.url)).thenReturn(kotlin.Result.success(mainBytes))
        whenever(validator.validate(emptyBytes, isolatedEntry.signature)).thenReturn(ScriptletValidationResult.Valid)
        whenever(validator.validate(mainBytes, mainEntry.signature)).thenReturn(ScriptletValidationResult.Valid)

        assertEquals(ScriptletUpdateResult.Success, updater.update(validSettings))
        verify(repository).storeScriptlets(
            eq("2026.3.9"),
            check { stored ->
                assertEquals(setOf(mainPath), stored.keys)
                assertEquals(String(mainBytes), String(stored.getValue(mainPath)))
            },
        )
    }

    @Test
    fun whenOneDownloadFailsThenUpdateShortCircuitsAndCancelsInFlightDownloads() = runTest {
        whenever(repository.getStoredVersion()).thenReturn("0.0.0")
        val isolatedDownloadStarted = CompletableDeferred<Unit>()
        val shortCircuitingDownloader = object : ScriptletDownloader {
            override suspend fun download(url: String): kotlin.Result<ByteArray> = when (url) {
                isolatedEntry.url -> {
                    isolatedDownloadStarted.complete(Unit)
                    awaitCancellation()
                }
                mainEntry.url -> kotlin.Result.failure(IOException("boom"))
                else -> error("unexpected url: $url")
            }
        }
        val shortCircuitingUpdater = RealScriptletUpdater(repository, shortCircuitingDownloader, validator)

        assertEquals(ScriptletUpdateResult.Retry, shortCircuitingUpdater.update(validSettings))
        assertTrue("isolated download should have started in parallel", isolatedDownloadStarted.isCompleted)
        verify(validator, never()).validate(any(), any())
        verify(repository, never()).storeScriptlets(any(), any())
    }
}
