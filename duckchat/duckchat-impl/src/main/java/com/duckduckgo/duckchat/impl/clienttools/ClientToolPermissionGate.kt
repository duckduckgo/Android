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

package com.duckduckgo.duckchat.impl.clienttools

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.isInternalBuild
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import javax.inject.Inject

interface ClientToolPermissionGate {
    suspend fun decide(
        tool: ClientTool,
        context: ClientToolCallContext,
    ): ClientToolPermissionDecision
}

enum class ClientToolPermissionDecision {
    ALLOWED,
    DENIED,
}

@ContributesBinding(AppScope::class)
class PromptlessClientToolPermissionGate @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
    private val browserToolsFeature: AiChatBrowserToolsFeature,
) : ClientToolPermissionGate {

    override suspend fun decide(
        tool: ClientTool,
        context: ClientToolCallContext,
    ): ClientToolPermissionDecision = when (tool.permissionMode) {
        ClientToolPermissionMode.AUTO -> ClientToolPermissionDecision.ALLOWED
        ClientToolPermissionMode.ASK ->
            if (appBuildConfig.isInternalBuild() && browserToolsFeature.devSkipPermissionPrompt().isEnabled()) {
                ClientToolPermissionDecision.ALLOWED
            } else {
                ClientToolPermissionDecision.DENIED
            }
    }
}
