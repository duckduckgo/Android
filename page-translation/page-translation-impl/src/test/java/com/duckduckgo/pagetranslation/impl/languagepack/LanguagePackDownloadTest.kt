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

package com.duckduckgo.pagetranslation.impl.languagepack

import com.duckduckgo.pagetranslation.impl.language.LanguagePair
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class LanguagePackDownloadTest {

    private val pair = LanguagePair("es", "en")
    private val failed = LanguagePackDownloadState.Failed(LanguagePackDownloadState.Failed.Reason.DOWNLOAD_ERROR)

    private val scheduler: LanguagePackScheduler = mock()
    private val testee = RealLanguagePackDownload(scheduler)

    private fun statesOf(initial: LanguagePackDownloadState = LanguagePackDownloadState.Idle) =
        MutableStateFlow(initial).also { whenever(scheduler.getDownloadStateFlow(pair.key)).thenReturn(it) }

    @Test
    fun whenStateFlowCompletesWithoutTerminalStateThenReturnsFailure() = runTest {
        whenever(scheduler.getDownloadStateFlow(pair.key)).thenReturn(emptyFlow())

        assertEquals(failed, testee.download(pair))
        assertFalse(testee.isDownloading(pair))
    }

    @Test
    fun whenDownloadedThenReturnsDownloaded() = runTest(UnconfinedTestDispatcher()) {
        val states = statesOf()
        val result = async { testee.download(pair) }

        states.value = LanguagePackDownloadState.InProgress(1, 10)
        states.value = LanguagePackDownloadState.Downloaded

        assertEquals(LanguagePackDownloadState.Downloaded, result.await())
        verify(scheduler).download(pair.key)
    }

    @Test
    fun whenFailedThenReturnsFailed() = runTest(UnconfinedTestDispatcher()) {
        val states = statesOf()
        val result = async { testee.download(pair) }

        states.value = LanguagePackDownloadState.InProgress(1, 10)
        states.value = failed

        assertEquals(failed, result.await())
    }

    @Test
    fun whenInterruptedThenDownloadedThenReturnsDownloaded() = runTest(UnconfinedTestDispatcher()) {
        val states = statesOf()
        val result = async { testee.download(pair) }

        states.value = LanguagePackDownloadState.Interrupted
        states.value = LanguagePackDownloadState.InProgress(1, 10)
        states.value = LanguagePackDownloadState.Downloaded

        assertEquals(LanguagePackDownloadState.Downloaded, result.await())
    }

    @Test
    fun whenAnEarlierDownloadFailedThenItsStateIsIgnoredAndTheNewOneIsReturned() = runTest(UnconfinedTestDispatcher()) {
        val states = statesOf(failed)
        val result = async { testee.download(pair) }
        assertTrue(result.isActive)

        states.value = LanguagePackDownloadState.InProgress(1, 10)
        states.value = LanguagePackDownloadState.Downloaded

        assertEquals(LanguagePackDownloadState.Downloaded, result.await())
    }

    @Test
    fun whenAnEarlierDownloadSucceededThenItIsIgnoredUntilTheNewOneCompletes() = runTest(UnconfinedTestDispatcher()) {
        val states = statesOf(LanguagePackDownloadState.Downloaded)
        val result = async { testee.download(pair) }
        assertTrue(result.isActive)

        states.value = LanguagePackDownloadState.InProgress(1, 10)
        states.value = failed

        assertEquals(failed, result.await())
    }

    @Test
    fun whenAwaitingThenIsDownloadingAndAfterDownloadedItIsNot() = runTest(UnconfinedTestDispatcher()) {
        val states = statesOf()
        val result = async { testee.download(pair) }
        assertTrue(testee.isDownloading(pair))

        states.value = LanguagePackDownloadState.InProgress(1, 10)
        states.value = LanguagePackDownloadState.Downloaded
        result.await()

        assertFalse(testee.isDownloading(pair))
    }

    @Test
    fun whenFailedThenIsNoLongerDownloading() = runTest(UnconfinedTestDispatcher()) {
        val states = statesOf()
        val result = async { testee.download(pair) }

        states.value = LanguagePackDownloadState.InProgress(1, 10)
        states.value = failed
        result.await()

        assertFalse(testee.isDownloading(pair))
    }

    @Test
    fun whenCallerCancelledThenIsNoLongerDownloading() = runTest(UnconfinedTestDispatcher()) {
        statesOf()
        val result = async(start = CoroutineStart.UNDISPATCHED) { testee.download(pair) }
        assertTrue(testee.isDownloading(pair))

        result.cancel()
        result.join()

        assertFalse(testee.isDownloading(pair))
    }

    @Test
    fun whenAnotherPairIsDownloadingThenThisOneIsNot() = runTest(UnconfinedTestDispatcher()) {
        val other = LanguagePair("fr", "en")
        whenever(scheduler.getDownloadStateFlow(other.key)).thenReturn(MutableStateFlow(LanguagePackDownloadState.Idle))
        val awaiting = async { testee.download(other) }

        assertFalse(testee.isDownloading(pair))
        awaiting.cancel()
    }
}
