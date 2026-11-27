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

package com.duckduckgo.pagetranslation.impl

import android.webkit.WebView
import com.duckduckgo.pagetranslation.api.TabContent
import com.duckduckgo.pagetranslation.impl.messaging.PageTranslationMessaging
import com.duckduckgo.pagetranslation.impl.session.TabTranslation
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class RealPageTranslationTest {
    private val attachedWebView = AttachedWebView()
    private val messaging: PageTranslationMessaging = mock()
    private val tabTranslation: TabTranslation = mock()
    private val pageTranslation = RealPageTranslation(mock(), attachedWebView, mock(), messaging, tabTranslation)
    private val webView: WebView = mock()

    @Test
    fun endThePreviousWebViewsSessionThenRegisterTheInterfaceOnAttach() {
        pageTranslation.attach(webView)

        assertSame(webView, attachedWebView.webView)
        inOrder(tabTranslation, messaging) {
            verify(tabTranslation).endSession()
            verify(messaging).register(webView)
        }
    }

    @Test
    fun endTheSessionWhenTheAttachedWebViewIsDetached() {
        pageTranslation.attach(webView)

        pageTranslation.detach(webView)

        assertNull(attachedWebView.webView)
        verify(tabTranslation, times(2)).endSession()
    }

    @Test
    fun ignoreADetachOfAWebViewThatIsNotAttached() {
        pageTranslation.attach(webView)

        pageTranslation.detach(mock())

        assertSame(webView, attachedWebView.webView)
        verify(tabTranslation, times(1)).endSession()
    }

    @Test
    fun passTheTabsVisibilityToTheTranslation() {
        pageTranslation.onTabStateChanged(isVisible = true, TabContent.WebPage)

        verify(tabTranslation).setVisible(true)
    }
}
