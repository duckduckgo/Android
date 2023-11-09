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

package com.duckduckgo.sync.settings.impl

import com.duckduckgo.sync.settings.api.SyncableSetting

open class FakeSyncableSetting : SyncableSetting {
    private lateinit var listener: () -> Unit

    override var key: String = "fake_setting"

    private var value: String? = "fake_value"

    override fun getValue(): String? = value

    override fun save(value: String?): Boolean {
        this.value = value
        return true
    }

    override fun deduplicate(value: String?): Boolean {
        this.value = value
        return true
    }

    override fun registerToRemoteChanges(onDataChanged: () -> Unit) {
        this.listener = onDataChanged
    }

    override fun onSettingChanged() {
        this.listener.invoke()
    }

    override fun onSyncDisabled() {
        // no-op
    }
}
