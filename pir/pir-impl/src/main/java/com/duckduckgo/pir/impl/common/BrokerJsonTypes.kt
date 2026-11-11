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

package com.duckduckgo.pir.impl.common

import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStepActions
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStepActions.OptOutStepActions
import com.duckduckgo.pir.impl.common.BrokerStepsParser.BrokerStepActions.ScanStepActions
import com.duckduckgo.pir.impl.scripts.models.BrokerAction

/**
 * The step and action types this app version can execute. Both the Moshi adapters that parse broker steps and the
 * validation that rejects broker JSON updates are built from these, so a type is either fully supported or rejected.
 */
object BrokerJsonTypes {
    const val STEP_TYPE_KEY = "stepType"
    const val ACTION_TYPE_KEY = "actionType"
    const val ACTIONS_KEY = "actions"

    const val STEP_TYPE_SCAN = "scan"
    const val STEP_TYPE_OPT_OUT = "optOut"

    val stepSubtypes: Map<String, Class<out BrokerStepActions>> = mapOf(
        STEP_TYPE_SCAN to ScanStepActions::class.java,
        STEP_TYPE_OPT_OUT to OptOutStepActions::class.java,
    )

    val actionSubtypes: Map<String, Class<out BrokerAction>> = mapOf(
        "extract" to BrokerAction.Extract::class.java,
        "expectation" to BrokerAction.Expectation::class.java,
        "click" to BrokerAction.Click::class.java,
        "executeScript" to BrokerAction.ExecuteScript::class.java,
        "fillForm" to BrokerAction.FillForm::class.java,
        "navigate" to BrokerAction.Navigate::class.java,
        "getCaptchaInfo" to BrokerAction.GetCaptchaInfo::class.java,
        "solveCaptcha" to BrokerAction.SolveCaptcha::class.java,
        "emailConfirmation" to BrokerAction.EmailConfirmation::class.java,
        "condition" to BrokerAction.Condition::class.java,
        "scroll" to BrokerAction.Scroll::class.java,
        "generateEmail" to BrokerAction.GenerateEmail::class.java,
        "getEmailData" to BrokerAction.GetEmailData::class.java,
    )
}
