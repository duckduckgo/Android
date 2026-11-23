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

package com.duckduckgo.sync.internal.quack

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.SyncCodeDispatcher
import dev.zacsweers.metro.ContributesIntoSet
import io.mehow.quack.QuackAction
import io.mehow.quack.QuackAction.Args
import io.mehow.quack.QuackAction.Outcome
import io.mehow.quack.QuackAction.Param
import io.mehow.quack.QuackAction.Terminal
import javax.inject.Inject

@ContributesIntoSet(AppScope::class)
class PairAsPresenterQuackAction @Inject constructor(
    private val dispatcher: SyncCodeDispatcher,
) : QuackAction {
    override val id = "sync.pair.presenter"
    override val description = "Shows a pairing code and pairs with the device that uses it"
    override val params = emptyList<Param<*>>()

    override suspend fun execute(args: Args, terminal: Terminal): Outcome {
        return dispatcher.presentV2().pairAutoAccepting(dispatcher, terminal, "Paired")
    }
}
