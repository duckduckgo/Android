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

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.pagetranslation.impl.PageTranslationFeature
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackDownloadState.Failed.Reason
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackFetcher.FetchResult
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackWorker.Companion.FAILURE_REASON_KEY
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackWorker.Companion.PAIR_KEY
import com.duckduckgo.pagetranslation.impl.manifest.ModelsManifest
import com.duckduckgo.pagetranslation.impl.manifest.ModelsManifest.ModelFile
import com.duckduckgo.pagetranslation.impl.manifest.ModelsManifest.TranslationModel
import com.duckduckgo.pagetranslation.impl.manifest.ModelsManifestStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class LanguagePackWorkerTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val feature = FakeFeatureToggleFactory.create(PageTranslationFeature::class.java)
    private val store: ModelsManifestStore = mock()
    private val fetcher: LanguagePackFetcher = mock()

    @Test
    fun failWithoutDownloadingWhenTranslationIsOff() = runTest {
        setTranslationEnabled(false)

        assertEquals(Result.failure(), createWorker().doWork())

        verifyNoInteractions(fetcher)
    }

    @Test
    fun failWithoutDownloadingWhenPairIsNotInManifest() = runTest {
        setTranslationEnabled(true)
        whenever(store.readManifest()).thenReturn(MANIFEST.copy(models = emptyMap()))

        assertEquals(Result.failure(), createWorker().doWork())

        verifyNoInteractions(fetcher)
    }

    @Test
    fun downloadPairIntoItsStagingDirectory() = runTest {
        setTranslationEnabled(true)
        whenever(store.readManifest()).thenReturn(MANIFEST)
        whenever(fetcher.fetch(any(), any(), any(), any())).thenReturn(FetchResult.SUCCESS)

        assertEquals(Result.success(), createWorker().doWork())

        verify(fetcher).fetch(eq(BASE_URL), eq(MODEL), eq(context.filesDir.resolve(STAGING_PATH)), any())
    }

    @Test
    fun reportNotEnoughStorageReasonInOutput() = runTest {
        setTranslationEnabled(true)
        whenever(store.readManifest()).thenReturn(MANIFEST)
        whenever(fetcher.fetch(any(), any(), any(), any())).thenReturn(FetchResult.NOT_ENOUGH_STORAGE)

        assertEquals(Result.failure(workDataOf(FAILURE_REASON_KEY to Reason.NOT_ENOUGH_STORAGE.name)), createWorker().doWork())
    }

    @Test
    fun reportDownloadErrorReasonWhenDownloadFails() = runTest {
        setTranslationEnabled(true)
        whenever(store.readManifest()).thenReturn(MANIFEST)
        whenever(fetcher.fetch(any(), any(), any(), any())).thenReturn(FetchResult.FAILURE)

        assertEquals(Result.failure(workDataOf(FAILURE_REASON_KEY to Reason.DOWNLOAD_ERROR.name)), createWorker().doWork())
    }

    private fun setTranslationEnabled(isEnabled: Boolean) {
        feature.translation().setRawStoredState(State(enable = isEnabled))
    }

    private fun createWorker() = TestListenableWorkerBuilder<LanguagePackWorker>(context)
        .setInputData(workDataOf(PAIR_KEY to PAIR))
        .build()
        .also {
            it.feature = feature
            it.store = store
            it.fetcher = lazyOf(fetcher)
            it.dispatchers = coroutineRule.testDispatcherProvider
        }

    private companion object {
        const val PAIR = "fr-en"
        const val BASE_URL = "https://models.test/"
        const val STAGING_PATH = "page-translation/packs/fr-en.tmp"

        val MODEL = TranslationModel(
            sourceLanguage = "fr",
            targetLanguage = "en",
            version = "3.0",
            architecture = "base-memory",
            totalBytes = 11,
            files = mapOf("model" to ModelFile("model.zst", bytes = 11, sha256 = "a", decompressedSize = 20, decompressedHash = "b")),
        )

        val MANIFEST = ModelsManifest(schemaVersion = 1, baseUrl = BASE_URL, models = mapOf(PAIR to MODEL))
    }
}
