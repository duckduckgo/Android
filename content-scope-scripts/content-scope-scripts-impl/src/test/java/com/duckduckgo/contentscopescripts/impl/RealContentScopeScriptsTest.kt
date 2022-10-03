/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.contentscopescripts.impl

import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.app.userwhitelist.api.UserWhiteListRepository
import com.duckduckgo.contentscopescripts.api.ContentScopeConfigPlugin
import com.duckduckgo.contentscopescripts.api.ContentScopeScripts
import com.duckduckgo.privacy.config.api.Gpc
import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

class RealContentScopeScriptsTest {

    private val mockPluginPoint: PluginPoint<ContentScopeConfigPlugin> = mock()
    private val mockAllowList: UserWhiteListRepository = mock()
    private val mockGpc: Gpc = mock()
    private val mockContentScopeJsReader: ContentScopeJSReader = mock()
    private val mockPlugin1: ContentScopeConfigPlugin = mock()
    private val mockPlugin2: ContentScopeConfigPlugin = mock()
    private val mockPlugin3: ContentScopeConfigPlugin = mock()

    lateinit var testee: ContentScopeScripts

    @Before
    fun setup() {
        testee = RealContentScopeScripts(mockPluginPoint, mockAllowList, mockGpc, mockContentScopeJsReader)
        whenever(mockPlugin1.config()).thenReturn(config1)
        whenever(mockPlugin2.config()).thenReturn(config2)
        whenever(mockPlugin3.config()).thenReturn(null)
        whenever(mockPluginPoint.getPlugins()).thenReturn(listOf(mockPlugin1, mockPlugin2, mockPlugin3))
        whenever(mockAllowList.userWhiteList).thenReturn(listOf(exampleUrl))
        whenever(mockGpc.isEnabled()).thenReturn(true)
        whenever(mockContentScopeJsReader.getContentScopeJS()).thenReturn(contentScopeJS)
    }

    @Test
    fun whenGetScriptWhenVariablesAreCachedAndNoChangesThenUseCachedVariables() {
        var js = testee.getScript()

        assertEquals(defaultExpectedJs, js)
        verify(mockContentScopeJsReader).getContentScopeJS()

        js = testee.getScript()

        assertEquals(defaultExpectedJs, js)
        verify(mockGpc, times(3)).isEnabled()
        verify(mockAllowList, times(3)).userWhiteList
        verifyNoMoreInteractions(mockContentScopeJsReader)
    }

    @Test
    fun whenGetScriptAndVariablesAreCachedAndAllowListChangedThenUseNewAllowListValue() {
        var js = testee.getScript()

        assertEquals(defaultExpectedJs, js)

        whenever(mockAllowList.userWhiteList).thenReturn(listOf(exampleUrl2))

        js = testee.getScript()

        assertEquals(
            "processConfig(" +
                "{\"features\":{" +
                "\"config1\":{\"state\":\"enabled\"}," +
                "\"config2\":{\"state\":\"disabled\"}}," +
                "\"unprotectedTemporary\":[]}, [\"foo.com\"], {\"globalPrivacyControlValue\":true})",
            js
        )

        verify(mockGpc, times(3)).isEnabled()
        verify(mockAllowList, times(4)).userWhiteList
        verify(mockContentScopeJsReader, times(2)).getContentScopeJS()
    }

    @Test
    fun whenGetScriptAndVariablesAreCachedAndGpcChangedThenUseNewGpcValue() {
        var js = testee.getScript()

        assertEquals(defaultExpectedJs, js)

        whenever(mockGpc.isEnabled()).thenReturn(false)

        js = testee.getScript()

        assertEquals(
            "processConfig(" +
                "{\"features\":{" +
                "\"config1\":{\"state\":\"enabled\"}," +
                "\"config2\":{\"state\":\"disabled\"}}," +
                "\"unprotectedTemporary\":[]}, [\"example.com\"], {\"globalPrivacyControlValue\":false})",
            js
        )

        verify(mockGpc, times(4)).isEnabled()
        verify(mockAllowList, times(3)).userWhiteList
        verify(mockContentScopeJsReader, times(2)).getContentScopeJS()
    }

    @Test
    fun whenGetScriptAndVariablesAreCachedAndConfigChangedThenUseNewConfigValue() {
        var js = testee.getScript()

        assertEquals(defaultExpectedJs, js)

        whenever(mockPluginPoint.getPlugins()).thenReturn(listOf(mockPlugin3, mockPlugin1))

        js = testee.getScript()

        assertEquals(
            "processConfig(" +
                "{\"features\":{" +
                "\"config1\":{\"state\":\"enabled\"}}," +
                "\"unprotectedTemporary\":[]}, [\"example.com\"], {\"globalPrivacyControlValue\":true})",
            js
        )

        verify(mockGpc, times(3)).isEnabled()
        verify(mockAllowList, times(3)).userWhiteList
        verify(mockContentScopeJsReader, times(2)).getContentScopeJS()
    }

    companion object {
        const val contentScopeJS = "processConfig(\$CONTENT_SCOPE\$, \$USER_UNPROTECTED_DOMAINS\$, \$USER_PREFERENCES\$)"
        const val config1 = "\"config1\":{\"state\":\"enabled\"}"
        const val config2 = "\"config2\":{\"state\":\"disabled\"}"
        const val exampleUrl = "example.com"
        const val exampleUrl2 = "foo.com"
        const val defaultExpectedJs = "processConfig(" +
            "{\"features\":{" +
            "\"config1\":{\"state\":\"enabled\"}," +
            "\"config2\":{\"state\":\"disabled\"}}," +
            "\"unprotectedTemporary\":[]}, [\"example.com\"], {\"globalPrivacyControlValue\":true})"
    }
}
