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
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.pagetranslation.impl.PageTranslationFeature
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackDownloadState.Failed.Reason
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackFetcher.FetchResult
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackWorker.Companion.DOWNLOADED_BYTES_KEY
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackWorker.Companion.FAILURE_REASON_KEY
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackWorker.Companion.PAIR_KEY
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackWorker.Companion.TOTAL_BYTES_KEY
import com.duckduckgo.pagetranslation.impl.manifest.ModelsManifest
import com.duckduckgo.pagetranslation.impl.manifest.ModelsManifest.ModelFile
import com.duckduckgo.pagetranslation.impl.manifest.ModelsManifest.TranslationModel
import com.duckduckgo.pagetranslation.impl.manifest.ModelsManifestStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
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
    private val feature = FakeFeatureToggleFactory.create(PageTranslationFeature::class.java, ioDispatcher = coroutineRule.testDispatcher)
    private val store: ModelsManifestStore = mock()
    private val fetcher: LanguagePackFetcher = mock()
    private val installer: LanguagePackInstaller = mock()

    @Test
    fun failWithoutDownloadingWhenTranslationIsOff() = runTest {
        setTranslationEnabled(false)

        assertEquals(Result.failure(), createWorker().doWork())

        verifyNoInteractions(fetcher)
    }

    @Test
    fun stopDownloadingWhenTranslationIsTurnedOff() = runTest {
        setTranslationEnabled(true)
        whenever(store.readManifest()).thenReturn(MANIFEST)
        whenever(fetcher.fetch(any(), any(), any(), any())).doSuspendableAnswer { awaitCancellation() }

        val work = async { createWorker().doWork() }
        runCurrent()
        setTranslationEnabled(false)

        assertEquals(Result.failure(workDataOf(FAILURE_REASON_KEY to Reason.DOWNLOAD_ERROR.name)), work.await())
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
        whenever(installer.install(any(), any(), any())).thenReturn(true)

        assertEquals(Result.success(), createWorker().doWork())

        verify(fetcher).fetch(eq(BASE_URL), eq(MODEL), eq(context.filesDir.resolve(STAGING_PATH)), any())
        verify(installer).install(MODEL, context.filesDir.resolve(STAGING_PATH), context.filesDir.resolve(PACK_PATH))
    }

    @Test
    fun reportDownloadErrorReasonWhenInstallFails() = runTest {
        setTranslationEnabled(true)
        whenever(store.readManifest()).thenReturn(MANIFEST)
        whenever(fetcher.fetch(any(), any(), any(), any())).thenReturn(FetchResult.SUCCESS)
        whenever(installer.install(any(), any(), any())).thenReturn(false)

        assertEquals(Result.failure(workDataOf(FAILURE_REASON_KEY to Reason.DOWNLOAD_ERROR.name)), createWorker().doWork())
    }

    @Test
    fun discardDownloadWhenManifestMovedPairToAnotherVersion() = runTest {
        setTranslationEnabled(true)
        whenever(store.readManifest()).thenReturn(MANIFEST, MANIFEST.copy(models = mapOf(PAIR to MODEL.copy(version = "4.0"))))
        whenever(fetcher.fetch(any(), any(), any(), any())).doSuspendableAnswer {
            context.filesDir.resolve(STAGING_PATH).mkdirs()
            FetchResult.SUCCESS
        }

        assertEquals(Result.failure(workDataOf(FAILURE_REASON_KEY to Reason.DOWNLOAD_ERROR.name)), createWorker().doWork())

        assertFalse(context.filesDir.resolve(STAGING_PATH).exists())
        verifyNoInteractions(installer)
    }

    @Test
    fun resetProgressOfAPreviousAttemptBeforeDownloading() = runTest {
        setTranslationEnabled(true)
        whenever(store.readManifest()).thenReturn(MANIFEST)
        whenever(fetcher.fetch(any(), any(), any(), any())).thenReturn(FetchResult.FAILURE)

        createWorker().doWork()

        assertEquals(listOf(workDataOf(DOWNLOADED_BYTES_KEY to 0L, TOTAL_BYTES_KEY to MODEL.totalBytes)), progressUpdates)
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

    private val progressUpdates = mutableListOf<Data>()

    private fun createWorker() = TestListenableWorkerBuilder<LanguagePackWorker>(context)
        .setInputData(workDataOf(PAIR_KEY to PAIR))
        .setProgressUpdater { _, _, data ->
            progressUpdates += data
            CallbackToFutureAdapter.getFuture { it.set(null) }
        }
        .build()
        .also {
            it.feature = feature
            it.store = store
            it.fetcher = lazyOf(fetcher)
            it.installer = lazyOf(installer)
            it.dispatchers = coroutineRule.testDispatcherProvider
        }

    private companion object {
        const val PAIR = "fr-en"
        const val BASE_URL = "https://models.test/"
        const val STAGING_PATH = "page-translation/packs/fr-en.tmp"
        const val PACK_PATH = "page-translation/packs/fr-en"

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
