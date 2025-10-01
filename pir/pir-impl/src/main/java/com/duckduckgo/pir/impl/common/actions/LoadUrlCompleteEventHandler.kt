/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.pir.impl.common.actions

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.common.PirJobConstants.DBP_INITIAL_URL
import com.duckduckgo.pir.impl.common.PirJobConstants.RECOVERY_URL
import com.duckduckgo.pir.impl.common.actions.EventHandler.Next
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.BrokerStepCompleted
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.ExecuteBrokerStepAction
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.ExecuteNextBrokerStep
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.Event.LoadUrlComplete
import com.duckduckgo.pir.impl.common.actions.PirActionsRunnerStateEngine.State
import com.duckduckgo.pir.impl.scripts.models.PirScriptRequestData.UserProfile
import com.squareup.anvil.annotations.ContributesMultibinding
import logcat.logcat
import javax.inject.Inject
import kotlin.reflect.KClass

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = EventHandler::class,
)
class LoadUrlCompleteEventHandler @Inject constructor() : EventHandler {
    override val event: KClass<out Event> = LoadUrlComplete::class

    override suspend fun invoke(
        state: State,
        event: Event,
    ): Next {
        /**
         * This means whatever url we loaded into the webview has beed completed.
         * - DBP_INITIAL_URL -> means we just initialized the webview and ready to start the run (entry point).
         * - RECOVERY_URL -> means we've loaded a url prior that failed to complete due to an error and now we are attempting to recover.
         *      - We mark the current Broker as completed.
         * - Else, we proceed to the next action.
         */
        val actualEvent = event as LoadUrlComplete

        if (state.pendingUrl == null) {
            return Next(state)
        }

        return when (actualEvent.url) {
            DBP_INITIAL_URL -> {
                Next(
                    nextState = state.copy(
                        currentBrokerStepIndex = 0,
                        currentActionIndex = 0,
                        pendingUrl = null,
                    ),
                    nextEvent = ExecuteNextBrokerStep,
                )
            }

            RECOVERY_URL -> {
                logcat { "PIR-RUNNER ($this): Completing broker due to recovery" }
                // nextCommand(BrokerCompleted(commandsFlow.value.state, isSuccess = false))
                Next(
                    nextState = state.copy(
                        pendingUrl = null,
                    ),
                    nextEvent = BrokerStepCompleted(false),
                )
            }

            else -> {
                // If the current action is still navigate, it means we just finished loading and we can proceed to next action.
                // Sometimes the loaded url gets redirected to another url (could be different domain too) so we can't really check here.
                logcat { "PIR-RUNNER ($this): Completed loading for ${event.url}" }
                Next(
                    nextState = state.copy(
                        currentActionIndex = state.currentActionIndex + 1,
                        actionRetryCount = 0,
                        pendingUrl = null,
                    ),
                    nextEvent = ExecuteBrokerStepAction(
                        UserProfile(
                            userProfile = state.profileQuery,
                        ),
                    ),
                )
            }
        }
    }
}
