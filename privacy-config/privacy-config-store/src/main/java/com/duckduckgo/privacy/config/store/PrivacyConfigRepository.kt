/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.privacy.config.store

interface PrivacyConfigRepository {
    fun insert(privacyConfig: PrivacyConfig)
    fun get(): PrivacyConfig?
    fun delete()
}

class RealPrivacyConfigRepository(database: PrivacyConfigDatabase) : PrivacyConfigRepository {

    private val privacyConfigDao: PrivacyConfigDao = database.privacyConfigDao()

    override fun insert(privacyConfig: PrivacyConfig) {
        privacyConfigDao.insert(privacyConfig)
    }

    override fun get(): PrivacyConfig? {
        return privacyConfigDao.get()
    }

    override fun delete() {
        privacyConfigDao.delete()
    }
}
