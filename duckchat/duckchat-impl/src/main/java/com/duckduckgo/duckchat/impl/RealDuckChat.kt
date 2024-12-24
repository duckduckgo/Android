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

package com.duckduckgo.duckchat.impl

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChat
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface DuckChatInternal : DuckChat {
    /**
     * Stores setting to determine whether the DuckChat should be shown in browser menu
     */
    fun setShowInBrowserMenu(showDuckChat: Boolean)

    /**
     * Observes whether DuckChat should be shown in browser menu based on user settings and remote config flag
     */
    fun observeShowInBrowserMenu(): Flow<Boolean>
}

data class DuckChatSettingJson(
    val aiChatURL: String,
)

@ContributesBinding(AppScope::class, boundType = DuckChat::class)
@ContributesBinding(AppScope::class, boundType = DuckChatInternal::class)
class RealDuckChat @Inject constructor(
    private val duckChatFeatureRepository: DuckChatFeatureRepository,
    private val duckChatFeature: DuckChatFeature,
    private val moshi: Moshi,
) : DuckChatInternal {

    private val jsonAdapter: JsonAdapter<DuckChatSettingJson> by lazy {
        moshi.adapter(DuckChatSettingJson::class.java)
    }

    override fun isEnabled(): Boolean {
        return duckChatFeature.self().isEnabled()
    }

    override fun setShowInBrowserMenu(showDuckChat: Boolean) {
        duckChatFeatureRepository.setShowInBrowserMenu(showDuckChat)
    }

    override fun observeShowInBrowserMenu(): Flow<Boolean> {
        return duckChatFeatureRepository.observeShowInBrowserMenu().map {
            it && duckChatFeature.self().isEnabled()
        }
    }

    override fun showInBrowserMenu(): Boolean {
        return duckChatFeatureRepository.shouldShowInBrowserMenu() && duckChatFeature.self().isEnabled()
    }

    override fun getDuckChatWebLink(): String {
        val link = duckChatFeature.self().getSettings()?.let {
            runCatching {
                val settingsJson = jsonAdapter.fromJson(it)
                settingsJson?.aiChatURL
            }.getOrDefault(DUCK_CHAT_WEB_LINK)
        } ?: DUCK_CHAT_WEB_LINK
        return link
    }

    companion object {
        /** Default link to DuckChat that identifies Android as the source */
        private const val DUCK_CHAT_WEB_LINK = "https://duckduckgo.com/?q=DuckDuckGo+AI+Chat&ia=chat&duckai=5"
    }
}
