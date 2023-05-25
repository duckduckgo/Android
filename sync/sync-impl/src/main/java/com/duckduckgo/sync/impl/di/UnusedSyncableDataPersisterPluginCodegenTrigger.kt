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

package com.duckduckgo.sync.impl.di

import com.duckduckgo.anvil.annotations.ContributesPluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.engine.SyncableDataPersister

/**
 * This is here to trigger the code generations
 * [SyncablePlugin] lives in the api module but we don't want to add
 * anvil dependencies
 * CodeGen should not be generated in public api modules.
 */
@ContributesPluginPoint(
    scope = AppScope::class,
    boundType = SyncableDataPersister::class,
)
@Suppress("unused")
private interface UnusedSyncableDataPersisterPluginCodegenTrigger
