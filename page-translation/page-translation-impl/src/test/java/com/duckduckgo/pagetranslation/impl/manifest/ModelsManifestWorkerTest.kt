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

package com.duckduckgo.pagetranslation.impl.manifest

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.pagetranslation.impl.PageTranslationDeviceSupport
import com.duckduckgo.pagetranslation.impl.PageTranslationFeature
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class ModelsManifestWorkerTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val feature = FakeFeatureToggleFactory.create(PageTranslationFeature::class.java)
    private val fetcher: ModelsManifestFetcher = mock()
    private var isDeviceSupported = true

    @Test
    fun succeedWithoutFetchingWhenTranslationIsOff() = runTest {
        feature.translation().setRawStoredState(State(enable = false))

        assertEquals(Result.success(), createWorker().doWork())

        verifyNoInteractions(fetcher)
    }

    @Test
    fun succeedWithoutFetchingOnUnsupportedDevices() = runTest {
        feature.translation().setRawStoredState(State(enable = true))
        isDeviceSupported = false

        assertEquals(Result.success(), createWorker().doWork())

        verifyNoInteractions(fetcher)
    }

    @Test
    fun returnFetchResultWhenTranslationIsOn() = runTest {
        feature.translation().setRawStoredState(State(enable = true))
        whenever(fetcher.fetch()).thenReturn(Result.retry())

        assertEquals(Result.retry(), createWorker().doWork())
    }

    private fun createWorker() = TestListenableWorkerBuilder<ModelsManifestWorker>(ApplicationProvider.getApplicationContext()).build().also {
        it.feature = feature
        it.deviceSupport = object : PageTranslationDeviceSupport {
            override fun isSupported() = isDeviceSupported
        }
        it.fetcher = lazyOf(fetcher)
        it.dispatchers = coroutineRule.testDispatcherProvider
    }
}
