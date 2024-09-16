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

package com.duckduckgo.autofill.sync

import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.api.SyncMessagePlugin
import com.squareup.anvil.annotations.ContributesTo
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoSet

/**
 * This module provides the [SyncMessagePlugin]s that are used by the Sync feature.
 * Anvil produces a runtime error, so we have to use a workaround to make it work.
 */
@Module
@ContributesTo(ActivityScope::class)
abstract class SyncMessagePluginModule {
    @Binds @IntoSet
    abstract fun providesCredentialsSyncPausedSyncMessagePlugin(messagePlugin: CredentialsSyncPausedSyncMessagePlugin): SyncMessagePlugin
}
