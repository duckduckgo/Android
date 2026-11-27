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
import com.duckduckgo.pagetranslation.impl.language.LanguagePacks
import com.duckduckgo.pagetranslation.impl.language.LanguagePair
import com.duckduckgo.pagetranslation.impl.translate.TranslateAction
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
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
    private val languagePacks: LanguagePacks = mock { on { sizeBytes(pair) } doReturn 23_000_000L }
    private val translateAction: TranslateAction = mock()
    private val activityController = Robolectric.buildActivity(SheetTestActivity::class.java).setup()
    private val hostFragment = HostFragment()
    private val attachedWebView = AttachedWebView()
    private val testee = LanguagePackSheetPresenter(attachedWebView, languagePacks, translateAction)

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

    @Test
    fun whenSheetConfirmedThenPairIsTranslated() {
        attachHost()
        requestDownload()

        sheet()!!.requireView().findViewById<View>(R.id.languagePackSheetButton).performClick()
        hostFragment.childFragmentManager.executePendingTransactions()

        verify(translateAction).translate(pair)
        assertNull(sheet())
    }

    @Test
    fun whenSheetDismissedThenNothingIsTranslated() {
        attachHost()
        requestDownload()

        sheet()!!.requireView().findViewById<View>(R.id.languagePackSheetClose).performClick()
        hostFragment.childFragmentManager.executePendingTransactions()

        verifyNoInteractions(translateAction)
        assertNull(sheet())
    }
}
