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

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.pagetranslation.impl.PageTranslationDeviceSupport
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

class LanguagePackCleanupObserverTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val scheduler: LanguagePackScheduler = mock()
    private var isDeviceSupported = true
    private val deviceSupport = object : PageTranslationDeviceSupport {
        override fun isSupported() = isDeviceSupported
    }
    private val observer =
        LanguagePackCleanupObserver(scheduler, deviceSupport, coroutineRule.testScope, coroutineRule.testDispatcherProvider)

    @Test
    fun removeLeftoverDeletedPacksAtLaunch() {
        observer.onCreate(mock<LifecycleOwner>())

        verify(scheduler).removeLeftoverOldDirectories()
    }

    @Test
    fun removeNothingOnUnsupportedDevices() {
        isDeviceSupported = false

        observer.onCreate(mock<LifecycleOwner>())

        verifyNoInteractions(scheduler)
    }
}
