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

package com.duckduckgo.app.anr.internal.setting

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.data.store.api.FakeSharedPreferencesProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CrashpadUploadPluginTest {

    private lateinit var uploadConfig: CrashpadUploadConfig
    private lateinit var plugin: CrashpadUploadPlugin

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        // Clear any state from previous tests
        context.getSharedPreferences("crashpad_upload_config", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        uploadConfig = CrashpadUploadConfig(FakeSharedPreferencesProvider())
        plugin = CrashpadUploadPlugin(uploadConfig)
    }

    @Test
    fun `subtitle shows Uploads disabled when URL is empty`() {
        assertEquals("Uploads disabled", plugin.subtitle())
    }

    @Test
    fun `subtitle shows URL when upload URL is configured`() {
        val url = "http://192.168.1.100:8080/upload"
        uploadConfig.uploadUrl = url
        assertEquals(url, plugin.subtitle())
    }

    @Test
    fun `subtitle updates after URL is cleared`() {
        uploadConfig.uploadUrl = "http://192.168.1.100:8080/upload"
        uploadConfig.uploadUrl = ""
        assertEquals("Uploads disabled", plugin.subtitle())
    }
}
