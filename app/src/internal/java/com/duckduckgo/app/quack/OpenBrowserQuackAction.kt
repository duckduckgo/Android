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

package com.duckduckgo.app.quack

import android.content.Context
import android.content.Intent
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.mode.InAppNavigation
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import io.mehow.quack.QuackAction
import io.mehow.quack.QuackAction.Args
import io.mehow.quack.QuackAction.Outcome
import io.mehow.quack.QuackAction.Param
import io.mehow.quack.QuackAction.Terminal
import javax.inject.Inject

@ContributesIntoSet(AppScope::class)
class OpenBrowserQuackAction @Inject constructor(
    private val context: Context,
) : QuackAction {
    override val id = "goto.browser"
    override val description = "Closes all screens above the browser"
    override val params = emptyList<Param<*>>()

    override suspend fun execute(args: Args, terminal: Terminal): Outcome {
        val intent = BrowserActivity.intent(context, InAppNavigation)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return Outcome.Ok("Opened the browser")
    }
}
