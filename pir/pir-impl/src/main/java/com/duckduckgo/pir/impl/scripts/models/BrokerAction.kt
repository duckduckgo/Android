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

package com.duckduckgo.pir.impl.scripts.models

import com.duckduckgo.pir.impl.scripts.models.BrokerAction.Click
import com.duckduckgo.pir.impl.scripts.models.BrokerAction.Condition
import com.duckduckgo.pir.impl.scripts.models.BrokerAction.EmailConfirmation
import com.duckduckgo.pir.impl.scripts.models.BrokerAction.Expectation
import com.duckduckgo.pir.impl.scripts.models.BrokerAction.Expectation.ExpectationSelector
import com.duckduckgo.pir.impl.scripts.models.BrokerAction.Extract
import com.duckduckgo.pir.impl.scripts.models.BrokerAction.FillForm
import com.duckduckgo.pir.impl.scripts.models.BrokerAction.GetCaptchaInfo
import com.duckduckgo.pir.impl.scripts.models.BrokerAction.Navigate
import com.duckduckgo.pir.impl.scripts.models.BrokerAction.SolveCaptcha
import com.duckduckgo.pir.impl.scripts.models.DataSource.USER_PROFILE
import com.squareup.moshi.Json

/**
 * This is the data representation of the actions from the broker json.
 */
sealed class BrokerAction(
    open val id: String,
    open val needsEmail: Boolean = false,
    open val dataSource: DataSource? = null,
) {
    data class Navigate(
        override val id: String,
        override val dataSource: DataSource = USER_PROFILE,
        val url: String,
        val ageRange: List<String> = emptyList(),
    ) : BrokerAction(id)

    data class Extract(
        override val id: String,
        val selector: String,
        val noResultsSelector: String?,
        val profile: ExtractProfileSelectors,
    ) : BrokerAction(id)

    data class FillForm(
        override val id: String,
        override val dataSource: DataSource? = DataSource.EXTRACTED_PROFILE,
        val elements: List<ElementSelector>,
        val selector: String,
    ) : BrokerAction(id) {
        override val needsEmail: Boolean = elements.any { it.type == "email" }
    }

    data class GetCaptchaInfo(
        override val id: String,
        val selector: String,
        val captchaType: String? = null,
    ) : BrokerAction(id)

    data class SolveCaptcha(
        override val id: String,
        val selector: String,
        val captchaType: String? = null,
    ) : BrokerAction(id)

    data class Click(
        override val id: String,
        val elements: List<ElementSelector>,
        val selector: String?,
        val choice: List<Choice> = emptyList(),
    ) : BrokerAction(id) {
        data class Choice(
            val condition: Condition,
            val elements: List<ElementSelector>,
        )

        data class Condition(
            val left: String,
            val operation: String,
            val right: String,
        )
    }

    data class Expectation(
        override val id: String,
        val expectations: List<ExpectationSelector>,
    ) : BrokerAction(id) {
        data class ExpectationSelector(
            val type: String,
            val selector: String,
            val expect: String?,
            val parent: String?,
            val failSilently: Boolean?,
        )
    }

    data class EmailConfirmation(
        override val id: String,
        val pollingTime: String,
    ) : BrokerAction(id)

    data class Condition(
        override val id: String,
        @Json(name = "_comment")
        val comment: String,
        val expectations: List<ExpectationSelector>,
        val actions: List<BrokerAction>,
    ) : BrokerAction(id)
}

data class ExtractProfileSelectors(
    val name: ProfileSelector?,
    val alternativeNamesList: ProfileSelector?,
    val age: ProfileSelector?,
    val addressFull: ProfileSelector?,
    val addressFullList: ProfileSelector?,
    val addressCityState: ProfileSelector?,
    val addressCityStateList: ProfileSelector?,
    val phone: ProfileSelector?,
    val phoneList: ProfileSelector?,
    val relativesList: ProfileSelector?,
    val profileUrl: ProfileSelector?,
    val reportedId: ProfileSelector?,
)

data class ProfileSelector(
    val selector: String?,
    val findElements: Boolean?,
    val beforeText: String?,
    val afterText: String?,
    val separator: String?,
    val identifierType: String?,
    val identifier: String?,
)

data class ElementSelector(
    val type: String,
    val selector: String,
    val parent: ParentElement?,
    val multiple: Boolean?,
    val min: String?,
    val max: String?,
    val failSilently: Boolean?,
)

data class ParentElement(
    val profileMatch: ProfileMatch,
)

data class ProfileMatch(
    val selector: String,
    val profile: ExtractProfileSelectors,
)

enum class DataSource {
    // Uses the profile obtained from the user
    @Json(name = "userProfile")
    USER_PROFILE,

    // Uses the profile scraped via the extract action
    @Json(name = "extractedProfile")
    EXTRACTED_PROFILE,
}

fun BrokerAction.asActionType(): String {
    return when (this) {
        is Navigate -> "navigate"
        is Extract -> "extract"
        is Expectation -> "expectation"
        is Click -> "click"
        is FillForm -> "fillForm"
        is GetCaptchaInfo -> "getCaptchaInfo"
        is SolveCaptcha -> "solveCaptcha"
        is EmailConfirmation -> "emailConfirmation"
        is Condition -> "condition"
    }
}
