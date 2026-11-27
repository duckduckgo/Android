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
package com.duckduckgo.pagetranslation.impl.sheet

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.pagetranslation.impl.AttachedWebView
import com.duckduckgo.pagetranslation.impl.R
import com.duckduckgo.pagetranslation.impl.language.LanguagePair
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackDownload
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackDownloadState
import com.duckduckgo.pagetranslation.impl.manifest.ModelsManifest
import com.duckduckgo.pagetranslation.impl.manifest.ModelsManifestStore
import com.duckduckgo.pagetranslation.impl.offer.ActiveOffer
import com.duckduckgo.pagetranslation.impl.offer.TranslationOfferSource
import com.duckduckgo.pagetranslation.impl.translate.TranslateAction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric

@RunWith(AndroidJUnit4::class)
class LanguagePackSheetPresenterTest {

    @SuppressLint("NoFragment")
    class HostFragment : Fragment() {
        val webView by lazy { WebView(requireContext()) }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
            FrameLayout(inflater.context).apply { addView(webView) }
    }

    private val pair = LanguagePair("es", "en")
    private val manifestStore: ModelsManifestStore = mock { onBlocking { readManifest() } doReturn manifestOf(pair.key to 23_000_000L) }
    private val offers = MutableStateFlow<ActiveOffer?>(null)
    private val offerSource: TranslationOfferSource = mock { on { offer } doReturn offers }
    private val result = CompletableDeferred<LanguagePackDownloadState>()
    private val download: LanguagePackDownload = mock {
        onBlocking { download(pair) } doSuspendableAnswer { result.await() }
    }
    private val translateAction: TranslateAction = mock()
    private val activityController = Robolectric.buildActivity(SheetTestActivity::class.java).setup()
    private val hostFragment = HostFragment()
    private val attachedWebView = AttachedWebView()
    private val testee = LanguagePackSheetPresenter(attachedWebView, manifestStore, offerSource, download, translateAction)

    @Before
    fun setup() {
        activityController.get().supportFragmentManager.beginTransaction().add(android.R.id.content, hostFragment, "host").commitNow()
    }

    private fun attach(webView: WebView) {
        attachedWebView.attach(webView)
        testee.listen(webView)
    }

    private fun attachHost() = attach(hostFragment.webView)

    private fun sheet() = hostFragment.childFragmentManager.findFragmentByTag(LanguagePackSheetFragment.TAG)

    private fun requestDownload() {
        testee.requestDownload(pair)
        hostFragment.childFragmentManager.executePendingTransactions()
    }

    @Test
    fun whenDownloadRequestedWithResumedHostThenSheetIsShown() {
        attachHost()

        requestDownload()

        assertNotNull(sheet())
    }

    @Test
    fun whenNoHostThenSheetIsNotShown() {
        requestDownload()

        assertNull(sheet())
    }

    @Test
    fun whenHostDetachedThenSheetIsNotShown() {
        attachHost()
        attachedWebView.detach(hostFragment.webView)

        requestDownload()

        assertNull(sheet())
    }

    @Test
    fun whenAttachedWebViewIsNotInAFragmentThenSheetIsNotShown() {
        attach(WebView(activityController.get()))

        requestDownload()

        assertNull(sheet())
    }

    @Test
    fun whenHostStateIsSavedThenSheetIsNotShown() {
        attachHost()
        activityController.pause().saveInstanceState(Bundle())

        requestDownload()

        assertNull(sheet())
    }

    @Test
    fun whenSheetAlreadyShownThenSecondRequestKeepsTheSameSheet() {
        attachHost()
        requestDownload()
        val first = sheet()

        requestDownload()

        assertNotNull(first)
        assertSame(first, sheet())
    }

    private fun confirm() {
        sheet()!!.requireView().findViewById<View>(R.id.languagePackSheetButton).performClick()
        hostFragment.childFragmentManager.executePendingTransactions()
    }

    @Test
    fun whenConfirmedAndDownloadedWithTheSameOfferThenTranslates() {
        offers.value = ActiveOffer(pair, document = 7L)
        attachHost()
        requestDownload()

        confirm()
        result.complete(LanguagePackDownloadState.Downloaded)

        verify(translateAction).translate(pair)
        assertNull(sheet())
    }

