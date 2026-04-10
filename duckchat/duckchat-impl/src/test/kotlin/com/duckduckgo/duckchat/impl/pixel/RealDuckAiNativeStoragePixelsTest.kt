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

package com.duckduckgo.duckchat.impl.pixel

import com.duckduckgo.app.statistics.pixels.Pixel
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class RealDuckAiNativeStoragePixelsTest {

    private val pixel: Pixel = mock()
    private lateinit var pixels: RealDuckAiNativeStoragePixels

    @Before
    fun setup() {
        pixels = RealDuckAiNativeStoragePixels(pixel)
    }

    @Test
    fun `reportMigrationDone fires unique pixel for given key`() {
        pixels.reportMigrationDone("chats")

        verify(pixel).fire("m_aichat_native_storage_migration_done_chats_unique", type = Pixel.PixelType.Unique())
    }

    @Test
    fun `reportMigrationDone fires count pixel for given key`() {
        pixels.reportMigrationDone("chats")

        verify(pixel).fire("m_aichat_native_storage_migration_done_chats_count")
    }

    @Test
    fun `reportMigrationDone uses the provided key in pixel name`() {
        pixels.reportMigrationDone("entries")

        verify(pixel).fire("m_aichat_native_storage_migration_done_entries_unique", type = Pixel.PixelType.Unique())
        verify(pixel).fire("m_aichat_native_storage_migration_done_entries_count")
    }

    @Test
    fun `reportMigrationDoneBlankKey fires blank count pixel`() {
        pixels.reportMigrationDoneBlankKey()

        verify(pixel).fire("m_aichat_native_storage_migration_done_blank_count")
    }
}
