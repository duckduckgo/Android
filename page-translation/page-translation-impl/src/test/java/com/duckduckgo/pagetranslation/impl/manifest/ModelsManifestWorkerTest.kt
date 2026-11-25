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
import com.duckduckgo.pagetranslation.impl.PageTranslationFeature
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class ModelsManifestWorkerTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val feature = FakeFeatureToggleFactory.create(PageTranslationFeature::class.java)
    private val fetcher: ModelsManifestFetcher = mock()
    private val store: ModelsManifestStore = mock()
    private val scheduler: LanguagePackScheduler = mock()

    @Test
    fun succeedWithoutFetchingWhenTranslationIsOff() = runTest {
        feature.translation().setRawStoredState(State(enable = false))

        assertEquals(Result.success(), createWorker().doWork())

        verifyNoInteractions(fetcher)
    }

    @Test
    fun returnFetchResultWhenTranslationIsOn() = runTest {
        feature.translation().setRawStoredState(State(enable = true))
        whenever(fetcher.fetch()).thenReturn(Result.retry())

        assertEquals(Result.retry(), createWorker().doWork())
    }

    @Test
    fun removePacksOfPairsTheRefreshedManifestNoLongerLists() = runTest {
        feature.translation().setRawStoredState(State(enable = true))
        whenever(fetcher.fetch()).thenReturn(Result.success())
        whenever(store.readManifest()).thenReturn(MANIFEST)

        assertEquals(Result.success(), createWorker().doWork())

        verify(scheduler).removeUnlisted(setOf("fr-en"))
    }

    @Test
    fun removeNoPacksWhenTheRefreshFails() = runTest {
        feature.translation().setRawStoredState(State(enable = true))
        whenever(fetcher.fetch()).thenReturn(Result.retry())
        whenever(store.readManifest()).thenReturn(MANIFEST)

        createWorker().doWork()

        verifyNoInteractions(scheduler)
    }

    @Test
    fun removeNoPacksWithoutAStoredManifest() = runTest {
        feature.translation().setRawStoredState(State(enable = true))
        whenever(fetcher.fetch()).thenReturn(Result.success())

        createWorker().doWork()

        verifyNoInteractions(scheduler)
    }

    private fun createWorker() = TestListenableWorkerBuilder<ModelsManifestWorker>(ApplicationProvider.getApplicationContext()).build().also {
        it.feature = feature
        it.fetcher = lazyOf(fetcher)
        it.store = store
        it.scheduler = scheduler
        it.dispatchers = coroutineRule.testDispatcherProvider
    }

    private companion object {
        val MANIFEST = ModelsManifest(
            schemaVersion = 1,
            baseUrl = "https://models.test/",
            models = mapOf(
                "fr-en" to ModelsManifest.TranslationModel(
                    sourceLanguage = "fr",
                    targetLanguage = "en",
                    version = "3.0",
                    architecture = "base-memory",
                    totalBytes = 11,
                    files = emptyMap(),
                ),
            ),
        )
    }
}
