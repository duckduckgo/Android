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
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Event
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Runner
import com.duckduckgo.sync.internal.ui.LogRow
import dev.zacsweers.metro.ContributesIntoSet
import io.mehow.quack.QuackAction
import io.mehow.quack.QuackAction.Args
import io.mehow.quack.QuackAction.Outcome
import io.mehow.quack.QuackAction.Param
import io.mehow.quack.QuackAction.Terminal
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@ContributesIntoSet(AppScope::class)
class MonitorPairingQuackAction @Inject constructor(
    private val runner: ExchangeV2Runner,
) : QuackAction {
    override val id = "sync.pair.monitor"
    override val description = "Logs the pairing messages until the session ends"
    override val params = emptyList<Param<*>>()

    override suspend fun execute(args: Args, terminal: Terminal): Outcome {
        val sessionEnded = runner.eventsSince(System.currentTimeMillis())
            .onEach { event -> logMessage(event, terminal) }
            .filterIsInstance<ExchangeV2Event.SessionEnded>()
            .first()
        return Outcome.Ok("Session ended (${sessionEnded.byeReason.value})")
    }

    private suspend fun logMessage(event: ExchangeV2Event, terminal: Terminal) {
        val row = LogRow.from(event, id = 0)
        if (row.eventType.category != LogRow.Category.Message) return
        terminal.send("${row.timestampText} ${row.eventType.label}: ${row.summary}", json = row.rawJson)
    }
}
