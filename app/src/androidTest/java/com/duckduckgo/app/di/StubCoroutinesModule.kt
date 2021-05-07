/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.di

import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
@ContributesTo(
    scope = AppObjectGraph::class,
    replaces = [CoroutinesModule::class]
)
class StubCoroutinesModule {

    @Provides
    @Singleton
    fun providesDispatcherProvider(): DispatcherProvider {
        return CoroutineTestRule().testDispatcherProvider
    }
}
