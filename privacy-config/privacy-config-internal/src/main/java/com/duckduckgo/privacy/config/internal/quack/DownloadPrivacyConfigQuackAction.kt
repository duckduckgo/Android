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

package com.duckduckgo.privacy.config.internal.quack

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.impl.PrivacyConfigDownloader.ConfigDownloadResult
import com.duckduckgo.privacy.config.internal.PrivacyConfigInternalLoader
import com.duckduckgo.privacy.config.store.PrivacyConfigRepository
import dev.zacsweers.metro.ContributesIntoSet
import io.mehow.quack.QuackAction
import io.mehow.quack.QuackAction.Args
import io.mehow.quack.QuackAction.Outcome
import io.mehow.quack.QuackAction.Param
import io.mehow.quack.QuackAction.Terminal
import javax.inject.Inject

@ContributesIntoSet(AppScope::class)
class DownloadPrivacyConfigQuackAction @Inject constructor(
    private val loader: PrivacyConfigInternalLoader,
    private val repository: PrivacyConfigRepository,
) : QuackAction {
    override val id = "privacy_config.download"
    override val description = "Downloads privacy config"
    override val params = emptyList<Param<*>>()

    override suspend fun execute(args: Args, terminal: Terminal): Outcome {
        return when (val result = loader.load()) {
            is ConfigDownloadResult.Success -> Outcome.Ok("Downloaded privacy config version ${repository.get()?.version}")
            is ConfigDownloadResult.Error -> Outcome.Error("Download failed: ${result.error ?: "unknown error"}")
        }
    }
}
