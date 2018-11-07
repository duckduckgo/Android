/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.privacy.renderer

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.privacy.model.HttpsStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class HttpsStatusRendererExtensionTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun whenHttpsStatusIsSecureThenTextReflectsSame() {
        assertEquals(context.getString(R.string.httpsGood), HttpsStatus.SECURE.text(context))
    }

    @Test
    fun whenHttpsStatusIsMixedThenTextReflectsSame() {
        assertEquals(context.getString(R.string.httpsMixed), HttpsStatus.MIXED.text(context))
    }

    @Test
    fun whenHttpsStatusIsNoneThenTextReflectsSame() {
        assertEquals(context.getString(R.string.httpsBad), HttpsStatus.NONE.text(context))
    }

    @Test
    fun whenHttpsStatusIsSecureThenIconReflectsSame() {
        assertEquals(R.drawable.dashboard_https_good, HttpsStatus.SECURE.icon())
    }

    @Test
    fun whenHttpsStatusIsMixedThenIconReflectsSame() {
        assertEquals(R.drawable.dashboard_https_neutral, HttpsStatus.MIXED.icon())
    }

    @Test
    fun whenHttpsStatusIsNoneThenIconReflectsSame() {
        assertEquals(R.drawable.dashboard_https_bad, HttpsStatus.NONE.icon())
    }

    @Test
    fun whenHttpsStatusIsSecureThenSuccessFailureIconIsSuccess() {
        assertEquals(R.drawable.icon_success, HttpsStatus.SECURE.successFailureIcon())
    }

    @Test
    fun whenHttpsStatusIsMixedThenSuccessFailureIconIsFailure() {
        assertEquals(R.drawable.icon_fail, HttpsStatus.MIXED.successFailureIcon())
    }

    @Test
    fun whenHttpsStatusIsNoneThenSuccessFailureIconIsFailure() {
        assertEquals(R.drawable.icon_fail, HttpsStatus.NONE.successFailureIcon())
    }
}