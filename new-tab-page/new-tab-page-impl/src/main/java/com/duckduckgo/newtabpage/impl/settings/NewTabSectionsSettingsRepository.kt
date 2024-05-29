/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.newtabpage.impl.settings

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.newtabpage.impl.settings.db.NewTabSettingsDatabase
import com.duckduckgo.newtabpage.impl.settings.db.NewTabUserSection
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface NewTabUserSectionsRepository {
    fun getUserSections(): List<NewTabUserSection>
    fun getUserSection(name: String): NewTabUserSection
}

@ContributesBinding(AppScope::class)
class RealNewTabUserSectionsRepository @Inject constructor(
    private val database: NewTabSettingsDatabase,
) : NewTabUserSectionsRepository {
    override fun getUserSections() = database.userSectionsDao().get()
    override fun getUserSection(name: String): NewTabUserSection {
        val existentSection = database.userSectionsDao().getBy(name)
        // it can be that a new section is created remotely and not yet locally.
        // we insert it in the db so it can be used in settings
        // it will only be visible if there's a view that implements the plugin with this name
        if (existentSection == null) {
            val newSection = NewTabUserSection(name = name, enabled = true)
            database.userSectionsDao().insert(newSection)
            return newSection
        } else {
            return existentSection
        }
    }
}