    @Test
    fun whenOfferChangedDuringTheDownloadThenDoesNotTranslate() {
        offers.value = ActiveOffer(pair, document = 7L)
        attachHost()
        requestDownload()

        confirm()
        offers.value = ActiveOffer(pair, document = 8L)
        result.complete(LanguagePackDownloadState.Downloaded)

        verify(translateAction, never()).translate(pair)
    }

    @Test
    fun whenOfferWentAwayDuringTheDownloadThenDoesNotTranslate() {
        offers.value = ActiveOffer(pair, document = 7L)
        attachHost()
        requestDownload()

        confirm()
        offers.value = null
        result.complete(LanguagePackDownloadState.Downloaded)

        verify(translateAction, never()).translate(pair)
    }

    @Test
    fun whenConfirmedWithoutAnOfferThenDownloadsButDoesNotTranslate() {
        attachHost()
        requestDownload()

        confirm()
        result.complete(LanguagePackDownloadState.Downloaded)

        runBlocking { verify(download).download(pair) }
        verify(translateAction, never()).translate(pair)
    }

    @Test
    fun whenOfferIsForAnotherPairThenDoesNotTranslate() {
        offers.value = ActiveOffer(LanguagePair("fr", "en"), document = 7L)
        attachHost()
        requestDownload()

        confirm()
        result.complete(LanguagePackDownloadState.Downloaded)

        verify(translateAction, never()).translate(pair)
    }

    @Test
    fun whenDownloadFailedThenDoesNotTranslate() {
        offers.value = ActiveOffer(pair, document = 7L)
        attachHost()
        requestDownload()

        confirm()
        result.complete(LanguagePackDownloadState.Failed(LanguagePackDownloadState.Failed.Reason.DOWNLOAD_ERROR))

        verify(translateAction, never()).translate(pair)
    }

    @Test
    fun whenConfirmedTwiceThenTranslatesOnce() {
        offers.value = ActiveOffer(pair, document = 7L)
        attachHost()
        requestDownload()
        confirm()
        requestDownload()
        confirm()

        result.complete(LanguagePackDownloadState.Downloaded)

        runBlocking { verify(download, times(2)).download(pair) }
        verify(translateAction, times(1)).translate(pair)
    }

    @Test
    fun whenSheetDismissedThenNothingIsDownloaded() {
        attachHost()
        requestDownload()

        sheet()!!.requireView().findViewById<View>(R.id.languagePackSheetClose).performClick()
        hostFragment.childFragmentManager.executePendingTransactions()

        verifyNoInteractions(download)
        assertNull(sheet())
    }

    @Test
    fun whenManifestListsPairThenSheetShowsItsSize() {
        attachHost()

        requestDownload()

        assertEquals(View.VISIBLE, sheetSize().visibility)
    }

    @Test
    fun whenManifestDoesNotListPairThenSheetHidesTheSize() {
        manifestStore.stub(manifestOf("fr-en" to 5L))
        attachHost()

        requestDownload()

        assertEquals(View.GONE, sheetSize().visibility)
    }

    @Test
    fun whenThereIsNoManifestThenSheetHidesTheSize() {
        manifestStore.stub(null)
        attachHost()

        requestDownload()

        assertEquals(View.GONE, sheetSize().visibility)
    }

    private fun sheetSize() = sheet()!!.requireView().findViewById<View>(R.id.languagePackSheetSize)

    private fun ModelsManifestStore.stub(manifest: ModelsManifest?) {
        runBlocking { whenever(readManifest()).thenReturn(manifest) }
    }

    private fun manifestOf(vararg models: Pair<String, Long>) = ModelsManifest(
        schemaVersion = 1,
        baseUrl = "https://example.com",
        models = models.associate { (key, bytes) ->
            key to ModelsManifest.TranslationModel(
                sourceLanguage = key.substringBefore('-'),
                targetLanguage = key.substringAfter('-'),
                version = "1",
                architecture = "base",
                totalBytes = bytes,
                files = emptyMap(),
            )
        },
    )
}
