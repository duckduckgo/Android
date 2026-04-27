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
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.store.impl.DuckAiNativeStoragePixels
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealDuckAiNativeStoragePixels @Inject constructor(
    private val pixel: Pixel,
) : DuckAiNativeStoragePixels {

    override fun reportMigrationDone(key: String) {
        pixel.fire("m_duck-ai_native-storage_migration_done_${key}_unique", type = Pixel.PixelType.Unique())
        pixel.fire("m_duck-ai_native-storage_migration_done_${key}_count")
    }

    override fun reportMigrationDoneBlankKey() {
        pixel.fire("m_duck-ai_native-storage_migration_done_blank_count")
    }

    override fun reportMigrationStarted() {
        pixel.fire("m_duck-ai_native-storage_migration_started")
    }

    override fun reportMigrationAlreadyDone() {
        pixel.fire("m_duck-ai_native-storage_migration_already-done")
    }

    override fun reportMigrationError() {
        pixel.fire("m_duck-ai_native-storage_migration_error")
    }

    override fun reportSettingsPutError() {
        pixel.fire("m_duck-ai_native-storage_settings-put_error")
    }

    override fun reportSettingsGetError() {
        pixel.fire("m_duck-ai_native-storage_settings-get_error")
    }

    override fun reportSettingsDeleteError() {
        pixel.fire("m_duck-ai_native-storage_settings-delete_error")
    }

    override fun reportChatPutError() {
        pixel.fire("m_duck-ai_native-storage_chat-put_error")
    }

    override fun reportChatGetError() {
        pixel.fire("m_duck-ai_native-storage_chat-get_error")
    }

    override fun reportChatDeleteError() {
        pixel.fire("m_duck-ai_native-storage_chat-delete_error")
    }

    override fun reportFilePutError() {
        pixel.fire("m_duck-ai_native-storage_file-put_error")
    }

    override fun reportFileGetError() {
        pixel.fire("m_duck-ai_native-storage_file-get_error")
    }

    override fun reportFileListError() {
        pixel.fire("m_duck-ai_native-storage_file-list_error")
    }

    override fun reportFileDeleteError() {
        pixel.fire("m_duck-ai_native-storage_file-delete_error")
    }
}
