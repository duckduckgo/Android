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

package com.duckduckgo.request.interception.impl

import android.net.Uri
import android.webkit.WebResourceRequest
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.privacy.db.PrivacyProtectionCountDao
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.httpsupgrade.api.HttpsUpgrader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class HttpsUpgradeStepTest {
    private val upgrader: HttpsUpgrader = mock()
    private val countDao: PrivacyProtectionCountDao = mock()
    private val request: WebResourceRequest = mock()
    private val dispatchers: DispatcherProvider = mock()

    @Test
    fun whenSubframeOrUrlIsNullThenDomainCallsAreSkipped() = runTest {
        val step = HttpsUpgradeStep(upgrader, countDao, this, dispatchers)
        whenever(request.isForMainFrame).thenReturn(false, true)
        whenever(request.url).thenReturn(null)
        assertNull(step.upgradeOrNull(request))
        assertNull(step.upgradeOrNull(request))
        verifyNoInteractions(upgrader)
        verifyNoInteractions(countDao)
    }

    @Test
    fun whenDomainDeclinesUpgradeThenNoUpgradeIsReturned() = runTest {
        val uri = Uri.parse("http://example.com")
        whenever(request.isForMainFrame).thenReturn(true)
        whenever(request.url).thenReturn(uri)
        whenever(upgrader.shouldUpgrade(uri)).thenReturn(false)
        assertNull(HttpsUpgradeStep(upgrader, countDao, this, dispatchers).upgradeOrNull(request))
        verify(upgrader, never()).upgrade(any())
        verify(countDao, never()).incrementUpgradeCount()
    }

    @Test
    fun whenPostAndNonHttpRequestAreEligibleThenUpgradeWithoutRecording() = runTest {
        val uri = Uri.parse("custom://example.com")
        val upgraded = Uri.parse("https://example.com")
        whenever(request.isForMainFrame).thenReturn(true)
        whenever(request.url).thenReturn(uri)
        whenever(request.method).thenReturn("POST")
        whenever(upgrader.shouldUpgrade(uri)).thenReturn(true)
        whenever(upgrader.upgrade(uri)).thenReturn(upgraded)
        assertEquals(upgraded, HttpsUpgradeStep(upgrader, countDao, this, dispatchers).upgradeOrNull(request))
        verify(upgrader).shouldUpgrade(uri)
        verify(upgrader).upgrade(uri)
        verify(countDao, never()).incrementUpgradeCount()
    }

    @Test
    fun whenUpgradeIsRecordedThenCountIsIncrementedOnceThroughIoDispatcher() = runTest {
        val appDispatcher = StandardTestDispatcher(testScheduler)
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        val appScope = CoroutineScope(Job() + appDispatcher)
        whenever(dispatchers.io()).thenReturn(ioDispatcher)
        HttpsUpgradeStep(upgrader, countDao, appScope, dispatchers).recordUpgrade()
        verify(countDao, never()).incrementUpgradeCount()
        testScheduler.advanceUntilIdle()
        verify(dispatchers).io()
        verify(countDao, times(1)).incrementUpgradeCount()
        appScope.cancel()
    }

    @Test
    fun whenCallerIsCancelledThenAsynchronousRecordingSurvives() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val appJob = Job()
        val appScope = CoroutineScope(appJob + dispatcher)
        whenever(dispatchers.io()).thenReturn(dispatcher)
        val step = HttpsUpgradeStep(upgrader, countDao, appScope, dispatchers)
        val caller = launch(start = CoroutineStart.UNDISPATCHED) {
            step.recordUpgrade()
            awaitCancellation()
        }
        verify(countDao, never()).incrementUpgradeCount()
        caller.cancel()
        testScheduler.advanceUntilIdle()
        verify(countDao).incrementUpgradeCount()
        appScope.cancel()
    }

    @Test
    fun whenAppScopeIsCancelledThenUpgradeIsNotRecorded() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val appScope = CoroutineScope(Job() + dispatcher)
        whenever(dispatchers.io()).thenReturn(dispatcher)
        appScope.cancel()
        HttpsUpgradeStep(upgrader, countDao, appScope, dispatchers).recordUpgrade()
        testScheduler.advanceUntilIdle()
        verify(countDao, never()).incrementUpgradeCount()
    }
}
