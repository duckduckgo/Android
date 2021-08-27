/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.globalprivacycontrol

class GlobalPrivacyControlManagerTest {

//    private val mockSettingsStore: SettingsDataStore = mock()
//    private val mockFeatureToggle: FeatureToggle = mock()
//    private val mockGpc: Gpc = mock()
//    lateinit var testee: GlobalPrivacyControlManager
//
//    @Before
//    fun setup() {
//        testee = GlobalPrivacyControlManager(mockSettingsStore, mockFeatureToggle, mockGpc)
//    }
//
//    @UiThreadTest
//    @Test
//    @SdkSuppress(minSdkVersion = 24)
//    fun whenInjectDoNotSellToDomAndGcpIsEnabledThenInjectToDom() {
//        val jsToEvaluate = getJsToEvaluate()
//        val webView = spy(WebView(InstrumentationRegistry.getInstrumentation().targetContext))
//        whenever(mockSettingsStore.globalPrivacyControlEnabled).thenReturn(true)
//
//        testee.injectDoNotSellToDom(webView)
//
//        verify(webView).evaluateJavascript(jsToEvaluate, null)
//    }
//
//    @UiThreadTest
//    @Test
//    @SdkSuppress(minSdkVersion = 24)
//    fun whenInjectDoNotSellToDomAndGcpIsNotEnabledThenDoNotInjectToDom() {
//        val jsToEvaluate = getJsToEvaluate()
//        val webView = spy(WebView(InstrumentationRegistry.getInstrumentation().targetContext))
//        whenever(mockSettingsStore.globalPrivacyControlEnabled).thenReturn(false)
//
//        testee.injectDoNotSellToDom(webView)
//
//        verify(webView, never()).evaluateJavascript(jsToEvaluate, null)
//    }
//
//    @Test
//    fun whenIsGpcActiveAndSettingEnabledThenReturnTrue() {
//        whenever(mockSettingsStore.globalPrivacyControlEnabled).thenReturn(true)
//
//        assertTrue(testee.isGpcActive())
//    }
//
//    @Test
//    fun whenIsGpcActiveAndSettingDisabledThenReturnFalse() {
//        whenever(mockSettingsStore.globalPrivacyControlEnabled).thenReturn(false)
//
//        assertFalse(testee.isGpcActive())
//    }
//
//    @Test
//    fun whenGetHeadersIfGpcIsEnabledThenReturnHeaders() {
//        whenever(mockSettingsStore.globalPrivacyControlEnabled).thenReturn(true)
//
//        val headers = testee.getHeaders("example.com")
//
//        assertEquals(GPC_HEADER_VALUE, headers[GPC_HEADER])
//    }
//
//    @Test
//    fun whenGetHeadersIfGpcIsDisabledThenReturnEmptyMap() {
//        whenever(mockSettingsStore.globalPrivacyControlEnabled).thenReturn(false)
//
//        val headers = testee.getHeaders("example.com")
//
//        assertTrue(headers.isEmpty())
//    }
//
//    @Test
//    fun whenShouldAddHeadersAndUrlIsFromTheHeadersConsumersListThenReturnTrue() {
//        assertTrue(testee.canPerformARedirect("http://nytimes.com".toUri()))
//    }
//
//    @Test
//    fun whenShouldAddHeadersAndUrlIsNotFromTheHeadersConsumersListThenReturnFalse() {
//        assertFalse(testee.canPerformARedirect("http://example.com".toUri()))
//    }
//
//    private fun getJsToEvaluate(): String {
//        val js = InstrumentationRegistry.getInstrumentation().targetContext.resources.openRawResource(R.raw.gpc)
//            .bufferedReader()
//            .use { it.readText() }
//        return "javascript:$js"
//    }
}
