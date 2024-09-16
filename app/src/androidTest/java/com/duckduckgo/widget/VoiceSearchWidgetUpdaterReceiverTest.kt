/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.widget

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class VoiceSearchWidgetUpdaterReceiverTest {

    private val testee = VoiceSearchWidgetUpdaterReceiver()
    private val updater: WidgetUpdater = mock()

    @Before
    fun setup() {
        testee.widgetUpdater = updater
    }

    @Test
    fun whenProcessIntentThenShouldUpdateWidgets() {
        val intent = Intent().apply {
            action = Intent.ACTION_LOCALE_CHANGED
        }
        val context = ApplicationProvider.getApplicationContext<Context>()

        testee.processIntent(context, intent)

        verify(updater).updateWidgets(any())
    }
}
