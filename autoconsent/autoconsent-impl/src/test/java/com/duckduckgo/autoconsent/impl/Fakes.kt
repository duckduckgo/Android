/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.autoconsent.impl

import android.webkit.WebView
import androidx.core.net.toUri
import com.duckduckgo.app.global.domain
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.app.userwhitelist.api.UserWhiteListRepository
import com.duckduckgo.autoconsent.api.AutoconsentCallback
import com.duckduckgo.autoconsent.store.AutoconsentSettingsRepository
import com.duckduckgo.privacy.config.api.UnprotectedTemporary

class FakePluginPoint : PluginPoint<MessageHandlerPlugin> {
    val plugin = FakeMessageHandlerPlugin()
    override fun getPlugins(): Collection<MessageHandlerPlugin> {
        return listOf(plugin)
    }
}

class FakeMessageHandlerPlugin : MessageHandlerPlugin {
    var count = 0

    override fun process(
        messageType: String,
        jsonString: String,
        webView: WebView,
        autoconsentCallback: AutoconsentCallback
    ) {
        count++
    }

    override val supportedTypes: List<String> = listOf("fake")
}

class FakeSettingsRepository : AutoconsentSettingsRepository {
    override var userSetting: Boolean = false
    override var firstPopupHandled: Boolean = false
}

class FakeUnprotected(private val exceptionList: List<String>) : UnprotectedTemporary {
    override fun isAnException(url: String): Boolean {
        return exceptionList.contains(url.toUri().domain())
    }
}

class FakeUserAllowlist(override val userWhiteList: List<String>) : UserWhiteListRepository
