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

package com.duckduckgo.duckchat.internal.testseeder

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.testseeder.api.TestSeederKey
import com.duckduckgo.testseeder.api.TestSeederPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class NativeInputToggleSeederPlugin @Inject constructor(
    private val duckChat: DuckChatInternal,
) : TestSeederPlugin {

    override val handledKeys = setOf(TestSeederKey.NATIVE_INPUT_TOGGLE.key)

    override suspend fun apply(key: String, value: String) {
        duckChat.setNativeInputFieldUserSetting(value == "true")
    }
}
